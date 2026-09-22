package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The ring that flashes round a Lava Reef miniboss hand when the hand is hit:
 * {@code ChildObjDat_78D98} -> {@code loc_78A28} (sonic3k.asm:160467-160472), created by
 * {@code sub_78CF4} on the frame the hit lands.
 *
 * <p>{@code loc_78A46} (sonic3k.asm:160474-160484) is the whole of its life: {@code Obj_Wait} over
 * {@code $2E = $1F} towards {@code Go_Delete_Sprite}, then copy {@code parent3}'s {@code x_pos}
 * and {@code y_pos} -- so it rides the hand rather than the frame it was born on -- and then one
 * test: if {@code parent3}'s {@code status} bit 7 is set, the hand has been destroyed and the ring
 * goes with it instead of drawing.
 *
 * <p>{@code parent3} is resolved by ring rather than stored, for the reason
 * {@link LrzMinibossRingChild#predecessorOf} gives: a stored component reference on a boss child
 * would need an {@code ObjectRefId} sidecar to survive a rewind capture, where a boolean does not.
 */
final class LrzMinibossHitSparkChild extends AbstractBossChild implements RewindRecreatable {

    /** {@code word_78D78}: priority 0, {@code $10 $10} size, mapping frame {@code $A}, collision 0. */
    private static final int MAPPING_FRAME = 0x0A;
    private static final int PRIORITY_BUCKET = 0;
    /** {@code loc_78A28}: {@code move.w #$1F,$2E(a0)}. */
    private static final int LIFETIME_FRAMES = 0x1F;

    private boolean mirrored;
    private int waitTimer = LIFETIME_FRAMES;
    /** {@code loc_78A28} runs on the creation frame and returns; {@code loc_78A46} starts after. */
    private boolean setupFrameDone;

    /** Restore construction uses the live concrete boss; snapshot fields restore the phase. */
    private LrzMinibossHitSparkChild(LrzMinibossInstance parent) {
        this(parent, false);
    }

    LrzMinibossHitSparkChild(AbstractBossInstance parent, boolean mirrored) {
        super(parent, "LRZMinibossHitSpark", PRIORITY_BUCKET, 0x9D);
        this.mirrored = mirrored;
        LrzMinibossHandChild hand = handOfRing();
        if (hand != null) {
            this.currentX = hand.getX();
            this.currentY = hand.getY();
        }
        updateDynamicSpawn();
    }

    @Override
    public LrzMinibossHitSparkChild recreateForRewind(RewindRecreateContext ctx) {
        return parent == null ? null : new LrzMinibossHitSparkChild(parent, mirrored);
    }

    /** {@code movea.w parent3(a0),a1}: the hand of this child's own ring. */
    private LrzMinibossHandChild handOfRing() {
        if (parent == null) {
            return null;
        }
        for (var sibling : parent.getChildComponents()) {
            if (sibling instanceof LrzMinibossHandChild hand && hand.ringMirrored() == mirrored) {
                return hand;
            }
        }
        return null;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!shouldUpdate(vIntRunCount)) {
            return;
        }
        if (!setupFrameDone) {
            setupFrameDone = true;
            return;
        }
        // loc_78A46: Obj_Wait first, so $2E counting past zero deletes this frame.
        waitTimer--;
        if (waitTimer < 0) {
            ObjectLifetimeOps.destroyBossChildLatched(this);
            return;
        }
        LrzMinibossHandChild hand = handOfRing();
        if (hand == null || hand.isStatusBit7Set()) {
            // btst #7,status(a1) / bne -> Go_Delete_Sprite. A hand that has already left the
            // child list is the same condition reached one frame later.
            ObjectLifetimeOps.destroyBossChildLatched(this);
            return;
        }
        currentX = hand.getX();
        currentY = hand.getY();
        updateDynamicSpawn();
    }

    boolean isMirrored() {
        return mirrored;
    }

    @Override public void syncPositionWithParent() { /* loc_78A46 copies parent3, not $44 */ }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(MAPPING_FRAME, currentX, currentY, mirrored, false);
    }
}
