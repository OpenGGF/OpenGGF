package com.openggf.game.internal;

import com.openggf.audio.GameAudioProfile;

/** Host-owned native menu source. Its media stays open until the isolated session closes. */
public interface HostedMenuMusic extends AutoCloseable {
    GameAudioProfile profile();
    int musicId();
    boolean complete();
    @Override void close() throws java.io.IOException;
}
