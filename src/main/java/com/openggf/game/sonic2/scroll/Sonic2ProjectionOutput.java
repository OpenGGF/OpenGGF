package com.openggf.game.sonic2.scroll;

import com.openggf.level.scroll.ScrollProjection;
import com.openggf.level.scroll.ScrollProjectionContext;
import com.openggf.level.scroll.ScrollView;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import java.util.function.IntUnaryOperator;

/** Owner-local immutable ROM-table capture and shared register publication for S2 projections. */
final class Sonic2ProjectionOutput {
    private Sonic2ProjectionOutput() { }

    static IntUnaryOperator captureRipple(ParallaxTables tables) {
        int[] values = new int[Math.max(66, tables == null ? 0 : tables.getRippleDataLength())];
        if (tables != null) for (int i = 0; i < values.length; i++) values[i] = tables.getRippleSigned(i);
        return index -> index < 0 || index >= values.length ? 0 : values[index];
    }

    static ScrollProjection finish(ScrollProjectionContext context, ScrollView view, ScrollEffectComposer composer) {
        return finish(context, view, composer, context.nativeOutput().backgroundX());
    }

    static ScrollProjection finish(ScrollProjectionContext context, ScrollView view,
                                   ScrollEffectComposer composer, int backgroundX) {
        var nativeOutput = context.nativeOutput();
        return new ScrollProjection(composer.packedScrollWords(), null, null, null,
                (short) (nativeOutput.foregroundY() + view.cameraY() - context.cameraY()),
                composer.getVscrollFactorBG(), backgroundX, nativeOutput.backgroundPeriod(),
                nativeOutput.shakeX(), nativeOutput.shakeY(), nativeOutput.tilemapMode());
    }
}
