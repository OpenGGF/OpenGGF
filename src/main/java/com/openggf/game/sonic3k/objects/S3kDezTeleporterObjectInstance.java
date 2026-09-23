package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.Tails;

import java.util.List;

/**
 * SKL {@code $59}, {@code Obj_DEZTeleporter} (sonic3k.asm:94913-95168).
 *
 * <p>Twenty-one of these sit in Death Egg act 2, always in vertically paired columns: the
 * placement table has them at matching {@code x} with one high and one low, and the pair's
 * two subtypes carry opposite bit 7s, so riding one flips gravity and riding its partner
 * flips it back.
 *
 * <p>The object is a thin dispatcher. {@code Obj_DEZTeleporter} runs {@code sub_48C30}
 * twice, once with {@code a1 = Player_1, a4 = $30(a0)} and once with
 * {@code a1 = Player_2, a4 = $3A(a0)} (:94914-94919), so each player gets an independent
 * ten-byte state block and an independent routine index. The jump table {@code off_48C3C}
 * (:94933-94936) has four entries:
 *
 * <ol>
 *   <li><b>Capture</b> ({@code loc_48C44}, :94939-94990).</li>
 *   <li><b>Spin-up</b> ({@code loc_48D2C}, :94992-95006), which ramps {@code 4(a4)} to
 *       {@code $300} and only then launches.</li>
 *   <li><b>Ride</b> ({@code loc_48DCA}, :95064-95158), which owns the flag write and the
 *       exit nudge.</li>
 *   <li><b>Release</b> ({@code loc_48E94}, :95160-95168).</li>
 * </ol>
 *
 * <p><b>The capture window is mirrored, not widened.</b> {@code addq.w #3,d0} then, when
 * {@code status} bit 0 is set, {@code addi.w #$A,d0} before {@code cmpi.w #$10,d0 / bhs}
 * (:94944-94952). Unflipped that is {@code -3 <= dx <= $C}; flipped it is
 * {@code -$D <= dx <= 2}. The window is the same {@code $10} px wide either way and simply
 * sits on the other side of the object's own centre, which is what a mirrored placement
 * needs. The Y window is a plain {@code -$20 <= dy < $20} (:94953-94957).
 *
 * <p><b>The flag write is the midpoint, Player 1 only</b> ({@code loc_48DCA}, :95065-95080):
 * {@code cmp.w d2,d1 / bne} requires the remaining frame budget {@code 6(a4)} to equal the
 * half budget {@code 8(a4)}, and {@code cmpa.w #Player_1,a1} rejects Player 2 outright. The
 * value written is subtype bit 7 ({@code rol.b #1,d0 / andi.b #1,d0}), and {@code 1(a4)} is
 * latched when that value <em>differs</em> from the flag it replaces — the exit offsets read
 * that latch to decide how far to nudge the player as control is returned.
 */
public final class S3kDezTeleporterObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {

    /** ROM {@code addq.w #3,d0} (:94946) and {@code cmpi.w #$10,d0} (:94952). */
    private static final int CAPTURE_X_BIAS = 3;
    private static final int CAPTURE_X_WIDTH = 0x10;
    /** ROM {@code addi.w #$A,d0} (:94951) when {@code status} bit 0 is set. */
    private static final int CAPTURE_X_MIRROR_BIAS = 0x0A;
    /** ROM {@code addi.w #$20,d1} / {@code cmpi.w #$40,d1} (:94955-94957). */
    private static final int CAPTURE_Y_BIAS = 0x20;
    private static final int CAPTURE_Y_HEIGHT = 0x40;

    /** ROM {@code addq.w #8,4(a4)} to {@code cmpi.w #$300,4(a4)} (:94993-94995). */
    private static final int SPIN_STEP = 8;
    private static final int SPIN_LAUNCH = 0x300;
    /** ROM {@code move.w #$1000,d0}, negated unless {@code status} bit 1 is set (:95001-95005). */
    private static final int LAUNCH_Y_VEL = 0x1000;
    /** ROM {@code cmpi.w #$C00,d0 / subi.w #$C00,d0} (:95009-95013): the pose phase wrap. */
    private static final int POSE_PHASE_WRAP = 0x0C00;
    /** ROM {@code move.b #6,2(a4)} when {@code Status_Facing} is set (:95058). */
    private static final int POSE_PHASE_FACING_LEFT = 6;

    /** {@code RawAni_48DB2} (:95051): the twelve captured-player mapping frames. */
    private static final int[] POSE_FRAMES = {
        0x55, 0x59, 0x5A, 0x5B, 0x5A, 0x59, 0x55, 0x56, 0x57, 0x58, 0x57, 0x56
    };
    /** {@code byte_48DBE} (:95061): the X-flip bit paired with each pose frame. */
    private static final int[] POSE_FLIP_BITS = {0, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0};

    /** ROM {@code cmpi.w #5,d1} and {@code subq.w #5,d2} (:95082, :95086). */
    private static final int INVULNERABLE_WINDOW_MARGIN = 5;

    /** ROM {@code moveq #9,d0} / {@code moveq #$11,d0} for {@code character_id == 1} (:95121-95125). */
    private static final int EXIT_NUDGE = 9;
    private static final int EXIT_NUDGE_TAILS = 0x11;
    /** ROM {@code moveq #7,d0} / {@code moveq #-7,d0} and the {@code subq/addq #8} for Tails. */
    private static final int EXIT_NUDGE_SHORT = 7;
    private static final int EXIT_NUDGE_SHORT_TAILS_DELTA = 8;
    /** ROM {@code moveq #$10,d0} / {@code moveq #-$10,d0} when {@code 1(a4)} is latched. */
    private static final int EXIT_NUDGE_FLIPPED = 0x10;

    /** ROM {@code addi.w #$10,d0 / cmpi.w #$20,d0} (:95162-95164). */
    private static final int RELEASE_X_HALF_WIDTH = 0x10;

    private enum Routine { CAPTURE, SPIN_UP, RIDE, RELEASE }

    private final RiderState playerOneState = new RiderState();
    private final RiderState playerTwoState = new RiderState();

    public S3kDezTeleporterObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZTeleporter");
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
        // Obj_DEZTeleporter (:94914-94919): the same subroutine, twice, over two state blocks.
        if (playerOne != null) {
            runFor(playerOne, playerOneState, true);
        }
        if (playerTwo != null) {
            runFor(playerTwo, playerTwoState, false);
        }
    }

    private void runFor(AbstractPlayableSprite player, RiderState state, boolean isPlayerOne) {
        switch (state.routine) {
            case CAPTURE -> tryCapture(player, state);
            case SPIN_UP -> spinUp(player, state);
            case RIDE -> ride(player, state, isPlayerOne);
            case RELEASE -> release(player, state);
            default -> state.reset();
        }
    }

    /** {@code loc_48C44} (:94939-94990). */
    private void tryCapture(AbstractPlayableSprite player, RiderState state) {
        int dx = (player.getCentreX() - getX() + CAPTURE_X_BIAS
                + (xFlippedPlacement() ? CAPTURE_X_MIRROR_BIAS : 0)) & 0xFFFF;
        if (dx >= CAPTURE_X_WIDTH) {
            return;
        }
        int dy = (player.getCentreY() - getY() + CAPTURE_Y_BIAS) & 0xFFFF;
        if (dy >= CAPTURE_Y_HEIGHT) {
            return;
        }
        // loc_48C44: boss defeat ($7FBD6) sets _unkFAB8 bit 0. Existing riders
        // finish their routine, but neither native player may enter a fresh ride.
        if (player.isObjectControlled() || player.getAir()) {
            return;
        }
        if (services().zoneRuntimeState() instanceof com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState dez
                && (dez.bossSignals() & 1) != 0) {
            return;
        }
        if (anotherTeleporterStillHolds(state == playerOneState)) {
            return;
        }
        capture(player, state);
    }

    /**
     * {@code loc_48CB0} (:94974-94990). The un-roll adjustment is the reverse-gravity row
     * ({@code tst.b (Reverse_gravity_flag).w / neg.w d0}, :94987-94989): the radius change
     * moves the player's centre the other way once down is up.
     */
    private void capture(AbstractPlayableSprite player, RiderState state) {
        state.routine = Routine.SPIN_UP;
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setObjectMappingFrameControl(true);
        player.setAnimationId(0);
        player.setGSpeed((short) 0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setPushing(false);
        player.setAir(true);
        NativePositionOps.writeXPosPreserveSubpixel(player, getX());
        if (player.getRolling()) {
            int radiusDelta = player.getYRadius() - player.getStandYRadius();
            player.applyCustomRadii(player.getStandXRadius(), player.getStandYRadius());
            player.setRollingFlagPreserveRadii(false);
            if (reverseGravityActive()) {
                radiusDelta = -radiusDelta;
            }
            NativePositionOps.addYPosPreserveSubpixel(player, radiusDelta);
        }
        state.flagChangedLatch = false;
        // clr.w 2(a4) / move.b #6,2(a4) when Status_Facing is set (:95055-95058): the pose
        // phase starts half a turn round for a player who entered facing left.
        state.posePhase = player.getDirection() == Direction.LEFT
                ? POSE_PHASE_FACING_LEFT << 8 : 0;
        state.spin = 0;
    }

    /** {@code loc_48D2C} (:94992-95006), then the shared pose update. */
    private void spinUp(AbstractPlayableSprite player, RiderState state) {
        state.spin += SPIN_STEP;
        if (state.spin == SPIN_LAUNCH) {
            int budget = spawn.subtype() & 0x7F;
            state.budget = budget;
            state.halfBudget = budget >> 1;
            player.setYSpeed((short) (yFlippedPlacement() ? LAUNCH_Y_VEL : -LAUNCH_Y_VEL));
            state.routine = Routine.RIDE;
            ObjectServices objectServices = tryServices();
            if (objectServices != null) {
                objectServices.playSfx(Sonic3kSfx.SUPER_TRANSFORM.id);
            }
        }
        updatePose(player, state);
    }

    /** {@code loc_48DCA} (:95064-95158). */
    private void ride(AbstractPlayableSprite player, RiderState state, boolean isPlayerOne) {
        if (state.budget == state.halfBudget && isPlayerOne) {
            writeFlagAtMidpoint(state);
        }
        applyInvulnerabilityWindow(player, state);
        state.budget--;
        if (state.budget >= 0) {
            // move.l y_pos(a1),d3 / asl.l #8,d0 / add.l d0,d3 (:95095-95101).
            NativePositionOps.addYPos16_16(player, player.getYSpeed() << 8);
            updatePose(player, state);
            return;
        }
        finishRide(player, state);
    }

    /** {@code loc_48DCA} :95065-95080: the only gravity write this object makes. */
    private void writeFlagAtMidpoint(RiderState state) {
        GameStateManager gameState = gameStateOrNull();
        if (gameState == null) {
            return;
        }
        // rol.b #1,d0 / andi.b #1,d0 (:95072-95074): subtype bit 7, nothing else.
        boolean target = (spawn.subtype() & 0x80) != 0;
        state.flagChangedLatch = target != gameState.isReverseGravityActive();
        gameState.setReverseGravityActive(target);
    }

    /**
     * {@code loc_48DF6} (:95081-95092). The rider is invulnerable only across the middle of
     * the ride: the timer is cleared every frame, then set when the remaining budget is at
     * least 5 and still below {@code 2 * halfBudget - 5}.
     */
    private void applyInvulnerabilityWindow(AbstractPlayableSprite player, RiderState state) {
        player.setInvulnerableFrames(0);
        if (state.budget < INVULNERABLE_WINDOW_MARGIN) {
            return;
        }
        int upper = state.halfBudget * 2 - INVULNERABLE_WINDOW_MARGIN;
        if (state.budget < upper) {
            player.setInvulnerableFrames(1);
        }
    }

    /**
     * {@code loc_48E2C} (:95103-95158). Control returns, and the player is nudged by an
     * offset chosen from three things: subtype bit 7, {@code status} bit 1, and whether the
     * midpoint write actually changed the flag.
     */
    private void finishRide(AbstractPlayableSprite player, RiderState state) {
        state.routine = Routine.RELEASE;
        ObjectControlState.none().applyTo(player);
        player.setObjectMappingFrameControl(false);
        player.setYSpeed((short) 0);

        boolean tails = isTails(player);
        int nudge = tails ? EXIT_NUDGE_TAILS : EXIT_NUDGE;
        boolean latched = state.flagChangedLatch;
        if ((spawn.subtype() & 0x80) != 0) {
            nudge = -nudge;
            if (!yFlippedPlacement()) {
                if (latched) {
                    return;
                }
            } else {
                nudge = latched ? EXIT_NUDGE_FLIPPED
                        : EXIT_NUDGE_SHORT - (tails ? EXIT_NUDGE_SHORT_TAILS_DELTA : 0);
            }
        } else if (yFlippedPlacement()) {
            if (latched) {
                return;
            }
        } else {
            nudge = latched ? -EXIT_NUDGE_FLIPPED
                    : -EXIT_NUDGE_SHORT + (tails ? EXIT_NUDGE_SHORT_TAILS_DELTA : 0);
        }
        NativePositionOps.addYPosPreserveSubpixel(player, nudge);
    }

    /** {@code loc_48E94} (:95160-95168): the block is only reusable once the player leaves. */
    private void release(AbstractPlayableSprite player, RiderState state) {
        int dx = (player.getCentreX() - getX() + RELEASE_X_HALF_WIDTH) & 0xFFFF;
        if (dx < RELEASE_X_HALF_WIDTH * 2) {
            return;
        }
        state.reset();
    }

    /**
     * {@code loc_48D66} (:95008-95022). The pose phase is a word that advances by the spin
     * ramp and wraps at {@code $C00}; its <em>high</em> byte ({@code move.b 2(a4),d0} on a
     * big-endian word) indexes the twelve-entry tables. The reverse-gravity row is
     * {@code ori.b #2,d0} on the flip byte (:95018-95020), which the engine already composes
     * at the draw from the same flag, so only the X flip is written here.
     */
    private void updatePose(AbstractPlayableSprite player, RiderState state) {
        int phase = state.posePhase + state.spin;
        if (phase >= POSE_PHASE_WRAP) {
            phase -= POSE_PHASE_WRAP;
        }
        state.posePhase = phase & 0xFFFF;
        int index = (state.posePhase >> 8) % POSE_FRAMES.length;
        player.setMappingFrame(POSE_FRAMES[index]);
        player.setDirection(POSE_FLIP_BITS[index] != 0 ? Direction.LEFT : Direction.RIGHT);
    }

    /**
     * {@code movea.w interact(a1),a3 / cmpi.l #Obj_DEZTeleporter,(a3) / tst.b (a3,d0.w)}
     * (:94968-94973): a player whose last interaction was another teleporter that still has a
     * live state block for them is not captured again. Modelled by asking the other
     * teleporters directly, which is the same question without a raw RAM pointer.
     */
    private boolean anotherTeleporterStillHolds(boolean playerOneSlot) {
        ObjectServices objectServices = tryServices();
        if (objectServices == null || objectServices.objectManager() == null) {
            return false;
        }
        for (ObjectInstance instance : objectServices.objectManager().getActiveObjects()) {
            if (instance == this
                    || !(instance instanceof S3kDezTeleporterObjectInstance other)) {
                continue;
            }
            if (other.holdsRiderInSlot(playerOneSlot)) {
                return true;
            }
        }
        return false;
    }

    /** {@code tst.b (a3,d0.w)}: the <em>same</em> slot in the other object, not either slot. */
    private boolean holdsRiderInSlot(boolean playerOneSlot) {
        return (playerOneSlot ? playerOneState : playerTwoState).routine != Routine.CAPTURE;
    }

    private boolean xFlippedPlacement() {
        return (spawn.renderFlags() & 1) != 0;
    }

    private boolean yFlippedPlacement() {
        return (spawn.renderFlags() & 2) != 0;
    }

    private boolean reverseGravityActive() {
        GameStateManager gameState = gameStateOrNull();
        return gameState != null && gameState.isReverseGravityActive();
    }

    private GameStateManager gameStateOrNull() {
        ObjectServices objectServices = tryServices();
        return objectServices == null ? null : objectServices.gameState();
    }

    /** ROM {@code cmpi.b #1,character_id(a1)} (:95123, :95132, :95147): character 1 is Tails. */
    private static boolean isTails(AbstractPlayableSprite player) {
        return player instanceof Tails;
    }

    private static AbstractPlayableSprite asSprite(PlayableEntity entity) {
        return entity instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    /** Test accessors for the two state blocks. */
    public boolean isRidingForTest(boolean playerOne) {
        RiderState state = playerOne ? playerOneState : playerTwoState;
        return state.routine == Routine.SPIN_UP || state.routine == Routine.RIDE;
    }

    public int remainingBudgetForTest(boolean playerOne) {
        return (playerOne ? playerOneState : playerTwoState).budget;
    }

    public boolean exitLatchForTest(boolean playerOne) {
        return (playerOne ? playerOneState : playerTwoState).flagChangedLatch;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // dbglistobj Obj_DEZTeleporter carries no mappings: the ROM object is invisible and
        // the visible ring column beside it is Obj_DEZTransRingSpawner, which slice 5 owns.
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

    private record RewindExtra(RiderSnapshot playerOne, RiderSnapshot playerTwo)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }

    private record RiderSnapshot(Routine routine, boolean flagChangedLatch, int posePhase,
                                 int spin, int budget, int halfBudget) { }

    /** The ROM's ten-byte block at {@code $30(a0)} / {@code $3A(a0)}. */
    private static final class RiderState {
        private Routine routine = Routine.CAPTURE;
        /** {@code 1(a4)}: set when the midpoint write changed the flag. */
        private boolean flagChangedLatch;
        /** {@code 2(a4)}: the pose phase word. */
        private int posePhase;
        /** {@code 4(a4)}: the spin ramp. */
        private int spin;
        /** {@code 6(a4)} and {@code 8(a4)}. */
        private int budget;
        private int halfBudget;

        private void reset() {
            routine = Routine.CAPTURE;
            flagChangedLatch = false;
            posePhase = 0;
            spin = 0;
            budget = 0;
            halfBudget = 0;
        }

        private RiderSnapshot snapshot() {
            return new RiderSnapshot(routine, flagChangedLatch, posePhase, spin,
                    budget, halfBudget);
        }

        private void restore(RiderSnapshot state) {
            routine = state.routine();
            flagChangedLatch = state.flagChangedLatch();
            posePhase = state.posePhase();
            spin = state.spin();
            budget = state.budget();
            halfBudget = state.halfBudget();
        }
    }
}
