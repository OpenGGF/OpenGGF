package com.openggf.game.internal;

/** Game-owned routine-zero branches that retain the assembled player slot. */
public interface SidekickCpuInitializationPolicy {
    boolean preservesSpawnState(int zone, int act, int starPostActivationMark);
}
