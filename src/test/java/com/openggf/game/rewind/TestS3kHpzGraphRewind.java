package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.S3kEmeraldProgression;
import com.openggf.game.sonic3k.objects.HPZMasterEmeraldGlowObjectInstance;
import com.openggf.game.sonic3k.objects.HPZMasterEmeraldObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSSEntryControlObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSanctuaryFallingCrystalObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSanctuarySmallEmeraldCeremonyObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSuperEmeraldObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSuperEmeraldReturnEffectObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestS3kHpzGraphRewind {

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0x1400, 0x200, 0x500, 0x300, 0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void sanctuaryGraphRestoresFreshAndRelinksEveryControllerOwnedChild() {
        GameStateManager gameState = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                gameState, List.of(2, 1, 2, 0, 3, 1, 2), true);
        Harness harness = Harness.create(gameState);
        ObjectManager objects = harness.objectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);

        HPZSSEntryControlObjectInstance controller = objects.createDynamicObject(
                () -> new HPZSSEntryControlObjectInstance(
                        spawn(0xB5, 0), progression, true));
        HPZMasterEmeraldObjectInstance master = objects.createDynamicObject(
                () -> new HPZMasterEmeraldObjectInstance(spawn(0xB0, 0), controller));
        HPZMasterEmeraldGlowObjectInstance glow = objects.createDynamicObject(
                () -> newGlow(master));
        HPZSuperEmeraldObjectInstance pedestal = objects.createDynamicObject(
                () -> new HPZSuperEmeraldObjectInstance(spawn(0xB4, 2), controller));
        HPZSanctuaryFallingCrystalObjectInstance crystal = objects.createDynamicObject(
                () -> new HPZSanctuaryFallingCrystalObjectInstance(controller, 2));
        HPZSanctuarySmallEmeraldCeremonyObjectInstance ceremony = objects.createDynamicObject(
                () -> new HPZSanctuarySmallEmeraldCeremonyObjectInstance(progression));
        HPZSuperEmeraldReturnEffectObjectInstance effect = objects.createDynamicObject(
                () -> new HPZSuperEmeraldReturnEffectObjectInstance(controller));

        RewindRegistry registry = registryFor(objects);
        CompositeSnapshot snapshot = registry.capture();
        List<ObjectInstance> sourceObjects = List.of(
                controller, master, glow, pedestal, crystal, ceremony, effect);
        sourceObjects.forEach(objects::removeDynamicObject);

        registry.restore(snapshot);

        HPZSSEntryControlObjectInstance restoredController =
                onlyLive(objects, HPZSSEntryControlObjectInstance.class);
        HPZMasterEmeraldObjectInstance restoredMaster =
                onlyLive(objects, HPZMasterEmeraldObjectInstance.class);
        HPZMasterEmeraldGlowObjectInstance restoredGlow =
                onlyLive(objects, HPZMasterEmeraldGlowObjectInstance.class);
        HPZSuperEmeraldObjectInstance restoredPedestal =
                onlyLive(objects, HPZSuperEmeraldObjectInstance.class);
        HPZSanctuaryFallingCrystalObjectInstance restoredCrystal =
                onlyLive(objects, HPZSanctuaryFallingCrystalObjectInstance.class);
        onlyLive(objects, HPZSanctuarySmallEmeraldCeremonyObjectInstance.class);
        HPZSuperEmeraldReturnEffectObjectInstance restoredEffect =
                onlyLive(objects, HPZSuperEmeraldReturnEffectObjectInstance.class);

        assertNotSame(controller, restoredController);
        assertNotSame(master, restoredMaster);
        assertNotSame(glow, restoredGlow);
        assertSame(restoredController, fieldValue(restoredMaster, "parentRef"));
        assertSame(restoredMaster, fieldValue(restoredGlow, "parentRef"));
        assertSame(restoredController, fieldValue(restoredPedestal, "parentRef"));
        assertSame(restoredController, fieldValue(restoredCrystal, "parentRef"));
        assertSame(restoredController, fieldValue(restoredEffect, "parentRef"));
    }

    private record Harness(ObjectManager objectManager) {
        static Harness create(GameStateManager gameState) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = new Camera() {
                @Override public short getX() { return 0x1500; }
                @Override public short getY() { return 0x240; }
            };
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GameStateManager gameState() { return gameState; }
                @Override public java.util.OptionalInt sanctuaryReentryStage() {
                    return java.util.OptionalInt.empty();
                }
                @Override public Optional<com.openggf.level.SanctuaryReturnContext>
                        sanctuaryReturnContext() {
                    return Optional.empty();
                }
                @Override public GraphicsManager graphicsManager() {
                    return GraphicsManager.getInstance();
                }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(), null, 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = objectManager;
            objectManager.reset(camera.getX());
            return new Harness(objectManager);
        }
    }

    private static ObjectSpawn spawn(int objectId, int subtype) {
        return new ObjectSpawn(0x1640, 0x340, objectId, subtype, 0, false, -1);
    }

    private static HPZMasterEmeraldGlowObjectInstance newGlow(
            HPZMasterEmeraldObjectInstance parent) {
        try {
            Constructor<HPZMasterEmeraldGlowObjectInstance> constructor =
                    HPZMasterEmeraldGlowObjectInstance.class
                            .getDeclaredConstructor(HPZMasterEmeraldObjectInstance.class);
            constructor.setAccessible(true);
            return constructor.newInstance(parent);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to construct HPZ Master Emerald glow", e);
        }
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(objectManager.rewindSnapshottable());
        return registry;
    }

    private static <T extends ObjectInstance> T onlyLive(
            ObjectManager objectManager, Class<T> type) {
        List<T> matches = objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> !object.isDestroyed())
                .toList();
        assertEquals(1, matches.size(), "restore must leave one " + type.getSimpleName());
        return matches.getFirst();
    }

    private static Object fieldValue(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError("Unable to read " + name, e);
            }
        }
        throw new AssertionError("Missing field " + name + " on " + target.getClass());
    }
}
