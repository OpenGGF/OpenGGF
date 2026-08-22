package com.openggf.audio.rewind;

import com.openggf.audio.MusicRestoreSink;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.synth.VirtualSynthesizer;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record SmpsDriverSnapshot(
        SmpsSequencer.Region region,
        SmpsDriver.ReadMode readMode,
        int palFullUpdateCounter,
        int sfxPriorityLatch,
        int spindashRevPlayingCounter,
        int spindashRevFrequencyIndex,
        int continuousSfxId,
        boolean continuousSfxFlag,
        int contSfxLoopCnt,
        List<SequencerEntry> sequencers,
        int[] fmLockSequencerIds,
        int[] psgLockSequencerIds,
        VirtualSynthesizer.Snapshot synthSnapshot,
        long ymServiceCursor,
        long nextYmServiceOrdinal,
        long nextYmWriteOrdinal,
        long driverGeneration) {

    public SmpsDriverSnapshot {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(readMode, "readMode");
        if (palFullUpdateCounter < 0 || palFullUpdateCounter > 6) {
            throw new IllegalArgumentException(
                    "PAL full-update counter must be in [0, 6]");
        }
        if (sfxPriorityLatch < 0 || sfxPriorityLatch > 0xFF) {
            throw new IllegalArgumentException(
                    "SFX priority latch must fit one unsigned byte");
        }
        if (spindashRevPlayingCounter < 0
                || spindashRevPlayingCounter > 0x3C) {
            throw new IllegalArgumentException(
                    "spindash-rev timeout must fit [0, 0x3C]");
        }
        if (spindashRevFrequencyIndex < 0
                || spindashRevFrequencyIndex > 0x0B) {
            throw new IllegalArgumentException(
                    "spindash-rev index must fit [0, 0x0B]");
        }
        sequencers = List.copyOf(sequencers);
        fmLockSequencerIds = Arrays.copyOf(fmLockSequencerIds, fmLockSequencerIds.length);
        psgLockSequencerIds = Arrays.copyOf(psgLockSequencerIds, psgLockSequencerIds.length);
        if (ymServiceCursor < 0 || nextYmServiceOrdinal < 0
                || nextYmWriteOrdinal < 0 || driverGeneration < 0) {
            throw new IllegalArgumentException(
                    "YM driver timeline state cannot be negative");
        }
        if (synthSnapshot != null) {
            if (driverGeneration != synthSnapshot.ymTimelineGeneration()) {
                throw new IllegalArgumentException(
                        "driver and synth YM generations do not match");
            }
            if (nextYmWriteOrdinal
                    != synthSnapshot.ymWriteTimeline().nextOrdinal()) {
                throw new IllegalArgumentException(
                        "driver and synth YM write ordinals do not match");
            }
            long lastPendingDue = synthSnapshot.ymWriteTimeline().pending()
                    .stream()
                    .mapToLong(entry -> entry.dueMasterCycle())
                    .max().orElse(0L);
            if (ymServiceCursor < lastPendingDue) {
                throw new IllegalArgumentException(
                        "YM service cursor precedes a committed write");
            }
        }
    }

    public SmpsDriverSnapshot(
            SmpsSequencer.Region region,
            SmpsDriver.ReadMode readMode,
            int palFullUpdateCounter,
            int sfxPriorityLatch,
            int spindashRevPlayingCounter,
            int spindashRevFrequencyIndex,
            int continuousSfxId,
            boolean continuousSfxFlag,
            int contSfxLoopCnt,
            List<SequencerEntry> sequencers,
            int[] fmLockSequencerIds,
            int[] psgLockSequencerIds,
            VirtualSynthesizer.Snapshot synthSnapshot) {
        this(region, readMode, palFullUpdateCounter, sfxPriorityLatch,
                spindashRevPlayingCounter, spindashRevFrequencyIndex,
                continuousSfxId, continuousSfxFlag, contSfxLoopCnt,
                sequencers, fmLockSequencerIds, psgLockSequencerIds,
                synthSnapshot, inferredCursor(synthSnapshot), 0,
                inferredWriteOrdinal(synthSnapshot),
                inferredGeneration(synthSnapshot));
    }

    public SmpsDriverSnapshot(
            SmpsSequencer.Region region,
            SmpsDriver.ReadMode readMode,
            int continuousSfxId,
            boolean continuousSfxFlag,
            int contSfxLoopCnt,
            List<SequencerEntry> sequencers,
            int[] fmLockSequencerIds,
            int[] psgLockSequencerIds) {
        this(
                region,
                readMode,
                5,
                0,
                0,
                0,
                continuousSfxId,
                continuousSfxFlag,
                contSfxLoopCnt,
                sequencers,
                fmLockSequencerIds,
                psgLockSequencerIds,
                null,
                0,
                0,
                0,
                1);
    }

    public SmpsDriverSnapshot(
            SmpsSequencer.Region region,
            SmpsDriver.ReadMode readMode,
            int continuousSfxId,
            boolean continuousSfxFlag,
            int contSfxLoopCnt,
            List<SequencerEntry> sequencers,
            int[] fmLockSequencerIds,
            int[] psgLockSequencerIds,
            VirtualSynthesizer.Snapshot synthSnapshot) {
        this(
                region,
                readMode,
                5,
                0,
                0,
                0,
                continuousSfxId,
                continuousSfxFlag,
                contSfxLoopCnt,
                sequencers,
                fmLockSequencerIds,
                psgLockSequencerIds,
                synthSnapshot,
                inferredCursor(synthSnapshot),
                0,
                inferredWriteOrdinal(synthSnapshot),
                inferredGeneration(synthSnapshot));
    }

    private static long inferredCursor(
            VirtualSynthesizer.Snapshot synthSnapshot) {
        if (synthSnapshot == null) {
            return 0;
        }
        return Math.max(synthSnapshot.renderedYmMasterCycle(),
                synthSnapshot.ymWriteTimeline().pending().stream()
                        .mapToLong(entry -> entry.dueMasterCycle())
                        .max().orElse(0L));
    }

    private static long inferredWriteOrdinal(
            VirtualSynthesizer.Snapshot synthSnapshot) {
        return synthSnapshot == null ? 0
                : synthSnapshot.ymWriteTimeline().nextOrdinal();
    }

    private static long inferredGeneration(
            VirtualSynthesizer.Snapshot synthSnapshot) {
        return synthSnapshot == null ? 1
                : synthSnapshot.ymTimelineGeneration();
    }

    @Override
    public int[] fmLockSequencerIds() {
        return Arrays.copyOf(fmLockSequencerIds, fmLockSequencerIds.length);
    }

    @Override
    public int[] psgLockSequencerIds() {
        return Arrays.copyOf(psgLockSequencerIds, psgLockSequencerIds.length);
    }

    public interface DependencyResolver {
        AbstractSmpsData resolveSmpsData(SequencerEntry entry);

        DacData resolveDacData(SequencerEntry entry);

        MusicRestoreSink resolveAudioManager(SequencerEntry entry);

        SmpsSequencerConfig resolveConfig(SequencerEntry entry);
    }

    public static DependencyResolver liveReferences() {
        return new DependencyResolver() {
            @Override
            public AbstractSmpsData resolveSmpsData(SequencerEntry entry) {
                return entry.smpsData();
            }

            @Override
            public DacData resolveDacData(SequencerEntry entry) {
                return entry.dacData();
            }

            @Override
            public MusicRestoreSink resolveAudioManager(SequencerEntry entry) {
                return entry.audioManager();
            }

            @Override
            public SmpsSequencerConfig resolveConfig(SequencerEntry entry) {
                return entry.config();
            }
        };
    }

    public record SequencerEntry(
            boolean sfx,
            SmpsSourceDescriptor source,
            SmpsSequencer.SourceDescriptorTrust sourceDescriptorTrust,
            SmpsSourceDescriptor fallbackVoiceSource,
            AbstractSmpsData smpsData,
            DacData dacData,
            MusicRestoreSink audioManager,
            SmpsSequencerConfig config,
            SmpsSequencerSnapshot snapshot) {

        public SequencerEntry {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(
                    sourceDescriptorTrust, "sourceDescriptorTrust");
            Objects.requireNonNull(smpsData, "smpsData");
            Objects.requireNonNull(audioManager, "audioManager");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(snapshot, "snapshot");
        }

        public SequencerEntry(
                boolean sfx,
                SmpsSourceDescriptor source,
                SmpsSourceDescriptor fallbackVoiceSource,
                AbstractSmpsData smpsData,
                DacData dacData,
                MusicRestoreSink audioManager,
                SmpsSequencerConfig config,
                SmpsSequencerSnapshot snapshot) {
            this(sfx, source,
                    SmpsSequencer.SourceDescriptorTrust.LEGACY_RECOMPUTE,
                    fallbackVoiceSource, smpsData, dacData, audioManager,
                    config, snapshot);
        }
    }
}
