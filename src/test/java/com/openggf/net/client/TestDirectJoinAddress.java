package com.openggf.net.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestDirectJoinAddress {
    private static final String CERT_PIN = "ab".repeat(32);
    private static final String HOST_ID = "cd".repeat(32);

    @Test
    void inviteRoundTripsBothBindingsAndUsesWss() {
        String invite = "127.0.0.1:27888#" + DirectJoinAddress.shareCode(CERT_PIN, HOST_ID);
        DirectJoinAddress parsed = DirectJoinAddress.parse(invite, 10000);
        assertEquals("wss://127.0.0.1:27888/race", parsed.uri().toString());
        assertEquals(CERT_PIN, parsed.certificateSha256());
        assertEquals(HOST_ID, parsed.hostFingerprint());
    }

    @Test
    void rejectsMissingOrTamperedInviteBeforeNetworkConnection() {
        assertThrows(IllegalArgumentException.class,
                () -> DirectJoinAddress.parse("127.0.0.1:27888", 27888));
        assertThrows(IllegalArgumentException.class,
                () -> DirectJoinAddress.parse("ws://127.0.0.1:27888#"
                        + DirectJoinAddress.shareCode(CERT_PIN, HOST_ID), 27888));
        assertThrows(IllegalArgumentException.class,
                () -> DirectJoinAddress.parse("127.0.0.1:27888#bad", 27888));
    }
}
