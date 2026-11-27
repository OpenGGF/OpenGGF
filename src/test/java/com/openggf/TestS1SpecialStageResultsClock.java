package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageProvider;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.runs.TraceRunFrameDriver;
import com.openggf.trace.replay.runs.TraceRunPresentationClosure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
@RequiresRom(SonicGame.SONIC_1)
class TestS1SpecialStageResultsClock {
    private static final int INCOMING_CLOCK = 0x4567;

    private GameplayModeContext context;
    private ObjectManager outgoing;
    private GameLoop loop;
    private Sonic1SpecialStageProvider provider;
    private boolean previousRewindSetting;

    @BeforeEach
    void setUp() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_1);
        HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        context = SessionManager.getCurrentGameplayMode();
        var config = SonicConfigurationService.getInstance();
        previousRewindSetting = config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        outgoing = context.getLevelManager().getObjectManager();
        provider = (Sonic1SpecialStageProvider) context.getWorldSession().getGameModule().getSpecialStageProvider();
        loop = new GameLoop(new InputHandler());
        loop.doEnterSpecialStage(provider, 0, false, SpecialStageStartupPolicy.FAST);
        outgoing.initVblaCounter(INCOMING_CLOCK);
    }

    @AfterEach
    void tearDown() {
        try {
            if (loop != null) loop.closePresence();
        } finally {
            SonicConfigurationService.getInstance().setConfigValue(
                    SonicConfiguration.LIVE_REWIND_ENABLED, previousRewindSetting);
            SessionManager.clear();
        }
    }

    @Test
    void finishLoopSpendsSixtyWaitsBeforeMaskedResultsSetup() {
        int beforeFinish = armFinishLoop();
        int objectFrame = outgoing.getFrameCounter();

        for (int wait = 1; wait <= 60; wait++) {
            assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode());
            loop.step();
            assertEquals(beforeFinish + wait, outgoing.getVblaCounter(), "SS_FinLoop wait " + wait);
            assertEquals(objectFrame, outgoing.getFrameCounter());
        }
        assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode());
        assertEquals(beforeFinish + 60, outgoing.getVblaCounter(), "masked setup adds no tick");
    }

    @Test
    void rewindReplaysTheFinishWaitsOnTheRestoredClock() {
        int beforeFinish = armFinishLoop();
        var rewind = context.getRewindController();
        assertNotNull(rewind);
        rewind.resetBufferAtCurrentFrame();
        int finishEntry = rewind.currentFrame();
        for (int wait = 0; wait < 7; wait++) {
            loop.step();
        }
        assertEquals(finishEntry + 7, rewind.currentFrame());

        rewind.seekTo(finishEntry + 3);
        assertEquals(finishEntry + 3, rewind.currentFrame());
        assertEquals(beforeFinish + 3, outgoing.getVblaCounter());
    }

    private int armFinishLoop() {
        for (int row = 0; row < 128; row++) {
            if (!context.getFadeManager().isActive()
                    && provider.specialStagePlcLifecyclePhase() == PlcLifecyclePhase.SPECIAL_STAGE) {
                break;
            }
            loop.step();
        }
        assertFalse(context.getFadeManager().isActive());
        assertEquals(PlcLifecyclePhase.SPECIAL_STAGE, provider.specialStagePlcLifecyclePhase());

        // Enter one object pass before SS_Finish arms its sixty-wait loop.
        var manager = provider.getManager();
        SpecialStageLifecycleTestAccess.writeField(manager, "exitTriggered", true);
        SpecialStageLifecycleTestAccess.writeField(manager, "ssRotate", 0x17C0);
        int beforeFinish = outgoing.getVblaCounter();
        loop.step();
        assertEquals(beforeFinish, outgoing.getVblaCounter(), "arming SS_FinLoop has no finish wait");

        return beforeFinish;
    }

    @Test
    void resultsWaitsAdvanceTheParkedObjectClockAndReturnWithItsPhase() {
        var level = context.getLevelManager();
        var fade = context.getFadeManager();
        int levelFrame = level.getFrameCounter();
        int objectFrame = outgoing.getFrameCounter();
        var rng = context.getRng().capture();

        loop.doEnterResultsScreen();
        assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode());
        assertEquals(INCOMING_CLOCK, outgoing.getVblaCounter(), "masked results setup");

        // This replay closure represents a masked row with no native wait.
        TraceRunPresentationClosure.execute(loop, new TraceRunFrameDriver.Step(
                TraceRunFrameDriver.Disposition.PRESENTATION_SUPPRESSED_CLOSURE, 0,
                false, false, false, false), fade::update);
        assertEquals(INCOMING_CLOCK, outgoing.getVblaCounter(), "masked presentation closure");

        int resultsWaits = 0;
        while (!fade.hasPendingCompletion() && resultsWaits < 1500) {
            loop.step();
            resultsWaits++;
            assertEquals(INCOMING_CLOCK + resultsWaits, outgoing.getVblaCounter(),
                    "SS_NormalExit wait " + resultsWaits);
        }
        assertTrue(fade.hasPendingCompletion(), "tally reaches PaletteWhiteOut");

        // The presentation host has a deferred update before the 22 native waits.
        int beforeWhiteout = outgoing.getVblaCounter();
        loop.step();
        assertEquals(beforeWhiteout, outgoing.getVblaCounter(), "deferred fade update has no wait");
        for (int wait = 1; wait <= 22; wait++) {
            loop.step();
            assertEquals(beforeWhiteout + wait, outgoing.getVblaCounter(), "PaletteWhiteOut wait " + wait);
        }
        assertFalse(fade.hasPendingCompletion());

        for (int wait = 1; wait <= 22; wait++) {
            loop.step();
            assertEquals(beforeWhiteout + 22 + wait, outgoing.getVblaCounter(), "PaletteFadeOut wait " + wait);
            assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode());
        }
        assertEquals(levelFrame, level.getFrameCounter());
        assertEquals(objectFrame, outgoing.getFrameCounter());
        assertEquals(rng, context.getRng().capture());

        loop.step();
        assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode());
        assertEquals(beforeWhiteout + 44, outgoing.getVblaCounter(), "return setup has no wait");
        assertNotSame(outgoing, level.getObjectManager());
        assertEquals(beforeWhiteout + 44, level.getObjectManager().getVblaCounter(),
                "return load inherits the serviced clock");
        System.out.printf("S1 results clock: incoming=$%04X, tally waits=%d, fade waits=44, return=$%04X%n",
                INCOMING_CLOCK, resultsWaits, level.getObjectManager().getVblaCounter());
    }
}
