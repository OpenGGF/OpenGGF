package com.openggf.tools.audio.parity;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.session.LegacyCompatibilitySmpsPhysicalPolicy;
import com.openggf.audio.session.OwnedSmpsAudioStream;
import com.openggf.audio.session.SmpsPhysicalDevice;
import com.openggf.audio.rewind.SmpsSequencerSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.data.Rom;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import com.openggf.game.sonic1.audio.smps.Sonic1SmpsLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * ROM-backed test host that replays the S1 sound-test SFX reference's request
 * sequence through the real {@link SmpsDriver}: GHZ music exactly as
 * {@link S1OpenGgfAudioCapture}, plus one SFX sequencer admission per recorded
 * dispatch, applied on the recorded invocation ordinal before that tick's
 * driver service — the ROM dispatches a queued sound (PlaySoundID) before the
 * track walk of the same UpdateMusic invocation.
 */
public final class S1OpenGgfSfxAudioCapture {
    private static final double SAMPLE_RATE = 44_100.0;

    private S1OpenGgfSfxAudioCapture() {
    }

    public record CaptureResult(int recordCount, int dispatchCount) {
    }

    public static CaptureResult capture(Path reference, Path romPath, Path output) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(romPath, "romPath");
        Objects.requireNonNull(output, "output");
        AudioParityMetadata referenceMetadata = S1OpenGgfAudioCapture.readReferenceMetadata(reference);
        if (!AudioParitySchema.SFX_REFERENCE_CAPTURE.equals(referenceMetadata.capture())) {
            throw new IllegalArgumentException("reference stream is not a BizHawk S1 SFX capture");
        }

        Map<Integer, List<Integer>> dispatchesByOrdinal = new HashMap<>();
        AudioParityJsonl.read(reference, tick -> {
            List<Integer> dispatches = tick.dispatches();
            if (dispatches != null && !dispatches.isEmpty()) {
                dispatchesByOrdinal.put(tick.ordinal(), dispatches);
            }
        });

        S1OpenGgfAudioCapture.verifyRomIdentity(romPath);
        try (Rom rom = S1OpenGgfAudioCapture.openRom(romPath)) {
            Sonic1SmpsLoader loader = new Sonic1SmpsLoader(rom);
            AbstractSmpsData song = Objects.requireNonNull(loader.loadMusic(Sonic1Music.GHZ.id),
                    "S1 GHZ music is absent from the verified ROM");
            DacData dacData = Objects.requireNonNull(loader.loadDacData(),
                    "S1 DAC data is absent from the verified ROM");
            S1OpenGgfAudioCapture.SongContract contract = S1OpenGgfAudioCapture.inspectGhz(rom, song);

            AudioParityMetadata outputMetadata = AudioParityMetadata.openGgfSfx(
                    referenceMetadata.terminalRecordCount(), referenceMetadata.romSha1(),
                    referenceMetadata.romCrc32());
            try (CaptureIterator ticks = new CaptureIterator(
                    loader, song, dacData, contract,
                    referenceMetadata.terminalRecordCount(),
                    dispatchesByOrdinal)) {
                AudioParityJsonl.writeNew(output, outputMetadata, ticks);
                return new CaptureResult(
                        referenceMetadata.terminalRecordCount(),
                        ticks.dispatchCount);
            }
        }
    }

    private static final class CaptureIterator
            implements Iterator<AudioParityTick>, ChipWriteObserver,
            AutoCloseable {
        private final OwnedSmpsAudioStream stream;
        private final Sonic1SmpsLoader loader;
        private final AbstractSmpsData song;
        private final DacData dacData;
        private final SmpsDriver driver;
        private final SmpsSequencer musicSequencer;
        private final S1OpenGgfAudioCapture.SongContract contract;
        private final Sonic1AudioProfile profile = new Sonic1AudioProfile();
        private final int terminalCount;
        private final Map<Integer, List<Integer>> dispatchesByOrdinal;
        private final List<AudioParityChipWrite> writes = new ArrayList<>();
        private int ordinal;
        private int dispatchCount;
        private int ringSpeaker;

        private CaptureIterator(Sonic1SmpsLoader loader, AbstractSmpsData song, DacData dacData,
                S1OpenGgfAudioCapture.SongContract contract, int terminalCount,
                Map<Integer, List<Integer>> dispatchesByOrdinal) {
            this.loader = loader;
            this.song = song;
            this.dacData = dacData;
            this.contract = contract;
            this.terminalCount = terminalCount;
            this.dispatchesByOrdinal = dispatchesByOrdinal;
            stream = new OwnedSmpsAudioStream(
                    "s1-sfx-parity", 0,
                    new SmpsPhysicalDevice.Settings(
                            SAMPLE_RATE, false, false),
                    LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE,
                    ChipWriteObserver.NONE);
            driver = stream.logicalDriver();
            musicSequencer = new SmpsSequencer(song, dacData, driver, () -> { },
                    Sonic1SmpsSequencerConfig.CONFIG);
            musicSequencer.setSampleRate(SAMPLE_RATE);
            driver.addSequencer(musicSequencer, false);
            stream.setChipWriteObserver(this);
            S1OpenGgfAudioCapture.CaptureIterator.initializeS1MusicPlayback(driver, song);
        }

        @Override
        public void close() {
            stream.close();
        }

        private void submitDispatches(List<Integer> dispatches) {
            for (int requestedSoundId : dispatches) {
                int soundId = requestedSoundId;
                if (soundId == 0xB5) {
                    // S1 Sound_PlaySFX substitutes the left-speaker program CE
                    // while v_ring_speaker is zero, then toggles the source-owned
                    // bit for the next ring request (SD:984-991).
                    if (ringSpeaker == 0) {
                        soundId = 0xCE;
                    }
                    ringSpeaker ^= 1;
                }
                AbstractSmpsData sfx = Objects.requireNonNull(loader.loadSfx(soundId),
                        "recorded SFX id is absent from the verified ROM: 0x"
                                + Integer.toHexString(soundId));
                SmpsSequencer sequencer = new SmpsSequencer(sfx, dacData, driver, () -> { },
                        Sonic1SmpsSequencerConfig.CONFIG);
                sequencer.setSampleRate(SAMPLE_RATE);
                sequencer.setSfxMode(true);
                sequencer.setSfxPriority(profile.getSfxPriority(soundId));
                sequencer.setSpecialSfx(false);
                sequencer.setFallbackVoiceData(song);
                driver.addSequencer(sequencer, true);
                dispatchCount++;
            }
        }

        @Override
        public boolean hasNext() {
            return ordinal < terminalCount;
        }

        @Override
        public AudioParityTick next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            List<Integer> dispatches = dispatchesByOrdinal.getOrDefault(ordinal, List.of());
            submitDispatches(dispatches);
            if (ordinal == 0) {
                musicSequencer.read(new short[0], 0);
            } else {
                // ROM walk order inside one UpdateMusic invocation: music tracks
                // first, SFX tracks after — the driver's sequencer list preserves
                // that order (music added first, SFX in admission order).
                driver.serviceOuterFrame();
            }
            driver.reapCompletedSequencers();
            SmpsSequencerSnapshot snapshot = musicSequencer.captureSnapshot();
            S1AudioStateNormalizer.NormalizedState state = S1AudioStateNormalizer.normalize(
                    snapshot, contract.assetRange(), contract.f7LoopIndices());
            AudioParityTick tick = new AudioParityTick(ordinal, state.global(), state.tracks(),
                    List.copyOf(writes), dispatches);
            writes.clear();
            ordinal++;
            return tick;
        }

        @Override
        public void onYm2612Write(int port, int register, int value) {
            writes.add(AudioParityChipWrite.ym2612(port, register, value));
        }

        @Override
        public void onPsgWrite(int value) {
            writes.add(AudioParityChipWrite.psg(value));
        }
    }
}
