package com.openggf.mods.ui;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestCreatorUi {
    @Test void bitmapGeometryIsCachedAsDisjointRectanglesWithoutLosingPixels() {
        BitmapFont solid = BitmapFont.binary("A", "1".repeat(64), 1, 64, 2);
        List<List<Integer>> solidRuns = new ArrayList<>();
        solid.glyphs("A", 4, 8, 3, (x, y, w, h) -> solidRuns.add(List.of(x, y, w, h)));
        assertEquals(List.of(List.of(4, 8, 3, 192)), solidRuns,
                "an uninterrupted stem needs one cached rectangle");

        String pixels = "11111001100110011111";
        BitmapFont face = BitmapFont.binary("A", pixels, 4, 5, 5);
        for (int scale : new int[] {1, 3, 8}) {
            int[][] visits = new int[5 * scale][4 * scale];
            face.glyphs("A", 0, 0, scale, (x, y, w, h) -> {
                for (int py = y; py < y + h; py++) {
                    for (int px = x; px < x + w; px++) visits[py][px]++;
                }
            });
            for (int y = 0; y < visits.length; y++) {
                for (int x = 0; x < visits[y].length; x++) {
                    assertEquals(pixels.charAt(y / scale * 4 + x / scale) - '0', visits[y][x],
                            "translucent pixels must be covered exactly once");
                }
            }
        }

        StringBuilder checker = new StringBuilder();
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 32; x++) checker.append((x + y) % 2 == 0 ? '1' : '0');
        }
        BitmapFont maximum = BitmapFont.binary("A", checker.toString(), 32, 64, 32);
        List<List<Integer>> first = new ArrayList<>();
        maximum.glyphs("A", 0, 0, 1, (x, y, w, h) -> first.add(List.of(x, y, w, h)));
        assertEquals(1024, first.size());
        List<List<Integer>> repeated = new ArrayList<>();
        maximum.glyphs("A", 0, 0, 1, (x, y, w, h) -> repeated.add(List.of(x, y, w, h)));
        assertEquals(first, repeated);
    }

    @Test void authoredFacesKeepSmallDigitsAndExactCaseWithoutReplacingTheirDesign() {
        BitmapFont digits = BitmapFont.binary("0", "111101101101111", 3, 5, 4);
        List<List<Integer>> runs = new ArrayList<>();
        digits.glyphs("0?0", 10, 20, 2, (x,y,w,h) -> runs.add(List.of(x,y,w,h)));
        assertEquals(List.of(10,20,6,2),runs.getFirst());
        assertTrue(runs.contains(List.of(26,20,6,2)));
        assertEquals(22,digits.width("0?0",2));
        assertEquals(0,digits.width("",1));
        BitmapFont cases = BitmapFont.binary("Xx", "1001" + "0110", 2, 2, 3);
        List<List<Integer>> lower = new ArrayList<>();
        cases.glyphs("x",0,0,1,(x,y,w,h)->lower.add(List.of(x,y,w,h)));
        assertEquals(List.of(List.of(1,0,1,1),List.of(0,1,1,1)),lower);
    }

    @Test void authoredFaceRejectsMalformedAndUnboundedInputBeforeDrawing() {
        assertThrows(IllegalArgumentException.class,()->BitmapFont.binary("AA","11",1,1,1));
        assertThrows(IllegalArgumentException.class,()->BitmapFont.binary("A","2",1,1,1));
        assertThrows(IllegalArgumentException.class,()->BitmapFont.binary("A","1",33,1,1));
        assertThrows(IllegalArgumentException.class,()->BitmapFont.binary("A","",1,1,1));
        var face=BitmapFont.binary("A","1",1,1,1);
        assertThrows(IllegalArgumentException.class,()->face.width("A",0));
        assertThrows(ArithmeticException.class,()->face.glyphs("AA",Integer.MAX_VALUE,0,1,(x,y,w,h)->{}));
    }
    @Test void overlayClipsBeforeQueuingAndUsesImmutableScreenCoordinates() {
        withHeadlessGraphics(graphics -> {
            var canvas = new LevelOverlayCanvas(graphics, 320, 224);
            var frame = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics,
                    -417, 905, () -> {
                        canvas.fill(-5, -7, 20, 30, 0x8044AAFF);
                        canvas.fill(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 20, 0xFFFFFFFF);
                        canvas.fill(0, 0, 20, 20, 0);
                    });
            assertEquals(1, frame.primitives().size());
            var geometry = com.openggf.graphics.SpritePresentation.geometry(frame.primitives().getFirst());
            assertEquals(com.openggf.graphics.SpritePresentation.PrimitiveKind.RECTANGLE, geometry.kind());
            assertEquals(List.of(new com.openggf.graphics.SpritePresentation.Vertex(0, 0, 15, 23,
                    0x8044AAFF)), geometry.vertices());
            var replay = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics,
                    9123, -605, () -> com.openggf.level.render.SpritePresentationRenderer.draw(
                            graphics, frame, 9123, -605, ignored -> true));
            assertEquals(frame, replay, "moving the camera cannot move an already prepared overlay");
            assertThrows(UnsupportedOperationException.class, () -> geometry.vertices().clear());

            var smaller = new LevelOverlayCanvas(graphics, 8, 10);
            var clipped = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics,
                    Integer.MIN_VALUE, Integer.MAX_VALUE,
                    () -> smaller.fill(4, 6, Integer.MAX_VALUE, Integer.MAX_VALUE, 0xFFFFFFFF));
            assertEquals(List.of(new com.openggf.graphics.SpritePresentation.Vertex(4, 6, 8, 10,
                    0xFFFFFFFF)), com.openggf.graphics.SpritePresentation.geometry(
                            clipped.primitives().getFirst()).vertices(),
                    "clip to the canvas's captured viewport, independently of the camera or GL viewport");
        });
    }

    @Test void overlayFontPreparationPreservesScaledTranslucentPixelsAndRejectsOpaqueCommands() {
        withHeadlessGraphics(graphics -> {
            String pixels = "11111001100110011111";
            var font = BitmapFont.binary("A", pixels, 4, 5, 5);
            var canvas = new LevelOverlayCanvas(graphics, 64, 80);
            for (int scale : new int[] {1, 3, 8}) {
                var frame = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics,
                        742, -319, () -> font.draw(canvas, "A", 7, 11, scale, 0x8044AAFF));
                int[][] visits = new int[5 * scale][4 * scale];
                for (var primitive : frame.primitives()) {
                    var geometry = com.openggf.graphics.SpritePresentation.geometry(primitive);
                    assertEquals(com.openggf.graphics.SpritePresentation.PrimitiveKind.RECTANGLE, geometry.kind());
                    for (var rectangle : geometry.vertices()) {
                        assertEquals(0x8044AAFF, rectangle.argb());
                        for (int y = rectangle.y1(); y < rectangle.y2(); y++) {
                            for (int x = rectangle.x1(); x < rectangle.x2(); x++) visits[y - 11][x - 7]++;
                        }
                    }
                }
                for (int y = 0; y < visits.length; y++) {
                    for (int x = 0; x < visits[y].length; x++) {
                        assertEquals(pixels.charAt(y / scale * 4 + x / scale) - '0', visits[y][x],
                                "cached geometry must cover each translucent glyph pixel exactly once");
                    }
                }
                var replay = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics,
                        -965, 1472, () -> com.openggf.level.render.SpritePresentationRenderer.draw(
                                graphics, frame, -965, 1472, ignored -> true));
                assertEquals(frame, replay);
            }
            var rejected = assertThrows(IllegalStateException.class,
                    () -> com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0,
                            () -> graphics.registerCommand((x, y, width, height) -> { })));
            assertTrue(rejected.getMessage().contains("GPU command submitted during CPU sprite preparation"));
            assertFalse(com.openggf.graphics.SpritePresentation.isPreparing(graphics));
        });
    }

    @Test void overlayProjectionAndReplayUseSuppliedManagersAndKeepWorldPrimitives() {
        withHeadlessGraphics(bootstrap -> {
            var source = new com.openggf.graphics.GraphicsManager();
            var destination = new com.openggf.graphics.GraphicsManager();
            source.initHeadless(); destination.initHeadless();
            try {
                var sourceProjection = new MutableProjection(300);
                var destinationProjection = new MutableProjection(512);
                com.openggf.graphics.GraphicsProjectionAccess.install(source, sourceProjection);
                com.openggf.graphics.GraphicsProjectionAccess.install(destination, destinationProjection);
                var command = com.openggf.graphics.GLCommand.screenSpaceRect(source, 288,
                        9, 13, 10, 8, 0x8044AAFF);
                assertEquals(287f, command.getY1(), "capture the injected host's projection, not bootstrap height");
                assertEquals(com.openggf.graphics.GLCommand.BlendType.ONE_MINUS_SRC_ALPHA, command.getBlendMode());
                assertEquals(128f / 255f, command.getAlpha());
                var canvas = new LevelOverlayCanvas(source, 414, 288);
                var frame = com.openggf.level.render.SpritePresentationRenderer.prepare(source, 1542, -900,
                        () -> canvas.fill(408, 282, 20, 20, 0x8044AAFF));
                assertEquals(List.of(new com.openggf.graphics.SpritePresentation.Vertex(408, 282, 414, 288,
                        0x8044AAFF)), com.openggf.graphics.SpritePresentation.geometry(
                                frame.primitives().getFirst()).vertices());
                var replayCommand = assertInstanceOf(com.openggf.graphics.GLCommand.class,
                        frame.primitives().getFirst().primitive().command(destination, -721, 1405));
                assertEquals(230f, replayCommand.getY1(), "reconstruct against destination's 512-pixel projection");
                destinationProjection.height = 640;
                var replay = com.openggf.level.render.SpritePresentationRenderer.prepare(destination, -721, 1405,
                        () -> com.openggf.level.render.SpritePresentationRenderer.draw(destination, frame,
                                -721, 1405, ignored -> true));
                assertEquals(frame, replay, "a different host, projection height and camera retain captured pixels");
                assertTrue(java.util.Arrays.stream(frame.primitives().getFirst().primitive().getClass()
                        .getRecordComponents()).allMatch(component -> component.getType().isPrimitive()
                                || component.getType().isEnum()), "CPU geometry retains only immutable scalar values");

                var world = com.openggf.level.render.SpritePresentationRenderer.prepare(bootstrap, 100, 200,
                        () -> bootstrap.registerCommand(new com.openggf.graphics.GLCommand(
                                com.openggf.graphics.GLCommand.CommandType.RECTI, 0,
                                1f, 0f, 0f, 105, 210, 115, 220)));
                assertEquals(List.of(new com.openggf.graphics.SpritePresentation.Vertex(5, 10, 15, 20,
                        0xFFFF0000)), com.openggf.graphics.SpritePresentation.geometry(
                                world.primitives().getFirst()).vertices(), "existing world primitives retain camera subtraction");
                var worldReplay = com.openggf.level.render.SpritePresentationRenderer.prepare(bootstrap, -700, 980,
                        () -> com.openggf.level.render.SpritePresentationRenderer.draw(bootstrap, world,
                                -700, 980, ignored -> true));
                assertEquals(world, worldReplay);
                assertThrows(ArithmeticException.class, () -> com.openggf.graphics.GLCommand.screenSpaceRect(
                        source, 288, Integer.MAX_VALUE, 0, 1, 1, -1));
                assertThrows(IllegalArgumentException.class, () -> com.openggf.graphics.GLCommand.screenSpaceRect(
                        source, 288, 0, 0, 0, 1, -1));
            } finally {
                source.cleanup(); destination.cleanup();
            }
        });
    }

    @Test void screenCommandsRejectCreatorLoadedGraphicsCallbacksWithTheirActualOwner(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        String name = CreatorGraphics.class.getName();
        var jar = temp.resolve("creator-graphics.jar");
        try (var input = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class");
             var output = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.jar.JarEntry(name.replace('.', '/') + ".class"));
            output.write(input.readAllBytes()); output.closeEntry();
        }
        var parent = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String requested, boolean resolve) throws ClassNotFoundException {
                if (requested.equals(name)) throw new ClassNotFoundException(requested);
                return super.loadClass(requested, resolve);
            }
        };
        try (var loader = new com.openggf.mods.code.ModDependencyClassLoader("overlay-owner",
                new java.net.URL[]{jar.toUri().toURL()}, parent, List.of())) {
            var creator = (com.openggf.graphics.GraphicsManager) loader.loadClass(name).getConstructor().newInstance();
            var disabled = new java.util.LinkedHashSet<String>();
            var boundary = new com.openggf.mods.code.ModFaultBoundary(
                    java.util.Map.of("dependent", java.util.Set.of("overlay-owner")),
                    new com.openggf.mods.ModRuntimeFindingStore(),
                    owners -> new com.openggf.mods.ModStateSaveResult.Saved(), disabled::addAll);
            var aborted = assertThrows(com.openggf.mods.code.ModFaultBoundary.CallbackAborted.class,
                    () -> boundary.run("overlay-owner", () -> com.openggf.graphics.GLCommand.screenSpaceRect(
                            creator, 224, 0, 0, 1, 1, -1)));
            assertEquals("overlay-owner", aborted.owner());
            assertInstanceOf(SecurityException.class, aborted.getCause());
            assertEquals(java.util.Set.of("overlay-owner", "dependent"), disabled);
            assertEquals(0, creator.getClass().getField("projectionCalls").getInt(creator),
                    "factory admission must precede any creator virtual call");
            withHeadlessGraphics(graphics -> {
                var frame = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 8, 16,
                        () -> new LevelOverlayCanvas(graphics, 320, 224).fill(1, 2, 3, 4, -1));
                assertThrows(SecurityException.class,
                        () -> frame.primitives().getFirst().primitive().command(creator, 5, 7));
                var trustedSubtype = new com.openggf.graphics.GraphicsManager() {
                    @Override public com.openggf.graphics.RenderProjection getProjectionSource() {
                        return new MutableProjection(256);
                    }
                };
                var command = com.openggf.graphics.GLCommand.screenSpaceRect(trustedSubtype, 224,
                        0, 10, 1, 1, -1);
                assertEquals(246f, command.getY1(), "trusted engine-loaded host subtypes remain supported");
            });
            assertEquals(0, creator.getClass().getField("projectionCalls").getInt(creator),
                    "deferred replay admission must also precede creator virtual calls");
        }
    }

    public static final class CreatorGraphics extends com.openggf.graphics.GraphicsManager {
        public int projectionCalls;
        @Override public com.openggf.graphics.RenderProjection getProjectionSource() {
            projectionCalls++;
            throw new AssertionError("creator graphics callback escaped into screen drawing");
        }
    }

    private static final class MutableProjection implements com.openggf.graphics.RenderProjection {
        int height;
        MutableProjection(int height) { this.height = height; }
        public float[] getProjectionMatrixBuffer() { return new float[16]; }
        public boolean isFBOProjectionActive() { return true; }
        public int getCurrentDisplayHeight() { return height; }
    }

    private static void withHeadlessGraphics(java.util.function.Consumer<com.openggf.graphics.GraphicsManager> check) {
        com.openggf.graphics.GraphicsManager.destroyForReinit();
        com.openggf.game.session.EngineServices.configure(
                com.openggf.game.session.EngineContext.fromLegacySingletonsForBootstrap());
        var graphics = com.openggf.graphics.GraphicsManager.getInstance();
        graphics.initHeadless();
        try {
            check.accept(graphics);
        } finally {
            com.openggf.game.session.SessionManager.clear();
            com.openggf.graphics.GraphicsManager.destroyForReinit();
        }
    }
    @Test void compactMetricsAndEllipsisPreserveExampleSpacing() {
        assertEquals(17, CompactFont.width("ABC", 1));
        assertEquals(34, CompactFont.width("ABC", 2));
        assertEquals(0, CompactFont.width("", 1));
        assertEquals("AB...", CompactFont.fit("ABCDEFG", 29, 1));
        assertEquals("..", CompactFont.fit("ABCDEFG", 11, 1));
        assertThrows(IllegalArgumentException.class, () -> CompactFont.width("A", 0));
    }

    @Test void compactLargeScalesKeepEveryTranslucentPixelAndMatchingMetrics() {
        String[] digit = {"11110", "00001", "00001", "01110", "00001", "00001", "11110"};
        for (int scale : new int[] {5, 8}) {
            int[][] visits = new int[7 * scale][5 * scale];
            PixelCanvas canvas = new PixelCanvas() {
                public int width() { return 80; }
                public int height() { return 80; }
                public void fill(int x, int y, int w, int h, int argb) {
                    assertEquals(0x8044AAFF, argb);
                    assertTrue(x >= 7 && y >= 11 && x + w <= 7 + 5 * scale && y + h <= 11 + 7 * scale);
                    for (int py = y; py < y + h; py++)
                        for (int px = x; px < x + w; px++) visits[py - 11][px - 7]++;
                }
            };
            CompactFont.draw(canvas, "3", 7, 11, scale, 0x8044AAFF);
            for (int y = 0; y < visits.length; y++)
                for (int x = 0; x < visits[y].length; x++)
                    assertEquals(digit[y / scale].charAt(x / scale) - '0', visits[y][x],
                            "large glyphs must preserve every pixel exactly once at scale " + scale);
            assertEquals(scale == 5 ? 25 : 40, CompactFont.width("3", scale));
            assertEquals(scale == 5 ? 85 : 136, CompactFont.width("3 A", scale));
            assertEquals(0, CompactFont.width("", scale));
            assertEquals("A...", CompactFont.fit("ABCDE", scale == 5 ? 115 : 184, scale));
            assertEquals("..", CompactFont.fit("ABCDE", scale == 5 ? 55 : 88, scale));
        }
    }

    @Test void compactScaleBoundsRejectBeforeDrawing() {
        List<List<Integer>> emitted = new ArrayList<>();
        PixelCanvas canvas = new PixelCanvas() {
            public int width() { return 80; }
            public int height() { return 80; }
            public void fill(int x, int y, int w, int h, int argb) { emitted.add(List.of(x, y, w, h, argb)); }
        };
        for (int scale : new int[] {0, 9}) {
            assertThrows(IllegalArgumentException.class, () -> CompactFont.width("3", scale));
            assertThrows(IllegalArgumentException.class, () -> CompactFont.fit("3", 80, scale));
            assertThrows(IllegalArgumentException.class, () -> CompactFont.draw(canvas, "3", 7, 11, scale, -1));
        }
        assertTrue(emitted.isEmpty(), "invalid scales must not emit a partial glyph");
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

    @Test void primitiveOffsetsRejectOverflowBeforeDrawingAnyPartialShape() {
        List<List<Integer>> rectangles = new ArrayList<>();
        PixelCanvas canvas = new PixelCanvas() {
            public int width() { return 320; }
            public int height() { return 224; }
            public void fill(int x,int y,int w,int h,int argb) { rectangles.add(List.of(x,y,w,h,argb)); }
        };
        assertThrows(ArithmeticException.class,
                () -> UiPrimitives.frame(canvas, Integer.MAX_VALUE, 0, 2, 1, -1));
        assertThrows(ArithmeticException.class,
                () -> UiPrimitives.frame(canvas, 0, Integer.MAX_VALUE, 1, 2, -1));
        assertThrows(ArithmeticException.class,
                () -> UiPrimitives.gradient(canvas, 0, Integer.MAX_VALUE, 1, 2, -1, -1));
        assertTrue(rectangles.isEmpty(), "Overflow must reject before emitting a partial shape");
        UiPrimitives.frame(canvas, Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 1, -1);
        assertEquals(4, rectangles.size());
        assertTrue(rectangles.stream().allMatch(rectangle -> rectangle.get(0) == Integer.MAX_VALUE
                && rectangle.get(1) == Integer.MAX_VALUE));
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
