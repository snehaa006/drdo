import { Injectable, NgZone } from '@angular/core';
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import GeoTIFF from 'ol/source/GeoTIFF';
import WebGLTileLayer from 'ol/layer/WebGLTile';
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

    this.map = new Map({
      target: targetId,
      layers: [this.deploymentLayer, this.controlPointLayer],
      view
    });

    this.map.on('click', (evt) => {
      this.zone.run(() => {
        const lonLat = toLonLat(evt.coordinate);
        this.mapClick$.next({ lat: lonLat[1], lon: lonLat[0], coordinate: evt.coordinate });
      });
    });
  }

  loadOfflineGeoTiff(url: string): void {
    if (this.geotiffLayer) {
      this.map.removeLayer(this.geotiffLayer);
    }
    const source = new GeoTIFF({ sources: [{ url }] });
    this.geotiffLayer = new WebGLTileLayer({ source, zIndex: 0 });
    this.map.getLayers().insertAt(0, this.geotiffLayer);
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
      fill: new Fill({ color: 'rgba(30, 144, 255, 0.18)' }),
      stroke: new Stroke({ color: '#1E90FF', width: 2, lineDash: [6, 3] })
    });
  }

  private controlPointStyle(): Style {
    return new Style({
      image: new CircleStyle({
        radius: 7,
        fill: new Fill({ color: '#00BFFF' }),
        stroke: new Stroke({ color: '#fff', width: 2 })
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
