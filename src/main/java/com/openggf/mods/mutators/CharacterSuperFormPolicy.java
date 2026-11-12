package com.openggf.mods.mutators;

import com.openggf.game.ModApi;
import com.openggf.audio.GameMusic;

/** Owner-local character options. The host matches the publishing owner before consuming these. */
@ModApi
public record CharacterSuperFormPolicy(String character, boolean sonic1, boolean sonic2,
        boolean sonic3k, int sonic1Emeralds, int rings, GameMusic sonic1Music) implements MutatorPolicy {
    public CharacterSuperFormPolicy {
        com.openggf.game.ModKeySyntax.requireLocalName(character);
        if (sonic1Emeralds < 0 || sonic1Emeralds > 6 || rings < 1 || rings > 999)
            throw new IllegalArgumentException("Invalid form gates");
        java.util.Objects.requireNonNull(sonic1Music);
    }
    @Override public MutatorCapability capability() { return MutatorCapability.CHARACTER_SUPER_FORM; }
}
