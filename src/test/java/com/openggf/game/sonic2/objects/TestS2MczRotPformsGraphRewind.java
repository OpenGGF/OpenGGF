package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.DeletedDynamicRewindCodecs;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
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

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS2MczRotPformsGraphRewind {
    private static final ObjectSpawn PARENT_SPAWN =
            new ObjectSpawn(0x180, 0x180, Sonic2ObjectIds.MCZ_ROT_PFORMS, 0x18, 0, false, 0, 74);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void mczRotPformsGraphRestoresChildrenWithoutDropsDoublesOrStaleReferences() {
        Harness harness = Harness.create(List.of(PARENT_SPAWN));
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        objectManager.update(0, null, null, 0, false);

        MCZRotPformsObjectInstance beforeParent = onlyParent(objectManager);
        List<MCZRotPformsObjectInstance> beforeChildren = childPlatforms(objectManager);
        assertEquals(2, beforeChildren.size(),
                "fixture must spawn two MCZ rotating-platform children: " + describePlatforms(objectManager));
        setIntField(beforeParent, "phaseDuration", 37);
        setIntField(beforeParent, "x", PARENT_SPAWN.x() + 9);
        setBooleanField(beforeParent, "activated", true);

        ObjectRefId parentId = objectId(objectManager, beforeParent);
        ObjectRefId rightChildId = objectId(objectManager, childAt(beforeChildren, PARENT_SPAWN.x() + 0x40));
        ObjectRefId leftChildId = objectId(objectManager, childAt(beforeChildren, PARENT_SPAWN.x() - 0x40));
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        beforeChildren.forEach(objectManager::removeDynamicObject);

        rewindRegistry.restore(snapshot);

        MCZRotPformsObjectInstance restoredParent =
                objectById(objectManager, MCZRotPformsObjectInstance.class, parentId);
        MCZRotPformsObjectInstance restoredRight =
                objectById(objectManager, MCZRotPformsObjectInstance.class, rightChildId);
        MCZRotPformsObjectInstance restoredLeft =
                objectById(objectManager, MCZRotPformsObjectInstance.class, leftChildId);

        assertEquals(3, livePlatforms(objectManager).size(),
                "restore must keep exactly the captured parent plus two children");
        assertNotSame(beforeParent, restoredParent, "restore must recreate the placed MCZ parent");
        assertNotSame(childAt(beforeChildren, PARENT_SPAWN.x() + 0x40), restoredRight,
                "restore must recreate the right child");
        assertNotSame(childAt(beforeChildren, PARENT_SPAWN.x() - 0x40), restoredLeft,
                "restore must recreate the left child");

        List<?> restoredChildList = readChildren(restoredParent);
        assertEquals(2, restoredChildList.size(), "restored parent must keep its two child refs");
        assertTrue(restoredChildList.contains(restoredRight),
                "restored parent child list must point to the restored right child");
        assertTrue(restoredChildList.contains(restoredLeft),
                "restored parent child list must point to the restored left child");
        for (Object stale : beforeChildren) {
            assertFalse(restoredChildList.contains(stale),
                    "restored parent child list must not retain stale pre-restore child refs");
        }

        assertEquals(37, readIntField(restoredParent, "phaseDuration"),
                "parent phaseDuration scalar must round-trip");
        assertEquals(PARENT_SPAWN.x() + 9, restoredParent.getX(),
                "parent x scalar must round-trip through compact restore");
        assertTrue(readBooleanField(restoredParent, "activated"),
                "parent activation scalar must round-trip");
    }

    @Test
    void mczRotPformsChildrenReferenceStillRequiresRewindIdentity() {
        Harness harness = Harness.create(List.of(PARENT_SPAWN));
        ObjectManager objectManager = harness.objectManager();
        objectManager.update(0, null, null, 0, false);
        MCZRotPformsObjectInstance parent = onlyParent(objectManager);
        MCZRotPformsObjectInstance unmanagedChild = new MCZRotPformsObjectInstance(
                new ObjectSpawn(0x340, 0x220, Sonic2ObjectIds.MCZ_ROT_PFORMS, 0x06, 0, false, 0, 75),
                "MCZRotPforms");
        readChildren(parent).add(unmanagedChild);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class, registryFor(objectManager)::capture);
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "missing non-null child identity must fail loudly");
    }

    @Test
    void mczRotPformsUsesRewindRecreatableWithoutExplicitDynamicCodec() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(MCZRotPformsObjectInstance.class),
                "MCZ rotating platforms must restore through RewindRecreatable");
        assertFalse(DeletedDynamicRewindCodecs.hasRegisteredDynamicCodec(
                        MCZRotPformsObjectInstance.class.getName()),
                "MCZ rotating platforms must not use an explicit S2 dynamic codec");
    }

    private record Harness(ObjectManager objectManager) {
        static Harness create(List<ObjectSpawn> spawns) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public int romZoneId() { return Sonic2ZoneConstants.ROM_ZONE_MCZ; }
            };
            ObjectManager objectManager = new ObjectManager(
                    spawns,
                    new Sonic2ObjectRegistry(),
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

    private static <T extends ObjectInstance> T objectById(
            ObjectManager objectManager, Class<T> type, ObjectRefId id) {
        return livePlatforms(objectManager).stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> objectId(objectManager, object).equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing restored object " + id));
    }

    private static MCZRotPformsObjectInstance onlyParent(ObjectManager objectManager) {
        List<MCZRotPformsObjectInstance> parents = livePlatforms(objectManager).stream()
                .filter(TestS2MczRotPformsGraphRewind::isParentSpawn)
                .toList();
        assertEquals(1, parents.size(),
                "expected one MCZ rotating-platform parent: " + describePlatforms(objectManager));
        return parents.getFirst();
    }

    private static List<MCZRotPformsObjectInstance> childPlatforms(ObjectManager objectManager) {
        return livePlatforms(objectManager).stream()
                .filter(platform -> !isParentSpawn(platform))
                .sorted(Comparator.comparingInt(MCZRotPformsObjectInstance::getX))
                .toList();
    }

    private static boolean isParentSpawn(MCZRotPformsObjectInstance platform) {
        ObjectSpawn spawn = platform.getSpawn();
        return spawn.objectId() == PARENT_SPAWN.objectId()
                && spawn.subtype() == PARENT_SPAWN.subtype()
                && spawn.x() == PARENT_SPAWN.x()
                && spawn.y() == PARENT_SPAWN.y()
                && spawn.layoutIndex() == PARENT_SPAWN.layoutIndex();
    }

    private static List<MCZRotPformsObjectInstance> livePlatforms(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(MCZRotPformsObjectInstance.class::isInstance)
                .map(MCZRotPformsObjectInstance.class::cast)
                .filter(platform -> !platform.isDestroyed())
                .toList();
    }

    private static String describePlatforms(ObjectManager objectManager) {
        return livePlatforms(objectManager).stream()
                .map(platform -> "x=" + platform.getSpawn().x()
                        + ", y=" + platform.getSpawn().y()
                        + ", subtype=0x" + Integer.toHexString(platform.getSpawn().subtype())
                        + ", layout=" + platform.getSpawn().layoutIndex()
                        + ", destroyed=" + platform.isDestroyed())
                .toList()
                .toString();
    }

    private static MCZRotPformsObjectInstance childAt(
            List<MCZRotPformsObjectInstance> children, int x) {
        return children.stream()
                .filter(child -> child.getSpawn().x() == x)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing child at x=" + x));
    }

    @SuppressWarnings("unchecked")
    private static List<MCZRotPformsObjectInstance> readChildren(MCZRotPformsObjectInstance parent) {
        try {
            Field field = MCZRotPformsObjectInstance.class.getDeclaredField("children");
            field.setAccessible(true);
            return (List<MCZRotPformsObjectInstance>) field.get(parent);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
