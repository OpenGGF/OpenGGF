package com.openggf.game.sonic3k;

import com.openggf.game.NativeGameStateOps;
import com.openggf.game.save.SaveReason;
import com.openggf.game.session.WorldSession;
import com.openggf.level.objects.ObjectServices;

/** Runtime semantics of the ROM's full SaveGame, distinct from persistence-only requests. */
public final class S3kFullSaveGame {
    /**
     * Marks the synchronous capture of a request made by {@link #complete}. Only SaveGame
     * stores {@code SaveGame_NextLevel[Current_zone_and_act]} (sonic3k.asm:15896-15908);
     * the engine's persistence-only progression requests keep the current location.
     */
    private static final ScopedValue<Boolean> SAVE_GAME = ScopedValue.newInstance();

    private S3kFullSaveGame() { }

    /**
     * SaveGame's common return loc_C4CC clears Collected_special_ring_array
     * (sonic3k.asm:15958), including SK-alone and zero Save_pointer branches.
     * Special-stage/lives saves use separate returns and must not call this.
     * Native clears after SRAM writes; Java requests asynchronous persistence
     * which captures this cleared mask. Clear before that request so No Save and
     * missing persistence contexts retain the same runtime behavior.
     */
    public static void complete(ObjectServices services) {
        // SaveGame / loc_C4B4 (sonic3k.asm:15946-15958) writes the capped
        // lives byte to both the slot and live state.
        if (hasNativeSaveSlot(services.worldSession())) {
            var state = services.gameState();
            NativeGameStateOps.restoreLives(state, Math.min(state.getLives() & 0xFF, 99));
        }
        NativeGameStateOps.clearSpecialRingCollection(services.gameState());
        ScopedValue.where(SAVE_GAME, Boolean.TRUE)
                .run(() -> services.requestSessionSave(SaveReason.PROGRESSION_SAVE));
    }

    /** SaveGame, SaveGame_SpecialStage and SaveGame_LivesContinues share these gates. */
    public static boolean hasNativeSaveSlot(WorldSession world) {
        if (world == null) return false;
        var save = world.getSaveSessionContext();
        return save != null && save.activeSlot().isPresent()
                && !Sonic3kCartridgePolicy.isStandaloneSk(world.getDataSource().rom().orElse(null));
    }

    /** True while the save provider captures the request made by {@link #complete}. */
    public static boolean isCapturingSaveGame() {
        return SAVE_GAME.isBound();
    }
}
