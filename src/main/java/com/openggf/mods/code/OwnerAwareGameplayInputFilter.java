package com.openggf.mods.code;

import com.openggf.control.PlayerInputState;
import com.openggf.game.GameplayInputFilter;
import com.openggf.game.ModKeySyntax;

import java.util.Objects;

/**
 * Carries the destination owner alongside an installed gameplay filter.
 * Owner fault-bound callback execution is supplied by the policy publication layer.
 */
public final class OwnerAwareGameplayInputFilter implements GameplayInputFilter {
    private final String ownerModId;
    private final GameplayInputFilter delegate;

    public OwnerAwareGameplayInputFilter(String ownerModId, GameplayInputFilter delegate) {
        this.ownerModId = ModKeySyntax.requireManifestId(ownerModId);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public String ownerModId() {
        return ownerModId;
    }

    @Override
    public PlayerInputState filter(PlayerInputState rawSnapshot) {
        return delegate.filter(rawSnapshot);
    }
}
