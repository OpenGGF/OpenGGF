package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.negWord;

/** {@code sub_5A508}/{@code sub_5A76C}, Death Egg's {@code $1700} deformation. */
public final class SwScrlDez3 extends AbstractZoneScrollHandler {
    private final ScrollEffectComposer composer = new ScrollEffectComposer();
    private int bgCameraX;

    @Override
    public void update(int[] buffer, int cameraX, int cameraY, int frameCounter, int actId) {
        resetScrollTracking();
        composer.reset();
        S3kDezZoneRuntimeState state = GameServices.hasRuntime()
                ? GameServices.zoneRuntimeRegistry().currentAs(S3kDezZoneRuntimeState.class).orElse(null)
                : null;
        int bossX = state == null ? 0x3C0 : state.act3BackgroundWord(0x02);
        int bossY = state == null ? 0x0F8 : state.act3BackgroundWord(0x04);
        int arenaOffset = state == null ? 0x6C0 : state.act3BackgroundWord(0x00);
        bgCameraX = (short) (cameraX - bossX + arenaOffset);
        int bgCameraY = (short) (cameraY - bossY + 0x180);
        short fg = negWord(cameraX & 0x1FF);
        short bg = negWord(bgCameraX);
        composer.setVscrollFactorBG((short) bgCameraY);
        composer.fillPackedScrollWords(0, VISIBLE_LINES, fg, bg);
        composer.copyPackedScrollWordsTo(buffer);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    @Override public int getBgCameraX() { return bgCameraX; }
}
