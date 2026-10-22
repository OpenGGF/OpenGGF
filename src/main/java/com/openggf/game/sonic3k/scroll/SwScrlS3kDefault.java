package com.openggf.game.sonic3k.scroll;

import static com.openggf.level.scroll.M68KMath.asrWord;
import static com.openggf.level.scroll.M68KMath.negWord;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

/**
 * Default fallback scroll handler for Sonic 3&K zones that don't have a zone-specific
 * implementation yet. Provides simple parallax: BG X at 1/4 FG speed, BG Y at 1/4 FG speed.
 */
public class SwScrlS3kDefault extends AbstractZoneScrollHandler
        implements com.openggf.level.scroll.ScrollProjectionSource {

    private final ScrollEffectComposer composer;

    public SwScrlS3kDefault() {
        this(224);
    }

    private SwScrlS3kDefault(int lines) {
        composer = new ScrollEffectComposer(lines);
    }

    @Override
    public void update(
            int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        composeCamera(horizScrollBuf, cameraX, cameraY, actId);
    }

    private void composeCamera(int[] horizScrollBuf, int cameraX, int cameraY, int actId) {
        resetScrollTracking();
        composer.reset();

        short fgScroll = negWord(cameraX);
        short bgScroll = asrWord(fgScroll, 2); // BG at 1/4 FG speed

        composer.setVscrollFactorBG(asrWord(cameraY, 2)); // BG Y at 1/4 FG speed
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
            var scratch = new SwScrlS3kDefault(view.height());
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
