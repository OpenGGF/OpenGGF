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
    @Test void resultsPreparationFinishesBeforeTwentySixSequentialEntryIterations() {
        var calls = new ArrayList<String>();
        var coordinator = coordinator(calls);
        var menus = new com.openggf.game.nativemenu.NativeMenuController();
        var screen = new com.openggf.game.nativemenu.NativeMenuScreen() {
            boolean pending;
            @Override public GameMode mode() { return GameMode.COMPETITION_RESULTS; }
            @Override public void initialize() { }
            @Override public void update(com.openggf.control.InputHandler input) { pending = true; }
            @Override public void afterFrame() {
                if (!pending) return;
                pending = false;
                assertThrows(IllegalStateException.class, NativeCompetitionIterationAccess::currentFrame);
                for (int entry = 0; entry < 26; entry++) {
                    coordinator.runLogicalIteration(() -> { }, frame -> {
                        GameLoopPlcLifecycle.runPhase(frame, PlcLifecyclePhase.LEVEL_TITLE_CARD, () -> { });
                        return null;
                    });
                }
            }
            @Override public void draw() { }
            @Override public void setClearColor() { }
            @Override public void reset() { pending = false; }
            @Override public Exit consumeExit() { return Exit.NONE; }
        };
        menus.open(screen);
        var input = mock(com.openggf.control.InputHandler.class);
        menus.beginFrame();
        coordinator.runLogicalIteration(() -> { }, frame -> {
            GameLoopPlcLifecycle.runNativeScreen(frame, GameMode.COMPETITION_RESULTS,
                    () -> menus.update(input, false));
            assertEquals(List.of("service:TWO_PLAYER_RESULTS", "prepare:TWO_PLAYER_RESULTS"), calls);
            return null;
        });
        menus.completeFrame();
        menus.completeFrame();

        assertEquals(54, calls.size());
        assertEquals(List.of("service:TWO_PLAYER_RESULTS", "prepare:TWO_PLAYER_RESULTS"), calls.subList(0, 2));
        for (int entry = 0; entry < 26; entry++) {
            assertEquals(List.of("service:LEVEL_TITLE_CARD", "prepare:LEVEL_TITLE_CARD"),
                    calls.subList(2 + entry * 2, 4 + entry * 2));
        }
        verify(input).update();
    }

    @Test void nativePauseWaitIncludingResumePrecedesTheNextOrdinaryWaitAndWorldBody() throws Exception {
        var calls = new ArrayList<String>();
        var coordinator = new PlcFrameLifecycleCoordinator(new PlcLifecycleService() {
            @Override public void serviceVBlank(PlcLifecyclePhase phase) { calls.add("service:" + phase); }
            @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                return phase != PlcLifecyclePhase.NORMAL_PAUSE;
            }
            @Override public void prepareAfterLoop(PlcLifecyclePhase phase) { calls.add("prepare:" + phase); }
        });
        var module = mock(GameModule.class);
        var mode = mock(com.openggf.game.session.GameplayModeContext.class);
        var level = mock(LevelManager.class);
        var sprites = mock(com.openggf.sprites.managers.SpriteManager.class);
        var gameState = new com.openggf.game.GameStateManager();
        var camera = new Camera();
        var player = new com.openggf.game.sonic3k.competition.S3kCompetitionPlayer(0,
                com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character.SONIC, 100, 200);
        var actor = player.sprite();
        camera.setFocusedSprite(actor);
        var beforePlayer = player.capture();
        var beforeCamera = camera.capture();
        var beforeGame = gameState.capture();
        var session = new com.openggf.game.session.WorldSession(module);
        when(mode.getWorldSession()).thenReturn(session);
        when(mode.getLevelManager()).thenReturn(level);
        when(mode.getSpriteManager()).thenReturn(sprites);
        when(mode.getGameStateManager()).thenReturn(gameState);
        when(mode.getCamera()).thenReturn(camera);
        when(mode.hardwareTiming()).thenReturn(new HardwareTimingService());
        when(mode.hardwareTimingBoundaryObserver()).thenReturn(boundary -> calls.add(boundary.name()));
        var roster = mock(com.openggf.game.competition.NativeCompetitionRoster.class);
        when(roster.player(0)).thenReturn(actor);
        // The factory is independently covered by the participant/bootstrap tests.
        // Inject its already-attached context to isolate the public world's pause consumer.
        var constructor = com.openggf.game.competition.NativeCompetitionWorld.class.getDeclaredConstructor(
                com.openggf.game.session.GameplayModeContext.class,
                com.openggf.game.competition.NativeCompetitionRoster.class);
        constructor.setAccessible(true);
        var world = constructor.newInstance(mode, roster);
        var input = new com.openggf.control.InputHandler();
        var beforeInput = input.logical();
        try (var sessions = mockStatic(com.openggf.game.session.SessionManager.class)) {
            sessions.when(com.openggf.game.session.SessionManager::getCurrentGameplayMode).thenReturn(mode);
            for (int wait = 0; wait < 2; wait++) {
                var frame = coordinator.latchBeforeFadeUpdate();
                GameLoopPlcLifecycle.runNativeScreen(frame, GameMode.COMPETITION, () ->
                        assertEquals(LevelFrameResult.PAUSED, world.pauseVBlank(frame)));
                frame.finish();
            }
            verifyNoInteractions(level, sprites);
            assertEquals(beforePlayer, player.capture());
            assertEquals(beforeCamera, camera.capture());
            assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey("game-state", beforeGame, gameState.capture()));
            assertSame(beforeInput, input.logical());
            var ordinary = coordinator.latchBeforeFadeUpdate();
            GameLoopPlcLifecycle.runNativeScreen(ordinary, GameMode.COMPETITION, () ->
                    assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                            world.step(input, ordinary, () -> calls.add("players"))));
            ordinary.finish();
        }
        verify(level).advanceLevelFrameCounter();
        verify(level).update();
        verify(sprites).publishHeldInputForLevelEvents(input);
        assertEquals(List.of("service:NORMAL_PAUSE", "VINT_SERVICE", "service:NORMAL_PAUSE", "VINT_SERVICE",
                "service:ORDINARY_LEVEL", "VINT_SERVICE", "players", "POST_OBJECTS", "PRE_MAIN_LOOP",
                "prepare:ORDINARY_LEVEL"), calls);
    }

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
