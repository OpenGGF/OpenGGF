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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Real ROM rows through the ordinary shared tile/SAT renderer, fixed field zero only. */
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
                     320, 224, PresentationOffscreenTestContext.LegacyProfile.CORE_41);
                var renderer = new NativeCompetitionRenderer(); AutoCloseable cleanup = () -> {
                    try {
                        SessionManager.closeGameplaySession();
                    } finally {
                        GameServices.graphics().cleanup();
                    }
                }) {
            var graphics = GameServices.graphics();
            graphics.setHeadlessMode(false);
            graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            graphics.setViewport(0, 0, 320, 224);
            glViewport(0, 0, 320, 224);
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
                for (int y = 0; y < 224; y++)
                    for (int x = 0; x < 320; x++) {
                        int word = planes.cell(Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P1, x / 8, y / 8);
                        int c = Im2FieldOracle.pixel(bytes, word & 1023, (word & 0x800) == 0 ? x % 8 : 7 - x % 8,
                                (word & 0x1000) == 0 ? 2 * (y % 8) : 15 - 2 * (y % 8));
                        assertEquals(rgb(c, word >>> 13 & 3), planePixels.argb(x, y), "plane " + x + "," + y);
                    }
                var entries = new ArrayList<Sonic2CompetitionSatBuilder.SatEntry>();
                for (int flip = 0; flip < 4; flip++)
                    for (int y : new int[] {9, 232})
                        entries.add(new Sonic2CompetitionSatBuilder.SatEntry(256 + y, 9, 1,
                                9 | (flip << 13) | ((flip & 1) == 0 ? 0 : 0x800) | ((flip & 2) == 0 ? 0 : 0x1000),
                                144 + flip * 72));
                var page = new Sonic2CompetitionSatBuilder.Page(0, entries, entries.size(), List.of(), false);
                var sprites = NativeCompetitionRenderer.prepareSprites(
                        graphics, 0, 224, () -> Sonic2CompetitionFieldSprites.submit(graphics, page));
                var pixels = draw(renderer, context, new Sonic2CompetitionPlanes(), sprites);
                for (var entry : entries) {
                    int top = Math.floorDiv(entry.y() + 1, 2), width = entry.widthCells() * 8,
                        height = entry.heightCells() * 16;
                    for (int y = 0; y < entry.heightCells() * 8; y++)
                        for (int x = 0; x < width; x++) {
                            int sx = entry.name().hFlip() ? width - 1 - x : x;
                            int sy = 2 * (top + y) - entry.y();
                            if (entry.name().vFlip())
                                sy = height - 1 - sy;
                            int cell = (entry.name().patternIndex() + sx / 8 * entry.heightCells() + sy / 16) & 1023;
                            int c = Im2FieldOracle.pixel(bytes, cell, sx % 8, sy % 16);
                            assertEquals(rgb(c, entry.name().palette()), pixels.argb(entry.x() + x, top + y),
                                    "sprite " + entry + " at " + x + "," + y);
                        }
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            TestEnvironment.resetAll();
        }
    }
    private static int rgb(int color, int palette) {
        return color == 0 ? 0xFF000000 : 0xFF000000 | (color * 17 << 16) | (palette * 85 << 8) | (255 - color * 17);
    }
    private static RgbaImage draw(NativeCompetitionRenderer renderer,
            com.openggf.game.session.GameplayModeContext context, Sonic2CompetitionPlanes planes,
            SpritePresentation.Frame sprites) {
        var view = new NativeCompetitionRenderer.View(context.getCamera().capture(), 0, 224, 0, 0, 0, 512, new int[224],
                sprites, planes.source(Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P1));
        PaletteUploadPresentation.withoutDisplayedFade(
                GameServices.graphics(), () -> renderer.draw(context, List.of(view), 0));
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }
}
