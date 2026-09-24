package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * {@code loc_9039A}, the eight pieces {@code ChildObjDat_90626} hangs off
 * {@code Obj_LRZRockCrusher} (sonic3k.asm:197378-197508, ROM {@code $9039A}).
 *
 * <p>{@code CreateChild1_Normal} gives child {@code n} the subtype {@code 2n} and the offset pair
 * from the table: {@code (-$1C,$1C)}, {@code ($1C,$1C)}, {@code (-$24,$1C)}, {@code ($24,$1C)} for
 * the lower four and the same X offsets with {@code -$24} for the upper four (:197446-197463).
 * {@code loc_903CC} then runs {@code SetUp_ObjAttributes3} from {@code word_90614}
 * ({@code priority $200}, a {@code $C x $14} box, {@code mapping_frame} 1, {@code collision_flags}
 * {@code $8B}), and the pieces whose subtype is {@code $8} or more -- the upper row -- take
 * {@code mapping_frame} 2 and {@code collision_flags} {@code $12} instead (:197484-197490).
 *
 * <p>The shake is {@code loc_90436} (:197486-197508). {@code $40(a0)} is a per-frame delta added
 * to {@code child_dy}, {@code $2E(a0)} its remaining frames, and {@code byte_904AC} holds the four
 * {@code (frames, delta)} pairs {@code (1,8)}, {@code (3,-4)}, {@code (3,-4)}, {@code (7,2)}. The
 * pair is chosen by {@code (subtype & 8) >> 1} plus {@code 2} on every other visit
 * ({@code bchg #2,$38(a0)}), so the upper and lower rows read different halves of the table and
 * each row alternates between its two pairs.
 *
 * <p>{@code $39(a0)} counts two shake bursts and {@code loc_90490} then returns the piece to
 * routine 4 with {@code $2E = (subtype & 8) >> 1 + 4} (:197509-197516).
 */
public final class LrzRockCrusherPieceInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable, RewindRecreatable {

    /** {@code word_90614}: {@code dc.w $200} (sonic3k.asm:197435). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0200);
    /** {@code dc.b $C,$14}: width and height (:197436). */
    private static final int HALF_WIDTH = 0x0C;
    private static final int HALF_HEIGHT = 0x14;
    /** {@code dc.b 1,$8B}: the lower row's frame and collision (:197436). */
    private static final int LOWER_FRAME = 1;
    private static final int LOWER_COLLISION_FLAGS = 0x8B;
    /** {@code move.b #2,mapping_frame / move.b #$12,collision_flags} (:197488-197489). */
    private static final int UPPER_FRAME = 2;
    private static final int UPPER_COLLISION_FLAGS = 0x12;
    /** {@code byte_904AC} (sonic3k.asm:197303-197307): four {@code (frames, delta)} pairs. */
    private static final int[][] SHAKE_TABLE = {{1, 8}, {3, -4}, {3, -4}, {7, 2}};
    /** {@code move.b #2,$39(a0)} (:197517). */
    private static final int SHAKE_BURSTS = 2;

    /** ROM {@code subtype(a0)} = the child index times two. */
    private int subtype;
    /** sub_905A8's $20 timer; Touch_Enemy clears collision until it expires. */
    private int hitFlashTimer;
    private boolean hitCollisionDisabled;
    /** ROM {@code child_dx(a0)} / {@code child_dy(a0)}. */
    private int childDx;
    private int childDy;
    /** ROM {@code mapping_frame(a0)} and {@code collision_flags(a0)} as {@code loc_903CC} set them. */
    private int mappingFrame;
    private int collisionFlags;
    /** ROM {@code $2E(a0)}: the shake pair's remaining frames, and the routine-4 wait. */
    private int timer;
    /** ROM {@code $39(a0)}: shake bursts left. */
    private int bursts;
    /** ROM {@code $40(a0)}: the per-frame {@code child_dy} delta. */
    private int delta;
    /** ROM {@code $38(a0)} bit 2: which half of each row's table pair is next. */
    private boolean alternate;
    /** ROM {@code routine(a0)}: 2 = wait for the parent, 4 = countdown, 6 = shake, 8 = done. */
    private int routine = 2;
    /** ROM {@code $46(a0)}: the parent this piece reads through {@code Refresh_ChildPosition}. */
    @RewindTransient(reason = "parent3 link restored by ObjectRefId in restoreRewindState")
    private LrzRockCrusherObjectInstance parent;

    private record ParentLink(ObjectRefId parentId) implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    LrzRockCrusherPieceInstance(int subtype, int childDx, int childDy) {
        super(new ObjectSpawn(0, 0, Sonic3kObjectIds.SPIKER, subtype, 0, false, 0),
                "LRZRockCrusherPiece");
        this.subtype = subtype & 0xFF;
        this.childDx = (byte) childDx;
        this.childDy = (byte) childDy;
        boolean upper = this.subtype >= 8;
        this.mappingFrame = upper ? UPPER_FRAME : LOWER_FRAME;
        this.collisionFlags = upper ? UPPER_COLLISION_FLAGS : LOWER_COLLISION_FLAGS;
        // andi.b #4,d0 / move.b d0,$2E(a0) (:197491-197492).
        this.timer = this.subtype & 4;
    }

    void attachTo(LrzRockCrusherObjectInstance owner) {
        this.parent = owner;
    }

    @Override
    public LrzRockCrusherPieceInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzRockCrusherPieceInstance(ctx.spawn().subtype(), 0, 0));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        switch (routine) {
            // loc_903F4 (:197494-197499): wait for the parent's $38 bit 2.
            case 2 -> {
                if (parent != null && parent.piecesReleased()) {
                    routine = 4;
                    // loc_903F4 falls directly into loc_90408; it does not
                    // wait for another object pass to decrement the delay.
                    advanceReleaseCountdown();
                }
            }
            case 4 -> advanceReleaseCountdown();
            case 6 -> shake();
            default -> {
                // loc_904B4 (:197310): position only.
            }
        }
        refreshPosition();
        if (hitCollisionDisabled) {
            hitFlashTimer = LrzRockCrusherObjectInstance.advanceHitFlash(services(), hitFlashTimer);
            if (hitFlashTimer == 0) hitCollisionDisabled = false;
        }
    }

    /** loc_90408, including its fallthrough to loc_90426/loc_90436. */
    private void advanceReleaseCountdown() {
        timer = (byte) (timer - 1);
        if (timer < 0) {
            routine = 6;
            bursts = SHAKE_BURSTS;
            delta = 0;
            shake();
        } else if (parent != null && parent.piecesFinished()) {
            routine = 8;
        }
    }

    /** {@code loc_90436} (sonic3k.asm:197486-197508). */
    private void shake() {
        // move.b $40(a0),d0 / add.b d0,$43(a0): the delta lands on child_dy, a BYTE.
        childDy = (byte) (childDy + delta);
        timer = (byte) (timer - 1);
        if (timer >= 0) {
            return;
        }
        bursts--;
        if (bursts < 0) {
            // loc_90490 (:197509-197515).
            routine = 4;
            timer = ((subtype & 8) >> 1) + 4;
            return;
        }
        // lsr.w #1,d3 / bchg #2,$38(a0) / beq -> d1 = 0 when the bit WAS clear, 2 when it was
        // set (:197502-197506). d3 is then a BYTE offset into byte_904AC, whose entries are
        // (frames, delta) PAIRS, so the pair index is d3 / 2 and never leaves 0..3.
        int rowByteOffset = (subtype & 8) >> 1;
        boolean wasSet = alternate;
        alternate = !alternate;
        int byteOffset = rowByteOffset + (wasSet ? 2 : 0);
        int[] pair = SHAKE_TABLE[byteOffset / 2];
        timer = pair[0];
        delta = pair[1];
    }

    /** {@code Refresh_ChildPosition} (sonic3k.asm:197281-197294). */
    private void refreshPosition() {
        if (parent == null) {
            return;
        }
        updateDynamicSpawn((parent.getCentreX() + childDx) & 0xFFFF,
                (parent.getCentreY() + childDy) & 0xFFFF);
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
                    : (LrzRockCrusherObjectInstance) context.requireIdentityTable()
                            .resolveObject(link.parentId(), true);
        }
    }

    /** ROM {@code child_dy(a0)}. */
    public int childDy() {
        return childDy;
    }

    /** ROM {@code $2E(a0)}. */
    public int timer() {
        return timer;
    }

    /** ROM {@code $40(a0)}. */
    public int delta() {
        return delta;
    }

    /** ROM {@code routine(a0)}. */
    public int routine() {
        return routine;
    }

    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return getSpawn().y() & 0xFFFF;
    }

    @Override
    public int getCollisionFlags() {
        return hitCollisionDisabled ? 0 : collisionFlags;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        // Touch_Enemy saves/clears collision_flags before sub_905A8 sees the hit.
        // The shared touch controller owns the player's rebound. A second hit
        // must not restart this object's native lockout.
        if (subtype >= 8 && !hitCollisionDisabled) hitCollisionDisabled = true;
    }

    @Override
    public int getCollisionProperty() {
        // move.b #-1,collision_property(a0) for the upper row (:197385-197386): the ROM's
        // "cannot be hurt" marker, the same value the parent carries.
        return subtype >= 8 ? -1 : 0;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // The child copies art_tile from the parent (CreateChild1_Normal, :196933).
        return parent != null && parent.isHighPriority();
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_HEIGHT;
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
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_ROCK_CRUSHER);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
