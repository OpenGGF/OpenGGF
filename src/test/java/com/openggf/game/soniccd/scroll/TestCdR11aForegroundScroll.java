package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdR11aLoadPlan;
import com.openggf.game.soniccd.level.CdR11aProfile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-authored before the implementation; bounded runs are recorded in the dated CD plan.
 * Constructed RAM-width cases, not a native execution oracle or retail-route certificate.
 * Source pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 */
class TestCdR11aForegroundScroll {
    private static final CdPlayerMovement.Bounds DEFAULT_BOUNDS = bounds(0, 0x2897, 0x710);

    @Test
    void deadCameraWritesOnlyBothBottomWordsAndKeepsEveryScrollFractionAndFlag() {
        var before = new State(0x234, 0x07008000, 0x43, 0x54, 0x1234, 0xFEDC,
                0xF5A5, 0x710, 0x80, 0xA55A, 0xB612);
        var shared = new CdPlayerMovement.Bounds(0x123, 0x456, 0x710, 0xFE);
        var result = deadCameraFreeze(before, shared, 0x88);
        assertEquals(new State(0x234, 0x07008000, 0x43, 0x54, 0x1234, 0xFEDC,
                0xF5A5, 0x700, 0x80, 0xA55A, 0xB612), result.state());
        assertEquals(new CdPlayerMovement.Bounds(0x123, 0x456, 0x700, 0xFE), result.bounds());
        assertEquals(0x88, result.cameraYCentre());
        assertEquals(Boundary.DEAD_CAMERA_FREEZE, result.boundary());
    }

    @ParameterizedTest
    @CsvSource({"0,0", "32768,32768", "65535,65535"})
    void deadCameraCopiesRawYWordAcrossTheSignedBoundaryWithoutClamping(int word, int expected) {
        var before = new State(0, (word << 16) | 0xABCD, 0x10, 0x10,
                0, 0, 0, 0x710, 0, 0, 0);
        var result = deadCameraFreeze(before, DEFAULT_BOUNDS, 0x60);
        assertEquals(expected, result.bounds().bottom());
        assertEquals(expected, result.state().destBottomBound());
        assertEquals(before.cameraY16_16(), result.state().cameraY16_16());
    }

    @Test
    void deadCameraRequiresItsActualSharedOwnersAndWordCentre() {
        var before = state(0x200, 0x07008000);
        assertThrows(NullPointerException.class, () -> deadCameraFreeze(null, DEFAULT_BOUNDS, 0x60));
        assertThrows(NullPointerException.class, () -> deadCameraFreeze(before, null, 0x60));
        assertThrows(IllegalArgumentException.class, () -> deadCameraFreeze(before, DEFAULT_BOUNDS, -1));
        assertThrows(IllegalArgumentException.class, () -> deadCameraFreeze(before, DEFAULT_BOUNDS, 0x10000));
    }

    @Test
    void coldLoadPlanCutAndStationarySourceStartDoNoFollowingWork() throws IOException {
        var plan = loadPlan(0x40, 0x76B, 0, 0x2897, 0, 0x710, 0x60);
        var cold = coldFromLoadPlan(plan, 320, 224);
        assertEquals(new State(0, 0x070B0000, 0x10, 0x10, 0, 0, 0,
                0x710, 0, 0, 0), cold);
        var result = step(cold, input(0x40, 0x76B));
        assertEquals(cold, result.state());
        assertSame(DEFAULT_BOUNDS, result.bounds());
        assertEquals(0x60, result.cameraYCentre());
        assertEquals(Boundary.FOREGROUND_COMPLETE_STATIC_BACKGROUND_DEVELOPMENT, result.boundary());
    }

    @Test
    void coldFactoryUsesActualDecodedStartAndSourceFixedNinetySixYSubtraction() throws IOException {
        var plan = loadPlan(0x200, 0x100, 0x80, 0x100, 0, 0x90, 0x88);
        var cold = coldFromLoadPlan(plan, 320, 224);
        assertEquals(0x100, cold.cameraX());
        assertEquals(0x00900000, cold.cameraY16_16());
        assertEquals(0x90, cold.destBottomBound());
        assertEquals(0x80, cold.destLeftBound());
        // LevelSizeLoad subtracts #$60 here, not the table's $88 centre.
        var uncapped = coldFromLoadPlan(loadPlan(0x200, 0x100, 0, 0x1000, 0, 0x710, 0x88), 320, 224);
        assertEquals(0x00A00000, uncapped.cameraY16_16());
    }

    @ParameterizedTest
    @CsvSource({"512,1024,0,10391,528,4096", "512,512,0,10391,496,61440",
            "512,1024,0,520,520,2048", "512,512,508,10391,508,64512"})
    void horizontalCapsUseCurrentSharedLeftAndRight(int cameraX, int actorX, int left, int right,
                                                    int expectedX, int expectedDiff) {
        var in = withBounds(input(actorX, 0x160), bounds(left, right, 0x710));
        var result = step(state(cameraX, 0x01000000), in);
        assertEquals(expectedX, result.state().cameraX());
        assertEquals(expectedDiff, result.state().scrollXDiff());
        assertEquals(in.bounds(), result.bounds());
    }

    @Test
    void horizontalDecisionUsesSecondSubtractionBorrowEvenForActorBehindCamera() {
        // $C0-$100=$FFC0; $FFC0-$A0 has NO borrow, so CamAhead receives signed -224.
        // Its signed BLT #16 leaves -224 intact; the ahead path never checks leftBound.
        var in = withBounds(input(0xC0, 0x160), bounds(0x80, 0x2897, 0x710));
        var result = step(state(0x100, 0x01000000), in);
        assertEquals(0x20, result.state().cameraX());
        assertEquals(0x2000, result.state().scrollXDiff()); // ASL.W wraps -224<<8.
    }

    @Test
    void horizontalCentreIsTheCurrentBodyOwnedInput() {
        var in = input(0x2A1, 0x160);
        assertEquals(0x201, step(state(0x200, 0x01000000), in).state().cameraX());
        var extended = new Input(in.actor(), in.bounds(), 0, 0xB0, 0x60,
                in.region(), 0, 320, 224);
        assertEquals(0x1F1, step(state(0x200, 0x01000000), extended).state().cameraX());
    }

    @Test
    void exactVerticalPathZerosFractionAndRotatesCompleteLongDifference() {
        var before = state(0x200, 0x01001234);
        var down = step(before, input(0x2A0, 0x163));
        assertEquals(0x01030000, down.state().cameraY16_16());
        assertEquals(0x02ED, down.state().scrollYDiff());
        var up = step(before, input(0x2A0, 0x15D));
        assertEquals(0x00FD0000, up.state().cameraY16_16());
        assertEquals(0xFCED, up.state().scrollYDiff());
        assertEquals(0x01001234, before.cameraY16_16());
    }

    @Test
    void cappedVerticalPathsPreserveNativeLongFraction() {
        var before = state(0x200, 0x01001234);
        var down = step(before, input(0x2A0, 0x1C4));
        assertEquals(0x01061234, down.state().cameraY16_16());
        assertEquals(0x0600, down.state().scrollYDiff());
        var up = step(before, input(0x2A0, 0xFC));
        assertEquals(0x00FA1234, up.state().cameraY16_16());
        assertEquals(0xFA00, up.state().scrollYDiff());
    }

    @ParameterizedTest
    @CsvSource({"2047,6", "2048,16", "-2047,6", "-2048,16", "-32768,16"})
    void groundSpeedThresholdUsesWordNegateThenUnsignedCompare(int groundVelocity, int cap) {
        var in = withActor(input(0x2A0, 0x1C4), new Actor(0x2A0, 0x1C4, 0, groundVelocity, 0, 0));
        var result = step(state(0x200, 0x01001234), in);
        assertEquals(((0x100 + cap) << 16) | 0x1234, result.state().cameraY16_16());
        assertEquals(cap << 8, result.state().scrollYDiff());
    }

    @Test
    void alteredYCentreSelectsTwoPixelCapBeforeAnyGroundSpeedTest() {
        var in = new Input(new Actor(0x2A0, 0x200, 0, -32768, 0, 0), DEFAULT_BOUNDS,
                0, 0xA0, 0x88, CdReleaseVariant.USA, 0, 320, 224);
        var result = step(state(0x200, 0x01001234), in);
        assertEquals(0x01021234, result.state().cameraY16_16());
        assertEquals(0x0200, result.state().scrollYDiff());
    }

    @ParameterizedTest
    @CsvSource({"-33,16711680,65261", "-32,16781876,0", "0,16781876,0",
            "31,16781876,0", "32,16777216,65517", "33,16842752,237"})
    void airWindowUsesBorrowAndBottomEdgeStillTakesExactZeroPath(int offset, int expectedY, int expectedDiff) {
        var in = withActor(input(0x2A0, 0x160 + offset),
                new Actor(0x2A0, 0x160 + offset, 2, 0, 0, 0));
        var result = step(state(0x200, 0x01001234), in);
        assertEquals(expectedY, result.state().cameraY16_16());
        assertEquals(expectedDiff, result.state().scrollYDiff());
    }

    @Test
    void rollingSubtractsFiveBeforeGroundAndAirWindows() {
        var before = state(0x200, 0x01001234);
        var corrected = withActor(input(0x2A0, 0x165), new Actor(0x2A0, 0x165, 4, 0, 0, 0));
        assertEquals(0x01001234, step(before, corrected).state().cameraY16_16());
        var above = withActor(input(0x2A0, 0x160), new Actor(0x2A0, 0x160, 4, 0, 0, 0));
        assertEquals(0x00FB0000, step(before, above).state().cameraY16_16());
        var air = withActor(input(0x2A0, 0x165), new Actor(0x2A0, 0x165, 6, 0, 0, 0));
        assertEquals(0x01001234, step(before, air).state().cameraY16_16());
    }

    @Test
    void airOutsideWindowUsesSixteenPixelCapEvenWithAlteredCentre() {
        var in = new Input(new Actor(0x2A0, 0x300, 2, 0, 0, 0), DEFAULT_BOUNDS,
                0, 0xA0, 0x88, CdReleaseVariant.USA, 0, 320, 224);
        assertEquals(0x01101234, step(state(0x200, 0x01001234), in).state().cameraY16_16());
    }

    @Test
    void ordinaryTopAndBottomCappingRetainsFractionOnCappedPaths() {
        var down = withRegion(withBounds(input(0x2A0, 0x1C4), bounds(0, 0x2897, 0x100)), CdReleaseVariant.JAPAN);
        assertEquals(0x01001234, step(state(0x200, 0x00FE1234), down).state().cameraY16_16());
        var up = new Input(new Actor(0x2A0, 0, 0, 0, 0, 0), DEFAULT_BOUNDS,
                0x10, 0xA0, 0x60, CdReleaseVariant.JAPAN, 0, 320, 224);
        assertEquals(0x00101234, step(state(0x200, 0x00121234), up).state().cameraY16_16());
    }

    @ParameterizedTest
    @CsvSource({"USA,1808,1808", "JAPAN,1040,1804", "EUROPE,1040,1804"})
    void eventEightFortyCrossingObservesRegionSpecificOldOrNewX(CdReleaseVariant region,
                                                              int target, int bottom) {
        var result = step(state(0x830, 0x01000000), withRegion(input(0xA00, 0x160), region));
        assertEquals(0x840, result.state().cameraX());
        assertEquals(target, result.state().destBottomBound());
        assertEquals(bottom, result.bounds().bottom());
    }

    @ParameterizedTest
    @CsvSource({"USA,1040,1040", "JAPAN,784,1036", "EUROPE,784,1036"})
    void eventE00CrossingObservesRegionSpecificOldOrNewX(CdReleaseVariant region, int target, int bottom) {
        var in = withRegion(withBounds(input(0x1000, 0x160), bounds(0, 0x2897, 0x410)), region);
        var result = step(state(0xDF0, 0x01000000), in);
        assertEquals(0xE00, result.state().cameraX());
        assertEquals(target, result.state().destBottomBound());
        assertEquals(bottom, result.bounds().bottom());
    }

    @Test
    void eventYCentreIsVisibleToUsaFollowAndOnlyNextFollowElsewhere() {
        var in = input(0x1C16, 0x188);
        assertEquals(0x01001234, step(state(0x200, 0x01001234), in).state().cameraY16_16());
        var jp = step(state(0x200, 0x01001234), withRegion(in, CdReleaseVariant.JAPAN));
        assertEquals(0x01061234, jp.state().cameraY16_16());
        assertEquals(0x88, jp.cameraYCentre());
    }

    @ParameterizedTest
    @CsvSource({"7190,136", "7189,96", "8645,136", "8646,96"})
    void secondRampRangeOnlyChangesYCenterAndDoesNotRejectCoordinates(int actorX, int expectedCentre) {
        var result = step(state(0x200, 0x01000000), input(actorX, 0x160));
        assertEquals(expectedCentre, result.cameraYCentre());
    }

    @Test
    void eventDoesNotResetPreviouslyAlteredYCenterOutsideRampRange() {
        var in = new Input(new Actor(0x2A0, 0x188, 0, 0, 0, 0), DEFAULT_BOUNDS,
                0, 0xA0, 0x88, CdReleaseVariant.USA, 0, 320, 224);
        assertEquals(0x88, step(state(0x200, 0x01000000), in).cameraYCentre());
    }

    @Test
    void hudSourceByteControlsLeftLockAndAnOmittedDevelopmentTimerMustBeExplicitZero() {
        var in = input(0x8E0, 0x160);
        for (int active : new int[]{1, 0x80, 0xFF}) {
            var result = step(state(0x840, 0x01000000), withHud(in, active));
            assertEquals(0x820, result.bounds().left());
            assertEquals(0x820, result.state().destLeftBound());
        }
        assertEquals(0, step(state(0x840, 0x01000000), in).bounds().left());
        assertEquals(0, step(state(0x83F, 0x01000000), withHud(input(0x8DF, 0x160), 1)).bounds().left());
        var already = withBounds(withHud(in, 1), bounds(0x900, 0x2897, 0x710));
        var untouched = step(state(0x840, 0x01000000), already);
        assertEquals(0x900, untouched.bounds().left());
        assertEquals(0, untouched.state().destLeftBound());
    }

    @Test
    void givingUpSetsCurrentAndDestinationBottomFromRegionOrderedCamera() {
        var in = withActor(withRegion(input(0x2A0, 0x163), CdReleaseVariant.JAPAN),
                new Actor(0x2A0, 0x163, 0, 0, 0x2B, 0));
        var usa = step(state(0x200, 0x01001234), withRegion(in, CdReleaseVariant.USA));
        assertEquals(0x01000000, usa.state().cameraY16_16());
        assertEquals(0x100, usa.bounds().bottom());
        assertEquals(0x100, usa.state().destBottomBound());
        var result = step(state(0x200, 0x01001234), in);
        assertEquals(0x01030000, result.state().cameraY16_16());
        assertEquals(0x103, result.bounds().bottom());
        assertEquals(0x103, result.state().destBottomBound());
        assertEquals(0, result.state().bottomBoundShift());
    }

    @Test
    void upwardBoundaryShiftReanchorsToEvenCameraWordThenSubtractsFour() {
        var in = withRegion(input(0x9A0, 0x561), CdReleaseVariant.JAPAN);
        var result = step(state(0x900, 0x05011234), in);
        assertEquals(0x410, result.state().destBottomBound());
        assertEquals(0x4FC, result.bounds().bottom());
        assertEquals(0x05011234, result.state().cameraY16_16());
        assertEquals(0x0100, result.state().bottomBoundShift());
    }

    @Test
    void upwardBoundaryShiftBelowTargetUsesExistingBottomWithoutReanchoring() {
        var in = withRegion(withBounds(input(0x9A0, 0x160), bounds(0, 0x2897, 0x500)), CdReleaseVariant.JAPAN);
        var result = step(state(0x900, 0x01000000), in);
        assertEquals(0x4FC, result.bounds().bottom());
        assertEquals(0x410, result.state().destBottomBound());
    }

    @ParameterizedTest
    @CsvSource({"1544,0,1556", "1544,2,1568", "1543,2,1556"})
    void downwardBoundaryShiftQuadruplesOnlyForAirAtUnsignedCameraPlusEightGate(int cameraY,
                                                                              int flags, int expected) {
        var in = withRegion(withBounds(input(0x2A0, cameraY + 0x60), bounds(0, 0x2897, 0x610)), CdReleaseVariant.JAPAN);
        in = withActor(in, new Actor(0x2A0, cameraY + 0x60, flags, 0, 0, 0));
        var result = step(state(0x200, cameraY << 16), in);
        assertEquals(expected, result.bounds().bottom());
        assertEquals(0x0100, result.state().bottomBoundShift());
    }

    @Test
    void sharedShiftUsesUnsignedBorrowAndDoesNotClampFourPixelOvershoot() {
        var signedBoundary = withRegion(withBounds(input(0x2A0, 0x160), bounds(0, 0x2897, 0x8000)), CdReleaseVariant.JAPAN);
        assertEquals(0x7FFC, step(state(0x200, 0x01000000), signedBoundary).bounds().bottom());
        var odd = withRegion(withBounds(input(0x2A0, 0x160), bounds(0, 0x2897, 0x70F)), CdReleaseVariant.JAPAN);
        assertEquals(0x713, step(state(0x200, 0x01000000), odd).bounds().bottom());
    }

    @Test
    void highByteShiftStopClearsOnlyHighByteAndTakesExactZeroFractionPath() {
        var before = withShift(state(0x200, 0x01001234), 0x017A);
        var result = step(before, input(0x2A0, 0x160));
        assertEquals(0x007A, result.state().bottomBoundShift());
        assertEquals(0x01000000, result.state().cameraY16_16());
        assertEquals(0xFFED, result.state().scrollYDiff());
        var lowOnly = step(withShift(before, 0x007A), input(0x2A0, 0x160));
        assertEquals(0x01001234, lowOnly.state().cameraY16_16());
        assertEquals(0x007A, lowOnly.state().bottomBoundShift());
    }

    @Test
    void airWindowStopAlsoClearsShiftHighByteAndEventSetsHighByteWithoutLosingLowByte() {
        var air = withActor(input(0x2A0, 0x160), new Actor(0x2A0, 0x160, 2, 0, 0, 0));
        var stopped = step(withShift(state(0x200, 0x01001234), 0x017A), air);
        assertEquals(0x007A, stopped.state().bottomBoundShift());
        assertEquals(0x01000000, stopped.state().cameraY16_16());
        var shifting = withRegion(withBounds(input(0x2A0, 0x160), bounds(0, 0x2897, 0x700)), CdReleaseVariant.JAPAN);
        assertEquals(0x017A, step(withShift(state(0x200, 0x01000000), 0x007A), shifting).state().bottomBoundShift());
    }

    @Test
    void scrollLockTestsHighByteAndPreservesEveryPriorWordBeforeAnyUnsupportedRampGate() {
        var locked = new State(0x200, 0x01001234, 0x10, 0x10, 0x1234, 0xABCD,
                0xFFFF, 0x410, 0x820, 0x017A, 0x0101);
        var in = withActor(input(0x1C16, 0x300), new Actor(0x1C16, 0x300, 2, 0, 0x2B, 2));
        var result = step(locked, in);
        assertSame(locked, result.state());
        assertSame(in.bounds(), result.bounds());
        assertEquals(in.cameraYCentre(), result.cameraYCentre());
        assertEquals(Boundary.SCROLL_LOCKED, result.boundary());
        var lowOnly = new State(0x200, 0x01001234, 0x10, 0x10, 0x1234, 0xABCD,
                0xFFFF, 0x710, 0, 0, 1);
        assertEquals(0, step(lowOnly, input(0x2A0, 0x160)).state().scrollFlags());
    }

    @Test
    void blockCrossingsStoreMemoryByteBitsInHighByteOfNativeFlagsWord() {
        var forward = withActor(input(0x100, 0x70), new Actor(0x100, 0x70, 0, 0x800, 0, 0));
        var down = step(state(0, 0), forward).state();
        assertEquals(0x10, down.cameraX());
        assertEquals(0x00100000, down.cameraY16_16());
        assertEquals(0, down.horizontalCrossed());
        assertEquals(0, down.verticalCrossed());
        assertEquals(0x0A00, down.scrollFlags());
        var reverse = withActor(input(0xA0, 0x60), new Actor(0xA0, 0x60, 0, 0x800, 0, 0));
        var up = step(new State(0x10, 0x00100000, 0, 0, 0, 0, 0, 0x710, 0, 0, 0), reverse).state();
        assertEquals(0, up.cameraX());
        assertEquals(0, up.cameraY16_16());
        assertEquals(0x10, up.horizontalCrossed());
        assertEquals(0x10, up.verticalCrossed());
        assertEquals(0x0500, up.scrollFlags());
    }

    @Test
    void horizontalEqualityStillChecksCrossingButIdleVerticalReturnDoesNot() {
        var before = new State(0x10, 0x00100000, 0x10, 0x10, 0, 0,
                0xFFFF, 0x710, 0, 0, 0);
        var result = step(before, input(0xB0, 0x70)).state();
        assertEquals(0x0800, result.scrollFlags());
        assertEquals(0, result.horizontalCrossed());
        assertEquals(0x10, result.verticalCrossed());
        var stop = step(withShift(before, 0x0100), input(0xB0, 0x70)).state();
        assertEquals(0x0A00, stop.scrollFlags());
        assertEquals(0, stop.verticalCrossed());
    }

    @Test
    void trueTopWrapRejectsAtMinus256ButMinus255CapsNormally() {
        var in = withRegion(input(0x2A0, 0xFF61), CdReleaseVariant.JAPAN);
        // Old -250, actor-camera-centre=-5 -> exact -255, ordinary top cap.
        assertEquals(0, step(state(0x200, 0xFF060000), in).state().cameraY16_16());
        var wrapping = withRegion(input(0x2A0, 0xFF60), CdReleaseVariant.JAPAN);
        var failure = assertThrows(UnsupportedScroll.class,
                () -> step(state(0x200, 0xFF060000), wrapping));
        assertEquals(UnsupportedBoundary.TOP_WRAP_ACTOR_AND_BACKGROUND, failure.boundary());
    }

    @Test
    void trueBottomWrapRejectsOnlyAt2048AfterActualBottomComparison() {
        var in = withRegion(withBounds(input(0x2A0, 0x900), bounds(0, 0x2897, 0x700)), CdReleaseVariant.JAPAN);
        assertEquals(0x07001234, step(state(0x200, 0x07F91234), in).state().cameraY16_16());
        var failure = assertThrows(UnsupportedScroll.class,
                () -> step(state(0x200, 0x07FA1234), in));
        assertEquals(UnsupportedBoundary.BOTTOM_WRAP_ACTOR_AND_BACKGROUND, failure.boundary());
        // Crossing $800 is not a wrap when the source bottom comparison bypasses ChkBottom's body.
        var lower = withBounds(in, bounds(0, 0x2897, 0x900));
        assertEquals(0x08001234, step(state(0x200, 0x07FA1234), lower).state().cameraY16_16());
    }

    @Test
    void failedWrapAfterStagedXAndUsaEventsPublishesNothingAndLeavesOldValuesImmutable() {
        var old = state(0x900, 0x07FA1234);
        var copy = copy(old);
        var in = withHud(withBounds(input(0xB00, 0x900), bounds(0, 0x2897, 0x900)), 1);
        // USA first reanchors the high bottom to even $7FA then subtracts four; Y then reaches $800.
        assertThrows(UnsupportedScroll.class, () -> step(old, in));
        assertEquals(copy, old);
        assertEquals(0, in.bounds().left());
        assertEquals(0x900, in.bounds().bottom());
        assertEquals(0x60, in.cameraYCentre());
        assertEquals(step(copy, input(0x9A0, 0x85A)), step(old, input(0x9A0, 0x85A)));
    }

    @Test
    void activeRampControlRejectsWithoutUsingAnXRangeAsAStandIn() {
        var before = state(0x200, 0x01000000);
        var in = withActor(input(0x2A0, 0x160), new Actor(0x2A0, 0x160, 0, 0, 0, 2));
        var failure = assertThrows(UnsupportedScroll.class, () -> step(before, in));
        assertEquals(UnsupportedBoundary.ACTIVE_3D_RAMP_BACKGROUND, failure.boundary());
        assertEquals(state(0x200, 0x01000000), before);
        assertDoesNotThrow(() -> step(before, input(0x1C16, 0x160)));
    }

    @Test
    void snapshotsAreExactValuesAndReplayFeedsReturnedSharedFieldsOnce() {
        var original = new State(0x830, 0x01001234, 0x10, 0x10, 0xBEEF, 0xCAFE,
                0x1234, 0x710, 7, 0x007A, 0);
        var restored = copy(original);
        var inputs = List.of(withHud(withRegion(input(0xA00, 0x180), CdReleaseVariant.JAPAN), 1),
                withHud(withRegion(input(0xA10, 0x190), CdReleaseVariant.JAPAN), 1),
                withHud(withRegion(input(0xA20, 0x1A0), CdReleaseVariant.JAPAN), 1));
        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
        var first = replay(original, inputs);
        var second = replay(restored, inputs);
        assertEquals(first, second);
        assertEquals(0x1234, original.scrollFlags());
        assertEquals(0, inputs.getFirst().bounds().left());
        assertEquals(0x820, first.getLast().bounds().left());
    }

    @Test
    void invalidWidthsHeightsAndNativeFieldWidthsRejectWithoutMutation() throws IOException {
        var old = state(0x200, 0x01001234);
        var frozen = copy(old);
        var in = input(0x2A0, 0x160);
        var plan = loadPlan(0x40, 0x76B, 0, 0x2897, 0, 0x710, 0x60);
        for (int width : new int[]{0, 319, 321, 424}) {
            assertThrows(IllegalArgumentException.class, () -> coldFromLoadPlan(plan, width, 224));
            var bad = new Input(in.actor(), in.bounds(), 0, 0xA0, 0x60, in.region(), 0, width, 224);
            assertThrows(IllegalArgumentException.class, () -> step(old, bad));
        }
        var high = new Input(in.actor(), in.bounds(), 0, 0xA0, 0x60, in.region(), 0, 320, 240);
        assertThrows(IllegalArgumentException.class, () -> step(old, high));
        assertThrows(IllegalArgumentException.class, () -> coldFromLoadPlan(plan, 320, 240));
        assertThrows(IllegalArgumentException.class, () -> new Actor(0x10000, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Actor(0, 0, 256, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Actor(0, 0, 0, 32768, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new State(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> step(old, null));
        assertThrows(NullPointerException.class, () -> step(null, in));
        assertThrows(NullPointerException.class, () -> coldFromLoadPlan(null, 320, 224));
        assertEquals(frozen, old);
        assertEquals(DEFAULT_BOUNDS, in.bounds());
    }

    private static List<Result> replay(State state, List<Input> inputs) {
        var results = new ArrayList<Result>();
        var sharedBounds = inputs.getFirst().bounds();
        int yCentre = inputs.getFirst().cameraYCentre();
        for (var in : inputs) {
            var result = step(state, new Input(in.actor(), sharedBounds, in.topBound(), in.cameraXCentre(),
                    yCentre, in.region(), in.sourceHudTimeActive(), in.viewportWidth(), in.viewportHeight()));
            results.add(result);
            state = result.state(); sharedBounds = result.bounds(); yCentre = result.cameraYCentre();
        }
        return results;
    }

    private static State state(int x, int y16_16) {
        return new State(x, y16_16, 0x10, 0x10, 0, 0, 0, 0x710, 0, 0, 0);
    }
    private static State withShift(State s, int shift) {
        return new State(s.cameraX(), s.cameraY16_16(), s.horizontalCrossed(), s.verticalCrossed(),
                s.scrollXDiff(), s.scrollYDiff(), s.scrollFlags(), s.destBottomBound(), s.destLeftBound(),
                shift, s.scrollLock());
    }
    private static State copy(State s) { return withShift(s, s.bottomBoundShift()); }
    private static CdPlayerMovement.Bounds bounds(int left, int right, int bottom) {
        return new CdPlayerMovement.Bounds(left, right, bottom, 0x42);
    }
    private static Input input(int x, int y) {
        return new Input(new Actor(x, y, 0, 0, 0, 0), DEFAULT_BOUNDS, 0,
                0xA0, 0x60, CdReleaseVariant.USA, 0, 320, 224);
    }
    private static Input withActor(Input i, Actor actor) {
        return new Input(actor, i.bounds(), i.topBound(), i.cameraXCentre(), i.cameraYCentre(),
                i.region(), i.sourceHudTimeActive(), i.viewportWidth(), i.viewportHeight());
    }
    private static Input withBounds(Input i, CdPlayerMovement.Bounds bounds) {
        return new Input(i.actor(), bounds, i.topBound(), i.cameraXCentre(), i.cameraYCentre(),
                i.region(), i.sourceHudTimeActive(), i.viewportWidth(), i.viewportHeight());
    }
    private static Input withRegion(Input i, CdReleaseVariant region) {
        return new Input(i.actor(), i.bounds(), i.topBound(), i.cameraXCentre(), i.cameraYCentre(),
                region, i.sourceHudTimeActive(), i.viewportWidth(), i.viewportHeight());
    }
    private static Input withHud(Input i, int active) {
        return new Input(i.actor(), i.bounds(), i.topBound(), i.cameraXCentre(), i.cameraYCentre(),
                i.region(), active, i.viewportWidth(), i.viewportHeight());
    }

    /** Constructed format input to the actual load-plan decoder, not authenticated media or runtime fallback. */
    private static CdR11aLoadPlan loadPlan(int x, int y, int left, int right, int top, int bottom,
                                          int centreY) throws IOException {
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        byte[] program = new byte[CdR11aProfile.FILE_BYTES];
        var bytes = ByteBuffer.wrap(program);
        bytes.position(profile.range(LEVEL_DATA_INDEX).fileOffset());
        bytes.putInt(0x0323C4EE).putInt(0x0223B0A8).putInt(0x210000).putShort((short) 0x81).putShort((short) 0x505);
        bytes.position(profile.range(LEVEL_START_LOC).fileOffset());
        bytes.putShort((short) x).putShort((short) y);
        bytes.position(profile.range(CAM_BOUNDS).fileOffset());
        for (int word : new int[]{4, left, right, top, bottom, centreY}) bytes.putShort((short) word);
        int layouts = profile.range(LEVEL_LAYOUTS).fileOffset();
        bytes.putShort(layouts, (short) (profile.range(LAYOUT_FG).fileOffset() - layouts));
        bytes.putShort(layouts + 2, (short) (profile.range(LAYOUT_BG).fileOffset() - layouts));
        int collision = profile.range(LEVEL_COL_INDEX).fileOffset();
        for (int slot = 0; slot < 8; slot++) bytes.putInt(collision + slot * 4, profile.range(LEVEL_COLLISION).nativeAddress());
        int plc = profile.range(PLC_LISTS).fileOffset();
        for (int slot = 0; slot < 19; slot++) bytes.putShort(plc + slot * 2, (short) 38);
        bytes.position(plc + 38);
        bytes.putShort((short) 0).putInt(profile.range(ART_LEVEL_TILES).nativeAddress()).putShort((short) 0);
        for (int section = 0; section < 5; section++) {
            bytes.putShort(profile.range(SECTION_RANGES).fileOffset() + section * 2,
                    (short) (section == 4 ? 0xFFFF : section + 1));
        }
        int palette = profile.range(PALETTE_TABLE).fileOffset();
        bytes.position(palette + 3 * 8);
        bytes.putInt(profile.range(PAL_SONIC).nativeAddress()).putShort((short) 0xFB00).putShort((short) 7);
        bytes.position(palette + 5 * 8);
        bytes.putInt(profile.range(PAL_LEVEL).nativeAddress()).putShort((short) 0xFB20).putShort((short) 23);
        return CdR11aLoadPlan.decode(profile, CdMmdImage.parse(program));
    }
}
