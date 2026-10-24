package com.openggf.game.sonic1.scroll;

import com.openggf.level.scroll.ScrollProjection;
import com.openggf.level.scroll.ScrollProjectionContext;
import com.openggf.level.scroll.ScrollView;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

/** Native S1 composition output, with gameplay-owned phase held during camera sampling. */
final class Sonic1ProjectionOutput {
    private Sonic1ProjectionOutput() { }

    static ScrollProjection finish(ScrollProjectionContext context, ScrollView view,
                                   ScrollEffectComposer composer, int backgroundX, int period) {
        var nativeOutput = context.nativeOutput();
        return new ScrollProjection(composer.packedScrollWords(), null, null, null,
                (short) (nativeOutput.foregroundY() + view.cameraY() - context.cameraY()),
                composer.getVscrollFactorBG(), backgroundX, period,
                nativeOutput.shakeX(), nativeOutput.shakeY(), nativeOutput.tilemapMode());
    }
}
