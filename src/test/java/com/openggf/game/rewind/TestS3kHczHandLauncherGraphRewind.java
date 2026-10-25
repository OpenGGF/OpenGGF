package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.HCZHandLauncherObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kHczHandLauncherGraphRewind {
    private static final String ARM_CLASS =
            "com.openggf.game.sonic3k.objects.HCZHandLauncherObjectInstance$HandLauncherArmChild";
    private static final ObjectSpawn LAUNCHER_SPAWN =
            new ObjectSpawn(0x1600, 0x0480, Sonic3kObjectIds.HCZ_HAND_LAUNCHER, 0, 0, false, 12);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void handLauncherArmRestoresFreshRelinkParentAndDoesNotDuplicate() throws Exception {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        HCZHandLauncherObjectInstance sourceLauncher = objectManager.createDynamicObject(
                () -> new HCZHandLauncherObjectInstance(LAUNCHER_SPAWN));
        sourceLauncher.update(0, null);
        ObjectInstance sourceArm = onlyArm(objectManager);
        ObjectRefId launcherId = objectId(objectManager, sourceLauncher);

        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceArm);
        ObjectInstance divergentArm = objectManager.createDynamicObject(
                () -> constructArm(sourceLauncher));
        assertEquals(1, liveArms(objectManager).size(),
                "diverge step should leave one unrelated hand-launcher arm before restore");

        rewindRegistry.restore(snapshot);

        HCZHandLauncherObjectInstance restoredLauncher =
                objectById(objectManager, HCZHandLauncherObjectInstance.class, launcherId);
        ObjectInstance restoredArm = onlyArm(objectManager);
        assertNotSame(sourceLauncher, restoredLauncher,
                "restore must recreate the HCZ hand launcher parent");
        assertNotSame(sourceArm, restoredArm,
                "restore must recreate the HCZ hand-launcher arm");
        assertNotSame(divergentArm, restoredArm,
                "restore must drop the divergent hand-launcher arm");
        assertSame(restoredLauncher, readObjectField(restoredArm, "parent"),
                "hand-launcher arm parent must resolve to the restored launcher");
        assertSame(restoredArm, readObjectField(restoredLauncher, "armChild"),
                "restored HCZ hand launcher must point at the restored arm child");

        restoredLauncher.update(1, null);
        assertEquals(1, liveArms(objectManager).size(),
                "post-restore update must not spawn a duplicate hand-launcher arm");
    }

    @Test
    void handLauncherFamilyUsesGenericRecreateWithoutExplicitS3kCodecs() throws Exception {
        assertTrue(RewindRecreatable.class.isAssignableFrom(HCZHandLauncherObjectInstance.class),
                "HCZ hand launcher must restore through generic recreate");
        assertTrue(RewindRecreatable.class.isAssignableFrom(Class.forName(ARM_CLASS)),
                "HCZ hand-launcher arm must restore through generic graph recreate");
    }

    @Test
    void retainedStandingRestoresBothWaysAndReplaysGrabAndRelease() throws Exception {
        com.openggf.tests.TestEnvironment.configureGameModuleFixture(
                com.openggf.tests.rules.SonicGame.SONIC_3K);
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) (LAUNCHER_SPAWN.x() - 8), (short) LAUNCHER_SPAWN.y());
        boolean[] standing = {false};
        Harness harness = Harness.create(player, standing);
        ObjectManager objects = harness.objectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        HCZHandLauncherObjectInstance launcher = objects.createDynamicObject(
                () -> new HCZHandLauncherObjectInstance(LAUNCHER_SPAWN));
        for (int pass = 0; pass < 6; pass++) launcher.update(pass, player);
        assertEquals(LAUNCHER_SPAWN.y() + 0x20, launcher.getY());
        ObjectRefId id = objectId(objects, launcher);
        RewindRegistry registry = registryFor(objects);
        registry.register(new RewindSnapshottable<PerObjectRewindSnapshot>() {
            @Override public String key() { return "test-player"; }
            @Override public PerObjectRewindSnapshot capture() { return player.captureRewindState(); }
            @Override public void restore(PerObjectRewindSnapshot snapshot) {
                player.restoreRewindState(snapshot);
            }
        });
        CompositeSnapshot beforeLanding = registry.capture();
        standing[0] = true;
        launcher.update(6, player);
        assertEquals(LAUNCHER_SPAWN.y() + 0x18, launcher.getY());
        assertFalse(player.isObjectControlled());
        CompositeSnapshot landed = registry.capture();
        standing[0] = false;
        launcher.update(7, player);
        assertTrue(player.isObjectControlled(), "retained landing must grab without a new contact");
        CompositeSnapshot held = registry.capture();
        player.setJumpInputPressed(true);
        launcher.update(8, player);
        assertFalse(player.isObjectControlled());
        assertEquals(-0x400, player.getYSpeed());

        registry.restore(beforeLanding);
        launcher = objectById(objects, HCZHandLauncherObjectInstance.class, id);
        // Different forward contact after restoring the clear bit must not
        // inherit the later landing/grab, even across fresh object recreation.
        launcher.update(6, player);
        launcher.update(7, player);
        assertFalse(player.isObjectControlled());

        registry.restore(landed);
        launcher = objectById(objects, HCZHandLauncherObjectInstance.class, id);
        launcher.update(7, player);
        assertTrue(player.isObjectControlled());
        assertEquals(1, liveArms(objects).size());
        registry.restore(held);
        launcher = objectById(objects, HCZHandLauncherObjectInstance.class, id);
        assertTrue(player.isObjectControlled());
        player.setJumpInputPressed(true);
        launcher.update(8, player);
        assertFalse(player.isObjectControlled());
        assertEquals(-0x400, player.getYSpeed());
        assertEquals(0x800, player.getXSpeed());
        assertEquals(1, liveArms(objects).size());

        registry.restore(landed);
        launcher = objectById(objects, HCZHandLauncherObjectInstance.class, id);
        launcher.update(7, player);
        for (int pass = 8; pass < 180 && player.isObjectControlled(); pass++) {
            launcher.update(pass, player);
        }
        assertFalse(player.isObjectControlled());
        assertEquals(0x1000, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(1, liveArms(objects).size());
    }

    private record Harness(ObjectManager objectManager, ObjectServices services) {
        static Harness create() { return create(null, new boolean[]{false}); }

        static Harness create(TestablePlayableSprite player, boolean[] standing) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCameraAtOrigin();
            if (player != null) camera.setFocusedSprite(player);
            ObjectPlayerQuery playerQuery = new ObjectPlayerQuery(() -> player, List::of);
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public ObjectPlayerQuery playerQuery() { return playerQuery; }
                @Override public ObjectSolidExecutionContext solidExecution() {
                    HCZHandLauncherObjectInstance launcher = holder[0].getActiveObjects().stream()
                            .filter(HCZHandLauncherObjectInstance.class::isInstance)
                            .map(HCZHandLauncherObjectInstance.class::cast).findFirst().orElseThrow();
                    return new ObjectSolidExecutionContext(SolidExecutionRegistry.inert(), launcher, () -> {
                        PlayerSolidContactResult contact = new PlayerSolidContactResult(
                                ContactKind.TOP, true, false, false, false, PreContactState.ZERO,
                                new PostContactState((short) 0, (short) 0, false, true, false), 0);
                        return new SolidCheckpointBatch(launcher,
                                standing[0] && player != null ? Map.of(player, contact) : Map.of());
                    });
                }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(),
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

    private static ObjectInstance constructArm(HCZHandLauncherObjectInstance parent) {
        try {
            Class<?> armType = Class.forName(ARM_CLASS);
            Constructor<?> ctor = armType.getDeclaredConstructor(ObjectSpawn.class, HCZHandLauncherObjectInstance.class);
            ctor.setAccessible(true);
            return (ObjectInstance) ctor.newInstance(LAUNCHER_SPAWN, parent);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to construct HCZ hand-launcher arm", e);
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

    private static ObjectInstance onlyArm(ObjectManager objectManager) {
        List<ObjectInstance> arms = liveArms(objectManager);
        assertEquals(1, arms.size(), "expected exactly one live HCZ hand-launcher arm");
        return arms.getFirst();
    }

    private static List<ObjectInstance> liveArms(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals(ARM_CLASS))
                .filter(object -> !object.isDestroyed())
                .toList();
    }

    private static Camera mockCameraAtOrigin() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }

    private static Object readObjectField(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.get(target);
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
