package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;

import java.util.List;

/** Transport-neutral connection to a race room, direct or master-relayed. */
@com.openggf.game.ModApi
public interface RaceConnection {
    List<RaceClient.InboundEvent> drainInbound();
    void sendControl(ControlMessage message);
    void sendBinary(byte[] data);
    int playerSlot();
    String sessionToken();
    /** Master admission token used for out-of-band HTTP; direct rooms have none. */
    default String uploadSessionToken() { return null; }
    ControlMessage.JoinAccepted joinAccepted();
    boolean isOpen();
    void close();
}
