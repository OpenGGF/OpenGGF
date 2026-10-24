package com.openggf.graphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Engine-only private palette rows that draw one sprite in a player's chosen look.
 *
 * <p>A {@link Block} owns four rows of the palette texture (one {@link RenderContext} block,
 * inside the bounded 64-row bank range). Each row is composed from a <em>source</em> row: the
 * level's own lines 0-3, or the rows of the sprite's existing context. On the character line,
 * every overridden entry replaces the live colour only while that live colour still equals the
 * stock colour, so runtime writes such as Super/Hyper palette cycles stay visible; the other
 * lines are copied unchanged. Rows recompose whenever their source row is uploaded (palette
 * writes, rewind restores, fades and colour-profile changes), and the underwater texture gets a
 * matching row derived from the zone's water palette. The rows are derived GPU state: nothing
 * here is captured by rewind, and the rings, HUD and objects that share line 0 never see them.
 */
public final class AppearancePalettes {
    private AppearancePalettes() { }

    private static void engineCaller() {
        var caller = com.openggf.util.EngineCallerAccess.callerOutside(AppearancePalettes.class);
        if (caller.getClassLoader() != AppearancePalettes.class.getClassLoader())
            throw new SecurityException("Appearance palettes belong to the engine renderer");
    }

    /**
     * Reserves and composes private rows for a non-stock look. Returns null for a missing stock
     * line, or when every bounded palette bank is already reserved (the caller then draws with
     * the shared rows, as before). The caller keeps the stock look on the shared rows.
     *
     * <p>The look arrives as plain colours so this renderer stays independent of the level,
     * game and network layers: callers convert a player's look into {@code overrides}.
     *
     * @param source        the sprite's existing render context, whose rows the block mirrors and
     *                      whose game it draws for; null for the level's own lines 0-3
     * @param characterLine logical line (0-3) holding the character's colours
     * @param stock         the character's stock 16-colour line, as the ROM loads it
     * @param liveSource    true when the source character line is this character's own line, so
     *                      runtime changes to it show through; false composes from {@code stock}
     *                      (another character's colours occupy the shared line)
     * @param overrides     replacement colours, read only at the indices in {@code overrideMask}
     * @param overrideMask  bit {@code i} set when entry {@code i} of the character line is replaced;
     *                      zero keeps every stock colour (a look with only a base colour)
     */
    public static Block acquire(GraphicsManager graphics, RenderContext source, int characterLine,
                                PaletteView stock, boolean liveSource, PaletteView overrides, int overrideMask) {
        engineCaller();
        Objects.requireNonNull(graphics, "graphics");
        if (stock == null) return null;
        if (overrideMask != 0) Objects.requireNonNull(overrides, "overrides");
        if (characterLine < 0 || characterLine >= RenderContext.LINES_PER_CONTEXT)
            throw new IllegalArgumentException("character line must be 0..3");
        if ((overrideMask & ~0xFFFF) != 0) throw new IllegalArgumentException("override mask covers entries 0..15");
        Block block = new Block(graphics, source, characterLine, stock, liveSource, overrides, overrideMask);
        if (!graphics.paletteTextures().appearancePaletteRows().admit(block)) return null;
        return block;
    }

    /** One sprite's private palette rows. */
    public static final class Block {
        private final GraphicsManager graphics;
        private final RenderContext source;
        private final int sourceBase;
        private final int characterLine;
        private final Line stock = new Line();
        private final boolean liveSource;
        private final boolean[] overridden = new boolean[16];
        private final Line overrides = new Line();
        private final Line[] rows = newLines();
        private final Line[] liveRows = newLines();
        private final Line[] waterRows = newLines();
        private Object token;
        private int base = -1;
        private RenderContext context;
        private boolean released;

        private Block(GraphicsManager graphics, RenderContext source, int characterLine,
                      PaletteView stock, boolean liveSource, PaletteView overrides, int overrideMask) {
            this.graphics = graphics;
            this.source = source;
            this.sourceBase = source == null ? 0 : source.getPaletteLineBase();
            this.characterLine = characterLine;
            this.liveSource = liveSource;
            copy(stock, this.stock);
            for (int i = 0; i < 16; i++) {
                if ((overrideMask & (1 << i)) == 0) continue;
                overridden[i] = true;
                set(this.overrides, i, overrides.red(i), overrides.green(i), overrides.blue(i));
            }
        }

        /**
         * The context to draw with, keeping the rows resident: after a palette reset (a game
         * change) the block reserves and composes fresh rows. Null once released, or when no
         * bank is free.
         */
        public RenderContext context() {
            if (released) return null;
            if (!resident() && !graphics.paletteTextures().appearancePaletteRows().admit(this)) return null;
            return context;
        }

        /** Returns the rows to the bank; later {@link #context()} calls answer null. */
        public void release() {
            if (released) return;
            released = true;
            graphics.paletteTextures().appearancePaletteRows().remove(this);
        }

        /** Composed colour of {@code index} on logical line {@code line}, as last uploaded. */
        public PaletteView composedLine(int line) {
            return rows[line];
        }

        boolean resident() {
            return base >= 0 && RenderContext.scopedPaletteBankValid(token, base);
        }

        boolean covers(int row) {
            return base >= 0 && row >= base && row < base + RenderContext.LINES_PER_CONTEXT;
        }

        /** Recomposes logical {@code line} from its source; true when the composed colours changed. */
        boolean compose(int line, PaletteView source) {
            Line live = liveRows[line];
            if (source != null) copy(source, live);
            else if (line == characterLine) copy(stock, live);
            else clear(live);
            Line out = rows[line];
            boolean changed = false;
            for (int i = 0; i < 16; i++) {
                PaletteView colour = live;
                if (line == characterLine && overridden[i]) {
                    if (!liveSource) colour = overrides;
                    else if (sameColour(live, stock, i)) colour = overrides;
                } else if (line == characterLine && !liveSource) {
                    colour = stock;
                }
                changed |= set(out, i, colour.red(i), colour.green(i), colour.blue(i));
            }
            return changed;
        }

        /**
         * The underwater row for logical {@code line}. With the level's own water lines the
         * stock entries take the zone's water colours and each override is shifted the way
         * the water palette shifts that entry; otherwise the whole row takes the water palette's
         * average shift (as donor rows do).
         */
        PaletteView underwater(int line, PaletteView[] water, int[] ratio) {
            Line out = waterRows[line];
            PaletteView waterLine = sourceBase == 0 && water != null && line < water.length ? water[line] : null;
            for (int i = 0; i < 16; i++) {
                if (waterLine == null || line == characterLine && !liveSource) {
                    shift(out, i, rows[line], ratio);
                } else if (line == characterLine && overridden[i] && sameColour(liveRows[line], stock, i)) {
                    // The stock entry's own water colour fixes the per-channel shift where it is defined.
                    perEntryShift(out, i, overrides, liveRows[line], waterLine, ratio);
                } else {
                    set(out, i, waterLine.red(i), waterLine.green(i), waterLine.blue(i));
                }
            }
            return out;
        }

        private static void perEntryShift(Line out, int i, PaletteView override, PaletteView live,
                                          PaletteView water, int[] ratio) {
            int r = channelShift(override.red(i), live.red(i), water.red(i), ratio[0]);
            int g = channelShift(override.green(i), live.green(i), water.green(i), ratio[1]);
            int b = channelShift(override.blue(i), live.blue(i), water.blue(i), ratio[2]);
            set(out, i, (byte) r, (byte) g, (byte) b);
        }

        private static int channelShift(byte value, byte live, byte water, int globalRatio) {
            int v = Byte.toUnsignedInt(value);
            int l = Byte.toUnsignedInt(live);
            int ratio = l > 0 ? UnderwaterPaletteTransform.ratioX256(l, Byte.toUnsignedInt(water)) : globalRatio;
            return UnderwaterPaletteTransform.scale(v, ratio);
        }

        private static void shift(Line out, int i, PaletteView colour, int[] ratio) {
            set(out, i, (byte) UnderwaterPaletteTransform.scale(Byte.toUnsignedInt(colour.red(i)), ratio[0]),
                    (byte) UnderwaterPaletteTransform.scale(Byte.toUnsignedInt(colour.green(i)), ratio[1]),
                    (byte) UnderwaterPaletteTransform.scale(Byte.toUnsignedInt(colour.blue(i)), ratio[2]));
        }

        private void bind(Object token, int base) {
            this.token = token;
            this.base = base;
            this.context = RenderContext.privateBlock(source, base);
        }

        private void unbind() {
            token = null;
            base = -1;
            context = null;
        }
    }

    /** The per-render-host registry behind every live block; driven by {@link GraphicsManager}. */
    static final class Rows {
        private final PaletteTextureOwner palettes;
        private final List<Block> blocks = new ArrayList<>();
        private final int[] ratio = new int[3];

        Rows(PaletteTextureOwner palettes) {
            this.palettes = palettes;
        }

        boolean isEmpty() {
            return blocks.isEmpty();
        }

        /** Reserves rows for {@code block} (new, or after a reset) and composes every line. */
        boolean admit(Block block) {
            Object token = new Object();
            int base;
            try {
                base = RenderContext.reserveScopedPaletteBank(token);
            } catch (IllegalStateException full) {
                // Every bounded bank is in use: the sprite keeps the shared rows.
                return false;
            }
            block.bind(token, base);
            if (!blocks.contains(block)) blocks.add(block);
            for (int line = 0; line < RenderContext.LINES_PER_CONTEXT; line++) {
                block.compose(line, palettes.cachedPaletteLine(block.sourceBase + line));
                palettes.uploadAppearanceRow(block.rows[line], base + line, block.sourceBase + line);
            }
            palettes.appearanceRowsChanged();
            return true;
        }

        void remove(Block block) {
            blocks.remove(block);
            if (block.resident()) {
                for (int line = 0; line < RenderContext.LINES_PER_CONTEXT; line++) {
                    palettes.uploadAppearanceRow(ScopedUnderwaterBasis.blankLine(), block.base + line, block.base + line);
                }
                RenderContext.releaseScopedPaletteBank(block.token);
                palettes.appearanceRowsChanged();
            }
            block.unbind();
        }

        /** A source row reached the texture: recompose and upload every block that mirrors it. */
        void sourceUploaded(int row, PaletteView palette) {
            if (blocks.isEmpty() || palette == null) return;
            boolean changed = false;
            for (int index = 0; index < blocks.size(); index++) {
                Block block = blocks.get(index);
                int line = row - block.sourceBase;
                if (line < 0 || line >= RenderContext.LINES_PER_CONTEXT || block.covers(row) || !block.resident()) {
                    continue;
                }
                changed |= block.compose(line, palette);
                palettes.uploadAppearanceRow(block.rows[line], block.base + line, row);
            }
            if (changed) palettes.appearanceUnderwaterChanged();
        }

        /** The row a block contributes to the underwater texture, or null when {@code row} is not one. */
        PaletteView underwaterRow(int row, PaletteView[] water, PaletteView normalLine0, PaletteView waterLine0) {
            for (Block block : blocks) {
                if (!block.covers(row) || !block.resident()) continue;
                waterRatio(normalLine0, waterLine0, ratio);
                return block.underwater(row - block.base, water, ratio);
            }
            return null;
        }

        /** The row whose palette fade a texture row follows: a block row fades with its source. */
        int fadeLine(int row) {
            for (Block block : blocks) {
                if (block.covers(row)) return block.sourceBase + row - block.base;
            }
            return row;
        }

        /** Palette textures were discarded: every block reserves fresh rows on its next use. */
        void invalidateAll() {
            for (Block block : blocks) {
                if (block.resident()) RenderContext.releaseScopedPaletteBank(block.token);
                block.unbind();
            }
        }

        /** {@link RenderContext#deriveUnderwaterPalette}'s average per-channel water shift, x256. */
        static void waterRatio(PaletteView normal, PaletteView water, int[] out) {
            out[0] = out[1] = out[2] = 256;
            if (normal == null || water == null) return;
            long nr = 0, ng = 0, nb = 0, wr = 0, wg = 0, wb = 0;
            for (int i = 1; i < 16; i++) {
                int r = Byte.toUnsignedInt(normal.red(i));
                int g = Byte.toUnsignedInt(normal.green(i));
                int b = Byte.toUnsignedInt(normal.blue(i));
                if (!UnderwaterPaletteTransform.eligible(r, g, b)) continue;
                nr += r; ng += g; nb += b;
                wr += Byte.toUnsignedInt(water.red(i));
                wg += Byte.toUnsignedInt(water.green(i));
                wb += Byte.toUnsignedInt(water.blue(i));
            }
            out[0] = UnderwaterPaletteTransform.ratioX256(nr, wr);
            out[1] = UnderwaterPaletteTransform.ratioX256(ng, wg);
            out[2] = UnderwaterPaletteTransform.ratioX256(nb, wb);
        }
    }

    /** A mutable 16-colour line owned by this renderer (8-bit RGB, as the palette texture holds). */
    private static final class Line implements PaletteView {
        private final byte[] red = new byte[16];
        private final byte[] green = new byte[16];
        private final byte[] blue = new byte[16];

        @Override public byte red(int colorIndex) { return red[colorIndex]; }
        @Override public byte green(int colorIndex) { return green[colorIndex]; }
        @Override public byte blue(int colorIndex) { return blue[colorIndex]; }
    }

    private static Line[] newLines() {
        Line[] lines = new Line[RenderContext.LINES_PER_CONTEXT];
        for (int i = 0; i < lines.length; i++) lines[i] = new Line();
        return lines;
    }

    private static boolean sameColour(PaletteView a, PaletteView b, int i) {
        return a.red(i) == b.red(i) && a.green(i) == b.green(i) && a.blue(i) == b.blue(i);
    }

    private static void copy(PaletteView from, Line to) {
        for (int i = 0; i < 16; i++) set(to, i, from.red(i), from.green(i), from.blue(i));
    }

    private static void clear(Line palette) {
        for (int i = 0; i < 16; i++) set(palette, i, (byte) 0, (byte) 0, (byte) 0);
    }

    private static boolean set(Line line, int i, byte r, byte g, byte b) {
        if (line.red[i] == r && line.green[i] == g && line.blue[i] == b) return false;
        line.red[i] = r;
        line.green[i] = g;
        line.blue[i] = b;
        return true;
    }
}
