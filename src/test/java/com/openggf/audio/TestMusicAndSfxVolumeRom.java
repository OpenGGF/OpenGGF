package com.openggf.audio;

import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.presentation.AudioPresentationFrameView;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The music and sound-effect volumes on real S2 driver output: silencing music
 * must keep the sound effects that steal its channels, and the reverse.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestMusicAndSfxVolumeRom {
    /** S2 ring pickup (FM channel steal) and the jump (PSG). */
    private static final int RING_SFX = 0xB5;
    private static final int JUMP_SFX = 0xA0;

    private Rom rom;

    @AfterEach
    void tearDown() {
        config().clearSessionOverrides();
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
        if (rom != null) {
            rom.close();
        }
    }

    @Test
    void musicAtZeroIsSilentButSoundEffectsStillPlay() {
        double fullMusic = musicEnergy(100, 100);
        double silencedMusic = musicEnergy(0, 100);
        assertTrue(fullMusic > 0, "the reference music must be audible");
        assertTrue(silencedMusic < fullMusic * 1e-4,
                "music at 0% must be silent: " + silencedMusic + " vs " + fullMusic);

        EnergySink sink = start(0, 100);
        AudioManager audio = AudioManager.getInstance();
        audio.playMusic(Sonic2Music.EMERALD_HILL.id);
        present(audio, 30);
        sink.energy = 0;
        audio.playSfx(RING_SFX);
        audio.playSfx(JUMP_SFX);
        present(audio, 20);
        assertTrue(sink.energy > fullMusic * 0.01 && sink.energy > silencedMusic * 1_000,
                "sound effects over silenced music must be audible: " + sink.energy);
    }

    @Test
    void soundEffectsAtZeroAreSilentOverSilencedMusic() {
        EnergySink sink = start(0, 0);
        AudioManager audio = AudioManager.getInstance();
        audio.playMusic(Sonic2Music.EMERALD_HILL.id);
        present(audio, 30);
        audio.playSfx(RING_SFX);
        audio.playSfx(JUMP_SFX);
        present(audio, 20);

        double reference = musicEnergy(100, 100);
        assertTrue(sink.energy < reference * 1e-4,
                "music and sound effects at 0% must be silent: " + sink.energy);
    }

    private double musicEnergy(int musicVolume, int sfxVolume) {
        EnergySink sink = start(musicVolume, sfxVolume);
        AudioManager audio = AudioManager.getInstance();
        audio.playMusic(Sonic2Music.EMERALD_HILL.id);
        present(audio, 50);
        return sink.energy;
    }

    private EnergySink start(int musicVolume, int sfxVolume) {
        config().setSessionOverride(SonicConfiguration.AUDIO_MUSIC_VOLUME, musicVolume);
        config().setSessionOverride(SonicConfiguration.AUDIO_SFX_VOLUME, sfxVolume);
        if (rom != null) {
            rom.close();
        }
        File file = RomTestUtils.ensureSonic2RomAvailable();
        rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        EnergySink sink = new EnergySink();
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new SinkBackend(sink));
        audio.setRom(rom);
        Sonic2AudioProfile profile = new Sonic2AudioProfile(event -> { });
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
        return sink;
    }

    private static void present(AudioManager audio, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }
    }

    private static SonicConfigurationService config() {
        return GameServices.configuration();
    }

    private static final class SinkBackend extends NullAudioBackend {
        private final EnergySink sink;

        private SinkBackend(EnergySink sink) {
            this.sink = sink;
        }

        @Override public int outputSampleRate() { return sink.sampleRate(); }

        @Override
        public AudioPresentationSink createPresentationSink(
                Consumer<Throwable> failureHandler, Consumer<String> warningHandler) {
            return sink;
        }
    }

    /** Sum of squared samples about the packet mean, so a resting DC level counts as silence. */
    private static final class EnergySink implements AudioPresentationSink {
        private double energy;

        @Override public int sampleRate() { return 48_000; }

        @Override
        public void accept(AudioPresentationFrameView frame) {
            short[] packet = new short[frame.stereoFrames() * 2];
            frame.copyTo(packet, 0);
            double mean = 0;
            for (short sample : packet) {
                mean += sample;
            }
            mean /= Math.max(1, packet.length);
            for (short sample : packet) {
                energy += (sample - mean) * (sample - mean);
            }
        }

        @Override public void onReverseBoundary() { }
        @Override public void close() { }
    }
}
