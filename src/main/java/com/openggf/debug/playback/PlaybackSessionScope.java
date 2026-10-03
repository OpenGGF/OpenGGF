package com.openggf.debug.playback;

import java.util.Objects;

/** Internal owner of one movie's playback across starts and deferred level-load rebinds. */
public final class PlaybackSessionScope implements AutoCloseable {
    private final PlaybackDebugManager playback;
    private final Bk2Movie movie;
    private boolean closed;

    public PlaybackSessionScope(PlaybackDebugManager playback, Bk2Movie movie) {
        this.playback = Objects.requireNonNull(playback, "playback");
        this.movie = Objects.requireNonNull(movie, "movie");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            playback.endSessionIfOwnedBy(movie);
        }
    }
}
