package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.mockito.Mockito.mock;

/**
 * A real Sonic 2 level held with Escape: it parks behind the master title, hub frames do not
 * advance it, and it resumes exactly where it stopped.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestGameLoopSuspension {
    private HeadlessTestFixture fixture;
    private GameplayModeContext gameplay;
    private InputHandler input;
    private GameLoop loop;
    private final AtomicInteger suspends = new AtomicInteger();
    private final AtomicInteger fullReturns = new AtomicInteger();
    private GameMode parkedFrom;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        gameplay = SessionManager.getCurrentGameplayMode();
        input = new InputHandler();
        loop = new GameLoop(input);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        loop.setReturnToMasterTitleHandler(fullReturns::incrementAndGet);
        loop.suspension.setHost(new GameLoopSuspension.Host() {
            @Override public boolean prepareSuspend(GameMode mode) { return true; }
            @Override public void suspend() { suspends.incrementAndGet(); parkedFrom = loop.suspension.park(); }
            @Override public void resume() { loop.suspension.unpark(parkedFrom); }
        });
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void parkedLevelIsUntouchedByHubFramesAndResumesWhereItStopped() {
        int right = loop.configService.getInt(SonicConfiguration.RIGHT);
        input.handleKeyEvent(right, GLFW_PRESS);
        steps(90);
        input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS);
        for (int n = 0; n < 600 && loop.getCurrentGameMode() != GameMode.MASTER_TITLE_SCREEN; n++) loop.step();
        input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_RELEASE);
        input.handleKeyEvent(right, GLFW_RELEASE);

        assertEquals(GameMode.MASTER_TITLE_SCREEN, loop.getCurrentGameMode(), "the hold reached the hub");
        assertEquals(1, suspends.get());
        assertEquals(0, fullReturns.get(), "a stock level suspends rather than tearing down");
        assertEquals(GameMode.LEVEL, parkedFrom, "park reports the mode it left, for resume");
        assertSame(gameplay, SessionManager.getCurrentGameplayMode(), "the session stays loaded");
        assertNull(loop.resolveGameplayModeContext(), "GameLoop runs the hub as if no game were bound");
        assertFalse(loop.ownsGameplayFadeLifecycle(), "the hub's fades run on the bootstrap manager");
        List<Object> parked = snapshot();
        assertTrue(fixture.sprite().getCentreX() > 0x60, "Sonic ran before the hold, so the state is not trivial");

        MasterTitleScreen hub = mock(MasterTitleScreen.class);
        loop.setMasterTitleScreenSupplier(() -> hub);
        input.handleKeyEvent(right, GLFW_PRESS); // input in the hub must not reach the parked level
        steps(300);
        input.handleKeyEvent(right, GLFW_RELEASE);
        assertEquals(parked, snapshot(), "hub frames leave the parked level exactly as it was");

        loop.suspension.unpark(parkedFrom);
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        assertSame(gameplay, loop.resolveGameplayModeContext());
        assertEquals(parked, snapshot(), "resuming itself changes nothing");
        long timer = gameplay.getLevelManager().getLevelGamestate().getTimerFrames();
        steps(30);
        assertTrue(gameplay.getLevelManager().getLevelGamestate().getTimerFrames() > timer,
                "the resumed level runs again");
    }

    @Test
    void musicCapturedBeforeTheFadeComesBackOnResume() {
        var audio = com.openggf.game.session.EngineServices.current().audio();
        audio.playMusic(com.openggf.game.sonic2.constants.Sonic2AudioConstants.MUS_EMERALD_HILL);
        // The fixture presents one audio frame per step, as Engine does after GameLoop.step();
        // the song plays for half a second so the snapshot holds a real position.
        for (int n = 0; n < 30; n++) fixture.stepFrame(false, false, false, false, false);
        var beforeFade = audio.captureLogicalSnapshot();
        assertNotNull(beforeFade.presentation().activeMusic(), "the level's zone music is playing");
        int zoneMusic = beforeFade.presentation().activeMusic().musicId();

        audio.fadeOutMusic(); // what the hold's fade-out does
        audio.stopMusic();
        audio.playSfx(MasterTitleScreen.AudioCue.CONFIRM.sfxName()); // a hub cue
        for (int n = 0; n < 10; n++) fixture.stepFrame(false, false, false, false, false);
        var stopped = audio.captureLogicalSnapshot().presentation().activeMusic();
        assertTrue(stopped == null || stopped.musicId() != zoneMusic, "the fixture really stopped the music");

        audio.restoreLogicalSnapshot(beforeFade);

        var resumed = audio.captureLogicalSnapshot().presentation();
        assertNotNull(resumed.activeMusic());
        assertEquals(zoneMusic, resumed.activeMusic().musicId());
        assertEquals(beforeFade.presentation().activeMusic(), resumed.activeMusic(),
                "the same slot, including its position and volume, not a restarted song");
    }

    @Test
    void escapeHeldDuringAnActiveRecordingKeepsTheFullReturn() {
        loop.userRecordingSessionLauncher.beginRecordingFromCurrentLevel();
        assertFalse(loop.suspension.suspendable(), "a recording owns its own ending");
    }

    @Test
    void discardEndsTheParkedRunsStateSoTheNormalTeardownCanClose() {
        loop.suspension.park();
        loop.suspension.discard();

        assertFalse(loop.suspension.parked());
        assertSame(gameplay, loop.resolveGameplayModeContext(),
                "discard leaves closing the session to the host's normal teardown");
    }

    private void steps(int frames) {
        for (int n = 0; n < frames; n++) loop.step();
    }

    private List<Object> snapshot() {
        AbstractPlayableSprite sonic = fixture.sprite();
        var level = gameplay.getLevelManager().getLevelGamestate();
        return List.of(sonic.getCentreX(), sonic.getCentreY(), sonic.getXSpeed(), sonic.getGSpeed(),
                gameplay.getCamera().getX(), gameplay.getCamera().getY(),
                level.getTimerFrames(), level.getRings());
    }
}
