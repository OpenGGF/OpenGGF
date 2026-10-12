package com.openggf.game.sonic3k;

import com.openggf.game.NativeGameStateOps;
import com.openggf.game.save.SaveReason;
import com.openggf.level.objects.ObjectServices;

/** Runtime semantics of the ROM's full SaveGame, distinct from persistence-only requests. */
public final class S3kFullSaveGame {
    private S3kFullSaveGame() { }

    /**
     * SaveGame's common return loc_C4CC clears Collected_special_ring_array
     * (sonic3k.asm:15922), including SK-alone and zero Save_pointer branches.
     * Special-stage/lives saves use separate returns and must not call this.
     * Native clears after SRAM writes; Java requests asynchronous persistence
     * whose payload omits this mask. Clear before that request so No Save and
     * missing persistence contexts retain the same runtime behavior.
     */
    public static void complete(ObjectServices services) {
        NativeGameStateOps.clearSpecialRingCollection(services.gameState());
        services.requestSessionSave(SaveReason.PROGRESSION_SAVE);
    }
}
