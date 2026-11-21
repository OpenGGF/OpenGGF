package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.opentest4j.AssertionFailedError;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Native picture check for all ten Collision Chaos stage programs, see {@link CczNativePictureCheck}. */
@EnabledOnOs(OS.LINUX)
class TestCczBackgroundPicture {
    @Test
    void backgroundMatchesTheNativeFramesInAllTenStages() throws Exception {
        CczNativePictureCheck.runAll(CczNativePictureCheck.STAGES);
    }

    @Test
    void pastForegroundWobblesLikeTheNativeFrame() throws Exception {
        assertForegroundWobble(CdStagePayload.R31B, 518);
    }

    @Test
    void goodFutureForegroundWobblesLikeTheNativeFrame() throws Exception {
        assertForegroundWobble(CdStagePayload.R31C, 520);
    }

    private static void assertForegroundWobble(CdStagePayload payload, int traceRow) throws Exception {
        var shot = CdTestMedia.nativeFile("rehost-fixtures-ccz2/%s/screenshot-lf0500-row%04d.png"
                .formatted(payload, traceRow));
        var rows = CczNativePictureCheck.rows(payload);
        var row = rows.get(traceRow);
        assertEquals(500, row.levelFrame(), "native shot's level frame");
        var config = GameServices.configuration();
        boolean oldAudio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            // The native VInt uploads the previous level iteration's scroll words.
            var engine = CczNativePictureCheck.renderEngineFrames(boot, disc, payload, rows, row.levelFrame() - 1)
                    .getFirst();
            var nativeImage = ScreenshotCapture.loadPNG(shot);
            int waterline = 0x5C0 - engine.cameraY();
            assertTrue(waterline > 0 && waterline < nativeImage.height(), "native shot straddles the wobble threshold");
            Path out = Path.of("target", "soniccd-ccz-background");
            Files.createDirectories(out);

            var modes = GameServices.advancedRenderModeController();
            var savedModes = modes.capture();
            modes.clear();
            CczNativePictureCheck.Rendered uniform;
            try {
                uniform = CczNativePictureCheck.capture();
            } finally {
                modes.restore(savedModes);
            }
            var before = compareBelowWaterline(uniform, nativeImage, row, waterline, out.resolve(payload + "-wobble-off.png"));
            var after = compareBelowWaterline(engine, nativeImage, row, waterline, out.resolve(payload + "-wobble-on.png"));
            var red = assertThrows(AssertionFailedError.class, () -> assertExact(before),
                    "a uniform foreground must fail the native picture assertion");
            System.out.println(payload + " uniform foreground control: " + red.getMessage());
            System.out.printf("%s frame %d, camera %X,%X: %d/%d unmasked pixels below the wobble threshold match%n",
                    payload, row.levelFrame(), engine.cameraX(), engine.cameraY(), after.same(), after.total());
            assertExact(after);
        } finally {
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
        }
    }

    private static CczNativePictureCheck.Comparison compareBelowWaterline(CczNativePictureCheck.Rendered engine,
            RgbaImage reference, CczNativePictureCheck.Row row, int waterline, Path panel) throws Exception {
        var comparison = CczNativePictureCheck.compare(engine.image(), reference, engine, row,
                CczNativePictureCheck.OBJECT_MARGIN, new int[][] {{0, 0, reference.width(), waterline}});
        ScreenshotCapture.savePNG(CczNativePictureCheck.panel(engine.image(), reference, comparison.ignored()), panel);
        assertTrue(comparison.total() >= 10_000,
                () -> panel.getFileName() + ": expected at least 10,000 unmasked pixels below the wobble threshold, got "
                        + comparison.total());
        return comparison;
    }

    private static void assertExact(CczNativePictureCheck.Comparison comparison) {
        assertEquals(comparison.total(), comparison.same(),
                () -> "matching pixels below the wobble threshold, differences at x "
                        + comparison.minX() + "-" + comparison.maxX() + ", y "
                        + comparison.minY() + "-" + comparison.maxY());
    }
}
