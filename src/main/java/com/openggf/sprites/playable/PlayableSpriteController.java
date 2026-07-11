package com.openggf.sprites.playable;

import com.openggf.sprites.managers.PlayableSpriteAnimation;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.sprites.managers.TailsFlightController;
import com.openggf.sprites.managers.TailsTailsController;

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
                tailsCarry != null ? tailsCarry.capture() : null);
    }

    public void restoreRewindState(RewindState state) {
        PlayableSpriteMovement.RewindState movementState = state != null ? state.movementState() : null;
        SpindashDustController.RewindState spindashState = state != null ? state.spindashDustState() : null;
        PlayableSpriteAnimation.RewindState animationState = state != null ? state.animationState() : null;
        DrowningController.RewindState drowningState = state != null ? state.drowningState() : null;
        TailsCarryController.Snapshot carryState = state != null ? state.tailsCarryState() : null;
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
        if (tailsCarry != null) {
            if (carryState != null) {
                tailsCarry.restore(carryState);
            } else {
                tailsCarry.clearAndReleaseMain();
            }
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

    public record RewindState(
            PlayableSpriteMovement.RewindState movementState,
            SpindashDustController.RewindState spindashDustState,
            PlayableSpriteAnimation.RewindState animationState,
            DrowningController.RewindState drowningState,
            TailsCarryController.Snapshot tailsCarryState
    ) {}
}
