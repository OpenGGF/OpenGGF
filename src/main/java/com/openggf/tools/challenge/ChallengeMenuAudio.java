package com.openggf.tools.challenge;

import com.openggf.audio.AudioManager;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.output.OpenAlPcmSink;
import com.openggf.audio.presentation.AudioPresentationFrameView;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.*;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Separate ROM-backed UI synthesis. Menu presentation never advances any worker clock. */
final class ChallengeMenuAudio implements AutoCloseable {
    /** Host feedback, each voiced by a native Sonic 1 sound effect from the supplied ROM. */
    enum Cue {
        /** Lamppost: the title and the all-ready barrier. */
        READY(Sonic1Sfx.LAMPPOST),
        /** Ring: countdown beats and a new sound focus. */
        TICK(Sonic1Sfx.RING),
        /** Spring: all three games start together. */
        GO(Sonic1Sfx.SPRING),
        /** Switch: host pause, resume and leaving a run. */
        SELECT(Sonic1Sfx.SWITCH),
        /** Wall smash: a run stopped and needs a retry. */
        FAULT(Sonic1Sfx.WALL_SMASH);

        private final Sonic1Sfx sound;
        Cue(Sonic1Sfx sound) {
            this.sound = sound;
        }
    }
    private final Rom rom = new Rom();
    private final AudioManager audio;
    private final Map<Cue, AbstractSmpsData> cues = new EnumMap<>(Cue.class);
    private final DacData dac;
    private final Path directory;
    private final UiSink output;
    private long nextPacket;
    private boolean closed;

    ChallengeMenuAudio(Path s1Rom, OpenAlPcmSink sink, ChallengeCapture capture) throws IOException {
        ChallengeRoms.validate("s1", s1Rom);
        // Private standalone configuration, never the player's working directory.
        directory = Files.createTempDirectory("openggf-challenge-menu-");
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
                    // The retained profiler argument is unused by this standalone
                    // presentation factory; it accepts null without a service root.
                    null, output,
                    new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
            var loader = profile.createSmpsLoader(rom);
            dac = loader.loadDacData();
            for (Cue cue : Cue.values()) {
                AbstractSmpsData data = loader.loadSfx(cue.sound.id);
                if (data == null)
                    throw new IOException("Missing title sound " + cue.sound);
                cues.put(cue, data);
            }
            audio = created;
        } catch (IOException | RuntimeException | Error failure) {
            AudioManager partial = created;
            ChallengeCleanup.closeAll(failure, () -> {
                if (partial != null)
                    partial.destroy();
            }, rom::close, this::deleteDirectory);
            throw failure;
        }
    }
    void cue(Cue cue) {
        if (!closed)
            audio.playStandaloneSfx(cues.get(cue), dac, 1f);
    }
    void update(boolean menuVisible) {
        update(menuVisible, System.nanoTime());
    }
    void update(boolean menuVisible, long now) {
        if (closed) return;
        output.audible = menuVisible;
        if (now < nextPacket)
            return;
        long following = nextPacket + 16_666_667;
        nextPacket = nextPacket == 0 || following <= now ? now + 16_666_667 : following;
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
        Throwable failure = ChallengeCleanup.closeAll(null, audio::destroy, rom::close, this::deleteDirectory);
        if (failure instanceof IOException io)
            throw io;
        if (failure instanceof RuntimeException runtime)
            throw runtime;
        if (failure instanceof Error error)
            throw error;
        if (failure != null)
            throw new IOException("Menu sound cleanup failed", failure);
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
