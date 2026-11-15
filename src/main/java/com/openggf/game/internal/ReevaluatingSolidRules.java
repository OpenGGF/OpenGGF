package com.openggf.game.internal;

/**
 * Game service selecting the re-evaluating {@code SolidObject} family for every solid object:
 * each call tests the whole box again (no standing short path), carries a rider by the object's
 * velocity, and returns 1 only for a top or bottom contact. The routine is
 * {@code com.openggf.level.objects.ReevaluatingSolidRoutine}; its objects implement
 * {@code ReevaluatingSolidObject}. The values here are the routine's player-side constants.
 */
public interface ReevaluatingSolidRules {
    /** Added to the object's width byte: the player's X radius plus one ({@code addi.w #9+1,d1}). */
    int widthAllowance();

    /** Added to the summed Y radii for the box test ({@code addq.w #2,d3}). */
    int heightAllowance();

    /** Player animation that releases the player from every solid (drowning). */
    int drowningAnimation();

    /** Player animation that keeps a stood-on object solid and is never landed out of (giving up). */
    int giveUpAnimation();

    /** Animation written when a rolling player first stands on an object (walking). */
    int walkAnimation();

    /** Post-hit invulnerability written when a hurt player lands on an object. */
    int hurtLandingInvulnerability();

    /** Centre Y rise when a rolling player first stands on an object. */
    int rollLandingRise();

    /** {@link #rollLandingRise()} for {@code player}, whose stage state may select another rise. */
    default int rollLandingRise(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        return rollLandingRise();
    }
}
