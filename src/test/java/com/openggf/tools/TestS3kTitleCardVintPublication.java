package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S3K title card is made of sprite objects (Obj_TitleCard children): Render_Sprites places
 * their pieces in the sprite table during a frame's logic and the following V-int uploads it, so a
 * piece moved by step k's logic appears in step k+1's picture, with the rest of that publication.
 * Native HPZ entry: the card is static from seq 152 to 153 and first moves at 153 -> 154, one
 * frame after the logic that moves it. Pictures omit the sprite pass ({@code render(false)}), so the
 * player's own animation cannot stand in for card movement; the card is still drawn.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kTitleCardVintPublication {
    // Card region, right of the HUD column.
    private static final int X0 = 110, X1 = 320, Y0 = 0, Y1 = 224;

    @AfterEach
    void tearDown() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
        SessionManager.clear();
    }

    @Test
    void cardExitMovementAppearsWithTheFollowingVintPublication() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
        try (var session = new GameplayCaptureSession(settings)) {
            limited();
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 22, 1, settings);
            List<RgbaImage> pictures = new ArrayList<>();
            List<int[]> cardX = new ArrayList<>();
            boolean sawTitle = false;
            int firstMove = -1;
            for (int step = 0; step < 500; step++) {
                session.step(null);
                GameMode mode = session.loop().getCurrentGameMode();
                if (mode == GameMode.TITLE_CARD) {
                    sawTitle = true;
                    continue;
                }
                if (!sawTitle || mode != GameMode.LEVEL) continue;
                var card = (Sonic3kTitleCardManager) session.loop().getTitleCardProvider();
                pictures.add(session.render(false));
                cardX.add(elemX(card));
                int i = cardX.size() - 1;
                if (i > 0 && firstMove < 0 && !Arrays.equals(cardX.get(i - 1), cardX.get(i))) {
                    firstMove = i;
                }
                if (firstMove >= 0 && i >= firstMove + 3) break;
            }
            assertTrue(firstMove > 0, "the released card leaves the screen");
            save(pictures.get(firstMove - 1), "exit-before.png");
            save(pictures.get(firstMove), "exit-move-logic.png");
            save(pictures.get(firstMove + 1), "exit-move-shown.png");
            assertTrue(sameCard(pictures.get(firstMove - 1), pictures.get(firstMove)),
                    "the step whose logic moves the card must still show the published (unmoved) card");
            assertFalse(sameCard(pictures.get(firstMove), pictures.get(firstMove + 1)),
                    "the move must appear with the next V-int publication");

            // Mid-exit rewind: restore redraws the published generation, forward replay republishes.
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            RgbaImage atCapture = session.render(false);
            var snapshot = registry.capture();
            session.step(null);
            RgbaImage next = session.render(false);
            session.step(null);
            session.step(null);
            registry.restore(snapshot);
            RgbaImage restored = session.render(false);
            save(restored, "exit-rewind-restored.png");
            assertTrue(sameCard(atCapture, restored), "restore must redraw the published card generation");
            session.step(null);
            assertTrue(sameCard(next, session.render(false)), "forward replay publishes the captured card state");
        }
    }

    private static void limited() {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
        config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
    }

    /** Every live input the card draws from: piece x, y, mapping frame and exited flag. */
    private static int[] elemX(Sonic3kTitleCardManager card) throws Exception {
        int[] x = ints(card, "elemX"), y = ints(card, "elemY"), frame = ints(card, "elemFrame");
        Field f = Sonic3kTitleCardManager.class.getDeclaredField("elemExited");
        f.setAccessible(true);
        boolean[] exited = (boolean[]) f.get(card);
        int[] all = new int[x.length * 4];
        for (int i = 0; i < x.length; i++) {
            all[i * 4] = x[i];
            all[i * 4 + 1] = y[i];
            all[i * 4 + 2] = frame[i];
            all[i * 4 + 3] = exited[i] ? 1 : 0;
        }
        return all;
    }

    private static int[] ints(Sonic3kTitleCardManager card, String name) throws Exception {
        Field f = Sonic3kTitleCardManager.class.getDeclaredField(name);
        f.setAccessible(true);
        return ((int[]) f.get(card)).clone();
    }

    private static boolean sameCard(RgbaImage a, RgbaImage b) {
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
