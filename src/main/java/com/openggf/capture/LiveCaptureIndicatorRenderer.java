package com.openggf.capture;

import java.util.Objects;

/** Draws the window-only recording indicator in logical projection space. */
public final class LiveCaptureIndicatorRenderer {
    public interface PrimitiveSink {
        void filledCircle(int x, int y, int diameter,
                          float red, float green, float blue, float alpha);
    }

    public interface TextSink {
        void draw(String text, int x, int y,
                  float red, float green, float blue, float alpha, float scale);
    }

    public interface TextMeasurer {
        int width(String text, float scale);
    }

    private static final int MARGIN = 8;
    private static final int DOT_DIAMETER = 10;
    private static final int GAP = 5;
    private static final float TEXT_SCALE = 0.8f;
    private static final String LABEL = "REC";

    private final PrimitiveSink primitives;
    private final TextSink text;
    private final TextMeasurer measurer;

    public LiveCaptureIndicatorRenderer(PrimitiveSink primitives, TextSink text,
                                        TextMeasurer measurer) {
        this.primitives = Objects.requireNonNull(primitives);
        this.text = Objects.requireNonNull(text);
        this.measurer = Objects.requireNonNull(measurer);
    }

    public void render(int projectionWidth, int projectionHeight) {
        int labelWidth = measurer.width(LABEL, TEXT_SCALE);
        int dotX = projectionWidth - MARGIN - labelWidth - GAP - DOT_DIAMETER;
        primitives.filledCircle(dotX, MARGIN, DOT_DIAMETER, 1f, 0f, 0f, 1f);
        text.draw(LABEL, dotX + DOT_DIAMETER + GAP, MARGIN,
                1f, 1f, 1f, 1f, TEXT_SCALE);
    }
}
