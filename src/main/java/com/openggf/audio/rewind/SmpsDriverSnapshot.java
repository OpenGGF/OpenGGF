package com.openggf.audio.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;

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
        int[] psgLockSequencerIds) {

    public SmpsDriverSnapshot {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(readMode, "readMode");
        sequencers = List.copyOf(sequencers);
        fmLockSequencerIds = Arrays.copyOf(fmLockSequencerIds, fmLockSequencerIds.length);
        psgLockSequencerIds = Arrays.copyOf(psgLockSequencerIds, psgLockSequencerIds.length);
    }

    @Override
    public int[] fmLockSequencerIds() {
        return Arrays.copyOf(fmLockSequencerIds, fmLockSequencerIds.length);
    }

    @Override
    public int[] psgLockSequencerIds() {
        return Arrays.copyOf(psgLockSequencerIds, psgLockSequencerIds.length);
    }

    public record SequencerEntry(
            boolean sfx,
            AbstractSmpsData smpsData,
            DacData dacData,
            AudioManager audioManager,
            SmpsSequencerConfig config,
            SmpsSequencerSnapshot snapshot) {

        public SequencerEntry {
            Objects.requireNonNull(smpsData, "smpsData");
            Objects.requireNonNull(audioManager, "audioManager");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }
}
