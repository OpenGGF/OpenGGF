package com.openggf.sprites.managers;

import com.openggf.camera.Camera;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.internal.NativePlayableSlot;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Sensor;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ROM-free caller contracts. Authored before the implementation; execution belongs to the parent. */
class TestNativePlayableSlotDispatch {
    private SpriteManager sprites;
    private LevelManager level;
    private final List<String> events = new ArrayList<>();

    @BeforeEach void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        GameplayModeContext context = TestEnvironment.activeGameplayMode();
        sprites = new SpriteManager();
        level = mock(LevelManager.class);
        when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(true);
        when(level.getFrameCounter()).thenReturn(0x10023);
        doAnswer(call -> { events.add("water"); return null; }).when(level)
                .updatePlayableWaterStateForCurrentLevel(any());
        doAnswer(call -> { events.add("touch"); return null; }).when(level).applyTouchResponses(any());
        doAnswer(call -> { events.add("planes"); return null; }).when(level).applyPlaneSwitchers(any());
        doAnswer(call -> { events.add("zone"); return null; }).when(level)
                .updateZoneFeaturesAfterPlayablePhysics(any());
        context.attachGameplayManagers(new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2), new DefaultSolidExecutionRegistry());
        context.attachLevelManagers(new WaterSystem(), new ParallaxManager(),
                mock(TerrainCollisionManager.class), mock(CollisionSystem.class), sprites, level);
        sprites.setFrameCounter(6);
    }

    @AfterEach void close() { SessionManager.clear(); }

    @Test void nativeControlledInputCanObserveGenericSuppressionWithoutConsumingAnyState() {
        assertFalse(SpriteManagerInternalAccess.inputSuppressed(sprites));
        sprites.setInputSuppressed(true);
        assertTrue(SpriteManagerInternalAccess.inputSuppressed(sprites));
        assertTrue(SpriteManagerInternalAccess.inputSuppressed(sprites));
        sprites.setInputSuppressed(false);
        sprites.setPlaybackInputSuppressed(true);
        assertFalse(SpriteManagerInternalAccess.inputSuppressed(sprites),
                "controlled logical overrides retain the existing playback-only bypass");
        assertTrue(events.isEmpty());
    }

    @Test void nativeInitAndLaterSlotsOwnTheWholeReturnAndUseTheLevelLowWord() {
        NativePlayer player = new NativePlayer("main");
        player.setInvulnerableFrames(20);
        player.setInvincibleFrames(30);
        player.setMoveLockTimer(11);
        player.setForcedJumpPress(true);
        player.queueControlLockedForNextFrame(true);
        player.queueForceInputRightForNextFrame(true);
        sprites.addSprite(player);
        int history = player.getHistorySlotIndex(0);
        player.body = () -> events.add(player.slots == 1 ? "init" : "main");

        sprites.update(input());
        sprites.update(input());

        assertEquals(List.of("slot:main", "init", "slot:main", "main"), events);
        assertEquals(2, player.slots);
        assertSame(level, player.level);
        assertEquals(0x23, player.levelFrames, "sprite cadence and V-int count are different clocks");
        assertEquals(8, sprites.getFrameCounter());
        assertEquals(history, player.getHistorySlotIndex(0));
        assertEquals(20, player.getInvulnerableFrames());
        assertEquals(30, player.getInvincibleFrames());
        assertEquals(11, player.getMoveLockTimer());
        assertTrue(player.isForcedJumpPress(), "generic endOfTick must not clear native bookkeeping");
        assertFalse(player.isControlLocked());
        assertFalse(player.isForceInputRight());
        player.applyQueuedControlStateForFrameStart();
        assertTrue(player.isControlLocked(), "queued controls were preserved, not consumed");
        assertTrue(player.isForceInputRight());
    }

    @Test void eventPublicationRetainsImmutableEdgesWithoutWritingNativeControls() {
        NativePlayer player = new NativePlayer("main");
        player.setDirectionalInputPressed(false, false, true, false);
        player.queueControlLockedForNextFrame(true);
        player.queueForceInputRightForNextFrame(true);
        sprites.addSprite(player);
        RecordingPlayer cartridge = new RecordingPlayer("sonic");
        cartridge.queueControlLockedForNextFrame(true);
        sprites.addSprite(cartridge);
        sprites.publishHeldInputForLevelEvents(input());
        assertTrue(player.isLeftPressed());
        assertFalse(player.isRightPressed());
        assertFalse(player.isControlLocked());
        assertFalse(player.isForceInputRight());
        assertTrue(cartridge.isRightPressed(), "nonparticipants still receive event input");
        assertTrue(cartridge.isControlLocked(), "nonparticipants still consume queued controls");
        assertEquals(InputActionMasks.ACTION_B, sprites.getJoypadPressSnapshot().player1ActionPressed());
        assertTrue(sprites.getJoypadPressSnapshot().player1StartPressed());
        assertEquals(InputActionMasks.ACTION_C, sprites.getJoypadPressSnapshot().player2ActionPressed());
        assertTrue(sprites.getJoypadPressSnapshot().player2StartPressed());
        assertEquals(List.of("queued"), events);
        sprites.setInputSuppressed(true);
        sprites.publishHeldInputForLevelEvents(input());
        assertEquals(com.openggf.game.JoypadPressSnapshot.NONE, sprites.getJoypadPressSnapshot());
        assertTrue(player.isLeftPressed(), "suppression changes observations, not the native latch owner");
    }

    @Test void motionInputRepublicationAlsoLeavesNativeFieldsAndQueuedControlsAlone() {
        var runtime = new com.openggf.game.mutators.MotionMutatorRuntime();
        runtime.bind(sprites, TestEnvironment.activeGameplayMode().getGameStateManager(),
                new com.openggf.game.mutators.TransientMutatorPolicySource() {
                    public List<Entry> entries() {
                        return List.of(new Entry("fixture", 1, new com.openggf.mods.mutators.InputControlPolicy(
                                com.openggf.mods.mutators.InputControlPolicy.Mode.REVERSE_HORIZONTAL), 0, 600, false));
                    }
                    public boolean acknowledge(long id) { return true; }
                    public boolean report(long id, String status) { return true; }
                });
        NativePlayer player = new NativePlayer("main");
        player.setDirectionalInputPressed(true, false, false, false);
        player.queueControlLockedForNextFrame(true);
        sprites.addSprite(player);
        sprites.publishHeldInputForLevelEvents(input());
        sprites.prepareMotionInputForNativeTick();
        assertTrue(player.isUpPressed());
        assertFalse(player.isLeftPressed());
        assertFalse(player.isRightPressed());
        assertFalse(player.isControlLocked());
        assertEquals(InputActionMasks.ACTION_B, sprites.getJoypadPressSnapshot().player1ActionPressed());
        assertEquals(InputActionMasks.ACTION_C, sprites.getJoypadPressSnapshot().player2ActionPressed());
        assertTrue(events.isEmpty());
    }

    @Test void ordinaryCartridgeAndMovementOnlyCustomParticipantsKeepTheirTails() {
        RecordingPlayer cartridge = new RecordingPlayer("main");
        assertLegacyTick(cartridge, "movement");
        sprites.clearAllSprites();
        events.clear();
        assertLegacyTick(new CustomPlayer(), "custom");
    }

    private void assertLegacyTick(RecordingPlayer player, String movement) {
        player.queueControlLockedForNextFrame(true);
        sprites.addSprite(player);
        int history = player.getHistorySlotIndex(0);
        sprites.publishHeldInputForLevelEvents(input());
        assertTrue(player.isRightPressed());
        assertTrue(player.isControlLocked());
        events.clear();
        sprites.update(input());
        assertEquals(List.of("capture", "queued", movement, "history", "water", "display",
                "animation", "touch", "planes", "status", "end", "history", "zone"), events);
        assertEquals((history + 1) & 63, player.getHistorySlotIndex(0));
    }

    @Test void slotsShareTheActiveDeferredScopeAndFailureClosesIt() {
        NativePlayer first = new NativePlayer("main");
        NativePlayer second = new NativePlayer("sidekick");
        second.setCpuControlled(true);
        sprites.addSprite(first);
        sprites.addSprite(second);
        first.body = () -> sprites.deferCrossPlayableMutationUntilPostTick(second,
                () -> events.add("deferred"));
        second.body = () -> {
            assertFalse(events.contains("deferred"));
            throw new IllegalStateException("failed native slot");
        };
        assertThrows(IllegalStateException.class, () -> sprites.update(input()));
        assertEquals(List.of("slot:main", "slot:sidekick", "deferred"), events);
        sprites.deferCrossPlayableMutationUntilPostTick(second, () -> events.add("outside"));
        assertEquals("outside", events.getLast(), "failed slot must release the active frame scope");
        first.body = () -> { };
        second.body = () -> { };
        events.clear();
        sprites.update(input());
        assertEquals(List.of("slot:main", "slot:sidekick"), events);
    }

    @Test void unsupportedEntriesRejectBeforeAnyActorOrManagerMutation() {
        NativePlayer player = new NativePlayer("main");
        player.setAir(true);
        player.setAngle((byte) 0x31);
        player.setDirectionalInputPressed(false, false, true, false);
        player.queueControlLockedForNextFrame(true);
        sprites.addSprite(player);
        int history = player.getHistorySlotIndex(0);
        List<Runnable> entries = List.of(
                sprites::updateWithoutInput,
                () -> sprites.withScriptedInputs(Map.of("main", PlayerInputState.neutral()),
                        handler -> fail("unsupported scripted callback ran")),
                () -> sprites.updateScripted(Map.of("main", PlayerInputState.neutral())),
                () -> sprites.warmUpFreshMainPlayableOnly(1, level, player),
                () -> sprites.warmUpCpuSidekicksOnly(1, level, player),
                () -> sprites.processInitialPlayableSlots(new ProcessSpritesEpoch(6, 1, false),
                        InitialPlayableInput.nativeNeutral()),
                sprites::processInitialTailsFixedSlot,
                () -> sprites.processInitialDustFixedSlot(0),
                sprites::advancePlayableSlotPrefix,
                () -> SpriteManager.tickPlayablePhysics(player, true, true, true, true,
                        true, true, true, true, level, 9));
        for (Runnable entry : entries) {
            assertThrows(IllegalStateException.class, entry::run);
            assertEquals(6, sprites.getFrameCounter());
            assertEquals(history, player.getHistorySlotIndex(0));
            assertTrue(player.getAir());
            assertEquals(0x31, player.getAngle() & 255);
            assertTrue(player.isLeftPressed());
            assertFalse(player.isControlLocked());
            assertEquals(0, player.slots);
            assertTrue(events.isEmpty(), "rejection must precede frame-start and legacy calls");
        }
        player.applyQueuedControlStateForFrameStart();
        assertTrue(player.isControlLocked());
    }

    @Test void rawMovementOnlyEntryRejectsEvenWithoutALevelBeforeObservingActorState() {
        NativePlayer player = new NativePlayer("main");
        assertThrows(IllegalStateException.class, () -> SpriteManager.tickPlayablePhysics(player,
                false, false, false, false, false, false, false, false, null, 0));
        assertTrue(events.isEmpty());
        assertEquals(0, player.slots);
    }

    @Test void registeredNativeCpuCannotFallThroughWarmupOrFixedStockWork() {
        NativePlayer player = new NativePlayer("main");
        player.setCpuControlled(true);
        sprites.addSprite(player);
        assertThrows(IllegalStateException.class, () -> sprites.warmUpCpuSidekicksOnly(1, level));
        sprites.advancePlayableFixedSlotsAfterObjectExecution();
        sprites.refreshPlayableRenderFlags(new Camera());
        assertTrue(events.isEmpty());
        assertEquals(6, sprites.getFrameCounter());
    }

    private InputHandler input() {
        InputHandler handler = new InputHandler();
        handler.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT, 0,
                        InputActionMasks.ACTION_B, InputActionMasks.ACTION_B, true, true),
                PlayerInputState.of(0, 0, InputActionMasks.ACTION_C, InputActionMasks.ACTION_C, true, true)));
        return handler;
    }

    private class RecordingPlayer extends AbstractPlayableSprite {
        private final PlayableSpriteAnimation animation = new PlayableSpriteAnimation(this) {
            @Override public void update(int frame) { events.add("animation"); }
        };
        RecordingPlayer(String code) { super(code, (short) 0, (short) 0); }
        @Override public SpriteMovementManager getMovementManager() {
            return (up, down, left, right, jump, test, fast, slow) -> events.add("movement");
        }
        @Override public PlayableSpriteAnimation getAnimationManager() { return animation; }
        @Override public void captureOnObjectAtFrameStart() { events.add("capture"); super.captureOnObjectAtFrameStart(); }
        @Override public void applyQueuedControlStateForFrameStart() { events.add("queued"); super.applyQueuedControlStateForFrameStart(); }
        @Override public void recordFollowerHistoryForTick() { events.add("history"); super.recordFollowerHistoryForTick(); }
        @Override public void tickInvulnerabilityDisplayTimerBeforeTouchResponse() { events.add("display"); }
        @Override public void tickStatus() { events.add("status"); }
        @Override public void endOfTick() { events.add("end"); super.endOfTick(); }
        @Override protected void defineSpeeds() { }
        @Override protected void createSensorLines() {
            groundSensors = new Sensor[0]; ceilingSensors = new Sensor[0]; pushSensors = new Sensor[0];
        }
        @Override public void draw() { }
    }

    private final class NativePlayer extends RecordingPlayer implements NativePlayableSlot, CustomPlayablePhysics {
        int slots, levelFrames;
        LevelManager level;
        Runnable body = () -> { };
        NativePlayer(String code) { super(code); }
        @Override public TailsTailsController getTailsTailsController() {
            throw new AssertionError("native slot reached legacy fixed tails work");
        }
        @Override public boolean shouldRefreshRenderFlagThisFrame() {
            throw new AssertionError("native slot reached generic display bookkeeping");
        }
        @Override public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                boolean jump, boolean test, boolean fast, boolean slow, LevelManager manager, int frames) {
            fail("full-slot ownership must take precedence over movement-only customization");
        }
        @Override public void runPlayableSlot(LevelManager manager, int frames) {
            slots++; level = manager; levelFrames = frames; events.add("slot:" + getCode()); body.run();
        }
    }

    private final class CustomPlayer extends RecordingPlayer implements CustomPlayablePhysics {
        CustomPlayer() { super("main"); }
        @Override public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                boolean jump, boolean test, boolean fast, boolean slow, LevelManager manager, int frames) {
            events.add("custom");
        }
    }
}
