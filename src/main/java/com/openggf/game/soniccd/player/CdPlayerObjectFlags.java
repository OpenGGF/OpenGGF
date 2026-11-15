package com.openggf.game.soniccd.player;

import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Sonic CD player {@code oFlags} bits that no shared status boolean models (the shared status byte is built from
 * facing, air, roll, on-object, roll-jump, push and underwater, bits 0-6).
 *
 * <p><b>Bit 7</b> of the player's object flags ({@code obj.flags}, {@code oFlags}): the act 3 bosses set it when their
 * hit kills the player, and test it afterwards. Wacky Workbench: {@code BossHurtPlayer} sets it when the hit leaves the
 * death animation ($18) playing ({@code bset #7,obj.flags(a1)}, objects/r6/boss.asm:1366-1381) and the defeat and the
 * conductors test it (:666, :1599). Stardust Speedway sets it just before {@code KillPlayer} (objects/r7/boss.asm:1259).
 * A boss calls {@link #setBit7(AbstractPlayableSprite)}; the CD trace replay ORs {@link #statusBits} into the status
 * byte it compares.
 *
 * <p>Native clears it with the player object: the level start clears object RAM on every stage load ({@code .ClearObjects},
 * Level/Main.asm:186-192), which is also the death restart ({@code ObjSonic_Restart} sets {@code levelRestart} and the
 * stage reloads; Level/_Objects/Sonic/Main.asm:2453-2459), so
 * {@link CdPlayerStage#resetForLevel} clears it with the rest of the stage word. The bit lives in the top bit of the movement manager's stage control
 * word, which is rewind-captured; zone profiles that use the word keep that bit ({@code WwzPlayerProfile}).
 */
public final class CdPlayerObjectFlags {
    /** The stage control word's bit reserved for {@code oFlags} bit 7. */
    public static final int BIT7_IN_STAGE_WORD = 1 << 31;

    private CdPlayerObjectFlags() { }

    /** {@code bset #7,oFlags} on the player. */
    public static void setBit7(AbstractPlayableSprite player) {
        PlayableSpriteMovement movement = movement(player);
        if (movement != null) movement.setStageControlState(movement.stageControlState() | BIT7_IN_STAGE_WORD);
    }

    /** {@code btst #7,oFlags} on the player. */
    public static boolean bit7(AbstractPlayableSprite player) {
        PlayableSpriteMovement movement = movement(player);
        return movement != null && (movement.stageControlState() & BIT7_IN_STAGE_WORD) != 0;
    }

    /** The status-byte bits this class holds, as native {@code oFlags} carries them. */
    public static int statusBits(AbstractPlayableSprite player) {
        return bit7(player) ? 0x80 : 0;
    }

    private static PlayableSpriteMovement movement(AbstractPlayableSprite player) {
        return player != null && player.getMovementManager() instanceof PlayableSpriteMovement movement ? movement : null;
    }
}
