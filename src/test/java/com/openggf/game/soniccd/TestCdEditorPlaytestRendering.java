package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.editor.project.*;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
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

    @Test void uneditedHudMatchesProductionAtTheSameFrame() throws Exception {
        Path output = Path.of(System.getProperty("openggf.test.output", "target/cd-editor-playtest"));
        Files.createDirectories(output);
        RgbaImage editor;
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var module = new SonicCdGameModule();
            var game = (SonicCdGame) module.createGame(disc);
            var source = (com.openggf.game.soniccd.level.CdLevel) game.loadLevel(CdStagePayload.R11A.ordinal());
            var metadata = new EditorProjectMetadata(GameId.SCD, "Unedited HUD", 0, 0, disc.identity(),
                    source.startX(), source.startY(), source.getMinX(), source.getMaxX(), source.getMinY(), source.getMaxY(),
                    new FullLevelExporter.ExportMusic.Stock(1), null);
            editor = capture(disc, module, EditorProjectDocument.create(metadata, source), output.resolve("cd-editor-native.png"));
        }
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var production = capture(disc, new SonicCdGameModule(), null, output.resolve("cd-production.png"));
            for (int y = 8; y < 48; y++) for (int x = 16; x < 112; x++)
                assertEquals(production.argb(x, y), editor.argb(x, y), "HUD pixel at " + x + "," + y);
            for (int y = 200; y < 216; y++) for (int x = 16; x < 48; x++)
                assertEquals(production.argb(x, y), editor.argb(x, y), "Lives pixel at " + x + "," + y);
        }
    }

    private static RgbaImage capture(CdDiscDataSource disc, SonicCdGameModule module,
            EditorProjectDocument document, Path path) throws Exception {
        var runtime = document == null ? null : EditorProjectRuntimeModule.prepare(module, document, document.metadata().startX(), document.metadata().startY());
        var config = GameServices.configuration();
        boolean audio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (var boot = new HeadlessGameBoot(320, 224)) {
            if (runtime != null) boot.setModuleDecorator(base -> runtime);
            var loop = boot.boot(disc, module, runtime == null ? 0 : runtime.launchZone(), runtime == null ? 0 : runtime.launchAct());
            // Let the native title/scroll presentation reach ordinary gameplay.
            for (int frame = 0; frame < 180; frame++) loop.step();
            try {
                assertInstanceOf(com.openggf.game.soniccd.level.CdLevel.class, GameServices.level().getCurrentLevel());
                assertInstanceOf(com.openggf.audio.NullAudioBackend.class, GameServices.audio().getBackend());
                if (document != null && document.metadata().startX() > 1000)
                    assertTrue(GameServices.camera().getX() > 1000, "Camera must be away from the native start");
                var graphics = GameServices.graphics();
                assertBlankScorePatterns(path);
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
                boolean shown = config.getBoolean(SonicConfiguration.TRACE_SHOW_GAME_HUD);
                try {
                    config.setSessionOverride(SonicConfiguration.TRACE_SHOW_GAME_HUD, false);
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                    GameServices.level().drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.gameplay());
                    graphics.flush();
                    if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
                    glFinish();
                    var withoutHud = ScreenshotCapture.captureFramebuffer(320, 224);
                    ScreenshotCapture.savePNG(withoutHud, path.resolveSibling(path.getFileName() + ".without-hud.png"));
                    // SCORE ends at x=56; blank score cells span x=56..103 before the final zero.
                    for (int py = 8; py < 24; py++) for (int px = 56; px < 104; px++)
                        assertEquals(withoutHud.argb(px, py), frame.argb(px, py),
                                "Blank score cell must show the unchanged world at " + px + "," + py);
                } finally { config.setSessionOverride(SonicConfiguration.TRACE_SHOW_GAME_HUD, shown); }
                return frame;
            } finally { GameServices.graphics().cleanup(); }
        } finally { config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, audio); }
    }

    /** Read the physical atlas slots, rather than assuming a CPU sheet proves its upload. */
    private static void assertBlankScorePatterns(Path path) throws Exception {
        var graphics = GameServices.graphics();
        var art = GameServices.level().getObjectRenderManager();
        var sheet = art.getSheet(CdObjectArtProvider.HUD);
        var renderer = art.getRenderer(CdObjectArtProvider.HUD);
        var report = new StringBuilder();
        int width = graphics.getPatternAtlasWidth(), height = graphics.getPatternAtlasHeight();
        var gpu = org.lwjgl.BufferUtils.createByteBuffer(width * height);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            // CdHud's SCORE_DIGITS: native tiles $583..$58E, six two-tile blank cells.
            for (int tile = 0x1b; tile < 0x27; tile++) {
                byte[] cpu = new byte[64];
                sheet.getPatterns()[tile].copyInto(cpu, 0);
                var entry = graphics.getPatternAtlasEntry(renderer.getPatternBase() + tile);
                glBindTexture(GL_TEXTURE_2D, graphics.getPatternAtlasTextureId(entry.atlasIndex()));
                glGetTexImage(GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30.GL_RED, GL_UNSIGNED_BYTE, gpu);
                int nonzero = 0, mismatch = 0;
                for (int py = 0; py < 8; py++) for (int px = 0; px < 8; px++) {
                    if (cpu[py * 8 + px] != 0) nonzero++;
                    if (cpu[py * 8 + px] != gpu.get((entry.tileY() * 8 + py) * width + entry.tileX() * 8 + px)) mismatch++;
                }
                assertEquals(0, nonzero, "Leading score cell must be transparent: " + tile);
                assertEquals(0, mismatch, "HUD GPU upload must equal its owned pattern: " + tile);
                report.append(String.format("HUD relative %02x virtual %x CPU nonzero=%d GPU mismatches=%d%n",
                        tile, renderer.getPatternBase() + tile, nonzero, mismatch));
            }
        } finally { glBindTexture(GL_TEXTURE_2D, previousTexture); }
        Files.writeString(path.resolveSibling(path.getFileName() + ".patterns.txt"), report);
    }
}
