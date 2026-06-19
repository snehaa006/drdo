import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { DeploymentResponse } from '../models/deployment.model';

export interface MapState {
  selectedLat: number | null;
  selectedLon: number | null;
  activeDeployment: DeploymentResponse | null;
  deployments: DeploymentResponse[];
  editingControlPoints: boolean;
}

@Injectable({ providedIn: 'root' })
export class MapStateService {
  private state = new BehaviorSubject<MapState>({
    selectedLat: null,
    selectedLon: null,
    activeDeployment: null,
    deployments: [],
    editingControlPoints: false
  });

  state$ = this.state.asObservable();

  setSelectedCoords(lat: number, lon: number): void {
    this.state.next({ ...this.state.value, selectedLat: lat, selectedLon: lon });
  }

  setActiveDeployment(d: DeploymentResponse | null): void {
    this.state.next({ ...this.state.value, activeDeployment: d });
  }

  setDeployments(list: DeploymentResponse[]): void {
    this.state.next({ ...this.state.value, deployments: list });
  }

  setEditingControlPoints(editing: boolean): void {
    this.state.next({ ...this.state.value, editingControlPoints: editing });
  }

  get snapshot(): MapState { return this.state.value; }
}