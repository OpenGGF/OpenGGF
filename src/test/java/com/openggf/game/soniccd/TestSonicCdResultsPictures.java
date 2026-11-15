package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.common.CdResults;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Disc-backed production rendering; staged results, without a native-frame parity claim. */
@EnabledOnOs(OS.LINUX)
class TestSonicCdResultsPictures {
    @Test
    void restoredResultsRefreshTheirUploadedDigits() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var session = new GameplayCaptureSession(settings)) {
            session.boot(disc, new SonicCdGameModule(), SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11A),
                    SonicCdZoneRegistry.actIndex(CdStagePayload.R11A), settings);
            assertTrue(session.usesNullAudioBackend());
            for (int i = 0; i < 200; i++) session.step(null);
            var sonic = (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
            GameServices.gameState().addScore(123400);
            var root = new CdResults(1, 50000, 1200);
            GameServices.level().getObjectManager().addDynamicObject(root);
            sonic.setX((short) (GameServices.camera().getX() + 400));
            for (int i = 0; i < 100 && lines().size() < 3; i++) root.update(i, sonic);
            assertEquals(3, lines().size());
            // All three lines settle within 36 moves; the headline then starts tallying.
            for (int i = 0; i < 37; i++) for (var line : lines()) line.update(i, sonic);
            var before = session.render();
            save(before, "results-before.png");
            var lineSnapshots = lines().stream().map(AbstractObjectInstance::captureRewindState).toList();
            var gameSnapshot = GameServices.gameState().capture();
            // Change the shared sheet and GPU upload after capturing the objects' cache markers.
            GameServices.gameState().addScore(111100);
            var changed = session.render();
            assertFalse(java.util.Arrays.equals(pixels(before), pixels(changed)), "changed score must be visible");
            var lines = lines();
            for (int i = 0; i < lines.size(); i++) lines.get(i).restoreRewindState(lineSnapshots.get(i));
            GameServices.gameState().restore(gameSnapshot);
            var restored = session.render();
            save(restored, "results-restored.png");
            assertArrayEquals(pixels(before), pixels(restored), "rewind must replace later uploaded score digits");
            for (int i = 0; i < 20; i++) for (var line : lines) line.update(i, sonic);
            var tally = session.render();
            save(tally, "results-tally.png");
            assertEquals(126600, GameServices.gameState().getScore(), "200 per frame for 12 ring frames, then 100");
            assertFalse(java.util.Arrays.equals(pixels(before), pixels(tally)), "tally must change the displayed counters");
            for (int i = 0; i < lines.size(); i++) lines.get(i).restoreRewindState(lineSnapshots.get(i));
            GameServices.gameState().restore(gameSnapshot);
            assertArrayEquals(pixels(before), pixels(session.render()), "restore the shared bonuses as well as score");
        }
    }

    private static List<AbstractObjectInstance> lines() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(CdResults.class::isInstance).map(AbstractObjectInstance.class::cast).toList();
    }

    private static void save(RgbaImage image, String name) throws Exception {
        Path out = Path.of("target", "soniccd-object-fixes", name);
        Files.createDirectories(out.getParent());
        ScreenshotCapture.savePNG(image, out);
    }

    private static int[] pixels(RgbaImage image) {
        int[] pixels = new int[320 * 224];
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) pixels[y * 320 + x] = image.argb(x, y);
        return pixels;
    }
}
