package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** ROM wall pieces through Slot's actual direct painter, in an owned offscreen context. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSlotCellPainterPixels {
    @Test
    void earlierOpaqueBlackCellOccludesLaterBlueAndRenderingIsPassive() throws Exception {
        var saved = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try (var session = enterSlot()) {
            var level = GameServices.level();
            var camera = GameServices.camera();
            var graphics = GameServices.graphics();
            assertFalse(level.getZoneFeatureProvider().useSpriteSatMasking(level.getCurrentZone()),
                    "this regression must exercise the actual Slot non-SAT painter branch");
            assertFalse(graphics.isSpriteSatCollectionActive());
            // Isolated opacity witness: retain the actual ROM mapping/patterns, but make
            // native CRAM indices $32/$33 distinguishable independent of the entry fade.
            // Retained row1017 has $32 blue and $33 black. Only index zero is transparent.
            graphics.clearPaletteFadePresentation();
            com.openggf.graphics.PaletteUploadPresentation.publishAndLatch(graphics);
            assertFalse(com.openggf.graphics.PaletteUploadPresentation.displayedFade(graphics).isActive());
            var wallPalette = new com.openggf.level.Palette();
            for (int index = 1; index < 16; index++) wallPalette.setColor(index,
                    new com.openggf.level.Palette.Color((byte) 0, (byte) 255, (byte) 0));
            wallPalette.setColor(2, new com.openggf.level.Palette.Color((byte) 0, (byte) 0, (byte) 102));
            wallPalette.setColor(3, new com.openggf.level.Palette.Color((byte) 0, (byte) 0, (byte) 0));
            graphics.cachePaletteTexture(wallPalette, 3);
            var renderer = new S3kSlotLayoutRenderer();
            renderer.updateAnimations(0x2C); // ROM frame11: art $33B + mapping $A9 = retained SAT tile $3E4.
            var before = CompactFieldCapturer.capture(renderer);
            int x = camera.getX() + 112, y = camera.getY() + 96;
            // Relative displacement of retained native SAT13/14: (166,-23) -> (177,-2).
            var earlier = cells(x, y);
            var later = cells(x + 11, y + 21);
            byte[] earlierPixels = draw(renderer, earlier, graphics);
            byte[] laterPixels = draw(renderer, later, graphics);
            var witnesses = new ArrayList<Integer>();
            for (int i = 0; i < earlierPixels.length; i += 4) {
                if (rgb(earlierPixels, i) == 0 && (rgb(laterPixels, i) & 0xFFFF00) == 0
                        && (laterPixels[i + 2] & 255) > 0) witnesses.add(i);
            }
            assertFalse(witnesses.isEmpty(), "actual ROM cells must overlap opaque black and blue pixels; colors=" + colors(earlierPixels));
            // Magenta clear survives index zero. A black pixel in earlierPixels is therefore
            // a drawn NONZERO pattern index, not a transparent RGB-black special case.
            var ordered = cells(x, y, x + 11, y + 21);
            byte[] combined = draw(renderer, ordered, graphics);
            for (int at : witnesses) assertEquals(0, rgb(combined, at),
                    "earlier native SAT cell must occlude later blue at buffer pixel " + at / 4);
            assertArrayEquals(combined, draw(renderer, ordered, graphics), "repeat render is identical");
            var reversed = cells(x + 11, y + 21, x, y);
            byte[] reversePixels = draw(renderer, reversed, graphics);
            for (int at : witnesses) assertEquals(rgb(laterPixels, at), rgb(reversePixels, at),
                    "swapping native order changes the overlap winner");
            var separated = cells(x, y, x + 64, y);
            byte[] separatePixels = draw(renderer, separated, graphics);
            assertTrue(coloredPixels(separatePixels) > coloredPixels(earlierPixels),
                    "both non-overlapping ROM cells remain visible");
            assertEquals(before, CompactFieldCapturer.capture(renderer), "render does not advance animation state");
            assertEquals(2, ordered.size());assertEquals(x, ordered.worldXAt(0));
            assertEquals(x + 11, ordered.worldXAt(1));
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            TestEnvironment.resetAll();
            TraceReplaySessionBootstrap.restoreGameplayConfig(saved);
        }
    }

    private static S3kSlotRenderBuffers.VisibleCells cells(int... xy) {
        var cells = new S3kSlotRenderBuffers.VisibleCells(xy.length / 2);
        for (int i = 0; i < xy.length; i += 2) cells.add(1, xy[i], xy[i + 1]);
        return cells;
    }

    private static byte[] draw(S3kSlotLayoutRenderer renderer, S3kSlotRenderBuffers.VisibleCells cells,
                               GraphicsManager graphics) {
        graphics.flush();
        glDisable(GL_DITHER);glClearColor(1, 0, 1, 1);glClear(GL_COLOR_BUFFER_BIT);
        graphics.beginPatternBatch();
        renderer.renderVisibleCells(cells, GameServices.camera(), GameServices.level().getObjectRenderManager());
        graphics.flushPatternBatch();graphics.flush();glFinish();
        ByteBuffer buffer = MemoryUtil.memAlloc(320 * 224 * 4);
        try {
            glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            byte[] bytes = new byte[buffer.remaining()];buffer.get(bytes);return bytes;
        } finally { MemoryUtil.memFree(buffer); }
    }
    private static int rgb(byte[] bytes, int at) {
        return ((bytes[at] & 255) << 16) | ((bytes[at + 1] & 255) << 8) | (bytes[at + 2] & 255);
    }
    private static java.util.Map<Integer,Integer> colors(byte[] bytes) {
        var colors = new java.util.TreeMap<Integer,Integer>();
        for (int i = 0; i < bytes.length; i += 4) colors.merge(rgb(bytes, i), 1, Integer::sum);
        return colors;
    }
    private static int coloredPixels(byte[] bytes) {
        int count = 0;
        for (int i = 0; i < bytes.length; i += 4) if (rgb(bytes, i) != 0xFF00FF) count++;
        return count;
    }
    private static GameplayCaptureSession enterSlot() throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
        GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        var session = new GameplayCaptureSession(settings);
        try {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "Slot cell painter fixture");
            for (int frame = 0; frame < 60; frame++) { session.step(neutral);session.renderFrame(); }
            GameServices.level().requestBonusStageEntry(com.openggf.game.BonusStageType.SLOT_MACHINE);
            for (int frame = 0; frame < 600; frame++) {
                session.step(neutral);session.renderFrame();
                if (session.loop().getCurrentGameMode() == GameMode.BONUS_STAGE
                        && !GameServices.fade().isActive() && !GameServices.fade().hasPendingCompletion()) break;
            }
            assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode());
            assertFalse(GameServices.fade().isActive());assertFalse(GameServices.fade().hasPendingCompletion());
            return session;
        } catch (Exception | Error e) { session.close();throw e; }
    }
}
