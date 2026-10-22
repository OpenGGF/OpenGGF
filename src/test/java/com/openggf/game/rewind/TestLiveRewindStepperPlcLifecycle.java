package com.openggf.game.rewind;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameModule;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStagePlayerPriorityPolicy;
import com.openggf.game.BonusStageType;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.RenderPriority;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.game.GameStateManager;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLiveRewindStepperPlcLifecycle {

    @Test
    void completedGameplayRowRunsIndependentlyOfCurrentPauseState() {
        Fixture fixture = fixture(true);

        LevelFrameResult result = fixture.step(false);

        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, result);
        assertEquals(List.of("service:ORDINARY_LEVEL", "prepare:ORDINARY_LEVEL"), fixture.events);
        org.junit.jupiter.api.Assertions.assertFalse(fixture.context.gameStateManager().isGamePaused());
    }

    @Test
    void unpausePressRunsAndPreparesOrdinaryLevel() {
        Fixture fixture = fixture(false);

        LevelFrameResult result = fixture.step(true);

        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, result);
        assertEquals(List.of(
                "service:ORDINARY_LEVEL",
                "prepare:ORDINARY_LEVEL"), fixture.events);
    }

    @Test
    void replayingRecordedHostUnpauseDoesNotToggleRomPause() {
        // Gamepad Start pauses/resumes the host. Paused presentations are not
        // recorded; the first completed gameplay row after resume still holds Start.
        GameStateManager state = new GameStateManager();
        List<String> events = new ArrayList<>();
        var lifecycle = new PlcFrameLifecycleCoordinator(recording(events));
        SpriteManager sprites = mock(SpriteManager.class);
        GameModule module = mock(GameModule.class);
        LevelFrameContext context = new LevelFrameContext(module, null, null, null,
                sprites, state, null, null, null, new HardwareTimingService(), null,
                RuntimeArtCoordinator.NONE);
        LiveRewindInputSource inputs = new LiveRewindInputSource();
        InputHandler input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()));
        inputs.appendFrame(input, SonicConfigurationService.getInstance());
        input.clearLogicalOverride();
        var stepper = new LiveRewindStepper(inputs, () -> input, mock(GameplayModeContext.class));
        var frame = lifecycle.latchBeforeFadeUpdate();
        try {
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, stepper.step(inputs.read(1),
                    sprites, mock(LevelManager.class), mock(com.openggf.camera.Camera.class),
                    input, context, frame), "a recorded completed gameplay row must execute its body");
        } finally {
            frame.finish();
        }
        org.mockito.Mockito.verify(sprites).update(input);
        org.junit.jupiter.api.Assertions.assertFalse(state.isGamePaused());
        assertEquals(List.of("service:ORDINARY_LEVEL", "prepare:ORDINARY_LEVEL"), events);
    }

    @ParameterizedTest
    @EnumSource(value = BonusStageType.class, names = {"GUMBALL", "GLOWING_SPHERE", "SLOT_MACHINE"})
    void bonusReplayRestoresOrdinaryPlayerPriorityAfterPhysicsAndBeforeTheProvider(BonusStageType type) {
        List<String> events = new ArrayList<>();
        var lifecycle = new PlcFrameLifecycleCoordinator(recording(events));
        SpriteManager sprites = mock(SpriteManager.class);
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        var follower = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        var objectOwned = new TestablePlayableSprite("knuckles", (short) 0, (short) 0);
        player.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
        follower.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
        objectOwned.setPriorityBucket(RenderPriority.MIN);
        when(sprites.getAllSprites()).thenReturn(List.of(player, follower, objectOwned));
        org.mockito.Mockito.doAnswer(call -> {
            player.setHighPriority(false);
            follower.setHighPriority(false);
            objectOwned.setHighPriority(false);
            events.add("physics-cleared-priority");
            return null;
        }).when(sprites).update(org.mockito.ArgumentMatchers.any(InputHandler.class));
        BonusStageProvider provider = org.mockito.Mockito.mock(BonusStageProvider.class,
                org.mockito.Mockito.withSettings().extraInterfaces(BonusStagePlayerPriorityPolicy.class));
        when(provider.getActiveType()).thenReturn(type);
        when(((BonusStagePlayerPriorityPolicy) provider).shouldForcePlayerHighPriority())
                .thenReturn(type != BonusStageType.SLOT_MACHINE);
        org.mockito.Mockito.doAnswer(call -> {
            assertEquals(type != BonusStageType.SLOT_MACHINE, player.isHighPriority());
            assertEquals(type != BonusStageType.SLOT_MACHINE, follower.isHighPriority());
            assertEquals(false, objectOwned.isHighPriority(), "retain the object's bucket/priority owner");
            events.add("provider-after-priority");
            return null;
        }).when(provider).onFrameUpdate();
        GameModule module = mock(GameModule.class);
        WorldSession world = mock(WorldSession.class);
        when(world.getGameModule()).thenReturn(module);
        GameplayModeContext mode = mock(GameplayModeContext.class);
        when(mode.getWorldSession()).thenReturn(world);
        when(mode.getSpriteManager()).thenReturn(sprites);
        when(mode.getActiveBonusStageProvider()).thenReturn(provider);
        GameStateManager state = mock(GameStateManager.class);
        LevelFrameContext context = new LevelFrameContext(module, null, null, null,
                sprites, state, null, null, null, new HardwareTimingService(), null,
                RuntimeArtCoordinator.NONE);
        var stepper = new LiveRewindStepper(new LiveRewindInputSource(),
                InputHandler::new, mode);
        var frame = lifecycle.latchBeforeFadeUpdate();
        try {
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, stepper.step(
                    new Bk2FrameInput(1, 0, 0, false, 0, 0, false, "ordinary bonus row"),
                    sprites, mock(LevelManager.class), mock(com.openggf.camera.Camera.class),
                    new InputHandler(), context, frame));
        } finally {
            frame.finish();
        }
        org.junit.jupiter.api.Assertions.assertTrue(events.indexOf("physics-cleared-priority")
                < events.indexOf("provider-after-priority"));
        assertEquals(type != BonusStageType.SLOT_MACHINE, player.isHighPriority());
        assertEquals(type != BonusStageType.SLOT_MACHINE, follower.isHighPriority());
        assertEquals(false, objectOwned.isHighPriority());
    }

    @Test
    void replayUsesInstalledManagersAndResamplesTheOwnersRestoredBonusProvider() {
        var ambient = com.openggf.tests.TestEnvironment.activeGameplayMode();
        SpriteManager installedSprites = mock(SpriteManager.class);
        LevelManager installedLevel = mock(LevelManager.class);
        var installedCamera = mock(com.openggf.camera.Camera.class);
        GameplayModeContext owner = mock(GameplayModeContext.class);
        WorldSession world = mock(WorldSession.class);
        GameModule module = mock(GameModule.class);
        when(world.getGameModule()).thenReturn(module);
        when(owner.getWorldSession()).thenReturn(world);
        when(owner.getSpriteManager()).thenReturn(installedSprites);
        when(owner.getLevelManager()).thenReturn(installedLevel);
        when(owner.getCamera()).thenReturn(installedCamera);
        when(owner.getGameStateManager()).thenReturn(new GameStateManager());
        when(owner.hardwareTiming()).thenReturn(new HardwareTimingService());
        when(owner.getFadeManager()).thenReturn(new com.openggf.graphics.FadeManager());
        when(owner.plcFrameLifecycle()).thenReturn(new PlcFrameLifecycleCoordinator((PlcLifecycleService) null));
        BonusStageProvider first = mock(BonusStageProvider.class);
        BonusStageProvider restored = mock(BonusStageProvider.class);
        when(first.getActiveType()).thenReturn(BonusStageType.GUMBALL);
        when(restored.getActiveType()).thenReturn(BonusStageType.GUMBALL);
        when(owner.getActiveBonusStageProvider()).thenReturn(first);
        LiveRewindInputSource inputs = new LiveRewindInputSource();
        InputHandler input = new InputHandler();
        inputs.appendFrame(input, SonicConfigurationService.getInstance());
        var stepper = new LiveRewindStepper(inputs, () -> input, owner);

        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, stepper.step(inputs.read(1)));
        when(owner.getActiveBonusStageProvider()).thenReturn(restored);
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, stepper.step(inputs.read(1)));

        org.mockito.Mockito.verify(installedSprites, org.mockito.Mockito.times(2)).update(input);
        org.mockito.Mockito.verify(first).onFrameUpdate();
        org.mockito.Mockito.verify(restored).onFrameUpdate();
        org.junit.jupiter.api.Assertions.assertSame(ambient,
                com.openggf.game.session.SessionManager.getCurrentGameplayMode());
        com.openggf.game.session.SessionManager.clear();
    }

    private static Fixture fixture(boolean remainsPaused) {
        List<String> events = new ArrayList<>();
        PlcLifecycleService service = recording(events);
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(service);
        GameStateManager state = new GameStateManager();
        state.setGamePaused(remainsPaused);
        GameModule module = mock(GameModule.class);
        LevelFrameContext context = new LevelFrameContext(
                module, null, null, null, mock(SpriteManager.class), state,
                null, null, null, new HardwareTimingService(), null,
                RuntimeArtCoordinator.NONE);
        LevelManager level = mock(LevelManager.class);
        LiveRewindInputSource inputs = new LiveRewindInputSource();
        LiveRewindStepper stepper = new LiveRewindStepper(
                inputs, InputHandler::new, mock(GameplayModeContext.class));
        return new Fixture(
                stepper, coordinator, context, level,
                mock(SpriteManager.class), events);
    }

    private static PlcLifecycleService recording(List<String> events) {
        return new PlcLifecycleService() {
            @Override
            public void serviceVBlank(PlcLifecyclePhase phase) {
                events.add("service:" + phase);
            }

            @Override
            public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                return phase == PlcLifecyclePhase.ORDINARY_LEVEL;
            }

            @Override
            public void prepareAfterLoop(PlcLifecyclePhase phase) {
                events.add("prepare:" + phase);
            }
        };
    }

    private record Fixture(
            LiveRewindStepper stepper,
            PlcFrameLifecycleCoordinator coordinator,
            LevelFrameContext context,
            LevelManager level,
            SpriteManager sprites,
            List<String> events) {
        LevelFrameResult step(boolean startPressed) {
            var frame = coordinator.latchBeforeFadeUpdate();
            LevelFrameResult result = stepper.step(
                    new Bk2FrameInput(
                            1, 0, 0, startPressed, 0, 0, false, "row"),
                    sprites, level, mock(com.openggf.camera.Camera.class),
                    new InputHandler(), context, frame);
            frame.finish();
            return result;
        }
    }
}
