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
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * SKL {@code $5C}, {@code Obj_DEZGravityHub} (sonic3k.asm:95545-95658).
 *
 * <p>Three act 2 placements, each paired with a {@code $5A} gravity tube above or below it:
 * {@code $0C40,$05C0} and {@code $32C0,$0640}/{@code $0840}. The hub is the junction the
 * tubes feed into — it catches the player, pulls them to its own centre, holds them spinning
 * there, and fires them out along whichever of the four exits the placement allows and the
 * player presses.
 *
 * <p>{@code Obj_DEZGravityHub} runs {@code sub_492D4} twice (:95546-95553), once with
 * {@code a1 = Player_1, a2 = $30(a0), d1 = Ctrl_1_logical} and once with
 * {@code a1 = Player_2, a2 = $32(a0), d1 = Ctrl_2_logical}, so each player gets an
 * independent two-byte block: {@code (a2)} is the state and {@code 1(a2)} the pose counter.
 *
 * <p><b>The state byte is a bit set, not a sequence.</b> {@code loc_49360} and
 * {@code loc_49386} do {@code bset #1,(a2)} and {@code bset #2,(a2)} as each axis reaches the
 * centre, on top of the {@code 1} the capture wrote, so the byte walks 1 → 3 or 5 → 7 and the
 * {@code cmpi.b #7,d0 / bhs} at :95594 is reached only when <em>both</em> axes are centred.
 * State {@code 8} is the post-launch state, and it only resets once the player has left the
 * same {@code $40} px window the capture used ({@code loc_49430}, :95677-95690).
 *
 * <p><b>The exit reads the press byte, not the held byte.</b> {@code d1} is the whole
 * {@code Ctrl_N_logical} word and {@code and.b subtype(a0),d1} (:95664) masks its <em>low</em>
 * byte, which is the press half. {@code word_49420} (:95672) is ordered up, down, left, right
 * to match the controller's own bit order, and {@code loc_49408} shifts until the first set
 * bit, so a player pressing two allowed directions at once leaves along the lower bit.
 *
 * <p>The object reads {@code Reverse_gravity_flag} nowhere: {@code $C00} out of a hub is
 * {@code $C00} out of a hub whichever way down is.
 */
public final class S3kDezGravityHubObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {

    /** {@code addi.w #$20,d0 / cmpi.w #$40,d0 / bhs} (:95560-95567), both axes. */
    private static final int WINDOW_BIAS = 0x20;
    private static final int WINDOW_SPAN = 0x40;
    /** {@code moveq #8,d1} (:95590, :95597): the per-frame pull toward the centre. */
    private static final int CENTRE_STEP = 8;
    /** {@code bset #1,(a2)} / {@code bset #2,(a2)} (:95369, :95390). */
    private static final int STATE_CAPTURED = 1;
    private static final int STATE_X_CENTRED = 2;
    private static final int STATE_Y_CENTRED = 4;
    private static final int STATE_CENTRED = STATE_CAPTURED | STATE_X_CENTRED | STATE_Y_CENTRED;
    /** {@code move.b #8,(a2)} (:95667): launched, waiting to leave the window. */
    private static final int STATE_LAUNCHED = 8;
    /** {@code word_49420} (:95672): up, down, left, right, in controller bit order. */
    private static final int[][] EXIT_VELOCITIES = {
        {0, -0xC00}, {0, 0xC00}, {-0xC00, 0}, {0xC00, 0}
    };
    /** Controller bit order: up, down, left, right (sonic3k.constants.asm button bits). */
    private static final int BUTTON_UP = 0x01;
    private static final int BUTTON_DOWN = 0x02;
    private static final int BUTTON_LEFT = 0x04;
    private static final int BUTTON_RIGHT = 0x08;
    private static final int DIRECTION_MASK =
            BUTTON_UP | BUTTON_DOWN | BUTTON_LEFT | BUTTON_RIGHT;

    /** {@code RawAni_493DA} (:95637): the held-in-the-hub pose, one entry per four frames. */
    private static final int[] POSE_FRAMES = {
        0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x31, 0x32, 0x33,
        0x76, 0x77, 0x6C, 0x6D, 0x6E, 0x6F, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75
    };
    /** {@code cmpi.b #$60,1(a2)} (:95629) and {@code lsr.w #2,d2} (:95634). */
    private static final int POSE_PERIOD = 0x60;
    private static final int POSE_SHIFT = 2;
    /** {@code move.b (Level_frame_counter+1).w,d0 / andi.b #$F,d0} (:95617-95619). */
    private static final int SFX_PERIOD_MASK = 0x0F;

    private final HubState playerOneState = new HubState();
    private final HubState playerTwoState = new HubState();

    public S3kDezGravityHubObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZGravityHub");
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
    }

    /** {@code sub_492D4} :95558-95690. */
    private void runFor(AbstractPlayableSprite player, HubState state) {
        int press = state.consumePressBits(player);
        if (state.state == 0) {
            tryCapture(player, state);
            return;
        }
        if (state.state < STATE_CENTRED) {
            // loc_49348 :95349-95398: pull toward the centre, then animate.
            pullTowardCentre(player, state);
            animate(player, state);
            return;
        }
        if (state.state == STATE_LAUNCHED) {
            // loc_49430 :95677-95690: hold the pose until the player leaves the window.
            if (insideWindow(player)) {
                animate(player, state);
                return;
            }
            release(player, state);
            return;
        }
        // loc_493F2 :95658-95670: centred, waiting for an allowed direction press.
        int allowed = press & spawn.subtype() & 0xFF;
        if (allowed != 0) {
            launch(player, state, allowed);
        }
        animate(player, state);
    }

    /** {@code sub_492D4}'s capture, :95559-95581. */
    private void tryCapture(AbstractPlayableSprite player, HubState state) {
        if (!insideWindow(player)) {
            return;
        }
        // btst #Status_OnObj (:95571): a player already standing on something is left alone,
        // which is what keeps the hub from stealing a rider off the tube that feeds it.
        if (player.isOnObject()) {
            return;
        }
        // cmpi.b #6,routine(a1) / bhs (:95573) plus the debug-placement gate (:95575).
        if (player.getDead() || player.isHurt() || player.isDebugMode()) {
            return;
        }
        player.setAir(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        // move.w #0,angle(a1) is a word write over angle and flip_angle (:95582).
        player.setAngle((byte) 0);
        player.setFlipAngle(0);
        // andi.b #$FC,render_flags(a1) (:95583) clears the X and Y mirror bits.
        player.setRenderFlips(false, false);
        // move.w #1,anim(a1) writes anim = Walk and prev_anim = Run, restarting the
        // animation; it does not select the Run id.
        player.setAnimationId(Sonic3kAnimationIds.WALK);
        player.publishRunAsPreviousAnimation();
        // move.b #$83,object_control(a1) (:95578): bit 7 with low bits set, the same shape
        // the gravity tube's vertical body writes.
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        player.setObjectMappingFrameControl(true);
        state.state = STATE_CAPTURED;
    }

    /** {@code loc_49348} :95349-95398: eight pixels per axis per frame, then a snap. */
    private void pullTowardCentre(AbstractPlayableSprite player, HubState state) {
        int dx = (short) (player.getCentreX() - getX());
        int stepX = dx < 0 ? -CENTRE_STEP : CENTRE_STEP;
        if (Math.abs(dx) < CENTRE_STEP) {
            NativePositionOps.writeXPosPreserveSubpixel(player, getX() & 0xFFFF);
            stepX = 0;
            state.state |= STATE_X_CENTRED;
        }
        if (stepX != 0) {
            NativePositionOps.writeXPosPreserveSubpixel(player,
                    (player.getCentreX() - stepX) & 0xFFFF);
        }
        int dy = (short) (player.getCentreY() - getY());
        int stepY = dy < 0 ? -CENTRE_STEP : CENTRE_STEP;
        if (Math.abs(dy) < CENTRE_STEP) {
            NativePositionOps.writeYPosPreserveSubpixel(player, getY() & 0xFFFF);
            stepY = 0;
            state.state |= STATE_Y_CENTRED;
        }
        if (stepY != 0) {
            NativePositionOps.writeYPosPreserveSubpixel(player,
                    (player.getCentreY() - stepY) & 0xFFFF);
        }
    }

    /** {@code loc_493F2} :95658-95670. */
    private void launch(AbstractPlayableSprite player, HubState state, int allowed) {
        int index = Integer.numberOfTrailingZeros(allowed);
        player.setXSpeed((short) EXIT_VELOCITIES[index][0]);
        player.setYSpeed((short) EXIT_VELOCITIES[index][1]);
        // move.b #0,object_control(a1) (:95669): the player has control back while the hub
        // still owns the pose, which is why the mapping-frame hold stays until the reset.
        ObjectControlState.none().applyTo(player);
        state.state = STATE_LAUNCHED;
    }

    /** {@code move.w #0,(a2)} (:95688): both bytes, state and pose counter. */
    private void release(AbstractPlayableSprite player, HubState state) {
        player.setObjectMappingFrameControl(false);
        state.state = 0;
        state.poseCounter = 0;
    }

    /** {@code loc_4939C} :95616-95641. */
    private void animate(AbstractPlayableSprite player, HubState state) {
        if ((levelFrameCounter() & SFX_PERIOD_MASK) == 0) {
            ObjectServices objectServices = tryServices();
            if (objectServices != null) {
                objectServices.playSfx(Sonic3kSfx.GRAVITY_TUNNEL.id);
            }
        }
        int pose = state.poseCounter;
        state.poseCounter = (state.poseCounter + 1) % POSE_PERIOD;
        player.setObjectMappingFrameControl(true);
        player.setMappingFrame(POSE_FRAMES[pose >> POSE_SHIFT]);
    }

    /** Both axes of {@code addi.w #$20 / cmpi.w #$40 / bhs} (:95560-95567, :95678-95686). */
    private boolean insideWindow(AbstractPlayableSprite player) {
        int dx = (player.getCentreX() - getX() + WINDOW_BIAS) & 0xFFFF;
        if (dx >= WINDOW_SPAN) {
            return false;
        }
        int dy = (player.getCentreY() - getY() + WINDOW_BIAS) & 0xFFFF;
        return dy < WINDOW_SPAN;
    }

    private int levelFrameCounter() {
        ObjectServices objectServices = tryServices();
        return objectServices == null || objectServices.levelManager() == null
                ? 0 : objectServices.levelManager().getFrameCounter();
    }

    private static AbstractPlayableSprite asSprite(PlayableEntity entity) {
        return entity instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    /** Test accessors. */
    public int stateForTest(boolean playerOne) {
        return (playerOne ? playerOneState : playerTwoState).state;
    }

    public int poseCounterForTest(boolean playerOne) {
        return (playerOne ? playerOneState : playerTwoState).poseCounter;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Obj_DEZGravityHub loads no mappings and never calls a draw routine: the visible
        // junction is the level's own art, and the object is the logic inside it.
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(
                        playerOneState.snapshot(), playerTwoState.snapshot()));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot,
                                   RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            playerOneState.restore(extra.playerOne());
            playerTwoState.restore(extra.playerTwo());
        }
    }

    private record RewindExtra(HubSnapshot playerOne, HubSnapshot playerTwo)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }

    private record HubSnapshot(int state, int poseCounter, int previousHeld) { }

    /** The ROM's two-byte block at {@code $30(a0)} / {@code $32(a0)}. */
    private static final class HubState {
        /** {@code (a2)}. */
        private int state;
        /** {@code 1(a2)}. */
        private int poseCounter;
        /**
         * The engine publishes held directions per player, not the ROM's press half of
         * {@code Ctrl_N_logical}. The edge is derived here, object-locally, so it is captured
         * and restored with the rest of the hub's state rather than read from a global.
         */
        private int previousHeld;

        private int consumePressBits(AbstractPlayableSprite player) {
            // getLogicalInputState is the engine's Ctrl_N_logical, in the pad's own bit
            // order, which is the word the ROM passes in d1 (:95548, :95552).
            int held = player.getLogicalInputState() & DIRECTION_MASK;
            int press = held & ~previousHeld;
            previousHeld = held;
            return press;
        }

        private HubSnapshot snapshot() {
            return new HubSnapshot(state, poseCounter, previousHeld);
        }

        private void restore(HubSnapshot snapshot) {
            state = snapshot.state();
            poseCounter = snapshot.poseCounter();
            previousHeld = snapshot.previousHeld();
        }
    }
}
