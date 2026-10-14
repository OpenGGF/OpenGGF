package com.openggf.level.render;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.mutators.TransientMutatorPolicySource.Entry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.GLCommand;
import com.openggf.mods.mutators.ScenePresentationPolicy;
import com.openggf.mods.mutators.ScenePresentationPolicy.Mode;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

class TestScenePresentationPass {
    private static Entry entry(Mode mode, int magnitude, long age, long remaining) {
        return new Entry("test", 1, new ScenePresentationPolicy(mode, magnitude, 120), age, remaining, false);
    }
    private static LogicalInputSnapshot input(int direction, boolean action) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(direction, 0,
                action ? InputActionMasks.ACTION_A : 0, 0, false, false), PlayerInputState.neutral());
    }

    private static ScenePresentationPass.Frame resolve(List<Entry> entries, LogicalInputSnapshot input,
                                                       float anchorX, float anchorY) {
        var player = input == null ? PlayerInputState.neutral() : input.player1();
        int held = player.heldMask();
        var intent = new ScenePresentationPass.Input(
                (held & (AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_RIGHT)) != 0,
                (held & (AbstractPlayableSprite.INPUT_UP | AbstractPlayableSprite.INPUT_DOWN)) != 0,
                player.actionHeldMask() != 0);
        return ScenePresentationPass.resolve(entries, intent, anchorX, anchorY);
    }

    @Test void logicalInputSquashesThePictureWithoutRemappingInputsAndExpiryIsImmediate() {
        var entries = List.of(entry(Mode.INPUT_SQUASH, 8, 17, 3));
        var neutral = resolve(entries, LogicalInputSnapshot.neutral(), 100, 75);
        assertFalse(neutral.active());
        var right = input(AbstractPlayableSprite.INPUT_RIGHT, false);
        var horizontal = resolve(entries, right, 100, 75);
        assertEquals(1.08f, horizontal.scaleX(), .00001); assertEquals(.92f, horizontal.scaleY(), .00001);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, right.player1().heldMask());
        var jump = resolve(entries, input(0, true), 100, 75);
        assertEquals(.92f, jump.scaleX(), .00001); assertEquals(1.08f, jump.scaleY(), .00001);
        assertFalse(resolve(List.of(entry(Mode.INPUT_SQUASH, 8, 20, 0)), right, 100, 75).active());
        assertEquals(horizontal, resolve(entries, right, 100, 75), "redraw is clock-neutral");
    }

    @Test void transformsComposeWithinTheGlobalPictureBoundsAndUseThePlayerAnchor() {
        var entries = List.of(entry(Mode.SHAKE, 6, 15, 10), entry(Mode.SHAKE, 6, 15, 10),
                entry(Mode.ZOOM, 8, 30, 10), entry(Mode.INPUT_SQUASH, 8, 30, 10));
        var frame = resolve(entries, input(AbstractPlayableSprite.INPUT_RIGHT, false), 89, 71);
        assertEquals(1.08f, frame.scaleX(), .00001); assertTrue(frame.scaleY() >= .92f);
        assertTrue(Math.abs(frame.shakeX()) <= 6); assertTrue(Math.abs(frame.shakeY()) <= 6);
        var zoom = resolve(List.of(entry(Mode.ZOOM, 8, 30, 10)), null, 89, 71);
        assertArrayEquals(new float[]{89, 71}, zoom.source(89, 71, 528, 224), .00001f);
        assertFalse(resolve(List.of(entry(Mode.NEGATIVE, 0, 15, 0)), null, 89, 71).active());
    }

    @Test void offscreenPixelsMatchTheLogicalTransformsAtNativeAndWideSizes() {
        withContext(() -> {
            var pass = new ScenePresentationPass(); var graphics = new GraphicsManager();
            int fbo = 0, texture = 0, otherFbo = 0, otherTexture = 0, oldTexture = 0, oldVao = 0;
            try {
                texture = colorTexture(1100, 500); fbo = framebuffer(texture);
                otherTexture = colorTexture(1, 1); otherFbo = framebuffer(otherTexture);
                oldTexture = glGenTextures(); oldVao = glGenVertexArrays();
                glDisable(GL_DITHER);
                for (int logicalWidth : new int[]{320, 528}) for (int scale : new int[]{1, 2}) {
                    int width = logicalWidth * scale, height = 224 * scale;
                    var picture = picture(logicalWidth, 224);
                    for (Mode mode : new Mode[]{Mode.NEGATIVE, Mode.MIRROR_HORIZONTAL, Mode.MIRROR_VERTICAL,
                            Mode.SHAKE, Mode.ZOOM, Mode.INPUT_SQUASH, Mode.SCENERY}) {
                        int magnitude = switch (mode) { case SHAKE -> 6; case ZOOM, INPUT_SQUASH -> 8; default -> 0; };
                        var frame = resolve(List.of(entry(mode, magnitude, 15, 120)),
                                input(AbstractPlayableSprite.INPUT_RIGHT, false), 93, 105);
                        prepareCaller(graphics, fbo, otherFbo, oldVao, oldTexture, width, height);
                        pass.render(graphics, frame, picture, () -> { }, () -> { }, 0, 0, logicalWidth, 224);
                        assertCallerState(graphics, fbo, otherFbo, oldVao, oldTexture, width, height);
                        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
                        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
                        glReadPixels(9, 7, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                        for (int y = 3; y < height; y += 17) for (int x = 3; x < width; x += 19) {
                            float[] source = frame.source((x + .5f) / scale, (height - y - .5f) / scale, logicalWidth, 224);
                            int expected = source[0] < 0 || source[1] < 0 || source[0] >= logicalWidth || source[1] >= 224
                                    ? 0xff204060 : picture.argb()[(int) source[1] * logicalWidth + (int) source[0]];
                            int offset = (y * width + x) * 4;
                            for (int channel = 0; channel < 3; channel++) {
                                int color = (expected >> (16 - channel * 8)) & 255;
                                if (frame.negative()) color = 255 - color;
                                assertEquals(color, pixels.get(offset + channel) & 255, 1, mode + " at " + x + "," + y);
                            }
                            assertEquals(255, pixels.get(offset + 3) & 255);
                        }
                        assertEquals(GL_NO_ERROR, glGetError());
                    }
                }
            } finally {
                pass.cleanup(); glBindFramebuffer(GL_FRAMEBUFFER, 0); glBindVertexArray(0);
                glDeleteVertexArrays(oldVao); glDeleteTextures(oldTexture);
                glDeleteFramebuffers(fbo); glDeleteFramebuffers(otherFbo);
                glDeleteTextures(texture); glDeleteTextures(otherTexture);
            }
        });
    }

    @Test void blueprintFiltersTerrainBeforeThePlayerAndPreservesRomSceneryAndFaultState() {
        withContext(() -> {
            var pass = new ScenePresentationPass(); var graphics = new GraphicsManager();
            int texture = colorTexture(550, 240), fbo = framebuffer(texture);
            int readTexture = colorTexture(1, 1), readFbo = framebuffer(readTexture);
            int oldTexture = glGenTextures(), oldVao = glGenVertexArrays();
            try {
                for (int width : new int[]{320, 528}) {
                    prepareCaller(graphics, fbo, readFbo, oldVao, oldTexture, width, 224);
                    var frame = resolve(List.of(entry(Mode.OUTLINE, 10, 7, 50)), null, 100, 100);
                    pass.render(graphics, frame, picture(width, 224),
                            () -> rect(30, 30, 100, 80, 1, 0, 0),
                            () -> rect(60, 50, 16, 24, 1, 1, 0), 0, 0, width, 224);
                    assertCallerState(graphics, fbo, readFbo, oldVao, oldTexture, width, 224);
                    glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
                    assertArrayEquals(new int[]{255, 255, 0, 255}, pixel(9 + 65, 7 + 60), "native player drawn after outline");
                    int[] interior = pixel(9 + 45, 7 + 45);
                    assertTrue(interior[2] > interior[0] && interior[0] < 20, "terrain interior is blueprint blue");
                    int[] edge = pixel(9 + 30, 7 + 45);
                    assertTrue(edge[1] > 150 && edge[2] > 200, "one logical pixel silhouette outline");
                    int expected = picture(width, 224).argb()[(224 - 151) * width + 200];
                    assertEquals(expected >> 16 & 255, pixel(9 + 200, 7 + 150)[0], "background is unfiltered");
                    prepareCaller(graphics, fbo, readFbo, oldVao, oldTexture, width, 224);
                    graphics.setUseUnderwaterPaletteForBackground(false);
                    graphics.setUseSpritePriorityShader(false);
                    assertThrows(IllegalStateException.class, () -> pass.render(graphics, frame, null,
                            () -> { graphics.setUseUnderwaterPaletteForBackground(true);
                                graphics.setUseSpritePriorityShader(true);
                                glDisable(GL_DEPTH_TEST); glBindFramebuffer(GL_FRAMEBUFFER, 0);
                                throw new IllegalStateException("native producer fault"); }, () -> fail("sprites after fault"),
                            0, 0, width, 224));
                    assertCallerState(graphics, fbo, readFbo, oldVao, oldTexture, width, 224);
                    assertFalse(graphics.isUseUnderwaterPaletteForBackground());
                    assertFalse(graphics.isUseSpritePriorityShader());
                    assertEquals(GL_NO_ERROR, glGetError());
                }
            } finally {
                pass.cleanup(); glBindFramebuffer(GL_FRAMEBUFFER, 0); glBindVertexArray(0);
                glDeleteVertexArrays(oldVao); glDeleteTextures(oldTexture);
                glDeleteFramebuffers(fbo); glDeleteFramebuffers(readFbo); glDeleteTextures(texture); glDeleteTextures(readTexture);
            }
        });
    }

    @Test void queuedWorldScopesKeepIndependentPicturesAndUnwindBeforeNativeHud() {
        withContext(() -> {
            var pass = new ScenePresentationPass(); var graphics = new GraphicsManager(); graphics.setGlInitialized(true);
            int texture = colorTexture(550, 240), fbo = framebuffer(texture);
            int readTexture = colorTexture(1, 1), readFbo = framebuffer(readTexture);
            int oldTexture = glGenTextures(), oldVao = glGenVertexArrays();
            try {
                prepareCaller(graphics, fbo, readFbo, oldVao, oldTexture, 528, 224);
                var first = pass.open(graphics,
                        resolve(List.of(entry(Mode.NEGATIVE, 0, 1, 20)), null, 100, 100),
                        picture(528, 224), 528, 224);
                graphics.registerCommand(first.beginCommand());
                graphics.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> rect(30, 40, 16, 16, 1, 0, 0)));
                graphics.registerCommand(first.endCommand());
                graphics.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
                    assertCallerState(graphics, fbo, readFbo, oldVao, oldTexture, 528, 224);
                    glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
                    assertArrayEquals(new int[]{0, 255, 255, 255}, pixel(9 + 35, 7 + 45));
                    glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
                }));
                var second = pass.open(graphics,
                        resolve(List.of(entry(Mode.MIRROR_HORIZONTAL, 0, 10, 20)), null, 200, 100),
                        picture(528, 224), 528, 224);
                graphics.registerCommand(second.beginCommand());
                graphics.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> rect(80, 40, 16, 16, 1, 0, 0)));
                graphics.registerCommand(second.endCommand());
                graphics.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
                    // A native HUD submission follows the world pass and keeps its original position/colour.
                    rect(9 + 10, 7 + 10, 8, 8, 1, 0, 1);
                }));
                graphics.flushWithCamera((short) 0, (short) 0, (short) 528, (short) 224);
                assertEquals(fbo, glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
                glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
                assertArrayEquals(new int[]{255, 0, 0, 255}, pixel(9 + 528 - 85, 7 + 45));
                assertArrayEquals(new int[]{255, 0, 255, 255}, pixel(9 + 12, 7 + 12));

                prepareCaller(graphics, fbo, readFbo, oldVao, oldTexture, 528, 224);
                var failed = pass.open(graphics, ScenePresentationPass.Frame.stock(0, 0), null, 528, 224);
                graphics.registerCommand(failed.beginCommand());
                graphics.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
                    glViewport(0, 0, 1, 1); throw new IllegalStateException("queued terrain fault");
                }));
                graphics.registerCommand(failed.endCommand());
                graphics.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> fail("HUD after failed world")));
                assertThrows(IllegalStateException.class,
                        () -> graphics.flushWithCamera((short) 0, (short) 0, (short) 528, (short) 224));
                assertCallerState(graphics, fbo, readFbo, oldVao, oldTexture, 528, 224);
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                graphics.discardQueuedCommands(); pass.cleanup(); glBindFramebuffer(GL_FRAMEBUFFER, 0); glBindVertexArray(0);
                glDeleteVertexArrays(oldVao); glDeleteTextures(oldTexture);
                glDeleteFramebuffers(fbo); glDeleteFramebuffers(readFbo); glDeleteTextures(texture); glDeleteTextures(readTexture);
            }
        });
    }

    private static void prepareCaller(GraphicsManager graphics, int fbo, int readFbo, int vao, int texture, int width, int height) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo); glViewport(9, 7, width, height);
        graphics.setViewport(9, 7, width, height);
        glDisable(GL_SCISSOR_TEST); glClearColor(32 / 255f, 64 / 255f, 96 / 255f, 1); glClear(GL_COLOR_BUFFER_BIT);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo); glBindVertexArray(vao); glUseProgram(0);
        glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, texture);
        glEnable(GL_DEPTH_TEST); glEnable(GL_SCISSOR_TEST); glScissor(9, 7, width, height);
        glEnable(GL_BLEND); glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_ADD);
        glBlendFuncSeparate(GL_ONE, GL_ZERO, GL_ZERO, GL_ONE);
        // The picture uploader must use its tight buffer rather than these caller strides.
        glPixelStorei(GL_UNPACK_ROW_LENGTH, width + 5); glPixelStorei(GL_UNPACK_SKIP_ROWS, 2);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 3);
    }
    private static void assertCallerState(GraphicsManager graphics, int fbo, int readFbo, int vao, int texture, int width, int height) {
        assertEquals(fbo, glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)); assertEquals(readFbo, glGetInteger(GL_READ_FRAMEBUFFER_BINDING));
        int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport); assertArrayEquals(new int[]{9, 7, width, height}, viewport);
        int[] scissor = new int[4]; glGetIntegerv(GL_SCISSOR_BOX, scissor); assertArrayEquals(viewport, scissor);
        assertEquals(9, graphics.getViewportX()); assertEquals(width, graphics.getViewportWidth());
        assertEquals(vao, glGetInteger(GL_VERTEX_ARRAY_BINDING)); assertEquals(0, glGetInteger(GL_CURRENT_PROGRAM));
        assertEquals(GL_TEXTURE2, glGetInteger(GL_ACTIVE_TEXTURE)); assertEquals(texture, glGetInteger(GL_TEXTURE_BINDING_2D));
        assertTrue(glIsEnabled(GL_DEPTH_TEST)); assertTrue(glIsEnabled(GL_SCISSOR_TEST)); assertTrue(glIsEnabled(GL_BLEND));
        assertEquals(GL_FUNC_REVERSE_SUBTRACT, glGetInteger(GL_BLEND_EQUATION_RGB));
        assertEquals(GL_ONE, glGetInteger(GL_BLEND_SRC_RGB)); assertEquals(GL_ZERO, glGetInteger(GL_BLEND_DST_RGB));
        assertEquals(width + 5, glGetInteger(GL_UNPACK_ROW_LENGTH));
        assertEquals(2, glGetInteger(GL_UNPACK_SKIP_ROWS)); assertEquals(3, glGetInteger(GL_UNPACK_SKIP_PIXELS));
    }
    private static ZonePictureSource.Picture picture(int width, int height) {
        int[] colors = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            colors[y * width + x] = 0xff000000 | ((x / 8 * 7) & 255) << 16 | ((y / 8 * 11) & 255) << 8 | (x + y & 255);
        return new ZonePictureSource.Picture(width, height, colors);
    }
    private static void rect(int x, int y, int width, int height, float r, float g, float b) {
        glDisable(GL_BLEND); glEnable(GL_SCISSOR_TEST); glScissor(x, y, width, height);
        glClearColor(r, g, b, 1); glClear(GL_COLOR_BUFFER_BIT); glDisable(GL_SCISSOR_TEST);
    }
    private static int[] pixel(int x, int y) {
        ByteBuffer value = BufferUtils.createByteBuffer(4); glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, value);
        return new int[]{value.get(0) & 255, value.get(1) & 255, value.get(2) & 255, value.get(3) & 255};
    }
    private static int colorTexture(int width, int height) {
        int texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return texture;
    }
    private static int framebuffer(int texture) {
        int fbo = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER)); return fbo;
    }
    private static void withContext(Runnable test) {
        if(com.openggf.tools.PresentationOffscreenTestContext.requested()) {
            try(var context=com.openggf.tools.PresentationOffscreenTestContext.open(64,64)) { test.run(); }
            return;
        }
        boolean nativeDisplay = Boolean.getBoolean("openggf.test.gl.native");
        glfwInitHint(GLFW_PLATFORM, nativeDisplay ? GLFW_ANY_PLATFORM : GLFW_PLATFORM_NULL);
        boolean initialized = glfwInit();
        if (nativeDisplay) assertTrue(initialized); else assumeTrue(initialized, "GLFW null platform unavailable");
        long window = 0;
        try {
            glfwDefaultWindowHints(); glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            if (!nativeDisplay) glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
            window = glfwCreateWindow(64, 64, "scene presentation regression", 0, 0);
            if (nativeDisplay) assertNotEquals(0, window); else assumeTrue(window != 0, "Surfaceless EGL unavailable");
            glfwMakeContextCurrent(window); GL.createCapabilities(); test.run();
        } finally {
            if (window != 0) { glfwDestroyWindow(window); GL.setCapabilities(null); }
            glfwTerminate(); glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
        }
    }
}
