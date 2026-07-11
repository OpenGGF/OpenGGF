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
}
