package com.openggf.game;

/**
 * Says who paces a special stage's ROM entry sequence. Every provider steps
 * the sequence's V-int waits itself frame by frame (the entry fade-to-white
 * is visible over the level and the fade-from-white is the reveal, so none of
 * it is hidden startup); only ownership of the synchronous entry-load span differs.
 */
@com.openggf.game.ModApi
public enum SpecialStageStartupPolicy {
    /** Ordinary play: the provider owns entry-load timing without an external row driver. */
    FAST,
    /**
     * A BK2-driven session paces the entry: recorded lag rows are admitted by
     * the timing port, so the provider must add no span of its own.
     */
    TRACE_ACCURATE
}
