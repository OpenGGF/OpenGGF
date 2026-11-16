package com.openggf.game.soniccd;

import com.openggf.editor.project.*;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.GameId;
import com.openggf.game.GameServices;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.LevelManager;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Source-backed editor launch, through the gameplay boot and production renderer.
 * Inputs: verified USA disc; output is optional local evidence, never bundled assets.
 * Origin: CD editor/import PR #352, edited-stage rendering obligation.
 */
@Isolated
class TestCdEditorPlaytestRendering {
    @Test void editedStageIsVisibleAwayFromTheNativeStart() throws Exception {
        Path output = Path.of(System.getProperty("openggf.test.output", "target/cd-editor-playtest"));
        Files.createDirectories(output);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var module = new SonicCdGameModule();
            var game = (SonicCdGame) module.createGame(disc);
            var source = (com.openggf.game.soniccd.level.CdLevel) game.loadLevel(CdStagePayload.R11A.ordinal());
            int x = 2368, y = 512;
            var metadata = new EditorProjectMetadata(GameId.SCD, "Visible editor edit", 0, 0, disc.identity(),
                    x, y, source.getMinX(), source.getMaxX(), source.getMinY(), source.getMaxY(),
                    new FullLevelExporter.ExportMusic.Stock(1), null);
            var document = EditorProjectDocument.create(metadata, source);
            // Clear a safe spawn and paint a visible foreground block to its left without intersecting the spawn.
            for (int bx = 8; bx <= 10; bx++) for (int by = 1; by <= 2; by++)
                document.level().getMap().setValue(0, bx, by, (byte) 0);
            var control = capture(disc, module, document, output.resolve("cd-editor-control.png"));
            document.level().getMap().setValue(0, 8, 2, (byte) 1);
            var saved = output.resolve("edited-playtest.ogfproject");
            new EditorProjectStore().save(saved, document);
            var reopened = new EditorProjectStore().open(saved);
            var edited = capture(disc, module, reopened, output.resolve("cd-editor-edited.png"));
            int different = 0;
            for (int i = 0; i < edited.pixels().length; i++) if (edited.pixels()[i] != control.pixels()[i]) different++;
            assertTrue(different > 100, "Visible authored foreground must change pixels: " + different);
            Files.writeString(output.resolve("cd-editor-render.txt"), "R11A source-backed editor playtest; authored spawn=" + x + "," + y
                    + "; native start=" + source.startX() + "," + source.startY() + "; changed pixels=" + different + "\n");
        }
    }

    private static RgbaImage capture(CdDiscDataSource disc, SonicCdGameModule module,
            EditorProjectDocument document, Path path) throws Exception {
        var runtime = EditorProjectRuntimeModule.prepare(module, document, document.metadata().startX(), document.metadata().startY());
        var config = GameServices.configuration();
        boolean audio = config.getBoolean(com.openggf.configuration.SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.AUDIO_ENABLED, false);
        try (var boot = new HeadlessGameBoot(320, 224)) {
            boot.setModuleDecorator(base -> runtime);
            var loop = boot.boot(disc, module, runtime.launchZone(), runtime.launchAct());
            // Let the native title/scroll presentation reach ordinary gameplay.
            for (int frame = 0; frame < 180; frame++) loop.step();
            try {
                assertInstanceOf(com.openggf.game.soniccd.level.CdLevel.class, GameServices.level().getCurrentLevel());
                assertInstanceOf(com.openggf.audio.NullAudioBackend.class, GameServices.audio().getBackend());
                assertTrue(GameServices.camera().getX() > 1000, "Camera must be away from the native start");
                var graphics = GameServices.graphics();
                GameServices.level().setClearColor();
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                GameServices.level().drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.gameplay());
                graphics.flush();
                if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
                glFinish();
                assertEquals(GL_NO_ERROR, glGetError());
                Files.writeString(path.resolveSibling(path.getFileName() + ".state.txt"),
                        "camera=" + GameServices.camera().getX() + "," + GameServices.camera().getY() + "\n");
                var frame = ScreenshotCapture.captureFramebuffer(320, 224);
                ScreenshotCapture.savePNG(frame, path);
                return frame;
            } finally { GameServices.graphics().cleanup(); }
        } finally { config.setSessionOverride(com.openggf.configuration.SonicConfiguration.AUDIO_ENABLED, audio); }
    }
}
