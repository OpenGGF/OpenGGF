package com.openggf.game.sonic1.audio.smps;

import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1SmpsCatalogResolution {
    @Test
    void everyDeclaredRetailSongAndSfxResolvesFromTheRom() {
        Sonic1SmpsLoader loader =
                new Sonic1SmpsLoader(TestEnvironment.currentRom());

        for (Sonic1Music music : Sonic1Music.values()) {
            assertNotNull(loader.loadMusic(music.id), music::name);
        }
        for (Sonic1Sfx sfx : Sonic1Sfx.values()) {
            assertNotNull(loader.loadSfx(sfx.id), sfx::name);
        }
        assertNotNull(loader.loadDacData(), "the retail DAC table must resolve");
    }
}
