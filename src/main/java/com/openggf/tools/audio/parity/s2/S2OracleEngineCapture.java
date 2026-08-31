package com.openggf.tools.audio.parity.s2;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.SmpsSequencerSnapshot;
import com.openggf.audio.rewind.SmpsTrackSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.audio.Sonic2SmpsConstants;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * ROM-backed headless engine capture for the S2 driver oracle: plays the EHZ
 * song through the real {@link SmpsDriver}/{@link SmpsSequencer} with the S2
 * config, one NTSC driver update per tick, and maps each post-tick snapshot
 * into the ROM zTrack vocabulary of {@link S2OracleDriverState}. Tick 0
 * corresponds to the fixture's anchor row (the zVInt that consumed the EHZ
 * request): it emits the shipped song-load silence burst
 * (zInitMusicPlayback/zBGMLoad, s2.sounddriver.asm:1667-2075, FixDriverBugs=0)
 * and then performs the first driver update.
 *
 * <p>Nothing here reads the fixture: the music id and speed-up tick come from
 * {@link S2OracleSchema} constants, while callers may supply raw driver SFX
 * requests captured at their source-owned pre-consumption boundary. Requests
 * are stimuli, never inferred from comparison state or chip writes.
 */
public final class S2OracleEngineCapture {
    private static final double SAMPLE_RATE = 44_100.0;

    private S2OracleEngineCapture() {
    }

    /** One engine tick in ROM vocabulary: mapped music-slot states plus ordered writes. */
    public record EngineTick(int ordinal, int currentTempo, int tempoTimeout,
            List<S2OracleComparison.MappedTrack> musicSlots,
            List<S2OracleRawStream.ChipWrite> writes) {
        public EngineTick {
            musicSlots = List.copyOf(musicSlots);
            writes = List.copyOf(writes);
        }
    }

    /** A raw byte written to the S2 driver's sound request queue before a tick. */
    public record DriverRequest(int tick, int soundId) {
        public DriverRequest {
            if (tick < 0) {
                throw new IllegalArgumentException("request tick must be non-negative");
            }
            if (soundId < Sonic2Sfx.ID_BASE || soundId > Sonic2Sfx.ID_MAX) {
                throw new IllegalArgumentException("request must be a Sonic 2 SFX id");
            }
        }
    }

    public static List<EngineTick> capture(Path romPath, int tickCount, int speedUpTick) {
        return capture(romPath, tickCount, speedUpTick, List.of());
    }

    public static List<EngineTick> capture(Path romPath, int tickCount, int speedUpTick,
            List<DriverRequest> requests) {
        Objects.requireNonNull(romPath, "romPath");
        requests = validateRequests(requests, tickCount);
        verifyRomIdentity(romPath);
        Rom rom = new Rom();
        if (!rom.open(romPath.toString())) {
            throw new IllegalArgumentException("cannot open verified S2 ROM");
        }
        try (rom) {
            Sonic2SmpsLoader loader = new Sonic2SmpsLoader(rom);
            // Engine music ids are systematically shifted from the ROM driver's
            // request ids (Sonic2SmpsLoader.findMusicOffset javadoc): engine
            // EMERALD_HILL (0x81) loads the song the driver plays for request
            // id 0x82 (S2OracleSchema.ANCHOR_ROM_MUSIC_ID).
            AbstractSmpsData song = Objects.requireNonNull(
                    loader.loadMusic(Sonic2Music.EMERALD_HILL.id),
                    "S2 EHZ music is absent from the verified ROM");
            DacData dacData = Objects.requireNonNull(loader.loadDacData(),
                    "S2 DAC data is absent from the verified ROM");

            List<EngineTick> ticks = new ArrayList<>(tickCount);
            WriteRecorder writes = new WriteRecorder();
            SmpsDriver driver = new SmpsDriver(SAMPLE_RATE);
            SmpsSequencer sequencer = new SmpsSequencer(song, dacData, driver, () -> { },
                    Sonic2SmpsSequencerConfig.CONFIG);
            sequencer.setSampleRate(SAMPLE_RATE);
            driver.addSequencer(sequencer, false);
            driver.setChipWriteObserver(writes);
            emitS2MusicLoadBurst(driver, song);
            // Oracle ticks are completed zUpdateMusic services (manifest kind
            // 9), not their parent zVInt service (kind 3). Keep the source-
            // accurate load burst out of the per-update write stream.
            writes.drain();

            int z80Start = song.getZ80StartAddress();
            int requestIndex = 0;
            boolean ringLeftNext = true;
            for (int ordinal = 0; ordinal < tickCount; ordinal++) {
                if (ordinal == speedUpTick) {
                    sequencer.setSpeedShoes(true);
                }
                if (requestIndex < requests.size()
                        && requests.get(requestIndex).tick() == ordinal) {
                    int requestedId = requests.get(requestIndex++).soundId();
                    int resolvedId = requestedId;
                    if (requestedId == Sonic2Sfx.RING_RIGHT.id) {
                        // zPlaySound_CheckRing resolves the raw B5h request to
                        // CEh while zRingSpeaker is zero, complements the flag,
                        // then leaves the next B5h request unchanged
                        // (s2.sounddriver.asm:2127-2135). FixDriverBugs=0 does
                        // not alter this shipped-ROM branch.
                        resolvedId = ringLeftNext
                                ? Sonic2Sfx.RING_LEFT.id
                                : Sonic2Sfx.RING_RIGHT.id;
                        ringLeftNext = !ringLeftNext;
                    }
                    admitSfx(loader, dacData, driver, resolvedId);
                }
                driver.serviceOuterFrame();
                SmpsSequencerSnapshot snapshot = sequencer.captureSnapshot();
                ticks.add(new EngineTick(ordinal,
                        effectiveTempo(snapshot, song),
                        snapshot.tempoAccumulator() & 0xff,
                        mapMusicSlots(snapshot, z80Start),
                        writes.drain()));
            }
            return ticks;
        }
    }

    private static List<DriverRequest> validateRequests(
            List<DriverRequest> requests, int tickCount) {
        Objects.requireNonNull(requests, "requests");
        List<DriverRequest> copy = List.copyOf(requests);
        int previousTick = -1;
        for (DriverRequest request : copy) {
            Objects.requireNonNull(request, "request");
            if (request.tick() >= tickCount) {
                throw new IllegalArgumentException("request tick exceeds capture range");
            }
            if (request.tick() <= previousTick) {
                throw new IllegalArgumentException(
                        "requests must be ordered with at most one per tick");
            }
            previousTick = request.tick();
        }
        return copy;
    }

    private static void admitSfx(Sonic2SmpsLoader loader, DacData dacData,
            SmpsDriver driver, int soundId) {
        AbstractSmpsData sfx = Objects.requireNonNull(loader.loadSfx(soundId),
                "S2 SFX is absent from the verified ROM");
        SmpsSequencer sfxSequencer = new SmpsSequencer(sfx, dacData, driver, () -> { },
                Sonic2SmpsSequencerConfig.CONFIG);
        sfxSequencer.setSampleRate(SAMPLE_RATE);
        sfxSequencer.setSfxMode(true);
        sfxSequencer.setSfxPriority(Sonic2SmpsConstants.getSfxPriority(soundId));
        driver.addSequencer(sfxSequencer, true);
    }

    private static int effectiveTempo(SmpsSequencerSnapshot snapshot, AbstractSmpsData song) {
        if (snapshot.speedShoes()) {
            Integer turbo = Sonic2SmpsSequencerConfig.SPEED_UP_TEMPOS.get(song.getId());
            if (turbo != null) {
                return turbo;
            }
        }
        return snapshot.normalTempo() & 0xff;
    }

    /**
     * Maps engine tracks onto the ten ROM music slots (DAC, FM1-6, PSG1-3).
     * The engine only instantiates tracks the song header declares; undeclared
     * slots are reported inactive, matching the zeroed RAM the ROM's
     * zInitMusicPlayback leaves behind.
     */
    private static List<S2OracleComparison.MappedTrack> mapMusicSlots(
            SmpsSequencerSnapshot snapshot, int z80Start) {
        S2OracleComparison.MappedTrack[] slots =
                new S2OracleComparison.MappedTrack[S2OracleDriverState.MUSIC_SLOTS.size()];
        for (SmpsTrackSnapshot track : snapshot.tracks()) {
            int slot = switch (track.type()) {
                case DAC -> 0;
                case FM -> 1 + track.channelId();
                case PSG -> 7 + track.channelId();
            };
            if (slot < 0 || slot >= slots.length || slots[slot] != null) {
                throw new IllegalStateException("engine track does not map to a unique S2 slot");
            }
            slots[slot] = S2OracleComparison.MappedTrack.fromEngine(track, z80Start);
        }
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] == null) {
                slots[index] = S2OracleComparison.MappedTrack.absent();
            }
        }
        return List.of(slots);
    }

    /**
     * The shipped song-start silence burst, FixDriverBugs=0
     * (s2.sounddriver.asm): zInitMusicPlayback ends with zFMSilenceAll
     * (key-off 28h for channel pairs 2/6, 1/5, 0/4, then FFh to registers
     * 30h-8Fh on both parts, sd:2518-2540, 2631-2657) and zPSGSilenceAll
     * (9F BF DF FF, sd:1412-1418); zBGMLoad then handles FM6/DAC
     * (sd:1893-1935) and zInitSFX sends note-offs through the freshly written
     * VoiceControl bytes, with undefined tracks aliasing 0 (sd:2008-2075).
     */
    private static void emitS2MusicLoadBurst(SmpsDriver driver, AbstractSmpsData song) {
        for (int channel = 2; channel >= 0; channel--) {
            driver.writeFm(driver, 0, 0x28, channel);
            driver.writeFm(driver, 0, 0x28, channel + 4);
        }
        for (int register = 0x30; register <= 0x8f; register++) {
            driver.writeFm(driver, 0, register, 0xff);
            driver.writeFm(driver, 1, register, 0xff);
        }
        driver.writePsg(driver, 0x9f);
        driver.writePsg(driver, 0xbf);
        driver.writePsg(driver, 0xdf);
        driver.writePsg(driver, 0xff);

        int declaredFmDacTracks = song.getFmPointers().length;
        int[] fmOrder = Sonic2SmpsSequencerConfig.FM_CHANNEL_ORDER;
        if (declaredFmDacTracks < fmOrder.length) {
            // Song leaves FM6 free: key it off, silence its part-II TLs, reset
            // its pan, DACEnabled = 80h (sd:1893-1935).
            driver.writeFm(driver, 0, 0x28, 6);
            for (int register : new int[] { 0x42, 0x46, 0x4a, 0x4e }) {
                driver.writeFm(driver, 1, register, 0xff);
            }
            driver.writeFm(driver, 1, 0xb6, 0xc0);
            driver.writeFm(driver, 0, 0x2b, 0x80);
        } else {
            driver.writeFm(driver, 0, 0x2b, 0x00);
        }

        // zInitSFX note-offs: 28h <- VoiceControl for the six FM music tracks
        // (undefined tracks carry VoiceControl 0 after the clear) and
        // VoiceControl|1Fh for the three PSG tracks (sd:2045-2075).
        for (int musicFmSlot = 1; musicFmSlot < fmOrder.length; musicFmSlot++) {
            int channel = musicFmSlot < declaredFmDacTracks ? fmOrder[musicFmSlot] : 0;
            driver.writeFm(driver, 0, 0x28, channel);
        }
        int[] psgOrder = Sonic2SmpsSequencerConfig.PSG_CHANNEL_ORDER;
        int declaredPsgTracks = song.getPsgPointers().length;
        for (int slot = 0; slot < psgOrder.length; slot++) {
            int channel = slot < declaredPsgTracks ? psgOrder[slot] : 0;
            driver.writePsg(driver, channel | 0x1f);
        }
    }

    private static void verifyRomIdentity(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("S2 ROM does not exist or is not a regular file");
        }
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                sha1.update(buffer, 0, count);
            }
            if (!S2OracleSchema.S2_REV01_SHA1.equals(
                    HexFormat.of().formatHex(sha1.digest()))) {
                throw new IllegalArgumentException(
                        "the S2 oracle requires the pinned S2 World REV01 ROM");
            }
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalArgumentException("cannot verify S2 ROM identity", error);
        }
    }

    private static final class WriteRecorder implements ChipWriteObserver {
        private final List<S2OracleRawStream.ChipWrite> writes = new ArrayList<>();

        @Override
        public void onYm2612Write(int port, int register, int value) {
            writes.add(new S2OracleRawStream.ChipWrite(true, port, register, value,
                    S2OracleRawStream.ChipWrite.SERVICE_UPDATE_MUSIC));
        }

        @Override
        public void onPsgWrite(int value) {
            writes.add(new S2OracleRawStream.ChipWrite(false, 0, 0, value,
                    S2OracleRawStream.ChipWrite.SERVICE_UPDATE_MUSIC));
        }

        private List<S2OracleRawStream.ChipWrite> drain() {
            List<S2OracleRawStream.ChipWrite> drained = List.copyOf(writes);
            writes.clear();
            return drained;
        }
    }
}
