package com.openggf.game;

import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.KnucklesRespawnStrategy;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.SonicRespawnStrategy;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.playable.TailsRespawnStrategy;

/** Engine-owned immutable definitions for the three stock playable characters. */
final class StockPlayableCharacters {
    private static final PlayableCharacterRegistry REGISTRY = create();

    private StockPlayableCharacters() { }

    static PlayableCharacterRegistry registry() {
        return REGISTRY;
    }

    private static PlayableCharacterRegistry create() {
        return PlayableCharacterRegistry.empty()
                .register(CharacterKey.SONIC, new CharacterDefinition(
                        CharacterKey.SONIC, "Sonic",
                        (code, x, y) -> new Sonic(code, (short) x, (short) y),
                        SonicRespawnStrategy::new, PlayerCharacter.SONIC_ALONE,
                        SecondaryAbility.INSTA_SHIELD, true, ignored -> SpriteArtSet.EMPTY))
                .register(CharacterKey.TAILS, new CharacterDefinition(
                        CharacterKey.TAILS, "Tails",
                        (code, x, y) -> new Tails(code, (short) x, (short) y),
                        TailsRespawnStrategy::new, PlayerCharacter.TAILS_ALONE,
                        SecondaryAbility.FLY, true, ignored -> SpriteArtSet.EMPTY))
                .register(CharacterKey.KNUCKLES, new CharacterDefinition(
                        CharacterKey.KNUCKLES, "Knuckles",
                        (code, x, y) -> new Knuckles(code, (short) x, (short) y),
                        KnucklesRespawnStrategy::new, PlayerCharacter.KNUCKLES,
                        SecondaryAbility.GLIDE, true, ignored -> SpriteArtSet.EMPTY));
    }

}
