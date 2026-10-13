package com.openggf.sprites.managers;

import com.openggf.sprites.playable.PlayableMutatorPolicySource;

/** Engine-only binding, deliberately outside the creator-facing Mod API. */
public final class SpriteManagerInternalAccess {
    private SpriteManagerInternalAccess() { }

    /** Retire a scene's follower roster while retaining reversible same-world membership. */
    public static void retireAllSidekicksForRewind(SpriteManager manager) {
        manager.retireAllSidekicksForRewind();
    }

    /**
     * Bind after gameplay-context creation. New/recreated roster members inherit
     * this source; a replacement gameplay context must be bound independently.
     */
    public static void bindMutatorPolicies(SpriteManager manager, PlayableMutatorPolicySource source) {
        manager.bindMutatorPolicies(source);
    }
}
