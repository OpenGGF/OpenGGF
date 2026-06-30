package com.openggf.audio.rewind;

import com.openggf.audio.AudioManager;
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
        int continuousSfxId,
        boolean continuousSfxFlag,
        int contSfxLoopCnt,
        List<SequencerEntry> sequencers,
        int[] fmLockSequencerIds,
        int[] psgLockSequencerIds,
        VirtualSynthesizer.Snapshot synthSnapshot) {

    public SmpsDriverSnapshot {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(readMode, "readMode");
        sequencers = List.copyOf(sequencers);
        fmLockSequencerIds = Arrays.copyOf(fmLockSequencerIds, fmLockSequencerIds.length);
        psgLockSequencerIds = Arrays.copyOf(psgLockSequencerIds, psgLockSequencerIds.length);
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
                continuousSfxId,
                continuousSfxFlag,
                contSfxLoopCnt,
                sequencers,
                fmLockSequencerIds,
                psgLockSequencerIds,
                null);
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

        AudioManager resolveAudioManager(SequencerEntry entry);

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
            public AudioManager resolveAudioManager(SequencerEntry entry) {
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
            SmpsSourceDescriptor fallbackVoiceSource,
            AbstractSmpsData smpsData,
            DacData dacData,
            AudioManager audioManager,
            SmpsSequencerConfig config,
            SmpsSequencerSnapshot snapshot) {

        public SequencerEntry {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(smpsData, "smpsData");
            Objects.requireNonNull(audioManager, "audioManager");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }
}
