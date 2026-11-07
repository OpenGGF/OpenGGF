package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdCczEnemyPorts.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Constructed component cuts only; execution evidence is recorded in the external P4 handoff.
 * No fixture authenticates a source, actual World, route, current art or native pixels.
 */
class TestCdCczEnemyFamily {
    static final int W = 40;
    @ParameterizedTest @ValueSource(ints = {0x24, 0x2D, 0x2E})
    void goodFutureConvertsFullImageBeforeInvalidDispatchAndNeverSounds(int id) {
        var h = new H(id, 255); h.good = 1; h.time = 2;
        h.edit(v -> dirty(v).moveByte(ID, id).moveByte(SUBTYPE, 255).moveByte(ROUTINE, 255)
                .moveLong(X_POSITION, 0x1234ABCD).moveLong(Y_POSITION, 0x5678BEEF));
        h.run(); byte[] expected = new byte[64]; expected[0] = 0x18;
        expected[8] = 0x12; expected[9] = 0x34; expected[12] = 0x56; expected[13] = 0x78;
        assertArrayEquals(expected, h.s().copyBytes()); assertEquals(0, h.draws); assertTrue(h.sounds.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints = {0x24, 0x2D, 0x2E})
    void onlyRawPresentWithSubtypeZeroExemptsConversion(int id) {
        var h = new H(id, 255); h.good = 255; h.time = 1;
        assertThrows(UnsupportedOperationException.class, h::run); assertEquals(id, h.s().id());
        h.time = 0x81; h.run(); assertEquals(0x18, h.s().id());
    }
    @ParameterizedTest @ValueSource(ints = {0x2D, 0x2E})
    void everySubtypeUsesByteMinusOneSignedSelector(int id) {
        for (int subtype = 0; subtype < 256; subtype++) {
            var h = new H(id, 255); h.good = 1; h.time = 2; final int st = subtype;
            h.edit(v -> v.moveByte(SUBTYPE, st));
            if ((byte) (subtype - 1) < 0) { h.run(); assertEquals(0x18, h.s().id()); }
            else { assertThrows(UnsupportedOperationException.class, h::run); assertEquals(id, h.s().id()); assertTrue(h.reads.isEmpty()); }
        }
    }
    @Test void futureSecondaryFFIsNeverNormalizedByGaInit() {
        var h = new H(0x2D, 0); h.edit(v -> v.moveByte(SUBTYPE, 255).moveByte(SUBTYPE2, 255)); h.run();
        assertEquals(255, h.s().subtype2()); assertEquals(60, word(h.s(), 0x2A)); assertEquals(2, h.s().routine());
    }
    @Test void kamaWatchOffscreenDoesNotReadPlayer() {
        var h = new H(0x24, 4); h.run(); assertFalse(h.reads.stream().anyMatch(x -> x.startsWith("P"))); assertEquals(1, h.s().unsignedByte(ANIMATION));
    }
    @ParameterizedTest @CsvSource({"95,31,true", "96,31,false", "95,32,false", "-95,-31,true", "-96,-31,false"})
    void kamaUnsignedRangeEndpointsAndAttackSameCallCountdown(int dx, int dy, boolean attacks) {
        var h = new H(0x24, 4); h.px = 0x270 - dx; h.py = 0x774 - dy;
        h.edit(v -> v.moveByte(SPRITE_FLAGS, 0x84)); h.run();
        assertEquals(attacks ? 6 : 4, h.s().routine());
        if (attacks) { assertEquals(119, word(h.s(), 0x2C)); assertEquals(0x25, h.pool.state(32).id()); assertEquals(0x25, h.pool.state(33).id()); }
    }
    @Test void kamaNegativeSubtypeSuppressesBothAllocations() {
        var h = new H(0x24, 4); h.edit(v -> v.moveByte(SPRITE_FLAGS, 0x84).moveByte(SUBTYPE, 255)); h.run();
        assertEquals(0, h.pool.state(32).id()); assertEquals(6, h.s().routine());
    }
    @Test void kamaDirtySickleChildrenRetainEveryUnprescribedByte() {
        var h = new H(0x24, 4); h.edit(v -> v.moveByte(SPRITE_FLAGS, 0x84));
        var before = dirty(h.pool.state(32)).moveByte(ID, 0); h.pool.write(before); h.run();
        var expected = before.moveByte(ID, 0x25).moveByte(SUBTYPE, 0).moveWord(X, 0x270 - 17).moveWord(Y, 0x774 - 4);
        expected = CdCczEnemySupport.wordAt(expected, 0x30, h.s().identity().nativeAddress24());
        expected = CdCczEnemySupport.wordAt(expected, 0x2C, 0x40); expected = CdCczEnemySupport.byteAt(expected, 0x2E, 0x84);
        assertArrayEquals(expected.copyBytes(), h.pool.state(32).copyBytes());
    }
    @Test void kamaFullPoolStillEntersAttackAndMakesBothFiniteAttempts() {
        var h = new H(0x24, 4); h.edit(v -> v.moveByte(SPRITE_FLAGS, 0x84)); h.fill(); h.run();
        assertEquals(6, h.s().routine()); assertEquals(119, word(h.s(), 0x2C));
    }
    @Test void kamaAttackToRecoveryFallsThroughWithTimer59() {
        var h = new H(0x24, 6); h.edit(v -> CdCczEnemySupport.wordAt(v, 0x2C, 1)); h.run();
        assertEquals(8, h.s().routine()); assertEquals(59, word(h.s(), 0x2C)); assertEquals(3, h.s().unsignedByte(ANIMATION));
    }
    @Test void kamaTurnRetainsWordAnimationOddityAndFlipsOnlyAtZero() {
        var h = new H(0x24, 12); h.edit(v -> CdCczEnemySupport.wordAt(v, 0x2C, 1)); h.run();
        assertEquals(10, h.s().routine()); assertEquals(1, h.s().flags() & 1);
    }
    @Test void sickleInitReturnsBeforeParentReadAndDoesNotSetWidthOrTimer() {
        var h = new H(0x25, 0); h.edit(v -> v.moveByte(WIDTH, 77)); h.run();
        assertEquals(2, h.s().routine()); assertEquals(77, h.s().unsignedByte(WIDTH)); assertEquals(0, word(h.s(), 0x2C)); assertEquals(-0x300, h.s().signedWord(X_VELOCITY));
    }
    @Test void sickleLaunchChecksParentAfterStoresThenClearsBeforeDraw() {
        var h = new H(0x25, 2); h.edit(v -> CdCczEnemySupport.wordAt(CdCczEnemySupport.wordAt(v, 0x30, 0xDA40), 0x2C, 1));
        h.run(); assertArrayEquals(new byte[64], h.s().copyBytes()); assertEquals(1, h.draws);
    }
    @Test void sickleSameIdReusedParentIsAcceptedWithoutGenerationInvention() {
        var h = new H(0x25, 2); h.pool.write(h.pool.state(41).moveByte(ID, 0x24));
        h.edit(v -> CdCczEnemySupport.wordAt(CdCczEnemySupport.wordAt(v, 0x30, 0xDA40), 0x2C, 1)); h.run();
        assertEquals(4, h.s().routine()); assertEquals(0x87, h.s().unsignedByte(COLLISION_TYPE));
    }
    @ParameterizedTest @CsvSource({"-28,true", "27,true", "28,false", "-29,false"})
    void sickleParryRangeIsHalfOpenAndPlayerIsReadOnly(int dx, boolean parry) {
        var h = new H(0x25, 4); h.invincible = 1; h.px += dx; h.pvx = 0x1234;
        h.edit(v -> v.moveByte(COLLISION_TYPE, 0x87).moveByte(MAP_FRAME, 1)); h.run();
        assertEquals(parry ? 1 : 0, h.s().unsignedByte(ANIMATION));
        if (parry) { assertEquals(0xF800, h.s().word(Y_VELOCITY)); assertEquals(0x40, word(h.s(), 0x2A)); }
        assertFalse(h.reads.contains("PFLAGS"));
    }
    @Test void sickleNonRollingPlayerStopsBeforeCoordinatesAndSoundUsesPreAnimationFrame() {
        var h = new H(0x25, 4); h.edit(v -> v.moveByte(SPRITE_FLAGS, 0x84)); h.run();
        assertEquals(List.of(Sound.SICKLE), h.sounds); assertTrue(h.reads.contains("PFLAGS")); assertFalse(h.reads.contains("PX"));
    }
    @Test void sickleDamageTimerZeroWrapsAndOnePinsWithoutUnderflowRepair() {
        var h = new H(0x25, 4); h.run(); assertEquals(255, raw(h.s(), 0x34));
        h.edit(v -> CdCczEnemySupport.byteAt(v, 0x34, 1).moveByte(COLLISION_TYPE, 0x87)); h.run();
        assertEquals(1, raw(h.s(), 0x34)); assertEquals(0, h.s().unsignedByte(COLLISION_TYPE));
    }
    @Test void gaActivePathDrawsTwiceAndReloadsBetweenDraws() {
        var h = new H(0x2D, 4); h.edit(v -> CdCczEnemySupport.wordAt(v, 0x2A, 2));
        h.drawRewrite = v -> v.moveByte(FLAGS, v.flags() + 1); h.run(); assertEquals(2, h.draws); assertEquals(2, h.s().flags());
    }
    @Test void gaDirectionPlayerReadOnlyOnReachedTimerZero() {
        var h = new H(0x2D, 6); h.edit(v -> CdCczEnemySupport.wordAt(v, 0x2A, 2)); h.run(); assertFalse(h.reads.contains("PX"));
        h.run(); assertTrue(h.reads.contains("PX")); assertEquals(8, h.s().routine());
    }
    @Test void gaDustWritesOnlyPrescribedChildFieldsAndHasNoDespawn() {
        var h = new H(0x2D, 8); h.edit(v -> CdCczEnemySupport.byteAt(CdCczEnemySupport.wordAt(v, 0x2A, 1), 0x3F, 255));
        var old = dirty(h.pool.state(32)).moveByte(ID, 0); h.pool.write(old); h.run();
        assertArrayEquals(old.moveByte(ID, 0x2D).moveLong(X_POSITION, h.s().longBits(X_POSITION))
                .moveLong(Y_POSITION, h.s().longBits(Y_POSITION)).moveByte(SUBTYPE, 1).copyBytes(), h.pool.state(32).copyBytes());
    }
    @Test void gaDustDeleteStillDrawsClearedImage() {
        var h = new H(0x2D, 2); h.edit(v -> CdCczEnemySupport.wordAt(v.moveByte(SUBTYPE, 1), 0x2A, 1)); h.run();
        assertEquals(1, h.draws); assertEquals(0, h.s().id()); assertTrue(h.reads.isEmpty());
    }
    @ParameterizedTest @CsvSource({"-80,true", "79,true", "80,false", "-81,false"})
    void tentouWaitBoxUsesHalfOpenNativeRangeAndLazyX(int dy, boolean reached) {
        // Native WaitForPlayer subtracts player Y from object Y (Tentou/Main.asm:134-139).
        var h = new H(0x2E, 4); h.py -= dy; h.run(); assertEquals(reached, h.reads.contains("PX"));
    }
    @Test void tentouRiseHasNoProbeAndKeepsAnimationDrawWrapper() {
        var h = new H(0x2E, 8); int old = h.s().longBits(Y_POSITION); h.run();
        assertEquals(old - 0x8000, h.s().longBits(Y_POSITION)); assertTrue(h.angles.isEmpty()); assertEquals(1, h.draws);
    }
    @Test void bombArmCountdownDoesNotAnimateAndFuseZeroDefersConversion() {
        var h = new H(0x2E, 4); h.edit(v -> CdCczEnemySupport.wordAt(v.moveByte(SUBTYPE, 1), 0x2A, 1)); h.run();
        assertEquals(6, h.s().routine()); assertEquals(120, word(h.s(), 0x2A));
        h.edit(v -> CdCczEnemySupport.wordAt(v, 0x2A, 1)); h.run(); assertEquals(8, h.s().routine()); assertEquals(0x2E, h.s().id());
        h.run(); assertEquals(0x18, h.s().id()); assertEquals(1, h.s().unsignedByte(ROUTINE_2));
    }
    @ParameterizedTest @ValueSource(ints = {2, 4, 6})
    void bombCollisionConvertsBeforeProbeTimerOrAnimation(int routine) {
        var h = new H(0x2E, routine); h.edit(v -> v.moveByte(SUBTYPE, 1).moveByte(COLLISION_STATUS, 1).moveByte(SPRITE_FLAGS, 0x84)); h.run();
        assertEquals(0x18, h.s().id()); assertEquals(List.of(Sound.EXPLOSION), h.sounds); assertTrue(h.angles.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints = {2, 3, 4})
    void chainActualProgrammeCountAllocatesOnlyLaterSlotsAndDoesNotRunChildren(int count) {
        var h = new H(0x2C, 0); h.links = count; h.run();
        for (int n = 0; n < count; n++) {
            var link = h.pool.state(W + 1 + n); assertEquals(0x2C, link.id()); assertEquals(255, link.subtype()); assertEquals(0, link.routine());
            assertEquals(h.s().identity().nativeAddress24() & 65535, word(link, 0x3E));
        }
        assertEquals(0, h.pool.state(32).id()); assertEquals(1, h.draws);
    }
    @Test void chainPartialFailureDeletesOnlyParentAndLeavesAllocatedChildForPhysicalPass() {
        var h = new H(0x2C, 0); h.fill(); h.pool.deleteObject(41); h.run();
        assertEquals(0, h.s().id()); assertEquals(0x2C, h.pool.state(41).id());
        var child = new CdSpikeChain(h.placement, h, h); child.run(41); assertEquals(0, h.pool.state(41).id());
    }
    @Test void chainChildCopiesBeforeParentCheckAndAcceptsSameIdReuse() {
        var h = new H(0x2C, 2); h.edit(v -> CdCczEnemySupport.wordAt(v.moveByte(SUBTYPE, 255), 0x3E, 0xDA40));
        h.pool.write(h.pool.state(41).moveByte(ID, 0x2C)); h.run(); assertEquals(0x2C, h.s().id()); assertEquals(1, h.draws);
    }
    @Test void chainRotateDoesNotInventChildIdGuard() {
        var h = new H(0x2C, 2); h.links = 2;
        h.edit(v -> CdCczEnemySupport.wordAt(CdCczEnemySupport.wordAt(v, 0x2E, 0xDA40), 0x30, 0xDA80));
        h.pool.write(h.pool.state(41).moveByte(ID, 0x55)); h.run(); assertEquals(0x55, h.pool.state(41).id());
        assertEquals(h.s().longBits(X_POSITION) + 0x100000, longWord(h.pool.state(41), 0x2A));
    }
    @Test void heldAnimatorDoesNotResolveMissingTable() {
        var h = new H(0x2D, 4); h.missingAnimation = true; h.edit(v -> CdCczEnemySupport.wordAt(v, 0x2A, 2));
        h.run(); assertEquals(2, h.draws);
    }
    @Test void unknownAnimationFailureRetainsResetAndSubtractPrefix() {
        var h = new H(0x2D, 4); h.missingAnimation = true; h.edit(v -> v.moveByte(ANIMATION, 9).moveByte(PREVIOUS_ANIMATION, 8));
        assertThrows(UnsupportedOperationException.class, h::run);
        assertEquals(9, h.s().unsignedByte(PREVIOUS_ANIMATION)); assertEquals(0, h.s().unsignedByte(ANIMATION_FRAME)); assertEquals(255, h.s().unsignedByte(ANIMATION_TIME)); assertEquals(0, h.draws);
    }
    @Test void missingFrameRetainsSpeedAndNoDraw() {
        var h = new H(0x2D, 4); h.animation = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(new byte[]{0,2,7}), 0, 1);
        h.edit(v -> v.moveByte(ANIMATION_TIME, 0)); assertThrows(UnsupportedOperationException.class, h::run);
        assertEquals(7, h.s().unsignedByte(ANIMATION_TIME)); assertEquals(0, h.draws);
    }
    @Test void ffLoopUsesNegativeReturnedFrameWithoutRecursiveDispatch() {
        var h = new H(0x2D, 4); h.animation = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(new byte[]{0,2,7,(byte)255}), 0, 1);
        h.edit(v -> v.moveByte(ANIMATION_TIME, 0).moveByte(ANIMATION_FRAME, 0)); h.run(); assertEquals(31, h.s().unsignedByte(MAP_FRAME));
    }
    @Test void readonlyFaultKeepsOriginalAndSuppressesFullPoolPostcheckViolation() {
        var h = new H(0x2D, 2); h.globalRewrite = () -> { h.pool.write(h.pool.state(127).moveByte(FLAGS, 99)); throw h.fault; };
        var actual = assertThrows(Fault.class, h::run); assertSame(h.fault, actual); assertEquals(1, actual.getSuppressed().length);
    }
    @Test void readonlyReturnDetectsSavedBankMutation() {
        var h = new H(0x2D, 2); h.globalRewrite = () -> h.placement.bsetSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.LOADED);
        assertThrows(IllegalStateException.class, h::run);
    }
    @Test void drawMutationIsReloadedForSavedAddressAndDespawn() {
        var h = new H(0x2D, 2); h.placement.bsetSavedFlag(1, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.drawRewrite = v -> v.moveWord(X, 0x1000).moveByte(SAVED_FLAGS_ID, 1); h.run(); assertEquals(0, h.s().id()); assertEquals(0, h.placement.savedFlag(1, 1));
    }
    @Test void lastSavedAddressWorksAndFirstUnownedRetainsDrawnSlot() {
        var h = new H(0x2D, 2); h.time = 2; h.drawRewrite = v -> v.moveWord(X, 0x1000).moveByte(SAVED_FLAGS_ID, 254);
        assertThrows(UnsupportedOperationException.class, h::run); assertEquals(0x2D, h.s().id()); assertEquals(1, h.draws);
    }
    @Test void unownedLinkFailsAfterCountdownWithoutAnyBroadAddressReader() {
        var h = new H(0x25, 2); h.edit(v -> CdCczEnemySupport.wordAt(v, 0x2C, 2));
        assertThrows(UnsupportedOperationException.class, h::run); assertEquals(1, word(h.s(), 0x2C)); assertEquals(0, h.draws);
    }
    @Test void componentCaptureRestoresEveryLinkTimerAndPhaseWithoutReplayingCalls() {
        var h = new H(0x2C, 0); h.run(); var cut = h.placement.capture(); var expected = h.pool.capture().slots();
        h.pool.deleteObject(W); h.placement.restore(cut); assertEquals(expected, h.pool.capture().slots()); assertEquals(1, h.draws);
    }
    @ParameterizedTest @CsvSource({"1900,12", "1901,10", "1914,10", "1915,12"})
    void kamaWalkFloorRejectsPositiveSevenButAcceptsNegativeSeven(int y, int routine) {
        var h = new H(0x24, 10); h.edit(v -> v.moveByte(FLAGS, 1).moveByte(Y_RADIUS, 20)
                .moveWord(Y, y).moveWord(Y_FRACTION, 0xBEEF)); h.run();
        assertEquals(routine, h.s().routine()); assertEquals(0xBEEF, h.s().word(Y_FRACTION));
        if (routine == 12) assertEquals(71, word(h.s(), 0x2C));
    }
    @Test void kamaRecoveryZeroWalksInSameCall() {
        var h = new H(0x24, 8); h.edit(v -> CdCczEnemySupport.wordAt(v.moveByte(FLAGS, 1).moveByte(Y_RADIUS, 20).moveWord(Y, 1901), 0x2C, 1));
        h.run(); assertEquals(0x271, h.s().centreX()); assertEquals(10, h.s().routine());
    }
    @Test void kamaInitFallPreservesSecondaryAndAdvancesOnlyOnNegativeFloor() {
        var h = new H(0x24, 0); h.edit(v -> v.moveWord(Y, 1914).moveByte(SUBTYPE2, 255)); h.run();
        assertEquals(4, h.s().routine()); assertEquals(255, h.s().subtype2()); assertEquals(0x23F2, h.s().word(TILE));
    }
    @Test void tentouFindFloorUsesSubtractDistanceAndFullLongDirtyMotionSelector() {
        var h = new H(0x2E, 2); h.edit(v -> v.moveByte(Y_RADIUS, 14).moveWord(Y, 1905));
        h.run(); assertEquals(4, h.s().routine()); assertEquals(0x3000, longWord(h.s(), 0x2E)); assertEquals(8, word(h.s(), 0x36));
        var dirty = new H(0x2E, 2); dirty.edit(v -> CdCczEnemySupport.longAt(v.moveByte(Y_RADIUS, 14).moveWord(Y, 1905), 0x2A, 1));
        dirty.run(); assertEquals(6, dirty.s().routine());
    }
    @Test void bombNegativeFloorSnapsWordOnlyBeforeArming() {
        var h = new H(0x2E, 2); h.edit(v -> v.moveByte(SUBTYPE, 1).moveByte(Y_RADIUS, 6).moveWord(Y, 1927).moveWord(Y_FRACTION, 0xABCD));
        h.run(); assertEquals(4, h.s().routine()); assertEquals(0xABCD, h.s().word(Y_FRACTION)); assertEquals(120, word(h.s(), 0x2A));
    }
    @Test void angleFailureRetainsPrezeroReceiptAndOriginalException() {
        var h = new H(0x24, 2); h.angleFailure = true; h.edit(v -> v.moveByte(Y_RADIUS, 20));
        assertSame(h.fault, assertThrows(Fault.class, h::run)); assertEquals(List.of(0), h.angles); assertEquals(0, h.draws);
        assertFalse(h.retainedAngle.isCurrent());
    }
    @Test void readonlySuccessChecksPlacementStatusAsWellAsPoolAndBank() {
        var h = new H(0x2D, 2); h.globalRewrite = h.placement::resetSavedObjectFlags;
        h.placement.bsetSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.LOADED);
        assertThrows(IllegalStateException.class, h::run);
    }
    @Test void drawFaultKeepsActualMutationWithoutWrapperTail() {
        var h = new H(0x2D, 2); h.drawRewrite = v -> { h.pool.write(v.moveByte(FLAGS, 77)); throw h.fault; };
        assertSame(h.fault, assertThrows(Fault.class, h::run)); assertEquals(77, h.s().flags()); assertEquals(0x2D, h.s().id());
    }
    @Test void untouchedSlotsAndSavedBankAreExactAcrossOrdinarySickleInit() {
        var h = new H(0x25, 0); var cut = h.placement.capture(); h.run();
        for (int n = 32; n < 128; n++) if (n != W) assertEquals(cut.pool().slots().get(n - 32), h.pool.state(n));
        assertArrayEquals(cut.savedFlags().copy(), h.placement.capture().savedFlags().copy()); assertEquals(cut.status(), h.placement.status());
    }
    static CdPlayerTerrain terrain() {
        byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50], ids = new byte[900];
        byte[] angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; fg[2 + 7 * 42 + 2] = 2;
        ids[1] = 7; angles[7] = 0x22; Arrays.fill(heights, 7 * 16, 8 * 16, (byte) 8);
        int at = 512 + (8 * 16 + 7) * 2; chunks[at] = 0x20; chunks[at + 1] = 1;
        return new CdPlayerTerrain(CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths));
    }
    @ParameterizedTest @ValueSource(ints = {0, 255})
    void tentouBombPhaseMaskIsStoredOnlyForEnabledProducerIncludingFullPool(int producer) {
        var h = new H(0x2E, 6); h.edit(v -> {
            v = CdCczEnemySupport.longAt(v.moveByte(Y_RADIUS, 14), 0x32, 1906 << 16);
            v = CdCczEnemySupport.longAt(v, 0x2E, 0x3000);
            return CdCczEnemySupport.byteAt(CdCczEnemySupport.wordAt(v, 0x36, 127), 0x3F, producer);
        }); h.fill(); h.run(); assertEquals(producer == 0 ? 128 : 0, word(h.s(), 0x36));
    }
    @Test void tentouReachedBombAllocationRetainsLongFractionsAndAddsWordYOnly() {
        var h = new H(0x2E, 6); h.edit(v -> {
            v = CdCczEnemySupport.longAt(v.moveByte(Y_RADIUS, 14), 0x32, (1906 << 16) | 0xBEEF);
            v = CdCczEnemySupport.longAt(v, 0x2E, 0x3000);
            return CdCczEnemySupport.byteAt(CdCczEnemySupport.wordAt(v, 0x36, 127), 0x3F, 255);
        }); h.run(); var child = h.pool.state(32); assertEquals(0x2E, child.id()); assertEquals(1, child.subtype());
        assertEquals((h.s().centreY() + 16) & 65535, child.centreY()); assertEquals(h.s().word(Y_FRACTION), child.word(Y_FRACTION));
    }
    @Test void tentouGapDetectionBobsOnceAndRetainsNewBaseBeforeCrossing() {
        var h = new H(0x2E, 6); h.edit(v -> CdCczEnemySupport.longAt(v.moveByte(Y_RADIUS, 14), 0x32, 1800 << 16)); h.run();
        assertEquals(10, h.s().routine()); assertEquals(1, word(h.s(), 0x36)); assertEquals(h.s().longBits(Y_POSITION), longWord(h.s(), 0x32));
    }
    @Test void tentouCrossGapReturnsToFindFloorOnlyAtSixteenOrMore() {
        var h = new H(0x2E, 10); h.edit(v -> v.moveByte(Y_RADIUS, 14).moveWord(Y, 1800)); h.run(); assertEquals(2, h.s().routine());
    }
    @ParameterizedTest @ValueSource(ints = {0xFE, 0xFD})
    void missingAnimationCommandOperandRetainsSpeedBeforeFailure(int command) {
        var h = new H(0x2D, 4); h.animation = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(new byte[]{0,2,7,(byte)command}), 0, 1);
        h.edit(v -> v.moveByte(ANIMATION_TIME, 0)); assertThrows(UnsupportedOperationException.class, h::run);
        assertEquals(7, h.s().unsignedByte(ANIMATION_TIME)); assertEquals(0, h.s().unsignedByte(ANIMATION_FRAME)); assertEquals(0, h.draws);
    }
    @Test void missingFEBackFrameRetainsSubtractedIndexBeforeFailure() {
        var h = new H(0x2D, 4); h.animation = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(new byte[]{0,2,7,(byte)0xFE,4}), 0, 1);
        h.edit(v -> v.moveByte(ANIMATION_TIME, 0)); assertThrows(UnsupportedOperationException.class, h::run);
        assertEquals(252, h.s().unsignedByte(ANIMATION_FRAME)); assertEquals(7, h.s().unsignedByte(ANIMATION_TIME));
    }
    @Test void sickleDespawnReadsPostDrawBaseRatherThanMovedX() {
        var h = new H(0x25, 0); h.drawRewrite = v -> CdCczEnemySupport.wordAt(v, 0x32, 0x1000); h.run(); assertEquals(0, h.s().id());
    }
    @ParameterizedTest @CsvSource({"128,1,0", "128,255,1", "129,255,2", "255,128,2"})
    void savedWarpUsesSignedDirectionAndClamp(int time, int direction, int period) {
        var h = new H(0x2D, 2); h.time = time; h.direction = direction;
        h.edit(v -> v.moveByte(SAVED_FLAGS_ID, 1).moveByte(SPRITE_FLAGS, 0x84));
        h.placement.bsetSavedFlag(1, period, CdObjectPlacementRuntime.SavedBit.LOADED); h.run();
        assertEquals(0, h.placement.savedFlag(1, period)); assertEquals(0, h.s().id());
    }
    static int raw(CdObjectState s, int at) { return Byte.toUnsignedInt(s.copyBytes()[at]); }
    static int word(CdObjectState s, int at) { return (raw(s, at) << 8) | raw(s, at + 1); }
    static int longWord(CdObjectState s, int at) { return (word(s, at) << 16) | word(s, at + 2); }
    static CdObjectState dirty(CdObjectState s) {
        for (var f : CdObjectState.LongField.values()) s = s.moveLong(f, 0xAABBCCDD);
        for (var f : CdObjectState.WordField.values()) s = s.moveWord(f, 0xBEEF);
        for (var f : CdObjectState.ByteField.values()) s = s.moveByte(f, 0xA5);
        return s;
    }
    static final class Fault extends RuntimeException { private static final long serialVersionUID = 1L; }
    static final class H implements CdCczEnemyPorts, Assets {
        final CdObjectPlacementRuntime placement = new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(
                HexFormat.of().parseHex("000c0014ffff000000000000ffff000000000000ffff00000000")));
        final CdObjectPool pool = placement.pool(); final CdCczEnemySupport body;
        final List<String> reads = new ArrayList<>(); final List<Sound> sounds = new ArrayList<>(); final List<Integer> angles = new ArrayList<>();
        final Fault fault = new Fault(); int good, time = 1, direction, camera = 0x200, invincible, px = 0x270, py = 0x774, pvx, links = 3, draws;
        boolean missingAnimation, angleFailure; AngleCall retainedAngle; Runnable globalRewrite = () -> { }; UnaryOperator<CdObjectState> drawRewrite = UnaryOperator.identity();
        CdObjectAnimation.AnimationTable animation = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(new byte[]{0,8,0,8,0,8,0,8,7,0,(byte)255}), 0, 4);
        H(int id, int routine) {
            pool.write(pool.state(W).moveByte(ID, id).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, 4)
                    .moveByte(ANIMATION_TIME, 1).moveWord(X, px).moveWord(Y, py));
            // Advanced Sickle cuts inherit Init's oKamaSickleBaseX store, before the
            // range-only despawn tail (KamaKama/Main.asm:464-465,484).
            if (id == 0x25 && routine != 0)
                pool.write(pool.state(W).moveWord(CdObjectState.WordField.RING_ANCHOR_X, px));
            body = switch (id) {
                case 0x24 -> new CdKamaKama(placement, this, terrain(), this);
                case 0x25 -> new CdKamaSickle(placement, this, this);
                case 0x2C -> new CdSpikeChain(placement, this, this);
                case 0x2D -> new CdGa(placement, this, this);
                case 0x2E -> new CdTentou(placement, this, terrain(), this);
                default -> throw new IllegalArgumentException();
            };
        }
        void fill() { for (int n = 32; n < 128; n++) if (n != W) pool.write(pool.state(n).moveByte(ID, 0x55)); }
        CdObjectState s() { return pool.state(W); } void edit(UnaryOperator<CdObjectState> f) { pool.write(f.apply(s())); } void run() { body.run(W); }
        @Override public int global(Global g) { reads.add("G" + g); globalRewrite.run(); return switch (g) {
            case GOOD_FUTURE -> good; case TIME_ZONE -> time; case WARP_DIRECTION -> direction; case CAMERA_X -> camera; case INVINCIBLE -> invincible; }; }
        @Override public int player(Player p) { reads.add("P" + p); return switch (p) { case X -> px; case Y -> py; case FLAGS -> 0; case X_VELOCITY -> pvx; }; }
        @Override public CdPlayerMath.SineCosine sine(int angle) { return new CdPlayerMath.SineCosine(0, 256); }
        @Override public void sound(SoundCall call) { assertTrue(call.isCurrent()); sounds.add(call.sound()); }
        @Override public void primaryAngle(AngleCall call) { assertTrue(call.isCurrent()); retainedAngle = call; angles.add(call.value()); if (angleFailure) throw fault; }
        @Override public void draw(CdObjectPool actual, CdObjectState.Identity identity) {
            assertSame(pool, actual); draws++; pool.write(drawRewrite.apply(pool.state(identity.wholeIndex())));
        }
        @Override public MappingView mapping(Mapping m) { return new MappingView(new CdObjectMappings.DeclaredTable(0x210000 + m.ordinal() * 0x100, CdAssetBytes.copyOf(new byte[2]), 0, 1)); }
        @Override public CdObjectAnimation.AnimationTable animation(Animation a) {
            if (missingAnimation) throw new UnsupportedOperationException("Missing test pointer"); return animation;
        }
        @Override public int chainLinkCount() { return links; }
    }
}
