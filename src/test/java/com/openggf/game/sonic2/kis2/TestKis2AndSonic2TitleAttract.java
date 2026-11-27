package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestKis2AndSonic2TitleAttract {
    private static final int TITLE_VINTS = 640;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void launchesOnTheLastTitleVInt(boolean kis2) throws Exception {
        try (var capture = boot(kis2)) {
            advanceToTitle(capture);
            for (int i = 1; i < TITLE_VINTS; i++) {
                capture.step(null);
                assertEquals(GameMode.TITLE_SCREEN, capture.loop().getCurrentGameMode());
            }
            capture.step(null);
            assertLaunched(capture, kis2);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void playsRomInputAndReturnsToSega(boolean kis2) throws Exception {
        try (var capture = boot(kis2)) {
            launch(capture, kis2);
            int start = capture.player().getCentreX();
            for (int i = 0; i < 400; i++) capture.step(null);
            assertNotEquals(start, capture.player().getCentreX(), "ROM input moves the demo player");
            for (int i = 400; i < 1900 && !closingFadeStarted(); i++) {
                assertNotEquals(GameMode.TITLE_SCREEN, capture.loop().getCurrentGameMode());
                capture.step(null);
            }
            assertTrue(closingFadeStarted(), "The ROM timer starts the native closing fade");
            int finalMainLoopClock = GameServices.level().getFrameCounter();
            // One closing wait has elapsed. The fade executes objects without ticking
            // Level_frame_counter and returns on its 60th Do_Updates service.
            for (int i = 1; i < 59; i++) {
                capture.step(null);
                assertNotEquals(GameMode.TITLE_SCREEN, capture.loop().getCurrentGameMode());
                assertEquals(finalMainLoopClock, GameServices.level().getFrameCounter());
            }
            capture.step(null);
            assertEquals(GameMode.TITLE_SCREEN, capture.loop().getCurrentGameMode());
            assertEquals(TitleScreenProvider.State.SEGA_LOGO, capture.loop().getTitleScreenProvider().getState());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void physicalStartEndsPlaybackEarly(boolean kis2) throws Exception {
        try (var capture = boot(kis2)) {
            launch(capture, kis2);
            for (int i = 0; i < 250; i++) capture.step(null);
            capture.step(new Bk2FrameInput(250, 0, 0, true, "Start"));
            returnToTitle(capture, 80);
            assertNotEquals(TitleScreenProvider.State.SEGA_LOGO, capture.loop().getTitleScreenProvider().getState());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cyclesTheFourRomDemoLevels(boolean kis2) throws Exception {
        try (var capture = boot(kis2)) {
            int[] romZones = {0, 13, 15, 12, 0};
            for (int zone : romZones) {
                advanceToTitle(capture);
                for (int i = 0; i < TITLE_VINTS; i++) capture.step(null);
                assertNotEquals(GameMode.TITLE_SCREEN, capture.loop().getCurrentGameMode());
                for (int i = 0; i < 250; i++) capture.step(null);
                assertEquals(zone, GameServices.level().getRomZoneId());
                assertEquals(0, GameServices.level().getCurrentAct());
                capture.step(new Bk2FrameInput(250, 0, 0, true, "Start"));
                returnToTitle(capture, 80);
            }
        }
    }

    private static GameplayCaptureSession boot(boolean kis2) throws Exception {
        TestEnvironment.resetAll();
        if (kis2) {
            Kis2TestRoms.configureExplicitCatalogue(SonicConfigurationService.getInstance());
            assumeTrue(Kis2TestRoms.lockOnDumpOrNull() != null, "KiS2 lock-on dump required");
        }
        var settings = new GameplayCaptureSession.Settings(320, kis2 ? "knuckles" : "sonic", "",
                "off", null, null, null, null, true, false);
        var capture = new GameplayCaptureSession(settings);
        try {
            capture.startAtTitle();
            capture.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, settings);
            assertEquals(kis2, GameServices.module() instanceof Kis2GameModule);
            return capture;
        } catch (Exception | Error failure) {
            capture.close();
            throw failure;
        }
    }

    private static void advanceToTitle(GameplayCaptureSession capture) {
        for (int i = 0; i < 1200; i++) {
            assertEquals(GameMode.TITLE_SCREEN, capture.loop().getCurrentGameMode());
            if (capture.loop().getTitleScreenProvider().getState() == TitleScreenProvider.State.ACTIVE) return;
            capture.step(null);
        }
        fail("Title did not reach its first Vint_Title wait");
    }

    private static void launch(GameplayCaptureSession capture, boolean kis2) {
        advanceToTitle(capture);
        for (int i = 0; i < TITLE_VINTS; i++) capture.step(null);
        assertLaunched(capture, kis2);
    }

    private static void assertLaunched(GameplayCaptureSession capture, boolean kis2) {
        var mode = capture.loop().getCurrentGameMode();
        if (kis2) {
            assertTrue(mode == GameMode.LEVEL || mode == GameMode.TITLE_CARD, "KiS2 attract mode: " + mode);
            assertEquals("knuckles", capture.player().getCode());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        } else {
            assertEquals(GameMode.COMPETITION, mode);
            var roster = com.openggf.game.competition.NativeCompetitionRoster.from(GameServices.worldSession());
            assertNotNull(roster, "Stock EHZ opens the native two-player world");
            assertEquals("sonic", roster.player(0).getCode());
            assertEquals("tails", roster.player(1).getCode());
        }
    }

    private static boolean closingFadeStarted() {
        return GameServices.graphics().getPaletteFadePresentation().mode() == PaletteFadePresentation.Mode.TO_BLACK;
    }

    private static void returnToTitle(GameplayCaptureSession capture, int limit) {
        for (int i = 0; i < limit && capture.loop().getCurrentGameMode() != GameMode.TITLE_SCREEN; i++) {
            capture.step(null);
        }
        assertEquals(GameMode.TITLE_SCREEN, capture.loop().getCurrentGameMode());
    }
}
