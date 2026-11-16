package com.openggf.game.sonic2.competition.view;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

import com.openggf.control.*;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.competition.Sonic2NativeCompetitionHost;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.graphics.*;
import com.openggf.level.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.PresentationOffscreenTestContext;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.opentest4j.AssertionFailedError;
import org.junit.jupiter.api.parallel.Isolated;

/** Real ROM rows through the ordinary shared tile/SAT renderer, all sixteen rows per cell. */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionFieldPixels {
    @Test
    void retainedPlaneAndWholeSpriteProjectionMatchIndependentNativeRowOracle() throws Exception {
        GameServices.audio().setBackend(new com.openggf.audio.NullAudioBackend());
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var services = EngineServices.current();
        var root = SessionManager.getCurrentWorldSession().rootGameModule();
        try (var gl = PresentationOffscreenTestContext.openConfigured(
                     320, 448, PresentationOffscreenTestContext.LegacyProfile.CORE_41);
                var renderer = new NativeCompetitionRenderer(448); AutoCloseable cleanup = () -> {
                    try {
                        SessionManager.closeGameplaySession();
                    } finally {
                        GameServices.graphics().cleanup();
                    }
                }) {
            var graphics = GameServices.graphics();
            graphics.setHeadlessMode(false);
            graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            graphics.setViewport(0, 0, 320, 448);
            glViewport(0, 0, 320, 448);
            var session = new Sonic2CompetitionSession();
            var host = new Sonic2NativeCompetitionHost(root, services,
                    ignored
                    -> {},
                    new Sonic2NativeCompetitionHost.Views() {
                        public void prepare(GameplayModeContext context, Sonic2CompetitionSession state,
                                Sonic2CompetitionLevelProfile profile,
                                com.openggf.game.sonic2.competition.Sonic2CompetitionNativeArt art) {}
                        public void draw(GameplayModeContext context, Sonic2CompetitionSession state) {}
                    },
                    new Sonic2NativeCompetitionHost.NativeState() {
                        public boolean swapActorWorldState(
                                GameplayModeContext context, Sonic2CompetitionSession state) {
                            return false;
                        }
                        public void freezeObjects(GameplayModeContext context, int ticks) {}
                    });
            host.bindNativeWaitInput(LogicalInputSnapshot::neutral, () -> false);
            session.updateMenu(LogicalInputSnapshot.ofPlayers(
                                       PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()),
                    true);
            host.loadAct(session, 0, 0);
            var context = host.context();
            graphics.bindRuntimeManagedReferences(context.getCamera(), context.getFadeManager());
            assertEquals(2048, context.getLevelManager().getCurrentLevel().getPatternCount());
            graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.FULL);
            for (int line = 0; line < 4; line++) {
                // The shared BG path takes its backdrop from semantic colour32,
                // in addition to the renderer's scratch clear. Define both inputs.
                var palette = context.getLevelManager().getCurrentLevel().getPalette(line);
                for (int c = 0; c < 16; c++)
                    palette.setColor(c, c == 0 ? new Palette.Color((byte) 0, (byte) 0, (byte) 0)
                            : new Palette.Color((byte) (c * 17), (byte) (line * 85), (byte) (255 - c * 17)));
                graphics.cachePaletteTexture(palette, line);
            }
            var art = context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionArtResidency.class);
            art.writePatterns(0, new byte[64]);
            art.fields().flush(graphics);
            byte[] bytes = new byte[65536];
            for (int i = 0; i < 2048; i++) System.arraycopy(art.patternBytes(i), 0, bytes, i * 32, 32);
            var planes = new Sonic2CompetitionPlanes();
            for (int row = 0; row < 32; row++)
                for (int col = 0; col < 64; col++)
                    planes.write(Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P1, col, row,
                            8 + (row * 11 + col) % 64 | ((col % 4) << 13) | ((col & 1) == 0 ? 0x800 : 0)
                                    | ((row & 1) == 0 ? 0x1000 : 0));
            for (boolean batching : new boolean[] {false, true}) {
                graphics.setBatchingEnabled(batching);
                graphics.setInstancedBatchingEnabled(batching);
                var planePixels = draw(renderer, context, planes, SpritePresentation.Frame.empty());
                for (int y = 0; y < 448; y++)
                    for (int x = 0; x < 320; x++) {
                        int word = planes.cell(Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P1, x / 8, y / 16);
                        int c = Im2FieldOracle.pixel(bytes, word & 1023, (word & 0x800) == 0 ? x % 8 : 7 - x % 8,
                                (word & 0x1000) == 0 ? y % 16 : 15 - y % 16);
                        int expected = rgb(c, word >>> 13 & 3);
                        int actual = planePixels.argb(x, y);
                        String message = "plane " + x + "," + y;
                        if (expected != actual) {
                            message = failureDetails("plane", message + " word=" + word,
                                    batching, graphics, planePixels);
                        }
                        assertEquals(expected, actual, message);
                    }
                var entries = new ArrayList<Sonic2CompetitionSatBuilder.SatEntry>();
                for (int flip = 0; flip < 4; flip++)
                    for (int y : new int[] {9, 232})
                        entries.add(new Sonic2CompetitionSatBuilder.SatEntry(256 + y, 9, 1,
                                9 | (flip << 13) | ((flip & 1) == 0 ? 0 : 0x800) | ((flip & 2) == 0 ? 0 : 0x1000),
                                144 + flip * 72));
                var page = new Sonic2CompetitionSatBuilder.Page(0, entries, entries.size(), List.of(), false);
                var sprites = NativeCompetitionRenderer.prepareSprites(
                        graphics, 0, 448, 448, () -> Sonic2CompetitionFieldSprites.submit(graphics, page));
                var pixels = draw(renderer, context, new Sonic2CompetitionPlanes(), sprites);
                for (var entry : entries) {
                    int top = entry.y(), width = entry.widthCells() * 8,
                        height = entry.heightCells() * 16;
                    for (int y = 0; y < entry.heightCells() * 16; y++)
                        for (int x = 0; x < width; x++) {
                            int sx = entry.name().hFlip() ? width - 1 - x : x;
                            int sy = y;
                            if (entry.name().vFlip())
                                sy = height - 1 - sy;
                            int cell = (entry.name().patternIndex() + sx / 8 * entry.heightCells() + sy / 16) & 1023;
                            int c = Im2FieldOracle.pixel(bytes, cell, sx % 8, sy % 16);
                            int expected = rgb(c, entry.name().palette());
                            int actual = pixels.argb(entry.x() + x, top + y);
                            String message = "sprite " + entry + " at " + x + "," + y;
                            if (expected != actual) {
                                message = failureDetails("sprite", message + " cell=" + cell
                                                + " source=" + sx % 8 + "," + sy % 16,
                                        batching, graphics, pixels);
                            }
                            assertEquals(expected, actual, message);
                        }
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            TestEnvironment.resetAll();
        }
    }
    @Test
    void diagnosticFrameRetainsThePixelsAndOriginalMismatch() throws Exception {
        String previous = System.getProperty("openggf.test.diagnostics");
        Path configuredRoot = TestSessionOutputPaths.diagnostics("competition-field-routing-control");
        Path retainedPath = null;
        try {
            System.setProperty("openggf.test.diagnostics", configuredRoot.toString());
            var graphics = new GraphicsManager();
            graphics.setBatchingEnabled(false);
            graphics.setInstancedBatchingEnabled(false);
            var image = new RgbaImage(2, 2, new int[] {0xFFFF0000, 0xFF000000, 0xFF00FF00, 0xFF0000FF});
            var failure = assertThrows(AssertionFailedError.class,
                    () -> assertEquals(0xFFCC0033, image.argb(1, 0),
                            failureDetails("image-control", "known synthetic mismatch", false, graphics, image)));
            assertEquals(0xFFCC0033, failure.getExpected().getValue());
            assertEquals(0xFF000000, failure.getActual().getValue());
            assertTrue(failure.getMessage().contains("iteration=direct"));
            assertTrue(failure.getMessage().contains("batching/instanced=false/false"));
            retainedPath = actualFramePath(failure);
            assertTrue(retainedPath.startsWith(configuredRoot.toAbsolutePath()),
                    "failure artifacts must respect the caller's diagnostics root");
            RgbaImage retained = ScreenshotCapture.loadPNG(retainedPath);
            assertEquals(image.width(), retained.width());
            assertEquals(image.height(), retained.height());
            assertArrayEquals(image.pixels(), retained.pixels());
        } finally {
            if (previous == null) {
                System.clearProperty("openggf.test.diagnostics");
            } else {
                System.setProperty("openggf.test.diagnostics", previous);
            }
            if (retainedPath != null) {
                Files.deleteIfExists(retainedPath);
            }
        }
    }

    @Test
    void unavailableArtifactDoesNotReplaceThePixelAssertion() throws Exception {
        var graphics = new GraphicsManager();
        var image = new RgbaImage(1, 1, new int[] {0xFF000000});
        try (MockedStatic<ScreenshotCapture> writer = Mockito.mockStatic(ScreenshotCapture.class)) {
            writer.when(() -> ScreenshotCapture.savePNG(Mockito.same(image), Mockito.any(Path.class)))
                    .thenThrow(new java.io.IOException("synthetic writer failure"));
            var failure = assertThrows(AssertionFailedError.class,
                    () -> assertEquals(0xFFCC0033, image.argb(0, 0),
                            failureDetails("writer-control", "known synthetic mismatch", true, graphics, image)));
            assertEquals(0xFFCC0033, failure.getExpected().getValue());
            assertEquals(0xFF000000, failure.getActual().getValue());
            assertTrue(failure.getMessage().contains("artifactUnavailable=IOException"));
            assertTrue(failure.getMessage().contains("iteration=instanced"));
        }
    }

    @Test
    void absentAndEmptyForkPropertiesCannotMaskTheMismatch() throws Exception {
        String previous = System.getProperty("java.io.tmpdir");
        var graphics = new GraphicsManager();
        var image = new RgbaImage(1, 1, new int[] {0xFF000000});
        try {
            for (String incoming : new String[] {null, ""}) {
                if (incoming == null) {
                    System.clearProperty("java.io.tmpdir");
                } else {
                    System.setProperty("java.io.tmpdir", incoming);
                }
                var failure = assertThrows(AssertionFailedError.class,
                        () -> assertEquals(0xFFCC0033, image.argb(0, 0),
                                failureDetails("property-control", "known synthetic mismatch", false, graphics, image)));
                assertEquals(0xFFCC0033, failure.getExpected().getValue());
                assertEquals(0xFF000000, failure.getActual().getValue());
                assertTrue(failure.getMessage().contains("fork=<unset>"));
                Path retainedPath = actualFramePath(failure);
                try {
                    assertArrayEquals(image.pixels(), ScreenshotCapture.loadPNG(retainedPath).pixels());
                } finally {
                    Files.deleteIfExists(retainedPath);
                }
            }
        } finally {
            if (previous == null) {
                System.clearProperty("java.io.tmpdir");
            } else {
                System.setProperty("java.io.tmpdir", previous);
            }
        }
    }

    private static Path actualFramePath(AssertionFailedError failure) {
        String message = failure.getMessage();
        String marker = "; actualFrame=";
        int start = message.indexOf(marker);
        assertTrue(start >= 0, message);
        int end = message.indexOf(" ==> expected:", start);
        return Path.of(message.substring(start + marker.length(), end));
    }

    /**
     * Retains first-mismatch evidence without another render or GL readback.
     * Inputs are the captured framebuffer, requested mode and current batching flags.
     * Origin: an unexplained category-run competition projection assertion (2026-11-09).
     */
    private static String failureDetails(String kind, String location, boolean iteration,
            GraphicsManager graphics, RgbaImage image) {
        long pid = ProcessHandle.current().pid();
        String mode = iteration ? "instanced" : "direct";
        String message = location + "; iteration=" + mode + " pid=" + pid;
        try {
            String temporaryRoot = System.getProperty("java.io.tmpdir", "");
            String fork = temporaryRoot.isEmpty() ? "<unset>"
                    : String.valueOf(Path.of(temporaryRoot).getFileName());
            message += " fork=" + fork;
            message += " batching/instanced=" + graphics.isBatchingEnabled()
                    + "/" + graphics.isInstancedBatchingEnabled();
            Path path = TestSessionOutputPaths.diagnostics("competition-field-pixels")
                    .resolve(pid + "-" + mode + "-" + kind + ".png");
            Files.createDirectories(path.getParent());
            ScreenshotCapture.savePNG(image, path);
            return message + "; actualFrame=" + path.toAbsolutePath();
        } catch (Exception | LinkageError diagnosticFailure) {
            // An unavailable artifact must leave the original expected/actual assertion primary.
            return message + "; artifactUnavailable=" + diagnosticFailure.getClass().getSimpleName();
        }
    }

    private static int rgb(int color, int palette) {
        return color == 0 ? 0xFF000000 : 0xFF000000 | (color * 17 << 16) | (palette * 85 << 8) | (255 - color * 17);
    }
    private static RgbaImage draw(NativeCompetitionRenderer renderer,
            com.openggf.game.session.GameplayModeContext context, Sonic2CompetitionPlanes planes,
            SpritePresentation.Frame sprites) {
        var view = new NativeCompetitionRenderer.View(context.getCamera().capture(), 0, 448, 0, 0, 0, 512, new int[448],
                sprites, planes.source(Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P1));
        PaletteUploadPresentation.withoutDisplayedFade(
                GameServices.graphics(), () -> renderer.draw(context, List.of(view), 0));
        return ScreenshotCapture.captureFramebuffer(320, 448);
    }
}
