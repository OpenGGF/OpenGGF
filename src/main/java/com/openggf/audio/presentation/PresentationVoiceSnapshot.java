package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioSourceDescriptor;
public sealed interface PresentationVoiceSnapshot
        permits PresentationVoiceSnapshot.Sample, PresentationVoiceSnapshot.Streamed {

    record Sample(long voiceId, int priority, String assetId, Integer musicId,
                  AudioSourceDescriptor sourceDescriptor, long sourcePositionQ32,
                  long sourceStepQ32, int gainQ16, boolean looping,
                  boolean stopped) implements PresentationVoiceSnapshot {
    }
    record Streamed(long voiceId, int priority,
                    AudioSourceDescriptor sourceDescriptor,
                    com.openggf.audio.StreamedMusicPort.State playback,
                    boolean stopped) implements PresentationVoiceSnapshot {
        public Streamed {
            java.util.Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
            java.util.Objects.requireNonNull(playback, "playback");
        }
    }

}
