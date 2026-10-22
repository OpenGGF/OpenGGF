package com.openggf.game.internal;

/**
 * Engine-owned profile whose consumed zone/act entry starts a blocking fade.
 *
 * <p>The request-producing level iteration still finishes normally. Only the
 * consumed entry boundary suspends gameplay until the destination load clears
 * the existing transition-inactive state. A positive fade duration alone does
 * not establish this capability, and creator profiles do not opt in through
 * the public LevelInitProfile contract.
 */
public interface BlockingLevelEntryFade {
}
