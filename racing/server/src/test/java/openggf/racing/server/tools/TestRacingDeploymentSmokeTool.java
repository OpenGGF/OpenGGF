package openggf.racing.server.tools;

import openggf.racing.server.master.SqliteIdentityStore;
import com.openggf.net.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestRacingDeploymentSmokeTool {
    private static final String HASH = "ab".repeat(32);

    @Test
    void preparationRefusesToAlterExistingData(@TempDir Path parent) throws Exception {
        Path data = parent.resolve("fixture");
        RacingDeploymentSmokeTool.main(new String[]{"prepare", "--data", data.toString()});
        String fingerprint = PlayerIdentity.loadOrCreate(data.resolve("client")).fingerprint();
        assertThrows(FileAlreadyExistsException.class, () -> RacingDeploymentSmokeTool.main(
                new String[]{"prepare", "--data", data.toString()}));
        try (SqliteIdentityStore store = new SqliteIdentityStore(data.resolve("master/master-identities.db"))) {
            assertEquals(50, store.find(fingerprint).orElseThrow().cleanRounds());
            assertEquals(0, store.verdictsFor(fingerprint).size());
        }
        assertEquals(fingerprint, PlayerIdentity.loadOrCreate(data.resolve("client")).fingerprint());
    }

    @Test
    void witnessRequiresTlsAndOriginWithoutCredentials() {
        for (String bad : new String[]{"http://localhost:27900", "https://user@localhost",
                "https://localhost/master", "https://localhost?x=y", "https://localhost#fragment"}) {
            assertThrows(IllegalArgumentException.class, () -> RacingDeploymentSmokeTool.masterUri(bad));
        }
        assertEquals(URI.create("https://localhost:27900"),
                RacingDeploymentSmokeTool.masterUri("https://localhost:27900"));
    }

    @Test
    void uploadCannotSendPlayerTokenToAnotherOriginOrResource() {
        URI base = URI.create("https://localhost");
        assertDoesNotThrow(() -> RacingDeploymentSmokeTool.requireSameOriginUpload(
                base, URI.create("https://localhost:443/recordings/" + HASH), HASH));
        for (String bad : new String[]{"https://elsewhere/recordings/", "http://localhost/recordings/",
                "https://localhost:27900/recordings/", "https://user@localhost/recordings/",
                "https://localhost/other/"}) {
            assertThrows(IllegalStateException.class, () -> RacingDeploymentSmokeTool.requireSameOriginUpload(
                    base, URI.create(bad + HASH), HASH));
        }
    }
}
