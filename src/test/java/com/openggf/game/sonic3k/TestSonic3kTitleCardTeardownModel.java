package com.openggf.game.sonic3k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardTeardownModel;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.SeamlessTransitionResourceHandoff;
import com.openggf.level.resources.DeferredLevelResourceManifest;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the ROM-derived residual lifetime of the title-card owner object.
 *
 * <p>The expected level frame is not a calibration target. It follows from
 * {@code docs/skdisasm/sonic3k.asm}:
 *
 * <ul>
 *   <li>{@code objoff_2E} is seeded to {@code $16} (22) at line 7878, one write
 *       before {@code LevelLoop}, and {@code Obj_TitleCardWait2} spends level
 *       frames 0-21 draining it (62249-62253).</li>
 *   <li>Frame 22 first bumps {@code objoff_32} (62256-62261). The longest-lived
 *       element is {@code Obj_TitleCardName}: {@code objoff_28} = 3, so it starts
 *       moving on frame 24 and advances {@code $20} per frame from its
 *       {@code objoff_46} rest position {@code $120} (288) (62450-62456,
 *       62356-62366).</li>
 *   <li>{@code Render_Sprites} drops a {@code render_flags} {@code $40} sprite
 *       once {@code x_pos - 128 - width_pixels >= 320} (36444-36456). With
 *       {@code width_pixels} = {@code $80} (128) that is {@code x_pos >= 576},
 *       reached after nine steps on frame 32: {@code 288 + 9 * 32 = 576}.</li>
 *   <li>Frame 33 is the first frame the element renders off-screen, so on frame
 *       34 it decrements {@code objoff_30} to zero and deletes itself
 *       (62362-62363), and the owner falls through {@code loc_2D86E} to
 *       {@code loc_2D8CA}'s {@code LoadEnemyArt} (62263, 62295-62299).</li>
 * </ul>
 *
 * <p>Recorded ROM ground truth agrees: every non-AIZ S3K zone first becomes
 * Kos-queue busy on level frame 34, and ICZ's recorded
 * {@code KOS_DECOMPRESSION_QUEUE} completion whose fingerprint matches the
 * engine's enemy-art submission is admitted on that frame.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kTitleCardTeardownModel {

    /**
     * Level frame on which the ROM reaches LoadEnemyArt, counted as ticks of
     * the model. The provider ticks once per level frame and the engine's
     * canonical {@code Level_frame_counter} is {@code frameCounter + 1}, so the
     * Nth tick is level frame N.
     */
    private static final int EXPECTED_LOAD_ENEMY_ART_FRAME = 34;

    @Test
    @DisplayName("owner reaches LoadEnemyArt on level frame 34")
    void reachesLoadEnemyArtOnDerivedFrame() {
        Sonic3kTitleCardTeardownModel model = new Sonic3kTitleCardTeardownModel();
        int firedFrame = -1;
        for (int frame = 0; frame < 200; frame++) {
            if (model.tick()) {
                firedFrame = model.ticksElapsed();
                break;
            }
        }
        assertEquals(EXPECTED_LOAD_ENEMY_ART_FRAME, firedFrame,
                "LoadEnemyArt frame must match the ROM-derived count");
        assertTrue(model.isComplete());
    }

    @Test
    @DisplayName("objoff_2E alone holds the owner for its full $16 frames")
    void wait2CounterHoldsForTwentyTwoFrames() {
        Sonic3kTitleCardTeardownModel model = new Sonic3kTitleCardTeardownModel();
        for (int frame = 0; frame < 0x16; frame++) {
            assertFalse(model.tick(), "frame " + frame + " must still be counting down");
        }
        assertFalse(model.isComplete(), "element drain has not started yet");
    }

    @Test
    @DisplayName("restoreTicks replays the model exactly")
    void restoreReplaysDeterministically() {
        Sonic3kTitleCardTeardownModel reference = new Sonic3kTitleCardTeardownModel();
        for (int frame = 0; frame < 30; frame++) {
            reference.tick();
        }
        Sonic3kTitleCardTeardownModel restored = new Sonic3kTitleCardTeardownModel();
        restored.restoreTicks(reference.ticksElapsed());
        assertEquals(reference.ticksElapsed(), restored.ticksElapsed());
        for (int frame = 30; frame < EXPECTED_LOAD_ENEMY_ART_FRAME - 1; frame++) {
            // both models are still mid-drain here
            assertEquals(reference.tick(), restored.tick(),
                    "restored model must fire on the same frame");
        }
        assertTrue(restored.tick(), "restored model fires on the derived frame");
        assertTrue(restored.isComplete());
    }

    @Test
    @DisplayName("AIZ preserve-current reload keeps the existing enemy batch and lease")
    void aizPreserveCurrentTransitionDoesNotRegisterOrReleaseEnemyArt() throws Exception {
        HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();
        Sonic3kObjectArtProvider provider = (Sonic3kObjectArtProvider) GameServices.module()
                .getObjectArtProvider();
        PlcProgressSnapshot before = provider.capture();

        GameServices.level().executeActTransition(SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(0, 1)
                .preserveMusic(true)
                .preserveLevelGamestate(true)
                .showInLevelTitleCard(false)
                .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.PRESERVE_CURRENT)
                .build());

        PlcProgressSnapshot after = provider.capture();
        assertEquals(before.runtimeArtAdmissionGeneration(),
                after.runtimeArtAdmissionGeneration(),
                "AIZ1BGE_FireTransition does not execute LoadEnemyArt");
        assertEquals(before.runtimeArtAdmissionLeaseId(),
                after.runtimeArtAdmissionLeaseId());
        assertEquals(before.pendingKosModules(), after.pendingKosModules(),
                "the existing AIZ enemy descriptors stay owned");
        assertEquals(before.pendingKosOrdinals(), after.pendingKosOrdinals(),
                "preserve-current cannot clear or resubmit live enemy work");
        assertEquals(-1, after.titleCardTeardownTicks(),
                "a false transition overlay never creates skipped-initial teardown");
    }

    @Test
    void immediateTransitionArmsInExecutorAndSubmitsAtFollowingProviderPump()
            throws Exception {
        HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();
        Sonic3kObjectArtProvider provider = (Sonic3kObjectArtProvider) GameServices.module()
                .getObjectArtProvider();

        GameServices.level().executeActTransition(SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(0, 1)
                .preserveMusic(true)
                .preserveLevelGamestate(true)
                .showInLevelTitleCard(false)
                .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.IMMEDIATE)
                .build());

        PlcProgressSnapshot armed = provider.capture();
        assertTrue(armed.runtimeArtAdmissionConsumed());
        assertTrue(armed.kosSubmissionArmed());
        assertFalse(armed.pendingKosModules().isEmpty());
        assertTrue(armed.pendingKosOrdinals().isEmpty(),
                "executor consumption arms only; it must not submit parents");

        provider.processRuntimeArtQueue();

        assertFalse(provider.capture().pendingKosOrdinals().isEmpty(),
                "the existing following provider pump submits the armed batch");
    }

    @Test
    void directResourceHandoffPreparationFailsWithoutProviderMutation() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();
        Sonic3kObjectArtProvider provider = (Sonic3kObjectArtProvider)
                GameServices.module().getObjectArtProvider();
        PlcProgressSnapshot before = provider.capture();

        assertThrows(IllegalStateException.class, () ->
                provider.prepareRuntimeArtForActTransition(
                        0, RuntimeArtAdmissionPolicy.RESOURCE_HANDOFF_OWNER));

        assertEquals(before, provider.capture(),
                "unsupported direct preparation cannot refresh, schedule, issue, or arm");
    }

    @Test
    void resourceHandoffPolicyFailsAtExecutorEntryWithoutMutation()
            throws Exception {
        HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();
        var levelManager = GameServices.level();
        Sonic3kObjectArtProvider provider = (Sonic3kObjectArtProvider)
                GameServices.module().getObjectArtProvider();
        var gameState = GameServices.gameState();
        gameState.setEndOfLevelActive(true);
        gameState.setEndOfLevelFlag(true);
        var handoff = new RecordingHandoff();
        var handoffId = GameServices.seamlessTransitionResourceHandoffs()
                .register(handoff);

        int beforeZone = levelManager.getCurrentZone();
        int beforeAct = levelManager.getCurrentAct();
        var beforeLevel = levelManager.getCurrentLevel();
        var beforeObjectManager = levelManager.getObjectManager();
        var beforeRingManager = levelManager.getRingManager();
        var beforeRenderer = levelManager.getObjectRenderManager();
        var beforeLevelState = levelManager.getLevelGamestate();
        PlcProgressSnapshot beforeProvider = provider.capture();

        SeamlessLevelTransitionRequest request =
                SeamlessLevelTransitionRequest.builder(
                                SeamlessLevelTransitionRequest.TransitionType
                                        .RELOAD_TARGET_LEVEL)
                        .targetZoneAct(0, 1)
                        .preserveMusic(true)
                        .resourceHandoff(handoffId)
                        .runtimeArtAdmissionPolicy(
                                RuntimeArtAdmissionPolicy.RESOURCE_HANDOFF_OWNER)
                        .build();

        assertThrows(IllegalStateException.class,
                () -> levelManager.executeActTransition(request));

        assertSame(handoff,
                GameServices.seamlessTransitionResourceHandoffs().peek(handoffId),
                "entry rejection cannot claim registry ownership");
        assertEquals(0, handoff.transferCount);
        assertTrue(gameState.isEndOfLevelActive(),
                "entry rejection cannot reset Level_end_flag");
        assertTrue(gameState.isEndOfLevelFlag(),
                "entry rejection cannot reset End_of_level_flag");
        assertEquals(beforeZone, levelManager.getCurrentZone());
        assertEquals(beforeAct, levelManager.getCurrentAct());
        assertSame(beforeLevel, levelManager.getCurrentLevel());
        assertSame(beforeObjectManager, levelManager.getObjectManager());
        assertSame(beforeRingManager, levelManager.getRingManager());
        assertSame(beforeRenderer, levelManager.getObjectRenderManager());
        assertSame(beforeLevelState, levelManager.getLevelGamestate());
        assertEquals(beforeProvider, provider.capture(),
                "entry rejection cannot refresh, schedule, issue, or admit art");
    }

    private static final class RecordingHandoff
            implements SeamlessTransitionResourceHandoff {
        private int transferCount;

        @Override
        public DeferredLevelResourceManifest deferredResources() {
            return DeferredLevelResourceManifest.EMPTY;
        }

        @Override
        public void transferAfterTargetInit() {
            transferCount++;
        }
    }
}
