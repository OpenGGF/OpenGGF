package com.openggf.game.sonic3k.difficulty;

/** Optional owner-scoped attack variation; native boss HP and progression stay unchanged. */
@com.openggf.game.ModApi
@FunctionalInterface
public interface AizMinibossAttackPolicy {
    /** Returns -1 for no follow-up, or 16..60 admitted object updates of visible warning. */
    int followupDelayTicks(int remainingHits);
}
