package com.openggf.audio;

/** Optional command-based native music route, independent of the SMPS music loader. */
public interface ExternalMusicCommands {
    /** Prepares native/streamed routing; true asks the shared owner to play the prepared override. */
    boolean routeMusicRequest(int id, StreamedMusicPort streamed);
}
