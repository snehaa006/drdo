export interface DeploymentRequest {
  centerLat: number;
  centerLon: number;
  frontageM: number;
  depthM: number;
  slopeThresholdDegrees?: number;
  headingDegrees?: number;
  terrainAdaptive?: boolean;
  bezierSmoothing?: boolean;
  name?: string;
}

export interface DeploymentResponse {
  id: number;
  deploymentUid: string;
  name: string;
  status: DeploymentStatus;
  centerLat: number;
  centerLon: number;
  headingDegrees?: number;
  parameters?: DeploymentParameters;
  geometry?: DeploymentGeometry;
  terrainAnalysis?: TerrainAnalysis;
  createdAt: string;
  updatedAt: string;
}

export type DeploymentStatus = 'DRAFT' | 'COMPUTING' | 'READY' | 'EDITED' | 'ARCHIVED';

export interface DeploymentParameters {
  id: number;
  frontageM: number;
  depthM: number;
  slopeThresholdDegrees: number;
  headingDegrees?: number;
  terrainAdaptive: boolean;
  bezierSmoothing: boolean;
  createdAt: string;
}

export interface DeploymentGeometry {
  id: number;
  geometryType: 'ELLIPSE' | 'BEZIER_ADAPTIVE' | 'BEZIER_CUSTOM';
  geojson: string;
  geomWkt?: string;
  version: number;
  isValid: boolean;
  validationMessage?: string;
  updatedAt: string;
}

export interface TerrainAnalysis {
  id: number;
  meanElevationM: number;
  minElevationM: number;
  maxElevationM: number;
  meanSlopeDegrees: number;
  maxSlopeDegrees: number;
  terrainRoughness: number;
  suitabilityScore: number;
  isPlanar: boolean;
  sampleCount: number;
  computedAt: string;
}

export interface ControlPoint {
  id?: number;
  pointIndex: number;
  pointType?: string;
  lat: number;
  lon: number;
  handleLat1?: number;
  handleLon1?: number;
  handleLat2?: number;
  handleLon2?: number;
  isLocked?: boolean;
}

export interface ControlPointUpdate {
  controlPoints: ControlPoint[];
}