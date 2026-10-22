package openggf.racing.server.master;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestMasterConfig {
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
