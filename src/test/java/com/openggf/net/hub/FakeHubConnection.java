package com.openggf.net.hub;

import java.util.ArrayList;
import java.util.List;

/** Recording transport used by hub-layer tests. */
final class FakeHubConnection implements HubConnection {
    final List<String> text = new ArrayList<>();
    final List<byte[]> binary = new ArrayList<>();
    String closedReason;

    @Override public void sendText(String value) { text.add(value); }
    @Override public void sendBinary(byte[] value) { binary.add(value); }
    @Override public void close(String reason) { closedReason = reason; }
    @Override public String remoteHost() { return "127.0.0.1"; }
}
