package com.openggf.tools.audio.playback;

import com.openggf.audio.AudioRequestObserver;

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

    record Ym2612KeyOn(int channel, int operator, int attenuation)
            implements AudioPlaybackTraceEvent {
        public Ym2612KeyOn {
            if (channel < 0 || channel > 5) {
                throw new IllegalArgumentException(
                        "YM2612 channel must be between 0 and 5");
            }
            if (operator < 0 || operator > 3) {
                throw new IllegalArgumentException(
                        "YM2612 operator must be between 0 and 3");
            }
            if (attenuation < 0 || attenuation > 1023) {
                throw new IllegalArgumentException(
                        "YM2612 attenuation must be between 0 and 1023");
            }
        }
    }

    record AudioRequest(
            AudioRequestObserver.RequestClass requestClass,
            int rawSoundId) implements AudioPlaybackTraceEvent {
        public AudioRequest {
            Objects.requireNonNull(requestClass, "requestClass");
            requireByte(rawSoundId, "raw sound id");
        }
    }

    private static void requireByte(int value, String label) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(label + " must be an unsigned byte");
        }
    }
}
