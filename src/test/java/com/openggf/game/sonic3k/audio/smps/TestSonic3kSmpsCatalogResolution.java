package com.openggf.game.sonic3k.audio.smps;

import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kSmpsCatalogResolution {
    @Test
    void everyDeclaredRetailSongAndSfxResolvesFromTheRom() {
        Sonic3kSmpsLoader loader =
                new Sonic3kSmpsLoader(TestEnvironment.currentRom());

        for (Sonic3kMusic music : Sonic3kMusic.values()) {
            assertNotNull(loader.loadMusic(music.id), music::name);
        }
        for (Sonic3kSfx sfx : Sonic3kSfx.values()) {
            assertNotNull(loader.loadSfx(sfx.id), sfx::name);
        }
        assertNotNull(loader.loadDacData(), "the retail DAC table must resolve");
        assertEquals(8, loader.getModEnvelopes().size(),
                "z80_ModEnvPointers has exactly eight retail entries");
        assertEquals(39, loader.getPsgEnvelopes().size(),
                "z80_VolEnvPointers has exactly 0x27 retail entries");
    }
}
