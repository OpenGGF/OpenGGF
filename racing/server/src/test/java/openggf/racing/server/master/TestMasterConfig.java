package openggf.racing.server.master;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestMasterConfig {
    @Test
    void publicRelayCommunicationDefaultsToQuickAndOwnersCanRestrictFurther(@TempDir Path dir)
            throws Exception {
        MasterConfig defaults = MasterConfig.defaults();
        assertEquals("QUICK_CHAT", defaults.chatModeLimit());
        assertEquals("QUICK_CHAT", defaults.relayChatMode("FULL_CHAT"));
        assertEquals("QUICK_CHAT", defaults.relayChatMode("QUICK_CHAT"));
        assertEquals("OFF", defaults.relayChatMode("OFF"));

        Path yaml = dir.resolve("master.yaml");
        Files.writeString(yaml, "chatModeLimit: FULL_CHAT\n");
        MasterConfig full = MasterConfig.load(yaml);
        assertEquals("FULL_CHAT", full.relayChatMode("FULL_CHAT"));
        assertEquals("QUICK_CHAT", full.relayChatMode("QUICK_CHAT"));
        Files.writeString(yaml, "chatModeLimit: \"OFF\"\n");
        assertEquals("OFF", MasterConfig.load(yaml).relayChatMode("FULL_CHAT"));
        Files.writeString(yaml, "chatModeLimit: anything\n");
        assertThrows(IOException.class, () -> MasterConfig.load(yaml));
        assertThrows(IllegalArgumentException.class, () -> defaults.relayChatMode("anything"));
    }

    @Test
    void fileAndLiteralTokensRejectHeaderUnsafeCharactersWithoutRevealingThem(@TempDir Path dir)
            throws Exception {
        for (String suffix : new String[] {"\uFEFF", "\u00A0", "\u0001", " ", "\u007F"}) {
            String invalidToken = "credential-marker" + suffix + "suffix";
            Path config = dir.resolve("master.yaml");
            Files.writeString(dir.resolve("invalid.secret"), invalidToken);
            Files.writeString(config, "adminTokenFile: invalid.secret\n");
            IOException fileFailure = assertThrows(IOException.class, () -> MasterConfig.load(config));
            assertNoCredentialInDiagnostic(fileFailure);

            for (String field : new String[] {"adminToken", "verifierRegistrationToken"}) {
                // JSON quoted text is also a YAML scalar; escaping control characters allows
                // this test to exercise token validation rather than YAML syntax rejection.
                String scalar = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(invalidToken);
                Files.writeString(config, field + ": " + scalar + "\n");
                IOException literalFailure = assertThrows(IOException.class,
                        () -> MasterConfig.load(config));
                assertNoCredentialInDiagnostic(literalFailure);
            }
        }
    }

    @Test
    void yamlSyntaxErrorsDoNotEchoCredentialsFromSourceSnippets(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("master.yaml");
        Files.writeString(config, "adminToken: credential-marker\nport: [\n");
        assertNoCredentialInDiagnostic(assertThrows(IOException.class, () -> MasterConfig.load(config)));
    }

    private static void assertNoCredentialInDiagnostic(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            assertFalse(String.valueOf(cause.getMessage()).contains("credential-marker"),
                    "configuration diagnostics must not include credential contents");
        }
    }

    @Test
    void tokenFilesResolveBesideTheConfigAndTrimTrailingNewlines(@TempDir Path dir)
            throws Exception {
        Files.writeString(dir.resolve("admin.secret"), "  admin-from-file\n");
        Files.writeString(dir.resolve("verifier.secret"), "verifier-from-file\r\n");
        Path config = dir.resolve("master.yaml");
        Files.writeString(config, "adminTokenFile: admin.secret\n"
                + "verifierRegistrationTokenFile: verifier.secret\n");

        MasterConfig loaded = MasterConfig.load(config);
        assertEquals("admin-from-file", loaded.adminToken());
        assertEquals("verifier-from-file", loaded.verifierRegistrationToken());
    }

    @Test
    void fileAndLiteralSecretCannotBothBeConfigured(@TempDir Path dir) throws Exception {
        for (String field : new String[] {"adminToken", "verifierRegistrationToken"}) {
            Path config = dir.resolve("master.yaml");
            Files.writeString(config, field + ": literal\n" + field + "File: absent.secret\n");
            assertThrows(IOException.class, () -> MasterConfig.load(config));
        }
    }

    @Test
    void unusableSecretFilesFailInsteadOfSilentlyDisablingAuthentication(@TempDir Path dir)
            throws Exception {
        Files.writeString(dir.resolve("empty.secret"), " \n\t");
        Files.writeString(dir.resolve("oversize.secret"), "s".repeat(16 * 1024 + 1));
        Files.writeString(dir.resolve("multiline.secret"), "first\nsecond");
        Files.write(dir.resolve("invalid.secret"), new byte[] {(byte) 0xC3, (byte) 0x28});
        Files.createDirectory(dir.resolve("directory.secret"));
        for (String secret : new String[] {"missing.secret", "empty.secret", "oversize.secret",
                "multiline.secret", "invalid.secret", "directory.secret"}) {
            Path config = dir.resolve("master.yaml");
            Files.writeString(config, "adminTokenFile: " + secret + "\n");
            assertThrows(IOException.class, () -> MasterConfig.load(config));
        }
    }

    @Test
    void verifierStalenessMustLeaveRoomForHeartbeatRecovery(@TempDir Path dir) throws Exception {
        Path tooShort = dir.resolve("too-short.yaml");
        Files.writeString(tooShort, "verifierStaleSeconds: 119\n");
        IOException rejected = assertThrows(IOException.class, () -> MasterConfig.load(tooShort));
        assertEquals(IllegalArgumentException.class, rejected.getCause().getClass());

        Path minimum = dir.resolve("minimum.yaml");
        Files.writeString(minimum, "verifierStaleSeconds: 120\n");
        assertEquals(120, MasterConfig.load(minimum).verifierStaleSeconds());
    }
}
