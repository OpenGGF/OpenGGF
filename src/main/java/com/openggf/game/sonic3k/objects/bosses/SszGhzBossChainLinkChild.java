package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * One link of {@code ChildObjDat_7A684}, the Green Hill recreation's ball and chain
 * (sonic3k.asm:162747-162861, {@code loc_7A428} / {@code loc_7A4D0} / {@code loc_7A514}).
 *
 * <p>{@code CreateChild9_TreeList} builds six of these with subtypes 0, 2, 4, 6, 8 and {@code $A}.
 * It is a <em>tree</em> list, so each link's {@code parent3} is the link before it and only the
 * first link's is the boss; every link's {@code $44} is the boss. That is what makes the chain
 * articulate: a link orbits the link in front of it, not the ship.
 *
 * <p>Three behaviours share one drop phase. {@code sub_7A634} gives each link its own drop length
 * from {@code word_7A642} ({@code $C, $14, $1C, $24, $2C, $3C} by subtype), and {@code loc_7A460}
 * moves it two pixels down a frame for that many frames, so the chain pays out rather than
 * appearing at full reach. The link that finishes last — subtype {@code $A}, the ball — is the one
 * that sets {@code $38} bit 2 on the boss, which is what releases the boss from its routine 6.
 *
 * <p>After the drop:
 * <ul>
 *   <li>Subtype 0 ({@code loc_7A482}, {@code loc_7A496}) waits for that same bit 2 and then owns
 *       the swing. Its {@code $3C} angle steps by {@code $3A}, and when {@code ($3C - $40)} falls
 *       below {@code $80} unsigned the step is negated — a pendulum between angles {@code $40} and
 *       {@code $BF} the long way round through zero. At the end of the sweep that lies in the
 *       boss's direction of travel it sets the boss's {@code $38} bit 3, which is what turns the
 *       ship around. Which end that is depends on {@code sls}'s {@code $3C <= $40} answer and on
 *       the boss's X-flip, which the ROM combines with a {@code not.b}.</li>
 *   <li>Subtypes 2-8 ({@code loc_7A4EC}, {@code loc_7A500}) copy their parent link's {@code $3C}
 *       and orbit it through {@code MoveSprite_CircularSimple} with {@code $3A = 4} — a
 *       {@code $100} sine shifted right four, so a 16-pixel arm.</li>
 *   <li>Subtype {@code $A} ({@code loc_7A544}) is the same with {@code $3A = 3}, a 32-pixel arm,
 *       plus {@code collision_flags $8F} and a mapping frame that alternates 0/1 on
 *       {@code V_int_run_count+3} bit 0.</li>
 * </ul>
 */
public final class SszGhzBossChainLinkChild extends AbstractObjectInstance
        implements RewindRecreatable {

    /** {@code word_7A642}: the per-subtype drop length, indexed by {@code subtype}. */
    static final int[] DROP_FRAMES = {0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x3C};
    /** {@code move.b #$1A,child_dy(a0)} in {@code loc_7A446}. */
    static final int ROOT_CHILD_DY = 0x1A;
    /** {@code addq.w #2,y_pos(a0)}. */
    private static final int DROP_STEP = 2;
    /** {@code move.b #1,$3A(a0)}: the root's angle step. */
    private static final int ROOT_ANGLE_STEP = 1;
    /** {@code move.b #4,$3A(a0)} / {@code move.b #3,$3A(a0)}: the orbit shifts. */
    private static final int MIDDLE_ORBIT_SHIFT = 4;
    static final int BALL_ORBIT_SHIFT = 3;
    /** {@code subi.b #$40,d0} and {@code cmpi.b #-$80,d0}. */
    private static final int SWING_BIAS = 0x40;
    private static final int SWING_HALF = 0x80;
    /** {@code ObjDat3_7A660} / {@code _7A66C} / {@code _7A678}. */
    private static final int LINK_PRIORITY = RenderPriority.fromS3kWord(0x300);
    private static final int BALL_PRIORITY = RenderPriority.fromS3kWord(0x280);
    /** All three rows use {@code make_art_tile(ArtTile_SSZGHZMisc,1,0)} in the shipped ROM. */
    private static final int PALETTE_LINE = 1;
    private static final int ROOT_FRAME = 2;
    private static final int MIDDLE_FRAME = 3;

    /** {@code routine(a0)} values, as the three dispatch tables index them. */
    private static final int ROUTINE_DROP = 2;
    private static final int ROUTINE_SETTLED = 4;
    private static final int ROUTINE_SWINGING = 6;

    private SszGhzBossObjectInstance boss;
    private SszGhzBossChainLinkChild chainParent;
    private int subtype;

    private int routine;
    /** {@code $2E(a0)}. */
    private int dropRemaining;
    /** {@code $3A(a0)}: the angle step for the root, the orbit shift for the rest. */
    private int step;
    /** {@code $3C(a0)}: the shared angle byte. */
    private int angle;
    private int x;
    private int y;
    /** {@code (V_int_run_count+3)} bit 0, as the ball's draw reads it. */
    private int lastVIntRunCount;

    public SszGhzBossChainLinkChild(ObjectSpawn spawn, SszGhzBossObjectInstance boss,
            SszGhzBossChainLinkChild chainParent) {
        super(spawn, "SSZGHZBossChainLink");
        this.boss = boss;
        this.chainParent = chainParent;
        this.subtype = spawn.subtype() & 0xFF;
        this.x = spawn.x();
        this.y = spawn.y();
        // SetUp_ObjAttributes in each routine-0 entry bumps routine to 2, and sub_7A634 fills $2E.
        this.routine = ROUTINE_DROP;
        this.dropRemaining = DROP_FRAMES[Math.min(subtype / 2, DROP_FRAMES.length - 1)];
        this.step = isRoot() ? ROOT_ANGLE_STEP
                : isBall() ? BALL_ORBIT_SHIFT : MIDDLE_ORBIT_SHIFT;
    }

    /** Probe/rewind constructor: the spawn alone, with the links re-resolved on restore. */
    public SszGhzBossChainLinkChild(ObjectSpawn spawn) {
        this(spawn, null, null);
    }

    @Override
    public SszGhzBossChainLinkChild recreateForRewind(RewindRecreateContext ctx) {
        return new SszGhzBossChainLinkChild(ctx.spawn());
    }

    private record RewindExtra(ObjectRefId bossId, ObjectRefId chainParentId, int subtype,
                               int routine, int dropRemaining, int step, int angle, int x, int y,
                               int lastVIntRunCount)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId bossId = context.identityTable()
                .map(table -> table.encodeObject(boss)).orElse(null);
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(chainParent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                bossId, parentId, subtype, routine, dropRemaining, step, angle, x, y,
                lastVIntRunCount));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            subtype = extra.subtype();
            routine = extra.routine();
            dropRemaining = extra.dropRemaining();
            step = extra.step();
            angle = extra.angle();
            x = extra.x();
            y = extra.y();
            lastVIntRunCount = extra.lastVIntRunCount();
            boss = extra.bossId() == null ? null
                    : (SszGhzBossObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.bossId(), true);
            chainParent = extra.chainParentId() == null ? null
                    : (SszGhzBossChainLinkChild) context.requireIdentityTable()
                    .resolveObject(extra.chainParentId(), true);
        }
    }

    boolean isRoot() { return subtype == 0; }

    boolean isBall() { return subtype == 0x0A; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        lastVIntRunCount = vIntRunCount;
        if (boss == null || boss.isDestroyed()) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        switch (routine) {
            case ROUTINE_DROP -> updateDrop();
            case ROUTINE_SETTLED -> updateSettled();
            case ROUTINE_SWINGING -> updateSwing();
            default -> { }
        }
    }

    /** {@code loc_7A460}, shared by all three dispatch tables. */
    private void updateDrop() {
        y = (y + DROP_STEP) & 0xFFFF;
        dropRemaining--;
        if (dropRemaining >= 0) {
            return;
        }
        routine += 2;
        // cmpi.b #$A,subtype(a0): only the ball tells the boss the chain is out.
        if (isBall()) {
            boss.setChainReady();
        }
    }

    /** {@code loc_7A482} for the root; {@code loc_7A500} for every other link. */
    private void updateSettled() {
        if (isRoot()) {
            if (boss.isChainReady()) {
                routine = ROUTINE_SWINGING;
            }
            return;
        }
        orbitParent();
    }

    /** {@code loc_7A496}: the root drives the angle and turns the ship around. */
    private void updateSwing() {
        int biased = (angle - SWING_BIAS) & 0xFF;
        // sls d1: set when the subtraction was Low or Same, i.e. angle <= $40.
        boolean lowOrSame = (angle & 0xFF) <= SWING_BIAS;
        if (Integer.compareUnsigned(biased, SWING_HALF) < 0) {
            step = -step;
            boolean reachedTravelEnd = boss.isRenderFlippedForTest() ? lowOrSame : !lowOrSame;
            if (reachedTravelEnd) {
                boss.setBallReachedFarSide();
            }
        }
        angle = (angle + step) & 0xFF;
        // Refresh_ChildPosition: child_dx 0, child_dy $1A from the boss.
        x = boss.getX() & 0xFFFF;
        y = (boss.getY() + ROOT_CHILD_DY) & 0xFFFF;
    }

    /** {@code loc_7A500} plus {@code MoveSprite_CircularSimple}. */
    private void orbitParent() {
        if (chainParent == null) {
            return;
        }
        angle = chainParent.angle;
        // swap / clr.w / asr.l d2: sine into X, cosine into Y, both $100-scaled.
        int offsetX = (TrigLookupTable.sinHex(angle) << 16) >> step;
        int offsetY = (TrigLookupTable.cosHex(angle) << 16) >> step;
        x = (((chainParent.x << 16) + offsetX) >> 16) & 0xFFFF;
        y = (((chainParent.y << 16) + offsetY) >> 16) & 0xFFFF;
    }

    /** {@code $3C(a0)}, for the tests and for the links behind this one. */
    public int angleForTest() { return angle; }

    public int routineForTest() { return routine; }

    public int subtypeForTest() { return subtype; }


    /** No {@code Obj_WaitOffscreen} in this chain either: the parent's escape takes it off screen. */
    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 8; }

    @Override
    public int getPriorityBucket() {
        return isBall() ? BALL_PRIORITY : LINK_PRIORITY;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_GHZ_BOSS_MISC);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame(), x, y, false, false, PALETTE_LINE);
    }

    /** {@code loc_7A514}'s two lines: the ball alternates 0/1 on {@code V_int_run_count+3} bit 0. */
    private int mappingFrame() {
        if (isRoot()) {
            return ROOT_FRAME;
        }
        if (!isBall()) {
            return MIDDLE_FRAME;
        }
        // btst #0,(V_int_run_count+3).w: the counter's low byte, which update() carries in.
        return (lastVIntRunCount & 1) != 0 ? 1 : 0;
    }
}
