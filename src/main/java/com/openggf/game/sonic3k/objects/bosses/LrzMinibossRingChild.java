package com.openggf.game.sonic3k.objects.bosses;

/**
 * A child of one of the Lava Reef miniboss's two twelve-object rings.
 *
 * <p>Exists so a link can find its {@code parent3} the way the ROM stores it. The create loop sets
 * {@code parent3(a1)} to the previously created child (sonic3k.asm:177181-177203), and that is a
 * stored pointer: deleting a sibling does not re-aim anyone. Looking the predecessor up by
 * position in the parent's child list would, because the engine prunes destroyed children from
 * that list, and at a ring boundary the shift would anchor ring two's first link to ring one's
 * hand. Identity by (ring, subtype) is stable under pruning and is what the ROM actually means.
 */
interface LrzMinibossRingChild {

    /** The child's ROM {@code subtype}: {@code 0, 2, ... $16} within its ring. */
    int ringSubtype();

    /** True for the second ring, the one {@code loc_787FE} gives {@code render_flags} bit 0. */
    boolean ringMirrored();
}
