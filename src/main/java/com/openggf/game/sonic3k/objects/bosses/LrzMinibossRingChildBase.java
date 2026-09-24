package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;

/**
 * The half of a Lava Reef miniboss ring child that every one of the three kinds shares:
 * {@code sub_78B46} (sonic3k.asm:160568-160590) and the two-step retirement behind it.
 *
 * <p>In the ROM this is literally shared code -- {@code loc_78838} (the arm segment),
 * {@code loc_788F4} (a link) and both of the hand's routines {@code loc_78946}/{@code loc_7897A}
 * all end in a {@code bsr.w}/{@code bra.w} to it -- so it is shared here too, rather than copied
 * into three classes where the three copies could drift.
 *
 * <p>The sequence, once the parent sets this ring's bit:
 *
 * <ol>
 *   <li>{@code sub_78B46} replaces the child's routine with {@code Wait_Draw}, raises
 *       {@code priority} to {@code $80} and loads {@code $2E} with {@code $2C - subtype * 2}.
 *       From here the child no longer moves or animates; it only counts down and draws.</li>
 *   <li>{@code loc_78B86} (sonic3k.asm:160592-160605), reached when {@code $2E} counts past zero,
 *       spawns a {@code Child6_CreateBossExplosion} with subtype {@code 6} and re-arms
 *       {@code $2E} to {@code $F} with {@code Go_Delete_Sprite} as the continuation.</li>
 *   <li>{@code $F} frames later the child deletes itself.</li>
 * </ol>
 *
 * <p>The child keeps drawing throughout: the parked routine is {@code Wait_Draw}, not
 * {@code Obj_Wait}. The one subtlety is the hand, whose idle routine is not drawn at all -- once
 * parked it <i>is</i> drawn, because it is no longer running its own routine.
 */
abstract class LrzMinibossRingChildBase extends AbstractBossChild implements LrzMinibossRingChild {

    /** {@code RETIRE_LIVE}, {@code RETIRE_PARKED} or {@code RETIRE_EXPLODING}. */
    private int retirePhase = RETIRE_LIVE;
    /** {@code $2E(a0)} while parked. */
    private int retireTimer;

    LrzMinibossRingChildBase(AbstractBossInstance parent, String name, int priority, int objectId) {
        super(parent, name, priority, objectId);
    }

    /**
     * Runs the parked side of the retirement.
     *
     * @return {@code true} when this child is retiring and must not run its own routine
     */
    final boolean retirementTick() {
        if (retirePhase == RETIRE_LIVE) {
            return false;
        }
        // Obj_Wait: subq.w #1,$2E(a0) / bmi -> jmp $34(a0). The continuation runs on the frame
        // the timer goes negative, not the frame after.
        retireTimer--;
        if (retireTimer >= 0) {
            return true;
        }
        if (retirePhase == RETIRE_PARKED) {
            retirePhase = RETIRE_EXPLODING;
            retireTimer = RETIRE_EXPLOSION_FRAMES;
            // CreateChild1_Normal over Child6_CreateBossExplosion with subtype 6. The sound is
            // the explosion object's, not this child's (Obj_CreateBossExplosion,
            // sonic3k.asm:176659-176672). Subtype 6 selects its own
            // CreateBossExpParameterIndex row, whose cadence is not modelled here.
            final int burstX = getX();
            final int burstY = getY();
            spawnChild(() -> S3kBossExplosionChild.createWithNativeInitSfx(burstX, burstY));
        } else {
            ObjectLifetimeOps.destroyBossChildLatched(this);                      // Go_Delete_Sprite
        }
        return true;
    }

    /**
     * {@code sub_78B46} itself: call it at the tail of the child's live routine, exactly where the
     * ROM's {@code bsr.w sub_78B46} sits -- after the frame's movement and animation, before the
     * draw.
     */
    final void sub78B46() {
        if (retirePhase != RETIRE_LIVE || !LrzMinibossRingChild.parentRetiresRing(parent, this)) {
            return;
        }
        retirePhase = RETIRE_PARKED;
        retireTimer = LrzMinibossRingChild.retireParkFrames(this);
        // sub_78B46 writes a ROM display-list offset, not a bucket index.
        priority = RenderPriority.fromS3kWord(RETIRE_PRIORITY);
    }
}
