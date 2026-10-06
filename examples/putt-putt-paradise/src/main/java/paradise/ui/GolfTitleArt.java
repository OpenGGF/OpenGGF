package paradise.ui;

import com.openggf.game.sonic2.titlescreen.TitleScreenDataLoader;
import com.openggf.game.sonic2.titlescreen.TitleScreenMappings;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpriteMappingFrame;

import java.util.List;
import java.util.Objects;

/** Remixes the player's ROM title art in memory; no extracted artwork is shipped. */
public final class GolfTitleArt {
    private final int backgroundBase = PatternAtlasRange.RESULTS_SCREENS.base();
    private final int spriteBase = PatternAtlasRange.SPECIAL_STAGE_RESULTS.base();
    private static final float HERO_SCALE = 0.5f;
    private static final int HERO_Y = 16;
    private final GraphicsManager graphics;
    private final TitleScreenDataLoader data = new TitleScreenDataLoader();
    private final List<SpriteMappingFrame> frames = TitleScreenMappings.createFrames();
    private final PatternDesc tile = new PatternDesc();

    public GolfTitleArt(GraphicsManager graphics) { this.graphics = Objects.requireNonNull(graphics); }

    public void initialize() {
        if (!data.loadData()) throw new IllegalStateException("Golf title art requires the Sonic 2 ROM");
        // Course loading replaces the VDP palettes. Re-entering the menu must restore them.
        data.resetCache();
    }

    public void draw(int width, long tick) {
        data.cacheToGpu();
        var sky = data.getBackgroundPalette().getColor(0);
        GolfText.panel(graphics, 0, 0, width, 224, (sky.r & 255) << 16 | (sky.g & 255) << 8 | sky.b & 255, 1);
        int[] map = data.getPlaneBMap();
        int columns = data.getPlaneBWidth();
        int drift = (int) ((tick / 8) % columns);
        graphics.beginPatternBatch();
        for (int y = 0; y < 28; y++) for (int x = 0; x < width / 8; x++) {
            // The ROM's horizon/water band scrolls independently of its upper sky.
            int sourceX = Math.floorMod(x + (y >= 20 ? drift : 0), columns);
            int word = map[y * columns + sourceX];
            if (word == 0) continue;
            tile.set(word);
            graphics.renderPatternWithId(backgroundBase + tile.getPatternIndex(), tile, x * 8, y * 8);
        }
        graphics.flushPatternBatch();

        float left = (width - 320 * HERO_SCALE) / 2;
        emblem(left, 0);
        // Obj0E final poses and hand positions, as used by TitleScreen_SetFinalState.
        sprite(10, left, 160, 104, true);
        sprite(4, left, 72, 32, false);
        sprite(18, left, 136, 24, false);
        emblem(left, 14); // Lower logo/ribbon sits in front of the character bodies.
        sprite(19, left, 141, 81, false);
        sprite(9, left, 193, 65, false);
        sprite(12 + (int) (tick / 8 % 3), left, 24, 80, false);
        sprite(12 + (int) ((tick / 8 + 1) % 3), left, 296, 80, false);
    }

    private void emblem(float left, int firstRow) {
        int[] map = data.getPlaneAMap();
        for (int y = firstRow; y < 23; y++) for (int x = 0; x < data.getPlaneAWidth(); x++) {
            int word = map[y * data.getPlaneAWidth() + x];
            // Palette zero is the stock copyright line, outside this remixed header.
            if (word == 0 || (word >> 13 & 3) != 3) continue;
            tile.set(word);
            graphics.renderPatternWithIdScaled(backgroundBase + tile.getPatternIndex(), tile,
                    left + x * 4, HERO_Y + y * 4, 4, 4);
        }
    }

    private void sprite(int frame, float left, int originX, int originY, boolean priorityOnly) {
        for (var piece : frames.get(frame).pieces()) {
            if (priorityOnly && !piece.priority()) continue;
            for (int x = 0; x < piece.widthTiles(); x++) for (int y = 0; y < piece.heightTiles(); y++) {
                int sourceX = piece.hFlip() ? piece.widthTiles() - 1 - x : x;
                int sourceY = piece.vFlip() ? piece.heightTiles() - 1 - y : y;
                int index = piece.tileIndex() + sourceX * piece.heightTiles() + sourceY;
                tile.set(index | piece.paletteIndex() << 13 | (piece.hFlip() ? 0x800 : 0)
                        | (piece.vFlip() ? 0x1000 : 0) | (piece.priority() ? 0x8000 : 0));
                graphics.renderPatternWithIdScaled(spriteBase + index, tile,
                        left + (originX + piece.xOffset() + x * 8) * HERO_SCALE,
                        HERO_Y + (originY + piece.yOffset() + y * 8) * HERO_SCALE, 4, 4);
            }
        }
    }
}
