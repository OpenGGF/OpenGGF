package com.openggf.data;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestRomManagerGameResolution {
    @TempDir Path temp;
    private EngineContext previous;

    @AfterEach void restoreServices() {
        if (previous != null) EngineServices.configure(previous);
    }

    @Test
    void resolvesOnlyExplicitStockGameCodesAndFailsClosedOtherwise() {
        previous = EngineServices.current();
        SonicConfigurationService config = SonicConfigurationService.createStandalone(temp);
        config.setConfigValue(SonicConfiguration.SONIC_1_ROM, "one.gen");
        config.setConfigValue(SonicConfiguration.SONIC_2_ROM, "two.gen");
        config.setConfigValue(SonicConfiguration.SONIC_3K_ROM, "three.gen");
        EngineServices.configure(new EngineContext(config, previous.graphics(), previous.audio(),
                previous.roms(), previous.profiler(), previous.debugOverlay(),
                previous.playbackDebug(), previous.romDetection(), previous.crossGameFeatures(),
                previous.moduleResolutionService()));

        assertEquals("one.gen", RomManager.resolveRomForGame("S1"));
        assertEquals("two.gen", RomManager.resolveRomForGame("s2"));
        assertEquals("three.gen", RomManager.resolveRomForGame("s3k"));
        assertThrows(IllegalArgumentException.class,
                () -> RomManager.resolveRomForGame("owner-game"));
        assertThrows(IllegalArgumentException.class,
                () -> RomManager.resolveRomForGame("standalone"));
        assertThrows(IllegalArgumentException.class,
                () -> RomManager.resolveRomForGame(null));
    }
}
