package com.openggf.stageplay.runtime;

import com.openggf.audio.StreamedMusicPort;
import com.openggf.audio.synth.PsgChip;
import com.openggf.stageplay.StageplaySpeech;

/** Short, deterministic SN76489 register performances. No oscillator, ROM patch, file or sound channel is borrowed. */
public final class StageplaySpeechSynth {
    public static final int SAMPLE_RATE = 48_000;
    private static final int SAMPLES_PER_FRAME = SAMPLE_RATE / 60;
    private StageplaySpeechSynth() { }

    public static StreamedMusicPort.SfxPcm render(StageplaySpeech.Blip blip) {
        int count = blip.lengthFrames() * SAMPLES_PER_FRAME;
        int[] left = new int[count], right = new int[count];
        var chip = new PsgChip(SAMPLE_RATE);
        double frequency = 440 * StrictMath.pow(2, (blip.pitch() - 69) / 12.0);
        tone(chip, 0, frequency, 0);
        switch (blip.voice().tone()) {
            case CHIME -> tone(chip, 1, frequency * 2, 5);
            case HOLLOW -> tone(chip, 1, frequency * 1.5, 4);
            case BUZZ -> {
                tone(chip, 2, frequency * 0.5, 5);
                chip.write(0xe3); // periodic noise clocked by tone 2: a pitched mechanical edge
                chip.write(0xf8);
            }
            case OFF -> chip.write(0x9f);
            case SQUARE -> { }
        }
        chip.renderStereo(left, right);
        int peak = 1;
        for (int value : left) peak = Math.max(peak, Math.abs(value));
        short[] pcm = new short[count];
        // Normalise the short chip performance, then use a brief attack/release to avoid cut-boundary clicks.
        int attack = Math.min(192, count / 4), release = Math.min(720, count / 3);
        double gain = 9000.0 / peak * blip.voice().level() / 100.0;
        for (int i = 0; i < count; i++) {
            double envelope = Math.min(1, i / (double) attack) * Math.min(1, (count - 1 - i) / (double) release);
            pcm[i] = (short) StrictMath.round(left[i] * gain * envelope);
        }
        return new StreamedMusicPort.SfxPcm(SAMPLE_RATE, 1, pcm, 1);
    }

    private static void tone(PsgChip chip, int channel, double frequency, int attenuation) {
        int period = (int) StrictMath.round(PsgChip.TICK_RATE_HZ / (2 * frequency));
        period = Math.clamp(period, 1, 1023);
        chip.write(0x80 | channel << 5 | period & 15);
        chip.write(period >>> 4);
        chip.write(0x90 | channel << 5 | attenuation);
    }
}
