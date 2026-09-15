package com.openggf.tools;

import com.openggf.graphics.RgbaImage;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestSessionOutputPaths;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Boots AIZ1 through the capture tool with an authored input log and proves the
 * two failure modes that cost the originating task most of a day cannot recur
 * silently: black frames, and frames with tiles but no playable sprite.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestGameplayCaptureSmoke {

    @Test
    void capturesMovingLeaderWithVisiblePlayerPixels() throws Exception {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        Path outDir = TestSessionOutputPaths.diagnostics("gameplay-capture-smoke");
        Files.createDirectories(outDir);
        Path input = outDir.resolve("input.txt");
        InputLogAuthorTool.author("40 R", input, "s3k");

        GameplayCaptureTool.Arguments arguments = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--rom", rom.toString(), "--zone", "aiz", "--act", "1",
                "--sidekick", "none", "--input", input.toString(), "--settle", "5",
                "--every", "15", "--no-video", "--out-dir", outDir.toString()});
        GameplayCaptureTool.Report report;
        try {
            report = GameplayCaptureTool.run(arguments);
        } catch (IllegalStateException | UnsatisfiedLinkError glUnavailable) {
            assumeTrue(false, "offscreen GL unavailable: " + glUnavailable.getMessage());
            return;
        }
        assertEquals(45, report.framesStepped());
        assertEquals(3, report.pngCount());

        List<String> state = Files.readAllLines(report.stateCsv(), StandardCharsets.UTF_8);
        assertEquals(GameplayCaptureSession.stateHeader(), state.get(0));
        assertEquals(46, state.size());
        int firstX = Integer.parseInt(state.get(1).split(",")[1]);
        int lastX = Integer.parseInt(state.get(45).split(",")[1]);
        assertTrue(lastX > firstX + 8, "leader should have moved right: " + firstX + " -> " + lastX);

        // The frame with sprites must differ from the same frame without them at the
        // leader's screen position; a black or tiles-only frame fails here.
        GameplayCaptureSession.Settings settings = new GameplayCaptureSession.Settings(
                320, "sonic", "", "off", null, null, null);
        try (GameplayCaptureSession session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 0, 0, settings);
            for (int i = 0; i < 20; i++) {
                session.step(null);
            }
            RgbaImage tilesOnly = session.render(false);
            RgbaImage full = session.render(true);
            int sx = session.player().getRenderCentreX() - com.openggf.game.GameServices.camera().getX();
            int sy = session.player().getRenderCentreY() - com.openggf.game.GameServices.camera().getY();
            int changed = 0;
            for (int y = Math.max(0, sy - 24); y < Math.min(full.height(), sy + 24); y++) {
                for (int x = Math.max(0, sx - 24); x < Math.min(full.width(), sx + 24); x++) {
                    if (full.argb(x, y) != tilesOnly.argb(x, y)) {
                        changed++;
                    }
                }
            }
            assertTrue(changed > 100, "playable sprite pixels expected near (" + sx + "," + sy + "), changed=" + changed);
        }
    }
    @Test
    void omittedTitleStillCreatesNativeSozControllerAndGhosts() throws Exception {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        GameplayCaptureSession.Settings settings = new GameplayCaptureSession.Settings(
                320, "sonic", "", "off", null, null, null);
        try (GameplayCaptureSession session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 8, 1, settings);
            var level = com.openggf.game.GameServices.level();
            var art = (com.openggf.game.sonic3k.Sonic3kObjectArtProvider)
                    level.getObjectRenderManager().getArtProvider();
            assertTrue(art.capture().titleCardTeardownTicks() >= 0,
                    "capture must retain the omitted native title owner");
            for (int i = 0; i < 200; i++) session.step(null);
            assertEquals(1, level.getObjectManager().getActiveObjects().stream()
                    .filter(com.openggf.game.sonic3k.objects.SozHyudoroControllerObjectInstance.class::isInstance).count());
            ((com.openggf.game.CheckpointState) level.getCheckpointState())
                    .saveCheckpoint(1, 0x140, 0x3AC, false);
            for (int i = 0; i < 40; i++) session.step(null);
            assertTrue(level.getObjectManager().getActiveObjects().stream()
                    .anyMatch(com.openggf.game.sonic3k.objects.SozHyudoroBodyObjectInstance.class::isInstance),
                    "the title-created controller must execute its real checkpoint-gated ghost behavior");
            assertTrue(session.render().width() > 0);
        }
    }

}
