package com.openggf.level;

import com.openggf.game.render.AdvancedRenderFrameState;

import java.util.Arrays;
import java.util.Objects;

/** Immutable scroll registers/buffers paired with a prepared sprite table. */
final class LevelScrollPresentation {
    record Registers(int cameraX, int cameraY, int cameraXWithShake,
                     short foregroundY, short backgroundY, int backgroundX, int backgroundPeriod,
                     boolean heatHaze, boolean perLineForeground, boolean reversePlanes,
                     boolean overrideForegroundY, short overriddenForegroundY,
                     boolean overrideBackgroundY, short overriddenBackgroundY) { }

    private final Registers registers;
    private final int[] horizontal;
    private final short[] backgroundLines, backgroundColumns, foregroundColumns, overrideColumns;

    LevelScrollPresentation(Registers registers, int[] horizontal, short[] backgroundLines,
                            short[] backgroundColumns, short[] foregroundColumns, short[] overrideColumns) {
        this.registers = registers;
        this.horizontal = horizontal.clone();
        this.backgroundLines = copy(backgroundLines);
        this.backgroundColumns = copy(backgroundColumns);
        this.foregroundColumns = copy(foregroundColumns);
        this.overrideColumns = copy(overrideColumns);
    }

    static LevelScrollPresentation capture(LevelManager level, AdvancedRenderFrameState mode) {
        var camera = level.camera;
        var scroll = level.parallaxManager;
        return new LevelScrollPresentation(new Registers(camera.getX(), camera.getY(), camera.getXWithShake(),
                scroll.getVscrollFactorFG(), scroll.getVscrollFactorBG(), scroll.getBgCameraX(),
                scroll.getBgPeriodWidth(), mode.enableForegroundHeatHaze(), mode.enablePerLineForegroundScroll(),
                mode.reversePlaneAssignment(), mode.hasForegroundVScrollOverride(), mode.foregroundVScrollOverride(),
                mode.hasBackgroundVScrollOverride(), mode.backgroundVScrollOverride()),
                scroll.getHScrollForShader(), scroll.getVScrollPerLineBGForShader(),
                scroll.getVScrollPerColumnBGForShader(), scroll.getVScrollPerColumnFGForShader(),
                mode.foregroundPerColumnVScrollOverride());
    }

    Registers registers() { return registers; }
    int[] horizontal() { return horizontal.clone(); }
    short[] backgroundLines() { return copy(backgroundLines); }
    short[] backgroundColumns() { return copy(backgroundColumns); }
    short[] foregroundColumns() { return copy(foregroundColumns); }

    AdvancedRenderFrameState renderMode() {
        var builder = AdvancedRenderFrameState.builder();
        if (registers.heatHaze()) builder.enableForegroundHeatHaze();
        if (registers.perLineForeground()) builder.enablePerLineForegroundScroll();
        if (registers.reversePlanes()) builder.reversePlaneAssignment();
        if (registers.overrideForegroundY()) builder.setForegroundVScrollOverride(registers.overriddenForegroundY());
        if (registers.overrideBackgroundY()) builder.setBackgroundVScrollOverride(registers.overriddenBackgroundY());
        builder.setForegroundPerColumnVScrollOverride(copy(overrideColumns));
        return builder.build();
    }

    private static short[] copy(short[] source) { return source == null ? null : source.clone(); }

    @Override public boolean equals(Object other) {
        return other instanceof LevelScrollPresentation state && registers.equals(state.registers)
                && Arrays.equals(horizontal, state.horizontal)
                && Arrays.equals(backgroundLines, state.backgroundLines)
                && Arrays.equals(backgroundColumns, state.backgroundColumns)
                && Arrays.equals(foregroundColumns, state.foregroundColumns)
                && Arrays.equals(overrideColumns, state.overrideColumns);
    }

    @Override public int hashCode() {
        return Objects.hash(registers, Arrays.hashCode(horizontal), Arrays.hashCode(backgroundLines),
                Arrays.hashCode(backgroundColumns), Arrays.hashCode(foregroundColumns), Arrays.hashCode(overrideColumns));
    }
}
