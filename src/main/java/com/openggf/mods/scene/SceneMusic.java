package com.openggf.mods.scene;

/** Finite ROM-derived music for scenes whose rules use the audible song clock. */
@com.openggf.game.ModApi
public interface SceneMusic {
    /**
     * Prepares at most ten minutes at NTSC driver cadence; no PCM is read from mod assets.
     * Full and selected-part stereo PCM together are bounded to 256 MiB. Requests
     * exceeding that budget at the current output rate are rejected before allocation.
     * Repeating the same request reuses the current preparation. A different request
     * retires it and stops its player; old metadata remains readable but cannot start.
     */
    ScenePreparedMusic prepare(String gameId, int musicId, int durationFrames);

    /**
     * Prepares ROM music without waiting for synthesis. The production host loads
     * ROM data on the scene thread and renders independently in one bounded worker.
     * A different request cancels the previous job and retires the current song.
     * Closing the scene cancels pending work. Legacy hosts may complete synchronously.
     */
    default SceneMusicPreparation prepareAsync(String gameId, int musicId, int durationFrames) {
        return completed(prepare(gameId, musicId, durationFrames));
    }

    /**
     * Prepares a selected-part mix without starting audio. The production host
     * stops any current player and renders the mix asynchronously; starting this
     * exact selection after READY reuses it. Legacy hosts may render during start.
     */
    default SceneMusicPreparation preparePartAsync(ScenePreparedMusic song, java.util.List<SceneMusicPart> parts) {
        java.util.Objects.requireNonNull(parts, "parts");
        return completed(song);
    }

    private static SceneMusicPreparation completed(ScenePreparedMusic song) {
        java.util.Objects.requireNonNull(song, "song");
        return new SceneMusicPreparation() {
            @Override public State state() { return State.READY; }
            @Override public int progressPercent() { return 100; }
            @Override public String error() { return null; }
            @Override public ScenePreparedMusic prepared() { return song; }
            @Override public void cancel() { }
        };
    }

    /** Starts the current preparation with the supplied logical-channel selection as its musical part. */
    SceneMusicPlayer start(ScenePreparedMusic song, int fmMask, int psgMask,
                          boolean dacMuted, int leadInSamples);

    /** Starts a curated musical role whose logical channels change between song sections. */
    default SceneMusicPlayer start(ScenePreparedMusic song, java.util.List<SceneMusicPart> parts,
                                   int leadInSamples) {
        if (parts.size() != 1 || parts.getFirst().onsetSamples() != 0) {
            throw new UnsupportedOperationException("section music is unavailable in this scene host");
        }
        SceneMusicPart part = parts.getFirst();
        return start(song, part.fmMask(), part.psgMask(), part.dacMuted(), leadInSamples);
    }
}
