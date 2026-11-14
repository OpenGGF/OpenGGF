package com.openggf.game.soniccd.scroll;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import static com.openggf.level.scroll.M68KMath.*;

/**
 * Stand-in background scroll until a zone lane ports its stage's scroll routine.
 * Foreground follows the camera exactly. Background X is half the camera X and background
 * Y equals camera Y, which is what Palmtree Panic Act 1 Present's InitLevelScroll sets
 * before the first 3D ramp (Level/Palmtree Panic/Scroll (Act 1 Present).asm:135-152);
 * no per-line deformation is modelled.
 */
public final class CdMinimalScroll extends AbstractZoneScrollHandler {
    private final ScrollEffectComposer composer = new ScrollEffectComposer();
    private int bgCameraX;

    @Override
    public void update(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        resetScrollTracking();
        composer.reset();
        bgCameraX = (cameraX & 0xFFFF) >>> 1;
        composer.setVscrollFactorBG((short) cameraY);
        composer.fillPackedScrollWords(0, VISIBLE_LINES, negWord(cameraX), negWord(bgCameraX));
        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    @Override
    public short getVscrollFactorBG() {
        return vscrollFactorBG;
    }

    @Override
    public int getBgCameraX() {
        return bgCameraX;
    }
}
