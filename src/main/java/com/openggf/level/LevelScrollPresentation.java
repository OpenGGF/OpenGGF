package com.openggf.level;

import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.graphics.ArenaMaskState;

import java.util.Arrays;
import java.util.Objects;

/** Immutable scroll registers/buffers paired with a prepared sprite table. */
final class LevelScrollPresentation {
    record Registers(int cameraX, int cameraY, int cameraXWithShake,
                     short foregroundY, short backgroundY, int backgroundX, int backgroundPeriod,
                     boolean heatHaze, boolean perLineForeground, boolean reversePlanes,
                     boolean overrideForegroundY, short overriddenForegroundY,
                     boolean overrideBackgroundY, short overriddenBackgroundY, ArenaMaskState arenaMask) { }

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
                mode.hasBackgroundVScrollOverride(), mode.backgroundVScrollOverride(), level.spritePresentationRenderer().boundsMask.sample()),
                scroll.getHScrollForShader(), scroll.getVScrollPerLineBGForShader(),
                scroll.getVScrollPerColumnBGForShader(), scroll.getVScrollPerColumnFGForShader(),
                mode.foregroundPerColumnVScrollOverride());
    }

    /** Capture the bounds in the same generation as the displayed camera and sprite table.
     * ROM camera bounds remain gameplay-owned; this only hides newly exposed wide pixels.
     * No zone ID, boss flag or explicit mask activation decides whether an edge is visible.
     */
    static ArenaMaskState captureArenaMask(LevelManager level) {
        var camera = level.camera;
        int currentMaxX = camera.getMaxX();
        var rules = level.gameModule == null ? null : level.gameModule.getRules().playerMovement();
        if (rules != null && !rules.levelBoundaryRightStrict()) {
            var state = com.openggf.game.GameServices.gameState();
            boolean locked = rules.levelBoundaryLockUsesScreenLockFlag()
                    ? state.isScreenLocked() : state.isBossFightActive();
            // S1/S2 permit another $40 pixels in ordinary play. Do not conceal
            // playable space merely because the camera itself stops earlier.
            if (!locked && !state.isEndOfLevelActive()) {
                currentMaxX += 64;
            }
        }
        var bounds = LevelBoundsMaskGeometry.select(level, currentMaxX);
        return ArenaMaskState.fromBounds(bounds.minX(), bounds.maxX(), camera.getXWithShake(),
                camera.getWidth(), level.objectManager == null ? level.frameCounter : level.objectManager.getFrameCounter(),
                level.zoneFeatureProvider != null && level.zoneFeatureProvider.foregroundWrapsHorizontally());
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
