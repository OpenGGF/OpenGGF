package com.openggf.audio.presentation;

import com.openggf.audio.ChannelType;
import com.openggf.audio.rewind.AudioSourceDescriptor;

import java.util.Objects;

/**
 * Immutable, fully resolved mutations applied at an audio frame boundary.
 */
public sealed interface AudioPresentationCommand
        permits AudioPresentationCommand.ReplaceMusic,
        AudioPresentationCommand.PushMusicOverride,
        AudioPresentationCommand.RestoreMusicOverride,
        AudioPresentationCommand.EndMusicOverride,
        AudioPresentationCommand.AddSmpsSfx,
        AudioPresentationCommand.StartSampleSfx,
        AudioPresentationCommand.ReplaceRawPcm,
        AudioPresentationCommand.StopRawPcm,
        AudioPresentationCommand.StopMusic,
        AudioPresentationCommand.StopAllSfx,
        AudioPresentationCommand.FadeMusic,
        AudioPresentationCommand.SetVoiceGain,
        AudioPresentationCommand.SetVoicePitch,
        AudioPresentationCommand.SetSpeedShoes,
        AudioPresentationCommand.SetSpeedMultiplier,
        AudioPresentationCommand.ChangeMusicTempo,
        AudioPresentationCommand.ResetRingAlternation,
        AudioPresentationCommand.ToggleMute,
        AudioPresentationCommand.ToggleSolo,
        AudioPresentationCommand.RewindBoundary,
        AudioPresentationCommand.HardReset {

    default boolean structural() {
        return !(this instanceof StartSampleSfx
                || this instanceof SetVoiceGain
                || this instanceof SetVoicePitch
                || this instanceof SetSpeedShoes
                || this instanceof SetSpeedMultiplier);
    }

    default boolean droppableSampleStart() {
        return this instanceof StartSampleSfx;
    }

    default Object coalescingKey() {
        if (this instanceof SetVoiceGain command) {
            return command.voiceId();
        }
        if (this instanceof SetVoicePitch command) {
            return command.voiceId();
        }
        if (this instanceof SetSpeedShoes) {
            return SetSpeedShoes.class;
        }
        if (this instanceof SetSpeedMultiplier) {
            return SetSpeedMultiplier.class;
        }
        return null;
    }

    sealed interface VoiceDescriptor
            permits SampleVoiceDescriptor, SmpsVoiceDescriptor, StreamedVoiceDescriptor {
        long voiceId();

        int priority();
    }

    /** A creator-supplied streamed music override awaiting registry apply. */
    record StreamedVoiceDescriptor(PresentationVoiceSnapshot.Streamed snapshot)
            implements VoiceDescriptor {
        public StreamedVoiceDescriptor {
            Objects.requireNonNull(snapshot, "snapshot");
        }

        @Override
        public long voiceId() {
            return snapshot.voiceId();
        }

        @Override
        public int priority() {
            return snapshot.priority();
        }
    }

    record SampleVoiceDescriptor(PresentationVoiceSnapshot.Sample snapshot)
            implements VoiceDescriptor {
        public SampleVoiceDescriptor {
            snapshot = copySample(Objects.requireNonNull(snapshot, "snapshot"));
        }

        public static SampleVoiceDescriptor fromVoice(SampleBackedVoice voice) {
            Objects.requireNonNull(voice, "voice");
            return new SampleVoiceDescriptor(
                    (PresentationVoiceSnapshot.Sample) voice.snapshot());
        }

        @Override
        public long voiceId() {
            return snapshot.voiceId();
        }

        @Override
        public int priority() {
            return snapshot.priority();
        }
    }

    record SmpsVoiceDescriptor(
            long voiceId,
            int priority,
            Integer musicId,
            AudioSourceDescriptor sourceDescriptor,
            int maxStereoFrames) implements VoiceDescriptor {
        public SmpsVoiceDescriptor {
            Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
            if (maxStereoFrames < 0) {
                throw new IllegalArgumentException(
                        "maxStereoFrames must be non-negative");
            }
        }
    }

    record MusicVoiceEntry(int musicId, AudioSourceDescriptor sourceDescriptor,
                           VoiceDescriptor voiceDescriptor) {
        public MusicVoiceEntry {
            Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
            Objects.requireNonNull(voiceDescriptor, "voiceDescriptor");
        }

        public static MusicVoiceEntry fromVoice(
                int musicId,
                AudioSourceDescriptor sourceDescriptor,
                PresentationVoice voice) {
            Objects.requireNonNull(voice, "voice");
            VoiceDescriptor descriptor;
            if (voice instanceof SampleBackedVoice sample) {
                PresentationVoiceSnapshot.Sample snapshot =
                        (PresentationVoiceSnapshot.Sample) sample.snapshot();
                descriptor = new SampleVoiceDescriptor(
                        new PresentationVoiceSnapshot.Sample(
                                snapshot.voiceId(), snapshot.priority(),
                                snapshot.assetId(), musicId, sourceDescriptor,
                                snapshot.sourcePositionQ32(),
                                snapshot.sourceStepQ32(), snapshot.gainQ16(),
                                snapshot.looping(), snapshot.stopped()));
            } else if (voice instanceof StreamedMusicVoice streamed) {
                descriptor = new StreamedVoiceDescriptor(
                        (PresentationVoiceSnapshot.Streamed) streamed.snapshot());
            } else if (voice instanceof SmpsCompositeVoice composite) {
                PresentationVoiceSnapshot.Smps snapshot =
                        (PresentationVoiceSnapshot.Smps) composite.snapshot();
                descriptor = new SmpsVoiceDescriptor(
                        snapshot.voiceId(), snapshot.priority(), musicId,
                        sourceDescriptor, snapshot.maxStereoFrames());
            } else {
                throw new IllegalArgumentException(
                        "unsupported music presentation voice "
                                + voice.getClass().getName());
            }
            return new MusicVoiceEntry(
                    musicId, Objects.requireNonNull(sourceDescriptor,
                    "sourceDescriptor"), descriptor);
        }
    }

    record ReplaceMusic(MusicVoiceEntry music) implements AudioPresentationCommand {
        public ReplaceMusic {
            Objects.requireNonNull(music, "music");
        }
    }

    record PushMusicOverride(MusicVoiceEntry music) implements AudioPresentationCommand {
        public PushMusicOverride {
            Objects.requireNonNull(music, "music");
        }
    }

    record RestoreMusicOverride() implements AudioPresentationCommand {
    }

    record EndMusicOverride(int musicId) implements AudioPresentationCommand {
    }

    record AddSmpsSfx(ResolvedSmpsSfxSource source) implements AudioPresentationCommand {
        public AddSmpsSfx {
            Objects.requireNonNull(source, "source");
        }
    }

    record StartSampleSfx(SampleVoiceDescriptor voice)
            implements AudioPresentationCommand {
        public StartSampleSfx {
            Objects.requireNonNull(voice, "voice");
        }

        public static StartSampleSfx fromVoice(SampleBackedVoice voice) {
            return new StartSampleSfx(SampleVoiceDescriptor.fromVoice(voice));
        }
    }

    record ReplaceRawPcm(SampleVoiceDescriptor voice)
            implements AudioPresentationCommand {
        public ReplaceRawPcm {
            Objects.requireNonNull(voice, "voice");
        }

        public static ReplaceRawPcm fromVoice(SampleBackedVoice voice) {
            return new ReplaceRawPcm(SampleVoiceDescriptor.fromVoice(voice));
        }
    }

    record StopRawPcm() implements AudioPresentationCommand {
    }

    record StopMusic() implements AudioPresentationCommand {
    }

    record StopAllSfx() implements AudioPresentationCommand {
    }

    record FadeMusic(int steps, int delay) implements AudioPresentationCommand {
    }

    record SetVoiceGain(long voiceId, int gainQ16) implements AudioPresentationCommand {
    }

    record SetVoicePitch(long voiceId, long sourceStepQ32) implements AudioPresentationCommand {
    }

    record SetSpeedShoes(boolean enabled) implements AudioPresentationCommand {
    }

    record SetSpeedMultiplier(int multiplier) implements AudioPresentationCommand {
    }

    record ChangeMusicTempo(int dividingTiming) implements AudioPresentationCommand {
    }

    record ResetRingAlternation(boolean ringLeft) implements AudioPresentationCommand {
    }

    record ToggleMute(ChannelType type, int channel) implements AudioPresentationCommand {
        public ToggleMute {
            Objects.requireNonNull(type, "type");
        }
    }

    record ToggleSolo(ChannelType type, int channel) implements AudioPresentationCommand {
        public ToggleSolo {
            Objects.requireNonNull(type, "type");
        }
    }

    record RewindBoundary() implements AudioPresentationCommand {
    }

    record HardReset() implements AudioPresentationCommand {
    }

    private static PresentationVoiceSnapshot.Sample copySample(
            PresentationVoiceSnapshot.Sample sample) {
        return new PresentationVoiceSnapshot.Sample(
                sample.voiceId(), sample.priority(), sample.assetId(),
                sample.musicId(), sample.sourceDescriptor(),
                sample.sourcePositionQ32(), sample.sourceStepQ32(),
                sample.gainQ16(), sample.looping(), sample.stopped());
    }
}
