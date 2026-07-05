package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.rewind.InMemoryKeyframeStore;
import com.openggf.game.rewind.InputSource;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

/**
 * A special/bonus-stage entry sets a {@code GameLoop}-local "transition pending"
 * flag and starts a fade, but {@code currentGameMode} stays {@code GameMode.LEVEL}
 * until the fade's completion callback runs. Live rewind's engagement gate in
 * {@code GameLoop.stepInternal()} historically only checked
 * {@code currentGameMode == GameMode.LEVEL}, so a rewind already held when the
 * transition fires could keep walking backward through pre-transition history,
 * restoring a {@link FadeManager} snapshot whose completion callback is not
 * rewind-restorable -- orphaning the pending flag forever (softlock: everything
 * frozen except the music). See ssentry-rewind-report.md.
 */
class TestGameLoopSpecialStageRewindGate {

    private SonicConfigurationService config;
    private GameLoop loop;
    private InputHandler input;
    private FadeManager fadeManager;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        fadeManager = SessionManager.getCurrentGameplayMode().getFadeManager();
        input = new InputHandler();
        loop = new GameLoop(input);
    }

    @AfterEach
    void tearDown() {
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        SessionManager.clear();
    }

    @Test
    void heldRewindDisengagesWhenSpecialStageTransitionBecomesPending() throws Exception {
        assertHeldRewindDisengagesWhenPending("specialStageTransitionPending");
    }

    /**
     * Companion case for the S3K/S2-shared bonus-stage twin of the same bug
     * ({@code GameLoop.enterBonusStage}, same pending-flag/fade shape). Cheap to
     * cover here because {@code bonusStageTransitionPending} is a plain
     * {@code GameLoop} field usable under the same Sonic2 fixture -- no actual
     * S3K module/bonus-stage content is required to exercise the gate.
     */
    @Test
    void heldRewindDisengagesWhenBonusStageTransitionBecomesPending() throws Exception {
        assertHeldRewindDisengagesWhenPending("bonusStageTransitionPending");
    }

    private void assertHeldRewindDisengagesWhenPending(String pendingFlagField) throws Exception {
        LiveRewindManager liveRewindManager = (LiveRewindManager) getField(loop, "liveRewindManager");
        RewindController controller = new RewindController(
                new RewindRegistry(), new InMemoryKeyframeStore(), new FakeInputSource(20), in -> { }, 2);
        for (int i = 0; i < 5; i++) {
            controller.recordExternalStep();
        }
        installTestController(liveRewindManager, controller);

        int rewindKey = config.getInt(SonicConfiguration.LIVE_REWIND_KEY);
        input.handleKeyEvent(rewindKey, GLFW_PRESS);

        loop.step();
        assertEquals(4, controller.currentFrame(),
                "rewind should engage normally while genuinely rewindable (no transition pending)");
        assertTrue((boolean) getField(liveRewindManager, "rewinding"));
        assertTrue(fadeManager.isReversePresentationActive());

        // The transition fires mid-hold: currentGameMode is still LEVEL (the mode
        // only flips once the fade-to-white/black completion callback runs), but
        // the level is no longer in a rewindable sub-state.
        setField(loop, pendingFlagField, true);

        loop.step();

        assertFalse((boolean) getField(liveRewindManager, "rewinding"),
                "held rewind must cleanly disengage once a transition is pending, not keep walking "
                        + "backward through pre-transition history");
        assertEquals(4, controller.currentFrame(),
                "no further backward stepping should happen once the transition preempts rewind");
        assertFalse(fadeManager.isReversePresentationActive(),
                "the reverse-presentation fade overlay must end when rewind is preempted mid-hold");
    }

    private static void installTestController(LiveRewindManager manager, RewindController controller) throws Exception {
        setField(manager, "installedGameplayMode", SessionManager.getCurrentGameplayMode());
        setField(manager, "inputSource", new com.openggf.game.rewind.LiveRewindInputSource());
        setField(manager, "rewindController", controller);
        // RewindSpeedController is package-private (com.openggf.game.rewind); reach its
        // static factory reflectively rather than widening its visibility for this test.
        Class<?> speedControllerClass = Class.forName("com.openggf.game.rewind.RewindSpeedController");
        Method fromConfig = speedControllerClass.getDeclaredMethod("fromConfig", SonicConfigurationService.class);
        fromConfig.setAccessible(true);
        setField(manager, "speedController", fromConfig.invoke(null, SonicConfigurationService.getInstance()));
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FakeInputSource implements InputSource {
        private final int frames;

        FakeInputSource(int frames) {
            this.frames = frames;
        }

        @Override
        public int frameCount() {
            return frames;
        }

        @Override
        public Bk2FrameInput read(int frame) {
            return new Bk2FrameInput(frame, 0, 0, false, "fake");
        }
    }
}
