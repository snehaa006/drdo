import { Injectable } from "@angular/core";
import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Observable } from "rxjs";
import { environment } from "../../../../environments/environment";
import { ControlPointUpdate, DeploymentGeometry } from "../../../core/models/deployment.model";

@Injectable({ providedIn: "root" })
export class GeometryEditingService {
  private readonly base = environment.apiBaseUrl;
  private readonly headers = new HttpHeaders({ "Content-Type": "application/json" });

  constructor(private http: HttpClient) {}

  applyEdit(uid: string, update: ControlPointUpdate): Observable<DeploymentGeometry> {
    return this.http.put<DeploymentGeometry>(
      `${this.base}/geometry/deployment/${uid}/edit`, update, { headers: this.headers });
  }
}