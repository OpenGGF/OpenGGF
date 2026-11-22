package com.openggf.audio.smps;

import java.util.List;

public interface SmpsSfxData {
    int getTickMultiplier();

    List<? extends SmpsSfxTrack> getTrackEntries();

    interface SmpsSfxTrack {
        int channelMask();

        int pointer();

        int transpose();

        int volume();

        /**
         * Claims an FM slot and preserves its chip state without servicing a
         * stream. Used when the native slot takes over with sound disabled
         * and its bytecode points outside the available program.
         */
        default boolean holdsFmState() { return false; }
    }
}
