package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectState.ByteField;
import com.openggf.game.soniccd.objects.CdObjectState.WordField;
import com.openggf.game.soniccd.objects.CdSolidObject.Call;
import com.openggf.game.soniccd.objects.CdSolidObject.CeilingDistance;
import com.openggf.game.soniccd.objects.CdSolidObject.Entry;
import com.openggf.game.soniccd.objects.CdSolidObject.FloorDistance;
import com.openggf.game.soniccd.objects.CdSolidObject.Gates;
import com.openggf.game.soniccd.objects.CdSolidObject.GetOffReason;
import com.openggf.game.soniccd.objects.CdSolidObject.GlobalField;
import com.openggf.game.soniccd.objects.CdSolidObject.Globals;
import com.openggf.game.soniccd.objects.CdSolidObject.KillPlayerCall;
import com.openggf.game.soniccd.objects.CdSolidObject.Path;
import com.openggf.game.soniccd.objects.CdSolidObject.PlayFm;
import com.openggf.game.soniccd.objects.CdSolidObject.Player;
import com.openggf.game.soniccd.objects.CdSolidObject.PlayerField;
import com.openggf.game.soniccd.objects.CdSolidObject.Result;
import com.openggf.game.soniccd.objects.CdSolidObject.RoomInFront;
import com.openggf.game.soniccd.objects.CdSolidObject.Routine;
import com.openggf.game.soniccd.objects.CdSolidObject.RoutineCall;
import com.openggf.game.soniccd.objects.CdSolidObject.UnsupportedBoundary;
import com.openggf.game.soniccd.objects.CdSolidObject.UnsupportedSolidObject;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdSolidObject.PlayerField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Constructed Level/Object Solid.asm cases (GetOffObject :15-55, StandOnObject :65-146, BtmSolidObject :159-161,
 * TopSolidObject :174-175, SolidObject :188-479, CheckWallCrush :489-514, PushObject :524-581, StopObjPush :591-607)
 * at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, with KillPlayer (PP Player Object Collision.asm:279-298),
 * Player_ResetOnFloor (Sonic/Main.asm:2295-2330) and Collision Check.asm:14-65/79-125/298-314. Expectations are
 * literal values recomputed by hand from those lines; the terrain distances were also cross-checked with a
 * source-reasoning model, which is NOT a Java pass. Written before the implementation. All tests UNRUN by the
 * external author. Real probes run through the frozen ROOT S1 CdPlayerMovement ports over a constructed
 * format-decoder terrain (every FG cell is chunk 2, blocks 16x16, never an authenticated asset). The single
 * retail case reads actual supplied media only when soniccd.media.root is configured.
 */
class TestCdSolidObject {
    private static final OptionalInt NONE = OptionalInt.empty();
    /** A first vertical landing: both centre longs, StandOnObject's jump/flags and .SetInteract writes. */
    private static final Set<PlayerField> LANDING = EnumSet.of(CENTRE_X, CENTRE_X_FRACTION, CENTRE_Y, CENTRE_Y_FRACTION,
            PLAYER_JUMP, FLAGS, STAND_OBJECT, ANGLE, Y_VELOCITY, GROUND_VELOCITY);
    /** A continuing stand: .MoveOnObject longs, CLR.B oPlayerJump and the BSET #3 that finds us already recorded. */
    private static final Set<PlayerField> CARRY = EnumSet.of(CENTRE_X, CENTRE_X_FRACTION, CENTRE_Y, CENTRE_Y_FRACTION,
            PLAYER_JUMP, FLAGS);
    /** An executed KillPlayer without a rolled ResetOnFloor branch (Player Object Collision.asm:282-292). */
    private static final Set<PlayerField> KILL = EnumSet.of(ROUTINE, FLAGS, PLAYER_JUMP, Y_VELOCITY, X_VELOCITY,
            GROUND_VELOCITY, STICK, ROTATION_DISTANCE, ANIMATION, TILE, PRIORITY);
    /** GetOffObject's release of its own standing player (:38-49). */
    private static final Set<PlayerField> DETACH = EnumSet.of(STICK, FLAGS, PLAYER_CONTROL, STAND_OBJECT);

    // ----------------------------------------------------------------------------------------------------------
    // Vertical contact from above: .MoveOnObject, real probes and StandOnObject.
    // ----------------------------------------------------------------------------------------------------------

    @Test void topSolidLandingAttachesAfterTheRealOpenAirFloorProbe() {
        var s = new Scene(); var before = s.object();
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        // Y := oY(a0) - (12 + 19) = $61 keeps the $1234 fraction; .SetInteract stores the WHOLE index 40.
        var e = s.p.copy(); e.y = 0x00611234; e.yv = 0; e.gv = 0x0123; e.angle = 0; e.flags = 0x08; e.jump = 0; e.stand = 40;
        assertEquals(e.player(), r.player());
        assertEquals(before.moveByte(ByteField.FLAGS, 0x08), r.object()); // TOP wrote 1, StandOnObject cleared it.
        assertEquals(new Globals(0, 4, 0x12, 0x34), r.globals()); // Open air: neither FindLevelFloor stored an angle.
        assertEquals(List.of(new FloorDistance(27, NONE, NONE), call(Routine.STAND_ON_OBJECT)), r.calls());
        assertEquals(LANDING, r.playerWrites());
        assertEquals(EnumSet.of(ByteField.SOLID_TYPE, ByteField.FLAGS), r.objectWrites());
        assertEquals(Set.of(), r.globalWrites());
        assertEquals(List.of(), r.otherSlots());
        assertEquals(Entry.TOP_SOLID_OBJECT, r.entry());
        assertEquals(Path.STAND_ON, r.path());
        assertEquals(Optional.empty(), r.getOffReason());
        assertEquals(OptionalInt.of(1), r.d0());
        assertEquals(List.of(), r.fmRequests());
    }

    @Test void continuingStandRunsNoSetInteractSoAngleAndGroundVelocitySurvive() {
        var s = new Scene().standing(); s.p.gv = 0x55;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.jump = 0; // StandOnObject :107-116 returns once oPlayerStandObj is already us.
        assertEquals(e.player(), r.player());
        assertEquals(s.object(), r.object());
        assertEquals(CARRY, r.playerWrites());
        assertEquals(List.of(new FloorDistance(27, NONE, NONE), call(Routine.STAND_ON_OBJECT)), r.calls());
        assertEquals(OptionalInt.of(1), r.d0());
    }

    @ParameterizedTest @CsvSource({"0,9,19,97", "1,5,10,100"})
    void rollingLandingRestoresTheNormalOrMiniatureRadiiWithoutAChargeStopRequest(int mini, int xRadius, int yRadius,
                                                                                 int yWord) {
        var s = new Scene(); s.mini = mini;
        s.p.flags = 0x06; s.p.xr = 7; s.p.yr = 0x0E; s.p.anim = 2; s.p.y = 0x00681234;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        // .MoveOnObject places Y $66 with the ROLLING radius; StandOnObject then SUBQ.W #5 / #2 (:88-98).
        var e = s.p.copy(); e.y = (yWord << 16) | 0x1234; e.xr = xRadius; e.yr = yRadius; e.anim = 0; e.flags = 0x08;
        e.yv = 0; e.gv = 0x0123; e.angle = 0; e.jump = 0; e.stand = 40;
        assertEquals(e.player(), r.player());
        assertEquals(with(LANDING, X_RADIUS, Y_RADIUS, ANIMATION), r.playerWrites());
        assertEquals(List.of(new FloorDistance(27, NONE, NONE), call(Routine.STAND_ON_OBJECT)), r.calls());
        assertEquals(List.of(), r.fmRequests()); // Unlike Player_ResetOnFloor, StandOnObject requests no FM_CHARGESTOP.
    }

    @Test void hurtLandingReturnsToTheMainRoutineWithTheHurtTimer() {
        var s = new Scene(); s.p.routine = 4; s.p.hurt = 5;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.routine = 2; e.hurt = 120; e.y = 0x00611234; e.yv = 0; e.gv = 0x0123; e.angle = 0;
        e.flags = 0x08; e.jump = 0; e.stand = 40;
        assertEquals(e.player(), r.player());
        assertEquals(with(LANDING, ROUTINE, HURT), r.playerWrites());
    }

    @Test void landingOnANewObjectReleasesThePreviouslyStoodPhysicalSlot() {
        var s = new Scene(); s.p.flags = 0x08; s.p.stand = 41; s.p.yv = 0;
        s.other(41, o -> o.moveByte(ByteField.ID, 0x12).moveByte(ByteField.FLAGS, 0x09).moveWord(WordField.X, 0x0200));
        var previous = s.pool.state(41);
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        assertEquals(List.of(previous.moveByte(ByteField.FLAGS, 0x01)), r.otherSlots()); // BCLR #3,oFlags(a2) only.
        var e = s.p.copy(); e.y = 0x00611234; e.yv = 0; e.gv = 0x0123; e.angle = 0; e.flags = 0x08; e.jump = 0; e.stand = 40;
        assertEquals(e.player(), r.player());
        assertEquals(s.object().moveByte(ByteField.FLAGS, 0x08), r.object());
    }

    @Test void standByteZeroAddressesThePlayerSlotAndClearsItsOwnJustSetStandingBit() {
        var s = new Scene(); s.p.flags = 0x08; s.p.stand = 0; s.p.yv = 0;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        // 0*$40+$FFD000 is objPlayerSlot: :117-118 clears the player's bit 3 that :104 had just set.
        var e = s.p.copy(); e.y = 0x00611234; e.yv = 0; e.gv = 0x0123; e.angle = 0; e.flags = 0x00; e.jump = 0; e.stand = 40;
        assertEquals(e.player(), r.player());
        assertEquals(List.of(), r.otherSlots());
        assertEquals(s.object().moveByte(ByteField.FLAGS, 0x08), r.object());
    }

    @ParameterizedTest @CsvSource({"2,85", "4,291"})
    void springTopSkipsEveryTerrainProbeAndOnlyAnUpwardSpringKeepsGroundVelocity(int routine, int groundVelocity) {
        var s = new Scene(); s.object(o -> o.moveByte(ByteField.ID, 0x0A).moveByte(ByteField.ROUTINE, routine));
        s.p.gv = 0x55;
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.y = 0x00611234; e.yv = 0; e.gv = groundVelocity; e.angle = 0; e.flags = 0x08; e.jump = 0;
        e.stand = 40;
        assertEquals(e.player(), r.player());
        assertEquals(List.of(call(Routine.STAND_ON_OBJECT)), r.calls()); // .MoveY :393-394 bypasses both probes.
        assertEquals(routine == 2 ? without(LANDING, GROUND_VELOCITY) : LANDING, r.playerWrites());
    }

    @Test void aMovingObjectCarriesTheCompleteSixteenSixteenLongsIncludingFractionCarry() {
        var s = new Scene().standing();
        s.object(o -> o.moveWord(WordField.X_VELOCITY, 0x0180).moveWord(WordField.Y_VELOCITY, 0x0040));
        s.p.x = 0x0100C000; s.p.xv = 0;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x01024000; e.y = 0x00615234; e.jump = 0; // $C000+$8000 carries into the X word.
        assertEquals(e.player(), r.player());
        assertEquals(List.of(new RoomInFront(0xC0, 19, OptionalInt.of(0xC0), OptionalInt.of(0xC0)),
                new FloorDistance(27, NONE, NONE), call(Routine.STAND_ON_OBJECT)), r.calls());
        assertEquals(new Globals(0, 4, 0xC0, 0xC0), r.globals()); // MOVE.B d0 to both buffers (:27-28).
        assertEquals(EnumSet.of(GlobalField.PRIMARY_ANGLE, GlobalField.SECONDARY_ANGLE), r.globalWrites());
    }

    @Test void aCarriedPlayerIsMovedOutOfARealRightWallBeforeTheFloorProbe() {
        var s = new Scene().standing(); s.terrain.column(0, 0x4002); // Lrb-only full-width wall, angle $C0.
        s.object(o -> o.moveWord(WordField.X_VELOCITY, 0x0100)); s.p.xv = 0;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x00F58000; e.jump = 0; // $101 + d1 (-12) through ADD.W; fraction kept.
        assertEquals(e.player(), r.player());
        assertEquals(List.of(new RoomInFront(0xC0, -12, OptionalInt.of(0xC0), OptionalInt.of(0xC0)),
                new FloorDistance(27, NONE, NONE), call(Routine.STAND_ON_OBJECT)), r.calls());
        assertEquals(new Globals(0, 4, 0xC0, 0xC0), r.globals());
        assertEquals(CARRY, r.playerWrites());
    }

    @Test void aLeftCarriedPlayerIsMovedOutOfARealLeftWallWithTheNegatedDistance() {
        var s = new Scene().standing(); s.terrain.column(15, 0x4802); // X-flipped wall: angle $C0 -> $40.
        s.object(o -> o.moveWord(WordField.X_VELOCITY, -0x0100)); s.p.xv = 0;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x010A8000; e.jump = 0; // $FF + NEG.W d1 (-11 -> 11), :377-382.
        assertEquals(e.player(), r.player());
        assertEquals(new RoomInFront(0x40, -11, OptionalInt.of(0x40), OptionalInt.of(0x40)), r.calls().getFirst());
        assertEquals(new Globals(0, 4, 0x40, 0x40), r.globals());
        assertEquals(Path.STAND_ON, r.path());
    }

    @Test void aRealFloorAboveTheCarriedFeetTakesThePlayerAndReturnsNoCollision() {
        var s = new Scene(); s.terrain.row(6, 0x6001); s.object(o -> o.moveWord(WordField.Y, 0x0070)); s.p.y = 0x00531234;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.y = 0x004C1234; // $70-$1F = $51, then ADD.W d1 (-5), :404-407.
        assertEquals(e.player(), r.player());
        assertEquals(List.of(new FloorDistance(-5, OptionalInt.of(0), OptionalInt.of(0)), call(Routine.STOP_OBJ_PUSH),
                call(Routine.GET_OFF_OBJECT)), r.calls());
        assertEquals(new Globals(0, 4, 0, 0), r.globals());
        assertEquals(EnumSet.of(CENTRE_X, CENTRE_X_FRACTION, CENTRE_Y, CENTRE_Y_FRACTION), r.playerWrites());
        assertEquals(s.object(), r.object());
        assertEquals(Path.GET_OFF, r.path());
        assertEquals(Optional.of(GetOffReason.FLOOR_UNDER_OBJECT), r.getOffReason());
        assertEquals(OptionalInt.of(0), r.d0());
    }

    @Test void aPositiveRealFloorProbeStillStoresTheFoundAngleInBothBuffersBeforeStanding() {
        var s = new Scene(); s.terrain.row(6, 0x6001); s.object(o -> o.moveWord(WordField.Y, 0x0066)); s.p.y = 0x00471234;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        assertEquals(List.of(new FloorDistance(5, OptionalInt.of(0), OptionalInt.of(0)), call(Routine.STAND_ON_OBJECT)),
                r.calls());
        assertEquals(new Globals(0, 4, 0, 0), r.globals());
        assertEquals(Path.STAND_ON, r.path());
        assertEquals(0x00471234, r.player().actor().motion().centreY16_16());
    }

    @Test void aRisingPlatformCrushesIntoARealCeilingAndTheLaterGetOffClearsKillPlayersStickByte() {
        var s = risingPlatform(); s.terrain.row(5, 0x5001); // Y-flipped floor block: ceiling angle $80.
        s.p.ctrl = 0x01; s.p.charge = 0x1E; s.invincible = 1;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.y = 0x01601234; e.routine = 6; e.flags = 0x02; e.yv = -0x700; e.xv = 0; e.gv = 0;
        e.stick = 0; e.rot = 0x60; e.anim = 0x18; e.tile = 0x8780; e.priority = 0; e.jump = 0; e.ctrl = 0; e.stand = 0;
        assertEquals(e.player(), r.player());
        assertEquals(s.object().moveByte(ByteField.FLAGS, 0), r.object());
        var calls = r.calls();
        assertEquals(new CeilingDistance(-10, OptionalInt.of(0x80), NONE), calls.get(0));
        var killed = kill(calls.get(1));
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER, killed.exit());
        assertEquals(0x01, killed.actor().motion().stick()); // MOVE.W oY ($0160) to the $38 word; byte39 = $60.
        assertEquals(List.of(call(Routine.STOP_OBJ_PUSH), call(Routine.GET_OFF_OBJECT), new PlayFm(FmSound.FM_CHARGESTOP)),
                calls.subList(2, 5));
        assertEquals(5, calls.size());
        assertEquals(List.of(FmSound.FM_HURT, FmSound.FM_CHARGESTOP), r.fmRequests()); // KillPlayer never clears charge.
        assertEquals(new Globals(0, 0, 0x80, 0x34), r.globals());
        assertEquals(EnumSet.of(GlobalField.PRIMARY_ANGLE, GlobalField.INVINCIBLE, GlobalField.SCORE_CHAIN), r.globalWrites());
        assertEquals(with(with(KILL, CENTRE_X, CENTRE_X_FRACTION, CENTRE_Y, CENTRE_Y_FRACTION), PLAYER_CONTROL, STAND_OBJECT),
                r.playerWrites());
        assertEquals(Optional.of(GetOffReason.CEILING_CRUSH), r.getOffReason());
        assertEquals(OptionalInt.of(0), r.d0());
    }

    @Test void anOpenCeilingProbeLetsTheRisingPlatformCarryThePlayerUpward() {
        var s = risingPlatform();
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.y = 0x01601234; e.jump = 0;
        assertEquals(e.player(), r.player());
        assertEquals(List.of(new CeilingDistance(22, NONE, NONE), call(Routine.STAND_ON_OBJECT)), r.calls());
        assertEquals(new Globals(0, 4, 0x12, 0x34), r.globals());
    }

    @Test void aLowByteDebugModeWordPassesTheSolidGateButTheSharedKillPlayerWritesNothing() {
        var s = risingPlatform(); s.terrain.row(5, 0x5001); s.debugMode = 0x0001; s.p.ctrl = 0x01; s.invincible = 1;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.y = 0x01601234; e.flags = 0x02; e.ctrl = 0; e.stand = 0; e.stick = 0;
        assertEquals(e.player(), r.player()); // Routine, velocities, tile and invincible are unchanged.
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE, kill(r.calls().get(1)).exit());
        assertEquals(new Globals(1, 4, 0x80, 0x34), r.globals());
        assertEquals(EnumSet.of(GlobalField.PRIMARY_ANGLE), r.globalWrites());
        assertEquals(with(EnumSet.of(CENTRE_X, CENTRE_X_FRACTION, CENTRE_Y, CENTRE_Y_FRACTION), STICK, FLAGS,
                PLAYER_CONTROL, STAND_OBJECT), r.playerWrites());
        assertEquals(List.of(), r.fmRequests());
        assertEquals(Optional.of(GetOffReason.CEILING_CRUSH), r.getOffReason());
    }

    // ----------------------------------------------------------------------------------------------------------
    // Vertical contact from below: .PlayerBelow, .CheckCrushFloor and .HitBottom.
    // ----------------------------------------------------------------------------------------------------------

    @Test void jumpingIntoTheUndersideHitsBottomAndStopsVerticalMotion() {
        var s = below();
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.y = 0x00A11234; e.yv = 0; // SUB.W d2 ($FFFE) moves the player down by two.
        assertEquals(e.player(), r.player());
        assertEquals(EnumSet.of(CENTRE_Y, Y_VELOCITY), r.playerWrites());
        assertEquals(EnumSet.of(ByteField.SOLID_TYPE), r.objectWrites());
        assertEquals(List.of(call(Routine.STOP_OBJ_PUSH), call(Routine.GET_OFF_OBJECT)), r.calls());
        assertEquals(Path.HIT_BOTTOM, r.path());
        assertEquals(OptionalInt.of(1), r.d0());
    }

    @ParameterizedTest @ValueSource(ints = {0x100, 0, -0x100})
    void aGroundedPlayerIsKilledOnlyByADescendingObjectAndThenStillHitsBottom(int objectYVelocity) {
        var s = below(); s.object(o -> o.moveWord(WordField.Y_VELOCITY, objectYVelocity));
        s.p.flags = 0; s.p.yv = 0; s.p.gv = 0x0123; s.invincible = 1;
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.y = 0x00A11234; e.yv = 0; // .HitBottom overwrites KillPlayer's -$700.
        if (objectYVelocity > 0) {
            e.routine = 6; e.flags = 0x02; e.xv = 0; e.gv = 0; e.stick = 0; e.rot = 0x9F; e.anim = 0x18;
            e.tile = 0x8780; e.priority = 0; e.jump = 0;
            assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER, kill(r.calls().getFirst()).exit());
            assertEquals(List.of(call(Routine.STOP_OBJ_PUSH), call(Routine.GET_OFF_OBJECT)), r.calls().subList(1, 3));
            assertEquals(3, r.calls().size());
            assertEquals(new Globals(0, 0, 0x12, 0x34), r.globals());
            assertEquals(with(KILL, CENTRE_Y), r.playerWrites());
            assertEquals(EnumSet.of(GlobalField.INVINCIBLE, GlobalField.SCORE_CHAIN), r.globalWrites());
            assertEquals(List.of(FmSound.FM_HURT), r.fmRequests());
        } else {
            assertEquals(List.of(call(Routine.STOP_OBJ_PUSH), call(Routine.GET_OFF_OBJECT)), r.calls());
            assertEquals(new Globals(1, 4, 0x12, 0x34), r.globals());
            assertEquals(EnumSet.of(CENTRE_Y, Y_VELOCITY), r.playerWrites());
        }
        assertEquals(e.player(), r.player());
        assertEquals(Path.HIT_BOTTOM, r.path());
        assertEquals(OptionalInt.of(1), r.d0());
    }

    @ParameterizedTest @CsvSource({"0,9,19,151,149", "1,5,10,154,152"})
    void aRollingCrushRunsTheSharedResetResizeAndHitBottomUsesTheEarlierDepth(int mini, int xRadius, int yRadius,
                                                                             int yWord, int byte39) {
        var s = new Scene(); s.mini = mini; s.object(o -> o.moveWord(WordField.Y_VELOCITY, 0x0100));
        s.p.flags = 0x04; s.p.xr = 7; s.p.yr = 0x0E; s.p.anim = 2; s.p.y = 0x009A1234; s.p.yv = 0; s.p.gv = 0x0123;
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.routine = 6; e.flags = 0x02; e.xr = xRadius; e.yr = yRadius; e.y = (yWord << 16) | 0x1234;
        e.yv = 0; e.xv = 0; e.gv = 0; e.stick = 0; e.rot = byte39; e.anim = 0x18; e.tile = 0x8780; e.priority = 0;
        e.jump = 0;
        assertEquals(e.player(), r.player());
        assertEquals(List.of(FmSound.FM_CHARGESTOP, FmSound.FM_HURT), r.fmRequests());
        assertEquals(with(KILL, X_RADIUS, Y_RADIUS, CENTRE_Y), r.playerWrites());
        assertEquals(Path.HIT_BOTTOM, r.path());
    }

    // ----------------------------------------------------------------------------------------------------------
    // Horizontal contact: .CollideHoriz, PushObject, StopObjPush and CheckWallCrush.
    // ----------------------------------------------------------------------------------------------------------

    @Test void aLeftSidePushHaltsAGroundedPlayerAndMarksBothPushBits() {
        var s = side(); s.p.gv = 0x0123;
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x00E68000; e.flags = 0x20; e.gv = 0; e.xv = 0; e.push = 40;
        assertEquals(e.player(), r.player());
        assertEquals(s.object().moveByte(ByteField.FLAGS, 0x20), r.object());
        assertEquals(EnumSet.of(PUSH_OBJECT, CENTRE_X, FLAGS, GROUND_VELOCITY, X_VELOCITY), r.playerWrites());
        assertEquals(EnumSet.of(ByteField.SOLID_TYPE, ByteField.FLAGS), r.objectWrites());
        assertEquals(List.of(call(Routine.PUSH_OBJECT), call(Routine.GET_OFF_OBJECT), call(Routine.CHECK_WALL_CRUSH)),
                r.calls());
        assertEquals(Path.HALT_ON_X, r.path());
        assertEquals(OptionalInt.of(0), r.d0());
    }

    @Test void anAirbornePushOnlyClearsTheXVelocity() {
        var s = side(); s.p.flags = 0x02; s.p.gv = 0x0123;
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x00E68000; e.xv = 0; e.push = 40;
        assertEquals(e.player(), r.player());
        assertEquals(s.object(), r.object());
        assertEquals(EnumSet.of(PUSH_OBJECT, CENTRE_X, X_VELOCITY), r.playerWrites());
        assertEquals(EnumSet.of(ByteField.SOLID_TYPE), r.objectWrites());
    }

    @Test void aRightSidePushMovesThePlayerRightAndHaltsLeftwardMotion() {
        var s = side(); s.p.x = 0x01188000; s.p.xv = -0x0123; s.p.gv = -0x0123;
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x011A8000; e.flags = 0x20; e.gv = 0; e.xv = 0; e.push = 40; // SUB.W d0 = -2.
        assertEquals(e.player(), r.player());
        assertEquals(Path.HALT_ON_X, r.path());
    }

    @ParameterizedTest @ValueSource(ints = {0, -0x80})
    void aStationaryOrRetreatingLeftPlayerStopsPushingAndBothPushBitsClear(int xVelocity) {
        var s = side(); s.p.flags = 0x20; s.p.xv = xVelocity; s.p.push = 40;
        s.object(o -> o.moveByte(ByteField.FLAGS, 0x20));
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x00E68000; e.flags = 0; e.push = 0;
        assertEquals(e.player(), r.player());
        assertEquals(s.object().moveByte(ByteField.FLAGS, 0), r.object());
        assertEquals(EnumSet.of(CENTRE_X, PUSH_OBJECT, FLAGS), r.playerWrites()); // PushObject found us: no write.
        assertEquals(List.of(call(Routine.PUSH_OBJECT), call(Routine.GET_OFF_OBJECT), call(Routine.STOP_OBJ_PUSH),
                call(Routine.CHECK_WALL_CRUSH)), r.calls());
        assertEquals(Path.STOP_PUSH, r.path());
        assertEquals(OptionalInt.of(0), r.d0());
    }

    @Test void aSpringSidePushClearsThePushByteInsteadOfRecordingTheSpring() {
        var s = side(); s.object(o -> o.moveByte(ByteField.ID, 0x0A)); s.p.push = 41;
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x00E68000; e.flags = 0x20; e.gv = 0; e.xv = 0; e.push = 0;
        assertEquals(e.player(), r.player());
        assertEquals(Path.HALT_ON_X, r.path());
    }

    @Test void anotherPushedObjectWithNoMotionOnEitherSideLeavesThePushByteUnchanged() {
        var s = side(); s.p.push = 41;
        s.other(41, o -> o.moveByte(ByteField.ID, 0x12).moveWord(WordField.X, 0x00C0));
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x00E68000; e.flags = 0x20; e.gv = 0; e.xv = 0;
        assertEquals(e.player(), r.player());
        assertEquals(EnumSet.of(CENTRE_X, FLAGS, GROUND_VELOCITY, X_VELOCITY), r.playerWrites());
        assertEquals(List.of(), r.otherSlots()); // PushObject only reads a2.
    }

    @Test void aMovingObjectCrushesThePlayerAgainstTheOtherPushedPhysicalSlot() {
        var s = side(); s.object(o -> o.moveWord(WordField.X_VELOCITY, -0x0100)); s.p.push = 41; s.invincible = 1;
        s.other(41, o -> o.moveByte(ByteField.ID, 0x12).moveWord(WordField.X, 0x00C0));
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x00E68000; e.routine = 6; e.flags = 0x02; e.yv = -0x700; e.xv = 0; e.gv = 0;
        e.stick = 0; e.rot = 0x80; e.anim = 0x18; e.tile = 0x8780; e.priority = 0; e.jump = 0;
        assertEquals(e.player(), r.player()); // The crush RTS keeps oPlayerPushObj = 41 (:567-571).
        assertEquals(new Globals(0, 0, 0x40, 0x40), r.globals());
        var calls = r.calls();
        assertEquals(call(Routine.PUSH_OBJECT), calls.get(0));
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER, kill(calls.get(1)).exit());
        // KillPlayer's oYVel -$700 enters the later CalcRoomInFront prediction: Y $79, +8 = $81.
        assertEquals(List.of(call(Routine.GET_OFF_OBJECT), call(Routine.STOP_OBJ_PUSH), call(Routine.CHECK_WALL_CRUSH),
                new RoomInFront(0x40, 28, OptionalInt.of(0x40), OptionalInt.of(0x40))), calls.subList(2, 6));
        assertEquals(6, calls.size());
        assertEquals(with(KILL, CENTRE_X), r.playerWrites());
        assertEquals(EnumSet.allOf(GlobalField.class), r.globalWrites());
        assertEquals(List.of(FmSound.FM_HURT), r.fmRequests());
        assertEquals(Path.STOP_PUSH, r.path());
        assertEquals(OptionalInt.of(0), r.d0());
    }

    // x words $E8/$118; velocities +/-$123; the other pushed slot's X $C0/$F0/$140/$110.
    @ParameterizedTest @CsvSource({"232,291,192,true", "232,291,240,false", "280,-291,320,true", "280,-291,272,false"})
    void pushObjectCrushesOnlyWhenThePlayerLiesBetweenUsAndTheOtherPushedObject(int xWord, int xVelocity, int otherX,
                                                                              boolean killed) {
        var s = side(); s.p.x = (xWord << 16) | 0x8000; s.p.xv = xVelocity; s.p.push = 41;
        s.object(o -> o.moveWord(WordField.X_VELOCITY, -0x0100));
        s.other(41, o -> o.moveByte(ByteField.ID, 0x12).moveWord(WordField.X, otherX));
        var r = s.run(Entry.SOLID_OBJECT);
        assertEquals(killed, r.calls().stream().anyMatch(KillPlayerCall.class::isInstance));
        assertEquals(41, r.player().pushObject());
        assertEquals(killed ? Path.STOP_PUSH : Path.HALT_ON_X, r.path());
    }

    @Test void aFlowerCapsuleNeverCrushesBetweenPushedObjects() {
        var s = side(); s.p.push = 41;
        s.object(o -> o.moveByte(ByteField.ID, 0x15).moveWord(WordField.X_VELOCITY, -0x0100));
        s.other(41, o -> o.moveByte(ByteField.ID, 0x12).moveWord(WordField.X, 0x00C0));
        var r = s.run(Entry.SOLID_OBJECT);
        var e = s.p.copy(); e.x = 0x00E68000; e.flags = 0x20; e.gv = 0; e.xv = 0;
        assertEquals(e.player(), r.player());
        // .HaltOnX predicts with the player's +$123: X $E7, wall probe $D2 in open air.
        assertEquals(List.of(call(Routine.PUSH_OBJECT), call(Routine.GET_OFF_OBJECT), call(Routine.CHECK_WALL_CRUSH),
                new RoomInFront(0x40, 29, OptionalInt.of(0x40), OptionalInt.of(0x40))), r.calls());
        assertEquals(Path.HALT_ON_X, r.path());
    }

    @ParameterizedTest @CsvSource({"13,-4,true", "12,12,false"})
    void anObjectMovingLeftCrushesAStationaryPlayerOnlyIntoARealLeftWall(int wallColumn, int distance, boolean killed) {
        var s = side(); s.terrain.column(wallColumn, 0x4802); s.object(o -> o.moveWord(WordField.X_VELOCITY, -0x0100));
        s.p.xv = 0; s.invincible = 1;
        var r = s.run(Entry.SOLID_OBJECT);
        var calls = r.calls();
        assertEquals(List.of(call(Routine.PUSH_OBJECT), call(Routine.GET_OFF_OBJECT), call(Routine.STOP_OBJ_PUSH),
                call(Routine.CHECK_WALL_CRUSH), new RoomInFront(0x40, distance, OptionalInt.of(0x40), OptionalInt.of(0x40))),
                calls.subList(0, 5));
        var e = s.p.copy(); e.x = 0x00E68000; e.push = 0; // .SetPushObj then StopObjPush on the same call.
        if (killed) {
            assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER, kill(calls.get(5)).exit());
            assertEquals(6, calls.size());
            e.routine = 6; e.flags = 0x02; e.yv = -0x700; e.gv = 0; e.stick = 0; e.rot = 0x80; e.anim = 0x18;
            e.tile = 0x8780; e.priority = 0; e.jump = 0;
        } else {
            assertEquals(5, calls.size());
        }
        assertEquals(e.player(), r.player());
        assertEquals(new Globals(killed ? 0 : 1, killed ? 0 : 4, 0x40, 0x40), r.globals());
        assertEquals(Path.STOP_PUSH, r.path());
    }

    // ----------------------------------------------------------------------------------------------------------
    // Every .GetOff gate, in source order, plus the range and width/byte quirks.
    // ----------------------------------------------------------------------------------------------------------

    @Test void aDrowningPlayerGetsOffWithoutReleasingObjectControl() {
        var s = new Scene().standing(); s.p.anim = 0x17; s.p.ctrl = 0x01; s.p.stick = 1;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.flags = 0x02; e.stand = 0; e.stick = 0;
        assertEquals(e.player(), r.player());
        assertEquals(Optional.of(GetOffReason.PLAYER_DROWNING), r.getOffReason());
        assertEquals(EnumSet.of(STICK, FLAGS, STAND_OBJECT), r.playerWrites());
        assertEquals(s.object().moveByte(ByteField.FLAGS, 0), r.object());
        assertEquals(List.of(call(Routine.STOP_OBJ_PUSH), call(Routine.GET_OFF_OBJECT)), r.calls());
        assertEquals(Path.GET_OFF, r.path());
        assertEquals(OptionalInt.of(0), r.d0());
    }

    @Test void anInvisiblePlayerGetsOffWithoutReleasingObjectControl() {
        var s = new Scene().standing(); s.p.ctrl = 0x41; s.p.stick = 1;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.flags = 0x02; e.stand = 0; e.stick = 0;
        assertEquals(e.player(), r.player());
        assertEquals(Optional.of(GetOffReason.PLAYER_INVISIBLE), r.getOffReason());
        assertEquals(EnumSet.of(STICK, FLAGS, STAND_OBJECT), r.playerWrites());
    }

    @ParameterizedTest @ValueSource(ints = {6, 8, 0xFE})
    void aDeadOrLaterRoutineGetsOffAndReleasesObjectControl(int routine) {
        var s = new Scene().standing(); s.p.routine = routine; s.p.ctrl = 0x01; s.p.stick = 1;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.flags = 0x02; e.stand = 0; e.stick = 0; e.ctrl = 0;
        assertEquals(e.player(), r.player());
        assertEquals(Optional.of(GetOffReason.PLAYER_DEAD), r.getOffReason()); // CMPI.B #6 / BCC: unsigned.
        assertEquals(DETACH, r.playerWrites());
    }

    @Test void theHurtRoutineIsNotDeadAndReturnsToMainOnTheContinuingStand() {
        var s = new Scene().standing(); s.p.routine = 4;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        assertEquals(Path.STAND_ON, r.path());
        assertEquals(2, r.player().actor().motion().routine());
        assertEquals(120, r.player().actor().hurt());
        assertEquals(with(CARRY, ROUTINE, HURT), r.playerWrites());
    }

    @Test void anAbsentPlayerSlotGetsOffButGetOffObjectStillReleasesIt() {
        var s = new Scene().standing(); s.p.id = 0; s.p.ctrl = 0x01;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.flags = 0x02; e.stand = 0; e.ctrl = 0;
        assertEquals(e.player(), r.player());
        assertEquals(Optional.of(GetOffReason.PLAYER_ABSENT), r.getOffReason());
    }

    @Test void anOffscreenObjectReleasesAChargingPlayerWithAChargeStopRequestButKeepsTheCharge() {
        var s = new Scene().standing(); s.object(o -> o.moveByte(ByteField.SPRITE_FLAGS, 0x04)); s.p.charge = 0x1E;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        var e = s.p.copy(); e.flags = 0x02; e.stand = 0; // oPlayerCharge itself is not written (:32-35).
        assertEquals(e.player(), r.player());
        assertEquals(Optional.of(GetOffReason.OBJECT_OFFSCREEN), r.getOffReason());
        assertEquals(List.of(call(Routine.STOP_OBJ_PUSH), call(Routine.GET_OFF_OBJECT), new PlayFm(FmSound.FM_CHARGESTOP)),
                r.calls());
        assertEquals(List.of(FmSound.FM_CHARGESTOP), r.fmRequests());
    }

    @Test void getOffObjectReleasesOnlyThePhysicalSlotThePlayerStandsOn() {
        var s = new Scene().standing(); s.p.stand = 41; s.object(o -> o.moveByte(ByteField.SPRITE_FLAGS, 0x04));
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        assertEquals(Optional.of(GetOffReason.OBJECT_OFFSCREEN), r.getOffReason());
        assertEquals(s.p.copy().player(), r.player());
        assertEquals(Set.of(), r.playerWrites());
        assertEquals(s.object(), r.object()); // Our bit 3 survives: the identity compare at :21-30 fails first.
    }

    @ParameterizedTest @CsvSource({"256,true", "65280,true", "255,false"})
    void solidObjectTestsOnlyTheHighByteOfTheDebugModeWord(int debugMode, boolean gated) {
        var s = new Scene().standing(); s.debugMode = debugMode;
        var r = s.run(Entry.TOP_SOLID_OBJECT);
        if (gated) {
            assertEquals(Optional.of(GetOffReason.DEBUG_MODE), r.getOffReason());
        } else {
            assertEquals(Path.STAND_ON, r.path());
        }
    }

    // x words $E5/$E6/$119/$11A against oX $100, width $10: d1 = $1A from the constant player width 9.
    @ParameterizedTest @CsvSource({"229,X_RANGE_LEFT", "230,SHALLOW_SIDE", "281,SHALLOW_SIDE", "282,X_RANGE_RIGHT"})
    void horizontalRangeIsSignedBelowAndUnsignedAboveWithTheConstantPlayerWidth(int xWord, GetOffReason reason) {
        var s = new Scene(); s.p.x = (xWord << 16) | 0x8000; s.p.xr = 5; // A miniature X radius is never read here.
        var r = s.run(Entry.SOLID_OBJECT);
        assertEquals(Optional.of(reason), r.getOffReason());
        assertEquals(s.p.copy().player(), r.player());
        assertEquals(Set.of(), r.playerWrites());
    }

    // y words $5E/$5F/$A0/$A1 against oY $80 and d3 = $21; zero is "above" (.CollideVert :340-342).
    @ParameterizedTest @CsvSource({"94,GET_OFF,Y_RANGE_ABOVE", "95,STAND_ON,", "160,HIT_BOTTOM,", "161,GET_OFF,Y_RANGE_BELOW"})
    void verticalRangeEdgesTreatAZeroEdgeDistanceAsAbove(int yWord, Path path, GetOffReason reason) {
        var s = new Scene(); s.p.y = (yWord << 16) | 0x1234;
        var r = s.run(Entry.SOLID_OBJECT);
        assertEquals(path, r.path());
        assertEquals(Optional.ofNullable(reason), r.getOffReason());
    }

    @Test void aBoredPlayerIsSolidOnlyWhileTheObjectIsStoodOnAndBypassesBothRisingTests() {
        var notStood = new Scene(); notStood.p.anim = 0x2B;
        assertEquals(Optional.of(GetOffReason.BORED_NOT_STOOD_ON), notStood.run(Entry.TOP_SOLID_OBJECT).getOffReason());
        var stood = new Scene().standing(); stood.p.anim = 0x2B; stood.p.yv = -0x100;
        assertEquals(Path.STAND_ON, stood.run(Entry.TOP_SOLID_OBJECT).path());
    }

    @Test void topSolidityRejectsAnyRisingPlayerBeforeTheVerticalRange() {
        var top = new Scene(); top.p.yv = -1;
        assertEquals(Optional.of(GetOffReason.TOP_SOLID_RISING), top.run(Entry.TOP_SOLID_OBJECT).getOffReason());
        var solid = new Scene(); solid.p.yv = -1;
        assertEquals(Optional.of(GetOffReason.RISING_ABOVE), solid.run(Entry.SOLID_OBJECT).getOffReason());
        var resting = new Scene(); resting.p.yv = 0;
        assertEquals(Path.STAND_ON, resting.run(Entry.TOP_SOLID_OBJECT).path());
    }

    @Test void sideContactGatesForTopSolidityAirborneSpringsAndShallowDepth() {
        var top = side();
        assertEquals(Optional.of(GetOffReason.TOP_SOLID_SIDE), top.run(Entry.TOP_SOLID_OBJECT).getOffReason());
        var spring = side(); spring.object(o -> o.moveByte(ByteField.ID, 0x0A)); spring.p.flags = 0x02;
        assertEquals(Optional.of(GetOffReason.SPRING_SIDE_AIRBORNE), spring.run(Entry.SOLID_OBJECT).getOffReason());
        var shallow = side(); shallow.p.y = 0x00631234; // d5 = 4, CMPI.B #4 / BLS.
        assertEquals(Optional.of(GetOffReason.SHALLOW_SIDE), shallow.run(Entry.SOLID_OBJECT).getOffReason());
        var deep = side(); deep.p.y = 0x00641234;
        assertEquals(Path.HALT_ON_X, deep.run(Entry.SOLID_OBJECT).path());
    }

    @Test void sideDepthIsTestedAsALowByteSoAFullHex100DepthCountsAsShallow() {
        var s = side(); s.object(o -> o.moveByte(ByteField.Y_RADIUS, 0x7F)); s.p.yr = 0x7F; s.p.y = 0x00801234;
        assertEquals(Optional.of(GetOffReason.SHALLOW_SIDE), s.run(Entry.SOLID_OBJECT).getOffReason());
        var control = side(); control.object(o -> o.moveByte(ByteField.Y_RADIUS, 0x7F)); control.p.yr = 0x7F;
        control.p.y = 0x007F1234; // d5 = $FF.
        assertEquals(Path.HALT_ON_X, control.run(Entry.SOLID_OBJECT).path());
    }

    @Test void aMonitorIsNotSolidVerticallyForARollingPlayer() {
        var rolling = new Scene(); rolling.object(o -> o.moveByte(ByteField.ID, 0x19)); rolling.p.flags = 0x06;
        assertEquals(Optional.of(GetOffReason.MONITOR_ROLLING), rolling.run(Entry.SOLID_OBJECT).getOffReason());
        var upright = new Scene(); upright.object(o -> o.moveByte(ByteField.ID, 0x19));
        assertEquals(Path.STAND_ON, upright.run(Entry.SOLID_OBJECT).path());
    }

    @Test void belowContactGatesForTopSolidityAndTheSpinningDisc() {
        var top = below(); top.p.yv = 0;
        assertEquals(Optional.of(GetOffReason.TOP_SOLID_BELOW), top.run(Entry.TOP_SOLID_OBJECT).getOffReason());
        var disc = below(); disc.object(o -> o.moveByte(ByteField.ID, 0x09));
        assertEquals(Optional.of(GetOffReason.SPINNING_DISC_BELOW), disc.run(Entry.SOLID_OBJECT).getOffReason());
    }

    // Sprite flags $84 / $86 (vertical flip bit 1).
    @ParameterizedTest @CsvSource({"SOLID_OBJECT,132,GET_OFF", "SOLID_OBJECT,134,HIT_BOTTOM", "BTM_SOLID_OBJECT,132,HIT_BOTTOM"})
    void aSpringIsSolidFromBelowOnlyWhenBottomSolidOrVerticallyFlipped(Entry entry, int spriteFlags, Path path) {
        var s = below(); s.object(o -> o.moveByte(ByteField.ID, 0x0A).moveByte(ByteField.SPRITE_FLAGS, spriteFlags));
        var r = s.run(entry);
        assertEquals(path, r.path());
        if (path == Path.GET_OFF) assertEquals(Optional.of(GetOffReason.SPRING_BELOW), r.getOffReason());
    }

    @Test void bottomSolidityWritesTypeTwoAndPlainSolidObjectReadsAndClobbersTheRoutine2Alias() {
        var r = new Scene().run(Entry.BTM_SOLID_OBJECT);
        assertEquals(Path.STAND_ON, r.path()); // Type 2 is fully solid from above.
        assertTrue(r.objectWrites().contains(ByteField.SOLID_TYPE));
        assertEquals(0, r.object().unsignedByte(ByteField.SOLID_TYPE));
        var dirty = new Scene(); dirty.object(o -> o.moveByte(ByteField.ROUTINE_2, 1)); dirty.p.yv = -1;
        var d = dirty.run(Entry.SOLID_OBJECT); // oSolidType rs.b 0 IS oRoutine2 ($25).
        assertEquals(Optional.of(GetOffReason.TOP_SOLID_RISING), d.getOffReason());
        assertEquals(0, d.object().unsignedByte(ByteField.ROUTINE_2));
    }

    // ----------------------------------------------------------------------------------------------------------
    // Byte/word wraps.
    // ----------------------------------------------------------------------------------------------------------

    @Test void aNegativeWidthByteSignExtendsSoAPlayerHex200PixelsAwayStillLands() {
        var s = new Scene(); s.object(o -> o.moveByte(ByteField.WIDTH, 0xF0)); s.p.x = 0x03008000;
        var r = s.run(Entry.SOLID_OBJECT); // d1 = $FFFA, d2 = $FFF4: d0 $1FA passes both range tests.
        assertEquals(Path.STAND_ON, r.path());
        assertEquals(40, r.player().standObject());
        assertEquals(0x03008000, r.player().actor().motion().centreX16_16());
    }

    @Test void horizontalOverlapWrapsAsAWordAcrossTheCoordinateOrigin() {
        var s = new Scene(); s.object(o -> o.moveWord(WordField.X, 0x0005)); s.p.x = 0xFFF88000;
        var r = s.run(Entry.SOLID_OBJECT); // $FFF8 - $0005 + $1A = $000D.
        assertEquals(Path.STAND_ON, r.path());
        assertEquals(List.of(new FloorDistance(27, NONE, NONE), call(Routine.STAND_ON_OBJECT)), r.calls());
    }

    @Test void theTopPlacementWrapsTheYWordAndKeepsTheFraction() {
        var s = new Scene(); s.object(o -> o.moveByte(ByteField.ID, 0x0A).moveWord(WordField.Y, 0x0010));
        s.p.y = 0xFFF31234;
        var r = s.run(Entry.SOLID_OBJECT); // $0010 - $1F = $FFF1; the spring needs no terrain probe.
        assertEquals(0xFFF11234, r.player().actor().motion().centreY16_16());
        assertEquals(Path.STAND_ON, r.path());
    }

    // ----------------------------------------------------------------------------------------------------------
    // The two subroutine entries, boundaries, composition, validation and the authenticated binding.
    // ----------------------------------------------------------------------------------------------------------

    @Test void directGetOffObjectReleasesItsOwnStandingPlayerAndRequestsChargeStop() {
        var s = new Scene().standing(); s.p.charge = 0x10; s.p.ctrl = 0x01; s.p.stick = 1;
        var r = s.run(Entry.GET_OFF_OBJECT);
        var e = s.p.copy(); e.flags = 0x02; e.stand = 0; e.stick = 0; e.ctrl = 0;
        assertEquals(e.player(), r.player());
        assertEquals(s.object().moveByte(ByteField.FLAGS, 0), r.object());
        assertEquals(List.of(new PlayFm(FmSound.FM_CHARGESTOP)), r.calls());
        assertEquals(DETACH, r.playerWrites());
        assertEquals(EnumSet.of(ByteField.FLAGS), r.objectWrites());
        assertEquals(Path.ROUTINE_RETURN, r.path());
        assertEquals(OptionalInt.empty(), r.d0());
        assertEquals(Optional.empty(), r.getOffReason());
    }

    @Test void directGetOffObjectReturnsABoredPlayerToTheGround() {
        var s = new Scene().standing(); s.p.anim = 0x2B;
        assertEquals(0x00, s.run(Entry.GET_OFF_OBJECT).player().actor().motion().flags()); // BSET #1 then BCLR #1.
    }

    @ParameterizedTest @ValueSource(ints = {41, 0, 168})
    void directGetOffObjectOnlyComparesTheStandByteAndNeverDereferencesIt(int standByte) {
        var s = new Scene().standing(); s.p.stand = standByte;
        var r = s.run(Entry.GET_OFF_OBJECT);
        assertEquals(s.p.copy().player(), r.player());
        assertEquals(s.object(), r.object());
        assertEquals(Set.of(), r.playerWrites());
        assertEquals(Set.of(), r.objectWrites());
        assertEquals(List.of(), r.calls());
    }

    @Test void directStandOnObjectGivesUpForABoredPlayerWithoutMarkingEitherSlot() {
        var s = new Scene(); s.p.anim = 0x2B; s.p.routine = 4; s.p.hurt = 3;
        var r = s.run(Entry.STAND_ON_OBJECT); // Unreachable through SolidObject, whose bored gate needs bit 3 set.
        var e = s.p.copy(); e.routine = 2; e.hurt = 120; e.jump = 0;
        assertEquals(e.player(), r.player());
        assertEquals(s.object(), r.object()); // BSET #3 then BCLR #3; the solid type was already 0.
        assertEquals(List.of(call(Routine.GET_OFF_OBJECT)), r.calls());
        assertEquals(EnumSet.of(ROUTINE, HURT, PLAYER_JUMP), r.playerWrites());
        assertEquals(EnumSet.of(ByteField.SOLID_TYPE, ByteField.FLAGS), r.objectWrites());
        assertEquals(Path.ROUTINE_RETURN, r.path());
        assertEquals(OptionalInt.empty(), r.d0());
    }

    @ParameterizedTest @CsvSource({"5,RESERVED_SLOT_ADDRESS", "31,RESERVED_SLOT_ADDRESS", "128,OUT_OF_POOL_ADDRESS",
            "255,OUT_OF_POOL_ADDRESS"})
    void standingOnAnUnownedSlotStopsAtStandOnObjectsDereference(int standByte, UnsupportedBoundary boundary) {
        var s = new Scene(); s.p.flags = 0x08; s.p.stand = standByte; s.p.yv = 0;
        var thrown = assertThrows(UnsupportedSolidObject.class, () -> s.run(Entry.TOP_SOLID_OBJECT));
        assertEquals(boundary, thrown.boundary());
        assertEquals(standByte, thrown.slotByte());
        assertEquals(Routine.STAND_ON_OBJECT, thrown.routine());
    }

    @ParameterizedTest @CsvSource({"7,RESERVED_SLOT_ADDRESS", "200,OUT_OF_POOL_ADDRESS"})
    void pushingAgainstAnUnownedSlotStopsAtPushObjectsDereference(int pushByte, UnsupportedBoundary boundary) {
        var s = side(); s.p.push = pushByte;
        var thrown = assertThrows(UnsupportedSolidObject.class, () -> s.run(Entry.SOLID_OBJECT));
        assertEquals(boundary, thrown.boundary());
        assertEquals(pushByte, thrown.slotByte());
        assertEquals(Routine.PUSH_OBJECT, thrown.routine());
    }

    @Test void aSecondTopSolidCallOverTheThreadedFirstResultOnlyContinuesTheStand() {
        // ObjFloatBlock_TopSolid (Floating Block/Main.asm:156-165) BSRs .Check and then falls into it: two calls.
        var s = new Scene();
        var first = s.run(Entry.TOP_SOLID_OBJECT);
        s.pool.write(first.object());
        var second = s.terrain.owner().run(Entry.TOP_SOLID_OBJECT, first.object(), first.player(), s.gates(),
                first.globals(), s.pool.capture());
        assertEquals(Path.STAND_ON, second.path());
        assertEquals(first.player(), second.player());
        assertEquals(first.object(), second.object());
        assertEquals(CARRY, second.playerWrites());
        assertEquals(List.of(new FloorDistance(27, NONE, NONE), call(Routine.STAND_ON_OBJECT)), second.calls());
    }

    @Test void recordsAcceptOnlyNativeWidths() {
        var actor = new P().player().actor();
        assertThrows(IllegalArgumentException.class, () -> new Player(actor, 0x100, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Player(actor, 1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Player(actor, 1, 0, 0x100));
        assertThrows(NullPointerException.class, () -> new Player(null, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Gates(0x10000, 0));
        assertThrows(IllegalArgumentException.class, () -> new Gates(0, 0x100));
        assertThrows(IllegalArgumentException.class, () -> new Globals(0x100, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Globals(0, 0x10000, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Globals(0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Globals(0, 0, 0, 0x100));
        assertThrows(IllegalArgumentException.class, () -> new PlayFm(FmSound.FM_HURT));
    }

    @Test void everyCallInputIsExplicitAndRequired() {
        var s = new Scene(); var owner = s.terrain.owner(); var cut = s.pool.capture();
        var object = s.object(); var player = s.p.player(); var gates = s.gates(); var globals = s.globals();
        assertThrows(NullPointerException.class, () -> owner.run(null, object, player, gates, globals, cut));
        assertThrows(NullPointerException.class, () -> owner.run(Entry.SOLID_OBJECT, null, player, gates, globals, cut));
        assertThrows(NullPointerException.class, () -> owner.run(Entry.SOLID_OBJECT, object, null, gates, globals, cut));
        assertThrows(NullPointerException.class, () -> owner.run(Entry.SOLID_OBJECT, object, player, null, globals, cut));
        assertThrows(NullPointerException.class, () -> owner.run(Entry.SOLID_OBJECT, object, player, gates, null, cut));
        assertThrows(NullPointerException.class, () -> owner.run(Entry.SOLID_OBJECT, object, player, gates, globals, null));
        assertThrows(NullPointerException.class, () -> new CdSolidObject(null));
        assertThrows(NullPointerException.class, () -> CdSolidObject.fromAssets(null));
    }

    @Test void aResultRejectsAnInconsistentReturnSite() {
        var r = new Scene().run(Entry.TOP_SOLID_OBJECT);
        assertThrows(IllegalArgumentException.class, () -> new Result(r.entry(), r.object(), r.player(), r.globals(),
                r.otherSlots(), r.objectWrites(), r.playerWrites(), r.globalWrites(), r.calls(), r.path(),
                r.getOffReason(), OptionalInt.of(0)));
        assertThrows(IllegalArgumentException.class, () -> new Result(r.entry(), r.object(), r.player(), r.globals(),
                r.otherSlots(), r.objectWrites(), r.playerWrites(), r.globalWrites(), r.calls(), Path.ROUTINE_RETURN,
                r.getOffReason(), r.d0()));
        assertThrows(IllegalArgumentException.class, () -> new Result(r.entry(), r.object(), r.player(), r.globals(),
                r.otherSlots(), r.objectWrites(), r.playerWrites(), r.globalWrites(), r.calls(), r.path(),
                Optional.of(GetOffReason.DEBUG_MODE), r.d0()));
        assertThrows(IllegalArgumentException.class, () -> new Result(Entry.GET_OFF_OBJECT, r.object(), r.player(),
                r.globals(), r.otherSlots(), r.objectWrites(), r.playerWrites(), r.globalWrites(), r.calls(), r.path(),
                r.getOffReason(), r.d0()));
        assertThrows(IllegalArgumentException.class, () -> new Result(r.entry(), r.object(), r.player(), r.globals(),
                List.of(r.object()), r.objectWrites(), r.playerWrites(), r.globalWrites(), r.calls(), r.path(),
                r.getOffReason(), r.d0()));
        assertEquals(r, new Result(r.entry(), r.object(), r.player(), r.globals(), r.otherSlots(), r.objectWrites(),
                r.playerWrites(), r.globalWrites(), r.calls(), r.path(), r.getOffReason(), r.d0()));
    }

    @Test void actualUsaR11aBindingLeavesTheNeverDrawnPresentFloatBlockWithoutAPlayerWrite() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "UNRUN actual-media opt-in: set soniccd.media.root");
        java.nio.file.Path cue;
        try (var files = Files.list(java.nio.file.Path.of(configured).resolve("USA"))) { // A configured missing dir fails.
            var cues = files.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue"))
                    .toList();
            assertEquals(1, cues.size(), "Configured USA media must contain exactly one CUE");
            cue = cues.getFirst();
        }
        CdSolidObject actual;
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            actual = CdSolidObject.fromAssets(CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A")));
        }
        // Row 37 after ObjFloatBlock_Init (Floating Block/Main.asm:39-48) is never drawn on the Present floor
        // route, so DrawObjects never sets its sprite bit 7 and TopSolidObject takes .GetOff at :197-198.
        var s = new Scene();
        s.object(o -> o.moveByte(ByteField.SPRITE_FLAGS, 0x04).moveWord(WordField.X, 0x05B0).moveWord(WordField.Y, 0x020C)
                .moveByte(ByteField.WIDTH, 0x0C).moveByte(ByteField.Y_RADIUS, 0x0C));
        s.p.x = 0x05B00000; s.p.y = 0x076B0000; s.p.flags = 0; s.p.yv = 0;
        var r = s.run(actual, Entry.TOP_SOLID_OBJECT);
        assertEquals(Optional.of(GetOffReason.OBJECT_OFFSCREEN), r.getOffReason());
        assertEquals(Set.of(), r.playerWrites());
        assertEquals(s.p.copy().player(), r.player());
        assertEquals(r, s.run(Entry.TOP_SOLID_OBJECT));
    }

    // ----------------------------------------------------------------------------------------------------------
    // Fixtures.
    // ----------------------------------------------------------------------------------------------------------

    private static RoutineCall call(Routine routine) { return new RoutineCall(routine); }

    private static CdPlayerTouchResponse.KillResult kill(Call call) {
        return assertInstanceOf(KillPlayerCall.class, call).result();
    }

    private static Set<PlayerField> with(Set<PlayerField> base, PlayerField... extra) {
        var set = EnumSet.noneOf(PlayerField.class); set.addAll(base); set.addAll(Arrays.asList(extra));
        return set;
    }

    private static Set<PlayerField> without(Set<PlayerField> base, PlayerField... removed) {
        var set = EnumSet.noneOf(PlayerField.class); set.addAll(base); Arrays.asList(removed).forEach(set::remove);
        return set;
    }

    /** Player at the object's left edge: d0 = 2 (d4 = 2), centred vertically (d5 = $21), grounded and at rest. */
    private static Scene side() {
        var s = new Scene(); s.p.x = 0x00E88000; s.p.y = 0x00801234; s.p.flags = 0; s.p.yv = 0;
        return s;
    }

    /** Player rising into the underside: d2 $40 becomes -2 (d5 = 2). */
    private static Scene below() {
        var s = new Scene(); s.p.y = 0x009F1234; s.p.yv = -0x400;
        return s;
    }

    /** Standing on a platform at Y $180 rising by -$100: .MoveY lifts the player to $160 before GetCeilDist. */
    private static Scene risingPlatform() {
        var s = new Scene().standing();
        s.object(o -> o.moveWord(WordField.Y, 0x0180).moveWord(WordField.Y_VELOCITY, -0x0100));
        s.p.y = 0x01611234;
        return s;
    }

    /** Whole slot 40 at ($100,$80), width $10, Y radius $C; a falling normal-size player centred above it (d2 = 4). */
    private static final class Scene {
        final Terrain terrain = new Terrain();
        final CdObjectPool pool = new CdObjectPool();
        final P p = new P();
        int debugMode, mini, invincible, scoreChain = 4, primary = 0x12, secondary = 0x34;

        Scene() {
            pool.write(pool.state(40).moveByte(ByteField.ID, 0x12).moveByte(ByteField.SPRITE_FLAGS, 0x84)
                    .moveWord(WordField.X, 0x0100).moveWord(WordField.X_FRACTION, 0x4321)
                    .moveWord(WordField.Y, 0x0080).moveWord(WordField.Y_FRACTION, 0x8765)
                    .moveByte(ByteField.WIDTH, 0x10).moveByte(ByteField.Y_RADIUS, 0x0C)
                    .moveByte(ByteField.ROUTINE, 2).moveByte(ByteField.SUBTYPE, 0x22));
        }

        CdObjectState object() { return pool.state(40); }
        void object(UnaryOperator<CdObjectState> edit) { pool.write(edit.apply(object())); }
        void other(int whole, UnaryOperator<CdObjectState> edit) { pool.write(edit.apply(pool.state(whole))); }

        /** Already standing on whole 40: both bit 3s and oPlayerStandObj, resting on its top (d2 = 2). */
        Scene standing() {
            p.flags = 0x08; p.stand = 40; p.yv = 0; p.y = 0x00611234;
            object(o -> o.moveByte(ByteField.FLAGS, 0x08));
            return this;
        }

        Gates gates() { return new Gates(debugMode, mini); }
        Globals globals() { return new Globals(invincible, scoreChain, primary, secondary); }
        Result run(Entry entry) { return run(terrain.owner(), entry); }
        Result run(CdSolidObject owner, Entry entry) {
            return owner.run(entry, object(), p.player(), gates(), globals(), pool.capture());
        }
    }

    /** Every player-slot byte the owner reads is explicit constructed ingress. */
    private static final class P {
        int x = 0x01008000, y = 0x00631234, xv = 0x0123, yv = 0x0400, gv = 0, routine = 2, angle = 0x10, flags = 0x02;
        int spr = 0x04, xr = 9, yr = 0x13, anim = 0, prev = 0, jump = 1, charge = 0, lock = 0, stick = 0, ctrl = 0;
        int pri = 0x56, sec = 0x78, tile = 0x0780, priority = 2, hurt = 0, rot = 0, id = 1, push = 0, stand = 0;

        P copy() {
            var c = new P();
            c.x = x; c.y = y; c.xv = xv; c.yv = yv; c.gv = gv; c.routine = routine; c.angle = angle; c.flags = flags;
            c.spr = spr; c.xr = xr; c.yr = yr; c.anim = anim; c.prev = prev; c.jump = jump; c.charge = charge;
            c.lock = lock; c.stick = stick; c.ctrl = ctrl; c.pri = pri; c.sec = sec; c.tile = tile;
            c.priority = priority; c.hurt = hurt; c.rot = rot; c.id = id; c.push = push; c.stand = stand;
            return c;
        }

        Player player() {
            return new Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x, y, xv, yv, gv, routine, angle,
                    flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl, pri, sec), tile, priority, hurt, rot),
                    id, push, stand);
        }
    }

    /**
     * Format-decoder terrain, not an authenticated asset: 42x8 FG cells all chunk 2 (byte offset $200). Collision
     * ID 1 is a full-height floor (angle 0), ID 2 a full-width wall (angle $C0). Words: bit 13 top solidity
     * (floor probes, d5 = $D), bit 14 lrb solidity (walls/ceilings, d5 = $E), $800 X flip, $1000 Y flip.
     */
    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];

        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; ids[2] = 2;
            Arrays.fill(heights, 16, 32, (byte) 16);
            Arrays.fill(widths, 32, 48, (byte) 16);
            angles[2] = (byte) 0xC0;
        }

        void block(int x, int y, int value) { word(chunks, 512 + (y * 16 + x) * 2, value); }
        void row(int y, int value) { for (int x = 0; x < 16; x++) block(x, y, value); }
        void column(int x, int value) { for (int y = 0; y < 16; y++) block(x, y, value); }

        CdSolidObject owner() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
            byte[] animations = new byte[578];
            for (int i = 0; i < 60; i++) word(animations, i * 2, 120);
            animations[122] = (byte) 0xFF;
            byte[] mappings = new byte[398], dplcs = new byte[398];
            for (int i = 0; i < 198; i++) { word(mappings, i * 2, 396); word(dplcs, i * 2, 396); }
            var visual = CdSonicVisualAssets.decode(mappings, dplcs, new byte[69472]);
            byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            var assets = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                    new byte[4], new byte[640], atan, visual);
            return new CdSolidObject(new CdPlayerMovement(terrain, assets));
        }
    }

    private static void word(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8);
        bytes[at + 1] = (byte) value;
    }

    // Bounded opt-in prefix seam. Native format inputs only; all additions SOURCE-ONLY / JAVA UNRUN.
    private static final Set<PlayerField> PREFIX_POSITION = EnumSet.of(CENTRE_X, CENTRE_X_FRACTION,
            CENTRE_Y, CENTRE_Y_FRACTION);

    @Test void prefixFirstUnavailableFloorWordPreservesBothAngleBuffersWithoutInventingAProbeReturn() {
        var s = prefixScene(); s.p.spr = 0x44; prefixCell(s, 1, 0, 0xF9);
        var stop = prefixStopped(s, CdSolidObject.StopSite.FLOOR_PRIMARY);
        var e = s.p.copy(); e.y = 0x00611234;
        prefixValues(s, stop, e, s.globals(), Set.of(ByteField.SOLID_TYPE), PREFIX_POSITION, Set.of(), List.of());
        prefixWordStop(stop, 0x21F2E0, 1, 0xF9, 122,
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS);
    }

    @Test void prefixSecondFootFailureIsIdentifiedEvenWhenTheCompletedFirstFootNeverStoredAnAngle() {
        var s = prefixScene(); s.object(o -> o.moveWord(WordField.X, 0xF8)); s.p.x = 0x00F88000;
        s.p.spr = 0x44; prefixCell(s, 0, 0, 0xF9);
        var stop = prefixStopped(s, CdSolidObject.StopSite.FLOOR_SECONDARY);
        var e = s.p.copy(); e.y = 0x00611234;
        prefixValues(s, stop, e, s.globals(), Set.of(ByteField.SOLID_TYPE), PREFIX_POSITION, Set.of(), List.of());
        prefixWordStop(stop, 0x21F2FC, 0, 0xF9, 122,
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS);
    }

    @Test void prefixCompletedFirstFootRawZeroSurvivesASecondFootFailureWithoutAnAggregateFloorDistance() {
        var s = prefixScene(); s.object(o -> o.moveWord(WordField.X, 0xF8)); s.p.x = 0x00F88000;
        s.p.spr = 0x44; prefixCell(s, 0, 0, 0xF9); prefixBlock(s, 2, 0, 7, 0x2001);
        Arrays.fill(s.terrain.heights, 16, 32, (byte) 1);
        var stop = prefixStopped(s, CdSolidObject.StopSite.FLOOR_SECONDARY);
        var e = s.p.copy(); e.y = 0x00611234;
        prefixValues(s, stop, e, new Globals(0, 4, 0, 0x34), Set.of(ByteField.SOLID_TYPE), PREFIX_POSITION,
                Set.of(GlobalField.PRIMARY_ANGLE), List.of());
        prefixWordStop(stop, 0x21F2FC, 0, 0xF9, 122,
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS);
    }

    @Test void prefixRawOddPrimaryAngleSurvivesAnAdjacentReadFailureInsideTheFirstFoot() {
        var s = prefixScene(); s.object(o -> o.moveWord(WordField.Y, 0x10B)); s.p.y = 0x00EE1234;
        s.p.spr = 0x44; prefixCell(s, 1, 1, 0xF9); prefixBlock(s, 2, 0, 15, 0x2001);
        s.terrain.angles[1] = 3; Arrays.fill(s.terrain.heights, 16, 32, (byte) 0);
        var stop = prefixStopped(s, CdSolidObject.StopSite.FLOOR_PRIMARY);
        var e = s.p.copy(); e.y = 0x00EC1234;
        prefixValues(s, stop, e, new Globals(0, 4, 3, 0x34), Set.of(ByteField.SOLID_TYPE), PREFIX_POSITION,
                Set.of(GlobalField.PRIMARY_ANGLE), List.of());
        prefixWordStop(stop, 0x21F200, 129, 0xF9, 122,
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS);
    }

    @Test void prefixRawSecondaryAngleSurvivesAnAdjacentFailureAfterAnAngleFreePrimaryFoot() {
        var s = prefixScene(); s.object(o -> o.moveWord(WordField.X, 0xF8).moveWord(WordField.Y, 0x10B));
        s.p.x = 0x00F88000; s.p.y = 0x00EE1234; s.p.spr = 0x44;
        prefixCell(s, 0, 1, 0xF9); prefixBlock(s, 2, 14, 15, 0x2001);
        s.terrain.angles[1] = 3; Arrays.fill(s.terrain.heights, 16, 32, (byte) 0);
        var stop = prefixStopped(s, CdSolidObject.StopSite.FLOOR_SECONDARY);
        var e = s.p.copy(); e.y = 0x00EC1234;
        prefixValues(s, stop, e, new Globals(0, 4, 0x12, 3), Set.of(ByteField.SOLID_TYPE), PREFIX_POSITION,
                Set.of(GlobalField.SECONDARY_ANGLE), List.of());
        prefixWordStop(stop, 0x21F21C, 128, 0xF9, 122,
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS);
    }

    @Test void prefixRoomSeedsBothBuffersThenRetainsTheRawWallAngleBeforeItsAdjacentReadFails() {
        var s = prefixScene(); s.object(o -> o.moveWord(WordField.X, 0xF4).moveWord(WordField.X_VELOCITY, 0x100));
        s.p.x = 0x00F40000; s.p.xv = 0; s.p.yv = 0; s.p.spr = 0x44;
        prefixCell(s, 1, 0, 0xF9); prefixBlock(s, 2, 15, 6, 0x4001); s.terrain.angles[1] = 3;
        var stop = prefixStopped(s, CdSolidObject.StopSite.ROOM_PRIMARY);
        var e = s.p.copy(); e.x = 0x00F50000; e.y = 0x00611234;
        prefixValues(s, stop, e, new Globals(0, 4, 3, 0xC0), Set.of(ByteField.SOLID_TYPE),
                Set.of(CENTRE_X, CENTRE_X_FRACTION, CENTRE_Y),
                Set.of(GlobalField.PRIMARY_ANGLE, GlobalField.SECONDARY_ANGLE), List.of());
        prefixWordStop(stop, 0x21F2C0, 1, 0xF9, 122,
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS);
    }

    @Test void prefixLeftRoomFailureRetainsItsSameValueSeedsButNotTheLaterYLongMask() {
        var s = prefixScene(); s.object(o -> o.moveWord(WordField.X, 0x10B).moveWord(WordField.X_VELOCITY, -0x100));
        s.p.x = 0x010B0000; s.p.xv = 0; s.p.yv = 0; s.p.spr = 0x44; s.primary = s.secondary = 0x40;
        prefixCell(s, 1, 0, 0xF9);
        var stop = prefixStopped(s, CdSolidObject.StopSite.ROOM_PRIMARY);
        var e = s.p.copy(); e.x = 0x010A0000; e.y = 0x00611234;
        prefixValues(s, stop, e, s.globals(), Set.of(ByteField.SOLID_TYPE),
                Set.of(CENTRE_X, CENTRE_X_FRACTION, CENTRE_Y),
                Set.of(GlobalField.PRIMARY_ANGLE, GlobalField.SECONDARY_ANGLE), List.of());
        prefixWordStop(stop, 0x21F2C0, 1, 0xF9, 122,
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS);
    }

    @Test void prefixCeilingRetainsItsRawOddAngleBeforeItsOwnAdjacentFailureWithoutKillOrGetOff() {
        var s = prefixScene(); s.object(o -> o.moveWord(WordField.Y, 0x12A).moveWord(WordField.Y_VELOCITY, -0x100));
        s.p.y = 0x010D1234; s.p.yv = 0; s.p.spr = 0x44;
        prefixCell(s, 1, 0, 0xF9); prefixBlock(s, 2, 0, 0, 0x4001);
        s.terrain.angles[1] = 3; Arrays.fill(s.terrain.heights, 16, 32, (byte) 0);
        var stop = prefixStopped(s, CdSolidObject.StopSite.CEILING_PRIMARY);
        var e = s.p.copy(); e.y = 0x010A1234;
        prefixValues(s, stop, e, new Globals(0, 4, 3, 0x34), Set.of(ByteField.SOLID_TYPE), PREFIX_POSITION,
                Set.of(GlobalField.PRIMARY_ANGLE), List.of());
        prefixWordStop(stop, 0x21F3E0, 1, 0xF9, 122,
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS);
    }

    @Test void prefixUnavailableAlternateCollisionIdIsTypedAndStopsBeforeAnyAngleWrite() {
        var s = prefixScene(); s.p.spr = 0x44; prefixCell(s, 1, 0, 0x82); prefixBlock(s, 3, 0, 7, 0x2384);
        var stop = prefixStopped(s, CdSolidObject.StopSite.FLOOR_PRIMARY);
        var e = s.p.copy(); e.y = 0x00611234;
        prefixValues(s, stop, e, s.globals(), Set.of(ByteField.SOLID_TYPE), PREFIX_POSITION, Set.of(), List.of());
        assertEquals(new CdSolidObject.CollisionId(900), stop.stop().detail());
        assertEquals("Collision ID requires unavailable resident bytes: 900", stop.stop().originalException().getMessage());
    }

    @Test void prefixFlaggedZeroChunkHasTheActualOddUnderflowAddressAdmittedByThePublicDecoder() {
        var s = prefixScene(); prefixCell(s, 1, 0, 0x80);
        var stop = prefixStopped(s, CdSolidObject.StopSite.FLOOR_PRIMARY);
        var e = s.p.copy(); e.y = 0x00611234;
        prefixValues(s, stop, e, s.globals(), Set.of(ByteField.SOLID_TYPE), PREFIX_POSITION, Set.of(), List.of());
        prefixWordStop(stop, 0x21FEE1, 1, 0x80, 0,
                com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.ODD_NATIVE_WORD_ADDRESS);
    }

    @ParameterizedTest @CsvSource({"1,RESERVED_SLOT_ADDRESS", "5,RESERVED_SLOT_ADDRESS", "31,RESERVED_SLOT_ADDRESS",
            "128,OUT_OF_POOL_ADDRESS", "255,OUT_OF_POOL_ADDRESS"})
    void prefixStandReleaseStopsAtTheActualAddressAfterJumpAndFlagsButBeforeSetInteract(int stand,
                                                                                       UnsupportedBoundary boundary) {
        var s = prefixScene(); s.p.flags = 8; s.p.stand = stand; s.p.yv = 0;
        var stop = prefixStopped(s, CdSolidObject.StopSite.STAND_RELEASE);
        var e = s.p.copy(); e.y = 0x00611234; e.jump = 0;
        prefixValues(s, stop, e, s.globals(), Set.of(ByteField.SOLID_TYPE, ByteField.FLAGS), CARRY, Set.of(),
                List.of(new FloorDistance(27, NONE, NONE), call(Routine.STAND_ON_OBJECT)));
        assertEquals(new CdSolidObject.SlotAddress(boundary, stand, Routine.STAND_ON_OBJECT), stop.stop().detail());
        var original = assertInstanceOf(UnsupportedSolidObject.class, stop.stop().originalException());
        assertEquals(boundary, original.boundary()); assertEquals(stand, original.slotByte());
        assertEquals(Routine.STAND_ON_OBJECT, original.routine());
        assertEquals(boundary + " in STAND_ON_OBJECT: slot byte $" + Integer.toHexString(stand) + " addresses $"
                + Integer.toHexString((0xFFD000 + stand * 0x40) & 0xFFFFFF), original.getMessage());
    }

    @ParameterizedTest @CsvSource({"0,9,19,92", "1,5,10,95"})
    void prefixHurtRolledStandFailureKeepsTheReachedResizeHurtAndAnimationWrites(int mini, int xr, int yr, int y) {
        var s = prefixScene(); s.mini = mini; s.p.flags = 0x0C; s.p.routine = 4; s.p.hurt = 7;
        s.p.stand = 5; s.p.yv = 0; s.p.anim = 2;
        var stop = prefixStopped(s, CdSolidObject.StopSite.STAND_RELEASE);
        var e = s.p.copy(); e.y = (y << 16) | 0x1234; e.routine = 2; e.hurt = 120; e.flags = 8; e.jump = 0;
        e.xr = xr; e.yr = yr; e.anim = 0;
        prefixValues(s, stop, e, s.globals(), Set.of(ByteField.SOLID_TYPE, ByteField.FLAGS),
                with(CARRY, ROUTINE, HURT, X_RADIUS, Y_RADIUS, ANIMATION), Set.of(),
                List.of(new FloorDistance(27, NONE, NONE), call(Routine.STAND_ON_OBJECT)));
    }

    @Test void prefixStandFailureKeepsEarlierCompletedRoomAndFloorObservationsInOrder() {
        var s = prefixScene(); s.p.flags = 8; s.p.stand = 128; s.p.yv = 0; s.p.xv = 0;
        s.object(o -> o.moveWord(WordField.X_VELOCITY, 0x100));
        var stop = prefixStopped(s, CdSolidObject.StopSite.STAND_RELEASE);
        var e = s.p.copy(); e.x = 0x01018000; e.y = 0x00611234; e.jump = 0;
        prefixValues(s, stop, e, new Globals(0, 4, 0xC0, 0xC0), Set.of(ByteField.SOLID_TYPE, ByteField.FLAGS), CARRY,
                Set.of(GlobalField.PRIMARY_ANGLE, GlobalField.SECONDARY_ANGLE),
                List.of(new RoomInFront(0xC0, 20, OptionalInt.of(0xC0), OptionalInt.of(0xC0)),
                        new FloorDistance(27, NONE, NONE), call(Routine.STAND_ON_OBJECT)));
    }

    @ParameterizedTest @ValueSource(strings = {"landing", "carry", "right", "left", "floor", "ceiling", "kill",
            "rolledKill", "miniKill", "alias0", "free32", "reused127", "full", "getOff", "wrongGetOff",
            "offscreen", "debug", "dead", "rising", "below", "side"})
    void prefixCompletedContainsTheGenuineLegacyResultIncludingMasksCallsAndOrderedFm(String vector) {
        var s = prefixScene(); Entry entry = Entry.TOP_SOLID_OBJECT;
        switch (vector) {
            case "carry" -> s.standing();
            case "right", "left" -> {
                s.standing(); s.p.xv = 0;
                s.object(o -> o.moveWord(WordField.X_VELOCITY, vector.equals("right") ? 0x100 : -0x100));
            }
            case "floor" -> { s.terrain.row(6, 0x6001); s.object(o -> o.moveWord(WordField.Y, 0x66)); s.p.y = 0x00471234; }
            case "ceiling", "kill", "rolledKill", "miniKill" -> {
                s.standing(); s.object(o -> o.moveWord(WordField.Y, 0x180).moveWord(WordField.Y_VELOCITY, -0x100));
                s.p.y = 0x01611234;
                if (!vector.equals("ceiling")) { s.terrain.row(5, 0x5001); s.p.ctrl = 1; s.p.charge = 0x1E; s.invincible = 1; }
                if (vector.endsWith("Kill")) s.p.flags = 0x0C;
                if (vector.equals("miniKill")) s.mini = 1;
            }
            case "alias0" -> { s.p.flags = 8; s.p.stand = 0; s.p.yv = 0; }
            case "free32", "reused127" -> {
                int index = vector.equals("free32") ? 32 : 127; s.p.flags = 8; s.p.stand = index; s.p.yv = 0;
                s.other(index, o -> o.moveByte(ByteField.ID, 0x20).moveByte(ByteField.FLAGS, 0xAD)
                        .moveWord(WordField.X_FRACTION, 0xABCD).moveWord(WordField.Y_FRACTION, 0x7654)
                        .moveLong(CdObjectState.LongField.CHECKPOINT_PARENT, 0x13579BDF)
                        .moveWord(WordField.EXPLOSION_POINTS_FRAME, 0xCAFE));
                if (index == 32) s.pool.objNull(index); // ID zero does not erase physical RAM or release ownership.
            }
            case "full" -> { for (int i = 32; i < 128; i++) s.other(i, o -> o.moveByte(ByteField.ID, 0x20)); }
            case "getOff", "wrongGetOff" -> {
                s.standing(); s.p.charge = 1; s.p.stick = 0x55; s.p.ctrl = 1; entry = Entry.GET_OFF_OBJECT;
                if (vector.equals("wrongGetOff")) s.p.stand = 255;
            }
            case "offscreen" -> s.object(o -> o.moveByte(ByteField.SPRITE_FLAGS, 4));
            case "debug" -> s.debugMode = 0x100;
            case "dead" -> s.p.routine = 6;
            case "rising" -> s.p.yv = -1;
            case "below" -> { s.p.y = 0x009F1234; s.p.yv = 0; }
            case "side" -> { s.p.x = 0x00E88000; s.p.y = 0x00801234; s.p.yv = 0; }
            case "landing" -> { }
            default -> fail(vector);
        }
        var cut = s.pool.capture(); var owner = s.terrain.owner();
        var legacy = owner.run(entry, s.object(), s.p.player(), s.gates(), s.globals(), cut);
        var completed = assertInstanceOf(CdSolidObject.Completed.class,
                owner.runWithPrefix(entry, s.object(), s.p.player(), s.gates(), s.globals(), cut));
        assertEquals(legacy, completed.completedResult()); assertEquals(cut.slots(), s.pool.capture().slots());
        if (vector.equals("alias0")) { assertEquals(0, legacy.player().actor().motion().flags()); assertEquals(List.of(), legacy.otherSlots()); }
        if (vector.equals("free32") || vector.equals("reused127")) {
            int index = vector.equals("free32") ? 32 : 127;
            assertEquals(List.of(s.pool.state(index).moveByte(ByteField.FLAGS, 0xA5)), legacy.otherSlots());
            assertArrayEquals(s.pool.state(index).moveByte(ByteField.FLAGS, 0xA5).copyBytes(), legacy.otherSlots().getFirst().copyBytes());
        }
        if (vector.equals("full")) assertTrue(s.pool.findObjSlot().isEmpty(), "Capacity exhaustion is normal, never a stop");
        if (vector.endsWith("Kill")) assertEquals(List.of(FmSound.FM_CHARGESTOP, FmSound.FM_HURT, FmSound.FM_CHARGESTOP), legacy.fmRequests());
    }

    @Test void prefixDiscardLeavesTheOwnerStatelessAndTheNextCallStartsAtTheEntryAgain() {
        var s = prefixScene(); s.p.spr = 0x44; prefixCell(s, 1, 0, 0xF9);
        var owner = s.terrain.owner(); var cut = s.pool.capture();
        var first = assertInstanceOf(CdSolidObject.NativeStopped.class,
                owner.runWithPrefix(Entry.TOP_SOLID_OBJECT, s.object(), s.p.player(), s.gates(), s.globals(), cut));
        var second = assertInstanceOf(CdSolidObject.NativeStopped.class,
                owner.runWithPrefix(Entry.TOP_SOLID_OBJECT, s.object(), s.p.player(), s.gates(), s.globals(), cut));
        assertEquals(first.prefix(), second.prefix()); assertEquals(first.stop().site(), second.stop().site());
        assertNotSame(first.stop().originalException(), second.stop().originalException());
        var fresh = s.p.copy(); fresh.spr = 4;
        var expected = owner.run(Entry.TOP_SOLID_OBJECT, s.object(), fresh.player(), s.gates(), s.globals(), cut);
        assertEquals(expected, assertInstanceOf(CdSolidObject.Completed.class,
                owner.runWithPrefix(Entry.TOP_SOLID_OBJECT, s.object(), fresh.player(), s.gates(), s.globals(), cut)).completedResult());
        assertEquals(cut.slots(), s.pool.capture().slots());
    }

    @ParameterizedTest @ValueSource(strings = {"SOLID_OBJECT", "BTM_SOLID_OBJECT", "STAND_ON_OBJECT"})
    void prefixUnsupportedEntriesRejectBeforeTouchingAnyOtherInput(String entry) {
        var owner = new Scene().terrain.owner();
        assertThrows(IllegalArgumentException.class,
                () -> owner.runWithPrefix(Entry.valueOf(entry), null, null, null, null, null));
    }

    @Test void prefixHostInputMisuseRemainsAHostExceptionAndNeverBecomesANativeOutcome() {
        var s = prefixScene(); var owner = s.terrain.owner(); var cut = s.pool.capture();
        assertThrows(NullPointerException.class, () -> owner.runWithPrefix(null, s.object(), s.p.player(), s.gates(), s.globals(), cut));
        assertThrows(NullPointerException.class, () -> owner.runWithPrefix(Entry.TOP_SOLID_OBJECT, null, s.p.player(), s.gates(), s.globals(), cut));
        assertThrows(NullPointerException.class, () -> owner.runWithPrefix(Entry.TOP_SOLID_OBJECT, s.object(), null, s.gates(), s.globals(), cut));
        assertThrows(NullPointerException.class, () -> owner.runWithPrefix(Entry.TOP_SOLID_OBJECT, s.object(), s.p.player(), null, s.globals(), cut));
        assertThrows(NullPointerException.class, () -> owner.runWithPrefix(Entry.TOP_SOLID_OBJECT, s.object(), s.p.player(), s.gates(), null, cut));
        assertThrows(NullPointerException.class, () -> owner.runWithPrefix(Entry.TOP_SOLID_OBJECT, s.object(), s.p.player(), s.gates(), s.globals(), null));
        assertEquals(cut.slots(), s.pool.capture().slots());
    }

    @Test void prefixRecordsDefensivelyCopyMetadataAndKeepTheExactOriginalExceptionReference() {
        var s = prefixScene(); var other = s.pool.state(41);
        var others = new java.util.ArrayList<>(List.of(other));
        var objects = EnumSet.of(ByteField.SOLID_TYPE); var players = EnumSet.of(CENTRE_Y);
        var globals = EnumSet.of(GlobalField.PRIMARY_ANGLE); var calls = new java.util.ArrayList<Call>();
        calls.add(call(Routine.STAND_ON_OBJECT));
        var prefix = new CdSolidObject.Prefix(Entry.TOP_SOLID_OBJECT, s.object(), s.p.player(), s.globals(),
                others, objects, players, globals, calls);
        others.clear(); objects.clear(); players.clear(); globals.clear(); calls.clear();
        assertEquals(List.of(other), prefix.otherSlots()); assertEquals(Set.of(ByteField.SOLID_TYPE), prefix.objectWrites());
        assertEquals(Set.of(CENTRE_Y), prefix.playerWrites()); assertEquals(Set.of(GlobalField.PRIMARY_ANGLE), prefix.globalWrites());
        assertEquals(List.of(call(Routine.STAND_ON_OBJECT)), prefix.calls());
        assertThrows(UnsupportedOperationException.class, () -> prefix.otherSlots().clear());
        assertThrows(UnsupportedOperationException.class, () -> prefix.objectWrites().clear());
        assertThrows(UnsupportedOperationException.class, () -> prefix.playerWrites().clear());
        assertThrows(UnsupportedOperationException.class, () -> prefix.globalWrites().clear());
        assertThrows(UnsupportedOperationException.class, () -> prefix.calls().clear());
        assertThrows(IllegalArgumentException.class, () -> new CdSolidObject.Prefix(prefix.entry(), prefix.object(), prefix.player(),
                prefix.globals(), List.of(prefix.object()), Set.of(), Set.of(), Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CdSolidObject.Prefix(prefix.entry(), prefix.object(), prefix.player(),
                prefix.globals(), List.of(other, other), Set.of(), Set.of(), Set.of(), List.of()));
        var original = new IllegalStateException("actual owner instance");
        var stop = new CdSolidObject.NativeStop(CdSolidObject.StopSite.FLOOR_PRIMARY, new CdSolidObject.CollisionId(900), original);
        var outcome = new CdSolidObject.NativeStopped(prefix, stop);
        assertSame(original, outcome.stop().originalException()); assertSame(prefix, outcome.prefix()); assertSame(stop, outcome.stop());
    }

    @Test void prefixFmRequestsFlattenOnlyCompletedCallMetadataInItsExistingOrder() {
        var s = risingPlatform(); s.p.flags = 0x0C; s.p.charge = 1; s.terrain.row(5, 0x5001);
        var cut = s.pool.capture(); var completed = s.run(Entry.TOP_SOLID_OBJECT);
        var prefix = new CdSolidObject.Prefix(completed.entry(), completed.object(), completed.player(), completed.globals(),
                completed.otherSlots(), completed.objectWrites(), completed.playerWrites(), completed.globalWrites(), completed.calls());
        assertEquals(List.of(FmSound.FM_CHARGESTOP, FmSound.FM_HURT, FmSound.FM_CHARGESTOP), prefix.fmRequests());
        assertEquals(completed.fmRequests(), prefix.fmRequests());
        assertThrows(UnsupportedOperationException.class, () -> prefix.fmRequests().clear());
        assertEquals(cut.slots(), s.pool.capture().slots());
    }

    private static Scene prefixScene() {
        var s = new Scene(); s.object(o -> o.moveByte(ByteField.ID, 0x20).moveByte(ByteField.SUBTYPE, 0xC0)); return s;
    }
    private static void prefixCell(Scene s, int x, int y, int value) { s.terrain.fg[2 + 42 * y + x] = (byte) value; }
    private static void prefixBlock(Scene s, int chunk, int x, int y, int value) {
        word(s.terrain.chunks, (chunk - 1) * 512 + y * 32 + x * 2, value);
    }
    private static CdSolidObject.NativeStopped prefixStopped(Scene s, CdSolidObject.StopSite site) {
        var owner = s.terrain.owner(); var cut = s.pool.capture(); var object = s.object(); var player = s.p.player();
        var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class,
                owner.runWithPrefix(Entry.TOP_SOLID_OBJECT, object, player, s.gates(), s.globals(), cut));
        assertEquals(site, stopped.stop().site()); assertEquals(Entry.TOP_SOLID_OBJECT, stopped.prefix().entry());
        assertEquals(List.of(), stopped.prefix().otherSlots()); assertEquals(List.of(), stopped.prefix().fmRequests());
        assertEquals(CdObjectPool.DYNAMIC_COUNT, cut.slots().size()); assertEquals(cut.slots(), s.pool.capture().slots());
        for (int i = 0; i < 96; i++) assertArrayEquals(cut.slots().get(i).copyBytes(), s.pool.state(32 + i).copyBytes());
        var legacy = assertThrows(stopped.stop().originalException().getClass(),
                () -> owner.run(Entry.TOP_SOLID_OBJECT, object, player, s.gates(), s.globals(), cut));
        assertEquals(stopped.stop().originalException().getMessage(), legacy.getMessage());
        assertEquals(cut.slots(), s.pool.capture().slots()); return stopped;
    }
    private static void prefixValues(Scene s, CdSolidObject.NativeStopped stopped, P expectedPlayer, Globals expectedGlobals,
                                     Set<ByteField> objects, Set<PlayerField> players, Set<GlobalField> globals, List<Call> calls) {
        var p = stopped.prefix(); var expectedObject = s.object().moveByte(ByteField.SOLID_TYPE,
                stopped.stop().site() == CdSolidObject.StopSite.STAND_RELEASE ? 0 : 1);
        if (stopped.stop().site() == CdSolidObject.StopSite.STAND_RELEASE) expectedObject = expectedObject.moveByte(ByteField.FLAGS, expectedObject.flags() | 8);
        assertEquals(expectedObject, p.object()); assertArrayEquals(expectedObject.copyBytes(), p.object().copyBytes());
        assertEquals(expectedPlayer.player(), p.player()); assertEquals(expectedGlobals, p.globals());
        assertEquals(objects, p.objectWrites()); assertEquals(players, p.playerWrites());
        assertEquals(globals, p.globalWrites()); assertEquals(calls, p.calls());
    }
    private static void prefixWordStop(CdSolidObject.NativeStopped stop, int address, int layoutOffset, int layoutByte,
                                       int effectiveChunk, com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason reason) {
        var detail = assertInstanceOf(CdSolidObject.TerrainWord.class, stop.stop().detail());
        assertEquals(new com.openggf.game.soniccd.level.CdTerrainCollision.Address(layoutOffset, layoutByte, effectiveChunk,
                address & 0xFFFF), detail.address());
        assertEquals(address, detail.address().nativeAddress()); assertEquals(reason, detail.reason());
        assertEquals(IllegalStateException.class, stop.stop().originalException().getClass());
        assertEquals("Unavailable native collision word at $" + Integer.toHexString(address) + ": " + reason,
                stop.stop().originalException().getMessage());
    }
}
