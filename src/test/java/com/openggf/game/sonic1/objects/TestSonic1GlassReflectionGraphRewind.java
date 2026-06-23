package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestSonic1GlassReflectionGraphRewind {

    private static final ObjectSpawn FAR_GLASS =
            new ObjectSpawn(0x0080, 0x0180, Sonic1ObjectIds.MZ_GLASS_BLOCK, 0x04, 0, false, 10);
    private static final ObjectSpawn NEAR_GLASS =
            new ObjectSpawn(0x0200, 0x01C0, Sonic1ObjectIds.MZ_GLASS_BLOCK, 0x04, 0, false, 11);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void glassReflectionRestoresFreshWithIdentityScalarsAndNearestParentRelink() {
        Harness harness = Harness.create(List.of(FAR_GLASS, NEAR_GLASS));
        ObjectManager objectManager = harness.objectManager();
        Sonic1GlassBlockObjectInstance nearParent =
                liveParentAt(objectManager, NEAR_GLASS.x());
        Sonic1GlassBlockObjectInstance farParent =
                liveParentAt(objectManager, FAR_GLASS.x());
        Sonic1GlassReflectionInstance before = objectManager.createDynamicObject(
                () -> new Sonic1GlassReflectionInstance(
                        NEAR_GLASS, nearParent, reflectedSubtype(NEAR_GLASS), isTall(NEAR_GLASS)));
        writeInt(before, "x", 0x0224);
        writeInt(before, "y", 0x0142);
        writeInt(before, "baseY", 0x01D8);
        writeInt(before, "glassDist", 0x56);

        ObjectRefId beforeId = objectId(objectManager, before);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();
        objectManager.removeDynamicObject(before);
        Sonic1GlassReflectionInstance replacement = objectManager.createDynamicObject(
                () -> new Sonic1GlassReflectionInstance(
                        FAR_GLASS, farParent, reflectedSubtype(FAR_GLASS), isTall(FAR_GLASS)));

        rewindRegistry.restore(snapshot);

        Sonic1GlassReflectionInstance restored = onlyReflection(objectManager);
        Sonic1GlassBlockObjectInstance restoredNearParent =
                liveParentAt(objectManager, NEAR_GLASS.x());
        Sonic1GlassBlockObjectInstance restoredFarParent =
                liveParentAt(objectManager, FAR_GLASS.x());
        assertNotSame(before, restored, "restore must recreate the removed reflection");
        assertNotSame(replacement, restored, "restore must drop unrelated post-snapshot reflections");
        assertEquals(beforeId, objectId(objectManager, restored),
                "reflection rewind identity must be preserved");
        assertSame(restoredNearParent, readObject(restored, "parent"),
                "reflection must relink to the nearest live glass block");
        assertNotSame(restoredFarParent, readObject(restored, "parent"),
                "reflection must not relink to the farther live glass block");
        assertEquals(0x0224, readInt(restored, "x"), "x scalar must restore exactly");
        assertEquals(0x0142, readInt(restored, "y"), "y scalar must restore exactly");
        assertEquals(0x01D8, readInt(restored, "baseY"), "baseY scalar must restore exactly");
        assertEquals(0x56, readInt(restored, "glassDist"), "glassDist scalar must restore exactly");
        assertEquals(reflectedSubtype(NEAR_GLASS), readInt(restored, "reflectSubtype"),
                "reflectSubtype must be derived from the captured spawn subtype");
        assertEquals(isTall(NEAR_GLASS), readBoolean(restored, "isTall"),
                "isTall must be derived from the captured spawn subtype");

        restored.update(1, new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0180));
        assertFalse(restored.isDestroyed(), "restored reflection must survive with its relinked parent");
        assertSame(restoredNearParent, readObject(restored, "parent"),
                "update must continue following the relinked parent");
    }

    @Test
    void directGenericRecreateWithoutLiveGlassBlockReturnsNull() {
        Harness harness = Harness.create(List.of());
        ObjectInstance recreated = genericRecreate(harness.objectManager(), NEAR_GLASS);

        assertNull(recreated, "generic recreate must drop a reflection when no live glass block exists");
    }

    @Test
    void directGenericRecreateRelinksNearestLiveGlassBlock() {
        Harness harness = Harness.create(List.of(FAR_GLASS, NEAR_GLASS));
        ObjectInstance recreated = genericRecreate(harness.objectManager(), NEAR_GLASS);

        Sonic1GlassReflectionInstance reflection =
                assertInstanceOf(Sonic1GlassReflectionInstance.class, recreated);
        assertSame(liveParentAt(harness.objectManager(), NEAR_GLASS.x()),
                readObject(reflection, "parent"),
                "direct generic recreate must relink to the nearest live glass block");
        assertEquals(reflectedSubtype(NEAR_GLASS), readInt(reflection, "reflectSubtype"));
        assertEquals(isTall(NEAR_GLASS), readBoolean(reflection, "isTall"));
    }

    private static ObjectInstance genericRecreate(ObjectManager objectManager, ObjectSpawn spawn) {
        PerObjectRewindSnapshot state = new PerObjectRewindSnapshot(
                false, false, false, 0, 0, 0, 0, false, 0, false, false, 0, -1, null, null, null);
        ObjectManagerSnapshot.DynamicObjectEntry entry =
                new ObjectManagerSnapshot.DynamicObjectEntry(
                        Sonic1GlassReflectionInstance.class.getName(), spawn, 0, state);
        return ObjectRewindDynamicCodecs.genericRecreate(
                entry, new DynamicObjectRecreateContext(objectManager));
    }

    private static int reflectedSubtype(ObjectSpawn spawn) {
        return ((spawn.subtype() & 0xFF) + 8) & 0x0F;
    }

    private static boolean isTall(ObjectSpawn spawn) {
        return (spawn.subtype() & 0xFF) < 3;
    }

    private record Harness(ObjectManager objectManager) {
        static Harness create(List<ObjectSpawn> spawns) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
            };
            ObjectManager objectManager = new ObjectManager(
                    spawns, new Sonic1ObjectRegistry(), 0, null, null,
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

    private static Sonic1GlassBlockObjectInstance liveParentAt(ObjectManager objectManager, int x) {
        List<Sonic1GlassBlockObjectInstance> matches = objectManager.getActiveObjects().stream()
                .filter(object -> object instanceof Sonic1GlassBlockObjectInstance)
                .map(Sonic1GlassBlockObjectInstance.class::cast)
                .filter(object -> !object.isDestroyed())
                .filter(object -> object.getX() == x)
                .toList();
        assertEquals(1, matches.size(), "expected one live glass block at X " + x);
        return matches.getFirst();
    }

    private static Sonic1GlassReflectionInstance onlyReflection(ObjectManager objectManager) {
        List<Sonic1GlassReflectionInstance> reflections = objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == Sonic1GlassReflectionInstance.class)
                .map(Sonic1GlassReflectionInstance.class::cast)
                .filter(object -> !object.isDestroyed())
                .toList();
        assertEquals(1, reflections.size(), "expected exactly one live glass reflection");
        return reflections.getFirst();
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
