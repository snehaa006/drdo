import { Component, Input } from '@angular/core';
import { TerrainAnalysis } from '../../../core/models/deployment.model';

@Component({
  selector: 'app-terrain-panel',
  templateUrl: './terrain-panel.component.html',
  styleUrls: ['./terrain-panel.component.scss']
})
export class TerrainPanelComponent {
  @Input() terrain: TerrainAnalysis | null = null;

  get suitabilityClass(): string {
    if (!this.terrain) return '';
    const s = this.terrain.suitabilityScore;
    if (s >= 0.7) return 'suitable';
    if (s >= 0.4) return 'marginal';
    return 'unsuitable';
  }
}