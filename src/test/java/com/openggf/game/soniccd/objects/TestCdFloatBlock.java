package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdFloatBlock.CallReturned;
import com.openggf.game.soniccd.objects.CdFloatBlock.DeleteSite;
import com.openggf.game.soniccd.objects.CdFloatBlock.Deleted;
import com.openggf.game.soniccd.objects.CdFloatBlock.NativeCall;
import com.openggf.game.soniccd.objects.CdFloatBlock.NativeRoutine;
import com.openggf.game.soniccd.objects.CdFloatBlock.RoutineEntered;
import com.openggf.game.soniccd.objects.CdFloatBlock.TopSolid;
import com.openggf.game.soniccd.objects.CdFloatBlock.TopSolidCall;
import com.openggf.game.soniccd.objects.CdFloatBlock.UnsupportedBoundary;
import com.openggf.game.soniccd.objects.CdFloatBlock.UnsupportedFloatBlock;
import com.openggf.game.soniccd.objects.CdSolidObject.GetOffReason;
import com.openggf.game.soniccd.objects.CdSolidObject.PlayerField;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the production port; every case is UNRUN by the external author. ObjFloatBlock ($12),
 * Level/_Objects/Floating Block/Main.asm:7-176 at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, with
 * SetObjectTileID (Object Tile ID Set.asm:7-17), AnimateObject (Object Animate.asm:14-95), DrawObject/DeleteObject
 * (Object Functions.asm:137-196,228-237) and TopSolidObject (Object Solid.asm:174-479) through the shared
 * CdSolidObject owner. Fixtures: Ani_FloatBlock/MapSpr_FloatBlock are the two includes assembled from the pinned
 * source (their SHA-256 equal the authenticated USA views); ObjectTileIDs, the oMap long and the tile word are
 * constructed markers, so no literal here can stand in for an authenticated value. Real probes run over the same
 * constructed format-decoder terrain as TestCdSolidObject (every FG cell chunk 2, open air). The commit stand-in
 * plays Root's S3/S4 role: CAS against the call inputs and call cut, then publish exactly the Result. The single
 * retail case reads supplied media only when soniccd.media.root is configured.
 */
class TestCdFloatBlock {
    private static final int WHOLE = 40;
    private static final int CUT_INDEX = WHOLE - CdObjectPool.FIRST_WHOLE_INDEX;
    /** Constructed markers, deliberately unlike the real $20A3FA / $4334, so only the bound assets can supply them. */
    private static final int MAP = 0x00FB1012;
    private static final int TILE_WORD = 0x5A3C;
    private static final OptionalInt NONE = OptionalInt.empty();
    /** Ani_FloatBlock (Data/Animations.asm): words 6/10/20; [2,5,FF]+pad, [2,1,5,2,5,3,5,4,5,FC], [2,1,0,2,0,3,0,4,0,FC]. */
    private static final byte[] ANI = bytes(
            0x00, 0x06, 0x00, 0x0A, 0x00, 0x14,
            0x02, 0x05, 0xFF, 0x00,
            0x02, 0x01, 0x05, 0x02, 0x05, 0x03, 0x05, 0x04, 0x05, 0xFC,
            0x02, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x04, 0x00, 0xFC);
    /** MapSpr_FloatBlock (Data/Mappings.asm): six frames, real zero pads after frames 0, 3 and 4. */
    private static final byte[] MAPS = bytes(
            0x00, 0x0C, 0x00, 0x0E, 0x00, 0x1E, 0x00, 0x2E, 0x00, 0x3A, 0x00, 0x46,
            0x00, 0x00,
            0x03, 0xF4, 0x09, 0x00, 0x00, 0xF4, 0x04, 0x00, 0x00, 0x00, 0xFC, 0x04, 0x00, 0x00, 0x00, 0x04,
            0x03, 0xF4, 0x09, 0x08, 0x00, 0xF4, 0x04, 0x00, 0x08, 0x00, 0xF4, 0x04, 0x00, 0x08, 0x00, 0xFC,
            0x02, 0xF4, 0x09, 0x18, 0x00, 0xF4, 0x04, 0x00, 0x18, 0x00, 0xFC, 0x00,
            0x02, 0xF4, 0x09, 0x10, 0x00, 0xF4, 0x04, 0x00, 0x10, 0x00, 0xFC, 0x00,
            0x01, 0xF4, 0x0A, 0x00, 0x06, 0xF4);
    private static final byte[] TILE_IDS = tileIds();
    /** A first vertical landing: both centre longs, StandOnObject's jump/flags and .SetInteract writes. */
    private static final Set<PlayerField> LANDING = EnumSet.of(PlayerField.CENTRE_X, PlayerField.CENTRE_X_FRACTION,
            PlayerField.CENTRE_Y, PlayerField.CENTRE_Y_FRACTION, PlayerField.PLAYER_JUMP, PlayerField.FLAGS,
            PlayerField.STAND_OBJECT, PlayerField.ANGLE, PlayerField.Y_VELOCITY, PlayerField.GROUND_VELOCITY);
    /** A continuing stand: .MoveOnObject longs, CLR.B oPlayerJump and the BSET #3 that finds us recorded. */
    private static final Set<PlayerField> CARRY = EnumSet.of(PlayerField.CENTRE_X, PlayerField.CENTRE_X_FRACTION,
            PlayerField.CENTRE_Y, PlayerField.CENTRE_Y_FRACTION, PlayerField.PLAYER_JUMP, PlayerField.FLAGS);
    /** GetOffObject's release of its own standing player (Object Solid.asm:38-49). */
    private static final Set<PlayerField> DETACH = EnumSet.of(PlayerField.STICK, PlayerField.FLAGS,
            PlayerField.PLAYER_CONTROL, PlayerField.STAND_OBJECT);

    // ----------------------------------------------------------------------------------------------------------
    // Init, Main and the Present appear/visible/vanish cycle.
    // ----------------------------------------------------------------------------------------------------------

    @Test void initFallsIntoMainAndThePresentHidesANeverDrawnBlockAfterTwoIdenticalGetOffCalls() {
        var h = new Harness();
        h.draw.onScreen = false; // Far from the camera: DrawObject's entry BCLR #7 and no later DrawDone.
        // SpawnObject's writes for an X-flipped row over dirty reused bytes (Object Spawner.asm:267-319).
        h.edit(o -> o.moveByte(ID, 0x12).moveByte(SPRITE_FLAGS, 0x01).moveByte(FLAGS, 0x01).moveWord(X, 0x0100)
                .moveWord(Y, 0x0080).moveWord(X_FRACTION, 0x4321).moveByte(ANGLE, 0x5A).moveByte(ANIMATION_TIME, 7));
        var before = h.slot();
        var result = h.run(present(0x0080));

        // Init (:39-48): ORI.B keeps the flip, both literals come from the bound assets, frame 5; then Main.
        var initialised = before.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x05).moveLong(MAPPINGS, MAP)
                .moveWord(TILE, TILE_WORD).moveByte(PRIORITY, 1).moveByte(WIDTH, 0x0C).moveByte(Y_RADIUS, 0x0C)
                .moveByte(MAP_FRAME, 5);
        var first = h.commit.calls.get(0);
        var r0 = first.result();
        assertEquals(initialised, first.callCut().slots().get(CUT_INDEX), "every Init write is live at the call cut");
        assertEquals(initialised, r0.object(), "TOP wrote oSolidType 1 and .GetOff cleared it again");
        assertEquals(Optional.of(GetOffReason.OBJECT_OFFSCREEN), r0.getOffReason()); // TST.B oSprFlags(a0) (:197-198)
        assertEquals(Set.of(), r0.playerWrites());
        assertEquals(EnumSet.of(SOLID_TYPE), r0.objectWrites());
        assertEquals(List.of(new CdSolidObject.RoutineCall(CdSolidObject.Routine.STOP_OBJ_PUSH),
                new CdSolidObject.RoutineCall(CdSolidObject.Routine.GET_OFF_OBJECT)), r0.calls());
        // BSR.W .Check then the fall-through into .Check: the same a1, the threaded result, the identical call.
        assertEquals(List.of(new RoutineEntered(NativeRoutine.INIT), new RoutineEntered(NativeRoutine.MAIN),
                new TopSolid(0, r0), new TopSolid(1, r0), new CallReturned(NativeCall.DRAW_OBJECT)), result.effects());
        assertEquals(r0.player(), h.commit.calls.get(1).player());
        assertEquals(r0.globals(), h.commit.calls.get(1).globals());
        assertEquals(r0.object(), h.commit.calls.get(1).callCut().slots().get(CUT_INDEX));

        // Main's Present branch writes the invisible frame 0 after the solidity call (:67-68), then DrawObject.
        var expected = initialised.moveByte(MAP_FRAME, 0);
        assertEquals(List.of(expected), h.draw.observed);
        assertEquals(expected, result.state());
        assertFalse(result.deleted());
        assertEquals(new P().player(), h.player, "the falling player was never touched");
    }

    @Test void presentLandingStandsTwiceThenSchedulesTheAppearRoutineAndDraws() {
        var h = new Harness();
        h.edit(o -> initialised(o, 2, 0x84, 0)); // Drawn on screen last frame: sprite bit 7 set.
        var before = h.slot();
        var result = h.run(present(0x0080));
        assertEquals(List.of("MAIN", "TS0:STAND_ON", "TS1:STAND_ON", "DRAW_OBJECT"), trace(result));

        var first = h.commit.calls.get(0).result();
        var second = h.commit.calls.get(1).result();
        // Y := oY(a0) - ($C + $13) = $61 keeps the $1234 fraction; .SetInteract stores the WHOLE index 40.
        assertEquals(landed().player(), first.player());
        assertEquals(LANDING, first.playerWrites());
        assertEquals(before.moveByte(FLAGS, 0x08), first.object());
        assertEquals(EnumSet.of(SOLID_TYPE, FLAGS), first.objectWrites());
        assertEquals(List.of(new CdSolidObject.FloorDistance(27, NONE, NONE),
                new CdSolidObject.RoutineCall(CdSolidObject.Routine.STAND_ON_OBJECT)), first.calls());
        assertEquals(OptionalInt.of(1), first.d0());
        // The second .Check starts from the threaded first result and only continues the stand.
        assertEquals(first.player(), h.commit.calls.get(1).player());
        assertEquals(first.object(), h.commit.calls.get(1).callCut().slots().get(CUT_INDEX));
        assertEquals(first.player(), second.player());
        assertEquals(first.object(), second.object());
        assertEquals(CARRY, second.playerWrites());

        // .Appear: frame 0, then routine 6 and animation 1 because bit 3 is now set (:67-72), then DrawObject.
        var drawn = second.object().moveByte(MAP_FRAME, 0).moveByte(ROUTINE, 6).moveByte(ANIMATION, 1);
        assertEquals(List.of(drawn), h.draw.observed);
        assertEquals(drawn, result.state());
        assertEquals(landed().player(), h.player);
    }

    @Test void appearFlickersThroughScriptOneAndItsFcAdvancesToVisibleOnTheTwentyFifthDispatch() {
        var h = standingOn(6, 1);
        int[] frames = {1, 1, 1, 5, 5, 5, 2, 2, 2, 5, 5, 5, 3, 3, 3, 5, 5, 5, 4, 4, 4, 5, 5, 5, 5};
        for (int i = 0; i < frames.length; i++) {
            var result = h.run(present(0x0080));
            String at = "dispatch " + (i + 1);
            assertEquals(List.of("APPEAR", "TS0:STAND_ON", "TS1:STAND_ON", "ANIMATE_OBJECT", "DRAW_OBJECT"),
                    trace(result), at);
            assertEquals(frames[i], result.state().unsignedByte(MAP_FRAME), at);
            assertEquals(i < 24 ? 6 : 8, result.state().routine(), at); // $FC: ADDQ.B #2,oRoutine (Animate.asm:78-81)
        }
        var s = h.slot();
        assertEquals(1, s.unsignedByte(ANIMATION));
        assertEquals(1, s.unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(8, s.unsignedByte(ANIMATION_FRAME), "$FC leaves the script index on its terminator");
        assertEquals(2, s.unsignedByte(ANIMATION_TIME), "$FC still reloaded the delay byte");
        assertEquals(0x84, s.spriteFlags());
        assertEquals(50, h.commit.calls.size());
        assertEquals(25, h.draw.observed.size());
        assertEquals(landed().player(), h.player, "a resting stand never moves the player");
    }

    @Test void visibleRestartsTheSteadyScriptWhileStoodOnThenStartsVanishingUndrawnOnRelease() {
        var h = standingOn(8, 1);
        h.edit(o -> o.moveByte(PREVIOUS_ANIMATION, 1).moveByte(ANIMATION_FRAME, 8).moveByte(ANIMATION_TIME, 2)
                .moveByte(MAP_FRAME, 5));
        var held = h.run(present(0x0080));
        assertEquals(List.of("VISIBLE", "TS0:STAND_ON", "TS1:STAND_ON", "ANIMATE_OBJECT", "DRAW_OBJECT"), trace(held));
        // MOVE.B #0,oAnim(a0) precedes the solidity call (:119-120).
        assertEquals(0, h.commit.calls.get(0).callCut().slots().get(CUT_INDEX).unsignedByte(ANIMATION));
        var s = held.state();
        assertEquals(List.of(8, 0, 0, 1, 2, 5), List.of(s.routine(), s.unsignedByte(ANIMATION),
                s.unsignedByte(PREVIOUS_ANIMATION), s.unsignedByte(ANIMATION_FRAME), s.unsignedByte(ANIMATION_TIME),
                s.unsignedByte(MAP_FRAME)));

        // The player walks right off: the first .Check releases both bit 3s, the second finds nothing to release.
        var walking = landed(); walking.x = 0x01308000;
        h.player = walking.player();
        int draws = h.draw.observed.size();
        var released = h.run(present(0x0080));
        assertEquals(List.of("VISIBLE", "TS0:GET_OFF/X_RANGE_RIGHT", "TS1:GET_OFF/X_RANGE_RIGHT"), trace(released));
        var r0 = h.commit.calls.get(2).result();
        var detached = walking.copy(); detached.flags = 0x02; detached.stand = 0; detached.stick = 0; detached.ctrl = 0;
        assertEquals(detached.player(), r0.player());
        assertEquals(DETACH, r0.playerWrites());
        assertEquals(0, r0.object().flags() & 8);
        assertEquals(Set.of(), h.commit.calls.get(3).result().playerWrites());
        // ADDQ.B #2,oRoutine / MOVE.B #2,oAnim / RTS (:124-126): no AnimateObject, no DrawObject.
        var end = released.state();
        assertEquals(List.of(10, 2, 5), List.of(end.routine(), end.unsignedByte(ANIMATION), end.unsignedByte(MAP_FRAME)));
        assertEquals(draws, h.draw.observed.size());
        assertEquals(0x84, end.spriteFlags(), "without DrawObject bit 7 keeps the previous frame's DrawDone");
        assertEquals(detached.player(), h.player);
    }

    @Test void vanishFlickersOutThenResetSkipsSolidityAndDrawingBeforeMainHidesTheBlockAgain() {
        var h = new Harness();
        h.edit(o -> initialised(o, 10, 0x84, 5).moveByte(ANIMATION, 2).moveByte(ANIMATION_FRAME, 1)
                .moveByte(ANIMATION_TIME, 2));
        h.player = away().player();
        int[] frames = {1, 1, 1, 0, 0, 0, 2, 2, 2, 0, 0, 0, 3, 3, 3, 0, 0, 0, 4, 4, 4, 0, 0, 0, 0};
        for (int i = 0; i < frames.length; i++) {
            var result = h.run(present(0x0080));
            String at = "dispatch " + (i + 1);
            // ObjFloatBlock_Vanish animates and draws whether or not it is stood on (:137-142).
            assertEquals(List.of("VANISH", "TS0:GET_OFF/X_RANGE_RIGHT", "TS1:GET_OFF/X_RANGE_RIGHT", "ANIMATE_OBJECT",
                    "DRAW_OBJECT"), trace(result), at);
            assertEquals(frames[i], result.state().unsignedByte(MAP_FRAME), at);
            assertEquals(i < 24 ? 10 : 12, result.state().routine(), at);
        }
        int calls = h.commit.calls.size();
        int draws = h.draw.observed.size();
        var reset = h.run(present(0x0080));
        assertEquals(List.of("RESET"), trace(reset)); // MOVE.B #2,oRoutine / RTS (:148-150)
        assertEquals(2, reset.state().routine());
        assertEquals(calls, h.commit.calls.size(), "ObjFloatBlock_Reset never reaches ObjFloatBlock_TopSolid");
        assertEquals(draws, h.draw.observed.size());
        var main = h.run(present(0x0080));
        assertEquals(List.of("MAIN", "TS0:GET_OFF/X_RANGE_RIGHT", "TS1:GET_OFF/X_RANGE_RIGHT", "DRAW_OBJECT"), trace(main));
        assertEquals(0, main.state().unsignedByte(MAP_FRAME));
        assertEquals(2, main.state().routine());
        assertEquals(away().player(), h.player);
    }

    @Test void appearingWithoutTheStandingPlayerReturnsToMainWithoutAnimatingOrDrawing() {
        var h = new Harness();
        h.edit(o -> initialised(o, 6, 0x84, 0).moveByte(ANIMATION, 1).moveByte(ANIMATION_TIME, 1));
        h.player = away().player();
        var before = h.slot();
        var result = h.run(present(0x0080));
        assertEquals(List.of("APPEAR", "TS0:GET_OFF/X_RANGE_RIGHT", "TS1:GET_OFF/X_RANGE_RIGHT"), trace(result));
        assertEquals(before.moveByte(ROUTINE, 2), result.state(), "MOVE.B #2,oRoutine / RTS (:104-107) only");
        assertTrue(h.draw.observed.isEmpty());
    }

    // ----------------------------------------------------------------------------------------------------------
    // Past, Future and the falling block.
    // ----------------------------------------------------------------------------------------------------------

    @Test void aPastBlockIsAPlainVisibleTopSolidPlatform() {
        var h = new Harness();
        h.edit(o -> initialised(o, 2, 0x84, 5));
        var result = h.run(new CdFloatBlock.Globals(0, 0x0080, 0x0000));
        assertEquals(List.of("MAIN", "TS0:STAND_ON", "TS1:STAND_ON", "DRAW_OBJECT"), trace(result));
        var s = result.state();
        assertEquals(List.of(2, 5, 0, 0x08), List.of(s.routine(), s.unsignedByte(MAP_FRAME), s.unsignedByte(ANIMATION),
                s.flags()));
        assertEquals(landed().player(), h.player);
    }

    @ParameterizedTest(name = "timeZone {0}: frame {1}")
    @CsvSource({"0x00, 5", "0x01, 0", "0x02, 5", "0x80, 0", "0x81, 0", "0x82, 0"})
    void theRawPeriodByteSelectsMainsBranchIncludingTheWarpBit(String zoneHex, int frame) {
        // TST.B timeZone / CMPI.B #TIME_FUTURE,timeZone (:57-60) compare the whole byte, warp bit 7 included.
        var h = new Harness();
        h.edit(o -> initialised(o, 2, 0x84, 5));
        h.player = away().player();
        var result = h.run(new CdFloatBlock.Globals(Integer.decode(zoneHex), 0x0080, 0x0000));
        assertEquals(List.of("MAIN", "TS0:GET_OFF/X_RANGE_RIGHT", "TS1:GET_OFF/X_RANGE_RIGHT", "DRAW_OBJECT"),
                trace(result));
        assertEquals(frame, result.state().unsignedByte(MAP_FRAME));
        assertEquals(2, result.state().routine());
    }

    @Test void futureLandingStartsFallingInTheSameDispatchAndKeepsAllFourTopSolidCalls() {
        var h = new Harness();
        h.edit(o -> initialised(o, 2, 0x84, 5));
        var result = h.run(new CdFloatBlock.Globals(2, 0x0080, 0x0000));
        // .StartFall's ADDQ.B #2,oRoutine falls into ObjFloatBlock_Fall (:77-85): Main pair, then Fall pair.
        assertEquals(List.of("MAIN", "TS0:STAND_ON", "TS1:STAND_ON", "FALL", "TS2:STAND_ON", "TS3:STAND_ON",
                "DRAW_OBJECT"), trace(result));
        var calls = h.commit.calls;
        assertEquals(4, calls.size());
        assertEquals(LANDING, calls.get(0).result().playerWrites());
        for (int i = 1; i < 4; i++) {
            assertEquals(CARRY, calls.get(i).result().playerWrites(), "call " + i);
            assertEquals(calls.get(i - 1).result().player(), calls.get(i).player(), "call " + i);
            assertEquals(calls.get(i - 1).result().globals(), calls.get(i).globals(), "call " + i);
        }
        assertEquals(4, calls.get(2).callCut().slots().get(CUT_INDEX).routine(), "routine 4 is live for the Fall pair");
        assertEquals(0x0080, calls.get(3).result().object().centreY(), "ADDQ.W #2,oY follows the solidity (:87)");
        var s = result.state();
        assertEquals(List.of(4, 0x0082, 0x8765, 5), List.of(s.routine(), s.centreY(), s.word(Y_FRACTION),
                s.unsignedByte(MAP_FRAME)));
        assertEquals(landed().player(), h.player, "placed against the block's pre-move Y $80");
    }

    @Test void aFallingBlockCarriesTheStandingPlayerFromItsPreviousYBeforeMovingTwoPixels() {
        var h = standingOn(4, 0);
        h.edit(o -> o.moveWord(Y, 0x0082).moveByte(MAP_FRAME, 5));
        var result = h.run(new CdFloatBlock.Globals(2, 0x0080, 0x0000));
        assertEquals(List.of("FALL", "TS0:STAND_ON", "TS1:STAND_ON", "DRAW_OBJECT"), trace(result));
        var carried = landed(); carried.y = 0x00631234; // $82 - $1F; feet $76 leave 25 px of open air.
        var r0 = h.commit.calls.get(0).result();
        assertEquals(carried.player(), r0.player());
        assertEquals(CARRY, r0.playerWrites());
        assertEquals(List.of(new CdSolidObject.FloorDistance(25, NONE, NONE),
                new CdSolidObject.RoutineCall(CdSolidObject.Routine.STAND_ON_OBJECT)), r0.calls());
        assertEquals(carried.player(), h.player);
        assertEquals(0x0084, result.state().centreY());
        assertEquals(0x8765, result.state().word(Y_FRACTION), "ADDQ.W keeps the fraction word");
    }

    @ParameterizedTest(name = "Y {0}, camera ({1},{2}): {3}")
    @CsvSource({
            "0x00DE, 0x0080, 0x0000, DRAW",            // Y $E0 equals cameraY + $E0: BCC keeps drawing
            "0x00DF, 0x0080, 0x0000, FALL",            // $E1: JMP DeleteObject; the zeroed X 0 stays inside the window
            "0x00DF, 0x0000, 0x0000, FALL",            // cameraX < $80: d1 $FF80, 0 - $FF80 = $80
            "0x00DF, 0x0400, 0x0000, FALL_AND_WINDOW", // 0 - $380 = $FC80 > $280: the zeroed slot is deleted again
            "0x0080, 0x0080, 0xFF40, FALL"             // cameraY + $E0 wraps to $0020, below $82
    })
    void fallDeletesBelowTheCameraAndTheWrapperRetestsTheZeroedSlot(String yHex, String cameraXHex, String cameraYHex,
                                                                    String outcome) {
        var h = new Harness();
        h.edit(o -> initialised(o, 4, 0x84, 5).moveWord(Y, Integer.decode(yHex)));
        h.player = away().player();
        var result = h.run(new CdFloatBlock.Globals(2, Integer.decode(cameraXHex), Integer.decode(cameraYHex)));
        var trace = trace(result);
        assertEquals(List.of("FALL", "TS0:GET_OFF/X_RANGE_RIGHT", "TS1:GET_OFF/X_RANGE_RIGHT"), trace.subList(0, 3));
        var tail = trace.subList(3, trace.size());
        switch (outcome) {
            case "DRAW" -> {
                assertEquals(List.of("DRAW_OBJECT"), tail);
                assertEquals(0x00E0, result.state().centreY());
                assertFalse(result.deleted());
            }
            case "FALL" -> {
                assertEquals(List.of("DELETE:FALL_BELOW_CAMERA"), tail);
                assertArrayEquals(new byte[CdObjectState.BYTE_SIZE], result.state().copyBytes());
                assertTrue(h.draw.observed.isEmpty());
            }
            default -> {
                assertEquals(List.of("DELETE:FALL_BELOW_CAMERA", "DELETE:CHUNK_WINDOW"), tail);
                assertArrayEquals(new byte[CdObjectState.BYTE_SIZE], result.state().copyBytes());
            }
        }
        assertEquals(new CdObjectState.Identity(WHOLE), result.state().identity());
    }

    @Test void deletingTheStoodOnFallingBlockLeavesThePlayersStandBytesForTheNextModeCall() {
        var h = standingOn(4, 0);
        h.edit(o -> o.moveWord(Y, 0x00DF).moveByte(MAP_FRAME, 5));
        var resting = landed(); resting.y = 0x00C01234; // $DF - $1F: already standing on the low block.
        h.player = resting.player();
        var result = h.run(new CdFloatBlock.Globals(2, 0x0080, 0x0000));
        assertEquals(List.of("FALL", "TS0:STAND_ON", "TS1:STAND_ON", "DELETE:FALL_BELOW_CAMERA"), trace(result));
        // DeleteObject clears only a0's 64 bytes (Object Functions.asm:228-237): no GetOffObject runs.
        assertTrue(result.deleted());
        assertEquals(0x08, h.player.actor().motion().flags() & 0x08);
        assertEquals(WHOLE, h.player.standObject());
    }

    // ----------------------------------------------------------------------------------------------------------
    // The dispatcher's own $280 window.
    // ----------------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "X {0}, cameraX {1}: deleted={2}")
    @CsvSource({
            "0x0280, 0x0080, false", // ($280 - 0) = $280: BHI needs strictly above
            "0x02FF, 0x0080, false", // ANDI.W #$FF80 drops the low seven bits
            "0x0300, 0x0080, true",
            "0x0080, 0x0100, false", // ($80 - $80) = 0
            "0x007F, 0x0100, true",  // (0 - $80) = $FF80 unsigned: left of the window
            "0x0100, 0x007F, false"  // ($7F - $80) wraps to $FFFF, masked $FF80: $100 - $FF80 = $180
    })
    void theWrapperWindowDeletesAfterAnyRoutineOnlyAboveItsUnsignedLimit(String xHex, String cameraXHex, boolean deleted) {
        var h = new Harness();
        h.edit(o -> initialised(o, 12, 0x84, 0).moveWord(X, Integer.decode(xHex)));
        var result = h.run(present(Integer.decode(cameraXHex)));
        // No saved flag, CheckObjDespawn or DespawnObject: CMPI.W #$280 / BHI.W DeleteObject (:13-21).
        assertEquals(deleted ? List.of("RESET", "DELETE:CHUNK_WINDOW") : List.of("RESET"), trace(result));
        assertEquals(deleted, result.deleted());
        if (deleted) {
            assertArrayEquals(new byte[CdObjectState.BYTE_SIZE], result.state().copyBytes());
        } else {
            assertEquals(2, result.state().routine());
        }
        assertTrue(h.commit.calls.isEmpty());
        assertTrue(h.draw.observed.isEmpty());
    }

    // ----------------------------------------------------------------------------------------------------------
    // Call cuts, the Root commit seam and failure boundaries.
    // ----------------------------------------------------------------------------------------------------------

    @Test void theSecondCheckSeesTheFirstCallsReleaseOfThePreviouslyStoodSlotInAFreshCallCut() {
        var h = new Harness();
        h.edit(o -> initialised(o, 2, 0x84, 0));
        h.pool().write(h.pool().state(41).moveByte(ID, 0x12).moveByte(FLAGS, 0x09).moveWord(X, 0x0200));
        var previous = h.pool().state(41);
        var p = new P(); p.flags = 0x08; p.stand = 41; p.yv = 0;
        h.player = p.player();
        var result = h.run(present(0x0080));
        assertEquals(List.of("MAIN", "TS0:STAND_ON", "TS1:STAND_ON", "DRAW_OBJECT"), trace(result));
        var first = h.commit.calls.get(0);
        var second = h.commit.calls.get(1);
        var released = previous.moveByte(FLAGS, 0x01); // StandOnObject's BCLR #3,oFlags(a2) (:117-118)
        assertEquals(previous, first.callCut().slots().get(41 - CdObjectPool.FIRST_WHOLE_INDEX));
        assertEquals(List.of(released), first.result().otherSlots());
        assertEquals(released, second.callCut().slots().get(41 - CdObjectPool.FIRST_WHOLE_INDEX),
                "the second call cut is captured after the first publication");
        assertEquals(List.of(), second.result().otherSlots());
        assertEquals(first.result().player(), second.player());
        assertEquals(released, h.pool().state(41));
        assertEquals(6, result.state().routine());
    }

    @Test void aCommitPortThatSkipsTheSolidPublicationClosesTheDispatchBeforeTheSecondCheck() {
        var h = new Harness();
        h.edit(o -> initialised(o, 2, 0x84, 0));
        h.commit.publish = false;
        var before = h.slot();
        assertThrows(IllegalStateException.class, () -> h.run(present(0x0080)));
        assertEquals(1, h.commit.calls.size());
        assertEquals(before, h.slot(), "only the body's own pre-call cut write is live");
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test void aSolidOwnerBoundaryPropagatesAfterTheAlreadyCommittedPreCallWrite() {
        var h = new Harness();
        h.edit(o -> initialised(o, 8, 0x84, 5).moveByte(ANIMATION, 1));
        var p = new P(); p.flags = 0x08; p.stand = 5; p.yv = 0; // Standing "on" reserved slot 5.
        h.player = p.player();
        var failure = assertThrows(CdSolidObject.UnsupportedSolidObject.class, () -> h.run(present(0x0080)));
        assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS, failure.boundary());
        assertEquals(0, h.slot().unsignedByte(ANIMATION), "Visible's MOVE.B #0,oAnim was live at the call cut");
        assertTrue(h.commit.calls.isEmpty());
        assertTrue(h.draw.observed.isEmpty());
    }

    @ParameterizedTest(name = "routine {0}")
    @ValueSource(ints = {1, 7, 13, 14, 0xFE})
    void aRoutineOutsideTheSevenEntryIndexStopsBeforeAnyWrite(int routine) {
        var h = new Harness();
        h.edit(o -> initialised(o, routine, 0x84, 5));
        var before = h.slot();
        var failure = assertThrows(UnsupportedFloatBlock.class, () -> h.run(present(0x0080)));
        assertEquals(UnsupportedBoundary.ROUTINE_DISPATCH, failure.boundary());
        assertEquals(WHOLE, failure.wholeIndex());
        assertEquals(before, h.slot());
        assertTrue(h.commit.calls.isEmpty());
        assertTrue(h.draw.observed.isEmpty());
    }

    @ParameterizedTest(name = "subtype2 {0}")
    @ValueSource(ints = {1, 0x80, 0xFF})
    void initStopsAtAnUnreviewedSubtype2TileWordBeforeAnyWrite(int subtype2) {
        var h = new Harness();
        h.edit(o -> o.moveByte(ID, 0x12).moveWord(X, 0x0100).moveWord(Y, 0x0080).moveByte(SUBTYPE2, subtype2));
        var before = h.slot();
        var failure = assertThrows(UnsupportedFloatBlock.class, () -> h.run(present(0x0080)));
        assertEquals(UnsupportedBoundary.TILE_ID_SUBTYPE2, failure.boundary());
        assertEquals(before, h.slot());
        assertTrue(h.commit.calls.isEmpty());
        // Only Init calls SetObjectTileID (:43-44); the same byte is inert in every later routine.
        h.edit(o -> initialised(o, 2, 0x84, 0));
        assertEquals(List.of("MAIN", "TS0:STAND_ON", "TS1:STAND_ON", "DRAW_OBJECT"), trace(h.run(present(0x0080))));
        assertEquals(subtype2, h.slot().subtype2());
    }

    @Test void wrongSlotIdsAndInvalidInputsAreRejectedBeforeAnyWrite() {
        var h = new Harness();
        h.edit(o -> initialised(o, 2, 0x84, 0).moveByte(ID, 0x3D));
        var before = h.slot();
        assertThrows(IllegalArgumentException.class, () -> h.run(present(0x0080)));
        assertEquals(before, h.slot());
        var inputs = h.inputs();
        assertThrows(IndexOutOfBoundsException.class, () -> h.block.run(31, present(0x0080), inputs));
        assertThrows(IndexOutOfBoundsException.class, () -> h.block.run(128, present(0x0080), inputs));
        assertThrows(NullPointerException.class, () -> h.block.run(WHOLE, null, inputs));
        assertThrows(NullPointerException.class, () -> h.block.run(WHOLE, present(0x0080), null));
        assertThrows(IllegalArgumentException.class, () -> new CdFloatBlock.Globals(0x100, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdFloatBlock.Globals(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdFloatBlock.Globals(3, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdFloatBlock.Globals(0x83, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdFloatBlock.Globals(1, 0x10000, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdFloatBlock.Globals(1, 0, -1));
        assertThrows(NullPointerException.class, () -> new CdFloatBlock.SolidInputs(null, inputs.gates(), inputs.globals()));
        assertThrows(NullPointerException.class, () -> new CdFloatBlock.SolidInputs(inputs.player(), null, inputs.globals()));
        assertThrows(NullPointerException.class, () -> new CdFloatBlock.SolidInputs(inputs.player(), inputs.gates(), null));
        var cut = h.pool().capture();
        var top = h.solid.run(CdSolidObject.Entry.TOP_SOLID_OBJECT, h.slot().moveByte(ID, 0x12), inputs.player(),
                inputs.gates(), inputs.globals(), cut);
        assertThrows(NullPointerException.class, () -> new TopSolidCall(null, inputs.player(), inputs.globals(), top));
        assertThrows(NullPointerException.class, () -> new TopSolidCall(cut, null, inputs.globals(), top));
        assertThrows(NullPointerException.class, () -> new TopSolidCall(cut, inputs.player(), null, top));
        assertThrows(NullPointerException.class, () -> new TopSolidCall(cut, inputs.player(), inputs.globals(), null));
        var solid = h.solid;
        assertThrows(NullPointerException.class, () -> new CdFloatBlock(null, assets(), solid, h.commit, h.draw));
        assertThrows(NullPointerException.class, () -> new CdFloatBlock(h.runtime, null, solid, h.commit, h.draw));
        assertThrows(NullPointerException.class, () -> new CdFloatBlock(h.runtime, assets(), null, h.commit, h.draw));
        assertThrows(NullPointerException.class, () -> new CdFloatBlock(h.runtime, assets(), solid, null, h.draw));
        assertThrows(NullPointerException.class, () -> new CdFloatBlock(h.runtime, assets(), solid, h.commit, null));
    }

    @Test void effectRecordsKeepOnlyTheirNativeShapes() {
        var h = new Harness();
        h.edit(o -> initialised(o, 2, 0x84, 0));
        var in = h.inputs();
        var top = h.solid.run(CdSolidObject.Entry.TOP_SOLID_OBJECT, h.slot(), in.player(), in.gates(), in.globals(),
                h.pool().capture());
        var plain = h.solid.run(CdSolidObject.Entry.SOLID_OBJECT, h.slot(), in.player(), in.gates(), in.globals(),
                h.pool().capture());
        assertEquals(top, new TopSolid(3, top).result());
        assertThrows(IllegalArgumentException.class, () -> new TopSolid(-1, top));
        assertThrows(IllegalArgumentException.class, () -> new TopSolid(4, top), "Main pair + Fall pair is the most");
        assertThrows(IllegalArgumentException.class, () -> new TopSolid(0, plain), "only TopSolidObject is called");
        assertThrows(NullPointerException.class, () -> new TopSolid(0, null));
        assertThrows(NullPointerException.class, () -> new RoutineEntered(null));
        assertThrows(NullPointerException.class, () -> new CallReturned(null));
        assertThrows(NullPointerException.class, () -> new Deleted(null));
        assertThrows(NullPointerException.class, () -> new CdFloatBlock.Result(null, List.of()));
        var gone = new CdFloatBlock.Result(h.slot(), List.of(new Deleted(DeleteSite.CHUNK_WINDOW)));
        assertTrue(gone.deleted());
        assertThrows(UnsupportedOperationException.class, () -> gone.effects().add(new Deleted(DeleteSite.CHUNK_WINDOW)));
        assertFalse(new CdFloatBlock.Result(h.slot(), List.of(new RoutineEntered(NativeRoutine.RESET))).deleted());
    }

    @Test void assetsRequireTheSourceExtentsAndDeclaredCounts() throws Exception {
        // The two fixtures are the pinned includes; their digests equal the authenticated USA views.
        var sha = MessageDigest.getInstance("SHA-256");
        assertEquals("3c6451c3f6719a3765334aa1c13d2864ae4b92a523fb5a7e335c6dc362f8d6b9", HexFormat.of().formatHex(sha.digest(ANI)));
        assertEquals("98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e", HexFormat.of().formatHex(sha.digest(MAPS)));
        assertEquals(0x12, CdFloatBlock.OBJECT_ID);
        assertEquals(5, CdFloatBlock.TILE_SELECTOR);
        var visual = visual();
        var assets = new CdFloatBlock.Assets(CdAssetBytes.copyOf(TILE_IDS), visual, MAP);
        assertEquals(MAP, assets.mappingsAddress());
        assertThrows(IllegalArgumentException.class,
                () -> new CdFloatBlock.Assets(CdAssetBytes.copyOf(Arrays.copyOf(TILE_IDS, 73)), visual, MAP));
        byte[] twoScripts = bytes(0x00, 0x04, 0x00, 0x08, 0x02, 0x05, 0xFF, 0x00, 0x02, 0x01, 0x05, 0xFC);
        var twoScriptVisual = CdObjectVisualAssets.decode(MAPS, 6, twoScripts, 2, 15);
        assertThrows(IllegalArgumentException.class,
                () -> new CdFloatBlock.Assets(CdAssetBytes.copyOf(TILE_IDS), twoScriptVisual, MAP));
        assertThrows(NullPointerException.class, () -> new CdFloatBlock.Assets(null, visual, MAP));
        assertThrows(NullPointerException.class, () -> new CdFloatBlock.Assets(CdAssetBytes.copyOf(TILE_IDS), null, MAP));
        assertThrows(NullPointerException.class, () -> CdFloatBlock.Assets.fromObjectAssets(null));
    }

    @Test void actualUsaAssetsAndSolidBindingInitialiseRow37InvisibleAndUnstoodOnThePresentFloorRoute() throws Exception {
        var usa = loadUsa();
        var objects = usa.openingObjects();
        var assets = CdFloatBlock.Assets.fromObjectAssets(objects);
        assertEquals(0x20A3FA, assets.mappingsAddress()); // move.l #$20A3FA,oMap at USA $A2CA
        assertArrayEquals(objects.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes().copy(), assets.objectTileIds().copy());
        assertEquals(0x4334, CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), 5, 0));
        assertArrayEquals(ANI, assets.visual().animationTable().copy());
        assertArrayEquals(MAPS, assets.visual().mappings().copy());

        var h = new Harness(assets, CdSolidObject.fromAssets(usa));
        h.draw.onScreen = false; // Y $20C sits ~1,280 px above the floor route's camera: never drawn.
        h.edit(o -> o.moveByte(ID, 0x12).moveWord(X, 0x05B0).moveWord(Y, 0x020C)); // Row 37, subtype 0/0, no flips
        var floor = new P(); floor.x = 0x05B00000; floor.y = 0x076B0000; floor.flags = 0; floor.yv = 0;
        h.player = floor.player();
        var result = h.run(new CdFloatBlock.Globals(1, 0x0380, 0x070B));
        assertEquals(List.of("INIT", "MAIN", "TS0:GET_OFF/OBJECT_OFFSCREEN", "TS1:GET_OFF/OBJECT_OFFSCREEN",
                "DRAW_OBJECT"), trace(result));
        var s = result.state();
        assertEquals(List.of(2, 0x04, 0x4334, 0x20A3FA, 1, 0x0C, 0x0C, 0), List.of(s.routine(), s.spriteFlags(),
                s.word(TILE), s.longBits(MAPPINGS), s.unsignedByte(PRIORITY), s.unsignedByte(WIDTH),
                s.unsignedByte(Y_RADIUS), s.unsignedByte(MAP_FRAME)));
        for (var call : h.commit.calls) { assertEquals(Set.of(), call.result().playerWrites()); }
        assertEquals(floor.player(), h.player);
    }

    // ----------------------------------------------------------------------------------------------------------
    // Fixtures.
    // ----------------------------------------------------------------------------------------------------------

    private static CdFloatBlock.Globals present(int cameraX) { return new CdFloatBlock.Globals(1, cameraX, 0x0000); }

    /** Compact native trace: routine entries, TopSolid ordinal/return site/.GetOff reason, returns and deletions. */
    private static List<String> trace(CdFloatBlock.Result result) {
        var out = new ArrayList<String>();
        for (var effect : result.effects()) {
            if (effect instanceof RoutineEntered entered) {
                out.add(entered.routine().name());
            } else if (effect instanceof TopSolid top) {
                out.add("TS" + top.ordinal() + ":" + top.result().path().name()
                        + top.result().getOffReason().map(reason -> "/" + reason.name()).orElse(""));
            } else if (effect instanceof CallReturned returned) {
                out.add(returned.call().name());
            } else if (effect instanceof Deleted deleted) {
                out.add("DELETE:" + deleted.site().name());
            } else {
                fail("Unknown effect " + effect);
            }
        }
        return out;
    }

    /** The image Init leaves (Floating Block/Main.asm:39-48) at whole 40, X $100 / Y $80, with marker fractions. */
    private static CdObjectState initialised(CdObjectState o, int routine, int spriteFlags, int mapFrame) {
        return o.moveByte(ID, 0x12).moveWord(X, 0x0100).moveWord(X_FRACTION, 0x4321).moveWord(Y, 0x0080)
                .moveWord(Y_FRACTION, 0x8765).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, spriteFlags)
                .moveLong(MAPPINGS, MAP).moveWord(TILE, TILE_WORD).moveByte(PRIORITY, 1).moveByte(WIDTH, 0x0C)
                .moveByte(Y_RADIUS, 0x0C).moveByte(MAP_FRAME, mapFrame);
    }

    /** A drawn block at the given routine that the resting player already stands on (both bit 3s, $3D = 40). */
    private static Harness standingOn(int routine, int animation) {
        var h = new Harness();
        h.edit(o -> initialised(o, routine, 0x84, 0).moveByte(FLAGS, 0x08).moveByte(ANIMATION, animation));
        h.player = landed().player();
        return h;
    }

    /** The validated first landing on a block at Y $80: Y $61, at rest, standing on whole 40 (Solid.asm:121-140). */
    private static P landed() {
        var p = new P();
        p.y = 0x00611234; p.yv = 0; p.gv = 0x0123; p.angle = 0; p.flags = 0x08; p.jump = 0; p.stand = WHOLE;
        return p;
    }

    /** A falling player past the right edge: d0 = $130 - $100 + $16 = $46 >= $2C (Object Solid.asm:210-213). */
    private static P away() {
        var p = new P(); p.x = 0x01308000;
        return p;
    }

    private static CdFloatBlock.Assets assets() {
        return new CdFloatBlock.Assets(CdAssetBytes.copyOf(TILE_IDS), visual(), MAP);
    }

    private static CdObjectVisualAssets visual() { return CdObjectVisualAssets.decode(MAPS, 6, ANI, 3, 15); }

    /** Format stand-in for the 74-byte ObjectTileIDs: 18 index words, each naming its own data word. */
    private static byte[] tileIds() {
        var out = ByteBuffer.allocate(74);
        for (int i = 0; i < 18; i++) { out.putShort((short) (36 + 2 * i)); }
        for (int i = 0; i < 19; i++) { out.putShort((short) (i == 5 ? TILE_WORD : 0x0101 * (i + 1))); }
        return out.array();
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    private static int[] row(int x, int y, int id, int subtype, int periods, int subtype2) {
        return new int[]{x, y, id, subtype, periods, subtype2};
    }

    private static CdObjectPlacements layout(int[]... records) {
        int nullAt = 12 + 8 * (records.length + 1);
        var bytes = ByteBuffer.allocate(nullAt + 6);
        bytes.putShort((short) 12).putShort((short) nullAt).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        for (int[] r : records) {
            bytes.putShort((short) r[0]).putShort((short) r[1]).put((byte) r[2]).put((byte) r[3])
                    .put((byte) r[4]).put((byte) r[5]);
        }
        bytes.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        bytes.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(bytes.array());
    }

    private static CdR11aAssets loadUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "UNRUN actual-media opt-in: set soniccd.media.root");
        java.nio.file.Path cue;
        try (var files = Files.list(java.nio.file.Path.of(root).resolve("USA"))) { // A configured missing dir fails.
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue"))
                    .toList();
            assertEquals(1, cues.size(), "Configured USA media must contain exactly one CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
    }

    /** One live runtime, the shared Solid owner, Root's commit stand-in and a DrawObject stand-in. */
    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout(row(0x05B0, 0x020C, 0x12, 0x00, 0xE0, 0)));
        final Commit commit = new Commit(runtime.pool());
        final Draw draw = new Draw();
        final CdSolidObject solid;
        final CdFloatBlock block;
        CdSolidObject.Player player = new P().player();
        CdSolidObject.Gates gates = new CdSolidObject.Gates(0, 0);
        CdSolidObject.Globals globals = new CdSolidObject.Globals(0, 4, 0x12, 0x34);

        Harness() { this(assets(), new Terrain().owner()); }
        Harness(CdFloatBlock.Assets assets, CdSolidObject solid) {
            this.solid = solid;
            block = new CdFloatBlock(runtime, assets, solid, commit, draw);
        }

        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> change) { pool().write(change.apply(slot())); }
        CdFloatBlock.SolidInputs inputs() { return new CdFloatBlock.SolidInputs(player, gates, globals); }

        /** One RunObjects visit; Root's published player/globals become the next dispatch's ingress. */
        CdFloatBlock.Result run(CdFloatBlock.Globals world) {
            commit.live = player;
            commit.liveGlobals = globals;
            var result = block.run(WHOLE, world, inputs());
            player = commit.live;
            globals = commit.liveGlobals;
            return result;
        }
    }

    /** Root's S3/S4 role: CAS against the call inputs and call cut, then publish exactly the Result's images. */
    private static final class Commit implements CdFloatBlock.TopSolidCommit {
        final CdObjectPool pool;
        final List<TopSolidCall> calls = new ArrayList<>();
        CdSolidObject.Player live;
        CdSolidObject.Globals liveGlobals;
        boolean publish = true;

        Commit(CdObjectPool pool) { this.pool = pool; }

        @Override public void commit(TopSolidCall call) {
            calls.add(call);
            assertEquals(live, call.player(), "S3 CAS: every call starts from the live player");
            assertEquals(liveGlobals, call.globals(), "S3 CAS: every call starts from the live globals");
            var images = new ArrayList<CdObjectState>();
            images.add(call.result().object());
            images.addAll(call.result().otherSlots());
            for (var image : images) {
                assertEquals(call.callCut().slots().get(image.identity().dynamicIndex()),
                        pool.state(image.identity().wholeIndex()), "S4 CAS: nothing moved after the call cut");
            }
            if (!publish) { return; }
            images.forEach(pool::write);
            live = call.result().player();
            liveGlobals = call.result().globals();
        }
    }

    /** DrawObject's entry BCLR #7 (Object Functions.asm:138) and, when on screen, that frame's DrawDone BSET #7. */
    private static final class Draw implements CdFloatBlock.DrawObjectCall {
        boolean onScreen = true;
        final List<CdObjectState> observed = new ArrayList<>();

        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            var live = pool.state(slot.wholeIndex());
            observed.add(live);
            pool.write(live.moveByte(SPRITE_FLAGS, (live.spriteFlags() & 0x7F) | (onScreen ? 0x80 : 0)));
        }
    }

    /** Every player-slot byte the Solid owner reads is explicit constructed ingress (TestCdSolidObject's values). */
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

        CdSolidObject.Player player() {
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x, y, xv, yv, gv,
                    routine, angle, flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl, pri, sec), tile,
                    priority, hurt, rot), id, push, stand);
        }
    }

    /**
     * Format-decoder terrain, not an authenticated asset (TestCdSolidObject's fixture): 42x8 FG cells all chunk 2,
     * open air everywhere, so every floor probe returns 31 - (feet Y & 15) with no angle write.
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
}
