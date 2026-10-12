package com.openggf.sprites.managers;

import com.openggf.sprites.playable.PlayableMutatorPolicySource;

/** Engine-only binding, deliberately outside the creator-facing Mod API. */
public final class SpriteManagerInternalAccess {
    private SpriteManagerInternalAccess() { }

    /**
     * Bind after gameplay-context creation. New/recreated roster members inherit
     * this source; a replacement gameplay context must be bound independently.
     */
    public static void bindMutatorPolicies(SpriteManager manager, PlayableMutatorPolicySource source) {
        manager.bindMutatorPolicies(source);
    }
}
