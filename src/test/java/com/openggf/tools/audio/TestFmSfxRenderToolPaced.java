package com.openggf.tools.audio;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code --paced} renders the way the game plays. The default direct-read render never enables
 * the S3K DAC (its legacy policy has no idle-loop enable), so a promo bed rendered that way had no
 * drums (2026-10-23 Time Attack promo); the paced render must enable it.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestFmSfxRenderToolPaced {
    private static final int AZURE_LAKE = 0x20;
    private static final double RATE = 8_000;

    @TempDir
    Path out;

    @Test
    void pacedS3kMusicEnablesTheDac() {
        File file = RomTestUtils.ensureSonic3kRomAvailable();
        assertNotNull(file);
        Rom rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));

        FmSfxRenderTool.PacedRender render = FmSfxRenderTool.renderPaced(new Sonic3kAudioProfile(),
                "s3k", rom, AZURE_LAKE, true, RATE, (int) (5 * RATE));

        assertTrue(render.dacEnables() > 0, "the song's drum samples reach the DAC");
        boolean audible = false;
        for (short sample : render.samples()) {
            audible |= sample != 0;
        }
        assertTrue(audible, "the paced render produces sound");
    }

    @Test
    void pacedFlagWritesOnlyThePacedWav() throws Exception {
        File file = RomTestUtils.ensureSonic3kRomAvailable();
        assertNotNull(file);

        FmSfxRenderTool.main(new String[] {"--game", "s3k", "--rom", file.getAbsolutePath(),
                "--music", "20", "--out", out.toString(), "--rate", "8000", "--max-seconds", "2",
                "--paced"});

        assertTrue(Files.size(out.resolve("s3k-music-20-paced.wav")) > 44);
        assertTrue(Files.notExists(out.resolve("s3k-music-20-mix.wav")));
    }
}
