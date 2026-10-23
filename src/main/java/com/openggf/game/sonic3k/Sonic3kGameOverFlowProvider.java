package com.openggf.game.sonic3k;

import com.openggf.game.GameOverFlowProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kGameOverCardObjectInstance;
import com.openggf.level.objects.AbstractGameOverCardObjectInstance;
import com.openggf.level.objects.ObjectServices;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Sonic 3&amp;K {@code loc_12432} / {@code loc_12498} (docs/skdisasm/sonic3k.asm:24628-24656):
 * <pre>
 *   move.l  #Obj_GameOver,(Reserved_object_3).w     ; frame 0 (GAME) / 2 (TIME)
 *   move.l  #Obj_GameOver,(Dynamic_object_RAM).w    ; frame 1 / 3 (OVER)
 *   ...
 *   move.w  #mus_GameOver,d0 ; Play_Music
 *   moveq   #3,d0            ; Load_PLC_2
 * </pre>
 * {@code Load_PLC_2 #3} decompresses ArtNem_GameOver over the shield tiles.
 * The independently decoded rendering sheet remains resident, while the event
 * owns the native queue submission that gates object initialization.
 */
public final class Sonic3kGameOverFlowProvider implements GameOverFlowProvider {

    @Override
    public void beginGameOverCard(ObjectServices services, boolean timeOver) {
        Sonic3kGameOverPlcService plc = services.gameService(Sonic3kGameOverPlcService.class);
        if (plc == null) {
            throw new IllegalStateException("S3K GAME OVER requires its native PLC service");
        }
        AbstractGameOverCardObjectInstance.spawnPair(
                services,
                timeOver,
                S3kGameOverCardObjectInstance::new,
                Sonic3kConstants.SST_SLOT_GAME_OVER_WORD,
                Sonic3kConstants.SST_SLOT_GAME_OVER_OVER);
        services.playMusic(Sonic3kMusic.GAME_OVER.id);
        try {
            plc.beginGameOver();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to submit S3K GAME OVER PLC3", e);
        }
    }
}
