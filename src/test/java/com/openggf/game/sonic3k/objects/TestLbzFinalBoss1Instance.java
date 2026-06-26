package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
        assertEquals(-1, firstBossChild(boss, LbzFinalBoss1Instance.ChildKind.TURRET_SEGMENT).paletteOverrideForTest(),
                "ObjDat3_736D8/736E4 use make_art_tile(ArtTile_LBZFinalBoss1,1,1); keep platform/turret art on line 1");
        assertEquals(0, firstBossChild(boss, LbzFinalBoss1Instance.ChildKind.GUN_POD).paletteOverrideForTest(),
                "word_736F0 uses make_art_tile(ArtTile_LBZFinalBoss1,0,1) for the thruster/gun pods");
        // ROM: boss music arrives through Obj_Song_Fade_Transition, not a direct play.
        assertEquals(1, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.MUSIC_FADE).size());
        assertTrue(services.musicIds.isEmpty(),
                "loc_729DE spawns a fade-transition object instead of playing mus_EndBoss directly");
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
    void robotnikHeadFacesInwardAfterRandomSideReposition() throws Exception {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        boss.activateForTest();
        LbzFinalBoss1Instance.RobotnikHeadChild head = assertInstanceOf(
                LbzFinalBoss1Instance.RobotnikHeadChild.class,
                boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.ROBOTNIK_HEAD).get(0));

        boss.forceHitCountForTest(2);
        boss.setCentreYForTest(services.cameraY() - 0xB0);
        services.rng().setSeed(1); // Odd Random_Number bit: word_72AE8+$2 => camera.x+$30.
        boss.update(1, null);
        head.update(1, null);

        assertEquals(services.camera.getX() + 0x30, boss.getCentreX(),
                "loc_72AAA places the odd-random pass on the left side of the arena");
        assertTrue(hFlipForTest(head),
                "Robotnik head must inherit render_flags bit0 and face right/inward on the left side");

        boss.setCentreYForTest(services.cameraY() + 0x118);
        services.rng().setSeed(2); // Even Random_Number bit: word_72AE8 => camera.x+$110.
        boss.update(2, null);
        head.update(2, null);

        assertEquals(services.camera.getX() + 0x110, boss.getCentreX(),
                "loc_72AAA places the even-random pass on the right side of the arena");
        assertFalse(hFlipForTest(head),
                "Robotnik head must face left/inward on the right side");
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

        // Step 8 -> 9: frame $04 with table bit7 clear, matching the unflipped boss.
        head.forceArcStepForTest(8);
        head.forceStepTimerExpiredForTest();
        head.update(1, null);

        assertTrue(boss.isLaserFiringNotchSet(), "frame $04 sets $38 bit3 so routine $04 can reposition");
        assertEquals(1, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.MUZZLE_LASER).size(),
                "matching direction at the firing notch creates loc_7321A muzzle child");
        LbzFinalBoss1Instance.MuzzleLaserChild muzzle = assertInstanceOf(
                LbzFinalBoss1Instance.MuzzleLaserChild.class,
                boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.MUZZLE_LASER).get(0));
        assertEquals(0, muzzle.paletteOverrideForTest(),
                "word_73704 uses make_art_tile(ArtTile_LBZFinalBoss1,0,1) for the muzzle/laser");

        for (int frame = 4; frame < 120
                && boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.LASER_TRAIL).isEmpty(); frame++) {
            muzzle.update(frame, null);
        }
        assertTrue(muzzle.isFiredForTest(), "muzzle should reach loc_732BA and fire the beam");
        assertEquals(0, firstBossChild(boss, LbzFinalBoss1Instance.ChildKind.LASER_TRAIL).paletteOverrideForTest(),
                "word_7370C is the beam trail child and must match the laser palette");

        // Step 0 -> 1: frame $04 with table bit7 set — notch strobes but the
        // direction mismatch (sub_733FC d2 == 0) spawns no muzzle.
        head.forceArcStepForTest(0);
        head.forceStepTimerExpiredForTest();
        head.update(2, null);

        assertTrue(boss.isLaserFiringNotchSet(), "frame $04 always strobes $38 bit3");
        assertEquals(1, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.MUZZLE_LASER).size(),
                "mismatched head direction must not spawn a second muzzle");

        head.forceArcStepForTest(1);
        head.forceStepTimerExpiredForTest();
        head.update(3, null);

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
        assertTrue(boss.isDetachFlagSetForTest(0), "HP 5 sets $38 bit0 — the TOP segment detaches");
        assertEquals(8, boss.getRoutine(), "detach threshold enters routine $08 recoil wait");
        assertEquals(0x0F, boss.getRecoilTimer());
        assertEquals(2, boss.childrenOfKindForTest(LbzFinalBoss1Instance.ChildKind.HIT_SPARK).size());

        boss.finishHitFlashForTest();
        boss.forceHitCountForTest(2);
        boss.onPlayerAttack(null, null);

        assertEquals(1, boss.getCollisionProperty());
        assertTrue(boss.isDetachFlagSetForTest(1), "HP 1 sets $38 bit1 — the MID segment detaches");
        assertFalse(boss.isDetachFlagSetForTest(2), "the bottom segment only detaches at defeat");
        assertTrue(boss.isLaserFiringNotchSet(), "HP 1 also forces the aggressive reposition bit");
    }

    @Test
    void hitSpeedQuirkDoublesInRangeAndLeavesOutOfRangeVelocityUnchanged() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        boss.activateForTest();

        // In range: y_vel*2 within ±$800 is applied.
        boss.forceYVelocityForTest(0x0300);
        boss.forceHitCountForTest(7);
        boss.onPlayerAttack(null, null);

        assertEquals(0x0600, boss.getYVelocity(),
                "sub_734FA doubles y_vel when the doubled value stays within ±$800");

        // Out of range: the ROM skips the write — y_vel keeps its original value.
        boss.finishHitFlashForTest();
        boss.forceYVelocityForTest(0x0600);
        boss.forceHitCountForTest(7);
        boss.onPlayerAttack(null, null);

        assertEquals(0x0600, boss.getYVelocity(),
                "sub_734FA leaves y_vel unchanged when y_vel*2 exceeds +$800 (no clamping)");

        boss.finishHitFlashForTest();
        boss.forceYVelocityForTest(-0x0600);
        boss.forceHitCountForTest(7);
        boss.onPlayerAttack(null, null);

        assertEquals(-0x0600, boss.getYVelocity(),
                "sub_734FA leaves y_vel unchanged when y_vel*2 exceeds -$800 (no clamping)");
    }

    @Test
    void recoilWaitsSixteenFramesThenDropsForExactlyFiveFramesBeforeHold() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        boss.activateForTest();
        boss.forceHitCountForTest(6);
        boss.onPlayerAttack(null, null);
        int startY = boss.getCentreY();

        // ROM loc_72AEE: $40 = $F decrements to -1 — sixteen wait frames.
        for (int frame = 0; frame < 0x0F; frame++) {
            boss.update(frame, null);
            assertEquals(8, boss.getRoutine(), "routine $08 must consume sixteen wait frames");
            assertEquals(startY, boss.getCentreY(), "routine $08 wait frames must not drop the boss");
        }
        boss.update(0x0F, null);
        assertEquals(0x0A, boss.getRoutine(), "the sixteenth wait frame arms routine $0A");
        assertEquals(startY, boss.getCentreY(), "the transition frame itself does not drop yet");

        // ROM loc_72B04: $40 = 4 — y += 8 on every drop frame including the
        // one where the counter goes negative (five drops total).
        for (int frame = 0; frame < 5; frame++) {
            boss.update(0x20 + frame, null);
            assertEquals(startY + ((frame + 1) * 8), boss.getCentreY(),
                    "routine $0A adds y+=8 once per drop frame");
            if (frame < 4) {
                assertEquals(0x0A, boss.getRoutine(), "routine $0A owns the first four visible drop frames");
            }
        }

        assertEquals(6, boss.getRoutine(), "after five drop frames the boss returns to routine $06 hold");
        assertEquals(startY + 0x28, boss.getCentreY());
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
        boss.setPlayersReadyForResultsForTest(true);
        // Sink bottom reached: loc_72B34 arms the $3F wait + Ctrl_2 lock.
        boss.update(10, null);
        assertEquals(LbzFinalBoss1Instance.FinalePhase.WAIT_PLAYER_READY, boss.getFinalePhase());
        boss.forceFinaleTimerForTest(0);
        boss.update(11, null);

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

        boss.update(10, player);
        boss.forceFinaleTimerForTest(0);
        boss.update(11, player);

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
        assertFalse(player.isObjectControlSuppressesMovement(),
                "loc_72BBC restores player movement before Ctrl_1_locked forced autowalk; "
                        + "movement suppression leaves Sonic running in place forever");
        assertEquals(LbzFinalBoss1Instance.FinalePhase.AUTOWALK, boss.getFinalePhase());

        player.setCentreX((short) 0x44A0);
        boss.update(31, player);

        assertEquals(AbstractPlayableSprite.INPUT_UP, player.getForcedInputMask(),
                "when P1 reaches the launch mark the cutscene stops and holds Up for look-up setup");
        assertEquals(Direction.RIGHT, player.getDirection(),
                "loc_72C3C clears Status_Facing so Sonic faces right/away before looking up");
        assertFalse(player.getForcedAnimationId() == Sonic3kAnimationIds.LOOK_UP.id(),
                "loc_72C3C only holds Up and clears facing; the visible turn-away pose comes from "
                        + "Animate_ExternalPlayerSprite after milestone A");
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
        assertEquals(-1, player.getForcedAnimationId(),
                "loc_72C68 switches to external player sprite animation, not the regular LOOK_UP anim");
        assertEquals(LbzFinalBoss1Instance.FinalePhase.LOOK_UP, boss.getFinalePhase());

        boss.update(41, player);

        assertTrue(player.isObjectMappingFrameControl(),
                "Animate_ExternalPlayerSprite writes mapping_frame directly under object control");
        assertEquals(0x55, player.getMappingFrame(),
                "byte_7386A first Sonic frame is the turn-away/Death Egg look frame $55");
        assertEquals(Direction.RIGHT, player.getDirection(),
                "byte_7386A frame $55 leaves render_flags bit0 clear, so Sonic faces away/right first");
        assertEquals(LbzFinalBoss1Instance.FinalePhase.WAIT_LAUNCH_MILESTONE_B, boss.getFinalePhase());

        boss.signalLaunchMilestoneBForTest();
        boss.update(42, player);

        assertTrue(services.lbzState.consumeFinalFallRequested());
        assertFalse(player.getAir(),
                "loc_72CC6 only sets Events_fg_5; P1 stays in the external cutscene pose while the screen falls");
        assertTrue(player.isObjectMappingFrameControl(),
                "final fall must not release manual mapping control back to the regular player animation");
        assertEquals(LbzFinalBoss1Instance.FinalePhase.FINAL_FALL, boss.getFinalePhase());
    }

    @Test
    void launchPadEngineFlamesPlayBossExplosionSfx() {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x44A0, (short) 0x0740);
        boss.update(0, null);
        boss.forceSonicFinalePhaseForTest(LbzFinalBoss1Instance.FinalePhase.AUTOWALK);

        boss.update(30, player);
        AbstractObjectInstance flame = (AbstractObjectInstance) boss.childrenOfKindForTest(
                LbzFinalBoss1Instance.ChildKind.ENGINE_FLAME).get(0);
        flame.update(31, player);

        assertTrue(services.sfxIds.contains(Sonic3kSfx.EXPLODE.id),
                "sub_83E84 creates Obj_BossExplosion1, whose init routine plays sfx_Explode");
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

    @Test
    void deathEggExplosionDebrisUsesSpriteCheckDeleteXYNotXOnlyRange() throws Exception {
        HarnessServices services = new HarnessServices(PlayerCharacter.SONIC_AND_TAILS);
        LbzFinalBoss1Instance boss = newBoss(services);
        boss.update(0, null);
        AbstractObjectInstance debris = newDeathEggExplosionDebrisForTest(
                boss,
                services.camera.getX() + 0x20,
                services.camera.getY() + 0x180,
                7);

        debris.update(0, null);

        assertTrue(debris.isDestroyed(),
                "loc_72E54 switches to MoveChkDel, which calls Sprite_CheckDeleteXY; "
                        + "Y-offscreen debris must delete instead of wrapping forever while X remains in range");
    }

    private static LbzFinalBoss1Instance newBoss(HarnessServices services) {
        LbzFinalBoss1Instance boss = new LbzFinalBoss1Instance(new ObjectSpawn(
                0x44A0, 0x0780, OBJ_LBZ_FINAL_BOSS_1, 0, 0, false, 0));
        boss.setServices(services);
        return boss;
    }

    private static AbstractObjectInstance newDeathEggExplosionDebrisForTest(
            LbzFinalBoss1Instance boss,
            int x,
            int y,
            int frame)
            throws Exception {
        Class<?> cls = Class.forName(
                "com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance$DeathEggExplosionDebrisChild");
        Constructor<?> ctor = cls.getDeclaredConstructor(LbzFinalBoss1Instance.class, int.class, int.class, int.class);
        ctor.setAccessible(true);
        return (AbstractObjectInstance) ctor.newInstance(boss, x, y, frame);
    }

    private static LbzFinalBoss1Instance.BossChild firstBossChild(
            LbzFinalBoss1Instance boss,
            LbzFinalBoss1Instance.ChildKind kind) {
        return assertInstanceOf(LbzFinalBoss1Instance.BossChild.class,
                boss.childrenOfKindForTest(kind).stream().findFirst().orElseThrow());
    }

    private static boolean hFlipForTest(LbzFinalBoss1Instance.BossChild child) throws Exception {
        Field hFlip = LbzFinalBoss1Instance.BossChild.class.getDeclaredField("hFlip");
        hFlip.setAccessible(true);
        return hFlip.getBoolean(child);
    }

    private static final class HarnessServices extends StubObjectServices {
        private final Camera camera = new Camera();
        private final LbzZoneRuntimeState lbzState;
        private final GameStateManager gameState = new GameStateManager();
        private final List<Integer> musicIds = new ArrayList<>();
        private final List<Integer> sfxIds = new ArrayList<>();
        private final int levelMusicId = 0x17;
        private boolean transitionRequested;

        private HarnessServices(PlayerCharacter character) {
            lbzState = new LbzZoneRuntimeState(1, character);
            camera.setX((short) 0x4400);
            camera.setY((short) 0x0600);
        }

        @Override
        public com.openggf.level.objects.ObjectPlayerQuery playerQuery() {
            return new com.openggf.level.objects.ObjectPlayerQuery(() -> null, List::of);
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
        public void playSfx(int soundId) {
            sfxIds.add(soundId);
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
