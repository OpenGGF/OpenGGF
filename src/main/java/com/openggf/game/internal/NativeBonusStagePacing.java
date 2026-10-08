package com.openggf.game.internal;

/** Engine lifetime identity for a native bonus entry; never supplies creator or gameplay authority. */
public interface NativeBonusStagePacing {
    long pacingEntryEpoch();
}
