package com.openggf.graphics;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestPooledRenderCommandDiscard {
    private static final PatternAtlas.Entry ENTRY =
            new PatternAtlas.Entry(1, 1, 1, 0, 0, 0.0f, 0.0f, 1.0f, 1.0f);

    private GraphicsManager graphics;
    private SonicConfigurationService configuration;

    @BeforeEach
    void setUp() throws Exception {
        TestEnvironment.resetAll();
        graphics = new GraphicsManager();
        graphics.initHeadless();
        configuration = GameServices.configuration();
        clearPatternCommandPool();
    }

    @AfterEach
    void tearDown() throws Exception {
        graphics.resetState();
        clearPatternCommandPool();
    }

    @Test
    void patternCommandsReturnToPoolAcross600HeadlessDrops() throws Exception {
        GLCommandable first = patternCommand();
        graphics.registerCommand(first);
        graphics.flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);

        GLCommandable second = patternCommand();
        assertSame(first, second, "headless queue drop must recycle the actual pattern command");
        second.discard();

        Set<Object> identities = identitySet();
        for (int frame = 0; frame < 600; frame++) {
            GLCommandable command = patternCommand();
            identities.add(command);
            graphics.registerCommand(command);
            graphics.flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
        }
        assertEquals(1, identities.size());
    }

    @Test
    void batchCommandsRetainNativeBackingsAcross600ResetDrops() throws Exception {
        BatchedPatternRenderer renderer = new BatchedPatternRenderer(graphics, configuration);
        GLCommandable first = batchCommand(renderer, false);
        Object[] firstBackings = batchBackings(first, false);
        graphics.registerCommand(first);
        graphics.resetState();

        GLCommandable second = batchCommand(renderer, false);
        assertSame(first, second, "reset must recycle the actual batch command");
        assertBackingsSame(firstBackings, batchBackings(second, false));
        second.discard();

        assertBoundedDroppedFrames(renderer, false, first, firstBackings);
        renderer.cleanupHeadless();
    }

    @Test
    void shadowCommandsRetainNativeBackingsAcross600CleanupDrops() throws Exception {
        BatchedPatternRenderer renderer = new BatchedPatternRenderer(graphics, configuration);
        GLCommandable first = batchCommand(renderer, true);
        Object[] firstBackings = batchBackings(first, true);
        graphics.registerCommand(first);
        graphics.cleanup();

        GLCommandable second = batchCommand(renderer, true);
        assertSame(first, second, "cleanup must recycle the actual shadow command before renderer teardown");
        assertBackingsSame(firstBackings, batchBackings(second, true));
        second.discard();

        assertBoundedDroppedFrames(renderer, true, first, firstBackings);
        renderer.cleanupHeadless();
    }

    @Test
    void instancedCommandsReturnAfterEarlierThrowAndReuseNativeBackingFor600Drops() throws Exception {
        InstancedPatternRenderer renderer = new InstancedPatternRenderer(graphics, configuration);
        setField(renderer, "supported", true);
        GLCommandable first = instancedCommand(renderer);
        Object firstBacking = getField(first, "instanceBuffer");

        setBooleanField(graphics, "headlessMode", false);
        setBooleanField(graphics, "glInitialized", true);
        graphics.registerCommand((cameraX, cameraY, cameraWidth, cameraHeight) -> {
            throw new IllegalStateException("expected");
        });
        graphics.registerCommand(first);
        assertThrows(IllegalStateException.class,
                () -> graphics.flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224));
        setBooleanField(graphics, "headlessMode", true);
        setBooleanField(graphics, "glInitialized", false);

        GLCommandable second = instancedCommand(renderer);
        assertSame(first, second, "exception tail discard must recycle the actual instanced command");
        assertSame(firstBacking, getField(second, "instanceBuffer"));
        second.discard();

        Set<Object> commands = identitySet();
        Set<Object> backings = identitySet();
        for (int frame = 0; frame < 600; frame++) {
            GLCommandable command = instancedCommand(renderer);
            commands.add(command);
            backings.add(getField(command, "instanceBuffer"));
            graphics.registerCommand(command);
            graphics.flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
        }
        assertEquals(1, commands.size());
        assertEquals(1, backings.size());
        renderer.cleanupHeadless();
    }

    @Test
    void capturedFailureCleansFrameOnceDiscardsTailRestoresCaptureAndPropagatesOriginal() {
        TrackingGraphicsManager trackingGraphics = new TrackingGraphicsManager();
        trackingGraphics.setGlInitialized(true);
        TrackingCommand throwing = new TrackingCommand(true);
        TrackingCommand tail = new TrackingCommand(false);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> trackingGraphics.executeCapturedCommands(() -> {
                    trackingGraphics.registerCommand(throwing);
                    trackingGraphics.registerCommand(tail);
                }, 0, 0, 320, 224));

        assertEquals("expected", failure.getMessage());
        assertEquals(1, trackingGraphics.frameCleanups);
        assertEquals(1, throwing.executions);
        assertEquals(0, tail.executions);
        assertEquals(1, throwing.releases);
        assertEquals(1, tail.releases);

        trackingGraphics.setGlInitialized(false);
        TrackingCommand afterCapture = new TrackingCommand(false);
        trackingGraphics.registerCommand(afterCapture);
        trackingGraphics.flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
        assertEquals(1, afterCapture.releases,
                "the capture target must be restored before the exception leaves executeCapturedCommands");
    }

    @Test
    void acquisitionFailuresKeepManagerBeforeEntryBeforeDescriptorAndDoNotRestorePool() throws Exception {
        PatternRenderCommand first = (PatternRenderCommand) patternCommand(); first.recycle();
        NullPointerException missingManager = assertThrows(NullPointerException.class,
                () -> PatternRenderCommand.obtain(null, 11, null));
        assertEquals("graphicsManager", missingManager.getMessage());
        assertEquals(0, PatternRenderCommand.pooledCommandCount());
        assertNull(getField(first, "graphicsManager"));
        assertFalse((boolean) getField(first, "leased"));
        PatternRenderCommand second = (PatternRenderCommand) patternCommand(); second.recycle();
        assertThrows(NullPointerException.class,
                () -> PatternRenderCommand.obtain(null, 11, graphics));
        assertSame(graphics, getField(second, "graphicsManager"));
        assertEquals(11, getField(second, "paletteTextureId"));
        assertFalse((boolean) getField(second, "leased"));
        second.discard(); first.discard();
        assertEquals(0, PatternRenderCommand.pooledCommandCount());
    }

    @Test
    void displayFailureOccursAfterLeaseAndGeometryAndCanBeRecycled() throws Exception {
        PatternRenderCommand command = (PatternRenderCommand) patternCommand(); command.recycle();
        IllegalStateException expected = new IllegalStateException("display height");
        GraphicsProjectionAccess.install(graphics, new RenderProjection() {
            public float[] getProjectionMatrixBuffer() { return null; }
            public boolean isFBOProjectionActive() { return true; }
            public int getCurrentDisplayHeight() { throw expected; }
        });
        assertSame(expected, assertThrows(IllegalStateException.class,
                () -> {
                    PatternRenderCommand acquired = PatternRenderCommand.obtain(ENTRY, 11, graphics);
                    acquired.capturePalette(0);
                    acquired.finish(false, false, false, -3.25f, 6.5f, 17.75f, 4.125f);
                }));
        assertTrue((boolean) getField(command, "leased"));
        assertEquals(-3.25f, getField(command, "x"));
        assertEquals(17.75f, getField(command, "width"));
        assertEquals(4.125f, getField(command, "height"));
        assertEquals(216f, getField(command, "y"), "failed display lookup leaves the previous Y untouched");
        command.discard(); command.discard();
        assertEquals(1, PatternRenderCommand.pooledCommandCount());
        GraphicsProjectionAccess.install(graphics, null);
    }

    @Test
    void deferredPaletteFailureRecyclesAndNextLeaseClearsReplayCropPresentationAndGhostState() throws Exception {
        PatternRenderCommand command = (PatternRenderCommand) patternCommand();
        IllegalStateException expected = new IllegalStateException("deferred palette");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        command.paletteReplay((host, palette) -> {
            assertSame(graphics, host); assertEquals(0, palette); calls.incrementAndGet(); throw expected;
        });
        command.resolveStripTextureCoordinates(ENTRY, 1);
        setField(command, "presentationQuad", new float[8]);
        setField(command, "presentationCount", 2);
        setField(command, "presentationHue", .5f);
        setField(command, "presentationOpacity", .25f);
        setField(command, "ghostEffectActive", true);
        setField(command, "ghostColour", true);
        setField(command, "ghostAlpha", .125f);
        assertEquals(0, calls.get(), "palette replay is deferred to execute");
        assertSame(expected, assertThrows(IllegalStateException.class, () -> command.execute(0, 0, 320, 224)));
        assertEquals(1, calls.get());
        assertEquals(1, PatternRenderCommand.pooledCommandCount());
        PatternRenderCommand next = (PatternRenderCommand) patternCommand();
        assertSame(command, next);
        assertNull(getField(next, "paletteReplay")); assertNull(getField(next, "presentationQuad"));
        assertEquals(0, getField(next, "presentationCount"));
        assertEquals(0f, getField(next, "presentationHue"));
        assertEquals(1f, getField(next, "presentationOpacity"));
        assertFalse((boolean) getField(next, "textureCoordinatesResolved"));
        assertFalse((boolean) getField(next, "ghostEffectActive"));
        assertFalse((boolean) getField(next, "ghostColour"));
        assertEquals(1f, getField(next, "ghostAlpha"));
        next.discard();
    }

    private void assertBoundedDroppedFrames(BatchedPatternRenderer renderer, boolean shadow,
                                            Object expectedCommand, Object[] expectedBackings) throws Exception {
        Set<Object> commands = identitySet();
        Set<Object>[] backings = new Set[expectedBackings.length];
        for (int i = 0; i < backings.length; i++) backings[i] = identitySet();
        for (int frame = 0; frame < 600; frame++) {
            GLCommandable command = batchCommand(renderer, shadow);
            commands.add(command);
            Object[] currentBackings = batchBackings(command, shadow);
            for (int i = 0; i < currentBackings.length; i++) backings[i].add(currentBackings[i]);
            graphics.registerCommand(command);
            graphics.flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
        }
        assertEquals(Set.of(expectedCommand), commands);
        for (Set<Object> backingIdentities : backings) assertEquals(1, backingIdentities.size());
    }

    private GLCommandable patternCommand() {
        PatternRenderCommand command = PatternRenderCommand.obtain(ENTRY, 1, graphics);
        command.capturePalette(0);
        command.finish(false, false, false, 0, 0, 8, 8);
        return command;
    }

    private static GLCommandable batchCommand(BatchedPatternRenderer renderer, boolean shadow) {
        if (shadow) {
            renderer.beginShadowBatch(ENTRY.atlasIndex());
            assertTrue(renderer.admitShadowPattern(ENTRY));
            renderer.appendAdmittedShadowPattern(ENTRY, false, false, 0, 0);
            return renderer.endShadowBatch();
        }
        renderer.beginBatch();
        assertTrue(renderer.admitPattern(ENTRY));
        renderer.appendAdmittedPattern(ENTRY, 0, false, false, 0, 0);
        return renderer.endBatch();
    }

    private static GLCommandable instancedCommand(InstancedPatternRenderer renderer) {
        renderer.beginBatch(ENTRY.atlasIndex());
        assertTrue(renderer.admitPattern(ENTRY));
        renderer.appendAdmittedPattern(ENTRY, 0, false, false, false, 0, 0);
        return renderer.endBatch();
    }

    private static Object[] batchBackings(Object command, boolean shadow) throws Exception {
        return shadow
                ? new Object[]{getField(command, "vertexBuffer"), getField(command, "texCoordBuffer")}
                : new Object[]{getField(command, "vertexBuffer"), getField(command, "texCoordBuffer"),
                        getField(command, "paletteCoordBuffer")};
    }

    private static void assertBackingsSame(Object[] expected, Object[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertSame(expected[i], actual[i]);
    }

    private static Set<Object> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static final class TrackingCommand implements GLCommandable {
        private final boolean throwing;
        private boolean discarded;
        private int executions;
        private int releases;

        private TrackingCommand(boolean throwing) {
            this.throwing = throwing;
        }

        @Override
        public void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
            executions++;
            try {
                if (throwing) {
                    throw new IllegalStateException("expected");
                }
            } finally {
                discard();
            }
        }

        @Override
        public void discard() {
            if (!discarded) {
                discarded = true;
                releases++;
            }
        }
    }

    private static final class TrackingGraphicsManager extends GraphicsManager {
        private int frameCleanups;

        void cleanupPatternFrameState() {
            frameCleanups++;
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearPatternCommandPool() throws Exception {
        Field field = PatternRenderCommand.class.getDeclaredField("pool");
        field.setAccessible(true);
        ((java.util.ArrayDeque<PatternRenderCommand>) field.get(null)).clear();
    }
}
