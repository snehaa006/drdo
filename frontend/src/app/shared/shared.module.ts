import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { CoordFormatPipe } from "./pipes/coord-format.pipe";

@NgModule({
  declarations: [CoordFormatPipe],
  imports: [CommonModule],
  exports: [CoordFormatPipe]
})
export class SharedModule {}