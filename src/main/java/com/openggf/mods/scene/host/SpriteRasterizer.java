package com.openggf.mods.scene.host;

import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingRasterizer;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns one Mega Drive sprite mapping frame into an RGBA {@link SceneSprite}, the way the
 * VDP would draw it: pieces are 8x8 tiles laid out column by column, mirrored per piece,
 * coloured from the piece's palette line, with colour 0 transparent and earlier pieces on
 * top of later ones. Engine-internal.
 */
public final class SpriteRasterizer {
    private SpriteRasterizer() {
    }

    /** {@code tiles} is the art (or the frame's DPLC bank); {@code palette} has 64 ARGB colours. */
    public static SceneSprite rasterize(SpriteMappingFrame frame, Pattern[] tiles, int[] palette, int paletteLine,
            int tileOffset) {
        var pixels = SpriteMappingRasterizer.rasterize(frame, tiles, palette, paletteLine, tileOffset);
        return new SceneSprite(new SceneImage(pixels.width(), pixels.height(), pixels.argb()),
                pixels.originX(), pixels.originY());
    }

    /** The tiles a DPLC frame streams into VRAM, in bank order. */
    static Pattern[] dplcBank(SpriteDplcFrame dplc, Pattern[] art) {
        if (dplc == null) {
            return art;
        }
        List<Pattern> bank = new ArrayList<>();
        for (TileLoadRequest request : dplc.requests()) {
            int dest = request.destinationOffset() >= 0 ? request.destinationOffset() : bank.size();
            for (int i = 0; i < request.count(); i++) {
                int src = request.startTile() + i;
                Pattern tile = src >= 0 && src < art.length ? art[src] : null;
                int slot = dest + i;
                while (bank.size() <= slot) {
                    bank.add(null);
                }
                bank.set(slot, tile);
            }
        }
        return bank.toArray(new Pattern[0]);
    }

    /** Mega Drive colour words to {@code 0xFFRRGGBB}. */
    static int[] colors(byte[] words) {
        int[] out = new int[words.length / 2];
        for (int i = 0; i < out.length; i++) {
            int word = ((words[i * 2] & 0xFF) << 8) | (words[i * 2 + 1] & 0xFF);
            int r = ((word >> 1) & 7) * 255 / 7;
            int g = ((word >> 5) & 7) * 255 / 7;
            int b = ((word >> 9) & 7) * 255 / 7;
            out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return out;
    }
}
