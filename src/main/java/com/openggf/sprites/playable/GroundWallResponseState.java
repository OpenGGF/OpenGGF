package com.openggf.sprites.playable;

/**
 * Per-frame ground-wall collision response bookkeeping for a playable sprite.
 *
 * <p>Holds cohesive, intra-frame pieces of state produced by the ground-wall
 * collision pass and consumed within the same movement frame:
 *
 * <ul>
 *   <li>The <b>deferred velocity response</b> ({@code deferred} / {@code mode} /
 *       {@code distance}): when {@code CollisionSystem.resolveGroundWallCollision}
 *       must defer the velocity adjustment until after {@code MoveSprite}, it stages
 *       the mode and signed distance here; {@code PlayableSpriteMovement} clears it at
 *       frame start and {@code CollisionSystem.applyDeferredGroundWallVelocityResponse}
 *       consumes + clears it mid-frame.</li>
 *   <li>The <b>pre-control ground speed</b> ({@code preControlGSpeed}): snapshot of
 *       ground speed before the CPU sidekick controller mutates the sprite this frame.
 *       S3K's Tails CPU wall probe uses the pre-control inertia for zero-distance seam
 *       handling; the later pre-physics snapshot is already after CPU follow
 *       acceleration.</li>
 * </ul>
 *
 * <p>All fields are recomputed/cleared each frame, so this holder is rewind-transient:
 * {@code AbstractPlayableSprite}'s explicit rewind snapshot does not persist it.
 */
@com.openggf.game.ModApi
final class GroundWallResponseState {

    private boolean deferred;
    private int mode;
    private int distance;
    private short preControlGSpeed;

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

    void capturePreControlGSpeed(short gSpeed) {
        this.preControlGSpeed = gSpeed;
    }

    short preControlGSpeed() {
        return preControlGSpeed;
    }
}
