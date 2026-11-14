package com.openggf.game.soniccd.zones.wwz;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The stage state Wacky Workbench's player checks read but do not own. Each method is
 * one RAM variable or flag that an unhosted owner writes; {@link #CLEAR} answers every
 * one as the cleared variable reads ({@code InitStage} clears the variable block), and
 * the owning lane supplies the real value through {@link WwzPlayerProfile#setSignals}.
 * Citations are {@code Level/Wacky Workbench/Objects/Sonic/Main.asm} (act 1 Present) and
 * {@code src/objects/r6/player.asm} (the other nine stages) at scddisasm pin 1ecc5f3a.
 */
public interface WwzStageSignals {
    /** Every signal read as cleared RAM. */
    WwzStageSignals CLEAR = new WwzStageSignals() { };

    /**
     * {@code bossFlags}: any bit set stops the floor bounce ({@code tst.b bossFlags},
     * Main.asm:522-523); bit 7, the boss defeated, stops the sparks (Main.asm:453-455).
     * Owner: the zone's boss.
     */
    default int bossFlags() {
        return 0;
    }

    /**
     * Writes {@code bossFlags}: act 3's animated tiles turn a bit 6 request into bit 7 ({@code bclr #6} then
     * {@code bset #7}, r6/stage_gfx_animate_3c.asm:11, 27; _3d.asm the same). Owner: the zone's boss, whose RAM holds
     * the byte; cleared RAM has no boss to write to.
     */
    default void setBossFlags(int value) {
    }

    /** {@code bossFlags} as the frame's objects left it, before the animated tiles' write (comparison only). */
    default int bossFlagsAfterObjects() {
        return bossFlags();
    }

    /**
     * {@code palCycleSteps+3}: the step of the fourth palette-cycle script, the electric
     * floor flash. In the Past and Present the floor does not bounce on step $3C / $5A
     * (Main.asm:524-534). Owner: {@code WwzPaletteCycle}'s {@code steps[3]}, which has no
     * accessor yet.
     */
    default int floorFlashStep() {
        return 0;
    }

    /**
     * {@code aniArtFrames+2}: nonzero while the spark tiles are shown; read only in the
     * Good Future and act 3's boss arena (Main.asm:475-477). Owner: {@code WwzAnimatedTiles}.
     */
    default int sparkTileFrame() {
        return 0;
    }

    /** {@code bossMusic}: act 3's boss is active (Main.asm:463-464). Owner: the boss. */
    default boolean bossMusic() {
        return false;
    }

    /** {@code timeWarp}: a warp is in progress (Main.asm:379-380, 443-444). Owner: time travel. */
    default boolean timeWarping() {
        return false;
    }

    /** {@code spawnMode == 2}: the player is returning from a warp (Main.asm:327-328). Owner: time travel. */
    default boolean warpSpawn() {
        return false;
    }

    /**
     * {@code ObjSonic_CheckHangBar} stops a warp on grabbing a bar: {@code timeWarpTimer}
     * and {@code timeWarp} clear, and {@code timeWarpDir} too when the timer had reached 90
     * (Main.asm:344-350). Owner: time travel.
     */
    default void stopTimeWarp(AbstractPlayableSprite player) {
    }
}
