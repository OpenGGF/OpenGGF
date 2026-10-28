package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Original-USA component bindings only. These deliberately selected component
 * positions are not gameplay ingress, an actual native slot or a new Frame.
 * N1ecc5f3a Sonic/Main.asm:135-155,573-669,782-831,2500-2603;
 * Past Scroll:146-147. Literal expected writes come from those source owners.
 */
class TestCdStagePlayerBodyMovement {
    private static CdR11aAssets present;
    private static CdR11bAssets past;

    @BeforeAll
    static void loadConfiguredOriginalUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        if (root == null || root.isBlank()) return;
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)
                    .endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA directory must contain exactly one original CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            present = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStagePayload.R11A.stage());
            past = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void namedFactoriesAndOldNullCallsKeepUnambiguousRejections() {
        assertThrows(NullPointerException.class, () -> CdPlayerBody.fromStageSource(null));
        assertThrows(NullPointerException.class, () -> CdPlayerMovement.fromStageSource(null));
        assertThrows(NullPointerException.class, () -> new CdPlayerBody(null));
        assertThrows(NullPointerException.class, () -> new CdPlayerMovement(null, null));
    }

    @Test
    void originalSourceTablesRetainThePastSpecialIdsAndSharedRampBytes() {
        requireActual();
        assertArrayEquals(new byte[]{0x7F, 0x7F, 0x7F, 0x7F}, present.player().specialChunks().copy());
        assertArrayEquals(new byte[]{(byte) 0x8C, 0x7F, 0x1E, 0x1E}, past.player().specialChunks().copy());
        byte[] ramps = {6, 7, 8, 0x49, 0x4C, (byte) 0xFF};
        assertArrayEquals(ramps, present.player().rampChunks().copy());
        assertArrayEquals(ramps, past.player().rampChunks().copy());
        assertEquals(0x21F200, a().mappingAddress());
        assertEquals(0x230F60, b().mappingAddress());
        assertNotEquals(a().mappingAddress(), b().mappingAddress());
    }

    @Test
    void actualInitUsesEachLiveMappingAndOnlyTheNativeInitStores() {
        requireActual();
        checkInit(a(), 1, 0x21F200);
        checkInit(b(), 0, 0x230F60);
    }

    @Test
    void presentNamedInitMatchesTheOldConstructorWithoutMutatingIngress() {
        requireActual();
        var state = state(motion(0x3F20, 0x420, 0, 2, 29, 0), 1, 0);
        var env = bodyEnvironment(0, 1, 0, 37, 0);
        assertEquals(new CdPlayerBody(present).step(state, env), body(a()).step(state, env));
        assertEquals(0, state.motion().routine()); assertEquals(29, state.motion().charge());
        assertEquals(0, state.header().mappingAddress()); assertEquals(0x55, state.history().bytes().unsignedByte(0));
    }

    @Test
    void bodyAbsentSlotReturnsBeforeFamilyDebugOrRoutineChecksForBothSources() {
        requireActual();
        for (var source : List.of(a(), b())) {
            var state = state(motion(0x20, 0x420, 7, 2, 255, 0), 0, 0);
            var env = bodyEnvironment(0xFFFF, 0xFF, 1, 37, 0x100);
            var result = body(source).step(state, env);
            assertSame(state, result.state()); assertSame(env, result.environment());
            assertEquals(CdPlayerBody.Boundary.SLOT_ABSENT, result.boundary());
            assertTrue(result.effects().isEmpty()); assertTrue(result.display().isEmpty());
        }
    }

    @Test
    void bodyBadSlotStillPrecedesTheSourceFamilyBoundary() {
        requireActual();
        var state = state(motion(0x20, 0x420, 0, 2, 0, 0), 2, 0);
        assertBodyBoundary(CdPlayerBody.UnsupportedBoundary.SLOT_ID,
                () -> body(b()).step(state, bodyEnvironment(1, 1, 0, 0, 0x100)));
    }

    @Test
    void bodyOrdinaryFamilyUsesTheBoundRawPeriodBeforeDebugAndDispatch() {
        requireActual();
        var state = state(motion(0x20, 0x420, 7, 2, 0, 0), 1, 0);
        assertBodyBoundary(CdPlayerBody.UnsupportedBoundary.SOURCE_FAMILY,
                () -> body(b()).step(state, bodyEnvironment(0, 1, 0, 0, 0x100)));
        assertBodyBoundary(CdPlayerBody.UnsupportedBoundary.SOURCE_FAMILY,
                () -> body(a()).step(state, bodyEnvironment(0, 0, 0, 0, 0x100)));
        for (int period : new int[]{2, 0x7F, 0x83, 0xFF}) {
            assertBodyBoundary(CdPlayerBody.UnsupportedBoundary.SOURCE_FAMILY,
                    () -> body(b()).step(state, bodyEnvironment(0, period, 0, 0, 0)));
        }
        assertBodyBoundary(CdPlayerBody.UnsupportedBoundary.SOURCE_FAMILY,
                () -> body(b()).step(state, bodyEnvironment(1, 0, 0, 0, 0)));
    }

    @Test
    void bodyMarkedPeriodsRequireTheExplicitBorrowEvenForInit() {
        requireActual();
        var state = state(motion(0x20, 0x420, 0, 2, 0, 0), 1, 0);
        for (var source : List.of(a(), b())) {
            for (int period : new int[]{0x80, 0x81, 0x82}) {
                var env = bodyEnvironment(0, period, 0, 0, 0);
                assertBodyBoundary(CdPlayerBody.UnsupportedBoundary.SOURCE_FAMILY,
                        () -> body(source).step(state, env));
                var result = stepBorrowed(body(source), state, env);
                assertEquals(CdPlayerBody.Boundary.INIT_RETURN, result.boundary());
                assertEquals(source.mappingAddress(), result.state().header().mappingAddress());
                assertFalse(result.effects().stream().anyMatch(CdPlayerBody.TimeWarpCall.class::isInstance));
            }
        }
    }

    @Test
    void pastMainRejectsTheUnusedPresentMappingBeforeTimeWarp() {
        requireActual();
        var state = state(motion(0x20, 0x420, 2, 2, 17, 0x81), 1, 0x21F200);
        assertBodyBoundary(CdPlayerBody.UnsupportedBoundary.MAPPING_BINDING,
                () -> stepBorrowed(body(b()), state, bodyEnvironment(0, 0, 0, 37, 0)));
    }

    @Test
    void movementKeepsRoutineAndBoredChecksBeforeTheBoundFamily() {
        requireActual();
        var env = movementEnvironment(1, 1, 0);
        assertMotionBoundary(CdPlayerMovement.UnsupportedBoundary.ROUTINE,
                () -> movement(b()).step(motion(0x20, 0x420, 0, 2, 0, 0), neutral(), env));
        var ordinary = motion(0x20, 0x420, 2, 2, 0, 0);
        var bored = new CdPlayerMotionState(ordinary.centreX16_16(), ordinary.centreY16_16(), 0, 0, 0,
                2, 0, 2, 0x44, 9, 19, 0x2B, 0x2B, 0, 0, 0, 0, 0, 0x12, 0x34);
        assertMotionBoundary(CdPlayerMovement.UnsupportedBoundary.BORED_GIVE_UP,
                () -> movement(b()).step(bored, neutral(), env));
        assertMotionBoundary(CdPlayerMovement.UnsupportedBoundary.SOURCE_FAMILY,
                () -> movement(b()).step(ordinary, neutral(), movementEnvironment(0, 1, 0)));
        assertMotionBoundary(CdPlayerMovement.UnsupportedBoundary.SOURCE_FAMILY,
                () -> movement(a()).step(ordinary, neutral(), movementEnvironment(0, 0, 0)));
        assertMotionBoundary(CdPlayerMovement.UnsupportedBoundary.SOURCE_FAMILY,
                () -> movement(b()).stepToTimeWarp(ordinary, neutral(), movementEnvironment(0, 2, 0), Optional.empty()));
    }

    @Test
    void movementMarkedPeriodsAreOnlyAdmittedAtTheExplicitTimeWarpCut() {
        requireActual();
        var state = motion(0x3F20, 0x420, 2, 2, 0, 0);
        for (var source : List.of(a(), b())) {
            for (int period : new int[]{0x80, 0x81, 0x82}) {
                var env = movementEnvironment(0, period, 0);
                assertMotionBoundary(CdPlayerMovement.UnsupportedBoundary.SOURCE_FAMILY,
                        () -> movement(source).step(state, neutral(), env));
                var result = movement(source).stepToTimeWarp(state, neutral(), env, Optional.empty());
                assertEquals(CdPlayerMovement.Boundary.TIME_WARP, result.boundary());
                assertEquals(CdPlayerMovement.Continuation.MD_AIR_AFTER_TIME_WARP, result.continuation());
            }
        }
    }

    @Test
    void contextFreePastControlledLowXStopsBeforeChargeOrDirectionNoWriteReturns() {
        requireActual();
        for (int x : new int[]{0, 0x20, 0x8FF}) {
            for (int charge : new int[]{0, 17}) {
                for (int direction : new int[]{0, 1, 0xFF}) {
                    var state = state(motion(x, 0x420, 2, 2, charge, 0x81), 1, 0x230F60);
                    var env = bodyEnvironment(0, 0, direction, 37, 0);
                    assertBodyBoundary(CdPlayerBody.UnsupportedBoundary.TIME_WARP_STOP_RESET,
                            () -> body(b()).step(state, env));
                    assertEquals(charge, state.motion().charge()); assertEquals(37, env.globals().timeWarpTimer());
                }
            }
        }
    }

    @Test
    void contextFreePastMovementLowXStopsBeforeChargeOrDirectionNoWriteReturns() {
        requireActual();
        for (int x : new int[]{0, 0x20, 0x8FF}) {
            for (int charge : new int[]{0, 17}) {
                for (int direction : new int[]{0, 1, 0xFF}) {
                    var state = motion(x, 0x420, 2, 2, charge, 0);
                    var env = movementEnvironment(0, 0, direction);
                    assertMotionBoundary(CdPlayerMovement.UnsupportedBoundary.TIME_WARP_ACTIVE,
                            () -> movement(b()).step(state, neutral(), env));
                }
            }
        }
    }

    @Test
    void pastControlledAtTheLiteral900BoundaryCanTakeTheChargeReturn() {
        requireActual(); assertPastClearRow();
        var state = state(motion(0x900, 0x420, 2, 2, 17, 0x81), 1, 0x230F60);
        var env = bodyEnvironment(0, 0, 1, 37, 0);
        var result = body(b()).step(state, env);
        assertEquals(CdPlayerBody.Boundary.CONTROLLED_MAIN_RETURN, result.boundary());
        assertEquals(37, result.environment().globals().timeWarpTimer());
        assertEquals(1, result.environment().movement().gates().timeWarpDirection());
        assertFalse(result.effects().stream().anyMatch(CdPlayerBody.TimeWarpCall.class::isInstance));
        assertEquals(37, env.globals().timeWarpTimer()); assertEquals(17, state.motion().charge());
    }

    @Test
    void explicitPastPrefixReachesTimeWarpEvenWhenContextFreeEntryMustStop() {
        requireActual();
        var state = motion(0x20, 0x420, 2, 2, 17, 0);
        var env = movementEnvironment(0, 0, 0);
        var result = movement(b()).stepToTimeWarp(state, neutral(), env, Optional.empty());
        assertEquals(CdPlayerMovement.Boundary.TIME_WARP, result.boundary());
        assertEquals(CdPlayerMovement.Continuation.MD_AIR_AFTER_TIME_WARP, result.continuation());
        assertEquals(17, result.state().charge()); assertEquals(0x00200000, result.state().centreX16_16());
        assertEquals(0x04200000, result.state().centreY16_16()); assertTrue(result.effects().isEmpty());
    }

    @Test
    void fullBorrowedPastControlledHelperClearsAllThreeScalarsBeforeChargeAndDirection() {
        requireActual(); assertPastClearRow();
        for (int charge : new int[]{0, 17}) {
            for (int direction : new int[]{0, 1, 0xFF}) {
                var state = state(motion(0x20, 0x420, 2, 2, charge, 0x81), 1, 0x230F60);
                var env = bodyEnvironment(0, 0, direction, 37, 0);
                var result = stepBorrowed(body(b()), state, env);
                assertEquals(CdPlayerBody.Boundary.CONTROLLED_MAIN_RETURN, result.boundary());
                assertLowXWarp(result, direction);
                assertEquals(0x00200000, result.state().motion().centreX16_16());
                assertEquals(0x04200000, result.state().motion().centreY16_16());
                assertEquals(37, env.globals().timeWarpTimer()); assertEquals(charge, state.motion().charge());
                assertEquals(0x55, state.history().bytes().unsignedByte(0));
            }
        }
    }

    @Test
    void fullBorrowedPastOrdinaryModeResumesOnceAfterTheSharedLowXHelper() {
        requireActual(); assertPastClearRow();
        var state = state(motion(0x20, 0x420, 2, 2, 17, 0x80), 1, 0x230F60);
        var result = stepBorrowed(body(b()), state, bodyEnvironment(0, 0, 0, 37, 0));
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, result.boundary());
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, result.modeBoundary().orElseThrow());
        assertLowXWarp(result, 0);
        assertEquals(18, result.state().motion().charge(), "one predispatch, never a second Movement increment");
        assertEquals(0x38, result.state().motion().yVelocity());
        assertEquals(0x04200000, result.state().motion().centreY16_16());
        assertEquals(4, result.state().history().index());
        assertEquals(1, calls(result, CdPlayerBody.NativeCall.MODE));
        assertEquals(1, calls(result, CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH));
        assertEquals(17, state.motion().charge()); assertEquals(0, state.history().index());
    }

    @Test
    void borrowedPastAt900RetainsTheNoWriteChargeReturnAndContactValue() {
        requireActual(); assertPastClearRow();
        var state = state(motion(0x900, 0x420, 2, 2, 17, 0x81), 1, 0x230F60);
        var result = stepBorrowed(body(b()), state, bodyEnvironment(0, 0, 1, 37, 0));
        var warp = onlyWarp(result);
        assertEquals(new CdPlayerTimeWarp.State(37, 1, 7), warp.state());
        assertTrue(warp.stores().isEmpty()); assertTrue(warp.reservedPatches().isEmpty());
        assertTrue(warp.saveResult().isEmpty()); assertTrue(warp.restartResult().isEmpty());
    }

    @Test
    void borrowedMarkedPeriodDoesNotApplyTheRawPastLowXReset() {
        requireActual(); assertPastClearRow();
        for (int period : new int[]{0x80, 0x81, 0x82}) {
            var state = state(motion(0x20, 0x420, 2, 2, 17, 0x81), 1, 0x230F60);
            var result = stepBorrowed(body(b()), state, bodyEnvironment(0, period, 1, 37, 0));
            assertEquals(new CdPlayerTimeWarp.State(37, 1, 7), onlyWarp(result).state());
            assertTrue(onlyWarp(result).stores().isEmpty());
            assertEquals(period, result.environment().movement().gates().timeZone());
        }
    }

    @Test
    void reachedMissingReservedOwnerKeepsTheRealHelperBoundary() {
        requireActual(); assertPastClearRow();
        var m = motion(0x900, 0x420, 2, 2, 0, 0x81);
        m = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), 0, 0, 0x600, 2, 0, 2, 0x44,
                9, 19, 0, 0, 0, 0, 0, 0, 0x81, 0x12, 0x34);
        var state = state(m, 1, 0x230F60);
        var failure = assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,
                () -> stepBorrowed(body(b()), state, bodyEnvironment(0, 0, 1, 1, 0)));
        assertEquals(CdPlayerTimeWarp.UnsupportedBoundary.RESERVED_TIME_STAR_READ, failure.boundary());
        assertEquals(new CdPlayerTimeWarp.State(1, 1, 7), failure.prefix().state());
        assertTrue(failure.prefix().stores().isEmpty());
        assertEquals(0x55, state.history().bytes().unsignedByte(0));
    }

    @Test
    void actualPastDynamicsStillStopAtReachedTouchInsteadOfAnEmptyFallback() {
        requireActual(); assertPastClearRow();
        var state = state(motion(0x20, 0x420, 2, 2, 17, 1), 1, 0x230F60);
        assertBodyBoundary(CdPlayerBody.UnsupportedBoundary.TOUCH_SCAN,
                () -> stepBorrowed(body(b()), state, bodyEnvironment(0, 0, 0, 37, 0)));
        assertEquals(0, state.history().index()); assertEquals(0x55, state.history().bytes().unsignedByte(0));
    }

    @Test
    void actualPastProbesUseTheClearedFourthRowWithoutInventingAngleWrites() {
        requireActual(); assertPastClearRow();
        byte[] chunks = past.terrain().chunks().copy();
        byte[] ids = past.terrain().collision().ids().copy();
        var m = motion(0x20, 0x420, 2, 2, 0, 0);
        var owner = movement(b());
        assertEquals(new CdPlayerMovement.ProbeResult(28, OptionalInt.empty(), OptionalInt.empty()), owner.checkFloor(m));
        assertEquals(new CdPlayerMovement.ProbeResult(21, OptionalInt.of(0), OptionalInt.of(0)),
                owner.calcRoomInFront(m, 0));
        assertEquals(new CdPlayerMovement.ProbeResult(22, OptionalInt.empty(), OptionalInt.empty()), owner.getCeilDist(m));
        assertArrayEquals(chunks, past.terrain().chunks().copy());
        assertArrayEquals(ids, past.terrain().collision().ids().copy());
        assertEquals(0x00200000, m.centreX16_16()); assertEquals(0x04200000, m.centreY16_16());
        assertEquals(0x12, m.previousPrimaryAngle()); assertEquals(0x34, m.previousSecondaryAngle());
    }

    @Test
    void nativeRampGatePreservesPastAndMarkedControlAndVelocityBeforeTheWarpCut() {
        requireActual();
        // Column63 lies beyond both copied FG widths. It is explicit cleared component terrain,
        // not a searched or fitted physical player position. The tested branch must not read it.
        var m = new CdPlayerMotionState(0x3F200000, 0x04200000, 0x123, -0x900, 0,
                2, 0, 2, 0x44, 9, 19, 0, 0, 0, 0, 0, 0, 2, 0x12, 0x34);
        for (var source : List.of(a(), b())) {
            for (int period : new int[]{0x80, 0x81, 0x82}) {
                var r = movement(source).stepToTimeWarp(m, neutral(), movementEnvironment(0, period, 0), Optional.empty());
                assertEquals(2, r.state().playerControl()); assertEquals(0x123, r.state().xVelocity());
                assertEquals(-0x900, r.state().yVelocity()); assertTrue(r.effects().isEmpty());
            }
        }
        var pastResult = movement(b()).stepToTimeWarp(m, neutral(), movementEnvironment(0, 0, 0), Optional.empty());
        assertEquals(2, pastResult.state().playerControl()); assertEquals(0x123, pastResult.state().xVelocity());
        assertEquals(-0x900, pastResult.state().yVelocity());
    }

    @Test
    void ordinaryPresentRampExitKeepsTheOldNativeBoostAndNamedEquivalence() {
        requireActual();
        assertEquals(0, a().terrain().layout().ram().unsignedByte(0x23F));
        var m = new CdPlayerMotionState(0x3F200000, 0x04200000, 0x123, -0x900, 0,
                2, 0, 2, 0x44, 9, 19, 0, 0, 0, 0, 0, 0, 2, 0x12, 0x34);
        var env = movementEnvironment(0, 1, 0);
        var result = movement(a()).stepToTimeWarp(m, neutral(), env, Optional.empty());
        assertEquals(0, result.state().playerControl()); assertEquals(0x600, result.state().xVelocity());
        assertEquals(-0x900, result.state().yVelocity());
        assertEquals(new CdPlayerMovement(present.terrain(), present.player())
                .stepToTimeWarp(m, neutral(), env, Optional.empty()), result);
        assertEquals(2, m.playerControl()); assertEquals(0x123, m.xVelocity());
    }

    @Test
    void ordinaryPresentAirModeKeepsLiteralMotionAndTheOldResult() {
        requireActual();
        var m = motion(0x3F20, 0x420, 2, 2, 0, 0);
        var env = movementEnvironment(0, 1, 0);
        var result = movement(a()).step(m, neutral(), env);
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, result.boundary());
        assertEquals(0x3F200000, result.state().centreX16_16());
        assertEquals(0x04200000, result.state().centreY16_16()); assertEquals(0x38, result.state().yVelocity());
        assertEquals(new CdPlayerMovement(present.terrain(), present.player()).step(m, neutral(), env), result);
    }

    private static void checkInit(CdStagePlayerSource source, int period, int map) {
        var m = new CdPlayerMotionState(0x00201234, 0x04205678, -0x123, 0x234, -0x345,
                0, 0x24, 1, 0xC3, 3, 4, 5, 6, 1, 29, -7, 8, 0x12, 0x12, 0x34);
        var state = state(m, 1, 0x11223344); var env = bodyEnvironment(0, period, 0, 37, 0);
        var result = body(source).step(state, env);
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, result.boundary());
        assertEquals(new CdPlayerBody.Header(1, map, 0x780, 2, 0x18), result.state().header());
        assertEquals(new CdPlayerMotionState(0x00201234, 0x04205678, -0x123, 0x234, -0x345,
                2, 0x24, 1, 4, 9, 19, 5, 6, 1, 30, -7, 8, 0x12, 0x12, 0x34), result.state().motion());
        assertEquals(new CdPlayerMovement.Physics(0x600, 0xC, 0x80), result.environment().movement().physics());
        assertEquals(state.animation(), result.state().animation()); assertEquals(state.timers(), result.state().timers());
        assertEquals(state.history(), result.state().history()); assertEquals(state.objectLinks(), result.state().objectLinks());
        assertEquals(0x39, result.state().rotationDistance()); assertEquals(0x2B, result.state().rotationAngle());
        assertEquals(env.controls(), result.environment().controls()); assertEquals(env.globals(), result.environment().globals());
        assertEquals(List.of(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH, CdPlayerBody.NativeCall.INIT),
                result.effects().stream().filter(CdPlayerBody.CallReturned.class::isInstance)
                        .map(CdPlayerBody.CallReturned.class::cast).map(CdPlayerBody.CallReturned::call).toList());
        assertTrue(result.display().isEmpty()); assertTrue(result.modeBoundary().isEmpty());
        assertEquals(29, state.motion().charge()); assertEquals(0x11223344, state.header().mappingAddress());
    }

    private static void assertLowXWarp(CdPlayerBody.Result result, int direction) {
        var warp = onlyWarp(result);
        assertEquals(new CdPlayerTimeWarp.State(0, 0, 0), warp.state());
        assertEquals(List.of(new CdPlayerTimeWarp.GlobalWrite(CdPlayerTimeWarp.GlobalField.TIMER_WORD, 37, 0),
                new CdPlayerTimeWarp.GlobalWrite(CdPlayerTimeWarp.GlobalField.DIRECTION_BYTE, direction, 0),
                new CdPlayerTimeWarp.GlobalWrite(CdPlayerTimeWarp.GlobalField.TIME_WARP_BYTE, 7, 0)), warp.stores());
        assertTrue(warp.reservedPatches().isEmpty()); assertTrue(warp.saveResult().isEmpty());
        assertTrue(warp.restartResult().isEmpty()); assertEquals(0, result.environment().globals().timeWarpTimer());
        assertEquals(0, result.environment().movement().gates().timeWarpDirection());
    }

    private static CdPlayerTimeWarp.Result onlyWarp(CdPlayerBody.Result result) {
        var values = result.effects().stream().filter(CdPlayerBody.TimeWarpCall.class::isInstance)
                .map(CdPlayerBody.TimeWarpCall.class::cast).toList();
        assertEquals(1, values.size()); return values.getFirst().result();
    }

    private static long calls(CdPlayerBody.Result result, CdPlayerBody.NativeCall call) {
        return result.effects().stream().filter(e -> e instanceof CdPlayerBody.CallReturned c && c.call() == call).count();
    }

    private static CdPlayerBody.Result stepBorrowed(CdPlayerBody owner, CdPlayerBody.State state,
                                                    CdPlayerBody.Environment env) {
        var context = new CdPlayerBody.TimeWarpContext(new CdPlayerTimeWarp.Context(OptionalInt.empty(), Optional.empty()),
                new CdPlayerBody.ContactGlobals(7, 5, 3));
        return owner.step(state, env, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(context));
    }

    private static void assertBodyBoundary(CdPlayerBody.UnsupportedBoundary boundary,
                                           org.junit.jupiter.api.function.Executable action) {
        assertEquals(boundary, assertThrows(CdPlayerBody.UnsupportedBody.class, action).boundary());
    }

    private static void assertMotionBoundary(CdPlayerMovement.UnsupportedBoundary boundary,
                                             org.junit.jupiter.api.function.Executable action) {
        assertEquals(boundary, assertThrows(CdPlayerMovement.UnsupportedMotion.class, action).boundary());
    }

    private static void assertPastClearRow() {
        // LoadLevelLayout clears all $800 native bytes, then copies only four Past FG rows.
        // These fixed component inputs intentionally use row4. They establish no actual ingress.
        for (int column = 0; column < 64; column++)
            assertEquals(0, past.terrain().layout().ram().unsignedByte(0x200 + column));
    }

    private static void requireActual() { assumeTrue(present != null && past != null, "Set soniccd.media.root to original media"); }
    private static CdStagePlayerSource a() { return CdStagePlayerSource.fromPresent(present); }
    private static CdStagePlayerSource b() { return CdStagePlayerSource.fromPast(past); }
    private static CdPlayerBody body(CdStagePlayerSource source) { return CdPlayerBody.fromStageSource(source); }
    private static CdPlayerMovement movement(CdStagePlayerSource source) { return CdPlayerMovement.fromStageSource(source); }
    private static CdPlayerMovement.Input neutral() { return new CdPlayerMovement.Input(0, 0); }

    private static CdPlayerMotionState motion(int x, int y, int routine, int flags, int charge, int control) {
        return new CdPlayerMotionState(x << 16, y << 16, 0, 0, 0, routine, 0, flags, 0x44,
                9, 19, 0, 0, 0, charge, 0, 0, control, 0x12, 0x34);
    }

    private static CdPlayerBody.State state(CdPlayerMotionState motion, int slotId, int map) {
        byte[] history = new byte[0x100]; java.util.Arrays.fill(history, (byte) 0x55);
        return new CdPlayerBody.State(motion, new CdPlayerBody.AnimationCursor(0, 1, 0),
                new CdPlayerBody.Header(slotId, map, 0x780, 2, 0x18), new CdPlayerBody.Timers(120, 7, 9, 11),
                new CdPlayerBody.History(CdAssetBytes.copyOf(history), 0), 0x39, new CdPlayerBody.ObjectLinks(0xA1, 0xFE), 0x2B);
    }

    private static CdPlayerMovement.Environment movementEnvironment(int zoneAct, int period, int direction) {
        return new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600, 0xC, 0x80),
                new CdPlayerMovement.Bounds(0, 0x7FFF, 0x700, 0),
                new CdPlayerMovement.Gates(zoneAct, period, 0, 0, 0, 0, direction),
                new CdPlayerMovement.Globals(0, 0x60, 0x12, 0x34, 0, 0, 0, 0, 0, 0));
    }

    private static CdPlayerBody.Environment bodyEnvironment(int zoneAct, int period, int direction, int timer, int debug) {
        return new CdPlayerBody.Environment(movementEnvironment(zoneAct, period, direction),
                new CdPlayerBody.Controls(0, 0, 0, 0, 0), new CdPlayerBody.Globals(1, 160, 0, debug, 0, timer, 0, 0),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }

    // N1ecc5f3a Sonic:395-435, Main:555-578. Source music requests only, no playback.
    @Test
    void pastInvincibilityExpiryFadesBeforeItsActualPastMusicRequest() {
        requireActual(); assertPastClearRow();
        var result = expiryResult(b(), 0, 0, 1, 0, 0, 1, 9);
        assertEquals(List.of("SCMD_FADECDA", "SCMD_PASTMUS"), musicNames(result));
        assertEquals(0, result.state().timers().invincibility());
        assertEquals(0, result.environment().globals().invincible());
        assertEquals(0, result.environment().movement().gates().timeZone());
    }

    @Test
    void pastShoesExpiryRestoresPhysicsThenFadesAndRequestsPastMusic() {
        requireActual(); assertPastClearRow();
        var result = expiryResult(b(), 0, 0, 0, 1, 0, 9, 1);
        assertEquals(List.of("SCMD_FADECDA", "SCMD_PASTMUS"), musicNames(result));
        assertEquals(new CdPlayerMovement.Physics(0x600, 0xC, 0x80), result.environment().movement().physics());
        assertEquals(0, result.state().timers().speedShoes());
        assertEquals(0, result.environment().movement().gates().speedShoes());
    }

    @Test
    void simultaneousExpiryAndBossOrOtherPowerupGatesKeepNativeMusicOrder() {
        requireActual(); assertPastClearRow();
        assertEquals(List.of("SCMD_FADECDA", "SCMD_PASTMUS"),
                musicNames(expiryResult(b(), 0, 0, 1, 1, 0, 1, 1)));
        assertTrue(musicNames(expiryResult(b(), 0, 0, 1, 0, 1, 1, 9)).isEmpty());
        assertTrue(musicNames(expiryResult(b(), 0, 0, 1, 1, 0, 2, 1)).isEmpty());
        assertTrue(musicNames(expiryResult(b(), 0, 0, 1, 1, 0, 1, 2)).isEmpty());
    }

    @Test
    void markedMusicUsesTheRawMarkerForFadeAndMaskedPeriodForSelection() {
        requireActual(); assertPastClearRow();
        for (var source : List.of(a(), b())) {
            assertEquals(List.of("SCMD_PASTMUS"), musicNames(expiryResult(source, 0x80, 0, 1, 0, 0, 1, 9)));
            assertEquals(List.of("SCMD_R1AMUS"), musicNames(expiryResult(source, 0x81, 0, 1, 0, 0, 1, 9)));
        }
        assertEquals(List.of("SCMD_R1AMUS"), musicNames(expiryResult(a(), 1, 0, 1, 0, 0, 1, 9)));
    }

    @Test
    void markedFutureMusicStopsBeforeItsUnownedGoodFutureRead() {
        requireActual(); assertPastClearRow();
        var failure = assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> expiryResult(b(), 0x82, 0, 1, 0, 0, 1, 9));
        assertEquals("LEVEL_MUSIC_GOOD_FUTURE", failure.boundary().name());
    }

    @Test
    void timeAttackFutureMusicSkipsGoodFutureAndUsesItsLiteralTableEntry() {
        requireActual(); assertPastClearRow();
        assertEquals(List.of("SCMD_R1DMUS"), musicNames(expiryResult(b(), 0x82, 1, 1, 0, 0, 1, 9)));
    }

    private static CdPlayerBody.Result expiryResult(CdStagePlayerSource source, int period, int timeAttack,
                                                     int invincible, int shoes, int boss,
                                                     int invincibilityTimer, int shoesTimer) {
        var original = state(motion(0x1000, 0x420, 2, 2, 17, 0x81), 1, source.mappingAddress());
        var input = new CdPlayerBody.State(original.motion(), original.animation(), original.header(),
                new CdPlayerBody.Timers(7, invincibilityTimer, shoesTimer, 11), original.history(),
                original.rotationDistance(), original.objectLinks(), original.rotationAngle());
        var before = bodyEnvironment(0, period, 0, 37, 0);
        var m = before.movement(); var g = m.gates();
        var env = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(
                new CdPlayerMovement.Physics(0x900, 0x18, 0xC0), m.bounds(),
                new CdPlayerMovement.Gates(g.zoneAct(), g.timeZone(), g.miniSonic(), g.waterSlide(),
                        g.windTunnel(), shoes, g.timeWarpDirection()), m.globals()), before.controls(),
                new CdPlayerBody.Globals(1, 160, timeAttack, 0, 0, 37, invincible, boss), before.dynamicSlots());
        var result = stepBorrowed(body(source), input, env);
        assertEquals(CdPlayerBody.Boundary.CONTROLLED_MAIN_RETURN, result.boundary());
        assertEquals(0x10000000, result.state().motion().centreX16_16());
        assertEquals(0x04200000, result.state().motion().centreY16_16());
        assertEquals(invincibilityTimer, input.timers().invincibility());
        assertEquals(shoesTimer, input.timers().speedShoes());
        assertEquals(invincible, env.globals().invincible());
        assertEquals(shoes, env.movement().gates().speedShoes());
        return result;
    }

    private static List<String> musicNames(CdPlayerBody.Result result) {
        return result.effects().stream().filter(CdPlayerBody.SoundRequest.class::isInstance)
                .map(CdPlayerBody.SoundRequest.class::cast).map(request -> request.sound().name()).toList();
    }


    // MoveAir Sonic1566-1579 gates BOTH restrictions on raw1/zone0, not the loaded program name.
    @Test
    void pastAirSteeringDoesNotBorrowThePresentLaunchWindow() {
        requireActual(); assertPastClearRow();
        for (int x : new int[]{0x6C7, 0x6C8, 0x700, 0x83F, 0x840}) {
            var result = steeringResult(0, x, 0);
            assertEquals(0x18, result.state().motion().xVelocity(), "native acceleration C doubled at MoveAir");
            assertEquals((x << 16) + 0x1800, result.state().motion().centreX16_16());
        }
    }

    @Test
    void pastAirSteeringIgnoresThePresentOnlyRampControlSuppression() {
        requireActual(); assertPastClearRow();
        var result = steeringResult(0, 0x1000, 2);
        assertEquals(0x18, result.state().motion().xVelocity());
        assertEquals(2, result.state().motion().playerControl() & 2, "Past Handle3DRamp is a no-write return");
    }

    @Test
    void markedAirSteeringSkipsBothPresentOnlyChecks() {
        requireActual(); assertPastClearRow();
        for (int period : new int[]{0x80, 0x81, 0x82}) {
            var result = steeringResult(period, 0x700, 2);
            assertEquals(0x18, result.state().motion().xVelocity());
            assertEquals(2, result.state().motion().playerControl() & 2);
            assertEquals(period, result.environment().movement().gates().timeZone());
        }
    }

    private static CdPlayerBody.Result steeringResult(int period, int x, int control) {
        var input = state(motion(x, 0x420, 2, 2, 17, control | 0x80), 1, 0x230F60);
        var before = bodyEnvironment(0, period, 0, 37, 0);
        var env = new CdPlayerBody.Environment(before.movement(),
                new CdPlayerBody.Controls(8, 0, 0, 0, 0), before.globals(), before.dynamicSlots());
        var result = stepBorrowed(body(b()), input, env); // Actual shared TimeWarp, not a forged continuation.
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, result.boundary());
        assertEquals(0, input.motion().xVelocity()); assertEquals(x << 16, input.motion().centreX16_16());
        assertEquals(0, env.controls().logicalHeld());
        assertEquals(8, result.environment().controls().logicalHeld());
        return result;
    }

}
