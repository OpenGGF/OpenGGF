package com.openggf.mods.ui;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestCreatorUi {
    @org.junit.jupiter.api.Test
    void overlayClipsBeforeQueuingAndUsesImmutableScreenCoordinates() throws Exception {
        var graphics = org.mockito.Mockito.mock(com.openggf.graphics.GraphicsManager.class);
        var canvas = new LevelOverlayCanvas(graphics, 320, 224);
        canvas.fill(-5, -7, 20, 30, 0xFFFFFFFF);
        canvas.fill(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 20, 0xFFFFFFFF);
        canvas.fill(0, 0, 20, 20, 0);
        var command = org.mockito.ArgumentCaptor.forClass(com.openggf.graphics.GLCommandable.class);
        org.mockito.Mockito.verify(graphics).registerCommand(command.capture());
        Object rectangle = command.getValue();
        java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
        for (var component : rectangle.getClass().getRecordComponents()) {
            var accessor = component.getAccessor(); accessor.setAccessible(true);
            fields.put(component.getName(), accessor.invoke(rectangle));
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, fields.get("x"));
        org.junit.jupiter.api.Assertions.assertEquals(0, fields.get("y"));
        org.junit.jupiter.api.Assertions.assertEquals(15, fields.get("width"));
        org.junit.jupiter.api.Assertions.assertEquals(23, fields.get("height"));
        org.mockito.Mockito.verifyNoMoreInteractions(graphics);
    }
    @Test void compactMetricsAndEllipsisPreserveExampleSpacing() {
        assertEquals(17, CompactFont.width("ABC", 1));
        assertEquals(34, CompactFont.width("ABC", 2));
        assertEquals(0, CompactFont.width("", 1));
        assertEquals("AB...", CompactFont.fit("ABCDEFG", 29, 1));
        assertEquals("..", CompactFont.fit("ABCDEFG", 11, 1));
        assertThrows(IllegalArgumentException.class, () -> CompactFont.width("A", 0));
    }

    @Test void compactRasterRunsMatchTheMaintainedGlyphDesign() {
        List<List<Integer>> runs = new ArrayList<>();
        CompactFont.glyphs("A", 10, 20, 1,
                (x,y,w,h) -> runs.add(List.of(x,y,w,h)));
        assertEquals(List.of(11,20,3,1), runs.getFirst());
        assertTrue(runs.contains(List.of(10,23,5,1)));
        List<List<Integer>> fallback = new ArrayList<>();
        CompactFont.glyphs("?", 0, 0, 1, (x,y,w,h) -> fallback.add(List.of(x,y,w,h)));
        List<List<Integer>> unsupported = new ArrayList<>();
        CompactFont.glyphs("\u2603", 0, 0, 1, (x,y,w,h) -> unsupported.add(List.of(x,y,w,h)));
        assertEquals(fallback, unsupported);
    }

    @Test void atlasMetricsPreserveVariableAdvanceAndIgnoreUnmappedGlyphs() {
        AtlasFont font = AtlasFont.parse("= A\n.#.\n#.#\n###\n#.#\n#.#\n\n= I\n#\n#\n#\n#\n#\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), 5);
        assertEquals(5, font.width("AI"));
        assertEquals(7, font.width("A "));
        assertEquals(3, font.width("?"));
        assertEquals(5, font.height());
        assertThrows(IllegalArgumentException.class, () -> AtlasFont.parse("= A\n##\n#\n".getBytes(), 2));
    }

    @Test void wrappingMeasuresPixelsAndRespectsExplicitBreaks() {
        assertEquals(List.of("ONE", "TWO", "", "X"), TextLayout.wrap("ONE TWO\n\nX", 20,
                text -> CompactFont.width(text, 1)));
        assertEquals(List.of("LONGWORD"), TextLayout.wrap("LONGWORD", 5,
                text -> CompactFont.width(text, 1)));
        assertEquals(12, TextLayout.alignedX(10, 20, 16, TextLayout.Alignment.CENTER));
        assertEquals(14, TextLayout.alignedX(10, 20, 16, TextLayout.Alignment.RIGHT));
    }

    @Test void focusRetainsIdentityAndIgnoresDisabledOrHiddenRegions() {
        FocusRegions focus = new FocusRegions();
        focus.replace(List.of(new FocusRegions.Region("a",0,0,10,10,true),
                new FocusRegions.Region("disabled",0,15,10,10,false),
                new FocusRegions.Region("b",0,30,10,10,true)));
        assertEquals("a", focus.focused());
        assertEquals("b", focus.move(FocusRegions.Direction.DOWN));
        assertNull(focus.hit(0,15));
        assertEquals("b", focus.hit(9,39));
        assertNull(focus.hit(10,39));
        focus.replace(List.of(new FocusRegions.Region("b",30,0,10,10,true)));
        assertEquals("b", focus.focused());
        focus.replace(List.of());
        assertNull(focus.focused());
    }

    @Test void primitivesClampMetersAndInterpolateAlphaWithColor() {
        List<List<Integer>> rectangles = new ArrayList<>();
        PixelCanvas canvas = new PixelCanvas() {
            public int width() { return 320; }
            public int height() { return 224; }
            public void fill(int x,int y,int w,int h,int argb) { rectangles.add(List.of(x,y,w,h,argb)); }
        };
        UiPrimitives.gradient(canvas, 0, 0, 4, 2, 0x00000000, 0xFFFFFFFF);
        assertEquals(List.of(0,0,4,1,0x00000000), rectangles.getFirst());
        assertEquals(List.of(0,1,4,1,0xFFFFFFFF), rectangles.getLast());
        rectangles.clear();
        UiPrimitives.meter(canvas, 2, 3, 10, 4, 200, 100, 0xFF000000, 0xFFFFFFFF);
        assertEquals(List.of(2,3,10,4,0xFFFFFFFF), rectangles.getLast());
        assertThrows(IllegalArgumentException.class,
                () -> UiPrimitives.meter(canvas,0,0,10,4,1,0,0,0));
    }
}
