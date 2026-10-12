package com.openggf.sprites.playable;

import com.openggf.level.objects.ObjectInstance;

/** Engine-only cross-package access to playable controller internals. */
public final class PlayableSpriteInternalAccess {
    private PlayableSpriteInternalAccess() {
    }

    public static void bindMutatorPolicies(AbstractPlayableSprite sprite, PlayableMutatorPolicySource source) {
        sprite.mutatorPolicySource = source;
    }

    public static PlayableMutatorPolicy mutatorPolicy(AbstractPlayableSprite sprite) {
        PlayableMutatorPolicySource source = sprite.mutatorPolicySource;
        return source == null ? PlayableMutatorPolicy.STOCK
                : java.util.Objects.requireNonNull(source.policyFor(sprite), "effective playable policy");
    }

    public static boolean activateScriptedSuperForm(SuperStateController controller) {
        return controller.activateFromScript();
    }

    public static Short projectedObjectControlledSolidContactXSpeed(
            AbstractPlayableSprite sprite, ObjectInstance candidate) {
        return sprite.getObjectControlledSolidContactProjectedXSpeed(candidate);
    }
}
