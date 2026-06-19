import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { environment } from "../../../environments/environment";

export interface GeoTiffInfo {
  name: string;
  crs: string;
  minLon: number;
  minLat: number;
  maxLon: number;
  maxLat: number;
  width: number;
  height: number;
  bandCount: number;
}

@Injectable({ providedIn: "root" })
export class TileApiService {
  private readonly base = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  /** Lists offline GeoTIFF base maps registered on the backend. */
  listGeoTiffs(): Observable<GeoTiffInfo[]> {
    return this.http.get<GeoTiffInfo[]>(`${this.base}/tiles/geotiff`);
  }

  /** Direct URL the OpenLayers GeoTIFF source can fetch the raster from. */
  geoTiffUrl(name: string): string {
    return `${this.base}/tiles/geotiff/${encodeURIComponent(name)}`;
  }
}
