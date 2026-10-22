package com.openggf.graphics;

import com.openggf.game.GameId;
import com.openggf.level.Palette;
import com.openggf.net.identity.PlayerAppearance;

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
     * Reserves and composes private rows for {@code look}. Returns null for the stock look, a
     * missing stock line, or when every bounded palette bank is already reserved (the caller then
     * draws with the shared rows, as before).
     *
     * @param sourceBase    first texture row the block mirrors: 0 for the level's lines, or the
     *                      base of the sprite's existing render context
     * @param characterLine logical line (0-3) holding the character's colours
     * @param stock         the character's stock 16-colour line, as the ROM loads it
     * @param liveSource    true when the source character line is this character's own line, so
     *                      runtime changes to it show through; false composes from {@code stock}
     *                      (another character's colours occupy the shared line)
     */
    public static Block acquire(GraphicsManager graphics, GameId gameId, int sourceBase, int characterLine,
                                PaletteView stock, boolean liveSource, PlayerAppearance look) {
        engineCaller();
        Objects.requireNonNull(graphics, "graphics");
        if (look == null || look.isStock() || stock == null) return null;
        if (characterLine < 0 || characterLine >= RenderContext.LINES_PER_CONTEXT || sourceBase < 0)
            throw new IllegalArgumentException("character line must be 0..3 and the source base non-negative");
        Block block = new Block(graphics, gameId, sourceBase, characterLine, stock, liveSource, look);
        if (!graphics.appearancePaletteRows().admit(block)) return null;
        return block;
    }

    /** One sprite's private palette rows. */
    public static final class Block {
        private final GraphicsManager graphics;
        private final GameId gameId;
        private final int sourceBase;
        private final int characterLine;
        private final Palette stock = new Palette();
        private final boolean liveSource;
        private final PlayerAppearance look;
        private final boolean[] overridden = new boolean[16];
        private final Palette.Color[] overrides = new Palette.Color[16];
        private final Palette[] rows = newLines();
        private final Palette[] liveRows = newLines();
        private final Palette[] waterRows = newLines();
        private Object token;
        private int base = -1;
        private RenderContext context;
        private boolean released;

        private Block(GraphicsManager graphics, GameId gameId, int sourceBase, int characterLine,
                      PaletteView stock, boolean liveSource, PlayerAppearance look) {
            this.graphics = graphics;
            this.gameId = gameId;
            this.sourceBase = sourceBase;
            this.characterLine = characterLine;
            this.liveSource = liveSource;
            this.look = look;
            copy(stock, this.stock);
            for (PlayerAppearance.Entry entry : look.palette()) {
                Palette.Color colour = new Palette.Color();
                colour.fromSegaFormat(entry.colour());
                overrides[entry.index()] = colour;
                overridden[entry.index()] = true;
            }
        }

        public PlayerAppearance look() {
            return look;
        }

        /**
         * The context to draw with, keeping the rows resident: after a palette reset (a game
         * change) the block reserves and composes fresh rows. Null once released, or when no
         * bank is free.
         */
        public RenderContext context() {
            if (released) return null;
            if (!resident() && !graphics.appearancePaletteRows().admit(this)) return null;
            return context;
        }

        /** Returns the rows to the bank; later {@link #context()} calls answer null. */
        public void release() {
            if (released) return;
            released = true;
            graphics.appearancePaletteRows().remove(this);
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
            Palette live = liveRows[line];
            if (source != null) copy(source, live);
            else if (line == characterLine) copy(stock, live);
            else clear(live);
            Palette out = rows[line];
            boolean changed = false;
            for (int i = 0; i < 16; i++) {
                Palette.Color colour = live.getColor(i);
                if (line == characterLine && overridden[i]) {
                    if (!liveSource) colour = overrides[i];
                    else if (sameColour(live, stock, i)) colour = overrides[i];
                } else if (line == characterLine && !liveSource) {
                    colour = stock.getColor(i);
                }
                changed |= set(out, i, colour.r, colour.g, colour.b);
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
            Palette out = waterRows[line];
            PaletteView waterLine = sourceBase == 0 && water != null && line < water.length ? water[line] : null;
            for (int i = 0; i < 16; i++) {
                Palette.Color composed = rows[line].getColor(i);
                if (waterLine == null || line == characterLine && !liveSource) {
                    shift(out, i, composed, ratio);
                } else if (line == characterLine && overridden[i] && sameColour(liveRows[line], stock, i)) {
                    // The stock entry's own water colour fixes the per-channel shift where it is defined.
                    perEntryShift(out, i, overrides[i], liveRows[line], waterLine, ratio);
                } else {
                    set(out, i, waterLine.red(i), waterLine.green(i), waterLine.blue(i));
                }
            }
            return out;
        }

        private static void perEntryShift(Palette out, int i, Palette.Color override, PaletteView live,
                                          PaletteView water, int[] ratio) {
            int r = channelShift(override.r, live.red(i), water.red(i), ratio[0]);
            int g = channelShift(override.g, live.green(i), water.green(i), ratio[1]);
            int b = channelShift(override.b, live.blue(i), water.blue(i), ratio[2]);
            set(out, i, (byte) r, (byte) g, (byte) b);
        }

        private static int channelShift(byte value, byte live, byte water, int globalRatio) {
            int v = Byte.toUnsignedInt(value);
            int l = Byte.toUnsignedInt(live);
            int ratio = l > 0 ? Byte.toUnsignedInt(water) * 256 / l : globalRatio;
            return Math.min(255, v * ratio / 256);
        }

        private static void shift(Palette out, int i, Palette.Color colour, int[] ratio) {
            set(out, i, (byte) Math.min(255, Byte.toUnsignedInt(colour.r) * ratio[0] / 256),
                    (byte) Math.min(255, Byte.toUnsignedInt(colour.g) * ratio[1] / 256),
                    (byte) Math.min(255, Byte.toUnsignedInt(colour.b) * ratio[2] / 256));
        }

        private void bind(Object token, int base) {
            this.token = token;
            this.base = base;
            this.context = RenderContext.privateBlock(gameId, base);
        }

        private void unbind() {
            token = null;
            base = -1;
            context = null;
        }
    }

    /** The per-render-host registry behind every live block; driven by {@link GraphicsManager}. */
    static final class Rows {
        private final GraphicsManager graphics;
        private final List<Block> blocks = new ArrayList<>();
        private final int[] ratio = new int[3];

        Rows(GraphicsManager graphics) {
            this.graphics = graphics;
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
                block.compose(line, graphics.cachedPaletteLine(block.sourceBase + line));
                graphics.uploadAppearanceRow(block.rows[line], base + line, block.sourceBase + line);
            }
            graphics.appearanceRowsChanged();
            return true;
        }

        void remove(Block block) {
            blocks.remove(block);
            if (block.resident()) {
                for (int line = 0; line < RenderContext.LINES_PER_CONTEXT; line++) {
                    graphics.uploadAppearanceRow(ScopedUnderwaterBasis.blankLine(), block.base + line, block.base + line);
                }
                RenderContext.releaseScopedPaletteBank(block.token);
                graphics.appearanceRowsChanged();
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
                graphics.uploadAppearanceRow(block.rows[line], block.base + line, row);
            }
            if (changed) graphics.appearanceUnderwaterChanged();
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
                if (r + g + b == 0) continue;
                nr += r; ng += g; nb += b;
                wr += Byte.toUnsignedInt(water.red(i));
                wg += Byte.toUnsignedInt(water.green(i));
                wb += Byte.toUnsignedInt(water.blue(i));
            }
            if (nr > 0) out[0] = (int) (wr * 256 / nr);
            if (ng > 0) out[1] = (int) (wg * 256 / ng);
            if (nb > 0) out[2] = (int) (wb * 256 / nb);
        }
    }

    private static Palette[] newLines() {
        Palette[] lines = new Palette[RenderContext.LINES_PER_CONTEXT];
        for (int i = 0; i < lines.length; i++) lines[i] = new Palette();
        return lines;
    }

    private static boolean sameColour(PaletteView a, PaletteView b, int i) {
        return a.red(i) == b.red(i) && a.green(i) == b.green(i) && a.blue(i) == b.blue(i);
    }

    private static void copy(PaletteView from, Palette to) {
        for (int i = 0; i < 16; i++) set(to, i, from.red(i), from.green(i), from.blue(i));
    }

    private static void clear(Palette palette) {
        for (int i = 0; i < 16; i++) set(palette, i, (byte) 0, (byte) 0, (byte) 0);
    }

    private static boolean set(Palette palette, int i, byte r, byte g, byte b) {
        Palette.Color colour = palette.getColor(i);
        if (colour.r == r && colour.g == g && colour.b == b) return false;
        colour.r = r;
        colour.g = g;
        colour.b = b;
        return true;
    }
}
