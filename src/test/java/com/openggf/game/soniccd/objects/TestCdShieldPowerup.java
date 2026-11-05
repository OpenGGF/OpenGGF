package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.level.CdR12aTerrainAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdShieldPowerup.ReadField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ALL JAVA UNRUN. Constructed full31/raw-bank and scalar external values are
 * labelled contract vectors, never media/native execution or route evidence.
 * Three actual-source tests require soniccd.media.root and load existing original
 * USA assets; no ASM bytes are loaded/generated as a runtime fallback.
 */
class TestCdShieldPowerup {
    private static final int MAP = 0x21D528;
    private static final List<CdShieldPowerup.ReadField> NORMAL_READS =
            List.of(SHIELD, TIME_WARP, INVINCIBLE, PLAYER_X_WORD, PLAYER_Y_WORD, PLAYER_FLAGS, ZONE);

    @Test void cleanInitWritesOnlyNativeFieldsAndReturnsWithoutShieldOrDrawFallthrough() {
        var f = new Fixture();
        f.bank.write(f.bank.state(6).withBytes(new byte[64]).moveByte(ID, 3));
        var source = f.bank.state(6); var cut = f.bank.capture();
        var result = f.body.run(6);
        assertEquals(initialized(source, 0), result.state());
        assertEquals(List.of(new CdShieldPowerup.RoutineEntered(CdShieldPowerup.NativeRoutine.INIT)), result.effects());
        assertTrue(f.readsSeen.isEmpty()); assertTrue(f.io.isEmpty()); assertOthers(cut, f.bank);
        assertEquals(0, result.state().unsignedByte(PREVIOUS_ANIMATION));
    }

    @ParameterizedTest @CsvSource({"0,2", "1,4", "4,4", "5,6", "8,6", "255,6"})
    void dirtyInitPreservesEveryOtherByteAndRawAnimationDispatch(int animation, int routine) {
        var f = new Fixture(); f.setInit(animation); var source = f.bank.state(6); var cut = f.bank.capture();
        var result = f.body.run(6);
        assertEquals(initialized(source, animation), result.state());
        assertEquals(routine, result.state().unsignedByte(ROUTINE));
        assertEquals(source.unsignedByte(PREVIOUS_ANIMATION), result.state().unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(source.unsignedByte(ANIMATION_TIME), result.state().unsignedByte(ANIMATION_TIME));
        assertTrue(f.readsSeen.isEmpty()); assertTrue(f.io.isEmpty()); assertOthers(cut, f.bank);
    }

    @ParameterizedTest @ValueSource(ints = {1,2,3,4,5,6,7,8})
    void initPublicationFaultRetainsExactEarlierNativeStores(int failedOrdinal) {
        var f = new Fixture(); f.setInit(5); var source = f.bank.state(6); var cut = f.bank.capture();
        var expected = initPrefixes(source, 5);
        var original = new AssertionError("constructed Init publication fault");
        var probe = f.bind();
        probe.before = call -> { if (f.body.currentOrdinal(call) == failedOrdinal) throw original; };
        assertSame(original, assertThrows(AssertionError.class, () -> f.body.run(6)));
        assertEquals(failedOrdinal == 1 ? source : expected.get(failedOrdinal - 2), f.bank.state(6));
        assertTrue(f.readsSeen.isEmpty()); assertTrue(f.io.isEmpty()); assertOthers(cut, f.bank);
        assertFalse(f.body.isCurrentCall(probe.last));
    }

    @ParameterizedTest @ValueSource(ints = {1,3,4,5,6,7,8,254,255})
    void unownedRoutineStopsAtDispatchWithTypedBoundaryBeforeAnyExternalRead(int routine) {
        var f = new Fixture(); f.bank.write(f.bank.state(6).moveByte(ROUTINE, routine)); var cut = f.bank.capture();
        var failure = assertThrows(CdShieldPowerup.UnsupportedShieldPowerup.class, () -> f.body.run(6));
        assertEquals(routine == 4 ? CdShieldPowerup.UnsupportedBoundary.INVINCIBILITY_STARS_ROUTINE
                : routine == 6 ? CdShieldPowerup.UnsupportedBoundary.TIME_STARS_ROUTINE
                : CdShieldPowerup.UnsupportedBoundary.ROUTINE_DISPATCH, failure.boundary());
        assertEquals(6, failure.wholeIndex()); assertEquals(routine, failure.value());
        assertEquals(cut.slots(), f.bank.capture().slots()); assertTrue(f.readsSeen.isEmpty()); assertTrue(f.io.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {-1,0,1,5,7,12,31,32,127})
    void admissionDoesNotBorrowPlayerDynamicOrOtherReservedSlots(int whole) {
        var f = new Fixture(); var cut = f.bank.capture();
        assertThrows(IllegalArgumentException.class, () -> f.body.run(whole));
        assertEquals(cut.slots(), f.bank.capture().slots()); assertTrue(f.readsSeen.isEmpty());
    }

    @Test void actualIdIsRequiredWithoutImportOrInitialization() {
        var f = new Fixture(); f.bank.write(f.bank.state(6).moveByte(ID, 0)); var cut = f.bank.capture();
        assertThrows(IllegalArgumentException.class, () -> f.body.run(6));
        assertEquals(cut.slots(), f.bank.capture().slots()); assertTrue(f.readsSeen.isEmpty());
    }

    @Test void absentShieldDeletesDirtyWholeSlotAndDoesNotInvokeWrapperDraw() {
        var f = new Fixture(); f.values[SHIELD.ordinal()] = 0; var cut = f.bank.capture();
        var result = f.body.run(6);
        assertArrayEquals(new byte[64], result.state().copyBytes()); assertTrue(result.deleted());
        assertEquals(List.of(SHIELD), f.readsSeen); assertTrue(f.io.isEmpty()); assertOthers(cut, f.bank);
        assertEquals(List.of(new CdShieldPowerup.RoutineEntered(CdShieldPowerup.NativeRoutine.SHIELD),
                new CdShieldPowerup.ValueRead(SHIELD, 0), new CdShieldPowerup.Deleted()), result.effects());
    }

    @ParameterizedTest @CsvSource({"1,0,2", "255,255,2", "0,1,3", "0,255,3"})
    void timeWarpAndInvincibilityGatesAvoidUnreachedPlayerZoneAnimationAndPowerupReads(int warp, int invincible, int reads) {
        var f = new Fixture(); f.values[TIME_WARP.ordinal()] = warp; f.values[INVINCIBLE.ordinal()] = invincible;
        f.values[PLAYER_X_WORD.ordinal()] = -1; f.values[ZONE.ordinal()] = -1;
        f.bank.write(f.bank.state(6).moveByte(ANIMATION, 255).moveByte(ANIMATION_TIME, 0)); var cut = f.bank.capture();
        var result = f.body.run(6);
        assertEquals(cut.slots(), f.bank.capture().slots()); assertEquals(cut.slots().get(5), result.state());
        assertEquals(NORMAL_READS.subList(0, reads), f.readsSeen); assertTrue(f.io.isEmpty());
    }

    @ParameterizedTest @EnumSource(CdShieldPowerup.ReadField.class)
    void failedExternalReadKeepsOnlyItsReachedPlayerAndTilePrefix(CdShieldPowerup.ReadField failedField) {
        var f = new Fixture(); f.values[ZONE.ordinal()] = 6; f.readFailureField = failedField;
        var original = new AssertionError("constructed external read fault"); f.readFailure = original;
        var source = f.bank.state(6); var cut = f.bank.capture();
        assertSame(original, assertThrows(AssertionError.class, () -> f.body.run(6)));
        var expected = source;
        if (failedField.ordinal() > PLAYER_X_WORD.ordinal()) expected = expected.moveWord(X, 0x1234);
        if (failedField.ordinal() > PLAYER_Y_WORD.ordinal()) expected = expected.moveWord(Y, 0xFEDC);
        if (failedField.ordinal() > PLAYER_FLAGS.ordinal()) expected = expected.moveByte(FLAGS, 0xA5);
        if (failedField == CdShieldPowerup.ReadField.LAYER) expected = expected.moveByteAt(2, expected.byteAt(2) | 0x80);
        assertEquals(expected, f.bank.state(6)); assertOthers(cut, f.bank); assertTrue(f.io.isEmpty());
        assertEquals(Arrays.asList(CdShieldPowerup.ReadField.values()).subList(0, failedField.ordinal() + 1), f.readsSeen);
    }

    @ParameterizedTest @EnumSource(CdShieldPowerup.ReadField.class)
    void widthValidationOccursOnlyOnTheReachedRead(CdShieldPowerup.ReadField field) {
        var f = new Fixture(); f.values[ZONE.ordinal()] = 6;
        f.values[field.ordinal()] = field == PLAYER_X_WORD || field == PLAYER_Y_WORD ? 65536 : 256;
        assertThrows(IllegalArgumentException.class, () -> f.body.run(6));
        assertEquals(field, f.readsSeen.getLast()); assertTrue(f.io.isEmpty());
    }

    @ParameterizedTest @CsvSource({"0,255,4660,false", "5,255,4660,false", "6,0,37428,true", "6,255,4660,true"})
    void mmzLayerReadsOnlyAfterTileHighByteOrAndPreservesLowByte(int zone, int layer, int tile, boolean layerRead) {
        var f = new Fixture(); f.values[ZONE.ordinal()] = zone; f.values[CdShieldPowerup.ReadField.LAYER.ordinal()] = layer;
        f.onRead = field -> { if (field == CdShieldPowerup.ReadField.LAYER) assertEquals(0x9234, f.bank.state(6).word(TILE)); };
        var result = f.body.run(6);
        assertEquals(tile, result.state().word(TILE)); assertEquals(layerRead, f.readsSeen.contains(CdShieldPowerup.ReadField.LAYER));
        assertEquals(0x1234, result.state().word(X)); assertEquals(0xFEDC, result.state().word(Y));
        assertEquals(0xA5, result.state().unsignedByte(FLAGS));
    }

    @Test void nativeShieldDelaySequenceUsesOneSharedAnimatorOnTheOriginalImage() {
        var f = new Fixture(); var source = f.bank.state(6); var cut = f.bank.capture();
        int[] frames = {1,1,0,0,2,2,0,0,3,3,0,0,1,1};
        for (int i = 0; i < frames.length; i++) {
            var result = f.body.run(6);
            assertEquals(frames[i], result.state().unsignedByte(MAP_FRAME), "constructed call " + i);
            assertEquals((i & 1) == 0 ? 1 : 0, result.state().unsignedByte(ANIMATION_TIME));
            assertEquals(1, result.effects().stream().filter(CdShieldPowerup.AnimationReturned.class::isInstance).count());
            assertEquals(source.word(X_FRACTION), result.state().word(X_FRACTION));
            assertEquals(source.word(Y_FRACTION), result.state().word(Y_FRACTION));
        }
        assertOthers(cut, f.bank); assertEquals(NORMAL_READS, f.readsSeen.subList(0, 7));
    }

    @ParameterizedTest @CsvSource({"0,1,1", "1,0,0", "128,127,0", "129,1,1", "255,1,1"})
    void dirtyTimerUsesByteDecrementAndSignedBranch(int timer, int after, int mapFrame) {
        var f = new Fixture(); f.bank.write(f.bank.state(6).moveByte(ANIMATION_TIME, timer));
        var result = f.body.run(6);
        assertEquals(after, result.state().unsignedByte(ANIMATION_TIME)); assertEquals(mapFrame, result.state().unsignedByte(MAP_FRAME));
    }

    @Test void heldForeignAnimationNeverReadsItsUnknownPointerOrNormalizesRawBytes() {
        var f = new Fixture(table(18, 0x7FFF, new byte[0]));
        f.bank.write(f.bank.state(6).moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 255)
                .moveByte(ANIMATION_TIME, 1).moveLong(MAPPINGS, 0xDEADBEEF).moveByte(MAP_FRAME, 0xE7));
        var result = f.body.run(6);
        assertEquals(0, result.state().unsignedByte(ANIMATION_TIME)); assertEquals(0xE7, result.state().unsignedByte(MAP_FRAME));
        assertEquals(255, result.state().unsignedByte(ANIMATION)); assertEquals(0xDEADBEEF, result.state().longBits(MAPPINGS));
        assertEquals(List.of("read", "draw"), f.io);
    }

    @Test void reachedDirectoryFaultRetainsResetAndDecrementWithoutAnySelectorOrDraw() {
        var f = new Fixture(); f.bank.write(f.bank.state(6).moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 8)
                .moveByte(ANIMATION_FRAME, 77).moveByte(ANIMATION_TIME, 44));
        var failure = assertThrows(CdShieldPowerup.UnsupportedShieldPowerup.class, () -> f.body.run(6));
        assertEquals(CdShieldPowerup.UnsupportedBoundary.ANIMATION_DIRECTORY_ADDRESS, failure.boundary());
        assertEquals(510, failure.value()); var state = f.bank.state(6);
        assertEquals(255, state.unsignedByte(PREVIOUS_ANIMATION)); assertEquals(0, state.unsignedByte(ANIMATION_FRAME));
        assertEquals(255, state.unsignedByte(ANIMATION_TIME)); assertTrue(f.io.isEmpty());
        assertEquals(0xFEDC, state.word(Y)); assertEquals(0xA5, state.unsignedByte(FLAGS));
    }

    @ParameterizedTest @CsvSource({"18,40,,ANIMATION_SPEED_ADDRESS,40,255,0",
            "19,18,09,ANIMATION_FRAME_ADDRESS,19,9,0",
            "20,18,02fe,ANIMATION_OPERAND_ADDRESS,20,2,0",
            "20,18,02fd,ANIMATION_OPERAND_ADDRESS,20,2,0",
            "21,18,02fe02,ANIMATION_FRAME_ADDRESS,273,2,254",
            "18,-1,,ANIMATION_SPEED_ADDRESS,-1,255,0"})
    void animationFaultPrefixesAreReachedAndFinite(int length, int pointer, String hex,
            CdShieldPowerup.UnsupportedBoundary boundary, int address, int timer, int index) {
        var f = new Fixture(table(length, pointer, hex == null ? new byte[0] : HexFormat.of().parseHex(hex)));
        var failure = assertThrows(CdShieldPowerup.UnsupportedShieldPowerup.class, () -> f.body.run(6));
        assertEquals(boundary, failure.boundary()); assertEquals(address, failure.value());
        assertEquals(timer, f.bank.state(6).unsignedByte(ANIMATION_TIME));
        assertEquals(index, f.bank.state(6).unsignedByte(ANIMATION_FRAME)); assertTrue(f.io.isEmpty());
        assertEquals(NORMAL_READS, f.readsSeen);
    }

    @Test void ffRestartTreatsItsNegativeTargetAsFrameDataWithoutRecursiveCommandDispatch() {
        var f = new Fixture(table(20, 18, new byte[]{2, (byte) 0xFF}));
        var result = f.body.run(6);
        assertEquals(31, result.state().unsignedByte(MAP_FRAME)); assertEquals(1, result.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(0x86, result.state().unsignedByte(SPRITE_FLAGS));
    }

    @Test void feLoopbackUsesOriginalIndexAndFrameFlipBitsExactlyOnce() {
        var f = new Fixture(table(22, 18, new byte[]{2,0x61,(byte)0xFE,1}));
        f.bank.write(f.bank.state(6).moveByte(ANIMATION_FRAME,1));
        var result = f.body.run(6);
        assertEquals(1, result.state().unsignedByte(ANIMATION_FRAME)); assertEquals(2, result.state().unsignedByte(ANIMATION_TIME));
        assertEquals(1, result.state().unsignedByte(MAP_FRAME)); assertEquals(0x86, result.state().unsignedByte(SPRITE_FLAGS));
        assertEquals(1, result.effects().stream().filter(CdShieldPowerup.AnimationReturned.class::isInstance).count());
    }

    @Test void fdPublishesNewRawAnimationWithoutEagerlyReadingTheNewDirectory() {
        var f = new Fixture(table(21, 18, new byte[]{0,(byte)0xFD,(byte)255}));
        var result = f.body.run(6);
        assertEquals(255, result.state().unsignedByte(ANIMATION)); assertEquals(0, result.state().unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(0, result.state().unsignedByte(MAP_FRAME)); assertEquals(List.of("read", "draw"), f.io);
        assertEquals(CdShieldPowerup.UnsupportedBoundary.ANIMATION_DIRECTORY_ADDRESS,
                assertThrows(CdShieldPowerup.UnsupportedShieldPowerup.class, () -> f.body.run(6)).boundary());
    }

    @Test void fcUsesChangedRoutineAtChkSaveRoutThenStopsAtUnownedRoutineOnNextVisit() {
        var f = new Fixture(table(20, 18, new byte[]{0, (byte) 0xFC})); f.powerup = 0;
        var result = f.body.run(6);
        assertEquals(4, result.state().unsignedByte(ROUTINE)); assertEquals(132, f.powerup);
        assertEquals(List.of("read", "write:4", "read", "write:132", "draw"), f.io);
        assertEquals(CdShieldPowerup.UnsupportedBoundary.INVINCIBILITY_STARS_ROUTINE,
                assertThrows(CdShieldPowerup.UnsupportedShieldPowerup.class, () -> f.body.run(6)).boundary());
    }

    @ParameterizedTest @CsvSource({"0,130,true", "1,130,true", "2,2,true", "18,18,true", "128,130,true",
            "130,130,true", "8,8,false", "136,136,false", "255,255,false"})
    void selectorLowNibbleAndSeparateMoveBsetAndDrawCutsRetainRawGlobal(int before, int after, boolean drawn) {
        var f = new Fixture(); f.powerup = before; var result = f.body.run(6);
        assertEquals(after, f.powerup);
        var expected = !drawn ? List.of("read") : (before & 15) == 2 ? List.of("read", "draw")
                : List.of("read", "write:2", "read", "write:130", "draw");
        assertEquals(expected, f.io);
        assertEquals(drawn, result.effects().stream().anyMatch(CdShieldPowerup.DrawReturned.class::isInstance));
        if (drawn && (before & 15) != 2) assertEquals(List.of(new CdShieldPowerup.PowerupWritten(before, 2),
                new CdShieldPowerup.PowerupWritten(2, 130)), result.effects().stream().filter(CdShieldPowerup.PowerupWritten.class::isInstance).toList());
    }

    @Test void drawReloadAllowsOnlyItsOwnNativeBitSevenAndRetainsImmutableOriginal() {
        var f = new Fixture(); var source = f.bank.state(6); var cut = f.bank.capture();
        f.draw = (bank, identity) -> {
            assertEquals(6, identity.wholeIndex()); assertEquals(0xFFD180, identity.nativeAddress24());
            bank.write(bank.state(6).moveByte(SPRITE_FLAGS, bank.state(6).unsignedByte(SPRITE_FLAGS) & 0x7F));
        };
        var result = f.body.run(6);
        assertEquals(5, result.state().unsignedByte(SPRITE_FLAGS)); assertEquals(f.bank.state(6), result.state());
        assertEquals(0x84, source.unsignedByte(SPRITE_FLAGS)); assertOthers(cut, f.bank);
        var copy = result.state().copyBytes(); copy[0] = 99; assertEquals(3, result.state().unsignedByte(ID));
        assertThrows(UnsupportedOperationException.class, () -> result.effects().clear());
    }

    @ParameterizedTest @ValueSource(strings = {"external", "read", "write", "draw"})
    void callbackMutationOutsideOwnDrawBitIsRejectedAndRetained(String site) {
        var f = new Fixture(); f.powerup = 0; f.attack = site; f.attackSlot = 20;
        assertThrows(IllegalStateException.class, () -> f.body.run(6)); assertEquals(77, f.bank.state(20).unsignedByte(ID));
    }

    @ParameterizedTest @ValueSource(strings = {"external", "read", "write", "draw"})
    void originalCallbackFailureSurvivesWithSuppressedFullBankViolation(String site) {
        var f = new Fixture(); f.powerup = 0; f.attack = site; f.attackSlot = 20;
        var original = new AssertionError("constructed original callback"); f.callbackFailure = original;
        assertSame(original, assertThrows(AssertionError.class, () -> f.body.run(6)));
        assertTrue(Arrays.stream(original.getSuppressed()).anyMatch(IllegalStateException.class::isInstance));
        assertEquals(77, f.bank.state(20).unsignedByte(ID));
    }

    @Test void ownNonDrawMutationIsRejectedAndOwnNonBitSevenDrawMutationIsRejected() {
        var f = new Fixture(); f.onRead = field -> f.bank.write(f.bank.state(6).moveWord(X, 7));
        assertThrows(IllegalStateException.class, () -> f.body.run(6));
        var g = new Fixture(); g.draw = (bank, identity) -> bank.write(bank.state(6).moveWord(X, 7));
        assertThrows(IllegalStateException.class, () -> g.body.run(6)); assertEquals(7, g.bank.state(6).word(X));
        assertEquals(1, g.bank.state(6).unsignedByte(MAP_FRAME));
    }

    @ParameterizedTest @ValueSource(strings = {"external", "read", "write", "draw"})
    void callbackReentryIsRejectedAndLaterInvocationStillWorks(String site) {
        var f = new Fixture(); f.powerup = 0;
        f.onSite = at -> { if (site.equals(at)) assertThrows(IllegalStateException.class, () -> f.body.run(6)); };
        assertDoesNotThrow(() -> f.body.run(6)); f.onSite = at -> { };
        assertDoesNotThrow(() -> f.body.run(6));
    }

    @Test void unadoptedMoveStopsAtNativeBsetReadButNoExtraReadInventsBsetAuthority() {
        var f = new Fixture(); f.powerup = 0; f.ignoreWriteNumber = 1;
        assertThrows(IllegalStateException.class, () -> f.body.run(6)); assertEquals(List.of("read", "write:2", "read"), f.io);
        var g = new Fixture(); g.powerup = 0; g.ignoreWriteNumber = 2;
        var result = g.body.run(6); // Value-only legacy callback cannot certify the second store/no-op.
        assertEquals(2, g.powerup); assertEquals(List.of("read", "write:2", "read", "write:130", "draw"), g.io);
        assertTrue(result.effects().contains(new CdShieldPowerup.PowerupWritten(2, 130)));
    }

    @Test void malformedPowerupIsValidatedAtReachedReadAfterAnimation() {
        var f = new Fixture(); f.powerup = 256;
        assertThrows(IllegalArgumentException.class, () -> f.body.run(6));
        assertEquals(1, f.bank.state(6).unsignedByte(MAP_FRAME)); assertEquals(List.of("read"), f.io);
    }

    @Test void originalDrawFailureRetainsBitSevenAndNoLaterOperationRuns() {
        var f = new Fixture(); var original = new AssertionError("constructed Draw failure");
        var probe = f.bind();
        f.draw = (bank, identity) -> { bank.write(bank.state(6).moveByte(SPRITE_FLAGS, 5)); throw original; };
        assertSame(original, assertThrows(AssertionError.class, () -> f.body.run(6)));
        assertEquals(5, f.bank.state(6).unsignedByte(SPRITE_FLAGS)); assertEquals(List.of("read", "draw"), f.io);
        assertEquals(1, probe.failures.size()); assertSame(original, probe.failures.getFirst());
        assertFalse(f.body.isCurrentCall(probe.last));
    }

    @Test void rawBackingReadCannotReenterEvenBeforeInitialIdAdmission() {
        var f = new Fixture();
        f.raw.inRead = () -> assertThrows(IllegalStateException.class, () -> f.body.run(6));
        assertDoesNotThrow(() -> f.body.run(6));
        f.raw.inRead = () -> { }; assertDoesNotThrow(() -> f.body.run(6));
    }

    @Test void originalPowerupStoreFailureKeepsItsActualStorePrefixAndStopsBeforeBset() {
        var f = new Fixture(); f.powerup = 0; var original = new AssertionError("constructed store prefix failure");
        var probe = f.bind();
        f.onSite = site -> { if (site.equals("write")) throw original; };
        assertSame(original, assertThrows(AssertionError.class, () -> f.body.run(6)));
        assertEquals(2, f.powerup); assertEquals(List.of("read", "write:2"), f.io);
        assertEquals(1, f.bank.state(6).unsignedByte(MAP_FRAME)); assertSame(original, probe.failures.getFirst());
        assertFalse(f.body.isCurrentCall(probe.last));
    }

    @Test void privateGuardPreparationFailurePerformsNoPrimitiveAndKeepsOriginalFailure() {
        var f = new Fixture(); var original = new AssertionError("constructed missing private pending receipt");
        var probe = new Probe(f) {
            @Override public void prepared(CdShieldPowerup.Call call) { throw original; }
        };
        f.body = CdShieldPowerup.withWorldOwner(f.bank, f.body.assets(), f.reader, f.port, f.drawer, probe, probe);
        var cut = f.bank.capture();
        assertSame(original, assertThrows(AssertionError.class, () -> f.body.run(6)));
        assertTrue(f.readsSeen.isEmpty()); assertTrue(f.io.isEmpty());
        assertEquals(cut.slots(), f.bank.capture().slots()); assertSame(original, probe.failures.getFirst());
    }

    @Test void rawOwnerRejectedPublicationIsNotRetriedRolledBackOrReplaced() {
        var f = new Fixture(); f.setInit(0); var source = f.bank.state(6);
        f.raw.rejectCas = f.raw.casCalls + 3; var original = f.raw.casCalls;
        assertThrows(IllegalStateException.class, () -> f.body.run(6));
        assertEquals(original + 3, f.raw.casCalls);
        assertEquals(source.moveByte(ROUTINE, 2).moveLong(MAPPINGS, MAP), f.bank.state(6));
    }

    @Test void worldReceiptsExposeOnlyOriginalPendingPrimitiveAndCompletionWithMonotoneOrdinal() {
        var f = new Fixture(); f.powerup = 0; var probe = f.bind();
        f.raw.inCas = () -> { assertTrue(f.body.isCurrentPrimitive(probe.last)); probe.primitives++; };
        f.onRead = field -> assertTrue(f.body.isCurrentPrimitive(probe.last));
        f.onSite = site -> assertTrue(f.body.isCurrentPrimitive(probe.last));
        var result = f.body.run(6);
        assertTrue(probe.primitives > 0); assertEquals(probe.prepared, probe.completed);
        assertEquals(probe.prepared.size(), probe.lastOrdinal);
        assertTrue(result.effects().contains(new CdShieldPowerup.PowerupWritten(2, 130)));
        var stale = probe.last; assertFalse(f.body.isCurrentCall(stale)); assertFalse(f.body.isCurrentPrimitive(stale));
        assertThrows(IllegalStateException.class, () -> f.body.currentResult(stale));
        assertThrows(IllegalStateException.class, () -> f.body.currentOrdinal(stale));
        probe.lastOrdinal = 0; f.body.run(6); assertFalse(f.body.isCurrentCall(stale));
    }

    @Test void clonedPublicCallAndEqualImagesNeverBecomePendingAuthority() {
        var f = new Fixture(); var probe = f.bind();
        probe.before = call -> {
            var clone = call instanceof CdShieldPowerup.ReadCall r
                    ? new CdShieldPowerup.ReadCall(r.cut(), r.source().withBytes(r.source().copyBytes()), r.field())
                    : new CdShieldPowerup.DrawCall(call.cut(), call.source());
            assertFalse(f.body.isCurrentCall(clone)); assertFalse(f.body.isCurrentPrimitive(clone));
            assertThrows(IllegalStateException.class, () -> f.body.currentResult(clone));
            assertThrows(IllegalStateException.class, () -> f.body.currentOrdinal(clone));
            assertThrows(IllegalStateException.class, () -> f.body.run(6));
        };
        assertDoesNotThrow(() -> f.body.run(6));
    }

    @ParameterizedTest @ValueSource(strings = {"before", "after"})
    void observerMutationIsRejectedWithoutRollbackOrImplicitReceiptAdoption(String stage) {
        var f = new Fixture(); var probe = f.bind();
        Consumer<CdShieldPowerup.Call> attack = call -> f.bank.write(f.bank.state(20).moveByte(ID, 77));
        if (stage.equals("before")) probe.before = attack; else probe.after = attack;
        assertThrows(IllegalStateException.class, () -> f.body.run(6));
        assertEquals(77, f.bank.state(20).unsignedByte(ID)); assertTrue(f.io.isEmpty());
    }

    @Test void privateGuardCanRejectNoOpCompletionBeforeFurtherSelectorOrDraw() {
        var f = new Fixture(); f.powerup = 0; f.ignoreWriteNumber = 2; var probe = f.bind();
        var original = new AssertionError("private owner rejected missing actual BSET primitive receipt");
        probe.onCompleted = call -> { if (call instanceof CdShieldPowerup.PowerupWriteCall w && w.value() == 130) throw original; };
        assertSame(original, assertThrows(AssertionError.class, () -> f.body.run(6)));
        assertEquals(List.of("read", "write:2", "read", "write:130"), f.io); assertEquals(2, f.powerup);
    }

    @Test void valueOnlyAbaAndSameValueRawCasAreObservationalLimitsNotProducerProof() {
        var f = new Fixture(); var cut = f.bank.capture();
        f.onRead = field -> { var before = f.bank.state(20); f.bank.write(before.moveByte(ID, 77)); f.bank.write(before); };
        assertDoesNotThrow(() -> f.body.run(6)); assertOthers(cut, f.bank);
        var image = f.bank.state(6); int before = f.raw.casCalls;
        f.bank.write(image.withBytes(image.copyBytes())); assertEquals(before + 1, f.raw.casCalls);
        assertEquals(image, f.bank.state(6)); // Equal values establish no native/external lifetime receipt.
    }

    @Test void nullsAreRejectedWithoutReadsDefaultsOrBankInitialization() {
        var f = new Fixture(); var assets = f.body.assets(); var cut = f.bank.capture();
        assertThrows(NullPointerException.class, () -> new CdShieldPowerup(null, assets, f.reader, f.port, f.drawer));
        assertThrows(NullPointerException.class, () -> new CdShieldPowerup(f.bank, null, f.reader, f.port, f.drawer));
        assertThrows(NullPointerException.class, () -> new CdShieldPowerup(f.bank, assets, null, f.port, f.drawer));
        assertThrows(NullPointerException.class, () -> new CdShieldPowerup(f.bank, assets, f.reader, null, f.drawer));
        assertThrows(NullPointerException.class, () -> new CdShieldPowerup(f.bank, assets, f.reader, f.port, null));
        assertThrows(NullPointerException.class, () -> CdShieldPowerup.withWorldOwner(f.bank, assets, f.reader, f.port, f.drawer, null, null));
        assertEquals(cut.slots(), f.bank.capture().slots()); assertTrue(f.readsSeen.isEmpty()); assertTrue(f.io.isEmpty());
    }

    @Test void actualOriginalAssetsReuseNineScriptAndThirteenRawRootViewsWithoutAnotherDecoder() throws Exception {
        var objects = originalAssets(); var borrowed = CdTimeStars.Assets.fromAct2Assets(objects);
        assertSame(objects.view(CdR12aProfile.Asset.ANI_POWERUP), borrowed.animations().bytes());
        assertEquals(MAP, borrowed.mappingAddressBits()); assertEquals(9, borrowed.animations().scriptCount());
        assertEquals(296, borrowed.animations().bytes().length());
        assertEquals("0012001a0020003a0054006e007a00b400ee", HexFormat.of().formatHex(borrowed.animations().bytes().slice(0,18).copy()));
        var mappings = objects.view(CdR12aProfile.Asset.MAP_SPR_POWERUP);
        int[] roots = {37,26,47,68,89,110,131,152,174,196,218,240,262};
        for (int i = 0; i < roots.length; i++) assertEquals(roots[i], (short) mappings.unsignedWord(2*i));
        assertEquals(0, mappings.unsignedByte(37)); assertEquals(4, mappings.unsignedByte(26));
        var f = new Fixture(borrowed); assertSame(borrowed, f.body.assets());
        f.setInit(0); var cut = f.bank.capture(); f.body.run(6); assertOthers(cut, f.bank);
    }

    @Test void actualSourceShieldAnimationDelayFramesAndLoopRemainNativeWithConstructedPlayer() throws Exception {
        var assets = CdTimeStars.Assets.fromAct2Assets(originalAssets());
        assertEquals("01010002000300ff", HexFormat.of().formatHex(assets.animations().bytes().slice(18,8).copy()));
        var f = new Fixture(assets); f.bank.write(f.bank.state(6).moveByte(ROUTINE, 0));
        f.body.run(6); assertTrue(f.readsSeen.isEmpty()); assertTrue(f.io.isEmpty());
        // Init retained old timer/index; deliberately construct a reset edge for this script test.
        f.bank.write(f.bank.state(6).moveByte(PREVIOUS_ANIMATION, 255));
        int[] expected = {1,1,0,0,2,2,0,0,3,3,0,0,1};
        for (int frame : expected) assertEquals(frame, f.body.run(6).state().unsignedByte(MAP_FRAME));
        assertEquals(2, f.powerup); // No source-only art upload or synthetic selector service.
    }

    @Test void actualRetainedSourceBytesMatchOriginalAndCopiesCannotModifyBorrowedViews() throws Exception {
        var objects = originalAssets();
        String root = System.getProperty("soniccd.media.root");
        try (var source = CdProgramAssets.open(usaCue(root), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            for (var key : List.of(CdR12aProfile.Asset.ANI_POWERUP, CdR12aProfile.Asset.MAP_SPR_POWERUP)) {
                var bytes = objects.view(key); var before = bytes.copy();
                assertArrayEquals(source.mmd(CdNativeFile.R12A).readFile(key.fileOffset(), key.length()), before);
                var copy = bytes.copy(); copy[0] ^= 1; assertArrayEquals(before, bytes.copy());
                assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(bytes.length()));
            }
        }
    }

    private static CdR12aObjectAssets originalAssets() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Configure actual original CD media root for the three source-backed tests"); assertFalse(root.isBlank());
        try (var source = CdProgramAssets.open(usaCue(root), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return CdR12aTerrainAssets.fromPrograms(source).objects();
        }
    }
    private static Path usaCue(String root) throws Exception {
        try (var entries = Files.list(Path.of(root).resolve("USA"))) {
            var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); return cues.getFirst();
        }
    }
    private static CdReservedObjectBank.Image initialized(CdReservedObjectBank.Image source, int animation) {
        var prefixes = initPrefixes(source, animation); return prefixes.getLast();
    }
    private static List<CdReservedObjectBank.Image> initPrefixes(CdReservedObjectBank.Image source, int animation) {
        var list = new ArrayList<CdReservedObjectBank.Image>(); var state = source.moveByte(ROUTINE, 2); list.add(state);
        state = state.moveLong(MAPPINGS, MAP); list.add(state);
        state = state.moveByte(SPRITE_FLAGS, 4); list.add(state);
        state = state.moveByte(PRIORITY, 1); list.add(state);
        state = state.moveByte(WIDTH, 16); list.add(state);
        state = state.moveWord(TILE, 0x544); list.add(state);
        if (animation != 0) { state = state.moveByte(ROUTINE, 4); list.add(state);
            if (animation >= 5) { state = state.moveByte(ROUTINE, 6); list.add(state); } }
        return list;
    }
    private static CdObjectAnimation.AnimationTable table(int length, int pointer, byte[] script) {
        byte[] bytes = new byte[length];
        for (int at = 0; at < 18; at += 2) { bytes[at] = (byte) (pointer >>> 8); bytes[at+1] = (byte) pointer; }
        if (pointer >= 18 && pointer <= length) System.arraycopy(script, 0, bytes, pointer, Math.min(script.length, length-pointer));
        return new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes), 0, 9);
    }
    private static CdTimeStars.Assets constructedAssets() {
        // Explicitly constructed contract data, coinciding with native Shield script0;
        // production only borrows actual CdTimeStars.Assets.fromAct2Assets views.
        return new CdTimeStars.Assets(table(26, 18, new byte[]{1,1,0,2,0,3,0,(byte)0xFF}), MAP);
    }
    private static void assertOthers(CdReservedObjectBank.Snapshot cut, CdReservedObjectBank bank) {
        for (int whole = 1; whole < 32; whole++) if (whole != 6)
            assertEquals(cut.slots().get(whole-1), bank.state(whole), "unwritten whole " + whole);
    }

    /** Constructed actual raw owner for this test, not a second live bank or captured partition. */
    private static final class RawOwner implements CdReservedObjectBank.RawBacking {
        CdAssetBytes bytes;
        int casCalls, rejectCas;
        Runnable inCas = () -> { };
        RawOwner() {
            byte[] raw = new byte[31*64];
            for (int whole = 1; whole < 32; whole++) for (int at = 0; at < 64; at++)
                raw[(whole-1)*64+at] = (byte)(whole*3+at*7);
            bytes = CdAssetBytes.copyOf(raw);
        }
        Runnable inRead = () -> { };
        public CdAssetBytes read() { inRead.run(); return bytes; }
        public boolean compareAndSet(CdAssetBytes expected, CdAssetBytes next) {
            casCalls++; inCas.run();
            if (casCalls == rejectCas || !Arrays.equals(expected.copy(), bytes.copy())) return false;
            bytes = next; return true;
        }
    }
    private static final class Fixture {
        final RawOwner raw = new RawOwner();
        final CdReservedObjectBank bank = CdReservedObjectBank.backedBy(raw);
        final List<CdShieldPowerup.ReadField> readsSeen = new ArrayList<>();
        final List<String> io = new ArrayList<>();
        final int[] values = {1,0,0,0x1234,0xFEDC,0xA5,0,0};
        int powerup = 2, writes, ignoreWriteNumber, attackSlot;
        String attack;
        AssertionError callbackFailure, readFailure;
        CdShieldPowerup.ReadField readFailureField;
        Consumer<CdShieldPowerup.ReadField> onRead = field -> { };
        Consumer<String> onSite = site -> { };
        CdShieldPowerup.DrawReserved draw = (bank, identity) -> { };
        CdShieldPowerup body;
        final CdShieldPowerup.ReadPort reader = field -> {
            readsSeen.add(field); onRead.accept(field); invoke("external");
            if (field == readFailureField) throw readFailure;
            return values[field.ordinal()];
        };
        final CdShieldPowerup.PowerupPort port = new CdShieldPowerup.PowerupPort() {
            public int read() { io.add("read"); invoke("read"); return powerup; }
            public void write(int value) { io.add("write:"+value); if (++writes != ignoreWriteNumber) powerup = value; invoke("write"); }
        };
        final CdShieldPowerup.DrawReserved drawer = (bank, identity) -> { io.add("draw"); invoke("draw"); draw.drawObject(bank, identity); };
        Fixture() { this(constructedAssets()); }
        Fixture(CdObjectAnimation.AnimationTable table) { this(new CdTimeStars.Assets(table, MAP)); }
        Fixture(CdTimeStars.Assets assets) {
            bank.write(bank.state(6).moveByte(ID,3).moveByte(ROUTINE,2).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0)
                    .moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,0).moveByte(SPRITE_FLAGS,0x84)
                    .moveWord(TILE,0x1234).moveLong(MAPPINGS,MAP));
            body = new CdShieldPowerup(bank, assets, reader, port, drawer);
        }
        void setInit(int animation) {
            bank.write(bank.state(6).moveByte(ROUTINE,0).moveByte(ANIMATION,animation)
                    .moveByte(PREVIOUS_ANIMATION,0xA3).moveByte(ANIMATION_FRAME,0xB2).moveByte(ANIMATION_TIME,0xC1));
        }
        void invoke(String site) {
            onSite.accept(site);
            if (!site.equals(attack)) return;
            bank.write(bank.state(attackSlot).moveByte(ID,77));
            if (callbackFailure != null) throw callbackFailure;
        }
        Probe bind() {
            var probe = new Probe(this);
            body = CdShieldPowerup.withWorldOwner(bank, body.assets(), reader, port, drawer, probe, probe);
            return probe;
        }
    }
    private static class Probe implements CdShieldPowerup.DispatchObserver, CdShieldPowerup.ReceiptGuard {
        final Fixture f;
        final List<CdShieldPowerup.Call> prepared = new ArrayList<>(), completed = new ArrayList<>();
        final List<Throwable> failures = new ArrayList<>();
        CdShieldPowerup.Call last;
        long lastOrdinal;
        int primitives;
        Consumer<CdShieldPowerup.Call> before = call -> { }, after = call -> { }, onCompleted = call -> { };
        Probe(Fixture f) { this.f = f; }
        public void beforeOperation(CdShieldPowerup.Call call) {
            last = call; assertTrue(f.body.isCurrentCall(call)); assertFalse(f.body.isCurrentPrimitive(call));
            assertEquals(++lastOrdinal, f.body.currentOrdinal(call));
            assertThrows(IllegalStateException.class, () -> f.body.currentResult(call)); before.accept(call);
        }
        public void prepared(CdShieldPowerup.Call call) { assertSame(last, call); assertTrue(f.body.isCurrentCall(call)); prepared.add(call); }
        public void afterOperation(CdShieldPowerup.Call call) {
            assertSame(last, call); assertFalse(f.body.isCurrentPrimitive(call));
            var result = f.body.currentResult(call);
            if (call instanceof CdShieldPowerup.ReadCall r) assertEquals(f.values[r.field().ordinal()], result);
            else if (call instanceof CdShieldPowerup.PowerupReadCall) assertEquals(f.powerup, result);
            else assertNull(result);
            after.accept(call);
        }
        public void completed(CdShieldPowerup.Call call) {
            assertTrue(f.body.isCurrentCall(call)); assertFalse(f.body.isCurrentPrimitive(call));
            onCompleted.accept(call); completed.add(call);
        }
        public void failedOperation(CdShieldPowerup.Call call, Throwable original) {
            assertSame(last, call); assertTrue(f.body.isCurrentCall(call)); assertFalse(f.body.isCurrentPrimitive(call)); failures.add(original);
        }
    }
}
