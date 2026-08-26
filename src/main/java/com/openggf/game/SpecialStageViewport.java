package com.openggf.game;

import java.util.Objects;

/**
 * Immutable presentation geometry shared by special-stage render owners.
 *
 * <p>The stage world remains native-sized. A logical viewport wider than the
 * native 320-pixel H40 presentation receives a centered outer region; the
 * Sonic 2 H32 region is centered inside that outer region. This value carries
 * presentation geometry only and deliberately has no game or gameplay state.</p>
 */
public final class SpecialStageViewport {
    public static final int NATIVE_WIDTH = 320;
    public static final int H32_WIDTH = 256;
    public static final int NATIVE_HEIGHT = 224;
    private static final int H32_MARGIN = (NATIVE_WIDTH - H32_WIDTH) / 2;
    private static final SpecialStageViewport NATIVE = new SpecialStageViewport(NATIVE_WIDTH);

    private final int logicalWidth;
    private final Region outer;
    private final Region innerH32;

    private SpecialStageViewport(int logicalWidth) {
        this.logicalWidth = logicalWidth;
        int outerX = (logicalWidth - NATIVE_WIDTH) / 2;
        this.outer = new Region(outerX, 0, NATIVE_WIDTH, NATIVE_HEIGHT);
        this.innerH32 = new Region(outerX + H32_MARGIN, 0, H32_WIDTH, NATIVE_HEIGHT);
    }

    /** Returns geometry for a resolved logical width, clamped to native width. */
    public static SpecialStageViewport fromLogicalWidth(int logicalWidth) {
        int resolvedWidth = Math.max(NATIVE_WIDTH, logicalWidth);
        return resolvedWidth == NATIVE_WIDTH ? NATIVE : new SpecialStageViewport(resolvedWidth);
    }

    /** Returns the canonical native presentation geometry. */
    public static SpecialStageViewport nativeViewport() {
        return NATIVE;
    }

    public int logicalWidth() {
        return logicalWidth;
    }

    public int logicalHeight() {
        return NATIVE_HEIGHT;
    }

    public Region outer() {
        return outer;
    }

    public Region innerH32() {
        return innerH32;
    }

    public int outerOriginX() {
        return outer.x();
    }

    public int innerH32OriginX() {
        return innerH32.x();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SpecialStageViewport that)) {
            return false;
        }
        return logicalWidth == that.logicalWidth
                && outer.equals(that.outer)
                && innerH32.equals(that.innerH32);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logicalWidth, outer, innerH32);
    }

    /** An immutable rectangle in logical presentation coordinates. */
    public record Region(int x, int y, int width, int height) {
        public Region {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("region dimensions must be non-negative");
            }
        }
    }
}
