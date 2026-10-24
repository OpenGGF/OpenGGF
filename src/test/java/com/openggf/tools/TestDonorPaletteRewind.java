package com.openggf.tools;

import com.openggf.game.GameId;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2SuperStateController;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.level.Palette;
import com.openggf.sprites.playable.SuperState;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Donated colors must rewind through the active gameplay owner, before another logic tick. */
@RequiresRom(SonicGame.SONIC_3K)
class TestDonorPaletteRewind {
    @Test
    void donatedSuperWriteRestoresLiveAliasesAndPresentedPixelsWithoutForwardTick() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "s2",
                RomTestUtils.ensureSonic2RomAvailable().toPath(), null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 2, 0, settings);
            for (int frame = 0; frame < 4; frame++) session.step(null);
            var donor = GameServices.crossGameFeatures().getDonorRenderContext();
            assertSame(donor, RenderContext.getOrCreateDonor(GameId.S2));
            assertTrue(RenderContext.getSidekickContexts().isEmpty(), "single donor reproduction");
            Palette palette = donor.getPalette(0);
            Palette.Color[] aliases = palette.colors.clone();
            // A separate ROM load is the expected normal row, independent of the snapshot.
            int[] expectedRgb = rgb(GameServices.crossGameFeatures().loadCharacterPalette("sonic"));
            assertArrayEquals(expectedRgb, rgb(palette));
            var player = session.player();
            var controller = assertInstanceOf(Sonic2SuperStateController.class, player.getSuperStateController());
            GameServices.gameState().configureSpecialStageProgress(7, 7);
            for (int emerald = 0; emerald < 7; emerald++) GameServices.gameState().markEmeraldCollected(emerald);
            player.setRingCount(100);
            player.setAir(true);
            player.setJumping(true);
            player.setYSpeed((short) 0x28);
            controller.checkTransformationBeforeMove();
            assertEquals(SuperState.TRANSFORMING, controller.getState());
            RgbaImage before = session.render();
            RgbaImage tiles = session.render(false);
            var bodyPixels = new ArrayList<Integer>();
            for (int i = 0; i < before.pixels().length; i++) {
                int color = before.pixels()[i] & 0xFFFFFF;
                if (before.pixels()[i] != tiles.pixels()[i]
                        && Arrays.stream(expectedRgb, 2, 6).anyMatch(rgb -> rgb == color)) bodyPixels.add(i);
            }
            assertFalse(bodyPixels.isEmpty(), "visible donor Sonic blue pixels are required");
            int[] expectedGpu = gpuRow(donor.getEffectivePaletteLine(0));
            var displayedFade = PaletteUploadPresentation.captureDisplayedFade(GameServices.graphics());
            var mode = SessionManager.getCurrentGameplayMode();
            assertNotNull(mode);
            var registry = mode.getRewindRegistry();
            var snapshot = registry.capture();
            for (int frame = 0; frame < 24; frame++) session.step(null);
            int[] advancedRgb = rgb(palette);
            int[] advancedGpu = gpuRow(donor.getEffectivePaletteLine(0));
            var advancedSnapshot = registry.capture();
            assertFalse(Arrays.equals(expectedRgb, advancedRgb), "production donated super write executed");
            assertFalse(Arrays.equals(expectedGpu, advancedGpu), "production donated write reached the GPU");
            registry.restore(snapshot);
            // No tick, palette replay, fabricated snapshot, or renderer restoration between these observations.
            int[] restoredGpu = gpuRow(donor.getEffectivePaletteLine(0));
            RgbaImage restored = session.render();
            long changedPixels = bodyPixels.stream()
                    .filter(i -> before.pixels()[i] != restored.pixels()[i]).count();
            assertAll(
                    () -> assertArrayEquals(expectedRgb, rgb(palette), "donor CPU colors immediately restore"),
                    () -> assertArrayEquals(expectedGpu, restoredGpu, "donor GPU row restores before redraw or V-int"),
                    () -> assertEquals(0, changedPixels, "restored published donor body pixels"),
                    () -> assertEquals(displayedFade,
                            PaletteUploadPresentation.captureDisplayedFade(GameServices.graphics()),
                            "native displayed fade publication generation survives donor restore"),
                    () -> assertSame(palette, donor.getPalette(0)),
                    () -> assertArrayEquals(aliases, palette.colors, "live Color aliases survive restore"),
                    () -> assertSame(donor, RenderContext.getOrCreateDonor(GameId.S2)));
            for (int frame = 0; frame < 24; frame++) session.step(null);
            assertArrayEquals(advancedRgb, rgb(palette), "ordinary forward writes repeat after restore");
            registry.restore(snapshot);
            registry.restore(advancedSnapshot);
            assertArrayEquals(advancedRgb, rgb(palette), "forward seek restores values rather than replaying a palette tick");
            assertArrayEquals(advancedGpu, gpuRow(donor.getEffectivePaletteLine(0)), "forward seek uploads immediately");
        }
    }

    private static int[] rgb(Palette palette) {
        int[] values = new int[Palette.PALETTE_SIZE];
        for (int i = 0; i < values.length; i++) values[i] = (palette.red(i) & 255) << 16
                | (palette.green(i) & 255) << 8 | palette.blue(i) & 255;
        return values;
    }

    private static int[] gpuRow(int row) {
        int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
        glBindTexture(GL_TEXTURE_2D, GameServices.graphics().getCombinedPaletteTextureId());
        int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        var bytes = MemoryUtil.memAlloc(16 * height * 4);
        try {
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            int[] colors = new int[16];
            for (int color = 0; color < colors.length; color++) {
                int offset = (row * 16 + color) * 4;
                colors[color] = (bytes.get(offset) & 255) << 24 | (bytes.get(offset + 1) & 255) << 16
                        | (bytes.get(offset + 2) & 255) << 8 | bytes.get(offset + 3) & 255;
            }
            return colors;
        } finally {
            MemoryUtil.memFree(bytes);
            glBindTexture(GL_TEXTURE_2D, previous);
        }
    }
}
