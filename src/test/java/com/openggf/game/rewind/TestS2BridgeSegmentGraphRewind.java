package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic2.objects.BridgeObjectInstance;
import com.openggf.game.sonic2.objects.BridgeSegmentObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Graph-rewind coverage for the parent-dependent Sonic 2 Obj11 subsprite
 * children. A segment has no standalone construction contract: it restores
 * against the matching live bridge and adopts itself into the parent's exact
 * child slot.
 */
class TestS2BridgeSegmentGraphRewind {

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void twoSegmentBridgeRestoresFreshChildrenAndParentSlots() {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);

        BridgeObjectInstance sourceParent = objectManager.createDynamicObject(
                () -> new BridgeObjectInstance(
                        new ObjectSpawn(0x400, 0x300, 0x11, 12, 0, false, 0),
                        "Bridge"));
        sourceParent.update(0, null);

        List<BridgeSegmentObjectInstance> sourceSegments = segments(objectManager);
        assertEquals(2, sourceSegments.size(),
                "a 12-log Obj11 bridge must allocate both ROM subsprite objects");
        BridgeSegmentObjectInstance sourceFirst = sourceSegments.get(0);
        BridgeSegmentObjectInstance sourceSecond = sourceSegments.get(1);
        ObjectRefId parentId = objectId(objectManager, sourceParent);
        ObjectRefId firstId = objectId(objectManager, sourceFirst);
        ObjectRefId secondId = objectId(objectManager, sourceSecond);

        RewindRegistry registry = new RewindRegistry();
        registry.register(objectManager.rewindSnapshottable());
        CompositeSnapshot snapshot = registry.capture();

        objectManager.removeDynamicObject(sourceFirst);
        objectManager.removeDynamicObject(sourceSecond);
        registry.restore(snapshot);

        BridgeObjectInstance restoredParent = objectById(
                objectManager, BridgeObjectInstance.class, parentId);
        BridgeSegmentObjectInstance restoredFirst = objectById(
                objectManager, BridgeSegmentObjectInstance.class, firstId);
        BridgeSegmentObjectInstance restoredSecond = objectById(
                objectManager, BridgeSegmentObjectInstance.class, secondId);

        assertEquals(2, segments(objectManager).size(),
                "restore must keep exactly the captured Obj11 child graph");
        assertNotSame(sourceParent, restoredParent, "restore must recreate the parent");
        assertNotSame(sourceFirst, restoredFirst, "restore must recreate segment one");
        assertNotSame(sourceSecond, restoredSecond, "restore must recreate segment two");
        assertSame(restoredParent, restoredFirst.getParentBridge());
        assertSame(restoredParent, restoredSecond.getParentBridge());
        assertSame(restoredFirst, readField(restoredParent, "segment1"),
                "first ROM child slot must relink to the restored first segment");
        assertSame(restoredSecond, readField(restoredParent, "segment2"),
                "second ROM child slot must relink to the restored second segment");
    }

    private static List<BridgeSegmentObjectInstance> segments(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(BridgeSegmentObjectInstance.class::isInstance)
                .map(BridgeSegmentObjectInstance.class::cast)
                .filter(segment -> !segment.isDestroyed())
                .sorted((left, right) -> Integer.compare(left.getSlotIndex(), right.getSlotIndex()))
                .toList();
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "ObjectManager capture identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T objectById(
            ObjectManager objectManager, Class<T> type, ObjectRefId id) {
        return objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> !object.isDestroyed())
                .filter(object -> objectId(objectManager, object).equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing restored object " + id));
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName, e);
        }
    }

    private record Harness(ObjectManager objectManager) {
        static Harness create() {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public ObjectPlayerQuery playerQuery() {
                    return new ObjectPlayerQuery(() -> null, List::of);
                }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(), null, 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = objectManager;
            objectManager.reset(0);
            return new Harness(objectManager);
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
