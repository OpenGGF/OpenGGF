package com.openggf.level;

/** Internal host-loop view of the level-inactive flag, without a pending bonus request. */
public final class LevelTransitionHostAccess {
    private LevelTransitionHostAccess() { }

    /**
     * Whether a transition already in progress freezes the host iteration. A
     * pending bonus request is Restart_level_flag for the object pass that
     * raises it or next runs, and that iteration's LevelLoop tail consumes it
     * (docs/skdisasm/sonic3k.asm:7926-7929), so it must not freeze that pass.
     */
    public static boolean freezesHostIteration(LevelManager level) {
        return level != null && level.isLevelInactiveBeforeObjectPass();
    }
}
