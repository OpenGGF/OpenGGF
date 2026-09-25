package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.game.ShieldType;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The cloud a Toxomister breathes out: {@code loc_8FDBA} and its five routines
 * (sonic3k.asm, ROM {@code $8FDBA}).
 *
 * <ul>
 *   <li>{@code loc_8FDD8}: {@code SetUp_ObjAttributes3} from {@code word_9003A}, then
 *       {@code collision_flags $D8}, {@code y_radius $18}, {@code $2E = $6F} with
 *       {@code $34 = loc_8FE10}, and the seven {@code ChildObjDat_90048} puffs.</li>
 *   <li>Routines 2 and 6 are {@code loc_8FE06}: the latch below, then {@code Obj_Wait}.</li>
 *   <li>{@code loc_8FE10}: after {@code $6F} frames the cloud starts falling, {@code y_vel $40},
 *       and routine 4 ({@code loc_8FE26}) is {@code MoveSprite2} plus
 *       {@code ObjHitFloor_DoRoutine}. {@code loc_8FE36} then parks it for {@code $7F} frames
 *       before {@code Go_Delete_Sprite}.</li>
 *   <li>Routine 8 ({@code loc_8FE50}) is the attached state, below.</li>
 * </ul>
 *
 * <h2>The latch, {@code sub_8FF8C}</h2>
 * <p>A touch that is neither {@code anim == 2} (rolling) nor carried by a bubble shield latches
 * the toucher: {@code $44(a0)} takes the player, {@code $3E(a0)} their controller port,
 * {@code $3A(a0)} that port's current word, routine 8 and {@code $2E = 59}.
 *
 * <h2>The attached state, {@code loc_8FE50}</h2>
 * <p>Three ways out and one ongoing effect.
 * <ul>
 *   <li>{@code cmpi.b #9,anim(a1)}: a spindash frees the player immediately and raises
 *       {@code $38} bit 2, which is what makes the puffs scatter outward instead of just
 *       rising.</li>
 *   <li>{@code Check_LRControllerShake} (sonic3k.asm:179881-179900): {@code $3C(a0)} is reloaded
 *       to 5 and {@code $3D(a0)} to 60 whenever the 60-frame window lapses, and each frame the
 *       held left/right bits ({@code andi.w #$C}) differ from the stored ones costs one of the
 *       five. Spending all five frees the player.</li>
 *   <li>{@code sub_881FE} takes one ring every 60 frames and, at zero rings, kills the
 *       player.</li>
 *   <li>{@code sub_8FFE0} is the slow-down, every frame: an eighth off {@code x_vel}, and an
 *       eighth off {@code ground_vel} when grounded or {@code y_vel} when airborne. It is an
 *       arithmetic shift, so it decays toward zero from either sign rather than reversing.</li>
 * </ul>
 */
public final class ToxomisterCloudInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, RewindRecreatable {

    /** {@code word_9003A}: {@code dc.w 0} and {@code dc.b 8,8,2,0}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0000);
    private static final int HALF_SIZE = 8;
    private static final int MAPPING_FRAME = 2;
    /** {@code move.b #$D8,collision_flags(a0)} (loc_8FDD8). */
    private static final int COLLISION_FLAGS = 0xD8;
    /** {@code move.b #$18,y_radius(a0)} (loc_8FDD8). */
    private static final int Y_RADIUS = 0x18;
    /** {@code move.w #$6F,$2E(a0)} (loc_8FDD8). */
    private static final int HOVER_FRAMES = 0x6F;
    /** {@code move.w #$40,y_vel(a0)} (loc_8FE10). */
    private static final int FALL_Y_VEL = 0x40;
    /** {@code move.w #$7F,$2E(a0)} (loc_8FE36). */
    private static final int SETTLED_FRAMES = 0x7F;
    /** {@code move.w #60-1,$2E(a0)} (sub_8FF8C) and {@code sub_881FE}'s own reload. */
    private static final int RING_DRAIN_PERIOD = 60 - 1;
    /** {@code move.b #5,$3C(a0)} / {@code move.b #60,$3D(a0)} (Check_LRControllerShake). */
    private static final int SHAKE_REVERSALS = 5;
    private static final int SHAKE_WINDOW = 60;
    /** {@code ChildObjDat_90048} (sonic3k.asm, {@code $90048}): seven puffs. */
    private static final int[][] PUFFS = {
            {-0x0C, 4}, {0x0C, 4}, {-8, -4}, {8, -4}, {0, -4}, {-4, 4}, {4, 4}};

    /** ROM {@code routine(a0)}: 2 hovering, 4 falling, 6 settled, 8 attached. */
    private int routine = 2;
    /** ROM {@code $2E(a0)}. */
    private int timer = HOVER_FRAMES;
    /** ROM {@code $44(a0)} as an index, so the blob carries it: 1 = P1, 2 = P2, 0 = none. */
    private int attachedPlayerSlot;
    /** Touch_Special's byte, consumed by sub_8FF8C on the next object pass. */
    private int collisionProperty;
    /** ROM {@code status(a0)} bit 7: the puffs read it to start dispersing. */
    private boolean dispersing;
    /** ROM {@code $38(a0)} bit 2: set only by the spindash escape. */
    private boolean escapedBySpindash;
    /** ROM {@code $3C(a0)} / {@code $3D(a0)} inside {@code Check_LRControllerShake}. */
    private int shakeReversalsLeft;
    private int shakeWindow;
    /** ROM {@code $3A(a0)}: the controller word as the last frame left it. */
    private int lastDirectionBits;

    private final SubpixelMotion.State motion;
    /** ROM {@code parent3(a0)}: the body that breathed this cloud. */
    @RewindTransient(reason = "parent3 link restored by ObjectRefId in restoreRewindState")
    private ToxomisterBadnikInstance body;

    private record BodyLink(ObjectRefId bodyId) implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    ToxomisterCloudInstance(int x, int y) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, 0, 0, false, 0), "ToxomisterCloud");
        this.motion = new SubpixelMotion.State(x & 0xFFFF, y & 0xFFFF, 0, 0, 0, 0);
    }

    void attachTo(ToxomisterBadnikInstance owner) {
        this.body = owner;
    }

    @Override
    public ToxomisterCloudInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new ToxomisterCloudInstance(ctx.spawn().x(), ctx.spawn().y()));
    }

    /** {@code CreateChild1_Normal(ChildObjDat_90048)} at the end of {@code loc_8FDD8}. */
    void createPuffs() {
        for (int i = 0; i < PUFFS.length; i++) {
            final int index = i * 2;
            final int dx = PUFFS[i][0];
            final int dy = PUFFS[i][1];
            ToxomisterPuffInstance puff =
                    spawnFreeChild(() -> new ToxomisterPuffInstance(index, dx, dy));
            if (puff != null) {
                puff.attachTo(this);
            }
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // sub_8FF8C may change routine, but the already-dispatched routine still runs
        // its tail: Obj_Wait decrements the new timer, or falling still moves/lands.
        switch (routine) {
            case 2 -> {
                consumeContact(playerEntity);
                hover();
            }
            case 4 -> {
                consumeContact(playerEntity);
                fall();
            }
            case 6 -> {
                consumeContact(playerEntity);
                settle();
            }
            default -> stayAttached(playerEntity);
        }
        // Child_AddToTouchList (sonic3k.asm:84962-84966) runs after loc_8FDBA's own dispatch:
        // when the body raised status bit 7 the cloud takes Go_Delete_Sprite instead of joining
        // the collision response list. Go_Delete_Sprite (sonic3k.asm) itself does
        // bset #7,status(a0), which is the bit the puffs read in loc_8FEDC -- so the cloud's
        // own children disperse through loc_8FF12 exactly as they do on its other endings.
        if (body != null && body.isDestroyed() && !dispersing) {
            expire();
        }
        updateDynamicSpawn(motion.x, motion.y);
    }

    /** Routine 2, {@code loc_8FE06} into {@code loc_8FE10}. */
    private void hover() {
        timer--;
        if (timer < 0) {
            routine = 4;
            motion.yVel = FALL_Y_VEL;
        }
    }

    /** Routine 4, {@code loc_8FE26} and {@code loc_8FE36}. */
    private void fall() {
        SubpixelMotion.moveSprite2(motion);
        // ObjHitFloor_DoRoutine (sonic3k.asm:177964-177979): only while y_vel is not negative,
        // and only a strictly negative or zero distance lands it.
        if (motion.yVel < 0) {
            return;
        }
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(motion.x, motion.y, Y_RADIUS);
        if (!floor.foundSurface() || floor.distance() > 0) {
            return;
        }
        motion.y = (motion.y + floor.distance()) & 0xFFFF;
        routine = 6;
        motion.yVel = 0;
        timer = SETTLED_FRAMES;
    }

    /** Routine 6, {@code loc_8FE06} into {@code Go_Delete_Sprite}. */
    private void settle() {
        timer--;
        if (timer < 0) {
            expire();
        }
    }

    /** Routine 8, {@code loc_8FE50}. */
    private void stayAttached(PlayableEntity updatePlayer) {
        PlayableEntity player = attachedPlayer(updatePlayer);
        if (player == null) {
            expire();
            return;
        }
        // cmpi.b #9,anim(a1) / beq loc_8FE82: the spindash escape, which also scatters the puffs.
        if (player.getAnimationId() == Sonic3kAnimationIds.SPINDASH.id()) {
            escapedBySpindash = true;
            expire();
            return;
        }
        if (shakenOff(player)) {
            expire();
            return;
        }
        motion.x = player.getCentreX() & 0xFFFF;
        motion.y = player.getCentreY() & 0xFFFF;
        applySlowDown(player);
        drainRing(player);
    }

    /** {@code Check_LRControllerShake} (sonic3k.asm:179881-179900). */
    private boolean shakenOff(PlayableEntity player) {
        shakeWindow--;
        if (shakeWindow < 0) {
            shakeReversalsLeft = SHAKE_REVERSALS;
            shakeWindow = SHAKE_WINDOW;
        }
        int bits = directionBits(player);
        if (bits == 0) {
            // beq.s locret_8586E with d0 still zero: not a reversal, and not an escape.
            return false;
        }
        int previous = lastDirectionBits;
        lastDirectionBits = bits;
        if ((previous ^ bits) == 0) {
            return false;
        }
        shakeReversalsLeft--;
        // subq.b #1,$3C(a0) / bmi.s locret with d0 still non-zero: spending the last one frees.
        return shakeReversalsLeft < 0;
    }

    /** {@code andi.w #$C,d0}: the held LEFT and RIGHT bits of the latched controller word. */
    private int directionBits(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return 0;
        }
        int bits = 0;
        if (sprite.isLeftPressed()) {
            bits |= 0x4;
        }
        if (sprite.isRightPressed()) {
            bits |= 0x8;
        }
        return bits;
    }

    /** {@code sub_8FFE0} (sonic3k.asm, {@code $8FFE0}). */
    private void applySlowDown(PlayableEntity player) {
        player.setXSpeed((short) (player.getXSpeed() - (player.getXSpeed() >> 3)));
        if (player.getAir()) {
            player.setYSpeed((short) (player.getYSpeed() - (player.getYSpeed() >> 3)));
        } else {
            player.setGSpeed((short) (player.getGSpeed() - (player.getGSpeed() >> 3)));
        }
    }

    /** {@code sub_881FE} (sonic3k.asm, {@code $881FE}): one ring a second, then death. */
    private void drainRing(PlayableEntity player) {
        timer--;
        if (timer >= 0) {
            return;
        }
        timer = RING_DRAIN_PERIOD;
        int rings;
        try {
            rings = services().levelGamestate().getRings();
        } catch (Exception unavailable) {
            return;
        }
        if (rings - 1 < 0) {
            // loc_88258: the cloud becomes the killer.
            player.applyHurtOrDeath(getCentreX(), com.openggf.game.DamageCause.NORMAL, false);
            expire();
            return;
        }
        services().levelGamestate().setRings(rings - 1);
        try {
            services().playSfx(Sonic3kSfx.RING_RIGHT.id);
        } catch (Exception ignored) {
            // Headless unit contexts have no audio service.
        }
    }

    private PlayableEntity attachedPlayer(PlayableEntity updatePlayer) {
        if (attachedPlayerSlot == 2) {
            try {
                return services().playerQuery().nativeP2OrNull();
            } catch (Exception e) {
                return null;
            }
        }
        return attachedPlayerSlot == 1 ? updatePlayer : null;
    }

    /** {@code Go_Delete_Sprite}: the body watches {@code status} bit 7 to breathe another. */
    private void expire() {
        dispersing = true;
        ObjectLifetimeOps.expireDynamic(this);
    }

    // ===== TouchResponseListener: sub_8FF8C =====

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (routine == 8 || player == null) {
            return;
        }
        // loc_103FA: +1 for native P1, +2 for native P2. Touch merely publishes the
        // byte; the object's next sub_8FF8C call chooses the player and checks their anim.
        collisionProperty = (collisionProperty + (player == nativeP2OrNull() ? 2 : 1)) & 0xFF;
    }

    private void consumeContact(PlayableEntity primaryPlayer) {
        if (collisionProperty == 0) {
            return;
        }
        int contacts = collisionProperty & 3;
        collisionProperty = 0;
        // word_8FFD4: simultaneous contact (3) selects P1, just like P1-only (1).
        PlayableEntity player = contacts == 2 ? nativeP2OrNull() : primaryPlayer;
        if (player == null) {
            return;
        }
        // cmpi.b #2,anim(a2) / beq: a rolling player passes straight through.
        if (player.getAnimationId() == Sonic3kAnimationIds.ROLL.id()) {
            return;
        }
        // btst #Status_BublShield,status_secondary(a2) / bne.
        if (player.hasShield() && player.getShieldType() == ShieldType.BUBBLE) {
            return;
        }
        attachedPlayerSlot = player == nativeP2OrNull() ? 2 : 1;
        routine = 8;
        timer = RING_DRAIN_PERIOD;
        shakeReversalsLeft = SHAKE_REVERSALS;
        shakeWindow = SHAKE_WINDOW;
        lastDirectionBits = directionBits(player);
    }

    private PlayableEntity nativeP2OrNull() {
        try {
            return services().playerQuery().nativeP2OrNull();
        } catch (Exception e) {
            return null;
        }
    }

    // ===== Accessors =====

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId id = context.identityTable().map(table -> table.encodeObject(body)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new BodyLink(id));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof BodyLink link) {
            body = link.bodyId() == null ? null
                    : (ToxomisterBadnikInstance) context.requireIdentityTable()
                            .resolveObject(link.bodyId(), true);
        }
    }

    /** ROM {@code routine(a0)}. */
    public int routine() {
        return routine;
    }

    /** ROM {@code $2E(a0)}. */
    public int timer() {
        return timer;
    }

    /** ROM {@code $44(a0)} as a slot: 1 = P1, 2 = P2, 0 = unattached. */
    public int attachedPlayerSlot() {
        return attachedPlayerSlot;
    }

    /** ROM {@code status(a0)} bit 7. */
    public boolean puffsDispersing() {
        return dispersing;
    }

    /** ROM {@code $38(a0)} bit 2. */
    public boolean escapedBySpindash() {
        return escapedBySpindash;
    }

    /** ROM {@code $3C(a0)}. */
    public int shakeReversalsLeft() {
        return shakeReversalsLeft;
    }

    /** The body's {@code render_flags} bit 0, which {@code loc_90002} signs the puffs with. */
    public boolean bodyFacingRight() {
        return body != null && body.facingRight();
    }

    public int getCentreX() {
        return motion.x & 0xFFFF;
    }

    public int getCentreY() {
        return motion.y & 0xFFFF;
    }

    // Declare the same ROM touch semantics for the shared profile dispatcher.
    // The legacy accessors below remain compatible with direct object consumers.
    private static final TouchResponseProfile TOUCH_PROFILE = TouchResponseProfile.fromCanonical(
            new com.openggf.game.profiles.touchresponse.TouchResponseProfile(
                    com.openggf.game.profiles.touchresponse.TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY,
                    true, true, false,
                    com.openggf.game.profiles.touchresponse.TouchShieldDeflectCapability.NONE,
                    0,
                    com.openggf.game.profiles.touchresponse.TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                    com.openggf.game.profiles.touchresponse.TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                    com.openggf.game.profiles.touchresponse.TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS));

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_PROFILE;
    }

    @Override
    public int getCollisionFlags() {
        return routine == 8 ? 0 : COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return collisionProperty;
    }

    @Override
    public boolean usesS3kTouchSpecialPropertyResponse() {
        // Touch_ChkValue -> Touch_Special -> loc_103FA for $D8. The shared default
        // interprets $C0 as boss contact and would hurt/bounce the player after our latch.
        return true;
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        // A rolling/bubble-shield contact is discarded each object pass, not until exit.
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
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
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.TOXOMISTER);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(MAPPING_FRAME, getX(), getY(), false, false);
    }
}
