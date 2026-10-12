package com.openggf.game;

/** Explicit admission cause; presentation flags are never configuration authority. */
@ModApi
public enum LevelLoadCause {
    DECODE_ONLY, FULL_LEVEL_ASSEMBLY, FULL_RESTART, FULL_DEATH_RELOAD,
    STAGE_RETURN_FULL_ASSEMBLY, PREVIEW, CHECKPOINT_RESTORE, EDITOR_SWAP, SEAMLESS_HANDOFF
}
