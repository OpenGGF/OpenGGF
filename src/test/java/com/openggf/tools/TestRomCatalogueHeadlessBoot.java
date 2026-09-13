package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomImageCatalogue;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomManager;
import com.openggf.game.GameId;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.FullReset;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boot smoke through the ROM catalogue: the per-game keys are blank, so every
 * game must be found in {@code roms.directory} by size and header. S3K additionally boots from an {@code SK} + {@code S3}
 * composite assembled from the lock-on dump's two windows. ROM-gated; skips
 * when an image is absent. No file is copied, linked or renamed.
 */
@FullReset
@ExtendWith(SingletonResetExtension.class)
class TestRomCatalogueHeadlessBoot {

    private Object oldS1;
    private Object oldS2;
    private Object oldS3k;
    private Object oldDirectory;
    private Object oldPreferComposite;
    private Object oldSkipIntros;

    @BeforeEach
    void rememberConfiguration() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        oldS1 = configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM);
        oldS2 = configuration.getConfigValue(SonicConfiguration.SONIC_2_ROM);
        oldS3k = configuration.getConfigValue(SonicConfiguration.SONIC_3K_ROM);
        oldDirectory = configuration.getConfigValue(SonicConfiguration.ROMS_DIRECTORY);
        oldPreferComposite = configuration.getConfigValue(SonicConfiguration.ROMS_PREFER_COMPOSITE);
        oldSkipIntros = configuration.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
    }

    @AfterEach
    void restoreConfiguration() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, oldS1);
        configuration.setConfigValue(SonicConfiguration.SONIC_2_ROM, oldS2);
        configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, oldS3k);
        configuration.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, oldDirectory);
        configuration.setConfigValue(SonicConfiguration.ROMS_PREFER_COMPOSITE, oldPreferComposite);
        configuration.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkipIntros);
        RomManager.getInstance().reloadCatalogue();
        SessionManager.clear();
    }

    private static void pointCatalogueAt(File image, boolean preferComposite) {
        Path directory = image.toPath().toAbsolutePath().getParent();
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, "");
        configuration.setConfigValue(SonicConfiguration.SONIC_2_ROM, "");
        configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, "");
        configuration.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, directory.toString());
        configuration.setConfigValue(SonicConfiguration.ROMS_PREFER_COMPOSITE, preferComposite);
        configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        RomManager.getInstance().reloadCatalogue();
    }

    /** Boots, steps a few frames and returns the description of the ROM that was booted. */
    private static String bootAndStep(RomIdentity logicalRom, GameId expectedGame, int zone, int act) throws Exception {
        EngineServices.current().graphics().initHeadless();
        try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            GameLoop loop = boot.boot(logicalRom, zone, act);
            var mode = SessionManager.getCurrentGameplayMode();
            assertEquals(expectedGame, mode.getWorldSession().resolvedGameModule().getGameId());
            var rom = RomManager.getInstance().getRom();
            assertTrue(rom.isInMemory(), "catalogue-served ROMs are in-memory views");
            assertTrue(mode.getLevelManager().getCurrentLevel() != null, "a level must be loaded after boot");
            for (int frame = 0; frame < 3; frame++) {
                loop.step();
            }
            assertTrue(rom.isOpen(), "the catalogue view stays open while stepping");
            return rom.describe();
        }
    }

    @Test
    void sonic1BootsFromTheDirectoryScanWhenItsKeyIsBlank() throws Exception {
        File image = RomTestUtils.ensureSonic1RomAvailable();
        Assumptions.assumeTrue(image != null, "Sonic 1 image is required");
        pointCatalogueAt(image, false);
        RomImageCatalogue.Resolution resolution = RomManager.getInstance().resolveLogicalRom(RomIdentity.S1).orElseThrow();
        assertTrue(resolution.source() != RomImageCatalogue.Source.EXPLICIT_KEY, resolution.describe());
        bootAndStep(RomIdentity.S1, GameId.S1, 0, 0);
    }

    @Test
    void sonic2BootsFromTheDirectoryScanWhenItsKeyIsBlank() throws Exception {
        File image = RomTestUtils.ensureSonic2RomAvailable();
        Assumptions.assumeTrue(image != null, "Sonic 2 image is required");
        pointCatalogueAt(image, false);
        bootAndStep(RomIdentity.S2, GameId.S2, 0, 0);
    }

    @Test
    void sonic3kBootsFromAnSkPlusS3CompositeOfTheLockOnWindows() throws Exception {
        File image = RomTestUtils.ensureSonic3kRomAvailable();
        Assumptions.assumeTrue(image != null, "Sonic 3 & Knuckles image is required");
        pointCatalogueAt(image, true);
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        RomImageCatalogue.Resolution resolution = RomManager.getInstance().resolveLogicalRom(RomIdentity.S3K).orElseThrow();
        Assumptions.assumeTrue(resolution.source() == RomImageCatalogue.Source.COMPOSITE,
                "expected a composite S3K resolution but got " + resolution.describe());
        assertEquals(2, resolution.parts().size());
        String booted = bootAndStep(RomIdentity.S3K, GameId.S3K, 0, 0);
        assertTrue(booted.contains("composite"), booted);
    }
}
