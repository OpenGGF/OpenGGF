package com.openggf.mods.code;

import com.openggf.control.PlayerInputState;
import com.openggf.game.GameplayInputFilter;
import com.openggf.game.ModKeySyntax;

import java.util.Objects;

/**
 * Carries the destination owner and its runtime fault boundary alongside an installed
 * gameplay filter.
 */
public final class OwnerAwareGameplayInputFilter implements GameplayInputFilter {
    private final String ownerModId;
    private final GameplayInputFilter delegate;
    private final ModFaultBoundary faultBoundary;

    public OwnerAwareGameplayInputFilter(String ownerModId, GameplayInputFilter delegate,
                                         ModFaultBoundary faultBoundary) {
        this.ownerModId = ModKeySyntax.requireManifestId(ownerModId);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.faultBoundary = Objects.requireNonNull(faultBoundary, "faultBoundary");
    }

    public String ownerModId() {
        return ownerModId;
    }

    @Override
    public PlayerInputState filter(PlayerInputState rawSnapshot) {
        return faultBoundary.call(ownerModId, () -> Objects.requireNonNull(
                delegate.filter(rawSnapshot), "gameplay input filter result"));
    }
}
