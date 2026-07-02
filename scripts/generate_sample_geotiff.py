#!/usr/bin/env python3
"""
Builds a minimal, valid, offline GeoTIFF (uncompressed RGB, EPSG:3857) using only
the Python standard library (struct) -- no GDAL/rasterio/PIL, fully offline.

Covers the same lat/lon extent as the sample DTED tiles already committed under
data/terrain/dted (lon 75-79E, lat 28-31N), so the offline base-map + terrain
analysis pipeline can be exercised end-to-end with matching test data.

The raster content is a synthetic elevation-tinted pattern with 1-degree
graticule lines -- clearly a placeholder, not real imagery.
"""
import math
import struct

R = 6378137.0  # Web Mercator sphere radius


def lon_to_x(lon):
    return R * math.radians(lon)


def lat_to_y(lat):
    return R * math.log(math.tan(math.pi / 4 + math.radians(lat) / 2))


def build_geotiff(path, lon_min, lon_max, lat_min, lat_max, width, height):
    x_min, x_max = lon_to_x(lon_min), lon_to_x(lon_max)
    y_min, y_max = lat_to_y(lat_min), lat_to_y(lat_max)

    px_scale_x = (x_max - x_min) / width
    px_scale_y = (y_max - y_min) / height

    # ---- Build pixel data (chunky RGB, top row = north/y_max) ----
    pixels = bytearray(width * height * 3)

    # Precompute pixel-row for each integer latitude (graticule lines)
    lat_lines_row = set()
    lat0 = math.ceil(lat_min)
    while lat0 <= lat_max:
        y = lat_to_y(lat0)
        row = round((y_max - y) / px_scale_y)
        if 0 <= row < height:
            lat_lines_row.add(row)
        lat0 += 1

    lon_lines_col = set()
    lon0 = math.ceil(lon_min)
    while lon0 <= lon_max:
        x = lon_to_x(lon0)
        col = round((x - x_min) / px_scale_x)
        if 0 <= col < width:
            lon_lines_col.add(col)
        lon0 += 1

    for row in range(height):
        v = row / (height - 1)  # 0 at north edge, 1 at south edge
        for col in range(width):
            u = col / (width - 1)
            # Synthetic elevation-style shading: green lowlands -> brown/tan highlands
            elev = 0.5 + 0.5 * math.sin(u * math.pi * 2.3) * math.cos(v * math.pi * 1.7)
            if elev < 0.4:
                r, g, b = 60, 110 + int(40 * elev), 60
            elif elev < 0.7:
                r, g, b = 120 + int(60 * elev), 100 + int(40 * elev), 70
            else:
                r, g, b = 150 + int(90 * elev), 130 + int(70 * elev), 110 + int(80 * elev)

            if row in lat_lines_row or col in lon_lines_col:
                r, g, b = 200, 40, 40  # graticule overlay

            off = (row * width + col) * 3
            pixels[off] = min(r, 255)
            pixels[off + 1] = min(g, 255)
            pixels[off + 2] = min(b, 255)

    # ---- GeoTIFF tag values ----
    # GeoKeyDirectoryTag: header + 3 keys
    #  1024 GTModelTypeGeoKey    = 1 (Projected)
    #  1025 GTRasterTypeGeoKey   = 1 (RasterPixelIsArea)
    #  3072 ProjectedCSTypeGeoKey = 3857 (WGS 84 / Pseudo-Mercator)
    geo_keys = [
        1, 1, 0, 3,       # KeyDirectoryVersion, KeyRevision, MinorRevision, NumberOfKeys
        1024, 0, 1, 1,
        1025, 0, 1, 1,
        3072, 0, 1, 3857,
    ]

    model_pixel_scale = [px_scale_x, px_scale_y, 0.0]
    # Tiepoint: raster (0,0) [top-left pixel] -> model (x_min, y_max)
    model_tiepoint = [0.0, 0.0, 0.0, x_min, y_max, 0.0]

    endian = "<"  # little-endian (II)
    entries = []  # (tag, type, count, value_bytes_or_None, offset_placeholder)

    def entry(tag, typ, count, packed):
        entries.append((tag, typ, count, packed))

    TYPE_SHORT, TYPE_LONG, TYPE_DOUBLE = 3, 4, 12
    TYPE_SIZE = {TYPE_SHORT: 2, TYPE_LONG: 4, TYPE_DOUBLE: 8}

    strip_byte_count = len(pixels)

    entry(256, TYPE_LONG, 1, struct.pack(endian + "I", width))          # ImageWidth
    entry(257, TYPE_LONG, 1, struct.pack(endian + "I", height))         # ImageLength
    entry(258, TYPE_SHORT, 3, None)                                     # BitsPerSample (external, 3 shorts)
    entry(259, TYPE_SHORT, 1, struct.pack(endian + "HH", 1, 0))         # Compression = none
    entry(262, TYPE_SHORT, 1, struct.pack(endian + "HH", 2, 0))         # PhotometricInterpretation = RGB
    entry(273, TYPE_LONG, 1, None)                                      # StripOffsets (fill later)
    entry(277, TYPE_SHORT, 1, struct.pack(endian + "HH", 3, 0))         # SamplesPerPixel = 3
    entry(278, TYPE_LONG, 1, struct.pack(endian + "I", height))         # RowsPerStrip
    entry(279, TYPE_LONG, 1, struct.pack(endian + "I", strip_byte_count))  # StripByteCounts
    entry(284, TYPE_SHORT, 1, struct.pack(endian + "HH", 1, 0))         # PlanarConfiguration = chunky
    entry(33550, TYPE_DOUBLE, 3, None)                                  # ModelPixelScaleTag (external)
    entry(33922, TYPE_DOUBLE, 6, None)                                  # ModelTiepointTag (external)
    entry(34735, TYPE_SHORT, len(geo_keys), None)                       # GeoKeyDirectoryTag (external)

    n = len(entries)
    header_size = 8
    ifd_size = 2 + n * 12 + 4
    ifd_start = header_size
    external_start = ifd_start + ifd_size

    # Lay out external (>4-byte) data blocks in tag order, record their offsets
    external_blobs = {}
    cursor = external_start

    bits_per_sample_bytes = struct.pack(endian + "HHH", 8, 8, 8)
    external_blobs[258] = (cursor, bits_per_sample_bytes); cursor += len(bits_per_sample_bytes)

    pixel_scale_bytes = struct.pack(endian + "3d", *model_pixel_scale)
    external_blobs[33550] = (cursor, pixel_scale_bytes); cursor += len(pixel_scale_bytes)

    tiepoint_bytes = struct.pack(endian + "6d", *model_tiepoint)
    external_blobs[33922] = (cursor, tiepoint_bytes); cursor += len(tiepoint_bytes)

    geokeys_bytes = struct.pack(endian + f"{len(geo_keys)}H", *geo_keys)
    external_blobs[34735] = (cursor, geokeys_bytes); cursor += len(geokeys_bytes)

    strip_offset = cursor  # pixel data goes right after all external tag blobs

    # ---- Write file ----
    with open(path, "wb") as f:
        f.write(b"II")
        f.write(struct.pack(endian + "H", 42))
        f.write(struct.pack(endian + "I", ifd_start))

        f.write(struct.pack(endian + "H", n))
        for tag, typ, count, packed in entries:
            f.write(struct.pack(endian + "HHI", tag, typ, count))
            size = TYPE_SIZE[typ] * count
            if tag == 273:  # StripOffsets -> resolved now
                f.write(struct.pack(endian + "I", strip_offset))
            elif packed is not None and size <= 4:
                f.write(packed.ljust(4, b"\x00"))
            elif tag in external_blobs:
                off, _ = external_blobs[tag]
                f.write(struct.pack(endian + "I", off))
            else:
                raise AssertionError(f"tag {tag} not resolved")
        f.write(struct.pack(endian + "I", 0))  # next IFD offset = 0 (none)

        for tag in (258, 33550, 33922, 34735):
            off, blob = external_blobs[tag]
            assert f.tell() == off, f"offset mismatch for tag {tag}: at {f.tell()} expected {off}"
            f.write(blob)

        assert f.tell() == strip_offset
        f.write(pixels)

    return {
        "path": path, "width": width, "height": height,
        "bbox_3857": (x_min, y_min, x_max, y_max),
        "bbox_4326": (lon_min, lat_min, lon_max, lat_max),
        "size_bytes": external_start + cursor - external_start + strip_byte_count,
    }


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-o", "--out", default="data/terrain/geotiff/offline_sample_basemap.tif",
                         help="Output .tif path")
    parser.add_argument("--lon-min", type=float, default=75.0)
    parser.add_argument("--lon-max", type=float, default=79.0)
    parser.add_argument("--lat-min", type=float, default=28.0)
    parser.add_argument("--lat-max", type=float, default=31.0)
    parser.add_argument("--width", type=int, default=512)
    parser.add_argument("--height", type=int, default=384)
    args = parser.parse_args()

    info = build_geotiff(args.out, args.lon_min, args.lon_max, args.lat_min, args.lat_max,
                          args.width, args.height)
    print(info)
