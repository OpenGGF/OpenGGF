package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.schema.RewindObjectStateBlob;
import com.openggf.game.sonic3k.objects.AizGiantRideVineObjectInstance;
import com.openggf.game.sonic3k.objects.AizRideVineObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prove-first rewind coverage for the AIZ ride vines (ride mechanics in the
 * primary release slice). Both the {@code handle} plain-state-holder (the
 * player's ride anchor — native grab flags plus PlayerRefId-backed extension state) and the {@code chain}
 * {@code Segment[]} (the rendered swing/deploy link positions) ride keyframes: a
 * live rewind hold renders restored state without re-running {@code update()}, so
 * a dropped chain would show the vine links detached from the captured root/handle
 * mid-ride. These tests pin that the grab/ride state AND the chain segment state
 * survive a capture -> mutate -> restore cycle.
 */
class TestAizRideVineRewind {

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void bothVinesAreCompactSchemaCapturableSoTheHandleGrabStateRidesKeyframes() {
        for (Class<? extends ObjectInstance> type :
                List.of(AizRideVineObjectInstance.class, AizGiantRideVineObjectInstance.class)) {
            assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(type),
                    type.getSimpleName() + " must use the default object-subclass capture path");
            assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(type),
                    type.getSimpleName() + " must be compact-schema capturable so its handle grab state "
                            + "(the ride anchor) rides keyframes rather than dropping to the generic path");
        }
    }

    @Test
    void rideVineGrabStateAndRootScalarsSurviveRewind() {
        // Capture/restore the compact default-object-subclass schema directly: the vine's
        // captured fields are scalars plus the handle plain-state-holder. Supply the focused
        // player identity because the handle now owns optional player-reference fields. This exercises
        // exactly the keyframe path without ObjectManager's onUnload grab-clear (which needs
        // config/sprite services the stub harness omits).
        TestablePlayableSprite rewindPlayer = player("sonic");
        Harness harness = Harness.create(rewindPlayer);
        ObjectManager objectManager = harness.objectManager();

        // subtype low bits set the zip distance; keep it small and in range.
        ObjectSpawn spawn = new ObjectSpawn(0x1800, 0x0500, 0x04, 0, 1, false, 0, 71);
        AizRideVineObjectInstance vine = objectManager.createDynamicObject(
                () -> new AizRideVineObjectInstance(spawn));

        // Seed mid-ride state: player grabbed on the handle, root mid-zip.
        writeInt(vine, "currentX", 0x1830);
        writeInt(vine, "currentY", 0x0522);
        writeInt(vine, "rootAngle", 0x0140);
        writeBoolean(vine, "firstCopiesParent", true);
        writeInt(vine, "handle.mode", 2);
        writeInt(vine, "handle.p1.grabFlag", 1);
        writeInt(vine, "handle.p1.releaseDelay", 0x30);
        // Held-anim latch state (grabbed rolling, TouchFloor already latched Walk):
        // both booleans must ride the keyframe or a mid-ride rewind would revert the
        // anim byte to HANG2 in the airborne-after-grounding window.
        writeBoolean(vine, "handle.p1.rollingAtGrab", true);
        writeBoolean(vine, "handle.p1.walkLatched", true);
        writeInt(vine, "handle.x", 0x1830);
        writeInt(vine, "handle.y", 0x0560);

        // Seed the rendered link chain (Segment[] of angles/positions/mapping frames).
        writeChainInt(vine, 0, "angle", 0x0080);
        writeChainInt(vine, 0, "x", 0x1840);
        writeChainInt(vine, 0, "y", 0x0532);
        writeChainInt(vine, 2, "angle", 0x00C0);
        writeChainInt(vine, 2, "mappingFrame", 5);

        RewindCaptureContext context = rewindContext(rewindPlayer);
        RewindObjectStateBlob blob = CompactFieldCapturer.capture(vine, context);

        // Drive live state away from the captured keyframe (as if play continued forward).
        writeInt(vine, "handle.mode", 0);
        writeInt(vine, "handle.p1.grabFlag", 0);
        writeInt(vine, "handle.p1.releaseDelay", 0);
        writeBoolean(vine, "handle.p1.rollingAtGrab", false);
        writeBoolean(vine, "handle.p1.walkLatched", false);
        writeInt(vine, "handle.x", 0x1F00);
        writeInt(vine, "handle.y", 0x0400);
        writeInt(vine, "currentX", 0x1F00);
        writeInt(vine, "currentY", 0x0400);
        writeInt(vine, "rootAngle", 0);
        writeBoolean(vine, "firstCopiesParent", false);
        writeChainInt(vine, 0, "angle", 0);
        writeChainInt(vine, 0, "x", 0x2000);
        writeChainInt(vine, 0, "y", 0);
        writeChainInt(vine, 2, "angle", 0);
        writeChainInt(vine, 2, "mappingFrame", 0);

        CompactFieldCapturer.restore(vine, blob, context);

        // The ride anchor: handle grab flags + position survive → the player is not stranded.
        assertEquals(1, readInt(vine, "handle.p1.grabFlag"),
                "captured handle grab flag (the ride anchor) must survive rewind");
        assertEquals(2, readInt(vine, "handle.mode"), "handle mode must survive rewind");
        assertEquals(0x30, readInt(vine, "handle.p1.releaseDelay"),
                "handle release-delay countdown must survive rewind");
        assertEquals(0x1830, readInt(vine, "handle.x"), "handle X must survive rewind");
        assertEquals(0x0560, readInt(vine, "handle.y"), "handle Y must survive rewind");
        assertTrue(readBoolean(vine, "handle.p1.rollingAtGrab"),
                "held-anim rollingAtGrab latch must survive rewind");
        assertTrue(readBoolean(vine, "handle.p1.walkLatched"),
                "held-anim Walk latch must survive rewind (else HANG2 reverts on rewind)");

        // Root scalars that drive the chain also survive.
        assertEquals(0x1830, readInt(vine, "currentX"), "root currentX must survive rewind");
        assertEquals(0x0522, readInt(vine, "currentY"), "root currentY must survive rewind");
        assertEquals(0x0140, readInt(vine, "rootAngle"), "rootAngle must survive rewind");
        assertTrue(readBoolean(vine, "firstCopiesParent"), "firstCopiesParent must survive rewind");

        // The rendered link chain survives → a mid-ride live-rewind hold shows a coherent vine.
        assertEquals(0x0080, readChainInt(vine, 0, "angle"), "chain[0] angle must survive rewind");
        assertEquals(0x1840, readChainInt(vine, 0, "x"), "chain[0] X must survive rewind");
        assertEquals(0x0532, readChainInt(vine, 0, "y"), "chain[0] Y must survive rewind");
        assertEquals(0x00C0, readChainInt(vine, 2, "angle"), "chain[2] angle must survive rewind");
        assertEquals(5, readChainInt(vine, 2, "mappingFrame"), "chain[2] mappingFrame must survive rewind");
    }

    @Test
    void nonEmptyExtensionGrabStateRelinksThroughPlayerRefs() throws Exception {
        TestablePlayableSprite oldMain = player("old-main");
        TestablePlayableSprite oldP2 = player("old-p2");
        TestablePlayableSprite oldExtension = player("old-extension");
        AizRideVineObjectInstance vine = new AizRideVineObjectInstance(
                new ObjectSpawn(0x1800, 0x0500, 0x04, 0, 1, false, 0, 72));
        Object handle = findField(vine.getClass(), "handle").get(vine);
        findField(handle.getClass(), "p1Owner").set(handle, oldMain);
        findField(handle.getClass(), "p2Owner").set(handle, oldP2);
        Map<Object, Object> extensions = extensionStates(handle);
        var stateConstructor = Class.forName(
                "com.openggf.game.sonic3k.objects.AizVineHandleLogic$PlayerState")
                .getDeclaredConstructor();
        stateConstructor.setAccessible(true);
        Object extensionState = stateConstructor.newInstance();
        findField(extensionState.getClass(), "grabFlag").setInt(extensionState, 1);
        extensions.put(oldExtension, extensionState);

        RewindObjectStateBlob blob = CompactFieldCapturer.capture(
                vine, rewindContext(oldMain, oldP2, oldExtension));
        TestablePlayableSprite newMain = player("new-main");
        TestablePlayableSprite newP2 = player("new-p2");
        TestablePlayableSprite newExtension = player("new-extension");
        CompactFieldCapturer.restore(vine, blob, rewindContext(newMain, newP2, newExtension));

        Object restoredHandle = findField(vine.getClass(), "handle").get(vine);
        assertSame(newMain, findField(restoredHandle.getClass(), "p1Owner").get(restoredHandle));
        assertSame(newP2, findField(restoredHandle.getClass(), "p2Owner").get(restoredHandle));
        assertTrue(extensionStates(restoredHandle).containsKey(newExtension));
        assertEquals(1, findField(extensionStates(restoredHandle).get(newExtension).getClass(), "grabFlag")
                .getInt(extensionStates(restoredHandle).get(newExtension)));
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> extensionStates(Object handle) throws ReflectiveOperationException {
        return (Map<Object, Object>) findField(handle.getClass(), "extensionStates").get(handle);
    }

    private static RewindCaptureContext rewindContext(
            TestablePlayableSprite main,
            TestablePlayableSprite nativeP2,
            TestablePlayableSprite extension) {
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(main, PlayerRefId.mainPlayer());
        identities.registerPlayer(nativeP2, PlayerRefId.sidekick(0));
        identities.registerPlayer(extension, PlayerRefId.sidekick(1));
        return RewindCaptureContext.withIdentityTable(identities);
    }

    private static RewindCaptureContext rewindContext(TestablePlayableSprite main) {
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(main, PlayerRefId.mainPlayer());
        return RewindCaptureContext.withIdentityTable(identities);
    }

    private static TestablePlayableSprite player(String code) {
        return new TestablePlayableSprite(code, (short) 0x1200, (short) 0x0400);
    }

    private static int readChainInt(Object vine, int index, String segmentField) {
        try {
            Object segment = java.lang.reflect.Array.get(findField(vine.getClass(), "chain").get(vine), index);
            return findField(segment.getClass(), segmentField).getInt(segment);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read chain[" + index + "]." + segmentField, e);
        }
    }

    private static void writeChainInt(Object vine, int index, String segmentField, int value) {
        try {
            Object segment = java.lang.reflect.Array.get(findField(vine.getClass(), "chain").get(vine), index);
            findField(segment.getClass(), segmentField).setInt(segment, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write chain[" + index + "]." + segmentField, e);
        }
    }

    private static int readInt(Object root, String path) {
        try {
            Resolved resolved = resolve(root, path);
            return resolved.field().getInt(resolved.target());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + path, e);
        }
    }

    private static boolean readBoolean(Object root, String path) {
        try {
            Resolved resolved = resolve(root, path);
            return resolved.field().getBoolean(resolved.target());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + path, e);
        }
    }

    private static void writeInt(Object root, String path, int value) {
        try {
            Resolved resolved = resolve(root, path);
            resolved.field().setInt(resolved.target(), value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + path, e);
        }
    }

    private static void writeBoolean(Object root, String path, boolean value) {
        try {
            Resolved resolved = resolve(root, path);
            resolved.field().setBoolean(resolved.target(), value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + path, e);
        }
    }

    /** Navigates a dotted field path (e.g. "handle.p1.grabFlag") to the final owning object + field. */
    private static Resolved resolve(Object root, String path) throws ReflectiveOperationException {
        String[] parts = path.split("\\.");
        Object target = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Field field = findField(target.getClass(), parts[i]);
            target = field.get(target);
        }
        return new Resolved(target, findField(target.getClass(), parts[parts.length - 1]));
    }

    private record Resolved(Object target, Field field) {}

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

    private static final class Harness {
        private final ObjectManager objectManager;

        private Harness(ObjectManager objectManager) {
            this.objectManager = objectManager;
        }

        static Harness create(AbstractPlayableSprite main) {
            TestCamera camera = new TestCamera();
            camera.setFocusedSprite(main);
            MutableServices services = new MutableServices(camera);
            ObjectManager objectManager = new ObjectManager(
                    List.of(),
                    null,
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            services.objectManager = objectManager;
            objectManager.reset(camera.getX());
            // In-place restore left enabled: the vine's onUnload grab-clear path needs
            // config/sprite services the stub omits, so we prove ride-state survival by
            // in-place round-trip rather than remove+recreate.
            return new Harness(objectManager);
        }

        ObjectManager objectManager() {
            return objectManager;
        }
    }

    private static final class MutableServices extends StubObjectServices {
        private ObjectManager objectManager;
        private final Camera camera;

        private MutableServices(Camera camera) {
            this.camera = camera;
        }

        @Override public ObjectManager objectManager() { return objectManager; }
        @Override public Camera camera() { return camera; }
        @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
    }

    private static final class TestCamera extends Camera {
        private AbstractPlayableSprite focusedSprite;

        @Override public void setFocusedSprite(AbstractPlayableSprite sprite) { focusedSprite = sprite; }
        @Override public AbstractPlayableSprite getFocusedSprite() { return focusedSprite; }
        @Override public short getX() { return 0x1000; }
        @Override public short getY() { return 0x0300; }
        @Override public short getWidth() { return 320; }
        @Override public short getHeight() { return 224; }
        @Override public boolean isVerticalWrapEnabled() { return false; }
    }
}
