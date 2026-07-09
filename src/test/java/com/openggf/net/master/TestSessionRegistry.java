package com.openggf.net.master;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSessionRegistry {
    private long now = 1_000_000;
    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry(() -> now, MasterConfig.defaults());
    }

    private static ControlMessage.RoomDescriptor desc(String name) {
        return new ControlMessage.RoomDescriptor(name, "s3k", 0, 0,
                "OPEN", null, 8, false);
    }

    @Test
    void createListFindAndFilter() throws Exception {
        SessionRegistry.RoomEntry room = registry.create(desc("A"), "DIRECT", "fp-a",
                "1.1.1.1", 27888, "0.6:cafe");
        registry.create(new ControlMessage.RoomDescriptor("B", "s2", 0, 0,
                        "OPEN", null, 8, false), "RELAY", "fp-b", "2.2.2.2",
                0, "0.6:cafe");
        assertTrue(room.roomId().startsWith("r-"));
        assertEquals(2, registry.list(null).size());
        assertEquals(1, registry.list("s3k").size());
        assertEquals(1, registry.totalPages(null));
        assertTrue(registry.find(room.roomId()).isPresent());
    }

    @Test
    void perIdentityAndPerIpCapsReject() throws Exception {
        registry.create(desc("1"), "DIRECT", "fp", "1.1.1.1", 1, "f");
        registry.create(desc("2"), "DIRECT", "fp", "1.1.1.1", 2, "f");
        assertThrows(SessionRegistry.RoomCreateException.class,
                () -> registry.create(desc("3"), "DIRECT", "fp", "1.1.1.1", 3, "f"));

        registry.create(desc("4"), "DIRECT", "fp2", "9.9.9.9", 4, "f");
        registry.create(desc("5"), "DIRECT", "fp3", "9.9.9.9", 5, "f");
        registry.create(desc("6"), "DIRECT", "fp4", "9.9.9.9", 6, "f");
        registry.create(desc("7"), "DIRECT", "fp5", "9.9.9.9", 7, "f");
        assertThrows(SessionRegistry.RoomCreateException.class,
                () -> registry.create(desc("8"), "DIRECT", "fp6", "9.9.9.9", 8, "f"));
    }

    @Test
    void staleRoomsExpireHeartbeatKeepsAlive() throws Exception {
        SessionRegistry.RoomEntry room = registry.create(desc("A"), "DIRECT", "fp",
                "1.1.1.1", 1, "f");
        now += MasterConfig.defaults().roomHeartbeatTimeoutSeconds() * 1000 - 1;
        registry.heartbeat(room.roomId(), 3);
        assertEquals(0, registry.expireStale());
        assertEquals(3, registry.find(room.roomId()).orElseThrow().playerCount());

        now += MasterConfig.defaults().roomHeartbeatTimeoutSeconds() * 1000 + 1;
        assertEquals(1, registry.expireStale());
        assertTrue(registry.find(room.roomId()).isEmpty());
    }

    @Test
    void hostDisconnectRemovesAllTheirRooms() throws Exception {
        registry.create(desc("A"), "DIRECT", "fp", "1.1.1.1", 1, "f");
        registry.create(desc("B"), "DIRECT", "fp", "1.1.1.1", 2, "f");
        assertEquals(2, registry.removeByHostFingerprint("fp").size());
        assertTrue(registry.list(null).isEmpty());
    }

    @Test
    void relayRoomsAreExemptFromHeartbeatExpiry() throws Exception {
        SessionRegistry.RoomEntry relay = registry.create(desc("R"), "RELAY", "fp-r",
                "3.3.3.3", 0, "f");
        now += MasterConfig.defaults().roomHeartbeatTimeoutSeconds() * 1000 + 1;
        assertEquals(0, registry.expireStale());
        assertTrue(registry.find(relay.roomId()).isPresent());
    }

    @Test
    void configLoadsFromYamlWithDefaults(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Path yaml = dir.resolve("master.yaml");
        java.nio.file.Files.writeString(yaml, """
                port: 12345
                attackMode: true
                identityPowBits: 8
                """);
        MasterConfig config = MasterConfig.load(yaml);
        assertEquals(12345, config.port());
        assertTrue(config.attackMode());
        assertEquals(8, config.identityPowBits());
        assertEquals(20, MasterConfig.defaults().identityPowBits());
        assertEquals(2, config.maxRoomsPerIdentity());
        assertEquals(48, config.establishedAgeHours());
        assertEquals(27900, MasterConfig.defaults().port());
        assertEquals(0, new MasterConfig(0, null, null, true, null, 0, "t",
                0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0).port());
    }
}
