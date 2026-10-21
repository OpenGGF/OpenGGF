package openggf.racing.server.verifier;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.VerdictCodec;
import openggf.racing.server.master.MasterConfig;
import openggf.racing.server.master.MasterServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The verifier's HTTP transport against the real master routes over loopback. */
@Timeout(60)
class TestHttpMasterApiAgainstMaster {
    private static final String HASH = "0".repeat(64);

    @TempDir Path dir;

    @Test
    void realMasterRefusesAVerdictForAJobItNoLongerHoldsAsConflict() throws Exception {
        Path yaml = dir.resolve("master.yaml");
        Files.writeString(yaml, """
                port: 0
                adminPort: 0
                adminToken: secret-admin-token
                plaintextForTest: true
                dbPath: ids.db
                verifierRegistrationToken: register-me
                """);
        try (MasterServer master = MasterServer.start(MasterConfig.load(yaml),
                dir.resolve("master"))) {
            HttpMasterApi api = new HttpMasterApi("http://127.0.0.1:" + master.port(),
                    "register-me", false);
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("worker"));
            api.register(identity.publicKeyEncoded(), Set.of("fp"));
            assertEquals(Optional.empty(), api.pollJobJson(), "an empty queue answers 204");
            api.heartbeat();

            // MasterHttpRoutes answers 409 for a job it no longer tracks, exactly as for a
            // voided or re-leased one (TestMasterHttpRoutes pins those paths); the worker
            // must see a refused verdict rather than a failed cycle.
            byte[] signature = identity.sign(VerdictCodec.canonicalBytes(
                    "vj-gone", "r#1#1", HASH, VerdictCodec.RESULT_PASS));
            assertFalse(api.postVerdict("vj-gone", VerdictCodec.RESULT_PASS, signature));
        }
    }
}
