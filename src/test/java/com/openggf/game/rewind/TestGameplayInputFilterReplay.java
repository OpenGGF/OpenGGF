package com.openggf.game.rewind;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Sensor;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TestGameplayInputFilterReplay {
    private GameplayModeContext gameplay;
    private SpriteManager sprites;
    private CapturingPlayable player;
    private LevelManager level;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        gameplay = TestEnvironment.activeGameplayMode();
        sprites = new SpriteManager();
        gameplay.attachGameplayManagers(new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2),
                new DefaultSolidExecutionRegistry());
        level = mock(LevelManager.class);
        TerrainCollisionManager terrain = mock(TerrainCollisionManager.class);
        gameplay.attachLevelManagers(new WaterSystem(), new ParallaxManager(),
                terrain, new CollisionSystem(terrain), sprites, level);
        GameplayInputFilterAccess.install(gameplay, raw -> PlayerInputState.of(
                raw.heldMask() & ~(AbstractPlayableSprite.INPUT_LEFT
                        | AbstractPlayableSprite.INPUT_RIGHT),
                raw.pressedMask() & ~(AbstractPlayableSprite.INPUT_LEFT
                        | AbstractPlayableSprite.INPUT_RIGHT),
                raw.actionHeldMask(), raw.actionPressedMask(),
                raw.startHeld(), raw.startPressed()));
        player = new CapturingPlayable();
        sprites.addSprite(player);
        gameplay.getCamera().setFocusedSprite(player);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void rewindSeekResimulatesTheRawRecordedRowThroughTheGameplayFilter() {
        InputHandler input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT,
                        AbstractPlayableSprite.INPUT_RIGHT,
                        InputActionMasks.ACTION_A, InputActionMasks.ACTION_A,
                        false, false),
                PlayerInputState.neutral()));
        LiveRewindInputSource recorded = new LiveRewindInputSource();

        recorded.appendFrame(input, SonicConfigurationService.getInstance());
        var rawRow = recorded.read(1);
        assertTrue((rawRow.p1InputMask() & AbstractPlayableSprite.INPUT_RIGHT) != 0);
        assertTrue((rawRow.p1InputMask() & AbstractPlayableSprite.INPUT_JUMP) != 0);
        assertEquals(InputActionMasks.ACTION_A, rawRow.p1ActionMask());

        RewindController controller = new RewindController(
                gameplay.getRewindRegistry(), new InMemoryKeyframeStore(), recorded,
                new LiveRewindStepper(recorded, () -> input,
                        () -> LevelFrameContext.from(gameplay)),
                10);

        driveLiveFrame(input);
        assertTrue(controller.recordExternalStep());
        Observation firstRun = observe();

        player.movementRight = true;
        player.movementJump = false;
        controller.seekTo(0);
        assertEquals(0, controller.currentFrame());
        controller.seekTo(1);
        assertEquals(1, controller.currentFrame());
        Observation replay = observe();

        assertEquals(firstRun, replay);
        assertFalse(replay.right());
        assertTrue(replay.jump());
        assertTrue((recorded.read(1).p1InputMask() & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                "rewind replay must not replace the upstream raw input row");
        assertTrue((recorded.read(1).p1InputMask() & AbstractPlayableSprite.INPUT_JUMP) != 0);
    }

    private void driveLiveFrame(InputHandler input) {
        sprites.publishHeldInputForLevelEvents(input);
        LevelFrameStep.execute(LevelFrameContext.from(gameplay), level, gameplay.getCamera(),
                () -> sprites.update(input));
    }

    private Observation observe() {
        return new Observation(player.movementRight, player.movementJump,
                player.getLogicalInputState());
    }

    private record Observation(boolean right, boolean jump, int logicalMask) { }

    private static final class CapturingPlayable extends AbstractPlayableSprite
            implements CustomPlayablePhysics {
        private boolean movementRight;
        private boolean movementJump;

        private CapturingPlayable() {
            super("main", (short) 0, (short) 0);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            movementRight = right;
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
