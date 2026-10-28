package com.openggf.game.internal;

/** Internal ordinary-ring counter transition; monitor rewards have their own native owner. */
@FunctionalInterface
public interface RingCollectionPolicy {
    record Result(int rings, int extraLifeFlags, boolean extraLife, boolean refreshHud) { }

    Result collect(int held, int extraLifeFlags, int amount);
}
