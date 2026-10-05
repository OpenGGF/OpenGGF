package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.session.SmpsDriverSessionSnapshot;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;

import java.util.List;
import java.util.Objects;

public record AudioPresentationSnapshot(
        long nextVoiceId,
        List<PresentationVoiceSnapshot> voices,
        MusicSlotSnapshot activeMusic,
        List<MusicSlotSnapshot> overrideStack,
        Long rawPcmVoiceId,
        int fmMuteMask,
        int fmSoloMask,
        int psgMuteMask,
        int psgSoloMask,
        boolean sfxBlocked,
        boolean sfxBlockHeldThroughFadeIn,
        boolean pendingRestore,
        boolean speedShoesEnabled,
        int speedMultiplier,
        boolean ringLeft,
        SmpsCoordFlagRuntimeState.Snapshot coordFlagRuntimeState,
        SmpsDriverSessionSnapshot smpsSession,
        SmpsDriverSnapshot smpsLogical,
        ForwardTiming forwardTiming) {

    /** Source-frame phase, independent of the wall-clock output packet clock. */
    public record ForwardTiming(double phase, int frameSamples, int sampleRemainder) {
        public static final ForwardTiming INITIAL = new ForwardTiming(0, 0, 0);
        public ForwardTiming {
            if (!Double.isFinite(phase) || phase < 0 || phase >= 1
                    || frameSamples < 0 || sampleRemainder < 0) {
                throw new IllegalArgumentException("invalid forward audio timing");
            }
        }
    }

    /** Compatibility construction for snapshots at a fresh source-clock boundary. */
    public AudioPresentationSnapshot(long nextVoiceId, List<PresentationVoiceSnapshot> voices,
            MusicSlotSnapshot activeMusic, List<MusicSlotSnapshot> overrideStack, Long rawPcmVoiceId,
            int fmMuteMask, int fmSoloMask, int psgMuteMask, int psgSoloMask,
            boolean sfxBlocked, boolean sfxBlockHeldThroughFadeIn, boolean pendingRestore,
            boolean speedShoesEnabled, int speedMultiplier, boolean ringLeft,
            SmpsCoordFlagRuntimeState.Snapshot coordFlagRuntimeState,
            SmpsDriverSessionSnapshot smpsSession, SmpsDriverSnapshot smpsLogical) {
        this(nextVoiceId, voices, activeMusic, overrideStack, rawPcmVoiceId,
                fmMuteMask, fmSoloMask, psgMuteMask, psgSoloMask, sfxBlocked,
                sfxBlockHeldThroughFadeIn, pendingRestore, speedShoesEnabled,
                speedMultiplier, ringLeft, coordFlagRuntimeState, smpsSession,
                smpsLogical, ForwardTiming.INITIAL);
    }

    private static final AudioPresentationSnapshot EMPTY =
            new AudioPresentationSnapshot(0, List.of(), null, List.of(),
                    null, 0, 0, 0, 0, false, false, false, false, 1, true,
                    new SmpsCoordFlagRuntimeState.Snapshot(0), null, null);

    public AudioPresentationSnapshot {
        voices = List.copyOf(Objects.requireNonNull(voices, "voices"));
        overrideStack =
                List.copyOf(Objects.requireNonNull(overrideStack, "overrideStack"));
        Objects.requireNonNull(coordFlagRuntimeState, "coordFlagRuntimeState");
        Objects.requireNonNull(forwardTiming, "forwardTiming");
        if ((smpsSession == null) != (smpsLogical == null)) {
            throw new IllegalArgumentException(
                    "session and logical SMPS snapshots must be paired");
        }
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
}
