package com.openggf.audio.rewind;

@com.openggf.game.ModApi
public interface AudioReplayScope extends AutoCloseable {
    @Override
    void close();
}
