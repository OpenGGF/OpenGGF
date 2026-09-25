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
     * capture.
     *
     * <p>Returns {@code null} when the predecessor is gone. A caller must <b>not</b> substitute the
     * boss body for it: the ROM's {@code parent3} is a raw object-slot pointer, and a slot whose
     * object has been deleted is not re-aimed at anything -- least of all at the drill, which sits
     * a screen away from where the arm's end was. Standing still is the closer reading, and it is
     * the one that does not teleport a hand across the arena the frame a link retires.
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

    /** {@code sub_78B46} has not fired: the child is running its own routine. */
    int RETIRE_LIVE = 0;
    /** Parked on {@code Wait_Draw} counting {@code $2E} out towards {@code loc_78B86}. */
    int RETIRE_PARKED = 1;
    /** {@code loc_78B86} has spawned the explosion; {@code $F} frames to {@code Go_Delete_Sprite}. */
    int RETIRE_EXPLODING = 2;

    /** {@code loc_78B86}: {@code move.w #$F,$2E(a0)} before {@code Go_Delete_Sprite}. */
    int RETIRE_EXPLOSION_FRAMES = 0x0F;
    /** {@code sub_78B46}: {@code move.w #$80,priority(a0)}. */
    int RETIRE_PRIORITY = 0x80;

    /**
     * {@code sub_78B46} (sonic3k.asm:160568-160590), which every ring child runs at the tail of
     * its own live routine.
     *
     * <p>It tests one bit of the parent's {@code $38} -- {@code 6} for the unmirrored ring,
     * {@code 7} for the mirrored one ({@code btst #0,render_flags(a0)} picks between
     * {@code moveq #6} and {@code moveq #7}). Two things set those bits: {@code loc_78D2C} when
     * that ring's hand runs out of hits, so killing a hand peels its whole arm away, and
     * {@code loc_78C60} when the drill itself dies, which sets both.
     *
     * @return {@code true} when the parent's retirement bit for this ring is set
     */
    static boolean parentRetiresRing(AbstractBossInstance parent, LrzMinibossRingChild self) {
        if (!(parent instanceof LrzMinibossInstance boss)) {
            return false;
        }
        int bit = self.ringMirrored() ? 7 : 6;
        return (boss.getFlags38() & (1 << bit)) != 0;
    }

    /**
     * {@code sub_78B46}: {@code move.w #$2C,d1 / sub.w subtype*2,d1 / move.w d1,$2E(a0)}. The
     * subtype rises towards the hand, so the wait shortens towards the hand: the arm peels away
     * from its far end first rather than all at once.
     */
    static int retireParkFrames(LrzMinibossRingChild self) {
        return 0x2C - self.ringSubtype() * 2;
    }
}
