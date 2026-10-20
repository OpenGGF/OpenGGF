package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kMonitorGraphRewind {
    private static final String CONTENTS_CLASS =
            "com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance$MonitorContentsSlot";
    private static final String MONITOR_TYPE_CLASS =
            "com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance$MonitorType";
    private static final ObjectSpawn MONITOR_SPAWN =
            new ObjectSpawn(0x0580, 0x0520, Sonic3kObjectIds.MONITOR, 0x03, 0, false, 0x0520, 11);
    private static final ObjectSpawn DIVERGENT_CONTENTS_SPAWN =
            new ObjectSpawn(0x05e0, 0x0528, Sonic3kObjectIds.MONITOR, 0x00, 0, false, 19);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void independentMonitorContentsRestoreTheirOwnExactState() throws Exception {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        Sonic3kMonitorObjectInstance sourceMonitor = only(objectManager, Sonic3kMonitorObjectInstance.class);
        writeBooleanField(sourceMonitor, "broken", true);
        writeIntField(sourceMonitor, "mappingFrame", 0x0B);
        spawnMonitorContents(sourceMonitor, objectManager);
        ObjectInstance sourceContents = onlyContents(objectManager);
        writeIntField(sourceContents, "iconSubY", 0x0500 << 8);
        writeIntField(sourceContents, "iconVelY", -0x240);
        writeIntField(sourceContents, "iconWaitFrames", 9);
        writeBooleanField(sourceContents, "effectApplied", false);
        ObjectRefId monitorId = objectId(objectManager, sourceMonitor);
        ObjectRefId contentsId = objectId(objectManager, sourceContents);

        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceContents);
        ObjectInstance divergentContents = objectManager.createDynamicObject(
                () -> constructContents(DIVERGENT_CONTENTS_SPAWN, "EGGMAN"));
        assertEquals(1, liveContents(objectManager).size(),
                "diverge step should leave one unrelated S3K monitor contents object before restore");

        rewindRegistry.restore(snapshot);

        Sonic3kMonitorObjectInstance restoredMonitor =
                objectById(objectManager, Sonic3kMonitorObjectInstance.class, monitorId);
        ObjectInstance restoredContents = objectByIdRaw(objectManager, Class.forName(CONTENTS_CLASS), contentsId);
        assertEquals(1, liveContents(objectManager).size(),
                "restore must keep exactly the captured S3K monitor contents object");
        assertNotSame(sourceMonitor, restoredMonitor,
                "restore must recreate the S3K monitor shell");
        assertNotSame(sourceContents, restoredContents,
                "restore must recreate the S3K monitor contents object");
        assertNotSame(divergentContents, restoredContents,
                "restore must drop the divergent S3K monitor contents object");
        assertTrue(readBooleanField(restoredMonitor, "broken"),
                "restored monitor must keep captured broken-shell state");
        assertEquals(0x0B, readIntField(restoredMonitor, "mappingFrame"),
                "restored monitor must keep captured mapping frame");
        assertFalse(readBooleanField(restoredMonitor, "iconActive"),
                "a managed shell does not run the contents routine itself");
        assertTrue(readBooleanField(restoredContents, "iconActive"),
                "restored contents must keep captured active state");
        assertEquals(0x0500 << 8, readIntField(restoredContents, "iconSubY"),
                "restored contents must keep captured subpixel y");
        assertEquals(-0x240, readIntField(restoredContents, "iconVelY"),
                "restored contents must keep captured velocity");
        assertEquals(9, readIntField(restoredContents, "iconWaitFrames"),
                "restored contents must keep captured wait frames");
        assertEquals("RINGS", readObjectField(restoredContents, "type").toString(),
                "restored contents must keep the captured content type");
        assertEquals(MONITOR_SPAWN.x(), restoredContents.getX(),
                "restored contents keep their own x");
        assertEquals(0x0500, restoredContents.getY(),
                "restored contents read y from their own rise state");
    }

    @Test
    void monitorFamilyUsesGenericRecreateWithoutExplicitS3kCodecs() throws Exception {
        assertTrue(RewindRecreatable.class.isAssignableFrom(Sonic3kMonitorObjectInstance.class),
                "S3K monitor must restore through generic recreate");
        assertTrue(RewindRecreatable.class.isAssignableFrom(Class.forName(CONTENTS_CLASS)),
                "S3K monitor contents slot must restore through generic graph recreate");
    }

    private record Harness(ObjectManager objectManager, Camera camera, ObjectServices services) {
        static Harness create() {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCameraAtMonitor();
            ObjectPlayerQuery playerQuery = new ObjectPlayerQuery(() -> null, List::of);
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public ObjectPlayerQuery playerQuery() { return playerQuery; }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(MONITOR_SPAWN),
                    new Sonic3kObjectRegistry(),
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(camera.getX());
            return new Harness(objectManager, camera, services);
        }
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
    }

    private static void spawnMonitorContents(
            Sonic3kMonitorObjectInstance monitor,
            ObjectManager objectManager) throws Exception {
        Method method = Sonic3kMonitorObjectInstance.class.getDeclaredMethod(
                "spawnMonitorContents", ObjectManager.class, AbstractPlayableSprite.class);
        method.setAccessible(true);
        method.invoke(monitor, objectManager, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ObjectInstance constructContents(ObjectSpawn spawn, String typeName) {
        try {
            Class<?> type = Class.forName(CONTENTS_CLASS);
            Class<? extends Enum> monitorType = (Class<? extends Enum>) Class.forName(MONITOR_TYPE_CLASS);
            Constructor<?> ctor = type.getDeclaredConstructor(ObjectSpawn.class, PlayableEntity.class);
            ctor.setAccessible(true);
            ObjectInstance contents = (ObjectInstance) ctor.newInstance(spawn, null);
            Field field = field(contents, "type");
            field.set(contents, Enum.valueOf(monitorType, typeName));
            return contents;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to construct S3K monitor contents", e);
        }
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "ObjectManager identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T objectById(
            ObjectManager objectManager,
            Class<T> type,
            ObjectRefId id) {
        return objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> id.equals(objectManager.captureIdentityContext()
                        .requireIdentityTable()
                        .idFor(object)))
                .findFirst()
                .orElseThrow();
    }

    private static ObjectInstance objectByIdRaw(
            ObjectManager objectManager,
            Class<?> type,
            ObjectRefId id) {
        return objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .filter(object -> id.equals(objectManager.captureIdentityContext()
                        .requireIdentityTable()
                        .idFor(object)))
                .findFirst()
                .orElseThrow();
    }

    private static ObjectInstance onlyContents(ObjectManager objectManager) {
        List<ObjectInstance> contents = liveContents(objectManager);
        assertEquals(1, contents.size(), "expected exactly one live S3K monitor contents slot");
        return contents.getFirst();
    }

    private static <T extends ObjectInstance> T only(ObjectManager objectManager, Class<T> type) {
        List<T> matches = objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> !object.isDestroyed())
                .toList();
        assertEquals(1, matches.size(), "expected exactly one live " + type.getSimpleName());
        return matches.getFirst();
    }

    private static List<ObjectInstance> liveContents(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals(CONTENTS_CLASS))
                .filter(object -> !object.isDestroyed())
                .toList();
    }

    private static Camera mockCameraAtMonitor() {
        return new Camera() {
            @Override public short getX() { return 0x0500; }
            @Override public short getY() { return 0x04c0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }

    private static Object readObjectField(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.get(target);
    }

    private static int readIntField(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.getInt(target);
    }

    private static boolean readBooleanField(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.getBoolean(target);
    }

    private static void writeIntField(Object target, String name, int value) throws Exception {
        Field field = field(target, name);
        field.setInt(target, value);
    }

    private static void writeBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = field(target, name);
        field.setBoolean(target, value);
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
