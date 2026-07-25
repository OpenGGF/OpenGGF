package com.openggf.capture;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveCaptureIndicatorRendererTest {
    @Test
    void placesRedDotAndWhiteRecEightPixelsFromTopRightAt224High() {
        assertPlacement(224, 206, 208);
    }

    @Test
    void topRightPlacementTracksASecondProjectionHeight() {
        assertPlacement(240, 222, 224);
    }

    private static void assertPlacement(int projectionHeight, int expectedDotY, int expectedTextY) {
        List<String> calls = new ArrayList<>();
        LiveCaptureIndicatorRenderer renderer = new LiveCaptureIndicatorRenderer(
                (x, y, diameter, r, g, b, a) ->
                        calls.add("dot:" + x + ":" + y + ":" + diameter + ":" + r + ":" + g + ":" + b + ":" + a),
                (text, x, y, r, g, b, a, scale) ->
                        calls.add("text:" + text + ":" + x + ":" + y + ":" + r + ":" + g + ":" + b + ":" + a + ":" + scale),
                new LiveCaptureIndicatorRenderer.TextMeasurer() {
                    public int width(String text, float scale) { return 15; }
                    public int height(float scale) { return 8; }
                });

        renderer.render(320, projectionHeight);

        assertEquals(List.of(
                "dot:282:" + expectedDotY + ":10:1.0:0.0:0.0:1.0",
                "text:REC:297:" + expectedTextY + ":1.0:1.0:1.0:1.0:0.8"), calls);
    }
}
