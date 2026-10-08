package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * ROM {@code loc_46284}-{@code loc_46424} (sonic3k.asm:92158-92300): the short bar at the tip of
 * {@code Obj_SSZSwingingCarrier}'s arm, and the only part of the family a player touches.
 *
 * <p>Init: {@code render_flags 4}, {@code height_pixels $C}, {@code width_pixels $18},
 * {@code priority $80}, {@code make_art_tile(ArtTile_SSZMisc+$74,3,0)} over
 * {@code Map_SSZElevatorBar} at mapping frame 3. Each frame it takes the arc's last sub-sprite
 * position, adds {@code $C} to the Y, and calls {@code SolidObjectFull} with {@code d1 = $23},
 * {@code d2 = d3 = 8} — a full solid, not a top, so the bar can also be hit from below.
 *
 * <p>{@code sub_46324}: a player who is <em>standing</em> is caught — {@code sfx_Roll},
 * {@code object_control 1}, all three velocities zeroed and a forced roll — and then carried at
 * {@code x_pos(a0)}, {@code y_pos(a0) - $C} for as long as they hold on. A player who instead
 * touched the side or bottom ({@code d6 & 5}) is hurt through {@code sub_24280}, which rewinds
 * their Y by one frame of {@code y_vel} before calling {@code HurtCharacter} so the knockback
 * starts from where they were, not where the bar pushed them.
 *
 * <p>For the rotator kind (subtype bit 7) the catch also copies the object's facing bit onto the
 * player, and each carried frame probes the floor with {@code SonicOnObjHitFloor}: a negative
 * distance lifts the player out of the ground, and past {@code -$10} the bar lets go entirely and
 * throws them sideways at {@code $800} with {@code sfx_Dash}. That is how the rotating carriers
 * deposit a player on the deck instead of grinding them through it.
 *
 * <p>Any A/B/C press releases with {@code y_vel -$680} — note this branch does <em>not</em> set
 * {@code Status_Roll} or touch the radii, unlike the rotating platform's release.
 */
public final class SszSwingingCarrierBarObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable {
    /** {@code move.w #$80,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x80);
    /** {@code make_art_tile(ArtTile_SSZMisc+$74,3,0)}. */
    private static final int PALETTE_LINE = 3;
    /** {@code move.b #3,mapping_frame(a0)}. */
    private static final int MAPPING_FRAME = 3;
    /** {@code moveq #$23,d1} / {@code moveq #8,d2} / {@code moveq #8,d3}. */
    private static final SolidObjectParams SOLID = SolidObjectParams.of(0x23, 8, 8);
    /** {@code addi.w #$C,d0}, and the {@code subi.w #$C,d0} that carries the player. */
    static final int TIP_Y_OFFSET = 0x0C;
    /** {@code move.w #-$680,y_vel(a1)}. */
    static final int RELEASE_Y_VEL = -0x680;
    /** {@code move.w #$800,d0}. */
    static final int EJECT_X_VEL = 0x800;
    /** {@code cmpi.w #-$10,d1}. */
    static final int EJECT_FLOOR_DEPTH = -0x10;

    private SszSwingingCarrierArcObjectInstance arc;
    private final boolean rotator;
    private int x;
    private int y;
    private boolean killed;
    /** {@code $30}/{@code $32}: whether each player is being carried. */
    private boolean p1Carried;
    private boolean p2Carried;

    private record RewindExtra(ObjectRefId arcId, boolean killed, int x, int y,
                               boolean p1Carried, boolean p2Carried)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszSwingingCarrierBarObjectInstance(ObjectSpawn spawn,
            SszSwingingCarrierArcObjectInstance arc) {
        super(spawn, "SSZSwingingCarrierBar");
        this.arc = arc;
        this.rotator = (spawn.subtype() & 0x80) != 0;
        this.x = spawn.x();
        this.y = spawn.y();
    }

    /** Probe/rewind constructor. */
    public SszSwingingCarrierBarObjectInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public SszSwingingCarrierBarObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszSwingingCarrierBarObjectInstance(ctx.spawn());
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    /** {@code st routine(a1)} from {@code loc_4620A}. */
    void killFromArc() {
        killed = true;
    }

    // loc_462B6 deletes only after the arc signals routine=$FF. Its swinging tip
    // may leave the load window while the hub remains active; generic culling removed the
    // bar early and left the arc with a dangling reference during rewind capture.
    @Override public boolean isPersistent() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        if (killed) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (arc != null) {
            x = arc.tipX();
            y = (arc.tipY() + TIP_Y_OFFSET) & 0xFFFF;
        }
        var batch = checkpointAll();
        servicePlayer(0, player, batch);
        // lea (Player_2).w,a1: the routine's second pass is the native Player 2 slot.
        servicePlayer(1, services().playerQuery().nativeP2OrNull(), batch);
    }

    /** One {@code sub_46324} call. */
    private void servicePlayer(int slot, PlayableEntity entity,
            com.openggf.game.solid.SolidCheckpointBatch batch) {
        if (!(entity instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        if (!carried(slot)) {
            if (sprite.isObjectControlled() || sprite.isHurt() || sprite.getDead()) {
                return;
            }
            var contact = batch.perPlayer().get(entity);
            if (contact != null && contact.standingNow()) {
                catchPlayer(slot, sprite);
            } else if (contact != null && contact.pushingNow()) {
                // andi.w #5,d6 / jsr sub_24280: side and bottom contacts hurt.
                boolean hadRings = sprite.getRingCount() > 0;
                sprite.applyHurtOrDeath(x, DamageCause.NORMAL, hadRings);
                return;
            } else {
                return;
            }
        }
        carry(slot, sprite);
    }

    /** {@code loc_4634A}. */
    private void catchPlayer(int slot, AbstractPlayableSprite sprite) {
        services().playSfx(Sonic3kSfx.ROLL.id);
        setCarried(slot, true);
        // object_control 1: bit 0 only, so the player can still jump out but nothing else moves.
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
        sprite.setYSpeed((short) 0);
        sprite.setXSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setSpindash(false);
        sprite.setRolling(true);
        sprite.setAnimationId(Sonic3kAnimationIds.ROLL);
        if (rotator) {
            sprite.setDirection(isRenderFlipped()
                    ? com.openggf.physics.Direction.LEFT : com.openggf.physics.Direction.RIGHT);
        }
    }

    /** {@code loc_4639A}. */
    private void carry(int slot, AbstractPlayableSprite sprite) {
        NativePositionOps.writeXPosPreserveSubpixel(sprite, x);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, (y - TIP_Y_OFFSET) & 0xFFFF);
        if (rotator && ejectIntoFloor(slot, sprite)) {
            return;
        }
        if (!sprite.isLogicalJumpPressActive()) {
            return;
        }
        setCarried(slot, false);
        sprite.setYSpeed((short) RELEASE_Y_VEL);
        ObjectControlState.none().applyTo(sprite);
        sprite.setAir(true);
        sprite.setJumping(true);
        releaseRide(sprite);
        services().playSfx(Sonic3kSfx.JUMP.id);
    }

    /**
     * {@code jsr (SonicOnObjHitFloor)} and the {@code -$10} branch. Returns true when the bar has
     * let go this frame.
     */
    private boolean ejectIntoFloor(int slot, AbstractPlayableSprite sprite) {
        var levelManager = services().levelManager();
        if (levelManager == null) {
            return false;
        }
        var result = ObjectTerrainUtils.checkFloorDist(levelManager,
                sprite.getCentreX() & 0xFFFF,
                (sprite.getCentreY() & 0xFFFF) + sprite.getYRadius());
        int distance = result == null ? 0 : result.distance();
        if (distance >= 0) {
            return false;
        }
        NativePositionOps.addYPosPreserveSubpixel(sprite, distance);
        if (distance > EJECT_FLOOR_DEPTH) {
            return false;
        }
        setCarried(slot, false);
        ObjectControlState.none().applyTo(sprite);
        releaseRide(sprite);
        int velocity = isRenderFlipped() ? -EJECT_X_VEL : EJECT_X_VEL;
        sprite.setXSpeed((short) velocity);
        sprite.setGSpeed((short) velocity);
        services().playSfx(Sonic3kSfx.DASH.id);
        return true;
    }

    private void releaseRide(AbstractPlayableSprite sprite) {
        var objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.releaseRidingObject(sprite, this);
        }
    }

    private boolean carried(int slot) { return slot == 0 ? p1Carried : p2Carried; }

    private void setCarried(int slot, boolean value) {
        if (slot == 0) {
            p1Carried = value;
        } else {
            p2Carried = value;
        }
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isTopSolidOnly() { return false; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x0C; }

    public boolean carriedForTest(int slot) { return carried(slot); }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_ELEVATOR_BAR);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(MAPPING_FRAME, x, y, false, false, PALETTE_LINE);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId arcId = context.identityTable()
                .map(table -> table.encodeObject(arc)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(arcId, killed, x, y, p1Carried, p2Carried));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            killed = extra.killed();
            x = extra.x();
            y = extra.y();
            p1Carried = extra.p1Carried();
            p2Carried = extra.p2Carried();
            arc = extra.arcId() == null ? null
                    : (SszSwingingCarrierArcObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.arcId(), true);
        }
    }

    /** {@code btst #0,status(a0)}: the placement's X-flip bit. */
    private boolean isRenderFlipped() {
        return (getSpawn().renderFlags() & 1) != 0;
    }

}
