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

    record MusicVoiceEntry(int musicId, AudioSourceDescriptor sourceDescriptor,
                           PresentationVoice voice) {
        public MusicVoiceEntry {
            Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
            Objects.requireNonNull(voice, "voice");
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

    record StartSampleSfx(SampleBackedVoice voice) implements AudioPresentationCommand {
        public StartSampleSfx {
            Objects.requireNonNull(voice, "voice");
        }
    }

    record ReplaceRawPcm(SampleBackedVoice voice) implements AudioPresentationCommand {
        public ReplaceRawPcm {
            Objects.requireNonNull(voice, "voice");
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
}
