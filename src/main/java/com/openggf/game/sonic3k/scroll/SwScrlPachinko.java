package com.openggf.game.sonic3k.scroll;

import static com.openggf.level.scroll.M68KMath.negWord;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

/**
 * Pachinko / Glowing Spheres bonus stage scroll handler.
 *
 * <p>ROM reference: {@code Pachinko_BGScroll} in sonic3k.asm.
 * Background vertical scroll follows camera Y at 1/8 speed. Background horizontal
 * scroll uses an anchored 5/8 parallax curve around screen center offset $A0.
 */
public class SwScrlPachinko extends AbstractZoneScrollHandler
        implements com.openggf.level.scroll.ScrollProjectionSource {
    private static final int VISIBLE_LINES = 224;

    private final ScrollEffectComposer composer;

    public SwScrlPachinko() {
        this(224);
    }

    private SwScrlPachinko(int lines) {
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
        resetScrollTracking();
        composer.reset();

        short fgScroll = negWord(cameraX);
        int bgWorldX = 0x60 + (((cameraX - 0xA0) * 5) >> 3);
        short bgScroll = negWord(bgWorldX);
        composer.setVscrollFactorBG((short) (cameraY >> 3));

        composer.fillPackedScrollWords(0, composer.visibleLineCount(), fgScroll, bgScroll);

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    @Override
    public com.openggf.level.scroll.ScrollProjectionPhase captureProjection(
            com.openggf.level.scroll.ScrollProjectionContext context) {
        return view -> {
            var scratch = new SwScrlPachinko(view.height());
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
                    context.nativeOutput().backgroundPeriod(),
                    context.nativeOutput().shakeX(),
                    context.nativeOutput().shakeY(),
                    context.nativeOutput().tilemapMode());
        };
    }
}
