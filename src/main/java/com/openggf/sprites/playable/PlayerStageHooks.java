package com.openggf.sprites.playable;

import com.openggf.game.GameModule;
import com.openggf.game.internal.PlayerStageRoutines;

/**
 * The game's {@link PlayerStageRoutines} at the shared player's hurt entry and water check, kept
 * out of {@link AbstractPlayableSprite}. Each method is a no-op answer for a game without stage
 * routines; Sonic CD's {@code PlayerCheckWater} (scddisasm src/objects/player.asm:431-482) and
 * {@code HurtPlayer} are the one implementer.
 */
final class PlayerStageHooks {
    private PlayerStageHooks() { }

    private static PlayerStageRoutines routines(AbstractPlayableSprite player) {
        GameModule module;
        try {
            module = player.currentGameModule();
        } catch (IllegalStateException ex) {
            return null;
        }
        return module != null && module.getPhysicsProvider() instanceof PlayerStageRoutines r ? r : null;
    }

    /**
     * A game's stage routines may clear their own state at the hurt entry
     * (Sonic CD Wacky Workbench HurtPlayer, Player Object Collision.asm:236-238).
     */
    static void beforeHurt(AbstractPlayableSprite player) {
        PlayerStageRoutines r = routines(player);
        if (r != null) r.beforeHurt(player);
    }

    /**
     * A fixed level object owns the air countdown and its bubble cadence: the level events'
     * (S3K's Breathing_bubbles in fixed object RAM) or the stage's own air-countdown slot.
     */
    static boolean fixedObjectOwnsDrowningCadence(AbstractPlayableSprite player) {
        try {
            return player.currentGameModule().getLevelEventProvider()
                    .ownsFixedDrowningBubbleCadence(player) || ownsCountdown(player);
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    /** The stage's water check returns before comparing the level. */
    static boolean skipsWaterCheck(AbstractPlayableSprite player) {
        PlayerStageRoutines r = routines(player);
        return r != null && !r.runsWaterCheck(player);
    }

    /**
     * A player above the level that is still falling keeps the underwater status
     * ({@code PlayerCheckWaterSurface}: {@code tst.w obj.y_speed; beq; bpl}, player.asm:461-464).
     */
    static boolean keepsUnderwaterWhileFalling(AbstractPlayableSprite player, int ySpeed) {
        if (ySpeed <= 0) return false;
        PlayerStageRoutines r = routines(player);
        return r != null && r.waterExitWaitsWhileFalling(player);
    }

    /** The stage's part of the water entry (its air timer and air-countdown slot). */
    static void onEntry(AbstractPlayableSprite player) {
        PlayerStageRoutines r = routines(player);
        if (r != null) r.onWaterEntry(player);
    }

    /** The stage created its own splash object (and sound) for this entry or exit. */
    static boolean spawnsSplash(AbstractPlayableSprite player) {
        PlayerStageRoutines r = routines(player);
        return r != null && r.spawnsWaterSplash(player);
    }

    /** A fixed air-countdown slot of the stage owns the air timer and the drowning. */
    static boolean ownsCountdown(AbstractPlayableSprite player) {
        PlayerStageRoutines r = routines(player);
        return r != null && r.ownsDrowningCountdown(player);
    }
}
