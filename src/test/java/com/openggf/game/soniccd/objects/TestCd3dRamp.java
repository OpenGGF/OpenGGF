package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.Cd3dRamp.DespawnEntry.*;
import static com.openggf.game.soniccd.objects.Cd3dRamp.NativeCall.*;
import static com.openggf.game.soniccd.objects.Cd3dRamp.UnsupportedBoundary.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the production port; every case is UNRUN by the external author.
 * Obj3DRamp (Level/Palmtree Panic/Objects/3D Ramp/Main.asm:194-341) at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. The script fixture is the reviewed 30-byte USA Ani_3DRamp
 * include; the mapping fixture is a ten-frame format stand-in because this body never reads pieces.
 * The player's oYVel write is a STAGED effect here; nothing in these tests applies it.
 */
class TestCd3dRamp {
    private static final int RAMP_MAP = 0x21FDBC;
    /** Common row 50: X $668, Y $758, ID $2A, subtype 0, subtype2 0, Present only, no saved flag. */
    private static final int ROW_X = 0x668;
    private static final int ROW_Y = 0x758;
    /** Ani_3DRamp: script 0 delay 0 frames 4,4,3,3,3,5,5,2,2,2 FF; script 1 delay 2 frames 4,7,5,6 FF; script 2 delay 2 frames 8,0,9,0 FF. */
    private static final byte[] RAMP_SCRIPTS = bytes(
            0x00, 0x06, 0x00, 0x12, 0x00, 0x18,
            0x00, 0x04, 0x04, 0x03, 0x03, 0x03, 0x05, 0x05, 0x02, 0x02, 0x02, 0xFF,
            0x02, 0x04, 0x07, 0x05, 0x06, 0xFF,
            0x02, 0x08, 0x00, 0x09, 0x00, 0xFF);
    private static final byte[] RAMP_MAPPINGS = tenSinglePieceFrames();
    private static final int UP = 0xC00;
    private static final int DOWN = -0xC00;

    @Test
    void firstCallInitialisesAndFallsThroughMainWithoutTouchingAFarPlayer() {
        var h = new Harness();
        h.write(h.slot(34).moveWord(X_VELOCITY, 0x1234).moveByte(ANIMATION_TIME, 7));
        h.place(34, ROW_X, ROW_Y, 0, 0);
        h.write(h.slot(34).moveByte(SPRITE_FLAGS, 0x83)); // Seed after SpawnObject replaces its flip byte.
        var result = h.run(34, grounded(0x600, 0x76B, 0), present(0x400));
        assertEquals(List.of(call(INIT), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), result.effects());
        assertFalse(result.deleted());
        var s = result.state();
        assertEquals(2, s.routine());
        assertEquals(0x2A, s.id());
        assertEquals(4, s.spriteFlags(), "MOVE.B #4 overwrites the dirty byte; DrawObject stand-in clears bit 7");
        assertEquals(1, s.unsignedByte(PRIORITY));
        assertEquals(RAMP_MAP, s.longBits(MAPPINGS));
        assertEquals(0x441, s.word(TILE));
        assertEquals(0x20, s.unsignedByte(WIDTH));
        assertEquals(0x20, s.unsignedByte(Y_RADIUS));
        assertEquals(ROW_X, s.word(RAMP_ANCHOR_X));
        assertEquals(ROW_X, s.centreX(), "timer 0 and player not on a ramp: oX := oVar2A + 0");
        assertEquals(ROW_Y, s.centreY());
        assertEquals(0, s.unsignedByte(MAP_FRAME), ".TimeRunSet writes frame 0");
        assertEquals(0, s.unsignedByte(RAMP_TIMER));
        assertEquals(0, s.unsignedByte(ANIMATION), "no animation write while the timer is idle");
        assertEquals(7, s.unsignedByte(ANIMATION_TIME), "AnimateObject was not called");
        assertEquals(0x1234, s.word(X_VELOCITY));
        assertEquals(0, s.unsignedByte(COLLISION_TYPE), "never selectable by Player_ObjCollide");
        assertEquals(s, h.slot(34));
        assertEquals(List.of(s), h.draw.observed);
    }

    @ParameterizedTest(name = "player ({0},{1}) launches={2}")
    @CsvSource({
            "0x648, 0x76B, true",   // $648-$668+$20 = 0: not negative, below $40
            "0x647, 0x76B, false",  // -1: BMI .End
            "0x687, 0x76B, true",   // $3F
            "0x688, 0x76B, false",  // $40: BCC .End
            "0x650, 0x738, true",   // $738-$758+$20 = 0
            "0x650, 0x737, false",
            "0x650, 0x777, true",   // $3F
            "0x650, 0x778, false"
    })
    void touchBoxUsesWidthAndRadiusWithSignedThenUnsignedWordTests(String pxHex, String pyHex, boolean launches) {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var result = h.run(34, grounded(Integer.decode(pxHex), Integer.decode(pyHex), 0), present(0x400));
        if (launches) {
            assertEquals(List.of(call(INIT), write(UP), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)),
                    result.effects());
            assertEquals(60, result.state().unsignedByte(RAMP_TIMER), "MOVE.B #60,oVar2E");
        } else {
            assertEquals(List.of(call(INIT), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), result.effects());
            assertEquals(0, result.state().unsignedByte(RAMP_TIMER));
        }
        assertEquals(ROW_X, result.state().centreX());
    }

    @ParameterizedTest(name = "oYVel {0} -> staged {1}")
    @CsvSource({"0x0000, 3072", "0x0001, 3072", "0x7FFF, 3072", "0xFFFF, -3072", "0x8000, -3072", "0xF400, -3072"})
    void launchSignFollowsThePlayerVerticalVelocitySignAndIsStagedNotApplied(String yVelHex, int staged) {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        int yVel = Integer.decode(yVelHex);
        var player = grounded(0x660, 0x76B, yVel);
        var result = h.run(34, player, present(0x400));
        assertTrue(result.effects().contains(write(staged)));
        assertEquals(1L, result.effects().stream().filter(e -> e instanceof Cd3dRamp.PlayerWrite).count());
        assertEquals(yVel, player.yVelocityWord(), "the player record is untouched: root applies the word");
        assertEquals(0xC00, Cd3dRamp.LAUNCH_SPEED);
        assertEquals(60, Cd3dRamp.TOUCH_TIMER_FRAMES);
    }

    @Test
    void runningTimerAnimatesShiftsByThePlayerChunkPositionAndKeepsLaunching() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var first = h.run(34, grounded(0x680, 0x76B, 0), present(0x400));
        assertEquals(List.of(call(INIT), write(UP), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), first.effects());
        assertEquals(60, first.state().unsignedByte(RAMP_TIMER));
        assertEquals(ROW_X, first.state().centreX());

        var second = h.run(34, grounded(0x680, 0x76B, 0), present(0x400));
        assertEquals(List.of(call(ANIMATE_OBJECT), write(UP), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)),
                second.effects());
        var s = second.state();
        assertEquals(2, s.unsignedByte(ANIMATION), "player not on a ramp: MOVE.B #1 then ADDQ.B #1");
        assertEquals(2, s.unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(8, s.unsignedByte(MAP_FRAME), "script 2 publishes frame 8 on the changed-animation reset");
        assertEquals(1, s.unsignedByte(ANIMATION_FRAME));
        assertEquals(2, s.unsignedByte(ANIMATION_TIME));
        assertEquals(0x698, s.centreX(), "low byte $80: ($80>>2)+($80>>3) = $30 added to the anchor");
        assertEquals(59, s.unsignedByte(RAMP_TIMER));
        assertEquals(ROW_X, s.word(RAMP_ANCHOR_X));

        var third = h.run(34, grounded(0x680, 0x76B, 0), present(0x400));
        assertEquals(8, third.state().unsignedByte(MAP_FRAME), "delay 2 waits a call");
        assertEquals(1, third.state().unsignedByte(ANIMATION_TIME));
        assertEquals(0x698, third.state().centreX());
        assertEquals(58, third.state().unsignedByte(RAMP_TIMER));
        assertTrue(third.effects().contains(write(UP)));

        var pushedOut = h.run(34, grounded(0x668, 0x76B, 0), present(0x400));
        assertEquals(0x68F, pushedOut.state().centreX(), "low byte $68: $1A + $D = $27");
        assertEquals(57, pushedOut.state().unsignedByte(RAMP_TIMER));
        assertFalse(pushedOut.effects().stream().anyMatch(e -> e instanceof Cd3dRamp.PlayerWrite),
                "$668-$68F+$20 is negative: the shifted booster no longer overlaps the player");
    }

    @Test
    void timerExpiryReturnsToFrameZeroAndTheAnchorWithoutTouchingTheAnimationId() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        h.run(34, grounded(0x600, 0x76B, 0), present(0x400));
        h.write(h.slot(34).moveByte(RAMP_TIMER, 1));
        var last = h.run(34, grounded(0x600, 0x76B, 0), present(0x400));
        assertEquals(List.of(call(ANIMATE_OBJECT), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), last.effects());
        assertEquals(0, last.state().unsignedByte(RAMP_TIMER));
        assertEquals(2, last.state().unsignedByte(ANIMATION));
        assertEquals(8, last.state().unsignedByte(MAP_FRAME));
        assertEquals(ROW_X, last.state().centreX(), "low byte 0 shifts by 0 while the timer still ran");
        var idle = h.run(34, grounded(0x600, 0x76B, 0), present(0x400));
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), idle.effects());
        assertEquals(0, idle.state().unsignedByte(MAP_FRAME), ".TimeRunSet");
        assertEquals(2, idle.state().unsignedByte(ANIMATION), ".TimeRunSet does not write oAnim");
    }

    @ParameterizedTest(name = "on ramp player X {0}: frame {1}, X {2}, launches={3}")
    @CsvSource({
            "0x6A0, 3, 0x6A4, true",   // $A0/$30 = 3; $28+$14 = $3C; $6A0-$6A4+$20 = $1C
            "0x6D0, 3, 0x6AE, false",  // $D0 -> $BF; $2F+$17 = $46; $6D0-$6AE+$20 = $42 >= $40
            "0x6F5, 0, 0x668, false",  // $F5 -> 0
            "0x600, 0, 0x668, false",  // $00: $600-$668+$20 negative
            "0x660, 2, 0x68C, false"   // $60/$30 = 2; $18+$C = $24; $660-$68C+$20 = -$C: BMI .End
    })
    void onRampWithIdleTimerSelectsTheFrameByChunkPositionAndShiftsTheBooster(
            String pxHex, int frame, String expectedXHex, boolean launches) {
        int px = Integer.decode(pxHex);
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var result = h.run(34, onRamp(px, 0x76B, 0xFF00), present(0x400));
        assertEquals(frame, result.state().unsignedByte(MAP_FRAME));
        int expectedX = Integer.decode(expectedXHex);
        assertEquals(expectedX, result.state().centreX());
        if (launches) {
            assertTrue(result.effects().contains(write(DOWN)), "negative oYVel launches upwards with -$C00");
            assertEquals(60, result.state().unsignedByte(RAMP_TIMER));
        } else {
            assertFalse(result.effects().stream().anyMatch(e -> e instanceof Cd3dRamp.PlayerWrite));
            assertEquals(0, result.state().unsignedByte(RAMP_TIMER));
        }
    }

    @Test
    void onRampWithTheTimerRunningUsesScriptOneAndKeepsTheAnimatedFrame() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        h.run(34, grounded(0x680, 0x76B, 0), present(0x400)); // timer := 60
        var result = h.run(34, onRamp(0x6A0, 0x76B, 0), present(0x400));
        assertEquals(List.of(call(ANIMATE_OBJECT), write(UP), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)),
                result.effects());
        var s = result.state();
        assertEquals(1, s.unsignedByte(ANIMATION), "player on a ramp: MOVE.B #1,oAnim without the increment");
        assertEquals(4, s.unsignedByte(MAP_FRAME), "script 1 publishes frame 4; DIVU is skipped while the timer runs");
        assertEquals(0x6A4, s.centreX());
        assertEquals(59, s.unsignedByte(RAMP_TIMER));
    }

    @Test
    void flippedSubtypeMirrorsTheChunkPositionAndNegatesTheShift() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 1, 0);
        var result = h.run(34, onRamp(0x640, 0x76B, 0), present(0x400));
        var s = result.state();
        assertEquals(5, s.spriteFlags(), "MOVE.B #4 then BSET #0");
        assertEquals(1, s.flags() & 1, "BSET #0,oFlags");
        assertEquals(3, s.unsignedByte(MAP_FRAME), "$FF-$40 = $BF -> $BF/$30 = 3");
        assertEquals(0x622, s.centreX(), "shift $46 negated: $668-$46");
        assertEquals(List.of(call(INIT), write(UP), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), result.effects());
        assertEquals(60, s.unsignedByte(RAMP_TIMER));

        var idle = new Harness();
        idle.place(34, ROW_X, ROW_Y, 1, 0);
        var away = idle.run(34, grounded(0x400, 0x600, 0), present(0x400));
        assertEquals(ROW_X, away.state().centreX(), "no ramp bit and no timer: the mirrored booster rests on its anchor");
        assertEquals(5, away.state().spriteFlags());
    }

    @Test
    void airborneGateAppliesOnlyWhileTheTimerIsIdle() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var airborne = h.run(34, new Cd3dRamp.Player(0x660, 0x76B, 0, 2, 0, 0), present(0x400));
        assertEquals(List.of(call(INIT), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), airborne.effects());
        assertEquals(0, airborne.state().unsignedByte(RAMP_TIMER), "BTST #1,oFlags(a6) / BNE .End before .ChkTouch");

        h.write(h.slot(34).moveByte(RAMP_TIMER, 60));
        var running = h.run(34, new Cd3dRamp.Player(0x680, 0x76B, 0xFC00, 2, 0, 0), present(0x400));
        assertTrue(running.effects().contains(write(DOWN)), "SUBQ.B / BRA .ChkTouch skips the air test");
        assertEquals(0x698, running.state().centreX());
        assertEquals(59, running.state().unsignedByte(RAMP_TIMER));
    }

    @Test
    void boredAnimationBlocksTheLaunchInsideTheBox() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var result = h.run(34, new Cd3dRamp.Player(0x660, 0x76B, 0, 0, 0x2B, 0), present(0x400));
        assertFalse(result.effects().stream().anyMatch(e -> e instanceof Cd3dRamp.PlayerWrite));
        assertEquals(0, result.state().unsignedByte(RAMP_TIMER));
        var other = h.run(34, new Cd3dRamp.Player(0x660, 0x76B, 0, 0, 0x2A, 0), present(0x400));
        assertTrue(other.effects().contains(write(UP)), "only $2B is tested");
    }

    @Test
    void secondSubtypeSelectsTheUnownedFallBodyBeforeAnyWrite() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 1);
        var before = h.slot(34);
        var failure = assertThrows(Cd3dRamp.Unsupported3dRamp.class, () -> h.run(34, grounded(0x600, 0x76B, 0), present(0x400)));
        assertEquals(ALTERNATE_OWNER_3D_FALL, failure.boundary());
        assertEquals(34, failure.wholeIndex());
        assertEquals(before, h.slot(34));
        assertTrue(h.draw.observed.isEmpty());
        h.write(h.slot(34).moveByte(ROUTINE, 2).moveByte(SUBTYPE2, 0x80));
        var again = assertThrows(Cd3dRamp.Unsupported3dRamp.class, () -> h.run(34, grounded(0x600, 0x76B, 0), present(0x400)));
        assertEquals(ALTERNATE_OWNER_3D_FALL, again.boundary(), "TST.B oSubtype2 precedes the routine dispatch");
    }

    @ParameterizedTest(name = "routine {0}")
    @CsvSource({"4", "1", "0xFE"})
    void routineOutsideTheTwoEntryTableStopsBeforeAnyWrite(String routineHex) {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        h.write(h.slot(34).moveByte(ROUTINE, Integer.decode(routineHex)));
        var before = h.slot(34);
        var failure = assertThrows(Cd3dRamp.Unsupported3dRamp.class, () -> h.run(34, grounded(0x600, 0x76B, 0), present(0x400)));
        assertEquals(ROUTINE_DISPATCH, failure.boundary());
        assertEquals(before, h.slot(34));
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void anchorDespawnUsesTheOriginalXNotTheShiftedOne() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var shifted = h.run(34, onRamp(0x6A0, 0x76B, 0), present(0x400)); // $600-$380 = $280 keeps
        assertEquals(0x6A4, shifted.state().centreX());
        assertFalse(shifted.deleted());
        var gone = h.run(34, onRamp(0x6A0, 0x76B, 0), present(0x3FF)); // $600-$300 = $300
        assertEquals(List.of(call(ANIMATE_OBJECT), write(UP), call(MAIN), call(DRAW_OBJECT),
                new Cd3dRamp.Despawn(ANCHOR_OFFSCREEN, 0, -1), call(CHECK_OBJ_DESPAWN2)), gone.effects());
        assertTrue(gone.deleted());
        assertArrayEquals(new byte[64], h.slot(34).copyBytes());

        var onscreen = new Harness();
        onscreen.draw.mode = RecordingDraw.Mode.SET_BIT7;
        onscreen.place(34, ROW_X, ROW_Y, 0, 0);
        assertFalse(onscreen.run(34, grounded(0x600, 0x76B, 0), present(0x900)).deleted());
        var warped = onscreen.run(34, grounded(0x600, 0x76B, 0), new Cd3dRamp.Globals(0x81, 0xFF, 0x400));
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT), new Cd3dRamp.Despawn(TIME_WARP, 0, -1),
                call(CHECK_OBJ_DESPAWN2)), warped.effects());

        var saved = new Harness();
        for (int period = 0; period < 3; period++) { saved.runtime.bsetSavedFlag(11, period, CdObjectPlacementRuntime.SavedBit.LOADED); }
        saved.write(saved.slot(34).moveByte(SAVED_FLAGS_ID, 11));
        saved.place(34, ROW_X, ROW_Y, 0, 0);
        var cleared = saved.run(34, grounded(0x600, 0x76B, 0), new Cd3dRamp.Globals(0x81, 0xFF, 0x400));
        assertTrue(cleared.effects().contains(new Cd3dRamp.Despawn(TIME_WARP, 11, 2)), "warp towards the future: old period 2");
        assertEquals(0, saved.runtime.savedFlag(11, 2) & 0x80);
        assertEquals(0x80, saved.runtime.savedFlag(11, 1) & 0x80);
    }

    @Test
    void dirtyRoutineTwoSkipsInitAndRunsMainOnWhateverTheSlotHolds() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        h.write(h.slot(34).moveByte(ROUTINE, 2).moveWord(RAMP_ANCHOR_X, 0x700));
        var result = h.run(34, grounded(0x600, 0x76B, 0), present(0x600)); // $700-$580 = $180 keeps
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), result.effects());
        assertEquals(0x700, result.state().centreX(), "Main trusts the dirty anchor; Init never ran");
        assertEquals(0, result.state().longBits(MAPPINGS));
        assertEquals(0, result.state().word(TILE));
    }

    @Test
    void wrongSlotIdAndInvalidInputsAreRejectedBeforeAnyWrite() {
        var h = new Harness();
        h.write(h.slot(34).moveByte(ID, 0x2C));
        assertThrows(IllegalArgumentException.class, () -> h.run(34, grounded(0, 0, 0), present(0)));
        assertEquals(0x2C, h.slot(34).id());
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Player(0x10000, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Player(0, 0x10000, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Player(0, 0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Player(0, 0, 0, 0x100, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Player(0, 0, 0, 0, 0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Player(0, 0, 0, 0, 0, 0x100));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Globals(3, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Globals(1, 0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Globals(1, 0, 0x10000));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.PlayerWrite(Cd3dRamp.PlayerField.Y_VELOCITY, 0x8000));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.PlayerWrite(Cd3dRamp.PlayerField.Y_VELOCITY, -0x8001));
        assertThrows(NullPointerException.class, () -> new Cd3dRamp.PlayerWrite(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Despawn(ANCHOR_OFFSCREEN, 0, 1));
        assertThrows(NullPointerException.class, () -> h.ramp.run(34, null, present(0)));
        assertThrows(NullPointerException.class, () -> h.ramp.run(34, grounded(0, 0, 0), null));
        byte[] twoScripts = bytes(0x00, 0x04, 0x00, 0x07, 0x02, 0x00, 0xFF, 0x02, 0x01, 0xFF);
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Assets(
                CdObjectVisualAssets.decode(RAMP_MAPPINGS, 10, twoScripts, 2, 63), RAMP_MAP));
        byte[] nineFrameScripts = bytes(0x00, 0x06, 0x00, 0x09, 0x00, 0x0C, 0x02, 0x04, 0xFF, 0x02, 0x07, 0xFF, 0x02, 0x08, 0xFF);
        assertThrows(IllegalArgumentException.class, () -> new Cd3dRamp.Assets(
                CdObjectVisualAssets.decode(nSinglePieceFrames(9), 9, nineFrameScripts, 3, 63), RAMP_MAP),
                "ten declared MapSpr_3DRamp frames are required");
        assertThrows(NullPointerException.class, () -> new Cd3dRamp.Assets(null, RAMP_MAP));
        assertThrows(NullPointerException.class, () -> new Cd3dRamp(null, assets(), new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new Cd3dRamp(h.runtime, null, new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new Cd3dRamp(h.runtime, assets(), null));
    }

    @Test
    void storageAliasesOverlapTheSharedObjectTail() {
        assertEquals(0x2A, RAMP_ANCHOR_X.byteOffset());
        assertEquals(MOSQUI_ANCHOR_X.byteOffset(), RAMP_ANCHOR_X.byteOffset());
        assertEquals(0x2E, RAMP_TIMER.byteOffset());
        assertEquals(0x2A, Cd3dRamp.OBJECT_ID);
        assertEquals(0x441, Cd3dRamp.NATIVE_TILE_WORD);
    }

    @Test
    void authenticatedUsaAssetsBindTheBoosterMappingAddressAndScripts() throws Exception {
        var objects = loadUsa().openingObjects();
        var assets = Cd3dRamp.Assets.fromObjectAssets(objects);
        assertEquals(RAMP_MAP, assets.mappingsAddress());
        assertEquals(10, assets.booster().mappingFrames().size());
        assertEquals(3, assets.booster().animations().size());
        assertArrayEquals(RAMP_SCRIPTS, assets.booster().animationTable().copy(), "the script fixture is the authenticated include");
    }

    private static Cd3dRamp.Effect call(Cd3dRamp.NativeCall nativeCall) { return new Cd3dRamp.CallReturned(nativeCall); }
    private static Cd3dRamp.Effect write(int signedWord) {
        return new Cd3dRamp.PlayerWrite(Cd3dRamp.PlayerField.Y_VELOCITY, signedWord);
    }
    private static Cd3dRamp.Player grounded(int x, int y, int yVel) { return new Cd3dRamp.Player(x, y, yVel, 0, 0, 0); }
    private static Cd3dRamp.Player onRamp(int x, int y, int yVel) { return new Cd3dRamp.Player(x, y, yVel, 0, 0, 2); }
    private static Cd3dRamp.Globals present(int cameraX) { return new Cd3dRamp.Globals(1, 0, cameraX); }
    private static Cd3dRamp.Assets assets() {
        return new Cd3dRamp.Assets(CdObjectVisualAssets.decode(RAMP_MAPPINGS, 10, RAMP_SCRIPTS, 3, 63), RAMP_MAP);
    }

    /** Format stand-in: n frames of one zero piece each; the body never reads mapping pieces. */
    private static byte[] nSinglePieceFrames(int n) {
        var out = ByteBuffer.allocate(2 * n + 6 * n);
        for (int i = 0; i < n; i++) { out.putShort((short) (2 * n + 6 * i)); }
        for (int i = 0; i < n; i++) { out.put((byte) 1).put(new byte[5]); }
        return out.array();
    }
    private static byte[] tenSinglePieceFrames() { return nSinglePieceFrames(10); }

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
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for supplied-media checks");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "USA needs exactly one actual CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
    }

    /** Native DrawObject stand-in over the live pool; the shared queue is a separate owner. */
    private static final class RecordingDraw implements Cd3dRamp.DrawObjectCall {
        enum Mode { CLEAR_BIT7, SET_BIT7, UNTOUCHED }
        Mode mode = Mode.CLEAR_BIT7;
        final List<CdObjectState> observed = new ArrayList<>();

        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            var live = pool.state(slot.wholeIndex());
            observed.add(live);
            switch (mode) {
                case CLEAR_BIT7 -> pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 0x7F));
                case SET_BIT7 -> pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() | 0x80));
                case UNTOUCHED -> { }
            }
        }
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout(row(ROW_X, ROW_Y, 0x2A, 0x00, 0x40, 0)));
        final RecordingDraw draw = new RecordingDraw();
        final Cd3dRamp ramp = new Cd3dRamp(runtime, assets(), draw);

        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot(int whole) { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }
        Cd3dRamp.Result run(int whole, Cd3dRamp.Player player, Cd3dRamp.Globals globals) {
            return ramp.run(whole, player, globals);
        }

        /** SpawnObject's writes only (Object Spawner.asm:267-319); unflagged records write no saved ID. */
        void place(int whole, int x, int packedY, int subtype, int subtype2) {
            int rotated = ((packedY << 2) | (packedY >>> 14)) & 0xFFFF;
            int flips = rotated & 3;
            var state = slot(whole).moveWord(X, x).moveWord(Y, packedY & 0xFFF)
                    .moveByte(SPRITE_FLAGS, flips).moveByte(FLAGS, flips);
            write(state.moveByte(ID, 0x2A).moveByte(SUBTYPE, subtype).moveByte(SUBTYPE2, subtype2));
        }
    }
}
