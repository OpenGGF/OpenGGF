package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.util.FboHelper;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** Actual ownership and palette-shader pixels from synthetic inlets, without desktop access. */
@Isolated
class TestS3kSpecialStageSpritePixels {
    private static final int FLOOR = 0x3000, FRONT = 0x4000, REAR = 0x4001;
    private static final int GREEN = 0x00FF00, RED = 0xFF0000, BLUE = 0x0000FF;

    @Test
    void multiTileFlipsRetainColumnMajorArtAndEarlierSatOwnership() throws Exception {
        withGraphics((graphics, width) -> {
            int[] colors = {0, GREEN, RED, BLUE, 0xFFFF00, 0x00FFFF, 0xFF00FF, 0xFFFFFF, 0xFF8000, 0x808080};
            for (int line = 0; line < 4; line++) {
                var palette = new Palette();
                for (int i = 1; i < colors.length; i++) palette.setColor(i, new Palette.Color(
                        (byte) (colors[i] >>> 16), (byte) (colors[i] >>> 8), (byte) colors[i]));
                graphics.cachePaletteTexture(palette, line);
            }
            // Four column-major tiles, each with a distinct fill and a distinct
            // source top-left corner. H+V must move that corner to bottom-right.
            for (int tile = 0; tile < 4; tile++) {
                var art = solid(tile + 2); art.setPixel(0, 0, (byte) (tile + 6));
                graphics.cachePatternTexture(art, FRONT + tile);
            }
            var map = mapping(0x1800); map[35] = 5; // one 2x2 H+V mapping piece
            for (boolean batching : new boolean[]{false, true}) {
                graphics.setBatchingEnabled(batching);
                var renderer = renderer(graphics, width, map, 0x50, 0x50, 44);
                var perspective = (byte[]) field(renderer, "perspectiveMaps");
                word(perspective, 104, 128 + 164); // rear overlaps at (+4,+4)
                var manager = manager();
                manager.getGrid().setCellByIndex(0x2C8, Sonic3kSpecialStageConstants.CELL_BLUE);
                manager.getGrid().setCellByIndex(0x2C9, Sonic3kSpecialStageConstants.CELL_RED);
                draw(graphics, renderer, manager, width);
                int[][] expectedTiles = {{3, 1}, {2, 0}};
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    int tile = expectedTiles[y / 8][x / 8];
                    int index = tile + (x % 8 == 7 && y % 8 == 7 ? 6 : 2);
                    assertPixel(width / 2 + x, 40 + y, colors[index],
                            "earlier flipped 2x2 pixel (" + x + "," + y + ") batch=" + batching);
                }
                assertPixel(width / 2 + 18, 46, BLUE, "rear's exposed top-right source tile");
                assertPixel(width / 2 + 19, 51, 0xFFFFFF, "partially masked rear retains both within-tile flips");
                assertPixel(width / 2 + 19, 59, 0xFF00FF, "rear's bottom-right uses source tile zero");
            }
        });
    }

    @Test
    void maskedSlotsScratchAndTexturesRemainBoundedAcrossFramesResizeResetAndContextRecreation() throws Exception {
        var layer = new S3kSpecialStageSpriteLayer();
        Object[] previousContextEntry = {null};
        withGraphics((graphics, width) -> {
            Object occupancy = field(layer, "occupied"), plane = field(layer, "highPlane"), scratch = field(layer, "scratch");
            int pages = graphics.getPatternAtlas().getAtlasCount();
            Object[] firstEntry = {null}, firstMask = {null};
            int texture = graphics.getPatternAtlas().getTextureId(0);
            for (int frame = 0; frame < 600; frame++) {
                int viewportWidth = frame % 2 == 0 ? 320 : width;
                int origin = (viewportWidth - 320) / 2;
                graphics.setViewport(0, 0, viewportWidth, 224);
                graphics.setProjectionWidth(viewportWidth);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, viewportWidth, 0, 224, -1, 1).get(new float[16]));
                glViewport(0, 0, viewportWidth, 224);
                clear();
                graphics.executeCapturedCommands(() -> {
                    layer.begin(graphics, origin);
                    graphics.beginPatternBatch();
                    layer.sprite(FRONT, new PatternDesc(0x8000), 24, 32, false);
                    layer.sprite(REAR, new PatternDesc(0x8000), 28, 32, false);
                    graphics.flushPatternBatch();
                }, 0, 0, viewportWidth, 224);
                var entry = graphics.getPatternAtlasEntry(S3kSpecialStageSpriteLayer.MASK_PATTERN_BASE);
                assertNotNull(entry);
                if (frame == 0) {
                    firstEntry[0] = entry;
                    firstMask[0] = ((java.util.List<?>) field(layer, "masks")).getFirst();
                    assertNotSame(previousContextEntry[0], entry, "new context re-uploads its own mask");
                } else {
                    assertSame(firstEntry[0], entry, "steady frames/outer viewport resize retain the slot");
                    assertSame(firstMask[0], ((java.util.List<?>) field(layer, "masks")).getFirst());
                }
                assertEquals(texture, graphics.getPatternAtlas().getTextureId(0));
                assertNull(graphics.getPatternAtlasEntry(S3kSpecialStageSpriteLayer.MASK_PATTERN_BASE + 1));
                assertEquals(pages, graphics.getPatternAtlas().getAtlasCount(), "no per-frame GPU texture growth");
            }
            assertSame(occupancy, field(layer, "occupied"));
            assertSame(plane, field(layer, "highPlane"));
            assertSame(scratch, field(layer, "scratch"));
            assertEquals(1, ((java.util.List<?>) field(layer, "masks")).size(), "one retained masked fragment");
            assertPixel((width - 320) / 2 + 29, 35, RED, "earlier sprite after resize");
            assertPixel((width - 320) / 2 + 33, 35, BLUE, "masked rear after resize");
            previousContextEntry[0] = firstEntry[0];
            layer.reset();
            assertNull(graphics.getPatternAtlasEntry(S3kSpecialStageSpriteLayer.MASK_PATTERN_BASE), "stage exit retires owned slot");
            assertTrue(((java.util.List<?>) field(layer, "masks")).isEmpty());
            // Re-entry with the same atlas must not trust an uncached CPU mask.
            graphics.executeCapturedCommands(() -> {
                layer.begin(graphics, 0);
                layer.sprite(FRONT, new PatternDesc(0x8000), 24, 32, false);
                layer.sprite(REAR, new PatternDesc(0x8000), 28, 32, false);
            }, 0, 0, width, 224);
            assertNotNull(graphics.getPatternAtlasEntry(S3kSpecialStageSpriteLayer.MASK_PATTERN_BASE));
        });
    }

    @Test
    void firstOpaqueSatPixelOwnsPriorityBeforePlaneComparison() throws Exception {
        withGraphics((graphics, width) -> {
            for (boolean batching : new boolean[]{false, true}) {
                graphics.setBatchingEnabled(batching);
                var layer = new S3kSpecialStageSpriteLayer();
                int origin = (width - 320) / 2;
                for (boolean highPlane : new boolean[]{false, true}) {
                    for (boolean frontHigh : new boolean[]{false, true}) {
                        clear();
                        graphics.executeCapturedCommands(() -> {
                            layer.begin(graphics, origin);
                            var plane = new PatternDesc(highPlane ? 0x8000 : 0);
                            graphics.renderPatternWithId(FLOOR, plane, origin + 24, 32);
                            layer.plane(FLOOR, plane, 24, 32);
                            graphics.beginPatternBatch();
                            layer.sprite(FRONT, new PatternDesc(frontHigh ? 0x8000 : 0), 24, 32, false);
                            layer.sprite(REAR, new PatternDesc(frontHigh ? 0 : 0x8000), 24, 32, false);
                            graphics.flushPatternBatch();
                        }, 0, 0, width, 224);
                        assertPixel(origin + 27, 35, highPlane && !frontHigh ? GREEN : RED,
                                "front=" + frontHigh + " plane=" + highPlane + " batch=" + batching);
                    }
                }
                // Transparent source pixels never claim ownership. The flipped
                // front has a hole on the right; the rear stays blue there.
                var holes = solid(2); holes.setPixel(0, 0, (byte) 0);
                graphics.cachePatternTexture(holes, FRONT);
                clear();
                graphics.executeCapturedCommands(() -> {
                    layer.begin(graphics, origin);
                    graphics.beginPatternBatch();
                    layer.sprite(FRONT, new PatternDesc(0x9800), 24, 32, false);
                    layer.sprite(REAR, new PatternDesc(0), 24, 32, false);
                    graphics.flushPatternBatch();
                }, 0, 0, width, 224);
                assertPixel(origin + 31, 39, BLUE, "transparent flipped front admits rear");
                assertPixel(origin + 30, 39, RED, "opaque flipped front retains ownership");
                graphics.cachePatternTexture(solid(2), FRONT);
            }
        });
    }

    @Test
    void productionTraversalAndMappingPiecesWinWithEqualOrInvertedY() throws Exception {
        withGraphics((graphics, width) -> {
            for (boolean batching : new boolean[]{false, true}) {
                graphics.setBatchingEnabled(batching);
                for (int rearY : new int[]{40, 38, 42}) {
                    for (int[] priorities : new int[][]{{0x50, 0x54}, {0x54, 0x50}}) {
                        var renderer = renderer(graphics, width, mapping(0), priorities[0], priorities[1], rearY);
                        var manager = manager();
                        manager.getGrid().setCellByIndex(0x2C8, Sonic3kSpecialStageConstants.CELL_BLUE);
                        manager.getGrid().setCellByIndex(0x2C9, Sonic3kSpecialStageConstants.CELL_RED);
                        draw(graphics, renderer, manager, width);
                        assertPixel(width / 2 + 2, 44, priorities[0] == 0x50 ? RED : GREEN,
                                "native traversal survives rearY=" + rearY + " batch=" + batching);
                    }
                }
                var pieces = mapping(0, 1);
                var renderer = renderer(graphics, width, pieces, 0x50, 0x50, 40);
                var manager = manager();
                manager.getGrid().setCellByIndex(0x2C8, Sonic3kSpecialStageConstants.CELL_BLUE);
                draw(graphics, renderer, manager, width);
                assertPixel(width / 2 + 2, 44, RED, "earlier mapping piece wins over later blue piece");
            }
        });
    }

    @Test
    void originalUnsignedPerspectiveAndWordAdditionReachTheGpu() throws Exception {
        assertEquals(0xC680, Sonic3kSpecialStageRenderer.effectiveGridArtWord(0, 0xC680, 0x53));
        assertEquals(0x4680, Sonic3kSpecialStageRenderer.effectiveGridArtWord(0, 0xC680, 0x54));
        assertEquals(0x4680, Sonic3kSpecialStageRenderer.effectiveGridArtWord(0, 0xC680, 0x8050));
        assertEquals(0x2680, Sonic3kSpecialStageRenderer.effectiveGridArtWord(0x6000, 0xC680, 0x50));
        withGraphics((graphics, width) -> {
            var manager = manager();
            manager.getGrid().setCellByIndex(0x2C8, Sonic3kSpecialStageConstants.CELL_BLUE);
            for (int perspective : new int[]{0x53, 0x54, 0x8050}) {
                draw(graphics, renderer(graphics, width, mapping(0), perspective, 0, 40), manager, width);
                assertPixel(width / 2 + 2, 44, perspective == 0x53 ? RED : GREEN, "original perspective=" + perspective);
            }
            draw(graphics, renderer(graphics, width, mapping(0x6000), 0x50, 0, 40), manager, width);
            assertPixel(width / 2 + 2, 44, GREEN, "ADD.W carries through palette and clears priority");
        });
    }

    @Test
    void planeAHudPlayersAndRearShadowShareOwnershipAndPreserveTheCaptureFramebuffer() throws Exception {
        withGraphics((graphics, width) -> {
            var target = FboHelper.createColorOnly(width, 224, GL_CLAMP_TO_EDGE);
            assertNotNull(target);
            try {
                glBindFramebuffer(GL_FRAMEBUFFER, target.fboId());
                graphics.setUseSpritePriorityShader(true);
                graphics.setBatchingEnabled(false); // delayed direct commands must also retain the private shader scope
                var renderer = renderer(graphics, width, mapping(0), 0x54, 0, 40);
                var manager = manager();
                // The ordinary player owns the same pixels before the grid and
                // the last-SAT shadow. HUD digits are a plane, icons are sprites.
                renderer.setPlayerPatternBase(REAR);
                renderer.setSonicMappingData(character(), character());
                byte[] scalars = new byte[512];
                word(scalars, 0xE0 * 2, -11585); word(scalars, 0x20 * 2, 11585);
                renderer.setPlayerProjectionScalars(scalars);
                renderer.setShadowPatternBase(0x6000);
                for (int i = 0; i < 16; i++) graphics.cachePatternTexture(solid(15), 0x6000 + i);
                set(manager, "initialized", true);
                set(manager, "mainLoopSpritesPublished", true);
                manager.getGrid().setCellByIndex(0x2C8, Sonic3kSpecialStageConstants.CELL_BLUE);
                draw(graphics, renderer, manager, width);
                assertEquals(target.fboId(), glGetInteger(GL_FRAMEBUFFER_BINDING), "retain owned capture FBO");
                assertPixel(width / 2 + 2, 44, BLUE, "ordinary sprite precedes grid despite its plane priority");
                assertPixel(width / 2 - 13, 151, 0x008000, "unowned rear shadow darkens floor");
                var perspective = new byte[96 + 240 * 6];
                perspective[1] = (byte) 0xFF; perspective[3] = 96;
                word(perspective, 96, 0x54); word(perspective, 98, 128 + 144); word(perspective, 100, 128 + 150);
                renderer.setPerspectiveMaps(perspective);
                draw(graphics, renderer, manager, width);
                assertPixel(width / 2 - 13, 151, GREEN, "hidden low grid pixel blocks the later high shadow");
                var playerAtShadow = character(); playerAtShadow[58] = (byte) (150 - 162);
                renderer.setSonicMappingData(playerAtShadow, playerAtShadow);
                draw(graphics, renderer, manager, width);
                assertPixel(width / 2 + 2, 151, BLUE, "shadow cannot darken an earlier player pixel");

                // Number tiles are high Plane A, so a high grid sprite may cover
                // them. An icon at the same position is earlier SAT and wins.
                renderer.setDigitsPatternBase(FLOOR);
                var numberMap = new byte[120]; var template = new byte[48];
                for (int i = 0; i < 120; i += 2) word(numberMap, i, 0x8000 | Sonic3kSpecialStageConstants.ART_TILE_DIGITS);
                for (int i = 0; i < 48; i += 2) word(template, i, 0x8000 | Sonic3kSpecialStageConstants.ART_TILE_DIGITS);
                renderer.setHudNumberMap(numberMap); renderer.setHudTemplate(template);
                word(perspective, 96, 0x50); word(perspective, 98, 128 + 24); word(perspective, 100, 128 + 8);
                draw(graphics, renderer, manager, width);
                assertPixel((width - 320) / 2 + 26, 10, RED, "high grid sprite covers Plane A number tile");
                word(perspective, 96, 0x54);
                draw(graphics, renderer, manager, width);
                assertPixel((width - 320) / 2 + 26, 10, GREEN, "number plane covers winning low grid sprite");
                for (int i = 0; i < 18; i++) graphics.cachePatternTexture(solid(3), 0x7000 + i);
                renderer.setIconsPatternBase(0x7000);
                word(perspective, 96, 0x50); word(perspective, 98, 128 + 72);
                draw(graphics, renderer, manager, width);
                assertPixel((width - 320) / 2 + 74, 10, BLUE, "ordinary icon precedes high grid sprite");
                assertEquals(target.fboId(), glGetInteger(GL_FRAMEBUFFER_BINDING));
                assertTrue(graphics.isUseSpritePriorityShader(), "restore producer and execution priority scope");
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { glBindFramebuffer(GL_FRAMEBUFFER, 0); FboHelper.destroy(target); }
        });
    }

    private static void withGraphics(GpuCheck check) throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try {
            for (int width : new int[]{320, 800}) {
                try (var context = PresentationOffscreenTestContext.open(width, 224)) {
                    var graphics = new GraphicsManager();
                    try {
                        graphics.init("shaders/shader_the_hedgehog.glsl");
                        graphics.setViewport(0, 0, width, 224);
                        graphics.setProjectionWidth(width);
                        graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, width, 0, 224, -1, 1).get(new float[16]));
                        glViewport(0, 0, width, 224); glDisable(GL_DITHER);
                        for (int line = 0; line < 4; line++) {
                            var palette = new Palette();
                            palette.setColor(1, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
                            palette.setColor(2, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
                            palette.setColor(3, new Palette.Color((byte) 0, (byte) 0, (byte) 255));
                            graphics.cachePaletteTexture(palette, line);
                        }
                        graphics.cachePatternTexture(solid(1), FLOOR);
                        graphics.cachePatternTexture(solid(2), FRONT);
                        graphics.cachePatternTexture(solid(3), REAR);
                        check.run(graphics, width);
                    } finally {
                        var atlas = graphics.getPatternAtlas();
                        int[] textures = atlas == null ? new int[0] : new int[atlas.getAtlasCount()];
                        for (int i = 0; i < textures.length; i++) textures[i] = atlas.getTextureId(i);
                        graphics.cleanup();
                        for (int texture : textures) assertFalse(glIsTexture(texture), "context owner deletes atlas page texture");
                        RenderContext.reset();
                    }
                }
            }
        } finally { TestEnvironment.resetAll(); }
    }

    private static Sonic3kSpecialStageRenderer renderer(GraphicsManager graphics, int width, byte[] mapping,
                                                       int first, int second, int rearY) {
        var renderer = new Sonic3kSpecialStageRenderer(graphics);
        renderer.setSpecialStageViewport(SpecialStageViewport.fromLogicalWidth(width));
        renderer.setArtLoaded(true); renderer.setFloorPatternBase(FLOOR);
        renderer.setSpherePatternBase(FRONT); renderer.setSphereMappingData(mapping);
        var floor = new byte[9 * 40 * 28 * 2];
        for (int i = 0; i < floor.length; i += 2) word(floor, i, 0x8000);
        renderer.setFloorMapData(floor);
        var perspective = new byte[96 + 240 * 6];
        perspective[0] = 0; perspective[1] = (byte) 0xFF; perspective[3] = 96;
        word(perspective, 96, first); word(perspective, 98, 128 + 160); word(perspective, 100, 128 + 40);
        word(perspective, 102, second); word(perspective, 104, 128 + 160); word(perspective, 106, 128 + rearY);
        renderer.setPerspectiveMaps(perspective);
        return renderer;
    }

    private static Sonic3kSpecialStageManager manager() throws Exception {
        var manager = new Sonic3kSpecialStageManager();
        manager.getPlayer().initialize(Sonic3kSpecialStageConstants.ANGLE_NORTH, 0x1000, 0x1000, false);
        set(manager, "playerCharacter", PlayerCharacter.SONIC_ALONE); set(manager, "tailsEnabled", false);
        return manager;
    }

    private static byte[] mapping(int... words) {
        var data = new byte[34 + words.length * 6];
        for (int frame = 0; frame < 16; frame++) word(data, frame * 2, 32);
        word(data, 32, words.length);
        for (int i = 0; i < words.length; i++) word(data, 36 + i * 6, words[i]);
        return data;
    }

    private static byte[] character() {
        var data = new byte[64];
        word(data, 0, 56); word(data, 24, 24); word(data, 48, 1); word(data, 50, 0);
        word(data, 56, 1);
        // Native grounded projection is 162: place this test piece at screen 40.
        data[58] = (byte) (40 - 162);
        return data;
    }

    private static Pattern solid(int index) {
        var pattern = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) index);
        return pattern;
    }

    private static void draw(GraphicsManager graphics, Sonic3kSpecialStageRenderer renderer,
                             Sonic3kSpecialStageManager manager, int width) {
        clear(); graphics.executeCapturedCommands(() -> renderer.render(manager), 0, 0, width, 224);
    }

    private static void clear() { glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT); }
    private static void assertPixel(int x, int y, int rgb, String message) {
        glFinish(); ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        glReadPixels(x, 223 - y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        int actual = (pixel.get(0) & 255) << 16 | (pixel.get(1) & 255) << 8 | (pixel.get(2) & 255);
        assertEquals(rgb, actual, message);
    }
    private static void word(byte[] data, int at, int word) { data[at] = (byte) (word >>> 8); data[at + 1] = (byte) word; }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    @FunctionalInterface private interface GpuCheck { void run(GraphicsManager graphics, int width) throws Exception; }
}
