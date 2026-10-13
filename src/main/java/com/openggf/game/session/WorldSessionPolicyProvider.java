package com.openggf.game.session;

/** Internal module service creating policy state with the lifetime of one world. */
@FunctionalInterface
public interface WorldSessionPolicyProvider {
    /** Returns null when the resolved module has no supported policy configuration. */
    WorldSessionPolicyState open(WorldSession world);
    /** Each provider creates an independent captured child in stable dependency order. */
    static WorldSessionPolicyProvider compose(WorldSessionPolicyProvider... providers) {
        var values = java.util.Arrays.stream(providers).filter(java.util.Objects::nonNull).toList();
        return world -> CompositeWorldSessionPolicyState.of(values.stream().map(p -> p.open(world))
                .toArray(WorldSessionPolicyState[]::new));
    }
}
