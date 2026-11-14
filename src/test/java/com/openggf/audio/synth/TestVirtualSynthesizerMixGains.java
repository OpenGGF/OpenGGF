package com.openggf.audio.synth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The presentation-only music/SFX balance applied per channel at the chip output. */
class TestVirtualSynthesizerMixGains {
    private static final int FRAMES = 2_048;

    @ParameterizedTest
    @EnumSource(FmCoreSelection.class)
    void unityGainsLeaveTheOutputBitIdentical(FmCoreSelection core) {
        VirtualSynthesizer gained = primed(core);
        gained.markFmChannelRole(0, true);
        gained.setMixGains(FmChip.UNITY_GAIN, FmChip.UNITY_GAIN);

        assertArrayEquals(render(primed(core)), render(gained));
    }

    @ParameterizedTest
    @EnumSource(FmCoreSelection.class)
    void zeroGainOnARoleSoundsExactlyLikeMutingItsChannels(FmCoreSelection core) {
        VirtualSynthesizer muted = primed(core);
        muted.setFmMute(0, true);
        muted.setPsgMute(0, true);

        VirtualSynthesizer sfxSilenced = primed(core);
        sfxSilenced.markFmChannelRole(0, true);
        sfxSilenced.markPsgChannelRole(0, true);
        sfxSilenced.setMixGains(FmChip.UNITY_GAIN, 0);

        VirtualSynthesizer musicSilenced = primed(core);
        musicSilenced.setMixGains(0, FmChip.UNITY_GAIN);

        short[] expected = render(muted);
        assertArrayEquals(expected, render(sfxSilenced));
        assertArrayEquals(expected, render(musicSilenced));
    }

    @ParameterizedTest
    @EnumSource(FmCoreSelection.class)
    void eachChannelTakesTheGainOfTheRoleThatLastDroveIt(FmCoreSelection core) {
        VirtualSynthesizer musicSilenced = primed(core);
        musicSilenced.setMixGains(0, FmChip.UNITY_GAIN);
        musicSilenced.markFmChannelRole(0, true);
        musicSilenced.markPsgChannelRole(0, true);

        assertArrayEquals(render(primed(core)), render(musicSilenced),
                "channels an SFX drives keep the SFX gain while music is silenced");
    }

    @ParameterizedTest
    @EnumSource(FmCoreSelection.class)
    void partialGainLowersTheLevel(FmCoreSelection core) {
        VirtualSynthesizer half = primed(core);
        half.setMixGains(FmChip.UNITY_GAIN / 2, FmChip.UNITY_GAIN);
        VirtualSynthesizer muted = primed(core);
        muted.setFmMute(0, true);
        muted.setPsgMute(0, true);

        double full = energy(render(primed(core)), render(muted));
        double halved = energy(render(half), render(muted));
        assertTrue(halved > full * 0.15 && halved < full * 0.35,
                "half gain should leave about a quarter of the energy: " + halved / full);
        assertFalse(halved == 0.0);
    }

    /** Signal energy relative to the silent (muted) rendering, which carries the resting level. */
    private static double energy(short[] signal, short[] silence) {
        double sum = 0;
        for (int i = 0; i < signal.length; i++) {
            double d = signal[i] - silence[i];
            sum += d * d;
        }
        return sum;
    }

    private static short[] render(VirtualSynthesizer synth) {
        short[] out = new short[FRAMES * 2];
        synth.renderFrames(out, 0, FRAMES);
        return out;
    }

    /** One sustained FM note on channel 0 and one PSG tone on channel 0. */
    private static VirtualSynthesizer primed(FmCoreSelection core) {
        VirtualSynthesizer synth = new VirtualSynthesizer(44_100.0, ChipWriteObserver.NONE,
                VirtualSynthesizer.Initialization.LEGACY_SILENCE, core);
        synth.setInstrument(synth, 0, new byte[] {
                0x32,
                0x71, 0x0D, 0x33, 0x01,
                0x5F, 0x5F, 0x5F, 0x5F,
                0x14, 0x0E, 0x0E, 0x0E,
                0x08, 0x08, 0x08, 0x08,
                0x0F, 0x0F, 0x0F, 0x0F,
                0x1B, 0x16, 0x1F, 0x00});
        synth.writeFm(synth, 0, 0xA4, 0x22);
        synth.writeFm(synth, 0, 0xA0, 0x69);
        synth.writeFm(synth, 0, 0xB4, 0xC0);
        synth.writeFm(synth, 0, 0x28, 0xF0);
        synth.writePsg(synth, 0x80 | 0x04);
        synth.writePsg(synth, 0x12);
        synth.writePsg(synth, 0x90 | 0x02);
        return synth;
    }
}
