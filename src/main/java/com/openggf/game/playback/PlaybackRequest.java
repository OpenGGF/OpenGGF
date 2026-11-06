package com.openggf.game.playback;

import com.openggf.game.recording.UserRecordingPlaybackOptions;

import java.util.Objects;

public sealed interface PlaybackRequest {
    record Recording(PlaybackEntry.Recording entry, UserRecordingPlaybackOptions options) implements PlaybackRequest {
        public Recording {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(options, "options");
        }
    }

    record Trace(PlaybackEntry.Trace entry) implements PlaybackRequest {
        public Trace { Objects.requireNonNull(entry, "entry"); }
    }
}
