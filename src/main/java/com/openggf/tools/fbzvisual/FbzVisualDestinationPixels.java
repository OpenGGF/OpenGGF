package com.openggf.tools.fbzvisual;

import com.openggf.graphics.RgbaImage;
import java.util.Map;
import java.util.List;

/** Read-only destination-local GPU/foreground pixel oracle from FBZ visual work, 2026-09-14.
 * Inputs are actual uploaded descriptors, lookup, atlas, palette and last-draw uniforms.
 * Sprite owners are explicitly outside this foreground mapping, not silently accepted.
 */
final class FbzVisualDestinationPixels {
    static Map<String, Object> measure(FbzVisualCadenceCapture.Spec spec, RgbaImage image,
                                       Map<String, Object> uniforms, Map<String, byte[]> textures) {
        if (n(uniforms, "PerLineScroll") != 0 || n(uniforms, "PerColumnVScroll") != 0)
            throw new IllegalStateException("Destination oracle requires uniform foreground scroll");
        byte[] descriptors = textures.get("foreground.rgba"), lookup = textures.get("lookup.rgba");
        byte[] atlas = textures.get("atlas.indexed"), palette = textures.get("palette.rgba");
        int tw = n(uniforms, "TilemapWidth"), th = n(uniforms, "TilemapHeight");
        int aw = n(uniforms, "AtlasWidth"), wx = n(uniforms, "WorldOffsetX"), wy = n(uniforms, "WorldOffsetY");
        byte[] packed = new byte[spec.tileCount() * 32];
        for (int t = 0; t < spec.tileCount(); t++) {
            int tile = spec.destinationTile() + t;
            int ax = (lookup[tile * 4] & 255) * 8, ay = (lookup[tile * 4 + 1] & 255) * 8;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x += 2)
                packed[t * 32 + y * 4 + x / 2] = (byte) ((atlas[(ay + y) * aw + ax + x] & 15) << 4
                        | atlas[(ay + y) * aw + ax + x + 1] & 15);
        }
        FbzVisualSourcePixelMask mask = new FbzVisualSourcePixelMask();
        int opaque = 0, matched = 0, x0 = image.width(), y0 = image.height(), x1 = 0, y1 = 0;
        for (int y = 0; y < image.height(); y++) for (int x = 0; x < image.width(); x++) {
            int worldX = x + wx, worldY = y + wy;
            int tx = Math.floorDiv(worldX, 8), ty = Math.floorDiv(worldY, 8);
            if (n(uniforms, "WrapY") == 0 && (ty < 0 || ty >= th)) continue;
            int px = Math.floorMod(tx + n(uniforms, "TilemapRingBaseX"), tw);
            int py = Math.floorMod(ty + n(uniforms, "TilemapRingBaseY"), th);
            int address = (py * tw + px) * 4;
            if ((descriptors[address + 3] & 255) < 128) continue;
            int flags = descriptors[address + 1] & 255, tile = (descriptors[address] & 255) + (flags & 7) * 256;
            if (tile < spec.destinationTile() || tile >= spec.destinationTile() + spec.tileCount()) continue;
            int lx = Math.floorMod(worldX, 8), ly = Math.floorMod(worldY, 8);
            if ((flags & 32) != 0) lx = 7 - lx;
            if ((flags & 64) != 0) ly = 7 - ly;
            int ax = (lookup[tile * 4] & 255) * 8 + lx, ay = (lookup[tile * 4 + 1] & 255) * 8 + ly;
            int colour = atlas[ay * aw + ax] & 15;
            if (colour == 0) continue;
            opaque++;
            int pa = (((flags >>> 3) & 3) * 16 + colour) * 4;
            int rgb = (palette[pa] & 255) << 16 | (palette[pa + 1] & 255) << 8 | palette[pa + 2] & 255;
            mask.add(x, y, tile, lx, ly, ((flags >>> 3) & 3) * 16 + colour, (image.argb(x,y) & 0xFFFFFF) == rgb);
            if ((image.argb(x, y) & 0xFFFFFF) == rgb) {
                matched++; x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x + 1); y1 = Math.max(y1, y + 1);
            }
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>(Map.of("gpu_destination_sha256", FbzVisualPrebootVerifier.sha256(packed),
                "owner_domain", "foreground-name-table-only", "opaque_placed_pixels", opaque,
                "exact_matched_pixels", matched,
                "matched_bounds", matched == 0 ? List.of() : List.of(x0, y0, x1 - x0, y1 - y0),
                "acceptance", "candidate-geometric-mapping-independent-review-required"));
        result.put("source_pixel_mask", mask.evidence(palette));
        if (spec.destinationTile() == 0x200) result.put("sprite_destination_evidence", FbzVisualSpikePixels.measure(image, palette));
        return Map.copyOf(result);
    }
    private static int n(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof Number value)) throw new IllegalStateException("Missing GPU uniform " + key);
        return value.intValue();
    }
}
