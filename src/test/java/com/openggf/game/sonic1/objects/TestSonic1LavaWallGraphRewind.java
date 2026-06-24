package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.DeletedDynamicRewindCodecs;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic1LavaWallGraphRewind {
    private static final ObjectSpawn CAPTURED_MAIN_SPAWN =
            new ObjectSpawn(0x0600, 0x0180, Sonic1ObjectIds.LAVA_WALL, 0, 0, false, 40);
    private static final ObjectSpawn CAPTURED_TRAIL_SPAWN =
            new ObjectSpawn(0x0580, 0x0180, Sonic1ObjectIds.LAVA_WALL, 0, 0, false, 41);
    private static final ObjectSpawn REPLACEMENT_MAIN_SPAWN =
            new ObjectSpawn(0x0700, 0x0180, Sonic1ObjectIds.LAVA_WALL, 0, 0, false, 42);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void lavaWallMainAndTrailRestoreFreshWithoutDropsDoublesOrStaleReferences() {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        Sonic1LavaWallObjectInstance beforeMain = objectManager.createDynamicObject(
                () -> new Sonic1LavaWallObjectInstance(CAPTURED_MAIN_SPAWN));
        seedMain(beforeMain, 0x0620, 0x180, true, 2, 1, 4, 3, true);
        Sonic1LavaWallObjectInstance beforeTrail = objectManager.createDynamicObject(
                () -> newTrail(CAPTURED_TRAIL_SPAWN, beforeMain));
        seedTrail(beforeTrail, 0x05A0, 6, 4);

        ObjectRefId mainId = objectId(objectManager, beforeMain);
        ObjectRefId trailId = objectId(objectManager, beforeTrail);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(beforeTrail);
        objectManager.removeDynamicObject(beforeMain);
        Sonic1LavaWallObjectInstance replacementMain = objectManager.createDynamicObject(
                () -> new Sonic1LavaWallObjectInstance(REPLACEMENT_MAIN_SPAWN));
        seedMain(replacementMain, 0x0700, 0, false, 4, 0, 0, 0, false);
        Sonic1LavaWallObjectInstance replacementTrail = objectManager.createDynamicObject(
                () -> newTrail(new ObjectSpawn(0x0680, 0x0180, Sonic1ObjectIds.LAVA_WALL, 0, 0, false, 43),
                        replacementMain));

        rewindRegistry.restore(snapshot);

        assertEquals(2, liveWalls(objectManager).size(),
                "restore must keep exactly the captured lava wall main and trail");
        Sonic1LavaWallObjectInstance restoredMain =
                assertInstanceOf(Sonic1LavaWallObjectInstance.class, objectWithId(objectManager, mainId));
        Sonic1LavaWallObjectInstance restoredTrail =
                assertInstanceOf(Sonic1LavaWallObjectInstance.class, objectWithId(objectManager, trailId));

        assertNotSame(beforeMain, restoredMain, "restore must recreate the removed main wall");
        assertNotSame(beforeTrail, restoredTrail, "restore must recreate the removed trail wall");
        assertNotSame(replacementMain, restoredMain, "restore must drop unrelated replacement main walls");
        assertNotSame(replacementTrail, restoredTrail, "restore must drop unrelated replacement trail walls");
        assertEquals("MAIN", roleName(restoredMain), "main role must be reconstructed from graph topology");
        assertEquals("TRAIL", roleName(restoredTrail), "trail role must be reconstructed from graph topology");
        assertSame(restoredMain, readObject(restoredTrail, "mainWall"),
                "trail mainWall must point at the restored main wall");
        assertNotSame(beforeMain, readObject(restoredTrail, "mainWall"),
                "trail mainWall must not retain stale pre-restore refs");
        assertEquals(0x0620, readInt(restoredMain, "currentX"), "main currentX scalar must restore");
        assertEquals(0x05A0, readInt(restoredTrail, "currentX"), "trail currentX scalar must restore");
        assertEquals(0x180, readInt(restoredMain, "velX"), "velocity scalar must restore");
        assertTrue(readBoolean(restoredMain, "moveFlag"), "move flag scalar must restore");
        assertTrue(readBoolean(restoredMain, "childSpawned"), "child-spawn guard must restore");
        assertEquals(CAPTURED_MAIN_SPAWN.y(), readInt(restoredMain, "currentY"),
                "main final Y must be reconstructed from the captured spawn");
        assertEquals(CAPTURED_TRAIL_SPAWN.y(), readInt(restoredTrail, "currentY"),
                "trail final Y must be reconstructed from the restored main");

        restoredMain.update(1, null);
        assertEquals(2, liveWalls(objectManager).size(),
                "restored main update must not spawn a duplicate trail wall");
        assertSame(restoredMain, readObject(restoredTrail, "mainWall"),
                "trail must stay linked to the restored main after update");
    }

    @Test
    void lavaWallTrailMainRefStillRequiresRewindIdentity() {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        Sonic1LavaWallObjectInstance unmanagedMain =
                new Sonic1LavaWallObjectInstance(CAPTURED_MAIN_SPAWN);
        objectManager.createDynamicObject(() -> newTrail(CAPTURED_TRAIL_SPAWN, unmanagedMain));

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> registryFor(objectManager).capture(),
                "required lava wall main refs must fail loudly when the target has no rewind identity");
        assertTrue(thrown.getMessage().contains("no registered id for object reference"));
    }

    @Test
    void lavaWallUsesGenericRecreateWithoutExplicitDynamicCodec() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(Sonic1LavaWallObjectInstance.class),
                "lava wall must restore through RewindRecreatable generic recreate");
        assertFalse(DeletedDynamicRewindCodecs.hasRegisteredDynamicCodec(
                        Sonic1LavaWallObjectInstance.class.getName()),
                "lava wall must not keep an explicit S1 dynamic rewind codec");
    }

    private static void seedMain(
            Sonic1LavaWallObjectInstance wall,
            int currentX,
            int velX,
            boolean moveFlag,
            int routine,
            int animFrameIndex,
            int animTimer,
            int displayFrame,
            boolean childSpawned) {
        writeInt(wall, "currentX", currentX);
        writeInt(wall, "velX", velX);
        writeBoolean(wall, "moveFlag", moveFlag);
        writeInt(wall, "routine", routine);
        writeInt(wall, "animFrameIndex", animFrameIndex);
        writeInt(wall, "animTimer", animTimer);
        writeInt(wall, "displayFrame", displayFrame);
        writeBoolean(wall, "childSpawned", childSpawned);
    }

    private static void seedTrail(Sonic1LavaWallObjectInstance wall, int currentX, int routine, int displayFrame) {
        writeInt(wall, "currentX", currentX);
        writeInt(wall, "routine", routine);
        writeInt(wall, "displayFrame", displayFrame);
    }

    private record Harness(ObjectManager objectManager) {
        static Harness create() {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(), new Sonic1ObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = objectManager;
            objectManager.reset(0);
            return new Harness(objectManager);
        }
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "ObjectManager capture identity table must register " + object.getClass());
        return id;
    }

    private static ObjectInstance objectWithId(ObjectManager objectManager, ObjectRefId id) {
        List<ObjectInstance> matches = objectManager.getActiveObjects().stream()
                .filter(object -> id.equals(objectManager.captureIdentityContext().requireIdentityTable().idFor(object)))
                .toList();
        assertEquals(1, matches.size(), "expected one live object for rewind id " + id);
        return matches.getFirst();
    }

    private static List<Sonic1LavaWallObjectInstance> liveWalls(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == Sonic1LavaWallObjectInstance.class)
                .map(Sonic1LavaWallObjectInstance.class::cast)
                .filter(object -> !object.isDestroyed())
                .sorted(Comparator.comparingInt(TestSonic1LavaWallGraphRewind::slotIndex))
                .toList();
    }

    private static int slotIndex(ObjectInstance object) {
        return object instanceof com.openggf.level.objects.AbstractObjectInstance aoi
                ? aoi.getSlotIndex()
                : -1;
    }

    private static Sonic1LavaWallObjectInstance newTrail(
            ObjectSpawn spawn,
            Sonic1LavaWallObjectInstance main) {
        try {
            Constructor<Sonic1LavaWallObjectInstance> constructor =
                    Sonic1LavaWallObjectInstance.class.getDeclaredConstructor(
                            ObjectSpawn.class, Sonic1LavaWallObjectInstance.class);
            constructor.setAccessible(true);
            return constructor.newInstance(spawn, main);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to create lava wall trail fixture", e);
        }
    }

    private static String roleName(Sonic1LavaWallObjectInstance wall) {
        return readObject(wall, "role").toString();
    }

    private static int readInt(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static boolean readBoolean(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static Object readObject(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).get(target);
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

    private static void writeBoolean(Object target, String fieldName, boolean value) {
        try {
            findField(target.getClass(), fieldName).setBoolean(target, value);
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
