package com.openggf.game.sonic2.titlescreen;

import com.openggf.audio.NullAudioBackend;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.graphics.RgbaImage;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static com.openggf.tests.reel.TestReelCapture.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 3584M/5735S: the final credit-text P2 edge survives TitleScreen's P1-only clear.
 * Native input-copy/load/object and palette owners are independently qualified in
 * docs/architecture/validation/2026-10-28-tas-support-intro.md. These are reached
 * engine phase checks, not a claim that a Gens movie row is an engine tick.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2IntroP2Start {
    private static final GameplayCaptureSession.Settings SETTINGS =
            new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
    private static final Bk2FrameInput P2_START =
            new Bk2FrameInput(0, 0, 0, false, 0, 0, true, "P2 Start");
    private static final Bk2FrameInput P1_START =
            new Bk2FrameInput(0, 0, 0, true, 0, 0, false, "P1 Start");

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void p2FinalTextSampleLetsTheFirstTitleLoopStartLaunch() throws Exception {
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            var title = boot(session);
            reachFinalTextSample(session, title, null);
            step(session, P2_START);
            assertEquals(TitleScreenProvider.State.FADE_IN, title.getState());
            assertTrue((boolean) value(title, "introComplete"),
                    "TitleScreen clears only P1 before the initial Obj0E update; P2 must complete the intro there");
            assertFalse((boolean) value(title, "sonicPaletteLoaded"),
                    "the following global fade clears the skip owner's normal-palette writes");
            assertFalse(((boolean[]) value(title, "sparklePlayedAt"))[0],
                    "the early final-state branch bypasses Obj0E_Sonic_Init's sparkle");
            finishTitleFade(session, title, null);
            step(session, P2_START);
            assertLaunchedFirstAct(session);
        }
    }

    @Test
    void lastTextSampleDoesNotShortenEitherPaletteFade() throws Exception {
        assertEquals(23, toTitle(false), "initial object update plus 22 palette-fade iterations");
        assertEquals(23, toTitle(true), "the one-frame saving belongs to later Start eligibility, not a shorter fade");
    }

    enum Control { NEUTRAL, P1, EARLY_P2, LATE_P2, HELD_P2 }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Control.class)
    void otherSamplesNeedSeparateSkipAndLaunchEdges(Control control) throws Exception {
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            var title = boot(session);
            reachFinalTextSample(session, title,
                    control == Control.EARLY_P2 || control == Control.HELD_P2 ? P2_START : null);
            step(session, switch (control) {
                case P1 -> P1_START;
                case HELD_P2 -> P2_START;
                default -> null;
            });
            assertFalse((boolean) value(title, "introComplete"), "only a fresh final-sample P2 edge owns the early skip");
            assertTrue(((boolean[]) value(title, "sparklePlayedAt"))[0], "ordinary initialization must still run");
            finishTitleFade(session, title, control == Control.LATE_P2 ? P2_START : null);
            assertFalse((boolean) value(title, "introComplete"), "palette fades sample input without executing title objects");
            step(session, P2_START);
            assertEquals(GameMode.TITLE_SCREEN, session.loop().getCurrentGameMode(),
                    "the first title-loop Start is consumed by the ordinary intro skip");
            assertTrue((boolean) value(title, "introComplete"));
            assertTrue((boolean) value(title, "sonicPaletteLoaded"), "a post-fade skip retains its normal palettes");
            step(session, P1_START);
            assertLaunchedFirstAct(session);
        }
    }

    @Test
    void lostNormalPalettesKeepTailsVisibleAndDoNotLeakAcrossReusedTitleLifetimes() throws Exception {
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            var title = boot(session);
            var backend = assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            // Independent ROM mapping: all 13 pieces of native Tails frame4 use
            // palette1, the sole target line loaded before the global fade.
            var rom = GameServices.rom().getRom();
            int frame = 0x136A8 + rom.read16BitAddr(0x136A8 + 4 * 2);
            assertEquals(13, rom.read16BitAddr(frame));
            for (int piece = 0; piece < 13; piece++) {
                assertEquals(1, (rom.read16BitAddr(frame + 4 + piece * 8) >>> 13) & 3);
            }
            reachFinalTextSample(session, title, null);
            step(session, P2_START);
            finishTitleFade(session, title, null);
            RgbaImage corrupted = session.render();
            assertCorruptedTitlePixels(corrupted);

            // Reuse the actual provider, ROM art and GL context. A fresh JVM or
            // data loader cannot hide a stale black palette upload.
            title.reset();
            title.initialize();
            assertSame(title, session.loop().getTitleScreenProvider());
            reachFinalTextSample(session, title, null);
            step(session, P1_START);
            finishTitleFade(session, title, null);
            step(session, P2_START);
            assertEquals(GameMode.TITLE_SCREEN, session.loop().getCurrentGameMode());
            RgbaImage ordinary = session.render();
            assertTrue(nonBlack(ordinary, 128) > 5000, "ordinary post-fade skip must restore the main title and background");

            title.reset();
            title.initialize();
            reachFinalTextSample(session, title, null);
            step(session, P2_START);
            finishTitleFade(session, title, null);
            RgbaImage repeated = session.render();
            assertCorruptedTitlePixels(repeated);
            assertArrayEquals(corrupted.pixels(), repeated.pixels(), "reused boot must reproduce the same palette/object ownership");
            assertSame(backend, GameServices.audio().getBackend(), "title reentry must retain its device-free backend");
        }
    }

    private static TitleScreenManager boot(GameplayCaptureSession session) throws Exception {
        session.startAtTitle();
        session.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, SETTINGS);
        return assertInstanceOf(TitleScreenManager.class, session.loop().getTitleScreenProvider());
    }

    private static int toTitle(boolean pressP2) throws Exception {
        TestEnvironment.resetAll();
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            var title = boot(session);
            reachFinalTextSample(session, title, null);
            step(session, pressP2 ? P2_START : null);
            int elapsed = 1;
            while (title.getState() != TitleScreenProvider.State.ACTIVE && elapsed < 100) {
                step(session, null);
                elapsed++;
            }
            assertTrue(elapsed < 100, "must naturally reach the interactive title owner");
            return elapsed;
        }
    }

    private static void reachFinalTextSample(GameplayCaptureSession session, TitleScreenManager title,
            Bk2FrameInput penultimateSample) throws Exception {
        int ticks = 0;
        while (title.getState() != TitleScreenProvider.State.INTRO_TEXT_FADE_OUT && ticks++ < 1000) {
            step(session, null);
        }
        assertTrue(ticks < 1000, "must naturally reach the text fade owner");
        for (int fade = 0; fade < 20; fade++) {
            assertEquals(TitleScreenProvider.State.INTRO_TEXT_FADE_OUT, title.getState());
            step(session, null);
        }
        step(session, penultimateSample);
        assertEquals(21, value(title, "introTextTimer"));
    }

    private static void finishTitleFade(GameplayCaptureSession session, TitleScreenManager title,
            Bk2FrameInput firstSample) throws Exception {
        assertEquals(1, value(title, "frameCounter"), "initial Obj0E update precedes the palette owner");
        for (int fade = 0; fade < 22; fade++) {
            assertEquals(TitleScreenProvider.State.FADE_IN, title.getState());
            step(session, fade == 0 ? firstSample : null);
            assertEquals(1, value(title, "frameCounter"), "palette fading must not execute title objects");
            assertEquals(-0x280, value(title, "cameraX"), "the palette owner does not scroll the title");
        }
        assertEquals(TitleScreenProvider.State.ACTIVE, title.getState());
    }

    private static void assertLaunchedFirstAct(GameplayCaptureSession session) {
        assertEquals(TitleScreenProvider.State.EXITING, session.loop().getTitleScreenProvider().getState(),
                "the native input owner must commit the launch on this title iteration");
        // GameLoopNativeMenus retains TITLE_SCREEN until its normal fade
        // callback installs the level. Do not mistake that host presentation
        // lifetime for another opportunity to skip or accept Start.
        int fadeFrames = 0;
        while (session.loop().getCurrentGameMode() == GameMode.TITLE_SCREEN && fadeFrames++ < 100) {
            step(session, null);
        }
        assertNotEquals(GameMode.TITLE_SCREEN, session.loop().getCurrentGameMode());
        assertNotNull(session.player(), "the actual title launch must install a playable participant");
        assertEquals(0, GameServices.level().getCurrentZone());
        assertEquals(0, GameServices.level().getCurrentAct());
    }

    private static void assertCorruptedTitlePixels(RgbaImage image) {
        int visible = nonBlack(image, 0);
        assertTrue(visible > 1000 && visible < 6000, "Tails must remain drawable through surviving palette1");
        assertEquals(0, nonBlack(image, 128), "the main title, menu and lower background lost their normal palettes");
    }

    private static int nonBlack(RgbaImage image, int firstRow) {
        int count = 0;
        for (int y = firstRow; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if ((image.argb(x, y) & 0xFFFFFF) != 0) count++;
            }
        }
        return count;
    }

    private static Object value(TitleScreenManager title, String name) throws Exception {
        Field field = TitleScreenManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(title);
    }
}
