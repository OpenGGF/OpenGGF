package com.openggf.game.sonic1.audio;

import com.openggf.audio.session.SmpsFadeOutEffects;
import com.openggf.audio.session.SmpsStatefulCommandPolicy;
import com.openggf.audio.session.SmpsWriteProgram;

/** Command side effects owned by the Sonic 1 68000 sound driver. */
public final class Sonic1StatefulCommandPolicy implements SmpsStatefulCommandPolicy {
    public static final Sonic1StatefulCommandPolicy INSTANCE = new Sonic1StatefulCommandPolicy();
    private static final Identity IDENTITY = new Identity("sonic1-commands-v1");

    private Sonic1StatefulCommandPolicy() { }

    @Override
    public Identity identity() {
        return IDENTITY;
    }

    @Override
    public SmpsFadeOutEffects fadeOutEffects() {
        // FadeOutMusic (s1.sounddriver.asm:1360-1367) calls StopSFX and
        // StopSpecialSFX, arms the music fade, then clears f_speedup.
        // These instructions are common to the shipped FixBugs=0 path.
        return new SmpsFadeOutEffects(false, true, true, SmpsWriteProgram.EMPTY);
    }
}
