package infinite;

import com.openggf.game.sonic1.scroll.Sonic1ScrollHandlerProvider;
import com.openggf.game.sonic1.titlescreen.Sonic1TitleScreenManager;
import com.openggf.game.titlescreen.SegaPaletteFade;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.PatternDesc;
import com.openggf.level.scroll.ZoneScrollHandler;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * Shows the zone picker's zone behind the Sonic 1 title emblem: that zone's act 1 background
 * art, palette and stock Deform_* parallax, scrolling right at the stock title's 2 px per frame.
 * Green Hill keeps the stock title background (the ROM title already shows it). A newly picked
 * zone fades in from black. Everything is read from the ROM through the act's level data.
 * <p>
 * The title draws this in place of its Plane B only while {@link TitleWordmark} is driving the
 * stock title, so a session without the mod sees the stock title even if the manager is shared.
 */
final class TitleBackground implements Sonic1TitleScreenManager.BackgroundOverride {
    static final int FADE_FRAMES = 12;
    // Tit_MainLoop: addq.w #2,obX(a0) on the background scroll object.
    static final int SCROLL_STEP = 2;
    private static final int VISIBLE_LINES = 224;
    private static final int SCREEN_ROWS = 29;

    private final IntSupplier selected;
    private final IntFunction<Level> levels;
    private final IntUnaryOperator cameraY;
    private final Sonic1ScrollHandlerProvider scroll = new Sonic1ScrollHandlerProvider();
    private final int[] lines = new int[VISIBLE_LINES];
    private final PatternDesc desc = new PatternDesc();
    private boolean active;
    private boolean loaded;
    // The zone whose scroll lines are current, and the zone whose art is in the atlas.
    private int shown = -1;
    private int uploaded = -1;
    private int fade;
    private int cameraX;

    /**
     * {@code levels} returns a zone's act 1 level (null if it cannot be read) and {@code cameraY}
     * the camera Y its background is shown for.
     */
    TitleBackground(IntSupplier selected, IntFunction<Level> levels, IntUnaryOperator cameraY) {
        this.selected = selected;
        this.levels = levels;
        this.cameraY = cameraY;
    }

    /** Set by the title wrapper around the stock title's update, draw and clear-colour calls. */
    void setActive(boolean active) { this.active = active; }

    /** The zone whose background is showing, or -1 for the stock title background. */
    int showing() { return shown > 0 && active ? shown : -1; }

    /** This frame's packed H-scroll lines and the shown zone's background V-scroll. */
    int[] lines() { return lines; }
    int vscroll() { return shown > 0 ? handler(shown).getVscrollFactorBG() & 0xFFFF : 0; }

    void reset() {
        shown = -1;
        fade = 0;
        cameraX = 0;
    }

    private boolean replaces(int zone) { return active && zone > 0 && levels.apply(zone) != null; }

    @Override public void update(int frameCounter) {
        int zone = selected.getAsInt();
        if (!replaces(zone)) { shown = -1; return; }
        cameraX += SCROLL_STEP;
        if (zone != shown) {
            shown = zone;
            fade = 0;
        } else if (fade < FADE_FRAMES) {
            fade++;
        }
        handler(zone).update(lines, cameraX, cameraY.applyAsInt(zone), frameCounter, 0);
    }

    private ZoneScrollHandler handler(int zone) {
        if (!loaded) {
            try {
                scroll.load(null); // The S1 handlers read no ROM data.
            } catch (java.io.IOException impossible) {
                throw new IllegalStateException(impossible);
            }
            loaded = true;
        }
        return scroll.getHandler(zone);
    }

    @Override public boolean draw(GraphicsManager gm, int viewportWidth, SegaPaletteFade.Mode fadeMode, int fadeSteps) {
        int zone = selected.getAsInt();
        if (!replaces(zone)) return false;
        Level level = levels.apply(zone);
        if (zone != shown) {
            // Drawn before the first scroll update (the title's fade-in): seed the lines in place.
            shown = zone;
            fade = 0;
            handler(zone).update(lines, cameraX, cameraY.applyAsInt(zone), 0, 0);
        }
        if (uploaded != zone) {
            int count = Math.min(level.getPatternCount(), Sonic1TitleScreenManager.BACKGROUND_OVERRIDE_PATTERN_LIMIT);
            for (int i = 0; i < count; i++) {
                gm.cachePatternTexture(level.getPattern(i), Sonic1TitleScreenManager.BACKGROUND_OVERRIDE_PATTERN_BASE + i);
            }
            uploaded = zone;
        }
        for (int line = 0; line < 4; line++) {
            gm.cachePaletteTexture(faded(level.getPalette(line), fadeMode, fadeSteps), line);
        }
        gm.beginPatternBatch();
        drawPlane(gm, level, viewportWidth, handler(zone).getVscrollFactorBG() & 0xFFFF);
        gm.flushPatternBatch();
        gm.flushScreenSpace();
        return true;
    }

    @Override public Palette.Color backdrop(SegaPaletteFade.Mode fadeMode, int fadeSteps) {
        int zone = selected.getAsInt();
        if (!replaces(zone)) return null;
        // Levels set the backdrop to palette line 2, colour 0 ($8720), as the title does.
        return faded(levels.apply(zone).getPalette(2), fadeMode, fadeSteps).getColor(0);
    }

    /** The title's own fade wins; otherwise a newly picked zone fades in from black. */
    private Palette faded(Palette palette, SegaPaletteFade.Mode fadeMode, int fadeSteps) {
        if (fadeMode != SegaPaletteFade.Mode.NONE) return SegaPaletteFade.apply(palette, fadeMode, fadeSteps);
        if (fade >= FADE_FRAMES) return palette;
        int steps = (fade + 1) * (SegaPaletteFade.ROM_FADE_FRAMES - 1) / FADE_FRAMES;
        return SegaPaletteFade.apply(palette, SegaPaletteFade.Mode.FROM_BLACK, steps);
    }

    /** Plane B as the VDP shows it: one H-scroll value per 8-pixel row, V-scroll from the handler. */
    private void drawPlane(GraphicsManager gm, Level level, int viewportWidth, int vscroll) {
        int columns = Sonic1TitleScreenManager.bgTileColumns(viewportWidth);
        int subTileY = vscroll & 7;
        for (int row = 0; row < SCREEN_ROWS; row++) {
            int drawY = row * 8 - subTileY;
            int scanline = Math.max(0, drawY);
            if (scanline >= VISIBLE_LINES) break;
            int bgX = -(short) lines[scanline];
            int worldY = (vscroll & ~7) + row * 8;
            for (int column = 0; column < columns; column++) {
                int word = tileWord(level, (bgX & ~7) + column * 8, worldY);
                if (word == 0) continue;
                desc.set(word);
                gm.renderPatternWithId(Sonic1TitleScreenManager.BACKGROUND_OVERRIDE_PATTERN_BASE + desc.getPatternIndex(),
                        desc, column * 8 - (bgX & 7), drawY);
            }
        }
    }

    /** The background layer's pattern word at a pixel, through block and chunk flips; 0 if empty. */
    static int tileWord(Level level, int worldX, int worldY) {
        int blockSize = level.getBlockPixelSize();
        int x = Math.floorMod(worldX, level.getLayerWidthBlocks(1) * blockSize);
        int y = Math.floorMod(worldY, level.getLayerHeightBlocks(1) * blockSize);
        int id = Byte.toUnsignedInt(level.getMap().getValue(1, x / blockSize, y / blockSize));
        if (id == 0 || id >= level.getBlockCount()) return 0;
        var chunkDesc = level.getBlock(id).getChunkDesc(x % blockSize / 16, y % blockSize / 16);
        if (chunkDesc == null || chunkDesc.getChunkIndex() >= level.getChunkCount()) return 0;
        boolean hFlip = chunkDesc.getHFlip(), vFlip = chunkDesc.getVFlip();
        int patternX = (x >> 3) & 1, patternY = (y >> 3) & 1;
        var pattern = level.getChunk(chunkDesc.getChunkIndex())
                .getPatternDesc(hFlip ? 1 - patternX : patternX, vFlip ? 1 - patternY : patternY);
        if (pattern == null) return 0;
        // Chunk flips toggle the pattern's own H (bit 11) and V (bit 12) flips.
        return pattern.get() ^ (hFlip ? 0x0800 : 0) ^ (vFlip ? 0x1000 : 0);
    }
}
