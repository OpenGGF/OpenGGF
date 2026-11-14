package com.openggf.game.internal;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Engine-only stage routines a game's {@link com.openggf.game.PhysicsProvider} adds to
 * the shared player owner. Each method is one native call site in the player's main
 * routine; the shared code calls it at that point and never asks which game, zone or
 * act is loaded. Every default is the behaviour of a game whose player routine has no
 * such call, so a provider that does not implement this interface changes nothing.
 *
 * <p>Sonic CD's player is the only implementer: its {@code ObjSonic_Main} and movement
 * modes call {@code ObjSonic_Handle3DRamp}, {@code ObjSonic_TimeWarp},
 * {@code ObjSonic_SpecialCol}, {@code Player_ObjCollide} behind the
 * {@code oPlayerCtrl} gate and {@code ObjSonic_SpecialChunks} (scddisasm
 * src/Level/_Objects/Sonic/Main.asm:285-353, 675-780).
 */
public interface PlayerStageRoutines {
    /**
     * The opening of each normal-control movement mode (ground, air, roll and jump),
     * before the mode's own checks. Returns whether the player is on a pseudo-3D ramp
     * for the rest of this frame; {@code wasOnRamp} is the previous frame's state. The
     * routine may rewrite the player's velocity on the frame it leaves a ramp.
     */
    default boolean updatePseudo3dRamp(AbstractPlayableSprite player, boolean wasOnRamp) {
        return false;
    }

    /** Immediately after {@link #updatePseudo3dRamp}, still before the mode's own checks. */
    default void afterPseudo3dRampCheck(AbstractPlayableSprite player) {
    }

    /**
     * The air movement routine returns at once: no steering, no camera bias reset and
     * no air drag this frame.
     */
    default boolean freezesAirMovement(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * Whether the player's main routine runs its speed-panned camera focus this frame.
     * False leaves the focus column unchanged: the stage's copy of the routine returns
     * at once.
     */
    default boolean runsSpeedPannedFocus(AbstractPlayableSprite player) {
        return true;
    }

    /** While on a pseudo-3D ramp the speed-panned camera focus returns to the centre. */
    default boolean pseudo3dRampCentresFocus(AbstractPlayableSprite player) {
        return false;
    }

    /** After the movement-mode dispatch of a normal-control frame (not hurt, not dead). */
    default void afterMovementMode(AbstractPlayableSprite player) {
    }

    /** Gate on the player's object touch pass, after its animation. */
    default boolean objectCollisionEnabled(AbstractPlayableSprite player) {
        return true;
    }

    /**
     * Whether the touch pass treats the player as invincible this frame for a stage state of its own: an enemy
     * it touches is attacked and a hazard does not hurt it. False keeps the shared tests (invincibility, the
     * roll and the game's ability attacks).
     */
    default boolean touchesAsInvincible(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * The opening of the air movement mode, after the falling walk reset and before
     * {@link #updatePseudo3dRamp}. True means a stage-owned air state (a pole or bar the
     * player holds) moved the player in place of the mode's movement, ramp check, jump
     * height, steering, gravity and angle reset: only the mode's air level collision
     * follows this frame. Never called for the hurt routine.
     */
    default boolean runsStageAirState(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * The falling walk reset of the un-rolled air mode leaves the current animation
     * alone this frame (a hanging pose that is not a fall).
     */
    default boolean holdsFallingAirAnimation(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * Whether the air mode caps a rising speed faster than the shared upward cap when
     * the jump flag is clear. False: the stage's copy of the jump-height routine returns
     * at once on that path.
     */
    default boolean capsUpwardVelocity(AbstractPlayableSprite player) {
        return true;
    }

    /**
     * Whether the game's roll-entry speed floor applies on this stage. False: the
     * stage's copy of the roll start returns when already rolling and raises only a
     * zero ground speed.
     */
    default boolean rollEntrySpeedFloor(AbstractPlayableSprite player) {
        return true;
    }

    /** Whether balancing on a terrain ledge cancels the timed charge and zeroes ground speed. */
    default boolean balanceCancelsTimedCharge(AbstractPlayableSprite player) {
        return true;
    }

    /** The per-frame ground-speed step of the timed spin-dash charge; {@code standardStep} otherwise. */
    default int timedSpindashStep(AbstractPlayableSprite player, int standardStep) {
        return standardStep;
    }

    /**
     * The player's hurt entry, after the damage gates pass and before the shield, ring
     * and death tests: state the stage's copy of the routine clears on every hit.
     */
    default void beforeHurt(AbstractPlayableSprite player) {
    }

    /**
     * The jump, after its input and charge gates and before the headroom test: true when
     * the object the player stands on supplied the jump's velocity itself, so the headroom
     * test and the standard jump velocity are skipped while the rest of the jump (air,
     * jump flag, radii, sound) still runs.
     */
    default boolean jumpLaunchedByStandingObject(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * Added to the X speed the ground and roll modes move the player by (their position
     * update), without changing the stored speed: the object the player stands on carries
     * it. Zero for no such object.
     */
    default int standingObjectMoveXBias(AbstractPlayableSprite player) {
        return 0;
    }

    /**
     * The standing tail's balance test while the player stands on an object: true when that
     * object makes the routine write Walk and skip the balance, look and charge checks.
     */
    default boolean standingObjectHoldsWalk(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * The start of the player's routine every frame, before any mode, hurt or death
     * dispatch: stage state that the player's later radius and slope writes read.
     */
    default void beforePlayerRoutine(AbstractPlayableSprite player) {
    }

    /**
     * A frame in which a controlling object suppresses the player's movement modes (and the
     * player is neither hurt nor dead): the part of the main routine that still runs.
     */
    default void whileObjectControlled(AbstractPlayableSprite player) {
    }

    /**
     * The hurt routine after its move and gravity, before its landing collision: a stage's
     * special collision call there.
     */
    default void afterHurtGravity(AbstractPlayableSprite player) {
    }

    /** What a stage routine that opens the ground mode decided; see {@link #stageGroundMode}. */
    enum GroundMode {
        /** The ground mode runs as usual. */
        NORMAL,
        /** The mode returns at once: no movement, no collision. */
        HELD,
        /** Only the level-boundary check, then a move with gravity; no level collision. */
        FALLING
    }

    /**
     * The opening of the ground (walking) mode, before every other ground routine. Sonic CD's
     * {@code ObjSonic_MdGround} checks the boredom timer there and, while the player gives up,
     * either stands or flies off (scddisasm src/Level/_Objects/Sonic/Main.asm:675-692).
     */
    default GroundMode stageGroundMode(AbstractPlayableSprite player) {
        return GroundMode.NORMAL;
    }

    /**
     * Whether the death routine keeps the player's X speed and moves by it. Every game's kill
     * routine clears it; a death that skips the kill (Sonic CD's give-up falling past the
     * bottom boundary, Main.asm:1704-1707) keeps the speed it had (ObjSonic_Dead, Main.asm:2397-2400).
     */
    default boolean deathKeepsXSpeed(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * Whether the player's touch pass responds to a special ($40-type) object it overlaps. False ends the pass with
     * no response, as Sonic CD Metallic Madness's touch routine does for an object on the other layer
     * (r8/player_object_collide.asm:104-116).
     */
    default boolean admitsSpecialTouch(AbstractPlayableSprite player, com.openggf.level.objects.ObjectInstance object) {
        return true;
    }

    // ---- The player's water routine (Sonic CD ObjSonic_Water / PlayerCheckWater) ----------

    /**
     * Whether the player's water check runs this frame at all. False returns before the
     * water level is compared: the underwater status and speeds stay as they are.
     */
    default boolean runsWaterCheck(AbstractPlayableSprite player) {
        return true;
    }

    /**
     * Whether a player above the water level that is still moving down keeps its
     * underwater status this frame: the exit runs only once the vertical speed is zero
     * or upward.
     */
    default boolean waterExitWaitsWhileFalling(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * The water entry, after the underwater status is set and before the speed constants
     * change: the stage's air-timer reset and its fixed air-countdown slot.
     */
    default void onWaterEntry(AbstractPlayableSprite player) {
    }

    /**
     * The splash of a water entry or exit that moved the player vertically. True means the
     * stage created its own splash object (with its own sound), so the shared splash and
     * sound are skipped.
     */
    default boolean spawnsWaterSplash(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * Whether a fixed air-countdown slot of the stage, not the shared per-frame drowning
     * update, owns the air timer, its bubbles and the drowning sink for {@code player}.
     */
    default boolean ownsDrowningCountdown(AbstractPlayableSprite player) {
        return false;
    }
}
