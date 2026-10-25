package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdObjectState.ByteField;
import com.openggf.game.soniccd.objects.CdObjectState.WordField;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.Actor;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.AddPoints;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.Exit;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.FindObjSlot;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.PlayFm;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.PlayerGlobals;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.ResetOnFloor;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.Result;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.SubCpu;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.SubCpuCommand;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.WorldGlobals;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Constructed Player_TouchMonitor/TouchEnemy/TouchHazard/HurtPlayer/KillPlayer/AddPoints cases, recomputed from
 * scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff (Level/Palmtree Panic/Player Object Collision.asm:102-298,
 * Level/_Objects/HUD and Points/Main.asm:157-180, Level/Object Spawner.asm:329-340, Sonic/Main.asm:2295-2330).
 * Written before the implementation. All tests UNRUN by the external author. The single retail case
 * reads actual supplied media only when soniccd.media.root is configured.
 */
class TestCdPlayerTouchResponse {
    // EnemyPoints dc.w 10,20,50,100 (:200-204). Constructed format input; runtime binds only fromAssets.
    private static final byte[] ENEMY_POINTS = {0, 10, 0, 20, 0, 50, 0, 100};

    @Test void h1WalkingIntoMosquiWithRingsSpawnsTheLostRingAtThePlayerAndHurtsAwayFromTheEnemy() {
        var w = World.mosqui(0x2B); var p = new Player();
        var r = w.respond(p);
        // FindObjSlot's answer keeps every unwritten byte (fractions, routine); only oID/oX/oY are written (:236-238).
        assertEquals(List.of(w.slot(32).moveByte(ByteField.ID, 0x11).moveWord(WordField.X, 0x0260)
                .moveWord(WordField.Y, 0x076B)), r.changedSlots());
        assertEquals(0xBEEF, r.changedSlots().getFirst().word(WordField.X_FRACTION));
        var e = p.copy(); e.charge = 0; e.routine = 4; e.flags = 2; e.yv = -0x400; e.xv = -0x200; e.gv = 0;
        e.anim = 0x1A; e.hurt = 0x78; e.jump = 0;
        assertEquals(e.actor(), r.actor());
        w.combineRing = 0; w.scoreChain = 0;
        assertEquals(w.playerGlobals(), r.player());
        assertEquals(w.worldGlobals(), r.world()); // rings stay 5: only ObjLostRing_Init clears them.
        assertEquals(List.of(new FindObjSlot(OptionalInt.of(32)), new ResetOnFloor()), r.calls());
        assertEquals(OptionalInt.of(-1), r.d0());
        assertEquals(Exit.HURT, r.exit());
    }

    @ParameterizedTest @CsvSource({"607,-512", "608,-512", "623,-512", "624,512", "640,512"})
    void h2h3KnockbackStaysLeftOnlyWhilePlayerXIsBelowTheHazardXAndEqualityGoesRight(int x, int xVelocity) {
        var w = World.mosqui(0x2B); var p = new Player(); p.x = x;
        var r = w.respond(p);
        assertEquals(xVelocity, r.actor().motion().xVelocity());
        assertEquals(x, r.changedSlots().getFirst().centreX()); // The PLAYER's oX, not the hazard's.
        assertEquals(0x0270, w.slot(40).centreX());
    }

    @Test void knockbackSideTestIsAnUnsignedWordCompare() {
        // $7FF8 <u $8008 keeps the left knockback; a signed compare would have negated it.
        var w = World.mosqui(0xAB, 0x8008, 0x0760); var p = new Player(); p.x = 0x7FF8;
        assertEquals(-0x200, w.respond(p).actor().motion().xVelocity());
    }

    @ParameterizedTest @CsvSource({"608,-256", "640,256"})
    void h4UnderwaterHurtUsesHalfSpeedsAndKeepsBitSixBeforeTheSideTest(int x, int xVelocity) {
        var w = World.mosqui(0x2B); var p = new Player(); p.x = x; p.flags = 0x40;
        var r = w.respond(p);
        assertEquals(0x42, r.actor().motion().flags());
        assertEquals(-0x200, r.actor().motion().yVelocity());
        assertEquals(xVelocity, r.actor().motion().xVelocity());
    }

    @ParameterizedTest @CsvSource({"0,9,19,1894", "1,5,10,1897"})
    void h5h6RollingHazardHitUnrollsThroughTheSharedResetAfterTheRingTookThePreResetY(int mini, int xRadius,
                                                                                    int yRadius, int y) {
        var w = World.mosqui(0xAB); w.mini = mini;
        var p = new Player(); p.flags = 4; p.xr = 7; p.yr = 0x0E; p.anim = 2;
        var r = w.respond(p);
        var e = p.copy(); e.charge = 0; e.routine = 4; e.flags = 2; e.xr = xRadius; e.yr = yRadius; e.y = y;
        e.yv = -0x400; e.xv = -0x200; e.gv = 0; e.anim = 0x1A; e.hurt = 0x78; e.jump = 0;
        assertEquals(e.actor(), r.actor()); // SUBQ.W keeps the $ABCD fraction.
        assertEquals(0x076B, r.changedSlots().getFirst().centreY()); // HurtPlayer:238 precedes .SetHurt.
        assertEquals(List.of(new FindObjSlot(OptionalInt.of(32)), new ResetOnFloor(),
                new PlayFm(FmSound.FM_CHARGESTOP)), r.calls());
    }

    @Test void h7FullPoolRecordsTheFailedFindObjSlotAndStillHurtsWithoutAnyImage() {
        var w = World.mosqui(0x2B);
        for (int whole = 32; whole < 128; whole++) { if (whole != 40) { w.occupy(whole); } }
        var p = new Player();
        var r = w.respond(p);
        assertTrue(r.changedSlots().isEmpty());
        var e = p.copy(); e.charge = 0; e.routine = 4; e.flags = 2; e.yv = -0x400; e.xv = -0x200; e.gv = 0;
        e.anim = 0x1A; e.hurt = 0x78; e.jump = 0;
        assertEquals(e.actor(), r.actor());
        assertEquals(5, r.world().rings());
        assertEquals(List.of(new FindObjSlot(OptionalInt.empty()), new ResetOnFloor()), r.calls());
    }

    @ParameterizedTest @CsvSource({"1,0,1", "2,2,0", "3,2,1", "128,128,0", "129,128,1", "255,254,1"})
    void h8h9AnyNonzeroShieldSkipsRingLossButOnlyBitZeroPreservesCombineRing(int shield, int finalShield,
                                                                          int finalCombineRing) {
        var w = World.mosqui(0x2B); w.shield = shield; w.combineRing = 1;
        var r = w.respond(new Player());
        assertEquals(finalShield, r.player().shield());
        assertEquals(finalCombineRing, r.player().combineRing());
        assertTrue(r.changedSlots().isEmpty());
        assertEquals(List.of(new ResetOnFloor()), r.calls());
        assertEquals(4, r.actor().motion().routine()); assertEquals(Exit.HURT, r.exit());
    }

    @Test void shieldAlsoSkipsTheZeroRingKillTest() {
        var w = World.mosqui(0x2B); w.shield = 1; w.rings = 0;
        var r = w.respond(new Player());
        assertEquals(Exit.HURT, r.exit()); assertEquals(4, r.actor().motion().routine());
        assertEquals(0, r.player().shield()); assertEquals(1, r.player().combineRing());
        assertEquals(List.of(new ResetOnFloor()), r.calls());
    }

    @Test void h10ZeroRingsKillPlayerWritesTheYWordAcrossStickAndRotationDistance() {
        var w = World.mosqui(0x2B); w.rings = 0;
        var p = new Player();
        var r = w.respond(p);
        // MOVE.W oY,oPlayerStick (:289) writes $38 = $07 and $39 = $6B. Charge, hurt and shield are not written here.
        var e = p.copy(); e.routine = 6; e.flags = 2; e.yv = -0x700; e.xv = 0; e.gv = 0; e.stick = 0x07;
        e.rotation = 0x6B; e.anim = 0x18; e.tile = 0x8780; e.priority = 0; e.jump = 0;
        assertEquals(e.actor(), r.actor());
        w.scoreChain = 0;
        assertEquals(w.playerGlobals(), r.player()); // invincible already 0; combineRing 1 and shield 0 untouched.
        assertEquals(w.worldGlobals(), r.world());
        assertTrue(r.changedSlots().isEmpty());
        assertEquals(List.of(new ResetOnFloor(), new PlayFm(FmSound.FM_HURT)), r.calls());
        assertEquals(OptionalInt.of(-1), r.d0()); assertEquals(Exit.KILL_PLAYER, r.exit());
    }

    @Test void rollingKillPlayerStoresThePostResetYWordAndSetsOnlyTheTilePriorityBit() {
        var w = World.mosqui(0xAB, 0x0270, 0x06F8); w.rings = 0;
        var p = new Player(); p.y = 0x0703; p.flags = 4; p.xr = 7; p.yr = 0x0E; p.anim = 2; p.tile = 0x7FFF;
        var r = w.respond(p);
        var e = p.copy(); e.routine = 6; e.flags = 2; e.xr = 9; e.yr = 0x13; e.y = 0x06FE; e.yv = -0x700; e.xv = 0;
        e.gv = 0; e.stick = 0x06; e.rotation = 0xFE; e.anim = 0x18; e.tile = 0xFFFF; e.priority = 0; e.jump = 0;
        assertEquals(e.actor(), r.actor());
        assertEquals(List.of(new ResetOnFloor(), new PlayFm(FmSound.FM_CHARGESTOP), new PlayFm(FmSound.FM_HURT)),
                r.calls());
    }

    @ParameterizedTest @CsvSource({"1", "256", "65535"})
    void h11DebugCheatWordTurnsTheZeroRingDeathIntoAnAllocationFreeHurt(int cheat) {
        var w = World.mosqui(0x2B); w.rings = 0; w.debugCheat = cheat;
        var p = new Player();
        var r = w.respond(p);
        var e = p.copy(); e.charge = 0; e.routine = 4; e.flags = 2; e.yv = -0x400; e.xv = -0x200; e.gv = 0;
        e.anim = 0x1A; e.hurt = 0x78; e.jump = 0;
        assertEquals(e.actor(), r.actor());
        assertTrue(r.changedSlots().isEmpty());
        assertEquals(List.of(new ResetOnFloor()), r.calls());
        assertEquals(0, r.player().combineRing()); assertEquals(Exit.HURT, r.exit());
    }

    @ParameterizedTest @CsvSource({"1", "256", "32768"})
    void h12AnyNonzeroDebugModeWordMakesTheZeroRingKillWriteNothing(int debugMode) {
        // ObjSonic tests only debugMode's HIGH byte (Sonic/Main.asm:69); KillPlayer:280 tests the whole word.
        var w = World.mosqui(0xAB); w.rings = 0; w.debugMode = debugMode;
        var p = new Player(); p.flags = 4; p.xr = 7; p.yr = 0x0E;
        assertNoWrites(w, p, w.respond(p), Exit.KILL_PLAYER_DEBUG_NO_WRITE);
    }

    @ParameterizedTest @CsvSource({"1", "120", "256", "65535"})
    void h13NonzeroPostDisplayHurtWordReturnsNoHurtWithNoWrites(int hurt) {
        var w = World.mosqui(0x2B); var p = new Player(); p.hurt = hurt;
        assertNoWrites(w, p, w.respond(p), Exit.NO_HURT);
    }

    @ParameterizedTest @CsvSource({"1,0", "255,0", "0,1", "0,128"})
    void h14h15InvincibleOrTimeWarpWalkerKillsTheEnemyButGetsNoHurtFromAHazard(int invincible, int timeWarp) {
        var enemy = World.mosqui(0x2B); enemy.invincible = invincible; enemy.timeWarp = timeWarp;
        var kill = enemy.respond(new Player());
        assertEquals(Exit.KILL_ENEMY, kill.exit());
        assertEquals(0x18, kill.changedSlots().getFirst().id());
        var hazard = World.mosqui(0xAB); hazard.invincible = invincible; hazard.timeWarp = timeWarp;
        var p = new Player();
        assertNoWrites(hazard, p, hazard.respond(p), Exit.NO_HURT);
    }

    @Test void k1RollingKillRewritesTheSelectedSlotAsAnExplosionAndNegatesYVelocity() {
        var w = World.mosqui(0x2B); w.scoreChain = 0;
        var p = new Player(); p.anim = 2; p.yv = 0x300; p.y = 0x0750;
        var r = w.respond(p);
        var killed = w.slot(40).moveByte(ByteField.FLAGS, 0x81).moveWord(WordField.EXPLOSION_POINTS_FRAME, 0)
                .moveByte(ByteField.ID, 0x18).moveByte(ByteField.ROUTINE, 0).moveByte(ByteField.SUBTYPE, 1);
        assertEquals(List.of(killed), r.changedSlots());
        // oColType, oColStatus, oRoutine2 and positions are unwritten; ObjExplosion_Init clears oColType later.
        assertEquals(0x2B, r.changedSlots().getFirst().unsignedByte(ByteField.COLLISION_TYPE));
        var e = p.copy(); e.yv = -0x300;
        assertEquals(e.actor(), r.actor());
        w.scoreChain = 2;
        assertEquals(w.playerGlobals(), r.player());
        w.score = 10; w.hudScore = 1;
        assertEquals(w.worldGlobals(), r.world());
        assertEquals(List.of(new AddPoints(10, false), new PlayFm(FmSound.FM_DESTROY)), r.calls());
        assertEquals(OptionalInt.empty(), r.d0()); assertEquals(Exit.KILL_ENEMY, r.exit());
    }

    @ParameterizedTest @CsvSource({"-256,1872,0", "-32768,1872,-32512", "-1,1904,255", "768,1904,512",
            "768,1888,512", "0,1888,-256", "0,1872,0", "768,1872,-768"})
    void k2k3BounceTestsSignedVelocityThenThePlayerYAgainstTheEnemyY(int yVelocity, int y, int expected) {
        var w = World.mosqui(0x2B); var p = new Player(); p.anim = 2; p.yv = yVelocity; p.y = y;
        assertEquals(expected, w.respond(p).actor().motion().yVelocity());
    }

    @Test void bounceComparesPlayerAndEnemyYAsUnsignedWords() {
        // $8000 >=u $7FF8 takes .BounceUp; a signed compare would NEG.W instead.
        var w = World.mosqui(0x2B, 0x0270, 0x7FF8); var p = new Player(); p.anim = 2; p.yv = 0x300; p.y = 0x8000;
        assertEquals(0x200, w.respond(p).actor().motion().yVelocity());
    }

    @ParameterizedTest @CsvSource({"0,2,10,0", "2,4,20,2", "4,6,50,4", "6,8,100,6", "7,9,100,6", "8,10,100,6",
            "28,30,100,6", "30,32,1000,10", "31,33,1000,10", "32,34,1000,10", "65534,0,100,6", "65535,1,100,6"})
    void k4OldChainWordClampsTheTableIndexWhileTheNewWrappedChainDecidesTheThousand(int oldChain, int newChain,
                                                                                   int points, int frame) {
        var w = World.mosqui(0x2B); w.scoreChain = oldChain; var p = new Player(); p.anim = 2;
        var r = w.respond(p);
        assertEquals(newChain, r.player().scoreChain());
        assertEquals(frame, r.changedSlots().getFirst().word(WordField.EXPLOSION_POINTS_FRAME));
        assertEquals(new AddPoints(points, false), r.calls().getFirst());
        assertEquals(points, r.world().score());
    }

    @ParameterizedTest @CsvSource({"1", "3", "5"})
    void oddClampedChainStopsAtTheOddWordReadInsteadOfInventingATableIndex(int oldChain) {
        var w = World.mosqui(0x2B); w.scoreChain = oldChain; var p = new Player(); p.anim = 2;
        var before = w.pool.capture().slots();
        var thrown = assertThrows(CdPlayerTouchResponse.UnsupportedResponse.class, () -> w.respond(p));
        assertEquals(CdPlayerTouchResponse.UnsupportedBoundary.ODD_ENEMY_POINTS_ADDRESS, thrown.boundary());
        assertEquals(before, w.pool.capture().slots());
    }

    @ParameterizedTest @CsvSource({"999990,1000000,999999,1000000,3", "999899,1000000,999999,1000000,3",
            "999898,1000000,999998,1000000,3", "-10,1000000,90,1000000,3", "2147483647,1000000,999999,1000000,3",
            "999999,999999,999999,1004999,4"})
    void k5ScoreAddLWrapsBeforeTheUnsignedCapAndOnlyThenTestsTheNextLife(int score, int next, int finalScore,
                                                                       int finalNext, int lives) {
        var w = World.mosqui(0x2B); w.scoreChain = 6; w.score = score; w.next = next;
        var p = new Player(); p.anim = 2;
        var r = w.respond(p);
        assertEquals(finalScore, r.world().score()); assertEquals(finalNext, r.world().nextLifeScore());
        assertEquals(lives, r.world().lives()); assertEquals(1, r.world().updateHudScore());
        assertEquals(new AddPoints(100, lives == 4), r.calls().getFirst());
    }

    @Test void k6ReachingNextLifeScoreGrantsOneLifeAndOrdersSubCpuBeforeFmDestroy() {
        var w = World.mosqui(0x2B); w.scoreChain = 0; w.score = 4990; w.next = 5000; w.lives = 3; w.hudLives = 0;
        var p = new Player(); p.anim = 2;
        var r = w.respond(p);
        w.score = 5000; w.next = 10000; w.lives = 4; w.hudLives = 1; w.hudScore = 1;
        assertEquals(w.worldGlobals(), r.world());
        assertEquals(List.of(new AddPoints(10, true), new SubCpu(SubCpuCommand.SCMD_YESSFX),
                new PlayFm(FmSound.FM_DESTROY)), r.calls());
    }

    @Test void k7AtMostOneLifePerAddPointsCallEvenWhenTheRaisedThresholdIsStillReached() {
        var w = World.mosqui(0x2B); w.scoreChain = 0x1E; w.score = 4990; w.next = 5000;
        var p = new Player(); p.anim = 2;
        var r = w.respond(p);
        assertEquals(5990, r.world().score()); assertEquals(10000, r.world().nextLifeScore());
        assertEquals(4, r.world().lives());
        var constructed = World.mosqui(0x2B); constructed.scoreChain = 0x1E; constructed.score = 9000; constructed.next = 0;
        var q = new Player(); q.anim = 2;
        var once = constructed.respond(q);
        assertEquals(10000, once.world().score()); assertEquals(5000, once.world().nextLifeScore()); // No second test.
        assertEquals(4, once.world().lives()); assertEquals(1, once.world().updateHudLives());
        assertEquals(1, once.calls().stream().filter(SubCpu.class::isInstance).count());
    }

    @Test void k8LivesAndHudLivesAreWrappingBytes() {
        var w = World.mosqui(0x2B); w.scoreChain = 0; w.score = 4990; w.next = 5000; w.lives = 0xFF; w.hudLives = 0xFF;
        var p = new Player(); p.anim = 2;
        var r = w.respond(p);
        assertEquals(0, r.world().lives()); assertEquals(0, r.world().updateHudLives());
    }

    @Test void k9NonzeroColStatusTakesDamageEnemyWithoutScoreSoundOrBounce() {
        var w = World.mosqui(0x2B); w.pool.write(w.pool.state(40).moveByte(ByteField.COLLISION_STATUS, 2));
        var p = new Player(); p.anim = 2; p.xv = 0x0201; p.yv = 0x0300;
        var r = w.respond(p);
        var e = p.copy(); e.xv = -0x101; e.yv = -0x180;
        assertEquals(e.actor(), r.actor());
        assertEquals(List.of(w.slot(40).moveByte(ByteField.COLLISION_TYPE, 0).moveByte(ByteField.COLLISION_STATUS, 1)),
                r.changedSlots()); // oFlags stays $01 while the status byte remains nonzero.
        assertEquals(w.playerGlobals(), r.player()); assertEquals(w.worldGlobals(), r.world());
        assertTrue(r.calls().isEmpty());
        assertEquals(OptionalInt.empty(), r.d0()); assertEquals(Exit.DAMAGE_ENEMY, r.exit());
    }

    @ParameterizedTest @CsvSource({"1,0,129", "2,1,1", "255,254,1"})
    void k9DeleteFlagIsSetOnlyWhenTheStatusByteDecrementReachesZero(int status, int finalStatus, int finalFlags) {
        var w = World.mosqui(0x2B); w.pool.write(w.pool.state(40).moveByte(ByteField.COLLISION_STATUS, status));
        w.invincible = 1; // An invincible walker also enters .DamageEnemy (:131-134).
        var r = w.respond(new Player());
        var slot = r.changedSlots().getFirst();
        assertEquals(finalStatus, slot.unsignedByte(ByteField.COLLISION_STATUS));
        assertEquals(finalFlags, slot.flags());
        assertEquals(0, slot.unsignedByte(ByteField.COLLISION_TYPE));
        assertEquals(0x3D, slot.id());
    }

    @ParameterizedTest @CsvSource({"1,-1", "-1,0", "3,-2", "-3,1", "-32768,-16384", "32767,-16384", "0,0"})
    void k9NegThenMemoryAsrRoundsDownAndKeepsTheMinimumWord(int velocity, int expected) {
        var w = World.mosqui(0x2B); w.pool.write(w.pool.state(40).moveByte(ByteField.COLLISION_STATUS, 3));
        var p = new Player(); p.anim = 2; p.xv = velocity; p.yv = velocity;
        var r = w.respond(p);
        assertEquals(expected, r.actor().motion().xVelocity());
        assertEquals(expected, r.actor().motion().yVelocity());
    }

    @Test void idZeroHazardThatIsFindObjSlotsAnswerBecomesTheRingAndItsOverwrittenXKnocksRight() {
        var w = new World(); w.place(32, 0, 0xAB, 0x0270, 0x0760);
        var r = w.respond(new Player());
        // a2 still addresses slot 32; .NoWater (:258-261) reads its NEW oX (the player's), so NEG.W runs.
        assertEquals(List.of(w.slot(32).moveByte(ByteField.ID, 0x11).moveWord(WordField.X, 0x0260)
                .moveWord(WordField.Y, 0x076B)), r.changedSlots());
        assertEquals(0x84, r.changedSlots().getFirst().spriteFlags()); // Hazard bytes stay under the new $11.
        assertEquals(0x200, r.actor().motion().xVelocity());
        assertEquals(List.of(new FindObjSlot(OptionalInt.of(32)), new ResetOnFloor()), r.calls());
    }

    @Test void idZeroHazardAfterTheFirstFreeSlotIsNotTheAllocationAndKeepsItsOwnX() {
        var w = new World(); w.place(33, 0, 0xAB, 0x0270, 0x0760);
        var r = w.respond(new Player());
        assertEquals(1, r.changedSlots().size());
        assertEquals(32, r.changedSlots().getFirst().identity().wholeIndex());
        assertEquals(-0x200, r.actor().motion().xVelocity());
    }

    @Test void idZeroHazardAfterOccupiedSlotsIsStillChosenByIdOnly() {
        var w = new World(); w.occupy(32); w.place(33, 0, 0xAB, 0x0270, 0x0760);
        var r = w.respond(new Player());
        assertEquals(33, r.changedSlots().getFirst().identity().wholeIndex());
        assertEquals(0x200, r.actor().motion().xVelocity());
    }

    @Test void allocationIsDecidedOverTheCallCutSnapshotAndTheLivePoolIsNeverWritten() {
        var w = World.mosqui(0x2B); var p = new Player();
        var cut = w.pool.capture(); var selected = select(p, cut);
        w.occupy(32); // A later live writer cannot move the source decision made at the call.
        var r = response().respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), cut);
        assertEquals(List.of(cut.slots().getFirst().moveByte(ByteField.ID, 0x11).moveWord(WordField.X, 0x0260)
                .moveWord(WordField.Y, 0x076B)), r.changedSlots());
        assertEquals(0, cut.slots().getFirst().id());
        assertEquals(0x10, w.pool.state(32).id());
    }

    @Test void resultHoldsOnlyFinalValuesImagesAndCallsAndRepeatsExactly() {
        assertEquals(List.of(Actor.class, PlayerGlobals.class, WorldGlobals.class, List.class, List.class,
                OptionalInt.class, Exit.class), Arrays.stream(Result.class.getRecordComponents())
                .map(component -> component.getType()).toList());
        var w = World.mosqui(0x2B); var owner = response(); var p = new Player();
        var cut = w.pool.capture(); var selected = select(p, cut);
        var first = owner.respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), cut);
        assertEquals(first, owner.respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), cut));
        assertThrows(UnsupportedOperationException.class, () -> first.changedSlots().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.calls().clear());
        assertEquals(cut.slots(), w.pool.capture().slots());
    }

    @Test void specialRemainsUnownedAndTheSelectionMustBeTheCallCutImage() {
        for (int type : new int[]{0xEB}) { // MONITOR is now owned; SPECIAL still has no response owner.
            var w = World.mosqui(type); var p = new Player();
            var cut = w.pool.capture(); var selected = select(p, cut);
            assertThrows(IllegalArgumentException.class,
                    () -> response().respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), cut));
        }
        var w = World.mosqui(0x2B); var p = new Player();
        var cut = w.pool.capture(); var selected = select(p, cut);
        w.pool.write(w.pool.state(40).moveByte(ByteField.ROUTINE, 4));
        var later = w.pool.capture();
        assertThrows(IllegalArgumentException.class,
                () -> response().respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), later));
        var foreign = new CdObjectPool().capture();
        assertThrows(IllegalArgumentException.class,
                () -> response().respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), foreign));
        assertThrows(NullPointerException.class,
                () -> response().respond(null, p.actor(), w.playerGlobals(), w.worldGlobals(), cut));
        assertThrows(NullPointerException.class,
                () -> response().respond(selected, null, w.playerGlobals(), w.worldGlobals(), cut));
        assertThrows(NullPointerException.class,
                () -> response().respond(selected, p.actor(), null, w.worldGlobals(), cut));
        assertThrows(NullPointerException.class,
                () -> response().respond(selected, p.actor(), w.playerGlobals(), null, cut));
        assertThrows(NullPointerException.class,
                () -> response().respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), null));
    }

    @Test void constructorTakesOnlyTheCompleteEightByteTableAndReadsSuppliedWordsVerbatim() {
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTouchResponse(CdAssetBytes.copyOf(new byte[7])));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTouchResponse(CdAssetBytes.copyOf(new byte[9])));
        assertThrows(NullPointerException.class, () -> new CdPlayerTouchResponse(null));
        assertThrows(NullPointerException.class, () -> CdPlayerTouchResponse.fromAssets(null));
        var custom = new CdPlayerTouchResponse(CdAssetBytes.copyOf(new byte[]{0x12, 0x34, 0, 2, 0, 3, (byte) 0xFF, (byte) 0xFF}));
        for (int[] row : new int[][]{{0, 0x1234}, {2, 2}, {4, 3}, {6, 0xFFFF}}) {
            var w = World.mosqui(0x2B); w.scoreChain = row[0]; w.next = 1000000; var p = new Player(); p.anim = 2;
            assertEquals(new AddPoints(row[1], false), w.respond(custom, p).calls().getFirst());
        }
    }

    @Test void projectionsKeepNativeWidthsWithScoreAsRawLongBits() {
        var m = new Player().motion();
        assertThrows(NullPointerException.class, () -> new Actor(null, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Actor(m, 0x10000, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Actor(m, 0, 0x100, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Actor(m, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Actor(m, 0, 0, 0, 0x100));
        int[][] invalid = {{0x100, 0, 0, 0, 0, 0, 0, 0}, {0, 0x100, 0, 0, 0, 0, 0, 0}, {0, 0, 0x100, 0, 0, 0, 0, 0},
                {0, 0, 0, 0x100, 0, 0, 0, 0}, {0, 0, 0, 0, 0x10000, 0, 0, 0}, {0, 0, 0, 0, 0, 0x10000, 0, 0},
                {0, 0, 0, 0, 0, 0, 0x10000, 0}, {0, 0, 0, 0, 0, 0, 0, 0x100}, {-1, 0, 0, 0, 0, 0, 0, 0}};
        for (int[] g : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> new PlayerGlobals(g[0], g[1], g[2], g[3], g[4], g[5], g[6], g[7]));
        }
        assertThrows(IllegalArgumentException.class, () -> new WorldGlobals(0x10000, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldGlobals(0, 0x100, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldGlobals(0, 0, 0x100, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldGlobals(0, 0, 0, 0, 0, 0x100));
        assertDoesNotThrow(() -> new WorldGlobals(0xFFFF, 0xFF, 0xFF, -1, Integer.MIN_VALUE, 0xFF));
        assertThrows(IllegalArgumentException.class, () -> new FindObjSlot(OptionalInt.of(31)));
        assertThrows(IllegalArgumentException.class, () -> new FindObjSlot(OptionalInt.of(128)));
        assertThrows(NullPointerException.class, () -> new FindObjSlot(null));
        assertThrows(IllegalArgumentException.class, () -> new AddPoints(0x10000, false));
        assertThrows(NullPointerException.class, () -> new PlayFm(null));
        assertThrows(NullPointerException.class, () -> new SubCpu(null));
    }

    @Test void resultRejectsRepeatedSlotIdentitiesAndAD0ThatDisagreesWithTheExit() {
        var r = World.mosqui(0x2B).respond(new Player());
        var ring = r.changedSlots().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new Result(r.actor(), r.player(), r.world(),
                List.of(ring, ring), r.calls(), r.d0(), r.exit()));
        assertThrows(IllegalArgumentException.class, () -> new Result(r.actor(), r.player(), r.world(),
                r.changedSlots(), r.calls(), OptionalInt.empty(), Exit.HURT));
        assertThrows(IllegalArgumentException.class, () -> new Result(r.actor(), r.player(), r.world(),
                r.changedSlots(), r.calls(), OptionalInt.of(0), Exit.HURT));
        assertThrows(IllegalArgumentException.class, () -> new Result(r.actor(), r.player(), r.world(),
                r.changedSlots(), r.calls(), OptionalInt.of(-1), Exit.KILL_ENEMY));
        assertEquals(r, new Result(r.actor(), r.player(), r.world(), r.changedSlots(), r.calls(), r.d0(), r.exit()));
    }

    @Test void actualUsaEnemyPointsViewDrivesTheSameRewardsAsTheConstructedTable() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "UNRUN actual-media opt-in: set soniccd.media.root");
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve("USA"))) { // A configured but missing directory fails.
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue"))
                    .toList();
            assertEquals(1, cues.size(), "Configured USA media must contain exactly one CUE");
            cue = cues.getFirst();
        }
        CdPlayerTouchResponse actual;
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            actual = CdPlayerTouchResponse.fromAssets(CdR11aAssetLoader.load(source, CdReleaseVariant.USA,
                    CdStageKey.parse("R11A")).openingObjects());
        }
        for (int[] row : new int[][]{{0, 10}, {2, 20}, {4, 50}, {6, 100}, {0x1E, 1000}}) {
            var w = World.mosqui(0x2B); w.scoreChain = row[0]; var p = new Player(); p.anim = 2;
            var retail = w.respond(actual, p);
            assertEquals(new AddPoints(row[1], false), retail.calls().getFirst());
            assertEquals(w.respond(response(), p), retail);
        }
    }

    @Test void directKillCallNeedsNoEnemySelectionOrPoolAndPreservesUnwrittenActorFields() {
        var p = new Player(); p.routine = 4; p.flags = 0x70; p.hurt = 120;
        var result = CdPlayerTouchResponse.killPlayer(p.actor(), 0x80, 0, 0xBEEF, 0);
        var expected = p.copy(); expected.routine = 6; expected.flags = 0x42;
        expected.yv = -0x700; expected.xv = 0; expected.gv = 0; expected.jump = 0;
        expected.stick = 7; expected.rotation = 0x6B; expected.anim = 0x18;
        expected.tile = 0x8780; expected.priority = 0;
        assertEquals(expected.actor(), result.actor());
        assertEquals(0, result.invincible()); assertEquals(0, result.scoreChain());
        assertEquals(Exit.KILL_PLAYER, result.exit());
        assertEquals(List.of(new ResetOnFloor(), new PlayFm(FmSound.FM_HURT)), result.calls());
    }

    @ParameterizedTest @CsvSource({"0,9,19,1894,102", "1,5,10,1897,105"})
    void directKillResetsNormalOrMiniRollBeforeWritingTheTwoStickBytes(
            int mini, int xRadius, int yRadius, int y, int lowStickByte) {
        var p = new Player(); p.routine = 4; p.flags = 0x74; p.xr = 7; p.yr = 14;
        var result = CdPlayerTouchResponse.killPlayer(p.actor(), 1, 0, 0x1234, mini);
        var expected = p.copy(); expected.routine = 6; expected.flags = 0x42;
        expected.xr = xRadius; expected.yr = yRadius; expected.y = y;
        expected.yv = -0x700; expected.xv = 0; expected.gv = 0; expected.jump = 0;
        expected.stick = 7; expected.rotation = lowStickByte; expected.anim = 0x18;
        expected.tile = 0x8780; expected.priority = 0;
        assertEquals(expected.actor(), result.actor());
        assertEquals(List.of(new ResetOnFloor(), new PlayFm(FmSound.FM_CHARGESTOP),
                new PlayFm(FmSound.FM_HURT)), result.calls());
        assertEquals(0, result.scoreChain()); assertEquals(0, result.invincible());
    }

    @ParameterizedTest @CsvSource({"1", "256", "32768"})
    void directKillTestsTheWholeDebugWordAndLeavesEveryActorByteUntouched(int debug) {
        var p = new Player(); p.flags = 0x74;
        var result = CdPlayerTouchResponse.killPlayer(p.actor(), 0x80, debug, 0xBEEF, 1);
        assertEquals(p.actor(), result.actor()); assertEquals(0x80, result.invincible());
        assertEquals(0xBEEF, result.scoreChain()); assertTrue(result.calls().isEmpty());
        assertEquals(Exit.KILL_PLAYER_DEBUG_NO_WRITE, result.exit());
    }

    @ParameterizedTest @CsvSource({"0", "1"})
    void selectedHazardAndDirectKillReuseTheSameNativeCallResult(int debug) {
        var w = World.mosqui(0xAB); w.rings = 0; w.debugMode = debug;
        var p = new Player(); p.flags = 0x74;
        var direct = CdPlayerTouchResponse.killPlayer(p.actor(), w.invincible, w.debugMode, w.scoreChain, w.mini);
        var selected = w.respond(p);
        assertEquals(direct.actor(), selected.actor());
        assertEquals(direct.invincible(), selected.player().invincible());
        assertEquals(direct.scoreChain(), selected.player().scoreChain());
        assertEquals(direct.calls(), selected.calls()); assertEquals(direct.exit(), selected.exit());
    }

    @Test void directKillPortChecksNativeWidthsAndReturnsAnImmutableCallList() {
        var p = new Player();
        assertThrows(NullPointerException.class, () -> CdPlayerTouchResponse.killPlayer(null, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CdPlayerTouchResponse.killPlayer(p.actor(), 256, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CdPlayerTouchResponse.killPlayer(p.actor(), 0, 65536, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CdPlayerTouchResponse.killPlayer(p.actor(), 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> CdPlayerTouchResponse.killPlayer(p.actor(), 0, 0, 0, -1));
        assertThrows(UnsupportedOperationException.class,
                () -> CdPlayerTouchResponse.killPlayer(p.actor(), 0, 0, 0, 0).calls().clear());
    }


    // Monitor slice: hand literals from Player_TouchMonitor (:102-125), FixBugs=0.
    // Test mirrors frozen before either production mirror; ALL JAVA UNRUN.
    @ParameterizedTest @CsvSource({
            "784,768,-1,0,1,4,true",       // equality: $0310-$10 == $0300
            "783,768,-256,0,-256,0,false", // one below equality: no writes
            "785,768,-256,0,256,4,true",
            "784,768,-32768,165,-32768,165,true", // NEG.W $8000 stays $8000; fall $A5 stays
            "784,768,-1,1,1,1,true",
            "784,768,-256,255,256,255,true",
            "16,0,-1,0,1,4,true",
            "15,65535,-256,0,256,4,true", // SUB.W underflow wraps to $FFFF
            "0,65520,-256,0,256,4,true",  // $0000-$10 == $FFF0
            "0,65521,-256,0,-256,0,false",
            "32783,32768,-1,0,-1,0,false", // $7FFF <u $8000, despite the signed order
            "32784,32767,-1,0,1,4,true",
            "65535,65519,-32768,128,-32768,128,true"})
    void monitorUpwardUsesUnsignedWrappedYAndOnlyWritesVelocityAndTheZeroFallByte(
            int playerY, int objectY, int velocity, int fall, int expectedVelocity, int expectedFall, boolean writes) {
        var w = monitorWorld(objectY, fall, 0xFE); var p = new Player();
        p.y = playerY; p.yv = velocity; p.hurt = 0xFFFF; p.anim = 0x2B;
        var before = w.pool.capture(); var selected = selectMonitor(w, before);
        var r = response().respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), before);
        var expected = p.copy(); expected.yv = expectedVelocity;
        assertEquals(expected.actor(), r.actor());
        assertMonitorUnownedValues(w, r);
        assertEquals(writes ? Exit.MONITOR_UPWARD_BOUNCE : Exit.MONITOR_UPWARD_NO_WRITE, r.exit());
        if (writes) {
            byte[] bytes = before.slots().get(8).copyBytes();
            bytes[0x12] = (byte) 0xFE; bytes[0x13] = (byte) 0x80; bytes[0x25] = (byte) expectedFall;
            assertEquals(1, r.changedSlots().size());
            var image = r.changedSlots().getFirst();
            assertArrayEquals(bytes, image.copyBytes());
            assertEquals(selected.selectedSlot().orElseThrow().identity(), image.identity());
            assertDoesNotThrow(() -> w.pool.validateRestore(before));
        } else assertTrue(r.changedSlots().isEmpty());
        assertEquals(before.slots(), w.pool.capture().slots());
    }

    @ParameterizedTest @CsvSource({
            "512,2,0,2,-512,4,true", "0,2,0,2,0,4,true", "32767,2,0,254,-32767,0,true",
            "1,2,0,255,-1,1,true", "0,2,0,255,0,1,true",
            "512,0,4,2,512,2,false", "0,0,4,2,0,2,false", "512,3,4,254,512,254,false",
            "1,255,0,255,1,255,false", "32767,2,4,0,-32767,2,true"})
    void monitorGoingDownTestsAnimationRatherThanRollingStatusAndWrapsOnlyTheRoutineByte(
            int velocity, int animation, int flags, int routine, int expectedVelocity, int expectedRoutine, boolean writes) {
        var w = monitorWorld(0x0300, 0xA5, routine); var p = new Player();
        p.y = 0x0300; p.yv = velocity; p.anim = animation; p.flags = flags;
        var before = w.pool.capture(); var r = response().respond(selectMonitor(w, before), p.actor(),
                w.playerGlobals(), w.worldGlobals(), before);
        var expected = p.copy(); expected.yv = expectedVelocity;
        assertEquals(expected.actor(), r.actor()); assertMonitorUnownedValues(w, r);
        assertEquals(writes ? Exit.MONITOR_DOWNWARD_BOUNCE : Exit.MONITOR_DOWNWARD_NO_WRITE, r.exit());
        if (writes) {
            byte[] bytes = before.slots().get(8).copyBytes(); bytes[0x24] = (byte) expectedRoutine;
            assertEquals(1, r.changedSlots().size());
            assertArrayEquals(bytes, r.changedSlots().getFirst().copyBytes());
            assertEquals(before.slots().get(8).identity(), r.changedSlots().getFirst().identity());
        } else assertTrue(r.changedSlots().isEmpty());
        assertEquals(before.slots(), w.pool.capture().slots());
    }

    @ParameterizedTest @CsvSource({"-256,256,MONITOR_UPWARD_BOUNCE", "512,-512,MONITOR_DOWNWARD_BOUNCE"})
    void monitorHasNoHurtShieldInvincibilityTimeWarpOrDebugGate(int velocity, int expected,
                                                               Exit exit) {
        for (int[] gates : new int[][]{{0,0,0,0}, {0xFFFF,0,0,0}, {0,0x80,0,0},
                {0,0,0xFF,0}, {0,0,0,0x80}, {0xFFFF,0xFF,0xFF,0xFF}}) {
            var w = monitorWorld(0x0300, 0, 2); var p = new Player();
            p.y = 0x0310; p.yv = velocity; p.anim = 2; p.hurt = gates[0];
            w.shield = gates[1]; w.invincible = gates[2]; w.timeWarp = gates[3];
            w.rings = 0; w.debugCheat = 0xFFFF; w.debugMode = 0x8000; w.mini = 1;
            var cut = w.pool.capture(); var r = response().respond(selectMonitor(w, cut), p.actor(),
                    w.playerGlobals(), w.worldGlobals(), cut);
            var actor = p.copy(); actor.yv = expected;
            assertEquals(actor.actor(), r.actor()); assertEquals(exit, r.exit());
            assertMonitorUnownedValues(w, r); assertEquals(1, r.changedSlots().size());
            assertEquals(cut.slots(), w.pool.capture().slots());
        }
    }

    @Test void monitorRequiresSamePoolAndExactSelectedImageButNotSnapshotWrapperIdentity() {
        var w = monitorWorld(0x0300, 0, 2); var p = new Player(); p.y = 0x0310; p.yv = -1;
        var cut = w.pool.capture(); var selected = selectMonitor(w, cut); var samePool = w.pool.capture();
        assertNotSame(cut, samePool); assertSame(cut.slots().get(8), samePool.slots().get(8));
        var owner = response(); var expected = owner.respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), cut);
        assertEquals(expected, owner.respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), samePool));
        w.pool.write(w.pool.state(40).moveByte(ByteField.MAP_FRAME, 0x7F)); // Even an unowned byte makes it stale.
        var stale = w.pool.capture();
        assertThrows(IllegalArgumentException.class,
                () -> owner.respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), stale));
        var foreign = monitorWorld(0x0300, 0, 2).pool.capture();
        assertArrayEquals(cut.slots().get(8).copyBytes(), foreign.slots().get(8).copyBytes());
        assertEquals(cut.slots().get(8).identity(), foreign.slots().get(8).identity());
        assertThrows(IllegalArgumentException.class,
                () -> owner.respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), foreign));
        var otherIndex = CdObjectPool.overlay(cut, List.of(cut.slots().get(8).moveByte(ByteField.MAP_FRAME, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> owner.respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), otherIndex));
        assertEquals(stale.slots(), w.pool.capture().slots());
    }

    @ParameterizedTest @CsvSource({"0", "25"})
    void monitorSelectedIdZeroOrNineteenHasTheSameWritesWithoutAllocation(int id) {
        var w = monitorWorld(0x0300, 0, 2); var p = new Player(); p.y = 0x0310; p.yv = -1;
        w.pool.write(w.pool.state(40).moveByte(ByteField.ID, id));
        var cut = w.pool.capture(); var selected = selectMonitor(w, cut);
        assertEquals(40, selected.selectedSlot().orElseThrow().identity().wholeIndex());
        var r = response().respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), cut);
        assertEquals(Exit.MONITOR_UPWARD_BOUNCE, r.exit()); assertEquals(1, r.actor().motion().yVelocity());
        assertEquals(1, r.changedSlots().size()); assertEquals(id, r.changedSlots().getFirst().id());
        assertMonitorUnownedValues(w, r); assertEquals(cut.slots(), w.pool.capture().slots());
        assertEquals(0, cut.slots().getFirst().id(), "the free earlier slot was neither allocated nor cleared");
    }

    @Test void monitorLiteralRightBottomEqualityReturnsWithoutWritesBeforeTheLaterRing() {
        var w = new World(); var p = new Player(); p.x = 0x0100; p.y = 0x0200;
        w.place(32, 0x19, 0x46, 0x0118, 0x0220); w.place(33, 0x10, 0x47, 0x0100, 0x0200);
        var cut = w.pool.capture(); var selected = monitorScanner().scan(
                new CdPlayerObjectTouch.PlayerCut(p.x, p.y, p.yr, 0, p.hurt), cut);
        var handler = assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class, selected);
        assertEquals(CdPlayerObjectTouch.Kind.MONITOR, handler.kind());
        assertSame(cut.slots().getFirst(), handler.selectedSlot().orElseThrow());
        var r = response().respond(handler, p.actor(), w.playerGlobals(), w.worldGlobals(), cut);
        assertEquals(p.actor(), r.actor()); assertTrue(r.changedSlots().isEmpty());
        assertEquals(Exit.MONITOR_DOWNWARD_NO_WRITE, r.exit()); assertMonitorUnownedValues(w, r);
        w.pool.write(w.pool.state(32).moveWord(WordField.X, 0x0119));
        var later = monitorScanner().scan(new CdPlayerObjectTouch.PlayerCut(p.x, p.y, p.yr, 0, p.hurt), w.pool.capture());
        assertEquals(CdPlayerObjectTouch.Kind.ITEM, later.kind());
        assertEquals(33, later.selectedSlot().orElseThrow().identity().wholeIndex());
    }

    @Test void monitorResultIsFinalImmutableAndReplaysFromTheSavedCutAfterLiveMutationAndRestore() {
        var w = monitorWorld(0x0300, 0, 2); var p = new Player(); p.y = 0x0310; p.yv = -1;
        var cut = w.pool.capture(); var selected = selectMonitor(w, cut); var owner = response();
        var first = owner.respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), cut);
        assertEquals(1, first.changedSlots().size(), "the two upward stores publish one final physical-slot image");
        assertThrows(UnsupportedOperationException.class, () -> first.changedSlots().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.calls().clear());
        byte[] detached = first.changedSlots().getFirst().copyBytes(); detached[0x25] = 99;
        w.pool.deleteObject(40);
        assertEquals(first, owner.respond(selected, p.actor(), w.playerGlobals(), w.worldGlobals(), cut));
        w.pool.restore(cut);
        var restored = w.pool.capture();
        assertEquals(first, owner.respond(selectMonitor(w, restored), p.actor(), w.playerGlobals(), w.worldGlobals(), restored));
        assertEquals(4, first.changedSlots().getFirst().unsignedByte(ByteField.ROUTINE_2));
        assertEquals(cut.slots(), w.pool.capture().slots());
    }

    @Test void monitorExitsAppendWithoutChangingExistingExitOrdinalsOrMinusOneContracts() {
        assertEquals(List.of(Exit.DAMAGE_ENEMY, Exit.KILL_ENEMY, Exit.NO_HURT, Exit.HURT,
                Exit.KILL_PLAYER, Exit.KILL_PLAYER_DEBUG_NO_WRITE), Arrays.asList(Exit.values()).subList(0, 6));
        for (var exit : List.of(Exit.MONITOR_UPWARD_NO_WRITE, Exit.MONITOR_UPWARD_BOUNCE,
                Exit.MONITOR_DOWNWARD_NO_WRITE, Exit.MONITOR_DOWNWARD_BOUNCE)) {
            assertFalse(exit.explicitMinusOne());
            var p = new Player(); var w = monitorWorld(0x0300, 0, 2);
            assertThrows(IllegalArgumentException.class, () -> new Result(p.actor(), w.playerGlobals(), w.worldGlobals(),
                    List.of(), List.of(), OptionalInt.of(-1), exit));
            assertDoesNotThrow(() -> new Result(p.actor(), w.playerGlobals(), w.worldGlobals(),
                    List.of(), List.of(), OptionalInt.empty(), exit));
        }
    }

    private static void assertMonitorUnownedValues(World w, Result r) {
        assertEquals(w.playerGlobals(), r.player()); assertEquals(w.worldGlobals(), r.world());
        assertTrue(r.calls().isEmpty()); assertEquals(OptionalInt.empty(), r.d0());
    }
    private static World monitorWorld(int y, int fall, int routine) {
        var w = World.mosqui(0x46, 0x0270, y);
        w.pool.write(w.pool.state(40).moveByte(ByteField.ID, 0x19).moveByte(ByteField.ROUTINE, routine)
                .moveByte(ByteField.ROUTINE_2, fall).moveWord(WordField.Y_VELOCITY, 0x7777)
                .moveWord(WordField.Y_FRACTION, 0x5678));
        return w;
    }
    private static CdPlayerObjectTouch monitorScanner() {
        var dimensions = new java.util.ArrayList<>(sizes());
        dimensions.set(5, new CdR11aObjectAssets.TouchSize(0x10, 0x10)); // ObjColSizes selector6 (:404).
        return new CdPlayerObjectTouch(dimensions);
    }
    private static CdPlayerObjectTouch.HandlerBoundary selectMonitor(World w, CdObjectPool.Snapshot cut) {
        // Select the native entry independently of the actor vectors: this pure owner starts AFTER geometry.
        var slot = cut.slots().get(8);
        var selected = assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class, monitorScanner().scan(
                new CdPlayerObjectTouch.PlayerCut(slot.centreX(), slot.centreY(), 0x13, 0, 0), cut));
        assertEquals(CdPlayerObjectTouch.Kind.MONITOR, selected.kind());
        assertSame(slot, selected.selectedSlot().orElseThrow()); return selected;
    }

    private static void assertNoWrites(World w, Player p, Result r, Exit exit) {
        assertEquals(p.actor(), r.actor());
        assertEquals(w.playerGlobals(), r.player());
        assertEquals(w.worldGlobals(), r.world());
        assertTrue(r.changedSlots().isEmpty()); assertTrue(r.calls().isEmpty());
        assertEquals(OptionalInt.of(-1), r.d0()); assertEquals(exit, r.exit());
    }

    private static CdPlayerTouchResponse response() { return new CdPlayerTouchResponse(CdAssetBytes.copyOf(ENEMY_POINTS)); }

    private static CdPlayerObjectTouch.HandlerBoundary select(Player p, CdObjectPool.Snapshot cut) {
        var selected = new CdPlayerObjectTouch(sizes()).scan(new CdPlayerObjectTouch.PlayerCut(p.x, p.y, p.yr, 0, p.hurt), cut);
        return assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class, selected);
    }

    private static List<CdR11aObjectAssets.TouchSize> sizes() {
        // Constructed: every selector is $C x $C, the size ObjColSizes gives #43 ($2B). Geometry belongs to TestCdPlayerObjectTouch.
        return Collections.nCopies(63, new CdR11aObjectAssets.TouchSize(0x0C, 0x0C));
    }

    /** Test-only actor projection. Every field the response must leave alone carries a distinctive value. */
    private static final class Player {
        int x = 0x0260, xFraction = 0x1234, y = 0x076B, yFraction = 0xABCD;
        int xv = 0x200, yv, gv = 0x200, routine = 2, angle = 0x12, flags, spr = 4, xr = 9, yr = 0x13, anim, prev = 7;
        int jump = 1, charge = 0x1E, lock = 3, stick, ctrl, pri = 0x34, sec = 0x56;
        int tile = 0x0780, priority = 2, hurt, rotation = 0x5A;

        CdPlayerMotionState motion() {
            return new CdPlayerMotionState((x << 16) | xFraction, (y << 16) | yFraction, xv, yv, gv, routine, angle,
                    flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl, pri, sec);
        }
        Actor actor() { return new Actor(motion(), tile, priority, hurt, rotation); }
        Player copy() {
            var p = new Player();
            p.x = x; p.xFraction = xFraction; p.y = y; p.yFraction = yFraction; p.xv = xv; p.yv = yv; p.gv = gv;
            p.routine = routine; p.angle = angle; p.flags = flags; p.spr = spr; p.xr = xr; p.yr = yr; p.anim = anim;
            p.prev = prev; p.jump = jump; p.charge = charge; p.lock = lock; p.stick = stick; p.ctrl = ctrl;
            p.pri = pri; p.sec = sec; p.tile = tile; p.priority = priority; p.hurt = hurt; p.rotation = rotation;
            return p;
        }
    }

    /** Constructed dynamic RAM; the scanner and response see only its immutable call-cut capture. */
    private static final class World {
        final CdObjectPool pool = new CdObjectPool();
        int timeWarp, invincible, shield, combineRing = 1, debugCheat, debugMode, scoreChain = 4, mini;
        int rings = 5, lives = 3, hudLives, score, next = 5000, hudScore = 0x80;
        CdObjectPool.Snapshot cut;

        /** Row-11 Mosqui shape at whole 40 (oColType $2B, Mosqui/Main.asm:33); whole 32 is the first free slot. */
        static World mosqui(int colType) { return mosqui(colType, 0x0270, 0x0760); }
        static World mosqui(int colType, int x, int y) {
            var w = new World();
            // FindObjSlot tests only oID (Object Spawner.asm:334): these leftover bytes must survive under a new $11.
            w.pool.write(w.pool.state(32).moveWord(WordField.X_FRACTION, 0xBEEF).moveWord(WordField.Y_FRACTION, 0xCAFE)
                    .moveByte(ByteField.ROUTINE, 6));
            for (int whole = 33; whole < 40; whole++) { w.occupy(whole); }
            w.place(40, 0x3D, colType, x, y);
            return w;
        }
        void occupy(int whole) { pool.write(pool.state(whole).moveByte(ByteField.ID, 0x10)); }
        void place(int whole, int id, int colType, int x, int y) {
            pool.write(pool.state(whole).moveByte(ByteField.ID, id).moveByte(ByteField.SPRITE_FLAGS, 0x84)
                    .moveByte(ByteField.COLLISION_TYPE, colType).moveWord(WordField.X, x)
                    .moveWord(WordField.X_FRACTION, 0x4321).moveWord(WordField.Y, y).moveByte(ByteField.FLAGS, 1)
                    .moveByte(ByteField.ROUTINE, 2).moveByte(ByteField.ROUTINE_2, 4).moveByte(ByteField.SUBTYPE, 0x22)
                    .moveWord(WordField.EXPLOSION_POINTS_FRAME, 0xBEEF));
        }
        PlayerGlobals playerGlobals() {
            return new PlayerGlobals(timeWarp, invincible, shield, combineRing, debugCheat, debugMode, scoreChain, mini);
        }
        WorldGlobals worldGlobals() { return new WorldGlobals(rings, lives, hudLives, score, next, hudScore); }
        Result respond(Player p) { return respond(response(), p); }
        Result respond(CdPlayerTouchResponse owner, Player p) {
            cut = pool.capture();
            return owner.respond(select(p, cut), p.actor(), playerGlobals(), worldGlobals(), cut);
        }
        CdObjectState slot(int whole) { return cut.slots().get(whole - CdObjectPool.FIRST_WHOLE_INDEX); }
    }
}
