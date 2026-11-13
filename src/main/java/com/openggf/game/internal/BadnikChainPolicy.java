package com.openggf.game.internal;

/** Internal host-owned chain authority; absent hosts retain actor-local scoring. */
public interface BadnikChainPolicy {
    int incrementAndAward();
    void reset();
    int word();
    void restoreWord(int value);
}
