package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.MhzZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMhzMinibossEscapeShardGraphRewind {
    private static final ObjectSpawn DISTRACTOR_PARENT_SPAWN =
            new ObjectSpawn(0x0340, 0x0500, Sonic3kObjectIds.AIZ_END_BOSS, 0, 0, false, 80);
    private static final ObjectSpawn CAPTURED_PARENT_SPAWN =
            new ObjectSpawn(0x0180, 0x0500, Sonic3kObjectIds.MHZ_MINIBOSS, 0, 0, false, 81);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x800, 0x700, 0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void escapeShardRestoresFreshWithExactCapturedParentAndScalarState() {
        Harness harness = Harness.create(List.of(DISTRACTOR_PARENT_SPAWN, CAPTURED_PARENT_SPAWN));
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        List<MhzMinibossInstance> parents = liveObjects(objectManager, MhzMinibossInstance.class);
        assertEquals(2, parents.size(), "precondition: two MHZ miniboss parents must be live");
        MhzMinibossInstance parentDistractor = parents.get(0);
        MhzMinibossInstance parentCaptured = parents.get(1);

        MhzMinibossEscapeShardInstance sourceShard = objectManager.createDynamicObject(
                () -> new MhzMinibossEscapeShardInstance(0x0348, 0x04F8, parentCaptured));
        setShardState(sourceShard, 0x034C, 0x04FC, 0x034C0000, 0x04FC0000,
                -0x123, 0x234, 0x1A, 4, 7, 6, 0x1D, 2, true, true);

        ObjectRefId distractorParentId = objectId(objectManager, parentDistractor);
        ObjectRefId capturedParentId = objectId(objectManager, parentCaptured);
        ObjectRefId shardId = objectId(objectManager, sourceShard);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceShard);
        MhzMinibossInstance divergentParent = objectManager.createDynamicObject(
                () -> new MhzMinibossInstance(new ObjectSpawn(
                        0x0520, 0x0580, Sonic3kObjectIds.MHZ_MINIBOSS, 0, 0, false, 82)));
        MhzMinibossEscapeShardInstance divergentShard = objectManager.createDynamicObject(
                () -> new MhzMinibossEscapeShardInstance(0x0528, 0x0578, divergentParent));
        setShardState(divergentShard, 0x0528, 0x0578, 0x05280000, 0x05780000,
                0x777, -0x555, 0x18, 2, 3, 0, 0x05, 4, false, false);

        rewindRegistry.restore(snapshot);

        assertEquals(2, liveObjects(objectManager, MhzMinibossInstance.class).size(),
                "restore must keep exactly the captured MHZ miniboss parents");
        assertEquals(1, liveObjects(objectManager, MhzMinibossEscapeShardInstance.class).size(),
                "restore must recreate exactly the captured escape shard");

        MhzMinibossInstance restoredDistractor =
                objectById(objectManager, MhzMinibossInstance.class, distractorParentId);
        MhzMinibossInstance restoredParent =
                objectById(objectManager, MhzMinibossInstance.class, capturedParentId);
        MhzMinibossEscapeShardInstance restoredShard =
                objectById(objectManager, MhzMinibossEscapeShardInstance.class, shardId);

        assertNotSame(parentDistractor, restoredDistractor, "restore must recreate the distractor parent");
        assertNotSame(parentCaptured, restoredParent, "restore must recreate the captured parent");
        assertNotSame(sourceShard, restoredShard, "restore must recreate the shard");
        assertNotSame(divergentParent, restoredParent, "restore must drop the divergent parent");
        assertNotSame(divergentShard, restoredShard, "restore must drop the divergent shard");

        assertSame(restoredParent, readObjectField(restoredShard, "parent"),
                "shard parent must resolve to the restored captured parent by ObjectRefId");
        assertNotSame(parentCaptured, readObjectField(restoredShard, "parent"),
                "shard must not retain the stale source parent");
        assertNotSame(restoredDistractor, readObjectField(restoredShard, "parent"),
                "shard must not relink to the restored distractor parent");
        assertNotSame(divergentParent, readObjectField(restoredShard, "parent"),
                "shard must not relink to a divergent parent");

        assertShardState(restoredShard, 0x034C, 0x04FC, 0x034C0000, 0x04FC0000,
                -0x123, 0x234, 0x1A, 4, 7, 6, 0x1D, 2, true, true);
    }

    @Test
    void escapeShardUsesRewindRecreatableWithoutExplicitDynamicCodec() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(MhzMinibossEscapeShardInstance.class),
                "MHZ miniboss escape shard must restore through RewindRecreatable generic recreate");
        assertFalse(hasRegisteredS3kCodec(MhzMinibossEscapeShardInstance.class.getName()),
                "MHZ miniboss escape shard must not keep an explicit S3K dynamic codec");
    }

    @Test
    void captureFailsForEscapeShardWhoseRequiredParentHasNoRewindIdentity() {
        Harness harness = Harness.create(List.of());
        MhzMinibossInstance unmanagedParent =
                new MhzMinibossInstance(new ObjectSpawn(
                        0x0340, 0x0500, Sonic3kObjectIds.MHZ_MINIBOSS, 0, 0, false, 83));
        unmanagedParent.setServices(harness.services());
        MhzMinibossEscapeShardInstance shard = harness.objectManager().createDynamicObject(
                () -> new MhzMinibossEscapeShardInstance(0x0348, 0x04F8, unmanagedParent));
        assertSame(unmanagedParent, readObjectField(shard, "parent"),
                "precondition: shard parent is outside ObjectManager identity registration");

        RewindRegistry rewindRegistry = registryFor(harness.objectManager());
        IllegalStateException thrown = assertThrows(IllegalStateException.class, rewindRegistry::capture);
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "missing required parent target must fail loudly");
    }

    private record Harness(ObjectManager objectManager, StubObjectServices services) {
        static Harness create(List<ObjectSpawn> spawns) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            StubObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public int romZoneId() { return Sonic3kZoneIds.ZONE_MHZ; }
                @Override public int featureZoneId() { return Sonic3kZoneIds.ZONE_MHZ; }
            };
            services.zoneRuntimeRegistry().install(new MhzZoneRuntimeState(0, PlayerCharacter.SONIC_AND_TAILS));
            ObjectManager objectManager = new ObjectManager(
                    spawns, new MhzTestRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = objectManager;
            objectManager.reset(0);
            return new Harness(objectManager, services);
        }
    }

    private static final class MhzTestRegistry extends Sonic3kObjectRegistry {
        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_MHZ;
        }
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        RewindIdentityTable table = objectManager.captureIdentityContext().requireIdentityTable();
        ObjectRefId id = table.idFor(object);
        assertNotNull(id, "ObjectManager capture identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T objectById(
            ObjectManager objectManager, Class<T> type, ObjectRefId id) {
        return liveObjects(objectManager, type).stream()
                .filter(object -> objectId(objectManager, object).equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing restored object " + id));
    }

    private static boolean hasRegisteredS3kCodec(String className) {
        Set<String> names = new HashSet<>();
        for (DynamicObjectRewindCodec codec : new Sonic3kObjectRegistry().dynamicRewindCodecs()) {
            names.add(codec.className());
        }
        return names.contains(className);
    }

    private static <T extends ObjectInstance> List<T> liveObjects(ObjectManager objectManager, Class<T> type) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .sorted(Comparator.comparingInt(TestMhzMinibossEscapeShardGraphRewind::slotIndex))
                .toList();
    }

    private static int slotIndex(ObjectInstance object) {
        return object instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1;
    }

    private static void setShardState(
            MhzMinibossEscapeShardInstance shard,
            int x,
            int y,
            int xFixed,
            int yFixed,
            int xVel,
            int yVel,
            int mappingFrame,
            int animFrame,
            int animFrameTimer,
            int routine,
            int timer,
            int priorityBucket,
            boolean initialized,
            boolean touchedFloor) {
        setIntField(shard, "x", x);
        setIntField(shard, "y", y);
        setIntField(shard, "xFixed", xFixed);
        setIntField(shard, "yFixed", yFixed);
        setIntField(shard, "xVel", xVel);
        setIntField(shard, "yVel", yVel);
        setIntField(shard, "mappingFrame", mappingFrame);
        setIntField(shard, "animFrame", animFrame);
        setIntField(shard, "animFrameTimer", animFrameTimer);
        setIntField(shard, "routine", routine);
        setIntField(shard, "timer", timer);
        setIntField(shard, "priorityBucket", priorityBucket);
        setBooleanField(shard, "initialized", initialized);
        setBooleanField(shard, "touchedFloor", touchedFloor);
    }

    private static void assertShardState(
            MhzMinibossEscapeShardInstance shard,
            int x,
            int y,
            int xFixed,
            int yFixed,
            int xVel,
            int yVel,
            int mappingFrame,
            int animFrame,
            int animFrameTimer,
            int routine,
            int timer,
            int priorityBucket,
            boolean initialized,
            boolean touchedFloor) {
        assertEquals(x, readIntField(shard, "x"), "x must restore exactly");
        assertEquals(y, readIntField(shard, "y"), "y must restore exactly");
        assertEquals(xFixed, readIntField(shard, "xFixed"), "xFixed must restore exactly");
        assertEquals(yFixed, readIntField(shard, "yFixed"), "yFixed must restore exactly");
        assertEquals(xVel, readIntField(shard, "xVel"), "xVel must restore exactly");
        assertEquals(yVel, readIntField(shard, "yVel"), "yVel must restore exactly");
        assertEquals(mappingFrame, readIntField(shard, "mappingFrame"), "mappingFrame must restore exactly");
        assertEquals(animFrame, readIntField(shard, "animFrame"), "animFrame must restore exactly");
        assertEquals(animFrameTimer, readIntField(shard, "animFrameTimer"),
                "animFrameTimer must restore exactly");
        assertEquals(routine, readIntField(shard, "routine"), "routine must restore exactly");
        assertEquals(timer, readIntField(shard, "timer"), "timer must restore exactly");
        assertEquals(priorityBucket, readIntField(shard, "priorityBucket"),
                "priorityBucket must restore exactly");
        assertEquals(initialized, readBooleanField(shard, "initialized"), "initialized must restore exactly");
        assertEquals(touchedFloor, readBooleanField(shard, "touchedFloor"), "touchedFloor must restore exactly");
    }

    private static int readIntField(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static boolean readBooleanField(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static Object readObjectField(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static void setIntField(Object target, String fieldName, int value) {
        try {
            findField(target.getClass(), fieldName).setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) {
        try {
            findField(target.getClass(), fieldName).setBoolean(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 0x800; }
            @Override public short getHeight() { return 0x700; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
