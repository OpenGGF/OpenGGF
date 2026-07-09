package com.openggf.net.hub;

/** Transport-neutral peer connection used by both direct and relay rooms. */
public interface HubConnection {
    void sendText(String text);

    void sendBinary(byte[] data);

    void close(String reason);

    String remoteHost();
}
