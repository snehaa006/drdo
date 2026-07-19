import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { DeploymentRequest } from '../../../core/models/deployment.model';

@Component({
  selector: 'app-deployment-form',
  templateUrl: './deployment-form.component.html',
  styleUrls: ['./deployment-form.component.scss']
})
export class DeploymentFormComponent implements OnInit, OnChanges {
  /** Coordinates picked by clicking the map (also editable directly in the form). */
  @Input() centerLat: number | null = null;
  @Input() centerLon: number | null = null;
  @Input() loading = false;
  @Output() formSubmit = new EventEmitter<DeploymentRequest>();
  @Output() cancel = new EventEmitter<void>();

  form!: FormGroup;

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', [Validators.maxLength(256)]],
      centerLat: [this.centerLat, [Validators.required, Validators.min(-90), Validators.max(90)]],
      centerLon: [this.centerLon, [Validators.required, Validators.min(-180), Validators.max(180)]],
      frontageM: [200, [Validators.required, Validators.min(10), Validators.max(5000)]],
      depthM: [100, [Validators.required, Validators.min(5), Validators.max(2000)]],
      slopeThresholdDegrees: [15, [Validators.required, Validators.min(0), Validators.max(90)]],
      headingDegrees: [0, [Validators.min(0), Validators.max(360)]],
      terrainAdaptive: [true],
      bezierSmoothing: [true]
    });
  }

  /** Keep the lat/lon fields in sync when the user clicks a point on the map. */
  ngOnChanges(changes: SimpleChanges): void {
    if (!this.form) return;
    if (changes['centerLat'] && this.centerLat !== null) {
      this.form.patchValue({ centerLat: this.centerLat }, { emitEvent: false });
    }
    if (changes['centerLon'] && this.centerLon !== null) {
      this.form.patchValue({ centerLon: this.centerLon }, { emitEvent: false });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    const v = this.form.value;
    this.formSubmit.emit({
      centerLat: v.centerLat,
      centerLon: v.centerLon,
      frontageM: v.frontageM,
      depthM: v.depthM,
      slopeThresholdDegrees: v.slopeThresholdDegrees,
      headingDegrees: v.headingDegrees,
      terrainAdaptive: v.terrainAdaptive,
      bezierSmoothing: v.bezierSmoothing,
      name: v.name || undefined
    });
  }
}
