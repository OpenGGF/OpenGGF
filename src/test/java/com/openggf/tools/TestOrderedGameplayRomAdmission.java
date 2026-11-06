package com.openggf.tools;

import com.openggf.InputBindingFactory;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import java.util.ArrayList;
import java.util.List;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Original ROM assets and real GameLoop/native movement owners, with display-free EGL. */
class TestOrderedGameplayRomAdmission {
    @Test @RequiresRom(SonicGame.SONIC_2)
    void s2ReleasedKeyboardTapReachesNativeJumpAndThenRelease() throws Exception {
        jump(RomTestUtils.ensureSonic2RomAvailable().toPath());
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void s3kReleasedKeyboardTapReachesNativeJumpAndThenRelease() throws Exception {
        jump(RomTestUtils.ensureSonic3kRomAvailable().toPath());
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void controlledKeyboardResumeRetiresReleasedPausedTapBeforeItsNativeRow() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0, settings);
            int setup = 0;
            do {
                session.step(null); session.renderFrame();
                assertTrue(++setup < 600, "production entry did not complete");
            } while (session.loop().getCurrentGameMode() != GameMode.LEVEL
                    || GameServices.level().hasPendingFreshLevelTransitionBoundary()
                    || session.player().getAir() || session.player().isControlLocked());
            var world = SessionManager.getCurrentGameplayMode().getWorldSession();
            var base = world.getGameModule();
            var rows = new ArrayList<PlayerInputState>();
            var module = new DelegatingGameModule(base, "test:ordered-controlled") {
                @Override public GameplayFrameController gameplayFrameController() {
                    return controller;
                }
                private final GameplayFrameController controller =
                        new GameplayFrameController() {
                    public boolean beforeTick(CourseControl c,
                            LogicalInputSnapshot s) { return true; }
                    public GameplayFrameDisposition beforeFrame(
                            CourseControl c, LogicalInputSnapshot s) {
                        rows.add(s.player1());
                        return GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
                    }
                    public void afterTick(CourseControl c, boolean advanced) { }
                };
            };
            var field = world.getClass().getDeclaredField("gameModule");
            field.setAccessible(true); field.set(world, module);
            var config = EngineServices.current().configuration();
            config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
            config.setConfigValue(SonicConfiguration.PAUSE_KEY, GLFW_KEY_F9);
            var input = new InputHandler(InputBindingFactory.supplier(config));
            session.loop().setInputHandler(input);
            int frame = GameServices.sprites().getFrameCounter();
            input.handleKeyEvent(GLFW_KEY_F9, GLFW_PRESS);
            session.loop().step();
            assertTrue(session.loop().isUserPaused());
            input.handleKeyEvent(GLFW_KEY_F9, GLFW_RELEASE);
            session.loop().step();
            assertTrue(rows.isEmpty(), "paused service must not enter the controller");
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
            input.handleKeyEvent(GLFW_KEY_F9, GLFW_PRESS);
            session.loop().step(); // No service between the released paused tap and resume.
            assertFalse(session.loop().isUserPaused());
            assertEquals(frame + 1, GameServices.sprites().getFrameCounter());
            assertEquals(List.of(PlayerInputState.neutral()), rows,
                    "the resumed controller/native row must not see the retired paused tap");
            assertEquals(0, GameServices.sprites().getJoypadPressSnapshot().player1ActionPressed());
            assertFalse(session.player().getAir(), "the retired tap cannot initiate a native jump");
            session.renderFrame();
        }
    }

    private void jump(Path rom) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 0, 0, settings);
            var level = GameServices.level();
            int setup = 0;
            do {
                session.step(null); session.renderFrame();
                assertTrue(++setup < 600, "production entry/landing did not complete");
            } while (session.loop().getCurrentGameMode() != GameMode.LEVEL
                    || level.hasPendingFreshLevelTransitionBoundary() || session.player().getAir()
                    || session.player().isControlLocked());
            var config = EngineServices.current().configuration();
            config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
            var input = new InputHandler(InputBindingFactory.supplier(config));
            session.loop().setInputHandler(input);
            int frame = GameServices.sprites().getFrameCounter();
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
            assertFalse(input.isPhysicalKeyDown(GLFW_KEY_SPACE));
            assertEquals(2, input.capturePhysicalInput().events().size());
            try (var host = InputHandlerInternalAccess.openHostInputPass(input)) {
                input.refreshLogicalSnapshot();
                host.handoff(session.loop()::step);
            }
            assertEquals(frame + 1, GameServices.sprites().getFrameCounter());
            assertEquals(InputActionMasks.ACTION_A, input.logical().player1().actionPressedMask());
            assertEquals(InputActionMasks.ACTION_A, input.logical().player1().actionHeldMask());
            assertTrue(session.player().getAir(), "a released physical tap must initiate the native jump");
            assertTrue(session.player().getYSpeed() < 0);
            var movement = assertInstanceOf(com.openggf.sprites.managers.PlayableSpriteMovement.class,
                    session.player().getMovementManager());
            assertFalse(movement.captureRewindState().jumpReleasedSinceJump());
            int jumpSpeed = session.player().getYSpeed();
            session.loop().step();
            assertEquals(frame + 2, GameServices.sprites().getFrameCounter());
            assertEquals(PlayerInputState.neutral(), input.logical().player1());
            assertTrue(movement.captureRewindState().jumpReleasedSinceJump(),
                    "the native movement owner must observe release, rather than merely apply gravity");
            assertTrue(session.player().getYSpeed() > jumpSpeed);
            session.renderFrame();

            config.setConfigValue(SonicConfiguration.PAUSE_KEY, GLFW_KEY_F9);
            input.handleKeyEvent(GLFW_KEY_F9, GLFW_PRESS);
            session.loop().step();
            assertTrue(session.loop().isUserPaused(), "actual pause-key callback must enter pause");
            assertEquals(frame + 2, GameServices.sprites().getFrameCounter());
            input.handleKeyEvent(GLFW_KEY_F9, GLFW_RELEASE);
            session.loop().step(); // Retire the pause key before the paused tap interval.
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
            input.handleKeyEvent(GLFW_KEY_F9, GLFW_PRESS);
            session.loop().step(); // No paused service between the tap and resume callback.
            assertFalse(session.loop().isUserPaused());
            assertEquals(frame + 3, GameServices.sprites().getFrameCounter());
            assertEquals(PlayerInputState.neutral(), input.logical().player1(),
                    "keyboard resume must retire taps collected while paused before admitting its tick");
            input.handleKeyEvent(GLFW_KEY_F9, GLFW_RELEASE);

            config.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, GLFW_KEY_F8);
            session.loop().toggleUserPause();
            input.handleKeyEvent(GLFW_KEY_F8, GLFW_PRESS);
            session.loop().step();
            assertEquals(frame + 4, GameServices.sprites().getFrameCounter(), "paused frame-step admits exactly one tick");
            input.handleKeyEvent(GLFW_KEY_F8, GLFW_RELEASE);
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
            session.loop().step();
            assertEquals(frame + 4, GameServices.sprites().getFrameCounter(), "ordinary paused service admits no tick");
            session.loop().toggleUserPause();
            session.loop().step();
            assertEquals(frame + 5, GameServices.sprites().getFrameCounter());
            assertEquals(PlayerInputState.neutral(), input.logical().player1(), "paused taps cannot attack after resume");
        }
    }
}
