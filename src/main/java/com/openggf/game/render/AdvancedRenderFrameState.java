package com.openggf.game.render;

import com.openggf.util.ShortIndexedView;

import java.util.Arrays;

/**
 * Frame-local render-mode state consumed during one queued render frame.
 *
 * <p>Controller-resolved instances are double-buffered. Their column storage is
 * private and exposed to render consumers only through a read-only indexed view.
 */
public final class AdvancedRenderFrameState {
    private static final AdvancedRenderFrameState DISABLED =
            new AdvancedRenderFrameState(false, false, null);

    private boolean enableForegroundHeatHaze;
    private boolean enablePerLineForegroundScroll;
    private boolean reversePlaneAssignment;
    private boolean hasForegroundVScrollOverride;
    private short foregroundVScrollOverride;
    private boolean hasBackgroundVScrollOverride;
    private short backgroundVScrollOverride;
    private short[] foregroundPerColumnVScrollOverride;
    private int foregroundPerColumnVScrollLength;
    private boolean hasForegroundPerColumnVScrollOverride;
    private final ColumnView foregroundPerColumnVScrollView = new ColumnView();

    public AdvancedRenderFrameState(boolean enableForegroundHeatHaze,
                                    boolean enablePerLineForegroundScroll,
                                    short[] foregroundPerColumnVScrollOverride) {
        write(enableForegroundHeatHaze, enablePerLineForegroundScroll, foregroundPerColumnVScrollOverride);
    }

    public static AdvancedRenderFrameState disabled() {
        return DISABLED;
    }

    public boolean enableForegroundHeatHaze() {
        return enableForegroundHeatHaze;
    }

    public boolean enablePerLineForegroundScroll() {
        return enablePerLineForegroundScroll;
    }

    public boolean reversePlaneAssignment() { return reversePlaneAssignment; }
    public boolean hasForegroundVScrollOverride() { return hasForegroundVScrollOverride; }
    public short foregroundVScrollOverride() { return foregroundVScrollOverride; }
    public boolean hasBackgroundVScrollOverride() { return hasBackgroundVScrollOverride; }
    public short backgroundVScrollOverride() { return backgroundVScrollOverride; }

    /** Compatibility accessor returning a defensive copy, never the frame backing. */
    public short[] foregroundPerColumnVScrollOverride() {
        if (!hasForegroundPerColumnVScrollOverride) {
            return null;
        }
        return foregroundPerColumnVScrollLength == 0
                ? new short[0]
                : Arrays.copyOf(foregroundPerColumnVScrollOverride, foregroundPerColumnVScrollLength);
    }

    /** Read-only, allocation-free view shared by all render passes in this frame. */
    public ShortIndexedView foregroundPerColumnVScrollView() {
        return hasForegroundPerColumnVScrollOverride ? foregroundPerColumnVScrollView : null;
    }

    Object foregroundPerColumnVScrollBackingIdentity() {
        return foregroundPerColumnVScrollOverride;
    }

    private void write(boolean heatHaze, boolean perLineScroll, short[] columns) {
        enableForegroundHeatHaze = heatHaze;
        enablePerLineForegroundScroll = perLineScroll;
        int length = columns == null ? 0 : columns.length;
        hasForegroundPerColumnVScrollOverride = columns != null;
        if (length > 0) {
            if (foregroundPerColumnVScrollOverride == null
                    || foregroundPerColumnVScrollOverride.length < length) {
                foregroundPerColumnVScrollOverride = new short[length];
            }
            System.arraycopy(columns, 0, foregroundPerColumnVScrollOverride, 0, length);
        }
        foregroundPerColumnVScrollLength = length;
    }

    private void write(boolean heatHaze, boolean perLineScroll, short[] columns,
                       boolean reversePlanes, boolean hasFgVScroll, short fgVScroll,
                       boolean hasBgVScroll, short bgVScroll) {
        write(heatHaze, perLineScroll, columns);
        reversePlaneAssignment = reversePlanes;
        hasForegroundVScrollOverride = hasFgVScroll;
        foregroundVScrollOverride = fgVScroll;
        hasBackgroundVScrollOverride = hasBgVScroll;
        backgroundVScrollOverride = bgVScroll;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enableForegroundHeatHaze;
        private boolean enablePerLineForegroundScroll;
        private short[] foregroundPerColumnVScrollOverride;
        private boolean reversePlaneAssignment;
        private boolean hasForegroundVScrollOverride;
        private short foregroundVScrollOverride;
        private boolean hasBackgroundVScrollOverride;
        private short backgroundVScrollOverride;

        public Builder enableForegroundHeatHaze() {
            this.enableForegroundHeatHaze = true;
            return this;
        }

        public Builder enablePerLineForegroundScroll() {
            this.enablePerLineForegroundScroll = true;
            return this;
        }

        public Builder setForegroundPerColumnVScrollOverride(short[] foregroundPerColumnVScrollOverride) {
            this.foregroundPerColumnVScrollOverride = foregroundPerColumnVScrollOverride;
            return this;
        }

        public Builder reversePlaneAssignment() {
            reversePlaneAssignment = true;
            return this;
        }

        public Builder setForegroundVScrollOverride(short value) {
            hasForegroundVScrollOverride = true;
            foregroundVScrollOverride = value;
            return this;
        }

        public Builder setBackgroundVScrollOverride(short value) {
            hasBackgroundVScrollOverride = true;
            backgroundVScrollOverride = value;
            return this;
        }

        public AdvancedRenderFrameState build() {
            if (!enableForegroundHeatHaze
                    && !enablePerLineForegroundScroll
                    && foregroundPerColumnVScrollOverride == null
                    && !reversePlaneAssignment
                    && !hasForegroundVScrollOverride
                    && !hasBackgroundVScrollOverride) {
                return DISABLED;
            }
            AdvancedRenderFrameState state = new AdvancedRenderFrameState(
                    enableForegroundHeatHaze,
                    enablePerLineForegroundScroll,
                    foregroundPerColumnVScrollOverride);
            state.write(enableForegroundHeatHaze, enablePerLineForegroundScroll,
                    foregroundPerColumnVScrollOverride, reversePlaneAssignment,
                    hasForegroundVScrollOverride, foregroundVScrollOverride,
                    hasBackgroundVScrollOverride, backgroundVScrollOverride);
            return state;
        }

        void reset() {
            enableForegroundHeatHaze = false;
            enablePerLineForegroundScroll = false;
            foregroundPerColumnVScrollOverride = null;
            reversePlaneAssignment = false;
            hasForegroundVScrollOverride = false;
            foregroundVScrollOverride = 0;
            hasBackgroundVScrollOverride = false;
            backgroundVScrollOverride = 0;
        }

        AdvancedRenderFrameState buildInto(AdvancedRenderFrameState target) {
            if (!enableForegroundHeatHaze
                    && !enablePerLineForegroundScroll
                    && foregroundPerColumnVScrollOverride == null
                    && !reversePlaneAssignment
                    && !hasForegroundVScrollOverride
                    && !hasBackgroundVScrollOverride) {
                return DISABLED;
            }
            target.write(enableForegroundHeatHaze, enablePerLineForegroundScroll,
                    foregroundPerColumnVScrollOverride, reversePlaneAssignment,
                    hasForegroundVScrollOverride, foregroundVScrollOverride,
                    hasBackgroundVScrollOverride, backgroundVScrollOverride);
            return target;
        }
    }

    private final class ColumnView implements ShortIndexedView {
        @Override
        public int size() {
            return foregroundPerColumnVScrollLength;
        }

        @Override
        public short get(int index) {
            if (index < 0 || index >= foregroundPerColumnVScrollLength) {
                throw new IndexOutOfBoundsException(index);
            }
            return foregroundPerColumnVScrollOverride[index];
        }
    }
}
