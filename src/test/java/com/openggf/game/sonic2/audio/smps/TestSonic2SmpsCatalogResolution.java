package com.openggf.game.sonic2.audio.smps;

import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SmpsCatalogResolution {
    @Test
    void everyDeclaredRetailSongAndSfxResolvesFromTheRom() {
        Sonic2SmpsLoader loader =
                new Sonic2SmpsLoader(TestEnvironment.currentRom());

        for (Sonic2Music music : Sonic2Music.values()) {
            assertNotNull(loader.loadMusic(music.id), music::name);
        }
        for (Sonic2Sfx sfx : Sonic2Sfx.values()) {
            assertNotNull(loader.loadSfx(sfx.id), sfx::name);
        }
        assertNotNull(loader.loadDacData(), "the retail DAC table must resolve");
    }
}
