package com.openggf.game.sonic2.audio;

import com.openggf.audio.session.LegacyCompatibilitySmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsChipWrite;
import com.openggf.audio.session.SmpsMusicActivation;
import com.openggf.audio.session.SmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsWriteProgram;

import java.util.ArrayList;
import java.util.List;

/** Named S2 host policy retaining the verified pre-migration 202-write program. */
public final class Sonic2SmpsCompatibilityPolicy
        implements SmpsPhysicalPolicy {
    public static final Sonic2SmpsCompatibilityPolicy INSTANCE =
            new Sonic2SmpsCompatibilityPolicy();

    private static final Identity IDENTITY =
            new Identity("sonic2-compatibility-v1");
    private static final LegacyCompatibilitySmpsPhysicalPolicy DELEGATE =
            LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;

    private Sonic2SmpsCompatibilityPolicy() {
    }

    @Override
    public Identity identity() {
        return IDENTITY;
    }

    @Override
    public SmpsWriteProgram boot() {
        return DELEGATE.boot();
    }

    @Override
    public SmpsWriteProgram stopAll() {
        return DELEGATE.stopAll();
    }

    @Override
    public SmpsWriteProgram beginMusicLoad() {
        // s2.sounddriver.asm zBGMLoad enters zInitMusicPlayback before
        // OptimiseDriver=0 zSaxmanDec: 6 key-offs, 192 register clears and
        // four PSG silences. FixDriverBugs=0 is the shipped path.
        return DELEGATE.stopAll();
    }

    @Override
    public SmpsWriteProgram activateMusic(SmpsMusicActivation activation) {
        return activationProgram(activation.fmDacTrackCount());
    }

    /**
     * zBGMLoad's FM6 disposition, in the shipped driver's own write order
     * (s2.sounddriver.asm:1893-1938). The file assembles with
     * {@code FixDriverBugs = fixBugs = 0} and {@code OptimiseDriver = 0}
     * (:8-9, s2.asm:27), so both conditional blocks below are present in the
     * shipped ROM and are what this reproduces.
     *
     * <p>A song declaring seven FM+DAC tracks is using FM6 as a real FM
     * channel, so the load only tells the chip the DAC is off. Any other
     * count leaves FM6 to the DAC track: the driver keys the channel off,
     * silences its four operator total-level registers, resets its panning
     * (the DAC track never runs zSetVoice, so nothing else would), and only
     * then enables the DAC.
     *
     * <p>With {@code FixDriverBugs = 1} the key-off and the total-level loop
     * would both be gone, deferred to a later zFMNoteOff and
     * zFMSilenceChannel; the engine takes the shipped {@code = 0} path.
     */
    private static SmpsWriteProgram activationProgram(int fmDacTrackCount) {
        if (fmDacTrackCount == 7) {
            return SEVEN_TRACK_ACTIVATION;
        }
        return SILENCE_FM6_ACTIVATION;
    }

    /** zBGMLoad :1893-1898 - FM6 is in use, so the DAC stays disabled. */
    private static final SmpsWriteProgram SEVEN_TRACK_ACTIVATION =
            new SmpsWriteProgram(List.of(
                    new SmpsChipWrite.Ym2612(0, 0x2B, 0x00)));

    /** zBGMLoad .silencefm6 :1900-1938. */
    private static final SmpsWriteProgram SILENCE_FM6_ACTIVATION =
            silenceFm6Activation();

    private static SmpsWriteProgram silenceFm6Activation() {
        List<SmpsChipWrite> writes = new ArrayList<>(7);
        // :1900-1904 - key off all four operators of FM6.
        writes.add(new SmpsChipWrite.Ym2612(0, 0x28, 0x06));
        // :1906-1916 - total silence on FM6's four total level registers.
        for (int register = 0x42; register <= 0x4E; register += 4) {
            writes.add(new SmpsChipWrite.Ym2612(1, register, 0xFF));
        }
        // :1918-1935 - default stereo panning for the DAC track's channel.
        writes.add(new SmpsChipWrite.Ym2612(1, 0xB6, 0xC0));
        // .writesilence :1936-1938 - FM6 is free, so the DAC is enabled.
        writes.add(new SmpsChipWrite.Ym2612(0, 0x2B, 0x80));
        return new SmpsWriteProgram(writes);
    }
}
