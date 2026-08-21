package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.synth.VirtualSynthesizer;

public sealed interface PresentationVoiceSnapshot
        permits PresentationVoiceSnapshot.Smps, PresentationVoiceSnapshot.Sample {

    record Smps(long voiceId, int priority, Integer musicId,
                AudioSourceDescriptor sourceDescriptor, int maxStereoFrames,
                SmpsDriverSnapshot driver) implements PresentationVoiceSnapshot {
    }

    enum SampleRenderMode {
        HOST_LINEAR,
        YM2612_DAC
    }

    record Sample(long voiceId, int priority, String assetId, Integer musicId,
                  AudioSourceDescriptor sourceDescriptor, long sourcePositionQ32,
                  long sourceStepQ32, int gainQ16, boolean looping,
                  boolean stopped, SampleRenderMode renderMode,
                  VirtualSynthesizer.Snapshot synthSnapshot,
                  int lastDacSourceFrame) implements PresentationVoiceSnapshot {
        public Sample(long voiceId, int priority, String assetId,
                      Integer musicId, AudioSourceDescriptor sourceDescriptor,
                      long sourcePositionQ32, long sourceStepQ32, int gainQ16,
                      boolean looping, boolean stopped) {
            this(voiceId, priority, assetId, musicId, sourceDescriptor,
                    sourcePositionQ32, sourceStepQ32, gainQ16, looping,
                    stopped, SampleRenderMode.HOST_LINEAR, null, -1);
        }
    }
}
