package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameId;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic1.objects.Sonic1PointsObjectInstance;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic2.objects.PointsObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.DynamicObjectRecreateContext;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    // Private helpers

    private static ObjectInstance invokeGenericRecreate(String fqn, int x, int y, GameId gameId) {
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
        ObjectSpawn spawn = new ObjectSpawn(x, y, 0, 0, 0, false, 0);
        PerObjectRewindSnapshot state = new PerObjectRewindSnapshot(
                false, false, false, 0, 0, 0, 0, false, 0, false, false, 0, -1, null, null, null);
        ObjectManagerSnapshot.DynamicObjectEntry entry =
                new ObjectManagerSnapshot.DynamicObjectEntry(fqn, spawn, 0, state);
        DynamicObjectRecreateContext ctx = new DynamicObjectRecreateContext(om);
        return ObjectRewindDynamicCodecs.genericRecreate(entry, ctx);
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
