package com.openggf.tools;

import com.openggf.configuration.WidescreenAspect;

import java.util.Arrays;

/** Capture-only presentation dimensions; trace comparison remains native. */
public record TraceCaptureDimensions(
        int logicalWidth,
        int logicalHeight,
        int scale,
        int physicalWidth,
        int physicalHeight,
        WidescreenAspect aspect) {

    public static final int LOGICAL_HEIGHT = 224;
    public static final int NATIVE_WIDTH = 320;

    public TraceCaptureDimensions {
        if (logicalHeight != LOGICAL_HEIGHT) {
            throw new IllegalArgumentException("capture logical height must be 224: " + logicalHeight);
        }
        if (scale <= 0) {
            throw new IllegalArgumentException("--scale must be positive: " + scale);
        }
        if (physicalWidth != Math.multiplyExact(logicalWidth, scale)
                || physicalHeight != Math.multiplyExact(logicalHeight, scale)) {
            throw new IllegalArgumentException(
                    "capture physical dimensions do not match logical dimensions");
        }
    }

    public static TraceCaptureDimensions resolve(int logicalWidth, int scale) {
        validateLogicalWidth(logicalWidth);
        WidescreenAspect aspect = Arrays.stream(WidescreenAspect.values())
                .filter(candidate -> candidate.pixelWidth() == logicalWidth)
                .findFirst().orElseThrow();
        if (scale <= 0) {
            throw new IllegalArgumentException("--scale must be positive: " + scale);
        }
        return new TraceCaptureDimensions(logicalWidth, LOGICAL_HEIGHT, scale,
                Math.multiplyExact(logicalWidth, scale),
                Math.multiplyExact(LOGICAL_HEIGHT, scale), aspect);
    }

    static void validateLogicalWidth(int logicalWidth) {
        boolean supported = Arrays.stream(WidescreenAspect.values())
                .anyMatch(candidate -> candidate.pixelWidth() == logicalWidth);
        if (!supported) {
            throw new IllegalArgumentException(
                "--width must be one of 320, 352, 400, 528, or 800 pixels: "
                            + logicalWidth);
        }
    }

    public String widthMode() {
        return aspect.name();
    }

    public String supportTier() {
        return switch (aspect) {
            case NATIVE_4_3, WIDE_16_10, WIDE_16_9 -> "SUPPORTED";
            case ULTRA_21_9 -> "SMOKE";
            case SUPER_32_9 -> "EXPLORATORY";
        };
    }
}
