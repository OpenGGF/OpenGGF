package com.openggf.game.rewind;

import com.openggf.level.objects.AbstractPointsObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;
import com.openggf.level.objects.SpawnCoordinateSubtypeDefaultArgsRewindRecreatable;
import com.openggf.level.objects.SpawnCoordinateZeroScalarArgsRewindRecreatable;
import com.openggf.level.objects.SpawnCoordinateZeroPairRewindRecreatable;
import com.openggf.level.objects.SpawnNullableReferenceRewindRecreatable;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SpawnRomZoneRewindRecreatable;
import com.openggf.level.objects.SpawnServicesDefaultArgsRewindRecreatable;
import com.openggf.level.objects.SpawnServicesRewindRecreatable;
import com.openggf.level.objects.SpawnTrailingZeroIntsRewindRecreatable;
import com.openggf.level.objects.SpawnYCoordinateRewindRecreatable;
import com.openggf.level.objects.ZeroArgRewindRecreatable;
import com.openggf.level.objects.ZeroScalarArgsRewindRecreatable;
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
            "com.openggf.game.sonic2.objects.DestroyedEggPrisonObjectInstance",
            "com.openggf.game.sonic2.objects.HtzGroundFireObjectInstance",
            "com.openggf.game.sonic2.objects.InvisibleBlockObjectInstance",
            "com.openggf.game.sonic2.objects.bosses.ARZBossEyes",
            "com.openggf.game.sonic2.objects.SmallMetalPformObjectInstance$SmallMetalPformChildInstance",
            "com.openggf.game.sonic2.objects.SpikyBlockSpikeInstance",
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

    private static final List<String> SPAWN_COORDINATE_RECREATORS = List.of(
            "com.openggf.game.sonic1.objects.badniks.Sonic1BuzzBomberMissileDissolveInstance",
            "com.openggf.game.sonic2.objects.bosses.LavaBubbleObjectInstance",
            "com.openggf.game.sonic3k.objects.Aiz2BossEndSequenceController",
            "com.openggf.game.sonic3k.objects.Aiz2EndEggCapsuleInstance",
            "com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance$AizTreeRevealControlObjectInstance",
            "com.openggf.game.sonic3k.objects.AizMinibossNapalmProjectile",
            "com.openggf.game.sonic3k.objects.Mgz2EndEggCapsuleInstance",
            "com.openggf.game.sonic3k.objects.S3kBossExplosionChild",
            "com.openggf.game.sonic3k.objects.bosses.HczEndBossGeyserCutscene",
            "com.openggf.game.sonic3k.objects.bosses.HczEndBossEggCapsuleInstance",
            "com.openggf.game.sonic3k.objects.bosses.IczEndBossEggCapsuleInstance",
            "com.openggf.game.sonic3k.objects.bosses.IczEndBossInstance$IczEndBossRobotnikEscapeShip",
            "com.openggf.game.sonic3k.objects.bosses.MhzEndBossEggCapsuleInstance");

    private static final List<String> POINTS_RECREATORS = List.of(
            "com.openggf.game.sonic1.objects.Sonic1PointsObjectInstance",
            "com.openggf.game.sonic2.objects.PointsObjectInstance",
            "com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance");

    private static final List<String> SPAWN_COORDINATE_ZERO_PAIR_RECREATORS = List.of(
            "com.openggf.game.sonic1.objects.badniks.Sonic1BombShrapnelInstance",
            "com.openggf.game.sonic1.objects.Sonic1EndingEmeraldsObjectInstance",
            "com.openggf.game.sonic2.objects.BubbleObjectInstance",
            "com.openggf.game.sonic3k.objects.AizBombExplosionInstance",
            "com.openggf.game.sonic3k.objects.AizMinibossDebrisChild");

    private static final List<String> SPAWN_COORDINATE_ZERO_SCALAR_ARGS_RECREATORS = List.of(
            "com.openggf.game.sonic1.objects.badniks.Sonic1NewtronMissileInstance",
            "com.openggf.level.objects.boss.BossExplosionObjectInstance",
            "com.openggf.game.sonic2.objects.bosses.MCZFallingDebrisInstance",
            "com.openggf.game.sonic2.objects.HtzFireProjectileObjectInstance",
            "com.openggf.game.sonic2.objects.LeafParticleObjectInstance",
            "com.openggf.game.sonic3k.objects.AizEndBossDebrisChild",
            "com.openggf.game.sonic3k.objects.MGZHeadTriggerObjectInstance$HeadTriggerStoneChipChild",
            "com.openggf.game.sonic3k.objects.MGZHeadTriggerProjectileInstance",
            "com.openggf.game.sonic3k.objects.MgzMinibossInstance$CeilingSpireChild");

    private static final List<String> SPAWN_COORDINATE_SUBTYPE_DEFAULT_ARGS_RECREATORS = List.of(
            "com.openggf.game.sonic1.objects.Sonic1MonitorPowerUpObjectInstance",
            "com.openggf.game.sonic3k.objects.AizMinibossImpactFlameChild");

    private static final List<String> SPAWN_COORDINATE_DEFAULT_ARGS_RECREATORS = List.of(
            "com.openggf.game.sonic1.objects.badniks.Sonic1CrabmeatProjectileInstance",
            "com.openggf.game.sonic1.objects.Sonic1ExplosionItemObjectInstance",
            "com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow");

    private static final List<String> SPAWN_AND_COORDINATE_ZERO_SCALAR_ARGS_RECREATORS = List.of(
            "com.openggf.game.sonic2.objects.ArrowProjectileInstance",
            "com.openggf.game.sonic2.objects.VerticalLaserObjectInstance",
            "com.openggf.game.sonic2.objects.WallTurretShotInstance",
            "com.openggf.game.sonic3k.objects.badniks.BlastoidBadnikInstance$BlastoidProjectile",
            "com.openggf.game.sonic3k.objects.badniks.SnaleBlasterBadnikInstance$SnaleBlasterProjectile",
            "com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance$SpikerSpikeProjectile");

    private static final List<String> SPAWN_TRAILING_ZERO_INTS_RECREATORS = List.of(
            "com.openggf.level.objects.EggPrisonAnimalInstance",
            "com.openggf.game.sonic2.objects.bosses.CPZBossFallingPart",
            "com.openggf.game.sonic3k.objects.AizRockFragmentChild");

    private static final List<String> SPAWN_NULLABLE_REFERENCE_RECREATORS = List.of(
            "com.openggf.game.sonic2.objects.EggPrisonButtonObjectInstance",
            "com.openggf.game.sonic2.objects.MonitorContentsObjectInstance");

    private static final List<String> SPAWN_SERVICES_RECREATORS = List.of(
            "com.openggf.level.objects.ExplosionObjectInstance",
            "com.openggf.level.objects.SkidDustObjectInstance");

    private static final List<String> SPAWN_SERVICES_DEFAULT_ARGS_RECREATORS = List.of(
            "com.openggf.level.objects.AnimalObjectInstance",
            "com.openggf.level.objects.AbstractPointsObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1PointsObjectInstance",
            "com.openggf.game.sonic2.objects.PointsObjectInstance",
            "com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance");

    private static final List<String> SPAWN_ROM_ZONE_RECREATORS = List.of(
            "com.openggf.game.sonic1.objects.Sonic1CollapsingFloorObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1FloatingBlockObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1SpikedBallChainObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1StomperDoorObjectInstance");

    private static final List<String> SPAWN_Y_COORDINATE_RECREATORS = List.of(
            "com.openggf.game.sonic3k.objects.AizBattleshipInstance");

    private static final List<String> SPAWN_DEFAULT_ARGS_RECREATORS = List.of(
            "com.openggf.game.sonic3k.objects.Mgz2CapsuleAnimalInstance",
            "com.openggf.game.sonic3k.objects.RockDebrisChild");

    private static final List<String> ZERO_ARG_RECREATORS = List.of(
            "com.openggf.game.sonic1.objects.Sonic1EndingSTHObjectInstance",
            "com.openggf.game.sonic3k.objects.AizBgTreeSpawnerInstance",
            "com.openggf.game.sonic3k.objects.AizBossSmallInstance");

    private static final List<String> ZERO_SCALAR_ARGS_RECREATORS = List.of(
            "com.openggf.level.objects.SignpostSparkleObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance",
            "com.openggf.game.sonic2.objects.ResultsScreenObjectInstance",
            "com.openggf.game.sonic3k.objects.AizBgTreeInstance");

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

    @Test
    void spawnCoordinateRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_COORDINATE_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnCoordinateRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-coordinate recreate hook");
        }
    }

    @Test
    void spawnCoordinateRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_COORDINATE_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnCoordinateRewindRecreatable's default hook");
        }
    }

    @Test
    void pointsRecreatorsUseBaseHook() {
        for (String className : POINTS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(AbstractPointsObjectInstance.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the points recreate hook");
        }
    }

    @Test
    void pointsRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : POINTS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use AbstractPointsObjectInstance's default hook");
        }
    }

    @Test
    void spawnCoordinateZeroPairRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_COORDINATE_ZERO_PAIR_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnCoordinateZeroPairRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-coordinate zero-pair recreate hook");
        }
    }

    @Test
    void spawnCoordinateZeroPairRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_COORDINATE_ZERO_PAIR_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnCoordinateZeroPairRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnCoordinateZeroScalarArgsRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_COORDINATE_ZERO_SCALAR_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnCoordinateZeroScalarArgsRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-coordinate zero-scalar recreate hook");
        }
    }

    @Test
    void spawnCoordinateZeroScalarArgsRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_COORDINATE_ZERO_SCALAR_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName()
                            + " should use SpawnCoordinateZeroScalarArgsRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnCoordinateSubtypeDefaultArgsRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_COORDINATE_SUBTYPE_DEFAULT_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnCoordinateSubtypeDefaultArgsRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName()
                            + " should inherit the spawn-coordinate subtype-default recreate hook");
        }
    }

    @Test
    void spawnCoordinateSubtypeDefaultArgsRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_COORDINATE_SUBTYPE_DEFAULT_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName()
                            + " should use "
                            + "SpawnCoordinateSubtypeDefaultArgsRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnCoordinateDefaultArgsRecreatorsUseMarkerInterface() {
        Class<?> marker = loadClass("com.openggf.level.objects.SpawnCoordinateDefaultArgsRewindRecreatable");
        for (String className : SPAWN_COORDINATE_DEFAULT_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(marker.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-coordinate default-args recreate hook");
        }
    }

    @Test
    void spawnCoordinateDefaultArgsRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_COORDINATE_DEFAULT_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnCoordinateDefaultArgsRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnAndCoordinateZeroScalarArgsRecreatorsUseMarkerInterface() {
        Class<?> marker = loadClass("com.openggf.level.objects.SpawnAndCoordinateZeroScalarArgsRewindRecreatable");
        for (String className : SPAWN_AND_COORDINATE_ZERO_SCALAR_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(marker.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-and-coordinate zero-scalar recreate hook");
        }
    }

    @Test
    void spawnAndCoordinateZeroScalarArgsRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_AND_COORDINATE_ZERO_SCALAR_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName()
                            + " should use SpawnAndCoordinateZeroScalarArgsRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnTrailingZeroIntsRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_TRAILING_ZERO_INTS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnTrailingZeroIntsRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn trailing-zero-ints recreate hook");
        }
    }

    @Test
    void spawnTrailingZeroIntsRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_TRAILING_ZERO_INTS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnTrailingZeroIntsRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnNullableReferenceRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_NULLABLE_REFERENCE_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnNullableReferenceRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn nullable-reference recreate hook");
        }
    }

    @Test
    void spawnNullableReferenceRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_NULLABLE_REFERENCE_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnNullableReferenceRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnServicesRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_SERVICES_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnServicesRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-services recreate hook");
        }
    }

    @Test
    void spawnServicesRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_SERVICES_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnServicesRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnServicesDefaultArgsRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_SERVICES_DEFAULT_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnServicesDefaultArgsRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-services default-args recreate hook");
        }
    }

    @Test
    void spawnServicesDefaultArgsRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_SERVICES_DEFAULT_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnServicesDefaultArgsRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnRomZoneRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_ROM_ZONE_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnRomZoneRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-ROM-zone recreate hook");
        }
    }

    @Test
    void spawnRomZoneRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_ROM_ZONE_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnRomZoneRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnYCoordinateRecreatorsUseMarkerInterface() {
        for (String className : SPAWN_Y_COORDINATE_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(SpawnYCoordinateRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-Y-coordinate recreate hook");
        }
    }

    @Test
    void spawnYCoordinateRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_Y_COORDINATE_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnYCoordinateRewindRecreatable's default hook");
        }
    }

    @Test
    void spawnDefaultArgsRecreatorsUseMarkerInterface() {
        Class<?> marker = loadClass("com.openggf.level.objects.SpawnDefaultArgsRewindRecreatable");
        for (String className : SPAWN_DEFAULT_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(marker.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn default-args recreate hook");
        }
    }

    @Test
    void spawnDefaultArgsRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : SPAWN_DEFAULT_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnDefaultArgsRewindRecreatable's default hook");
        }
    }

    @Test
    void zeroArgRecreatorsUseMarkerInterface() {
        for (String className : ZERO_ARG_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(ZeroArgRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the zero-arg recreate hook");
        }
    }

    @Test
    void zeroArgRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : ZERO_ARG_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use ZeroArgRewindRecreatable's default hook");
        }
    }

    @Test
    void zeroScalarArgsRecreatorsUseMarkerInterface() {
        for (String className : ZERO_SCALAR_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertTrue(ZeroScalarArgsRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the zero-scalar recreate hook");
        }
    }

    @Test
    void zeroScalarArgsRecreatorsDoNotDeclareRecreateForRewind() {
        for (String className : ZERO_SCALAR_ARGS_RECREATORS) {
            Class<?> cls = loadClass(className);
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use ZeroScalarArgsRewindRecreatable's default hook");
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
