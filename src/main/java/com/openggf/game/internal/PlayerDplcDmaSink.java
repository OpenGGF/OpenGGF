package com.openggf.game.internal;

import com.openggf.level.render.SpriteDplcFrame;

/**
 * Internal, non-Mod-API receiver for {@code Add_To_DMA_Queue} work that player-art DPLC routines submit during the
 * object pass. A game-owned runtime-art coordinator implements it when it records V-int DMA work; shared sprite code
 * reports the ROM routine's own inputs and decides nothing.
 */
public interface PlayerDplcDmaSink {
    /** One {@code Tails_Tail_Load_PLC} call: the tail object's {@code mapping_frame} and that frame's DPLC. */
    void tailsTailLoadPlc(int mappingFrame, SpriteDplcFrame dplc);

    /**
     * One body DPLC call for {@code player} after its animation pass: the player's {@code mapping_frame} and the DPLC
     * of the art it is drawn with. The game selects the ROM routine for the character.
     */
    void playerLoadPlc(com.openggf.sprites.playable.AbstractPlayableSprite player, int mappingFrame,
                       SpriteDplcFrame dplc);

    /**
     * The player's own routine skipped its body DPLC call because an object owns the mapping frame
     * ({@code object_control} bit 1); that owner may have written the routine's previous-frame byte.
     */
    void playerLoadPlcOwnedByObject(com.openggf.sprites.playable.AbstractPlayableSprite player);
}
