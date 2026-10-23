package com.openggf.tools.audio;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.session.LegacyCompatibilitySmpsPhysicalPolicy;
import com.openggf.audio.session.OwnedSmpsAudioStream;
import com.openggf.audio.session.SmpsDriverSessionConfiguration;
import com.openggf.audio.session.SmpsPhysicalDevice;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsConfigBinding;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.Ym2612Chip;
import com.openggf.data.Rom;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import com.openggf.game.sonic1.audio.smps.Sonic1SmpsLoader;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.version.AppVersion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Headless renderer for one ROM-backed SFX or song through the real SMPS
 * driver, capturing the YM2612 write stream alongside the audio.
 *
 * <p>Generalises {@code PsgSfxRenderTool} (branch {@code feature/ai-psg-clean-room})
 * to the FM side for the Nuked-OPN2 port validation
 * ({@code docs/architecture/designs/2026-08-29-nuked-opn2-port-contract.md},
 * stages 1 and 2). For one SFX id or music id it writes three files into
 * {@code --out}:
 *
 * <ul>
 *   <li>{@code <game>-<kind>-<id>-mix.wav}: the full driver mix, 16-bit stereo;</li>
 *   <li>{@code <game>-<kind>-<id>-fm.wav}: the same render with every PSG
 *       channel muted, so only the FM chip (and its DAC) reaches the output;</li>
 *   <li>{@code <game>-<kind>-<id>-ym-writes.txt}: every resolved YM2612 write
 *       the driver issued, one {@code <frame> <port> <reg> <val>} line (port
 *       0/1, register and value in hex), where {@code frame} is the number of
 *       output frames rendered before the write landed. The driver is read
 *       one frame at a time so the position is exact.</li>
 * </ul>
 *
 * <p>{@code --paced} instead renders the way the game plays: one 60 Hz service per frame through
 * the game's own physical policy (its {@link GameAudioProfile}), as scene music does. It writes
 * only {@code <game>-<kind>-<id>-paced.wav} and reports how many times the DAC was enabled. Use it
 * for anything meant to be heard, such as a promo bed or stinger: the default direct-read render
 * uses the legacy policy, which never enables the Sonic 3 &amp; Knuckles DAC, so its S3K music has
 * no drums (2026-10-23 Time Attack promo).</p>
 *
 * <p>The legacy write log remains a frame-stamped logical diagnostic. Pass
 * {@code --physical-writes} to also produce {@code -ym-bus.jsonl}: dispatched
 * raw YM address/data strobes (including DAC bytes) and PSG data bytes at
 * native chip clocks. It is an engine/Nuked comparison artifact, not a
 * hardware capture or gameplay timing authority. The physical file carries
 * reset, restore, policy, and rollback discontinuities explicitly and never
 * manufactures a bus write for those model changes.
 *
 * <pre>
 * java -cp target/classes:... com.openggf.tools.audio.FmSfxRenderTool \
 *     --game s1 --rom /path/to/s1.gen (--sfx A6 | --music 81) --out /task/dir \
 *     [--rate 44100 | --rate internal] [--max-seconds 5]
 *     [--physical-writes [--physical-capacity 1000000]] [--paced]
 * </pre>
 */
public final class FmSfxRenderTool {

    private static final double DEFAULT_RATE = 44_100.0;
    private static final double DEFAULT_SFX_MAX_SECONDS = 5.0;
    private static final double DEFAULT_MUSIC_MAX_SECONDS = 30.0;
    private static final int PSG_CHANNELS = 4;
    private static final int DEFAULT_PHYSICAL_CAPTURE_CAPACITY = 1_000_000;

    private FmSfxRenderTool() {
    }

    public static void main(String[] arguments) throws IOException {
        String game = null;
        String romPath = null;
        int sfxId = -1;
        int musicId = -1;
        Path out = null;
        double rate = DEFAULT_RATE;
        double maxSeconds = -1;
        boolean physicalWrites = false;
        boolean paced = false;
        int physicalCapacity = DEFAULT_PHYSICAL_CAPTURE_CAPACITY;
        for (int i = 0; i < arguments.length; i++) {
            switch (arguments[i]) {
                case "--game" -> game = arguments[++i].toLowerCase(Locale.ROOT);
                case "--rom" -> romPath = arguments[++i];
                case "--sfx" -> sfxId = parseHex(arguments[++i]);
                case "--music" -> musicId = parseHex(arguments[++i]);
                case "--out" -> out = Path.of(arguments[++i]);
                case "--rate" -> rate = parseRate(arguments[++i]);
                case "--max-seconds" -> maxSeconds = Double.parseDouble(arguments[++i]);
                case "--physical-writes" -> physicalWrites = true;
                case "--paced" -> paced = true;
                case "--physical-capacity" -> physicalCapacity = Integer.parseInt(arguments[++i]);
                default -> throw new IllegalArgumentException("unknown argument: " + arguments[i]);
            }
        }
        if (game == null || romPath == null || (sfxId < 0) == (musicId < 0) || out == null) {
            System.err.println("usage: --game s1|s2|s3k --rom <path> (--sfx <hex id> | --music <hex id>)"
                    + " --out <dir> [--rate hz|internal] [--max-seconds s] [--paced]");
            System.exit(2);
        }
        boolean music = musicId >= 0;
        if (maxSeconds < 0) {
            maxSeconds = music ? DEFAULT_MUSIC_MAX_SECONDS : DEFAULT_SFX_MAX_SECONDS;
        }

        Rom rom = new Rom();
        if (!rom.open(romPath)) {
            System.err.println("failed to open ROM: " + romPath);
            System.exit(1);
        }
        if (paced) {
            GameAudioProfile profile = switch (game) {
                case "s1" -> new Sonic1AudioProfile();
                case "s2" -> new Sonic2AudioProfile();
                case "s3k" -> new Sonic3kAudioProfile();
                default -> throw new IllegalArgumentException("unknown game: " + game);
            };
            Files.createDirectories(out);
            int id = music ? musicId : sfxId;
            String stem = String.format(Locale.ROOT, "%s-%s-%02x", game, music ? "music" : "sfx", id);
            PacedRender render = renderPaced(profile, game, rom, id, music, rate, (int) (maxSeconds * rate));
            StereoPcmWavWriter.write(out.resolve(stem + "-paced.wav"), render.samples, rate);
            System.out.printf(Locale.ROOT, "%s: %d frames (%.3f s) paced, dac-enables=%d, complete=%s%n",
                    stem, render.frames, render.frames / rate, render.dacEnables, render.complete);
            return;
        }
        SmpsLoader loader;
        SmpsSequencerConfig config;
        switch (game) {
            case "s1" -> {
                loader = new Sonic1SmpsLoader(rom);
                config = Sonic1SmpsSequencerConfig.CONFIG;
            }
            case "s2" -> {
                loader = new Sonic2SmpsLoader(rom);
                config = Sonic2SmpsSequencerConfig.CONFIG;
            }
            case "s3k" -> {
                loader = new Sonic3kSmpsLoader(rom);
                config = Sonic3kSmpsSequencerConfig.CONFIG;
            }
            default -> throw new IllegalArgumentException("unknown game: " + game);
        }
        int id = music ? musicId : sfxId;
        AbstractSmpsData data = music ? loader.loadMusic(musicId) : loader.loadSfx(sfxId);
        if (data == null) {
            System.err.println((music ? "music" : "SFX") + " 0x" + Integer.toHexString(id)
                    + " did not load for " + game);
            System.exit(1);
        }
        DacData dac = loader.loadDacData();

        Files.createDirectories(out);
        String stem = String.format(Locale.ROOT, "%s-%s-%02x", game, music ? "music" : "sfx", id);
        int maxFrames = (int) (maxSeconds * rate);

        Render mix = render(data, dac, config, rate, maxFrames, music, false,
                physicalWrites ? new PhysicalChipCapture(physicalCapacity) : null);
        Render fmOnly = render(data, dac, config, rate, maxFrames, music, true);

        StereoPcmWavWriter.write(out.resolve(stem + "-mix.wav"), mix.samples, rate);
        StereoPcmWavWriter.write(out.resolve(stem + "-fm.wav"), fmOnly.samples, rate);
        try (PrintWriter log = new PrintWriter(Files.newBufferedWriter(out.resolve(stem + "-ym-writes.txt")))) {
            log.printf(Locale.ROOT, "# game=%s %s=%02X rate=%.6f frames=%d complete=%s%n",
                    game, music ? "music" : "sfx", id, rate, mix.frames, mix.complete);
            for (Write w : mix.writes) {
                log.printf(Locale.ROOT, "%d %d %02X %02X%n", w.frame, w.port, w.register, w.value);
            }
        }
        if (mix.physicalCapture != null) {
            PhysicalChipCapture capture = mix.physicalCapture;
            capture.write(out.resolve(stem + "-ym-bus.jsonl"), game,
                    music ? "music" : "sfx", id, rate,
                    Path.of(romPath).toAbsolutePath().normalize().toString(),
                    sha1(Path.of(romPath)), AppVersion.identity());
            if (capture.overflowed()) {
                throw new IllegalStateException("physical capture overflowed after render: "
                        + capture.dropped() + " events dropped; incomplete "
                        + stem + "-ym-bus.jsonl is not replayable");
            }
        }
        System.out.printf(Locale.ROOT, "%s: %d frames (%.3f s), %d YM writes, complete=%s%n",
                stem, mix.frames, mix.frames / rate, mix.writes.size(), mix.complete);
    }

    private static int parseHex(String text) {
        return Integer.parseInt(text.replaceFirst("^0[xX]", ""), 16);
    }

    private static double parseRate(String text) {
        if ("internal".equalsIgnoreCase(text)) {
            return Ym2612Chip.getInternalRate();
        }
        return Double.parseDouble(text);
    }

    private static String sha1(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("JDK lacks SHA-1", failure);
        }
    }

    private record Write(long frame, int port, int register, int value) {
    }

    record PacedRender(short[] samples, int frames, boolean complete, int dacEnables) {
    }

    /**
     * Renders through the production-paced session, as {@code LiveSceneMusic} plays scene music:
     * the game's physical and stateful command policies, its presentation coordination flags, and
     * one driver service per 60 Hz frame followed by that frame's PCM (with the DAC idle loop).
     */
    static PacedRender renderPaced(GameAudioProfile profile, String game, Rom rom, int id,
            boolean music, double rate, int maxFrames) {
        SmpsLoader loader = profile.createSmpsLoader(rom);
        AbstractSmpsData data = music ? loader.loadMusic(id) : loader.loadSfx(id);
        if (data == null) {
            throw new IllegalArgumentException((music ? "music" : "SFX") + " 0x"
                    + Integer.toHexString(id) + " did not load for " + game);
        }
        DacData dac = loader.loadDacData();
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState());
        profile.configurePresentationCoordFlagHandlers(handlers);
        SmpsSequencerConfig base = profile.getSequencerConfig();
        SmpsSequencerConfig config = SmpsConfigBinding.bind(base,
                () -> base.getCoordFlagHandler() == null ? null : handlers.handlerFor(game));
        int[] dacEnables = {0};
        ChipWriteObserver observer = new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                if (port == 0 && register == 0x2B && (value & 0x80) != 0) {
                    dacEnables[0]++;
                }
            }

            @Override
            public void onPsgWrite(int value) {
            }
        };
        short[] samples = new short[maxFrames * 2];
        int frames = 0;
        boolean complete;
        try (OwnedSmpsAudioStream stream = new OwnedSmpsAudioStream(game, 0,
                new SmpsPhysicalDevice.Settings(rate, false), profile.smpsPhysicalPolicy(), observer,
                new SmpsDriverSessionConfiguration(profile.smpsStatefulCommandPolicy()))) {
            SmpsDriver driver = stream.logicalDriver();
            driver.setRegion(SmpsSequencer.Region.NTSC);
            SmpsSequencer seq = new SmpsSequencer(data, dac, driver, () -> { }, config);
            seq.setSampleRate(rate);
            seq.setSfxMode(!music);
            if (music) {
                seq.setFallbackVoiceData(data);
            }
            driver.addSequencer(seq, !music);
            short[] packet = new short[((int) Math.ceil(rate / 60) + 1) * 2];
            for (long tick = 0; frames < maxFrames && !driver.isComplete(); tick++) {
                int available = (int) (Math.floor((tick + 1) * rate / 60) - Math.floor(tick * rate / 60));
                stream.serviceAndRenderFrame(packet, available);
                int count = Math.min(available, maxFrames - frames);
                System.arraycopy(packet, 0, samples, frames * 2, count * 2);
                frames += count;
            }
            complete = driver.isComplete();
        }
        return new PacedRender(Arrays.copyOf(samples, frames * 2), frames, complete, dacEnables[0]);
    }

    private record Render(short[] samples, int frames, boolean complete,
            List<Write> writes, PhysicalChipCapture physicalCapture) {
    }

    private static Render render(AbstractSmpsData data, DacData dac, SmpsSequencerConfig config,
            double rate, int maxFrames, boolean music, boolean mutePsg) {
        return render(data, dac, config, rate, maxFrames, music, mutePsg, null);
    }

    private static Render render(AbstractSmpsData data, DacData dac, SmpsSequencerConfig config,
            double rate, int maxFrames, boolean music, boolean mutePsg,
            PhysicalChipCapture physicalCapture) {
        List<Write> writes = new ArrayList<>();
        long[] framesRendered = {0};
        ChipWriteObserver physicalOnly = physicalObserver(physicalCapture);
        try (OwnedSmpsAudioStream stream = new OwnedSmpsAudioStream(
                "fm-render", 0,
                new SmpsPhysicalDevice.Settings(rate, false, com.openggf.audio.synth.FmCoreSelection.ACCURATE),
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE,
                physicalOnly)) {
            SmpsDriver driver = stream.logicalDriver();
            driver.setRegion(SmpsSequencer.Region.NTSC);
            stream.applyChannelMasks(0, mutePsg ? 0x0F : 0);
            if (physicalCapture != null) {
                physicalCapture.beginYmReplaySegment(stream.captureSynthSnapshotForTesting().ym());
            }
            // Keep the historical logical log starting after boot and mask setup,
            // while physical capture has already observed those dispatched strobes.
            stream.setChipWriteObserver(new ChipWriteObserver() {
                @Override
                public void onYm2612Write(int port, int register, int value) {
                    writes.add(new Write(framesRendered[0], port, register, value));
                }

                @Override
                public void onPsgWrite(int value) {
                }

                @Override
                public boolean observesPhysicalWrites() {
                    return physicalCapture != null;
                }

                @Override
                public void onYm2612BusWrite(long cycle, int busPort, int value,
                        ChipWriteObserver.PhysicalWriteOrigin origin) {
                    physicalCapture.onYm2612BusWrite(cycle, busPort, value, origin);
                }

                @Override
                public void onPsgBusWrite(long tick, int value) {
                    physicalCapture.onPsgBusWrite(tick, value);
                }

                @Override
                public void onPhysicalTimelineBoundary(
                        ChipWriteObserver.ChipClockDomain domain, long clock,
                        ChipWriteObserver.PhysicalTimelineBoundary boundary) {
                    physicalCapture.onPhysicalTimelineBoundary(domain, clock, boundary);
                }
            });

            SmpsSequencer seq = new SmpsSequencer(data, dac, driver, () -> { }, config);
            seq.setSampleRate(rate);
            seq.setSfxMode(!music);
            driver.addSequencer(seq, !music);

            short[] samples = new short[maxFrames * 2];
            short[] frame = new short[2];
            int frames = 0;
            while (frames < maxFrames && !driver.isComplete()) {
                stream.read(frame, 2);
                samples[frames * 2] = frame[0];
                samples[frames * 2 + 1] = frame[1];
                frames++;
                framesRendered[0] = frames;
            }
            short[] trimmed = new short[frames * 2];
            System.arraycopy(samples, 0, trimmed, 0, trimmed.length);
            if (physicalCapture != null) {
                physicalCapture.finish(frames, stream.captureSynthSnapshotForTesting().ym());
            }
            return new Render(trimmed, frames, driver.isComplete(), writes,
                    physicalCapture);
        }
    }

    private static ChipWriteObserver physicalObserver(
            PhysicalChipCapture physicalCapture) {
        if (physicalCapture == null) {
            return ChipWriteObserver.NONE;
        }
        return new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
            }

            @Override
            public void onPsgWrite(int value) {
            }

            @Override
            public boolean observesPhysicalWrites() {
                return true;
            }

            @Override
            public void onYm2612BusWrite(long cycle, int busPort, int value,
                    ChipWriteObserver.PhysicalWriteOrigin origin) {
                physicalCapture.onYm2612BusWrite(cycle, busPort, value, origin);
            }

            @Override
            public void onPsgBusWrite(long tick, int value) {
                physicalCapture.onPsgBusWrite(tick, value);
            }

            @Override
            public void onPhysicalTimelineBoundary(
                    ChipWriteObserver.ChipClockDomain domain, long clock,
                    ChipWriteObserver.PhysicalTimelineBoundary boundary) {
                physicalCapture.onPhysicalTimelineBoundary(domain, clock, boundary);
            }
        };
    }

}
