package com.openggf.game;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guide opens on its own at most once per run, only while unsettled, and never in test mode. */
class TestSetupGuideSession {
    @TempDir Path dir;

    @Test void theAutomaticOpeningIsClaimedOncePerRun() {
        var config = SonicConfigurationService.createStandalone(dir);
        var session = new SetupGuideSession(false);
        assertTrue(session.claimAutomaticOpen(config));
        assertFalse(session.claimAutomaticOpen(config), "a rebuilt hub in the same run does not open it again");
        assertTrue(session.automaticOpenUsed());
    }

    @Test void aFinishedOrSkippedGuideNeverOpensOnItsOwn() throws Exception {
        var config = SonicConfigurationService.createStandalone(dir);
        SetupGuidePreset.skip(config);
        assertFalse(new SetupGuideSession(true).claimAutomaticOpen(SonicConfigurationService.createStandalone(dir)));
        ConfigurationPersistence.apply(config, java.util.Map.of(SonicConfiguration.SETUP_GUIDE, SetupGuidePreset.DONE));
        assertFalse(new SetupGuideSession(true).claimAutomaticOpen(config));
    }

    @Test void testModeNeverOpensIt() {
        var config = SonicConfigurationService.createStandalone(dir);
        config.setSessionOverride(SonicConfiguration.TEST_MODE_ENABLED, true);
        assertFalse(new SetupGuideSession(false).claimAutomaticOpen(config));
    }

    @Test void anUpgradeIsAConfigurationFileThatExistedBeforeTheRun() throws Exception {
        try (var reference = getClass().getResourceAsStream("/config.yaml")) {
            Files.copy(reference, dir.resolve("config.yaml.example"));
        }
        var firstRun = SonicConfigurationService.createStandalone(dir);
        var fresh = SetupGuideSession.forConfiguration(firstRun);
        firstRun.ensureConfigFileExists();
        assertFalse(fresh.existingInstall(), "The extracted reference and subsequent bootstrap are a fresh install");
        assertTrue(fresh.claimAutomaticOpen(firstRun));

        Files.writeString(dir.resolve("config.yaml"), "display:\n  aspect: \"NATIVE_4_3\"\n");
        var existing = SonicConfigurationService.createStandalone(dir);
        assertTrue(SetupGuideSession.forConfiguration(existing).existingInstall());
    }
}
