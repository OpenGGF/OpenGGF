package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.BossChildComponent;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * One link of a Lava Reef miniboss arm: subtypes {@code 2}-{@code $14} of each ring
 * ({@code loc_788DE}, sonic3k.asm:160394-160414).
 *
 * <p>These are not independent orbiters. {@code MoveSprite_CircularSimple}
 * (sonic3k.asm:177668-177684) positions each one relative to {@code parent3(a0)}, which
 * {@code CreateChild8_TreeListRepeated} sets to the <b>previous child in the ring</b>, so the ten
 * links form an articulated chain hanging off the arm segment. Its {@code asr.l d2} with
 * {@code d2 = 4} turns the 16.16 sine into a 16-pixel radius.
 *
 * <p>{@code sub_78BD6} seeds {@code $3C = $80} and {@code $40 = 1}, and {@code loc_788F4} adds
 * {@code $40} to {@code $3C} each frame, negating {@code $40} whenever the sum leaves
 * {@code [$70,$90]}. The chain therefore sways 16 angle units either side of straight down.
 */
final class LrzMinibossOrbiterChild extends AbstractBossChild implements RewindRecreatable, LrzMinibossRingChild {

    /** {@code word_78D66}: mapping frame 8, priority 0. */
    private static final int MAPPING_FRAME = 8;
    private static final int PRIORITY_BUCKET = 0;
    /** {@code sub_78BD6} (sonic3k.asm:160642-160648). */
    private static final int INITIAL_ANGLE = 0x80;
    private static final int INITIAL_ANGLE_STEP = 1;
    /** {@code loc_788F4}'s reflection window. */
    private static final int ANGLE_MIN = 0x70;
    private static final int ANGLE_MAX = 0x90;
    /** {@code moveq #4,d2} into {@code MoveSprite_CircularSimple}: a 16-pixel link. */
    private static final int LINK_RADIUS_SHIFT = 4;

    // Not final: a non-final scalar rides the generic compact-schema rewind capture, where an
    // uncapturable final field would trip the rewind-coverage guard. Both are deterministic
    // anyway - the create loop rebuilds them in the same order every time.
    private boolean mirrored;
    private int childSubtype;
    private int angle = INITIAL_ANGLE;
    private int angleStep = INITIAL_ANGLE_STEP;

    LrzMinibossOrbiterChild(AbstractBossInstance parent, int childSubtype, boolean mirrored) {
        super(parent, "LRZMinibossArmLink", PRIORITY_BUCKET, 0x9D);
        this.mirrored = mirrored;
        this.childSubtype = childSubtype;
    }

    @Override
    public LrzMinibossOrbiterChild recreateForRewind(RewindRecreateContext ctx) {
        return parent == null ? null
                : new LrzMinibossOrbiterChild(parent, childSubtype, mirrored);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!shouldUpdate(vIntRunCount)) {
            return;
        }
        // loc_788F4: step the angle and reflect at the window edges.
        int stepped = (angle + angleStep) & 0xFF;
        if (stepped < ANGLE_MIN || stepped > ANGLE_MAX) {
            angleStep = -angleStep;
            stepped = (angle + angleStep) & 0xFF;
        }
        angle = stepped;
        syncPositionWithParent();
        updateDynamicSpawn();
    }

    /** {@code MoveSprite_CircularSimple} with {@code d2 = 4}, anchored on the previous link. */
    @Override
    public void syncPositionWithParent() {
        int anchorX;
        int anchorY;
        BossChildComponent previousLink = previousLinkInRing();
        if (previousLink != null && !previousLink.isDestroyed()) {
            anchorX = previousLink.getX();
            anchorY = previousLink.getY();
        } else if (parent != null && !parent.isDestroyed()) {
            anchorX = parent.getX();
            anchorY = parent.getY();
        } else {
            return;
        }
        // GetSineCosine returns 8.8 sine and cosine ($100 = 1.0). swap/clr.w lifts each into
        // the whole part of a 16.16 value and asr.l #4 divides by 16, so a full unit is 16
        // pixels: offset = 16 * sin(angle) on X and 16 * cos(angle) on Y.
        double radians = angle * Math.PI / 128.0;
        int radius = 1 << LINK_RADIUS_SHIFT;
        currentX = anchorX + (int) Math.round(Math.sin(radians) * radius);
        currentY = anchorY + (int) Math.round(Math.cos(radians) * radius);
    }

    /**
     * {@code parent3(a1)}: the child created just before this one in the same ring, which the
     * create loop's {@code addq.w #2,d2} makes the one two subtypes lower.
     *
     * <p>Resolved by identity rather than by list position, and on demand rather than stored. By
     * position it would be wrong twice over: the engine prunes destroyed children from the
     * parent's list, so a retiring sibling would silently re-aim every later link, and at the ring
     * boundary that shift would anchor ring two's first link to ring one's hand. The ROM's
     * {@code parent3} is a stored pointer and does neither. On demand, because storing the
     * reference would need an {@code ObjectRefId} sidecar to survive a rewind capture.
     */
    private BossChildComponent previousLinkInRing() {
        if (parent == null) {
            return null;
        }
        for (BossChildComponent sibling : parent.getChildComponents()) {
            if (sibling instanceof LrzMinibossRingChild ringChild
                    && ringChild.ringMirrored() == mirrored
                    && ringChild.ringSubtype() == childSubtype - 2) {
                return sibling;
            }
        }
        return null;
    }

    int getAngle() {
        return angle;
    }

    /** The resolved {@code parent3}, for the ring-identity test. */
    BossChildComponent previousLinkForTest() {
        return previousLinkInRing();
    }

    @Override public int ringSubtype() { return childSubtype; }
    @Override public boolean ringMirrored() { return mirrored; }

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
