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
    @Test void guestResumeRetainsHostPause() throws Exception {
        TestGolfProtocol.runProbe(temp, "RoomProbe", "guestResumeRetainsHostPause");
    }
    @Test void hostResumeRetainsGuestPause() throws Exception {
        TestGolfProtocol.runProbe(temp, "RoomProbe", "hostResumeRetainsGuestPause");
    }
    @Test void pausedPendingRequestIsReofferedOnce() throws Exception {
        TestGolfProtocol.runProbe(temp, "RoomProbe", "pausedPendingRequestIsReofferedOnce");
    }
    @Test void concessionsPreserveOwnerAndScores() throws Exception {
        TestGolfProtocol.runProbe(temp, "RoomProbe", "concessionsPreserveOwnerAndScores");
    }
    @Test void pauseIntentResynchronizesAfterReconnect() throws Exception {
        TestGolfProtocol.runProbe(temp, "RoomProbe", "pauseIntentResynchronizesAfterReconnect");
    }
    @Test void handshakePauseIntentPrecedesReady() throws Exception {
        TestGolfProtocol.runProbe(temp, "RoomProbe", "handshakePauseIntentPrecedesReady");
    }
}
