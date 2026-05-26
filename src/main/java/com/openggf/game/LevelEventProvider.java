package com.openggf.game;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Interface for game-specific dynamic level events.
 *
 * Level events handle runtime changes to camera boundaries, boss arena setup,
 * earthquake effects, and other zone-specific behaviors that trigger based on
 * player/camera position during gameplay.
 *
 * Implementations are game-specific (e.g., Sonic 2's RunDynamicLevelEvents).
 */
public interface LevelEventProvider {

    /**
     * Initialize level event state for a new level.
     * Called when a level is loaded or restarted.
     *
     * @param zone The zone index
     * @param act The act index within the zone
     */
    void initLevel(int zone, int act);

    /**
     * Update level events for the current frame.
     * Called once per frame before camera boundary easing.
     *
     * Implementations should check camera/player position and trigger
     * appropriate boundary changes or other level events.
     */
    void update();

    /**
     * Updates fixed in-level object RAM that is outside the dynamic SST scan.
     * <p>
     * S3K runs fixed objects such as {@code Breathing_bubbles} and
     * {@code Breathing_bubbles_P2} after dynamic object RAM and before
     * {@code ScreenEvents} (docs/skdisasm/sonic3k.constants.asm:311-312;
     * docs/skdisasm/sonic3k.asm:7893-7898,35965). The default is a no-op for
     * games without fixed object sidecars.
     */
    default void updateFixedInLevelObjects() {
        // Default no-op
    }

    /**
     * Returns true when the level-event provider owns the drowning bubble
     * cadence through ROM fixed object sidecars. Shared player water code uses
     * this to avoid running its generic bubble RNG cadence in parallel.
     */
    default boolean ownsFixedDrowningBubbleCadence() {
        return false;
    }

    /**
     * Returns true when the level-event provider owns the drowning bubble
     * cadence for this specific player slot. Multi-player or novelty sidekick
     * configurations may only have ROM fixed sidecars for a subset of sprites.
     */
    default boolean ownsFixedDrowningBubbleCadence(AbstractPlayableSprite player) {
        return ownsFixedDrowningBubbleCadence();
    }

    /**
     * Returns true when an event/object-order bridge should treat a sidekick
     * follow step as still inside a ROM-visible push/on-object context.
     */
    default boolean isSidekickObjectOrderFollowSteeringContext(
            AbstractPlayableSprite sidekick,
            AbstractPlayableSprite effectiveLeader) {
        return false;
    }

    /**
     * Returns true when an event/object-order bridge should use the alternate
     * ROM-visible follow target for the immediate +/-1 sidekick x_pos nudge.
     * This can be narrower than the steering bridge above.
     */
    default boolean isSidekickObjectOrderFollowNudgeContext(
            AbstractPlayableSprite sidekick,
            AbstractPlayableSprite effectiveLeader) {
        return isSidekickObjectOrderFollowSteeringContext(sidekick, effectiveLeader);
    }

    /**
     * Returns true when a ROM door/platform support context should suppress
     * stale push-grace handling during sidekick follow steering.
     */
    default boolean isSidekickDoorSupportGraceFollowSteeringContext(
            AbstractPlayableSprite sidekick,
            ObjectInstance ridingObject) {
        return false;
    }

    /**
     * Returns true when an event marker path reaches Tails_Catch_Up_Flying
     * with the stored engine counter one tick behind the ROM-visible sidekick
     * CPU slot. This is separate from normal follow/auto-jump cadence.
     */
    default boolean usesSidekickRomVisibleCatchUpMarkerFrameCounterBridge(AbstractPlayableSprite sidekick) {
        return false;
    }

    /**
     * Returns true when level-event ordering should put an uninitialized
     * sidekick into a dormant marker state instead of the normal spawn path.
     */
    default boolean shouldEnterSidekickDormantMarker(AbstractPlayableSprite sidekick) {
        return false;
    }

    /**
     * Called when a player falls below the bottom boundary.
     * If this returns true, the pit death is intercepted (e.g. zone transition).
     * <p>
     * ROM reference: Sonic_LevelBound in s1disasm - SBZ2 intercepts bottom
     * boundary death to transition to SBZ3 (LZ act 3).
     *
     * @param player the player about to die
     * @return true if the death should be suppressed
     */
    default boolean interceptPitDeath(AbstractPlayableSprite player) {
        return false;
    }
}
