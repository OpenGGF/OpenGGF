package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The level fade-in reaches the picture with the V-int that publishes its frame's sprite table.
 *
 * <p>{@code Level/loc_64DC} (sonic3k.asm:7874-7878) runs {@code Pal_FillBlack} and arms
 * {@code Palette_fade_timer = $16}; each {@code LevelLoop} frame's {@code Animate_Palette}
 * (5018-5028) applies one {@code Pal_FromBlack} pass to {@code Normal_palette}, and only the next
 * {@code VInt_8} copies {@code Normal_palette} to CRAM beside the sprite table and scroll buffers.
 * Native HPZ entry frames show it: the release frame and the first LevelLoop frame have the same
 * playfield (seq 130/131), and the first fade pass appears one frame later (seq 132). The engine
 * presents each step's V-int publication, so a fade pass requested by a step's logic must appear
 * in the next step's picture, not in its own.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLevelFadeCramPublication {
    // Playfield rows/columns clear of the HUD (left column, top rows) and the lives icon.
    private static final int X0 = 48, X1 = 320, Y0 = 60, Y1 = 140;

    @AfterEach
    void tearDown() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
        SessionManager.clear();
    }

    @Test
    void levelFadePassAppearsWithTheFollowingVintPublication() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
        List<RgbaImage> afterRelease = new ArrayList<>();
        RgbaImage lastTitle = null;
        try (var session = new GameplayCaptureSession(settings)) {
            var config = GameServices.configuration();
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 22, 1, settings);
            boolean sawTitle = false;
            for (int step = 0; step < 400 && afterRelease.size() < 4; step++) {
                session.step(null);
                GameMode mode = session.loop().getCurrentGameMode();
                if (mode == GameMode.TITLE_CARD) {
                    sawTitle = true;
                    lastTitle = session.render();
                } else if (sawTitle && mode == GameMode.LEVEL) {
                    afterRelease.add(session.render());
                }
            }
        }
        assertTrue(lastTitle != null && afterRelease.size() == 4, "direct HPZ boot shows its card and releases");
        save(lastTitle, "title-last.png");
        for (int i = 0; i < afterRelease.size(); i++) save(afterRelease.get(i), "lfc" + i + ".png");

        // Release frame keeps the last title frame's playfield (native seq 129 == 130).
        assertTrue(samePlayfield(lastTitle, afterRelease.get(0)),
                "the release step must not show its own Pal_FillBlack/fade request before the next V-int");
        // First LevelLoop frame still shows the release palette (native seq 130 == 131 playfield).
        assertTrue(samePlayfield(afterRelease.get(0), afterRelease.get(1)),
                "LFC 1 must show the palette published at its V-int, not its own Pal_FromBlack pass");
        // The first pass reaches CRAM one frame later (native seq 131 -> 132).
        assertFalse(samePlayfield(afterRelease.get(1), afterRelease.get(2)),
                "the first Pal_FromBlack pass must appear in the LFC 2 picture");
    }

    /**
     * A rewind snapshot taken mid-fade holds two generations: the fade the step's logic requested
     * (pass k) and the fade CRAM displays (pass k-1, published by the step's V-int). Restoring it
     * must redraw the displayed generation at once, and stepping on must publish pass k.
     */
    @Test
    void midFadeRestoreRedrawsTheDisplayedGenerationAndReplaysForward() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
        try (var session = new GameplayCaptureSession(settings)) {
            var config = GameServices.configuration();
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 22, 1, settings);
            boolean sawTitle = false;
            int levelSteps = 0;
            for (int step = 0; step < 400 && levelSteps < 6; step++) {
                session.step(null);
                GameMode mode = session.loop().getCurrentGameMode();
                if (mode == GameMode.TITLE_CARD) sawTitle = true;
                else if (sawTitle && mode == GameMode.LEVEL) levelSteps++;
            }
            assertTrue(levelSteps == 6, "released into LevelLoop");
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            RgbaImage atCapture = session.render();
            var snapshot = registry.capture();
            session.step(null);
            RgbaImage next = session.render();
            assertFalse(samePlayfield(atCapture, next), "the captured step's requested pass is shown one step later");
            session.step(null);
            session.step(null);
            registry.restore(snapshot);
            RgbaImage restored = session.render();
            save(atCapture, "rewind-capture.png");
            save(restored, "rewind-restored.png");
            assertTrue(samePlayfield(atCapture, restored),
                    "restore must redraw the displayed fade generation, not the restored owner's newer request");
            session.step(null);
            RgbaImage replayed = session.render();
            assertTrue(samePlayfield(next, replayed), "forward replay publishes the captured request at its V-int");
        }
    }

    private static boolean samePlayfield(RgbaImage a, RgbaImage b) {
        for (int y = Y0; y < Y1; y++) {
            for (int x = X0; x < Math.min(X1, Math.min(a.width(), b.width())); x++) {
                if (a.argb(x, y) != b.argb(x, y)) return false;
            }
        }
        return true;
    }

    private static void save(RgbaImage image, String name) throws Exception {
        String dir = System.getProperty("fadecram.out");
        if (dir == null) return;
        Path out = Path.of(dir);
        Files.createDirectories(out);
        com.openggf.graphics.ScreenshotCapture.savePNG(image, out.resolve(name));
    }
}
