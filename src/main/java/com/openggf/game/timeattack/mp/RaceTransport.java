package com.openggf.game.timeattack.mp;

import com.openggf.net.client.RaceClient;
import com.openggf.net.protocol.ControlMessage;

import java.util.List;

/** Engine-side view of a room connection; network code remains engine-free. */
public interface RaceTransport {
    List<RaceClient.InboundEvent> drainInbound();

    void sendControl(ControlMessage message);

    void sendBinary(byte[] data);

    int playerSlot();

    boolean isOpen();

    void close();
}
