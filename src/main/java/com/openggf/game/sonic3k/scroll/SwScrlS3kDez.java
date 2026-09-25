package com.openggf.game.sonic3k.scroll;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.negWord;

/**
 * Sonic 3 &amp; Knuckles Death Egg acts 1 and 2 ({@code $B00}, {@code $B01}) deformation.
 *
 * <p>Both acts use {@code PlainDeformation} (sonic3k.asm:103598):
 *
 * <pre>
 * PlainDeformation:
 *         lea     (H_scroll_buffer).w,a1
 *         move.w  (Camera_X_pos_copy).w,d0
 *         neg.w   d0
 *         swap    d0
 *         move.w  (Camera_X_pos_BG_copy).w,d0
 *         neg.w   d0
 *         ...     ; the same longword on every line
 * </pre>
 *
 * <p>{@code DEZ1_BackgroundInit} (:118641) and {@code DEZ2_BackgroundInit} (:118770) both clear
 * {@code Camera_X_pos_BG_copy} and {@code Camera_Y_pos_BG_copy} before the first pass, and
 * nothing ever writes them again: those two words are only written by a zone's own deformation
 * routine, and the Death Egg routine only reads them. So the background scroll word stays 0 for
 * the whole of both acts and {@code V_scroll_value_BG} — copied from {@code Camera_Y_pos_BG_copy}
 * at the end of {@code ScreenEvents} (:102254) — stays 0 with it. The Death Egg background is a
 * fixed image behind a scrolling foreground.
 *
 * <p>{@code SwScrlS3kDefault} scrolls the background at a quarter of the camera speed on both
 * axes, which is wrong here; this handler exists to stop that.
 *
 * <p>The {@code $1700} final-boss act is a different routine ({@code sub_5A508}) and belongs to
 * its own handler.
 */
public class SwScrlS3kDez extends AbstractZoneScrollHandler {

    private int currentAct;

    private final ScrollEffectComposer composer = new ScrollEffectComposer();

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        currentAct = actId;
        resetScrollTracking();
        composer.reset();

        short fgScroll = negWord(cameraX);
        // Camera_X_pos_BG_copy, cleared by the act's BackgroundInit and never rewritten.
        short bgScroll = 0;

        // Camera_Y_pos_BG_copy, likewise cleared and never rewritten.
        composer.setVscrollFactorBG((short) 0);
        composer.fillPackedScrollWords(0, VISIBLE_LINES, fgScroll, bgScroll);

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }
    @Override
    public int getBgPeriodWidth() {
        int width = com.openggf.game.GameServices.hasRuntime()
                ? com.openggf.game.GameServices.camera().getWidth() : 320;
        return currentAct == 0 ? Math.max(512, (width + 15) & ~15) : 512;
    }
}
