package com.openggf.mods.mutators;

import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import java.util.Objects;

/** Host-prepared catalog factory. Session state is created once and held only by its world. */
public final class MutatorWorldProvider implements WorldSessionPolicyProvider {
    private final MutatorCatalog catalog;
    private final WorldSessionPolicyProvider engineChildren;

    public MutatorWorldProvider(MutatorCatalog catalog) {
        this(catalog,null);
    }
    public MutatorWorldProvider(MutatorCatalog catalog,WorldSessionPolicyProvider inherited) {
        this.catalog=Objects.requireNonNull(catalog,"catalog");
        // An outer mod owns the aggregated catalogue, replacing the earlier lease owner.
        // Retain the native pack graph exactly once instead of nesting earlier mod owners.
        engineChildren=inherited instanceof MutatorWorldProvider previous?previous.engineChildren:inherited;
    }

    @Override public WorldSessionPolicyState open(WorldSession world) {
        // The explicit presentation patch may add support outside the backing catalog module.
        var support=world.resolvedGameModule().getGameService(MutatorSupportProfile.class);
        var owner=catalog.definitions().isEmpty() || support==null?null:new MutatorWorldState(world,catalog,support);
        return com.openggf.game.session.CompositeWorldSessionPolicyState.of(owner,
                engineChildren==null?null:engineChildren.open(world));
    }
}
