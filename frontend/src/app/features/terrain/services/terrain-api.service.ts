import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { environment } from "../../../../environments/environment";
import { TerrainAnalysis } from "../../../core/models/deployment.model";

@Injectable({ providedIn: "root" })
export class TerrainApiService {
  private readonly base = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  getTerrainForDeployment(uid: string): Observable<TerrainAnalysis> {
    return this.http.get<TerrainAnalysis>(`${this.base}/terrain/deployment/${uid}`);
  }

  recomputeTerrain(uid: string): Observable<TerrainAnalysis> {
    return this.http.post<TerrainAnalysis>(`${this.base}/terrain/deployment/${uid}/recompute`, {});
  }
}