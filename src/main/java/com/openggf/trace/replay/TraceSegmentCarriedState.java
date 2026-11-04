package com.openggf.trace.replay;

import java.util.List;

/**
 * Run-level state the ROM carries into a standalone run segment, resolved
 * from recorder observations of the run that produced the segment.
 *
 * <p>A segment replayed on its own cold-boots the engine, so nothing the
 * earlier segments did exists yet; the ROM, in contrast, enters the segment
 * holding the run's rings, emeralds, giant-ring memory and shield. This record
 * is that carried state, and {@link TraceSegmentCarriedStateImport} is the one
 * place it is written into the engine -- once, at segment entry, before the
 * first recorded row is driven. Nothing here is ever re-read or re-applied
 * after entry. See
 * {@code docs/architecture/designs/2026-11-03-segment-carried-run-state.md}.
 *
 * <p>Every field is {@code null} (or empty) when the recording does not
 * establish it; an unestablished value is never defaulted or guessed.
 *
 * @param provenance           run id, segment index and directory that were resolved
 * @param ringCount            {@code Ring_count}: the inbound transition's recorded
 *                             ring count ({@code rings_after} on a return,
 *                             {@code rings_before} into a bonus stage)
 * @param chaosEmeraldCount    {@code Chaos_emerald_count} from the latest recorded
 *                             transition at or before this segment
 * @param emeraldStates        {@code Collected_emeralds_array}, seven entries 0-3,
 *                             folded from the run's recorded special-stage history
 * @param superEmeraldCount    {@code Super_emerald_count} implied by {@code emeraldStates}
 * @param consumedGiantRings   giant rings the run consumed earlier in this zone visit
 *                             ({@code Collected_special_ring_array} sources)
 * @param player1ShieldStatus  {@code status_secondary} shield bits Player 1 holds at
 *                             entry (fire/lightning/bubble), or {@code null}
 * @param specialStageCursor   {@code Current_special_stage} at a special-stage arm
 * @param superEmeraldStageSet {@code SK_special_stage_flag} at a special-stage arm
 * @param lastStarPostHit      {@code Last_star_post_hit} as recorded at the segment's
 *                             load ({@code metadata.last_star_post_hit}); bit 7 marks
 *                             a special-stage return
 * @param restore              the {@code Load_Starpost_Settings} restore the ROM performs
 *                             at this load, from the recorded {@code level_entry_ram}
 *                             saved blocks; {@code null} for a start-table entry or when
 *                             not recorded
 * @param lifeCount            {@code Life_count} from {@code level_entry_ram}
 * @param collectedSpecialRingArray {@code Collected_special_ring_array} from
 *                             {@code level_entry_ram}; replaces the touch derivation
 * @param recordedSpecialStageCursor {@code Current_special_stage} from {@code level_entry_ram}
 * @param disagreements        where a recorded value differs from the value derived
 *                             without it (one of the two is wrong)
 * @param unresolved           carried variables this recording cannot establish,
 *                             each with the reason
 */
public record TraceSegmentCarriedState(
        String provenance,
        Integer ringCount,
        Integer chaosEmeraldCount,
        List<Integer> emeraldStates,
        Integer superEmeraldCount,
        List<ConsumedGiantRing> consumedGiantRings,
        Integer player1ShieldStatus,
        Integer specialStageCursor,
        Boolean superEmeraldStageSet,
        Integer lastStarPostHit,
        LevelEntryRestore restore,
        Integer lifeCount,
        Integer collectedSpecialRingArray,
        Integer recordedSpecialStageCursor,
        List<String> disagreements,
        List<String> unresolved) {

    public TraceSegmentCarriedState {
        emeraldStates = emeraldStates == null ? null : List.copyOf(emeraldStates);
        consumedGiantRings = consumedGiantRings == null
                ? List.of() : List.copyOf(consumedGiantRings);
        disagreements = disagreements == null ? List.of() : List.copyOf(disagreements);
        unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
    }

    /** No run context: a fixture that is not a run segment carries nothing. */
    public static TraceSegmentCarriedState none(String reason) {
        return new TraceSegmentCarriedState(
                null, null, null, null, null, List.of(), null, null, null, null,
                null, null, null, null, List.of(), List.of(reason));
    }

    /**
     * A giant-ring touch ({@code Obj_SSEntryRing} with its {@code Obj_SSEntryFlash})
     * the recorder observed in an earlier segment of this zone visit. {@code SSEntryFlash_GoSS} set bit {@code subtype} of
     * {@code Collected_special_ring_array} for it (loc_618D0, sonic3k.asm:128477-128481),
     * and only a full {@code SaveGame} clears the array again (loc_C4CC,
     * sonic3k.asm:15957-15958).
     */
    public record ConsumedGiantRing(String segmentDir, int x, int y, int subtype,
                                    int touchFrame) {
    }

    /**
     * What {@code Load_Starpost_Settings} restores at this load: the
     * {@code Saved2_*} block when {@code Special_bonus_entry_flag} is set
     * (loc_2D2C2), otherwise the {@code Saved_*} block, whose ring count and
     * extra-life flags survive only with {@code Respawn_table_keep} set
     * (sonic3k.asm:61800-61856).
     *
     * @param block          {@code "Saved"} or {@code "Saved2"}
     * @param timerFrames    {@code Timer} after the restore's {@code Timer_frame = 59},
     *                       {@code Timer_second - 1}, as engine frames
     * @param checkpoint     the star-post checkpoint the {@code Saved_*} block holds
     */
    public record LevelEntryRestore(String block, int ringCount, int extraLifeFlags,
                                    long timerFrames, int cameraMaxY,
                                    int dynamicResizeRoutine, int meanWaterLevel,
                                    boolean waterFullScreen, int topSolidBit, int lrbSolidBit,
                                    Checkpoint checkpoint) {
    }

    /**
     * The {@code Saved_*} star-post block a later death reload restores
     * ({@code Last_star_post_hit} index, position, camera, rings, timer, bounds,
     * path bits).
     */
    public record Checkpoint(int index, int x, int y, int cameraX, int cameraY,
                             int rings, int extraLifeFlags, long timerFrames,
                             int cameraMaxY, int dynamicResizeRoutine,
                             int topSolidBit, int lrbSolidBit) {
    }
}
