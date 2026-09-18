package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.PoweredScreenAttackable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM object {@code Obj_Toxomister} -- object id {@code $9B} in the {@code SKL} pointer set
 * (sonic3k.asm, ROM {@code $8FD48}; {@code Map_Toxomister} at ROM {@code $9008E}). The
 * {@code S3KL} set spends the same id on {@code Obj_Bubbles}. Lava Reef places 22 in act 1 and 9
 * in act 2.
 *
 * <p>The body itself barely moves. {@code Obj_WaitOffscreen} heads the routine; the init sets
 * {@code ObjDat_Toxomister} ({@code make_art_tile(ArtTile_Toxomister,1,1)}, so the priority bit is
 * set; {@code priority $280}, an {@code 8 x 8} box, {@code mapping_frame} 1,
 * {@code collision_flags $18}), raises {@code render_flags} bit 6 and gives itself one child
 * sprite at {@code y +- $18} -- the sign taken from {@code render_flags} bit 1, the placement's
 * Y flip -- and then runs {@code sub_8FF72}.
 *
 * <p>{@code sub_8FF72} plays {@code sfx_EnemyBreath}, creates the cloud through
 * {@code ChildObjDat_90040} at {@code (-$C, 8)} and keeps its address in {@code $44(a0)}.
 * {@code loc_8FD76} then does one thing: watch that address. The moment the cloud raises
 * {@code status} bit 7 -- any of its three endings -- the body waits {@code $1F} frames
 * ({@code loc_8FDA0}) and breathes another ({@code loc_8FDB0}).
 *
 * <p>{@code sub_8FF5A} turns the body to face the nearer player every eighth frame of
 * {@code (V_int_run_count+3)}, through {@code Find_SonicTails} and {@code Change_FlipX}. That is
 * the clock the ROM reads here, not the level counter.
 */
public final class ToxomisterBadnikInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable, PoweredScreenAttackable,
        RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code ObjDat_Toxomister}: {@code dc.w $280}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code dc.b 8,8,1,$18}. */
    private static final int HALF_SIZE = 8;
    private static final int MAPPING_FRAME = 1;
    private static final int COLLISION_FLAGS = 0x18;
    /** {@code moveq #$18,d1}, negated when {@code render_flags} bit 1 is set. */
    private static final int CHILD_SPRITE_OFFSET_Y = 0x18;
    /** {@code ChildObjDat_90040}: {@code dc.b -$C,8}. */
    private static final int CLOUD_OFFSET_X = -0x0C;
    private static final int CLOUD_OFFSET_Y = 8;
    /** {@code move.w #$1F,$2E(a0)} (loc_8FD76). */
    private static final int REBREATH_DELAY = 0x1F;
    /** {@code andi.b #7,d0} on {@code (V_int_run_count+3)} (sub_8FF5A). */
    private static final int FACING_PERIOD_MASK = 7;

    /** ROM {@code render_flags(a0)} bit 7 as {@code Obj_WaitOffscreen} reads it. */
    private boolean awake;
    /** ROM {@code render_flags(a0)} bit 0: {@code Change_FlipX} sets it toward the player. */
    private boolean facingRight;
    /** ROM {@code $2E(a0)} while {@code loc_8FDA0} waits to breathe again. */
    private int rebreathTimer = -1;
    /** ROM {@code sub2_y_pos(a0)}: the body's own second sprite. */
    private int childSpriteY;
    /** ROM {@code $44(a0)}: the live cloud's SST address. */
    @RewindTransient(reason = "$44(a0) cloud link restored by ObjectRefId in restoreRewindState")
    private ToxomisterCloudInstance cloud;

    private record CloudLink(ObjectRefId cloudId) implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public ToxomisterBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Toxomister");
        // btst #1,render_flags(a0) / neg.w d1 (the init): the placement's Y flip.
        boolean flippedY = (spawn.renderFlags() & 0x2) != 0;
        this.childSpriteY = ((spawn.y() & 0xFFFF)
                + (flippedY ? -CHILD_SPRITE_OFFSET_Y : CHILD_SPRITE_OFFSET_Y)) & 0xFFFF;
    }

    /**
     * {@code Obj_Toxomister} is installed from the SKL object pointer table at ROM
     * {@code $0008FD48} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0008}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0008;
    }

    @Override
    public ToxomisterBadnikInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new ToxomisterBadnikInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // Obj_WaitOffscreen (sonic3k.asm:180271-180302).
        if (!awake) {
            awake = isOnScreen();
            if (!awake) {
                return;
            }
            breathe();
            return;
        }
        if (rebreathTimer >= 0) {
            // loc_8FDA0: Obj_Wait on $2E(a0), still turning to face the player.
            faceNearestPlayer(vIntRunCount, playerEntity);
            rebreathTimer--;
            if (rebreathTimer < 0) {
                breathe();
            }
            return;
        }
        // loc_8FD76: watch the cloud's status bit 7.
        if (cloud == null || cloud.isDestroyed() || cloud.puffsDispersing()) {
            rebreathTimer = REBREATH_DELAY;
            cloud = null;
            return;
        }
        faceNearestPlayer(vIntRunCount, playerEntity);
    }

    /**
     * {@code Touch_EnemyNormal} (sonic3k.asm:20945-20990).
     *
     * <p>{@code ObjDat_Toxomister}'s {@code collision_flags $18} has zero in bits 6-7, so
     * {@code Touch_ChkValue} (sonic3k.asm:20774-20776) routes the body through
     * {@code Touch_Enemy}. An attacking player therefore destroys it exactly like any other
     * badnik: {@code bset #7,status(a1)}, the chain bonus and points, and
     * {@code move.l #Obj_Explosion,(a1)}. The bounce applied to the player belongs to the
     * shared touch owner, not here.
     */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        destroyAsBadnik(player, false);
    }

    @Override
    public void onPoweredScreenAttack(PlayableEntity player) {
        destroyAsBadnik(player, true);
    }

    private void destroyAsBadnik(PlayableEntity player, boolean powered) {
        AbstractS3kBadnikInstance.destroyAsS3kBadnik(this, getCentreX(), getCentreY(), getSpawn(),
                player, services(), powered);
    }

    /** {@code sub_8FF72} (sonic3k.asm, {@code $8FF72}). */
    private void breathe() {
        playSfx(Sonic3kSfx.ENEMY_BREATH.id);
        final int x = (getCentreX() + CLOUD_OFFSET_X) & 0xFFFF;
        final int y = (getCentreY() + CLOUD_OFFSET_Y) & 0xFFFF;
        ToxomisterCloudInstance created = spawnFreeChild(() -> new ToxomisterCloudInstance(x, y));
        if (created == null) {
            return;
        }
        created.attachTo(this);
        created.createPuffs();
        cloud = created;
    }

    /** {@code sub_8FF5A} (sonic3k.asm, {@code $8FF5A}). */
    private void faceNearestPlayer(int vIntRunCount, PlayableEntity playerEntity) {
        if ((vIntRunCount & FACING_PERIOD_MASK) != 0) {
            return;
        }
        PlayableEntity nearest = nearestPlayer(playerEntity);
        if (nearest == null) {
            return;
        }
        // Find_SonicTails leaves d0 = 2 when the player is LEFT of the object, and Change_FlipX
        // sets render_flags bit 0 on a non-zero d0.
        facingRight = nearest.getCentreX() >= getCentreX();
    }

    private PlayableEntity nearestPlayer(PlayableEntity playerEntity) {
        PlayableEntity best = playerEntity;
        int bestDistance = playerEntity == null
                ? Integer.MAX_VALUE
                : Math.abs((short) (getCentreX() - playerEntity.getCentreX()));
        PlayableEntity p2 = nativeP2OrNull();
        if (p2 != null && Math.abs((short) (getCentreX() - p2.getCentreX())) < bestDistance) {
            best = p2;
        }
        return best;
    }

    private PlayableEntity nativeP2OrNull() {
        try {
            return services().playerQuery().nativeP2OrNull();
        } catch (Exception e) {
            return null;
        }
    }

    private void playSfx(int id) {
        try {
            services().playSfx(id);
        } catch (Exception e) {
            // Headless unit contexts have no audio service.
        }
    }

    // ===== Accessors =====

    /** ROM {@code render_flags(a0)} bit 7. */
    public boolean awake() {
        return awake;
    }

    /** ROM {@code render_flags(a0)} bit 0. */
    public boolean facingRight() {
        return facingRight;
    }

    /** ROM {@code $2E(a0)} while {@code loc_8FDA0} is waiting; {@code -1} when it is not. */
    public int rebreathTimer() {
        return rebreathTimer;
    }

    /** ROM {@code sub2_y_pos(a0)}. */
    public int childSpriteY() {
        return childSpriteY;
    }

    /** ROM {@code $44(a0)}. */
    public ToxomisterCloudInstance cloud() {
        return cloud;
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId id = context.identityTable().map(table -> table.encodeObject(cloud)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new CloudLink(id));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof CloudLink link) {
            cloud = link.cloudId() == null ? null
                    : (ToxomisterCloudInstance) context.requireIdentityTable()
                            .resolveObject(link.cloudId(), true);
        }
    }

    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return getSpawn().y() & 0xFFFF;
    }

    @Override
    public int getCollisionFlags() {
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_Toxomister,1,1) sets the priority bit.
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
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!awake) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.TOXOMISTER);
        if (renderer == null) {
            return;
        }
        // mainspr_childsprites 1: the body draws its own second sprite at sub2_y_pos.
        renderer.drawFrameIndex(MAPPING_FRAME, getX(), getY(), !facingRight, false);
        renderer.drawFrameIndex(0, getX(),
                childSpriteY - HALF_SIZE, !facingRight, false);
    }
}
