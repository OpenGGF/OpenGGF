package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Literal Powerup/Main.asm component vectors at native pin1ecc5f3a. The synthetic
 * directory below deliberately shares native script5 across nine IDs; it is a
 * constructed-format fixture, not media authentication or a live World proof.
 */
class TestCdTimeStars {
    private static final int MAP = 0x23EE9C;

    @Test void initRetainsEveryUnwrittenByteAndNeverReadsHistoryPowerupOrDraws() {
        var f = new Fixture();
        var before = f.bank.state(12).moveByte(ROUTINE, 0).moveByte(ANIMATION, 5);
        f.bank.write(before);
        var cut = f.bank.capture();
        var result = f.body.run(12, globals(0, 1, 0, CdAssetBytes.copyOf(new byte[0]), 0xFFFF));
        var expected = before.moveByte(ROUTINE, 6).moveLong(MAPPINGS, MAP)
                .moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 1).moveByte(WIDTH, 16).moveWord(TILE, 0x544);
        assertEquals(expected, result.state());
        assertEquals(expected, f.bank.state(12));
        assertEquals(List.of(new CdTimeStars.RoutineEntered(CdTimeStars.NativeRoutine.INIT)), result.effects());
        assertTrue(f.calls.isEmpty());
        assertOthers(cut, f.bank, 12);
    }

    @Test void everyRawAnimationUsesNativeUnsignedInitBranches() {
        var f = new Fixture();
        for (int animation = 0; animation < 256; animation++) {
            var source = f.bank.state(12).moveByte(ID, 3).moveByte(ROUTINE, 0).moveByte(ANIMATION, animation);
            f.bank.write(source);
            int expected = animation == 0 ? 2 : animation < 5 ? 4 : 6;
            assertEquals(expected, f.body.run(12, normal(0x10)).state().unsignedByte(ROUTINE));
        }
        assertTrue(f.calls.isEmpty());
    }

    @ParameterizedTest @CsvSource({"12,5", "13,6", "14,7", "15,8"})
    void eachActualReservedIdentityInitializesItsOwnImageOnly(int whole, int animation) {
        var f = new Fixture();
        f.bank.write(f.bank.state(whole).moveByte(ID, 3).moveByte(ROUTINE, 0).moveByte(ANIMATION, animation));
        var before = f.bank.capture();
        var result = f.body.run(whole, normal(0x10));
        assertEquals(whole, result.state().identity().wholeIndex());
        assertEquals(6, result.state().unsignedByte(ROUTINE));
        assertEquals(animation, result.state().unsignedByte(ANIMATION));
        assertOthers(before, f.bank, whole); assertTrue(f.calls.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 5, 7, 8, 254, 255})
    void unsupportedRoutineStopsBeforeTimeWarpAndEveryWrite(int routine) {
        var f = new Fixture();
        f.bank.write(f.bank.state(12).moveByte(ROUTINE, routine));
        var before = f.bank.capture();
        var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class, () -> f.body.run(12, normal(0x10)));
        assertEquals(CdTimeStars.UnsupportedBoundary.ROUTINE_DISPATCH, failure.boundary());
        assertEquals(routine, failure.value()); assertEquals(12, failure.wholeIndex());
        assertEquals(before.slots(), f.bank.capture().slots()); assertTrue(f.calls.isEmpty());
    }

    @Test void zeroTimeWarpDeletesAllSixtyFourBytesWithoutLaterReads() {
        var f = new Fixture(); var before = f.bank.capture();
        var result = f.body.run(12, globals(6, 0, 255, CdAssetBytes.copyOf(new byte[0]), 0xFFFF));
        assertTrue(result.deleted()); assertArrayEquals(new byte[64], result.state().copyBytes());
        assertEquals(List.of(new CdTimeStars.RoutineEntered(CdTimeStars.NativeRoutine.TIME_STARS), new CdTimeStars.Deleted()), result.effects());
        assertOthers(before, f.bank, 12); assertTrue(f.calls.isEmpty());
    }

    @Test void mmzLayerReadStopsAfterOnlyTheTileHighByteOr() {
        var f = new Fixture();
        f.bank.write(f.bank.state(12).moveWord(TILE, 0x1234)); var source = f.bank.state(12);
        var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class,
                () -> f.body.run(12, globals(6, 1, 0, history(256), 0x10)));
        assertEquals(CdTimeStars.UnsupportedBoundary.LAYER_READ, failure.boundary());
        assertEquals(source.moveByteAt(2, 0x92), f.bank.state(12)); assertTrue(f.calls.isEmpty());
    }

    @ParameterizedTest @CsvSource({"5,16,0,12,4", "6,16,0,244,4", "7,16,0,220,4", "8,16,0,196,4",
            "5,16,4,8,8", "5,16,20,248,0", "5,16,24,244,0", "5,16,252,16,0", "5,16,255,13,3"})
    void historyUsesLiteralLowByteDeltaAndDirtyPhaseRules(int animation, int recordIndex, int phase, int offset, int nextPhase) {
        var f = new Fixture();
        f.bank.write(f.bank.state(12).moveByte(ANIMATION, animation).moveByte(PREVIOUS_ANIMATION, animation)
                .moveByteAt(0x30, phase));
        if ((offset & 1) != 0) {
            var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class, () -> f.body.run(12, normal(recordIndex)));
            assertEquals(CdTimeStars.UnsupportedBoundary.HISTORY_X_ADDRESS, failure.boundary());
            assertEquals(offset, failure.value()); assertEquals(nextPhase, f.bank.state(12).byteAt(0x30));
        } else {
            var result = f.body.run(12, normal(recordIndex));
            assertEquals(0x1000 + offset, result.state().word(X));
            assertEquals(0x2000 + offset, result.state().word(Y));
            assertEquals(nextPhase, result.state().byteAt(0x30));
        }
    }

    @ParameterizedTest @CsvSource({"272,268", "32784,-32756", "65296,-244"})
    void historyPreservesTheWordHighByteAndUsesSignedAddress(int recordIndex, int expectedOffset) {
        var f = new Fixture(); var source = f.bank.state(12);
        var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class,
                () -> f.body.run(12, globals(0, 1, 0xA5, history(256), recordIndex)));
        assertEquals(CdTimeStars.UnsupportedBoundary.HISTORY_X_ADDRESS, failure.boundary());
        assertEquals(expectedOffset, failure.value());
        assertEquals(source.moveByteAt(0x30, 4), f.bank.state(12)); assertTrue(f.calls.isEmpty());
    }

    @Test void historyYFaultRetainsAlreadyWrittenPhaseAndXOnly() {
        var f = new Fixture(); var source = f.bank.state(12);
        var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class,
                () -> f.body.run(12, globals(0, 1, 0xA5, CdAssetBytes.copyOf(new byte[]{0x12, 0x34}), 4)));
        assertEquals(CdTimeStars.UnsupportedBoundary.HISTORY_Y_ADDRESS, failure.boundary());
        assertEquals(2, failure.value());
        assertEquals(source.moveByteAt(0x30, 4).moveWord(X, 0x1234), f.bank.state(12));
        assertTrue(f.calls.isEmpty());
    }

    @Test void activeHistoryCopiesWordsFlagsAndOneAnimatorWithoutTouchingFractionsOrOtherSlots() {
        var f = new Fixture(); var source = f.bank.state(12); var cut = f.bank.capture();
        var result = f.body.run(12, normal(0x10));
        var expected = source.moveByteAt(0x30, 4).moveWord(X, 0x100C).moveWord(Y, 0x200C)
                .moveByte(FLAGS, 0xA5).moveByte(ANIMATION_FRAME, 1).moveByte(ANIMATION_TIME, 0)
                .moveByte(MAP_FRAME, 8).moveByte(SPRITE_FLAGS, 0x85);
        assertEquals(expected, result.state()); assertEquals(expected, f.drawSeen);
        assertEquals(List.of("read", "draw"), f.calls);
        assertEquals(List.of(new CdTimeStars.RoutineEntered(CdTimeStars.NativeRoutine.TIME_STARS),
                new CdTimeStars.AnimationReturned(), new CdTimeStars.DrawReturned()), result.effects());
        assertOthers(cut, f.bank, 12);
        assertEquals(source.word(X_FRACTION), result.state().word(X_FRACTION));
        assertEquals(source.word(Y_FRACTION), result.state().word(Y_FRACTION));
    }

    @Test void waitingAnimationDoesNotReadAnUnreachedForeignScript() {
        var f = new Fixture();
        f.bank.write(f.bank.state(12).moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 255).moveByte(ANIMATION_TIME, 1));
        var result = f.body.run(12, normal(0x10));
        assertEquals(0, result.state().unsignedByte(ANIMATION_TIME)); assertEquals(0, result.state().unsignedByte(MAP_FRAME));
        assertEquals(1, f.calls.stream().filter("draw"::equals).count());
    }

    @Test void reachedDirectoryFaultRetainsResetAndDecrement() {
        var f = new Fixture();
        f.bank.write(f.bank.state(12).moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 4)
                .moveByte(ANIMATION_FRAME, 15).moveByte(ANIMATION_TIME, 12));
        var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class, () -> f.body.run(12, normal(0x10)));
        assertEquals(CdTimeStars.UnsupportedBoundary.ANIMATION_DIRECTORY_ADDRESS, failure.boundary());
        var image = f.bank.state(12);
        assertEquals(255, image.unsignedByte(PREVIOUS_ANIMATION)); assertEquals(0, image.unsignedByte(ANIMATION_FRAME));
        assertEquals(255, image.unsignedByte(ANIMATION_TIME)); assertTrue(f.calls.isEmpty());
    }

    @Test void missingAnimationSpeedRetainsEarlierHistoryAndTimerPrefix() {
        var f = new Fixture(table(18, 40, new byte[0]));
        var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class, () -> f.body.run(12, normal(0x10)));
        assertEquals(CdTimeStars.UnsupportedBoundary.ANIMATION_SPEED_ADDRESS, failure.boundary());
        assertEquals(40, failure.value()); assertEquals(255, f.bank.state(12).unsignedByte(ANIMATION_TIME));
        assertEquals(0x100C, f.bank.state(12).word(X)); assertEquals(0xA5, f.bank.state(12).unsignedByte(FLAGS));
        assertTrue(f.calls.isEmpty());
    }

    @Test void missingAnimationFrameRetainsLoadedSpeed() {
        var f = new Fixture(table(19, 18, new byte[]{9}));
        var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class, () -> f.body.run(12, normal(0x10)));
        assertEquals(CdTimeStars.UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, failure.boundary());
        assertEquals(19, failure.value()); assertEquals(9, f.bank.state(12).unsignedByte(ANIMATION_TIME));
        assertTrue(f.calls.isEmpty());
    }

    @Test void missingFeOperandRetainsSpeedButDoesNotInventFrameStore() {
        var f = new Fixture(table(20, 18, new byte[]{2, (byte) 0xFE}));
        var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class, () -> f.body.run(12, normal(0x10)));
        assertEquals(CdTimeStars.UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, failure.boundary());
        assertEquals(20, failure.value()); assertEquals(2, f.bank.state(12).unsignedByte(ANIMATION_TIME));
        assertEquals(0, f.bank.state(12).unsignedByte(ANIMATION_FRAME)); assertTrue(f.calls.isEmpty());
    }

    @Test void ffResetAndFeSubtractionArePublishedBeforeTheirTargetFault() {
        var ff = new Fixture(table(20, 18, new byte[]{2, (byte) 0xFF}));
        ff.bank.write(ff.bank.state(12).moveByte(ANIMATION_FRAME, 0));
        // FF's restart frame is the same available negative byte: native AniNext treats it as DATA.
        assertEquals(31, ff.body.run(12, normal(0x10)).state().unsignedByte(MAP_FRAME));
        var fe = new Fixture(table(21, 18, new byte[]{2, (byte) 0xFE, 2}));
        var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class, () -> fe.body.run(12, normal(0x10)));
        assertEquals(CdTimeStars.UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, failure.boundary());
        assertEquals(273, failure.value()); assertEquals(254, fe.bank.state(12).unsignedByte(ANIMATION_FRAME));
    }

    @ParameterizedTest @CsvSource({"0,134,5", "2,134,5", "6,6,2", "128,134,5", "134,134,2", "8,8,1", "136,136,1", "255,255,1"})
    void powerupSelectionRetainsRawBytesAndSeparateMoveThenBset(int before, int after, int calls) {
        var f = new Fixture(); f.powerup = before;
        var result = f.body.run(12, normal(0x10));
        assertEquals(after, f.powerup); assertEquals(calls, f.calls.size());
        assertEquals((before & 15) < 8, result.effects().stream().anyMatch(CdTimeStars.DrawReturned.class::isInstance));
        if ((before & 15) < 8 && (before & 15) != 6) {
            assertEquals(List.of("read", "write:6", "read", "write:134", "draw"), f.calls);
            assertEquals(List.of(new CdTimeStars.PowerupWritten(before, 6), new CdTimeStars.PowerupWritten(6, 134)),
                    result.effects().stream().filter(CdTimeStars.PowerupWritten.class::isInstance).toList());
        }
    }

    @Test void drawReloadAdmitsOnlyOwnOnscreenBit() {
        var f = new Fixture();
        f.draw = (bank, identity) -> bank.write(bank.state(identity.wholeIndex()).moveByte(SPRITE_FLAGS,
                bank.state(identity.wholeIndex()).unsignedByte(SPRITE_FLAGS) & 0x7F));
        var result = f.body.run(12, normal(0x10));
        assertEquals(5, result.state().unsignedByte(SPRITE_FLAGS)); assertEquals(f.bank.state(12), result.state());
    }

    @Test void fcUsesItsNewRoutineForTheSeparatePowerupStoresWithoutAnOperandRead() {
        var f = new Fixture(table(20, 18, new byte[]{0, (byte) 0xFC}));
        var result = f.body.run(12, normal(0x10));
        assertEquals(8, result.state().unsignedByte(ROUTINE));
        assertEquals(136, f.powerup);
        assertEquals(List.of("read", "write:8", "read", "write:136", "draw"), f.calls);
        var failure = assertThrows(CdTimeStars.UnsupportedTimeStars.class, () -> f.body.run(12, normal(0x10)));
        assertEquals(CdTimeStars.UnsupportedBoundary.ROUTINE_DISPATCH, failure.boundary());
    }

    @Test void aNonAdoptingPowerupMoveCannotSilentlyReachDraw() {
        var f = new Fixture(); f.powerup = 0; f.ignoreWrites = true;
        assertThrows(IllegalStateException.class, () -> f.body.run(12, normal(0x10)));
        assertEquals(0, f.powerup);
        assertEquals(List.of("read", "write:6", "read"), f.calls);
        assertEquals(8, f.bank.state(12).unsignedByte(MAP_FRAME));
    }

    @Test void malformedPortValueFailsAtItsReachedReadAfterTheAnimationPrefix() {
        var f = new Fixture();
        var body = new CdTimeStars(f.bank, assets(defaultTable()), new CdTimeStars.PowerupPort() {
            public int read() { return 256; }
            public void write(int value) { fail("No write after malformed reached byte"); }
        }, f.drawer);
        assertThrows(IllegalArgumentException.class, () -> body.run(12, normal(0x10)));
        assertEquals(8, f.bank.state(12).unsignedByte(MAP_FRAME));
        assertTrue(f.calls.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"read", "write", "draw"})
    void callbackPoolMutationOnReturnIsRejectedAndNeverRolledBack(String site) {
        var f = new Fixture(); f.powerup = 0;
        f.attack = site; f.attackSlot = 20;
        assertThrows(IllegalStateException.class, () -> f.body.run(12, normal(0x10)));
        assertEquals(77, f.bank.state(20).unsignedByte(ID));
    }

    @ParameterizedTest @ValueSource(strings = {"read", "write", "draw"})
    void originalCallbackFailureSurvivesWithSuppressedNonwriteViolation(String site) {
        var f = new Fixture(); f.powerup = 0; f.attack = site; f.attackSlot = 20;
        var failure = new AssertionError("original callback"); f.callbackFailure = failure;
        assertSame(failure, assertThrows(AssertionError.class, () -> f.body.run(12, normal(0x10))));
        assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(IllegalStateException.class::isInstance));
    }

    @Test void drawOwnNonBitSevenMutationIsRejectedAfterItsCompletedAnimation() {
        var f = new Fixture();
        f.draw = (bank, identity) -> bank.write(bank.state(identity.wholeIndex()).moveWord(X, 0x7777));
        assertThrows(IllegalStateException.class, () -> f.body.run(12, normal(0x10)));
        assertEquals(0x7777, f.bank.state(12).word(X)); assertEquals(8, f.bank.state(12).unsignedByte(MAP_FRAME));
    }

    @Test void reentryIsRejectedButAnOrdinaryLaterInvocationStillWorks() {
        var f = new Fixture();
        f.draw = (bank, identity) -> assertThrows(IllegalStateException.class, () -> f.body.run(12, normal(0x10)));
        assertDoesNotThrow(() -> f.body.run(12, normal(0x10)));
        f.draw = (bank, identity) -> { };
        assertDoesNotThrow(() -> f.body.run(12, normal(0x10)));
    }

    @Test void frozenResultEffectsCannotBeCommandsOrMutatedByCaller() {
        var f = new Fixture(); var result = f.body.run(12, normal(0x10)); var cut = f.bank.capture();
        assertThrows(UnsupportedOperationException.class, () -> result.effects().clear());
        assertEquals(cut.slots(), f.bank.capture().slots());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 11, 16, 31, 32, 127, -1})
    void admissionNeverBorrowsPrimaryDynamicOrAnotherReservedSlot(int whole) {
        var f = new Fixture(); var cut = f.bank.capture();
        assertThrows(IllegalArgumentException.class, () -> f.body.run(whole, normal(0x10)));
        assertEquals(cut.slots(), f.bank.capture().slots()); assertTrue(f.calls.isEmpty());
    }

    @Test void nullAndWidthContractsDoNotCreateDefaults() {
        var f = new Fixture();
        assertThrows(NullPointerException.class, () -> new CdTimeStars(null, assets(defaultTable()), f.port, f.drawer));
        assertThrows(NullPointerException.class, () -> new CdTimeStars.Assets(null, MAP));
        assertThrows(IllegalArgumentException.class, () -> new CdTimeStars.Assets(new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(new byte[2]), 0, 1), MAP));
        assertThrows(IllegalArgumentException.class, () -> globals(256, 0, 0, history(256), 0));
        assertThrows(IllegalArgumentException.class, () -> globals(0, -1, 0, history(256), 0));
        assertThrows(IllegalArgumentException.class, () -> globals(0, 0, 256, history(256), 0));
        assertThrows(IllegalArgumentException.class, () -> globals(0, 0, 0, history(256), 65536));
        assertThrows(NullPointerException.class, () -> globals(0, 0, 0, null, 0));
        assertThrows(NullPointerException.class, () -> f.body.run(12, null));
        assertDoesNotThrow(() -> f.body.run(12, normal(0x10)));
    }

    private static CdTimeStars.Assets assets(CdObjectAnimation.AnimationTable table) { return new CdTimeStars.Assets(table, MAP); }
    private static CdTimeStars.Globals normal(int index) { return globals(0, 1, 0xA5, history(256), index); }
    private static CdTimeStars.Globals globals(int zone, int warp, int flags, CdAssetBytes history, int index) {
        return new CdTimeStars.Globals(zone, warp, flags, history, index);
    }
    private static CdAssetBytes history(int length) {
        byte[] bytes = new byte[length];
        for (int at = 0; at + 3 < length; at += 4) {
            int x = 0x1000 + at, y = 0x2000 + at;
            bytes[at] = (byte) (x >>> 8); bytes[at + 1] = (byte) x;
            bytes[at + 2] = (byte) (y >>> 8); bytes[at + 3] = (byte) y;
        }
        return CdAssetBytes.copyOf(bytes);
    }
    private static CdObjectAnimation.AnimationTable defaultTable() {
        return table(30, 18, new byte[]{0, 8, 9, 10, 11, 12, 11, 10, 9, 8, 0, (byte) 0xFF});
    }
    private static CdObjectAnimation.AnimationTable table(int length, int pointer, byte[] script) {
        byte[] bytes = new byte[length];
        for (int at = 0; at < 18; at += 2) { bytes[at] = (byte) (pointer >>> 8); bytes[at + 1] = (byte) pointer; }
        if (pointer >= 18 && pointer <= length) System.arraycopy(script, 0, bytes, pointer, Math.min(script.length, length - pointer));
        return new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes), 0, 9);
    }
    private static void assertOthers(CdReservedObjectBank.Snapshot before, CdReservedObjectBank bank, int selected) {
        for (int whole = 1; whole < 32; whole++) if (whole != selected)
            assertEquals(before.slots().get(whole - 1), bank.state(whole), "unwritten whole " + whole);
    }
    private static final class Fixture {
        final CdReservedObjectBank bank = CdReservedObjectBank.clearedAfterLevelStart();
        final List<String> calls = new ArrayList<>();
        int powerup = 6, attackSlot;
        boolean ignoreWrites;
        String attack;
        AssertionError callbackFailure;
        CdReservedObjectBank.Image drawSeen;
        CdTimeStars.DrawReserved draw = (bank, identity) -> { };
        final CdTimeStars.PowerupPort port = new CdTimeStars.PowerupPort() {
            public int read() { calls.add("read"); invoke("read"); return powerup; }
            public void write(int value) { calls.add("write:" + value); if (!ignoreWrites) powerup = value; invoke("write"); }
        };
        final CdTimeStars.DrawReserved drawer = (bank, identity) -> {
            calls.add("draw"); drawSeen = bank.state(identity.wholeIndex()); invoke("draw"); draw.drawObject(bank, identity);
        };
        final CdTimeStars body;
        Fixture() { this(defaultTable()); }
        Fixture(CdObjectAnimation.AnimationTable table) {
            for (int whole = 1; whole < 32; whole++) {
                byte[] bytes = new byte[64];
                for (int at = 0; at < 64; at++) bytes[at] = (byte) (whole * 3 + at * 7);
                bank.write(bank.state(whole).withBytes(bytes));
            }
            bank.write(bank.state(12).moveByte(ID, 3).moveByte(ROUTINE, 6).moveByte(ANIMATION, 5)
                    .moveByte(PREVIOUS_ANIMATION, 5).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0)
                    .moveByte(MAP_FRAME, 0).moveByte(SPRITE_FLAGS, 0x84).moveByteAt(0x30, 0));
            body = new CdTimeStars(bank, assets(table), port, drawer);
        }
        void invoke(String site) {
            if (!site.equals(attack)) return;
            bank.write(bank.state(attackSlot).moveByte(ID, 77));
            if (callbackFailure != null) throw callbackFailure;
        }
    }
}
