import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";

export interface SystemConfig {
  threshold: { slopeThresholdDegrees: number; roughnessThreshold: number; suitabilityMinScore: number };
  frontage:  { minM: number; maxM: number; defaultM: number; stepM: number };
  depth:     { minM: number; maxM: number; defaultM: number; stepM: number };
}

@Injectable({ providedIn: "root" })
export class ConfigApiService {
  private readonly base = environment.apiBaseUrl;
  constructor(private http: HttpClient) {}

  getConfig(): Observable<SystemConfig> {
    return this.http.get<SystemConfig>(`${this.base}/config`);
  }

  updateSlopeThreshold(degrees: number): Observable<void> {
    return this.http.patch<void>(`${this.base}/config/slope-threshold?degrees=${degrees}`, {});
  }
}