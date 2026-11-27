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
        assertFalse(SetupGuideSession.forConfiguration(SonicConfigurationService.createStandalone(dir)).existingInstall());
        Files.writeString(dir.resolve("config.yaml"), "display:\n  aspect: \"NATIVE_4_3\"\n");
        assertTrue(SetupGuideSession.forConfiguration(SonicConfigurationService.createStandalone(dir)).existingInstall());
    }
}
