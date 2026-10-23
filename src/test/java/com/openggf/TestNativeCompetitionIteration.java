package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.LevelManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeCompetitionIteration {
    @Test void canonicalWorldOwnsOneVblankAndOnePreparationThroughScreenDispatch() {
        var calls = new ArrayList<String>();
        var coordinator = coordinator(calls);
        var frame = coordinator.latchBeforeFadeUpdate();
        var module = mock(GameModule.class);
        var context = new LevelFrameContext(module, null, null, null, null,
                null, null, null, null, new HardwareTimingService(), boundary -> calls.add(boundary.name()));
        var level = mock(LevelManager.class);
        when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(true);
        doAnswer(ignored -> { calls.add("objects"); return null; })
                .when(level).updateObjectPositionsPostPhysicsWithoutTouches(any(Runnable.class), eq(false));

        GameLoopPlcLifecycle.runNativeScreen(frame, GameMode.COMPETITION, () -> {
            assertSame(frame, NativeCompetitionIterationAccess.currentFrame());
            NativeCompetitionIterationAccess.runLoop(frame, () -> LevelFrameStep.execute(
                    context, frame, PlcLifecyclePhase.ORDINARY_LEVEL, level, mock(Camera.class),
                    () -> calls.add("players"), LevelFrameStep.DIRECT_WRAPPER));
        });
        frame.finish();

        assertEquals(List.of("service:ORDINARY_LEVEL", "VINT_SERVICE", "players", "objects",
                "POST_OBJECTS", "PRE_MAIN_LOOP", "prepare:ORDINARY_LEVEL"), calls);
        verify(level).advanceLevelFrameCounter();
        verify(level).prepareTouchResponseSnapshots();
        verify(level).update();
        assertThrows(IllegalStateException.class, NativeCompetitionIterationAccess::currentFrame);
    }

    @Test void alternateNativeScanKeepsItsSpecialStagePhase() {
        var calls = new ArrayList<String>();
        var coordinator = coordinator(calls);
        var frame = coordinator.latchBeforeFadeUpdate();
        var context = new LevelFrameContext(mock(GameModule.class), null, null, null, null,
                null, null, null, null, new HardwareTimingService(), boundary -> calls.add(boundary.name()));
        GameLoopPlcLifecycle.runNativeScreen(frame, GameMode.COMPETITION, () ->
                NativeCompetitionIterationAccess.runLoop(frame, () -> {
                    LevelFrameStep.executeHardwareTimedObjectScan(context, frame,
                            PlcLifecyclePhase.SPECIAL_STAGE, () -> calls.add("halfpipe"));
                    return null;
                }));
        frame.finish();
        assertEquals(List.of("service:SPECIAL_STAGE", "VINT_SERVICE", "halfpipe",
                "POST_OBJECTS", "PRE_MAIN_LOOP", "prepare:SPECIAL_STAGE"), calls);
    }

    @Test void idleAndSetupOnlyIterationsStillCloseTheirToken() {
        var calls = new ArrayList<String>();
        var coordinator = coordinator(calls);
        var first = coordinator.latchBeforeFadeUpdate();
        GameLoopPlcLifecycle.runNativeScreen(first, GameMode.COMPETITION, () -> { });
        first.finish();
        var second = coordinator.latchBeforeFadeUpdate();
        GameLoopPlcLifecycle.runNativeScreen(second, GameMode.COMPETITION, () ->
                NativeCompetitionIterationAccess.runLoop(second, () -> LevelFrameResult.SETUP_ONLY));
        second.finish();
        assertEquals(List.of("service:ORDINARY_LEVEL", "prepare:ORDINARY_LEVEL",
                "service:ORDINARY_LEVEL", "prepare:ORDINARY_LEVEL"), calls);
    }

    @Test void activeBlockingFadeRetainsItsAlreadyPreparedToken() {
        var calls = new ArrayList<String>();
        var coordinator = coordinator(calls);
        try (var fade = coordinator.beginNativeBlockingFade()) {
            coordinator.runLogicalIteration(() -> { }, frame -> {
                GameLoopPlcLifecycle.runNativeScreen(frame, GameMode.COMPETITION, () -> { });
                assertEquals(PlcLifecyclePhase.PALETTE_FADE, frame.ownerPhase());
                return null;
            });
        }
        assertEquals(List.of("service:PALETTE_FADE", "prepare:PALETTE_FADE"), calls);
    }

    @Test void aSecondWorldOrForeignTokenCannotRunInTheSameAdmittedIteration() {
        var calls = new ArrayList<String>();
        var frame = coordinator(calls).latchBeforeFadeUpdate();
        var foreign = coordinator(new ArrayList<>()).latchBeforeFadeUpdate();
        GameLoopPlcLifecycle.runNativeScreen(frame, GameMode.COMPETITION, () -> {
            assertThrows(IllegalStateException.class, () -> NativeCompetitionIterationAccess.runLoop(foreign, () -> null));
            NativeCompetitionIterationAccess.runLoop(frame, () -> {
                frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
                frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
                return null;
            });
            assertThrows(IllegalStateException.class, () -> NativeCompetitionIterationAccess.runLoop(frame, () -> null));
        });
        frame.finish();
        foreign.finish();
    }

    @Test void nestedAndFailedScreenUpdatesCannotLeakTheScopedToken() {
        var frame = coordinator(new ArrayList<>()).latchBeforeFadeUpdate();
        var failure = new IllegalArgumentException("native callback");
        assertSame(failure, assertThrows(IllegalArgumentException.class, () ->
                GameLoopPlcLifecycle.runNativeScreen(frame, GameMode.COMPETITION, () -> {
                    assertThrows(IllegalStateException.class, () ->
                            NativeCompetitionIterationAccess.runAdmitted(frame, () -> { }));
                    throw failure;
                })));
        frame.finish();
        assertThrows(IllegalStateException.class, NativeCompetitionIterationAccess::currentFrame);
    }

    @Test void menusAndResultsKeepTheirExistingSemanticOwners() {
        var calls = new ArrayList<String>();
        var coordinator = coordinator(calls);
        for (var mode : List.of(GameMode.OPTIONS, GameMode.COMPETITION_MENU, GameMode.COMPETITION_RESULTS)) {
            var frame = coordinator.latchBeforeFadeUpdate();
            GameLoopPlcLifecycle.runNativeScreen(frame, mode, () ->
                    assertThrows(IllegalStateException.class, NativeCompetitionIterationAccess::currentFrame));
            frame.finish();
        }
        assertEquals(List.of("service:LEVEL_SELECT", "prepare:LEVEL_SELECT",
                "service:LEVEL_SELECT", "prepare:LEVEL_SELECT",
                "service:TWO_PLAYER_RESULTS", "prepare:TWO_PLAYER_RESULTS"), calls);
    }

    private static PlcFrameLifecycleCoordinator coordinator(List<String> calls) {
        return new PlcFrameLifecycleCoordinator(new PlcLifecycleService() {
            @Override public void serviceVBlank(PlcLifecyclePhase phase) { calls.add("service:" + phase); }
            @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) { return true; }
            @Override public void prepareAfterLoop(PlcLifecyclePhase phase) { calls.add("prepare:" + phase); }
        });
    }
}
