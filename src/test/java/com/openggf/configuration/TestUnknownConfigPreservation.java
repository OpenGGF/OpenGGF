package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Unknown YAML belongs to the user even when a later engine version stops consuming it. */
class TestUnknownConfigPreservation {
    @TempDir Path directory;

    private Path config() { return directory.resolve("config.yaml"); }
    private Path backup() { return directory.resolve("config.yaml.bak"); }
    private String sparse(String contents) {
        return ConfigYamlWriter.FORMAT_KEY + ": " + ConfigYamlWriter.SPARSE_FORMAT + "\n" + contents;
    }

    @Test void automaticLegacyRewriteCopiesExactSourceBeforeDroppingUnknownFields() throws Exception {
        String original = "# Keep this previous-version data\nretiredFeature:\n  server: 'example:1234'\n";
        Files.writeString(config(), original);
        SonicConfigurationService.createStandalone(directory);
        assertEquals(original, Files.readString(backup()));
        assertFalse(Files.readString(config()).contains("retiredFeature"));
        assertTrue(Files.readString(config()).contains(ConfigYamlWriter.FORMAT_KEY));
    }

    @Test void sparseSourceIsPreservedOnItsFirstLaterSaveOnly() throws Exception {
        String original = sparse("futurePlugin:\n  setting: 123\n");
        Files.writeString(config(), original);
        SonicConfigurationService service = SonicConfigurationService.createStandalone(directory);
        assertEquals(original, Files.readString(config()));
        assertFalse(Files.exists(backup()), "a sparse load must not rewrite or back up the file prematurely");
        service.setConfigValue(SonicConfiguration.FPS, 50);
        service.saveConfig();
        assertEquals(original, Files.readString(backup()));
        assertFalse(Files.readString(config()).contains("futurePlugin"));
        service.saveConfig();
        assertFalse(Files.exists(directory.resolve("config.yaml.bak.1")));
    }

    @Test void settingsApplyUsesTheSamePreservationBoundaryAndKeepsPriorBackups() throws Exception {
        Files.writeString(backup(), "older backup");
        String original = sparse("obsolete:\n  endpoint: kept\n");
        Files.writeString(config(), original);
        SonicConfigurationService service = SonicConfigurationService.createStandalone(directory);
        service.applySettings(Map.of(SonicConfiguration.FPS, 50));
        assertEquals("older backup", Files.readString(backup()));
        assertEquals(original, Files.readString(directory.resolve("config.yaml.bak.1")));
        assertEquals(50, service.getInt(SonicConfiguration.FPS));
        service.saveConfig();
        assertFalse(Files.exists(directory.resolve("config.yaml.bak.2")));
    }

    @Test void failedPreservationBlocksPublicationAndCanRetryWhenSourceReturns() throws Exception {
        String original = sparse("retired:\n  value: recoverable\n");
        Files.writeString(config(), original);
        SonicConfigurationService service = SonicConfigurationService.createStandalone(directory);
        Path displaced = directory.resolve("temporarily-unavailable.yaml");
        Files.move(config(), displaced);
        assertThrows(java.io.IOException.class,
                () -> service.applySettings(Map.of(SonicConfiguration.FPS, 50)));
        assertEquals(60, service.getInt(SonicConfiguration.FPS), "failed apply must not publish memory changes");
        service.saveConfig();
        assertFalse(Files.exists(config()), "a save must not overwrite its unavailable preservation source");
        Files.move(displaced, config());
        service.saveConfig();
        assertEquals(original, Files.readString(backup()));
        assertTrue(Files.exists(config()));
    }

    @Test void knownOnlySettingsNeedNoPreservationBackup() throws Exception {
        Files.writeString(config(), sparse("display:\n  fps: 50\n"));
        SonicConfigurationService service = SonicConfigurationService.createStandalone(directory);
        service.saveConfig();
        assertFalse(Files.exists(backup()));
    }
}
