package com.openggf.game.soniccd.flow;

import com.openggf.NativeMenuPresentationAccess;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdLoadTiming;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.audio.SonicCdAudioProfile;
import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.game.soniccd.fmv.CdFmvStreamer;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.Palette;
import com.openggf.level.PaletteDisplayColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;

@EnabledOnOs(OS.LINUX)
class TestCdVisualModeFlow {
    @Test
    void titleOpensVisualModeAndAllFourClipsReturnWithoutWritingSaveData() throws Exception {
        var saved = CdSaveRecord.decode(new byte[CdSaveRecord.FILE_BYTES]);
        saved.store(new CdSaveVariables(0x0215, 3, 0x04, 0x15, 0, 4, 0x0f));
        try (var harness = new CdFlowHarness(module -> CdFlowHarness.attachReadOnlySave(module, saved))) {
            harness.selectTitleOption(1);
            harness.until(() -> harness.loop.getCurrentGameMode() == GameMode.OPTIONS
                    && !GameServices.fade().isActive(), 600, 0);

            int[][] films = new int[3][];
            int[][] endings = new int[2][];
            for (int i = 0; i < films.length; i++) {
                int[] menu = draw(harness);
                assertTrue(Arrays.stream(menu).distinct().count() > 8, "the browser has drawn its choices");
                harness.step(0, CdFlowHarness.A, false);
                harness.idle(220);
                films[i] = draw(harness);
                assertFalse(Arrays.equals(menu, films[i]), "confirm starts a video");
                assertTrue(Arrays.stream(films[i]).distinct().count() > 8, "the disc supplies visible video pixels");
                harness.idle(60);
                assertFalse(Arrays.equals(films[i], draw(harness)), "the video advances");
                if (i > 0) {
                    // Both endings share the early footage. Their separate sequences are visible at frame 2500.
                    harness.idle(2500 - 280);
                    endings[i - 1] = draw(harness);
                }

                harness.step(0, CdFlowHarness.C, false);
                assertArrayEquals(menu, draw(harness), "Back restores the same selected row");
                harness.idle(1);
                harness.step(CdFlowHarness.DOWN, 0, false);
                harness.idle(1);
            }
            assertFalse(Arrays.equals(films[0], films[1]));
            assertFalse(Arrays.equals(endings[0], endings[1]), "the two ending choices play different clips");

            checkPencilTest(harness);
            harness.idle(1);
            harness.step(CdFlowHarness.DOWN, 0, false);
            harness.idle(1);
            harness.step(0, CdFlowHarness.A, false);
            harness.until(() -> harness.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
            assertNull(harness.scenes.pendingEnding());
            assertEquals(saved, harness.scenes.flow().backupRam().readSave());
        }
    }

    private static void checkPencilTest(CdFlowHarness harness) throws Exception {
        var profile = assertInstanceOf(SonicCdAudioProfile.class, GameServices.audio().getAudioProfile());
        var audio = profile.subCpuAudio();
        audio.loadStage(CdStagePayload.R11A);
        var pcm = audio.pcmDriver();
        assertNotNull(pcm);
        var cdda = audio.state().cdda();
        var disc = harness.disc.assets();
        int seek = CdFmvStreamer.seekFor(CdLoadTiming.programTiming(), disc.entry(CdNativeFile.PENCILSTM));
        int[] menu = draw(harness);
        harness.step(0, CdFlowHarness.A, false);
        int bank = 60;
        harness.idle(pencilPictureTick(bank, seek));
        int[] firstVisit = draw(harness);
        assertPencilPixels(firstVisit, disc.read(CdNativeFile.PENCILSTM, bank * 10_240L, 10_240));
        assertSame(pcm, audio.pcmDriver(), "silent playback leaves the loaded PCM driver in place");
        assertEquals(cdda, audio.state().cdda());
        harness.step(0, CdFlowHarness.C, false);
        assertArrayEquals(menu, draw(harness), "Back returns to the Pencil Test row");
        assertSame(pcm, audio.pcmDriver());

        harness.idle(1);
        int[] reopenedMenu = draw(harness);
        harness.step(0, CdFlowHarness.A, false);
        int elapsed = pencilPictureTick(bank, seek);
        harness.idle(elapsed);
        assertArrayEquals(firstVisit, draw(harness), "reopening starts a fresh clip");
        harness.idle(pencilPictureTick(839, seek) + 60 - elapsed);
        assertFalse(Arrays.equals(menu, draw(harness)), "the final picture is still in the playback view");
        harness.idle(1);
        assertArrayEquals(reopenedMenu, draw(harness), "EOF returns to the Pencil Test row");
        assertSame(pcm, audio.pcmDriver());
        assertEquals(cdda, audio.state().cdda());
    }

    private static int pencilPictureTick(int bank, int seek) {
        // Five sectors per picture at 75 Hz, quantized to the NTSC V-int clock.
        long clocks = (bank + 1L) * 5 * 53_693_175;
        return seek + 2 + (int) Math.ceilDiv(clocks, 75L * 896_040);
    }

    private static void assertPencilPixels(int[] actual, byte[] bank) {
        var colors = new Palette();
        colors.fromSegaFormat(Arrays.copyOfRange(bank, 0x10, 0x30));
        int[] argb = new int[16];
        argb[0] = 0xff000000 | PaletteDisplayColor.packed(new Palette.Color(), GameServices.graphics());
        assertEquals(argb[0], actual[0], "the clip has a plain black surround");
        for (int color = 1; color < 16; color++) {
            argb[color] = 0xff000000 | PaletteDisplayColor.packed(colors.colors[color], GameServices.graphics());
        }
        int opaque = 0;
        for (int y = 0; y < 112; y++) {
            for (int x = 0; x < 168; x++) {
                int tile = (y / 8) * 21 + x / 8;
                int packed = bank[0x30 + tile * 32 + (y % 8) * 4 + (x % 8) / 2] & 0xff;
                int color = (x % 2 == 0 ? packed >> 4 : packed) & 15;
                int pixel = actual[(56 + y) * 320 + 80 + x];
                if (argb[color] != pixel) fail("Unexpected disc pixel at " + x + ", " + y
                        + " expected " + Integer.toHexString(argb[color]) + " got " + Integer.toHexString(pixel));
                if (color != 0) opaque++;
            }
        }
        assertTrue(opaque > 100, "sample includes visible disc tiles");
    }

    private static int[] draw(CdFlowHarness harness) {
        assertEquals(GameMode.OPTIONS, harness.loop.getCurrentGameMode());
        var screen = NativeMenuPresentationAccess.screen(harness.loop);
        assertNotNull(screen);
        screen.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT);
        screen.draw();
        GameServices.graphics().flushScreenSpace();
        return ScreenshotCapture.captureFramebuffer(320, 224).pixels();
    }
}
