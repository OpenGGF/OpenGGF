package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;

import java.util.List;

/** Transport-neutral connection to a race room, direct or master-relayed. */
public interface RaceConnection {
    List<RaceClient.InboundEvent> drainInbound();
    void sendControl(ControlMessage message);
    void sendBinary(byte[] data);
    int playerSlot();
    String sessionToken();
    ControlMessage.JoinAccepted joinAccepted();
    boolean isOpen();
    void close();
}
