package com.openggf.tools.audio.parity.s3k;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.session.OwnedSmpsAudioStream;
import com.openggf.audio.session.SmpsChipWrite;
import com.openggf.audio.session.SmpsPhysicalDevice;
import com.openggf.audio.session.SmpsWriteProgram;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsPhysicalPolicy;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tools.audio.parity.AudioParityChipWrite;

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
 * ROM-backed headless host that drives the engine's S3K SMPS driver with the
 * request timeline of an oracle reference capture and records, per complete driver service,
 * the ordered chip writes plus a driver-RAM-shaped state export.
 *
 * <p>The reference's per-tick mailbox bytes are driver <em>inputs</em> (the
 * 68k-side {@code zMusicNumber}/{@code zSFXNumber0/1} writes the coming
 * invocation consumes) — the analogue of the S1 tool playing the GHZ song.
 * All compared values (RAM state, write stream) come from the engine alone.
 *
 * <p>Request dispatch mirrors zPlaySoundByIndex (D:1641-1665): music
 * 01h-32h, credits DCh, SFX 33h-DBh, E0h/E2h/E6h-FEh stop-all, E4h stop-SFX.
 * E1h/E5h fades and E3h PSG-mute are not yet modelled by this capture host
 * and are recorded as unsupported (the tick keeps advancing).
 */
public final class S3kOpenGgfAudioCapture {
    private static final double SAMPLE_RATE = 44_100.0;

    private S3kOpenGgfAudioCapture() {
    }

    public record CaptureResult(List<S3kAudioTick> ticks, List<String> unsupportedRequests) {
        public CaptureResult {
            ticks = List.copyOf(ticks);
            unsupportedRequests = List.copyOf(unsupportedRequests);
        }
    }

    public static CaptureResult capture(Path romPath, List<S3kAudioTick> reference,
            Integer corruptWriteTick) {
        Objects.requireNonNull(romPath, "romPath");
        verifyRomIdentity(romPath);
        Rom rom = new Rom();
        if (!rom.open(romPath.toString())) {
            throw new IllegalArgumentException("cannot open verified S3K ROM");
        }
        try (rom) {
            Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(rom);
            DacData dacData = loader.loadDacData();
            try (OwnedSmpsAudioStream stream = new OwnedSmpsAudioStream(
                    "s3k-oracle", 0,
                    new SmpsPhysicalDevice.Settings(
                            SAMPLE_RATE, false, false),
                    Sonic3kSmpsPhysicalPolicy.INSTANCE,
                    ChipWriteObserver.NONE)) {
            SmpsDriver driver = stream.logicalDriver();
            driver.setRegion(SmpsSequencer.Region.NTSC);
            List<AudioParityChipWrite> writes = new ArrayList<>();
            stream.setChipWriteObserver(new ChipWriteObserver() {
                @Override
                public void onYm2612Write(int port, int register, int value) {
                    writes.add(AudioParityChipWrite.ym2612(port, register, value));
                }

                @Override
                public void onPsgWrite(int value) {
                    writes.add(AudioParityChipWrite.psg(value));
                }
            });

            List<String> unsupported = new ArrayList<>();
            List<S3kAudioTick> ticks = new ArrayList<>(reference.size());
            if (reference.isEmpty()) {
                return new CaptureResult(ticks, unsupported);
            }

            // zInitAudioDriver owns one complete pre-V-int service. Its busy
            // loop has no engine-visible effect; the observable boundary is
            // zStopAllSound followed by the initial driver-variable stores
            // (D:523-551,2460-2521). The reference projector finds completion
            // through zPalDblUpdCounter=5, not through a movie frame.
            applyProgram(driver,
                    Sonic3kSmpsPhysicalPolicy.INSTANCE.boot());
            S3kAudioTick bootReference = reference.getFirst();
            addTick(ticks, driver, writes, bootReference, corruptWriteTick);

            for (int index = 1; index < reference.size(); index++) {
                S3kAudioTick referenceTick = reference.get(index);
                int ordinal = referenceTick.ordinal();
                for (int request : referenceTick.mailbox()) {
                    if (request != 0) {
                        dispatch(request, loader, dacData, driver, unsupported, ordinal);
                    }
                }
                driver.serviceOuterFrame();
                addTick(ticks, driver, writes, referenceTick, corruptWriteTick);
            }
            return new CaptureResult(ticks, unsupported);
            }
        }
    }

    private static void addTick(List<S3kAudioTick> ticks, SmpsDriver driver,
            List<AudioParityChipWrite> writes, S3kAudioTick referenceTick,
            Integer corruptWriteTick) {
        int ordinal = referenceTick.ordinal();
        if (corruptWriteTick != null && corruptWriteTick == ordinal && !writes.isEmpty()) {
            AudioParityChipWrite first = writes.getFirst();
            writes.set(0, first.chip().equals("psg")
                    ? AudioParityChipWrite.psg(first.value() ^ 0x01)
                    : AudioParityChipWrite.ym2612(first.port(), first.register(),
                            first.value() ^ 0x01));
        }
        S3kAudioTick normalized = S3kAudioStateNormalizer.normalize(ordinal,
                referenceTick.mailbox(), driver.captureSnapshot());
        ticks.add(new S3kAudioTick(ordinal, false, normalized.mailbox(),
                normalized.global(), normalized.tracks(), List.copyOf(writes)));
        writes.clear();
    }

    private static void applyProgram(
            SmpsDriver driver, SmpsWriteProgram program) {
        for (SmpsChipWrite write : program.writes()) {
            if (write instanceof SmpsChipWrite.Ym2612 ym) {
                driver.writeFm(driver, ym.port(), ym.register(), ym.value());
            } else if (write instanceof SmpsChipWrite.Psg psg) {
                driver.writePsg(driver, psg.value());
            }
        }
    }

    private static void dispatch(int request, Sonic3kSmpsLoader loader, DacData dacData,
            SmpsDriver driver, List<String> unsupported, int ordinal) {
        int id = request == S3kAudioParitySchema.CREDITS_K ? 0x32 : request;
        if (id >= S3kAudioParitySchema.MUSIC_FIRST && id <= S3kAudioParitySchema.MUSIC_LAST) {
            AbstractSmpsData song = loader.loadMusic(id);
            if (song == null) {
                unsupported.add("tick " + ordinal + ": music 0x" + Integer.toHexString(id)
                        + " did not load");
                return;
            }
            driver.stopAll(); // zPlayMusic -> zStopAllSound before zBGMLoad (D:1786-1795)
            SmpsSequencer sequencer = new SmpsSequencer(song, dacData, driver, () -> { },
                    Sonic3kSmpsSequencerConfig.CONFIG);
            sequencer.setSampleRate(SAMPLE_RATE);
            driver.addSequencer(sequencer, false);
            return;
        }
        if (id >= S3kAudioParitySchema.SFX_FIRST && id <= S3kAudioParitySchema.SFX_LAST) {
            AbstractSmpsData sfx = loader.loadSfx(id);
            if (sfx == null) {
                unsupported.add("tick " + ordinal + ": sfx 0x" + Integer.toHexString(id)
                        + " did not load");
                return;
            }
            SmpsSequencer sequencer = new SmpsSequencer(sfx, dacData, driver, () -> { },
                    Sonic3kSmpsSequencerConfig.CONFIG);
            sequencer.setSampleRate(SAMPLE_RATE);
            sequencer.setSfxMode(true);
            driver.addSequencer(sequencer, true);
            return;
        }
        if (id == S3kAudioParitySchema.CMD_STOP_SFX) {
            driver.stopAllSfx();
            return;
        }
        if (id == S3kAudioParitySchema.CMD_FADE_OUT
                || id == S3kAudioParitySchema.CMD_FADE_OUT2) {
            // zFadeOutMusic falls through zHaltDACPSG and always silences all
            // PSG channels on the request service (D:2307-2327). The later
            // 28h-step, six-service fade remains explicitly unsupported here.
            emitPsgSilence(driver);
            unsupported.add("tick " + ordinal + ": active music fade for request 0x"
                    + Integer.toHexString(id) + " is not modelled by this capture host");
            return;
        }
        if (id == S3kAudioParitySchema.CMD_SEGA) {
            // zPlaySegaSound begins with zStopAllSound, then leaves the PCM
            // loop to stream register 2Ah between interrupt services
            // (D:2703-2719,4267-4415).
            applyProgram(driver,
                    Sonic3kSmpsPhysicalPolicy.INSTANCE.stopAll());
            unsupported.add("tick " + ordinal
                    + ": SEGA PCM transport is outside the driver-service oracle");
            return;
        }
        if (id == S3kAudioParitySchema.CMD_MUTE_PSG) {
            unsupported.add("tick " + ordinal + ": request 0x" + Integer.toHexString(id)
                    + " is not modelled by this capture host");
            return;
        }
        // E0h and E6h-FEh: zStopAllSound (map §4.3).
        driver.stopAll();
    }

    private static void emitPsgSilence(SmpsDriver driver) {
        driver.writePsg(driver, 0x9f);
        driver.writePsg(driver, 0xbf);
        driver.writePsg(driver, 0xdf);
        driver.writePsg(driver, 0xff);
    }

    private static void verifyRomIdentity(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] chunk = new byte[64 * 1024];
            int count;
            while ((count = input.read(chunk)) >= 0) {
                sha1.update(chunk, 0, count);
            }
            String actual = HexFormat.of().formatHex(sha1.digest());
            if (!S3kAudioParitySchema.S3K_LOCKED_ON_SHA1.equals(actual)) {
                throw new IllegalArgumentException(
                        "S3K oracle capture requires the pinned locked-on ROM; got SHA-1 " + actual);
            }
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalArgumentException("cannot verify S3K ROM identity", error);
        }
    }
}
