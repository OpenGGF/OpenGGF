package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameId;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic1.objects.Sonic1PointsObjectInstance;
import com.openggf.game.sonic2.objects.BombPrizeObjectInstance;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.ConveyorObjectInstance;
import com.openggf.game.sonic2.objects.PointsObjectInstance;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossDefeatFragmentChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RED-GREEN test for Phase-2 codec-deletion batch. */
public class TestScalarOnlyCodecDeletion {

    private static final String HTZ_GROUND_FIRE_FQN =
            "com.openggf.game.sonic2.objects.HtzGroundFireObjectInstance";
    private static final String AIZ_BG_TREE_SPAWNER_FQN =
            "com.openggf.game.sonic3k.objects.AizBgTreeSpawnerInstance";
    private static final String AIZ_MINIBOSS_NAPALM_FQN =
            "com.openggf.game.sonic3k.objects.AizMinibossNapalmProjectile";

    /**
     * Batch-2 scalar-only classes whose dynamic rewind codec was deleted in favour
     * of the {@link RewindRecreatable} {@code genericRecreate} Path 1. Each must:
     * (a) implement {@link RewindRecreatable}, (b) NOT have a registered dynamic codec,
     * and (c) still round-trip {@code Passed} through the real ObjectManager harness.
     *
     * <p>Every class here is scalar-only (no {@code AbstractObjectInstance}/
     * {@code ObjectInstance}-typed instance fields, dodging the {@code required=true}
     * resolve-throw invariant) and was harness-PASSED via its codec before deletion.
     */
    private static final List<String> BATCH2_DELETED_CODEC_FQNS = List.of(
            "com.openggf.game.sonic3k.objects.AizBossSmallInstance",
            "com.openggf.game.sonic3k.objects.CnzMinibossScrollControlInstance",
            "com.openggf.game.sonic3k.objects.HCZConveyorBeltObjectInstance",
            "com.openggf.game.sonic3k.objects.MhzPulleyLiftObjectInstance",
            "com.openggf.game.sonic3k.objects.MhzSwingVineObjectInstance",
            "com.openggf.game.sonic3k.objects.CnzCannonInstance",
            "com.openggf.game.sonic3k.objects.CnzCylinderInstance",
            "com.openggf.game.sonic3k.objects.CnzBumperObjectInstance",
            "com.openggf.game.sonic3k.objects.PachinkoFlipperObjectInstance",
            "com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance");

    private record CodecDeletionCandidate(String fqn, GameId gameId) {}

    private static final List<CodecDeletionCandidate> BATCH3_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.SmallMetalPformObjectInstance$SmallMetalPformChildInstance",
                    GameId.S2),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.CnzLightsFlashChildInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.HCZWaterDropObjectInstance$WaterDropChild",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH4_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.CutsceneKnucklesAiz1Instance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH5_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.Sonic1TeleporterObjectInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.GumballMachineObjectInstance$ExitTriggerChild",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.MgzEndBossInstance",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH6_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.MgzDrillingRobotnikInstance",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH7_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.bosses.HczEndBossInstance",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH8_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(Sonic1PointsObjectInstance.class.getName(), GameId.S1),
            new CodecDeletionCandidate(PointsObjectInstance.class.getName(), GameId.S2),
            new CodecDeletionCandidate(Sonic3kPointsObjectInstance.class.getName(), GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH9_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.badniks.GrounderBadnikInstance",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH10_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(ConveyorObjectInstance.class.getName(), GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH11_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.AizBombExplosionInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.AizEndBossDebrisChild",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.AizMinibossImpactFlameChild",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.AizMinibossDebrisChild",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH12_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.AizRockFragmentChild",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.CnzMinibossDebrisChild",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.S3kBossExplosionChild",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.MhzPollenParticleInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.LightningSparkObjectInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.RockDebrisChild",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH13_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.MGZHeadTriggerProjectileInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.SongFadeTransitionInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.level.objects.EggPrisonAnimalInstance",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH14_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.S3kSignpostSparkleChild",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.S3kAirCountdownObjectInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.badniks.CaterkillerJrBodyInstance",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH15_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.AizBgTreeInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.Aiz2BossEndSequenceController",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.CutsceneKnucklesLbz1ThrownBomb",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.badniks.S3kBadnikProjectileInstance",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH16_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.MhzShipSequenceControllerInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH17_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.Aiz2EndEggCapsuleInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.bosses.HczEndBossEggCapsuleInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.bosses.IczEndBossEggCapsuleInstance",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH18_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.bosses.MhzEndBossEggCapsuleInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.Mgz2EndEggCapsuleInstance",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH19_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.badniks.BlastoidBadnikInstance$BlastoidProjectile",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.badniks.SnaleBlasterBadnikInstance$SnaleBlasterProjectile",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance$SpikerSpikeProjectile",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH20_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.S3kSignpostInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.bosses.HczEndBossGeyserCutscene",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.Mgz2CapsuleAnimalInstance",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH21_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance$AizTreeRevealControlObjectInstance",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.MGZHeadTriggerObjectInstance$HeadTriggerStoneChipChild",
                    GameId.S3K),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.MgzMinibossInstance$CeilingSpireChild",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH22_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.HtzFireProjectileObjectInstance",
                    GameId.S2),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.ArrowProjectileInstance",
                    GameId.S2),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.SteamPuffObjectInstance",
                    GameId.S2),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.LeafParticleObjectInstance",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH23_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.badniks.SpikerDrillObjectInstance",
                    GameId.S2),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.WallTurretShotInstance",
                    GameId.S2),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.VerticalLaserObjectInstance",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH24_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.bosses.LavaBubbleObjectInstance",
                    GameId.S2),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.bosses.MCZFallingDebrisInstance",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH25_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.BubbleObjectInstance",
                    GameId.S2),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.DestroyedEggPrisonObjectInstance",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH26_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.level.objects.boss.BossExplosionObjectInstance",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH27_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.badniks.BadnikProjectileInstance",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH28_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.bosses.CPZBossFallingPart",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH29_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.SpikyBlockSpikeInstance",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH30_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.MonitorContentsObjectInstance",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH31_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(BombPrizeObjectInstance.class.getName(), GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH32_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.ResultsScreenObjectInstance",
                    GameId.S2),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.RingPrizeObjectInstance",
                    GameId.S2),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic2.objects.bosses.Sonic2MTZBossInstance$MTZBossLaser",
                    GameId.S2));

    private static final List<CodecDeletionCandidate> BATCH33_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.badniks.Sonic1BombShrapnelInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.badniks.Sonic1CannonballInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.badniks.Sonic1NewtronMissileInstance",
                    GameId.S1));

    private static final List<CodecDeletionCandidate> BATCH34_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.badniks.Sonic1BuzzBomberMissileInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.badniks.Sonic1BuzzBomberMissileDissolveInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.badniks.Sonic1CrabmeatProjectileInstance",
                    GameId.S1));

    private static final List<CodecDeletionCandidate> BATCH35_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.Sonic1StomperDoorObjectInstance",
                    GameId.S1));

    private static final List<CodecDeletionCandidate> BATCH36_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.Sonic1FloatingBlockObjectInstance",
                    GameId.S1));

    private static final List<CodecDeletionCandidate> BATCH37_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.bosses.IczEndBossInstance$IczEndBossRobotnikEscapeShip",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH38_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.Sonic1CollapsingFloorObjectInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.Sonic1EndingEmeraldsObjectInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.Sonic1ExplosionItemObjectInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.Sonic1MonitorPowerUpObjectInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.Sonic1RingInstance",
                    GameId.S1),
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic1.objects.Sonic1SpikedBallChainObjectInstance",
                    GameId.S1));

    private static final List<CodecDeletionCandidate> BATCH39_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.level.objects.boss.BossExplosionObjectInstance",
                    GameId.S1));

    private static final List<CodecDeletionCandidate> BATCH40_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.bosses.MhzEndBossPaletteFadeController",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH41_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.badniks.CorkeyBadnikInstance$CorkeyShotChild",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH42_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    MhzEndBossDefeatFragmentChild.class.getName(),
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH43_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(
                    "com.openggf.game.sonic3k.objects.badniks.MadmoleBadnikInstance$SideDrillChild",
                    GameId.S3K));

    private static final List<CodecDeletionCandidate> BATCH44_DELETED_CODECS = List.of(
            new CodecDeletionCandidate(S3kResultsScreenObjectInstance.class.getName(), GameId.S3K));

    private static final SonicConfigurationService DEFAULT_CONFIGURATION =
            createDefaultConfiguration();
    private static final ObjectRenderManager INERT_RENDER_MANAGER =
            new ObjectRenderManager(new InertObjectArtProvider());

    @BeforeEach
    void initHeadless() { GraphicsManager.getInstance().initHeadless(); }

    @AfterEach
    void tearDown() { GraphicsManager.getInstance().resetState(); }

    // HTZ (S2) - already implements RewindRecreatable - should pass before AND after deletion

    @Test
    void htzGroundFireRoundTripsPassed() {
        RoundTripSweepResult result = RewindRoundTripHarness.probeClass(HTZ_GROUND_FIRE_FQN);
        assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                "HtzGroundFireObjectInstance must round-trip as Passed; got: " + result);
    }

    @Test
    void htzGroundFireGenericRecreateProducesInstance() {
        ObjectInstance result = invokeGenericRecreate(HTZ_GROUND_FIRE_FQN, 0x100, 0x200, GameId.S2);
        assertNotNull(result, "genericRecreate must return non-null for HtzGroundFireObjectInstance");
        assertEquals(HTZ_GROUND_FIRE_FQN, result.getClass().getName());
    }

    // AIZ BG Tree Spawner (S3K) - RED until RewindRecreatable is added

    @Test
    void aizBgTreeSpawnerIsRewindRecreatable() {
        Class<?> cls;
        try { cls = Class.forName(AIZ_BG_TREE_SPAWNER_FQN); }
        catch (ClassNotFoundException e) { throw new AssertionError(e); }
        assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                AIZ_BG_TREE_SPAWNER_FQN + " must implement RewindRecreatable");
    }

    @Test
    void aizBgTreeSpawnerGenericRecreateProducesInstance() {
        ObjectInstance result = invokeGenericRecreate(AIZ_BG_TREE_SPAWNER_FQN, 0, 0, GameId.S3K);
        assertNotNull(result, "genericRecreate must return non-null for AizBgTreeSpawnerInstance");
        assertEquals(AIZ_BG_TREE_SPAWNER_FQN, result.getClass().getName());
    }

    // AIZ Miniboss Napalm Projectile (S3K) - RED until RewindRecreatable is added

    @Test
    void aizMinibossNapalmIsRewindRecreatable() {
        Class<?> cls;
        try { cls = Class.forName(AIZ_MINIBOSS_NAPALM_FQN); }
        catch (ClassNotFoundException e) { throw new AssertionError(e); }
        assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                AIZ_MINIBOSS_NAPALM_FQN + " must implement RewindRecreatable");
    }

    @Test
    void aizMinibossNapalmGenericRecreateProducesInstance() {
        ObjectInstance result = invokeGenericRecreate(AIZ_MINIBOSS_NAPALM_FQN, 0x300, 0x400, GameId.S3K);
        assertNotNull(result, "genericRecreate must return non-null for AizMinibossNapalmProjectile");
        assertEquals(AIZ_MINIBOSS_NAPALM_FQN, result.getClass().getName());
    }

    // Integration anchor: after codec deletion, all three must pass via harness

    @Test
    void allThreeClassesRoundTripPassedAfterCodecDeletion() {
        for (String fqn : List.of(HTZ_GROUND_FIRE_FQN, AIZ_BG_TREE_SPAWNER_FQN, AIZ_MINIBOSS_NAPALM_FQN)) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(fqn);
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    fqn + " must round-trip as Passed via RewindRecreatable path; got: " + result);
        }
    }

    // =====================================================================
    // Batch 2: ten scalar-only S3K classes — codec deleted, RewindRecreatable added
    // =====================================================================

    @Test
    void batch2ClassesAllImplementRewindRecreatable() {
        for (String fqn : BATCH2_DELETED_CODEC_FQNS) {
            Class<?> cls;
            try { cls = Class.forName(fqn); }
            catch (ClassNotFoundException e) { throw new AssertionError(e); }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    fqn + " must implement RewindRecreatable (codec deleted in batch 2)");
        }
    }

    @Test
    void batch2ClassesHaveNoRegisteredCodec() {
        for (String fqn : BATCH2_DELETED_CODEC_FQNS) {
            assertFalse(hasRegisteredDynamicCodec(fqn),
                    fqn + " must have NO registered dynamic rewind codec after batch-2 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch2ClassesGenericRecreateProducesInstance() {
        for (String fqn : BATCH2_DELETED_CODEC_FQNS) {
            ObjectInstance result = invokeGenericRecreate(fqn, 0x120, 0x240, GameId.S3K);
            assertNotNull(result, "genericRecreate must return non-null for " + fqn);
            assertEquals(fqn, result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + fqn);
        }
    }

    @Test
    void batch2ClassesRoundTripPassedWithoutCodec() {
        for (String fqn : BATCH2_DELETED_CODEC_FQNS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(fqn);
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    fqn + " must round-trip as Passed via RewindRecreatable path (no codec); got: " + result);
        }
    }

    // =====================================================================
    // Batch 3: three scalar-only dynamic children — codec deleted, generic recreate
    // =====================================================================

    @Test
    void batch3ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH3_DELETED_CODECS) {
            Class<?> cls;
            try { cls = Class.forName(candidate.fqn()); }
            catch (ClassNotFoundException e) { throw new AssertionError(e); }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 3)");
        }
    }

    @Test
    void batch3ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH3_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-3 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch3ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH3_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch3ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH3_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 4: two scalar-only self-contained dynamics - codec deleted
    // =====================================================================

    @Test
    void batch4ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH4_DELETED_CODECS) {
            Class<?> cls;
            try { cls = Class.forName(candidate.fqn()); }
            catch (ClassNotFoundException e) { throw new AssertionError(e); }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 4)");
        }
    }

    @Test
    void batch4ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH4_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-4 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch4ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH4_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch4ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH4_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 5: gumball bonus-stage exit trigger - codec deleted
    // =====================================================================

    @Test
    void batch5ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH5_DELETED_CODECS) {
            Class<?> cls;
            try { cls = Class.forName(candidate.fqn()); }
            catch (ClassNotFoundException e) { throw new AssertionError(e); }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 5)");
        }
    }

    @Test
    void batch5ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH5_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-5 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch5ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH5_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch5ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH5_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 6: MGZ drilling Robotnik - codec deleted
    // =====================================================================

    @Test
    void batch6ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH6_DELETED_CODECS) {
            Class<?> cls;
            try { cls = Class.forName(candidate.fqn()); }
            catch (ClassNotFoundException e) { throw new AssertionError(e); }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 6)");
        }
    }

    @Test
    void batch6ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH6_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-6 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch6ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH6_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch6ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH6_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 7: HCZ end boss - codec deleted
    // =====================================================================

    @Test
    void batch7ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH7_DELETED_CODECS) {
            Class<?> cls;
            try { cls = Class.forName(candidate.fqn()); }
            catch (ClassNotFoundException e) { throw new AssertionError(e); }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 7)");
        }
    }

    @Test
    void batch7ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH7_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-7 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch7ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH7_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch7ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH7_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 8: points popups - codec deleted
    // =====================================================================

    @Test
    void batch8ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH8_DELETED_CODECS) {
            Class<?> cls;
            try { cls = Class.forName(candidate.fqn()); }
            catch (ClassNotFoundException e) { throw new AssertionError(e); }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 8)");
        }
    }

    @Test
    void batch8ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH8_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-8 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch8ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH8_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch8ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH8_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 9: S2 Grounder badnik - codec deleted
    // =====================================================================

    @Test
    void batch9ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH9_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 9)");
        }
    }

    @Test
    void batch9ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH9_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-9 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch9ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH9_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch9ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH9_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 10: S2 MTZ Conveyor - codec deleted, captured constructor base preserved
    // =====================================================================

    @Test
    void batch10ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH10_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 10)");
        }
    }

    @Test
    void batch10ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH10_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-10 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch10ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH10_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch10ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH10_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    @Test
    void conveyorGenericRecreatePreservesCapturedConstructorBase() {
        ObjectSpawn spawn = new ObjectSpawn(
                0x340, 0x280, Sonic2ObjectIds.CONVEYOR, 0x01, 0, false, 0);
        int capturedBaseX = 0x300;
        int capturedBaseY = 0x240;
        ConveyorObjectInstance source =
                new ConveyorObjectInstance(spawn, "Conveyor", capturedBaseX, capturedBaseY);
        PerObjectRewindSnapshot state = source.captureRewindState();

        ObjectInstance recreated = invokeGenericRecreateWithState(
                ConveyorObjectInstance.class.getName(), spawn, state, GameId.S2);

        assertNotNull(recreated, "genericRecreate must return non-null for ConveyorObjectInstance");
        assertEquals(ConveyorObjectInstance.class, recreated.getClass());
        PerObjectRewindSnapshot recreatedState =
                ((AbstractObjectInstance) recreated).captureRewindState();
        Object extra = recreatedState.objectSubclassExtra();
        assertNotNull(extra, "Conveyor recreate must preserve subclass rewind extra");
        assertEquals(capturedBaseX, readIntRecordComponent(extra, "baseX"),
                "Conveyor generic recreate must preserve captured baseX, not derive it from spawn.x");
        assertEquals(capturedBaseY, readIntRecordComponent(extra, "baseY"),
                "Conveyor generic recreate must preserve captured baseY, not derive it from spawn.y");
    }

    // =====================================================================
    // Batch 11: AIZ2 self-contained transient children - codecs deleted
    // =====================================================================

    @Test
    void batch11ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH11_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 11)");
        }
    }

    @Test
    void batch11ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH11_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-11 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch11ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH11_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch11ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH11_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 12: self-contained S3K transient/effect/reward children
    // =====================================================================

    @Test
    void batch12ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH12_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 12)");
        }
    }

    @Test
    void batch12ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH12_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-12 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch12ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH12_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch12ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH12_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 13: self-contained S3K release-sequence/effect dynamics
    // =====================================================================

    @Test
    void batch13ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH13_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 13)");
        }
    }

    @Test
    void batch13ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH13_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-13 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch13ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH13_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch13ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH13_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 14: self-contained S3K transient/countdown/badnik-child dynamics
    // =====================================================================

    @Test
    void batch14ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH14_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 14)");
        }
    }

    @Test
    void batch14ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH14_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-14 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch14ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH14_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch14ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH14_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 15: self-contained S3K exact-spawn dynamics
    // =====================================================================

    @Test
    void batch15ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH15_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 15)");
        }
    }

    @Test
    void batch15ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH15_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-15 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch15ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH15_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch15ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH15_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 16: session-level/no-ref exact-spawn S3K dynamics
    // =====================================================================

    @Test
    void batch16ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH16_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 16)");
        }
    }

    @Test
    void batch16ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH16_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-16 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch16ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH16_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch16ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH16_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 17: S3K exact-spawn end egg capsules
    // =====================================================================

    @Test
    void batch17ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH17_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 17)");
        }
    }

    @Test
    void batch17ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH17_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-17 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch17ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH17_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch17ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH17_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 18: remaining no-ref S3K exact-spawn end capsules
    // =====================================================================

    @Test
    void batch18ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH18_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 18)");
        }
    }

    @Test
    void batch18ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH18_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-18 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch18ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH18_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch18ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH18_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 19: private no-ref S3K badnik projectiles
    // =====================================================================

    @Test
    void batch19ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH19_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 19)");
        }
    }

    @Test
    void batch19ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH19_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-19 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch19ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH19_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch19ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH19_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 20: session-verified no-ref S3K exact-spawn dynamics
    // =====================================================================

    @Test
    void batch20ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH20_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 20)");
        }
    }

    @Test
    void batch20ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH20_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-20 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch20ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH20_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch20ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH20_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 21: session-verified no-ref S3K nested transient children
    // =====================================================================

    @Test
    void batch21ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH21_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 21)");
        }
    }

    @Test
    void batch21ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH21_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-21 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch21ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH21_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch21ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH21_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 22: S2 self-contained projectile/effect dynamics
    // =====================================================================

    @Test
    void batch22ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH22_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 22)");
        }
    }

    @Test
    void batch22ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH22_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-22 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch22ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH22_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch22ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH22_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 23: S2 scalar-only child projectiles/hazards
    // =====================================================================

    @Test
    void batch23ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH23_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 23)");
        }
    }

    @Test
    void batch23ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH23_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-23 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch23ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH23_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch23ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH23_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 24: S2 scalar-only boss hazards
    // =====================================================================

    @Test
    void batch24ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH24_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 24)");
        }
    }

    @Test
    void batch24ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH24_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-24 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch24ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH24_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch24ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH24_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 25: S2 scalar-only no-ref dynamics
    // =====================================================================

    @Test
    void batch25ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH25_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 25)");
        }
    }

    @Test
    void batch25ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH25_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-25 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch25ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH25_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch25ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH25_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 26: shared S2 boss explosion dynamic
    // =====================================================================

    @Test
    void batch26ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH26_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 26)");
        }
    }

    @Test
    void batch26ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH26_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-26 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch26ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH26_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch26ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH26_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 27: S2 shared badnik projectile dynamic
    // =====================================================================

    @Test
    void batch27ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH27_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 27)");
        }
    }

    @Test
    void batch27ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH27_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-27 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch27ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH27_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch27ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH27_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 28: S2 CPZ boss falling-part dynamic, session-tail verified
    // =====================================================================

    @Test
    void batch28ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH28_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 28)");
        }
    }

    @Test
    void batch28ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH28_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-28 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch28ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH28_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch28ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH28_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 29: S2 SpikyBlock spike dynamic, session-tail verified
    // =====================================================================

    @Test
    void batch29ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH29_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 29)");
        }
    }

    @Test
    void batch29ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH29_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-29 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch29ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH29_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch29ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH29_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 30: S2 monitor contents dynamic, session-tail verified
    // =====================================================================

    @Test
    void batch30ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH30_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 30)");
        }
    }

    @Test
    void batch30ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH30_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-30 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch30ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH30_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch30ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH30_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 31: S2 bomb prize dynamic
    // =====================================================================

    @Test
    void batch31ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH31_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 31)");
        }
    }

    @Test
    void batch31ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH31_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-31 deletion; "
                            + "it should round-trip purely via genericRecreate Path 1");
        }
    }

    @Test
    void batch31ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH31_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch31ClassesRoundTripPassedWithoutCodec() {
        for (CodecDeletionCandidate candidate : BATCH31_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed via RewindRecreatable path (no codec); got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 32: remaining S2 scalar/no-reference session-only dynamics
    // =====================================================================

    @Test
    void batch32ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH32_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 32)");
        }
    }

    @Test
    void batch32ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH32_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-32 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch32ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH32_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch32ClassesRoundTripDoesNotFailWhenProbeable() {
        for (CodecDeletionCandidate candidate : BATCH32_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            if (result instanceof RoundTripSweepResult.Unprobed unprobed) {
                assertTrue(unprobed.reason().contains("No probe-compatible constructor"),
                        candidate.fqn() + " may remain session-verified only, but not fail hard; got: " + result);
                continue;
            }
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed when the headless harness can construct it; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 33: S1 session-verified scalar/no-reference projectile dynamics
    // =====================================================================

    @Test
    void batch33ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH33_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 33)");
        }
    }

    @Test
    void batch33ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH33_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-33 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch33ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH33_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch33ClassesRoundTripPassedThroughObjectManagerSessionPath() {
        for (CodecDeletionCandidate candidate : BATCH33_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 34: S1 session-verified scalar/no-reference projectile dynamics
    // =====================================================================

    @Test
    void batch34ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH34_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 34)");
        }
    }

    @Test
    void batch34ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH34_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-34 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch34ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH34_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch34ClassesRoundTripPassedThroughObjectManagerSessionPath() {
        for (CodecDeletionCandidate candidate : BATCH34_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            if (result instanceof RoundTripSweepResult.Unprobed unprobed) {
                assertTrue(unprobed.reason().contains("No probe-compatible constructor"),
                        candidate.fqn()
                                + " may remain session-verified only, but not fail hard; got: "
                                + result);
                continue;
            }
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 35: S1 session-verified scalar/no-reference dynamics
    // =====================================================================

    @Test
    void batch35ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH35_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 35)");
        }
    }

    @Test
    void batch35ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH35_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-35 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch35ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH35_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch35ClassesRoundTripDoesNotFailWhenProbeable() {
        for (CodecDeletionCandidate candidate : BATCH35_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            if (result instanceof RoundTripSweepResult.Unprobed unprobed) {
                assertTrue(unprobed.reason().contains("No probe-compatible constructor"),
                        candidate.fqn()
                                + " may remain session-verified only, but not fail hard; got: "
                                + result);
                continue;
            }
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 36: S1 session-verified scalar/no-reference dynamics
    // =====================================================================

    @Test
    void batch36ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH36_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 36)");
        }
    }

    @Test
    void batch36ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH36_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-36 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch36ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH36_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch36ClassesRoundTripDoesNotFailWhenProbeable() {
        for (CodecDeletionCandidate candidate : BATCH36_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            if (result instanceof RoundTripSweepResult.Unprobed unprobed) {
                assertTrue(unprobed.reason().contains("No probe-compatible constructor"),
                        candidate.fqn()
                                + " may remain session-verified only, but not fail hard; got: "
                                + result);
                continue;
            }
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 37: S3K session-verified scalar/no-reference nested dynamic
    // =====================================================================

    @Test
    void batch37ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH37_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 37)");
        }
    }

    @Test
    void batch37ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH37_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-37 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch37ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH37_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch37ClassesRoundTripDoesNotFailWhenProbeable() {
        for (CodecDeletionCandidate candidate : BATCH37_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            if (result instanceof RoundTripSweepResult.Unprobed unprobed) {
                assertTrue(unprobed.reason().contains("No probe-compatible constructor"),
                        candidate.fqn()
                                + " may remain session-verified only, but not fail hard; got: "
                                + result);
                continue;
            }
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 38: S1 scalar/no-reference session-only exact-spawn dynamics
    // =====================================================================

    @Test
    void batch38ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH38_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 38)");
        }
    }

    @Test
    void batch38ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH38_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-38 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch38ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH38_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch38ClassesRoundTripDoesNotFailWhenProbeable() {
        for (CodecDeletionCandidate candidate : BATCH38_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            if (result instanceof RoundTripSweepResult.Unprobed unprobed) {
                assertTrue(unprobed.reason().contains("No probe-compatible constructor"),
                        candidate.fqn()
                                + " may remain session-verified only, but not fail hard; got: "
                                + result);
                continue;
            }
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 39: shared S1 boss explosion dynamic
    // =====================================================================

    @Test
    void batch39ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH39_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 39)");
        }
    }

    @Test
    void batch39ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH39_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-39 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch39ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH39_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch39ClassesRoundTripPassedThroughObjectManagerSessionPath() {
        for (CodecDeletionCandidate candidate : BATCH39_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 40: S3K MHZ end-boss palette fade controller
    // =====================================================================

    @Test
    void batch40ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH40_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 40)");
        }
    }

    @Test
    void batch40ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH40_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-40 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch40ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH40_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x120, 0x240, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void batch40ClassesRoundTripDoesNotFailWhenProbeable() {
        for (CodecDeletionCandidate candidate : BATCH40_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            if (result instanceof RoundTripSweepResult.Unprobed unprobed) {
                assertTrue(unprobed.reason().contains("No probe-compatible constructor"),
                        candidate.fqn()
                                + " may remain session-verified only, but not fail hard; got: "
                                + result);
                continue;
            }
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 41: S3K Corkey shot child, no live-nozzle dependency
    // =====================================================================

    @Test
    void batch41ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH41_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 41)");
        }
    }

    @Test
    void batch41ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH41_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-41 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch41ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH41_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x220, 0x1B4, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void corkeyShotGenericRecreateRestoresCapturedScriptWithoutLiveNozzle() {
        String fqn = BATCH41_DELETED_CODECS.getFirst().fqn();
        ObjectSpawn spawn = new ObjectSpawn(0x220, 0x1B4, 0, 0, 0, false, 0);
        int[] capturedScript = {9, 1, 8, 2, 7, 3, -0x0C};
        ObjectInstance source = new com.openggf.game.sonic3k.objects.badniks.CorkeyBadnikInstance.CorkeyShotChild(
                spawn, spawn.x(), spawn.y(), capturedScript);
        PerObjectRewindSnapshot state = ((AbstractObjectInstance) source).captureRewindState();

        ObjectInstance recreated = invokeGenericRecreateWithState(fqn, spawn, state, GameId.S3K);
        assertNotNull(recreated, "genericRecreate must not depend on a live Corkey nozzle");
        ((AbstractObjectInstance) recreated).restoreRewindState(state);

        assertArrayEquals(capturedScript, readIntArrayField(recreated, "script"),
                "standard scalar restore must reapply the exact captured Corkey shot script");
    }

    @Test
    void batch41ClassesRoundTripPassedThroughObjectManagerSessionPath() {
        for (CodecDeletionCandidate candidate : BATCH41_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 42: S3K MHZ end-boss defeat fragment, no live-boss dependency
    // =====================================================================

    @Test
    void batch42ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH42_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 42)");
        }
    }

    @Test
    void batch42ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH42_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-42 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch42ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH42_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x3CA0, 0x320, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void mhzEndBossDefeatFragmentGenericRecreateRestoresCapturedDerivedVelocityWithoutLiveBoss() {
        String fqn = BATCH42_DELETED_CODECS.getFirst().fqn();
        StubObjectServices stub = new StubObjectServices() {
            @Override public ObjectRenderManager renderManager() { return INERT_RENDER_MANAGER; }
            @Override public SonicConfigurationService configuration() { return DEFAULT_CONFIGURATION; }
        };
        ObjectSpawn bossSpawn = new ObjectSpawn(0x3D40, 0x2F0, 0x93, 0, 0, false, 0);
        MhzEndBossInstance parent = ObjectConstructionContext.construct(stub,
                () -> new MhzEndBossInstance(bossSpawn));
        parent.getState().renderFlags = 1;
        int capturedSubtype = 4;
        MhzEndBossDefeatFragmentChild source = ObjectConstructionContext.construct(stub,
                () -> MhzEndBossDefeatFragmentChild.forRewindRecreate(parent, capturedSubtype));
        PerObjectRewindSnapshot state = source.captureRewindState();
        ObjectSpawn capturedSpawn = source.getSpawn();

        ObjectInstance recreated = invokeGenericRecreateWithState(fqn, capturedSpawn, state, GameId.S3K);
        assertNotNull(recreated, "genericRecreate must not depend on a live MHZ end boss");
        assertEquals(fqn, recreated.getClass().getName(),
                "genericRecreate must return the same concrete fragment class before restore");
        ((AbstractObjectInstance) recreated).restoreRewindState(state);

        assertEquals(capturedSubtype, readIntField(recreated, "subtype"),
                "standard scalar restore must reapply the exact captured fragment subtype");
        assertEquals(readIntField(source, "xVel"), readIntField(recreated, "xVel"),
                "standard scalar restore must reapply xVel derived from the captured parent render flags");
        assertEquals(readIntField(source, "xFixed"), readIntField(recreated, "xFixed"),
                "standard scalar restore must reapply exact fixed-point X");
        assertEquals(readIntField(source, "yFixed"), readIntField(recreated, "yFixed"),
                "standard scalar restore must reapply exact fixed-point Y");
    }

    @Test
    void batch42ClassesRoundTripPassedThroughObjectManagerSessionPath() {
        for (CodecDeletionCandidate candidate : BATCH42_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 43: S3K Madmole side-drill child, no live-parent dependency
    // =====================================================================

    @Test
    void batch43ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH43_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 43)");
        }
    }

    @Test
    void batch43ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH43_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-43 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch43ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH43_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0x180, 0x140, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void madmoleSideDrillGenericRecreateRestoresCapturedMotionWithoutLiveParent() {
        String fqn = BATCH43_DELETED_CODECS.getFirst().fqn();
        ObjectInstance source = instantiateMadmoleSideDrill(0x180, 0x140, false);
        setBooleanField(source, "initialized", true);
        setBooleanField(source, "arcing", true);
        setBooleanField(source, "postCaptureDrift", true);
        setIntField(source, "currentX", 0x188);
        setIntField(source, "currentY", 0x14A);
        setIntField(source, "xVelocity", 0x380);
        setIntField(source, "yVelocity", -0x200);
        setIntField(source, "xSubpixel", 0x40);
        setIntField(source, "ySubpixel", 0x80);
        setIntField(source, "mappingFrame", 9);
        TestablePlayableSprite capturedPlayer =
                new TestablePlayableSprite("sonic", (short) 0x190, (short) 0x150);
        setObjectField(source, "capturedPlayer", capturedPlayer);
        RewindIdentityTable captureTable = new RewindIdentityTable();
        captureTable.registerPlayer(capturedPlayer, PlayerRefId.mainPlayer());
        RewindCaptureContext rewindContext =
                RewindCaptureContext.withIdentityTable(captureTable);
        PerObjectRewindSnapshot state = ((AbstractObjectInstance) source).captureRewindState(rewindContext);
        ObjectSpawn capturedSpawn = source.getSpawn();

        ObjectInstance unresolved = invokeGenericRecreateWithState(fqn, capturedSpawn, state, GameId.S3K);
        RewindCaptureContext missingPlayerContext =
                RewindCaptureContext.withIdentityTable(new RewindIdentityTable());
        assertThrows(IllegalStateException.class,
                () -> ((AbstractObjectInstance) unresolved).restoreRewindState(state, missingPlayerContext),
                "non-null capturedPlayer must use required player-ref resolve during compact restore");

        ObjectInstance recreated = invokeGenericRecreateWithState(fqn, capturedSpawn, state, GameId.S3K);
        TestablePlayableSprite restoredPlayer =
                new TestablePlayableSprite("sonic", (short) 0x190, (short) 0x150);
        RewindIdentityTable restoreTable = new RewindIdentityTable();
        restoreTable.registerPlayer(restoredPlayer, PlayerRefId.mainPlayer());
        RewindCaptureContext restoreContext = RewindCaptureContext.withIdentityTable(restoreTable);
        assertNotNull(recreated, "genericRecreate must not depend on a live Madmole parent");
        assertEquals(fqn, recreated.getClass().getName(),
                "genericRecreate must return the same concrete side-drill class before restore");
        ((AbstractObjectInstance) recreated).restoreRewindState(state, restoreContext);

        assertFalse(readBooleanField(recreated, "facingLeft"),
                "generic recreate must recover facingLeft from the captured spawn render flag");
        assertTrue(readBooleanField(recreated, "initialized"),
                "standard scalar restore must reapply initialized");
        assertTrue(readBooleanField(recreated, "arcing"),
                "standard scalar restore must reapply arcing");
        assertTrue(readBooleanField(recreated, "postCaptureDrift"),
                "standard scalar restore must reapply postCaptureDrift");
        assertEquals(readIntField(source, "currentX"), readIntField(recreated, "currentX"),
                "standard scalar restore must reapply exact X");
        assertEquals(readIntField(source, "currentY"), readIntField(recreated, "currentY"),
                "standard scalar restore must reapply exact Y");
        assertEquals(readIntField(source, "xVelocity"), readIntField(recreated, "xVelocity"),
                "standard scalar restore must reapply xVelocity");
        assertEquals(readIntField(source, "yVelocity"), readIntField(recreated, "yVelocity"),
                "standard scalar restore must reapply yVelocity");
        assertEquals(readIntField(source, "mappingFrame"), readIntField(recreated, "mappingFrame"),
                "standard scalar restore must reapply mappingFrame");
        assertSame(restoredPlayer, readObjectField(recreated, "capturedPlayer"),
                "compact restore must resolve capturedPlayer through the restore identity table");
    }

    @Test
    void batch43ClassesRoundTripPassedThroughObjectManagerSessionPath() {
        for (CodecDeletionCandidate candidate : BATCH43_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    // =====================================================================
    // Batch 44: S3K results screen - exact-spawn codec deleted
    // =====================================================================

    @Test
    void batch44ClassesAllImplementRewindRecreatable() {
        for (CodecDeletionCandidate candidate : BATCH44_DELETED_CODECS) {
            Class<?> cls;
            try {
                cls = Class.forName(candidate.fqn());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            assertTrue(RewindRecreatable.class.isAssignableFrom(cls),
                    candidate.fqn() + " must implement RewindRecreatable (codec deleted in batch 44)");
        }
    }

    @Test
    void batch44ClassesHaveNoRegisteredCodec() {
        for (CodecDeletionCandidate candidate : BATCH44_DELETED_CODECS) {
            assertFalse(hasRegisteredDynamicCodec(candidate.fqn(), candidate.gameId()),
                    candidate.fqn() + " must have NO registered dynamic rewind codec after batch-44 deletion; "
                            + "session restore must use genericRecreate Path 1");
        }
    }

    @Test
    void batch44ClassesGenericRecreateProducesInstance() {
        for (CodecDeletionCandidate candidate : BATCH44_DELETED_CODECS) {
            ObjectInstance result = invokeGenericRecreate(candidate.fqn(), 0, 0, candidate.gameId());
            assertNotNull(result, "genericRecreate must return non-null for " + candidate.fqn());
            assertEquals(candidate.fqn(), result.getClass().getName(),
                    "genericRecreate must return the same concrete class for " + candidate.fqn());
        }
    }

    @Test
    void s3kResultsGenericRecreateRestoresCapturedConstructorStateAndPlayerRef() {
        String fqn = BATCH44_DELETED_CODECS.getFirst().fqn();
        StubObjectServices stub = new StubObjectServices() {
            @Override public ObjectRenderManager renderManager() { return INERT_RENDER_MANAGER; }
            @Override public SonicConfigurationService configuration() { return DEFAULT_CONFIGURATION; }
        };
        S3kResultsScreenObjectInstance source = ObjectConstructionContext.construct(stub,
                () -> new S3kResultsScreenObjectInstance(PlayerCharacter.TAILS_ALONE, 1));
        setIntField(source, "timeBonus", 4321);
        setIntField(source, "ringBonus", 210);
        setIntField(source, "totalBonusCountUp", 1234);
        setIntField(source, "exitQueueCounter", 7);
        setBooleanField(source, "musicPlayed", true);
        setBooleanField(source, "actTransitionSignaled", true);
        TestablePlayableSprite capturedPlayer =
                new TestablePlayableSprite("tails", (short) 0x200, (short) 0x160);
        setObjectField(source, "playerRef", capturedPlayer);
        RewindIdentityTable captureTable = new RewindIdentityTable();
        captureTable.registerPlayer(capturedPlayer, PlayerRefId.mainPlayer());
        RewindCaptureContext rewindContext =
                RewindCaptureContext.withIdentityTable(captureTable);
        PerObjectRewindSnapshot state = source.captureRewindState(rewindContext);

        ObjectInstance unresolved = invokeGenericRecreateWithState(
                fqn, source.getSpawn(), state, GameId.S3K);
        RewindCaptureContext missingPlayerContext =
                RewindCaptureContext.withIdentityTable(new RewindIdentityTable());
        assertThrows(IllegalStateException.class,
                () -> ((AbstractObjectInstance) unresolved).restoreRewindState(state, missingPlayerContext),
                "non-null playerRef must use required player-ref resolve during compact restore");

        ObjectInstance recreated = invokeGenericRecreateWithState(
                fqn, source.getSpawn(), state, GameId.S3K);
        TestablePlayableSprite restoredPlayer =
                new TestablePlayableSprite("tails", (short) 0x200, (short) 0x160);
        RewindIdentityTable restoreTable = new RewindIdentityTable();
        restoreTable.registerPlayer(restoredPlayer, PlayerRefId.mainPlayer());
        RewindCaptureContext restoreContext = RewindCaptureContext.withIdentityTable(restoreTable);
        assertNotNull(recreated, "genericRecreate must return an S3K results screen");
        assertEquals(fqn, recreated.getClass().getName(),
                "genericRecreate must return the same concrete results class before restore");
        ((AbstractObjectInstance) recreated).restoreRewindState(state, restoreContext);

        assertEquals(PlayerCharacter.TAILS_ALONE, readObjectField(recreated, "character"),
                "standard restore must reapply the captured character constructor arg");
        assertEquals(1, readIntField(recreated, "act"),
                "standard restore must reapply the captured act constructor arg");
        assertEquals(4321, readIntField(recreated, "timeBonus"),
                "standard restore must reapply the exact time bonus");
        assertEquals(210, readIntField(recreated, "ringBonus"),
                "standard restore must reapply the exact ring bonus");
        assertEquals(1234, readIntField(recreated, "totalBonusCountUp"),
                "standard restore must reapply the exact tally count");
        assertEquals(7, readIntField(recreated, "exitQueueCounter"),
                "standard restore must reapply the exit queue counter");
        assertTrue(readBooleanField(recreated, "musicPlayed"),
                "standard restore must reapply musicPlayed");
        assertTrue(readBooleanField(recreated, "actTransitionSignaled"),
                "standard restore must reapply actTransitionSignaled");
        assertSame(restoredPlayer, readObjectField(recreated, "playerRef"),
                "compact restore must resolve playerRef through the restore identity table");
    }

    @Test
    void batch44ClassesRoundTripPassedThroughObjectManagerSessionPath() {
        for (CodecDeletionCandidate candidate : BATCH44_DELETED_CODECS) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(candidate.fqn());
            assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                    candidate.fqn()
                            + " must round-trip as Passed through the ObjectManager session path; got: "
                            + result);
        }
    }

    /**
     * Returns true if the given FQN has a registered dynamic rewind codec in the
     * shared codecs or in any of the three per-game registries. Distinct from
     * the harness's {@code hasRegisteredCodec}, which also returns true for
     * {@link RewindRecreatable} classes — here we want to confirm the explicit
     * codec entry is GONE, independent of the RewindRecreatable path.
     */
    private static boolean hasRegisteredDynamicCodec(String fqn) {
        for (var c : com.openggf.level.objects.ObjectRewindDynamicCodecs.sharedCodecs()) {
            if (fqn.equals(c.className())) return true;
        }
        for (ObjectRegistry reg : new ObjectRegistry[]{
                new Sonic1ObjectRegistry(),
                new Sonic2ObjectRegistry(),
                new Sonic3kObjectRegistry()}) {
            for (var c : reg.dynamicRewindCodecs()) {
                if (fqn.equals(c.className())) return true;
            }
        }
        return false;
    }

    private static boolean hasRegisteredDynamicCodec(String fqn, GameId gameId) {
        for (var c : com.openggf.level.objects.ObjectRewindDynamicCodecs.sharedCodecs()) {
            if (fqn.equals(c.className())) return true;
        }
        ObjectRegistry reg = switch (gameId) {
            case S1 -> new Sonic1ObjectRegistry();
            case S2 -> new Sonic2ObjectRegistry();
            case S3K -> new Sonic3kObjectRegistry();
        };
        for (var c : reg.dynamicRewindCodecs()) {
            if (fqn.equals(c.className())) return true;
        }
        return false;
    }

    // Private helpers

    private static ObjectInstance invokeGenericRecreate(String fqn, int x, int y, GameId gameId) {
        ObjectSpawn spawn = new ObjectSpawn(x, y, 0, 0, 0, false, 0);
        PerObjectRewindSnapshot state = new PerObjectRewindSnapshot(
                false, false, false, 0, 0, 0, 0, false, 0, false, false, 0, -1, null, null, null);
        return invokeGenericRecreateWithState(fqn, spawn, state, gameId);
    }

    private static ObjectInstance invokeGenericRecreateWithState(
            String fqn, ObjectSpawn spawn, PerObjectRewindSnapshot state, GameId gameId) {
        Camera camera = mockCamera();
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices stub = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public SonicConfigurationService configuration() { return DEFAULT_CONFIGURATION; }
            @Override public ObjectRenderManager renderManager() { return INERT_RENDER_MANAGER; }
        };
        ObjectRegistry registry = switch (gameId) {
            case S1 -> new Sonic1ObjectRegistry();
            case S2 -> new Sonic2ObjectRegistry();
            case S3K -> new Sonic3kObjectRegistry();
        };
        ObjectManager om = new ObjectManager(
                List.of(), registry, 0, null, null, GraphicsManager.getInstance(), camera, stub);
        holder[0] = om;
        om.reset(0);
        ObjectManagerSnapshot.DynamicObjectEntry entry =
                new ObjectManagerSnapshot.DynamicObjectEntry(fqn, spawn, 0, state);
        DynamicObjectRecreateContext ctx = new DynamicObjectRecreateContext(om);
        return ObjectRewindDynamicCodecs.genericRecreate(entry, ctx);
    }

    private static int readIntRecordComponent(Object record, String componentName) {
        try {
            var method = record.getClass().getDeclaredMethod(componentName);
            method.setAccessible(true);
            return (Integer) method.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + componentName + " from " + record.getClass(), e);
        }
    }

    private static int[] readIntArrayField(Object target, String fieldName) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (int[]) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static int readIntField(Object target, String fieldName) {
        try {
            var field = findField(target.getClass(), fieldName);
            return field.getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static boolean readBooleanField(Object target, String fieldName) {
        try {
            var field = findField(target.getClass(), fieldName);
            return field.getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static Object readObjectField(Object target, String fieldName) {
        try {
            var field = findField(target.getClass(), fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static void setIntField(Object target, String fieldName, int value) {
        try {
            var field = findField(target.getClass(), fieldName);
            field.setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static void setObjectField(Object target, String fieldName, Object value) {
        try {
            var field = findField(target.getClass(), fieldName);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) {
        try {
            var field = findField(target.getClass(), fieldName);
            field.setBoolean(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static ObjectInstance instantiateMadmoleSideDrill(int x, int y, boolean facingLeft) {
        try {
            Class<?> cls = Class.forName(BATCH43_DELETED_CODECS.getFirst().fqn());
            var ctor = cls.getDeclaredConstructor(int.class, int.class, boolean.class);
            ctor.setAccessible(true);
            return (ObjectInstance) ctor.newInstance(x, y, facingLeft);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to construct Madmole side-drill child", e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> cls, String fieldName)
            throws NoSuchFieldException {
        Class<?> current = cls;
        while (current != null) {
            try {
                var field = current.getDeclaredField(fieldName);
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

    private static SonicConfigurationService createDefaultConfiguration() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(
                java.nio.file.Path.of("target", "rewind-scalar-codec-deletion-config"));
        config.resetToDefaults();
        return config;
    }

    private static final class InertObjectArtProvider implements ObjectArtProvider {
        @Override public void loadArtForZone(int zoneIndex) {}
        @Override public PatternSpriteRenderer getRenderer(String key) { return null; }
        @Override public ObjectSpriteSheet getSheet(String key) { return null; }
        @Override public SpriteAnimationSet getAnimations(String key) { return null; }
        @Override public int getZoneData(String key, int zoneIndex) { return -1; }
        @Override public Pattern[] getHudDigitPatterns() { return new Pattern[0]; }
        @Override public Pattern[] getHudTextPatterns() { return new Pattern[0]; }
        @Override public Pattern[] getHudLivesPatterns() { return new Pattern[0]; }
        @Override public Pattern[] getHudLivesNumbers() { return new Pattern[0]; }
        @Override public List<String> getRendererKeys() { return List.of(); }
        @Override public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
            return baseIndex;
        }
        @Override public boolean isReady() { return true; }
    }
}
