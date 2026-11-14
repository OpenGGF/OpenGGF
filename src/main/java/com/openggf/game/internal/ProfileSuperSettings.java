package com.openggf.game.internal;

import com.openggf.audio.GameMusic;
import com.openggf.game.CharacterKey;

/** Value-only form settings; the publishing owner interprets creator policies. */
public record ProfileSuperSettings(boolean sonic1, boolean sonic2, boolean sonic3k,
        int sonic1Emeralds, int rings, GameMusic sonic1Music) {
    public static final ProfileSuperSettings DEFAULT = new ProfileSuperSettings(true, true, true,
            6, 50, GameMusic.INVINCIBILITY);
    @FunctionalInterface
    public interface Source { ProfileSuperSettings forCharacter(CharacterKey key); }
}
