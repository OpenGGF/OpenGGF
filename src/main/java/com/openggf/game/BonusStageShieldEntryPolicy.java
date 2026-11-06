package com.openggf.game;

/** Game-owned policy for carrying saved shields into the bonus interior. */
public interface BonusStageShieldEntryPolicy {
    boolean restoresSavedShieldOnEntry();
}
