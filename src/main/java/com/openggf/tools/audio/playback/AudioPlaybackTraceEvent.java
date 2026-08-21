package com.openggf.tools.audio.playback;

import java.util.Objects;

/** One immutable, ordered observation from a bounded playback scenario. */
public sealed interface AudioPlaybackTraceEvent {

    record Marker(String name) implements AudioPlaybackTraceEvent {
        public Marker {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("marker name must not be blank");
            }
        }
    }

    record Ym2612Write(int port, int register, int value)
            implements AudioPlaybackTraceEvent {
        public Ym2612Write {
            if (port < 0 || port > 1) {
                throw new IllegalArgumentException("YM2612 port must be 0 or 1");
            }
            requireByte(register, "YM2612 register");
            requireByte(value, "YM2612 value");
        }
    }

    record PsgWrite(int value) implements AudioPlaybackTraceEvent {
        public PsgWrite {
            requireByte(value, "PSG value");
        }
    }

    private static void requireByte(int value, String label) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(label + " must be an unsigned byte");
        }
    }
}
