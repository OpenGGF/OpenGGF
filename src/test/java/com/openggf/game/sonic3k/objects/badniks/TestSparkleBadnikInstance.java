package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestSparkleBadnikInstance {
    @Test
    void registryRoutesSparkleSlotToConcreteBadnik() throws Exception {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance instance = registry.create(new ObjectSpawn(
                0x2200, 0x0580, Sonic3kObjectIds.SPARKLE, 0, 0, false, 0));

        Class<?> expected = Class.forName(
                "com.openggf.game.sonic3k.objects.badniks.SparkleBadnikInstance");
        assertTrue(expected.isInstance(instance),
                "Object $A4 should be Obj_Sparkle, not a placeholder");
        assertEquals(expected, instance.getClass());
    }

    @Test
    void sparkleUsesRegisteredCnzSparkleRendererAndInitialFrame() {
        SparkleBadnikInstance sparkle = new SparkleBadnikInstance(new ObjectSpawn(
                0x2200, 0x0580, Sonic3kObjectIds.SPARKLE, 0, 0, false, 0));

        LevelManager levelManager = mock(LevelManager.class);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(levelManager.getObjectRenderManager()).thenReturn(renderManager);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.CNZ_SPARKLE)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);

        ((AbstractObjectInstance) sparkle).setServices(
                new TestObjectServices().withLevelManager(levelManager));

        sparkle.appendRenderCommands(new ArrayList<>());

        verify(renderManager).getRenderer(Sonic3kObjectArtKeys.CNZ_SPARKLE);
        verify(renderer).drawFrameIndex(0, 0x2200, 0x0580, false, false);
    }

    @Test
    void bottomSpawnFirstFireTeleportsUpInsteadOfDown() throws Exception {
        SparkleBadnikInstance sparkle = sparkleReadyToFire(0, new ArrayList<>());
        int startY = sparkle.getY();

        sparkle.update(0, null);

        assertEquals(startY - 0x68, sparkle.getY(),
                "Obj_Sparkle uses render_flags bit 1 as the stored vertical phase. "
                        + "A bottom-spawn Sparkle starts with the bit clear, so bchg #1 "
                        + "takes the first teleport upward instead of into the floor "
                        + "(docs/skdisasm/sonic3k.asm:186104-186116)");
    }

    @Test
    void topSpawnChargeCreatesWarningBoltBelowParent() throws Exception {
        List<ObjectInstance> spawned = new ArrayList<>();
        SparkleBadnikInstance sparkle = new SparkleBadnikInstance(new ObjectSpawn(
                0x0100, 0x0100, Sonic3kObjectIds.SPARKLE, 0, 0x02, false, 0));
        sparkle.setServices(new SparkleTestServices(spawned));
        setPrivateField(sparkle, "state", enumConstant(sparkle, "State", "CHARGE"));
        setPrivateField(sparkle, "chargeTimer", 0);
        setPrivateField(sparkle, "chargeDelay", 1);
        setPrivateField(sparkle, "chargeFrameIndex", 1);
        setPrivateField(sparkle, "chargeCycles", 11);

        sparkle.update(0, null);

        assertEquals(1, spawned.size());
        ObjectInstance warning = spawned.get(0);
        assertEquals("SparkleLightningWarning", warning.getName());
        assertEquals(0x0100 + 0x34, warning.getY(),
                "The warning child is positioned once from the parent's pre-toggle "
                        + "render_flags bit 1. Top-spawn Sparkle has that bit set, "
                        + "so the lightning warning appears below it.");
    }

    @Test
    void topSpawnProjectilesTravelUpAfterFirstDownTeleport() throws Exception {
        List<ObjectInstance> spawned = new ArrayList<>();
        SparkleBadnikInstance sparkle = sparkleReadyToFire(0x02, spawned);

        sparkle.update(0, null);

        assertEquals(0x0100 + 0x68, sparkle.getY());
        List<ObjectInstance> projectiles = spawned.stream()
                .filter(object -> "SparkleProjectile".equals(object.getName()))
                .toList();
        assertEquals(2, projectiles.size());

        for (ObjectInstance projectile : projectiles) {
            int initialY = projectile.getY();
            projectile.update(1, null);
            assertTrue(projectile.getY() < initialY,
                    "After a top-spawn Sparkle teleports down, Obj_Sparkle's "
                            + "projectile children read the toggled-clear bit 1 and "
                            + "launch upward (docs/skdisasm/sonic3k.asm:186153-186168)");
        }
    }

    private static SparkleBadnikInstance sparkleReadyToFire(int renderFlags,
            List<ObjectInstance> spawned) throws Exception {
        SparkleBadnikInstance sparkle = new SparkleBadnikInstance(new ObjectSpawn(
                0x0100, 0x0100, Sonic3kObjectIds.SPARKLE, 0, renderFlags, false, 0));
        sparkle.setServices(new SparkleTestServices(spawned));
        setPrivateField(sparkle, "state", enumConstant(sparkle, "State", "WARNING_WAIT"));
        setPrivateField(sparkle, "timer", 0);
        return sparkle;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Object instance, String enumSimpleName, String constantName) {
        for (Class<?> nested : instance.getClass().getDeclaredClasses()) {
            if (nested.getSimpleName().equals(enumSimpleName)) {
                return Enum.valueOf((Class<? extends Enum>) nested.asSubclass(Enum.class), constantName);
            }
        }
        throw new AssertionError("Missing nested enum " + enumSimpleName);
    }

    private static void setPrivateField(Object instance, String fieldName, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class SparkleTestServices extends TestObjectServices {
        private final ObjectManager objectManager;

        private SparkleTestServices(List<ObjectInstance> spawned) {
            this.objectManager = mock(ObjectManager.class);
            doAnswer(invocation -> {
                spawned.add(invocation.getArgument(0));
                return null;
            }).when(objectManager).addDynamicObjectAfterCurrent(any());
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }
    }
}
