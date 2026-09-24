package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * One of the seven puffs {@code ChildObjDat_90048} hangs off a Toxomister cloud
 * ({@code loc_8FE8E}, sonic3k.asm from ROM {@code $8FE8E}).
 *
 * <p>{@code loc_8FE8E} runs {@code SetUp_ObjAttributes3} from {@code word_9003A} (priority 0, an
 * {@code 8 x 8} box, {@code mapping_frame} 2, no collision), then inverts the child index:
 * {@code subi.b #$C,d0 / neg.b d0} turns the table's {@code 0, 2, 4, 6, 8, $A, $C} into
 * {@code $C, $A, 8, 6, 4, 2, 0}. {@code lsl.b #2,d0 / move.b d0,$2F(a0)} writes that times four
 * into the LOW BYTE of the word {@code Obj_Wait} counts down, so the puffs open in sequence, the
 * last-listed one first -- the same low-byte-of-a-word trick {@code $18}'s trigger distance uses.
 *
 * <p>{@code loc_8FEDC} then follows the parent through {@code Refresh_ChildPosition} and flickers:
 * it draws only when {@code (V_int_run_count+3)} bit 0 differs from the inverted index's bit 0
 * ({@code sne d0 / btst #0,subtype / not.b d0}), so alternate puffs are visible on alternate
 * frames and the cloud reads as churning.
 *
 * <p>When the parent raises {@code status} bit 7 the puff disperses ({@code loc_8FF12}): if the
 * parent also raised {@code $38} bit 2 -- the spindash escape -- {@code loc_90002} gives it an
 * outward {@code x_vel} from {@code word_90020} ({@code $100, $180, $200, $180, $100, $200, $180}),
 * signed by the BODY's facing, and {@code loc_8FF42} then subtracts {@code $10} from
 * {@code y_vel} every frame so the puffs rise away.
 */
public final class ToxomisterPuffInstance extends AbstractObjectInstance implements RewindRecreatable {

    /** {@code word_9003A}: {@code dc.w 0} (sonic3k.asm, {@code $9003A}). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0000);
    /** {@code dc.b 8,8,2,0}. */
    private static final int HALF_SIZE = 8;
    private static final int MAPPING_FRAME = 2;
    /** {@code word_90020} (sonic3k.asm, {@code $90020}), indexed by the INVERTED child index. */
    private static final int[] DISPERSE_X_VEL = {0x100, 0x180, 0x200, 0x180, 0x100, 0x200, 0x180};
    /** {@code addi.w #-$10,y_vel(a0)} (loc_8FF42). */
    private static final int RISE_ACCELERATION = -0x10;

    /** ROM {@code subtype(a0)} AFTER {@code subi.b #$C / neg.b}: {@code $C} down to {@code 0}. */
    private int invertedIndex;
    /** ROM {@code child_dx(a0)} / {@code child_dy(a0)}. */
    private int childDx;
    private int childDy;
    /** ROM {@code $2E(a0)}, seeded through its low byte with {@code invertedIndex * 4}. */
    private int openDelay;
    /** True once {@code loc_8FED4} has installed {@code loc_8FEDC}. */
    private boolean open;
    /** True once the parent's {@code status} bit 7 sent it to {@code loc_8FF12}. */
    private boolean dispersing;
    private final SubpixelMotion.State motion;
    /** ROM {@code parent3(a0)}: the cloud this puff belongs to. */
    @RewindTransient(reason = "parent3 link restored by ObjectRefId in restoreRewindState")
    private ToxomisterCloudInstance parent;

    private record ParentLink(ObjectRefId parentId) implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    ToxomisterPuffInstance(int childIndex, int childDx, int childDy) {
        super(new ObjectSpawn(0, 0, 0, childIndex, 0, false, 0), "ToxomisterPuff");
        // subi.b #$C,d0 / neg.b d0 (loc_8FE8E).
        this.invertedIndex = (byte) -(childIndex - 0x0C) & 0xFF;
        this.childDx = (byte) childDx;
        this.childDy = (byte) childDy;
        // lsl.b #2,d0 / move.b d0,$2F(a0): the LOW byte of the $2E word.
        this.openDelay = (this.invertedIndex << 2) & 0xFF;
        this.motion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);
    }

    void attachTo(ToxomisterCloudInstance cloud) {
        this.parent = cloud;
    }

    @Override
    public ToxomisterPuffInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new ToxomisterPuffInstance(ctx.spawn().subtype(), 0, 0));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (dispersing) {
            // loc_8FF42: addi.w #-$10,y_vel then MoveSprite2.
            motion.yVel = (short) (motion.yVel + RISE_ACCELERATION);
            SubpixelMotion.moveSprite2(motion);
            updateDynamicSpawn(motion.x, motion.y);
            if (!isOnScreen()) {
                ObjectLifetimeOps.expireDynamic(this);
            }
            return;
        }
        if (parent == null) {
            return;
        }
        if (parent.puffsDispersing()) {
            if (!open) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            beginDisperse();
            return;
        }
        if (!open) {
            // loc_8FEC8 is Obj_Wait plus Child_CheckParent (sonic3k.asm:177281 area), and
            // Child_CheckParent DELETES on the cloud's status bit 7 rather than dispersing: a
            // puff that has not opened yet simply goes away. Only loc_8FEDC, installed by the
            // $34 callback, takes the loc_8FF12 dispersal.
            openDelay--;
            if (openDelay < 0) {
                open = true;
            }
            follow();
            return;
        }
        follow();
    }

    /** {@code loc_8FF12} and {@code loc_90002} (sonic3k.asm, {@code $8FF12}, {@code $90002}). */
    private void beginDisperse() {
        dispersing = true;
        motion.x = parent.getCentreX();
        motion.y = parent.getCentreY();
        motion.xVel = 0;
        motion.yVel = 0;
        if (!parent.escapedBySpindash()) {
            return;
        }
        int magnitude = DISPERSE_X_VEL[invertedIndex % DISPERSE_X_VEL.length];
        // btst #0,render_flags(a2) on the BODY: clear means the value is negated.
        motion.xVel = parent.bodyFacingRight() ? magnitude : -magnitude;
    }

    /** {@code Refresh_ChildPosition} (sonic3k.asm:177281-177294). */
    private void follow() {
        motion.x = (parent.getCentreX() + childDx) & 0xFFFF;
        motion.y = (parent.getCentreY() + childDy) & 0xFFFF;
        updateDynamicSpawn(motion.x, motion.y);
    }

    /**
     * {@code btst #0,(V_int_run_count+3).w / sne d0 / btst #0,subtype(a0) / not.b d0} and the
     * {@code tst.b d0 / bne} that skips the draw (loc_8FEDC).
     */
    private boolean visibleThisFrame(int vIntRunCount) {
        boolean clockBit = (vIntRunCount & 1) != 0;
        boolean indexBit = (invertedIndex & 1) != 0;
        return clockBit == indexBit;
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId id = context.identityTable().map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new ParentLink(id));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof ParentLink link) {
            parent = link.parentId() == null ? null
                    : (ToxomisterCloudInstance) context.requireIdentityTable()
                            .resolveObject(link.parentId(), true);
        }
    }

    /** ROM {@code subtype(a0)} after the inversion. */
    public int invertedIndex() {
        return invertedIndex;
    }

    /** ROM {@code $2E(a0)}'s low byte as {@code loc_8FE8E} seeds it. */
    public int openDelay() {
        return openDelay;
    }

    /** True once the puff has left {@code loc_8FEC8}'s wait. */
    public boolean open() {
        return open;
    }

    /** ROM {@code x_vel(a0)}. */
    public int xVel() {
        return (short) motion.xVel;
    }

    public int getCentreX() {
        return motion.x & 0xFFFF;
    }

    public int getCentreY() {
        return motion.y & 0xFFFF;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // The child copies the body's art_tile, make_art_tile(ArtTile_Toxomister,1,1).
        return true;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_SIZE;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_SIZE;
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return false;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!open && !dispersing) {
            return;
        }
        if (!dispersing && !visibleThisFrame(currentVIntRunCountOrZero())) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.TOXOMISTER);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(MAPPING_FRAME, getX(), getY(), false, false);
    }

    private int currentVIntRunCountOrZero() {
        try {
            return services().levelManager() != null
                    ? services().levelManager().getFrameCounter()
                    : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
