package com.openggf.mods;

/** Logical streamed playback state captured outside the presentation hot path. */
public record StreamedPlaybackSnapshot(TrackKey key, int logicalMusicId,
                                       double sourceFramePosition, int pauseMask,
                                       StreamedFadeSnapshot fade, double rate) {
    public StreamedPlaybackSnapshot {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(fade, "fade");
        if (logicalMusicId < -1) throw new IllegalArgumentException("Logical music id must be -1 or nonnegative");
        if (!Double.isFinite(sourceFramePosition) || sourceFramePosition < 0) {
            throw new IllegalArgumentException("Source frame position must be finite and nonnegative");
        }
        int knownMask = StreamedMusicPlayer.PAUSE_JINGLE
                | StreamedMusicPlayer.PAUSE_APP | StreamedMusicPlayer.PAUSE_REWIND;
        if (pauseMask < 0 || (pauseMask & ~knownMask) != 0) {
            throw new IllegalArgumentException("Unknown streamed pause bits");
        }
        if (!Double.isFinite(rate) || (rate != 1.0 && rate != 1.25)) {
            throw new IllegalArgumentException("Unsupported streamed playback rate");
        }
    }
}
