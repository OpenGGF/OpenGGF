package com.openggf.game.sonic2.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.DeletedDynamicRewindCodecs;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.boss.AbstractBossChild;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS2DeathEggRobotGraphRewind {
    private static final ObjectSpawn ROBOT_SPAWN =
            new ObjectSpawn(0x0840, 0x0120, Sonic2ObjectIds.DEATH_EGG_ROBOT, 0, 0, false, 0x40);
    private static final ObjectSpawn DIVERGENT_ROBOT_SPAWN =
            new ObjectSpawn(0x0940, 0x0130, Sonic2ObjectIds.DEATH_EGG_ROBOT, 0, 0, false, 0x41);

    @BeforeEach
    void initHeadlessGraphics() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void resetGraphics() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void deathEggRobotGraphRestoresWithoutDropsDoublesOrStaleReferences() {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        RobotGraph source = RobotGraph.spawn(objectManager, ROBOT_SPAWN, 0x0868, 0x0108);
        seedSourceState(source);

        Map<Class<?>, Integer> sourceCounts = source.counts();
        Map<String, ObjectRefId> sourceIds = source.identityPreservedIds(objectManager);
        RewindRegistry registry = registryFor(objectManager);
        CompositeSnapshot snapshot = registry.capture();

        source.removeAll(objectManager);
        RobotGraph divergent = RobotGraph.spawn(objectManager, DIVERGENT_ROBOT_SPAWN, 0x0968, 0x0118);
        assertEquals(sourceCounts, divergent.counts(), "divergent fixture should cover the same graph shape");

        registry.restore(snapshot);

        RobotGraph restored = RobotGraph.fromLiveObjects(objectManager);
        assertEquals(sourceCounts, restored.counts(),
                "restore must not drop or duplicate any Death Egg Robot graph object");
        assertEquals(sourceIds, restored.identityPreservedIds(objectManager),
                "restore must preserve captured Death Egg Robot runtime dynamic identities");
        assertAllReferencesPointAtRestoredGraph(restored);
        assertRestoredObjectsAreFresh(source, divergent, restored);
        assertSourceScalarsRestored(restored);
    }

    @Test
    void deathEggRobotFamilyUsesRewindRecreatableWithoutExplicitDynamicCodecs() {
        for (Class<?> type : List.of(
                Sonic2DeathEggRobotInstance.class,
                Sonic2DeathEggRobotInstance.ArticulatedChild.class,
                Sonic2DeathEggRobotInstance.ForearmChild.class,
                Sonic2DeathEggRobotInstance.HeadChild.class,
                Sonic2DeathEggRobotInstance.JetChild.class,
                Sonic2DeathEggRobotInstance.SensorChild.class)) {
            assertTrue(RewindRecreatable.class.isAssignableFrom(type),
                    type.getSimpleName() + " must restore through generic recreate support");
            assertFalse(DeletedDynamicRewindCodecs.hasRegisteredDynamicCodec(type.getName()),
                    type.getSimpleName() + " must not rely on an explicit dynamic rewind codec");
        }
    }

    private static void seedSourceState(RobotGraph graph) {
        writeIntField(graph.boss(), "actionTimer", 0x123);
        writeIntField(graph.boss(), "attackIndex", 3);
        writeBooleanField(graph.boss(), "facingLeft", true);

        graph.shoulder().startFalling(0x400, -0x200);
        graph.shoulder().update(5, null);
        graph.frontForearm().startFalling(0x300, -0x180);
        graph.frontForearm().update(6, null);

        writeIntField(graph.sensor(), "sensorRoutine", 4);
        writeIntField(graph.sensor(), "countdown", 0x21);
        writeBooleanField(graph.sensor(), "lockOnActive", true);
        writeBooleanField(graph.sensor(), "lockOnPaletteFlip", true);
        writeIntField(graph.sensor(), "currentX", 0x0870);
        writeIntField(graph.sensor(), "currentY", 0x00F8);
    }

    private static void assertAllReferencesPointAtRestoredGraph(RobotGraph graph) {
        assertSame(graph.shoulder(), readObjectField(graph.boss(), "shoulder"));
        assertSame(graph.frontLowerLeg(), readObjectField(graph.boss(), "frontLowerLeg"));
        assertSame(graph.frontForearm(), readObjectField(graph.boss(), "frontForearm"));
        assertSame(graph.upperArm(), readObjectField(graph.boss(), "upperArm"));
        assertSame(graph.frontThigh(), readObjectField(graph.boss(), "frontThigh"));
        assertSame(graph.head(), readObjectField(graph.boss(), "head"));
        assertSame(graph.jet(), readObjectField(graph.boss(), "jet"));
        assertSame(graph.backLowerLeg(), readObjectField(graph.boss(), "backLowerLeg"));
        assertSame(graph.backForearm(), readObjectField(graph.boss(), "backForearm"));
        assertSame(graph.backThigh(), readObjectField(graph.boss(), "backThigh"));
        assertSame(graph.sensor(), readObjectField(graph.boss(), "sensorChild"));

        for (ObjectInstance child : graph.children()) {
            assertSame(graph.boss(), readObjectField(child, "parent"),
                    child.getClass().getSimpleName() + " parent must point at restored boss");
        }
        assertEquals(10, graph.boss().getChildComponents().size(),
                "constructor-owned body children should remain the boss child component set");
        for (ObjectInstance child : graph.constructorChildren()) {
            assertTrue(graph.boss().getChildComponents().contains(child),
                    child.getClass().getSimpleName() + " must be in childComponents");
        }
    }

    private static void assertRestoredObjectsAreFresh(
            RobotGraph source, RobotGraph divergent, RobotGraph restored) {
        for (ObjectInstance object : restored.objects()) {
            assertFalse(source.objects().contains(object), "restore must recreate removed source objects");
            assertFalse(divergent.objects().contains(object), "restore must discard divergent live objects");
        }
    }

    private static void assertSourceScalarsRestored(RobotGraph restored) {
        assertEquals(0x123, readIntField(restored.boss(), "actionTimer"));
        assertEquals(3, readIntField(restored.boss(), "attackIndex"));
        assertEquals(true, readBooleanField(restored.boss(), "facingLeft"));
        assertEquals(true, readBooleanField(restored.frontForearm(), "isFront"));
        assertEquals(false, readBooleanField(restored.backForearm(), "isFront"));
        assertEquals(4, readIntField(restored.sensor(), "sensorRoutine"));
        assertEquals(0x21, readIntField(restored.sensor(), "countdown"));
        assertEquals(true, readBooleanField(restored.sensor(), "lockOnActive"));
        assertEquals(true, readBooleanField(restored.sensor(), "lockOnPaletteFlip"));
        assertEquals(0x0870, restored.sensor().getX());
        assertEquals(0x00F8, restored.sensor().getY());
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(objectManager.rewindSnapshottable());
        return registry;
    }

    private record RobotGraph(
            Sonic2DeathEggRobotInstance boss,
            Sonic2DeathEggRobotInstance.ArticulatedChild shoulder,
            Sonic2DeathEggRobotInstance.ArticulatedChild frontLowerLeg,
            Sonic2DeathEggRobotInstance.ForearmChild frontForearm,
            Sonic2DeathEggRobotInstance.ArticulatedChild upperArm,
            Sonic2DeathEggRobotInstance.ArticulatedChild frontThigh,
            Sonic2DeathEggRobotInstance.HeadChild head,
            Sonic2DeathEggRobotInstance.JetChild jet,
            Sonic2DeathEggRobotInstance.ArticulatedChild backLowerLeg,
            Sonic2DeathEggRobotInstance.ForearmChild backForearm,
            Sonic2DeathEggRobotInstance.ArticulatedChild backThigh,
            Sonic2DeathEggRobotInstance.SensorChild sensor) {

        static RobotGraph spawn(ObjectManager objectManager, ObjectSpawn bossSpawn, int sensorX, int sensorY) {
            Sonic2DeathEggRobotInstance boss =
                    objectManager.createDynamicObject(() -> new Sonic2DeathEggRobotInstance(bossSpawn));
            Sonic2DeathEggRobotInstance.SensorChild sensor = objectManager.createDynamicObject(
                    () -> new Sonic2DeathEggRobotInstance.SensorChild(boss, sensorX, sensorY));
            writeObjectField(boss, "sensorChild", sensor);
            return fromBossAndSensor(boss, sensor);
        }

        static RobotGraph fromLiveObjects(ObjectManager objectManager) {
            return fromBossAndSensor(
                    only(objectManager, Sonic2DeathEggRobotInstance.class),
                    only(objectManager, Sonic2DeathEggRobotInstance.SensorChild.class));
        }

        private static RobotGraph fromBossAndSensor(
                Sonic2DeathEggRobotInstance boss,
                Sonic2DeathEggRobotInstance.SensorChild sensor) {
            return new RobotGraph(
                    boss,
                    readField(boss, "shoulder", Sonic2DeathEggRobotInstance.ArticulatedChild.class),
                    readField(boss, "frontLowerLeg", Sonic2DeathEggRobotInstance.ArticulatedChild.class),
                    readField(boss, "frontForearm", Sonic2DeathEggRobotInstance.ForearmChild.class),
                    readField(boss, "upperArm", Sonic2DeathEggRobotInstance.ArticulatedChild.class),
                    readField(boss, "frontThigh", Sonic2DeathEggRobotInstance.ArticulatedChild.class),
                    readField(boss, "head", Sonic2DeathEggRobotInstance.HeadChild.class),
                    readField(boss, "jet", Sonic2DeathEggRobotInstance.JetChild.class),
                    readField(boss, "backLowerLeg", Sonic2DeathEggRobotInstance.ArticulatedChild.class),
                    readField(boss, "backForearm", Sonic2DeathEggRobotInstance.ForearmChild.class),
                    readField(boss, "backThigh", Sonic2DeathEggRobotInstance.ArticulatedChild.class),
                    sensor);
        }

        Map<Class<?>, Integer> counts() {
            Map<Class<?>, Integer> counts = new LinkedHashMap<>();
            for (ObjectInstance object : objects()) {
                counts.merge(object.getClass(), 1, Integer::sum);
            }
            return counts;
        }

        Map<String, ObjectRefId> identityPreservedIds(ObjectManager objectManager) {
            RewindIdentityTable table = objectManager.captureIdentityContext().requireIdentityTable();
            Map<String, ObjectRefId> ids = new LinkedHashMap<>();
            ids.put("boss", requireId(table, boss));
            ids.put("sensor", requireId(table, sensor));
            return ids;
        }

        void removeAll(ObjectManager objectManager) {
            for (ObjectInstance object : objects().reversed()) {
                objectManager.removeDynamicObject(object);
            }
        }

        List<ObjectInstance> objects() {
            return List.of(
                    boss, shoulder, frontLowerLeg, frontForearm, upperArm, frontThigh,
                    head, jet, backLowerLeg, backForearm, backThigh, sensor);
        }

        List<ObjectInstance> children() {
            return List.of(
                    shoulder, frontLowerLeg, frontForearm, upperArm, frontThigh,
                    head, jet, backLowerLeg, backForearm, backThigh, sensor);
        }

        List<ObjectInstance> constructorChildren() {
            return List.of(
                    shoulder, frontLowerLeg, frontForearm, upperArm, frontThigh,
                    head, jet, backLowerLeg, backForearm, backThigh);
        }

    }

    private static <T extends ObjectInstance> T only(ObjectManager objectManager, Class<T> type) {
        List<T> matches = objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .toList();
        assertEquals(1, matches.size(), "expected exactly one live " + type.getSimpleName());
        return matches.getFirst();
    }

    private static ObjectRefId requireId(RewindIdentityTable table, ObjectInstance object) {
        ObjectRefId id = table.idFor(object);
        assertNotNull(id, "ObjectManager identity table must register " + object.getClass().getName());
        return id;
    }

    private static Object readObjectField(Object target, String name) {
        try {
            return field(target, name).get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static int readIntField(Object target, String name) {
        try {
            return field(target, name).getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean readBooleanField(Object target, String name) {
        try {
            return field(target, name).getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void writeObjectField(Object target, String name, Object value) {
        try {
            field(target, name).set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void writeIntField(Object target, String name, int value) {
        try {
            field(target, name).setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void writeBooleanField(Object target, String name, boolean value) {
        try {
            field(target, name).setBoolean(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static <T> T readField(Object target, String name, Class<T> type) {
        return type.cast(readObjectField(target, name));
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

    private record Harness(ObjectManager objectManager) {
        static Harness create() {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = new Camera() {
                @Override public short getX() { return 0x0800; }
                @Override public short getY() { return 0x0000; }
                @Override public short getWidth() { return 320; }
                @Override public short getHeight() { return 224; }
                @Override public boolean isVerticalWrapEnabled() { return false; }
            };
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
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
            objectManager.reset(camera.getX());
            objectManager.setRewindInPlaceRestoreEnabledForTest(false);
            return new Harness(objectManager);
        }
    }
}
