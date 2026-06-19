import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";
import { ControlPoint } from "../../../core/models/deployment.model";

@Component({
  selector: "app-geometry-editor",
  templateUrl: "./geometry-editor.component.html",
  styleUrls: ["./geometry-editor.component.scss"]
})
export class GeometryEditorComponent implements OnChanges {
  @Input()  controlPoints: ControlPoint[] = [];
  @Input()  loading = false;
  @Output() save   = new EventEmitter<ControlPoint[]>();
  @Output() cancel = new EventEmitter<void>();

  editablePoints: ControlPoint[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes["controlPoints"]) {
      this.editablePoints = this.controlPoints.map(cp => ({ ...cp }));
    }
  }

  updatePoint(idx: number, field: keyof ControlPoint, value: string): void {
    const num = parseFloat(value);
    if (!isNaN(num)) {
      (this.editablePoints[idx] as any)[field] = num;
    }
  }

  onSave(): void {
    this.save.emit(this.editablePoints);
  }

  trackByIndex(i: number): number { return i; }
}