package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * ROM {@code Obj_SSZRotatingPlatform} ({@code $76}, sonic3k.asm:91729-91856): the short post a
 * player balances on, which spins them in place. Seven act-1 placements: three {@code $00} and
 * four {@code $01}, and bit 0 is the only subtype bit either half of the object reads — it widens
 * the invisible carrier child from {@code $60} to {@code $A0}.
 *
 * <p>Init: {@code render_flags 4}, {@code height_pixels $20}, {@code width_pixels $C},
 * {@code priority $100}, {@code make_art_tile(ArtTile_SSZMisc+$AA,2,0)} over
 * {@code Map_SSZRotatingPlatform}, then {@code AllocateObjectAfterCurrent} for the
 * {@link SszRotatingPlatformCarrierObjectInstance} at {@code loc_45F10}, which inherits the post's
 * position and subtype. The child's slot handle lives in {@code $32(a0)}; the post kills it with
 * {@code st routine(a1)} when it culls itself.
 *
 * <p>{@code loc_45DFE} culls on the coarse camera distance — {@code (x_pos & $FF80) -
 * Camera_X_pos_coarse_back} above {@code $280} — clearing bit 7 of its respawn-table entry so the
 * pair comes back. Otherwise it animates {@code Ani_SSZRotatingPlatform} (delay 7 over frames 0, 1
 * and 2) and calls {@code SolidObjectTop} with {@code d1 = $10}, {@code d2 = d3 = $21}.
 *
 * <p>{@code sub_45E6E} is a two-step latch: the frame a player first stands, it writes
 * {@code $0100} into the player's word and does nothing else, so the spin only starts on the frame
 * after. From then on it holds the player with {@code object_control 3}, nudges them one pixel per
 * frame towards the post's centre, advances the pose angle by two and draws them through
 * {@link SszCarriedPlayerPose}. Any A/B/C press releases with {@code y_vel -$680} and a roll.
 */
public final class SszRotatingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    /** {@code move.w #$100,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x100);
    /** {@code make_art_tile(ArtTile_SSZMisc+$AA,2,0)}. */
    private static final int PALETTE_LINE = 2;
    /** {@code moveq #$10,d1} / {@code moveq #$21,d2} / {@code moveq #$21,d3}. */
    private static final SolidObjectParams SOLID = SolidObjectParams.of(0x10, 0x21, 0x21);
    /** {@code Ani_SSZRotatingPlatform}: {@code 7, 0,1,2, $FF}. */
    private static final int ANIM_DELAY = 7;
    private static final int ANIM_FRAMES = 3;
    /** {@code move.w #-$680,y_vel(a1)}. */
    static final int RELEASE_Y_VEL = -0x680;
    /** {@code addq.b #2,1(a3)}. */
    static final int ANGLE_STEP = 2;
    /** {@code move.w #$100,(a3)}: the one-frame arming latch. */
    private static final int ARMED = 1;
    /** {@code st (a3)}. */
    private static final int HELD = -1;

    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; rewind recreation rebuilds it.")
    private final int x;
    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; rewind recreation rebuilds it.")
    private final int y;
    private int mappingFrame;
    private int animTimer;
    private int animFrame;
    /** {@code $2E(a0)}/{@code $30(a0)}: state byte and pose angle for each player. */
    private int p1State;
    private int p1Angle;
    private int p2State;
    private int p2Angle;
    /** {@code $32(a0)}: the carrier child's slot handle. */
    private SszRotatingPlatformCarrierObjectInstance carrier;
    private boolean carrierSpawned;

    private record RewindExtra(ObjectRefId carrierId) implements
            PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszRotatingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZRotatingPlatform");
        this.x = spawn.x();
        this.y = spawn.y();
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        spawnCarrierOnce();
        // cmpi.w #$280,d0: the Sprite_OnScreen_Test2 window, widened for a wider viewport the
        // way every other coarse cull in the engine is.
        coarseXCullViewport(x);
        if (isDestroyed()) {
            // loc_45E0C: st routine(a1) kills the carrier, and bclr #7 on the respawn entry lets
            // the pair load again from the placement list.
            if (carrier != null) {
                carrier.killFromPost();
                carrier = null;
            }
            return;
        }
        animate();
        checkpointAll();
        servicePlayer(0, player);
        // lea (Player_2).w,a1: the routine's second pass is the native Player 2 slot.
        servicePlayer(1, services().playerQuery().nativeP2OrNull());
    }

    /** {@code jsr (AllocateObjectAfterCurrent)} in the init. */
    private void spawnCarrierOnce() {
        if (carrierSpawned) {
            return;
        }
        carrierSpawned = true;
        carrier = spawnChild(() -> new SszRotatingPlatformCarrierObjectInstance(
                new ObjectSpawn(x, y, 0, getSpawn().subtype(), getSpawn().renderFlags(), false, 0)));
    }

    /** One {@code sub_45E6E} call. */
    private void servicePlayer(int slot, PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        boolean standing = isRiding(sprite);
        int state = state(slot);
        if (state == 0) {
            if (standing) {
                // move.w #$100,(a3): byte 0 armed, byte 1 (the pose angle) cleared.
                setState(slot, ARMED);
                setAngle(slot, 0);
            }
            return;
        }
        if (state >= 0 && !standing) {
            setState(slot, 0);
            return;
        }
        setState(slot, HELD);
        sprite.setXSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setSpindash(false);
        sprite.setAnimationId(Sonic3kAnimationIds.WALK);
        sprite.setRollingFlagPreserveRadii(false);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
        if (sprite.isLogicalJumpPressActive()) {
            releasePlayer(slot, sprite);
            return;
        }
        // loc_45EEE: one pixel per frame towards the post's centre, never past it.
        int playerX = sprite.getCentreX() & 0xFFFF;
        if (playerX != x) {
            NativePositionOps.addXPosPreserveSubpixel(sprite, playerX < x ? 1 : -1);
        }
        int angle = (angle(slot) + ANGLE_STEP) & 0xFF;
        setAngle(slot, angle);
        try {
            SszCarriedPlayerPose.apply(services().rom(), sprite, angle);
        } catch (java.io.IOException ignored) {
            // The ROM is open for the level's whole life; a read failure here draws the last pose.
        }
    }

    /** The A/B/C branch shared with the carrier: {@code y_vel -$680}, rolling, {@code sfx_Jump}. */
    static void jumpOff(AbstractPlayableSprite sprite, com.openggf.level.objects.ObjectServices svc,
            AbstractObjectInstance owner) {
        sprite.setYSpeed((short) RELEASE_Y_VEL);
        ObjectControlState.none().applyTo(sprite);
        sprite.setObjectMappingFrameControl(false);
        sprite.setRolling(true);
        sprite.setAir(true);
        sprite.setJumping(true);
        sprite.setAnimationId(Sonic3kAnimationIds.ROLL);
        var objectManager = svc.objectManager();
        if (objectManager != null) {
            objectManager.releaseRidingObject(sprite, owner);
        }
        svc.playSfx(Sonic3kSfx.JUMP.id);
    }

    private void releasePlayer(int slot, AbstractPlayableSprite sprite) {
        setState(slot, 0);
        jumpOff(sprite, services(), this);
    }

    private boolean isRiding(AbstractPlayableSprite sprite) {
        var objectManager = services().objectManager();
        return objectManager != null && objectManager.isRidingObject(sprite, this);
    }

    /** {@code Animate_Sprite} over {@code Ani_SSZRotatingPlatform}. */
    private void animate() {
        if (--animTimer >= 0) {
            return;
        }
        animTimer = ANIM_DELAY;
        mappingFrame = animFrame;
        animFrame = (animFrame + 1) % ANIM_FRAMES;
    }

    private int state(int slot) { return slot == 0 ? p1State : p2State; }

    private void setState(int slot, int value) {
        if (slot == 0) {
            p1State = value;
        } else {
            p2State = value;
        }
    }

    private int angle(int slot) { return slot == 0 ? p1Angle : p2Angle; }

    private void setAngle(int slot, int value) {
        if (slot == 0) {
            p1Angle = value;
        } else {
            p2Angle = value;
        }
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x0C; }
    @Override public int getOnScreenHalfHeight() { return 0x20; }

    public int stateForTest(int slot) { return state(slot); }
    public int angleForTest(int slot) { return angle(slot); }
    public SszRotatingPlatformCarrierObjectInstance carrierForTest() { return carrier; }
    int mappingFrameForTest() { return mappingFrame; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_ROTATING_PLATFORM);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false, PALETTE_LINE);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId carrierId = context.identityTable()
                .map(table -> table.encodeObject(carrier)).orElse(null);
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(carrierId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            carrier = extra.carrierId() == null ? null
                    : (SszRotatingPlatformCarrierObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.carrierId(), true);
        }
    }
}
