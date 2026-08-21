package com.openggf.audio.synth;

/**
 * Diagnostic sink for resolved writes entering the emulated sound chips.
 * Implementations observe immutable byte values and cannot alter a write.
 */
public interface ChipWriteObserver {
    ChipWriteObserver NONE = new ChipWriteObserver() {
        @Override
        public void onYm2612Write(int port, int register, int value) {
        }

        @Override
        public void onPsgWrite(int value) {
        }
    };

    void onYm2612Write(int port, int register, int value);

    void onPsgWrite(int value);

    /**
     * Bit mask of YM2612 channels whose rendered internal samples are needed.
     * The default keeps the sample-rate diagnostic path completely disabled.
     */
    default int ym2612ChannelSampleMask() {
        return 0;
    }

    /** Receives one pre-pan internal YM2612 sample for a requested channel. */
    default void onYm2612ChannelSample(int channel, int output) {
    }

    /** Receives one operator's attenuation immediately before YM key-on. */
    default void onYm2612KeyOn(
            int channel, int operator, int attenuation) {
    }
}
