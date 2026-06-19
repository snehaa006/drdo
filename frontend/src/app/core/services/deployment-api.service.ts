import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DeploymentRequest, DeploymentResponse,
  ControlPointUpdate
} from '../models/deployment.model';

@Injectable({ providedIn: 'root' })
export class DeploymentApiService {
  private readonly base = environment.apiBaseUrl;
  private readonly headers = new HttpHeaders({ 'Content-Type': 'application/json' });

  constructor(private http: HttpClient) {}

  createDeployment(req: DeploymentRequest): Observable<DeploymentResponse> {
    return this.http.post<DeploymentResponse>(`${this.base}/deployments`, req, { headers: this.headers });
  }

  getDeployment(uid: string): Observable<DeploymentResponse> {
    return this.http.get<DeploymentResponse>(`${this.base}/deployments/${uid}`);
  }

  listDeployments(): Observable<DeploymentResponse[]> {
    return this.http.get<DeploymentResponse[]>(`${this.base}/deployments`);
  }

  getGeoJson(uid: string): Observable<string> {
    return this.http.get(`${this.base}/deployments/${uid}/geojson`, { responseType: 'text' });
  }

  updateControlPoints(uid: string, update: ControlPointUpdate): Observable<DeploymentResponse> {
    return this.http.put<DeploymentResponse>(
      `${this.base}/deployments/${uid}/control-points`, update, { headers: this.headers });
  }

  deleteDeployment(uid: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/deployments/${uid}`);
  }
}