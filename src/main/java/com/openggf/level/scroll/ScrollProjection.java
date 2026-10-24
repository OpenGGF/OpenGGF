package com.openggf.level.scroll;

import com.openggf.level.scroll.compose.ScrollEffectComposer;

import java.util.Arrays;
import java.util.Objects;

/** Immutable complete scroll output. Packed horizontal words contain foreground then background. */
public record ScrollProjection(
        int[] horizontal,
        short[] backgroundLines,
        short[] backgroundColumns,
        short[] foregroundColumns,
        short foregroundY,
        short backgroundY,
        int backgroundX,
        int backgroundPeriod,
        int shakeX,
        int shakeY,
        BgTilemapUpdateMode tilemapMode,
        java.util.Map<ScrollSpriteKey, ScrollSpritePosition> spritePositions) {
    public ScrollProjection {
        horizontal = Objects.requireNonNull(horizontal, "horizontal scroll").clone();
        backgroundLines = copy(backgroundLines);
        backgroundColumns = copy(backgroundColumns);
        foregroundColumns = copy(foregroundColumns);
        Objects.requireNonNull(tilemapMode, "background residency mode");
        spritePositions = java.util.Map.copyOf(spritePositions);
        if (backgroundPeriod <= 0) {
            throw new IllegalArgumentException("Background period must be positive");
        }
    }

    public ScrollProjection(
            int[] horizontal,
            short[] backgroundLines,
            short[] backgroundColumns,
            short[] foregroundColumns,
            short foregroundY,
            short backgroundY,
            int backgroundX,
            int backgroundPeriod,
            int shakeX,
            int shakeY,
            BgTilemapUpdateMode tilemapMode) {
        this(
                horizontal,
                backgroundLines,
                backgroundColumns,
                foregroundColumns,
                foregroundY,
                backgroundY,
                backgroundX,
                backgroundPeriod,
                shakeX,
                shakeY,
                tilemapMode,
                java.util.Map.of());
    }

    public ScrollProjection withSpritePositions(
            java.util.Map<ScrollSpriteKey, ScrollSpritePosition> positions) {
        return new ScrollProjection(
                horizontal,
                backgroundLines,
                backgroundColumns,
                foregroundColumns,
                foregroundY,
                backgroundY,
                backgroundX,
                backgroundPeriod,
                shakeX,
                shakeY,
                tilemapMode,
                positions);
    }

    @Override
    public int[] horizontal() {
        return horizontal.clone();
    }

    @Override
    public short[] backgroundLines() {
        return copy(backgroundLines);
    }

    @Override
    public short[] backgroundColumns() {
        return copy(backgroundColumns);
    }

    @Override
    public short[] foregroundColumns() {
        return copy(foregroundColumns);
    }

    public static ScrollProjection fromComposer(
            ScrollEffectComposer composer,
            int backgroundX,
            int backgroundPeriod,
            BgTilemapUpdateMode mode) {
        return new ScrollProjection(
                composer.packedScrollWords(),
                composer.getPerLineVScrollBG(),
                composer.getPerColumnVScrollBG(),
                composer.getPerColumnVScrollFG(),
                composer.getVscrollFactorFG(),
                composer.getVscrollFactorBG(),
                backgroundX,
                backgroundPeriod,
                composer.getShakeOffsetX(),
                composer.getShakeOffsetY(),
                mode);
    }

    private static short[] copy(short[] values) {
        return values == null ? null : values.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScrollProjection that
                && Arrays.equals(horizontal, that.horizontal)
                && Arrays.equals(backgroundLines, that.backgroundLines)
                && Arrays.equals(backgroundColumns, that.backgroundColumns)
                && Arrays.equals(foregroundColumns, that.foregroundColumns)
                && foregroundY == that.foregroundY
                && backgroundY == that.backgroundY
                && backgroundX == that.backgroundX
                && backgroundPeriod == that.backgroundPeriod
                && shakeX == that.shakeX
                && shakeY == that.shakeY
                && tilemapMode == that.tilemapMode
                && spritePositions.equals(that.spritePositions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Arrays.hashCode(horizontal),
                Arrays.hashCode(backgroundLines),
                Arrays.hashCode(backgroundColumns),
                Arrays.hashCode(foregroundColumns),
                foregroundY,
                backgroundY,
                backgroundX,
                backgroundPeriod,
                shakeX,
                shakeY,
                tilemapMode,
                spritePositions);
    }
}
