import { Injectable, NgZone } from '@angular/core';
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import GeoTIFF from 'ol/source/GeoTIFF';
import WebGLTileLayer from 'ol/layer/WebGLTile';
import Graticule from 'ol/layer/Graticule';
import { fromLonLat, toLonLat } from 'ol/proj';
import { GeoJSON } from 'ol/format';
import Feature from 'ol/Feature';
import { Polygon, Point } from 'ol/geom';
import { Style, Fill, Stroke, Circle as CircleStyle } from 'ol/style';
import Select from 'ol/interaction/Select';
import Modify from 'ol/interaction/Modify';
import { click } from 'ol/events/condition';
import { Subject } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface MapClickEvent {
  lat: number;
  lon: number;
  coordinate: number[];
}

export interface ControlPointMoveEvent {
  pointIndex: number;
  lat: number;
  lon: number;
}

@Injectable({ providedIn: 'root' })
export class OlMapService {
  private map!: Map;
  private geotiffLayer!: WebGLTileLayer;
  private graticuleLayer!: Graticule;
  private deploymentLayer!: VectorLayer<VectorSource>;
  private controlPointLayer!: VectorLayer<VectorSource>;
  private modifyInteraction!: Modify;

  readonly mapClick$ = new Subject<MapClickEvent>();
  readonly controlPointMoved$ = new Subject<ControlPointMoveEvent>();

  constructor(private zone: NgZone) {}

  initMap(targetId: string): void {
    const view = new View({
      center: fromLonLat(environment.mapConfig.defaultCenter),
      zoom: environment.mapConfig.defaultZoom,
      minZoom: environment.mapConfig.minZoom,
      maxZoom: environment.mapConfig.maxZoom,
      projection: 'EPSG:3857'
    });

    this.deploymentLayer = new VectorLayer({
      source: new VectorSource(),
      style: this.deploymentStyle(),
      zIndex: 10
    });

    this.controlPointLayer = new VectorLayer({
      source: new VectorSource(),
      style: this.controlPointStyle(),
      zIndex: 20
    });

    // Offline coordinate reference grid — always visible so the map never
    // appears blank when no GeoTIFF imagery is mounted.
    this.graticuleLayer = new Graticule({
      strokeStyle: new Stroke({ color: 'rgba(11, 92, 171, 0.35)', width: 1 }),
      showLabels: true,
      wrapX: false,
      zIndex: 1
    });

    this.map = new Map({
      target: targetId,
      layers: [this.graticuleLayer, this.deploymentLayer, this.controlPointLayer],
      view
    });

    this.map.on('click', (evt) => {
      this.zone.run(() => {
        const lonLat = toLonLat(evt.coordinate);
        this.mapClick$.next({ lat: lonLat[1], lon: lonLat[0], coordinate: evt.coordinate });
      });
    });
  }

  /**
   * Loads an offline GeoTIFF as the base imagery layer.
   * The raster is streamed from the backend (no internet / cloud tiles).
   * On success the view is fitted to the imagery extent.
   *
   * @param url        backend URL serving the .tif bytes
   * @param onError    optional callback invoked if the raster cannot be decoded
   */
  loadOfflineGeoTiff(url: string, onError?: (msg: string) => void): void {
    if (this.geotiffLayer) {
      this.map.removeLayer(this.geotiffLayer);
    }
    // `normalize: true` lets OpenLayers render arbitrary band ranges (8/16-bit)
    // without a hand-written style; `convertToRGB: 'auto'` handles colour TIFFs.
    const source = new GeoTIFF({
      sources: [{ url }],
      normalize: true,
      convertToRGB: 'auto'
    });

    source.on('change', () => {
      if (source.getState() === 'error') {
        const err = source.getError();
        const msg = err?.message ?? 'Failed to decode GeoTIFF';
        console.error('GeoTIFF load error:', msg);
        onError?.(msg);
      }
    });

    this.geotiffLayer = new WebGLTileLayer({ source, zIndex: 0 });
    this.map.getLayers().insertAt(0, this.geotiffLayer);

    // Fit the view to the imagery once its metadata resolves.
    source.getView()
      .then(cfg => { if (cfg.extent) this.map.getView().fit(cfg.extent, { duration: 500 }); })
      .catch(() => { /* extent unavailable — keep current view */ });
  }

  renderDeploymentGeometry(geojson: string): void {
    const source = this.deploymentLayer.getSource()!;
    source.clear();
    const format = new GeoJSON();
    const features = format.readFeatures(geojson, {
      featureProjection: 'EPSG:3857',
      dataProjection: 'EPSG:4326'
    });
    source.addFeatures(features);
    if (features.length > 0) {
      this.map.getView().fit(source.getExtent(), { padding: [60, 60, 60, 60], duration: 600 });
    }
  }

  renderControlPoints(points: Array<{ lat: number; lon: number; index: number }>): void {
    const source = this.controlPointLayer.getSource()!;
    source.clear();
    points.forEach(p => {
      const feat = new Feature({
        geometry: new Point(fromLonLat([p.lon, p.lat])),
        pointIndex: p.index
      });
      source.addFeature(feat);
    });
  }

  /**
   * Live edit preview: rebuild the deployment polygon from the current anchors using
   * the SAME smooth Catmull-Rom Bézier the backend applies on save, so the shape updates
   * as you drag and matches what SAVE will persist. Does NOT refit the view (that would
   * fight the drag). `points` must be in ring order (by point index).
   */
  renderPreviewFromControlPoints(points: Array<{ lat: number; lon: number }>): void {
    if (points.length < 3) return;
    const ring = this.catmullRomBezierRing(points).map(([lon, lat]) => fromLonLat([lon, lat]));
    const source = this.deploymentLayer.getSource()!;
    source.clear();
    source.addFeature(new Feature({ geometry: new Polygon([ring]) }));
  }

  /** Removes the draggable control-point markers (used on save/cancel). */
  clearControlPoints(): void {
    this.controlPointLayer.getSource()?.clear();
  }

  /** Closed smooth curve through the anchors — mirrors BezierEngine on the backend
   *  (Catmull-Rom tangents, tension 0.4, one cubic Bézier per segment). Returns
   *  [lon, lat] pairs in WGS84. */
  private catmullRomBezierRing(
    pts: Array<{ lat: number; lon: number }>, tension = 0.4, steps = 32
  ): number[][] {
    const n = pts.length;
    const h = pts.map((p, i) => {
      const prev = pts[(i - 1 + n) % n], next = pts[(i + 1) % n];
      const tx = (next.lon - prev.lon) * tension;
      const ty = (next.lat - prev.lat) * tension;
      return { inLon: p.lon - tx / 3, inLat: p.lat - ty / 3,
               outLon: p.lon + tx / 3, outLat: p.lat + ty / 3 };
    });
    const ring: number[][] = [];
    for (let i = 0; i < n; i++) {
      const p0 = pts[i], p1 = pts[(i + 1) % n];
      const ax = p0.lon, ay = p0.lat;
      const bx = h[i].outLon, by = h[i].outLat;
      const cx = h[(i + 1) % n].inLon, cy = h[(i + 1) % n].inLat;
      const dx = p1.lon, dy = p1.lat;
      for (let s = 0; s < steps; s++) {
        const t = s / steps, mt = 1 - t;
        ring.push([
          mt*mt*mt*ax + 3*mt*mt*t*bx + 3*mt*t*t*cx + t*t*t*dx,
          mt*mt*mt*ay + 3*mt*mt*t*by + 3*mt*t*t*cy + t*t*t*dy
        ]);
      }
    }
    ring.push(ring[0]);
    return ring;
  }

  enableControlPointEditing(
    onMove: (idx: number, lat: number, lon: number) => void
  ): void {
    if (this.modifyInteraction) {
      this.map.removeInteraction(this.modifyInteraction);
    }
    this.modifyInteraction = new Modify({ source: this.controlPointLayer.getSource()! });
    this.modifyInteraction.on('modifyend', (evt) => {
      this.zone.run(() => {
        evt.features.forEach(feat => {
          const geom = (feat as Feature).getGeometry();
          if (geom instanceof Point) {
            const lonLat = toLonLat(geom.getCoordinates());
            const idx = (feat as Feature).get('pointIndex') as number;
            onMove(idx, lonLat[1], lonLat[0]);
          }
        });
      });
    });
    this.map.addInteraction(this.modifyInteraction);
  }

  disableControlPointEditing(): void {
    if (this.modifyInteraction) {
      this.map.removeInteraction(this.modifyInteraction);
    }
  }

  panToCoord(lat: number, lon: number, zoom?: number): void {
    this.map.getView().animate({
      center: fromLonLat([lon, lat]),
      zoom: zoom ?? this.map.getView().getZoom(),
      duration: 500
    });
  }

  clearDeployments(): void {
    this.deploymentLayer.getSource()?.clear();
    this.controlPointLayer.getSource()?.clear();
  }

  private deploymentStyle(): Style {
    return new Style({
      fill: new Fill({ color: 'rgba(11, 92, 171, 0.16)' }),
      stroke: new Stroke({ color: '#0b5cab', width: 2.5, lineDash: [6, 3] })
    });
  }

  private controlPointStyle(): Style {
    return new Style({
      image: new CircleStyle({
        radius: 7,
        fill: new Fill({ color: '#1976d2' }),
        stroke: new Stroke({ color: '#ffffff', width: 2 })
      })
    });
  }


  addClickMarker(lat: number, lon: number): void {
    const src = this.controlPointLayer.getSource()!;
    // remove old crosshair markers (type = 'click')
    const toRemove = src.getFeatures().filter(f => f.get('markerType') === 'click');
    toRemove.forEach(f => src.removeFeature(f));
    const feat = new Feature({
      geometry: new Point(fromLonLat([lon, lat])),
      markerType: 'click'
    });
    feat.setStyle(new Style({
      image: new CircleStyle({
        radius: 5,
        fill: new Fill({ color: 'rgba(255,180,0,0.8)' }),
        stroke: new Stroke({ color: '#fff', width: 1.5 })
      })
    }));
    src.addFeature(feat);
  }

}
