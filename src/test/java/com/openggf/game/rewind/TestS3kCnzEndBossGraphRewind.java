package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.CnzCannonInstance;
import com.openggf.game.sonic3k.objects.bosses.CnzEndBossInstance;
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

class TestS3kCnzEndBossGraphRewind {
    private static final ObjectSpawn BOSS_SPAWN =
            new ObjectSpawn(0x3E40, 0x0240, Sonic3kObjectIds.CNZ_END_BOSS, 0, 0, false, 60);
    private static final ObjectSpawn CANNON_SPAWN =
            new ObjectSpawn(0x3F10, 0x0250, Sonic3kObjectIds.CNZ_CANNON, 0, 0, false, 61);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void cnzEndBossRestoresFreshWithRestoredEndCannonReferenceAndScalars() throws Exception {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        CnzEndBossInstance sourceBoss = objectManager.createDynamicObject(
                () -> new CnzEndBossInstance(BOSS_SPAWN));
        CnzCannonInstance sourceCannon = objectManager.createDynamicObject(
                () -> new CnzCannonInstance(CANNON_SPAWN));
        writeObjectField(sourceBoss, "endCannon", sourceCannon);
        writeBooleanField(sourceBoss, "cannonSpawned", true);
        writeBooleanField(sourceBoss, "cannonArmed", true);
        writeIntField(sourceBoss, "cannonLaunchTimer", 23);

        ObjectRefId bossId = objectId(objectManager, sourceBoss);
        ObjectRefId cannonId = objectId(objectManager, sourceCannon);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceCannon);
        CnzCannonInstance divergentCannon = objectManager.createDynamicObject(
                () -> new CnzCannonInstance(new ObjectSpawn(
                        0x4000, 0x0270, Sonic3kObjectIds.CNZ_CANNON, 0, 0, false, 62)));
        assertEquals(1, liveObjects(objectManager, CnzCannonInstance.class).size(),
                "diverge step should leave one unrelated cannon before restore");

        rewindRegistry.restore(snapshot);

        CnzEndBossInstance restoredBoss = objectById(objectManager, CnzEndBossInstance.class, bossId);
        CnzCannonInstance restoredCannon = objectById(objectManager, CnzCannonInstance.class, cannonId);
        assertEquals(1, liveObjects(objectManager, CnzEndBossInstance.class).size(),
                "restore must keep exactly one captured CNZ end boss");
        assertEquals(1, liveObjects(objectManager, CnzCannonInstance.class).size(),
                "restore must keep exactly one captured end cannon");
        assertNotSame(sourceBoss, restoredBoss, "restore must recreate the CNZ end boss");
        assertNotSame(sourceCannon, restoredCannon, "restore must recreate the CNZ cannon");
        assertNotSame(divergentCannon, restoredCannon, "restore must drop the divergent cannon");
        assertSame(restoredCannon, readObjectField(restoredBoss, "endCannon"),
                "boss endCannon must resolve to the restored cannon");
        assertNotSame(sourceCannon, readObjectField(restoredBoss, "endCannon"),
                "boss must not retain the stale pre-restore cannon");
        assertEquals(BOSS_SPAWN.x(), restoredBoss.getX(),
                "spawn-derived final x coordinate must be rebuilt by recreate");
        assertEquals(BOSS_SPAWN.y(), restoredBoss.getY(),
                "spawn-derived final y coordinate must be rebuilt by recreate");
        assertTrue(readBooleanField(restoredBoss, "cannonSpawned"),
                "cannonSpawned flag must restore from compact state");
        assertTrue(readBooleanField(restoredBoss, "cannonArmed"),
                "cannonArmed flag must restore from compact state");
        assertEquals(23, readIntField(restoredBoss, "cannonLaunchTimer"),
                "cannon launch timer must restore from compact state");
    }

    @Test
    void cnzEndBossUsesGenericRecreateWithoutExplicitS3kCodec() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(CnzEndBossInstance.class),
                "CNZ end boss must restore through RewindRecreatable generic recreate");
        assertFalse(DeletedDynamicRewindCodecs.hasRegisteredDynamicCodec(CnzEndBossInstance.class.getName()),
                "CNZ end boss must not keep an explicit S3K dynamic codec");
    }

    @Test
    void captureFailsWhenCnzEndBossCannonHasNoRewindIdentity() throws Exception {
        Harness harness = Harness.create();
        CnzEndBossInstance boss = harness.objectManager().createDynamicObject(
                () -> new CnzEndBossInstance(BOSS_SPAWN));
        CnzCannonInstance unmanagedCannon = new CnzCannonInstance(CANNON_SPAWN);
        unmanagedCannon.setServices(harness.services());
        writeObjectField(boss, "endCannon", unmanagedCannon);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> registryFor(harness.objectManager()).capture());
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "missing CNZ end-cannon identity must fail loudly");
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
            @Override public short getX() { return 0x3D80; }
            @Override public short getY() { return 0x0200; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
