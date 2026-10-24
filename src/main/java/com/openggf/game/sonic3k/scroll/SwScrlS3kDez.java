package com.openggf.game.sonic3k.scroll;

import static com.openggf.level.scroll.M68KMath.negWord;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

/**
 * Sonic 3 &amp; Knuckles Death Egg acts 1 and 2 ({@code $B00}, {@code $B01}) deformation.
 *
 * <p>Both acts use {@code PlainDeformation} (sonic3k.asm:103644):
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
 * <p>{@code DEZ1_BackgroundInit} (:118687) and {@code DEZ2_BackgroundInit} (:118816) both clear
 * {@code Camera_X_pos_BG_copy} and {@code Camera_Y_pos_BG_copy} before the first pass, and
 * nothing ever writes them again: those two words are only written by a zone's own deformation
 * routine, and the Death Egg routine only reads them. So the background scroll word stays 0 for
 * the whole of both acts and {@code V_scroll_value_BG} — copied from {@code Camera_Y_pos_BG_copy}
 * at the end of {@code ScreenEvents} (:102300) — stays 0 with it. The Death Egg background is a
 * fixed image behind a scrolling foreground.
 *
 * <p>{@code SwScrlS3kDefault} scrolls the background at a quarter of the camera speed on both
 * axes, which is wrong here; this handler exists to stop that.
 *
 * <p>The {@code $1700} final-boss act is a different routine ({@code sub_5A508}) and belongs to
 * its own handler.
 */
public class SwScrlS3kDez extends AbstractZoneScrollHandler
        implements com.openggf.level.scroll.ScrollProjectionSource {

    private int currentAct;

    private final ScrollEffectComposer composer;

    public SwScrlS3kDez() {
        this(224);
    }

    private SwScrlS3kDez(int lines) {
        composer = new ScrollEffectComposer(lines);
    }

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter, int actId) {
        composeCamera(horizScrollBuf, cameraX, cameraY, actId);
    }

    private void composeCamera(int[] horizScrollBuf, int cameraX, int cameraY,
                       int actId) {
        currentAct = actId;
        resetScrollTracking();
        composer.reset();

        short fgScroll = negWord(cameraX);
        // Camera_X_pos_BG_copy, cleared by the act's BackgroundInit and never rewritten.
        short bgScroll = 0;

        // Camera_Y_pos_BG_copy, likewise cleared and never rewritten.
        composer.setVscrollFactorBG((short) 0);
        composer.fillPackedScrollWords(0, composer.visibleLineCount(), fgScroll, bgScroll);

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

    @Override
    public com.openggf.level.scroll.ScrollProjectionPhase captureProjection(
            com.openggf.level.scroll.ScrollProjectionContext context) {
        return view -> {
            var scratch = new SwScrlS3kDez(view.height());
            int[] words = new int[view.height()];
            scratch.composeCamera(words, view.cameraX(), view.cameraY(), context.act());
            return new com.openggf.level.scroll.ScrollProjection(
                    words,
                    null,
                    null,
                    null,
                    (short) view.cameraY(),
                    scratch.vscrollFactorBG,
                    context.nativeOutput().backgroundX(),
                    context.act() == 0 ? Math.max(512, (view.width() + 15) & ~15) : 512,
                    context.nativeOutput().shakeX(),
                    context.nativeOutput().shakeY(),
                    context.nativeOutput().tilemapMode());
        };
    }
}
