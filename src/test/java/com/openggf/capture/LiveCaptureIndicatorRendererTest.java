package com.openggf.capture;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveCaptureIndicatorRendererTest {
    @Test
    void placesRedDotAndWhiteRecAtProjectionTopRight() {
        List<String> calls = new ArrayList<>();
        LiveCaptureIndicatorRenderer renderer = new LiveCaptureIndicatorRenderer(
                (x, y, diameter, r, g, b, a) ->
                        calls.add("dot:" + x + ":" + y + ":" + diameter + ":" + r + ":" + g + ":" + b + ":" + a),
                (text, x, y, r, g, b, a, scale) ->
                        calls.add("text:" + text + ":" + x + ":" + y + ":" + r + ":" + g + ":" + b + ":" + a + ":" + scale),
                (text, scale) -> 15);

        renderer.render(320, 224);

        assertEquals(List.of(
                "dot:282:8:10:1.0:0.0:0.0:1.0",
                "text:REC:297:8:1.0:1.0:1.0:1.0:0.8"), calls);
    }
}
