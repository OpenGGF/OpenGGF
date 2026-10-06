package com.openggf.mods.scene.host;

import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
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
final class SpriteRasterizer {
    private SpriteRasterizer() {
    }

    /** {@code tiles} is the art (or the frame's DPLC bank); {@code palette} has 64 ARGB colours. */
    static SceneSprite rasterize(SpriteMappingFrame frame, Pattern[] tiles, int[] palette, int paletteLine,
            int tileOffset) {
        List<SpriteMappingPiece> pieces = frame == null ? List.of() : frame.pieces();
        if (pieces.isEmpty()) {
            return new SceneSprite(new SceneImage(1, 1, new int[1]), 0, 0);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (SpriteMappingPiece p : pieces) {
            minX = Math.min(minX, p.xOffset());
            minY = Math.min(minY, p.yOffset());
            maxX = Math.max(maxX, p.xOffset() + p.widthTiles() * 8);
            maxY = Math.max(maxY, p.yOffset() + p.heightTiles() * 8);
        }
        int width = Math.max(1, maxX - minX);
        int height = Math.max(1, maxY - minY);
        int[] out = new int[width * height];
        // The first piece in a mapping frame has the highest priority, so draw it last.
        for (int i = pieces.size() - 1; i >= 0; i--) {
            SpriteMappingPiece p = pieces.get(i);
            int line = (paletteLine + p.paletteIndex()) & 3;
            for (int col = 0; col < p.widthTiles(); col++) {
                for (int row = 0; row < p.heightTiles(); row++) {
                    int tileIndex = p.tileIndex() - tileOffset + col * p.heightTiles() + row;
                    if (tileIndex < 0 || tileIndex >= tiles.length || tiles[tileIndex] == null) {
                        continue;
                    }
                    Pattern tile = tiles[tileIndex];
                    int destCol = p.hFlip() ? p.widthTiles() - 1 - col : col;
                    int destRow = p.vFlip() ? p.heightTiles() - 1 - row : row;
                    int baseX = p.xOffset() - minX + destCol * 8;
                    int baseY = p.yOffset() - minY + destRow * 8;
                    for (int py = 0; py < 8; py++) {
                        for (int px = 0; px < 8; px++) {
                            int index = tile.getPixel(p.hFlip() ? 7 - px : px, p.vFlip() ? 7 - py : py) & 0x0F;
                            if (index == 0) {
                                continue;
                            }
                            int x = baseX + px;
                            int y = baseY + py;
                            if (x >= 0 && y >= 0 && x < width && y < height) {
                                out[y * width + x] = palette[line * 16 + index];
                            }
                        }
                    }
                }
            }
        }
        return new SceneSprite(new SceneImage(width, height, out), -minX, -minY);
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
