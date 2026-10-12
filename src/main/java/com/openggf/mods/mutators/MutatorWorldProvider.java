package com.openggf.mods.mutators;

import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import java.util.Objects;

/** Host-prepared catalog factory. Session state is created once and held only by its world. */
public final class MutatorWorldProvider implements WorldSessionPolicyProvider {
    private final MutatorCatalog catalog;

    public MutatorWorldProvider(MutatorCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override public WorldSessionPolicyState open(WorldSession world) {
        if (catalog.definitions().isEmpty()) return null;
        // The explicit presentation patch may add support outside the backing catalog module.
        var support = world.resolvedGameModule().getGameService(MutatorSupportProfile.class);
        return support == null ? null : new MutatorWorldState(world, catalog, support);
    }
}
