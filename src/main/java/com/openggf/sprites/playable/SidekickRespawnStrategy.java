package com.openggf.sprites.playable;

import com.openggf.game.PhysicsFeatureSet;

/**
 * Strategy for per-character respawn behavior during the APPROACHING state.
 * Implementations define how a sidekick visually re-enters the game after despawning.
 */
public interface SidekickRespawnStrategy {
    /**
     * Called each frame while in APPROACHING state.
     * @return true when respawn is complete and sidekick should transition to NORMAL
     */
    boolean updateApproaching(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader,
                              int frameCounter);

    /**
     * Position and initialize the sidekick when transitioning from SPAWNING to APPROACHING.
     * @return true if approach started, false if conditions not met (stay in SPAWNING)
     */
    boolean beginApproach(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader);

    /**
     * Whether this strategy needs the normal physics pipeline to run during APPROACHING.
     * Strategies that manually position the sprite (Tails fly-in, Knuckles glide) return false.
     * Strategies that rely on ground speed and terrain collision (Sonic walk/spindash) return true.
     */
    default boolean requiresPhysics() {
        return false;
    }

    /**
     * Whether this approach routine owns its own off-screen timeout instead of
     * using {@link SidekickCpuController}'s NORMAL-routine despawn check.
     */
    default boolean handlesApproachDespawn() {
        return false;
    }

    /**
     * Whether this strategy should use S3K's Tails catch-up marker routine
     * after despawn instead of re-entering through SPAWNING.
     */
    default boolean usesS3kCatchUpMarker(PhysicsFeatureSet featureSet) {
        return false;
    }

    /**
     * Apply character-specific state that the ROM restores on the frame the
     * approach completes, before the shared controller transitions to NORMAL.
     */
    default void onApproachComplete(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
    }

    /**
     * Carries any approach-routine off-screen counter into the NORMAL routine.
     * S2 Tails uses one {@code Tails_respawn_counter} across
     * {@code TailsCPU_Flying} and {@code TailsCPU_CheckDespawn}; other
     * strategies keep the NORMAL despawn timer independent.
     */
    default int consumeApproachDespawnCarryFrames() {
        return 0;
    }

    /**
     * Comparison-only mirror of any approach-routine counter that shares the
     * ROM respawn-counter global. Implementations that do not own that counter
     * should return the controller's current value unchanged.
     */
    default int diagnosticRespawnCounter(int controllerValue) {
        return controllerValue;
    }

    /**
     * Comparison-only mirror of the ROM {@code Tails_CPU_target_X} word while
     * an approach strategy owns fly-in steering.
     */
    default int diagnosticTargetX(int controllerValue) {
        return controllerValue;
    }

    /**
     * Comparison-only mirror of the ROM {@code Tails_CPU_target_Y} word while
     * an approach strategy owns fly-in steering.
     */
    default int diagnosticTargetY(int controllerValue) {
        return controllerValue;
    }
}
