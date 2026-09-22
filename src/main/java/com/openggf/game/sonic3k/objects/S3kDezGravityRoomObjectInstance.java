package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * SKL {@code $5F}, {@code Obj_DEZGravityRoom} (sonic3k.asm:95814-95952).
 *
 * <p>One act 1 placement: the turbine corridor, a {@code $500} px long room the player is
 * blown rightwards through while steering up and down. {@code sub_4964A} runs once per player
 * over a single state byte — {@code $30(a0)} for Player 1 and {@code $31(a0)} for Player 2.
 *
 * <p><b>The corridor only catches a player to its right.</b> {@code sub.w x_pos(a0),d0 /
 * cmpi.w #$500,d0 / bhs} (:95847-95849) is an <em>unsigned</em> compare on the raw difference,
 * so a player left of the object wraps to a huge value and is skipped; the window is
 * {@code [x, x+$500)} in X and {@code ±$140} in Y. The same compare is the release test
 * (:95871-95875), which is why leaving the far end and backing out of the near end both simply
 * hand control back.
 *
 * <p><b>The blow is an uncapped accumulation and the steering is clamped.</b>
 * {@code addi.w #$38,x_vel(a1)} (:95872) has no ceiling of its own — the corridor's length is
 * the limit. Up and down each move {@code y_vel} by {@code $18} toward {@code ∓$600}
 * (:95874-95908), and the clamp is written so that a player already past {@code ±$600} keeps
 * the speed they arrived with rather than being pulled back to the limit. Then
 * {@code asr.w #5} of {@code y_vel} is subtracted from it as drag, with the borrow deciding
 * whether the result crosses zero and is flattened (:95910-95932).
 *
 * <p>The object moves and collides the player itself ({@code MoveSprite2},
 * {@code Player_JumpAngle}, {@code SonicKnux_DoLevelCollision}, :95733-95736) because
 * {@code object_control = 1} has stopped the player's own movement, and then sets
 * {@code Status_InAir} again (:95737) so a landing inside the corridor never sticks.
 *
 * <p>It reads {@code Reverse_gravity_flag} nowhere, and act 1 has no gravity flip in it.
 */
public final class S3kDezGravityRoomObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {

    /** {@code cmpi.w #$500,d0 / bhs} (:95849, :95875): the corridor's length. */
    private static final int CORRIDOR_LENGTH = 0x500;
    /** {@code addi.w #$140,d0 / cmpi.w #$280,d0 / bhs} (:95852-95854). */
    private static final int HALF_HEIGHT = 0x140;
    /** {@code addi.w #$38,x_vel(a1)} (:95872). */
    private static final int BLOW_ACCELERATION = 0x38;
    /** {@code move.w #$18,d5} (:95874): the steering step. */
    private static final int STEER_STEP = 0x18;
    /** {@code move.w #$600,d6} (:95873): the steering limit. */
    private static final int STEER_LIMIT = 0x600;
    /** {@code asr.w #5,d1} (:95919): the drag divisor. */
    private static final int DRAG_SHIFT = 5;
    /** {@code move.b (Level_frame_counter+1).w,d0 / andi.b #$F,d0} (:95738-95740). */
    private static final int SFX_PERIOD_MASK = 0x0F;

    private final RoomState playerOneState = new RoomState();
    private final RoomState playerTwoState = new RoomState();

    public S3kDezGravityRoomObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZGravityRoom");
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
        if (playerOne != null) {
            runFor(playerOne, playerOneState);
        }
        if (playerTwo != null) {
            runFor(playerTwo, playerTwoState);
        }
        // The manager runs this object's extended range tail after both player slots.
        // Its normal unload path clears the placement's live respawn bit.
    }

    @Override
    public boolean checksOutOfRangeAfterRoutine() {
        return true;
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        // Obj_DEZGravityRoom (:95823-95828): unlike RememberState's normal range,
        // the anchor is shifted $400 right and the native unsigned limit is $680.
        // The viewport term follows the engine's existing widescreen window policy.
        // Keeping the owner alive is essential while object_control suppresses movement.
        int coarseBack = (cameraX - 0x80) & 0xFF80;
        int shiftedAnchor = (getX() + 0x400) & 0xFF80;
        return ((shiftedAnchor - coarseBack) & 0xFFFF) > 0x400 + coarseXCullRange();
    }

    /** {@code sub_4964A} :95843-95952. */
    private void runFor(AbstractPlayableSprite player, RoomState state) {
        if (!state.captured) {
            tryCapture(player, state);
            return;
        }
        // loc_496A8 :95869-95876.
        if (player.isDebugMode() || !insideCorridor(player)) {
            ObjectControlState.none().applyTo(player);
            state.captured = false;
            return;
        }
        blow(player);
        moveAndCollide(player);
        playTurbineSfx();
    }

    /** {@code sub_4964A}'s capture, :95845-95867. */
    private void tryCapture(AbstractPlayableSprite player, RoomState state) {
        if (!insideCorridor(player)) {
            return;
        }
        int dy = (player.getCentreY() - getY() + HALF_HEIGHT) & 0xFFFF;
        if (dy >= HALF_HEIGHT * 2) {
            return;
        }
        if (player.isDebugMode()) {
            return;
        }
        // move.b #1,flip_angle / move.b #-1,flips_remaining / move.b #4,flip_speed
        // (:95858-95861): a non-zero flips_remaining is the endless tumble through the room.
        player.setFlipAngle(1);
        player.setAnimationId(Sonic3kAnimationIds.WALK);
        player.setFlipsRemaining(-1);
        player.setFlipSpeed(4);
        player.setRollingJump(false);
        player.setJumping(false);
        player.setAir(true);
        // move.b #1,object_control(a1) (:95866): bits 0-6 with bit 7 clear, so the player can
        // still jump out while their own movement is the object's to run.
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        state.captured = true;
    }

    /** {@code loc_496C8} :95872-95932. */
    private void blow(AbstractPlayableSprite player) {
        player.setXSpeed((short) (player.getXSpeed() + BLOW_ACCELERATION));
        int yVel = player.getYSpeed();
        if (player.isUpPressed()) {
            yVel = steer(yVel, -STEER_STEP, -STEER_LIMIT);
        }
        if (player.isDownPressed()) {
            yVel = steer(yVel, STEER_STEP, STEER_LIMIT);
        }
        player.setYSpeed((short) yVel);
        player.setYSpeed((short) applyDrag(yVel));
    }

    /**
     * {@code loc_496DA} / {@code loc_496F2} (:95876-95908). The step is applied, and only
     * taken back when it would carry the player past the limit; a player already beyond the
     * limit is left where they are rather than pulled back to it.
     */
    private static int steer(int yVel, int step, int limit) {
        int stepped = (short) (yVel + step);
        boolean pastLimit = step < 0 ? stepped <= limit : stepped >= limit;
        if (!pastLimit) {
            return stepped;
        }
        boolean alreadyPastLimit = step < 0 ? yVel <= limit : yVel >= limit;
        return alreadyPastLimit ? yVel : limit;
    }

    /**
     * {@code loc_49706} :95910-95932. {@code d1 = y_vel asr 5}, then {@code y_vel -= d1} with
     * the 68000 borrow deciding whether the subtraction crossed zero, in which case the
     * result is flattened to zero.
     */
    private static int applyDrag(int yVel) {
        int drag = (short) yVel >> DRAG_SHIFT;
        if (drag == 0) {
            return yVel;
        }
        int minuend = yVel & 0xFFFF;
        int subtrahend = drag & 0xFFFF;
        boolean borrow = minuend < subtrahend;
        int result = (short) (minuend - subtrahend);
        if (drag > 0) {
            // bcc keeps the result; a borrow means it went past zero.
            return borrow ? 0 : result;
        }
        // bcs keeps the result; no borrow means it went past zero.
        return borrow ? result : 0;
    }

    /** {@code loc_49730} :95733-95737. */
    private void moveAndCollide(AbstractPlayableSprite player) {
        player.move(player.getXSpeed(), player.getYSpeed());
        jumpAngle(player);
        ObjectServices objectServices = tryServices();
        CollisionSystem collisionSystem =
                objectServices == null ? null : objectServices.collisionSystem();
        if (collisionSystem != null) {
            collisionSystem.resolveAirCollision(player, landed -> { });
        }
        // bset #Status_InAir,status(a1) (:95737): a landing inside the corridor is undone on
        // the same frame it happens, which is what keeps the blow going over floor terrain.
        player.setAir(true);
    }

    /**
     * {@code Player_JumpAngle} (:24518-24534): {@code angle} walks toward 0 by two a frame and
     * stops there. The corridor calls it directly because the player's own airborne tail is
     * not running while {@code object_control} is set.
     */
    private static void jumpAngle(AbstractPlayableSprite player) {
        int angle = player.getAngle() & 0xFF;
        if (angle == 0) {
            return;
        }
        if (angle >= 0x80) {
            int raised = angle + 2;
            player.setAngle((byte) (raised > 0xFF ? 0 : raised));
            return;
        }
        int lowered = angle - 2;
        player.setAngle((byte) (lowered < 0 ? 0 : lowered));
    }

    /** {@code addi.w #$38} only runs inside the same window the capture used. */
    private boolean insideCorridor(AbstractPlayableSprite player) {
        return ((player.getCentreX() - getX()) & 0xFFFF) < CORRIDOR_LENGTH;
    }

    private void playTurbineSfx() {
        ObjectServices objectServices = tryServices();
        if (objectServices == null) {
            return;
        }
        int frameCounter = objectServices.levelManager() == null
                ? 0 : objectServices.levelManager().getFrameCounter();
        if ((frameCounter & SFX_PERIOD_MASK) == 0) {
            objectServices.playSfx(Sonic3kSfx.TURBINE_HUM.id);
        }
    }

    private static AbstractPlayableSprite asSprite(PlayableEntity entity) {
        return entity instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    /** Test accessors. */
    public boolean isCapturedForTest(boolean playerOne) {
        return (playerOne ? playerOneState : playerTwoState).captured;
    }

    /** Exposed for the drag and steering assertions, which are pure ROM arithmetic. */
    public static int steerForTest(int yVel, int step, int limit) {
        return steer(yVel, step, limit);
    }

    public static int dragForTest(int yVel) {
        return applyDrag(yVel);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Obj_DEZGravityRoom loads no mappings and never draws: the corridor is level art.
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context)
                .withObjectSubclassExtra(
                        new RewindExtra(playerOneState.captured, playerTwoState.captured));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot,
                                   RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            playerOneState.captured = extra.playerOne();
            playerTwoState.captured = extra.playerTwo();
        }
    }

    private record RewindExtra(boolean playerOne, boolean playerTwo)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }

    /** The ROM's single byte at {@code $30(a0)} / {@code $31(a0)}. */
    private static final class RoomState {
        private boolean captured;
    }
}
