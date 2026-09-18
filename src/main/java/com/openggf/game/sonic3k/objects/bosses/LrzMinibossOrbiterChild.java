package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.boss.BossChildComponent;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * One link of a Lava Reef miniboss arm: subtypes {@code 2}-{@code $14} of each ring
 * ({@code loc_788DE}, sonic3k.asm:160351-160357).
 *
 * <p>These are not independent orbiters. {@code MoveSprite_CircularSimple}
 * (sonic3k.asm:178424-178441) positions each one relative to {@code parent3(a0)}, which
 * {@code CreateChild8_TreeListRepeated} sets to the <b>previous child in the ring</b>, so the ten
 * links form an articulated chain hanging off the arm segment. Its {@code asr.l d2} with
 * {@code d2 = 4} turns the 16.8 sine into a 16-pixel radius, kept in 16.16 so the chain does not
 * shorten by a truncated pixel per joint.
 *
 * <p>{@code sub_78BD6} (sonic3k.asm:160629-160637) seeds {@code $3C = $80} and {@code $40 = 1} and
 * parks the link on {@code Wait_Draw} for the {@code $2E} that {@code loc_7880A} gave it, so the
 * two arms unroll link by link instead of snapping out together. {@code loc_788F4} then adds
 * {@code $40} to {@code $3C} each frame, flipping {@code $40} whenever the sum leaves
 * {@code [$70,$90]} -- but <b>storing the sum either way</b>, so the sway is {@code $6F}..{@code $91}.
 */
final class LrzMinibossOrbiterChild extends LrzMinibossRingChildBase implements RewindRecreatable {

    /** {@code word_78D66}: mapping frame 8, priority 0. */
    private static final int MAPPING_FRAME = 8;
    private static final int PRIORITY_BUCKET = 0;
    /** {@code sub_78BD6} (sonic3k.asm:160629-160637). */
    private static final int INITIAL_ANGLE = 0x80;
    private static final int INITIAL_ANGLE_STEP = 1;
    /** {@code loc_788F4}'s reflection window: {@code cmpi.b #$70 / blo} then {@code cmpi.b #-$70 / bls}. */
    private static final int ANGLE_MIN = 0x70;
    private static final int ANGLE_MAX = 0x90;
    /** {@code moveq #4,d2} into {@code MoveSprite_CircularSimple}: a 16-pixel link. */
    private static final int LINK_RADIUS_SHIFT = 4;
    /** {@code loc_787FE}'s {@code move.w #$10,$2E(a0)}, which only the mirrored ring runs. */
    private static final int MIRRORED_STAGGER_BASE = 0x10;

    // Not final: a non-final scalar rides the generic compact-schema rewind capture, where an
    // uncapturable final field would trip the rewind-coverage guard. Both are deterministic
    // anyway - the create loop rebuilds them in the same order every time.
    private boolean mirrored;
    private int childSubtype;
    private int angle = INITIAL_ANGLE;
    private int angleStep = INITIAL_ANGLE_STEP;
    /** {@code $2E(a0)} while the link is still parked on {@code Wait_Draw}. */
    private int waitTimer;
    /** loc_788DE runs on the creation frame and returns; the wait starts the frame after. */
    private boolean setupFrameDone;
    /** False until {@code Obj_Wait} has run {@code $34(a0)}, i.e. until {@code loc_788F4} is live. */
    private boolean staggerElapsed;
    private int xFixed;
    private int yFixed;

    LrzMinibossOrbiterChild(AbstractBossInstance parent, int childSubtype, boolean mirrored) {
        super(parent, "LRZMinibossArmLink", PRIORITY_BUCKET, 0x9D);
        this.mirrored = mirrored;
        this.childSubtype = childSubtype;
        // loc_787FE gives the mirrored ring $2E = $10, then loc_7880A's `add.w d1,$2E(a0)` with
        // d1 = subtype * 2 staggers each link along the arm (sonic3k.asm:160258-160260,
        // 160262-160285).
        this.waitTimer = (mirrored ? MIRRORED_STAGGER_BASE : 0) + childSubtype * 2;
        this.xFixed = currentX << 16;
        this.yFixed = currentY << 16;
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
        if (retirementTick()) {
            updateDynamicSpawn();
            return;
        }
        if (!setupFrameDone) {
            // loc_788DE: SetUp_ObjAttributes3, sub_78BD6, rts. The link does no work on the frame
            // CreateChild8_TreeListRepeated allocated it; sub_78BD6's seeds are in the constructor.
            setupFrameDone = true;
            return;
        }
        if (!staggerElapsed) {
            // Wait_Draw -> Obj_Wait (sonic3k.asm:177949-177958): subq.w #1,$2E / bmi -> $34(a0),
            // which is the `move.l #loc_788F4,(a0)` after the bsr. So loc_788F4 first runs on the
            // frame AFTER the counter goes negative, not on it.
            waitTimer--;
            if (waitTimer < 0) {
                staggerElapsed = true;
            }
            return;
        }
        // loc_788F4 (sonic3k.asm:160359-160376). The stepped value is written back at loc_7890C
        // unconditionally; only $40 is negated when it leaves the window, so the angle does reach
        // $6F and $91 before turning round.
        int stepped = (angle + angleStep) & 0xFF;
        if (stepped < ANGLE_MIN || stepped > ANGLE_MAX) {
            angleStep = -angleStep;
        }
        angle = stepped;
        syncPositionWithParent();
        // loc_788F4 ends bsr.w sub_78B46 / jmp Draw_Sprite.
        sub78B46();
        updateDynamicSpawn();
    }

    /** {@code MoveSprite_CircularSimple} with {@code d2 = 4}, anchored on the previous link. */
    @Override
    public void syncPositionWithParent() {
        int anchorXFixed;
        int anchorYFixed;
        BossChildComponent previousLink = LrzMinibossRingChild.predecessorOf(parent, this);
        if (previousLink instanceof LrzMinibossRingChild ring && !previousLink.isDestroyed()) {
            anchorXFixed = ring.ringXFixed();
            anchorYFixed = ring.ringYFixed();
        } else if (parent instanceof LrzMinibossInstance boss && !boss.isDestroyed()) {
            anchorXFixed = boss.getState().xFixed;
            anchorYFixed = boss.getState().yFixed;
        } else {
            return;
        }
        // GetSineCosine (sonic3k.asm:3021-3033) returns sine in d0 and cosine in d1, both x $100.
        // swap/clr.w lifts each into the whole half of a 16.16 longword and asr.l #4 divides by
        // 16, so one unit of the 8.8 sine is a 1/16 pixel and a full unit is 16 pixels. The
        // anchor is read and the sum written as longwords, so the sub-pixel half carries.
        int sine = TrigLookupTable.sinHex(angle);
        int cosine = TrigLookupTable.cosHex(angle);
        xFixed = anchorXFixed + (sine << (16 - LINK_RADIUS_SHIFT));
        yFixed = anchorYFixed + (cosine << (16 - LINK_RADIUS_SHIFT));
        currentX = xFixed >> 16;
        currentY = yFixed >> 16;
    }

    int getAngle() {
        return angle;
    }

    /** {@code $2E(a0)}: frames still to wait before {@code loc_788F4} takes over. */
    int getStaggerRemaining() {
        return staggerElapsed ? -1 : waitTimer;
    }

    boolean isStaggerElapsed() {
        return staggerElapsed;
    }

    /** The resolved {@code parent3}, for the ring-identity test. */
    BossChildComponent previousLinkForTest() {
        return LrzMinibossRingChild.predecessorOf(parent, this);
    }

    @Override public int ringSubtype() { return childSubtype; }
    @Override public boolean ringMirrored() { return mirrored; }
    @Override public int ringXFixed() { return xFixed; }
    @Override public int ringYFixed() { return yFixed; }

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
