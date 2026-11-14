package com.openggf.game.soniccd.program;

import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Palette;
import com.openggf.level.PaletteDisplayColor;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;

import java.util.BitSet;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Draws a program screen that the CD's non-level programs describe the way the hardware shows
 * them: a tile store, two 64x32 planes with per-line horizontal scroll, a 64-colour palette and a
 * sprite list, layered by priority (plane B, plane A and sprites low, then the same three high).
 * It is the shared presentation for every native program under {@code game/soniccd}; each program
 * owns its state and calls {@link #draw}. Pattern ids live in the results-screen atlas range,
 * which no level-free program shares.
 */
public final class CdProgramScreenRenderer {
    public record Sprite(int x, int y, int size, int tile) { }

    /**
     * One frame of program screen state. Tiles are 32-byte 4bpp patterns indexed by VRAM tile.
     * {@code vscrollA}/{@code vscrollB} are the full-screen vertical scroll words (VSRAM 0 and 2):
     * screen line {@code l} shows plane line {@code (l + vscroll) mod 256} of a 32-row plane.
     */
    public record Frame(byte[] tiles, BitSet dirtyTiles, int[] planeA, int[] planeB, int[] palette,
                        IntUnaryOperator scrollA, IntUnaryOperator scrollB, List<Sprite> sprites,
                        int width, boolean display, int vscrollA, int vscrollB) {
        public Frame(byte[] tiles, BitSet dirtyTiles, int[] planeA, int[] planeB, int[] palette,
                     IntUnaryOperator scrollA, IntUnaryOperator scrollB, List<Sprite> sprites,
                     int width, boolean display) {
            this(tiles, dirtyTiles, planeA, planeB, palette, scrollA, scrollB, sprites, width, display, 0, 0);
        }
    }

    private static final int BASE = PatternAtlasRange.RESULTS_SCREENS.base();
    private static final int TILES = 0x800;
    private final BitSet cached = new BitSet(TILES);
    private final Pattern scratch = new Pattern();
    private final byte[] tile = new byte[32];

    /** Forget uploaded tiles (a new program reuses the atlas range). */
    public void reset() { cached.clear(); }

    public void setClearColor(Frame frame) {
        GraphicsManager gm = GameServices.graphics();
        if (gm.isHeadlessMode()) return;
        Palette line = new Palette();
        line.fromSegaFormat(lineBytes(frame.palette(), 0));
        Palette.Color black = new Palette.Color();
        PaletteDisplayColor.glClear(frame.display() ? line.colors[0] : black, gm);
    }

    public void draw(Frame frame) {
        GraphicsManager gm = GameServices.graphics();
        if (gm.isHeadlessMode()) return;
        upload(gm, frame);
        if (!frame.display()) return;
        int offset = (Math.max(320, gm.getProjectionWidth()) - frame.width()) / 2;
        for (int priority = 0; priority < 2; priority++) {
            plane(gm, frame, frame.planeB(), frame.scrollB(), frame.vscrollB(), priority == 1, offset);
            plane(gm, frame, frame.planeA(), frame.scrollA(), frame.vscrollA(), priority == 1, offset);
            sprites(gm, frame, priority == 1, offset);
        }
        gm.disableScissor();
    }

    private void upload(GraphicsManager gm, Frame frame) {
        BitSet dirty = frame.dirtyTiles();
        for (int t = 0; t < TILES; t++) {
            if (cached.get(t) && !dirty.get(t)) continue;
            System.arraycopy(frame.tiles(), t * 32, tile, 0, 32);
            scratch.fromSegaFormat(tile);
            if (cached.get(t)) gm.updatePatternTexture(scratch, BASE + t);
            else gm.cachePatternTexture(scratch, BASE + t);
            cached.set(t);
        }
        dirty.clear();
        for (int line = 0; line < 4; line++) {
            Palette palette = new Palette();
            palette.fromSegaFormat(lineBytes(frame.palette(), line));
            gm.cachePaletteTexture(palette, line);
        }
    }

    private static byte[] lineBytes(int[] cram, int line) {
        byte[] bytes = new byte[32];
        for (int i = 0; i < 16; i++) {
            int word = cram[line * 16 + i];
            bytes[2 * i] = (byte) (word >> 8);
            bytes[2 * i + 1] = (byte) word;
        }
        return bytes;
    }

    /** A 64x32 plane, in runs of lines that share one horizontal scroll value. */
    private void plane(GraphicsManager gm, Frame frame, int[] map, IntUnaryOperator scroll, int vscroll,
                       boolean high, int offset) {
        PatternDesc desc = new PatternDesc();
        int v = vscroll & 0xFF;
        int top = 0;
        while (top < 224) {
            int value = scroll.applyAsInt(top) & 0x1FF;
            int bottom = top;
            while (bottom + 1 < 224 && (scroll.applyAsInt(bottom + 1) & 0x1FF) == value) bottom++;
            clip(gm, frame, offset, top, bottom);
            gm.beginPatternBatch();
            for (int screenRow = (top + (v & 7)) >> 3; screenRow <= (bottom + (v & 7)) >> 3; screenRow++) {
                // Screen line l shows plane line (l + vscroll) mod 256.
                int row = (screenRow + (v >> 3)) & 31;
                int screenY = screenRow * 8 - (v & 7);
                for (int column = 0; column < 64; column++) {
                    int word = map[row * 64 + column];
                    if ((word & 0x7FF) == 0 || ((word & 0x8000) != 0) != high) continue;
                    // A positive scroll moves the plane right: screen x = plane x + scroll, modulo 512.
                    int screenX = (column * 8 + value) & 0x1FF;
                    if (screenX > 0x1F8) screenX -= 0x200;
                    if (screenX >= frame.width()) continue;
                    desc.set(word);
                    gm.renderPatternWithId(BASE + (word & 0x7FF), desc, offset + screenX, screenY);
                }
            }
            gm.flushPatternBatch();
            gm.flushScreenSpace();
            top = bottom + 1;
        }
    }

    /** Sprites in reverse list order, so the first entry is drawn last and lands on top. */
    private void sprites(GraphicsManager gm, Frame frame, boolean high, int offset) {
        clip(gm, frame, offset, 0, 223);
        gm.beginPatternBatch();
        PatternDesc desc = new PatternDesc();
        List<Sprite> list = frame.sprites();
        for (int i = list.size() - 1; i >= 0; i--) {
            Sprite sprite = list.get(i);
            if (((sprite.tile() & 0x8000) != 0) != high) continue;
            int w = ((sprite.size() >> 2) & 3) + 1, h = (sprite.size() & 3) + 1;
            boolean hFlip = (sprite.tile() & 0x800) != 0, vFlip = (sprite.tile() & 0x1000) != 0;
            for (int cx = 0; cx < w; cx++) {
                for (int cy = 0; cy < h; cy++) {
                    int index = ((sprite.tile() & 0x7FF) + cx * h + cy) & 0x7FF;
                    desc.set((sprite.tile() & 0xF800) | index);
                    int dx = hFlip ? w - 1 - cx : cx, dy = vFlip ? h - 1 - cy : cy;
                    gm.renderPatternWithId(BASE + index, desc, offset + sprite.x() + dx * 8, sprite.y() + dy * 8);
                }
            }
        }
        gm.flushPatternBatch();
        gm.flushScreenSpace();
    }

    /** Scissor to the program's active display lines [top, bottom], in the window's pixels. */
    private static void clip(GraphicsManager gm, Frame frame, int offset, int top, int bottom) {
        int projection = Math.max(320, gm.getProjectionWidth());
        float sx = (float) gm.getViewportWidth() / projection;
        float sy = (float) gm.getViewportHeight() / 224;
        int x = gm.getViewportX() + Math.round(offset * sx);
        int y = gm.getViewportY() + Math.round((223 - bottom) * sy);
        gm.enableScissor(x, y, Math.round(frame.width() * sx), Math.max(1, Math.round((bottom - top + 1) * sy)));
    }
}
