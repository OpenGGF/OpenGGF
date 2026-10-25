package com.openggf.graphics;

import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.util.FboHelper;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.*;

/** Exact CPU projection ownership for companion rendering on a different logical height. */
@Isolated
class TestGraphicsProjectionScope {
    private record Projection(int height, float[] matrix) implements RenderProjection {
        Projection(int height) { this(height, ortho(height)); }
        public float[] getProjectionMatrixBuffer() { return matrix; }
        public boolean isFBOProjectionActive() { return true; }
        public int getCurrentDisplayHeight() { return height; }
    }

    @Test
    void restoresAllNullableSourceAndRawOverrideCombinationsByIdentity() {
        GraphicsManager graphics = new GraphicsManager();
        Projection temporary = new Projection(256);
        for (boolean hasSource : new boolean[]{false, true}) for (boolean hasRaw : new boolean[]{false, true}) {
            Projection previous = hasSource ? new Projection(224) : null;
            float[] raw = hasRaw ? ortho(400) : null;
            GraphicsProjectionAccess.install(graphics, previous);
            graphics.setProjectionMatrixBuffer(raw);
            try (var scope = GraphicsProjectionAccess.openProjection(graphics, temporary)) {
                assertSame(temporary, graphics.getProjectionSource());
                assertNull(graphics.rawProjectionMatrixBuffer());
                assertSame(temporary.matrix(), graphics.getProjectionMatrixBuffer());
            }
            assertSame(previous, graphics.getProjectionSource());
            assertSame(raw, graphics.rawProjectionMatrixBuffer());
            assertSame(raw != null ? raw : previous == null ? null : previous.matrix(),
                    graphics.getProjectionMatrixBuffer());
        }
    }

    @Test
    void nestedScopesRejectOutOfOrderCloseWithoutChangingEitherProjection() {
        GraphicsManager graphics = new GraphicsManager();
        Projection original = new Projection(224), outerProjection = new Projection(256), innerProjection = new Projection(320);
        float[] originalRaw = ortho(400), nestedRaw = ortho(480);
        GraphicsProjectionAccess.install(graphics, original);
        graphics.setProjectionMatrixBuffer(originalRaw);
        try (var outer = GraphicsProjectionAccess.openProjection(graphics, outerProjection)) {
            graphics.setProjectionMatrixBuffer(nestedRaw);
            try (var inner = GraphicsProjectionAccess.openProjection(graphics, innerProjection)) {
                assertThrows(IllegalStateException.class, outer::close);
                assertSame(innerProjection, graphics.getProjectionSource());
                assertNull(graphics.rawProjectionMatrixBuffer());
            }
            assertSame(outerProjection, graphics.getProjectionSource());
            assertSame(nestedRaw, graphics.rawProjectionMatrixBuffer());
        }
        assertSame(original, graphics.getProjectionSource());
        assertSame(originalRaw, graphics.rawProjectionMatrixBuffer());
    }

    @Test
    void foreignThreadCloseFailsWithoutMutationAndOwnerCanStillClose() throws Exception {
        GraphicsManager graphics = new GraphicsManager();
        Projection original = new Projection(224), temporary = new Projection(256);
        GraphicsProjectionAccess.install(graphics, original);
        try (var scope = GraphicsProjectionAccess.openProjection(graphics, temporary)) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread foreign = new Thread(() -> {
                try { scope.close(); } catch (Throwable caught) { failure.set(caught); }
            }, "projection-scope-foreign-close");
            foreign.start(); foreign.join();
            assertInstanceOf(IllegalStateException.class, failure.get());
            assertSame(temporary, graphics.getProjectionSource());
            assertNull(graphics.rawProjectionMatrixBuffer());
        }
        assertSame(original, graphics.getProjectionSource());
        assertNull(graphics.rawProjectionMatrixBuffer());
    }

    @Test
    void exceptionUnwindRestoresRawNullRatherThanMaterializingSourceFallback() {
        GraphicsManager graphics = new GraphicsManager();
        Projection original = new Projection(224), temporary = new Projection(256);
        GraphicsProjectionAccess.install(graphics, original);
        RuntimeException injected = new IllegalArgumentException("projection producer failed");
        assertSame(injected, assertThrows(IllegalArgumentException.class, () -> {
            try (var scope = GraphicsProjectionAccess.openProjection(graphics, temporary)) { throw injected; }
        }));
        assertSame(original, graphics.getProjectionSource());
        assertNull(graphics.rawProjectionMatrixBuffer());
        assertSame(original.matrix(), graphics.getProjectionMatrixBuffer());
        GraphicsProjectionAccess.install(graphics, temporary);
        assertSame(temporary.matrix(), graphics.getProjectionMatrixBuffer(), "restored raw null keeps fallback live");
    }

    @Test
    void closingScopeDoesNotDrainOrRebakeDeferredCommands() throws Exception {
        GraphicsManager graphics = new GraphicsManager();
        Projection original = new Projection(224), temporary = new Projection(256);
        GraphicsProjectionAccess.install(graphics, original);
        var entry = new PatternAtlas.Entry(0, 0, 0, 0, 0, 0f, 0f, 1f, 1f);
        PatternRenderCommand command;
        try (var scope = GraphicsProjectionAccess.openProjection(graphics, temporary)) {
            var desc = new PatternDesc();
            command = PatternRenderCommand.obtain(entry, 0, graphics);
            command.capturePalette(desc.getPaletteIndex());
            command.finish(desc.getHFlip(), desc.getVFlip(), desc.getPriority(), 4, 0, 8f, 8f);
            graphics.registerCommand(command);
        }
        try {
            assertEquals(1, graphics.commands.size(), "scope close never auto-drains a foreign render target");
            assertSame(command, graphics.commands.getFirst());
            var y = PatternRenderCommand.class.getDeclaredField("y");
            y.setAccessible(true);
            assertEquals(248f, y.getFloat(command), "height remains baked at construction");
            assertSame(original.matrix(), graphics.getProjectionMatrixBuffer(), "execution still resolves the live matrix");
        } finally { command.discard(); graphics.commands.clear(); }
    }

    @Test
    void nullProjectionClearsBothSourcesAndDoubleCloseCannotReplaceTheRestoredTuple() {
        GraphicsManager graphics = new GraphicsManager();
        Projection previous = new Projection(224);
        float[] raw = ortho(320);
        GraphicsProjectionAccess.install(graphics, previous);
        graphics.setProjectionMatrixBuffer(raw);
        var scope = GraphicsProjectionAccess.openProjection(graphics, null);
        assertNull(graphics.getProjectionSource());
        assertNull(graphics.rawProjectionMatrixBuffer());
        assertNull(graphics.getProjectionMatrixBuffer());
        scope.close();
        assertThrows(IllegalStateException.class, scope::close);
        assertSame(previous, graphics.getProjectionSource());
        assertSame(raw, graphics.rawProjectionMatrixBuffer());
    }

    @Test
    void activeBatchesKeepTheirOriginalHeightUntilTheyFinishAndRestartInsideTheScope() throws Exception {
        GraphicsManager graphics = new GraphicsManager();
        Projection previous = new Projection(224), companion = new Projection(256);
        GraphicsProjectionAccess.install(graphics, previous);
        var configuration = com.openggf.configuration.SonicConfigurationService.createStandalone();
        configuration.setConfigValue(com.openggf.configuration.SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        BatchedPatternRenderer batched = new BatchedPatternRenderer(graphics, configuration);
        InstancedPatternRenderer instanced = new InstancedPatternRenderer(graphics, configuration);
        var entry = new PatternAtlas.Entry(0, 0, 0, 0, 0, 0f, 0f, 1f, 1f);
        batched.beginBatch(); instanced.beginBatch();
        try (var scope = GraphicsProjectionAccess.openProjection(graphics, companion)) {
            // Deliberately violate the documented boundary: switching projection
            // cannot repair a batch begun under the old source, even for new art.
            assertTrue(batched.admitPattern(entry));
            batched.appendAdmittedPattern(entry, 0, false, false, 4, 0);
            assertTrue(instanced.admitPattern(entry));
            instanced.appendAdmittedPattern(entry, 0, false, false, false, 4, 0);
            assertEquals(216f, array(batched, "vertexData")[1]);
            assertEquals(216f, array(instanced, "instanceData")[1]);
            assertSame(companion.matrix(), graphics.getProjectionMatrixBuffer());
            batched.endBatch().discard(); instanced.endBatch().discard();
            batched.beginBatch(); instanced.beginBatch();
            assertTrue(batched.admitPattern(entry));
            batched.appendAdmittedPattern(entry, 0, false, false, 4, 0);
            assertTrue(instanced.admitPattern(entry));
            instanced.appendAdmittedPattern(entry, 0, false, false, false, 4, 0);
            assertEquals(248f, array(batched, "vertexData")[1]);
            assertEquals(248f, array(instanced, "instanceData")[1]);
            batched.endBatch().discard(); instanced.endBatch().discard();
        }
        assertSame(previous, graphics.getProjectionSource());
        assertNull(graphics.rawProjectionMatrixBuffer());
    }

    private static float[] array(Object renderer, String name) throws Exception {
        var field = renderer.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (float[]) field.get(renderer);
    }

    public static final class CreatorProbe {
        public static AutoCloseable open(GraphicsManager graphics, RenderProjection projection) {
            return GraphicsProjectionAccess.openProjection(graphics, projection);
        }
    }

    @Test
    void creatorClassLoaderCannotOpenEngineProjectionScope() throws Exception {
        String name = CreatorProbe.class.getName();
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        class CreatorLoader extends ClassLoader {
            CreatorLoader() { super(TestGraphicsProjectionScope.class.getClassLoader()); }
            Class<?> define(byte[] code) { return defineClass(name, code, 0, code.length); }
        }
        Class<?> creator = new CreatorLoader().define(bytes);
        GraphicsManager graphics = new GraphicsManager();
        Projection original = new Projection(224);
        GraphicsProjectionAccess.install(graphics, original);
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> creator.getMethod("open", GraphicsManager.class, RenderProjection.class).invoke(null, graphics, null));
        assertInstanceOf(SecurityException.class, failure.getCause());
        assertSame(original, graphics.getProjectionSource());
        assertNull(graphics.rawProjectionMatrixBuffer());
    }

    @Test
    void ownedEglCompanionPlacesRowZeroAt248InsteadOf216AndRestoresBothHeights() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (PresentationOffscreenTestContext context = PresentationOffscreenTestContext.open(320, 256)) {
            GraphicsManager graphics = new GraphicsManager();
            FboHelper.FboHandle target = null;
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setBatchingEnabled(false);
                graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
                Palette palette = new Palette();
                palette.setColor(1, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
                graphics.cachePaletteTexture(palette, 0);
                Pattern pattern = new Pattern();
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) 1);
                graphics.cachePatternTexture(pattern, 1);
                target = FboHelper.createColorOnly(320, 256, GL_CLAMP_TO_EDGE);
                assertNotNull(target);
                glBindFramebuffer(GL_FRAMEBUFFER, target.fboId());
                Projection scene = new Projection(224), companion = new Projection(256);
                GraphicsProjectionAccess.install(graphics, scene);
                graphics.setProjectionMatrixBuffer(companion.matrix());
                drawRowZero(graphics, 256);
                pixel(220, true); pixel(252, false); // matrix-only override leaves baked vertex Y at 216
                graphics.setProjectionMatrixBuffer(null);
                clear(graphics, 256);
                graphics.executeCapturedCommands(() -> {
                    try (var scope = GraphicsProjectionAccess.openProjection(graphics, companion)) {
                        graphics.renderPatternWithId(1, new PatternDesc(1), 4, 0);
                    }
                }, 0, 0, 320, 256);
                // Closing before execution keeps Y=248 but uses the restored 224 matrix,
                // projecting above the target. The scope must not auto-drain or rebake it.
                pixel(252, false); pixel(220, false);
                for (int priorHeight : new int[]{224, 256}) {
                    Projection previous = new Projection(priorHeight);
                    GraphicsProjectionAccess.install(graphics, previous);
                    try (var scope = GraphicsProjectionAccess.openProjection(graphics, companion)) {
                        drawRowZero(graphics, 256);
                        pixel(252, true); pixel(220, false); // source height and matrix now agree: Y=248
                    }
                    assertSame(previous, graphics.getProjectionSource());
                    assertNull(graphics.rawProjectionMatrixBuffer());
                    assertSame(previous.matrix(), graphics.getProjectionMatrixBuffer());
                    drawRowZero(graphics, priorHeight);
                    pixel(priorHeight - 4, true);
                    if (priorHeight == 224) pixel(252, false);
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                graphics.cleanup(); RenderContext.reset();
                glBindFramebuffer(GL_FRAMEBUFFER, 0); FboHelper.destroy(target);
            }
        }
    }

    private static float[] ortho(int height) {
        return new Matrix4f().ortho(0, 320, 0, height, -1, 1).get(new float[16]);
    }

    private static void drawRowZero(GraphicsManager graphics, int viewportHeight) {
        clear(graphics, viewportHeight);
        graphics.executeCapturedCommands(() -> graphics.renderPatternWithId(1, new PatternDesc(1), 4, 0),
                0, 0, 320, viewportHeight);
    }

    private static void clear(GraphicsManager graphics, int viewportHeight) {
        glViewport(0, 0, 320, viewportHeight);
        graphics.setViewport(0, 0, 320, viewportHeight);
        glDisable(GL_DITHER); glDisable(GL_SCISSOR_TEST); glDisable(GL_STENCIL_TEST); glDisable(GL_DEPTH_TEST);
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
    }

    private static void pixel(int y, boolean red) {
        ByteBuffer bytes = BufferUtils.createByteBuffer(4);
        glReadPixels(5, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        assertEquals(red ? 255 : 0, bytes.get(0) & 255, "red at GL Y=" + y);
        assertEquals(0, bytes.get(1) & 255, "green at GL Y=" + y);
        assertEquals(0, bytes.get(2) & 255, "blue at GL Y=" + y);
    }
}
