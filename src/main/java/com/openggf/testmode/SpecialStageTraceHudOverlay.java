package com.openggf.testmode;

import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Compact progress HUD for standalone special-stage trace replay. */
public final class SpecialStageTraceHudOverlay implements TraceSessionOverlay {
    private static final int X = 4;
    private static final int Y = 120;
    private static final int LINE_HEIGHT = 7;
    private static final float SCALE = 0.5f;

    private final Supplier<String> label;
    private final IntSupplier cursor;
    private final IntSupplier rowCount;
    private final BooleanSupplier lagRow;

    public SpecialStageTraceHudOverlay(
            Supplier<String> label,
            IntSupplier cursor,
            IntSupplier rowCount,
            BooleanSupplier lagRow) {
        this.label = label;
        this.cursor = cursor;
        this.rowCount = rowCount;
        this.lagRow = lagRow;
    }

    @Override
    public void render(PixelFontTextRenderer text) {
        text.beginBatch();
        try {
            text.drawShadowedText(label.get(), X, Y,
                    DebugColor.YELLOW, SCALE);
            text.drawShadowedText(String.format("FRAME %04d / %04d",
                            Math.max(0, cursor.getAsInt()),
                            Math.max(0, rowCount.getAsInt())),
                    X, Y + LINE_HEIGHT, DebugColor.LIGHT_GRAY, SCALE);
            text.drawShadowedText(lagRow.getAsBoolean()
                            ? "LAG ROW" : "PLAY ROW",
                    X, Y + LINE_HEIGHT * 2,
                    lagRow.getAsBoolean() ? DebugColor.GRAY : DebugColor.GREEN,
                    SCALE);
            text.drawShadowedText("ESC  EXIT", X, Y + LINE_HEIGHT * 3,
                    DebugColor.CYAN, SCALE);
        } finally {
            text.endBatch();
        }
    }
}
