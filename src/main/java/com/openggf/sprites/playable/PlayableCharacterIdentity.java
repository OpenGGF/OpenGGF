package com.openggf.sprites.playable;

import com.openggf.game.CharacterConstructionScope;
import com.openggf.game.CharacterKey;

/** Engine-owned identity operations kept out of the release-critical playable runtime. */
final class PlayableCharacterIdentity {
    private PlayableCharacterIdentity() { }

    static CharacterKey bindForConstruction(AbstractPlayableSprite sprite) {
        return CharacterConstructionScope.bindExpectedOrDefault(defaultFor(sprite));
    }

    static CharacterKey defaultFor(AbstractPlayableSprite sprite) {
        if (sprite instanceof Tails) return CharacterKey.TAILS;
        if (sprite instanceof Knuckles) return CharacterKey.KNUCKLES;
        return CharacterKey.SONIC;
    }
}
