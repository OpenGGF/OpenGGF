package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic2.objects.BreakableBlockObjectInstance.BreakableBlockFragmentInstance;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.OwnedMocks;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestS2BreakableBlockFragmentGraphRewind {

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pieceFragmentRestoresItsDrawBeforeAndAfterMovement(boolean captureAfterMovement) {
        try (OwnedMocks mocks = new OwnedMocks()) {
            var piece = new SpriteMappingPiece(-13, 7, 2, 3, 0x123, true, false, 2, true);
            ObjectRenderManager art = mocks.mock(ObjectRenderManager.class);
            PatternSpriteRenderer renderer = mocks.mock(PatternSpriteRenderer.class);
            when(art.getRenderer(Sonic2ObjectArtKeys.BREAKABLE_BLOCK)).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            ObjectManager manager = Harness.create(art).objectManager();
            var source = manager.createDynamicObject(() ->
                    new BreakableBlockFragmentInstance(0x140, 0x80, -0x100, -0x110, piece, Sonic2ObjectArtKeys.BREAKABLE_BLOCK));
            if (captureAfterMovement) source.update(1, null);
            int x = captureAfterMovement ? 0x13F : 0x140;
            int y = captureAfterMovement ? 0x7F : 0x80;
            source.appendRenderCommands(List.of());
            verify(renderer).drawPieces(List.of(piece), x, y, false, false);
            RewindRegistry registry = registryFor(manager);
            CompositeSnapshot snapshot = registry.capture();
            clearInvocations(renderer);
            source.update(2, null);
            source.appendRenderCommands(List.of());
            verify(renderer).drawPieces(List.of(piece), x - 1, y - 1, false, false);
            manager.removeDynamicObject(source);
            clearInvocations(renderer);

            PatternSpriteRenderer currentRenderer = mocks.mock(PatternSpriteRenderer.class);
            when(currentRenderer.isReady()).thenReturn(true);
            when(art.getRenderer(Sonic2ObjectArtKeys.BREAKABLE_BLOCK)).thenReturn(currentRenderer);
            registry.restore(snapshot);

            var restored = only(manager, BreakableBlockFragmentInstance.class);
            assertNotSame(source, restored);
            restored.appendRenderCommands(List.of());
            verify(currentRenderer).drawPieces(List.of(piece), x, y, false, false);
            clearInvocations(currentRenderer);
            restored.update(2, null);
            restored.appendRenderCommands(List.of());
            verify(currentRenderer).drawPieces(List.of(piece), x - 1, y - 1, false, false);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void frameFallbackPreservesArtPresenceAndRetirement(boolean hasArt) {
        try (OwnedMocks mocks = new OwnedMocks()) {
            ObjectRenderManager art = mocks.mock(ObjectRenderManager.class);
            PatternSpriteRenderer renderer = mocks.mock(PatternSpriteRenderer.class);
            when(art.getRenderer(Sonic2ObjectArtKeys.BREAKABLE_BLOCK)).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            ObjectManager manager = Harness.create(art).objectManager();
            var source = manager.createDynamicObject(() ->
                    new BreakableBlockFragmentInstance(0x140, 0xFF, 0, 0x100, 3, hasArt ? art : null));
            RewindRegistry registry = registryFor(manager);
            CompositeSnapshot snapshot = registry.capture();
            source.appendRenderCommands(List.of());
            if (hasArt) verify(renderer).drawFrameIndex(3, 0x140, 0xFF, false, false);
            else verifyNoInteractions(renderer);
            source.update(1, null);
            assertFalse(source.isDestroyed());
            source.update(2, null);
            assertTrue(source.isDestroyed());
            manager.removeDynamicObject(source);
            clearInvocations(renderer);

            registry.restore(snapshot);

            var restored = only(manager, BreakableBlockFragmentInstance.class);
            restored.appendRenderCommands(List.of());
            if (hasArt) verify(renderer).drawFrameIndex(3, 0x140, 0xFF, false, false);
            else verifyNoInteractions(renderer);
            clearInvocations(renderer);
            restored.update(1, null);
            assertFalse(restored.isDestroyed());
            restored.appendRenderCommands(List.of());
            if (hasArt) verify(renderer).drawFrameIndex(3, 0x140, 0x100, false, false);
            else verifyNoInteractions(renderer);
            clearInvocations(renderer);
            restored.update(2, null);
            assertTrue(restored.isDestroyed());
            restored.appendRenderCommands(List.of());
            verifyNoInteractions(renderer);
        }
    }

    @Test
    void pieceConstructorRequiresExplicitArtProvenance() {
        var piece = new SpriteMappingPiece(0, 0, 1, 1, 1, false, false, 0);
        assertThrows(NullPointerException.class,
                () -> new BreakableBlockFragmentInstance(0, 0, 0, 0, piece, (String) null));
        assertTrue(GenericRewindEligibility.usesCompactDefaultSubclassCapture(BreakableBlockFragmentInstance.class));
    }

    @Test
    void breakableBlockFragmentRestoresFreshWithCapturedPhysicsAndFrame() {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();

        BreakableBlockFragmentInstance before = objectManager.createDynamicObject(
                () -> new BreakableBlockFragmentInstance(0x0140, 0x0180, -0x0100, -0x0200, 3, null));
        seedFragmentState(before);
        ObjectRefId beforeId = objectId(objectManager, before);
        Map<String, Integer> beforeScalars = scalarSnapshot(before);
        RewindRegistry registry = registryFor(objectManager);
        CompositeSnapshot snapshot = registry.capture();

        objectManager.removeDynamicObject(before);
        BreakableBlockFragmentInstance divergent = objectManager.createDynamicObject(
                () -> new BreakableBlockFragmentInstance(0x0180, 0x01A0, 0x0100, -0x0100, 1, null));
        assertNotEquals(beforeId, objectId(objectManager, divergent),
                "post-snapshot divergent fragment must not alias captured identity");

        registry.restore(snapshot);

        BreakableBlockFragmentInstance restored = only(objectManager, BreakableBlockFragmentInstance.class);
        assertNotSame(before, restored, "restore must recreate the removed breakable-block fragment");
        assertNotSame(divergent, restored, "restore must drop unrelated post-snapshot fragments");
        assertEquals(beforeId, objectId(objectManager, restored),
                "breakable-block fragment dynamic identity must be preserved");
        assertEquals(beforeScalars, scalarSnapshot(restored),
                "compact restore must replay captured breakable-block fragment physics and frame");
    }

    @Test
    void breakableBlockFragmentUsesRewindRecreatableWithoutExplicitDynamicCodec() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(BreakableBlockFragmentInstance.class),
                "breakable-block fragments must restore through RewindRecreatable");

    }

    private static void seedFragmentState(BreakableBlockFragmentInstance fragment) {
        writeInt(fragment, "currentX", 0x0154);
        writeInt(fragment, "currentY", 0x0178);
        writeInt(fragment, "subX", 0x0154 << 8);
        writeInt(fragment, "subY", 0x0178 << 8);
        writeInt(fragment, "velX", -0x0120);
        writeInt(fragment, "velY", -0x01C0);
        writeInt(fragment, "frameIndex", 4);
    }

    private static Map<String, Integer> scalarSnapshot(BreakableBlockFragmentInstance fragment) {
        Map<String, Integer> scalars = new LinkedHashMap<>();
        scalars.put("currentX", readInt(fragment, "currentX"));
        scalars.put("currentY", readInt(fragment, "currentY"));
        scalars.put("subX", readInt(fragment, "subX"));
        scalars.put("subY", readInt(fragment, "subY"));
        scalars.put("velX", readInt(fragment, "velX"));
        scalars.put("velY", readInt(fragment, "velY"));
        scalars.put("frameIndex", readInt(fragment, "frameIndex"));
        return scalars;
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(objectManager.rewindSnapshottable());
        return registry;
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "ObjectManager capture identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T only(ObjectManager objectManager, Class<T> type) {
        List<T> matches = objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .toList();
        assertEquals(1, matches.size(), "expected exactly one live " + type.getSimpleName());
        return matches.getFirst();
    }

    private static int readInt(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static void writeInt(Object target, String fieldName, int value) {
        try {
            findField(target.getClass(), fieldName).setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Walk superclass chain.
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private record Harness(ObjectManager objectManager) {
        static Harness create() {
            return create(null);
        }

        static Harness create(ObjectRenderManager art) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCameraAtOrigin();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public ObjectRenderManager renderManager() { return art; }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(),
                    null,
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(0);
            objectManager.setRewindInPlaceRestoreEnabledForTest(false);
            return new Harness(objectManager);
        }
    }

    private static Camera mockCameraAtOrigin() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
