package com.openggf.game.rewind;

import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SpawnRewindRecreatable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards spawn-only rewind recreators against reintroducing one-method boilerplate.
 */
class TestSpawnRewindRecreatableCleanup {

    private static final List<String> SPAWN_ONLY_RECREATORS = List.of(
            "com.openggf.game.sonic1.objects.badniks.Sonic1BallHogBadnikInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1BatbrainBadnikInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1BombBadnikInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1BurrobotBadnikInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1BuzzBomberBadnikInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1ChopperBadnikInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1CrabmeatBadnikInstance",
            "com.openggf.game.sonic1.objects.Sonic1EggPrisonObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1FlappingDoorObjectInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1JawsBadnikInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1MotobugBadnikInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1NewtronBadnikInstance",
            "com.openggf.game.sonic1.objects.Sonic1PylonObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1RockObjectInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1RollerBadnikInstance",
            "com.openggf.game.sonic1.objects.Sonic1SpinningLightObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1WaterfallObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1WaterfallSoundObjectInstance",
            "com.openggf.game.sonic1.objects.badniks.Sonic1YadrinBadnikInstance",
            "com.openggf.game.sonic1.objects.Sonic1InvisibleBarrierObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1TeleporterObjectInstance",
            "com.openggf.game.sonic2.objects.BombPrizeObjectInstance",
            "com.openggf.game.sonic2.objects.bosses.ARZBossEyes",
            "com.openggf.game.sonic2.objects.SmallMetalPformObjectInstance$SmallMetalPformChildInstance",
            "com.openggf.game.sonic3k.objects.CnzBumperObjectInstance",
            "com.openggf.game.sonic3k.objects.CnzCannonInstance",
            "com.openggf.game.sonic3k.objects.CnzCylinderInstance",
            "com.openggf.game.sonic3k.objects.CnzLightsFlashChildInstance",
            "com.openggf.game.sonic3k.objects.CnzMinibossCoilInstance",
            "com.openggf.game.sonic3k.objects.CnzMinibossScrollControlInstance",
            "com.openggf.game.sonic3k.objects.CnzMinibossSparkInstance",
            "com.openggf.game.sonic3k.objects.CutsceneKnucklesAiz1Instance",
            "com.openggf.game.sonic3k.objects.GumballMachineObjectInstance$ExitTriggerChild",
            "com.openggf.game.sonic3k.objects.HCZConveyorBeltObjectInstance",
            "com.openggf.game.sonic3k.objects.HCZWaterDropObjectInstance$WaterDropChild",
            "com.openggf.game.sonic3k.objects.MgzDrillingRobotnikInstance",
            "com.openggf.game.sonic3k.objects.MgzEndBossInstance",
            "com.openggf.game.sonic3k.objects.MhzPulleyLiftObjectInstance",
            "com.openggf.game.sonic3k.objects.MhzSwingVineObjectInstance",
            "com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance",
            "com.openggf.game.sonic3k.objects.PachinkoFlipperObjectInstance",
            "com.openggf.game.sonic3k.objects.Sonic3kInvisibleBlockObjectInstance",
            "com.openggf.game.sonic3k.objects.Sonic3kInvisibleHurtBlockHObjectInstance",
            "com.openggf.game.sonic3k.objects.Sonic3kInvisibleHurtBlockVObjectInstance",
            "com.openggf.game.sonic3k.objects.badniks.CorkeyBadnikInstance$CorkeyShotChild",
            "com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance");

    @Test
    void spawnOnlyRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_ONLY_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-based recreate hook");
        }
    }

    @Test
    void spawnOnlyRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_ONLY_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnRewindRecreatable's default hook");
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing spawn-only recreator " + className, e);
        }
    }

    private static boolean declaresRecreateForRewind(Class<?> cls) {
        for (Method method : cls.getDeclaredMethods()) {
            if (method.getName().equals("recreateForRewind")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == RewindRecreateContext.class) {
                return true;
            }
        }
        return false;
    }
}
