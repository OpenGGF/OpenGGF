package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.IczSnowPileObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.IczEndBossInstance;
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

class TestS3kIczEndBossGraphRewind {
    private static final ObjectSpawn BOSS_SPAWN =
            new ObjectSpawn(0x4400, 0x0420, Sonic3kObjectIds.ICZ_END_BOSS, 0, 0, false, 70);
    private static final ObjectSpawn EMITTER_SPAWN =
            new ObjectSpawn(0x4380, 0x02F0, Sonic3kObjectIds.ICZ_SNOW_PILE, 0x18, 0, false, 71);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void iczEndBossRestoresFreshWithRestoredSnowdustEmitterReferenceAndScalars() throws Exception {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        IczEndBossInstance sourceBoss = objectManager.createDynamicObject(
                () -> new IczEndBossInstance(BOSS_SPAWN));
        IczSnowPileObjectInstance sourceEmitter = objectManager.createDynamicObject(
                () -> new IczSnowPileObjectInstance(EMITTER_SPAWN));
        writeObjectField(sourceBoss, "bossSnowdustEmitter", sourceEmitter);
        writeBooleanField(sourceBoss, "snowdustEmitterSpawned", true);
        writeBooleanField(sourceBoss, "arenaGateInitialized", true);
        writeBooleanField(sourceBoss, "arenaGateComplete", true);
        writeIntField(sourceBoss, "routineTimer", 37);
        writeIntField(sourceBoss, "swingTimer", 19);

        ObjectRefId bossId = objectId(objectManager, sourceBoss);
        ObjectRefId emitterId = objectId(objectManager, sourceEmitter);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceEmitter);
        IczSnowPileObjectInstance divergentEmitter = objectManager.createDynamicObject(
                () -> new IczSnowPileObjectInstance(new ObjectSpawn(
                        0x4500, 0x0310, Sonic3kObjectIds.ICZ_SNOW_PILE, 0x18, 0, false, 72)));
        assertEquals(1, liveObjects(objectManager, IczSnowPileObjectInstance.class).size(),
                "diverge step should leave one unrelated snowdust emitter before restore");

        rewindRegistry.restore(snapshot);

        IczEndBossInstance restoredBoss = objectById(objectManager, IczEndBossInstance.class, bossId);
        IczSnowPileObjectInstance restoredEmitter =
                objectById(objectManager, IczSnowPileObjectInstance.class, emitterId);
        assertEquals(1, liveObjects(objectManager, IczEndBossInstance.class).size(),
                "restore must keep exactly one captured ICZ end boss");
        assertEquals(1, liveObjects(objectManager, IczSnowPileObjectInstance.class).size(),
                "restore must keep exactly one captured snowdust emitter");
        assertNotSame(sourceBoss, restoredBoss, "restore must recreate the ICZ end boss");
        assertNotSame(sourceEmitter, restoredEmitter, "restore must recreate the snowdust emitter");
        assertNotSame(divergentEmitter, restoredEmitter, "restore must drop the divergent emitter");
        assertSame(restoredEmitter, readObjectField(restoredBoss, "bossSnowdustEmitter"),
                "boss snowdust emitter must resolve to the restored emitter");
        assertNotSame(sourceEmitter, readObjectField(restoredBoss, "bossSnowdustEmitter"),
                "boss must not retain the stale pre-restore emitter");
        assertEquals(BOSS_SPAWN.x(), restoredBoss.getX(),
                "spawn-derived boss x coordinate must be rebuilt by recreate");
        assertEquals(BOSS_SPAWN.y(), restoredBoss.getY(),
                "spawn-derived boss y coordinate must be rebuilt by recreate");
        assertTrue(readBooleanField(restoredBoss, "snowdustEmitterSpawned"),
                "snowdustEmitterSpawned flag must restore from compact state");
        assertTrue(readBooleanField(restoredBoss, "arenaGateInitialized"),
                "arenaGateInitialized flag must restore from compact state");
        assertTrue(readBooleanField(restoredBoss, "arenaGateComplete"),
                "arenaGateComplete flag must restore from compact state");
        assertEquals(37, readIntField(restoredBoss, "routineTimer"),
                "routine timer must restore from compact state");
        assertEquals(19, readIntField(restoredBoss, "swingTimer"),
                "swing timer must restore from compact state");
    }

    @Test
    void iczEndBossUsesGenericRecreateWithoutExplicitS3kCodec() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(IczEndBossInstance.class),
                "ICZ end boss must restore through RewindRecreatable generic recreate");
        assertFalse(DeletedDynamicRewindCodecs.hasRegisteredDynamicCodec(IczEndBossInstance.class.getName()),
                "ICZ end boss must not keep an explicit S3K dynamic codec");
    }

    @Test
    void captureFailsWhenIczEndBossSnowdustEmitterHasNoRewindIdentity() throws Exception {
        Harness harness = Harness.create();
        IczEndBossInstance boss = harness.objectManager().createDynamicObject(
                () -> new IczEndBossInstance(BOSS_SPAWN));
        IczSnowPileObjectInstance unmanagedEmitter = new IczSnowPileObjectInstance(EMITTER_SPAWN);
        unmanagedEmitter.setServices(harness.services());
        writeObjectField(boss, "bossSnowdustEmitter", unmanagedEmitter);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> registryFor(harness.objectManager()).capture());
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "missing ICZ snowdust emitter identity must fail loudly");
    }

    private record Harness(ObjectManager objectManager, ObjectServices services) {
        static Harness create() {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
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
            return new Harness(objectManager, services);
        }
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
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
        return liveObjects(objectManager, type).stream()
                .filter(object -> id.equals(objectManager.captureIdentityContext()
                        .requireIdentityTable()
                        .idFor(object)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing restored object " + id));
    }

    private static <T extends ObjectInstance> List<T> liveObjects(ObjectManager objectManager, Class<T> type) {
        return objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> !object.isDestroyed())
                .sorted(Comparator.comparingInt(ObjectInstance::getX))
                .toList();
    }

    private static Object readObjectField(Object target, String name) throws Exception {
        return field(target, name).get(target);
    }

    private static int readIntField(Object target, String name) throws Exception {
        return field(target, name).getInt(target);
    }

    private static boolean readBooleanField(Object target, String name) throws Exception {
        return field(target, name).getBoolean(target);
    }

    private static void writeObjectField(Object target, String name, Object value) throws Exception {
        field(target, name).set(target, value);
    }

    private static void writeIntField(Object target, String name, int value) throws Exception {
        field(target, name).setInt(target, value);
    }

    private static void writeBooleanField(Object target, String name, boolean value) throws Exception {
        field(target, name).setBoolean(target, value);
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

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0x4380; }
            @Override public short getY() { return 0x02F8; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
