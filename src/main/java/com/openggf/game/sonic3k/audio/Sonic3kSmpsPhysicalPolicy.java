package com.openggf.game.sonic3k.audio;

import com.openggf.audio.session.LegacyCompatibilitySmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsChipWrite;
import com.openggf.audio.session.SmpsMusicActivation;
import com.openggf.audio.session.SmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsWriteProgram;

import java.util.ArrayList;
import java.util.List;

/** Shipped Sonic 3 &amp; Knuckles Z80 physical-device policy. */
public final class Sonic3kSmpsPhysicalPolicy
        implements SmpsPhysicalPolicy {
    public static final Sonic3kSmpsPhysicalPolicy INSTANCE =
            new Sonic3kSmpsPhysicalPolicy();

    private static final Identity IDENTITY =
            new Identity("sonic3k-shipped-v1");
    private static final SmpsWriteProgram STOP_ALL = stopAllProgram();
    private static final SmpsWriteProgram BOOT = bootProgram();

    private Sonic3kSmpsPhysicalPolicy() {
    }

    @Override
    public Identity identity() {
        return IDENTITY;
    }

    @Override
    public SmpsWriteProgram boot() {
        return BOOT;
    }

    @Override
    public SmpsWriteProgram stopAll() {
        return STOP_ALL;
    }

    @Override
    public SmpsWriteProgram silenceAllPsg() {
        // Sound/Z80 Sound Driver.asm:zPlaySoundByIndex/zFadeEffects routes
        // E3h to zPSGSilenceAll, whose four iterations add 20h to 9Fh.
        return SmpsWriteProgram.SILENCE_ALL_PSG;
    }

    @Override
    public SmpsWriteProgram activateMusic(SmpsMusicActivation activation) {
        return LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE
                .activateMusic(activation);
    }

    private static SmpsWriteProgram bootProgram() {
        List<SmpsChipWrite> writes = new ArrayList<>(85);
        writes.addAll(STOP_ALL.writes());
        // zInitAudioDriver enters zPlayDigitalAudio; the first idle loop
        // disables DAC once more after zStopAllSound completes.
        writes.add(new SmpsChipWrite.Ym2612(0, 0x2B, 0x00));
        return new SmpsWriteProgram(writes);
    }

    private static SmpsWriteProgram stopAllProgram() {
        List<SmpsChipWrite> writes = new ArrayList<>(84);
        // Sound/Z80 Sound Driver.asm:zStopAllSound, pinned skdisasm
        // 044fa467, fix_sndbugs=0. The shipped branch calls
        // zFMSilenceChannel; the fixed branch would inline a safer key-off.
        for (int channel : new int[] {6, 0, 1, 2, 4, 5}) {
            int port = (channel & 4) == 0 ? 0 : 1;
            int offset = channel & 3;
            for (int register = 0x80;
                    register <= 0x8C; register += 4) {
                writes.add(new SmpsChipWrite.Ym2612(
                        port, register + offset, 0xFF));
            }
            for (int register = 0x40;
                    register <= 0x4C; register += 4) {
                writes.add(new SmpsChipWrite.Ym2612(
                        port, register + offset, 0x7F));
            }
            writes.add(new SmpsChipWrite.Ym2612(
                    0, 0x28, channel));
            for (int register = 0x90;
                    register <= 0x9C; register += 4) {
                writes.add(new SmpsChipWrite.Ym2612(
                        port, register + offset, 0x00));
            }
        }
        writes.add(new SmpsChipWrite.Psg(0x9F));
        writes.add(new SmpsChipWrite.Psg(0xBF));
        writes.add(new SmpsChipWrite.Psg(0xDF));
        writes.add(new SmpsChipWrite.Psg(0xFF));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x2B, 0x00));
        writes.add(new SmpsChipWrite.Ym2612(0, 0x27, 0x00));
        return new SmpsWriteProgram(writes);
    }
}
