package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;

public sealed interface PresentationVoiceSnapshot
        permits PresentationVoiceSnapshot.Smps, PresentationVoiceSnapshot.Sample,
                PresentationVoiceSnapshot.Streamed {

    record Smps(long voiceId, int priority, Integer musicId,
                AudioSourceDescriptor sourceDescriptor, int maxStereoFrames,
                SmpsDriverSnapshot driver) implements PresentationVoiceSnapshot {
    }

    /**
     * A creator-supplied streamed music override. Its logical playback state is
     * owned by the mod-side player, so it is captured verbatim rather than being
     * re-expressed as a source cursor: loop points, fade progress, pause mask
     * and tempo rate are all part of that state.
     */
    record Streamed(long voiceId, int priority,
                    AudioSourceDescriptor sourceDescriptor,
                    com.openggf.audio.StreamedMusicPort.State playback,
                    boolean stopped) implements PresentationVoiceSnapshot {
        public Streamed {
            java.util.Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
            java.util.Objects.requireNonNull(playback, "playback");
        }
    }

    record Sample(long voiceId, int priority, String assetId, Integer musicId,
                  AudioSourceDescriptor sourceDescriptor, long sourcePositionQ32,
                  long sourceStepQ32, int gainQ16, boolean looping,
                  boolean stopped) implements PresentationVoiceSnapshot {
    }
}
