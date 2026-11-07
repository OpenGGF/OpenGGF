package com.openggf.mods.run;

import com.openggf.game.ModApi;

/** Immutable hosted-race contract. */
@ModApi
public enum LevelStartCause { LAUNCH, RETRY, FAST_DEATH_RESTART, CHECKPOINT_RESPAWN, ARENA_DEATH_RESPAWN }
