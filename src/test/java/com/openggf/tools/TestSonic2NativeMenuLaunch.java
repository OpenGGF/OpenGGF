package com.openggf.tools;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.options.Sonic2NativeModeProvider;
import com.openggf.game.sonic2.titlescreen.TitleScreenManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN;
import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual title, ROM menu, runtime replacement and production draw owner. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2NativeMenuLaunch {
    @Test
    void optionsLaunchKeepsEmeraldsAndSelectedTeamWhileTitleLaunchClearsProgress() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.startAtTitle();
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0, settings);
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            var configuredMain = GameServices.configuration().getString(SonicConfiguration.MAIN_CHARACTER_CODE);
            var oldWorld = SessionManager.getCurrentWorldSession();
            var oldPlayer = session.player();
            GameServices.gameState().markEmeraldCollected(2);
            GameServices.gameState().consumeCurrentSpecialStageIndexAndAdvance();
            GameServices.gameState().addScore(1230);

            enterOptions(session);
            var modes = GameServices.module().getGameService(Sonic2NativeModeProvider.class);
            press(session, INPUT_RIGHT, false);
            press(session, INPUT_RIGHT, false);
            assertEquals(2, modes.optionsState().playerOption());
            assertNonblank(session.render());
            press(session, 0, true);
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertNotSame(oldWorld, SessionManager.getCurrentWorldSession());
            assertNotSame(oldPlayer, session.player(), "capture follows the replacement participant");
            assertEquals("tails", session.player().getCode());
            assertSame(session.player(), GameServices.sprites().getMainPlayable());
            assertSame(session.player(), GameServices.camera().getFocusedSprite());
            assertTrue(GameServices.sprites().getSidekicks().isEmpty());
            assertTrue(GameServices.gameState().hasEmerald(2));
            assertEquals(1, GameServices.gameState().getCurrentSpecialStageIndex());
            assertEquals(0, GameServices.gameState().getScore());
            assertEquals(configuredMain, GameServices.configuration().getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            for (int i = 0; i < 40; i++) session.step(null);
            assertVisiblePlayer(session);

            // Start on sound test follows the native SEGA path. The next title
            // ONE PLAYER retains Player_option but clears the emerald inventory.
            session.loop().initializeTitleScreenMode();
            enterOptions(session);
            assertSame(modes, GameServices.module().getGameService(Sonic2NativeModeProvider.class));
            assertEquals(2, modes.optionsState().playerOption());
            press(session, INPUT_DOWN, false);
            press(session, INPUT_DOWN, false);
            press(session, 0, true);
            assertEquals(GameMode.TITLE_SCREEN, session.loop().getCurrentGameMode());
            waitForTitle(session);
            press(session, 0, true);
            for (int i = 0; i < 500 && session.loop().getCurrentGameMode() != GameMode.LEVEL; i++) {
                session.step(null);
            }
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertEquals("tails", session.player().getCode());
            assertEquals(0, GameServices.gameState().getEmeraldCount());
            assertEquals(0, GameServices.gameState().getCurrentSpecialStageIndex());
            assertEquals(configuredMain, GameServices.configuration().getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        }
    }

    private static void enterOptions(GameplayCaptureSession session) {
        waitForTitle(session);
        press(session, INPUT_DOWN, false);
        assertEquals(2, ((TitleScreenManager) session.loop().getTitleScreenProvider()).getNativeMenuSelection());
        press(session, 0, true);
        assertEquals(GameMode.OPTIONS, session.loop().getCurrentGameMode());
        for (int i = 0; i < 30; i++) session.step(null);
    }

    private static void waitForTitle(GameplayCaptureSession session) {
        for (int i = 0; i < 1000
                && session.loop().getTitleScreenProvider().getState() != TitleScreenProvider.State.ACTIVE; i++) {
            session.step(null);
        }
        assertEquals(TitleScreenProvider.State.ACTIVE, session.loop().getTitleScreenProvider().getState());
        // One Start skips the remaining native character intro; it cannot also launch.
        press(session, 0, true);
        assertEquals(GameMode.TITLE_SCREEN, session.loop().getCurrentGameMode());
    }

    private static void press(GameplayCaptureSession session, int directions, boolean start) {
        session.step(new Bk2FrameInput(0, directions, 0, start, 0, 0, false, "native-menu-test"));
        session.step(null);
    }

    private static void assertNonblank(RgbaImage frame) {
        int color = frame.argb(0, 0);
        int different = 0;
        for (int y = 0; y < frame.height(); y++) for (int x = 0; x < frame.width(); x++) {
            if (frame.argb(x, y) != color) different++;
        }
        assertTrue(different > 1000, "the native screen must draw ROM pixels");
    }

    private static void assertVisiblePlayer(GameplayCaptureSession session) {
        var tiles = session.render(false);
        var full = session.render(true);
        int sx = session.player().getRenderCentreX() - GameServices.camera().getX();
        int sy = session.player().getRenderCentreY() - GameServices.camera().getY();
        int changed = 0;
        for (int y = Math.max(0, sy - 24); y < Math.min(full.height(), sy + 24); y++) {
            for (int x = Math.max(0, sx - 24); x < Math.min(full.width(), sx + 24); x++) {
                if (full.argb(x, y) != tiles.argb(x, y)) changed++;
            }
        }
        assertTrue(changed > 50, "replacement playable pixels expected, changed=" + changed);
    }
}
