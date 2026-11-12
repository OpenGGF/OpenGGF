package com.openggf.sprites.playable;

import com.openggf.game.CharacterDefinition;
import com.openggf.game.PlayableEntity;
import com.openggf.game.CharacterKey;
import com.openggf.game.PlayerCharacter;

/** Engine dispatch by declared ROM route, independent of body class or display name. */
public final class PlayableArchetype {
    private PlayableArchetype() { }

    public static boolean isTails(PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite sprite)) return false;
        // Native identity remains valid even for isolated native-body fixtures.
        if (sprite instanceof Tails) return true;
        var key = sprite.characterKey();
        if (key == null) return false; // Unbound test double; production factories reject null keys.
        if (CharacterKey.TAILS.equals(key)) return true;
        var module = sprite.currentGameModule();
        return module != null && module.getPlayableCharacterRegistry().find(sprite.characterKey())
                .map(definition -> definition.behavesLike() == PlayerCharacter.TAILS_ALONE).orElse(false);
    }

    public static boolean declares(AbstractPlayableSprite sprite, CharacterDefinition.MovementAbility ability) {
        var key = sprite.characterKey();
        if (key == null) return false;
        var module = sprite.currentGameModule();
        return module != null && module.getPlayableCharacterRegistry().find(key)
                .map(definition -> definition.movementAbilities().contains(ability)).orElse(false);
    }
}
