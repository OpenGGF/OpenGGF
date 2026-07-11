package com.openggf.game.timeattack.mp;

import com.openggf.net.client.RaceClient;
import com.openggf.net.client.RaceConnection;
import com.openggf.net.protocol.ControlMessage;

import java.util.List;

/** Engine-side view of a room connection; network code remains engine-free. */
@com.openggf.game.ModApi
public interface RaceTransport {
    static RaceTransport from(RaceConnection connection) {
        return new RaceTransport() {
            @Override public List<RaceClient.InboundEvent> drainInbound() {
                return connection.drainInbound();
            }
            @Override public void sendControl(ControlMessage message) {
                connection.sendControl(message);
            }
            @Override public void sendBinary(byte[] data) { connection.sendBinary(data); }
            @Override public int playerSlot() { return connection.playerSlot(); }
            @Override public String sessionToken() {
                return connection.joinAccepted() == null ? null
                        : connection.uploadSessionToken();
            }
            @Override public boolean isOpen() { return connection.isOpen(); }
            @Override public void close() { connection.close(); }
        };
    }

    List<RaceClient.InboundEvent> drainInbound();

    void sendControl(ControlMessage message);

    void sendBinary(byte[] data);

    int playerSlot();

    default String sessionToken() { return null; }

    boolean isOpen();

    void close();
}
