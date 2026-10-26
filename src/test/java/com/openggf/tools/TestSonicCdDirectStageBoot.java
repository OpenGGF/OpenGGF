package com.openggf.tools;

import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStockLaunch;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.graphics.ScreenshotCapture;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Real production GL/session/renderer acceptance for static terrain, not gameplay or native parity. */
class TestSonicCdDirectStageBoot {
    @ParameterizedTest(name = "{0}: normal source boot, visible terrain and same-world restore")
    @EnumSource(CdReleaseVariant.class)
    void staticDiscStageUsesTheNormalSessionLoopAndCaptureRenderer(CdReleaseVariant variant) throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied discs");
        Assumptions.assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")),
                "Select the display-free rendering owner explicitly");
        assertTrue(System.getenv("DISPLAY") == null && System.getenv("WAYLAND_DISPLAY") == null,
                "this check must have no ambient desktop connection");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve(directory))) {
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            cue = cues.getFirst();
        }
        var source = CdStockLaunch.prepare(cue, CdStockLaunch.Request.r11a());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.bootSonicCdStatic(source);
            var mode = SessionManager.getCurrentGameplayMode();
            assertSame(source, mode.getWorldSession().getDataSource());
            assertEquals(GameId.SCD, mode.getWorldSession().resolvedGameModule().getGameId());
            assertEquals(GameMode.LEVEL, capture.loop().getCurrentGameMode());
            assertInstanceOf(SonicCdLevel.class, GameServices.level().getCurrentLevel());
            assertNull(GameServices.camera().getFocusedSprite(), "a stock cartridge Sonic must not be substituted");
            assertTrue(source.rom().isEmpty());
            assertTrue(mode.getWorldSession().getGameModule().getPlayableCharacterRegistry().definitions().isEmpty());

            capture.step(null);
            capture.step(null);
            var image = capture.render();
            assertEquals(320, image.width());
            assertEquals(224, image.height());
            assertTrue(Arrays.stream(image.pixels()).distinct().count() > 8,
                    "a nonblank terrain/palette result is required, not just successful boot");
            var snapshot = mode.getRewindRegistry().capture();
            short x = GameServices.camera().getX();
            GameServices.camera().setX((short) (x + 32));
            var moved = capture.render();
            assertFalse(Arrays.equals(image.pixels(), moved.pixels()), "the existing renderer must consume camera state");
            mode.getRewindRegistry().restore(snapshot);
            assertEquals(x, GameServices.camera().getX());
            assertArrayEquals(image.pixels(), capture.render().pixels(), "same-world rewind restores the visible stage");
            capture.step(null);
            assertArrayEquals(image.pixels(), capture.render().pixels(), "held presentation cannot execute native actors");

            String output = System.getProperty("soniccd.static.capture.root");
            if (output != null && !output.isBlank()) {
                Path root = Path.of(output).toAbsolutePath();
                Files.createDirectories(root);
                Path png = root.resolve(variant.name().toLowerCase(Locale.ROOT) + "-r11a-static.png");
                if (Files.exists(png)) { throw new FileAlreadyExistsException(png.toString()); }
                ScreenshotCapture.savePNG(image, png);
            }
        }
    }
}
