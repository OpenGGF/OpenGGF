package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** ROM theme decoders and isolated final-PCM production; no desktop/audio device required. */
class TestHubMusicRomThemes {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1TitleThemeLoadsAndPresentsWithoutADevice() throws Exception {
        present("s1", SonicConfiguration.SONIC_1_ROM, RomTestUtils.ensureSonic1RomAvailable());
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2TitleThemeLoadsAndPresentsWithoutADevice() throws Exception {
        present("s2", SonicConfiguration.SONIC_2_ROM, RomTestUtils.ensureSonic2RomAvailable());
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kTitleThemeLoadsAndPresentsWithoutADevice() throws Exception {
        present("s3k", SonicConfiguration.SONIC_3K_ROM, RomTestUtils.ensureSonic3kRomAvailable());
    }
    private void present(String gameId, SonicConfiguration key, File image) throws Exception {
        assertNotNull(image, "Required ROM path must resolve");
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(key, image.getAbsolutePath());
        config.setConfigValue(SonicConfiguration.AUDIO_FM_CORE, "fast");
        try (HubMusicController.Session session = HubMusicController.openRomSession(gameId, config, true)) {
            session.volume(20, false);
            for (int i = 0; i < 90; i++) session.frame();
            session.volume(10, true);
            session.frame();
        }
    }
}
