package com.openggf.mods.scene;

import java.util.List;

/**
 * Opaque host-owned ROM music; PCM never crosses the creator API. Sample rate, length
 * and note metadata remain readable after retirement, but only the current preparation
 * can start playback. Retaining this handle does not retain retired PCM buffers.
 */
@com.openggf.game.ModApi
public interface ScenePreparedMusic {
    int sampleRate();
    long lengthSamples();
    List<SceneNoteEvent> notes();
}
