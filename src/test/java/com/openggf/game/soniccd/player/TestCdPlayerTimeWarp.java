package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.objects.CdReservedObjectBank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.player.CdPlayerTimeWarp.GlobalField.*;
import static com.openggf.game.soniccd.player.CdPlayerTimeWarp.ReservedField.*;
import static com.openggf.game.soniccd.player.CdPlayerTimeWarp.UnsupportedBoundary.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Literal ObjSonic_TimeWarp/MakeTimeWarpStars vectors at native source pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Sonic/Main.asm:112-128,573-669.
 * Pure component tests: no live Body, Frame, service, animation or native execution.
 */
class TestCdPlayerTimeWarp {
    private static final CdPlayerTimeWarp.Context ABSENT = new CdPlayerTimeWarp.Context(
            OptionalInt.empty(), Optional.empty());

    @ParameterizedTest
    @CsvSource({"0,0,230", "2,0,230", "0,255,65535", "2,255,89"})
    void ppOpeningRestrictionPrecedesChargeDirectionAndEveryBorrowedOwner(int period, int charge, int timer) {
        var input = new CdPlayerTimeWarp.Input(0, period, 0x08FF, charge, 0x8000, 0xFF, timer, 0xA5);
        var result = CdPlayerTimeWarp.step(input, new CdPlayerTimeWarp.Context(
                OptionalInt.of(0x100), Optional.empty()));
        assertEquals(new CdPlayerTimeWarp.State(0, 0, 0), result.state());
        assertEquals(List.of(write(TIMER_WORD, timer, 0), write(DIRECTION_BYTE, 0xFF, 0),
                write(TIME_WARP_BYTE, 0xA5, 0)), result.stores());
        assertTrue(result.reservedPatches().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0,0,2304", "0,2,2304", "0,128,2303", "0,130,2303",
            "256,0,2303", "65535,2,65535"})
    void ppGateUsesExactRawPeriodZoneWordAndUnsignedIntegerX(int zoneAct, int period, int x) {
        var input = new CdPlayerTimeWarp.Input(zoneAct, period, x, 7, 0, 0, 230, 0xA5);
        assertUnchanged(input, CdPlayerTimeWarp.step(input, ABSENT));
    }

    @ParameterizedTest
    @CsvSource({"1,0", "255,255", "45,1", "30,128"})
    void nonzeroChargeReturnsBeforeDirectionTimerOrReservedWork(int charge, int direction) {
        var input = input(charge, 0x8000, direction, 0xFFFF, 0xA5);
        assertUnchanged(input, CdPlayerTimeWarp.step(input, new CdPlayerTimeWarp.Context(
                OptionalInt.of(0x100), Optional.empty())));
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1536,230", "32768,65535"})
    void zeroDirectionReturnsWithoutActivatingOrReadingLaterOwners(int velocity, int timer) {
        var input = input(0, velocity, 0, timer, 0xA5);
        assertUnchanged(input, CdPlayerTimeWarp.step(input, ABSENT));
    }

    @ParameterizedTest
    @CsvSource({"1535,false", "64001,false", "1536,true", "64000,true", "32768,true", "65535,false"})
    void magnitudeIsWrappedUnsignedWordNotSignedAbsOrXVelocity(int rawVelocity, boolean fast) {
        var bank = dirtyBank();
        bank.write(bank.state(12).moveByteAt(0, 7));
        var input = input(0, rawVelocity, 1, 0, 0xA5);
        var result = CdPlayerTimeWarp.step(input, context(bank));
        assertEquals(new CdPlayerTimeWarp.State(fast ? 1 : 0, 1, fast ? 0xA5 : 0), result.state());
        assertEquals(fast ? List.of(write(TIMER_WORD, 0, 1)) : List.of(
                write(TIMER_WORD, 0, 1), write(TIMER_WORD, 1, 0), write(TIME_WARP_BYTE, 0xA5, 0)), result.stores());
        assertTrue(result.reservedPatches().isEmpty());
    }

    @Test
    void zeroTimerSlowPathRecordsActivationBeforeCancellationAndRetainsDirection() {
        var result = CdPlayerTimeWarp.step(input(0, 0x05FF, 0xFF, 0, 0xA5), ABSENT);
        assertEquals(new CdPlayerTimeWarp.State(0, 0xFF, 0), result.state());
        assertEquals(List.of(write(TIMER_WORD, 0, 1), write(TIMER_WORD, 1, 0),
                write(TIME_WARP_BYTE, 0xA5, 0)), result.stores());
    }

    @Test
    void fastActivationWritesOnlyFourRealReservedIdsAndAnimationBytesInNativeOrder() {
        var bank = dirtyBank();
        bank.write(bank.state(12).moveByteAt(0, 0));
        var cut = bank.capture();
        var result = CdPlayerTimeWarp.step(input(0, 0x0600, 0xFF, 0, 0xA5), context(bank));
        assertEquals(new CdPlayerTimeWarp.State(1, 0xFF, 1), result.state());
        var expected = new ArrayList<CdPlayerTimeWarp.Store>();
        expected.add(write(TIMER_WORD, 0, 1));
        expected.add(write(TIME_WARP_BYTE, 0xA5, 1));
        for (int whole = 12; whole <= 15; whole++) {
            var old = cut.slots().get(whole - 1);
            expected.add(new CdPlayerTimeWarp.ReservedWrite(whole, ID, old.byteAt(0), 3));
            expected.add(new CdPlayerTimeWarp.ReservedWrite(whole, ANIMATION, old.byteAt(0x1C), whole - 7));
        }
        assertEquals(expected, result.stores());
        assertEquals(4, result.reservedPatches().size());
        for (int index = 0; index < 4; index++) {
            int whole = 12 + index;
            var patch = result.reservedPatches().get(index);
            assertEquals(cut.slots().get(whole - 1), patch.before());
            byte[] bytes = patch.before().copyBytes();
            bytes[0] = 3;
            bytes[0x1C] = (byte) (whole - 7);
            assertArrayEquals(bytes, patch.after().copyBytes());
            assertEquals(whole, patch.after().identity().wholeIndex());
            assertEquals(0xFFD000 + whole * 0x40, patch.after().identity().nativeAddress24());
        }
        assertEquals(cut.slots(), bank.capture().slots(), "Pure result never writes the actual bank");
        assertTrue(bank.compareAndSet(cut, result.reservedPatches().stream()
                .map(CdPlayerTimeWarp.ReservedPatch::after).toList()));
        for (int whole = 1; whole <= 31; whole++) {
            if (whole < 12 || whole > 15) assertEquals(cut.slots().get(whole - 1), bank.state(whole));
        }
    }

    @Test
    void onlyFirstStarIdIsTestedAndAnOccupiedFirstStarDoesNotSetTimeWarp() {
        var bank = dirtyBank();
        bank.write(bank.state(12).moveByteAt(0, 0xFF));
        for (int whole = 13; whole <= 15; whole++) bank.write(bank.state(whole).moveByteAt(0, 0));
        var cut = bank.capture();
        var input = input(0, 0xFA00, 0xFF, 3, 0xA5);
        var result = CdPlayerTimeWarp.step(input, context(bank));
        assertUnchanged(input, result);
        assertEquals(cut.slots(), bank.capture().slots());
    }

    @Test
    void activeEarlyTimerDoesNotIncrementOrAllocateAndStillRecordsSameValueStores() {
        var bank = dirtyBank();
        bank.write(bank.state(12).moveByteAt(0, 0));
        var result = CdPlayerTimeWarp.step(input(0, 0x8000, 1, 89, 1), context(bank));
        assertEquals(new CdPlayerTimeWarp.State(89, 1, 1), result.state());
        assertEquals(write(TIME_WARP_BYTE, 1, 1), result.stores().getFirst());
        assertEquals(9, result.stores().size());
    }

    @ParameterizedTest
    @CsvSource({"1,false", "89,false", "90,true", "209,true"})
    void earlyCancellationRetainsDirectionButNinetyThroughTwoOhNineClearsIt(int timer, boolean clearDirection) {
        var result = CdPlayerTimeWarp.step(input(0, 0x05FF, 0xFF, timer, 0xA5), ABSENT);
        assertEquals(new CdPlayerTimeWarp.State(0, clearDirection ? 0 : 0xFF, 0), result.state());
        assertEquals(clearDirection ? List.of(write(TIMER_WORD, timer, 0), write(DIRECTION_BYTE, 0xFF, 0),
                write(TIME_WARP_BYTE, 0xA5, 0)) : List.of(write(TIMER_WORD, timer, 0),
                write(TIME_WARP_BYTE, 0xA5, 0)), result.stores());
    }

    @ParameterizedTest
    @CsvSource({"90,1536", "209,64000", "209,32768"})
    void laterFastWindowReturnsWithoutInspectingStarsOrSpawnMode(int timer, int velocity) {
        var input = input(0, velocity, 0xFF, timer, 0xA5);
        assertUnchanged(input, CdPlayerTimeWarp.step(input, new CdPlayerTimeWarp.Context(
                OptionalInt.of(0x100), Optional.empty())));
    }

    @ParameterizedTest
    @CsvSource({"210,0", "229,1536"})
    void actualSpawnModeTwoReturnsWithoutRepeatingSaveOrReadingStars(int timer, int velocity) {
        var input = input(0, velocity, 0xFF, timer, 0xA5);
        assertUnchanged(input, CdPlayerTimeWarp.step(input, new CdPlayerTimeWarp.Context(
                OptionalInt.of(2), Optional.empty())));
    }

    @ParameterizedTest
    @CsvSource({"210", "229"})
    void missingSpawnModeStopsAtItsReachedReadBeforeScrollLock(int timer) {
        var input = input(0, 0, 0xFF, timer, 0xA5);
        var stopped = assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,
                () -> CdPlayerTimeWarp.step(input, ABSENT));
        assertEquals(SPAWN_MODE_READ, stopped.boundary());
        assertUnchanged(input, stopped.prefix());
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "3", "255"})
    void saveStageStopsBeforeFirstUnownedScrollLockStoreRegardlessOfSpeed(int spawn) {
        var input = input(0, 0, 0xFF, 210, 0xA5);
        var stopped = assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,
                () -> CdPlayerTimeWarp.step(input, new CdPlayerTimeWarp.Context(
                        OptionalInt.of(spawn), Optional.empty())));
        assertEquals(SCROLL_LOCK_WRITE, stopped.boundary());
        assertUnchanged(input, stopped.prefix());
    }

    @ParameterizedTest
    @CsvSource({"230", "231", "32768", "65535"})
    void restartStageHasUnsignedPrecedenceAndStopsBeforeHighByteRestartWrite(int timer) {
        var input = input(0, 0, 0xFF, timer, 0xA5);
        var stopped = assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,
                () -> CdPlayerTimeWarp.step(input, new CdPlayerTimeWarp.Context(
                        OptionalInt.of(0x100), Optional.empty())));
        assertEquals(LEVEL_RESTART_WRITE, stopped.boundary());
        assertUnchanged(input, stopped.prefix());
    }

    @ParameterizedTest
    @CsvSource({"0,1", "89,89"})
    void missingRealStarCutRetainsOnlyStagedActivationBeforeTheFirstIdRead(int timer, int finalTimer) {
        var input = input(0, 0x0600, 0xFF, timer, 0xA5);
        var stopped = assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,
                () -> CdPlayerTimeWarp.step(input, ABSENT));
        assertEquals(RESERVED_TIME_STAR_READ, stopped.boundary());
        assertEquals(new CdPlayerTimeWarp.State(finalTimer, 0xFF, 0xA5), stopped.prefix().state());
        assertEquals(timer == 0 ? List.of(write(TIMER_WORD, 0, 1)) : List.of(), stopped.prefix().stores());
        assertTrue(stopped.prefix().reservedPatches().isEmpty());
    }

    @Test
    void malformedSpawnByteIsNotValidatedUntilNativeReadIsReached() {
        var malformed = new CdPlayerTimeWarp.Context(OptionalInt.of(256), Optional.empty());
        var charged = input(1, 0, 0xFF, 210, 0xA5);
        assertUnchanged(charged, CdPlayerTimeWarp.step(charged, malformed));
        assertThrows(IllegalArgumentException.class,
                () -> CdPlayerTimeWarp.step(input(0, 0, 0xFF, 210, 0xA5), malformed));
    }

    @Test
    void allScalarInputsRequireExactRawWidthsWithoutNormalizingThem() {
        int[] valid = {0, 1, 0x0900, 0, 0x8000, 0xFF, 0xFFFF, 0xA5};
        int[] widths = {0xFFFF, 0xFF, 0xFFFF, 0xFF, 0xFFFF, 0xFF, 0xFFFF, 0xFF};
        for (int field = 0; field < valid.length; field++) {
            for (int invalid : new int[]{-1, widths[field] + 1, Integer.MAX_VALUE}) {
                int[] args = valid.clone();
                args[field] = invalid;
                assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.Input(
                        args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]));
            }
        }
        assertThrows(NullPointerException.class, () -> CdPlayerTimeWarp.step(null, ABSENT));
        assertThrows(NullPointerException.class, () -> CdPlayerTimeWarp.step(input(0, 0, 0, 0, 0), null));
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.Context(null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.Context(OptionalInt.empty(), null));
    }

    @Test
    void metadataCopiesCallerListsAndCannotChangeAnUnrelatedImageByteOrOwner() {
        var bank = dirtyBank();
        var before = bank.state(12);
        var after = before.moveByteAt(0, 3).moveByteAt(0x1C, 5);
        var patch = new CdPlayerTimeWarp.ReservedPatch(before, after);
        var stores = new ArrayList<CdPlayerTimeWarp.Store>();
        var patches = new ArrayList<CdPlayerTimeWarp.ReservedPatch>();
        patches.add(patch);
        var result = new CdPlayerTimeWarp.Result(new CdPlayerTimeWarp.State(0, 0, 0), stores, patches);
        stores.add(write(TIMER_WORD, 0, 1));
        patches.clear();
        assertTrue(result.stores().isEmpty());
        assertEquals(List.of(patch), result.reservedPatches());
        assertThrows(UnsupportedOperationException.class, () -> result.stores().add(write(TIMER_WORD, 0, 1)));
        assertThrows(UnsupportedOperationException.class, () -> result.reservedPatches().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerTimeWarp.ReservedPatch(before, after.moveByteAt(0x3F, after.byteAt(0x3F) ^ 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerTimeWarp.ReservedPatch(before, dirtyBank().state(12)
                        .moveByteAt(0, 3).moveByteAt(0x1C, 5)));
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerTimeWarp.ReservedPatch(bank.state(11), bank.state(11).moveByteAt(0, 3)
                        .moveByteAt(0x1C, 4)));
    }

    @Test
    void storeMetadataHasFiniteWidthsSlotsAndLiteralStarValues() {
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.GlobalWrite(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> write(TIMER_WORD, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> write(TIMER_WORD, 0, 65536));
        for (var field : List.of(DIRECTION_BYTE, TIME_WARP_BYTE)) {
            assertThrows(IllegalArgumentException.class, () -> write(field, 256, 0));
            assertThrows(IllegalArgumentException.class, () -> write(field, 0, -1));
        }
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.ReservedWrite(11, ID, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.ReservedWrite(16, ID, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.ReservedWrite(12, ID, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.ReservedWrite(12, ANIMATION, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.ReservedWrite(12, ID, 256, 3));
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.ReservedWrite(12, null, 0, 3));
    }

    private static CdPlayerTimeWarp.Input input(int charge, int velocity, int direction, int timer, int timeWarp) {
        return new CdPlayerTimeWarp.Input(0, 1, 0x0900, charge, velocity, direction, timer, timeWarp);
    }

    private static CdPlayerTimeWarp.Context context(CdReservedObjectBank bank) {
        return new CdPlayerTimeWarp.Context(OptionalInt.empty(), Optional.of(bank.capture()));
    }

    private static CdPlayerTimeWarp.GlobalWrite write(CdPlayerTimeWarp.GlobalField field, int old, int value) {
        return new CdPlayerTimeWarp.GlobalWrite(field, old, value);
    }

    private static void assertUnchanged(CdPlayerTimeWarp.Input input, CdPlayerTimeWarp.Result result) {
        assertEquals(new CdPlayerTimeWarp.State(input.timer(), input.direction(), input.timeWarp()), result.state());
        assertTrue(result.stores().isEmpty());
        assertTrue(result.reservedPatches().isEmpty());
    }

    /** Invokes only the existing explicit ClearObjects factory; no snapshot/token is fabricated. */
    private static CdReservedObjectBank dirtyBank() {
        try {
            var factory = CdReservedObjectBank.class.getDeclaredMethod("clearedAfterLevelStart");
            factory.setAccessible(true);
            var bank = (CdReservedObjectBank) factory.invoke(null);
            for (int whole = 1; whole <= 31; whole++) {
                byte[] bytes = new byte[64];
                for (int offset = 0; offset < bytes.length; offset++) bytes[offset] = (byte) (whole * 19 + offset * 37);
                bank.write(bank.state(whole).withBytes(bytes));
            }
            return bank;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Existing reserved ClearObjects cut is required", error);
        }
    }
}
