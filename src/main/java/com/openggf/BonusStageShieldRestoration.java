package com.openggf;

import com.openggf.game.BonusStageProvider;
import com.openggf.game.ShieldType;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import static com.openggf.GameLoopBonusStageSession.*;

/** Native saved elemental-shield encoding and bonus entry/reward restoration rules. */
final class BonusStageShieldRestoration {
    private BonusStageShieldRestoration() { }

    static int encodeSavedShieldStatus(AbstractPlayableSprite playable) {
        if (playable == null || !playable.hasShield()) {
            return 0;
        }
        ShieldType shieldType = playable.getShieldType();
        if (shieldType == null) {
            return 0;
        }
        return switch (shieldType) {
            case FIRE -> 1 << STATUS_FIRE_SHIELD_BIT;
            case LIGHTNING -> 1 << STATUS_LIGHTNING_SHIELD_BIT;
            case BUBBLE -> 1 << STATUS_BUBBLE_SHIELD_BIT;
            default -> 0;
        };
    }

    static ShieldType resolveShieldToRestore(BonusStageProvider.BonusStageRewards rewards, int savedStatusSecondary) {
        if (rewards.fireShield()) {
            return ShieldType.FIRE;
        }
        if (rewards.bubbleShield()) {
            return ShieldType.BUBBLE;
        }
        if (rewards.lightningShield()) {
            return ShieldType.LIGHTNING;
        }
        if (rewards.shield()) {
            return ShieldType.BASIC;
        }

        return savedShieldType(savedStatusSecondary);
    }

    /**
     * ROM {@code SpawnLevelMainSprites_SpawnPowerup} {@code loc_6A02}
     * (docs/skdisasm/sonic3k.asm:8326-8355) tests the saved elemental bits in
     * fire -> lightning -> bubble order and re-gives that shield.
     */
    static ShieldType savedShieldType(int savedStatusSecondary) {
        int savedShieldBits = savedStatusSecondary & SAVED_SHIELD_MASK;
        if ((savedShieldBits & (1 << STATUS_FIRE_SHIELD_BIT)) != 0) {
            return ShieldType.FIRE;
        }
        if ((savedShieldBits & (1 << STATUS_LIGHTNING_SHIELD_BIT)) != 0) {
            return ShieldType.LIGHTNING;
        }
        if ((savedShieldBits & (1 << STATUS_BUBBLE_SHIELD_BIT)) != 0) {
            return ShieldType.BUBBLE;
        }
        return null;
    }

    /**
     * ROM {@code SpawnLevelMainSprites_SpawnPowerup}
     * (docs/skdisasm/sonic3k.asm:8296-8322) runs on every level spawn, and the
     * bonus zones are explicitly routed into its restore arm --
     * {@code cmpi.b #$13,(Current_zone).w / beq loc_69E0} and the same for
     * {@code #$14} (:8302-8305). It re-gives Player 1 the shield saved in
     * {@code Saved_status_secondary}, so the ROM's player keeps its elemental
     * shield for the DURATION of the bonus stage, not only after returning to
     * the level.
     *
     * <p>The engine captured the value at entry
     * ({@link #encodeSavedShieldStatus}) but consumed it only on the way out
     * ({@link #resolveShieldToRestore}), leaving the bonus-stage player
     * shieldless. In Pachinko that removes {@code Test_Ring_Collisions}'
     * {@code Status_LtngShield} arm (:18486-18489), which allocates
     * {@code Obj_Attracted_Ring} and pulls a nearby ring into the player.
     */
    static void applyBonusStageEntryShieldRestore(
            AbstractPlayableSprite playable, int savedStatusSecondary) {
        if (playable == null) {
            return;
        }
        ShieldType shieldType = savedShieldType(savedStatusSecondary);
        if (shieldType != null) {
            playable.giveShield(shieldType);
        }
    }

}
