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
}
