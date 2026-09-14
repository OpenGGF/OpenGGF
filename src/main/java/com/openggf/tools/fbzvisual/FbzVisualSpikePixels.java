package com.openggf.tools.fbzvisual;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.Sonic3kSpikeObjectInstance;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.TilemapSamplingDiagnostics;
import com.openggf.level.render.SpritePieceRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Actual upright-spike sprite-atlas pixel evidence; FBZ visual task, 2026-09-14.
 * Source owner buildFbzSpikesSheet aliases level$200-$207 into sheet indices8-15.
 * Active object poses and ROM mapping pieces locate pixels; actual GPU bytes,
 * not CPU sheet contents, are compared with the captured framebuffer.
 */
final class FbzVisualSpikePixels {
    static Map<String, Object> measure(RgbaImage image, byte[] palette) {
        var graphics = GameServices.graphics();
        var render = GameServices.level().getObjectRenderManager();
        var renderer = render.getRenderer(Sonic3kObjectArtKeys.SPIKES);
        var sheet = render.getSheet(Sonic3kObjectArtKeys.SPIKES);
        if (renderer == null || !renderer.isReady() || sheet == null)
            return Map.of("status", "spike-renderer-not-ready");
        int base = renderer.getPatternBase(), width = graphics.getPatternAtlasWidth();
        var atlases = new HashMap<Integer, byte[]>();
        java.util.function.IntFunction<byte[]> atlas = index -> atlases.computeIfAbsent(index,
                i -> TilemapSamplingDiagnostics.readIndexedAtlas(graphics.getPatternAtlasTextureId(i)));
        byte[] packed = new byte[8 * 32];
        for (int tile = 0; tile < 8; tile++) {
            var entry = graphics.getPatternAtlasEntry(base + 8 + tile);
            if (entry == null) throw new IllegalStateException("Missing upright spike atlas entry");
            byte[] bytes = atlas.apply(entry.atlasIndex());
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x += 2) {
                int p = (entry.tileY() * 8 + y) * width + entry.tileX() * 8 + x;
                packed[tile * 32 + y * 4 + x / 2] = (byte) ((bytes[p] & 15) << 4 | bytes[p + 1] & 15);
            }
        }
        int cameraX = GameServices.camera().getX(), cameraY = GameServices.camera().getY();
        FbzVisualSourcePixelMask mask = new FbzVisualSourcePixelMask();
        int[] totals = {0, 0, image.width(), image.height(), 0, 0};
        List<Map<String, Object>> owners = new ArrayList<>();
        for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(object instanceof Sonic3kSpikeObjectInstance spike) || object.isDestroyed()) continue;
            var spawn = spike.getSpawn(); int frame = Math.clamp((spawn.subtype() >>> 4) & 15, 0, 7);
            if (frame >= 4) continue; // ROM sideways branch retains the separate shared art bank.
            boolean hf = (spawn.renderFlags() & 1) != 0, vf = (spawn.renderFlags() & 2) != 0;
            int prior = totals[0];
            for (var piece : sheet.getFrame(frame).pieces()) {
                SpritePieceRenderer.preparePiece(piece, spike.getX(), spike.getY(), base, sheet.getPaletteIndex(),
                        hf, vf, false, prepared -> SpritePieceRenderer.renderPreparedPiece(prepared,
                                (pattern, flipX, flipY, pal, drawX, drawY) -> {
                    if (pattern < base + 8 || pattern >= base + 16) return;
                    var entry = graphics.getPatternAtlasEntry(pattern);
                    byte[] bytes = atlas.apply(entry.atlasIndex());
                    for (int dy = 0; dy < 8; dy++) for (int dx = 0; dx < 8; dx++) {
                        int x = drawX - cameraX + dx, y = drawY - cameraY + dy;
                        if (x < 0 || y < 0 || x >= image.width() || y >= image.height()) continue;
                        int ax = entry.tileX() * 8 + (flipX ? 7 - dx : dx);
                        int ay = entry.tileY() * 8 + (flipY ? 7 - dy : dy);
                        int nibble = bytes[ay * width + ax] & 15;
                        if (nibble == 0) continue;
                        totals[0]++; int pa = (pal * 16 + nibble) * 4;
                        int rgb = (palette[pa] & 255) << 16 | (palette[pa + 1] & 255) << 8 | palette[pa + 2] & 255;
                        mask.add(x, y, 0x200 + pattern - base - 8,
                                flipX ? 7-dx : dx, flipY ? 7-dy : dy,
                                pal * 16 + nibble, (image.argb(x,y) & 0xFFFFFF) == rgb);
                        if ((image.argb(x, y) & 0xFFFFFF) == rgb) {
                            totals[1]++; totals[2] = Math.min(totals[2], x); totals[3] = Math.min(totals[3], y);
                            totals[4] = Math.max(totals[4], x + 1); totals[5] = Math.max(totals[5], y + 1);
                        }
                    }
                }));
            }
            if (totals[0] > prior) owners.add(Map.of("world_x", spike.getX(), "world_y", spike.getY(),
                    "mapping_frame", frame, "hflip", hf, "vflip", vf));
        }
        return Map.of("source_pixel_mask", mask.evidence(palette), "owner_domain", "active-upright-spikes-ROM-mappings-actual-sprite-atlas",
                "gpu_destination_sha256", FbzVisualPrebootVerifier.sha256(packed),
                "opaque_placed_pixels", totals[0], "exact_matched_pixels", totals[1], "owners", owners,
                "matched_bounds", totals[1] == 0 ? List.of() : List.of(totals[2], totals[3], totals[4]-totals[2], totals[5]-totals[3]));
    }
}
