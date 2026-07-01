package com.openggf.audio;

/**
 * Simple unsigned 8-bit mono PCM stream mixed into the stereo output path.
 */
final class PcmSampleStream implements AudioStream {
    private static final int YM_DAC_GAIN = 64;

    private final byte[] pcm;
    private final double sourceStep;
    private double sourcePosition;

    PcmSampleStream(byte[] pcm, int sourceSampleRate, int outputSampleRate) {
        this.pcm = pcm != null ? pcm : new byte[0];
        int safeSourceRate = Math.max(1, sourceSampleRate);
        int safeOutputRate = Math.max(1, outputSampleRate);
        this.sourceStep = (double) safeSourceRate / safeOutputRate;
    }

    @Override
    public int read(short[] buffer) {
        return read(buffer, buffer.length);
    }

    @Override
    public int read(short[] buffer, int length) {
        int limit = Math.min(length, buffer.length);
        int out = 0;
        while (out + 1 < limit && !isComplete()) {
            int index = (int) sourcePosition;
            int centered = (pcm[index] & 0xFF) - 0x80;
            short sample = (short) (centered * YM_DAC_GAIN);
            buffer[out++] = sample;
            buffer[out++] = sample;
            sourcePosition += sourceStep;
        }
        return out;
    }

    @Override
    public boolean isComplete() {
        return sourcePosition >= pcm.length;
    }
}
