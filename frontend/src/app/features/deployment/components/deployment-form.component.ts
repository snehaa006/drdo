import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { DeploymentRequest } from '../../../core/models/deployment.model';

@Component({
  selector: 'app-deployment-form',
  templateUrl: './deployment-form.component.html',
  styleUrls: ['./deployment-form.component.scss']
})
export class DeploymentFormComponent implements OnInit {
  @Input() centerLat: number | null = null;
  @Input() centerLon: number | null = null;
  @Input() loading = false;
  @Output() submit = new EventEmitter<DeploymentRequest>();
  @Output() cancel = new EventEmitter<void>();

  form!: FormGroup;

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', [Validators.maxLength(256)]],
      frontageM: [200, [Validators.required, Validators.min(10), Validators.max(5000)]],
      depthM: [100, [Validators.required, Validators.min(5), Validators.max(2000)]],
      slopeThresholdDegrees: [15, [Validators.required, Validators.min(0), Validators.max(90)]],
      headingDegrees: [0, [Validators.min(0), Validators.max(360)]],
      terrainAdaptive: [true],
      bezierSmoothing: [true]
    });
  }

  onSubmit(): void {
    if (this.form.invalid || this.centerLat === null || this.centerLon === null) return;
    const val = this.form.value;
    this.submit.emit({
      centerLat: this.centerLat,
      centerLon: this.centerLon,
      frontageM: val.frontageM,
      depthM: val.depthM,
      slopeThresholdDegrees: val.slopeThresholdDegrees,
      headingDegrees: val.headingDegrees,
      terrainAdaptive: val.terrainAdaptive,
      bezierSmoothing: val.bezierSmoothing,
      name: val.name || undefined
    });
  }
}