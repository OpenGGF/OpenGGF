package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.IczIceSpikesObjectInstance;
import com.openggf.game.sonic3k.objects.MgzMinibossInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DynamicObjectRewindCodec;
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
import java.lang.reflect.Method;
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

class TestS3kNestedHurtboxGraphRewind {
    private static final String MGZ_DRILL_ARM_CLASS =
            "com.openggf.game.sonic3k.objects.MgzMinibossInstance$DrillArmChild";
    private static final String ICZ_HURT_CHILD_CLASS =
            "com.openggf.game.sonic3k.objects.IczIceSpikesObjectInstance$SpikeHurtChild";

    private static final ObjectSpawn MGZ_BOSS_SPAWN =
            new ObjectSpawn(0x0300, 0x0200, Sonic3kObjectIds.MGZ_MINIBOSS, 0, 0, false, 10);
    private static final ObjectSpawn ICZ_SPIKE_A =
            new ObjectSpawn(0x0120, 0x0180, Sonic3kObjectIds.ICZ_ICE_SPIKES, 0, 0, false, 20);
    private static final ObjectSpawn ICZ_SPIKE_B =
            new ObjectSpawn(0x0280, 0x01C0, Sonic3kObjectIds.ICZ_ICE_SPIKES, 0, 0, false, 21);

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
    void mgzDrillArmsRestoreFreshRelinkParentSlotsAndDoNotDuplicate() {
        Harness harness = Harness.create(List.of(MGZ_BOSS_SPAWN));
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        MgzMinibossInstance sourceBoss = only(objectManager, MgzMinibossInstance.class);
        invokeNoArg(sourceBoss, "spawnArmChildren");
        List<ObjectInstance> sourceArms = liveObjectsByName(objectManager, MGZ_DRILL_ARM_CLASS);
        assertEquals(2, sourceArms.size(), "precondition: MGZ miniboss must have two drill arms");
        ObjectInstance sourceLeft = armWithXOffset(sourceArms, -0x1C);
        ObjectInstance sourceRight = armWithXOffset(sourceArms, 0x1C);
        setIntField(sourceLeft, "currentX", 0x02D5);
        setIntField(sourceLeft, "currentY", 0x01E4);
        setIntField(sourceRight, "currentX", 0x032B);
        setIntField(sourceRight, "currentY", 0x01E5);

        ObjectRefId bossId = objectId(objectManager, sourceBoss);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceLeft);
        objectManager.removeDynamicObject(sourceRight);
        ObjectInstance divergentArm = objectManager.createDynamicObject(
                () -> constructMgzArm(sourceBoss, 0x40, 0));
        assertEquals(1, liveObjectsByName(objectManager, MGZ_DRILL_ARM_CLASS).size(),
                "diverge step should leave one unrelated arm before restore");

        rewindRegistry.restore(snapshot);

        MgzMinibossInstance restoredBoss = objectById(objectManager, MgzMinibossInstance.class, bossId);
        List<ObjectInstance> restoredArms = liveObjectsByName(objectManager, MGZ_DRILL_ARM_CLASS);
        assertEquals(2, restoredArms.size(), "restore must keep exactly the captured two MGZ drill arms");
        ObjectInstance restoredLeft = armWithXOffset(restoredArms, -0x1C);
        ObjectInstance restoredRight = armWithXOffset(restoredArms, 0x1C);
        assertNotSame(sourceBoss, restoredBoss, "restore must recreate the MGZ miniboss parent");
        assertNotSame(sourceLeft, restoredLeft, "restore must recreate the left arm");
        assertNotSame(sourceRight, restoredRight, "restore must recreate the right arm");
        assertNotSame(divergentArm, restoredLeft, "restore must drop the divergent arm");
        assertNotSame(divergentArm, restoredRight, "restore must drop the divergent arm");

        assertSame(restoredBoss, readObjectField(restoredLeft, "parent"),
                "left arm parent must resolve to the restored MGZ miniboss by captured identity");
        assertSame(restoredBoss, readObjectField(restoredRight, "parent"),
                "right arm parent must resolve to the restored MGZ miniboss by captured identity");
        assertNotSame(sourceBoss, readObjectField(restoredLeft, "parent"),
                "left arm must not retain the stale pre-restore parent");
        assertNotSame(sourceBoss, readObjectField(restoredRight, "parent"),
                "right arm must not retain the stale pre-restore parent");
        assertSame(restoredLeft, readObjectField(restoredBoss, "leftArm"),
                "MGZ miniboss leftArm must point at the restored left arm");
        assertSame(restoredRight, readObjectField(restoredBoss, "rightArm"),
                "MGZ miniboss rightArm must point at the restored right arm");
        assertEquals(0x02D5, readIntField(restoredLeft, "currentX"));
        assertEquals(0x01E4, readIntField(restoredLeft, "currentY"));
        assertEquals(0x032B, readIntField(restoredRight, "currentX"));
        assertEquals(0x01E5, readIntField(restoredRight, "currentY"));

        invokeNoArg(restoredBoss, "spawnArmChildren");
        assertEquals(2, liveObjectsByName(objectManager, MGZ_DRILL_ARM_CLASS).size(),
                "post-restore arm spawn path must not duplicate restored arms");
    }

    @Test
    void mgzFlippedDrillArmsRestoreSemanticParentSlotsAndDoNotDuplicate() {
        Harness harness = Harness.create(List.of(MGZ_BOSS_SPAWN));
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        MgzMinibossInstance sourceBoss = only(objectManager, MgzMinibossInstance.class);
        setBooleanField(sourceBoss, "facingRight", true);
        invokeNoArg(sourceBoss, "spawnArmChildren");
        List<ObjectInstance> sourceArms = liveObjectsByName(objectManager, MGZ_DRILL_ARM_CLASS);
        assertEquals(2, sourceArms.size(), "precondition: flipped MGZ miniboss must have two drill arms");
        ObjectInstance sourceLeft = armWithXOffset(sourceArms, -0x1C);
        ObjectInstance sourceRight = armWithXOffset(sourceArms, 0x1C);
        assertTrue(sourceLeft.getX() > sourceBoss.getX(),
                "precondition: facingRight flips the semantic left arm to the physical right");
        assertTrue(sourceRight.getX() < sourceBoss.getX(),
                "precondition: facingRight flips the semantic right arm to the physical left");

        ObjectRefId bossId = objectId(objectManager, sourceBoss);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceLeft);
        objectManager.removeDynamicObject(sourceRight);
        objectManager.createDynamicObject(() -> constructMgzArm(sourceBoss, 0x40, 0));

        rewindRegistry.restore(snapshot);

        MgzMinibossInstance restoredBoss = objectById(objectManager, MgzMinibossInstance.class, bossId);
        List<ObjectInstance> restoredArms = liveObjectsByName(objectManager, MGZ_DRILL_ARM_CLASS);
        assertEquals(2, restoredArms.size(), "restore must keep exactly the captured flipped MGZ drill arms");
        ObjectInstance restoredLeft = armWithXOffset(restoredArms, -0x1C);
        ObjectInstance restoredRight = armWithXOffset(restoredArms, 0x1C);
        assertSame(restoredBoss, readObjectField(restoredLeft, "parent"),
                "flipped left arm parent must resolve to the restored MGZ miniboss");
        assertSame(restoredBoss, readObjectField(restoredRight, "parent"),
                "flipped right arm parent must resolve to the restored MGZ miniboss");
        assertSame(restoredLeft, readObjectField(restoredBoss, "leftArm"),
                "flipped restore must relink semantic leftArm by captured xOffset, not physical side");
        assertSame(restoredRight, readObjectField(restoredBoss, "rightArm"),
                "flipped restore must relink semantic rightArm by captured xOffset, not physical side");

        invokeNoArg(restoredBoss, "spawnArmChildren");
        assertEquals(2, liveObjectsByName(objectManager, MGZ_DRILL_ARM_CLASS).size(),
                "post-restore flipped arm spawn path must not duplicate restored arms");
    }

    @Test
    void iczSpikeHurtChildrenRestoreFreshRelinkNearestParentsAndDoNotDuplicate() {
        Harness harness = Harness.create(List.of(ICZ_SPIKE_A, ICZ_SPIKE_B));
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        List<IczIceSpikesObjectInstance> sourceParents =
                liveObjects(objectManager, IczIceSpikesObjectInstance.class);
        assertEquals(2, sourceParents.size(), "precondition: two ICZ spike bases must be live");
        IczIceSpikesObjectInstance sourceParentA = sourceParents.get(0);
        IczIceSpikesObjectInstance sourceParentB = sourceParents.get(1);
        sourceParentA.update(0, null);
        sourceParentB.update(0, null);
        List<ObjectInstance> sourceChildren = liveObjectsByName(objectManager, ICZ_HURT_CHILD_CLASS);
        assertEquals(2, sourceChildren.size(), "precondition: each ICZ spike base must spawn one hurt child");
        ObjectInstance sourceChildA = childWithParent(sourceChildren, sourceParentA);
        ObjectInstance sourceChildB = childWithParent(sourceChildren, sourceParentB);

        ObjectRefId parentAId = objectId(objectManager, sourceParentA);
        ObjectRefId parentBId = objectId(objectManager, sourceParentB);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceChildA);
        objectManager.removeDynamicObject(sourceChildB);
        ObjectInstance divergentChild = objectManager.createDynamicObject(
                () -> constructIczHurtChild(sourceParentA, 0x0500, 0x0500));
        assertEquals(1, liveObjectsByName(objectManager, ICZ_HURT_CHILD_CLASS).size(),
                "diverge step should leave one unrelated ICZ hurt child before restore");

        rewindRegistry.restore(snapshot);

        IczIceSpikesObjectInstance restoredParentA =
                objectById(objectManager, IczIceSpikesObjectInstance.class, parentAId);
        IczIceSpikesObjectInstance restoredParentB =
                objectById(objectManager, IczIceSpikesObjectInstance.class, parentBId);
        List<ObjectInstance> restoredChildren = liveObjectsByName(objectManager, ICZ_HURT_CHILD_CLASS);
        assertEquals(2, liveObjects(objectManager, IczIceSpikesObjectInstance.class).size(),
                "restore must keep exactly the captured two ICZ spike parents");
        assertEquals(2, restoredChildren.size(),
                "restore must keep exactly the captured two ICZ hurt children");
        ObjectInstance restoredChildA = childWithParent(restoredChildren, restoredParentA);
        ObjectInstance restoredChildB = childWithParent(restoredChildren, restoredParentB);
        assertNotSame(sourceParentA, restoredParentA, "restore must recreate ICZ parent A");
        assertNotSame(sourceParentB, restoredParentB, "restore must recreate ICZ parent B");
        assertNotSame(sourceChildA, restoredChildA, "restore must recreate ICZ child A");
        assertNotSame(sourceChildB, restoredChildB, "restore must recreate ICZ child B");
        assertNotSame(divergentChild, restoredChildA, "restore must drop the divergent ICZ child");
        assertNotSame(divergentChild, restoredChildB, "restore must drop the divergent ICZ child");

        assertSame(restoredParentA, readObjectField(restoredChildA, "parent"),
                "ICZ child A parent must resolve to restored parent A by captured identity");
        assertSame(restoredParentB, readObjectField(restoredChildB, "parent"),
                "ICZ child B parent must resolve to restored parent B by captured identity");
        assertNotSame(sourceParentA, readObjectField(restoredChildA, "parent"),
                "ICZ child A must not retain the stale pre-restore parent");
        assertNotSame(sourceParentB, readObjectField(restoredChildB, "parent"),
                "ICZ child B must not retain the stale pre-restore parent");
        assertSame(restoredChildA, readObjectField(restoredParentA, "hurtChild"),
                "ICZ parent A hurtChild must point at the restored child");
        assertSame(restoredChildB, readObjectField(restoredParentB, "hurtChild"),
                "ICZ parent B hurtChild must point at the restored child");
        assertTrue(readBooleanField(restoredParentA, "childSpawned"),
                "ICZ parent A childSpawned latch must remain true after relink");
        assertTrue(readBooleanField(restoredParentB, "childSpawned"),
                "ICZ parent B childSpawned latch must remain true after relink");

        restoredParentA.update(1, null);
        restoredParentB.update(1, null);
        assertEquals(2, liveObjectsByName(objectManager, ICZ_HURT_CHILD_CLASS).size(),
                "post-restore ICZ update must not spawn duplicate hurt children");
    }

    @Test
    void nestedHurtboxChildrenUseRewindRecreatableWithoutExplicitDynamicCodecs() throws Exception {
        assertTrue(RewindRecreatable.class.isAssignableFrom(Class.forName(MGZ_DRILL_ARM_CLASS)),
                "MGZ drill arm must restore through RewindRecreatable generic recreate");
        assertTrue(RewindRecreatable.class.isAssignableFrom(Class.forName(ICZ_HURT_CHILD_CLASS)),
                "ICZ ice-spike hurt child must restore through RewindRecreatable generic recreate");
        assertFalse(hasExplicitS3kDynamicCodec(MGZ_DRILL_ARM_CLASS),
                "MGZ drill arm must not keep an explicit S3K dynamic codec");
        assertFalse(hasExplicitS3kDynamicCodec(ICZ_HURT_CHILD_CLASS),
                "ICZ ice-spike hurt child must not keep an explicit S3K dynamic codec");
    }

    @Test
    void captureFailsForNestedHurtboxWhoseRequiredParentHasNoRewindIdentity() {
        Harness mgzHarness = Harness.create(List.of());
        MgzMinibossInstance unmanagedBoss = new MgzMinibossInstance(MGZ_BOSS_SPAWN);
        unmanagedBoss.setServices(mgzHarness.services());
        ObjectInstance arm = mgzHarness.objectManager().createDynamicObject(
                () -> constructMgzArm(unmanagedBoss, -0x1C, -0x16));
        assertSame(unmanagedBoss, readObjectField(arm, "parent"),
                "precondition: MGZ arm parent is outside ObjectManager identity registration");

        IllegalStateException mgzThrown = assertThrows(
                IllegalStateException.class, registryFor(mgzHarness.objectManager())::capture);
        assertTrue(mgzThrown.getMessage().contains("no registered id for object reference"),
                "missing MGZ parent identity must fail loudly");

        Harness iczHarness = Harness.create(List.of());
        IczIceSpikesObjectInstance unmanagedSpike = new IczIceSpikesObjectInstance(ICZ_SPIKE_A);
        unmanagedSpike.setServices(iczHarness.services());
        ObjectInstance hurtChild = iczHarness.objectManager().createDynamicObject(
                () -> constructIczHurtChild(unmanagedSpike, ICZ_SPIKE_A.x(), ICZ_SPIKE_A.y() + 0x0C));
        assertSame(unmanagedSpike, readObjectField(hurtChild, "parent"),
                "precondition: ICZ hurt child parent is outside ObjectManager identity registration");

        IllegalStateException iczThrown = assertThrows(
                IllegalStateException.class, registryFor(iczHarness.objectManager())::capture);
        assertTrue(iczThrown.getMessage().contains("no registered id for object reference"),
                "missing ICZ parent identity must fail loudly");
    }

    private record Harness(ObjectManager objectManager, ObjectServices services) {
        static Harness create(List<ObjectSpawn> spawns) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            };
            ObjectManager objectManager = new ObjectManager(
                    spawns,
                    new Sonic3kObjectRegistry(),
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(0);
            return new Harness(objectManager, services);
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

    private static <T extends ObjectInstance> T only(ObjectManager objectManager, Class<T> type) {
        List<T> live = liveObjects(objectManager, type);
        assertEquals(1, live.size(), "expected exactly one live " + type.getSimpleName());
        return live.getFirst();
    }

    private static <T extends ObjectInstance> List<T> liveObjects(ObjectManager objectManager, Class<T> type) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .sorted(Comparator.comparingInt(TestS3kNestedHurtboxGraphRewind::slotIndex))
                .toList();
    }

    private static List<ObjectInstance> liveObjectsByName(ObjectManager objectManager, String className) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals(className) && !object.isDestroyed())
                .sorted(Comparator.comparingInt(TestS3kNestedHurtboxGraphRewind::slotIndex))
                .toList();
    }

    private static int slotIndex(ObjectInstance object) {
        return object instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1;
    }

    private static ObjectInstance armWithXOffset(List<ObjectInstance> arms, int xOffset) {
        List<ObjectInstance> matches = arms.stream()
                .filter(arm -> readIntField(arm, "xOffset") == xOffset)
                .toList();
        assertEquals(1, matches.size(), "expected exactly one MGZ drill arm xOffset " + xOffset);
        return matches.getFirst();
    }

    private static ObjectInstance childWithParent(List<ObjectInstance> children, Object parent) {
        return children.stream()
                .filter(child -> readObjectField(child, "parent") == parent)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing child for parent " + parent));
    }

    private static boolean hasExplicitS3kDynamicCodec(String className) {
        Set<String> names = new HashSet<>();
        for (DynamicObjectRewindCodec codec : new Sonic3kObjectRegistry().dynamicRewindCodecs()) {
            names.add(codec.className());
        }
        return names.contains(className);
    }

    private static ObjectInstance constructMgzArm(MgzMinibossInstance parent, int xOffset, int yOffset) {
        try {
            Class<?> cls = Class.forName(MGZ_DRILL_ARM_CLASS);
            Constructor<?> ctor = cls.getDeclaredConstructor(MgzMinibossInstance.class, int.class, int.class);
            ctor.setAccessible(true);
            return (ObjectInstance) ctor.newInstance(parent, xOffset, yOffset);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to construct MGZ drill arm", e);
        }
    }

    private static ObjectInstance constructIczHurtChild(
            IczIceSpikesObjectInstance parent, int x, int y) {
        try {
            Class<?> cls = Class.forName(ICZ_HURT_CHILD_CLASS);
            Constructor<?> ctor =
                    cls.getDeclaredConstructor(IczIceSpikesObjectInstance.class, int.class, int.class);
            ctor.setAccessible(true);
            return (ObjectInstance) ctor.newInstance(parent, x, y);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to construct ICZ hurt child", e);
        }
    }

    private static void invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to invoke " + methodName + " on " + target.getClass(), e);
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
