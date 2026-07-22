
import { Component, OnInit, OnDestroy, AfterViewInit } from "@angular/core";
import { Subject, takeUntil, debounceTime, switchMap, catchError, EMPTY } from "rxjs";
import { OlMapService } from "../services/ol-map.service";
import { MapStateService } from "../../../core/services/map-state.service";
import { DeploymentApiService } from "../../../core/services/deployment-api.service";
import { TileApiService } from "../../../core/services/tile-api.service";
import {
  DeploymentRequest,
  DeploymentResponse,
  ControlPoint,
  TerrainAnalysis
} from "../../../core/models/deployment.model";

@Component({
  selector: "app-map-view",
  templateUrl: "./map-view.component.html",
  styleUrls: ["./map-view.component.scss"]
})
export class MapViewComponent implements OnInit, AfterViewInit, OnDestroy {
  private destroy$ = new Subject<void>();

  selectedLat: number | null = null;
  selectedLon: number | null = null;
  showForm    = false;
  loading     = false;
  editingControlPoints = false;
  errorMessage: string | null = null;

  activeDeployment: DeploymentResponse | null = null;
  deployments: DeploymentResponse[] = [];

  /** Live terrain stats for the in-progress (unsaved) edit; shown in place of the
   *  saved terrain while dragging control points. */
  previewTerrain: TerrainAnalysis | null = null;
  /** Live footprint size (metres) for the in-progress edit; shown in place of the saved
   *  frontage/depth while dragging. */
  previewFrontageM: number | null = null;
  previewDepthM: number | null = null;

  private pendingControlPoints: ControlPoint[] = [];
  private terrainPreview$ = new Subject<void>();

  constructor(
    private mapService:  OlMapService,
    private stateService: MapStateService,
    private apiService:  DeploymentApiService,
    private tileService: TileApiService
  ) {}

  ngOnInit(): void {
    // While editing, recompute terrain for the dragged footprint (debounced so we don't
    // hammer the backend on every drag); switchMap drops superseded requests.
    this.terrainPreview$.pipe(
      debounceTime(250),
      switchMap(() => {
        if (!this.activeDeployment || this.pendingControlPoints.length < 3) return EMPTY;
        return this.apiService.previewTerrain(this.activeDeployment.deploymentUid, {
          controlPoints: this.pendingControlPoints
        }).pipe(catchError(() => EMPTY));
      }),
      takeUntil(this.destroy$)
    ).subscribe(t => this.previewTerrain = t);

    this.apiService.listDeployments()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: list => {
          this.deployments = list;
          this.stateService.setDeployments(list);
          list.forEach(d => {
            if (d.geometry?.geojson) this.mapService.renderDeploymentGeometry(d.geometry.geojson);
          });
        },
        error: err => this.errorMessage = "Failed to load deployments: " + err.message
      });
  }

  ngAfterViewInit(): void {
    this.mapService.initMap("ol-map-container");
    this.loadBaseImagery();

    this.mapService.mapClick$
      .pipe(takeUntil(this.destroy$))
      .subscribe(evt => {
        if (!this.editingControlPoints) {
          this.selectedLat  = evt.lat;
          this.selectedLon  = evt.lon;
          this.showForm     = true;
          this.activeDeployment = null;
          this.errorMessage = null;
        }
      });
  }

  /**
   * Discovers offline GeoTIFF base maps and renders the first one.
   * If none are mounted, the coordinate graticule remains as the base —
   * the map is never blank.
   */
  private loadBaseImagery(): void {
    this.tileService.listGeoTiffs()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: tiffs => {
          if (tiffs.length > 0) {
            const url = this.tileService.geoTiffUrl(tiffs[0].name);
            this.mapService.loadOfflineGeoTiff(url, msg =>
              this.errorMessage = "Base imagery could not be rendered: " + msg);
          }
        },
        error: () => { /* offline-first: graticule base remains, no error surfaced */ }
      });
  }

  onDeploymentSubmit(req: DeploymentRequest): void {
    this.loading      = true;
    this.errorMessage = null;
    this.apiService.createDeployment(req)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: d => {
          this.loading  = false;
          this.showForm = false;
          this.activeDeployment = d;
          this.deployments = [...this.deployments, d];
          if (d.geometry?.geojson) this.mapService.renderDeploymentGeometry(d.geometry.geojson);
          // Bring the new deployment into view (important when coordinates were
          // typed manually rather than picked on the map).
          this.mapService.panToCoord(d.centerLat, d.centerLon, 13);
          this.renderControlPoints(d);
        },
        error: err => {
          this.loading      = false;
          this.errorMessage = err.message || "Failed to create deployment";
        }
      });
  }

  selectDeployment(d: DeploymentResponse): void {
    this.activeDeployment = d;
    this.showForm = false;
    this.mapService.panToCoord(d.centerLat, d.centerLon, 13);
    if (d.geometry?.geojson) this.mapService.renderDeploymentGeometry(d.geometry.geojson);
  }

  deleteDeployment(uid: string): void {
    this.apiService.deleteDeployment(uid)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.deployments = this.deployments.filter(d => d.deploymentUid !== uid);
          if (this.activeDeployment?.deploymentUid === uid) {
            this.activeDeployment = null;
            this.mapService.clearDeployments();
          }
        },
        error: err => this.errorMessage = "Delete failed: " + err.message
      });
  }

  /** Frontage shown in the detail panel — the live edited size while dragging, else saved. */
  get displayFrontageKm(): number | null {
    const m = (this.editingControlPoints && this.previewFrontageM != null)
      ? this.previewFrontageM : this.activeDeployment?.parameters?.frontageM;
    return m != null ? m / 1000 : null;
  }
  /** Depth shown in the detail panel — the live edited size while dragging, else saved. */
  get displayDepthKm(): number | null {
    const m = (this.editingControlPoints && this.previewDepthM != null)
      ? this.previewDepthM : this.activeDeployment?.parameters?.depthM;
    return m != null ? m / 1000 : null;
  }

  enableEditing(): void {
    if (!this.activeDeployment) return;
    let cps = this.activeDeployment.controlPoints ?? [];
    if (!cps.length) {
      // Planar deployments (ellipses) are stored without control points. Derive draggable
      // anchors from the footprint so they can be reshaped too — a saved edit becomes a
      // custom Bézier, like every other edit.
      cps = this.deriveAnchorsFromFootprint(this.activeDeployment);
    }
    if (cps.length < 3) {
      this.errorMessage = "This deployment has no editable geometry.";
      return;
    }
    // Work on copies so a Cancel leaves the original geometry untouched.
    this.pendingControlPoints = cps.map(cp => ({ ...cp }));
    this.editingControlPoints = true;
    // until first drag, show the saved terrain / frontage / depth
    this.previewTerrain = null;
    this.previewFrontageM = this.previewDepthM = null;
    // Draw the draggable handles on the map first, then enable dragging on them.
    this.mapService.renderControlPoints(
      this.pendingControlPoints.map(cp => ({ lat: cp.lat, lon: cp.lon, index: cp.pointIndex })));
    this.mapService.enableControlPointEditing((idx, lat, lon) => {
      const cp = this.pendingControlPoints.find(p => p.pointIndex === idx);
      if (cp) { cp.lat = lat; cp.lon = lon; }
      const anchors = [...this.pendingControlPoints]
        .sort((a, b) => a.pointIndex - b.pointIndex)
        .map(p => ({ lat: p.lat, lon: p.lon }));
      // Live preview: redraw the polygon smoothly from the current anchors, so you see
      // the reshaped (and still curved) geometry before saving.
      this.mapService.renderPreviewFromControlPoints(anchors);
      // ...update frontage/depth from the new footprint (client-side, instant)...
      const size = this.mapService.footprintSizeFromControlPoints(anchors);
      if (size) { this.previewFrontageM = size.frontageM; this.previewDepthM = size.depthM; }
      // ...and recompute the terrain stats for the new footprint (debounced backend call).
      this.terrainPreview$.next();
    });
  }

  saveControlPoints(): void {
    if (!this.activeDeployment || !this.pendingControlPoints.length) return;
    this.loading = true;
    this.apiService.updateControlPoints(this.activeDeployment.deploymentUid, {
      controlPoints: this.pendingControlPoints
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: d => {
        this.loading = false;
        this.editingControlPoints = false;
        this.previewTerrain = null;
        this.previewFrontageM = this.previewDepthM = null;
        this.activeDeployment = d;
        this.mapService.disableControlPointEditing();
        this.mapService.clearControlPoints();
        if (d.geometry?.geojson) this.mapService.renderDeploymentGeometry(d.geometry.geojson);
        const idx = this.deployments.findIndex(x => x.deploymentUid === d.deploymentUid);
        if (idx !== -1) this.deployments = [...this.deployments.slice(0, idx), d, ...this.deployments.slice(idx + 1)];
      },
      error: err => { this.loading = false; this.errorMessage = err.message || "Save failed"; }
    });
  }

  cancelEditing(): void {
    this.editingControlPoints = false;
    this.previewTerrain = null;
    this.previewFrontageM = this.previewDepthM = null;
    this.mapService.disableControlPointEditing();
    this.mapService.clearControlPoints();
    // Discard the in-progress preview and restore the saved geometry unchanged.
    if (this.activeDeployment?.geometry?.geojson) {
      this.mapService.renderDeploymentGeometry(this.activeDeployment.geometry.geojson);
    }
  }

  /**
   * 8 anchor points around a deployment's footprint (ellipse math: centre + semi-axes,
   * rotated by heading). Used to make planar/ellipse deployments — which are stored
   * without control points — editable; dragging these and saving yields a custom Bézier.
   */
  private deriveAnchorsFromFootprint(d: DeploymentResponse): ControlPoint[] {
    const p = d.parameters;
    if (!p || d.centerLat == null || d.centerLon == null) return [];
    const count = 8;
    const mPerDegLat = 111320;
    const mPerDegLon = 111320 * Math.cos((d.centerLat * Math.PI) / 180);
    const rot = ((p.headingDegrees ?? d.headingDegrees ?? 0) * Math.PI) / 180;
    const a = p.frontageM / 2, b = p.depthM / 2;
    const anchors: ControlPoint[] = [];
    for (let i = 0; i < count; i++) {
      const ang = (2 * Math.PI * i) / count;
      const ex = a * Math.cos(ang), ey = b * Math.sin(ang);
      const rx = ex * Math.cos(rot) - ey * Math.sin(rot);
      const ry = ex * Math.sin(rot) + ey * Math.cos(rot);
      anchors.push({
        pointIndex: i,
        lat: d.centerLat + ry / mPerDegLat,
        lon: d.centerLon + rx / mPerDegLon
      });
    }
    return anchors;
  }

  private renderControlPoints(d: DeploymentResponse): void {
    const cps: ControlPoint[] = d.controlPoints ?? [];
    this.pendingControlPoints = cps;
    this.mapService.renderControlPoints(cps.map(cp => ({
      lat: cp.lat, lon: cp.lon, index: cp.pointIndex
    })));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
