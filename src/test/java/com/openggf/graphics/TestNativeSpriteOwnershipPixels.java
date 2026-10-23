package com.openggf.graphics;

import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.util.FboHelper;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Native source ownership must be resolved before sprite/background priority, on an owned EGL target. */
@Isolated
class TestNativeSpriteOwnershipPixels {
    @Test
    void ghostsBlendOverNativeWithoutClaimingCoverageInBothReplayPaths() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(false);
            for (boolean published : new boolean[]{false, true}) for (int mode = 0; mode < 3; mode++) {
                fixture.graphics.setBatchingEnabled(mode != 0);
                fixture.graphics.setInstancedBatchingEnabled(mode == 2);
                for (float alpha : new float[]{0, .5f}) {
                    fixture.clear(0);
                    fixture.layers(published, new ReplayLayer(1, true, alpha), new ReplayLayer(2, true, null));
                    rgb(pixels(), 41, 42, Math.round(255 * alpha), Math.round(255 * (1 - alpha)), 0);
                    pixel(pixels(), 46, 42, 2);
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void opaqueNativeWinnersCoverGhostsEvenWhenTerrainSuppliesTheirColour() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            for (boolean published : new boolean[]{false, true}) for (int mode = 0; mode < 3; mode++) {
                fixture.graphics.setBatchingEnabled(mode != 0);
                fixture.graphics.setInstancedBatchingEnabled(mode == 2);
                for (boolean hidden : new boolean[]{false, true}) {
                    fixture.terrain(hidden);
                    fixture.clear(0);
                    fixture.layers(published, new ReplayLayer(1, !hidden, null), new ReplayLayer(2, true, .5f));
                    pixel(pixels(), 41, 42, hidden ? 3 : 1);
                    rgb(pixels(), 46, 42, 0, 128, 128);
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void multipleGhostsBlendInPainterOrderAndRespectInterveningNativeWinners() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(false);
            for (boolean published : new boolean[]{false, true}) for (int mode = 0; mode < 3; mode++) {
                fixture.graphics.setBatchingEnabled(mode != 0);
                fixture.graphics.setInstancedBatchingEnabled(mode == 2);
                fixture.clear(0);
                fixture.layers(published, new ReplayLayer(1, true, .5f), new ReplayLayer(2, true, .5f),
                        new ReplayLayer(3, true, null));
                rgb(pixels(), 41, 42, 128, 64, 64);
                rgb(pixels(), 46, 42, 0, 128, 128);
                fixture.clear(0);
                fixture.layers(published, new ReplayLayer(1, true, .5f), new ReplayLayer(3, true, null),
                        new ReplayLayer(2, true, .5f));
                rgb(pixels(), 41, 42, 128, 0, 128);
                pixel(pixels(), 46, 42, 3);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void layeredScopesRestoreForeignStencilAndFaceStatesAfterDrawAndFailure() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(false);
            fixture.clear(0);
            fixture.layers(false, new ReplayLayer(1, true, .5f), new ReplayLayer(2, true, null));
            glStencilMask(0xFF);
            glClearStencil(0x66);
            glClear(GL_STENCIL_BUFFER_BIT);
            byte[] original = stencil(0);
            glEnable(GL_STENCIL_TEST);
            glStencilFuncSeparate(GL_FRONT, GL_NEVER, 3, 0x7F);
            glStencilFuncSeparate(GL_BACK, GL_LESS, 9, 0x37);
            glStencilMaskSeparate(GL_FRONT, 0x5A);
            glStencilMaskSeparate(GL_BACK, 0xA5);
            glStencilOpSeparate(GL_FRONT, GL_INVERT, GL_DECR, GL_REPLACE);
            glStencilOpSeparate(GL_BACK, GL_ZERO, GL_INCR, GL_KEEP);
            glClearStencil(37);
            glEnable(GL_SCISSOR_TEST);
            glScissor(32, 170, 24, 24);
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glEnable(GL_BLEND);
            glBlendFuncSeparate(GL_ONE, GL_ZERO, GL_ZERO, GL_ONE);
            glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_SUBTRACT);
            List<Integer> before = state();
            for (boolean published : new boolean[]{false, true}) for (int mode = 0; mode < 3; mode++) {
                fixture.graphics.setBatchingEnabled(mode != 0);
                fixture.graphics.setInstancedBatchingEnabled(mode == 2);
                fixture.layers(published, new ReplayLayer(1, true, .5f), new ReplayLayer(2, true, null));
                assertEquals(before, state());
                assertArrayEquals(original, stencil(0));
            }
            IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                    fixture.graphics.executeCapturedCommands(() -> SpriteSatInternalAccess.replayNativeLayers(
                            fixture.graphics, List.of(false, true), Boolean::booleanValue,
                            span -> fixture.graphics.registerCommand((x, y, width, height) -> {
                                if (span.getFirst()) throw new IllegalStateException("injected layered ghost draw failure");
                            })), 0, 0, 320, 224));
            assertEquals("injected layered ghost draw failure", failure.getMessage());
            assertEquals(before, state());
            assertArrayEquals(original, stencil(0));
            assertFalse(fixture.graphics.nativeSpriteOwnershipActive());
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void layeredColorOnlyScopesReleaseCoverageAfterDrawAndFailure() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(false);
            FboHelper.FboHandle scene = FboHelper.createColorOnly(320, 224, GL_CLAMP_TO_EDGE);
            assertNotNull(scene);
            try {
                for (boolean published : new boolean[]{false, true}) {
                    fixture.clear(scene.fboId());
                    fixture.layers(published, new ReplayLayer(1, true, .5f), new ReplayLayer(2, true, null));
                    rgb(pixels(), 41, 42, 128, 128, 0);
                    assertEquals(GL_NONE, glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,
                            GL_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
                    assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
                }
                assertThrows(IllegalStateException.class, () -> fixture.graphics.executeCapturedCommands(() ->
                        SpriteSatInternalAccess.replayNativeLayers(fixture.graphics, List.of(false, true), Boolean::booleanValue,
                                span -> fixture.graphics.registerCommand((x, y, width, height) -> {
                                    if (span.getFirst()) throw new IllegalStateException("injected layered scene failure");
                                })), 0, 0, 320, 224));
                assertFalse(fixture.graphics.nativeSpriteOwnershipActive());
                assertEquals(GL_NONE, glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,
                        GL_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                FboHelper.destroy(scene);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void layeredReplayRejectsMoreThan255SpansBeforeScopeOrEmission() {
        var frontToBack = new ArrayList<Boolean>();
        for (int rank = 0; rank < 255; rank++) frontToBack.add((rank & 1) != 0);
        var layers = NativeSpriteOwnership.layers(frontToBack, Boolean::booleanValue);
        assertEquals(255, layers.size());
        assertEquals(254, layers.getLast().rank());
        frontToBack.add(true);
        var graphics = new GraphicsManager();
        var emissions = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> SpriteSatInternalAccess.replayNativeLayers(
                graphics, frontToBack, Boolean::booleanValue, span -> emissions.incrementAndGet()));
        assertEquals(0, emissions.get());
        assertTrue(graphics.commands.isEmpty());
        assertFalse(graphics.nativeSpriteOwnershipActive());
    }

    @Test
    void terrainHiddenOpaqueWinnerStillExcludesLaterHighSpriteButItsTransparentHoleDoesNot() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            for (boolean batched : new boolean[]{false, true}) {
                fixture.graphics.setBatchingEnabled(batched);
                fixture.graphics.setInstancedBatchingEnabled(batched);
                fixture.terrain(true);
                for (boolean flipped : new boolean[]{false, true}) {
                    fixture.clear(0);
                    fixture.sat(1, false, flipped, 2, true);
                    ByteBuffer pixels = pixels();
                    pixel(pixels, flipped ? 46 : 41, 42, 3);
                    pixel(pixels, flipped ? 41 : 46, 42, 2);
                }
                fixture.clear(0);
                fixture.sat(1, true, false, 2, false);
                pixel(pixels(), 41, 42, 1);
                pixel(pixels(), 46, 42, 3);
                fixture.clear(0);
                fixture.sat(2, true, false, 1, false);
                pixel(pixels(), 41, 42, 2);
                pixel(pixels(), 46, 42, 2);
                fixture.terrain(false);
                fixture.clear(0);
                fixture.sat(1, false, false, 2, true);
                pixel(pixels(), 41, 42, 1);
                pixel(pixels(), 46, 42, 2);
                assertEquals(GL_NO_ERROR, glGetError());
            }
        }
    }

    @Test
    void publishedNativeRowsAndFlipsClaimOnlyTheirAdmittedOpaquePixelsInEveryBatchMode() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(true);
            for (int mode = 0; mode < 3; mode++) {
                fixture.graphics.setBatchingEnabled(mode != 0);
                fixture.graphics.setInstancedBatchingEnabled(mode == 2);
                for (boolean flip : new boolean[]{false, true}) for (boolean clipped : new boolean[]{false, true}) {
                    fixture.clear(0);
                    // Already admitted painter order: later high B, then earlier low A.
                    var frame = new SpritePresentation.Frame(List.of(
                            tile(2, true, false, 0, 8), tile(1, false, flip, clipped ? 2 : 0, clipped ? 6 : 8)));
                    fixture.graphics.executeCapturedCommands(() ->
                            SpritePresentationRenderer.drawNative(fixture.graphics, frame, 0, 0, layer -> true),
                            0, 0, 320, 224);
                    ByteBuffer pixels = pixels();
                    pixel(pixels, flip ? 46 : 41, 41, clipped ? 2 : 3);
                    pixel(pixels, flip ? 46 : 41, 43, 3);
                    pixel(pixels, flip ? 41 : 46, 43, 2);
                    pixel(pixels, flip ? 46 : 41, 47, clipped ? 2 : 3);
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void perObjectPaletteThreeTerrainExemptionSurvivesSatAdmissionAndBothGpuPaths() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            for (boolean batched : new boolean[]{false, true}) {
                fixture.graphics.setBatchingEnabled(batched);
                fixture.graphics.setInstancedBatchingEnabled(batched);
                for (int paletteLine : new int[]{0, 3}) {
                    fixture.terrain(true, paletteLine);
                    fixture.clear(0);
                    fixture.graphics.executeCapturedCommands(() -> {
                        fixture.graphics.beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224));
                        fixture.graphics.setCurrentSpriteTileOcclusionPaletteMask(0b0111);
                        fixture.submit(1, false, false);
                        fixture.graphics.setCurrentSpriteTileOcclusionPaletteMask(0xF);
                        fixture.submit(2, true, false);
                        fixture.graphics.endSpriteSatCollectionAndReplay();
                    }, 0, 0, 320, 224);
                    pixel(pixels(), 41, 42, paletteLine == 3 ? 1 : 3);
                    pixel(pixels(), 46, 42, 2);
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void existingStencilContentsBindingsAndBothFaceStatesSurviveRepeatedScopesAndFailures() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(true);
            fixture.clear(0);
            fixture.sat(1, false, false, 2, true); // retire initial palette residency before foreign-state setup
            glStencilMask(0xFF);
            glClearStencil(0x66);
            glClear(GL_STENCIL_BUFFER_BIT);
            byte[] original = stencil(0);
            FboHelper.FboHandle foreignRead = FboHelper.createColorOnly(16, 16, GL_CLAMP_TO_EDGE);
            assertNotNull(foreignRead);
            int foreignRenderbuffer = glGenRenderbuffers();
            int foreignVertexArray = glGenVertexArrays();
            int foreignBuffer = glGenBuffers();
            int[] foreignTextures = new int[7];
            try {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, foreignRead.fboId());
                glBindRenderbuffer(GL_RENDERBUFFER, foreignRenderbuffer);
                glBindVertexArray(foreignVertexArray);
                glEnableVertexAttribArray(1);
                glBindBuffer(GL_ARRAY_BUFFER, foreignBuffer);
                glUseProgram(fixture.graphics.getDebugShaderProgram().getProgramId());
                for (int unit = 0; unit < foreignTextures.length; unit++) {
                    foreignTextures[unit] = glGenTextures();
                    glActiveTexture(GL_TEXTURE0 + unit);
                    glBindTexture(GL_TEXTURE_2D, foreignTextures[unit]);
                }
                glActiveTexture(GL_TEXTURE3);
                glEnable(GL_STENCIL_TEST);
                glStencilFuncSeparate(GL_FRONT, GL_NEVER, 3, 0x7F);
                glStencilFuncSeparate(GL_BACK, GL_LESS, 9, 0x37);
                glStencilMaskSeparate(GL_FRONT, 0x5A);
                glStencilMaskSeparate(GL_BACK, 0xA5);
                glStencilOpSeparate(GL_FRONT, GL_INVERT, GL_DECR, GL_REPLACE);
                glStencilOpSeparate(GL_BACK, GL_ZERO, GL_INCR, GL_KEEP);
                glClearStencil(37);
                glEnable(GL_SCISSOR_TEST);
                glScissor(32, 170, 24, 24);
                glEnable(GL_DEPTH_TEST);
                glDepthMask(true);
                glEnable(GL_BLEND);
                glBlendFuncSeparate(GL_ONE, GL_ZERO, GL_ZERO, GL_ONE);
                glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_SUBTRACT);
                List<Integer> before = state();
                for (int repeat = 0; repeat < 2; repeat++) {
                    fixture.sat(1, false, false, 2, true);
                    assertEquals(before, state());
                    assertArrayEquals(original, stencil(0));
                }
                assertThrows(IllegalStateException.class, () -> fixture.graphics.executeCapturedCommands(() ->
                        fixture.graphics.withNativeSpriteOwnership(() -> fixture.graphics.registerCommand((x, y, w, h) -> {
                            throw new IllegalStateException("injected native draw failure");
                        })), 0, 0, 320, 224));
                assertEquals(before, state(), "the queued end scope must unwind even after a failed draw");
                assertArrayEquals(original, stencil(0));
                assertFalse(fixture.graphics.nativeSpriteOwnershipActive());
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glBindRenderbuffer(GL_RENDERBUFFER, 0);
                glBindVertexArray(0); glBindBuffer(GL_ARRAY_BUFFER, 0); glUseProgram(0);
                glDeleteVertexArrays(foreignVertexArray); glDeleteBuffers(foreignBuffer);
                glDeleteTextures(foreignTextures);
                glDeleteRenderbuffers(foreignRenderbuffer);
                FboHelper.destroy(foreignRead);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void colorOnlySceneTargetReceivesTemporaryCoverageWithoutKeepingTheAttachment() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(true);
            FboHelper.FboHandle scene = FboHelper.createColorOnly(320, 224, GL_CLAMP_TO_EDGE);
            assertNotNull(scene);
            try {
                for (int repeat = 0; repeat < 2; repeat++) {
                    fixture.clear(scene.fboId());
                    assertEquals(0, NativeSpriteOwnership.stencilBits());
                    fixture.sat(1, false, false, 2, true);
                    pixel(pixels(), 41, 42, 3);
                    pixel(pixels(), 46, 42, 2);
                    assertEquals(GL_NONE, glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,
                            GL_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
                    assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
                }
                assertThrows(IllegalStateException.class, () -> fixture.graphics.executeCapturedCommands(() ->
                        fixture.graphics.withNativeSpriteOwnership(() -> fixture.graphics.registerCommand((x, y, w, h) -> {
                            throw new IllegalStateException("injected scene draw failure");
                        })), 0, 0, 320, 224));
                assertEquals(GL_NONE, glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,
                        GL_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                FboHelper.destroy(scene);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    private static SpritePresentation.Tile tile(int pattern, boolean high, boolean flip, int start, int end) {
        return new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, pattern, 0, flip, false, high,
                40, 40, 8, 8, true, 0xF, false, 1, start, end);
    }

    private static ByteBuffer pixels() {
        glFinish();
        ByteBuffer pixels = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static void pixel(ByteBuffer pixels, int x, int y, int color) {
        int offset = ((223 - y) * 320 + x) * 4;
        for (int channel = 0; channel < 3; channel++) {
            assertEquals(color == channel + 1 ? 255 : 0, pixels.get(offset + channel) & 255,
                    "pixel " + x + "," + y + " channel " + channel);
        }
    }

    private static void rgb(ByteBuffer pixels, int x, int y, int red, int green, int blue) {
        int offset = ((223 - y) * 320 + x) * 4;
        int[] expected = {red, green, blue};
        for (int channel = 0; channel < expected.length; channel++) {
            int actual = pixels.get(offset + channel) & 255;
            assertTrue(Math.abs(expected[channel] - actual) <= 1,
                    "pixel " + x + "," + y + " channel " + channel + ": expected " + expected[channel] + ", got " + actual);
        }
    }

    private record ReplayLayer(int pattern, boolean high, Float ghostAlpha) { }

    private static byte[] stencil(int framebuffer) {
        int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            ByteBuffer data = BufferUtils.createByteBuffer(320 * 224);
            glReadPixels(0, 0, 320, 224, GL_STENCIL_INDEX, GL_UNSIGNED_BYTE, data);
            byte[] bytes = new byte[data.remaining()]; data.get(bytes); return bytes;
        } finally { glBindFramebuffer(GL_READ_FRAMEBUFFER, read); }
    }

    private static List<Integer> state() {
        List<Integer> values = new ArrayList<>();
        for (int name : new int[]{GL_DRAW_FRAMEBUFFER_BINDING, GL_READ_FRAMEBUFFER_BINDING, GL_RENDERBUFFER_BINDING,
                GL_CURRENT_PROGRAM, GL_VERTEX_ARRAY_BINDING, GL_ARRAY_BUFFER_BINDING, GL_ACTIVE_TEXTURE,
                GL_STENCIL_FUNC, GL_STENCIL_REF, GL_STENCIL_VALUE_MASK, GL_STENCIL_WRITEMASK,
                GL_STENCIL_FAIL, GL_STENCIL_PASS_DEPTH_FAIL, GL_STENCIL_PASS_DEPTH_PASS,
                GL_STENCIL_BACK_FUNC, GL_STENCIL_BACK_REF, GL_STENCIL_BACK_VALUE_MASK, GL_STENCIL_BACK_WRITEMASK,
                GL_STENCIL_BACK_FAIL, GL_STENCIL_BACK_PASS_DEPTH_FAIL, GL_STENCIL_BACK_PASS_DEPTH_PASS,
                GL_STENCIL_CLEAR_VALUE, GL_DEPTH_WRITEMASK, GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB,
                GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA, GL_BLEND_EQUATION_RGB, GL_BLEND_EQUATION_ALPHA}) {
            values.add(glGetInteger(name));
        }
        for (int name : new int[]{GL_STENCIL_TEST, GL_SCISSOR_TEST, GL_DEPTH_TEST, GL_BLEND}) values.add(glIsEnabled(name) ? 1 : 0);
        int[] box = new int[4]; glGetIntegerv(GL_SCISSOR_BOX, box);
        for (int component : box) values.add(component);
        values.add(glGetVertexAttribi(1, GL_VERTEX_ATTRIB_ARRAY_ENABLED));
        int active = glGetInteger(GL_ACTIVE_TEXTURE);
        for (int unit = 0; unit < 7; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            values.add(glGetInteger(GL_TEXTURE_BINDING_2D));
        }
        glActiveTexture(active);
        return values;
    }

    private static final class Fixture implements AutoCloseable {
        final PresentationOffscreenTestContext context = PresentationOffscreenTestContext.open(320, 224);
        final GraphicsManager graphics = new GraphicsManager();
        Fixture() throws Exception {
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, 320, 224);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
                Palette palette = new Palette();
                palette.setColor(1, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
                palette.setColor(2, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
                palette.setColor(3, new Palette.Color((byte) 0, (byte) 0, (byte) 255));
                for (int line = 0; line < 4; line++) graphics.cachePaletteTexture(palette, line);
                for (int id = 1; id <= 3; id++) {
                    Pattern pattern = new Pattern();
                    for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                        pattern.setPixel(x, y, (byte) (id == 1 && x >= 4 ? 0 : id));
                    }
                    graphics.cachePatternTexture(pattern, id);
                }
            } catch (Exception | Error failure) { close(); throw failure; }
        }
        void terrain(boolean high) {
            terrain(high, 0);
        }
        void terrain(boolean high, int paletteLine) {
            TilePriorityFBO priority = graphics.getTilePriorityFBO(320, 224);
            assertNotNull(priority);
            assertTrue(priority.isInitialized());
            glDisable(GL_SCISSOR_TEST);
            priority.begin();
            glClearColor(high ? 1 : 0, paletteLine / 3f, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            priority.end();
        }
        void clear(int target) {
            glBindFramebuffer(GL_FRAMEBUFFER, target);
            glViewport(0, 0, 320, 224);
            glDisable(GL_DITHER); glDisable(GL_SCISSOR_TEST); glDisable(GL_STENCIL_TEST); glDisable(GL_DEPTH_TEST);
            glClearColor(0, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT);
        }
        void sat(int first, boolean firstHigh, boolean flip, int second, boolean secondHigh) {
            graphics.executeCapturedCommands(() -> {
                graphics.beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224));
                submit(first, firstHigh, flip); submit(second, secondHigh, false);
                graphics.endSpriteSatCollectionAndReplay();
            }, 0, 0, 320, 224);
        }
        void layers(boolean published, ReplayLayer... layers) {
            graphics.executeCapturedCommands(() -> {
                if (published) {
                    var tiles = java.util.Arrays.stream(layers).map(layer -> new SpritePresentation.Tile(
                            SpritePresentation.Layer.OBJECT, layer.pattern(), 0, false, false, layer.high(),
                            40, 40, 8, 8, true, 0xF, layer.ghostAlpha() != null,
                            layer.ghostAlpha() == null ? 1 : layer.ghostAlpha(), 0, 8,
                            SpritePresentation.Subject.WORLD, true, SpriteSatEntry.PixelOperation.NORMAL)).toList();
                    SpritePresentationRenderer.drawNative(graphics, new SpritePresentation.Frame(tiles.reversed()),
                            0, 0, layer -> true);
                } else {
                    graphics.beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224));
                    for (ReplayLayer layer : layers) {
                        graphics.endGhostRenderEffect();
                        if (layer.ghostAlpha() != null) graphics.beginGhostRenderEffect(layer.ghostAlpha(), true);
                        submit(layer.pattern(), layer.high(), false);
                    }
                    graphics.endGhostRenderEffect();
                    graphics.endSpriteSatCollectionAndReplay();
                }
            }, 0, 0, 320, 224);
        }
        private void submit(int pattern, boolean high, boolean flip) {
            graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(40, 40, 1, 1, pattern, pattern,
                    0, flip, false, high, false, SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, null));
        }
        @Override public void close() {
            try { graphics.cleanup(); RenderContext.reset(); } finally { context.close(); }
        }
    }
}
