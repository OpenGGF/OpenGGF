package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * Internal host Move-tail ordering, independent of donor abilities, mutable
 * speed profiles and the later Roll routine's moving-crouch threshold.
 * S1 Sonic_LookUp and S2 Sonic_Lookup (also KiS2) test Up first;
 * S3K loc_11276 / loc_172A8 and Tails' Move tail test Down first.
 * This immutable composition service adds no gameplay or rewind state.
 */
public enum StandingInputPolicy {
    UP_FIRST, DOWN_FIRST;

    public static StandingInputPolicy forHost(GameModule host) {
        StandingInputPolicy policy = host == null ? null : host.getGameService(StandingInputPolicy.class);
        return policy == null ? UP_FIRST : policy;
    }
}
