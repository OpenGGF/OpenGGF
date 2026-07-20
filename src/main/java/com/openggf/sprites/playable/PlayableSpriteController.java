package com.openggf.sprites.playable;

import com.openggf.level.objects.PerObjectRewindSnapshot.PlayerRewindExtra;
import com.openggf.sprites.managers.PlayableSpriteAnimation;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.sprites.managers.TailsFlightController;
import com.openggf.sprites.managers.TailsTailsController;

@com.openggf.game.ModApi
public class PlayableSpriteController {
    private final PlayableSpriteMovement movement;
    private final PlayableSpriteAnimation animation;
    private final DrowningController drowning;
    private final TailsFlightController tailsFlight;
    private final TailsCarryController tailsCarry;
    private SpindashDustController spindashDust;
    private TailsTailsController tailsTails;
    private SuperStateController superState;

    public PlayableSpriteController(AbstractPlayableSprite sprite) {
        this.movement = new PlayableSpriteMovement(sprite);
        this.animation = new PlayableSpriteAnimation(sprite);
        this.drowning = new DrowningController(sprite);
        this.tailsFlight = new TailsFlightController(sprite);
        this.tailsCarry = new TailsCarryController(sprite);
    }

    public PlayableSpriteMovement getMovement() {
        return movement;
    }

    public PlayableSpriteAnimation getAnimation() {
        return animation;
    }

    public DrowningController getDrowning() {
        return drowning;
    }

    public TailsFlightController getTailsFlight() {
        return tailsFlight;
    }

    public TailsCarryController getTailsCarry() {
        return tailsCarry;
    }

    public SpindashDustController getSpindashDust() {
        return spindashDust;
    }

    public void setSpindashDust(SpindashDustController spindashDust) {
        this.spindashDust = spindashDust;
    }

    public TailsTailsController getTailsTails() {
        return tailsTails;
    }

    public void setTailsTails(TailsTailsController tailsTails) {
        this.tailsTails = tailsTails;
    }

    public SuperStateController getSuperState() {
        return superState;
    }

    public void setSuperState(SuperStateController superState) {
        this.superState = superState;
    }

    public RewindState captureRewindState() {
        return new RewindState(
                movement != null ? movement.captureRewindState() : null,
                spindashDust != null ? spindashDust.captureRewindState() : null,
                animation != null ? animation.captureRewindState() : null,
                drowning != null ? drowning.captureRewindState() : null,
                tailsCarry != null ? tailsCarry.capture() : null,
                superState != null ? superState.captureRewindState() : null);
    }

    public void restoreRewindState(RewindState state) {
        restoreRewindParticipants(state);
        TailsCarryController.Snapshot carryState = state != null ? state.tailsCarryState() : null;
        if (tailsCarry != null) {
            if (carryState != null) {
                tailsCarry.restore(carryState);
            } else {
                tailsCarry.clearAndReleaseMain();
            }
        }
    }

    void restoreRewindStateWithoutCarry(RewindState state) {
        restoreRewindParticipants(state);
    }

    void restoreRewindState(PlayerRewindExtra extra, SidekickCpuController cpuController,
            short[] xHistory, short[] yHistory, short[] inputHistory,
            byte[] jumpPressHistory, byte[] statusHistory) {
        boolean hasFullCarrySnapshot = extra.tailsCarryState() != null;
        if (hasFullCarrySnapshot) restoreRewindState(extra.controllerState());
        else restoreRewindStateWithoutCarry(extra.controllerState());

        if (extra.sidekickCpuExtra() != null) {
            if (cpuController == null) {
                throw new IllegalStateException(
                        "Cannot restore SidekickCpuController state without a live controller");
            }
            if (hasFullCarrySnapshot) cpuController.restoreRewindScalars(extra.sidekickCpuExtra());
            else cpuController.restoreRewindState(extra.sidekickCpuExtra());
        }
        copyHistory(extra.xHistory(), xHistory);
        copyHistory(extra.yHistory(), yHistory);
        copyHistory(extra.inputHistory(), inputHistory);
        copyHistory(extra.jumpPressHistory(), jumpPressHistory);
        copyHistory(extra.statusHistory(), statusHistory);
    }

    private static void copyHistory(short[] source, short[] target) {
        if (source != null) System.arraycopy(source, 0, target, 0, Math.min(source.length, target.length));
    }

    private static void copyHistory(byte[] source, byte[] target) {
        if (source != null) System.arraycopy(source, 0, target, 0, Math.min(source.length, target.length));
    }

    private void restoreRewindParticipants(RewindState state) {
        PlayableSpriteMovement.RewindState movementState = state != null ? state.movementState() : null;
        SpindashDustController.RewindState spindashState = state != null ? state.spindashDustState() : null;
        PlayableSpriteAnimation.RewindState animationState = state != null ? state.animationState() : null;
        DrowningController.RewindState drowningState = state != null ? state.drowningState() : null;
        SuperStateController.RewindState superStateState = state != null ? state.superStateState() : null;
        if (movement != null) {
            movement.restoreRewindState(movementState);
        }
        if (spindashDust != null) {
            spindashDust.restoreRewindState(spindashState);
        }
        if (animation != null) {
            animation.restoreRewindState(animationState);
        }
        if (drowning != null) {
            drowning.restoreRewindState(drowningState);
        }
        if (superState != null && superStateState != null) {
            superState.restoreRewindState(superStateState);
        }
    }

    public void clearCarryAndReleaseMain() {
        if (tailsCarry != null) {
            tailsCarry.clearAndReleaseMain();
        }
    }

    public void clearTailsFlightIf(boolean enabled) {
        if (enabled && tailsFlight != null) {
            tailsFlight.clear();
        }
    }

    public void resetSuperState() {
        if (superState != null) {
            superState.reset();
        }
    }

    @com.openggf.game.ModApi
    public record RewindState(
            PlayableSpriteMovement.RewindState movementState,
            SpindashDustController.RewindState spindashDustState,
            PlayableSpriteAnimation.RewindState animationState,
            DrowningController.RewindState drowningState,
            TailsCarryController.Snapshot tailsCarryState,
            SuperStateController.RewindState superStateState
    ) {}
}
