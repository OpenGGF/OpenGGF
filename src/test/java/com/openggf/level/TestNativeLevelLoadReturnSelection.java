package com.openggf.level;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.internal.NativeFreshLevelLoadProfile;
import com.openggf.game.internal.NativeLevelLoadInputs;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission;
import com.openggf.game.internal.NativeLevelLoadPlan;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Original return presentation selection survives a held display-clear boundary. */
class TestNativeLevelLoadReturnSelection {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminalFailureRetainsCleanupErrorAndCannotResume(boolean fatal) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var manager = GameServices.level();
        var context = new LevelLoadContext();
        Throwable primary = fatal ? new AssertionError("load failed") : new IllegalStateException("load failed");
        var cleanupFailure = new AssertionError("cleanup failed");
        var zoneFailure = new AssertionError("outer zone cleanup failed");
        var terminals = new AtomicInteger();
        var inputs = NativeLevelLoadInputs.fromRom(RomByteReader.fromBytes(new byte[]{1}), List.of(), List.of(0));
        var plan = new NativeLevelLoadPlan(List.of(new NativeLevelLoadPlan.Section(
                NativeLevelLoadPhaseAdmission.Phase.DISPLAY_CLEAR, () -> inputs, () -> true,
                List.of(), List.of(new InitStep("failingDecode", "terminal production failure", () -> {
                    if (primary instanceof Error error) throw error;
                    throw (RuntimeException) primary;
                })))));
        var profile = mock(LevelInitProfile.class, withSettings().extraInterfaces(NativeFreshLevelLoadProfile.class));
        when(((NativeFreshLevelLoadProfile) profile).freshLevelLoadPlan(any())).thenReturn(plan);
        var module = mock(GameModule.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        var attempt = new LevelLoadAttempt(null, null, LevelLoadMode.FULL, context);
        attempt.execute(() -> module, 0, () -> 1, phase -> NativeLevelLoadPhaseAdmission.Decision.HOLD);
        var pending = new NativeLevelLoadController.PendingLoad(attempt, context, 0, LevelLoadMode.FULL, null);
        pending.cleanup = () -> { throw cleanupFailure; };
        pending.zoneLoadFinished = succeeded -> {
            assertFalse(succeeded); terminals.incrementAndGet(); throw zoneFailure;
        };
        manager.nativeLoads.pending = pending;
        NativeLevelLoadAccess.afterCompletion(manager, () -> fail("failed loads must not publish saves"));
        if (fatal) {
            assertSame(primary, assertThrows(AssertionError.class, () -> NativeLevelLoadAccess.resume(manager)));
        } else {
            var failure = assertThrows(java.io.UncheckedIOException.class, () -> NativeLevelLoadAccess.resume(manager));
            assertSame(primary, failure.getCause().getCause());
        }
        assertArrayEquals(new Throwable[]{cleanupFailure, zoneFailure}, primary.getSuppressed());
        assertEquals(1, terminals.get());
        assertFalse(NativeLevelLoadAccess.isPending(manager));
        assertDoesNotThrow(() -> NativeLevelLoadAccess.resume(manager));
    }

    @Test void deferredPlaybackObservesTheCompletedLoadBeforeCallerPublication() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var manager = spy(GameServices.level());
        var context = new LevelLoadContext();
        context.setLevel(mock(Level.class));
        var inputs = NativeLevelLoadInputs.fromRom(RomByteReader.fromBytes(new byte[]{1}), List.of(), List.of(0));
        var plan = new NativeLevelLoadPlan(List.of(new NativeLevelLoadPlan.Section(
                NativeLevelLoadPhaseAdmission.Phase.DISPLAY_CLEAR, () -> inputs, () -> true,
                List.of(), List.of())));
        var profile = mock(LevelInitProfile.class, withSettings().extraInterfaces(NativeFreshLevelLoadProfile.class));
        when(((NativeFreshLevelLoadProfile) profile).freshLevelLoadPlan(any())).thenReturn(plan);
        var module = mock(GameModule.class); when(module.getLevelInitProfile()).thenReturn(profile);
        var attempt = new LevelLoadAttempt(null, null, LevelLoadMode.FULL, context);
        attempt.execute(() -> module, 0, () -> 1, phase -> NativeLevelLoadPhaseAdmission.Decision.HOLD);
        manager.nativeLoads.pending = new NativeLevelLoadController.PendingLoad(
                attempt, context, 0, LevelLoadMode.FULL, null);
        var tracker = new com.openggf.trace.replay.runs.RunLevelLoadTracker();
        tracker.prime(manager);
        long before = manager.getCompletedProductionLoadGeneration();
        var cleaned = new AtomicInteger();
        doNothing().when(manager).applyPersistedEditorEdits();
        doNothing().when(manager).restoreCheckpointRuntimeState(context);
        doNothing().when(manager).resetNativeRuntimeFrameBridge();
        doAnswer(call -> { cleaned.incrementAndGet(); return null; }).when(manager).clearNativeRuntimeAssembly();
        doAnswer(call -> {
            assertFalse(NativeLevelLoadAccess.isPending(manager));
            assertEquals(1, cleaned.get());
            assertEquals(before + 1, manager.getCompletedProductionLoadGeneration());
            assertTrue(tracker.observeLoaded(manager).isPresent(), "playback must see a real completed-load receipt");
            return null;
        }).when(manager).activateScheduledPlaybackForLoadedLevel();
        assertTrue(manager.nativeLoads.deferRuntimeCompletion(manager, context,
                LevelLoadMode.FULL, com.openggf.game.LevelLoadCause.DECODE_ONLY));
        NativeLevelLoadAccess.afterCompletion(manager, () -> assertTrue(tracker.latest().isPresent(),
                "caller publication follows playback's completed-load observation"));
        NativeLevelLoadAccess.resume(manager);
        NativeLevelLoadAccess.resume(manager);
        verify(manager, times(1)).activateScheduledPlaybackForLoadedLevel();
    }

    @Test void heldDisplayReturnRetainsSelectedTitleAndDefersSingleShotCallerCleanup() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var manager = GameServices.level();
        manager.setBonusStageReturnCheckpointIndex(0);
        var context = new LevelLoadContext();
        context.setLevelIndex(0);
        context.setShowTitleCard(true);
        Runnable selected = NativeLevelLoadAccess.selectTitleRequest(manager, context);
        var inputs = NativeLevelLoadInputs.fromRom(RomByteReader.fromBytes(new byte[]{1}), List.of(), List.of(0));
        var plan = new NativeLevelLoadPlan(List.of(new NativeLevelLoadPlan.Section(
                NativeLevelLoadPhaseAdmission.Phase.DISPLAY_CLEAR, () -> inputs, () -> true,
                List.of(), List.of(new InitStep("selectedReturn", "actual captured title policy", selected)))));
        var profile = mock(LevelInitProfile.class, withSettings().extraInterfaces(NativeFreshLevelLoadProfile.class));
        when(((NativeFreshLevelLoadProfile) profile).freshLevelLoadPlan(any())).thenReturn(plan);
        var module = mock(GameModule.class); when(module.getLevelInitProfile()).thenReturn(profile);
        var attempt = new LevelLoadAttempt(null, null, LevelLoadMode.FULL, context);
        attempt.execute(() -> module, 0, () -> 1, phase -> NativeLevelLoadPhaseAdmission.Decision.HOLD);
        assertFalse(manager.getTransitions().isTitleCardRequested());

        // Even an external caller clearing its flag cannot rewrite the chosen policy.
        manager.clearBonusStageReturn();
        attempt.resume(NativeLevelLoadPhaseAdmission.IMMEDIATE);
        assertTrue(manager.getTransitions().isTitleCardRequested(),
                "the saved-origin AIZ card cannot become the suppressed opening intro after HOLD");

        var cleanups = new AtomicInteger(); var restorations = new AtomicInteger();
        var terminals = new AtomicInteger();
        var publications = new AtomicInteger();
        manager.nativeLoads.pending = new NativeLevelLoadController.PendingLoad(
                attempt, context, 0, LevelLoadMode.FULL, null);
        manager.nativeLoads.pending.zoneLoadFinished = succeeded -> {
            assertTrue(succeeded); terminals.incrementAndGet();
        };
        NativeLevelLoadAccess.afterAttempt(manager, cleanups::incrementAndGet);
        NativeLevelLoadAccess.afterCompletion(manager, () -> {
            assertFalse(NativeLevelLoadAccess.isPending(manager));
            assertEquals(1, cleanups.get()); assertEquals(1, terminals.get());
            publications.incrementAndGet();
        });
        NativeLevelLoadAccess.afterPlayerAssembly(manager, restorations::incrementAndGet);
        assertEquals(0, cleanups.get()); assertEquals(0, restorations.get());
        NativeLevelLoadAccess.playerAssemblyReady(manager);
        NativeLevelLoadAccess.playerAssemblyReady(manager);
        assertEquals(1, restorations.get());
        NativeLevelLoadAccess.resume(manager);
        NativeLevelLoadAccess.resume(manager);
        assertEquals(1, cleanups.get());
        assertEquals(1, terminals.get());
        assertEquals(1, publications.get());
        assertFalse(NativeLevelLoadAccess.isPending(manager));
    }
}
