package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLbzFinalBoss1Instance {
    private static final int OBJ_LBZ_FINAL_BOSS_1 = 0xCA;

    @AfterEach
    void resetObjectCameraBounds() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @Test
    void initSetsRomHpCollisionTimerArtAndChildren() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);

        boss.update(0, null);

        assertEquals(9, boss.getCollisionProperty(), "loc_729DE writes collision_property(a0)=9");
        assertEquals(0x0F, boss.getCollisionFlags(), "ObjDat_LBZFinalBoss1 collision_flags is raw $0F");
        assertEquals(0x7F, boss.getActivationTimer(), "Sonic/Tails branch arms a $7F wait before activation");
        assertEquals(0x0C, boss.getMappingFrame(), "Robotnik ship body starts on frame $0C");
        assertEquals(Sonic3kObjectArtKeys.ROBOTNIK_SHIP, boss.getBodyArtKey());
        assertEquals(Sonic3kObjectArtKeys.LBZ_FINAL_BOSS_1, boss.getTurretArtKey());
        assertEquals(1, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.ROBOTNIK_HEAD).size());
        assertEquals(1, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.TOP_ATTACHMENT).size());
        assertEquals(3, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.TURRET_SEGMENT).size());
        assertEquals(2, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.LASER_HEAD).size());
        assertEquals(1, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.ORBITING_POD).size());
        assertEquals(2, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.GUN_POD).size());
        assertEquals(List.of(Sonic3kMusic.BOSS.id), services.musicIds);
    }

    @Test
    void activationAfterTimerStartsVerticalShuttleAndLoadsPalette() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);

        boss.update(0, null);
        for (int frame = 1; frame <= 0x80; frame++) {
            boss.update(frame, null);
        }

        assertEquals(4, boss.getRoutine(), "loc_72A5A switches routine to $04 after Obj_Wait");
        assertEquals(-0x100, boss.getYVelocity(), "activation sets y_vel=-$100");
        assertTrue(boss.isFinalBossPaletteLoaded(), "Pal_LBZFinalBoss1 must be loaded to line 1 on activation");
    }

    @Test
    void laserHeadsStrobeFiringNotchAndSpawnMuzzleForCurrentDirection() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        boss.activateForTest();
        boss.setRenderXFlipForTest(false);
        LbzFinalBoss1Instance.LaserHeadChild head = boss.childrenOfKindForTest(
                        LbzFinalBoss1Instance.ChildKind.LASER_HEAD).stream()
                .filter(LbzFinalBoss1Instance.LaserHeadChild.class::isInstance)
                .map(LbzFinalBoss1Instance.LaserHeadChild.class::cast)
                .findFirst()
                .orElseThrow();

        head.forceArcStepForTest(0);
        head.forceStepTimerExpiredForTest();
        head.update(1, null);

        assertTrue(boss.isLaserFiringNotchSet(), "frame $04 sets $38 bit3 so routine $04 can reposition");
        assertEquals(1, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.MUZZLE_LASER).size(),
                "matching direction at the firing notch creates loc_7321A muzzle child");

        head.forceArcStepForTest(1);
        head.forceStepTimerExpiredForTest();
        head.update(2, null);

        assertFalse(boss.isLaserFiringNotchSet(), "non-notch arc steps clear $38 bit3");
    }

    @Test
    void hitsAtHpFiveAndOneDetachSegmentsAndEnterRecoilHoldPath() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        boss.activateForTest();

        boss.forceHitCountForTest(6);
        boss.onPlayerAttack(null, null);

        assertEquals(5, boss.getCollisionProperty());
        assertTrue(boss.isDetachFlagSetForTest(1), "HP 5 sets first detach flag bit");
        assertEquals(8, boss.getRoutine(), "detach threshold enters routine $08 recoil wait");
        assertEquals(0x0F, boss.getRecoilTimer());
        assertEquals(2, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.HIT_SPARK).size());

        boss.finishHitFlashForTest();
        boss.forceHitCountForTest(2);
        boss.onPlayerAttack(null, null);

        assertEquals(1, boss.getCollisionProperty());
        assertTrue(boss.isDetachFlagSetForTest(2), "HP 1 sets second detach flag bit");
        assertTrue(boss.isLaserFiringNotchSet(), "HP 1 also forces the aggressive reposition bit");
    }

    @Test
    void hitSpeedQuirkClampsHighPositiveAndNegativeVelocity() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        boss.activateForTest();

        boss.forceYVelocityForTest(0x0600);
        boss.forceHitCountForTest(6);
        boss.onPlayerAttack(null, null);

        assertEquals(0x0800, boss.getYVelocity(),
                "sub_734FA clamps y_vel*2 to +$800 instead of leaving the original speed");

        boss.finishHitFlashForTest();
        boss.forceYVelocityForTest(-0x0600);
        boss.forceHitCountForTest(6);
        boss.onPlayerAttack(null, null);

        assertEquals(-0x0800, boss.getYVelocity(),
                "sub_734FA clamps y_vel*2 to -$800 instead of leaving the original speed");
    }

    @Test
    void recoilWaitsFifteenFramesThenDropsForExactlyFourFramesBeforeHold() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        boss.activateForTest();
        boss.forceHitCountForTest(6);
        boss.onPlayerAttack(null, null);
        int startY = boss.getCentreY();

        for (int frame = 0; frame < 0x0F; frame++) {
            boss.update(frame, null);
            assertEquals(8, boss.getRoutine(), "routine $08 must consume exactly fifteen wait frames");
            assertEquals(startY, boss.getCentreY(), "routine $08 wait frames must not drop the boss");
        }

        for (int frame = 0; frame < 4; frame++) {
            boss.update(0x20 + frame, null);
            assertEquals(startY + ((frame + 1) * 8), boss.getCentreY(),
                    "routine $0A adds y+=8 once per drop frame");
            if (frame < 3) {
                assertEquals(0x0A, boss.getRoutine(), "routine $0A owns the first three visible drop frames");
            }
        }

        assertEquals(6, boss.getRoutine(), "after four drop frames the boss returns to routine $06 hold");
        assertEquals(startY + 0x20, boss.getCentreY());
    }

    @Test
    void sonicDefeatSinksThenSpawnsResultsWithoutBigArm() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        boss.activateForTest();

        boss.forceHitCountForTest(1);
        boss.onPlayerAttack(null, null);
        boss.setCentreYForTest(services.cameraY() + 0x140);
        boss.forceFinaleTimerForTest(0);
        boss.setPlayersReadyForResultsForTest(true);
        boss.update(10, null);

        assertEquals(LbzFinalBoss1Instance.FinalePhase.WAIT_RESULTS_COMPLETE, boss.getFinalePhase());
        assertTrue(boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.RESULTS_SCREEN).stream()
                .anyMatch(S3kResultsScreenObjectInstance.class::isInstance));
        assertFalse(boss.spawnedClassNamesForTest().contains("Obj_LBZFinalBoss2"),
                "Sonic/Tails route must not enter the Knuckles Big Arm branch");
    }

    @Test
    void sonicFinaleWithRealGroundedPlayerAppliesEndingPoseAndClearsEndOfLevelFlag() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        services.gameState.setEndOfLevelFlag(true);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x44A0, (short) 0x0740);
        player.setDead(false);
        player.setAirForTest(false);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        boss.activateForTest();
        boss.forceHitCountForTest(1);
        boss.onPlayerAttack(player, null);
        boss.setCentreYForTest(services.cameraY() + 0x140);
        boss.forceFinaleTimerForTest(0);

        boss.update(10, player);

        assertEquals(LbzFinalBoss1Instance.FinalePhase.WAIT_RESULTS_COMPLETE, boss.getFinalePhase());
        assertTrue(player.isObjectControlled(), "Set_PlayerEndingPose applies native object_control ownership");
        assertTrue(player.isObjectControlSuppressesMovement(),
                "Set_PlayerEndingPose freezes player movement while results spawn");
        assertEquals(Sonic3kAnimationIds.VICTORY.id(), player.getForcedAnimationId(),
                "Sonic/Tails finale should force the victory animation");
        assertEquals(0, player.getForcedInputMask(), "ending pose clears scripted input");
        assertFalse(services.gameState.isEndOfLevelFlag(), "Obj_LBZFinalBoss1 clears End_of_level_flag before results");
        assertTrue(boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.RESULTS_SCREEN).stream()
                .anyMatch(S3kResultsScreenObjectInstance.class::isInstance));
    }

    @Test
    void knucklesDefeatIsLoggedStubAndNeverSpawnsBigArm() {
        HarnessServices services = new HarnessServices(PlayerCharacter.KNUCKLES);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);

        boss.forceHitCountForTest(1);
        boss.onPlayerAttack(null, null);
        boss.update(1, null);

        assertEquals(LbzFinalBoss1Instance.FinalePhase.KNUCKLES_STUB, boss.getFinalePhase());
        assertTrue(boss.isKnucklesBigArmStubbedForTest());
        assertFalse(boss.spawnedClassNamesForTest().contains("Obj_LBZFinalBoss2"),
                "Task D intentionally stubs Obj_LBZFinalBoss2/Big Arm");
    }

    @Test
    void resultsCompletionRestoresMusicAndLaunchMilestonesExposeState() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        boss.forceSonicFinalePhaseForTest(LbzFinalBoss1Instance.FinalePhase.WAIT_RESULTS_COMPLETE);

        boss.signalResultsCompleteForTest();
        boss.update(20, null);

        assertTrue(services.musicIds.contains(services.levelMusicId));
        assertEquals(LbzFinalBoss1Instance.FinalePhase.POST_RESULTS_DELAY, boss.getFinalePhase());

        boss.forceFinaleTimerForTest(0);
        boss.update(21, null);

        assertTrue(boss.isBossExplosionPlcQueuedForTest(),
                "FinalBoss1 step 3 should issue the raw PLC_BossExplosion load after the $1F delay");
        assertTrue(boss.isDeathEggSmallArtQueuedForTest(),
                "FinalBoss1 step 4 should queue ArtKosM_LBZ2DeathEggSmall for the launch miniatures");
        assertTrue(boss.isCutsceneAnchorRegisteredForTest(),
                "FinalBoss1 step 4 should register itself as the launch cutscene anchor");
        assertEquals(System.identityHashCode(boss), services.lbzState.getFinaleCutsceneAnchorId().orElseThrow(),
                "runtime anchor must be an object-reference-free id suitable for rewind snapshots");

        boss.forceSonicFinalePhaseForTest(LbzFinalBoss1Instance.FinalePhase.WAIT_LAUNCH_MILESTONE_B);
        boss.signalLaunchMilestoneBForTest();
        boss.update(21, null);

        assertTrue(services.lbzState.consumeFinalFallRequested(),
                "FinalBoss1 step 7 should expose the semantic FINAL_FALL hook through LBZ launch state");
    }

    @Test
    void autoWalkDrivesPlayerToCameraAnchorThenSpawnsLaunchFlames() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x4460, (short) 0x0740);
        boss.update(0, null);
        boss.forceSonicFinalePhaseForTest(LbzFinalBoss1Instance.FinalePhase.AUTOWALK);

        boss.update(30, player);

        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, player.getForcedInputMask(),
                "autowalk should drive P1 toward camera.x+$A0 before flames spawn");
        assertEquals(LbzFinalBoss1Instance.FinalePhase.AUTOWALK, boss.getFinalePhase());

        player.setCentreX((short) 0x44A0);
        boss.update(31, player);

        assertEquals(AbstractPlayableSprite.INPUT_UP, player.getForcedInputMask(),
                "when P1 reaches the launch mark the cutscene stops and holds Up for look-up setup");
        assertEquals(2, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.ENGINE_FLAME).size(),
                "FinalBoss1 finale step 5 spawns the two Death Egg engine flames");
        assertEquals(LbzFinalBoss1Instance.FinalePhase.WAIT_LAUNCH_MILESTONE_A, boss.getFinalePhase());
    }

    @Test
    void launchMilestoneAFreezesPlayerAndMilestoneBRequestsFinalFall() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x44A0, (short) 0x0740);
        boss.update(0, null);
        boss.forceSonicFinalePhaseForTest(LbzFinalBoss1Instance.FinalePhase.WAIT_LAUNCH_MILESTONE_A);

        boss.signalLaunchMilestoneAForTest();
        boss.update(40, player);

        assertTrue(player.isObjectControlled(), "milestone A freezes P1 under object control for the look-up script");
        assertEquals(Sonic3kAnimationIds.LOOK_UP.id(), player.getForcedAnimationId(),
                "milestone A should switch P1 to the look-up cutscene animation");
        assertEquals(LbzFinalBoss1Instance.FinalePhase.LOOK_UP, boss.getFinalePhase());

        boss.update(41, player);

        assertEquals(LbzFinalBoss1Instance.FinalePhase.WAIT_LAUNCH_MILESTONE_B, boss.getFinalePhase());

        boss.signalLaunchMilestoneBForTest();
        boss.update(42, player);

        assertTrue(services.lbzState.consumeFinalFallRequested());
        assertEquals(LbzFinalBoss1Instance.FinalePhase.FINAL_FALL, boss.getFinalePhase());
    }

    @Test
    void finalFallRequestsMhzTransitionWhenPlayerDropsBelowCamera() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x44A0, (short) 0x0730);
        boss.update(0, null);
        boss.forceSonicFinalePhaseForTest(LbzFinalBoss1Instance.FinalePhase.FINAL_FALL);

        boss.update(50, player);

        assertTrue(services.transitionRequested);
        assertTrue(boss.isDestroyed());
    }

    private static LbzFinalBoss1Instance newBoss(HarnessServices services) {
        LbzFinalBoss1Instance boss = new LbzFinalBoss1Instance(new ObjectSpawn(
                0x44A0, 0x0780, OBJ_LBZ_FINAL_BOSS_1, 0, 0, false, 0));
        boss.setServices(services);
        return boss;
    }

    private static final class HarnessServices extends StubObjectServices {
        private final Camera camera = new Camera();
        private final LbzZoneRuntimeState lbzState;
        private final GameStateManager gameState = new GameStateManager();
        private final List<Integer> musicIds = new ArrayList<>();
        private final int levelMusicId = 0x17;
        private boolean transitionRequested;

        private HarnessServices(PlayerCharacter character) {
            lbzState = new LbzZoneRuntimeState(1, character);
            camera.setX((short) 0x4400);
            camera.setY((short) 0x0600);
        }

        private int cameraY() {
            return Short.toUnsignedInt(camera.getY());
        }

        @Override
        public Camera camera() {
            return camera;
        }

        @Override
        public ZoneRuntimeState zoneRuntimeState() {
            return lbzState;
        }

        @Override
        public GameStateManager gameState() {
            return gameState;
        }

        @Override
        public void playMusic(int musicId) {
            musicIds.add(musicId);
        }

        @Override
        public int getCurrentLevelMusicId() {
            return levelMusicId;
        }

        @Override
        public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {
            assertEquals(Sonic3kZoneIds.ZONE_MHZ, zone);
            assertEquals(0, act);
            assertTrue(deactivateLevelNow);
            transitionRequested = true;
        }
    }
}
