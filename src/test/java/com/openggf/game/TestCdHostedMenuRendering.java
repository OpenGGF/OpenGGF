package com.openggf.game;

import com.openggf.configuration.*;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.graphics.ScreenshotCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

class TestCdHostedMenuRendering {
    @TempDir Path root;
    @Test void discTitleAppearsOnTheSelectedHostedCard() throws Exception {
        CdTestMedia.usaCue();
        var config = SonicConfigurationService.createStandalone(root);
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, root.toString());
        try (var context = PresentationOffscreenTestContext.openConfigured(960, 672,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            var screen = new MasterTitleScreen(config, new LaunchProfileStore(config),
                    List.of(MasterTitleEntry.Hosted.of(GameId.SCD)));
            try {
                screen.initialize();
                screen.setViewportWidth(320);
                screen.setProjectionMatrix(new org.joml.Matrix4f().ortho2D(0, 320, 0, 224).get(new float[16]));
                var input = new com.openggf.control.InputHandler();
                for (int i = 0; i < 60; i++) screen.update(input);
                assertEquals("scd", screen.getSelectedGameId());
                var field = MasterTitleScreen.class.getDeclaredField("hostedPreviews");
                field.setAccessible(true);
                // Decode runs in the background. Keep pumping publication on the thread that owns GL.
                long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                while (!((java.util.Map<?, ?>) field.get(screen)).containsKey(GameId.SCD)
                        && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                    screen.update(input);
                }
                assertTrue(((java.util.Map<?, ?>) field.get(screen)).containsKey(GameId.SCD),
                        "The hosted title preview is published on the selected card");
                glViewport(0, 0, 960, 672);
                glClear(GL_COLOR_BUFFER_BIT);
                screen.draw(); glFinish();
                assertEquals(GL_NO_ERROR, glGetError());
                Path out = Path.of(System.getProperty("openggf.test.output", "target/cd-menu"));
                java.nio.file.Files.createDirectories(out);
                ScreenshotCapture.captureAndSavePNG(960, 672, out.resolve("cd-master-title.png"));
            } finally { screen.cleanup(); }
        }
    }
}
