
import { Component, OnInit, OnDestroy, AfterViewInit } from "@angular/core";
import { Subject, takeUntil } from "rxjs";
import { OlMapService } from "../services/ol-map.service";
import { MapStateService } from "../../../core/services/map-state.service";
import { DeploymentApiService } from "../../../core/services/deployment-api.service";
import { TileApiService } from "../../../core/services/tile-api.service";
import {
  DeploymentRequest,
  DeploymentResponse,
  ControlPoint
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

  private pendingControlPoints: ControlPoint[] = [];

  constructor(
    private mapService:  OlMapService,
    private stateService: MapStateService,
    private apiService:  DeploymentApiService,
    private tileService: TileApiService
  ) {}

  ngOnInit(): void {
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

  enableEditing(): void {
    if (!this.activeDeployment) return;
    const cps = this.activeDeployment.controlPoints ?? [];
    if (!cps.length) {
      this.errorMessage = "This deployment has no editable control points.";
      return;
    }
    // Work on copies so a Cancel leaves the original geometry untouched.
    this.pendingControlPoints = cps.map(cp => ({ ...cp }));
    this.editingControlPoints = true;
    // Draw the draggable handles on the map first, then enable dragging on them.
    this.mapService.renderControlPoints(
      this.pendingControlPoints.map(cp => ({ lat: cp.lat, lon: cp.lon, index: cp.pointIndex })));
    this.mapService.enableControlPointEditing((idx, lat, lon) => {
      const cp = this.pendingControlPoints.find(p => p.pointIndex === idx);
      if (cp) { cp.lat = lat; cp.lon = lon; }
      // Live preview: redraw the polygon smoothly from the current anchors, so you see
      // the reshaped (and still curved) geometry before saving.
      this.mapService.renderPreviewFromControlPoints(
        [...this.pendingControlPoints]
          .sort((a, b) => a.pointIndex - b.pointIndex)
          .map(p => ({ lat: p.lat, lon: p.lon })));
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
    this.mapService.disableControlPointEditing();
    this.mapService.clearControlPoints();
    // Discard the in-progress preview and restore the saved geometry unchanged.
    if (this.activeDeployment?.geometry?.geojson) {
      this.mapService.renderDeploymentGeometry(this.activeDeployment.geometry.geojson);
    }
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
