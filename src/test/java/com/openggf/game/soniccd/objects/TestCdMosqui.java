package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdMosqui.DespawnEntry.*;
import static com.openggf.game.soniccd.objects.CdMosqui.NativeCall.*;
import static com.openggf.game.soniccd.objects.CdMosqui.UnsupportedBoundary.*;
import static com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.SavedBit.LOADED;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the production port; every case is UNRUN by the external author.
 * Literal format fixtures are the reviewed USA Mosqui views (Mosqui/Main.asm:154-165 includes);
 * no native comparison row, runtime disassembly read or fixture coordinate drives the body.
 * Source pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 */
class TestCdMosqui {
    private static final int NORMAL_MAP = 0x20B6E6;
    private static final int NORMAL_ANI = 0x20B6CA;
    private static final int DAMAGED_MAP = 0x20B722;
    private static final int DAMAGED_ANI = 0x20B6D8;
    /** Ani_Mosqui1: delay 1 frames 0,1 FF; delay 6 frames 2,3,4 FC, real trailing zero. */
    private static final byte[] NORMAL_SCRIPTS = bytes(
            0x00, 0x04, 0x00, 0x08, 0x01, 0x00, 0x01, 0xFF, 0x06, 0x02, 0x03, 0x04, 0xFC, 0x00);
    /** Ani_Mosqui2: delay 4 frames 0,1 FF; delay $E frames 2,3,4 FC. */
    private static final byte[] DAMAGED_SCRIPTS = bytes(
            0x00, 0x04, 0x00, 0x08, 0x04, 0x00, 0x01, 0xFF, 0x0E, 0x02, 0x03, 0x04, 0xFC);
    private static final byte[] NORMAL_MAPPINGS = bytes(
            0x00, 0x0A, 0x00, 0x1A, 0x00, 0x25, 0x00, 0x30, 0x00, 0x36,
            3, 0xF4, 0x08, 0x00, 0x00, 0xF8, 0xFC, 0x05, 0x00, 0x03, 0xF0, 0xFC, 0x05, 0x00, 0x07, 0x00,
            2, 0xFC, 0x05, 0x00, 0x03, 0xF0, 0xFC, 0x05, 0x00, 0x0B, 0x00,
            2, 0xF0, 0x0A, 0x00, 0x0F, 0xF8, 0x08, 0x04, 0x00, 0x18, 0xF0,
            1, 0xF0, 0x0B, 0x00, 0x1A, 0xF8,
            1, 0xF0, 0x0B, 0x00, 0x26, 0xF4);
    private static final byte[] DAMAGED_MAPPINGS = bytes(
            0x00, 0x0A, 0x00, 0x1A, 0x00, 0x25, 0x00, 0x30, 0x00, 0x36,
            3, 0xF4, 0x08, 0x00, 0x00, 0xF8, 0xFC, 0x05, 0x00, 0x32, 0xF0, 0xFC, 0x05, 0x00, 0x07, 0x00,
            2, 0xFC, 0x05, 0x00, 0x32, 0xF0, 0xFC, 0x05, 0x00, 0x0B, 0x00,
            2, 0xF0, 0x0A, 0x00, 0x0F, 0xF8, 0x08, 0x04, 0x00, 0x36, 0xF0,
            1, 0xF0, 0x0B, 0x00, 0x38, 0xF8,
            2, 0x00, 0x01, 0x00, 0x44, 0xFC, 0xF0, 0x0B, 0x00, 0x26, 0xF4, 0x00);

    @Test
    void firstCallInitialisesFallsThroughMainAndMovesTheAnchorMinusOne() {
        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 0, 10);
        var result = h.run(34, far());
        var s = result.state();
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(INIT), call(MAIN), call(DRAW_OBJECT),
                call(CHECK_OBJ_DESPAWN2)), result.effects());
        assertEquals(2, s.routine());
        assertEquals(4, s.spriteFlags(), "ORI.B #4; frame 0 copies flips from oFlags 0; DrawObject clears bit 7");
        assertEquals(3, s.unsignedByte(PRIORITY));
        assertEquals(0x10, s.unsignedByte(X_RADIUS));
        assertEquals(0x10, s.unsignedByte(WIDTH));
        assertEquals(0x10, s.unsignedByte(Y_RADIUS));
        assertEquals(0x2B, s.unsignedByte(COLLISION_TYPE));
        assertEquals(0x270, s.word(MOSQUI_ANCHOR_X));
        assertEquals(0x23A0, s.word(TILE));
        assertEquals(NORMAL_MAP, s.longBits(MAPPINGS));
        assertEquals(NORMAL_ANI, s.longBits(MOSQUI_ANIMATION_SCRIPT));
        assertEquals(-0x10000, s.longBits(MOSQUI_X_DELTA));
        assertEquals(0x026F0000, s.longBits(X_POSITION), "Init falls through into Main: $270 becomes $26F in the first call");
        assertEquals(0x26F, s.centreX());
        assertEquals(0x730, s.centreY());
        assertEquals(0, s.unsignedByte(ANIMATION));
        assertEquals(0, s.unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(1, s.unsignedByte(ANIMATION_FRAME));
        assertEquals(1, s.unsignedByte(ANIMATION_TIME));
        assertEquals(0, s.unsignedByte(MAP_FRAME));
        assertEquals(0x3D, s.id());
        assertEquals(10, s.savedFlagsId());
        assertEquals(0x80, h.runtime.savedFlag(10, 1));
        assertFalse(result.deleted());
        assertEquals(s, h.slot(34));
        assertEquals(List.of(s), h.draw.observed, "DrawObject saw the committed post-Main image");

        var second = h.run(34, far());
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)),
                second.effects());
        assertEquals(0x26E, second.state().centreX());
        assertEquals(0, second.state().unsignedByte(ANIMATION_TIME), "delay 1 waits one call");
        assertEquals(1, second.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(0, second.state().unsignedByte(MAP_FRAME));

        var third = h.run(34, far());
        assertEquals(0x26D, third.state().centreX());
        assertEquals(1, third.state().unsignedByte(MAP_FRAME));
        assertEquals(2, third.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(1, third.state().unsignedByte(ANIMATION_TIME));
    }

    @Test
    void initRetainsDirtySlotBytesAndTheAnimatorConsumesThemLiterally() {
        var h = new Harness();
        h.write(h.slot(34).moveWord(X_VELOCITY, 0x1234).moveWord(Y_VELOCITY, 0xFEDC)
                .moveByte(COLLISION_STATUS, 0x5A).moveByte(ANGLE, 0x77).moveByte(ROUTINE_2, 0x66)
                .moveWord(X_FRACTION, 0x8000).moveWord(Y_FRACTION, 0x4321)
                .moveByte(ANIMATION_TIME, 5).moveByte(ANIMATION_FRAME, 3).moveByte(MAP_FRAME, 0x1F));
        h.place(34, 0x270, 0x4730, 0, 0, 10); // packed Y bit 14 supplies flip bit 0 to both flag bytes
        var s = h.run(34, far()).state();
        assertEquals(0x1234, s.word(X_VELOCITY));
        assertEquals(0xFEDC, s.word(Y_VELOCITY));
        assertEquals(0x5A, s.unsignedByte(COLLISION_STATUS));
        assertEquals(0x77, s.unsignedByte(ANGLE));
        assertEquals(0x66, s.unsignedByte(ROUTINE_2));
        assertEquals(0x4321, s.word(Y_FRACTION));
        assertEquals(0x026F8000, s.longBits(X_POSITION), "SpawnObject wrote only the integer word; the fraction rides along");
        assertEquals(4, s.unsignedByte(ANIMATION_TIME), "SUBQ.B on the dirty timer waits without a publish");
        assertEquals(3, s.unsignedByte(ANIMATION_FRAME));
        assertEquals(0x1F, s.unsignedByte(MAP_FRAME));
        assertEquals(5, s.spriteFlags(), "flip bit 0 survives ORI.B #4");
        assertEquals(1, s.flags());
        assertEquals(0x730, s.centreY());
        assertEquals(2, s.routine());
    }

    @Test
    void normalPatrolReversesAtEightyWithASecondAddInTheSameCall() {
        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 0, 10);
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int call = 1; call <= 128; call++) {
            int x = h.run(34, far()).state().centreX();
            min = Math.min(min, x);
            max = Math.max(max, x);
            if (call < 128) { assertEquals(0x270 - call, x, "call " + call); }
        }
        var turned = h.slot(34);
        assertEquals(0x1F1, turned.centreX(), "$1F0 is |-$80|: NEG.L, BCHG, then the reversed delta is added again");
        assertEquals(0x10000, turned.longBits(MOSQUI_X_DELTA));
        assertEquals(5, turned.spriteFlags());
        assertEquals(1, turned.flags());
        assertEquals(0x270, turned.word(MOSQUI_ANCHOR_X), "the anchor never moves");
        for (int call = 129; call <= 383; call++) {
            int x = h.run(34, far()).state().centreX();
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        var back = h.slot(34);
        assertEquals(0x2EF, back.centreX());
        assertEquals(-0x10000, back.longBits(MOSQUI_X_DELTA));
        assertEquals(0, back.flags());
        assertEquals(4, back.spriteFlags());
        assertEquals(0x1F1, min, "normal patrol final X stays within $1F1..$2EF");
        assertEquals(0x2EF, max);
    }

    @Test
    void damagedPatrolMovesHalfPixelsAndADirtyFractionDelaysTheFirstStep() {
        // Anchor $350 is chunk $300: camera 0 would anchor-despawn it ($300 - $FF80 = $380); chunk $100 keeps it at $280.
        var h = new Harness();
        h.place(35, 0x350, 0x728, 0xFF, 0, 11);
        var first = h.run(35, farAt(0x100)).state();
        assertEquals(DAMAGED_MAP, first.longBits(MAPPINGS));
        assertEquals(DAMAGED_ANI, first.longBits(MOSQUI_ANIMATION_SCRIPT));
        assertEquals(-0x8000, first.longBits(MOSQUI_X_DELTA));
        assertEquals(0x034F8000, first.longBits(X_POSITION));
        assertEquals(0x34F, first.centreX());
        assertEquals(0x23A0, first.word(TILE));
        assertEquals(0, first.unsignedByte(MAP_FRAME));
        assertEquals(4, first.unsignedByte(ANIMATION_TIME), "Ani_Mosqui2 script 0 delay");
        assertEquals(1, first.unsignedByte(ANIMATION_FRAME));
        for (int call = 2; call <= 254; call++) { h.run(35, farAt(0x100)); }
        var before = h.slot(35);
        assertEquals(0x02D10000, before.longBits(X_POSITION));
        assertEquals(-0x8000, before.longBits(MOSQUI_X_DELTA));
        assertEquals(0, before.flags());
        var turned = h.run(35, farAt(0x100)).state();
        assertEquals(0x02D10000, turned.longBits(X_POSITION), "$2D0.8 is |-$80|; the second add returns to $2D1.0");
        assertEquals(0x8000, turned.longBits(MOSQUI_X_DELTA));
        assertEquals(1, turned.flags());
        assertEquals(5, turned.spriteFlags());

        var dirty = new Harness();
        dirty.write(dirty.slot(35).moveWord(X_FRACTION, 0x8000));
        dirty.place(35, 0x350, 0x728, 0xFF, 0, 11);
        assertEquals(0x03500000, dirty.run(35, farAt(0x100)).state().longBits(X_POSITION), "integer X holds for one call");
        assertEquals(0x034F8000, dirty.run(35, farAt(0x100)).state().longBits(X_POSITION));
    }

    @ParameterizedTest
    @CsvSource({"-48,0,true", "-49,0,false", "47,0,true", "48,0,false",
            "0,-48,true", "0,-49,false", "0,63,true", "0,64,false"})
    void rangeEdgesUseTheSecondSubtractionsCarryAndStartDiveNeitherMovesNorAnimates(int relX, int relY, boolean inRange) {
        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 0, 10);
        h.run(34, far());
        var result = h.run(34, globals(0x26F + relX, 0x730 + relY, 0));
        var s = result.state();
        if (inRange) {
            assertEquals(4, s.routine());
            assertEquals(1, s.unsignedByte(ANIMATION));
            assertEquals(0x26F, s.centreX(), ".StartDive returns before the delta add");
            assertEquals(1, s.unsignedByte(ANIMATION_TIME), "no AnimateObject in the StartDive call");
            assertEquals(1, s.unsignedByte(ANIMATION_FRAME));
            assertEquals(0, s.unsignedByte(MAP_FRAME));
            assertEquals(0, s.unsignedByte(PREVIOUS_ANIMATION));
            assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)),
                    result.effects());
        } else {
            assertEquals(2, s.routine());
            assertEquals(0, s.unsignedByte(ANIMATION));
            assertEquals(0x26E, s.centreX());
            assertEquals(0, s.unsignedByte(ANIMATION_TIME));
        }
    }

    @Test
    void rangeUsesWordWrapAndTheFullDebugWordSkipsIt() {
        var wrap = new Harness();
        wrap.place(34, 0x270, 0x010, 0, 0, 10);
        wrap.run(34, far());
        assertEquals(4, wrap.run(34, globals(0x26F, 0xFFF0, 0)).state().routine(),
                "player oY $FFF0 minus $10 is -$20 as a word, inside [-$30,$40)");

        var debug = new Harness();
        debug.place(34, 0x270, 0x730, 0, 0, 10);
        debug.run(34, far());
        var skipped = debug.run(34, new CdMosqui.Globals(0, 1, 0, 0x100, 0, 0x26F, 0x730));
        assertEquals(2, skipped.state().routine(), "TST.W debugMode: a high-byte-only value skips the range check");
        assertEquals(0x26E, skipped.state().centreX());
        assertEquals(4, debug.run(34, globals(0x26E, 0x730, 0)).state().routine());
    }

    @Test
    void startDiveThenTwentyTwoAnimateCallsThenLandWithFloorAdjustmentSoundWaitAndDespawn() {
        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 0, 10);
        h.run(34, far());                                                       // call 1: Init/Main, X $26F
        assertEquals(4, h.run(34, globals(0x26F, 0x730, 0)).state().routine());  // call 2: StartDive

        var firstFrame = h.run(34, far());                                      // call 3
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(ANIMATE), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)),
                firstFrame.effects());
        assertEquals(2, firstFrame.state().unsignedByte(MAP_FRAME));
        assertEquals(6, firstFrame.state().unsignedByte(ANIMATION_TIME));
        assertEquals(1, firstFrame.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(1, firstFrame.state().unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(4, firstFrame.state().routine());
        assertEquals(0x26F, firstFrame.state().centreX(), "routine 4 does not patrol");
        for (int call = 4; call <= 23; call++) {
            assertEquals(4, h.run(34, far()).state().routine(), "call " + call);
        }
        var armed = h.slot(34);
        assertEquals(4, armed.unsignedByte(MAP_FRAME));
        assertEquals(3, armed.unsignedByte(ANIMATION_FRAME));
        assertEquals(0, armed.unsignedByte(ANIMATION_TIME));

        var toDive = h.run(34, far());                                          // call 24: $FC
        assertEquals(6, toDive.state().routine());
        assertEquals(4, toDive.state().unsignedByte(MAP_FRAME));
        assertEquals(3, toDive.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(6, toDive.state().unsignedByte(ANIMATION_TIME));
        assertEquals(0x730, toDive.state().centreY());

        for (int k = 1; k <= 11; k++) {                                         // calls 25..35
            var r = h.run(34, far());
            assertEquals(0x730 + 6 * k, r.state().centreY(), "dive call " + k);
            assertEquals(6, r.state().routine(), "dive call " + k);
            assertEquals(4, r.state().unsignedByte(MAP_FRAME), "no animation while diving");
            assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE),
                            new CdMosqui.GlobalWrite(CdMosqui.GlobalField.PRIMARY_ANGLE, k >= 8 ? 0x22 : 0),
                            call(DIVE), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)),
                    r.effects(), "dive call " + k);
        }
        h.setVisible(34);                                                       // last DrawObjects drew it
        var landed = h.run(34, far());                                          // call 36: probe $788 is -9
        assertEquals(0x777, landed.state().centreY(), "Y $778 plus (d1 + 8) = -1");
        assertEquals(8, landed.state().routine());
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE),
                        new CdMosqui.GlobalWrite(CdMosqui.GlobalField.PRIMARY_ANGLE, 0x22),
                        new CdMosqui.SoundRequest(CdMosqui.Sound.FM_A7),
                        call(DIVE), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)),
                landed.effects());
        assertEquals(0, landed.state().spriteFlags() & 0x80, "the port's DrawObject clear follows the sound decision");

        h.setVisible(34);
        var visibleWait = h.run(34, far());                                     // call 37
        assertFalse(visibleWait.deleted());
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(WAIT), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)),
                visibleWait.effects());
        assertEquals(0x777, visibleWait.state().centreY());
        assertEquals(8, visibleWait.state().routine());

        var gone = h.run(34, far());                                            // call 38: not drawn last time
        assertTrue(gone.deleted());
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), new CdMosqui.Despawn(WAIT_OFFSCREEN, 10, 1), call(WAIT),
                call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), gone.effects());
        assertEquals(0, h.runtime.savedFlag(10, 1));
        assertArrayEquals(new byte[64], gone.state().copyBytes());
        assertEquals(0, h.draw.observed.getLast().id(), "DrawObject still ran, on the already deleted slot");
        assertThrows(IllegalArgumentException.class, () -> h.run(34, far()), "ID 0 is no longer this handler's");
    }

    @Test
    void landingWithoutTheVisibleBitRequestsNoSound() {
        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 0, 10);
        h.run(34, far());
        h.write(h.slot(34).moveByte(ROUTINE, 6).moveWord(Y, 0x772));
        var landed = h.run(34, far());
        assertEquals(0x777, landed.state().centreY());
        assertEquals(8, landed.state().routine());
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE),
                new CdMosqui.GlobalWrite(CdMosqui.GlobalField.PRIMARY_ANGLE, 0x22),
                call(DIVE), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), landed.effects());
    }

    @Test
    void waitDespawnPrecedesDrawObjectAndASecondAnchorDespawnWritesNoBank() {
        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 0, 10);
        h.runtime.bsetSavedFlag(10, 0, LOADED);
        h.runtime.bsetSavedFlag(10, 2, LOADED);
        h.run(34, far());
        h.write(h.slot(34).moveByte(ROUTINE, 8));
        var gone = h.run(34, globals(0x60, 0x6E0, 0x100));
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), new CdMosqui.Despawn(WAIT_OFFSCREEN, 10, 1), call(WAIT),
                call(DRAW_OBJECT), new CdMosqui.Despawn(ANCHOR_OFFSCREEN, 0, -1), call(CHECK_OBJ_DESPAWN2)),
                gone.effects(), "the zeroed slot's anchor 0 is beyond $280 of camera $100");
        assertEquals(0, h.runtime.savedFlag(10, 1));
        assertEquals(0x80, h.runtime.savedFlag(10, 0));
        assertEquals(0x80, h.runtime.savedFlag(10, 2));
        assertArrayEquals(new byte[64], gone.state().copyBytes());
        assertEquals(0, h.draw.observed.getLast().id());
        assertEquals(0, h.draw.observed.getLast().spriteFlags());
    }

    @Test
    void anchorDespawnIsInclusiveAtTwoEightyAndFollowsTheDrawObjectCall() {
        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 0, 10);
        h.run(34, far());
        assertFalse(h.run(34, globals(0x60, 0x6E0, 0x7F)).deleted(), "$200 - $FF80 = $280: BLS keeps");
        assertFalse(h.run(34, globals(0x60, 0x6E0, 0x2FF)).deleted(), "camera chunk $200 is the anchor chunk");
        var gone = h.run(34, globals(0x60, 0x6E0, 0x300));
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(MAIN), call(DRAW_OBJECT),
                new CdMosqui.Despawn(ANCHOR_OFFSCREEN, 10, 1), call(CHECK_OBJ_DESPAWN2)), gone.effects());
        var observed = h.draw.observed.getLast();
        assertEquals(0x3D, observed.id());
        assertEquals(0x26C, observed.centreX(), "the live body ran and was enqueued before the anchor despawn deleted it");
        assertEquals(0, h.runtime.savedFlag(10, 1));
        assertArrayEquals(new byte[64], gone.state().copyBytes());
    }

    @Test
    void timeWarpDespawnUsesTheOldPeriodWithClamps() {
        int[][] directionToPeriod = {{1, 0}, {0xFF, 2}, {2, 0}, {0xFE, 2}, {0, 1}};
        for (int[] c : directionToPeriod) {
            var h = new Harness();
            h.place(34, 0x270, 0x730, 0, 0, 10);
            for (int period = 0; period < 3; period++) { h.runtime.bsetSavedFlag(10, period, LOADED); }
            h.run(34, far());
            var gone = h.run(34, new CdMosqui.Globals(0, 0x81, c[0], 0, 0, 0x60, 0x6E0));
            assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(MAIN), call(DRAW_OBJECT),
                    new CdMosqui.Despawn(TIME_WARP, 10, c[1]), call(CHECK_OBJ_DESPAWN2)), gone.effects(), "dir " + c[0]);
            for (int period = 0; period < 3; period++) {
                assertEquals(period == c[1] ? 0 : 0x80, h.runtime.savedFlag(10, period), "dir " + c[0] + " period " + period);
            }
            assertArrayEquals(new byte[64], gone.state().copyBytes());
        }
    }

    @Test
    void aPortBitSevenWriteIsReloadedBeforeTheDespawnTest() {
        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 0, 10);
        h.run(34, far());
        h.draw.mode = RecordingDraw.Mode.SET_BIT7;
        var kept = h.run(34, globals(0x60, 0x6E0, 0x300));
        assertFalse(kept.deleted(), "the reloaded bit 7 selects DespawnObjTimeWarp; no warp, so no despawn");
        assertEquals(0x80, kept.state().spriteFlags() & 0x80, "the port's write is final; no stale image was written back");
        assertEquals(0x26E, kept.state().centreX());
        h.draw.mode = RecordingDraw.Mode.CLEAR_BIT7;
        assertTrue(h.run(34, globals(0x60, 0x6E0, 0x300)).deleted());

        var stale = new Harness();
        stale.place(34, 0x270, 0x730, 0, 0, 10);
        stale.run(34, far());
        stale.setVisible(34);
        stale.draw.mode = RecordingDraw.Mode.UNTOUCHED;
        assertFalse(stale.run(34, globals(0x60, 0x6E0, 0x300)).deleted(),
                "a port that fails to clear bit 7 is observed literally; the body adds no clamp");
    }

    @Test
    void goodFutureConversionStopsAtItsPredicateWithoutWrites() {
        var ok = new Harness();
        ok.place(34, 0x270, 0x730, 0, 0, 10);
        var alive = ok.run(34, new CdMosqui.Globals(1, 1, 0, 0, 0, 0x60, 0x6E0));
        assertEquals(2, alive.state().routine(), "a normal Mosqui survives a good-future Present");
        assertEquals(0x26F, alive.state().centreX());

        int[][] rejected = {{1, 1, 0xFF}, {1, 0, 0}, {1, 2, 0}, {1, 0x81, 0}, {0x80, 1, 0xFF}};
        for (int[] c : rejected) {
            var h = new Harness();
            h.place(34, 0x270, 0x730, c[2], 0, 10);
            var before = h.slot(34);
            byte[] bank = h.runtime.capture().savedFlags().copy();
            var failure = assertThrows(CdMosqui.UnsupportedMosqui.class,
                    () -> h.run(34, new CdMosqui.Globals(c[0], c[1], 0, 0, 0, 0x60, 0x6E0)), Arrays.toString(c));
            assertEquals(GOOD_FUTURE_CONVERSION, failure.boundary());
            assertEquals(34, failure.wholeIndex());
            assertEquals(before, h.slot(34), "no DeleteObject, X/Y restore or $18 write");
            assertArrayEquals(bank, h.runtime.capture().savedFlags().copy());
            assertTrue(h.draw.observed.isEmpty(), "ADDQ.L #4,SP would skip DrawObject; nothing was queued");
        }
    }

    @Test
    void unsupportedRoutineOrScriptPointerStopsBeforeAnyWrite() {
        for (int routine : new int[]{1, 10, 0xFF}) {
            var h = new Harness();
            h.place(34, 0x270, 0x730, 0, 0, 10);
            h.write(h.slot(34).moveByte(ROUTINE, routine));
            var before = h.slot(34);
            var failure = assertThrows(CdMosqui.UnsupportedMosqui.class, () -> h.run(34, far()), "routine " + routine);
            assertEquals(ROUTINE_DISPATCH, failure.boundary());
            assertEquals(before, h.slot(34));
            assertTrue(h.draw.observed.isEmpty());
        }

        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 0, 10);
        h.run(34, far());
        h.write(h.slot(34).moveLong(MOSQUI_ANIMATION_SCRIPT, NORMAL_ANI + 1));
        var before = h.slot(34);
        var failure = assertThrows(CdMosqui.UnsupportedMosqui.class, () -> h.run(34, far()));
        assertEquals(ANIMATION_SCRIPT_POINTER, failure.boundary());
        assertEquals(before, h.slot(34), "Main's X add is not committed without its AnimateObject");
        assertEquals(1, h.draw.observed.size(), "only the earlier successful call reached DrawObject");

        var swapped = new Harness();
        swapped.place(34, 0x270, 0x730, 0, 0, 10);
        swapped.run(34, far());
        swapped.write(swapped.slot(34).moveLong(MOSQUI_ANIMATION_SCRIPT, DAMAGED_ANI).moveByte(ANIMATION_TIME, 0));
        var s = swapped.run(34, far()).state();
        assertEquals(4, s.unsignedByte(ANIMATION_TIME), "the stored oVar30 selects the table, not oSubtype");
        assertEquals(1, s.unsignedByte(MAP_FRAME));

        var wrongId = new Harness();
        wrongId.write(wrongId.slot(40).moveByte(ID, 0x10));
        assertThrows(IllegalArgumentException.class, () -> wrongId.run(40, far()));
        assertThrows(IllegalArgumentException.class, () -> wrongId.run(41, far()));
        assertThrows(IndexOutOfBoundsException.class, () -> wrongId.run(31, far()));
    }

    @Test
    void unreviewedTileSubtypeStopsInitBeforeTheCommit() {
        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 1, 10);
        var before = h.slot(34);
        var failure = assertThrows(UnsupportedOperationException.class, () -> h.run(34, far()));
        assertFalse(failure instanceof CdMosqui.UnsupportedMosqui, "the tile owner reports its own boundary");
        assertTrue(failure.getMessage().contains("subtype2"));
        assertEquals(before, h.slot(34));
        assertTrue(h.draw.observed.isEmpty());
        assertEquals(0x80, h.runtime.savedFlag(10, 1));
    }

    @Test
    void spawnerAllocatedMosquiUnloadsAndReloadsThroughTheSharedBank() {
        var h = new Harness();
        h.runtime.spawnObjects(0, 1);
        assertEquals(0x3D, h.slot(32).id());
        assertEquals(1, h.slot(32).savedFlagsId());
        assertEquals(0, h.slot(32).routine());
        assertEquals(0x80, h.runtime.savedFlag(1, 1));
        assertEquals(0x26F, h.run(32, far()).state().centreX());
        h.runtime.spawnObjects(0x300, 1);
        assertEquals(0x3D, h.slot(32).id(), "the spawner never frees a slot");
        var gone = h.run(32, globals(0x60, 0x6E0, 0x300));
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(MAIN), call(DRAW_OBJECT),
                new CdMosqui.Despawn(ANCHOR_OFFSCREEN, 1, 1), call(CHECK_OBJ_DESPAWN2)), gone.effects());
        assertEquals(0, h.runtime.savedFlag(1, 1));
        assertEquals(0, h.slot(32).id());
        h.runtime.spawnObjects(0x200, 1);
        assertEquals(0x3D, h.slot(32).id(), "the backward pass reloads the record into the first free slot");
        assertEquals(0x80, h.runtime.savedFlag(1, 1));
        assertEquals(0, h.slot(32).routine(), "DeleteObject zeroed the routine; Init runs again");
        assertEquals(0x270, h.slot(32).centreX());
        assertEquals(0x26F, h.run(32, far()).state().centreX());
    }

    @Test
    void captureRestoreReplayIsIdenticalAndTheBodyHoldsNoState() {
        var h = new Harness();
        h.place(34, 0x270, 0x730, 0, 0, 10);
        for (int i = 0; i < 3; i++) { h.run(34, far()); }
        var snapshot = h.runtime.capture();
        var expected = new ArrayList<CdMosqui.Result>();
        for (int i = 0; i < 5; i++) { expected.add(h.run(34, far())); }
        h.runtime.restore(snapshot);
        var replayed = new ArrayList<CdMosqui.Result>();
        for (int i = 0; i < 5; i++) { replayed.add(h.run(34, far())); }
        assertEquals(expected, replayed);
        h.runtime.restore(snapshot);
        var another = new CdMosqui(h.runtime, assets(), h.terrain.probes(), h.draw);
        var again = new ArrayList<CdMosqui.Result>();
        for (int i = 0; i < 5; i++) { again.add(another.run(34, far())); }
        assertEquals(expected, again, "the pool and bank are the only mutable owners");
        assertEquals(0x268, h.slot(34).centreX(), "three calls, then five replayed from the snapshot");

        var deleting = h.runtime.capture();
        var gone = h.run(34, globals(0x60, 0x6E0, 0x300));
        assertTrue(gone.deleted());
        h.runtime.restore(deleting);
        assertEquals(0x80, h.runtime.savedFlag(10, 1), "restore brings the bank bit back with the slot");
        assertEquals(gone, h.run(34, globals(0x60, 0x6E0, 0x300)));
    }

    @Test
    void assetsGlobalsAndEffectsValidateTheirDeclaredWidths() {
        assertEquals(60, NORMAL_MAPPINGS.length);
        assertEquals(66, DAMAGED_MAPPINGS.length);
        assertEquals(14, NORMAL_SCRIPTS.length);
        assertEquals(13, DAMAGED_SCRIPTS.length);
        var a = assets();
        assertEquals(2, a.normal().animations().size());
        assertEquals(5, a.normal().mappingFrames().size());
        assertEquals(5, a.damaged().mappingFrames().size());
        assertEquals(1, a.normal().animations().get(0).delay());
        assertEquals(0x0E, a.damaged().animations().get(1).delay());
        assertThrows(IllegalArgumentException.class,
                () -> new CdMosqui.Assets(CdAssetBytes.copyOf(new byte[73]), a.normal(), a.damaged(), 1, 2, 3, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new CdMosqui.Assets(a.objectTileIds(), a.normal(), a.damaged(), 1, 2, 3, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new CdMosqui.Assets(a.objectTileIds(), a.normal(), a.damaged(), 1, 2, 1, 4));
        assertThrows(NullPointerException.class,
                () -> new CdMosqui.Assets(null, a.normal(), a.damaged(), 1, 2, 3, 4));
        var oneScript = CdObjectVisualAssets.decode(NORMAL_MAPPINGS, 5, bytes(0x00, 0x02, 0x01, 0x00, 0x01, 0xFF), 1, 70);
        assertThrows(IllegalArgumentException.class,
                () -> new CdMosqui.Assets(a.objectTileIds(), oneScript, a.damaged(), 1, 2, 3, 4));

        assertThrows(IllegalArgumentException.class, () -> new CdMosqui.Globals(0x100, 1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdMosqui.Globals(0, 3, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdMosqui.Globals(0, 1, 0, 0x10000, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdMosqui.Globals(0, 1, 0, 0, 0, 0x10000, 0));
        assertDoesNotThrow(() -> new CdMosqui.Globals(0xFF, 0x82, 0xFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF));
        assertThrows(IllegalArgumentException.class, () -> new CdMosqui.Despawn(WAIT_OFFSCREEN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdMosqui.Despawn(WAIT_OFFSCREEN, 10, -1));
        assertThrows(IllegalArgumentException.class, () -> new CdMosqui.Despawn(WAIT_OFFSCREEN, 10, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new CdMosqui.GlobalWrite(CdMosqui.GlobalField.PRIMARY_ANGLE, 0x100));
        assertThrows(NullPointerException.class, () -> new CdMosqui(null, a, new Terrain().probes(), new RecordingDraw()));
        var runtime = new CdObjectPlacementRuntime(layout(row(0x270, 0x730, 0xBD, 0x00, 0x40, 0)));
        assertThrows(NullPointerException.class, () -> new CdMosqui(runtime, a, new Terrain().probes(), null));
        var mosqui = new CdMosqui(runtime, a, new Terrain().probes(), new RecordingDraw());
        assertSame(a, mosqui.assets());
        assertThrows(NullPointerException.class, () -> mosqui.run(34, null));
    }

    /** Opt-in supplied-media check through the production loaders; a missing property is an explicit skip. */
    @Test
    void authenticatedUsaAssetsBindBothMosquiFormsAtTheirNativeAddresses() throws Exception {
        var assets = loadUsa();
        var objects = assets.openingObjects();
        var bound = CdMosqui.Assets.fromObjectAssets(objects);
        assertEquals(NORMAL_MAP, bound.normalMappingsAddress());
        assertEquals(NORMAL_ANI, bound.normalScriptAddress());
        assertEquals(DAMAGED_MAP, bound.damagedMappingsAddress());
        assertEquals(DAMAGED_ANI, bound.damagedScriptAddress());
        assertEquals(74, bound.objectTileIds().length());
        assertArrayEquals(NORMAL_SCRIPTS, bound.normal().animationTable().copy());
        assertArrayEquals(DAMAGED_SCRIPTS, bound.damaged().animationTable().copy());
        assertArrayEquals(NORMAL_MAPPINGS, bound.normal().mappings().copy());
        assertArrayEquals(DAMAGED_MAPPINGS, bound.damaged().mappings().copy());

        var runtime = CdObjectPlacementRuntime.fromAssets(assets);
        runtime.spawnObjects(0, 1);
        var draw = new RecordingDraw();
        var mosqui = new CdMosqui(runtime, bound, new CdPlayerTerrain(assets.terrain()), draw);
        var first = mosqui.run(34, far());
        var s = first.state();
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(INIT), call(MAIN), call(DRAW_OBJECT),
                call(CHECK_OBJ_DESPAWN2)), first.effects());
        assertEquals(0x3D, s.id());
        assertEquals(10, s.savedFlagsId());
        assertEquals(0x23A0, s.word(TILE));
        assertEquals(NORMAL_MAP, s.longBits(MAPPINGS));
        assertEquals(NORMAL_ANI, s.longBits(MOSQUI_ANIMATION_SCRIPT));
        assertEquals(-0x10000, s.longBits(MOSQUI_X_DELTA));
        assertEquals(0x26F, s.centreX());
        assertEquals(0x730, s.centreY());
        assertEquals(2, s.routine());
        assertEquals(4, s.spriteFlags());
        assertEquals(0, s.unsignedByte(MAP_FRAME));
        assertEquals(1, s.unsignedByte(ANIMATION_TIME));
        assertEquals(1, s.unsignedByte(ANIMATION_FRAME));
        assertEquals(0x80, runtime.savedFlag(10, 1));

        runtime.spawnObjects(0x100, 1);
        int damagedSlot = -1;
        for (int whole = 32; whole < 128; whole++) {
            var slot = runtime.pool().state(whole);
            if (slot.id() == 0x3D && slot.subtype() == 0xFF) { damagedSlot = whole; break; }
        }
        assertTrue(damagedSlot >= 0, "common row 13 ($350,$728) is included at camera chunk $100");
        assertEquals(0x350, runtime.pool().state(damagedSlot).centreX());
        assertEquals(0x728, runtime.pool().state(damagedSlot).centreY());
        var d = mosqui.run(damagedSlot, globals(0x60, 0x6E0, 0x100)).state();
        assertEquals(DAMAGED_MAP, d.longBits(MAPPINGS));
        assertEquals(DAMAGED_ANI, d.longBits(MOSQUI_ANIMATION_SCRIPT));
        assertEquals(-0x8000, d.longBits(MOSQUI_X_DELTA));
        assertEquals(0x034F8000, d.longBits(X_POSITION));
        assertEquals(0x23A0, d.word(TILE));
        assertEquals(4, d.unsignedByte(ANIMATION_TIME));
        assertEquals(0, d.unsignedByte(MAP_FRAME));
        assertEquals(2, d.routine());
    }

    private static CdMosqui.Effect call(CdMosqui.NativeCall nativeCall) { return new CdMosqui.CallReturned(nativeCall); }
    private static CdMosqui.Globals far() { return farAt(0); }
    private static CdMosqui.Globals farAt(int cameraX) { return globals(0x60, 0x6E0, cameraX); }
    private static CdMosqui.Globals globals(int playerX, int playerY, int cameraX) {
        return new CdMosqui.Globals(0, 1, 0, 0, cameraX, playerX, playerY);
    }

    private static CdMosqui.Assets assets() {
        return new CdMosqui.Assets(CdAssetBytes.copyOf(tileIds(0x23A0)),
                CdObjectVisualAssets.decode(NORMAL_MAPPINGS, 5, NORMAL_SCRIPTS, 2, 70),
                CdObjectVisualAssets.decode(DAMAGED_MAPPINGS, 5, DAMAGED_SCRIPTS, 2, 70),
                NORMAL_MAP, NORMAL_ANI, DAMAGED_MAP, DAMAGED_ANI);
    }

    /** Constructed ObjectTileIDs: 18 index words all selecting the first data word, 19 data words. */
    private static byte[] tileIds(int mosquiTile) {
        byte[] table = new byte[74];
        for (int i = 0; i < 18; i++) { table[2 * i + 1] = 36; }
        table[36] = (byte) (mosquiTile >>> 8);
        table[37] = (byte) mosquiTile;
        return table;
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
    private static final class RecordingDraw implements CdMosqui.DrawObjectCall {
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

    /**
     * Layout cell (2,7) covers world X $200..$2FF, Y $700..$7FF with chunk 2, whose block row 8 is a
     * full-height solid floor (top at world Y $780, angle $22) across every column.
     */
    private static final class Terrain {
        final byte[] chunks = new byte[61952];
        final byte[] fg = new byte[338];
        final byte[] bg = new byte[50];
        final byte[] ids = new byte[900];
        final byte[] angles = new byte[256];
        final byte[] heights = new byte[4096];
        final byte[] widths = new byte[4096];

        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            fg[2 + 7 * 42 + 2] = 2;
            for (int column = 0; column < 16; column++) {
                int at = 512 + (8 * 16 + column) * 2;
                chunks[at] = 0x20; chunks[at + 1] = 0x01;
            }
            ids[1] = 7; angles[7] = 0x22;
            Arrays.fill(heights, 7 * 16, 7 * 16 + 16, (byte) 16);
        }
        CdPlayerTerrain probes() {
            return new CdPlayerTerrain(CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg,
                    ids, angles, heights, widths));
        }
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout(row(0x270, 0x730, 0xBD, 0x00, 0x40, 0)));
        final Terrain terrain = new Terrain();
        final RecordingDraw draw = new RecordingDraw();
        final CdMosqui mosqui = new CdMosqui(runtime, assets(), terrain.probes(), draw);

        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot(int whole) { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }
        CdMosqui.Result run(int whole, CdMosqui.Globals globals) { return mosqui.run(whole, globals); }
        void setVisible(int whole) { write(slot(whole).moveByte(SPRITE_FLAGS, slot(whole).spriteFlags() | 0x80)); }

        /** SpawnObject's writes only (Object Spawner.asm:267-319); every other byte keeps the slot's contents. */
        void place(int whole, int x, int packedY, int subtype, int subtype2, int savedId) {
            int rotated = ((packedY << 2) | (packedY >>> 14)) & 0xFFFF;
            int flips = rotated & 3;
            var state = slot(whole).moveWord(X, x).moveWord(Y, packedY & 0xFFF)
                    .moveByte(SPRITE_FLAGS, flips).moveByte(FLAGS, flips);
            if (savedId != 0) {
                state = state.moveByte(SAVED_FLAGS_ID, savedId);
                runtime.bsetSavedFlag(savedId, 1, LOADED);
            }
            write(state.moveByte(ID, 0x3D).moveByte(SUBTYPE, subtype).moveByte(SUBTYPE2, subtype2));
        }
    }
}
