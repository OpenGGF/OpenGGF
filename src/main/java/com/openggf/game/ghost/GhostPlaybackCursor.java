package com.openggf.game.ghost;

/** Spawn-anchored playback: attempt frame N maps directly to recorded frame N. */
public final class GhostPlaybackCursor {
    private final GhostRecording recording;

    public GhostPlaybackCursor(GhostRecording recording) {
        this.recording = recording;
    }

    public GhostFrame frameFor(int attemptFrame) {
        return recording.frameAt(attemptFrame);
    }

    public boolean isFinishedAt(int attemptFrame) {
        return recording.frameAt(attemptFrame).finished();
    }
}
