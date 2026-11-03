package com.openggf.sprites.playable;

/**
 * Per-frame ground-wall collision response bookkeeping for a playable sprite.
 *
 * <p>Holds the <b>deferred velocity response</b> ({@code deferred} / {@code mode} /
 * {@code distance}): when {@code CollisionSystem.resolveGroundWallCollision} must defer
 * the velocity adjustment until after {@code MoveSprite}, it stages the mode and signed
 * distance here; {@code PlayableSpriteMovement} clears it at frame start and
 * {@code CollisionSystem.applyDeferredGroundWallVelocityResponse} consumes + clears it
 * mid-frame.
 *
 * <p>All fields are recomputed/cleared each frame, so this holder is rewind-transient:
 * {@code AbstractPlayableSprite}'s explicit rewind snapshot does not persist it.
 */
@com.openggf.game.ModApi
final class GroundWallResponseState {

    private boolean deferred;
    private int mode;
    private int distance;

    void defer(int mode, int distance) {
        this.deferred = true;
        this.mode = mode;
        this.distance = distance;
    }

    boolean hasDeferred() {
        return deferred;
    }

    int mode() {
        return mode;
    }

    int distance() {
        return distance;
    }

    void clearDeferred() {
        this.deferred = false;
        this.mode = 0;
        this.distance = 0;
    }
}
