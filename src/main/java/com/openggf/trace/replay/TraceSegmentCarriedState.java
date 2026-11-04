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
        List<String> unresolved) {

    public TraceSegmentCarriedState {
        emeraldStates = emeraldStates == null ? null : List.copyOf(emeraldStates);
        consumedGiantRings = consumedGiantRings == null
                ? List.of() : List.copyOf(consumedGiantRings);
        unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
    }

    /** No run context: a fixture that is not a run segment carries nothing. */
    public static TraceSegmentCarriedState none(String reason) {
        return new TraceSegmentCarriedState(
                null, null, null, null, null, List.of(), null, null, null, null,
                List.of(reason));
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
}
