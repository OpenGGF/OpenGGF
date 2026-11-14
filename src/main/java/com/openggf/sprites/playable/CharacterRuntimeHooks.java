package com.openggf.sprites.playable;

import java.util.Objects;

/** Engine-internal dispatch bridge for owner-bound playable callbacks. */
public final class CharacterRuntimeHooks {
    private CharacterRuntimeHooks() { }

    /** Rejects a factory returning an instance captured from another construction/owner lifetime. */
    public static void requireConstructionBoundary(AbstractPlayableSprite sprite,
            com.openggf.game.CharacterConstructionScope.CallbackInvoker invoker) {
        if (!Objects.requireNonNull(sprite,"sprite").hasConstructionCallbackInvoker(invoker))
            throw new IllegalArgumentException("Character factory must construct its result in the current owner scope");
    }

    /** Reads creator presentation through the same owner boundary as movement hooks. */
    public static com.openggf.game.StrikeTrailStyle strikeTrailStyle(AbstractPlayableSprite sprite) {
        return Objects.requireNonNull(sprite, "sprite").dispatchStrikeTrailStyle();
    }

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
