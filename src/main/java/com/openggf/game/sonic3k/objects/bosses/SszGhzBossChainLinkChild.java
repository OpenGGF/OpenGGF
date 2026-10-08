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
import com.openggf.level.objects.TouchResponseProvider;
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
 *       the swing. Its {@code $3C} angle steps by {@code $3A}, and the reversal test
 *       ({@code subi.b #$40,d0 / cmpi.b #-$80,d0 / bhs}) negates the step on every frame whose
 *       {@code ($3C - $40) & $FF} is below {@code $80} — that is, on every angle in
 *       {@code [$40,$BF]}. Walking it from zero shows what that produces: the step flips at
 *       {@code $40} (landing on {@code $3F}) and at {@code $BF} (landing on {@code $C0}), so the
 *       sweep runs {@code $40 -> 0 -> $BF} the long way round through zero and the interior
 *       {@code [$41,$BE]} is never visited at all. At the end of the sweep that lies in the
 *       boss's direction of travel it sets the boss's {@code $38} bit 3, which is what turns the
 *       ship around. {@code sls} sets
 *       {@code d1} when the subtract was Low-or-Same, i.e. {@code $3C <= $40}, and the boss's
 *       X-flip inverts that through {@code not.b}: unflipped, bit 3 comes from the {@code $BF}
 *       end; flipped, from the {@code $40} end.</li>
 *   <li>Subtypes 2-8 ({@code loc_7A4EC}, {@code loc_7A500}) copy their parent link's {@code $3C}
 *       and orbit it through {@code MoveSprite_CircularSimple} with {@code $3A = 4} — a
 *       {@code $100} sine shifted right four, so a 16-pixel arm.</li>
 *   <li>Subtype {@code $A} ({@code loc_7A544}) is the same with {@code $3A = 3}, a 32-pixel arm,
 *       plus {@code collision_flags $8F} and a mapping frame that alternates 0/1 on
 *       {@code V_int_run_count+3} bit 0.</li>
 * </ul>
 *
 * <p><b>The defeat breaks the chain up.</b> Both dispatchers end in a
 * {@code Child_*_Sprite_FlickerMove} that first tests {@code btst #7,status(a1)} on
 * {@code parent3}. That bit is not set by anything in {@code Obj_SSZGHZBoss}: the shared touch
 * response sets it, in {@code Touch_Enemy}'s {@code .checkhurtenemy} —
 * {@code subq.b #1,boss_hitcount2(a1) / bne.s .bossnotdefeated / bset #7,status(a1)}
 * (sonic3k.asm:20922) — on the killing hit. When the test passes, {@code loc_849D8} runs
 * {@code bset #7,status(a0)}, installs {@code Obj_FlickerMove}, <em>clears</em>
 * {@code collision_flags} and calls {@code Set_IndexedVelocity} with {@code d0 = 0}, which reads
 * {@code Obj_VelocityIndex + subtype*2}. Each link's {@code parent3} is the link in front of it
 * and {@code CreateChild9_TreeList} allocates them into ascending slots in that same order, so a
 * single object pass walks the whole chain — the root reads the ship's bit and sets its own, and
 * every later link finds its parent's already up. All six convert on the killing-hit frame, not
 * one a frame: the {@code s3k-sonic-tails-complete-emeralds} {@code hpz} segment's aux rows put
 * all six on {@code Obj_FlickerMove} at frame 4412, the same frame the ship takes
 * {@code Wait_FadeToLevelMusic}. The ball stops being able to hurt anyone on that frame, not when
 * the ship finally leaves.
 *
 * <p><b>The ball is the fight's only threat.</b> {@code ObjDat3_7A678}'s last byte is
 * {@code $8F} — category HURT, size index {@code $F} — and only {@code loc_7A514}, the ball's
 * dispatcher, ends {@code Child_DrawTouch_Sprite_FlickerMove}; the root and the four middle links
 * end {@code Child_Draw_Sprite_FlickerMove}, which has no
 * {@code Add_SpriteToCollisionResponseList}. Neither routine flickers per frame: "FlickerMove" is
 * the name of what they do once the parent's {@code status} bit 7 goes up, which happens only at
 * the killing hit (above), so the ball's hitbox is live on every frame of the swing until then.
 */
public final class SszGhzBossChainLinkChild extends AbstractObjectInstance
        implements RewindRecreatable, TouchResponseProvider {

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
    // loc_57BB2 roaming clouds use bucket 0 and intentionally precede this
    // bucket-5 ball in the ROM SAT. Cloud overlap is not a missing art priority bit.
    private static final int BALL_PRIORITY = RenderPriority.fromS3kWord(0x280);
    /** All three rows use {@code make_art_tile(ArtTile_SSZGHZMisc,1,0)} in the shipped ROM. */
    private static final int PALETTE_LINE = 1;
    /** {@code ObjDat3_7A678}: {@code dc.b 8,8,0,$8F} — the ball's {@code collision_flags}. */
    static final int BALL_COLLISION_FLAGS = 0x8F;
    private static final int ROOT_FRAME = 2;
    private static final int MIDDLE_FRAME = 3;

    /** {@code routine(a0)} values, as the three dispatch tables index them. */
    private static final int ROUTINE_DROP = 2;
    private static final int ROUTINE_SETTLED = 4;
    private static final int ROUTINE_SWINGING = 6;
    /** Not a {@code routine} value: {@code loc_849D8} replaces the code pointer entirely. */
    private static final int ROUTINE_FLICKER_MOVE = -1;
    /** {@code addi.w #$38,y_vel(a0)} in {@code MoveSprite}. */
    private static final int FLICKER_GRAVITY = 0x38;
    /**
     * {@code Obj_VelocityIndex} with {@code d0 = 0} and {@code d1 = subtype*2}, so the byte
     * displacement is the subtype itself doubled and each link gets its own pair. Subtypes 0, 2,
     * 4, 6, 8 and {@code $A} select entries 0 through 5.
     */
    private static final int[][] SCATTER_VELOCITY = {
            {-0x100, -0x100}, {0x100, -0x100}, {-0x200, -0x200},
            {0x200, -0x200}, {-0x300, -0x200}, {0x300, -0x200},
    };
    /** {@code cmpi.w #$280,d0} / {@code cmpi.w #$200,d0} in {@code Obj_FlickerMove}. */
    private static final int FLICKER_X_LIMIT = 0x280;
    private static final int FLICKER_Y_LIMIT = 0x200;

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
    /**
     * {@code x_pos}/{@code y_pos} <em>and</em> their sub-pixel words, as 16.16.
     * {@code MoveSprite_CircularSimple} is {@code move.l x_pos(a1),d2} / {@code move.l d2,x_pos(a0)}
     * throughout, so each link reads its parent's fraction and passes its own on down the tree. A
     * whole-pixel chain drops that fraction five times over and puts the ball up to three pixels
     * from where the cartridge does.
     */
    private int x;
    private int y;
    /** {@code (V_int_run_count+3)} bit 0, as the ball's draw reads it. */
    private int lastVIntRunCount;
    /** {@code x_vel}/{@code y_vel}, 8.8, once {@code Set_IndexedVelocity} has filled them. */
    private int xVel;
    private int yVel;
    /** {@code $38} bit 6, which {@code Obj_FlickerMove} toggles to skip every other draw. */
    private boolean flickerDrawn;

    public SszGhzBossChainLinkChild(ObjectSpawn spawn, SszGhzBossObjectInstance boss,
            SszGhzBossChainLinkChild chainParent) {
        super(spawn, "SSZGHZBossChainLink");
        this.boss = boss;
        this.chainParent = chainParent;
        this.subtype = spawn.subtype() & 0xFF;
        this.x = spawn.x() << 16;
        this.y = spawn.y() << 16;
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
                               int lastVIntRunCount, int xVel, int yVel,
                               boolean flickerDrawn)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId bossId = context.identityTable()
                .map(table -> table.encodeObject(boss)).orElse(null);
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(chainParent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                bossId, parentId, subtype, routine, dropRemaining, step, angle, x, y,
                lastVIntRunCount, xVel, yVel, flickerDrawn));
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
            xVel = extra.xVel();
            yVel = extra.yVel();
            flickerDrawn = extra.flickerDrawn();
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
        if (routine == ROUTINE_FLICKER_MOVE) {
            updateFlickerMove();
            return;
        }
        if (boss == null || boss.isDestroyed()) {
            if (boss != null) {
                boss.releaseChainLink(this);
            }
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        switch (routine) {
            case ROUTINE_DROP -> updateDrop();
            case ROUTINE_SETTLED -> updateSettled();
            case ROUTINE_SWINGING -> updateSwing();
            default -> { }
        }
        // The dispatchers fall into Child_Draw[Touch]_Sprite_FlickerMove, whose first test is
        // btst #7,status(a1) on parent3 — the link in front of this one, or the ship for the
        // root. loc_849D8 is what that test reaches.
        if (parentStatusBit7()) {
            enterFlickerMove();
        }
    }

    /** {@code parent3}: the previous link, or the ship for the root. */
    private boolean parentStatusBit7() {
        return chainParent != null ? chainParent.routine == ROUTINE_FLICKER_MOVE
                : boss != null && boss.hasStatusBit7();
    }

    /** {@code loc_849D8}. */
    private void enterFlickerMove() {
        routine = ROUTINE_FLICKER_MOVE;
        // Obj_FlickerMove never reads parent3 again. Drop Java graph ownership before
        // an earlier link is culled, while keeping this slot's routine/status visible.
        boss.releaseChainLink(this);
        boss = null;
        chainParent = null;
        int[] velocity = SCATTER_VELOCITY[Math.min(subtype / 2, SCATTER_VELOCITY.length - 1)];
        // btst #0,render_flags(a0) / neg.w x_vel: these links are never X-flipped.
        xVel = velocity[0];
        yVel = velocity[1];
        flickerDrawn = false;
    }

    /** {@code Obj_FlickerMove}: {@code MoveSprite} with gravity, an alternating draw, and a cull. */
    private void updateFlickerMove() {
        // move.w x_vel(a0),d0 / ext.l / lsl.l #8 / add.l d0,x_pos(a0): 8.8 into 16.16.
        x += xVel << 8;
        y += yVel << 8;
        // addi.w #$38,y_vel(a0) happens after the old y_vel has been applied.
        yVel += FLICKER_GRAVITY;
        flickerDrawn = !flickerDrawn;
        // The cull is an unsigned compare of the object against the camera, and the X half reads
        // Camera_X_pos_coarse_back — the camera's X rounded down to $80 and held a frame behind
        // the live value for the plane fill. The engine has no equivalent, so the live camera X
        // rounded the same way stands in: at a scatter velocity of $100-$300 a frame the two can
        // only disagree about which frame a link off the edge of a $280-wide window disappears on.
        var camera = services().camera();
        int dx = ((getX() & 0xFF80) - (camera.getX() & 0xFF80)) & 0xFFFF;
        int dy = (getY() - camera.getY() + 0x80) & 0xFFFF;
        if (dx > FLICKER_X_LIMIT || dy > FLICKER_Y_LIMIT) {
            // Go_Delete_Sprite_3.
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    /** {@code loc_7A460}, shared by all three dispatch tables. */
    private void updateDrop() {
        // addq.w #2,y_pos(a0): a word add on the pixel half, so the fraction is untouched.
        y = ((y + (DROP_STEP << 16)) & 0xFFFF0000) | (y & 0xFFFF);
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
        // Refresh_ChildPosition: child_dx 0, child_dy $1A from the boss. It is move.w into
        // x_pos/y_pos, so the root's own sub-pixel words keep whatever they held — zero, from
        // the allocation — and the chain's fractions are all made below this link.
        x = ((boss.getX() & 0xFFFF) << 16) | (x & 0xFFFF);
        y = (((boss.getY() + ROOT_CHILD_DY) & 0xFFFF) << 16) | (y & 0xFFFF);
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
        // move.l x_pos(a1),d2 / add.l / move.l d2,x_pos(a0): the parent's fraction rides along.
        x = chainParent.x + offsetX;
        y = chainParent.y + offsetY;
    }

    /**
     * Only {@code loc_7A514} reaches {@code Child_DrawTouch_Sprite_FlickerMove}, and only
     * {@code ObjDat3_7A678} carries a collision byte, so every link but the ball is inert.
     */
    @Override
    public int getCollisionFlags() {
        // loc_849D8's clr.b collision_flags(a0): a scattered link cannot hurt anyone.
        return isBall() && routine != ROUTINE_FLICKER_MOVE ? BALL_COLLISION_FLAGS : 0;
    }

    /** No {@code collision_property}: the ball hurts, it is not something that takes hits. */
    @Override
    public int getCollisionProperty() {
        return 0;
    }

    /** {@code $3C(a0)}, for the tests and for the links behind this one. */
    public int angleForTest() { return angle; }

    public int routineForTest() { return routine; }

    public int subtypeForTest() { return subtype; }

    /** {@code $3A(a0)}: the root's signed angle step, every other link's orbit shift. */
    public int stepForTest() { return step; }


    /** No {@code Obj_WaitOffscreen} in this chain either: the parent's escape takes it off screen. */
    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override public int getX() { return (x >> 16) & 0xFFFF; }
    @Override public int getY() { return (y >> 16) & 0xFFFF; }

    /** The 16.16 words, for a test that has to see the fraction the pixel getters drop. */
    public int xFixedForTest() { return x; }

    public int yFixedForTest() { return y; }

    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 8; }

    @Override
    public int getPriorityBucket() {
        return isBall() ? BALL_PRIORITY : LINK_PRIORITY;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // bchg #6,$38(a0) / beq -> return: Obj_FlickerMove draws on alternate frames only.
        if (routine == ROUTINE_FLICKER_MOVE && !flickerDrawn) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_GHZ_BOSS_MISC);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame(), getX(), getY(), false, false, PALETTE_LINE);
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
