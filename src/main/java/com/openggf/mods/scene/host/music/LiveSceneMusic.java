package com.openggf.mods.scene.host.music;

import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.presentation.ScenePcmSource;
import com.openggf.audio.session.OwnedSmpsAudioStream;
import com.openggf.audio.session.SmpsDriverSessionConfiguration;
import com.openggf.audio.session.SmpsPhysicalDevice;
import com.openggf.audio.smps.SmpsConfigBinding;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.data.Rom;
import java.util.Arrays;
import java.util.Objects;

/** Scene-owned live ROM music. The ROM sequencer owns intros, loops and endings. */
public final class LiveSceneMusic implements ScenePcmSource, AutoCloseable {
    private final OwnedSmpsAudioStream stream;
    private final int rate;
    private final short[] packet;
    private long frame;
    private int cursor, available;
    private int fadeRemaining = -1;
    private boolean paused, closed;

    public LiveSceneMusic(String game, int musicId, GameAudioProfile profile, Rom rom, int sampleRate) {
        if (sampleRate < 60) throw new IllegalArgumentException("Music output rate must be at least 60 Hz");
        rate = sampleRate;
        packet = new short[((rate + 59) / 60) * 2];
        var loader = profile.createSmpsLoader(Objects.requireNonNull(rom, "ROM"));
        var data = Objects.requireNonNull(loader.loadMusic(musicId), "ROM music ID unavailable");
        var dac = Objects.requireNonNull(loader.loadDacData(), "ROM DAC bank unavailable");
        var handlers = new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState());
        profile.configurePresentationCoordFlagHandlers(handlers);
        var base = profile.getSequencerConfig();
        var config = SmpsConfigBinding.bind(base,
                () -> base.getCoordFlagHandler() == null ? null : handlers.handlerFor(game));
        stream = new OwnedSmpsAudioStream(game, 0, new SmpsPhysicalDevice.Settings(rate, false),
                profile.smpsPhysicalPolicy(), ChipWriteObserver.NONE,
                new SmpsDriverSessionConfiguration(profile.smpsStatefulCommandPolicy()));
        try {
            var driver = stream.logicalDriver();
            driver.setRegion(SmpsSequencer.Region.NTSC);
            // Scene cues do not restore ambient gameplay music on a native jingle callback.
            var seq = new SmpsSequencer(data, dac, driver, () -> { }, config);
            seq.setSampleRate(rate);
            seq.setFallbackVoiceData(data);
            driver.addSequencer(seq, false);
        } catch (RuntimeException | Error failure) {
            stream.close();
            throw failure;
        }
    }

    @Override
    public void render(short[] target, int stereoFrames) {
        if (stereoFrames < 0 || stereoFrames > target.length / 2) throw new IllegalArgumentException("PCM buffer too small");
        if (closed || paused || fadeRemaining == 0) {
            Arrays.fill(target, 0, stereoFrames * 2, (short) 0);
            return;
        }
        // Packet sizes need not match NTSC driver cadence (capture, PAL output, odd rates).
        for (int offset = 0; offset < stereoFrames; ) {
            if (cursor == available) {
                available = (int) (((frame + 1) * rate / 60) - (frame * rate / 60));
                frame++;
                stream.serviceAndRenderFrame(packet, available);
                cursor = 0;
            }
            int count = Math.min(stereoFrames - offset, available - cursor);
            for (int i = 0; i < count; i++) {
                double gain = fadeRemaining < 0 ? 1 : (double) fadeRemaining / rate;
                target[(offset + i) * 2] = (short) (packet[(cursor + i) * 2] * gain);
                target[(offset + i) * 2 + 1] = (short) (packet[(cursor + i) * 2 + 1] * gain);
                if (fadeRemaining > 0) fadeRemaining--;
            }
            offset += count;
            cursor += count;
        }
    }

    public void fadeOut() { if (fadeRemaining < 0) fadeRemaining = rate; }
    @Override public void onHostPause() { paused = true; }
    @Override public boolean onHostResume() { paused = false; return true; }
    @Override public void close() {
        if (!closed) { closed = true; stream.close(); }
    }
}
