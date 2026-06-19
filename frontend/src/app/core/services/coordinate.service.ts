import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";

@Injectable({ providedIn: "root" })
export class CoordinateService {
  private readonly base = environment.apiBaseUrl;
  constructor(private http: HttpClient) {}

  toUtm(lat: number, lon: number): Observable<any> {
    return this.http.get(`${this.base}/coordinates/to-utm?lat=${lat}&lon=${lon}`);
  }

  fromUtm(easting: number, northing: number, zone: number, north = true): Observable<any> {
    return this.http.get(`${this.base}/coordinates/from-utm?easting=${easting}&northing=${northing}&zone=${zone}&north=${north}`);
  }

  metresPerDegree(lat: number): Observable<any> {
    return this.http.get(`${this.base}/coordinates/metres-per-degree?lat=${lat}`);
  }
}