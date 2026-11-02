package com.openggf.level.objects.observe;

/**
 * A boss's or badnik's remaining hits and defeat, read only, as its own routine counts them
 * (including multi-phase counts). Absence of the object is never defeat.
 */
public interface HitPointSource {
    int hitsRemaining();

    boolean defeated();
}
