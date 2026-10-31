package com.openggf.level.objects.boss;

/**
 * A boss whose remaining hit count can be collapsed so the next native hit is the defeating one.
 * Only the Instagib homing mutator calls this, immediately before the native touch pass delivers
 * its attack; the boss's own hit handling, invulnerability and defeat sequence then run unchanged.
 */
public interface InstagibBossTarget {
    void collapseToFinalHit();
}
