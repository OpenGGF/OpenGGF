package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests authored/frozen before CdSpring and its five state aliases. ALL JAVA UNRUN by this external author.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Level/_Objects/Spring/Main.asm:130-409;
 * Object Solid.asm:159-479 and Object Animate.asm:14-95. Exact literal includes, not a Spring model oracle.
 * Solid is the real shared pure owner over the existing format-decoder fixture; no injected contact answer.
 * The synchronous stand-ins publish values once and assert expected cuts. No media, World, renderer or Frame.
 */
class TestCdSpring {
    private static final int WHOLE = 40;
    private static final int MAP = 0x00CD1000; // Constructed marker; production must obtain the real view address.
    private static final byte[] ANI = bytes(0, 2, 0, 0, 1, 1, 2, 2, 2, 2, 2, 2, 1, 0xFC);
    /** Pinned Spring/Data/Mappings.asm:1-82 plus Main.asm:496 EVEN, all 200 bytes. */
    private static final byte[] MAPS = bytes(0x00, 0x0C, 0x00, 0x18, 0x00, 0x1E, 0x00, 0x28, 0x00, 0x34, 0x00, 0x3A, 0x02, 0xF8, 0x0C, 0x00,
                0x00, 0xF0, 0x00, 0x0C, 0x00, 0x04, 0xF0, 0x00, 0x01, 0x00, 0x0C, 0x00, 0x00, 0xF0, 0x03, 0xE0,
                0x0C, 0x00, 0x00, 0xF0, 0xE8, 0x06, 0x00, 0x08, 0xF8, 0x00, 0x0C, 0x00, 0x0E, 0xF0, 0x02, 0xF0,
                0x03, 0x00, 0x12, 0x00, 0xF0, 0x03, 0x00, 0x16, 0xF8, 0x00, 0x01, 0xF0, 0x03, 0x00, 0x12, 0xF8,
                0x03, 0xF0, 0x03, 0x00, 0x12, 0x18, 0xF8, 0x09, 0x00, 0x1A, 0x00, 0xF0, 0x03, 0x00, 0x20, 0xF8,
                0x00, 0x06, 0x00, 0x2A, 0x00, 0x4E, 0x07, 0x08, 0x00, 0x00, 0x00, 0xF0, 0x00, 0x00, 0x00, 0x01,
                0xF0, 0x08, 0x00, 0x00, 0x02, 0xF8, 0xF0, 0x08, 0x00, 0x03, 0xF0, 0xF8, 0x00, 0x00, 0x06, 0xF0,
                0xF8, 0x09, 0x00, 0x07, 0xF8, 0x08, 0x04, 0x00, 0x0D, 0x00, 0x07, 0x08, 0x00, 0x00, 0x00, 0xF0,
                0x00, 0x00, 0x00, 0x0F, 0xF0, 0x08, 0x00, 0x00, 0x10, 0xF8, 0xF0, 0x05, 0x00, 0x11, 0xF0, 0xF8,
                0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00, 0x15, 0xF8, 0x00, 0x05, 0x00, 0x16, 0x00, 0x08, 0x08,
                0x00, 0x00, 0x00, 0xF0, 0x00, 0x00, 0x00, 0x1A, 0xF0, 0x08, 0x00, 0x00, 0x1B, 0xF8, 0xE0, 0x08,
                0x00, 0x03, 0x00, 0xE8, 0x0E, 0x00, 0x1C, 0x00, 0xF8, 0x00, 0x00, 0x28, 0xF0, 0xF0, 0x02, 0x00,
                0x29, 0xF8, 0x00, 0x01, 0x00, 0x2C, 0x00, 0x00);
    private static final Set<CdSpring.LaunchPlayerField> VERTICAL = Set.of(
            CdSpring.LaunchPlayerField.CENTRE_Y, CdSpring.LaunchPlayerField.Y_VELOCITY,
            CdSpring.LaunchPlayerField.FLAGS);

    @Test void aliasesNameOnlyTheFiveNativeStorageLocations() {
        assertEquals(0x30, SPRING_SPEED.byteOffset());
        assertEquals(0x34, SPRING_PARENT.byteOffset());
        assertEquals(0x36, SPRING_ANCHOR_X.byteOffset());
        assertEquals(0x38, SPRING_PARENT_X_OFFSET.byteOffset());
        assertEquals(0x39, SPRING_PARENT_Y_OFFSET.byteOffset());
        assertEquals(0x3E, CdSpring.LaunchPlayerField.MOVE_LOCK.byteOffset());
        var h = new Harness();
        h.edit(o -> o.moveWord(SPRING_SPEED, 0xF600).moveWord(SPRING_PARENT, 0xD840)
                .moveByte(SPRING_PARENT_X_OFFSET, 0x80).moveByte(SPRING_PARENT_Y_OFFSET, 0x7F));
        assertEquals(-2560, h.slot().signedWord(SPRING_SPEED));
        assertEquals(-128, h.slot().signedByte(SPRING_PARENT_X_OFFSET));
        assertEquals(127, h.slot().signedByte(SPRING_PARENT_Y_OFFSET));
    }

    @Test void initRunsOffscreenButReturnsWithoutMainSolidOrAnimation() {
        var h = new Harness();
        h.edit(o -> o.moveByte(ID, 0x0A).moveByte(SPRITE_FLAGS, 0x11).moveByte(FLAGS, 0xC1)
                .moveWord(X, 0x100).moveWord(Y, 0x80).moveWord(X_FRACTION, 0x4321)
                .moveWord(Y_FRACTION, 0x8765).moveByte(ANIMATION_TIME, 7).moveByte(MAP_FRAME, 2)
                .moveByte(ROUTINE_2, 0x7A).moveByte(SUBTYPE2, 255));
        var before = h.slot();
        var expected = before.moveByte(ROUTINE, 2).moveLong(MAPPINGS, MAP).moveWord(TILE, 0x0520)
                .moveByte(SPRITE_FLAGS, 0x15).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 8)
                .moveWord(SPRING_ANCHOR_X, 0x100).moveByte(PRIORITY, 4).moveWord(SPRING_SPEED, 0xF000);
        var r = h.run();
        assertEquals(List.of("INIT", "DRAW_OBJECT"), trace(r));
        assertEquals(expected, h.draws.getFirst());
        assertEquals(expected, r.state());
        assertTrue(h.solids.isEmpty()); assertTrue(h.launches.isEmpty()); assertTrue(h.sounds.isEmpty());
        assertEquals(h.player, r.finalPlayer());
    }

    @ParameterizedTest
    @CsvSource({"0,0,2,16,8,0,1312,-4096,0", "2,0,2,16,8,0,9504,-2560,0",
            "0,2,14,16,8,0,1312,-4096,2", "4,2,8,8,16,6,1312,-4096,0",
            "6,0,8,8,16,6,9504,-2560,0", "12,2,8,8,16,6,1312,-4096,0",
            "14,0,8,8,16,6,9504,-2560,0", "243,0,2,16,8,0,9504,-2560,0"})
    void initPrioritiesAndPaletteHighByteFollowNativeSubtypeOrder(int subtype, int spr, int routine, int width,
            int radius, int mapOffset, int tile, int speed, int flags) {
        var h = new Harness(); h.edit(o -> o.moveByte(ID, 0x0A).moveWord(X, 0x100).moveWord(Y, 0x80)
                .moveByte(SUBTYPE, subtype).moveByte(SPRITE_FLAGS, spr));
        h.run();
        assertEquals(routine, h.slot().routine()); assertEquals(width, h.slot().unsignedByte(WIDTH));
        assertEquals(radius, h.slot().unsignedByte(Y_RADIUS)); assertEquals(MAP + mapOffset, h.slot().longBits(MAPPINGS));
        assertEquals(tile, h.slot().word(TILE)); assertEquals(speed, h.slot().signedWord(SPRING_SPEED));
        assertEquals(flags, h.slot().flags());
    }

    @Test void diagonalInitStopsAtSelectorFWithTheExactAlreadyWrittenPrefix() {
        var h = new Harness(); h.edit(o -> o.moveByte(ID, 0x0A).moveWord(X, 0x100).moveWord(Y, 0x80)
                .moveByte(SUBTYPE, 0x0A).moveWord(SPRING_SPEED, 0x1234));
        var before = h.slot();
        var e = assertThrows(CdSpring.UnsupportedSpring.class, h::run);
        assertEquals(CdSpring.UnsupportedBoundary.DIAGONAL_TILE_SELECTOR, e.boundary());
        assertEquals(15, e.value());
        assertEquals(before.moveByte(ROUTINE, 20).moveLong(MAPPINGS, MAP + 80).moveWord(TILE, 0x520)
                .moveByte(SPRITE_FLAGS, 4).moveByte(WIDTH, 24).moveByte(Y_RADIUS, 12)
                .moveWord(SPRING_ANCHOR_X, 0x100).moveByte(PRIORITY, 4), h.slot());
        assertTrue(h.draws.isEmpty()); assertTrue(h.solids.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0, 2})
    void upUsesPlainSolidAndIntegerYLaunchWithTheAuthenticSignedSpeed(int subtype) {
        var h = prepared(2); h.edit(o -> o.moveWord(SPRING_SPEED, subtype == 0 ? 0xF000 : 0xF600));
        var p = new P(); p.y = 0x00671234; p.gv = 0x345; p.charge = 17; h.player = p.player();
        var r = h.run();
        var call = h.solids.getFirst(); var launch = h.launches.getFirst();
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT, call.result().entry());
        assertEquals(CdSolidObject.Path.STAND_ON, call.result().path());
        assertEquals(List.of(new CdSolidObject.RoutineCall(CdSolidObject.Routine.STAND_ON_OBJECT)), call.result().calls());
        assertEquals(0x00651234, launch.expectedPlayer().actor().motion().centreY16_16());
        p.y = 0x006D1234; p.yv = subtype == 0 ? -4096 : -2560; p.flags = 2;
        p.anim = 0x10; p.angle = 0; p.jump = 0; p.stand = WHOLE;
        assertEquals(p.player(), h.player, "all other actor/slot fields, fractions and charge remain exact");
        var writes = EnumSet.copyOf(VERTICAL); writes.add(CdSpring.LaunchPlayerField.ANIMATION);
        assertEquals(writes, launch.playerWrites()); assertTrue(launch.globalWrites().isEmpty());
        assertEquals(call.result().player(), launch.expectedPlayer());
        assertEquals(call.result().globals(), launch.expectedGlobals());
        assertEquals(List.of("MAIN_UP", "SOLID_OBJECT:STAND_ON", "LAUNCH", "FM_SPRING", "DRAW_OBJECT"), trace(r));
        assertEquals(4, r.state().routine()); assertEquals(0x84, h.draws.getFirst().spriteFlags());
        assertEquals(0, r.state().flags() & 8); assertEquals(h.globals, r.finalGlobals());
        assertEquals(List.of(CdSpring.FmSound.FM_SPRING), h.sounds);
    }

    @ParameterizedTest @CsvSource({"0,0,4096,230,0", "1,0,-4096,246,1", "0,4,4096,230,4", "1,4,-4096,246,5"})
    void sideUsesObjectPushFlagNotD0AndKeepsRollingAnimation(int flip, int rolling, int speed, int x, int flags) {
        var h = prepared(8); h.edit(o -> o.moveByte(FLAGS, flip));
        var p = new P(); p.x = 0x00F08000; p.y = 0x00801234; p.yv = 0; p.flags = rolling;
        p.anim = 0x1A; p.charge = 9; h.player = p.player();
        var r = h.run(); var solid = h.solids.getFirst().result(); var launch = h.launches.getFirst();
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT, solid.entry());
        assertEquals(CdSolidObject.Path.HALT_ON_X, solid.path()); assertEquals(0, solid.d0().orElseThrow());
        assertEquals(0x20, solid.object().flags() & 0x20);
        p.x = (x << 16) | 0x8000; p.xv = speed; p.gv = speed; p.lock = 15; p.angle = 0;
        p.flags = flags; if (rolling == 0) p.anim = 0;
        assertEquals(p.player(), h.player);
        var writes = EnumSet.of(CdSpring.LaunchPlayerField.CENTRE_X, CdSpring.LaunchPlayerField.X_VELOCITY,
                CdSpring.LaunchPlayerField.FLAGS, CdSpring.LaunchPlayerField.MOVE_LOCK,
                CdSpring.LaunchPlayerField.GROUND_VELOCITY, CdSpring.LaunchPlayerField.ANGLE);
        if (rolling == 0) writes.add(CdSpring.LaunchPlayerField.ANIMATION);
        assertEquals(writes, launch.playerWrites()); assertEquals(10, r.state().routine());
        assertEquals(flip, r.state().flags());
        assertEquals(List.of("MAIN_SIDE", "SOLID_OBJECT:HALT_ON_X", "LAUNCH", "FM_SPRING", "DRAW_OBJECT"), trace(r));
    }

    @Test void sideDoesNotLaunchForD0OneWithoutObjectFlagFive() {
        var h = prepared(8); var p = new P(); p.y = 0x005F1234; h.player = p.player();
        h.run(); assertEquals(1, h.solids.getFirst().result().d0().orElseThrow());
        assertTrue(h.launches.isEmpty()); assertTrue(h.sounds.isEmpty()); assertEquals(8, h.slot().routine());
    }

    @Test void sideCanLaunchFromStaleObjectFlagFiveEvenWithGetOffD0Zero() {
        var h = prepared(8); h.edit(o -> o.moveByte(FLAGS, 0x20));
        var p = new P(); p.x = 0x02008000; p.flags = 0; h.player = p.player();
        h.run(); assertEquals(CdSolidObject.Path.GET_OFF, h.solids.getFirst().result().path());
        assertEquals(1, h.launches.size()); assertEquals(0x01F88000, h.player.actor().motion().centreX16_16());
    }

    @ParameterizedTest @CsvSource({"0,4096", "2,2560"})
    void downUsesBottomSolidAndFallsThroughSoundIntoTheFirstAnimationFrame(int subtype, int speed) {
        var h = prepared(14); h.edit(o -> o.moveWord(SPRING_SPEED, subtype == 0 ? 0xF000 : 0xF600)
                .moveByte(FLAGS, 2).moveByte(SPRITE_FLAGS, 0x86).moveByte(ROUTINE_2, 0xA7));
        var p = new P(); p.y = 0x009B1234; p.yv = -0x400; p.anim = 7; h.player = p.player();
        var r = h.run(); var solid = h.solids.getFirst().result(); var launch = h.launches.getFirst();
        assertEquals(CdSolidObject.Entry.BTM_SOLID_OBJECT, solid.entry());
        assertEquals(CdSolidObject.Path.HIT_BOTTOM, solid.path());
        assertEquals(0x009D1234, launch.expectedPlayer().actor().motion().centreY16_16());
        p.y = 0x00951234; p.yv = speed; assertEquals(p.player(), h.player);
        assertEquals(VERTICAL, launch.playerWrites()); assertTrue(launch.globalWrites().isEmpty());
        assertEquals(0, r.state().unsignedByte(ROUTINE_2)); assertEquals(1, r.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(0, r.state().unsignedByte(MAP_FRAME)); assertEquals(16, r.state().routine());
        assertEquals(List.of("MAIN_DOWN", "BTM_SOLID_OBJECT:HIT_BOTTOM", "LAUNCH", "FM_SPRING", "ANIM_DOWN",
                "ANIMATE_OBJECT", "DRAW_OBJECT"), trace(r));
    }

    @ParameterizedTest @ValueSource(ints = {0, 17})
    void sharedGetOffChargeStopPrecedesDownLaunchAndNeverClearsCharge(int charge) {
        var h = prepared(14); h.edit(o -> o.moveByte(FLAGS, 0x0A).moveByte(SPRITE_FLAGS, 0x86));
        var p = new P(); p.y = 0x009B1234; p.yv = -0x400; p.flags = 0x0A; p.stand = WHOLE;
        p.charge = charge; p.ctrl = 1; p.stick = 1; h.player = p.player(); h.run();
        assertEquals(charge == 0 ? List.of() : List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),
                h.solids.getFirst().result().fmRequests());
        assertEquals(charge, h.player.actor().motion().charge());
        assertEquals(0, h.launches.getFirst().expectedPlayer().standObject());
        assertEquals(List.of("SOLID", "LAUNCH", "SOUND", "DRAW"), h.order);
    }

    @Test void plainSolidActuallyReadsAndClearsDirtyRoutineTwo() {
        var h = prepared(2); h.edit(o -> o.moveByte(ROUTINE_2, 1));
        var p = new P(); p.y = 0x00671234; p.yv = -0x100; h.player = p.player(); h.run();
        assertEquals(CdSolidObject.GetOffReason.TOP_SOLID_RISING,
                h.solids.getFirst().result().getOffReason().orElseThrow());
        assertEquals(0, h.slot().unsignedByte(ROUTINE_2)); assertTrue(h.launches.isEmpty());
    }

    @Test void launchUsesTheRealSolidsNativeVelocityAdvectionFractionsAndChangedGlobals() {
        var h = prepared(2); h.edit(o -> o.moveWord(X_VELOCITY, 0x123).moveWord(Y_VELOCITY, -0x180));
        h.run(); var solid = h.solids.getFirst().result(); var launch = h.launches.getFirst();
        assertEquals(0x0101A300, solid.player().actor().motion().centreX16_16()); // $100.8000 + $123 << 8.
        assertEquals(0x00639234, solid.player().actor().motion().centreY16_16()); // $65.1234 - $180 << 8.
        assertEquals(0x006B9234, h.player.actor().motion().centreY16_16()); // Native ADDQ.W #8, fraction retained.
        assertEquals(0x0101A300, h.player.actor().motion().centreX16_16());
        assertEquals(0xC0, launch.expectedGlobals().primaryAngle());
        assertEquals(0xC0, launch.expectedGlobals().secondaryAngle());
        assertEquals(solid.globals(), h.globals); assertEquals(solid.globals(), launch.finalGlobals());
        assertEquals(1, solid.calls().stream().filter(CdSolidObject.RoomInFront.class::isInstance).count());
        assertTrue(solid.calls().stream().noneMatch(CdSolidObject.FloorDistance.class::isInstance));
        assertTrue(solid.calls().stream().noneMatch(CdSolidObject.CeilingDistance.class::isInstance));
    }

    @ParameterizedTest @ValueSource(ints = {4, 10, 16})
    void tenDelayZeroFramesThenFcThenResetCompleteEachNormalCycle(int routine) {
        var h = prepared(routine); h.edit(o -> o.moveByte(PREVIOUS_ANIMATION, 1).moveByte(FLAGS, 0x2A));
        int[] expected = {0, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1}; // Native script + FC's held frame; not 25 frames.
        for (int i = 0; i < expected.length; i++) {
            h.onScreen(); var r = h.run();
            assertEquals(expected[i], r.state().unsignedByte(MAP_FRAME), "animation call " + (i + 1));
            assertEquals(i < 10 ? routine : routine + 2, r.state().routine());
            assertEquals(i < 10 ? i + 1 : 10, r.state().unsignedByte(ANIMATION_FRAME));
            assertEquals(0, r.state().unsignedByte(ANIMATION_TIME));
            assertEquals(0, r.state().unsignedByte(PREVIOUS_ANIMATION));
            assertEquals(2, r.state().spriteFlags() & 3); // Source flags -> sprite flips, even for sideways state.
        }
        h.onScreen(); h.run(); assertEquals(routine - 2, h.slot().routine());
        assertEquals(1, h.slot().unsignedByte(PREVIOUS_ANIMATION)); assertEquals(0, h.slot().unsignedByte(MAP_FRAME));
        assertEquals(routine == 4 ? 0x22 : 0x2A, h.slot().flags());
        assertTrue(h.solids.isEmpty()); assertTrue(h.launches.isEmpty());
        h.edit(o -> o.moveByte(ROUTINE, routine)); h.onScreen(); h.run();
        assertEquals(1, h.slot().unsignedByte(ANIMATION_FRAME), "real previous-animation reset restarts the script");
    }

    @Test void positiveAnimationTimerHoldsEvenDirtyMapFrameWithoutLookingUpDirtyAnimation() {
        var h = prepared(4); h.edit(o -> o.moveByte(ANIMATION, 250).moveByte(PREVIOUS_ANIMATION, 250)
                .moveByte(ANIMATION_TIME, 1).moveByte(MAP_FRAME, 255));
        h.run(); assertEquals(0, h.slot().unsignedByte(ANIMATION_TIME)); assertEquals(255, h.slot().unsignedByte(MAP_FRAME));
        h.onScreen(); var before = h.slot(); assertThrows(UnsupportedOperationException.class, h::run);
        assertEquals(before, h.slot(), "shared pure animation failure publishes no uncompleted stores");
    }

    @ParameterizedTest @ValueSource(ints = {1, 3, 20, 22, 24, 26, 255})
    void offscreenNonzeroRoutineSkipsOnlyControlAndStillDrawsAndChecksAnchor(int routine) {
        var h = prepared(routine); h.edit(o -> o.moveByte(SPRITE_FLAGS, 4));
        var r = h.run(); assertEquals(List.of("DRAW_OBJECT"), trace(r)); assertEquals(routine, h.slot().routine());
        assertTrue(h.solids.isEmpty()); assertTrue(h.launches.isEmpty());
        h.edit(o -> o.moveWord(SPRING_ANCHOR_X, 0x380));
        assertTrue(h.run().deleted());
    }

    @ParameterizedTest @ValueSource(ints = {1, 3, 20, 22, 24, 26, 255})
    void invalidOrDiagonalIndicesStopOnlyWhenTheControlTableIsActuallyReached(int routine) {
        var h = prepared(routine); var before = h.slot();
        var e = assertThrows(CdSpring.UnsupportedSpring.class, h::run);
        assertEquals(routine == 20 || routine == 22 || routine == 24
                ? CdSpring.UnsupportedBoundary.DIAGONAL_ROUTINE : CdSpring.UnsupportedBoundary.ROUTINE_DISPATCH, e.boundary());
        assertEquals(before, h.slot()); assertTrue(h.draws.isEmpty());
    }

    @Test void drawCullUsesOldPositionButParentReadsUseTheLaterLivePositionAndSignedOffsets() {
        var h = prepared(6); h.edit(o -> o.moveWord(SPRING_PARENT, 0xDA40)
                .moveByte(SPRING_PARENT_X_OFFSET, 0x80).moveByte(SPRING_PARENT_Y_OFFSET, 0x7F)); // whole41
        h.pool().write(h.pool().state(41).moveByte(ID, 8).moveWord(X, 0x0200).moveWord(Y, 0x0300));
        h.afterDraw = () -> h.pool().write(h.pool().state(41).moveWord(X, 0x0040).moveWord(Y, 0xFFFF));
        var r = h.run(); assertEquals(0x0100, h.draws.getFirst().centreX()); assertEquals(0x80, h.draws.getFirst().centreY());
        assertEquals(0xFFC0, r.state().centreX()); assertEquals(0x007E, r.state().centreY());
        assertEquals(0x4321, r.state().word(X_FRACTION)); assertEquals(0x8765, r.state().word(Y_FRACTION));
        assertFalse(r.deleted(), "chunk check uses anchor $100, not the late parent X");
        assertEquals(List.of("RESET_UP", "DRAW_OBJECT", "FOLLOW_PARENT"), trace(r));
    }

    @Test void exactPlayerParentReadsCurrentProjectionWithoutInventingAReservedSlotImage() {
        var h = prepared(6); h.edit(o -> o.moveWord(SPRING_PARENT, 0xD000).moveByte(SPRING_PARENT_X_OFFSET, 1)
                .moveByte(SPRING_PARENT_Y_OFFSET, 0xFF));
        h.run(); assertEquals(0x101, h.slot().centreX()); assertEquals(0x66, h.slot().centreY());
    }

    @ParameterizedTest @CsvSource({"53312,16765000", "55873,16767561", "61440,16773128"})
    void unsupportedParentReadStopsAtItsActualAddressAfterDrawAndReset(int parent, int address) {
        var h = prepared(6); h.edit(o -> o.moveWord(SPRING_PARENT, parent));
        var e = assertThrows(CdSpring.UnsupportedSpring.class, h::run);
        assertEquals(CdSpring.UnsupportedBoundary.PARENT_READ, e.boundary());
        assertEquals(address, e.readAddress24().orElseThrow());
        assertEquals(2, h.slot().routine()); assertEquals(1, h.slot().unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(1, h.draws.size()); assertEquals(0x100, h.slot().centreX());
    }

    @ParameterizedTest @CsvSource({"640,128,false", "768,128,true", "65535,0,false", "0,128,false", "0,512,true"})
    void anchorChunkWindowUsesNativeWrappedWordsAndUnsignedBhi(int anchor, int camera, boolean deleted) {
        var h = prepared(6); h.edit(o -> o.moveWord(SPRING_ANCHOR_X, anchor).moveByte(SAVED_FLAGS_ID, 7));
        var saved = h.runtime.capture().savedFlags(); var r = h.run(camera);
        assertEquals(deleted, r.deleted());
        assertArrayEquals(saved.copy(), h.runtime.capture().savedFlags().copy(), "plain DeleteObject has no saved-bank write");
        if (deleted) assertArrayEquals(new byte[64], h.slot().copyBytes());
    }

    @Test void mirrorSpringInitFallsThroughCopiesPlayerAndAvoidsWrapperParentAndDespawn() {
        var h = new Harness(); h.edit(o -> o.moveByte(ID, 0x0A).moveByte(ROUTINE_2, 5).moveByte(SPRITE_FLAGS, 1)
                .moveWord(SPRING_PARENT, 0xD040).moveWord(SPRING_ANCHOR_X, 0x8000).moveWord(X_FRACTION, 0x4567)
                .moveWord(Y_FRACTION, 0x89AB));
        var r = h.run(0x8000);
        assertEquals(List.of("MIRROR_INIT", "MIRROR_MAIN", "DRAW_OBJECT"), trace(r));
        assertEquals(MAP, h.slot().longBits(MAPPINGS)); assertEquals(0x8520, h.slot().word(TILE));
        assertEquals(0x100, h.slot().centreX()); assertEquals(0x67, h.slot().centreY());
        assertEquals(0x4567, h.slot().word(X_FRACTION)); assertEquals(0x89AB, h.slot().word(Y_FRACTION));
        assertEquals(5, h.slot().unsignedByte(ROUTINE_2)); assertFalse(r.deleted());
        assertTrue(h.solids.isEmpty());
        assertEquals(List.of("MIRROR_MAIN", "DRAW_OBJECT"), trace(h.run(0)));
    }

    @Test void mirrorRoutineValidationPrecedesOrdinaryOffscreenGate() {
        var h = prepared(4); h.edit(o -> o.moveByte(ROUTINE_2, 5).moveByte(SPRITE_FLAGS, 4));
        assertEquals(CdSpring.UnsupportedBoundary.MIRROR_ROUTINE_DISPATCH,
                assertThrows(CdSpring.UnsupportedSpring.class, h::run).boundary());
        assertTrue(h.draws.isEmpty());
    }

    @Test void liveCutIncludesOtherSlotAndBothObjectImagesMustBePublishedBeforeLaunch() {
        var h = prepared(2); var p = new P(); p.flags = 8; p.stand = 41; h.player = p.player();
        h.pool().write(h.pool().state(41).moveByte(ID, 0x12).moveByte(FLAGS, 0xA8)); h.run();
        var call = h.solids.getFirst(); assertEquals(0xA8, call.callCut().slots().get(9).flags());
        assertEquals(1, call.result().otherSlots().size()); assertEquals(0xA0, h.pool().state(41).flags());
        assertEquals(call.result().player(), h.launches.getFirst().expectedPlayer());
        var bad = prepared(2); var q = new P(); q.flags = 8; q.stand = 41; bad.player = q.player();
        bad.pool().write(bad.pool().state(41).moveByte(ID, 0x12).moveByte(FLAGS, 8)); bad.publishOthers = false;
        assertThrows(IllegalStateException.class, bad::run); assertTrue(bad.launches.isEmpty());
    }

    @Test void missingSolidPublicationStopsBeforeLaunchWithoutRollback() {
        var h = prepared(2); h.publishSolid = false; var before = h.slot();
        assertThrows(IllegalStateException.class, h::run); assertEquals(before, h.slot());
        assertEquals(1, h.solids.size()); assertTrue(h.launches.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void pureSolidFailurePublishesNoSolidOrLaunchAndLeavesEarlierBodyPrefix() {
        var h = prepared(2); var p = new P(); p.flags = 8; p.stand = 7; h.player = p.player();
        var before = h.slot(); assertThrows(CdSolidObject.UnsupportedSolidObject.class, h::run);
        assertEquals(before, h.slot()); assertTrue(h.solids.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void launchFailureKeepsSolidAndPublishedObjectActionPrefixButDoesNotPlaySoundOrDraw() {
        var h = prepared(2); h.failLaunch = true;
        assertThrows(IllegalStateException.class, h::run);
        assertEquals(4, h.slot().routine()); assertEquals(0, h.slot().flags() & 8);
        assertEquals(h.solids.getFirst().result().player(), h.player);
        assertEquals(1, h.launches.size()); assertTrue(h.sounds.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void soundFailureKeepsLaunchButDownDoesNotReachAnimationOrDraw() {
        var h = prepared(14); var p = new P(); p.y = 0x009B1234; p.yv = -0x400; h.player = p.player();
        h.failSound = true; assertThrows(IllegalStateException.class, h::run);
        assertEquals(16, h.slot().routine()); assertEquals(0, h.slot().unsignedByte(ANIMATION_FRAME));
        assertEquals(4096, h.player.actor().motion().yVelocity()); assertTrue(h.draws.isEmpty());
        assertEquals(List.of("SOLID", "LAUNCH", "SOUND"), h.order);
    }

    @Test void drawFailureKeepsResetPrefixAndItsOwnBitClearWithoutParentOrChunkWork() {
        var h = prepared(6); h.edit(o -> o.moveWord(SPRING_PARENT, 0xD040).moveWord(SPRING_ANCHOR_X, 0x8000));
        h.failDraw = true; assertThrows(IllegalStateException.class, h::run);
        assertEquals(2, h.slot().routine()); assertEquals(4, h.slot().spriteFlags()); assertEquals(0x100, h.slot().centreX());
    }

    @Test void boundSpeedBytesAreConsumedInsteadOfAnEmbeddedLaunchTable() {
        var h = new Harness(assets(bytes(0xFE, 0xDC, 0x87, 0x65))); h.edit(o -> normal(o, 0).moveByte(SUBTYPE, 2));
        h.run(); assertEquals((short) 0x8765, h.slot().signedWord(SPRING_SPEED));
        h.onScreen(); h.run(); assertEquals((short) 0x8765, h.player.actor().motion().yVelocity());
    }

    @Test void assetsKeepTheFullSharedIncludeScriptAndIndependentRoots() throws Exception {
        var a = assets(bytes(0xF0, 0, 0xF6, 0));
        assertEquals(200, MAPS.length); assertEquals(14, ANI.length);
        assertEquals("09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c", sha(MAPS));
        assertEquals("7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5", sha(ANI));
        assertEquals(0, a.vertical().mappingTableOffset()); assertEquals(6, a.horizontal().mappingTableOffset());
        assertArrayEquals(MAPS, a.vertical().mappings().copy()); assertArrayEquals(MAPS, a.horizontal().mappings().copy());
        for (int i = 0; i < 256; i++) assertEquals((i & 2) == 0 ? -4096 : -2560, a.speedWord(i));
        assertThrows(IllegalArgumentException.class, () -> a.speedWord(-1));
        assertThrows(IllegalArgumentException.class, () -> a.speedWord(256));
        assertThrows(IllegalArgumentException.class, () -> assets(bytes(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new CdSpring.Assets(a.horizontal(), a.vertical(), MAP, a.speedWords()));
        assertThrows(IllegalArgumentException.class, () -> new CdSpring.Assets(a.vertical(), a.horizontal(), 0xFFFFFFFF, a.speedWords()));
    }

    @Test void explicitInputWidthsWrongIdsAndNullPortsRejectWithoutInventedDefaults() {
        var h = new Harness(); assertThrows(IllegalArgumentException.class, h::run);
        assertThrows(IllegalArgumentException.class, () -> new CdSpring.Globals(-1));
        assertThrows(IllegalArgumentException.class, () -> new CdSpring.Globals(65536));
        assertThrows(NullPointerException.class, () -> new CdSpring.SolidInputs(null, h.gates, h.globals));
        assertThrows(NullPointerException.class, () -> h.spring.run(WHOLE, null, h.inputs()));
        assertThrows(NullPointerException.class, () -> h.spring.run(WHOLE, new CdSpring.Globals(128), null));
        assertThrows(NullPointerException.class, () -> new CdSpring(h.runtime, assets(bytes(0xF0, 0, 0xF6, 0)),
                h.solid, h::solidCommit, null, h::draw, h::sound));
    }

    @Test void launchMetadataIsImmutableCompletedAndDoesNotPermitFractionStores() {
        var h = prepared(2); var r = h.run(); var call = h.launches.getFirst();
        assertThrows(UnsupportedOperationException.class, () -> call.playerWrites().clear());
        assertThrows(UnsupportedOperationException.class, () -> call.globalWrites().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
        assertEquals(9, CdSpring.LaunchPlayerField.values().length);
        assertEquals(call.expectedPlayer().actor().motion().centreX16_16() & 0xFFFF,
                call.finalPlayer().actor().motion().centreX16_16() & 0xFFFF);
        assertEquals(call.expectedPlayer().actor().motion().centreY16_16() & 0xFFFF,
                call.finalPlayer().actor().motion().centreY16_16() & 0xFFFF);
        assertEquals(List.of("SOLID", "LAUNCH", "SOUND", "DRAW"), h.order);
    }

    private static Harness prepared(int routine) {
        var h = new Harness(); h.edit(o -> normal(o, routine)); return h;
    }
    private static CdObjectState normal(CdObjectState o, int routine) {
        return o.moveByte(ID, 0x0A).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, 0x84)
                .moveWord(X, 0x100).moveWord(Y, 0x80).moveWord(X_FRACTION, 0x4321).moveWord(Y_FRACTION, 0x8765)
                .moveWord(SPRING_ANCHOR_X, 0x100).moveWord(SPRING_SPEED, 0xF000).moveLong(MAPPINGS, MAP)
                .moveWord(TILE, 0x520).moveByte(WIDTH, routine >= 8 && routine <= 12 ? 8 : 16)
                .moveByte(Y_RADIUS, routine >= 8 && routine <= 12 ? 16 : 8).moveByte(PRIORITY, 4);
    }

    private static List<String> trace(CdSpring.Result r) {
        return r.effects().stream().map(e -> switch (e) {
            case CdSpring.RoutineEntered x -> x.routine().name();
            case CdSpring.Solid x -> x.result().entry().name() + ":" + x.result().path().name();
            case CdSpring.Launched x -> "LAUNCH";
            case CdSpring.PlayedFm x -> x.sound().name();
            case CdSpring.CallReturned x -> x.call().name();
            case CdSpring.FollowedParent x -> "FOLLOW_PARENT";
            case CdSpring.Deleted x -> "DELETE";
        }).toList();
    }

    private static CdSpring.Assets assets(byte[] speeds) {
        return new CdSpring.Assets(CdObjectVisualAssets.decodeIndexed(MAPS, 0, 3, 12, 46, ANI, 1, 36),
                CdObjectVisualAssets.decodeIndexed(MAPS, 6, 3, 46, 80, ANI, 1, 36), MAP, CdAssetBytes.copyOf(speeds));
    }
    private static String sha(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length]; for (int i = 0; i < values.length; i++) out[i] = (byte) values[i]; return out;
    }

    private static CdObjectPlacements layout() {
        var b = ByteBuffer.allocate(34);
        b.putShort((short) 12).putShort((short) 28).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        b.putShort((short) 0x6F8).putShort((short) 0x200).put((byte) 0x0A).put((byte) 4).put((byte) 0x40).put((byte) 0);
        b.putShort((short) 0xFFFF).putInt(0).putShort((short) 0); b.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(b.array());
    }

    /** Live external publication stand-in. DrawObject itself clears bit7; tests explicitly mark later SAT visibility. */
    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout());
        final CdSolidObject solid = new Terrain().owner();
        final CdSpring spring;
        final List<CdSpring.SolidCall> solids = new ArrayList<>();
        final List<CdSpring.LaunchCall> launches = new ArrayList<>();
        final List<CdObjectState> draws = new ArrayList<>();
        final List<CdSpring.FmSound> sounds = new ArrayList<>();
        final List<String> order = new ArrayList<>();
        CdSolidObject.Player player = new P().player();
        CdSolidObject.Globals globals = new CdSolidObject.Globals(0, 4, 0x12, 0x34);
        final CdSolidObject.Gates gates = new CdSolidObject.Gates(0, 0);
        boolean publishSolid = true, publishOthers = true, failLaunch, failSound, failDraw;
        Runnable afterDraw = () -> { };
        Harness() { this(assets(bytes(0xF0, 0, 0xF6, 0))); }
        Harness(CdSpring.Assets assets) {
            spring = new CdSpring(runtime, assets, solid, this::solidCommit, this::launchCommit, this::draw, this::sound);
        }
        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> f) { pool().write(f.apply(slot())); }
        void onScreen() { edit(o -> o.moveByte(SPRITE_FLAGS, o.spriteFlags() | 0x80)); }
        CdSpring.SolidInputs inputs() { return new CdSpring.SolidInputs(player, gates, globals); }
        CdSpring.Result run() { return run(128); }
        CdSpring.Result run(int camera) { return spring.run(WHOLE, new CdSpring.Globals(camera), inputs()); }
        void solidCommit(CdSpring.SolidCall c) {
            order.add("SOLID"); solids.add(c); assertEquals(player, c.expectedPlayer()); assertEquals(globals, c.expectedGlobals());
            var images = new ArrayList<CdObjectState>(); images.add(c.result().object()); images.addAll(c.result().otherSlots());
            for (var image : images) assertEquals(c.callCut().slots().get(image.identity().dynamicIndex()),
                    pool().state(image.identity().wholeIndex()));
            if (!publishSolid) return;
            pool().write(c.result().object()); if (publishOthers) c.result().otherSlots().forEach(pool()::write);
            player = c.result().player(); globals = c.result().globals();
        }
        void launchCommit(CdSpring.LaunchCall c) {
            order.add("LAUNCH"); launches.add(c); assertEquals(player, c.expectedPlayer()); assertEquals(globals, c.expectedGlobals());
            assertEquals(c.identity(), slot().identity()); assertEquals(0, slot().flags() & (slot().routine() == 10 ? 32 : 8));
            assertOutsideLaunchWrites(c);
            if (failLaunch) throw new IllegalStateException("constructed launch failure");
            player = c.finalPlayer(); globals = c.finalGlobals();
        }
        void sound(CdSpring.FmSound sound) {
            order.add("SOUND"); sounds.add(sound); if (failSound) throw new IllegalStateException("constructed FM failure");
        }
        void draw(CdObjectPool pool, CdObjectState.Identity id) {
            order.add("DRAW"); var live = pool.state(id.wholeIndex()); draws.add(live);
            pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 127));
            if (failDraw) throw new IllegalStateException("constructed draw failure"); afterDraw.run();
        }
    }

    /** Independent storage audit: same-value stores are in the explicit sets; all other values must be identical. */
    private static void assertOutsideLaunchWrites(CdSpring.LaunchCall c) {
        var a = c.expectedPlayer(); var b = c.finalPlayer();
        assertEquals(a.slotId(), b.slotId()); assertEquals(a.pushObject(), b.pushObject()); assertEquals(a.standObject(), b.standObject());
        assertEquals(a.actor().tile(), b.actor().tile()); assertEquals(a.actor().priority(), b.actor().priority());
        assertEquals(a.actor().hurt(), b.actor().hurt()); assertEquals(a.actor().rotationDistance(), b.actor().rotationDistance());
        assertEquals(a.actor().motion().centreX16_16() & 0xFFFF, b.actor().motion().centreX16_16() & 0xFFFF);
        assertEquals(a.actor().motion().centreY16_16() & 0xFFFF, b.actor().motion().centreY16_16() & 0xFFFF);
        for (var component : CdPlayerMotionState.class.getRecordComponents()) {
            var field = switch (component.getName()) {
                case "centreX16_16" -> CdSpring.LaunchPlayerField.CENTRE_X;
                case "centreY16_16" -> CdSpring.LaunchPlayerField.CENTRE_Y;
                case "xVelocity" -> CdSpring.LaunchPlayerField.X_VELOCITY;
                case "yVelocity" -> CdSpring.LaunchPlayerField.Y_VELOCITY;
                case "groundVelocity" -> CdSpring.LaunchPlayerField.GROUND_VELOCITY;
                case "animation" -> CdSpring.LaunchPlayerField.ANIMATION;
                case "flags" -> CdSpring.LaunchPlayerField.FLAGS;
                case "angle" -> CdSpring.LaunchPlayerField.ANGLE;
                case "moveLock" -> CdSpring.LaunchPlayerField.MOVE_LOCK;
                default -> null;
            };
            if (field == null || !c.playerWrites().contains(field)) {
                try {
                    assertEquals(component.getAccessor().invoke(a.actor().motion()),
                            component.getAccessor().invoke(b.actor().motion()), component.getName());
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
        }
        assertEquals(c.expectedGlobals(), c.finalGlobals()); assertTrue(c.globalWrites().isEmpty());
    }

    /** Every player-slot byte the Solid owner reads is explicit constructed ingress (TestCdSolidObject's values). */
    private static final class P {
        int x = 0x01008000, y = 0x00671234, xv = 0x0123, yv = 0x0400, gv = 0, routine = 2, angle = 0x10, flags = 0x02;
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
