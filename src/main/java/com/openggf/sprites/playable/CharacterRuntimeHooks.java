package com.openggf.sprites.playable;

import java.util.Objects;

/** Engine-internal dispatch bridge for owner-bound playable callbacks. */
public final class CharacterRuntimeHooks {
    private CharacterRuntimeHooks() { }

    public static boolean activateAbility(AbstractPlayableSprite sprite) {
        return activateAbility(sprite, false, false, false, false);
    }

    public static boolean activateAbility(AbstractPlayableSprite sprite,
                                          boolean up, boolean down,
                                          boolean left, boolean right) {
        return Objects.requireNonNull(sprite, "sprite")
                .dispatchAbilityActivate(up, down, left, right);
    }
}
