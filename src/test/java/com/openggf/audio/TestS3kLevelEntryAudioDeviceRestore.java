package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.synth.NativeCompatiblePcmOutput;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A same-game level entry ({@code LevelManager.configureAudio}: the installed ROM, then the installed
 * profile) keeps the presentation device, so its state stays rewindable: a snapshot taken before the entry
 * restores into the kept device and forward replay reproduces the native-compatible stage's output (chip,
 * blip buffer and three-band filter state) byte for byte. Round-2 lane pcm-output, 2026-10-17.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLevelEntryAudioDeviceRestore {
    private static final int FRAMES = 90;

    @Test
    void levelEntryKeepsTheDeviceAndARestoreReplaysItsOutputByteIdentically() {
        TestEnvironment.resetAll();
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.AUDIO_FM_CORE, "accurate");
        AudioManager audio = AudioManager.getInstance();
        Rom rom = new Rom();
        assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
        try {
            audio.resetState();
            audio.setBackend(new NullAudioBackend());
            audio.setNativeCompatiblePcmCapture(NativeCompatiblePcmOutput.Config.withFilter(
                    NativeCompatiblePcmOutput.Filter.THREE_BAND));
            var profile = new Sonic3kAudioProfile();
            audio.setRom(rom);
            audio.setAudioProfile(profile);
            audio.setSoundMap(profile.getSoundMap());
            audio.playMusic(Sonic3kMusic.AIZ1.id);
            present(audio, 60);
            drain(audio); // each run below then drains only its own frames
            AudioLogicalSnapshot beforeEntry = audio.captureLogicalSnapshot();

            audio.setRom(rom);
            audio.setAudioProfile(profile);
            present(audio, FRAMES);
            short[] uninterrupted = drain(audio);
            assertEquals(1, audio.nativeCompatiblePcmSessions(), "the level entry kept the device");
            assertTrue(Arrays.stream(toInts(uninterrupted)).anyMatch(sample -> sample != 0),
                    "the compared window sounds");

            audio.restoreLogicalSnapshot(beforeEntry);
            present(audio, FRAMES);
            assertArrayEquals(uninterrupted, drain(audio),
                    "the pre-entry snapshot restores the kept device and replays its output exactly");

            // Kill control: the comparison can disagree. A window one hardware frame later differs.
            audio.restoreLogicalSnapshot(beforeEntry);
            present(audio, 1);
            drain(audio);
            present(audio, FRAMES);
            assertFalse(Arrays.equals(uninterrupted, drain(audio)), "a shifted window must not compare equal");
            assertEquals(1, audio.nativeCompatiblePcmSessions(), "restores reuse the kept device");
        } finally {
            audio.resetState();
            audio.setNativeCompatiblePcmCapture(null);
            config.clearSessionOverrides();
            rom.close();
        }
    }

    private static void present(AudioManager audio, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }
    }

    private static short[] drain(AudioManager audio) {
        short[] out = new short[2 * 4 * 8192 * 4];
        int frames = audio.drainNativeCompatiblePcm(out, 0, out.length / 2);
        assertTrue(frames < out.length / 2, "the drain buffer held every pending frame");
        return Arrays.copyOf(out, frames * 2);
    }

    private static int[] toInts(short[] samples) {
        int[] values = new int[samples.length];
        for (int index = 0; index < samples.length; index++) {
            values[index] = samples[index];
        }
        return values;
    }
}
