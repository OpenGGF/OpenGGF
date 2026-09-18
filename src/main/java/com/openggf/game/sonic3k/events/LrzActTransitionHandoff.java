package com.openggf.game.sonic3k.events;

import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.level.Pattern;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.SeamlessTransitionResourceHandoff;
import com.openggf.level.resources.DeferredLevelResourceManifest;
import com.openggf.sprites.NativePositionOps;

/**
 * The act-2 side of {@code loc_56CAA} (sonic3k.asm:115347-115374), applied after the target
 * {@code Load_Level} owner has run.
 *
 * <p>{@code loc_56CAA} does the whole change on one frame, and the parts that are not the reload
 * itself land here:
 *
 * <ul>
 *   <li>{@code sub.w d0,(Player_1+x_pos)} / {@code (Player_2+x_pos)} with {@code d0 = $2C00}.
 *       The camera, its bounds and every live object take the same offset through the transition
 *       request; the two players are written here because the target's own init has already
 *       placed them by the time this runs.</li>
 *   <li>{@code jsr (Clear_Switches)} clears {@code $20} bytes from {@code Level_trigger_array},
 *       and {@code sonic3k.constants.asm:699-700} puts {@code Anim_Counters ds.b $10} directly
 *       after {@code Level_trigger_array ds.b $10} -- so the animated-tile phase counters go with
 *       the triggers. The trigger half is explicit here; the counters are the reloaded act's own
 *       fresh {@link LrzZoneRuntimeState}.</li>
 *   <li>{@code clr.b (LRZ_rocks_routine)} and {@code clr.w (Events_routine_bg)}: the rock-sprite
 *       walk restarts and the background machine returns to stage 0, which is now LRZ2's.</li>
 * </ul>
 *
 * <p>The record carries the offset rather than reading it back from the request so that a rewind
 * restore replays the same arithmetic.
 */
record LrzActTransitionHandoff(int playerDeltaX, byte[] act2SecondaryArt, int artTile,
        Sonic3kLRZEvents eventAccess) implements SeamlessTransitionResourceHandoff {

    @Override
    public DeferredLevelResourceManifest deferredResources() {
        return DeferredLevelResourceManifest.EMPTY;
    }

    @Override
    public void transferAfterTargetInit() {
        LrzZoneRuntimeState state =
                S3kRuntimeStates.currentLrz(eventAccess.zoneRuntimeRegistry()).orElseThrow();
        if (state.actIndex() != 1) {
            throw new IllegalStateException("LRZ transition did not install Act 2");
        }
        // clr.b (LRZ_rocks_routine) / clr.w (Events_routine_bg): stage 0 is LRZ2's from here.
        state.setRocksRoutine(0);
        state.setRocksWindow(0, 0);
        state.setBackgroundRoutine(0);
        state.setEventsFg5(0);
        state.setAct2ArtJobOrdinal(-1);
        // Clear_Switches (sonic3k.asm:104284-104291): $20 bytes, the trigger array first.
        Sonic3kLevelTriggerManager.reset();

        applySecondaryArt();

        var main = eventAccess.spriteManager().getMainPlayable();
        NativePositionOps.writeXPosPreserveSubpixel(main,
                (main.getCentreX() + playerDeltaX) & 0xFFFF);
        for (var follower : eventAccess.spriteManager().getSidekicks()) {
            NativePositionOps.writeXPosPreserveSubpixel(follower,
                    (follower.getCentreX() + playerDeltaX) & 0xFFFF);
        }
    }

    /**
     * {@code Queue_Kos_Module(ArtKosM_LRZ2_Secondary)} at {@code tiles_to_bytes($090)}: the ROM's
     * queue uploads it before the swap and {@code Load_Level} never touches art, so the act-2
     * secondary patterns are in VRAM the moment the act changes. The engine applies them here,
     * after the target's own art load, for the same end state.
     */
    private void applySecondaryArt() {
        if (act2SecondaryArt == null || act2SecondaryArt.length == 0) {
            return;
        }
        byte[] art = act2SecondaryArt;
        int firstTile = artTile;
        eventAccess.zoneLayoutMutationPipeline().queue(context -> {
            MutationEffects effects = MutationEffects.redrawAllTilemaps();
            for (int offset = 0; offset < art.length; offset += PATTERN_BYTES) {
                Pattern pattern = new Pattern();
                pattern.fromSegaFormat(java.util.Arrays.copyOfRange(art, offset, offset + PATTERN_BYTES));
                context.surface().setPattern(firstTile + offset / PATTERN_BYTES, pattern);
            }
            Sonic3kPlcLoader.refreshAffectedRenderers(
                    java.util.List.of(new Sonic3kPlcLoader.TileRange(
                            firstTile, art.length / PATTERN_BYTES)),
                    eventAccess.levelManager());
            return effects;
        });
    }

    private static final int PATTERN_BYTES = 32;
}
