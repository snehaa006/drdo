import { Component, EventEmitter, Input, Output } from "@angular/core";
import { DeploymentResponse } from "../../../core/models/deployment.model";

@Component({
  selector: "app-deployment-list",
  templateUrl: "./deployment-list.component.html",
  styleUrls: ["./deployment-list.component.scss"]
})
export class DeploymentListComponent {
  @Input() deployments: DeploymentResponse[] = [];
  @Input() activeUid: string | null = null;
  @Output() selected = new EventEmitter<DeploymentResponse>();
  @Output() deleted  = new EventEmitter<string>();

  trackByUid(_: number, d: DeploymentResponse): string { return d.deploymentUid; }
}