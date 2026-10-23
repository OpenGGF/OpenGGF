package com.openggf.game.sonic2.specialstage;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.GraphicsProjectionAccess;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.RenderProjection;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DRAFT from the S2 chain-pacing task (2026-10-22): indexed tile row oracles in
 * the real 256x256 background target, while the host remains 320x224. Does not
 * boot gameplay, read a ROM, or open audio. Production begin/end APIs only;
 * no private projection-state inspection and no desktop fallback.
 */
@Isolated
class TestSonic2SpecialStageBackgroundProjection {
    private static final int WIDTH = 320, HEIGHT = 224, FBO_SIZE = 256;

    @BeforeEach
    void configuredHostHeightIs224() {
        TestEnvironment.resetAll();
        GameServices.configuration().setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, HEIGHT);
    }

    @AfterEach
    void restoreServices() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest(name = "batch mode {0}, host projection source {1}")
    @CsvSource({"0,false", "0,true", "1,false", "1,true", "2,false", "2,true"})
    void retainedBackgroundCommandsBakeFullHeightAndShaderSamplesTheNativeTop(
            int mode, boolean sourcePresent) throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (PixelFixture f = new PixelFixture(sourcePresent)) {
            if (mode == 2) {
                assumeTrue(f.graphics.getInstancedShaderProgram() != null,
                        "instanced path unavailable; direct and batched cases remain separate");
            }
            f.graphics.setBatchingEnabled(mode != 0);
            f.graphics.setInstancedBatchingEnabled(mode == 2);
            var pool = new Sonic2SpecialStageManager.BackgroundCommandPool();
            f.graphics.executeCapturedCommands(() -> {
                f.background.beginFBOProjection();
                try {
                    assertFboProjection(f.graphics);
                    f.graphics.registerCommand(pool.obtainBegin(f.background));
                    f.tiles.rebuildBackgroundToFBO(indexedRows(), f.palettes);
                } finally {
                    f.background.endFBOProjection();
                }
                // Construction has ended before any retained GL command runs.
                assertSame(f.host, f.graphics.getProjectionSource());
                assertArrayEquals(f.sceneMatrix, f.graphics.getProjectionMatrixBuffer());
                f.graphics.registerCommand(pool.obtainEnd(f.background));
            }, 0, 0, WIDTH, HEIGHT);

            // OpenGL coordinates, independent of the renderer's Y conversion.
            ByteBuffer texture = texturePixels(f.background.getFBOTextureId());
            rgba(texture, FBO_SIZE, 4, 252, 255, 0, 0, 255);
            rgba(texture, FBO_SIZE, 4, 4, 0, 0, 255, 255);
            rgba(texture, FBO_SIZE, 12, 252, 0, 0, 0, 0);
            assertSame(f.host, f.graphics.getProjectionSource());
            assertArrayEquals(f.sceneMatrix, f.graphics.getProjectionMatrixBuffer());

            f.clearScreen();
            f.graphics.executeCapturedCommands(() ->
                    f.graphics.registerCommand(pool.obtainShader(f.background, 0, 0)),
                    0, 0, WIDTH, HEIGHT);
            screenPixel(36, 4, 255, 0, 0); // H32 begins at host X32; local X4 is row0's tile.
            screenPixel(4, 4, 0, 0, 0);    // Native H32 border remains black.
            screenPixel(44, 4, 255, 0, 255); // Transparent next tile leaves the deliberate clear.

            f.clearScreen();
            f.graphics.executeCapturedCommands(() ->
                    f.graphics.registerCommand(pool.obtainShader(f.background, 0, 248)),
                    0, 0, WIDTH, HEIGHT);
            screenPixel(36, 4, 0, 0, 255); // Independent wrap/sample control at background row31.

            // The retained tile pass must preserve an absent local override too,
            // not only restore the construction-time effective matrix values.
            if (f.host != null) {
                f.host.matrix = matrix(320, 240);
                assertSame(f.host.matrix, f.graphics.getProjectionMatrixBuffer(),
                        "host source must stay live after retained BEGIN/batch/END and shader replay");
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void sourceIdentityAndOverridePresenceSurviveNestedProjectionScopes() {
        for (boolean source : new boolean[]{false, true}) {
            for (boolean override : new boolean[]{false, true}) {
                var f = new ProjectionFixture(source, override);
                f.background.beginFBOProjection();
                assertFboProjection(f.graphics);
                RenderProjection outer = f.graphics.getProjectionSource();
                f.background.beginFBOProjection();
                assertFboProjection(f.graphics);
                f.background.endFBOProjection();
                assertSame(outer, f.graphics.getProjectionSource());
                assertFboProjection(f.graphics);
                f.background.endFBOProjection();
                f.assertOriginalOwnership();
                f.background.endFBOProjection(); // Empty end is harmless.
                f.assertOriginalOwnership();
            }
        }
    }

    @Test
    void headlessCleanupUnwindsOutstandingSourcesWithoutRequiringGl() {
        for (boolean source : new boolean[]{false, true}) {
            for (boolean override : new boolean[]{false, true}) {
                var f = new ProjectionFixture(source, override);
                f.graphics.setHeadlessMode(true);
                f.background.beginFBOProjection();
                f.background.beginFBOProjection();
                assertFboProjection(f.graphics);
                f.background.cleanup();
                f.assertOriginalOwnership();
                f.background.cleanup();
                f.assertOriginalOwnership();
            }
        }
    }

    @Test
    void managerUnwindsProjectionWhenBackgroundBatchConstructionThrows() throws Exception {
        for (boolean source : new boolean[]{false, true}) {
            for (boolean override : new boolean[]{false, true}) {
                var f = new ProjectionFixture(source, override);
                var manager = new Sonic2SpecialStageManager(
                        new com.openggf.game.sonic2.debug.Sonic2SpecialStageSpriteDebug(), null, f.graphics);
                var tiles = mock(Sonic2SpecialStageRenderer.class);
                when(tiles.needsStaticBackgroundRebuild(any(), any())).thenReturn(true);
                var failure = new IllegalStateException("injected batch-construction failure");
                doThrow(failure).when(tiles).rebuildBackgroundToFBO(any(), any());
                set(manager, "renderer", tiles);
                set(manager, "bgRenderer", f.background);
                // executeCapturedCommands also releases the unexecuted BEGIN;
                // construction failure must not leave a retained GL pass armed.
                assertSame(failure, assertThrows(IllegalStateException.class, () ->
                        f.graphics.executeCapturedCommands(() -> queueBackground(manager, f.graphics),
                                0, 0, WIDTH, HEIGHT)));
                f.assertOriginalOwnership();
            }
        }
    }

    @Test
    void failedRetainedTileCommandUnwindsTheTilePassAndKeepsHostSourceLive() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (PixelFixture f = new PixelFixture(true)) {
            var pool = new Sonic2SpecialStageManager.BackgroundCommandPool();
            var failure = new IllegalArgumentException("injected retained tile draw failure");
            assertSame(failure, assertThrows(IllegalArgumentException.class, () ->
                    f.graphics.executeCapturedCommands(() -> {
                        f.graphics.registerCommand(pool.obtainBegin(f.background));
                        f.graphics.registerCommand((x, y, w, h) -> { throw failure; });
                        f.graphics.registerCommand(pool.obtainEnd(f.background));
                    }, 0, 0, WIDTH, HEIGHT)));
            assertEquals(0, glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
            assertSame(f.host, f.graphics.getProjectionSource());
            f.host.matrix = matrix(320, 240);
            assertSame(f.host.matrix, f.graphics.getProjectionMatrixBuffer());
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    private static void assertFboProjection(GraphicsManager graphics) {
        RenderProjection source = graphics.getProjectionSource();
        assertNotNull(source, "a matrix alone does not set the pattern-coordinate height");
        assertTrue(source.isFBOProjectionActive());
        assertEquals(FBO_SIZE, source.getCurrentDisplayHeight());
        assertArrayEquals(matrix(FBO_SIZE, FBO_SIZE), graphics.getProjectionMatrixBuffer());
    }

    private static byte[] indexedRows() {
        byte[] mappings = new byte[32 * 32 * 2];
        mappings[1] = 1; // Red indexed tile, tx0/ty0.
        mappings[(31 * 32) * 2 + 1] = 2; // Blue indexed tile, tx0/ty31.
        return mappings;
    }

    private static Pattern solid(int index) {
        var pattern = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            pattern.setPixel(x, y, (byte) index);
        }
        return pattern;
    }

    private static float[] matrix(int width, int height) {
        return new Matrix4f().ortho2D(0, width, 0, height).get(new float[16]);
    }

    private static ByteBuffer texturePixels(int texture) {
        int active = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glBindTexture(GL_TEXTURE_2D, texture);
            var bytes = BufferUtils.createByteBuffer(FBO_SIZE * FBO_SIZE * 4);
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            return bytes;
        } finally {
            glBindTexture(GL_TEXTURE_2D, previous);
            glActiveTexture(active);
        }
    }

    private static void screenPixel(int x, int genesisY, int red, int green, int blue) {
        var pixel = BufferUtils.createByteBuffer(4);
        glReadPixels(x, HEIGHT - 1 - genesisY, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        rgba(pixel, 1, 0, 0, red, green, blue, 255);
    }

    private static void rgba(ByteBuffer pixels, int width, int x, int y,
                             int red, int green, int blue, int alpha) {
        int offset = (y * width + x) * 4;
        assertEquals(red, pixels.get(offset) & 255, "red at " + x + "," + y);
        assertEquals(green, pixels.get(offset + 1) & 255, "green at " + x + "," + y);
        assertEquals(blue, pixels.get(offset + 2) & 255, "blue at " + x + "," + y);
        assertEquals(alpha, pixels.get(offset + 3) & 255, "alpha at " + x + "," + y);
    }

    private static void queueBackground(Sonic2SpecialStageManager manager, GraphicsManager graphics) {
        try {
            Method method = Sonic2SpecialStageManager.class.getDeclaredMethod(
                    "queueStaticBackgroundRebuildIfNeeded", GraphicsManager.class);
            method.setAccessible(true);
            method.invoke(manager, graphics);
        } catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof RuntimeException runtime) throw runtime;
            if (wrapped.getCause() instanceof Error error) throw error;
            throw new AssertionError(wrapped.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static final class MutableProjection implements RenderProjection {
        float[] matrix = matrix(WIDTH, HEIGHT);
        @Override public float[] getProjectionMatrixBuffer() { return matrix; }
        @Override public boolean isFBOProjectionActive() { return false; }
        @Override public int getCurrentDisplayHeight() { return HEIGHT; }
    }

    private static final class ProjectionFixture {
        final GraphicsManager graphics = new GraphicsManager();
        final SpecialStageBackgroundRenderer background = new SpecialStageBackgroundRenderer(graphics);
        final MutableProjection host;
        final float[] override;
        ProjectionFixture(boolean sourcePresent, boolean overridePresent) {
            host = sourcePresent ? new MutableProjection() : null;
            override = overridePresent ? matrix(400, 240) : null;
            GraphicsProjectionAccess.install(graphics, host);
            graphics.setProjectionMatrixBuffer(override);
        }
        void assertOriginalOwnership() {
            assertSame(host, graphics.getProjectionSource());
            if (host != null) host.matrix = matrix(512, 256);
            if (override != null) {
                assertArrayEquals(override, graphics.getProjectionMatrixBuffer());
            } else if (host != null) {
                assertSame(host.matrix, graphics.getProjectionMatrixBuffer(),
                        "a previously absent local override must remain absent");
            } else {
                assertNull(graphics.getProjectionMatrixBuffer());
            }
        }
    }

    private static final class PixelFixture implements AutoCloseable {
        final PresentationOffscreenTestContext context = PresentationOffscreenTestContext.open(WIDTH, HEIGHT);
        final GraphicsManager graphics = new GraphicsManager();
        final SpecialStageBackgroundRenderer background = new SpecialStageBackgroundRenderer(graphics);
        final Sonic2SpecialStageRenderer tiles = new Sonic2SpecialStageRenderer(graphics);
        final Palette[] palettes = new Palette[4];
        final float[] sceneMatrix = matrix(WIDTH, HEIGHT);
        final MutableProjection host;
        PixelFixture(boolean sourcePresent) throws Exception {
            host = sourcePresent ? new MutableProjection() : null;
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, WIDTH, HEIGHT);
                graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
                GraphicsProjectionAccess.install(graphics, host);
                graphics.setProjectionMatrixBuffer(host == null ? sceneMatrix : null);
                assertEquals(HEIGHT, graphics.getConfiguredScreenHeightPx());
                for (int line = 0; line < palettes.length; line++) {
                    var palette = new Palette();
                    palette.setColor(1, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
                    palette.setColor(2, new Palette.Color((byte) 0, (byte) 0, (byte) 255));
                    palettes[line] = palette;
                    graphics.cachePaletteTexture(palette, line);
                }
                graphics.cachePatternTexture(solid(1), 1);
                graphics.cachePatternTexture(solid(2), 2);
                tiles.setPatternBases(0, 0);
                background.init();
                assertTrue(background.isInitialized());
                clearScreen();
            } catch (Exception | Error failure) {
                close();
                throw failure;
            }
        }
        void clearScreen() {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, WIDTH, HEIGHT);
            glDisable(GL_DITHER);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_STENCIL_TEST);
            glClearColor(1, 0, 1, 1);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        @Override public void close() {
            try {
                background.cleanup();
                graphics.cleanup();
                RenderContext.reset();
            } finally {
                context.close();
            }
        }
    }
}
