package com.openggf.mods.code;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class TestGolfTransport {
    @TempDir Path temp;

    @Test void localhostOrderingBoundsDeadlineAndCleanup() throws Exception {
        TestGolfProtocol.runProbe(temp, "TransportProbe", "run");
    }
    @Test void productionRoomHandshakeExactlyOnceReconnectAndHostExit() throws Exception {
        TestGolfProtocol.runProbe(temp, "RoomProbe", "run");
    }
}
