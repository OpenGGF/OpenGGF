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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Constructed source tests, authored UNRUN (including compilation). Native pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Robot Generator/Main.asm:25-229,
 * Robot Generator/Tile IDs.asm:7-10, Object Animate.asm:14-95, Object Spawner.asm:329-340,385-446.
 * Actual placement/pool and shared Solid run against public-format terrain. Commit is a bounded
 * contract harness, never production Frame, global ownership or private phase authority. Literal
 * source expectations compare all64 bytes of every96 slot; inaccessible opaque bytes remain zero.
 * No route, media authentication, ROM parity, art completion, sound synthesis or pixel claim.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdRobotGenerator {
    private static final int WHOLE = 40, X0 = 3248, Y0 = 784, MAP = 0x20E816, SCHEDULE_ADDRESS = 0x20EA54;
    private static final byte[] BASE = hex("043f0409000000000454040000000000000000000000");
    private static final byte[] SCHEDULE = hex("01000002d8ec031c0a0412ee05eef60608f808ee0e0af60a0c1ef60f00ee"
            + "1214f614f612160817190df61a17ea1cfde71e0a1420f602221ef8230df628f60aff");
    private static final int[][] EVENTS = {{1,0,0},{2,-40,-20},{3,28,10},{4,18,-18},{5,-18,-10},{6,8,-8},
            {8,-18,14},{10,-10,10},{12,30,-10},{15,0,-18},{18,20,-10},{20,-10,18},{22,8,23},
            {25,13,-10},{26,23,-22},{28,-3,-25},{30,10,20},{32,-10,2},{34,30,-8},{35,13,-10},{40,-10,10}};

    @Test void fiveAliasesOverlapOnlyTheExistingNativeBytes() {
        var pool = new CdObjectPool(); var s = pool.state(WHOLE).moveWord(ROBOT_GENERATOR_TIMER, 0x1234)
                .moveLong(ROBOT_GENERATOR_SCHEDULE, 0x56789ABC).moveWord(ROBOT_GENERATOR_BASE_Y, 0xDEF0)
                .moveWord(ROBOT_GENERATOR_FLOAT_STEP, 0x1357).moveByte(ROBOT_GENERATOR_TIMER_HIGH, 0x24);
        byte[] expected = new byte[64]; w(expected, 0x2A, 0x2434); l(expected, 0x2C, 0x56789ABC);
        w(expected, 0x30, 0xDEF0); w(expected, 0x32, 0x1357); assertArrayEquals(expected, s.copyBytes());
        assertEquals(0x2A, ROBOT_GENERATOR_TIMER_HIGH.byteOffset()); assertEquals(0x2A, ROBOT_GENERATOR_TIMER.byteOffset());
        assertEquals(0x2C, ROBOT_GENERATOR_SCHEDULE.byteOffset()); assertEquals(0x30, ROBOT_GENERATOR_BASE_Y.byteOffset());
        assertEquals(0x32, ROBOT_GENERATOR_FLOAT_STEP.byteOffset()); assertArrayEquals(new byte[64], pool.state(WHOLE).copyBytes());
    }

    @Test void assetShapeChecksDoNotEagerlyReadTheRawReachedTables() {
        var a = assets(new byte[0], new byte[0]); assertEquals(0, a.baseTiles().length());
        assertEquals(0, a.explosionSchedule().length()); assertEquals(8, a.visual().mappingFrames().size());
        assertEquals(1, a.visual().animations().size());
        assertThrows(NullPointerException.class, () -> new CdRobotGenerator.Assets(null, visual(), MAP, bytes(SCHEDULE), SCHEDULE_ADDRESS));
        assertThrows(NullPointerException.class, () -> CdRobotGenerator.Assets.fromPastAssets(null));
        byte[] maps = new byte[15]; for (int i = 0; i < 7; i++) w(maps, 2 * i, 14);
        var seven = CdObjectVisualAssets.decodeStatic(maps, 7, 23);
        assertThrows(IllegalArgumentException.class, () -> new CdRobotGenerator.Assets(bytes(BASE), seven, MAP, bytes(SCHEDULE), SCHEDULE_ADDRESS));
    }

    @Test void rawGlobalsUseNativeWidthsAndInvalidRoutineDoesNotReadInputsOrGlobals() {
        assertDoesNotThrow(() -> new CdRobotGenerator.Globals(255,255,255,255,65535));
        assertThrows(IllegalArgumentException.class, () -> new CdRobotGenerator.Globals(256,0,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new CdRobotGenerator.Globals(0,-1,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new CdRobotGenerator.Globals(0,0,256,0,0));
        assertThrows(IllegalArgumentException.class, () -> new CdRobotGenerator.Globals(0,0,0,256,0));
        assertThrows(IllegalArgumentException.class, () -> new CdRobotGenerator.Globals(0,0,0,0,65536));
        for (int routine = 0; routine < 256; routine++) {
            if (routine == 0 || routine == 2 || routine == 4 || routine == 6) continue;
            var h = new Harness(); final int value = routine; h.edit(s -> s.moveByte(ROUTINE, value)); var before = images(h);
            var ex = assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class, () -> h.body.run(WHOLE, null, null));
            assertEquals(CdRobotGenerator.UnsupportedBoundary.ROUTINE_DISPATCH, ex.boundary()); assertEquals(routine, ex.value());
            assertImages(before, h); assertTrue(h.order.isEmpty());
        }
    }

    @Test void goodFutureInitHasExactHeaderAndUnconditionalDrawWithoutSolidOrAnimator() {
        for (int time : new int[]{0,1,2,3}) {
            var h = new Harness(); h.good = 7; h.time = time;
            h.edit(s -> dirty(s).moveByte(ID,0x2D).moveByte(ROUTINE,0).moveByte(SAVED_FLAGS_ID,0)
                    .moveByte(SPRITE_FLAGS,0xE1).moveWord(X,X0).moveWord(Y,Y0));
            var expected = images(h); var b = expected.get(WHOLE-32);
            b[0x24] = 2; b[1] = 0x65; b[0x18] = 4; b[0x17] = b[0x19] = 0x22; b[0x16] = 0x20;
            w(b,2,time == 0 ? 0x43F : time == 1 ? 0x409 : 0); l(b,4,MAP); l(b,0x2C,SCHEDULE_ADDRESS);
            w(b,0x30,Y0); w(b,0x2A,4); w(b,0x32,1); b[0x1A] = (byte)(time == 0 ? 1 : 0);
            var r = h.run(); assertImages(expected,h); assertArrayEquals(b,r.state().copyBytes());
            assertEquals(List.of("draw"),h.order); assertEquals(1,h.draws.size());
        }
    }

    @Test void badPastInitFallsIntoFloatThenGenuineSolidThenAnimatorInOneDispatch() {
        var h = new Harness(); h.edit(s -> s.moveByte(ROUTINE,0).moveByte(SPRITE_FLAGS,0xC1).moveByte(FLAGS,0x52)
                .moveByte(ANIMATION_TIME,2).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468));
        byte[] expected = h.slot().copyBytes(); expected[0x24] = 2; expected[1] = (byte)0xC5; expected[0x18] = 4;
        expected[0x17] = expected[0x19] = 0x22; expected[0x16] = 0x20; w(expected,2,0x43F); l(expected,4,MAP);
        l(expected,0x2C,SCHEDULE_ADDRESS); w(expected,0x30,Y0); w(expected,0x2A,5); w(expected,0x32,1);
        expected[0x1A] = 3; expected[0x20] = (byte)0xFA; w(expected,12,Y0-16);
        h.run(); assertArrayEquals(expected,h.solids.getFirst().expectedSource().copyBytes());
        expected[1] &= 0x7F; expected[0x25] = 0; expected[0x1E] = 1;
        assertArrayEquals(expected,h.slot().copyBytes()); assertEquals(List.of("solid:ACTIVE_SOLID","draw"),h.order);
        assertEquals(0,h.good); assertEquals(1,h.solids.size());
    }

    @Test void initMalformedBaseReadRetainsOnlyTheHeaderBeforeTheReachedWord() {
        var h = new Harness(assets(new byte[]{4},SCHEDULE)); h.edit(s -> s.moveByte(ROUTINE,0));
        var expected = images(h); var b = expected.get(WHOLE-32); b[0x24] = 2; b[1] |= 4;
        b[0x18] = 4; b[0x17] = b[0x19] = 0x22; b[0x16] = 0x20;
        var ex = assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class,h::run);
        assertEquals(CdRobotGenerator.UnsupportedBoundary.BASE_TILE_READ,ex.boundary()); assertImages(expected,h); assertTrue(h.order.isEmpty());
    }

    @Test void rawActAndAddByteTimeZoneKeepTheNativeHighByteBeforeTableIndex() {
        var h = new Harness(); h.good = 1; h.act = 64; h.time = 255; h.edit(s -> s.moveByte(ROUTINE,0));
        var ex = assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class,h::run);
        assertEquals(CdRobotGenerator.UnsupportedBoundary.BASE_TILE_READ,ex.boundary()); assertEquals(0x3FE,ex.value());
        assertEquals(2,h.slot().routine()); assertTrue(h.order.isEmpty());
    }

    @Test void floatWordWrapsAndMovesBeforeNegatingAtTheThirtyTwoTickBoundary() {
        var h = new Harness(); h.edit(s -> s.moveWord(ROBOT_GENERATOR_TIMER,0xFFFF).moveWord(ROBOT_GENERATOR_FLOAT_STEP,0x8000)
                .moveWord(Y,0x9234).moveByte(ANIMATION_TIME,2));
        var before = h.slot().copyBytes(); h.run(); w(before,0x2A,0); w(before,12,0x1234); w(before,0x32,0x8000);
        before[1] &= 0x7F; before[0x1E] = 1; before[0x25] = 0;
        assertArrayEquals(before,h.slot().copyBytes());
    }

    @Test void activeGoodOrNonPastReturnsWithoutReadingMalformedTablesOrSolidInputs() {
        for (int good : new int[]{0,1,255}) for (int time : new int[]{0,1,0x80,255}) {
            if (good == 0 && time == 0) continue;
            var h = new Harness(assets(new byte[0],new byte[0])); h.good = good; h.time = time;
            h.edit(s -> s.moveByte(ROUTINE,2).moveByte(SAVED_FLAGS_ID,0));
            var expected = images(h); expected.get(WHOLE-32)[1] &= 0x7F;
            if ((time & 0x80) != 0) expected.set(WHOLE-32,new byte[64]);
            h.body.run(WHOLE,h.globals(),null); assertImages(expected,h); assertEquals(List.of("draw"),h.order);
        }
    }

    @Test void hitClearsCollisionWordThenStoresGoodFutureAndPointsBeforeSolidWithoutAnimation() {
        var h = new Harness(); h.edit(s -> s.moveByte(COLLISION_STATUS,0xA5).moveWord(ROBOT_GENERATOR_TIMER,7)
                .moveWord(ROBOT_GENERATOR_FLOAT_STEP,1).moveByte(ANIMATION_TIME,0x7E));
        var expected = images(h); var b = expected.get(WHOLE-32); w(b,12,Y0+1); w(b,0x2A,0);
        b[0x20] = b[0x21] = 0; b[0x1A] = 7; b[0x24] = 4;
        h.beforeGood = call -> { assertImages(expected,h); assertEquals(0,call.expectedGoodFuture()); assertEquals(1,call.value()); };
        h.beforePoints = call -> { assertEquals(1,h.good); assertImages(expected,h); assertEquals(0x96,call.points()); };
        var result = h.run(); b[0x25] = 0; b[1] &= 0x7F; assertImages(expected,h);
        assertEquals(List.of("good:1","points:150","solid:HIT_SOLID","draw"),h.order);
        assertEquals(1,h.good); assertEquals(0x7E,result.state().unsignedByte(ANIMATION_TIME));
        assertEquals(1,result.effects().stream().filter(CdRobotGenerator.PointsAdded.class::isInstance).count());
    }

    @Test void hitGetOffUsesTheGenuineImmediatelyPrecedingSolidPlayerAndGlobals() {
        var h = new Harness(); var p = h.contact(); p.flags = 8; p.stand = WHOLE; p.yv = 0; p.charge = 1;
        h.player = p.player(); h.edit(s -> s.moveByte(FLAGS,8).moveByte(COLLISION_STATUS,1));
        h.run(); assertEquals(2,h.solids.size());
        var first = assertInstanceOf(CdSolidObject.Completed.class,h.solids.getFirst().outcome()).completedResult();
        var second = h.solids.get(1); assertEquals(CdRobotGenerator.Site.HIT_GET_OFF,second.site());
        assertEquals(first.player(),second.expectedPlayer()); assertEquals(first.globals(),second.expectedGlobals());
        assertEquals(0,h.player.standObject()); assertEquals(0,h.slot().flags() & 8);
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),h.solidSounds);
        assertEquals(List.of("good:1","points:150","solid:HIT_SOLID","solid:HIT_GET_OFF","draw"),h.order);
    }

    @Test void allTwentyOneLiteralEventsAllocateOnceWithSignedOffsetsAndLeaveDirtyChildBytes() {
        var h = new Harness(); h.edit(s -> s.moveByte(ROUTINE,4).moveWord(ROBOT_GENERATOR_TIMER,0x004D));
        var expected = images(h); int event = 0;
        for (int tick = 1; tick <= 40; tick++) {
            boolean emits = event < EVENTS.length && EVENTS[event][0] == tick;
            if (emits) {
                var free = h.pool().state(32); h.pool().write(dirty(free).moveByte(ID,0)); expected.set(0,h.pool().state(32).copyBytes());
                byte[] b = expected.getFirst(); b[0] = 0x18; b[0x25] = 1; w(b,8,X0+EVENTS[event][1]); w(b,12,Y0+EVENTS[event][2]);
                l(expected.get(WHOLE-32),0x2C,SCHEDULE_ADDRESS+3*(event+1)); event++;
            }
            expected.get(WHOLE-32)[0x2A] = (byte)tick; expected.get(WHOLE-32)[1] &= 0x7F;
            var r = h.run(); assertImages(expected,h); assertEquals(0x4D,r.state().word(ROBOT_GENERATOR_TIMER) & 255);
            if (emits) { h.pool().deleteObject(32); expected.set(0,new byte[64]); }
        }
        assertEquals(21,event); assertEquals(21,h.fm.size()); assertTrue(h.fm.stream().allMatch(c -> c.nativeId() == 0x9E));
        assertEquals(0,h.good); assertTrue(h.solids.isEmpty());
    }

    @Test void fullPoolFailureConsumesTheEventAndDoesNotRetryOrPlaySound() {
        var h = new Harness(); h.edit(s -> s.moveByte(ROUTINE,4).moveWord(ROBOT_GENERATOR_TIMER,0x007A)); h.fill();
        var expected = images(h); expected.get(WHOLE-32)[0x2A] = 1; l(expected.get(WHOLE-32),0x2C,SCHEDULE_ADDRESS+3);
        expected.get(WHOLE-32)[1] &= 0x7F; var r = h.run(); assertImages(expected,h);
        assertTrue(r.effects().stream().anyMatch(CdRobotGenerator.SlotUnavailable.class::isInstance)); assertTrue(h.fm.isEmpty());
        h.pool().deleteObject(32); h.run(); assertEquals(X0-40,h.pool().state(32).centreX());
        assertEquals(Y0-20,h.pool().state(32).centreY()); assertEquals(1,h.fm.size());
    }

    @Test void unmatchedTickDoesNotConsumeScheduleAndHighByteWrapDoesNotCarryIntoLowByte() {
        var h = new Harness(assets(BASE,new byte[]{3,1,2,(byte)0xFF}));
        h.edit(s -> s.moveByte(ROUTINE,4).moveWord(ROBOT_GENERATOR_TIMER,0xFFB7));
        var expected = images(h); expected.get(WHOLE-32)[0x2A] = 0; expected.get(WHOLE-32)[1] &= 0x7F;
        h.run(); assertImages(expected,h); assertTrue(h.fm.isEmpty());
        assertEquals(SCHEDULE_ADDRESS,h.slot().longBits(ROBOT_GENERATOR_SCHEDULE));
    }

    @Test void anyNegativeTickFinishesThenEightHighByteDelaysRestoreBaseAndReinitializeGoodPast() {
        var h = new Harness(assets(BASE,new byte[]{(byte)0x80})); h.good = 1;
        h.edit(s -> s.moveByte(ROUTINE,4).moveWord(ROBOT_GENERATOR_TIMER,0x286D).moveWord(ROBOT_GENERATOR_BASE_Y,Y0+16));
        var expected = images(h); var b = expected.get(WHOLE-32); b[0x24] = 6; b[0x2A] = 8; b[1] &= 0x7F;
        h.run(); assertImages(expected,h);
        for (int n = 7; n >= 0; n--) {
            b[0x2A] = (byte)n; if (n == 0) { b[0x24] = 0; w(b,12,Y0+16); }
            h.run(); assertImages(expected,h);
        }
        assertEquals(1,h.fm.size()); assertEquals(0xD9,h.fm.getFirst().nativeId());
        assertEquals(0x6D,h.slot().word(ROBOT_GENERATOR_TIMER));
        h.run(); assertEquals(2,h.slot().routine()); assertEquals(1,h.slot().unsignedByte(MAP_FRAME));
        assertEquals(Y0+16,h.slot().centreY()); assertTrue(h.solids.isEmpty());
    }

    @Test void malformedTickAndOffsetReadsRetainOnlyTheirReachedSourcePrefixes() {
        for (int length : new int[]{0,1,2}) {
            byte[] schedule = Arrays.copyOf(new byte[]{1,8,9},length); var h = new Harness(assets(BASE,schedule));
            h.edit(s -> s.moveByte(ROUTINE,4).moveWord(ROBOT_GENERATOR_TIMER,0x007A)); var expected = images(h);
            if (length > 0) expected.get(WHOLE-32)[0x2A] = 1;
            var e = assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class,h::run);
            assertEquals(length == 0 ? CdRobotGenerator.UnsupportedBoundary.SCHEDULE_TICK_READ : length == 1
                    ? CdRobotGenerator.UnsupportedBoundary.SCHEDULE_X_READ : CdRobotGenerator.UnsupportedBoundary.SCHEDULE_Y_READ,e.boundary());
            assertImages(expected,h); assertTrue(h.order.isEmpty()); assertEquals(SCHEDULE_ADDRESS,h.slot().longBits(ROBOT_GENERATOR_SCHEDULE));
        }
        var h = new Harness(); h.edit(s -> s.moveByte(ROUTINE,4).moveLong(ROBOT_GENERATOR_SCHEDULE,0xFF20EA54));
        var e = assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class,h::run);
        assertEquals(0xFF20EA54L,e.value()); assertTrue(h.order.isEmpty());
    }

    @Test void nativeSingleScriptPublishesThreeFourFiveSixAndLoopsWithFourVisitCadence() {
        var h = new Harness(); h.edit(s -> s.moveByte(ANIMATION_TIME,0));
        var expected = images(h); var b = expected.get(WHOLE-32);
        for (int visit = 1; visit <= 20; visit++) {
            int group = (visit - 1) / 4, frame = 3 + group % 4;
            w(b,0x2A,visit); w(b,12,Y0 + visit / 8); b[1] &= 0x7F; b[0x25] = 0;
            b[0x1A] = (byte)frame; b[0x1B] = (byte)(1 + group % 4); b[0x1E] = (byte)(3 - (visit - 1) % 4);
            h.run(); assertImages(expected,h);
        }
        assertEquals(20,h.solids.size()); assertEquals(20,h.draws.size()); assertTrue(h.fm.isEmpty());
    }

    @Test void heldAnimationTimerDoesNotReadAnUnownedDirectoryEntry() {
        var h = new Harness(); h.edit(s -> s.moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_TIME,2));
        var expected = images(h); var b = expected.get(WHOLE-32); w(b,0x2A,1); b[0x1E] = 1; b[1] &= 0x7F; b[0x25] = 0;
        h.run(); assertImages(expected,h); assertEquals(List.of("solid:ACTIVE_SOLID","draw"),h.order);
    }

    @Test void malformedAnimationIndexCommitsFloatSolidAndRealTimerPrefixWithoutDraw() {
        var h = new Harness(); h.edit(s -> s.moveByte(ANIMATION,1).moveByte(PREVIOUS_ANIMATION,1).moveByte(ANIMATION_TIME,0));
        var expected = images(h); w(expected.get(WHOLE-32),0x2A,1); expected.get(WHOLE-32)[0x1E] = (byte)0xFF;
        expected.get(WHOLE-32)[0x25] = 0;
        var e = assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class,h::run);
        assertEquals(CdRobotGenerator.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,e.boundary()); assertInstanceOf(UnsupportedOperationException.class,e.getCause()); assertImages(expected,h);
        assertEquals(List.of("solid:ACTIVE_SOLID"),h.order);
    }

    @Test void malformedAnimationFrameKeepsTheReachedSpeedAndOriginalMap() {
        var h = new Harness(); h.edit(s -> s.moveByte(ANIMATION_FRAME,255).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,6));
        var expected = images(h); w(expected.get(WHOLE-32),0x2A,1); expected.get(WHOLE-32)[0x1E] = 3;
        expected.get(WHOLE-32)[0x25] = 0;
        var e = assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class,h::run);
        assertEquals(CdRobotGenerator.UnsupportedBoundary.ANIMATION_FRAME_READ,e.boundary()); assertInstanceOf(UnsupportedOperationException.class,e.getCause()); assertImages(expected,h); assertTrue(h.draws.isEmpty());
    }

    @Test void nativeStoppedStandPrefixCommitsThenRethrowsTheIdenticalExceptionBeforeAnimationOrDraw() {
        for (int address : new int[]{1,31,128,255}) {
            var h = new Harness(); var p = h.contact(); p.flags = 8; p.stand = address; p.yv = 0; h.player = p.player();
            var ex = assertThrows(RuntimeException.class,h::run);
            var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class,h.solids.getFirst().outcome());
            assertSame(stopped.stop().originalException(),ex); assertEquals(stopped.prefix().object(),h.slot());
            assertEquals(stopped.prefix().player(),h.player); assertEquals(stopped.prefix().globals(),h.solidGlobals);
            assertTrue(h.draws.isEmpty()); assertEquals(2,h.slot().routine()); assertEquals(2,h.slot().unsignedByte(ANIMATION_TIME));
        }
    }

    @Test void readOnlyPortFailuresPreserveTheOriginalAndDoNotRepairWrongPoolOrSavedChanges() {
        for (String port : List.of("good","points","fm")) {
            var h = new Harness(); var original = new IllegalArgumentException(port);
            Runnable corrupt = () -> { h.pool().write(h.pool().state(127).moveByte(ID,0x55));
                h.placement.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.LOADED); throw original; };
            if (port.equals("fm")) { h.edit(s -> s.moveByte(ROUTINE,4)); h.beforeFm = call -> corrupt.run(); }
            else { h.edit(s -> s.moveByte(COLLISION_STATUS,1));
                if (port.equals("good")) h.beforeGood = call -> corrupt.run(); else h.beforePoints = call -> corrupt.run(); }
            assertSame(original,assertThrows(IllegalArgumentException.class,h::run)); assertEquals(1,original.getSuppressed().length);
            assertEquals(0x55,h.pool().state(127).id()); assertEquals(0x80,h.placement.savedFlag(1,0)); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void returnedReadOnlyMutationAndMissingOrWrongSolidPublicationCannotComplete() {
        var h = new Harness(); h.edit(s -> s.moveByte(COLLISION_STATUS,1));
        h.beforeGood = call -> h.pool().write(h.pool().state(127).moveByte(ID,1));
        assertThrows(IllegalStateException.class,h::run); assertTrue(h.solids.isEmpty());
        var missing = new Harness(); missing.edit(s -> s.moveByte(SOLID_TYPE,0x55)); missing.publishSolid = false;
        assertThrows(IllegalStateException.class,missing::run); assertTrue(missing.draws.isEmpty()); assertEquals(0x55,missing.slot().unsignedByte(SOLID_TYPE));
        var wrong = new Harness(); wrong.afterSolid = call -> wrong.pool().write(wrong.pool().state(127).moveByte(ID,1));
        assertThrows(IllegalStateException.class,wrong::run); assertTrue(wrong.draws.isEmpty());
    }

    @Test void drawAllowsOnlyItsActualBitSevenClearAndFailurePreservesOriginalException() {
        var h = new Harness(); h.good = 1; h.afterDraw = call -> h.edit(s -> s.moveByte(ROUTINE,6));
        assertThrows(IllegalStateException.class,h::run); assertEquals(6,h.slot().routine());
        var failed = new Harness(); failed.good = 1; var original = new AssertionError("queue failure");
        failed.afterDraw = call -> { throw original; };
        assertSame(original,assertThrows(AssertionError.class,failed::run)); assertEquals(0,original.getSuppressed().length);
        assertEquals(4,failed.slot().spriteFlags());
    }

    @Test void callbackFailureAfterCorrectSolidCommitRetainsTheNativeImagesAndExceptionIdentity() {
        var h = new Harness(); var original = new IllegalStateException("post commit"); h.afterSolid = call -> { throw original; };
        assertSame(original,assertThrows(IllegalStateException.class,h::run)); assertEquals(0,original.getSuppressed().length);
        var result = assertInstanceOf(CdSolidObject.Completed.class,h.solids.getFirst().outcome()).completedResult();
        assertEquals(result.object(),h.slot()); assertEquals(result.player(),h.player); assertTrue(h.draws.isEmpty());
    }

    @Test void synchronousReentryIsRejectedAtTheActualPortWithoutContinuingTheOuterBody() {
        var h = new Harness(); h.edit(s -> s.moveByte(COLLISION_STATUS,1)); h.beforePoints = call -> h.run();
        var e = assertThrows(IllegalStateException.class,h::run); assertTrue(e.getMessage().contains("Reentrant"));
        assertEquals(1,h.good); assertEquals(4,h.slot().routine()); assertTrue(h.solids.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void rebuildReturnToRoutineZeroRunsWarpDespawnAfterSoundAndDraw() {
        var h = new Harness(); h.time = 0x81; h.direction = 1;
        h.edit(s -> s.moveByte(ROUTINE,6).moveWord(ROBOT_GENERATOR_TIMER,0x017A).moveByte(SAVED_FLAGS_ID,219));
        h.placement.bsetSavedFlag(219,0,CdObjectPlacementRuntime.SavedBit.LOADED);
        var expected = images(h); expected.set(WHOLE-32,new byte[64]); var r = h.run(); assertImages(expected,h);
        assertTrue(r.deleted()); assertEquals(0,h.placement.savedFlag(219,0)); assertEquals(List.of("fm:217","draw"),h.order);
    }

    @Test void rawLinearSavedAliasAndOutOfBankStopOccurOnlyAfterDraw() {
        var h = new Harness(); h.good = 1; h.time = 3; h.camera = 0; h.edit(s -> s.moveByte(SAVED_FLAGS_ID,1));
        h.placement.bsetSavedFlag(2,0,CdObjectPlacementRuntime.SavedBit.LOADED); h.run();
        assertEquals(0,h.placement.savedFlag(2,0)); assertEquals(0,h.slot().id());
        var bad = new Harness(); bad.good = 1; bad.time = 127; bad.camera = 0;
        bad.edit(s -> s.moveByte(SAVED_FLAGS_ID,255)); var expected = images(bad); expected.get(WHOLE-32)[1] &= 0x7F;
        var ex = assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class,bad::run);
        assertEquals(CdRobotGenerator.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,ex.boundary()); assertEquals(894,ex.value());
        assertImages(expected,bad); assertEquals(List.of("draw"),bad.order);
    }

    @Test void returnedMetadataCannotReplayAnAlreadyCompletedChildSoundOrGlobalStore() {
        var h = new Harness(); h.edit(s -> s.moveByte(ROUTINE,4)); var result = h.run(); var after = images(h);
        for (int i = 0; i < 5; i++) { assertEquals(0x18,h.pool().state(32).id()); assertEquals(result.effects(),result.effects()); }
        assertImages(after,h); assertEquals(1,h.fm.size()); assertEquals(1,h.draws.size());
        assertThrows(UnsupportedOperationException.class,() -> result.effects().clear());
    }

    private static CdRobotGenerator.Assets assets(byte[] base, byte[] schedule) {
        return new CdRobotGenerator.Assets(bytes(base),visual(),MAP,bytes(schedule),SCHEDULE_ADDRESS);
    }
    private static CdObjectVisualAssets visual() {
        byte[] maps = new byte[17]; for (int i = 0; i < 8; i++) w(maps,2*i,16);
        return CdObjectVisualAssets.decode(maps,8,hex("00020303040506ff"),1,23);
    }
    private static CdAssetBytes bytes(byte[] b) { return CdAssetBytes.copyOf(b); }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static void w(byte[] b,int at,int value) { b[at]=(byte)(value>>>8); b[at+1]=(byte)value; }
    private static void l(byte[] b,int at,int value) { w(b,at,value>>>16); w(b,at+2,value); }
    private static List<byte[]> images(Harness h) {
        var images = new ArrayList<byte[]>(); for (int whole=32;whole<128;whole++) images.add(h.pool().state(whole).copyBytes()); return images;
    }
    private static void assertImages(List<byte[]> expected,Harness h) {
        assertEquals(96,expected.size()); for (int whole=32;whole<128;whole++) assertArrayEquals(expected.get(whole-32),h.pool().state(whole).copyBytes(),"whole "+whole);
    }
    private static CdObjectState dirty(CdObjectState s) {
        for (var f:CdObjectState.ByteField.values()) s=s.moveByte(f,0x51+7*f.byteOffset());
        for (var f:CdObjectState.WordField.values()) s=s.moveWord(f,0xA157+0x103*f.byteOffset());
        for (var f:CdObjectState.LongField.values()) s=s.moveLong(f,0xA1536729+0x01030507*f.byteOffset()); return s;
    }
    private static CdObjectPlacements layout() {
        var b=ByteBuffer.allocate(34); b.putShort((short)12).putShort((short)28).putShort((short)0xFFFF).putInt(0).putShort((short)0);
        b.putShort((short)X0).putShort((short)Y0).put((byte)0xAD).put((byte)0xC0).put((byte)0x40).put((byte)0);
        b.putShort((short)0xFFFF).putInt(0).putShort((short)0); b.putShort((short)0xFFFF).putInt(0); return CdObjectPlacements.decodeLayout(b.array());
    }
    private static final class Harness {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(layout());
        final List<String> order=new ArrayList<>(); final List<CdRobotGenerator.SolidCall> solids=new ArrayList<>();
        final List<CdRobotGenerator.FmRequest> fm=new ArrayList<>(); final List<CdRobotGenerator.DrawCall> draws=new ArrayList<>();
        final List<CdPlayerTouchResponse.FmSound> solidSounds=new ArrayList<>(); final CdRobotGenerator body;
        int good,time,act,direction,camera=X0; boolean publishSolid=true;
        CdSolidObject.Player player=new P().player(); CdSolidObject.Gates gates=new CdSolidObject.Gates(0,0);
        CdSolidObject.Globals solidGlobals=new CdSolidObject.Globals(0,4,0x12,0x34);
        Consumer<CdRobotGenerator.GoodFutureCall> beforeGood=c -> {};
        Consumer<CdRobotGenerator.PointsCall> beforePoints=c -> {};
        Consumer<CdRobotGenerator.FmRequest> beforeFm=c -> {};
        Consumer<CdRobotGenerator.DrawCall> afterDraw=c -> {};
        Consumer<CdRobotGenerator.SolidCall> afterSolid=c -> {};
        Harness() { this(assets(BASE,SCHEDULE)); }
        Harness(CdRobotGenerator.Assets data) {
            edit(s -> s.moveByte(ID,0x2D).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveWord(X,X0).moveWord(Y,Y0)
                    .moveByte(WIDTH,0x22).moveByte(X_RADIUS,0x22).moveByte(Y_RADIUS,0x20).moveByte(COLLISION_TYPE,0xFA)
                    .moveByte(ANIMATION_TIME,2).moveWord(ROBOT_GENERATOR_FLOAT_STEP,1).moveLong(MAPPINGS,MAP)
                    .moveLong(ROBOT_GENERATOR_SCHEDULE,SCHEDULE_ADDRESS).moveWord(ROBOT_GENERATOR_BASE_Y,Y0));
            body=new CdRobotGenerator(placement,data,new Terrain().owner(),this::commit,
                    c -> { assertCut(c.actualCut(),c.expectedSource()); assertEquals(good,c.expectedGoodFuture()); beforeGood.accept(c);
                        good=c.value(); order.add("good:"+good); },
                    c -> { assertCut(c.actualCut(),c.expectedSource()); beforePoints.accept(c); order.add("points:"+c.points()); },
                    c -> { assertCut(c.actualCut(),c.expectedSource()); beforeFm.accept(c); fm.add(c); order.add("fm:"+c.nativeId()); },
                    c -> { assertCut(c.actualCut(),c.expectedSource()); draws.add(c); order.add("draw");
                        pool().write(slot().moveByte(SPRITE_FLAGS,slot().spriteFlags() & 0x7F)); afterDraw.accept(c); });
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> op) { pool().write(op.apply(slot())); }
        CdRobotGenerator.Globals globals() { return new CdRobotGenerator.Globals(good,time,act,direction,camera); }
        CdRobotGenerator.Result run() { return body.run(WHOLE,globals(),new CdRobotGenerator.Inputs(player,gates,solidGlobals)); }
        void fill() { for(int i=32;i<128;i++) if(i!=WHOLE) pool().write(pool().state(i).moveByte(ID,0x31)); }
        P contact() { var p=new P(); p.x=X0<<16; p.y=(slot().centreY()-slot().unsignedByte(Y_RADIUS)-19)<<16; return p; }
        void assertCut(CdObjectPlacementRuntime.Snapshot cut,CdObjectState source) {
            placement.validateRestore(cut); assertEquals(source,slot()); assertEquals(96,cut.pool().slots().size());
            assertEquals(placement.status(),cut.status()); assertArrayEquals(placement.capture().savedFlags().copy(),cut.savedFlags().copy());
            for(var s:cut.pool().slots()) assertEquals(s,pool().state(s.identity().wholeIndex()));
        }
        void commit(CdRobotGenerator.SolidCall call) {
            assertCut(call.actualCut(),call.expectedSource()); assertEquals(player,call.expectedPlayer()); assertEquals(solidGlobals,call.expectedGlobals());
            CdObjectState object; List<CdObjectState> others; CdSolidObject.Player next; CdSolidObject.Globals nextGlobals;
            if(call.outcome() instanceof CdSolidObject.Completed done) { var r=done.completedResult(); object=r.object(); others=r.otherSlots();
                next=r.player(); nextGlobals=r.globals(); solidSounds.addAll(r.fmRequests()); }
            else { var p=((CdSolidObject.NativeStopped)call.outcome()).prefix(); object=p.object(); others=p.otherSlots(); next=p.player(); nextGlobals=p.globals(); }
            solids.add(call); order.add("solid:"+call.site());
            if(publishSolid) { pool().write(object); others.forEach(pool()::write); player=next; solidGlobals=nextGlobals; }
            afterSolid.accept(call);
        }
    }
    private static final class P {
        int x=(X0<<16)|0x8000,y=0x01001234,xv,yv=0x100,gv,routine=2,angle=0x10,flags=2;
        int spr=4,xr=9,yr=19,anim,prev,jump=1,charge,lock,stick,ctrl,pri=0x56,sec=0x78,id=1,push,stand;
        CdSolidObject.Player player() { return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x,y,xv,yv,gv,
                routine,angle,flags,spr,xr,yr,anim,prev,jump,charge,lock,stick,ctrl,pri,sec),0x780,2,0,0),id,push,stand); }
    }
    /** Public-format terrain from the existing Solid-object fixtures; no probe mocks or forged outcomes. */
    private static final class Terrain {
        CdSolidObject owner() {
            byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256],heights=new byte[4096],widths=new byte[4096];
            fg[0]=41; fg[1]=7; bg[0]=5; bg[1]=7; Arrays.fill(fg,2,fg.length,(byte)2); ids[1]=1; ids[2]=2;
            Arrays.fill(heights,16,32,(byte)16); Arrays.fill(widths,32,48,(byte)16); angles[2]=(byte)0xC0;
            var terrain=CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths);
            byte[] animations=new byte[578]; for(int i=0;i<60;i++) w(animations,2*i,120); animations[122]=(byte)0xFF;
            byte[] maps=new byte[398],dplcs=new byte[398]; for(int i=0;i<198;i++) { w(maps,2*i,396); w(dplcs,2*i,396); }
            var visual=CdSonicVisualAssets.decode(maps,dplcs,new byte[69472]); byte[] atan=new byte[257];
            for(int i=0;i<=256;i++) atan[i]=(byte)(i/8);
            var player=CdR11aPlayerAssets.decode(animations,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)0xFF},new byte[4],new byte[640],atan,visual);
            return new CdSolidObject(new CdPlayerMovement(terrain,player));
        }
    }
}
