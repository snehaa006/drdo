import { NgModule } from "@angular/core";
import { BrowserModule } from "@angular/platform-browser";
import { HttpClientModule, HTTP_INTERCEPTORS } from "@angular/common/http";
import { ReactiveFormsModule, FormsModule } from "@angular/forms";
import { CommonModule, DecimalPipe } from "@angular/common";

import { AppRoutingModule } from "./app-routing.module";
import { AppComponent } from "./app.component";
import { SharedModule } from "./shared/shared.module";
import { MapViewComponent } from "./features/map/components/map-view.component";
import { DeploymentFormComponent } from "./features/deployment/components/deployment-form.component";
import { DeploymentListComponent } from "./features/deployment/components/deployment-list.component";
import { GeometryEditorComponent } from "./features/deployment/components/geometry-editor.component";
import { TerrainPanelComponent } from "./features/terrain/components/terrain-panel.component";
import { ApiErrorInterceptor } from "./core/interceptors/api-error.interceptor";

@NgModule({
  declarations: [
    AppComponent,
    MapViewComponent,
    DeploymentFormComponent,
    DeploymentListComponent,
    GeometryEditorComponent,
    TerrainPanelComponent
  ],
  imports: [
    BrowserModule,
    CommonModule,
    HttpClientModule,
    ReactiveFormsModule,
    FormsModule,
    AppRoutingModule,
    SharedModule
  ],
  providers: [
    DecimalPipe,
    { provide: HTTP_INTERCEPTORS, useClass: ApiErrorInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}