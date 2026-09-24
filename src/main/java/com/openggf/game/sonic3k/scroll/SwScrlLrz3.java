package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.negWord;

/**
 * Lava Reef boss-act deformation ({@code LRZ3_BackgroundEvent/sub_59DDE}).
 *
 * <p>Before the descent, plane B follows X/Y at one sixteenth speed. After camera Y reaches
 * {@code $500}, the arena uses {@code cameraX-$700,cameraY-$500}. Once the screen event reaches
 * routine 8 the ROM overlays {@code AIZ2_SOZ1_LRZ3_FGDeformDelta} on both planes, with the
 * foreground phase advancing twice as fast as the background phase.
 */
public final class SwScrlLrz3 extends AbstractZoneScrollHandler {
    private static final short[] SHIMMER = {
            0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0
    };
    private final ScrollEffectComposer composer = new ScrollEffectComposer();

    @Override
    public void update(int[] target, int cameraX, int cameraY, int frameCounter, int actId) {
        resetScrollTracking();
        composer.reset();
        LrzZoneRuntimeState state = GameServices.hasRuntime()
                ? S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElse(null) : null;
        int bgX = cameraY < 0x500 ? (short) cameraX >> 4 : (short) (cameraX - 0x700);
        int bgY = cameraY < 0x500 ? (((short) cameraY >> 4) + 0x10) : (short) (cameraY - 0x500);
        short fgBase = negWord(cameraX);
        short bgBase = negWord(bgX);
        composer.setVscrollFactorBG((short) bgY);

        boolean shimmer = state != null && state.lrz3ScreenRoutine() >= 8;
        int fgPhase = ((cameraY + (frameCounter << 1)) & 0x3E) >> 1;
        int bgPhase = ((bgY + frameCounter) & 0x3E) >> 1;
        for (int line = 0; line < VISIBLE_LINES; line++) {
            int fg = fgBase;
            int bg = bgBase;
            if (shimmer) {
                fg += SHIMMER[(fgPhase + line) & 0x1F];
                bg += SHIMMER[(bgPhase + line) & 0x1F];
            }
            composer.writePackedScrollWord(line, (short) fg, (short) bg);
        }
        composer.copyPackedScrollWordsTo(target);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }
}
