package com.openggf.tools.audio.parity.s3k;

import com.openggf.tools.audio.parity.AudioParityChipWrite;

import java.util.List;
import java.util.Objects;

/** One S3K oracle frame: globals, sixteen track slots, and Z80-owned chip writes. */
public record S3kAudioTick(
        int ordinal,
        boolean lag,
        List<Integer> mailbox,
        GlobalState global,
        List<S3kAudioTrackState> tracks,
        List<AudioParityChipWrite> writes) {

    public S3kAudioTick {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be non-negative");
        }
        Objects.requireNonNull(global, "global");
        mailbox = List.copyOf(mailbox);
        tracks = List.copyOf(tracks);
        writes = List.copyOf(writes);
        if (mailbox.size() != 3) {
            throw new IllegalArgumentException("mailbox must carry zMusicNumber/zSFXNumber0/zSFXNumber1");
        }
        if (tracks.size() != S3kAudioParitySchema.ROLES.size()) {
            throw new IllegalArgumentException("tick must contain all sixteen fixed roles");
        }
        for (int index = 0; index < tracks.size(); index++) {
            if (!S3kAudioParitySchema.ROLES.get(index).equals(tracks.get(index).role())) {
                throw new IllegalArgumentException("fixed role absent or out of order at index " + index);
            }
        }
    }

    /**
     * Driver globals (zDataStart variables, routine map §1.3). Nullable values
     * are reference-only observations the engine does not model.
     */
    public record GlobalState(
            Integer currentTempo,       // 1C24 zCurrentTempo
            Integer tempoAccumulator,   // 1C13 zTempoAccumulator
            Integer tempoSpeedup,       // 1C08 zTempoSpeedup
            Integer speedupTimeout,     // 1C2F zSpeedupTimeout
            Integer dacIndex,           // 1C30 zDACIndex
            Integer fadeOutTimeout,     // 1C0D
            Integer fadeDelay,          // 1C0E
            Integer fadeDelayTimeout,   // 1C0F
            Integer fadeInTimeout,      // 1C29
            Integer pauseFlag,          // 1C10
            List<Integer> soundQueue,   // 1C05-07
            Integer nextSound,          // 1C09
            Integer palDoubleUpdateCounter) { // 1C04, boot-completion marker
        public GlobalState {
            soundQueue = soundQueue == null ? null : List.copyOf(soundQueue);
        }
    }
}
