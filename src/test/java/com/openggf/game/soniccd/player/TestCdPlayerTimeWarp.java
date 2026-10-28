package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;
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
    @Test void save210StagesTheHighLockBytePeriodAll26StoresAndFinalSpawnInNativeOrder() {
        var reads = new SaveInputs(); var initial = CdTimeWarpSaveData.State.unknown();
        var result = CdPlayerTimeWarp.step(input(0, 0x600, 255, 210, 1), saveContext(0, initial, reads));
        var save = result.saveResult().orElseThrow();
        assertEquals(new CdPlayerTimeWarp.State(210, 255, 1), result.state());
        assertTrue(result.stores().isEmpty()); assertTrue(result.reservedPatches().isEmpty());
        assertEquals(0xA55A, save.expectedScrollLockWord()); assertEquals(0x015A, save.finalScrollLockWord());
        assertEquals(1, save.expectedPeriodByte()); assertEquals(0x80, save.finalPeriodByte());
        assertEquals(0, save.expectedSpawnByte()); assertEquals(2, save.finalSpawnByte());
        assertSame(initial, save.expectedSavedState()); assertEquals(29, save.effects().size());
        assertEquals(new CdPlayerTimeWarp.LockWrite(0xA55A, 0x015A), save.effects().getFirst());
        assertEquals(new CdPlayerTimeWarp.PeriodWrite(1, 0x80), save.effects().get(1));
        assertEquals(new CdPlayerTimeWarp.SpawnWrite(0, 2), save.effects().getLast());
        assertEquals(List.of(CdTimeWarpSaveData.Store.values()), save.effects().stream()
                .filter(CdPlayerTimeWarp.SavedWrite.class::isInstance).map(CdPlayerTimeWarp.SavedWrite.class::cast)
                .map(e -> e.call().store()).toList());
        assertEquals("LOCK", reads.order.getFirst()); assertEquals(25, reads.order.size());
        assertEquals(0xA5, save.finalSavedState().read(CdTimeWarpSaveData.SavedField.SPAWN_MODE),
                "SaveTimeWarpData performs a fresh SPAWN_MODE read, distinct from the earlier branch operand");
        assertEquals(0xB7, save.finalSavedState().read(CdTimeWarpSaveData.SavedField.PRIMARY_FLAGS));
        assertEquals(0x22, save.finalSavedState().read(CdTimeWarpSaveData.SavedField.WATER_ROUTINE));
        assertEquals(0x00050000, save.finalSavedState().read(CdTimeWarpSaveData.SavedField.TIME_LONG));
        for (byte known : save.finalSavedState().knownMask().copy()) assertEquals(1, known);
        for (byte known : initial.knownMask().copy()) assertEquals(0, known);
    }

    @Test void save210PeriodAdditionUsesRawBytesAndOnlyRawPastRequestsPcmBeforeThePeriodWrite() {
        int[][] cases = {{0, 255, 0x80}, {0, 1, 0x81}, {1, 1, 0x82}, {1, 128, 0x80},
                {2, 1, 0x82}, {0x80, 255, 0x82}, {0x81, 1, 0x80}, {255, 1, 0x80}};
        for (int[] vector : cases) {
            var reads = new SaveInputs();
            var input = new CdPlayerTimeWarp.Input(0, vector[0], 0x900, 0, 0, vector[1], 229, 0xA5);
            var save = CdPlayerTimeWarp.step(input, saveContext(255, CdTimeWarpSaveData.State.unknown(), reads)).saveResult().orElseThrow();
            assertEquals(vector[2], save.finalPeriodByte()); assertEquals(vector[0], save.expectedPeriodByte());
            int at = vector[0] == 0 ? 2 : 1;
            assertEquals(new CdPlayerTimeWarp.PeriodWrite(vector[0], vector[2]), save.effects().get(at));
            var commands = save.effects().stream().filter(CdPlayerTimeWarp.SubCpuRequest.class::isInstance).toList();
            assertEquals(vector[0] == 0 ? 1 : 0, commands.size());
            if (vector[0] == 0) assertEquals(new CdPlayerTimeWarp.SubCpuRequest(CdPlayerTimeWarp.SubCpuCommand.SCMD_FADEPCM),
                    save.effects().get(1));
            assertEquals(new CdPlayerTimeWarp.SpawnWrite(255, 2), save.effects().getLast());
        }
    }

    @Test void saveReadsAreNotSampledBehindChargeDirectionSpawnTwoOrTheRestartBoundary() {
        var reads = new SaveInputs(); reads.failure = new IllegalStateException("must remain unread"); reads.failLock = true;
        var context = saveContext(2, CdTimeWarpSaveData.State.unknown(), reads);
        assertTrue(CdPlayerTimeWarp.step(input(1, 0, 255, 210, 1), context).saveResult().isEmpty());
        assertTrue(CdPlayerTimeWarp.step(input(0, 0, 0, 210, 1), context).saveResult().isEmpty());
        assertTrue(CdPlayerTimeWarp.step(input(0, 0, 255, 210, 1), context).saveResult().isEmpty());
        var stopped = assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,
                () -> CdPlayerTimeWarp.step(input(0, 0, 255, 230, 1), context));
        assertEquals(LEVEL_RESTART_WRITE, stopped.boundary()); assertTrue(stopped.prefix().saveResult().isEmpty());
        assertTrue(reads.order.isEmpty());
        var oldContext = new CdPlayerTimeWarp.Context(OptionalInt.of(0), Optional.empty());
        assertEquals(SCROLL_LOCK_WRITE, assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,
                () -> CdPlayerTimeWarp.step(input(0, 0, 255, 210, 1), oldContext)).boundary());
    }

    @Test void eachSavedStoreHasThePreviousLocalValueAndPreservesEveryUnwrittenSavedByte() {
        var reads = new SaveInputs(); var initial = CdTimeWarpSaveData.State.unknown();
        var save = CdPlayerTimeWarp.step(input(0, 0, 255, 210, 1), saveContext(0, initial, reads)).saveResult().orElseThrow();
        var addresses = new java.util.TreeSet<Integer>();
        for (var field : CdTimeWarpSaveData.SavedField.values())
            for (int i = 0; i < field.width(); i++) addresses.add(field.nativeAddress() + i);
        assertEquals(42, addresses.size());
        var previous = initial; int count = 0;
        for (var effect : save.effects()) {
            if (!(effect instanceof CdPlayerTimeWarp.SavedWrite written)) continue;
            var call = written.call(); assertEquals(previous, call.expectedState());
            for (int address : addresses) {
                if (address < call.store().nativeAddress() || address >= call.store().nativeAddress() + call.store().width()) {
                    assertEquals(previous.knownByte(address), call.finalState().knownByte(address));
                }
            }
            previous = call.finalState(); count++;
        }
        assertEquals(26, count); assertEquals(previous, save.finalSavedState());
    }

    @Test void saveReadFailuresEscapeUnchangedAndNoCompletedSaveResultOrCallerValueIsPublished() {
        var initial = CdTimeWarpSaveData.State.unknown(); var reads = new SaveInputs();
        reads.failAt = CdTimeWarpSaveData.Source.BG2_Y; reads.failure = new IllegalStateException("native source unavailable");
        var failedReads = reads;
        assertSame(reads.failure, assertThrows(IllegalStateException.class,
                () -> CdPlayerTimeWarp.step(input(0, 0, 255, 210, 1), saveContext(0, initial, failedReads))));
        assertEquals(CdTimeWarpSaveData.Source.BG2_Y.name(), reads.order.getLast());
        assertFalse(reads.order.contains(CdTimeWarpSaveData.Source.TIME_LONG.name()));
        for (byte known : initial.knownMask().copy()) assertEquals(0, known);
        reads = new SaveInputs(); reads.lock = 65536;
        var badLock = reads;
        assertThrows(IllegalArgumentException.class,
                () -> CdPlayerTimeWarp.step(input(0, 0, 255, 210, 1), saveContext(0, initial, badLock)));
        assertEquals(List.of("LOCK"), reads.order);
    }

    @Test void repeatedWaterSourceAndUnsignedFiveMinuteCapUseTheActualReachedReadValues() {
        for (int raw : new int[]{0x0004FFFF, 0x00050000, 0x00050001, 0x80000000, -1}) {
            var reads = new SaveInputs(); reads.time = raw;
            var save = CdPlayerTimeWarp.step(input(0, 0, 1, 210, 1),
                    saveContext(1, CdTimeWarpSaveData.State.unknown(), reads)).saveResult().orElseThrow();
            assertEquals(Integer.compareUnsigned(raw, 0x00050000) < 0 ? raw : 0x00050000,
                    save.finalSavedState().read(CdTimeWarpSaveData.SavedField.TIME_LONG));
            var water = save.effects().stream().filter(CdPlayerTimeWarp.SavedWrite.class::isInstance)
                    .map(CdPlayerTimeWarp.SavedWrite.class::cast).map(CdPlayerTimeWarp.SavedWrite::call)
                    .filter(c -> c.store() == CdTimeWarpSaveData.Store.WATER_ROUTINE_FIRST
                            || c.store() == CdTimeWarpSaveData.Store.WATER_ROUTINE_SECOND).toList();
            assertEquals(0x11, water.getFirst().rawValue()); assertEquals(0x22, water.getLast().rawValue());
            assertEquals(2, reads.waterReads);
        }
    }

    @Test void saveMetadataCopiesListsRequiresRawWidthsAndRetainsNoReadPortOrOwner() {
        var reads = new SaveInputs(); var initial = CdTimeWarpSaveData.State.unknown();
        var original = CdPlayerTimeWarp.step(input(0, 0, 1, 210, 1), saveContext(0, initial, reads)).saveResult().orElseThrow();
        var effects = new ArrayList<>(original.effects());
        var copy = new CdPlayerTimeWarp.SaveResult(original.expectedScrollLockWord(), original.finalScrollLockWord(),
                original.expectedPeriodByte(), original.finalPeriodByte(), original.expectedSpawnByte(), original.finalSpawnByte(),
                original.expectedSavedState(), original.finalSavedState(), effects);
        effects.clear(); assertEquals(29, copy.effects().size()); assertThrows(UnsupportedOperationException.class, copy.effects()::clear);
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.LockWrite(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.LockWrite(0, 65536));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.PeriodWrite(256, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.SpawnWrite(0, -1));
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.SavedWrite(null));
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.SubCpuRequest(null));
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.SaveContext(null, reads));
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.SaveContext(initial, null));
        assertEquals(9, CdPlayerTimeWarp.SaveResult.class.getRecordComponents().length);
        for (var component : CdPlayerTimeWarp.SaveResult.class.getRecordComponents())
            assertNotEquals(CdPlayerTimeWarp.SaveReads.class, component.getType());
    }

    @Test void saveContextAndResultCompatibilityConstructorsKeepTheOldEmptyOptionalContract() {
        var context = new CdPlayerTimeWarp.Context(OptionalInt.of(2), Optional.empty());
        assertTrue(context.saveContext().isEmpty());
        assertTrue(new CdPlayerTimeWarp.Result(new CdPlayerTimeWarp.State(0, 0, 0), List.of(), List.of()).saveResult().isEmpty());
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.Context(OptionalInt.empty(), Optional.empty(), null));
        assertThrows(NullPointerException.class,
                () -> new CdPlayerTimeWarp.Result(new CdPlayerTimeWarp.State(0, 0, 0), List.of(), List.of(), null));
        assertEquals(8, CdPlayerTimeWarp.Input.class.getRecordComponents().length);
        assertEquals(3, CdPlayerTimeWarp.State.class.getRecordComponents().length);
        assertEquals(4, CdPlayerTimeWarp.Context.class.getRecordComponents().length);
        assertEquals(5, CdPlayerTimeWarp.Result.class.getRecordComponents().length);
    }

    private static CdPlayerTimeWarp.Context saveContext(int spawn, CdTimeWarpSaveData.State saved, SaveInputs reads) {
        return new CdPlayerTimeWarp.Context(OptionalInt.of(spawn), Optional.empty(),
                Optional.of(new CdPlayerTimeWarp.SaveContext(saved, reads)));
    }

    private static final class SaveInputs implements CdPlayerTimeWarp.SaveReads {
        final List<String> order = new ArrayList<>();
        int lock = 0xA55A, waterReads, time = -1;
        RuntimeException failure; CdTimeWarpSaveData.Source failAt; boolean failLock;
        @Override public int readScrollLockWord() {
            order.add("LOCK"); if (failLock) throw failure; return lock;
        }
        @Override public int read(CdTimeWarpSaveData.Source source) {
            order.add(source.name()); if (source == failAt) throw failure;
            return switch (source) {
                case WATER_ROUTINE -> ++waterReads == 1 ? 0x11 : 0x22;
                case PRIMARY_FLAGS -> 255;
                case TIME_LONG -> time;
                default -> source.width() == 1 ? 0xA5 : 0xBEEF;
            };
        }
    }

    @Test void restart230ReturnsTheNativeHighByteStoreWithDirtyLowByteAndNoLaterSaveReads() {
        int[][] vectors = {{230, 0, 0x0100}, {231, 1, 0x0101}, {230, 0xA55A, 0x015A},
                {65535, 65535, 0x01FF}, {32768, 0x0100, 0x0100}};
        for (int[] vector : vectors) {
            var result = CdPlayerTimeWarp.step(input(0, 0, 255, vector[0], 0xA5), restartContext(vector[1]));
            assertEquals(new CdPlayerTimeWarp.RestartResult(vector[1], vector[2]), result.restartResult().orElseThrow());
            assertEquals(new CdPlayerTimeWarp.State(vector[0], 255, 0xA5), result.state());
            assertTrue(result.stores().isEmpty()); assertTrue(result.reservedPatches().isEmpty()); assertTrue(result.saveResult().isEmpty());
        }
    }

    @Test void restartWordDoesNotBypassEarlierRestrictionChargeDirectionOrTheUnchanged229SpawnTwoReturn() {
        var context = restartContext(0xA55A);
        for (var earlier : List.of(input(1, 0x8000, 255, 65535, 0xA5), input(0, 0x8000, 0, 65535, 0xA5))) {
            var result = CdPlayerTimeWarp.step(earlier, context);
            assertUnchanged(earlier, result); assertTrue(result.restartResult().isEmpty()); assertTrue(result.saveResult().isEmpty());
        }
        var restricted = CdPlayerTimeWarp.step(new CdPlayerTimeWarp.Input(0, 0, 0x08FF, 255, 0x8000, 255, 65535, 0xA5), context);
        assertEquals(new CdPlayerTimeWarp.State(0, 0, 0), restricted.state());
        assertTrue(restricted.restartResult().isEmpty()); assertTrue(restricted.saveResult().isEmpty());
        assertEquals(List.of(write(TIMER_WORD, 65535, 0), write(DIRECTION_BYTE, 255, 0), write(TIME_WARP_BYTE, 0xA5, 0)), restricted.stores());
        var beforeRestart = input(0, 0, 255, 229, 0xA5);
        var spawnTwo = new CdPlayerTimeWarp.Context(OptionalInt.of(2), Optional.empty(), context.saveContext(), context.levelRestartWord());
        var unchanged = CdPlayerTimeWarp.step(beforeRestart, spawnTwo);
        assertUnchanged(beforeRestart, unchanged); assertTrue(unchanged.restartResult().isEmpty()); assertTrue(unchanged.saveResult().isEmpty());
    }

    @Test void oldContextsKeepTheExactRestartFaultAndKnownRestartWordsDoNotAdmitEarlierUnownedStages() {
        var context = restartContext(0xA55A);
        for (var absent : List.of(new CdPlayerTimeWarp.Context(OptionalInt.of(256), Optional.empty()),
                new CdPlayerTimeWarp.Context(OptionalInt.of(256), Optional.empty(), context.saveContext()))) {
            var input = input(0, 0, 255, 230, 1);
            var stopped = assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class, () -> CdPlayerTimeWarp.step(input, absent));
            assertEquals(LEVEL_RESTART_WRITE, stopped.boundary()); assertUnchanged(input, stopped.prefix());
            assertTrue(stopped.prefix().saveResult().isEmpty()); assertTrue(stopped.prefix().restartResult().isEmpty());
        }
        var noSave = new CdPlayerTimeWarp.Context(OptionalInt.of(0), Optional.empty(), Optional.empty(), OptionalInt.of(0xA55A));
        assertEquals(SCROLL_LOCK_WRITE, assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,
                () -> CdPlayerTimeWarp.step(input(0, 0, 255, 210, 1), noSave)).boundary());
        assertEquals(RESERVED_TIME_STAR_READ, assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,
                () -> CdPlayerTimeWarp.step(input(0, 0x600, 255, 89, 1), noSave)).boundary());
    }

    @Test void restartMetadataAndBothLegacyConstructorAritiesKeepExplicitWordAndOptionalContracts() {
        var oldTwo = new CdPlayerTimeWarp.Context(OptionalInt.empty(), Optional.empty());
        var oldThree = new CdPlayerTimeWarp.Context(OptionalInt.empty(), Optional.empty(), Optional.empty());
        assertTrue(oldTwo.levelRestartWord().isEmpty()); assertTrue(oldThree.levelRestartWord().isEmpty());
        var state = new CdPlayerTimeWarp.State(230, 255, 1);
        assertTrue(new CdPlayerTimeWarp.Result(state, List.of(), List.of()).restartResult().isEmpty());
        var initial = CdTimeWarpSaveData.State.unknown();
        var saved = new CdPlayerTimeWarp.SaveResult(0, 0x0100, 1, 0x80, 1, 2, initial, initial, List.of());
        var oldFour = new CdPlayerTimeWarp.Result(state, List.of(), List.of(), Optional.of(saved));
        assertSame(saved, oldFour.saveResult().orElseThrow()); assertTrue(oldFour.restartResult().isEmpty());
        for (int invalid : new int[]{-1, 65536, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.Context(
                    OptionalInt.empty(), Optional.empty(), Optional.empty(), OptionalInt.of(invalid)));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.RestartResult(invalid, 0x0100));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.RestartResult(0, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.RestartResult(0, 1), "MOVE.B high is not MOVE.W1");
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerTimeWarp.RestartResult(0xFFFF, 0xFFFF));
        var dirtyWord = new CdPlayerTimeWarp.RestartResult(0xFFFF, 0x01FF);
        assertEquals(0xFFFF, dirtyWord.expectedRestartWord()); assertEquals(0x01FF, dirtyWord.finalRestartWord());
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.Context(
                OptionalInt.empty(), Optional.empty(), Optional.empty(), null));
        assertThrows(NullPointerException.class, () -> new CdPlayerTimeWarp.Result(state, List.of(), List.of(), Optional.empty(), null));
        assertEquals(List.of(2, 3, 4), java.util.Arrays.stream(CdPlayerTimeWarp.Context.class.getConstructors())
                .map(c -> c.getParameterCount()).sorted().toList());
        assertEquals(List.of(3, 4, 5), java.util.Arrays.stream(CdPlayerTimeWarp.Result.class.getConstructors())
                .map(c -> c.getParameterCount()).sorted().toList());
        assertEquals(2, CdPlayerTimeWarp.RestartResult.class.getRecordComponents().length);
        assertEquals(8, CdPlayerTimeWarp.Input.class.getRecordComponents().length); assertEquals(3, CdPlayerTimeWarp.State.class.getRecordComponents().length);
    }

    private static CdPlayerTimeWarp.Context restartContext(int oldWord) {
        var neverRead = new CdPlayerTimeWarp.SaveReads() {
            @Override public int readScrollLockWord() { throw new AssertionError("Restart precedes scrollLock/save"); }
            @Override public int read(CdTimeWarpSaveData.Source source) { throw new AssertionError("Restart must not sample " + source); }
        };
        return new CdPlayerTimeWarp.Context(OptionalInt.of(256), Optional.empty(),
                Optional.of(new CdPlayerTimeWarp.SaveContext(CdTimeWarpSaveData.State.unknown(), neverRead)), OptionalInt.of(oldWord));
    }

}
