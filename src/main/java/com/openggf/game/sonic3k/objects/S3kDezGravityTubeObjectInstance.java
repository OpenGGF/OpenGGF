package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * SKL {@code $5A}, {@code Obj_DEZGravityTube} (sonic3k.asm:95169-95401).
 *
 * <p>Twenty-four act 1 and seventeen act 2 placements, and the only remaining <em>reader</em>
 * of {@code Reverse_gravity_flag} in the Death Egg object set. It is also the neighbour of
 * every {@code $5B} gravity-swap trigger, which is why so much of the act's inverted-play
 * footage waits on it.
 *
 * <p>{@code subtype} bit 7 selects one of two entirely separate bodies, chosen once in the
 * init (:95170-95171):
 *
 * <ul>
 *   <li><b>Horizontal</b> ({@code loc_48EEC} / {@code sub_48F12}, bit 7 clear). The player
 *       runs <em>along</em> the tube while it lifts them on a cosine. {@code (subtype & $3F)
 *       << 3} is the X half-width and {@code $32(a0)} the Y half-height — {@code $20}, or
 *       {@code $60} when bit 6 is set, which also picks the wider sixteen-entry angle table
 *       and the larger {@code $5000} amplitude.</li>
 *   <li><b>Vertical</b> ({@code loc_4906A} / {@code sub_49090}, bit 7 set). A fixed
 *       {@code ±$20} X window and {@code (subtype & $3F) << 3} in Y; the player is turned on
 *       their side, given {@code object_control} bits 6 and 1, and swung around the tube's X
 *       axis by the same cosine.</li>
 * </ul>
 *
 * <p><b>Both reverse-gravity rows are in the horizontal body.</b> {@code loc_48FBA}
 * (:95273-95284) mirrors {@code flip_angle} about the horizontal on the way out —
 * {@code addi.b #$40 / neg.b / subi.b #$40}, which is a reflection, not a negation — and
 * XORs {@code render_flags} bit 1; {@code loc_4904A} (:95320-95323) XORs the same bit every
 * frame of the ride. The vertical body reads the flag nowhere.
 */
public final class S3kDezGravityTubeObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {

    /** ROM {@code andi.w #$3F,d0 / lsl.w #3,d0} (:95172-95173, :95180-95181). */
    private static final int SPAN_SHIFT = 3;
    private static final int SPAN_MASK = 0x3F;
    /** ROM {@code move.w #$20,d0} / {@code move.w #$60,d0} on {@code subtype} bit 6 (:95183-95188). */
    private static final int HORIZONTAL_HALF_HEIGHT = 0x20;
    private static final int HORIZONTAL_HALF_HEIGHT_WIDE = 0x60;
    /** ROM {@code addi.w #$20,d0 / cmpi.w #$40,d0} (:95355-95357), the vertical X window. */
    private static final int VERTICAL_HALF_WIDTH = 0x20;

    /** ROM {@code move.w #$1000,d0} / {@code move.w #$5000,d0} (:95301-95305). */
    private static final int LIFT_AMPLITUDE = 0x1000;
    private static final int LIFT_AMPLITUDE_WIDE = 0x5000;
    /** ROM {@code moveq #8,d3} / {@code moveq #4,d3} (:95300, :95306): the angle step. */
    private static final int ANGLE_STEP = 8;
    private static final int ANGLE_STEP_WIDE = 4;
    /** ROM {@code muls.w #$1000,d1} (:95443), the vertical body's fixed amplitude. */
    private static final int SWING_AMPLITUDE = 0x1000;
    /** ROM {@code addq.b #8,(a2)} (:95451). */
    private static final int SWING_ANGLE_STEP = 8;

    /** {@code byte_48F90} (:95253): the narrow mount angle table. */
    private static final int[] MOUNT_ANGLES = {0x80, 0x80, 0x80, 0x40, 0x40, 0, 0, 0};
    /** {@code byte_48F98} (:95255): the {@code subtype} bit 6 table. */
    private static final int[] MOUNT_ANGLES_WIDE = {
        0x80, 0x80, 0x70, 0x60, 0x50, 0x40, 0x40, 0x30, 0x20, 0x10, 0, 0
    };
    /** {@code RawAni_491DA} (:95469): the vertical body's twenty-six rider frames. */
    private static final int[] SWING_FRAMES = {
        0x6D, 0x6D, 0x6E, 0x6E, 0x6F, 0x6F, 0x70, 0x70, 0x71, 0x71, 0x72, 0x72, 0x73,
        0x73, 0x74, 0x74, 0x75, 0x75, 0x76, 0x76, 0x77, 0x77, 0x6C, 0x6C, 0x6D, 0x6D
    };
    /** ROM {@code divu.w #$B,d2} (:95452). */
    private static final int SWING_FRAME_DIVISOR = 0x0B;

    /** ROM {@code move.b #$80,flip_type(a1)} (:95246). */
    private static final int RIDE_FLIP_TYPE = 0x80;
    /** ROM {@code move.b #4,flip_speed(a1)} (:95277, :95429). */
    private static final int EXIT_FLIP_SPEED = 4;
    /** ROM {@code cmpi.b #6,routine(a1) / bhs} (:95230): dead or hurt players are ignored. */
    private static final int SFX_PERIOD_MASK = 0x0F;

    private final RiderState playerOneState = new RiderState();
    private final RiderState playerTwoState = new RiderState();

    public S3kDezGravityTubeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZGravityTube");
    }

    /** {@code move.b subtype(a0),d0 / bpl.s loc_48EC8} (:95170-95171). */
    private boolean isVertical() {
        return (spawn.subtype() & 0x80) != 0;
    }

    /** {@code btst #6,subtype(a0)} (:95185, :95259, :95302). */
    private boolean isWide() {
        return (spawn.subtype() & 0x40) != 0;
    }

    /** {@code andi.w #$3F,d0 / lsl.w #3,d0} into {@code $30(a0)}: the half-span. */
    private int halfSpan() {
        return (spawn.subtype() & SPAN_MASK) << SPAN_SHIFT;
    }

    private int horizontalHalfHeight() {
        return isWide() ? HORIZONTAL_HALF_HEIGHT_WIDE : HORIZONTAL_HALF_HEIGHT;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        ObjectServices objectServices = tryServices();
        AbstractPlayableSprite playerOne = asSprite(objectServices == null
                ? playerEntity : objectServices.playerQuery().mainPlayerOrNull());
        if (playerOne == null) {
            playerOne = asSprite(playerEntity);
        }
        AbstractPlayableSprite playerTwo = objectServices == null ? null
                : asSprite(objectServices.playerQuery().nativeP2OrNull());
        if (playerTwo == playerOne) {
            playerTwo = null;
        }
        // loc_48EEC / loc_4906A (:95192-95204, :95369-95387): the same subroutine over
        // Player_1 with p1_standing_bit and Player_2 with p2_standing_bit.
        if (playerOne != null) {
            runFor(playerOne, playerOneState, vIntRunCount);
        }
        if (playerTwo != null) {
            runFor(playerTwo, playerTwoState, vIntRunCount);
        }
    }

    private void runFor(AbstractPlayableSprite player, RiderState state, int vIntRunCount) {
        if (isVertical()) {
            if (state.riding) {
                verticalRide(player, state, vIntRunCount);
            } else {
                verticalMount(player, state, vIntRunCount);
            }
            return;
        }
        if (state.riding) {
            horizontalRide(player, state, vIntRunCount);
        } else {
            horizontalMount(player, state);
        }
    }

    // ---- horizontal body: loc_48EEC / sub_48F12 ----

    /** {@code sub_48F12} :95216-95252. */
    private void horizontalMount(AbstractPlayableSprite player, RiderState state) {
        int halfSpan = halfSpan();
        int dx = (player.getCentreX() - getX() + halfSpan) & 0xFFFF;
        if (dx >= halfSpan * 2) {
            return;
        }
        int halfHeight = horizontalHalfHeight();
        int dyRaw = (short) (player.getCentreY() - getY());
        int dy = (dyRaw + halfHeight) & 0xFFFF;
        if (dy >= halfHeight * 2) {
            return;
        }
        if (isIgnorable(player)) {
            return;
        }
        setRide(player);
        // lsr.w #3,d1 then, for the bit 6 tube, lsr.w #1 again (:95238-95243). d1 is the
        // biased dy that the band test just accepted, so the index is always in range.
        int index = dy >> 3;
        int[] table = MOUNT_ANGLES_WIDE;
        if (isWide()) {
            index >>= 1;
        } else {
            table = MOUNT_ANGLES;
        }
        state.angle = table[Math.min(index, table.length - 1)];
        state.riding = true;
        player.setFlipType(RIDE_FLIP_TYPE);
        player.setAnimationId(1);
        if (player.getGSpeed() == 0) {
            player.setGSpeed((short) 1);
        }
    }

    /** {@code loc_48FA4} :95262-95330. */
    private void horizontalRide(AbstractPlayableSprite player, RiderState state,
                                int vIntRunCount) {
        if (player.getAir()) {
            // loc_48FF6 (:95292-95293): an airborne rider leaves with y_vel zeroed first.
            player.setYSpeed((short) 0);
            horizontalExit(player, state);
            return;
        }
        int halfSpan = halfSpan();
        int dx = (player.getCentreX() - getX() + halfSpan) & 0xFFFF;
        if (dx >= halfSpan * 2) {
            horizontalExit(player, state);
            return;
        }
        if (!player.isOnObject()) {
            return;
        }
        int amplitude = isWide() ? LIFT_AMPLITUDE_WIDE : LIFT_AMPLITUDE;
        int step = isWide() ? ANGLE_STEP_WIDE : ANGLE_STEP;
        // GetSineCosine returns the cosine in d1 (:3025); muls.w d0,d1 / swap d1 keeps the
        // high word, which is the 8.8 cosine scaled by the amplitude (:95307-95312).
        int lift = (TrigLookupTable.cosHex(state.angle) * amplitude) >> 16;
        NativePositionOps.writeYPosPreserveSubpixel(player, (getY() + lift) & 0xFFFF);
        player.setFlipAngle(state.angle);
        state.angle = (state.angle + step) & 0xFF;
        if (player.getGSpeed() == 0) {
            player.setGSpeed((short) 1);
            player.setAnimationId(1);
        }
        // loc_4904A (:95320-95323): eori.b #2,render_flags(a1) every frame the flag is set.
        // The engine composes the player's drawn Y flip from the flag at the draw already,
        // so the XOR's net effect is carried there; nothing is written here.
        playTunnelSfx(vIntRunCount);
    }

    /**
     * {@code loc_48FBA} :95272-95291. The reverse-gravity row is a <em>reflection</em> of
     * {@code flip_angle} about the horizontal, not a negation: {@code addi.b #$40,d0},
     * {@code neg.b d0}, {@code subi.b #$40,d0}.
     */
    private void horizontalExit(AbstractPlayableSprite player, RiderState state) {
        player.setOnObject(false);
        releaseRide(player);
        state.riding = false;
        player.setFlipsRemaining(0);
        player.setFlipSpeed(EXIT_FLIP_SPEED);
        player.setAir(true);
        if (!reverseGravityActive()) {
            return;
        }
        int flipAngle = (player.getFlipAngle() + 0x40) & 0xFF;
        flipAngle = (-flipAngle) & 0xFF;
        player.setFlipAngle((flipAngle - 0x40) & 0xFF);
    }

    // ---- vertical body: loc_4906A / sub_49090 ----

    /** {@code sub_49090} :95352-95410. */
    private void verticalMount(AbstractPlayableSprite player, RiderState state,
                               int vIntRunCount) {
        int dx = (player.getCentreX() - getX() + VERTICAL_HALF_WIDTH) & 0xFFFF;
        if (dx >= VERTICAL_HALF_WIDTH * 2) {
            return;
        }
        int halfSpan = halfSpan();
        int dy = (player.getCentreY() - getY() + halfSpan) & 0xFFFF;
        if (dy >= halfSpan * 2) {
            return;
        }
        if (isIgnorable(player) || player.isObjectControlled()) {
            return;
        }
        setRide(player);
        state.riding = true;
        player.setAngle((byte) 0xC0);
        player.setDirection(com.openggf.physics.Direction.RIGHT);
        // move.b #0,(a2) then #$80 when the player is left of the tube (:95396-95400).
        state.angle = (short) (player.getCentreX() - getX()) < 0 ? 0x80 : 0;
        // move.w y_vel(a1),ground_vel(a1) / neg.w (:95402-95403).
        int groundVel = -player.getYSpeed();
        if ((short) (player.getCentreY() - getY()) < 0) {
            player.setAngle((byte) 0x40);
            groundVel = -groundVel;
        }
        player.setGSpeed((short) groundVel);
        // loc_49120 sets only object_control bits 6 and 1. Bit 0 remains clear,
        // so Sonic_Control (loc_10BFC) still integrates the rider along angle $40/$C0.
        // Suppressing the engine movement loop freezes Y forever inside the tube.
        ObjectControlState.nativeBits0To6CpuAllowedMovementActive().applyTo(player);
        player.setObjectMappingFrameControl(true);
        player.setAnimationId(1);
        player.setFlipAngle(0);
        verticalRide(player, state, vIntRunCount);
    }

    /** {@code loc_49142} :95415-95461. */
    private void verticalRide(AbstractPlayableSprite player, RiderState state,
                              int vIntRunCount) {
        int halfSpan = halfSpan();
        int dy = (player.getCentreY() - getY() + halfSpan) & 0xFFFF;
        if (dy >= halfSpan * 2) {
            // loc_49142's exit (:95420-95428). No reverse-gravity row here.
            ObjectControlState.none().applyTo(player);
            player.setObjectMappingFrameControl(false);
            player.setOnObject(false);
            releaseRide(player);
            state.riding = false;
            player.setFlipAngle(1);
            player.setFlipsRemaining(0);
            player.setFlipSpeed(EXIT_FLIP_SPEED);
            player.setAir(true);
            return;
        }
        if (!player.isOnObject()) {
            return;
        }
        int swing = (TrigLookupTable.cosHex(state.angle) * SWING_AMPLITUDE) >> 16;
        NativePositionOps.writeXPosPreserveSubpixel(player, (getX() + swing) & 0xFFFF);
        int frameIndex = (state.angle & 0xFF) / SWING_FRAME_DIVISOR;
        player.setMappingFrame(SWING_FRAMES[Math.min(frameIndex, SWING_FRAMES.length - 1)]);
        state.angle = (state.angle + SWING_ANGLE_STEP) & 0xFF;
        playTunnelSfx(vIntRunCount);
    }

    // ---- shared ----

    /** {@code cmpi.b #6,routine(a1) / bhs} plus the debug-placement gate (:95230-95233). */
    private boolean isIgnorable(AbstractPlayableSprite player) {
        return player.getDead() || player.isHurt() || player.isDebugMode();
    }

    /**
     * The ROM clears {@code Status_OnObj} at both exits ({@code loc_48FBA} :95273 and
     * {@code loc_49142}'s :95420) and leaves {@code interact(a1)} pointing at the last
     * support, which the next {@code RideObject_SetRide} overwrites. The engine's latch is
     * read together with {@code Status_OnObj}, so it is dropped here rather than left stale.
     */
    private void releaseRide(AbstractPlayableSprite player) {
        if (player.getLatchedSolidObjectInstance() == this) {
            player.setLatchedSolidObject(0, null);
        }
    }

    /** {@code sub_33C34} (:70167-70187), the ROM's {@code RideObject_SetRide}. */
    private void setRide(AbstractPlayableSprite player) {
        ObjectServices objectServices = tryServices();
        if (objectServices != null && objectServices.objectManager() != null) {
            objectServices.objectManager().markObjectSupportThisFrame(player);
        }
        if (player.getAir()) {
            player.setXSpeed((short) 0);
            if (player.getRolling()) {
                // Player_ResetOnFloor_Part2's add.w d0,y_pos(a1), where d0 is the radius
                // the un-roll gives back; NativePositionOps owns playable native writes.
                int radiusDelta = player.getYRadius() - player.getStandYRadius();
                player.setRolling(false);
                NativePositionOps.addYPosPreserveSubpixel(player, radiusDelta);
            }
            player.setPushing(false);
            player.setJumping(false);
        }
        player.setOnObject(true);
        player.setAir(false);
        // move.w a0,interact(a1) (:70172). The rider's interact word points at the tube for
        // the whole ride, which is what keeps Player_AnglePos (:18736-18741) out of the
        // terrain probe: it returns on Status_OnObj before any FindFloor. The engine models
        // that non-solid ownership as a latch, the same way the CNZ wire cage and barber pole
        // do; without it the terrain walk-off detaches the rider every frame and the tube
        // re-mounts it the next one.
        player.setLatchedSolidObject(spawn.objectId(), this);
    }

    /**
     * {@code move.b (Level_frame_counter+1).w,d0 / andi.b #$F,d0 / bne} (:95324-95328,
     * :95454-95458): one {@code sfx_GravityTunnel} every sixteen frames, shared by both
     * bodies and keyed on the level clock rather than on the rider.
     */
    private void playTunnelSfx(int vIntRunCount) {
        if ((vIntRunCount & SFX_PERIOD_MASK) != 0) {
            return;
        }
        ObjectServices objectServices = tryServices();
        if (objectServices != null) {
            objectServices.playSfx(Sonic3kSfx.GRAVITY_TUNNEL.id);
        }
    }

    private boolean reverseGravityActive() {
        ObjectServices objectServices = tryServices();
        GameStateManager gameState =
                objectServices == null ? null : objectServices.gameState();
        return gameState != null && gameState.isReverseGravityActive();
    }

    private static AbstractPlayableSprite asSprite(PlayableEntity entity) {
        return entity instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    /** Test accessors. */
    public boolean isRidingForTest(boolean playerOne) {
        return (playerOne ? playerOneState : playerTwoState).riding;
    }

    public int angleForTest(boolean playerOne) {
        return (playerOne ? playerOneState : playerTwoState).angle;
    }

    public boolean isVerticalForTest() {
        return isVertical();
    }

    public boolean isWideForTest() {
        return isWide();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // dbglistobj Obj_DEZGravityTube carries no mappings; the visible tunnel is level art.
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(
                        playerOneState.riding, playerOneState.angle,
                        playerTwoState.riding, playerTwoState.angle));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot,
                                   RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            playerOneState.riding = extra.playerOneRiding();
            playerOneState.angle = extra.playerOneAngle();
            playerTwoState.riding = extra.playerTwoRiding();
            playerTwoState.angle = extra.playerTwoAngle();
        }
    }

    private record RewindExtra(boolean playerOneRiding, int playerOneAngle,
                               boolean playerTwoRiding, int playerTwoAngle)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }

    /**
     * The ROM keeps the standing bit in the object's own {@code status} and the angle byte in
     * the per-player global {@code _unkF7B0}. Only one tube can hold a given player at a time
     * — the standing bit is what gates the mount — so holding the angle beside the bit in the
     * object is the same state with the same lifetime, and it rewinds with the object.
     */
    private static final class RiderState {
        private boolean riding;
        private int angle;
    }
}
