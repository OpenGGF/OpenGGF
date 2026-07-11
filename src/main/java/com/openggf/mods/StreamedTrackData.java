package com.openggf.mods;

import java.util.Objects;

/** Immutable playback view over launch-prepared, exclusively owned PCM. */
public final class StreamedTrackData {
    private final TrackKey key;
    private final PcmData pcm;
    private final int frameCount;
    private final long loopStartFrame;
    private final long loopEndFrame;
    private final float gain;
    private final boolean tempoEffects;

    public StreamedTrackData(PreparedTrack prepared) {
        Objects.requireNonNull(prepared, "prepared");
        key = prepared.key();
        pcm = prepared.pcm();
        frameCount = pcm.frameCount();
        loopStartFrame = prepared.loopStartFrame();
        loopEndFrame = prepared.loopEndFrame();
        gain = prepared.gain();
        tempoEffects = prepared.tempoEffects();
    }

    public TrackKey key() { return key; }
    public int sampleRate() { return pcm.sampleRate(); }
    public int channels() { return pcm.channels(); }
    public int frameCount() { return frameCount; }
    public long loopStartFrame() { return loopStartFrame; }
    public long loopEndFrame() { return loopEndFrame; }
    public float gain() { return gain; }
    public boolean tempoEffects() { return tempoEffects; }
    public boolean looping() { return loopEndFrame > loopStartFrame; }

    short sampleAt(int frame, int channel) {
        int sourceChannel = pcm.channels() == 1 ? 0 : channel;
        return pcm.sampleAt(frame * pcm.channels() + sourceChannel);
    }
}
