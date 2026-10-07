package com.openggf.game.sonic1.scroll;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import java.util.Arrays;

import static com.openggf.level.scroll.M68KMath.*;

/** REV01 Deform_SBZ: four cloud words and three independently moving building bands. */
public final class SwScrlSbz extends AbstractZoneScrollHandler {
    // v_bgscroll_buffer is $200 bytes. Deform_SBZ writes its first 32 words.
    private final short[] scrollBuffer = new short[0x100];
    private final ScrollEffectComposer composer = new ScrollEffectComposer();
    private long bg1X;
    private long bg2X;
    private long bg3X;
    private long bgY;
    private int lastCameraX;
    private int lastCameraY;
    private int activeAct;
    private boolean initialized;

    @Override
    public void init(int actId, int cameraX, int cameraY) {
        // BgScrollSpeed seeds all three X cameras; BgScroll_SBZ (REV01) rounds
        // the Y word down to eight pixels, divides by eight, then adds one.
        bg1X = bg2X = bg3X = (long) (short) cameraX << 16;
        bgY = (long) (((cameraY & 0x7F8) >> 3) + 1) << 16;
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        activeAct = actId;
        initialized = true;
        vscrollFactorBG = (short) (bgY >> 16);
        minScrollOffset = maxScrollOffset = 0;
        Arrays.fill(scrollBuffer, (short) 0);
    }

    @Override
    public void update(int[] horizScrollBuf, int cameraX, int cameraY,
                       int frameCounter, int actId) {
        if (!initialized || activeAct != actId) {
            init(actId, cameraX, cameraY);
        }
        composer.reset();
        resetScrollTracking();
        int dx = cameraX - lastCameraX;
        int dy = cameraY - lastCameraY;
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        bgY += (long) dy * 32 * 256;
        composer.setVscrollFactorBG((short) (bgY >> 16));
        short foreground = negWord(cameraX);

        if (actId != 0) {
            // Deform_SBZ2 is the uniform nonzero-act branch.
            bg1X += (long) dx * 64 * 256;
            composer.fillPackedScrollWords(0, VISIBLE_LINES, foreground,
                    negWord((int) (bg1X >> 16)));
        } else {
            // Deform_SBZ's BGScroll_Block1/2/3: $80/$60/$40 of scrshiftx.
            bg1X += (long) dx * 128 * 256;
            bg2X += (long) dx * 96 * 256;
            bg3X += (long) dx * 64 * 256;
            buildBands(cameraX);
            int y = (short) (bgY >> 16);
            int word = (y & 0x1F0) >> 4;
            int line = 0;
            int count = 16 - (y & 15);
            // BGScroll_X reads sequential words, with a shortened first group.
            // Only the visible 224 entries of the native HScroll tail are used.
            while (line < VISIBLE_LINES) {
                count = Math.min(count, VISIBLE_LINES - line);
                composer.fillPackedScrollWords(line, count, foreground, scrollBuffer[word++]);
                line += count;
                count = 16;
            }
        }
        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    private void buildBands(int cameraX) {
        short quarter = asrWord(negWord(cameraX), 2);
        short difference = (short) (asrWord(quarter, 1) - quarter);
        // DIVS consumes a signed long dividend and returns its word quotient.
        int increment = (short) (((int) difference << 3) / 4) << 12;
        int current = quarter & 0xFFFF;
        for (int i = 0; i < 4; i++) {
            scrollBuffer[i] = (short) current;
            current = Integer.rotateLeft(current, 16);
            current += increment;
            current = Integer.rotateLeft(current, 16);
        }
        Arrays.fill(scrollBuffer, 4, 14, negWord((int) (bg3X >> 16)));
        Arrays.fill(scrollBuffer, 14, 21, negWord((int) (bg2X >> 16)));
        Arrays.fill(scrollBuffer, 21, 32, negWord((int) (bg1X >> 16)));
    }

    @Override
    public int getBgCameraX() {
        // The stateless tilemap cache starts at the leftmost visible band,
        // as GHZ does; this is its source window, not a write to ROM BG state.
        return initialized ? lastCameraX - maxScrollOffset : Integer.MIN_VALUE;
    }

    @Override
    public int getBgPeriodWidth() {
        int viewportWidth = 320;
        try {
            viewportWidth = Math.max(viewportWidth,
                    GameServices.configuration().getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        } catch (RuntimeException ignored) {
            // Isolated scroll tests have no configured gameplay session.
        }
        return requiredBgPeriodWidth(getBgCameraX(),
                initialized ? maxScrollOffset - minScrollOffset : 0, viewportWidth);
    }

    static int requiredBgPeriodWidth(int leftmostSourceX, int bandSpread, int viewportWidth) {
        // LevelManager aligns the cache origin down to a sixteen-pixel tile pair.
        int alignment = leftmostSourceX == Integer.MIN_VALUE ? 0 : Math.floorMod(leftmostSourceX, 16);
        int coverage = bandSpread + Math.max(320, viewportWidth) + alignment;
        int width = 512;
        while (width < coverage) {
            width <<= 1;
        }
        return width;
    }

    @Override
    public Object captureRewindState() {
        return new State(bg1X, bg2X, bg3X, bgY, lastCameraX, lastCameraY, activeAct, initialized,
                vscrollFactorBG, minScrollOffset, maxScrollOffset);
    }

    @Override
    public void restoreRewindState(Object state) {
        if (state instanceof State saved) {
            bg1X = saved.bg1X;
            bg2X = saved.bg2X;
            bg3X = saved.bg3X;
            bgY = saved.bgY;
            lastCameraX = saved.cameraX;
            lastCameraY = saved.cameraY;
            activeAct = saved.act;
            initialized = saved.initialized;
            vscrollFactorBG = saved.vscroll;
            minScrollOffset = saved.minOffset;
            maxScrollOffset = saved.maxOffset;
        }
    }

    private record State(long bg1X, long bg2X, long bg3X, long bgY,
                         int cameraX, int cameraY, int act, boolean initialized,
                         short vscroll, int minOffset, int maxOffset) { }
}
