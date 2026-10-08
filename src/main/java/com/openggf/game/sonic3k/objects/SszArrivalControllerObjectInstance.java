package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * ROM {@code Obj_57C1E} and its successors {@code loc_57CD2}, {@code Obj_57D64} and
 * {@code loc_57DA2} (sonic3k.asm:116760-116858): the Sky Sanctuary teleporter arrival.
 *
 * <p>{@code SSZ1_ScreenInit} allocates it at X {@code $100} with {@code $2D = $6C};
 * {@code SSZ2_ScreenInit} at X {@code $A0} with {@code $2D = $44}. Its first pass
 * ({@code Obj_57C1E}) allocates the beam through {@code AllocateObjectAfterCurrent} as
 * {@code Obj_TeleporterBeamExpand} — already expanded — sets its own {@code y_pos} to
 * {@code $1000} and {@code $38} to {@code -$100} (the flag the beam clears when it finishes
 * contracting), spawns {@code Obj_57E34} in act 1, then puts Player 1 at
 * {@code Camera_Y + $65} under {@code object_control 3} and sets {@code Events_bg+$04}.
 *
 * <p>{@code loc_57CD2} rises: Player 1 and the camera both move up 8 px per frame for
 * {@code $2D} frames, except that the final decrement skips the camera. When the counter
 * reaches zero the next pass releases the player to {@code object_control 1} with the roll
 * animation and {@code y_vel = -1}, records the release Y in {@code $3E}, spawns the Tails
 * helper {@code Obj_57DCC} when act 1 and {@code Player_mode == 0} (act 2 instead sets the
 * player's {@code art_tile} bit 7), clears {@code Scroll_lock} and tells the beam to contract.
 *
 * <p>{@code Obj_57D64} then holds Player 1 on a {@code Gradual_SwingOffset($20000,$800)} arc
 * until the swing speed turns non-negative, at which point it clears {@code object_control},
 * {@code anim} and {@code Events_bg+$04}. {@code loc_57DA2} finally raises {@code Camera_min_Y}
 * to the camera once it settles at {@code Camera_max_Y}, and deletes itself when the beam has
 * cleared {@code $38}.
 */
public final class SszArrivalControllerObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, TeleporterBeamOwner {
    /** {@code Obj_57C1E}: {@code move.w #$1000,y_pos(a0)}. */
    private static final int CONTROLLER_Y = 0x1000;
    /** {@code loc_57CAC}: {@code Camera_Y_pos + $65}. */
    static final int PLAYER_Y_ABOVE_CAMERA = 0x65;
    /** {@code loc_57CD2}/{@code loc_57D50}: 8 px per frame for both the player and the camera. */
    private static final int RISE_STEP = 8;
    /** {@code Obj_57D64}: {@code move.l #$20000,d0} / {@code move.l #$800,d1}. */
    private static final int SWING_SPEED = 0x20000;
    private static final int SWING_ACCELERATION = 0x800;
    /** {@code Obj_57E34}: {@code move.w #$60,subtype(a1)}. */
    static final int CUTSCENE_SPAWNER_DELAY = 0x60;

    public static final int PHASE_INIT = 0;
    public static final int PHASE_RISE = 1;
    public static final int PHASE_SWING = 2;
    public static final int PHASE_SETTLE = 3;

    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; recreateForRewind rebuilds it.")
    private final int x;
    /** {@code $2D(a0)}: the rise frame count from the screen init. */
    private int riseRemaining;
    private int phase = PHASE_INIT;
    /** {@code $38(a0)}: {@code -$100} until the beam's deletion clears the low byte. */
    private int beamFlag;
    /** {@code $3E(a0)}: Player 1's Y when the rise released. */
    private int releaseBaseY;
    private final S3kGradualSwing swing = new S3kGradualSwing();
    private TeleporterBeamObjectInstance beam;

    private record RewindExtra(int riseRemaining, int phase, int beamFlag, int releaseBaseY,
                               S3kGradualSwing.Value swing,
                               ObjectRefId beamId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszArrivalControllerObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SszArrivalController");
        this.x = spawn.x();
        // SSZ1_ScreenInit move.b #$6C,$2D(a1); SSZ2_ScreenInit move.b #$44,$2D(a1).
        this.riseRemaining = spawn.subtype();
    }

    @Override
    public SszArrivalControllerObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszArrivalControllerObjectInstance(ctx.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId beamId = context.identityTable()
                .map(table -> table.encodeObject(beam)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                riseRemaining, phase, beamFlag, releaseBaseY,
                swing.captureRewindStateValue(), beamId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            riseRemaining = extra.riseRemaining();
            phase = extra.phase();
            beamFlag = extra.beamFlag();
            releaseBaseY = extra.releaseBaseY();
            swing.restoreRewindStateValue(extra.swing());
            beam = extra.beamId() == null ? null
                    : (TeleporterBeamObjectInstance) context.requireIdentityTable()
                            .resolveObject(extra.beamId(), true);
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (phase) {
            case PHASE_INIT -> applyInit();
            case PHASE_RISE -> updateRise();
            case PHASE_SWING -> updateSwing();
            default -> updateSettle();
        }
    }

    /** {@code Obj_57C1E} through {@code loc_57CAC}. */
    private void applyInit() {
        // AllocateObjectAfterCurrent runs the beam in the same object pass.
        beam = spawnChild(() -> TeleporterBeamObjectInstance.sszArrivalBeam(
                new ObjectSpawn(x, CONTROLLER_Y, 0, 0, 0, false, 0), this));
        if (beam == null) {
            // beq.s loc_57C28 / rts: a failed allocation leaves the controller at Obj_57C1E,
            // so the ROM retries on the next pass rather than releasing the player.
            return;
        }
        beamFlag = -0x100;
        // tst.b (Current_act).w / bne.s loc_57CAC: act 1 always gets the cutscene spawner,
        // for every Player_mode.
        if (services().currentAct() == 0) {
            spawnChild(() -> new SszCutsceneKnucklesSpawnerObjectInstance(
                    new ObjectSpawn(x, CONTROLLER_Y, 0, CUTSCENE_SPAWNER_DELAY, 0, false, 0)));
        }
        SszZoneRuntimeState state = state();
        if (state != null) {
            state.setEventsBgByte(0x04, 0xFF);
        }
        AbstractPlayableSprite sprite = playerOne();
        if (sprite != null) {
            int cameraY = services().camera().getY() & 0xFFFF;
            NativePositionOps.writeYPosPreserveSubpixel(sprite,
                    (cameraY + PLAYER_Y_ABOVE_CAMERA) & 0xFFFF);
            // object_control = 3: bits 0-6 without bit 7, and bit 1 suppresses Animate_Sonic,
            // so the cleared anim and mapping_frame survive the ride up.
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
            sprite.setObjectMappingFrameControl(true);
            sprite.setAnimationId(0);
            sprite.setMappingFrame(0);
        }
        phase = PHASE_RISE;
    }

    /** {@code loc_57CD2}/{@code loc_57D50}. */
    private void updateRise() {
        AbstractPlayableSprite sprite = playerOne();
        if (sprite == null) {
            return;
        }
        // move.w x_pos(a0),x_pos(a1): the controller holds Player 1 on the pad column.
        NativePositionOps.writeXPosPreserveSubpixel(sprite, x);
        if (riseRemaining != 0) {
            NativePositionOps.addYPosPreserveSubpixel(sprite, -RISE_STEP);
            riseRemaining--;
            if (riseRemaining != 0) {
                var camera = services().camera();
                camera.setY((short) (camera.getY() - RISE_STEP));
            }
            return;
        }
        release(sprite);
    }

    /** {@code loc_57CD2}'s zero-counter path through {@code loc_57D3C}. */
    private void release(AbstractPlayableSprite sprite) {
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
        sprite.setObjectMappingFrameControl(false);
        sprite.setAnimationId(Sonic3kAnimationIds.ROLL);
        sprite.setRollingFlagPreserveRadii(true);
        sprite.setYSpeed((short) -1);
        releaseBaseY = sprite.getCentreY() & 0xFFFF;
        if (services().currentAct() != 0) {
            // move.b #7,art_tile(a1) bit set: act 2 draws Knuckles in front of the plane.
            sprite.setHighPriority(true);
        } else if (sidekick() != null) {
            // tst.w (Player_mode).w / bne.s loc_57D3C: only the Sonic + Tails team gets the helper.
            spawnChild(() -> new SszTailsArrivalHelperObjectInstance(
                    new ObjectSpawn(x, releaseBaseY, 0, 0x0C, 0, false, 0)));
        }
        services().camera().setScrollLocked(false);
        if (beam != null) {
            beam.startContracting();
        }
        phase = PHASE_SWING;
    }

    /** {@code Obj_57D64}. */
    private void updateSwing() {
        AbstractPlayableSprite sprite = playerOne();
        if (sprite == null) {
            return;
        }
        int offset = swing.step(SWING_SPEED, SWING_ACCELERATION);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, (releaseBaseY + offset) & 0xFFFF);
        if (swing.rising()) {
            return;
        }
        ObjectControlState.none().applyTo(sprite);
        sprite.setObjectMappingFrameControl(false);
        sprite.setAnimationId(0);
        SszZoneRuntimeState state = state();
        if (state != null) {
            state.setEventsBgByte(0x04, 0);
        }
        phase = PHASE_SETTLE;
    }

    /** {@code loc_57DA2}. */
    private void updateSettle() {
        var camera = services().camera();
        int cameraY = camera.getY();
        if (cameraY == camera.getMaxY()) {
            if (cameraY != camera.getMinY()) {
                camera.setMinY((short) cameraY);
            }
            if (beamFlag == 0) {
                com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
            }
        }
    }

    @Override
    public void onBeamFinished() {
        beamFlag = 0;
        beam = null;
    }

    private AbstractPlayableSprite playerOne() {
        return services().spriteManager().getMainPlayable();
    }

    private AbstractPlayableSprite sidekick() {
        // Player_2 as the ROM sees it: an empty slot is absent, not a configured roster entry.
        return services().playerQuery().nativeP2OrNull()
                instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    private SszZoneRuntimeState state() {
        return S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
    }

    public int phaseForTest() { return phase; }
    public int riseRemainingForTest() { return riseRemaining; }
    public TeleporterBeamObjectInstance beamForTest() { return beam; }

    @Override public int getX() { return x; }
    @Override public int getY() { return CONTROLLER_Y; }
    @Override public int getOutOfRangeReferenceX() { return x; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // The controller has no mappings: it only scripts the player, camera and children.
    }
}
