package com.openggf.game.sonic1.scroll;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import static com.openggf.level.scroll.M68KMath.*;

/**
 * Uniform S1 background scrolling: quarter-speed X and eighth-speed Y.
 * Final Zone uses Deform_SBZ2 and the REV01 BgScroll_SBZ initialization.
 * Each route owns a separate instance, including fractional camera state.
 */
public final class UniformQuarterSpeedScroll extends AbstractZoneScrollHandler {

    // Persistent BG camera (16.16 fixed point)
    private long bgXPos;
    private long bgYPos;

    private int lastCameraX;
    private int lastCameraY;
    private boolean initialized = false;

    private final ScrollEffectComposer composer = new ScrollEffectComposer();

    public void init(int cameraX, int cameraY) {
        // BgScrollSpeed default: bgscreenposx = screenposx
        bgXPos = (long) cameraX << 16;
        // REV01 BgScroll_SBZ: andi.w #$7F8; asr.w #3; addq.w #1.
        // Revision 0 instead uses the unoffset one-eighth camera Y formula.
        int bgYInit = ((cameraY & 0x7F8) >> 3) + 1;
        bgYPos = (long) bgYInit << 16;
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        initialized = true;
    }

    @Override
    public void init(int actId, int cameraX, int cameraY) {
        init(cameraX, cameraY);
    }

    @Override
    public void update(int[] horizScrollBuf,
            int cameraX,
            int cameraY,
            int frameCounter,
            int actId) {
        if (!initialized) {
            init(cameraX, cameraY);
        }

        resetScrollTracking();
        composer.reset();

        int deltaX = cameraX - lastCameraX;
        int deltaY = cameraY - lastCameraY;
        lastCameraX = cameraX;
        lastCameraY = cameraY;

        // Deform_SBZ2: d4 = scrshiftx << 6 = deltaX * 64 * 256
        bgXPos += (long) deltaX * 64 * 256;

        // Deform_SBZ2: d5 = scrshifty << 5 = deltaY * 32 * 256
        bgYPos += (long) deltaY * 32 * 256;

        int bgX = (int) (bgXPos >> 16);
        int bgY = (int) (bgYPos >> 16);

        composer.setVscrollFactorBG((short) bgY);

        // Uniform h-scroll (Deform_SBZ2: all 224 lines same)
        short fgScroll = negWord(cameraX);
        short bgScroll = negWord(bgX);

        composer.fillPackedScrollWords(0, VISIBLE_LINES, fgScroll, bgScroll);
        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    @Override
    public int getBgCameraX() {
        return (int) (bgXPos >> 16);
    }
}
