package com.openggf.game.rewind;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.control.InputHandler;
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
    void heldPauseServicesNormalPauseWithoutPreparation() {
        Fixture fixture = fixture(true);

        LevelFrameResult result = fixture.step(false);

        assertEquals(LevelFrameResult.PAUSED, result);
        assertEquals(List.of("service:NORMAL_PAUSE"), fixture.events);
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
                InputHandler::new, () -> context, () -> mode);
        var frame = lifecycle.latchBeforeFadeUpdate();
        try {
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, stepper.step(
                    new Bk2FrameInput(1, 0, 0, false, 0, 0, false, "ordinary bonus row"),
                    sprites, mock(LevelManager.class), mock(com.openggf.camera.Camera.class),
                    new InputHandler(), frame));
        } finally {
            frame.finish();
        }
        org.junit.jupiter.api.Assertions.assertTrue(events.indexOf("physics-cleared-priority")
                < events.indexOf("provider-after-priority"));
        assertEquals(type != BonusStageType.SLOT_MACHINE, player.isHighPriority());
        assertEquals(type != BonusStageType.SLOT_MACHINE, follower.isHighPriority());
        assertEquals(false, objectOwned.isHighPriority());
    }

    private static Fixture fixture(boolean remainsPaused) {
        List<String> events = new ArrayList<>();
        PlcLifecycleService service = recording(events);
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(service);
        GameStateManager state = mock(GameStateManager.class);
        when(state.applyPauseToggle(org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(remainsPaused);
        GameModule module = mock(GameModule.class);
        LevelFrameContext context = new LevelFrameContext(
                module, null, null, null, mock(SpriteManager.class), state,
                null, null, null, new HardwareTimingService(), null,
                RuntimeArtCoordinator.NONE);
        LevelManager level = mock(LevelManager.class);
        LiveRewindInputSource inputs = new LiveRewindInputSource();
        LiveRewindStepper stepper = new LiveRewindStepper(
                inputs, InputHandler::new, () -> context, () -> null);
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
                    new InputHandler(), frame);
            frame.finish();
            return result;
        }
    }
}
