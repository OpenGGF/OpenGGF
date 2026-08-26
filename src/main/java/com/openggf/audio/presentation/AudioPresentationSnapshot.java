package com.openggf.audio.presentation;

import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;

import java.util.List;
import java.util.Objects;

public record AudioPresentationSnapshot(
        long nextVoiceId,
        List<PresentationVoiceSnapshot> voices,
        MusicSlotSnapshot activeMusic,
        List<MusicSlotSnapshot> overrideStack,
        PendingMusicSnapshot pendingMusic,
        Long standaloneSmpsVoiceId,
        Long rawPcmVoiceId,
        int fmMuteMask,
        int fmSoloMask,
        int psgMuteMask,
        int psgSoloMask,
        boolean activeMusicOverride,
        boolean sfxBlocked,
        boolean pendingRestore,
        boolean speedShoesEnabled,
        int speedMultiplier,
        boolean ringLeft,
        SmpsCoordFlagRuntimeState.Snapshot coordFlagRuntimeState) {

    private static final AudioPresentationSnapshot EMPTY =
            new AudioPresentationSnapshot(0, List.of(), null, List.of(),
                    null, null, null, 0, 0, 0, 0, false, false, false, false,
                    1, true,
                    new SmpsCoordFlagRuntimeState.Snapshot(0));

    public AudioPresentationSnapshot {
        voices = List.copyOf(Objects.requireNonNull(voices, "voices"));
        overrideStack =
                List.copyOf(Objects.requireNonNull(overrideStack, "overrideStack"));
        Objects.requireNonNull(coordFlagRuntimeState, "coordFlagRuntimeState");
    }

    public AudioPresentationSnapshot(
            long nextVoiceId,
            List<PresentationVoiceSnapshot> voices,
            MusicSlotSnapshot activeMusic,
            List<MusicSlotSnapshot> overrideStack,
            Long standaloneSmpsVoiceId,
            Long rawPcmVoiceId,
            int fmMuteMask,
            int fmSoloMask,
            int psgMuteMask,
            int psgSoloMask,
            boolean sfxBlocked,
            boolean pendingRestore,
            boolean speedShoesEnabled,
            int speedMultiplier,
            boolean ringLeft,
            SmpsCoordFlagRuntimeState.Snapshot coordFlagRuntimeState) {
        this(nextVoiceId, voices, activeMusic, overrideStack, null,
                standaloneSmpsVoiceId, rawPcmVoiceId, fmMuteMask, fmSoloMask,
                psgMuteMask, psgSoloMask, false, sfxBlocked, pendingRestore,
                speedShoesEnabled, speedMultiplier, ringLeft,
                coordFlagRuntimeState);
    }

    public static AudioPresentationSnapshot empty() {
        return EMPTY;
    }

    public record MusicSlotSnapshot(
            int musicId,
            AudioSourceDescriptor sourceDescriptor,
            long voiceId) {
        public MusicSlotSnapshot {
            Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
        }
    }

    /** A resolved ordinary-music request held in S3K's single input cell. */
    public record PendingMusicSnapshot(
            MusicSlotSnapshot music,
            GameAudioProfile.OrdinaryMusicSfxPolicy sfxPolicy,
            boolean readyAfterRestore) {
        public PendingMusicSnapshot {
            Objects.requireNonNull(music, "music");
            Objects.requireNonNull(sfxPolicy, "sfxPolicy");
        }
    }
}
