package openggf.timeattack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestLegacyTimeAttackSettings {
    @TempDir Path directory;

    @Test void importsGeneratedYamlScalarsCommentsAndQuotedTextWithoutChangingTheOriginal() throws Exception {
        String yaml = """
                format: 2
                timeAttack:
                  retryKey: F6 # retry
                  menuKey: F10
                  net:
                    hostPort: 30001
                    displayName: 'Tails'' #1'
                    lastJoinAddress: "host:1234#code"
                    masterUrl: "wss://example.test/master?token=one#two"
                    masterTrustInsecure: true
                  hud:
                    minimap: false
                other:
                  retryKey: F1
                """;
        Path source = Files.writeString(directory.resolve("config.yaml"), yaml);
        MemoryModStorage storage = new MemoryModStorage();
        LegacyTimeAttackSettings.importOnce(storage, directory);
        TimeAttackSettings settings = TimeAttackSettings.load(storage);
        assertEquals(295, settings.retryKey());
        assertEquals(30001, settings.hostPort());
        assertEquals("Tails' #1", settings.displayName());
        assertEquals("host:1234#code", settings.lastJoinAddress());
        assertEquals("wss://example.test/master?token=one#two", settings.masterUrl());
        assertTrue(settings.masterTrustInsecure());
        assertFalse(settings.minimap());
        assertEquals(yaml, Files.readString(source));
        Files.writeString(source, "timeAttack:\n  retryKey: F1\n");
        LegacyTimeAttackSettings.importOnce(storage, directory);
        assertEquals(295, TimeAttackSettings.load(storage).retryKey());
    }

    @Test void importsLegacyJsonBackupAndLetsYamlOverridesWin() throws Exception {
        Files.writeString(directory.resolve("config.json.bak"), """
                {"TIME_ATTACK_RETRY_KEY": 294,
                 "TIME_ATTACK_NET_HOST_PORT": 30002,
                 "TIME_ATTACK_NET_DISPLAY_NAME": "Tails \\u00e9",
                 "TIME_ATTACK_HUD_MINIMAP": false}
                """);
        Files.writeString(directory.resolve("config.yaml"), "timeAttack:\n  net:\n    hostPort: 30003\n");
        MemoryModStorage storage = new MemoryModStorage();
        LegacyTimeAttackSettings.importOnce(storage, directory);
        TimeAttackSettings settings = TimeAttackSettings.load(storage);
        assertEquals(294, settings.retryKey());
        assertEquals(30003, settings.hostPort());
        assertEquals("Tails é", settings.displayName());
        assertFalse(settings.minimap());
    }

    @Test void existingModSettingsAreNeverOverwritten() throws Exception {
        Files.writeString(directory.resolve("config.yaml"), "timeAttack:\n  retryKey: F1\n");
        MemoryModStorage storage = new MemoryModStorage();
        storage.write(TimeAttackSettings.FILE_NAME, "retryKey=295\nhostPort=31000\n");
        LegacyTimeAttackSettings.importOnce(storage, directory);
        assertEquals(295, TimeAttackSettings.load(storage).retryKey());
        assertEquals(31000, TimeAttackSettings.load(storage).hostPort());
    }

    @Test void badValuesDoNotDiscardOtherValidPreferences() throws Exception {
        Files.writeString(directory.resolve("config.yaml"), """
                timeAttack:
                  retryKey: NOT_A_KEY
                  net:
                    hostPort: -1
                    displayName: "Runner"
                    masterTrustInsecure: perhaps
                """);
        MemoryModStorage storage = new MemoryModStorage();
        LegacyTimeAttackSettings.importOnce(storage, directory);
        TimeAttackSettings settings = TimeAttackSettings.load(storage);
        assertEquals(82, settings.retryKey());
        assertEquals(27888, settings.hostPort());
        assertEquals("Runner", settings.displayName());
        assertFalse(settings.masterTrustInsecure());
    }

    @Test void oversizedConfigDoesNotCreateAMigrationResult() throws Exception {
        Files.write(directory.resolve("config.yaml"), new byte[1024 * 1024 + 1]);
        MemoryModStorage storage = new MemoryModStorage();
        assertThrows(java.io.IOException.class, () -> LegacyTimeAttackSettings.importOnce(storage, directory));
        assertTrue(storage.list().isEmpty());
    }
    @Test void newestNumberedBackupRestoresPreferencesLostDuringCoreConfigRewrite() throws Exception {
        Files.writeString(directory.resolve("config.yaml.bak"), "timeAttack: {retryKey: F1}\n");
        Files.writeString(directory.resolve("config.yaml.bak.2"), "timeAttack: {retryKey: F2}\n");
        Files.writeString(directory.resolve("config.yaml.bak.10"), "timeAttack: {retryKey: F6}\n");
        Files.writeString(directory.resolve("config.yaml"), "format: 2\naudio: {volume: 0.5}\n");
        MemoryModStorage storage = new MemoryModStorage();
        LegacyTimeAttackSettings.importOnce(storage, directory);
        assertEquals(295, TimeAttackSettings.load(storage).retryKey());
    }

}
