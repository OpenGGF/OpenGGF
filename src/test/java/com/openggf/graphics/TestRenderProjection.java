package com.openggf.graphics;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.PatternDesc;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

class TestRenderProjection {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    public static final class CreatorProbe {
        public static void install(GraphicsManager graphics,RenderProjection projection) {
            GraphicsProjectionAccess.install(graphics,projection);
        }
        public static java.util.function.BiConsumer<GraphicsManager,RenderProjection> installer() {
            return GraphicsProjectionAccess::install;
        }
    }
    private static final class Projection implements RenderProjection {
        final float[] scene = new float[16], framebuffer = new float[16];
        boolean offscreen;
        public float[] getProjectionMatrixBuffer() { return offscreen ? framebuffer : scene; }
        public boolean isFBOProjectionActive() { return offscreen; }
        public int getCurrentDisplayHeight() { return offscreen ? 256 : 224; }
    }

    @Test void creatorSurfaceKeepsRenderingAndCourseControlWithoutSessionOrchestration() {
        var types=com.openggf.mods.code.ModApiSignatureSurface.recursiveTypes().stream()
                .map(Class::getName).collect(java.util.stream.Collectors.toSet());
        assertFalse(types.contains(BatchedPatternRenderer.class.getName()));
        assertFalse(types.contains(InstancedPatternRenderer.class.getName()));
        assertFalse(types.contains(PatternRenderCommand.class.getName()), "implementations of a pinned interface are not automatically supported API");
        assertFalse(types.contains("com.openggf.Engine"));
        assertFalse(types.contains("com.openggf.GameLoop"));
        assertFalse(types.contains("com.openggf.game.session.GameplayModeContext"));
        for (String supported:java.util.List.of(
                "com.openggf.graphics.GraphicsManager", "com.openggf.graphics.RenderProjection",
                "com.openggf.game.GameModule", "com.openggf.game.mode.CourseControl",
                "com.openggf.level.objects.ObjectManager", "com.openggf.level.objects.ObjectQuery",
                "com.openggf.trace.TraceEvent$CnzSlotMachineState", "com.openggf.trace.TraceEvent$S2TornadoState")) {
            assertTrue(types.contains(supported),supported);
        }
    }

    @Test void projectionSwitchingAndSafeAreaRestorationKeepHostState() {
        var graphics = new GraphicsManager();
        var projection = new Projection();
        GraphicsProjectionAccess.install(graphics,projection);
        assertSame(projection.scene, graphics.getProjectionMatrixBuffer());
        projection.offscreen = true;
        assertSame(projection.framebuffer, graphics.getProjectionMatrixBuffer());
        graphics.beginSafeAreaProjection(400, 224);
        assertNotSame(projection.framebuffer, graphics.getProjectionMatrixBuffer());
        graphics.endSafeAreaProjection();
        assertSame(projection.framebuffer, graphics.getProjectionMatrixBuffer());
        projection.offscreen = false;
        assertSame(projection.scene, graphics.getProjectionMatrixBuffer());
        GraphicsProjectionAccess.install(graphics,null);
        assertNull(graphics.getProjectionMatrixBuffer());
    }

    @Test void allPatternPathsUseFramebufferHeightAndRestoreConfiguredHeight() throws Exception {
        var graphics = new GraphicsManager();
        var projection = new Projection();
        GraphicsProjectionAccess.install(graphics,projection);
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        var batched = new BatchedPatternRenderer(graphics, config);
        var instanced = new InstancedPatternRenderer(graphics, config);
        for (boolean offscreen : new boolean[]{false, true, false}) {
            projection.offscreen = offscreen;
            int height = offscreen ? 256 : 224;
            batched.beginBatch(); instanced.beginBatch();
            assertEquals(height, field(batched, "batchDisplayHeight"));
            assertEquals(height, field(instanced, "batchDisplayHeight"));
            graphics.initHeadless(); graphics.cachePatternTexture(new Pattern(), PatternAtlasRange.OBJECTS.base());
            graphics.setHeadlessMode(false); // Command construction only; no GL execution.
            PaletteTextureTestAccess.set(graphics, "combinedPaletteTextureId", 0);
            graphics.renderPatternWithId(PatternAtlasRange.OBJECTS.base(), new PatternDesc(), 8, 16);
            graphics.renderPatternWithIdScaled(PatternAtlasRange.OBJECTS.base(), new PatternDesc(), -2.25f, 16.5f, 11.75f, 3.25f);
            graphics.renderStripPatternWithId(PatternAtlasRange.OBJECTS.base(), new PatternDesc(), 8, 16, 2);
            try {
                assertEquals((float)(height - 16 - 8), field(graphics.commands.get(0), "y"));
                assertEquals(height - 16.5f - 3.25f, field(graphics.commands.get(1), "y"));
                assertEquals((float)(height - 16 - 2), field(graphics.commands.get(2), "y"));
            } finally {
                graphics.commands.forEach(GLCommandable::discard); graphics.commands.clear();
                graphics.setHeadlessMode(true); graphics.resetState();
            }
        }
    }

    @Test void creatorsCannotInstallRenderingCallbacksDirectlyOrThroughHiddenMethodReferences() throws Exception {
        String name=CreatorProbe.class.getName();
        var jar=temp.resolve("creator-projection-probe.jar");
        try (var input=getClass().getResourceAsStream("/"+name.replace('.','/')+".class");
             var output=new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.jar.JarEntry(name.replace('.','/')+".class"));
            output.write(input.readAllBytes());output.closeEntry();
        }
        ClassLoader engineParent=getClass().getClassLoader();
        ClassLoader filteredParent=new ClassLoader(engineParent) {
            @Override protected Class<?> loadClass(String requested,boolean resolve) throws ClassNotFoundException {
                if (requested.equals(name)) throw new ClassNotFoundException(requested);
                return super.loadClass(requested,resolve);
            }
        };
        var graphics=new GraphicsManager(); var projection=new Projection();
        java.util.function.BiConsumer<GraphicsManager,RenderProjection> host=GraphicsProjectionAccess::install;
        host.accept(graphics,projection);
        try (var loader=new com.openggf.mods.code.ModDependencyClassLoader("probe",
                new java.net.URL[]{jar.toUri().toURL()},filteredParent,java.util.List.of())) {
            var creator=loader.loadClass(name);
            var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,
                    ()->creator.getMethod("install",GraphicsManager.class,RenderProjection.class).invoke(null,graphics,null));
            assertInstanceOf(SecurityException.class,failure.getCause());
            @SuppressWarnings("unchecked")
            var installer=(java.util.function.BiConsumer<GraphicsManager,RenderProjection>)creator.getMethod("installer").invoke(null);
            assertThrows(SecurityException.class,()->installer.accept(graphics,null));
            assertSame(projection,graphics.getProjectionSource());
        }
        assertThrows(NoSuchMethodException.class,()->GraphicsManager.class.getMethod("setProjectionSource",RenderProjection.class));
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.get(object);
    }
}
