package com.openggf.tools.challenge;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.output.OpenAlPcmSink;
import com.openggf.audio.presentation.AudioPresentationFrameView;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.*;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Separate ROM-backed UI synthesis. Menu presentation never advances any worker clock. */
final class ChallengeMenuAudio implements AutoCloseable {
    private final Rom rom = new Rom();
    private final AudioManager audio;
    private final Map<GameSound, AbstractSmpsData> cues = new EnumMap<>(GameSound.class);
    private final DacData dac;
    private final Path directory;
    private final UiSink output;
    private long nextPacket;
    private boolean closed;

    ChallengeMenuAudio(Path s1Rom, OpenAlPcmSink sink, ChallengeCapture capture) throws IOException {
        ChallengeRoms.validate("s1", s1Rom);
        Path parent = Path.of("target", "challenge-menu").toAbsolutePath();
        Files.createDirectories(parent);
        directory = Files.createTempDirectory(parent, "audio-");
        var profile = new Sonic1AudioProfile();
        AudioManager created = null;
        try {
            if (!rom.open(s1Rom.toAbsolutePath().toString()))
                throw new IOException("Could not open title sound ROM");
            var config = SonicConfigurationService.createStandalone(directory);
            config.resetToDefaults();
            config.setSessionOverride(SonicConfiguration.REGION, "NTSC");
            config.setSessionOverride(SonicConfiguration.FPS, 60);
            output = new UiSink(sink, capture);
            created = AudioManager.createStandalonePresentation("s1", profile, config,
                    PerformanceProfiler.getInstance(), output,
                    new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
            created.setRom(rom);
            var loader = profile.createSmpsLoader(rom);
            dac = loader.loadDacData();
            for (GameSound sound : List.of(GameSound.RING, GameSound.JUMP, GameSound.CHECKPOINT,
                         GameSound.SPRING, GameSound.ERROR)) {
                AbstractSmpsData data = loader.loadSfx(profile.getSoundMap().get(sound));
                if (data == null)
                    throw new IOException("Missing title sound " + sound);
                cues.put(sound, data);
            }
            audio = created;
        } catch (IOException | RuntimeException | Error failure) {
            if (created != null)
                created.destroy();
            rom.close();
            deleteDirectory();
            throw failure;
        }
    }
    void cue(GameSound sound) {
        if (!closed)
            audio.playStandaloneSfx(cues.get(sound), dac, 1f);
    }
    void update(boolean menuVisible) {
        if (closed)
            return;
        output.audible = menuVisible;
        long now = System.nanoTime();
        if (now < nextPacket)
            return;
        nextPacket = now + 16_666_667;
        // UI synthesis has its own presentation clock and never catches up game ticks.
        audio.presentFrame(PresentationMode.FORWARD);
    }
    short[] mix(short[] gamePcm) {
        short[] cue = output.latest;
        output.latest = null;
        if (cue == null)
            return gamePcm;
        short[] mixed = gamePcm.clone();
        for (int i = 0; i < mixed.length && i < cue.length; i++) {
            int sum = mixed[i] + Math.round(cue[i] * .5f);
            mixed[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sum));
        }
        return mixed;
    }
    @Override
    public void close() throws IOException {
        if (closed)
            return;
        closed = true;
        audio.destroy();
        rom.close();
        deleteDirectory();
    }
    private void deleteDirectory() throws IOException {
        try (var files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
    private static final class UiSink implements AudioPresentationSink {
        final OpenAlPcmSink sink;
        final ChallengeCapture capture;
        boolean audible;
        short[] latest;
        UiSink(OpenAlPcmSink sink, ChallengeCapture capture) {
            this.sink = sink;
            this.capture = capture;
        }
        public int sampleRate() {
            return ChallengeProtocol.RATE;
        }
        public void accept(AudioPresentationFrameView frame) {
            short[] packet = new short[frame.stereoFrames() * 2];
            frame.copyTo(packet, 0);
            latest = packet;
            if (audible) {
                sink.acceptStereoPcm(packet);
                sink.updateDevice();
            }
            if (capture != null)
                try {
                    capture.menuPacket(packet);
                } catch (IOException failure) {
                    throw new IllegalStateException("Menu capture failed", failure);
                }
        }
        public void onReverseBoundary() {}
        public void close() {}
    }
}
