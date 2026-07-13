package com.openggf.mods;

import com.openggf.audio.StreamedMusicPort;

import java.util.Objects;

/** Allocation-free playback cursor over session-owned prepared PCM. */
public final class PreparedSfxCursor implements StreamedMusicPort.OneShot {
    private final PcmData pcm;
    private final float gain;
    private int frame;
    private boolean closed;

    public PreparedSfxCursor(PreparedSfx prepared) {
        Objects.requireNonNull(prepared, "prepared");
        this.pcm = prepared.pcm();
        this.gain = prepared.gain();
    }

    @Override
    public void mixInto(short[] output, int frames) {
        Objects.requireNonNull(output, "output");
        if (frames < 0 || frames > output.length / 2) throw new IllegalArgumentException("Invalid output frame count");
        if (closed) throw new IllegalStateException("One-shot cursor is closed");
        int available = Math.min(frames, pcm.frameCount() - frame);
        for (int index = 0; index < available; index++) {
            int source = (frame + index) * pcm.channels();
            int left = Math.round(pcm.sampleAt(source) * gain);
            int right = pcm.channels() == 1 ? left : Math.round(pcm.sampleAt(source + 1) * gain);
            int out = index * 2;
            output[out] = saturate(output[out] + left);
            output[out + 1] = saturate(output[out + 1] + right);
        }
        frame += available;
    }

    @Override public boolean complete() { return closed || frame >= pcm.frameCount(); }
    @Override public void close() { closed = true; }

    private static short saturate(int sample) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
    }
}
