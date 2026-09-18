package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.objects.boss.BossChildComponent;

/**
 * A child of one of the Lava Reef miniboss's two twelve-object rings.
 *
 * <p>Exists so a link can find its {@code parent3} the way the ROM stores it. The create loop sets
 * {@code parent3(a1)} to the previously created child (sonic3k.asm:177188-177191:
 * {@code move.w a3,parent3(a1)} with {@code movea.l a1,a3} at the end of each iteration), and that
 * is a stored pointer: deleting a sibling does not re-aim anyone. Looking the predecessor up by
 * position in the parent's child list would, because the engine prunes destroyed children from
 * that list, and at a ring boundary the shift would anchor ring two's first link to ring one's
 * hand. Identity by (ring, subtype) is stable under pruning and is what the ROM actually means.
 *
 * <p>The anchor is read as a <b>16.16</b> pair, because {@code MoveSprite_CircularSimple}
 * (sonic3k.asm:178433-178438) does {@code move.l x_pos(a1),d2} -- {@code x_pos} and {@code x_sub}
 * as one longword -- and writes the sum back the same way. Ten links chain off one another, so
 * dropping the sub-pixel half would let a truncation error accumulate down the arm.
 */
interface LrzMinibossRingChild {

    /** The child's ROM {@code subtype}: {@code 0, 2, ... $16} within its ring. */
    int ringSubtype();

    /** True for the second ring, the one {@code loc_787FE} gives {@code render_flags} bit 0. */
    boolean ringMirrored();

    /** {@code x_pos:x_sub} as the single 16.16 value {@code move.l x_pos(a1),d2} reads. */
    int ringXFixed();

    /** {@code y_pos:y_sub} as the single 16.16 value {@code move.l y_pos(a1),d3} reads. */
    int ringYFixed();

    /**
     * {@code parent3(a0)}: the child created just before this one in the same ring, which the
     * create loop's {@code addq.w #2,d2} makes the one two subtypes lower. Resolved by identity
     * rather than by list position -- see the class comment -- and on demand rather than stored,
     * because storing the reference would need an {@code ObjectRefId} sidecar to survive a rewind
     * capture. Returns {@code null} when the predecessor has been retired, which is the safe
     * reading: the caller falls back to the boss body rather than to whatever slid into the slot.
     */
    static BossChildComponent predecessorOf(AbstractBossInstance parent, LrzMinibossRingChild self) {
        if (parent == null) {
            return null;
        }
        for (BossChildComponent sibling : parent.getChildComponents()) {
            if (sibling instanceof LrzMinibossRingChild ringChild
                    && ringChild.ringMirrored() == self.ringMirrored()
                    && ringChild.ringSubtype() == self.ringSubtype() - 2) {
                return sibling;
            }
        }
        return null;
    }
}
