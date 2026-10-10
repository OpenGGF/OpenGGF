package com.openggf.game.session;

/** Internal module service creating policy state with the lifetime of one world. */
@FunctionalInterface
public interface WorldSessionPolicyProvider {
    /** Returns null when the resolved module has no supported policy configuration. */
    WorldSessionPolicyState open(WorldSession world);
}
