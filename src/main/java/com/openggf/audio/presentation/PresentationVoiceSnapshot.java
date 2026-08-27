package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;

public sealed interface PresentationVoiceSnapshot
        permits PresentationVoiceSnapshot.Smps, PresentationVoiceSnapshot.Sample {

    record Smps(long voiceId, int priority, Integer musicId,
                AudioSourceDescriptor sourceDescriptor, int maxStereoFrames,
                SmpsDriverSnapshot driver) implements PresentationVoiceSnapshot {
    }

    record Sample(long voiceId, int priority, String assetId, Integer musicId,
                  AudioSourceDescriptor sourceDescriptor, long sourcePositionQ32,
                  long sourceStepQ32, int gainQ16, boolean looping,
                  boolean stopped) implements PresentationVoiceSnapshot {
    }
}
