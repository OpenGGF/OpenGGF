package com.openggf.sprites.managers;

import com.openggf.camera.Camera;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameRng;
import com.openggf.game.GameplayInputFilter;
import com.openggf.game.GameStateManager;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.mods.code.OwnerAwareGameplayInputFilter;
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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TestSpriteManagerGameplayInputFilter {
    private GameplayModeContext gameplay;
    private SpriteManager sprites;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        gameplay = TestEnvironment.activeGameplayMode();
        sprites = new SpriteManager();
        gameplay.attachGameplayManagers(new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2),
                new DefaultSolidExecutionRegistry());
        gameplay.attachLevelManagers(new WaterSystem(), new ParallaxManager(),
                mock(TerrainCollisionManager.class), mock(CollisionSystem.class), sprites,
                mock(LevelManager.class));
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void filterSuppressesHorizontalOnceAndPreservesJumpForMovementAndEvents() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<PlayerInputState> filtered = new AtomicReference<>();
        gameplay.setGameplayInputFilter(raw -> {
            calls.incrementAndGet();
            PlayerInputState result = PlayerInputState.of(
                    raw.heldMask() & ~(AbstractPlayableSprite.INPUT_LEFT
                            | AbstractPlayableSprite.INPUT_RIGHT),
                    raw.pressedMask() & ~(AbstractPlayableSprite.INPUT_LEFT
                            | AbstractPlayableSprite.INPUT_RIGHT),
                    raw.actionHeldMask(), raw.actionPressedMask(),
                    raw.startHeld(), raw.startPressed());
            filtered.set(result);
            return result;
        });
        CapturingPlayable player = new CapturingPlayable();
        sprites.addSprite(player);
        InputHandler input = input(PlayerInputState.of(
                AbstractPlayableSprite.INPUT_LEFT,
                AbstractPlayableSprite.INPUT_LEFT,
                InputActionMasks.ACTION_B,
                InputActionMasks.ACTION_B,
                true,
                true));

        sprites.publishHeldInputForLevelEvents(input);
        assertFalse(player.isLeftPressed(), "level-event held input must use the filtered snapshot");
        sprites.update(input);

        assertFalse(player.movementLeft);
        assertTrue(player.movementJump);
        assertTrue(player.isJumpPressed());
        assertEquals(1, calls.get(), "events and movement must share one effective P1 snapshot");
        assertEquals(InputActionMasks.ACTION_B, filtered.get().actionHeldMask());
        assertEquals(InputActionMasks.ACTION_B, filtered.get().actionPressedMask());
        assertTrue(filtered.get().startHeld());
        assertTrue(filtered.get().startPressed());
    }

    @Test
    void playerInputStateReDerivesJumpFromPreservedActionMasks() {
        PlayerInputState reconstructed = PlayerInputState.of(
                0, 0, InputActionMasks.ACTION_C, InputActionMasks.ACTION_C, true, true);

        assertTrue((reconstructed.heldMask() & AbstractPlayableSprite.INPUT_JUMP) != 0);
        assertTrue((reconstructed.pressedMask() & AbstractPlayableSprite.INPUT_JUMP) != 0);
        assertEquals(InputActionMasks.ACTION_C, reconstructed.actionHeldMask());
        assertEquals(InputActionMasks.ACTION_C, reconstructed.actionPressedMask());
        assertTrue(reconstructed.startHeld());
        assertTrue(reconstructed.startPressed());
    }

    @Test
    void spriteAndContextResetRestoreIdentityFiltering() {
        GameplayInputFilter suppress = raw -> PlayerInputState.neutral();
        gameplay.setGameplayInputFilter(suppress);
        assertSame(suppress, gameplay.getGameplayInputFilter());
        assertSame(suppress, sprites.getGameplayInputFilter());

        sprites.resetState();
        assertSame(GameplayInputFilter.IDENTITY, sprites.getGameplayInputFilter());
        gameplay.tearDownManagers();
        assertSame(GameplayInputFilter.IDENTITY, gameplay.getGameplayInputFilter());
    }

    @Test
    void ownerAwareShapeRetainsDestinationOwnerWithoutChangingFilterSemantics() {
        OwnerAwareGameplayInputFilter owned = new OwnerAwareGameplayInputFilter(
                "alpha", raw -> PlayerInputState.neutral());

        assertEquals("alpha", owned.ownerModId());
        assertEquals(PlayerInputState.neutral(), owned.filter(PlayerInputState.of(
                AbstractPlayableSprite.INPUT_LEFT, 0, 0, 0, false, false)));
    }

    private static InputHandler input(PlayerInputState playerOne) {
        InputHandler input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                playerOne, PlayerInputState.neutral()));
        return input;
    }

    private static final class CapturingPlayable extends AbstractPlayableSprite
            implements CustomPlayablePhysics {
        private boolean movementLeft;
        private boolean movementJump;

        private CapturingPlayable() {
            super("main", (short) 0, (short) 0);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            movementLeft = left;
            movementJump = jump;
        }

        @Override protected void defineSpeeds() { }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override public void draw() { }
    }
}
