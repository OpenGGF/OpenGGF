package com.openggf.game.soniccd.player;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fixed component writes from N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Level/Main.asm:438-447 (selected nonzero spawn), 278-281, 295-300, 315-316.
 * Complete explicit values and original configured USA products are test inputs, not
 * certificates of native entry, phase, spawn, saved stores, player Init or gameplay.
 * The separate native timeWarp BYTE is external; timeWarpTimer WORD is a nonwrite.
 */
class TestCdLevelStartPlayerWrites {
    private static CdR11aAssets present;
    private static CdR11bAssets past;
    private static final Runnable NOTHING = () -> { };
    private static final List<Consumer<CdPlayerFrameRuntime>> WRITERS = List.of(
            CdPlayerFrameRuntime::applyLevelStartWarpPlayer,
            CdPlayerFrameRuntime::clearLevelStartControlsAndBoredTimer,
            CdPlayerFrameRuntime::clearLevelStartPlayerGlobals,
            f -> { f.clearLevelStartTimeZoneMarker(); });

    @BeforeAll
    static void loadConfiguredOriginalUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        if (root == null || root.isBlank()) return;
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString()
                    .toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA directory must contain exactly one original CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            present = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStagePayload.R11A.stage());
            past = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void loadPlayerChangesOnlyIdAndNonzeroSpawnHurtWordWithoutInitOrArt() {
        requireActual(); var f = dirtyPast(0x80); var before = cut(f);
        f.bindArtPublication((request, value) -> fail("Fixed LoadPlayer has no art service"));
        f.applyLevelStartWarpPlayer();
        assertEquals(player(1, 0x0078), f.state());
        assertSame(before.actor().state().motion(), f.state().motion());
        assertSame(before.actor().state().animation(), f.state().animation());
        assertSame(before.actor().state().history(), f.state().history());
        assertSame(before.actor().state().objectLinks(), f.state().objectLinks());
        assertSame(before.shared().environment(), f.environment());
        assertUnwrittenOwners(f, before);
        // Routine, mapping, tile, radii, flags, fractions, saved velocities and other timers stay dirty.
        assertEquals(0x12, f.state().motion().routine());
        assertEquals(0x12345678, f.state().header().mappingAddress());
        assertEquals(new CdPlayerBody.Timers(0x0078, 0xBCDE, 0xCDEF, 0xDEF0), f.state().timers());
        assertThrows(IllegalStateException.class, () -> f.runBody(0x9999));
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE));
        assertEquals(player(1, 0x0078), f.state()); assertUnwrittenOwners(f, before);
    }

    @Test
    void skipTitleCardClearsBothControlPairsAndPrimaryBoredWordOnly() {
        requireActual(); var f = dirtyPast(0x80); var before = cut(f);
        f.bindArtPublication((request, value) -> fail("Control clear cannot prime art"));
        f.clearLevelStartControlsAndBoredTimer();
        assertEquals(afterControlClear(0x80), f.environment());
        assertEquals(before.actor(), f.captureActor());
        assertSame(before.shared().environment().movement().physics(), f.environment().movement().physics());
        assertSame(before.shared().environment().movement().bounds(), f.environment().movement().bounds());
        assertSame(before.shared().environment().movement().gates(), f.environment().movement().gates());
        assertSame(before.shared().environment().globals(), f.environment().globals());
        assertEquals(0xA5, f.environment().controls().controlLocked());
        assertUnwrittenOwners(f, before);
        assertThrows(IllegalStateException.class, () -> f.runBody(0x9999));
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE));
        assertEquals(afterControlClear(0x80), f.environment()); assertUnwrittenOwners(f, before);
    }

    @Test
    void skipClearTouchesFourRepresentedGlobalsAndPreservesTimeWarpTimerWord() {
        requireActual(); var f = dirtyPast(0x80); var before = cut(f);
        f.clearLevelStartPlayerGlobals();
        assertEquals(afterPlayerGlobalsClear(0x80), f.environment());
        assertEquals(before.actor(), f.captureActor());
        assertSame(before.shared().environment().controls(), f.environment().controls());
        assertSame(before.shared().environment().movement().physics(), f.environment().movement().physics());
        assertSame(before.shared().environment().movement().bounds(), f.environment().movement().bounds());
        assertSame(before.shared().environment().movement().globals(), f.environment().movement().globals());
        assertEquals(0xABCD, f.environment().globals().timeWarpTimer());
        assertEquals(new CdPlayerBody.Timers(0xABCD, 0xBCDE, 0xCDEF, 0xDEF0), f.state().timers());
        assertUnwrittenOwners(f, before);
        // Native timeWarp BYTE belongs to ContactGlobals, not this Frame or this timer WORD.
        var external = new CdPlayerBody.ContactGlobals(0xE1, 0xD2, 0xC3);
        assertEquals(new CdPlayerBody.ContactGlobals(0xE1, 0xD2, 0xC3), external);
    }

    @Test
    void markerBclrReturnsOldBitAndPreservesRawLowBitsAndBoundProgram() {
        requireActual(); int[][] marked = {{0x80, 0}, {0x81, 1}, {0x82, 2}};
        for (boolean isPast : new boolean[]{false, true}) {
            for (var c : marked) {
                var f = isPast ? dirtyPast(c[0]) : dirtyPresent(c[0]); var before = cut(f);
                assertTrue(f.clearLevelStartTimeZoneMarker());
                assertEquals(dirtyEnvironment(0, c[1]), f.environment());
                assertEquals(before.actor(), f.captureActor()); assertUnwrittenOwners(f, before);
                assertSame(before.source(), f.stageSource());
                assertEquals(isPast ? 0 : 1, f.stageSource().ordinaryPeriod());
                if (c[1] != (isPast ? 0 : 1)) {
                    var cleared = cut(f);
                    assertSourceRejected(() -> f.serviceLevelLoad(0, TestCdLevelStartPlayerWrites::unreached,
                            TestCdLevelStartPlayerWrites::unreached, TestCdLevelStartPlayerWrites::unreached));
                    assertUnchanged(f, cleared);
                    for (var writer : WRITERS) {
                        assertSourceRejected(() -> writer.accept(f)); assertUnchanged(f, cleared);
                    }
                } else {
                    var cleared = cut(f); assertFalse(f.clearLevelStartTimeZoneMarker());
                    assertEquals(cleared.actor(), f.captureActor()); assertEquals(cleared.shared(), f.captureShared());
                    assertNotSame(cleared.shared().environment(), f.environment());
                }
            }
            var f = isPast ? dirtyPast(0) : dirtyPresent(1); var before = cut(f);
            assertFalse(f.clearLevelStartTimeZoneMarker());
            assertEquals(before.actor(), f.captureActor()); assertEquals(before.shared(), f.captureShared());
            assertNotSame(before.shared().environment(), f.environment()); assertUnwrittenOwners(f, before);
        }
    }

    @Test
    void allWritersUseTheExistingSourceFamilyGuardBeforeMutation() {
        requireActual();
        for (boolean isPast : new boolean[]{false, true}) {
            for (int period : new int[]{isPast ? 0 : 1, 0x80, 0x81, 0x82}) {
                for (var writer : WRITERS) {
                    var f = isPast ? dirtyPast(period) : dirtyPresent(period);
                    var source = f.stageSource(); writer.accept(f); assertSame(source, f.stageSource());
                    assertFalse(f.closed());
                }
            }
            for (int period : new int[]{isPast ? 1 : 0, 2, 0x7F, 0x83, 0xFF}) {
                for (var writer : WRITERS) {
                    var f = isPast ? dirtyPast(period) : dirtyPresent(period); var before = cut(f);
                    assertSourceRejected(() -> writer.accept(f)); assertUnchanged(f, before);
                }
            }
            for (var writer : WRITERS) {
                var f = isPast ? dirtyPast(0x80) : dirtyPresent(0x80); var s = f.captureShared();
                f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), dirtyEnvironment(0x0100, 0x80),
                        s.artState(), s.paused(), s.vIntClock()));
                var before = cut(f); assertSourceRejected(() -> writer.accept(f)); assertUnchanged(f, before);
            }
        }
    }

    @Test
    void sameValueNativeStoresStillProduceCompleteEqualValues() {
        requireActual();
        for (int index = 0; index < WRITERS.size(); index++) {
            var f = dirtyPast(0x80); var writer = WRITERS.get(index); writer.accept(f); var before = cut(f);
            writer.accept(f);
            assertEquals(before.actor(), f.captureActor()); assertEquals(before.shared(), f.captureShared());
            if (index == 0) assertNotSame(before.actor().state(), f.state());
            else assertNotSame(before.shared().environment(), f.environment());
            assertUnwrittenOwners(f, before);
        }
    }

    @Test
    void sameOwnerRestoreReplaysValuesWithoutChangingSourceClockArtOrCaptureOwner() {
        requireActual();
        for (var writer : WRITERS) {
            var f = dirtyPast(0x80); var before = cut(f); writer.accept(f); var after = cut(f);
            f.restoreActor(before.actor()); f.restoreShared(before.shared());
            assertUnchanged(f, before); writer.accept(f);
            assertEquals(after.actor(), f.captureActor()); assertEquals(after.shared(), f.captureShared());
            assertSame(before.source(), f.stageSource());
            var other = dirtyPast(0x80); var foreignBefore = cut(other);
            assertThrows(IllegalArgumentException.class, () -> other.restoreActor(after.actor()));
            assertThrows(IllegalArgumentException.class, () -> other.restoreShared(after.shared()));
            assertUnchanged(other, foreignBefore);
        }
    }

    @Test
    void pendingInputRejectsEveryWriterBeforeSourceCheckAndIsNotConsumed() {
        requireActual();
        for (var writer : WRITERS) {
            var f = dirtyPast(0x80); var before = cut(f);
            f.bindArtPublication((request, value) -> fail("Lag does not service Sonic art"));
            f.prepareInput(0x81);
            assertThrows(IllegalStateException.class, () -> writer.accept(f)); assertComponentsUnchanged(f, before);
            f.serviceVBlank(PlcLifecyclePhase.LAG); // Requires the still-pending original sample.
            assertSame(before.actor().state(), f.state()); assertSame(before.shared().environment(), f.environment());
            assertSame(before.shared().artState(), f.artState()); assertEquals(before.actor().display(), f.display());
            assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xF0123456L), 0x89ABCDF0L), f.vIntClock());
            assertTrue(f.paused()); assertFalse(f.closed());
        }
        var wrong = dirtyPast(1); var before = cut(wrong); wrong.prepareInput(0);
        for (var writer : WRITERS) {
            assertThrows(IllegalStateException.class, () -> writer.accept(wrong)); assertComponentsUnchanged(wrong, before);
        }
        wrong.clearPendingInput(); assertUnchanged(wrong, before);
    }

    @Test
    void unconsumedBodyEntitlementRejectsEveryWriterWithoutChangingValues() {
        requireActual(); var f = dirtyPast(0x80); var a = f.captureActor(); var s = f.captureShared();
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(a.owner(), player(0, 0xABCD), a.display()));
        f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), s.environment(), s.artState(), false, s.vIntClock()));
        f.bindArtPublication((request, value) -> { });
        var selection = f.prepareInput(0x24); f.serviceVBlank(selection.phase());
        var state = f.state(); var env = f.environment(); var art = f.artState(); var clock = f.vIntClock();
        var display = f.display(); var source = f.stageSource();
        for (var writer : WRITERS) {
            assertThrows(IllegalStateException.class, () -> writer.accept(f));
            assertSame(state, f.state()); assertSame(env, f.environment()); assertSame(art, f.artState());
            assertSame(clock, f.vIntClock()); assertSame(display, f.display()); assertSame(source, f.stageSource());
            assertFalse(f.paused()); assertFalse(f.closed());
        }
        assertThrows(IllegalStateException.class, f::captureActor);
        f.clearPendingInput(); assertSame(state, f.captureActor().state()); assertSame(env, f.captureShared().environment());
    }

    @Test
    void closedAndCaughtReentrantGuardsLeaveEveryComponentUntouched() {
        requireActual();
        for (var writer : WRITERS) {
            var f = dirtyPast(1); var before = cut(f); f.close();
            assertThrows(IllegalStateException.class, () -> writer.accept(f));
            assertComponentsUnchanged(f, before); assertTrue(f.closed());
        }
        var f = dirtyPast(0x80); var before = cut(f); int[] cuts = {0};
        f.serviceLevelLoad(0x24, () -> {
            cuts[0]++; var state = f.state(); var env = f.environment(); var art = f.artState(); var clock = f.vIntClock();
            for (var writer : WRITERS) {
                assertThrows(IllegalStateException.class, () -> writer.accept(f));
                assertSame(state, f.state()); assertSame(env, f.environment()); assertSame(art, f.artState());
                assertSame(clock, f.vIntClock()); assertSame(before.actor().display(), f.display());
                assertTrue(f.paused()); assertFalse(f.closed()); assertSame(before.source(), f.stageSource());
            }
        }, () -> cuts[0]++, () -> cuts[0]++);
        assertEquals(3, cuts[0]); assertEquals(before.actor(), f.captureActor());
        assertSame(before.shared().artState(), f.artState());
        assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xF0123456L), 0x89ABCDF0L), f.vIntClock());
    }

    @Test
    void aSingleNewPastFrameKeepsItsClockAcrossLoadWaitAndLaterFixedStores() {
        requireActual();
        // A complete component fixture, not evidence that RunMMD/Start/LoadTimeWarpData executed.
        var primary = new CdPlayerBody.State(new CdPlayerMotionState(0x12340000, 0x05670000, -0x123, 0x234, -0x345,
                0, 0, 0x42, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                new CdPlayerBody.AnimationCursor(0, 0, 0), new CdPlayerBody.Header(0, 0, 0, 0, 0),
                new CdPlayerBody.Timers(0, 0, 0, 0), new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[0x100]), 0),
                0, new CdPlayerBody.ObjectLinks(0, 0), 0);
        var env = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0, 0, 0),
                new CdPlayerMovement.Bounds(0x10, 0x2000, 0x300, 0), new CdPlayerMovement.Gates(0, 0x80, 0, 0, 0, 0, 0),
                new CdPlayerMovement.Globals(0, 0x60, 0, 0, 0, 0, 0x12, 0x34, 0x56, 0x78)),
                new CdPlayerBody.Controls(0, 0, 0, 0, 1), new CdPlayerBody.Globals(0, 160, 0, 0, 0, 0, 0, 0),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        var art = new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false);
        var f = CdPlayerFrameRuntime.fromPastEntry(past, primary, env, art,
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0), 0));
        var owner = f.captureActor().owner(); var sharedOwner = f.captureShared().owner(); var source = f.stageSource();
        f.bindArtPublication((request, value) -> fail("Load/fixed writes/fade have no Sonic DPLC"));
        f.serviceLevelLoad(8, NOTHING, NOTHING, NOTHING);
        assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0), 1), f.vIntClock());
        f.applyLevelStartWarpPlayer(); f.clearLevelStartControlsAndBoredTimer(); f.clearLevelStartPlayerGlobals();
        assertTrue(f.clearLevelStartTimeZoneMarker());
        assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0), 1), f.vIntClock());
        assertSame(owner, f.captureActor().owner()); assertSame(sharedOwner, f.captureShared().owner());
        assertSame(source, f.stageSource()); assertSame(art, f.artState()); assertTrue(f.display().isEmpty());
        assertEquals(new CdPlayerBody.Header(1, 0, 0, 0, 0), f.state().header());
        assertEquals(new CdPlayerBody.Timers(0x78, 0, 0, 0), f.state().timers());
        assertSame(primary.motion(), f.state().motion()); assertSame(primary.history(), f.state().history());
        assertEquals(new CdPlayerBody.Controls(0, 0, 0, 0, 1), f.environment().controls());
        assertEquals(new CdPlayerMovement.Physics(0, 0, 0), f.environment().movement().physics());
        f.serviceLevelPaletteFade(8, NOTHING, NOTHING);
        assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0), 2), f.vIntClock());
        assertEquals(new CdPlayerBody.Controls(8, 8, 0, 0, 1), f.environment().controls());
        assertEquals(0, f.environment().globals().levelFrames()); assertFalse(f.paused());
        assertSame(owner, f.captureActor().owner()); assertSame(sharedOwner, f.captureShared().owner());
        assertSame(source, f.stageSource()); assertSame(art, f.artState()); assertTrue(f.display().isEmpty());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    @Test
    void nativeByteAndWordCarriersRetainTheirStrictWidthChecks() {
        for (int value : new int[]{-1, 0x100}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Header(value, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Controls(value, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.Gates(0, value, 0, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.Gates(0, 0, 0, 0, 0, value, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Globals(0, 0, 0, 0, 0, 0, value, 0));
        }
        for (int value : new int[]{-1, 0x10000}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Timers(value, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Globals(value, 0, 0, 0, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.Globals(0, 0, 0, value, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerMovement.Globals(0, 0, 0, 0, value, 0, 0, 0, 0, 0));
        }
    }

    private record Cut(CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared,
                       CdStagePlayerSource source, boolean closed) { }
    private static Cut cut(CdPlayerFrameRuntime f) {
        return new Cut(f.captureActor(), f.captureShared(), f.stageSource(), f.closed());
    }
    private static void assertComponentsUnchanged(CdPlayerFrameRuntime f, Cut before) {
        assertSame(before.actor().state(), f.state()); assertSame(before.actor().display(), f.display());
        assertSame(before.shared().environment(), f.environment()); assertSame(before.shared().artState(), f.artState());
        assertSame(before.shared().vIntClock(), f.vIntClock()); assertEquals(before.shared().paused(), f.paused());
        assertSame(before.source(), f.stageSource());
    }
    private static void assertUnchanged(CdPlayerFrameRuntime f, Cut before) {
        assertComponentsUnchanged(f, before); assertEquals(before.closed(), f.closed());
        assertEquals(before.actor(), f.captureActor()); assertEquals(before.shared(), f.captureShared());
    }
    private static void assertUnwrittenOwners(CdPlayerFrameRuntime f, Cut before) {
        assertSame(before.shared().artState(), f.artState()); assertSame(before.shared().vIntClock(), f.vIntClock());
        assertEquals(before.shared().paused(), f.paused()); assertSame(before.actor().display(), f.display());
        assertSame(before.source(), f.stageSource()); assertEquals(before.closed(), f.closed());
        assertSame(before.actor().owner(), f.captureActor().owner());
        assertSame(before.shared().owner(), f.captureShared().owner());
    }
    private static void assertSourceRejected(org.junit.jupiter.api.function.Executable call) {
        assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,
                assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, call).boundary());
    }
    private static CdPlayerFrameRuntime dirtyPast(int period) {
        var f = CdPlayerFrameRuntime.fromPastEntry(past, player(0xA5, 0xABCD), dirtyEnvironment(0, period), art(), clock());
        return installCapturedTestValues(f, period);
    }
    private static CdPlayerFrameRuntime dirtyPresent(int period) {
        return installCapturedTestValues(CdPlayerFrameRuntime.externalTouchRequiredDevelopment(present), period);
    }
    private static CdPlayerFrameRuntime installCapturedTestValues(CdPlayerFrameRuntime f, int period) {
        var a = f.captureActor(); var s = f.captureShared();
        var display = Optional.of(new CdPlayerBody.DisplayPublication(true, 0x12345678, 0xF234, 0x67,
                0x89, 0xCD, 0x56, 0x12345678, 0x23456789));
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(a.owner(), player(0xA5, 0xABCD), display));
        f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), dirtyEnvironment(0, period), art(), true, clock()));
        return f;
    }
    private static CdPlayerBody.State player(int slotId, int hurt) {
        byte[] history = new byte[0x100];
        for (int i = 0; i < history.length; i++) history[i] = (byte) (i * 37 + 11);
        return new CdPlayerBody.State(new CdPlayerMotionState(0x12345678, 0x23456789, -0x1234, 0x2345, -0x3456,
                0x12, 0x34, 0x45, 0x56, 0x67, 0x78, 0x89, 0x9A, 0xAB, 0xBC, -0x123, 0xCD, 0xDE, 0xEF, 0xF0),
                new CdPlayerBody.AnimationCursor(0xAB, 0xBC, 0xCD),
                new CdPlayerBody.Header(slotId, 0x12345678, 0xF234, 0x67, 0x89),
                new CdPlayerBody.Timers(hurt, 0xBCDE, 0xCDEF, 0xDEF0),
                new CdPlayerBody.History(CdAssetBytes.copyOf(history), 0xBEEF), 0xF1,
                new CdPlayerBody.ObjectLinks(0xE2, 0xD3), 0xC4);
    }
    private static CdPlayerBody.Environment dirtyEnvironment(int zoneAct, int period) {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123, 0x23, 0x34),
                new CdPlayerMovement.Bounds(0x12, 0x2345, 0x3456, 0x78),
                new CdPlayerMovement.Gates(zoneAct, period, 0x12, 0x34, 0x56, 0x78, 0xFF),
                new CdPlayerMovement.Globals(0x12, 0x2345, 0x34, 0x56, 0xFFFF, 0x4567, 0x78, 0x89, 0x9A, 0xAB)),
                new CdPlayerBody.Controls(0x24, 0x18, 0xA8, 0xD0, 0xA5),
                new CdPlayerBody.Globals(0xFFFF, 0x2345, 0x12, 0xFEDC, 0x4567, 0xABCD, 0x56, 0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }
    private static CdPlayerBody.Environment afterControlClear(int period) {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123, 0x23, 0x34),
                new CdPlayerMovement.Bounds(0x12, 0x2345, 0x3456, 0x78),
                new CdPlayerMovement.Gates(0, period, 0x12, 0x34, 0x56, 0x78, 0xFF),
                new CdPlayerMovement.Globals(0x12, 0x2345, 0x34, 0x56, 0, 0x4567, 0x78, 0x89, 0x9A, 0xAB)),
                new CdPlayerBody.Controls(0, 0, 0, 0, 0xA5),
                new CdPlayerBody.Globals(0xFFFF, 0x2345, 0x12, 0xFEDC, 0x4567, 0xABCD, 0x56, 0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }
    private static CdPlayerBody.Environment afterPlayerGlobalsClear(int period) {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123, 0x23, 0x34),
                new CdPlayerMovement.Bounds(0x12, 0x2345, 0x3456, 0x78),
                new CdPlayerMovement.Gates(0, period, 0x12, 0x34, 0x56, 0, 0xFF),
                new CdPlayerMovement.Globals(0x12, 0x2345, 0x34, 0x56, 0xFFFF, 0x4567, 0x78, 0x89, 0x9A, 0xAB)),
                new CdPlayerBody.Controls(0x24, 0x18, 0xA8, 0xD0, 0xA5),
                new CdPlayerBody.Globals(0, 0x2345, 0x12, 0, 0x4567, 0xABCD, 0, 0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }
    private static CdPlayerArtRuntime.State art() {
        byte[] cpu = new byte[0x300]; byte[] resident = new byte[0x300];
        for (int i = 0; i < cpu.length; i++) { cpu[i] = (byte) (i * 17 + 3); resident[i] = (byte) (i * 29 + 7); }
        return new CdPlayerArtRuntime.State(cpu, resident, 0xE7, true);
    }
    private static CdPlayerFrameRuntime.VIntClock clock() {
        return new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xF0123456L), 0x89ABCDEFL);
    }
    private static void requireActual() {
        assumeTrue(present != null && past != null, "Requires configured original-USA soniccd.media.root");
    }
    private static void unreached() { fail("Rejected source cannot invoke a service callback"); }
}
