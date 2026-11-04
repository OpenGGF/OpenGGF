package com.openggf.net.client;

import com.openggf.net.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in live DNS/TLS/admission/browser witness. Uses a disposable identity and
 * never creates rooms, joins strangers, submits attempts or changes server trust.
 * Origin: external develop master health check (2026-11-03).
 */
@EnabledIfSystemProperty(named = "openggf.externalMaster.enabled", matches = "true")
@Timeout(60)
class ExternalMasterIT {
    @Test
    void admitsPlayerAndBrowsesRooms(@TempDir Path directory) throws Exception {
        String address = System.getProperty("openggf.externalMaster");
        assertNotNull(address, "Run with -Pexternal-master");
        assertFalse(address.isBlank(), "External master address must not be blank");
        try (MasterClient client = MasterClient.connect(address,
                PlayerIdentity.loadOrCreate(directory), "SERVICE-CHECK",
                "external-service-check", null).get(30, TimeUnit.SECONDS)) {
            var result = client.listRooms("s3k", 0).get(25, TimeUnit.SECONDS);
            assertNotNull(result.rooms());
            assertEquals(0, result.page());
            assertTrue(result.totalPages() >= 0);
            System.out.println("External master admitted player at " + client.endpoint()
                    + "; S3K browser returned " + result.rooms().size() + " rooms");
        }
    }
}
