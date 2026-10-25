package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.RomGame;
import com.openggf.data.RomManager;
import com.openggf.game.GameMode;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.RomCatalogueFixture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A real Sonic 2 level held with Escape: it parks behind the master title, hub frames do not
 * advance it, and it resumes exactly where it stopped.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestGameLoopSuspension {
    private HeadlessTestFixture fixture;
    private RomCatalogueFixture romCatalogue;
    private GameplayModeContext gameplay;
    private InputHandler input;
    private GameLoop loop;
    private final AtomicInteger suspends = new AtomicInteger();
    private final AtomicInteger fullReturns = new AtomicInteger();

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var image = RomTestUtils.ensureSonic2RomAvailable();
        assertNotNull(image, "the suspension and recording fixture requires the Sonic 2 ROM");
        romCatalogue = RomCatalogueFixture.bind(SonicConfigurationService.getInstance(),
                RomManager.getInstance(), Map.of(RomGame.S2, image.toPath()));
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        gameplay = SessionManager.getCurrentGameplayMode();
        input = new InputHandler();
        loop = new GameLoop(input);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        loop.setReturnToMasterTitleHandler(fullReturns::incrementAndGet);
        var effects = SuspensionTestAccess.effects();
        when(effects.capture().publishedWorld()).thenAnswer(ignored -> SessionManager.getCurrentWorldSession());
        org.mockito.Mockito.doAnswer(ignored -> {
            suspends.incrementAndGet();
            return org.mockito.Mockito.mock(com.openggf.game.launch.SuspendedSettings.class);
        }).when(effects.capture()).suspendSettings();
        loop.suspension.setEffects(effects.binding());
    }

    @AfterEach
    void tearDown() {
        try {
            SessionManager.clear();
        } finally {
            if (romCatalogue != null) romCatalogue.close();
        }
    }

    @Test
    void parkedLevelIsUntouchedByHubFramesAndResumesWhereItStopped() {
        int right = loop.configService.getInt(SonicConfiguration.RIGHT);
        input.handleKeyEvent(right, GLFW_PRESS);
        steps(90);
        input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS);
        for (int n = 0; n < 600 && loop.getCurrentGameMode() != GameMode.MASTER_TITLE_SCREEN; n++) {
            loop.step();
            loop.suspension.completePreparedSuspend(); // renderer has captured this frame
        }
        input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_RELEASE);
        input.handleKeyEvent(right, GLFW_RELEASE);

        assertEquals(GameMode.MASTER_TITLE_SCREEN, loop.getCurrentGameMode(), "the hold reached the hub");
        assertEquals(1, suspends.get());
        assertEquals(0, fullReturns.get(), "a stock level suspends rather than tearing down");
        SuspendedGame saved = SuspensionTestAccess.get(loop.suspension, "suspendedGame");
        assertEquals(GameMode.LEVEL, saved.mode(), "park reports the mode it left, for resume");
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

        when(hub.consumeResumeRequest()).thenReturn(true, false);
        loop.suspension.routeResume(hub); // Engine's between-frame handoff after the zoom.
        assertFalse(loop.resolveFadeManager().isActive(), "resume does not start another fade");
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        assertSame(gameplay, loop.resolveGameplayModeContext());
        assertEquals(parked, snapshot(), "resuming itself changes nothing");
        long timer = gameplay.getLevelManager().getLevelGamestate().getTimerFrames();
        steps(30);
        assertTrue(gameplay.getLevelManager().getLevelGamestate().getTimerFrames() > timer,
                "the resumed level runs again");
    }

    @Test
    void waitingForTheFinalPictureFreezesGameplayWithoutAFade() {
        steps(30);
        loop.suspension.leave();
        assertTrue(loop.suspension.preparing());
        assertFalse(loop.resolveFadeManager().isActive());
        List<Object> frozen = snapshot();
        steps(60);
        assertEquals(frozen, snapshot(), "rendering must not allow further gameplay steps");
        assertEquals(0, suspends.get(), "park only after capturing the picture");
        loop.suspension.completePreparedSuspend();
        loop.suspension.completePreparedSuspend();
        assertEquals(1, suspends.get());
        assertEquals(GameMode.MASTER_TITLE_SCREEN, loop.getCurrentGameMode());
        assertEquals(frozen, snapshot());
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
    void rewindSwitchedOnMidLevelRecordsFromThenAndRewinds() {
        var config = loop.configService;
        boolean before = config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED);
        try {
            config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
            int right = config.getInt(SonicConfiguration.RIGHT);
            input.handleKeyEvent(right, GLFW_PRESS);
            steps(30);
            int enabledAt = fixture.sprite().getCentreX();

            config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true); // what resume applies
            steps(90);
            input.handleKeyEvent(right, GLFW_RELEASE);
            int ranTo = fixture.sprite().getCentreX();
            assertTrue(ranTo > enabledAt + 16, "Sonic kept running after rewind was switched on");

            int rewindKey = config.getInt(SonicConfiguration.LIVE_REWIND_KEY);
            input.handleKeyEvent(rewindKey, GLFW_PRESS);
            steps(240);
            input.handleKeyEvent(rewindKey, GLFW_RELEASE);
            steps(2);

            int rewoundTo = fixture.sprite().getCentreX();
            assertTrue(rewoundTo < ranTo - 16, "holding rewind stepped back: " + ranTo + " -> " + rewoundTo);
            assertTrue(rewoundTo >= enabledAt - 2,
                    "history starts when rewind was switched on, not at the level start: " + rewoundTo);
        } finally {
            config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, before);
        }
    }

    @Test
    void aspectChangedMidLevelResizesTheCameraLevelAndParallax() {
        var config = loop.configService;
        steps(10);
        try {
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
            config.resolveDisplayAspect();
            // Engine.applyResolvedDisplayDimensions' runtime half, as on resume.
            gameplay.getCamera().refreshViewportDimensions(config);
            com.openggf.level.LevelDisplayWidth.refresh(gameplay.getLevelManager());

            assertEquals(400, gameplay.getCamera().getWidth());
            assertEquals(25, com.openggf.level.LevelDisplayWidth.parallaxColumns(com.openggf.game.GameServices.parallax()),
                    "a per-column VScroll entry for every 16 pixels of the wider picture");
            int right = config.getInt(SonicConfiguration.RIGHT);
            input.handleKeyEvent(right, GLFW_PRESS);
            int x = fixture.sprite().getCentreX();
            steps(120);
            input.handleKeyEvent(right, GLFW_RELEASE);
            assertTrue(fixture.sprite().getCentreX() > x, "the level runs on at the new width");
        } finally {
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
            config.resolveDisplayAspect();
            gameplay.getCamera().refreshViewportDimensions(config);
            com.openggf.level.LevelDisplayWidth.refresh(gameplay.getLevelManager());
        }
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
