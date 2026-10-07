package com.openggf.mods.scene;

/** A bounded host-owned ROM synthesis job; polling never waits for rendering. */
@com.openggf.game.ModApi
public interface SceneMusicPreparation {
    /** READY has a complete result; FAILED and CANCELLED are terminal. */
    @com.openggf.game.ModApi
    enum State { PREPARING, READY, FAILED, CANCELLED }

    /** Current rendering phase. */
    State state();

    /** Integer progress in 0..100; READY always reports 100. */
    int progressPercent();

    /** A bounded failure explanation in FAILED, otherwise null. */
    String error();

    /**
     * Publishes the READY result on the scene thread. Throws IllegalStateException
     * otherwise. Preparing a musical part returns the same prepared song.
     */
    ScenePreparedMusic prepared();

    /** Cancels an unpublished job and releases its retained PCM; safe to repeat. */
    void cancel();
}
