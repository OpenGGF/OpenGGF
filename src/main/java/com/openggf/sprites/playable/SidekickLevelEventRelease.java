package com.openggf.sprites.playable;

/**
 * Level-event owned writes to {@code Tails_CPU_routine} that object code outside this package
 * needs. {@link SidekickCpuController} is a Mod API type, so these engine-internal entry points
 * live here rather than as new public members on it.
 */
public final class SidekickLevelEventRelease {
    private SidekickLevelEventRelease() {
    }

    /**
     * ROM {@code Obj_57DCC} (sonic3k.asm:116913-116917) and the shared tail of
     * {@code loc_13B18}: {@code clr.w (Tails_CPU_flight_timer).w} then
     * {@code move.w #6,(Tails_CPU_routine).w}, sending the sidekick straight to the ground-follow
     * routine rather than through the catch-up flight that a routine-2 write would select.
     *
     * @return whether the sidekick had a CPU controller to release
     */
    public static boolean toNormalFollow(AbstractPlayableSprite sidekick) {
        if (sidekick == null) {
            return false;
        }
        SidekickCpuController controller = sidekick.getCpuController();
        if (controller == null) {
            return false;
        }
        controller.releaseDormantMarkerToNormalFollow();
        return true;
    }
}
