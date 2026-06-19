import { Pipe, PipeTransform } from "@angular/core";

@Pipe({ name: "coordFormat" })
export class CoordFormatPipe implements PipeTransform {
  transform(value: number, type: "lat" | "lon" = "lat"): string {
    if (value == null) return "--";
    const abs = Math.abs(value);
    const deg = Math.floor(abs);
    const minFull = (abs - deg) * 60;
    const min = Math.floor(minFull);
    const sec = ((minFull - min) * 60).toFixed(1);
    const hemi = type === "lat" ? (value >= 0 ? "N" : "S") : (value >= 0 ? "E" : "W");
    return `${deg}° ${min}' ${sec}" ${hemi}`;
  }
}