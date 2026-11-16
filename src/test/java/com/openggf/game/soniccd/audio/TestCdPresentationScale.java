package com.openggf.game.soniccd.audio;

import com.openggf.audio.presentation.AudioPresentationMixer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Absolute source balance, without media, a sound device or amplitude-normalized correlation. */
class TestCdPresentationScale {
    @Test
    void nativePcmAndFastFmUseTheSamePresentationScale() {
        // Reference MAME FM=8000 and CD PCM=8000 are equal before the final mix.
        // Our fast FM facade and shared headroom turn that FM level into 3000.
        short[] mix = render(256, 256);
        for (int frame = 20; frame < 200; frame++) {
            assertEquals(6000, mix[frame * 2], "equal PCM contribution plus existing FM");
            assertEquals(-6000, mix[frame * 2 + 1], "negative polarity");
        }
    }

    @Test
    void userMusicGainStillScalesPcmAndLeavesExistingFmUntouched() {
        for (int gain : new int[]{0, 128}) {
            short[] mix = render(gain, AudioPresentationMixer.UNITY_GAIN);
            for (int frame = 20; frame < 200; frame++) {
                assertEquals(gain == 0 ? 3000 : 4500, mix[frame * 2]);
                assertEquals(gain == 0 ? -3000 : -4500, mix[frame * 2 + 1]);
            }
        }
    }

    private static short[] render(int musicGain, int sfxGain) {
        CdSubCpuAudio subCpu = new CdSubCpuAudio(() -> {
            throw new AssertionError("no disc required for streamed PCM");
        });
        CdFmSoundRequestService mixer = new CdFmSoundRequestService(
                id -> 0, subCpu, new CdFmDriverState());
        mixer.bindOutputRate(48_000);
        mixer.setMixGains(musicGain, sfxGain);
        CdSubCpuAudio.ProgramPcm program = subCpu.beginProgramPcm();
        for (int i = 0; i < 543; i++) program.sample((8000 << 16) | (-8000 & 0xFFFF));
        short[] target = new short[800 * 2];
        for (int i = 0; i < 800; i++) {
            target[i * 2] = 3000;
            target[i * 2 + 1] = -3000;
        }
        mixer.render(target, 800);
        return target;
    }
}
