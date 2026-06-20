package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.badniks.DragonflyBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kBadnikChildGraphRewind {

    private static final String DRAGONFLY_LINKED_BODY_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.DragonflyBadnikInstance$LinkedBodyChild";
    private static final String SPIKER_TOP_SPIKE_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance$SpikerTopSpikeChild";
    private static final String TURBO_SPIKER_SHELL_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance$TurboSpikerShellChild";

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x400, 0x300, 0);
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void dragonflyLinkedBodyGraphRestoresExactParentAndPreviousSegmentByIdentity() {
        Harness harness = Harness.create(new MhzTestRegistry(), List.of(new ObjectSpawn(
                0x120, 0x100, Sonic3kObjectIds.DRAGONFLY, 0, 0, false, 10)));
        ObjectManager objectManager = harness.objectManager();
        TestablePlayableSprite player = player();
        DragonflyBadnikInstance sourceParent = only(objectManager, DragonflyBadnikInstance.class);
        sourceParent.update(0, player);
        sourceParent.update(1, player);
        List<ObjectInstance> sourceSegments = liveByClassName(objectManager, DRAGONFLY_LINKED_BODY_CHILD);
        assertEquals(7, sourceSegments.size(), "precondition: Dragonfly setup must create seven body links");

        ObjectInstance sourceSegment0 = segmentByIndex(sourceSegments, 0);
        ObjectInstance sourceSegment1 = segmentByIndex(sourceSegments, 1);
        setIntField(sourceSegment0, "childX", 0x1A0);
        setIntField(sourceSegment0, "childY", 0x120);
        setIntField(sourceSegment0, "countdown", 7);
        setIntField(sourceSegment1, "childX", 0x1B0);
        setIntField(sourceSegment1, "childY", 0x118);
        setIntField(sourceSegment1, "countdown", 5);

        ObjectRefId parentId = objectId(objectManager, sourceParent);
        ObjectRefId segment0Id = objectId(objectManager, sourceSegment0);
        ObjectRefId segment1Id = objectId(objectManager, sourceSegment1);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new DragonflyBadnikInstance(new ObjectSpawn(
                0x2C0, 0x160, Sonic3kObjectIds.DRAGONFLY, 0, 0, false, 11)));

        rewindRegistry.restore(snapshot);

        DragonflyBadnikInstance restoredParent = only(objectManager, DragonflyBadnikInstance.class);
        List<ObjectInstance> restoredSegments = liveByClassName(objectManager, DRAGONFLY_LINKED_BODY_CHILD);
        assertEquals(7, restoredSegments.size(), "restore must keep exactly the captured seven body links");
        ObjectInstance restoredSegment0 = segmentByIndex(restoredSegments, 0);
        ObjectInstance restoredSegment1 = segmentByIndex(restoredSegments, 1);

        assertNotSame(sourceParent, restoredParent, "restore must recreate the removed Dragonfly parent");
        assertNotSame(sourceSegment0, restoredSegment0, "restore must recreate removed body segment 0");
        assertNotSame(sourceSegment1, restoredSegment1, "restore must recreate removed body segment 1");
        assertEquals(parentId, objectId(objectManager, restoredParent),
                "Dragonfly parent rewind identity must be preserved");
        assertEquals(segment0Id, objectId(objectManager, restoredSegment0),
                "Dragonfly segment 0 rewind identity must be preserved");
        assertEquals(segment1Id, objectId(objectManager, restoredSegment1),
                "Dragonfly segment 1 rewind identity must be preserved");
        assertSame(restoredParent, readObjectField(restoredSegment0, "parent"),
                "segment 0 parent must resolve to the restored Dragonfly instance");
        assertSame(restoredParent, readObjectField(restoredSegment0, "followAnchor"),
                "segment 0 followAnchor must resolve to the restored Dragonfly instance");
        assertSame(restoredParent, readObjectField(restoredSegment1, "parent"),
                "segment 1 parent must resolve to the restored Dragonfly instance");
        assertSame(restoredSegment0, readObjectField(restoredSegment1, "followAnchor"),
                "segment 1 followAnchor must resolve to restored segment 0, not a stale pre-restore link");
        assertEquals(0x1A0, readIntField(restoredSegment0, "childX"));
        assertEquals(0x120, readIntField(restoredSegment0, "childY"));
        assertEquals(7, readIntField(restoredSegment0, "countdown"));
        assertEquals(0x1B0, readIntField(restoredSegment1, "childX"));
        assertEquals(0x118, readIntField(restoredSegment1, "childY"));
        assertEquals(5, readIntField(restoredSegment1, "countdown"));
    }

    @Test
    void spikerTopSpikeRestoresExactParentAndCooldownState() {
        Harness harness = Harness.create(new S3klTestRegistry(), List.of(new ObjectSpawn(
                0x160, 0x120, Sonic3kObjectIds.SPIKER, 0, 0, false, 20)));
        ObjectManager objectManager = harness.objectManager();
        SpikerBadnikInstance sourceParent = only(objectManager, SpikerBadnikInstance.class);
        sourceParent.update(0, player());
        sourceParent.update(1, player());
        ObjectInstance sourceTopSpike = onlyByClassName(objectManager, SPIKER_TOP_SPIKE_CHILD);
        setIntField(sourceTopSpike, "cooldown", 9);

        ObjectRefId parentId = objectId(objectManager, sourceParent);
        ObjectRefId topSpikeId = objectId(objectManager, sourceTopSpike);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new SpikerBadnikInstance(new ObjectSpawn(
                0x300, 0x180, Sonic3kObjectIds.SPIKER, 0, 0, false, 21)));

        rewindRegistry.restore(snapshot);

        SpikerBadnikInstance restoredParent = only(objectManager, SpikerBadnikInstance.class);
        ObjectInstance restoredTopSpike = onlyByClassName(objectManager, SPIKER_TOP_SPIKE_CHILD);
        assertNotSame(sourceParent, restoredParent, "restore must recreate the removed Spiker parent");
        assertNotSame(sourceTopSpike, restoredTopSpike, "restore must recreate the removed top spike");
        assertEquals(parentId, objectId(objectManager, restoredParent),
                "Spiker parent rewind identity must be preserved");
        assertEquals(topSpikeId, objectId(objectManager, restoredTopSpike),
                "Spiker top spike rewind identity must be preserved");
        assertSame(restoredParent, readObjectField(restoredTopSpike, "parent"),
                "top spike parent must resolve to the restored Spiker instance");
        assertEquals(9, readIntField(restoredTopSpike, "cooldown"),
                "top spike cooldown must be restored from compact state");
    }

    @Test
    void turboSpikerShellRestoresExactParentAndAttachedShellStateWithoutTrailEmitter() {
        Harness harness = Harness.create(new S3klTestRegistry(), List.of(new ObjectSpawn(
                0x1C0, 0x140, Sonic3kObjectIds.TURBO_SPIKER, 4, 0, false, 30)));
        ObjectManager objectManager = harness.objectManager();
        TestablePlayableSprite player = player();
        TurboSpikerBadnikInstance sourceParent = only(objectManager, TurboSpikerBadnikInstance.class);
        sourceParent.update(0, player);
        ObjectInstance sourceShell = onlyByClassName(objectManager, TURBO_SPIKER_SHELL_CHILD);
        setIntField(sourceShell, "currentX", 0x1D8);
        setIntField(sourceShell, "currentY", 0x148);
        setIntField(sourceShell, "xVelocity", 0x33);
        setIntField(sourceShell, "yVelocity", -0x44);

        ObjectRefId parentId = objectId(objectManager, sourceParent);
        ObjectRefId shellId = objectId(objectManager, sourceShell);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new TurboSpikerBadnikInstance(new ObjectSpawn(
                0x340, 0x180, Sonic3kObjectIds.TURBO_SPIKER, 4, 0, false, 31)));

        rewindRegistry.restore(snapshot);

        TurboSpikerBadnikInstance restoredParent = only(objectManager, TurboSpikerBadnikInstance.class);
        ObjectInstance restoredShell = onlyByClassName(objectManager, TURBO_SPIKER_SHELL_CHILD);
        assertNotSame(sourceParent, restoredParent, "restore must recreate the removed Turbo Spiker parent");
        assertNotSame(sourceShell, restoredShell, "restore must recreate the removed shell child");
        assertEquals(parentId, objectId(objectManager, restoredParent),
                "Turbo Spiker parent rewind identity must be preserved");
        assertEquals(shellId, objectId(objectManager, restoredShell),
                "Turbo Spiker shell rewind identity must be preserved");
        assertSame(restoredParent, readObjectField(restoredShell, "parent"),
                "shell parent must resolve to the restored Turbo Spiker instance");
        assertTrue((Boolean) readObjectField(restoredShell, "attached"),
                "attached shell state must restore as attached");
        assertNull(readObjectField(restoredShell, "trailEmitter"),
                "attached shell restore must not synthesize a launched-shell trail emitter");
        assertEquals(0x1D8, readIntField(restoredShell, "currentX"));
        assertEquals(0x148, readIntField(restoredShell, "currentY"));
        assertEquals(0x33, readIntField(restoredShell, "xVelocity"));
        assertEquals(-0x44, readIntField(restoredShell, "yVelocity"));
    }

    @Test
    void captureFailsForNonNullObjectReferenceWithoutRegisteredRewindIdentity() {
        Harness harness = Harness.create(new MhzTestRegistry(), List.of());
        ObjectManager objectManager = harness.objectManager();
        DragonflyBadnikInstance externalParent = new DragonflyBadnikInstance(new ObjectSpawn(
                0x120, 0x100, Sonic3kObjectIds.DRAGONFLY, 0, 0, false, 40));
        ObjectInstance child = objectManager.createDynamicObject(
                () -> instantiateDragonflyLinkedBodyChild(externalParent));
        assertSame(externalParent, readObjectField(child, "parent"),
                "precondition: child holds a non-null parent outside ObjectManager identity registration");

        RewindRegistry rewindRegistry = registryFor(objectManager);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, rewindRegistry::capture);
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "missing required object targets must fail loudly");
    }

    private static ObjectInstance instantiateDragonflyLinkedBodyChild(DragonflyBadnikInstance parent) {
        try {
            Class<?> cls = Class.forName(DRAGONFLY_LINKED_BODY_CHILD);
            Constructor<?> ctor = cls.getDeclaredConstructor(
                    DragonflyBadnikInstance.class, AbstractObjectInstance.class, int.class, int.class);
            ctor.setAccessible(true);
            return (ObjectInstance) ctor.newInstance(parent, parent, 0, 0);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to construct Dragonfly linked body child", e);
        }
    }

    private static ObjectInstance segmentByIndex(List<ObjectInstance> segments, int segmentIndex) {
        return segments.stream()
                .filter(segment -> readIntField(segment, "segmentIndex") == segmentIndex)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing Dragonfly segment " + segmentIndex));
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

    private static <T extends ObjectInstance> T only(ObjectManager objectManager, Class<T> type) {
        List<T> matches = objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .toList();
        assertEquals(1, matches.size(), "expected exactly one live " + type.getSimpleName());
        return matches.getFirst();
    }

    private static ObjectInstance onlyByClassName(ObjectManager objectManager, String className) {
        List<ObjectInstance> matches = liveByClassName(objectManager, className);
        assertEquals(1, matches.size(), "expected exactly one live " + className);
        return matches.getFirst();
    }

    private static List<ObjectInstance> liveByClassName(ObjectManager objectManager, String className) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals(className) && !object.isDestroyed())
                .sorted(Comparator.comparingInt(TestS3kBadnikChildGraphRewind::slotIndex))
                .toList();
    }

    private static int slotIndex(ObjectInstance object) {
        return object instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1;
    }

    private static int readIntField(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getInt(target);
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

    private static TestablePlayableSprite player() {
        return new TestablePlayableSprite("sonic", (short) 0x180, (short) 0x120);
    }

    private record Harness(ObjectManager objectManager) {
        static Harness create(ObjectRegistry registry, List<ObjectSpawn> spawns) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            TestablePlayableSprite player = player();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public ObjectPlayerQuery playerQuery() {
                    return new ObjectPlayerQuery(() -> player, List::of);
                }
            };
            ObjectManager objectManager = new ObjectManager(
                    spawns,
                    registry,
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(0);
            return new Harness(objectManager);
        }
    }

    private static final class S3klTestRegistry extends Sonic3kObjectRegistry {
        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_HCZ;
        }
    }

    private static final class MhzTestRegistry extends Sonic3kObjectRegistry {
        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_MHZ;
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 0x400; }
            @Override public short getHeight() { return 0x300; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
