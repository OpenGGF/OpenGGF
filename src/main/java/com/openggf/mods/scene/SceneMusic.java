package com.openggf.mods.scene;

/** Finite ROM-derived music for scenes whose rules use the audible song clock. */
@com.openggf.game.ModApi
public interface SceneMusic {
    /**
     * Prepares at most 90 seconds at NTSC driver cadence; no PCM is read from mod assets.
     * Repeating the same request reuses the current preparation. A different request
     * retires it and stops its player; old metadata remains readable but cannot start.
     */
    ScenePreparedMusic prepare(String gameId, int musicId, int durationFrames);

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
