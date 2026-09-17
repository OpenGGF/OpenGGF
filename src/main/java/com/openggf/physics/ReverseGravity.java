package com.openggf.physics;

/**
 * Geometry helpers for the S3K {@code Reverse_gravity_flag} ($FFFFF7C6).
 *
 * <p>The ROM keeps one global byte and never checks the zone: any level with the flag
 * set behaves this way, which is why nothing here is game- or zone-keyed. The flag is
 * read through {@code GameStateManager.isReverseGravityActive()}; this class only owns
 * the arithmetic the ROM branches share, so that the airborne, grounded, rolling and
 * Knuckles paths all get the same answer to "which way is down".
 *
 * <p>Two facts from {@code MoveSprite_TestGravity} (sonic3k.asm:36068-36083) govern the
 * whole model: <em>velocity is never inverted</em> (positive {@code y_vel} always means
 * "falling", toward whichever surface is the floor) and <em>position integration is</em>.
 * Everything else — mirrored terrain angles, swapped floor/ceiling probes, negated
 * push-out and radius adjustments — follows from that.
 *
 * <p>Deliberately not a {@code GameRules} member: {@code GameRules} and its rule records
 * are {@code @com.openggf.game.ModApi} and a new component would break the 0.7 signature
 * pin. This class is engine-internal.
 */
public final class ReverseGravity {

    private ReverseGravity() { }

    /**
     * The value {@code MoveSprite_TestGravity} / {@code MoveSprite_TestGravity2} feed to
     * the position add: {@code move.w y_vel(a0),d0} followed by {@code neg.w d0} when the
     * flag is set (sonic3k.asm:36073-36078, :36094-36096). The stored {@code y_vel} is
     * left alone, and so is the {@code addi.w #$38,y_vel(a0)} gravity step that precedes
     * this in {@code MoveSprite_TestGravity}.
     */
    public static short integrationYSpeed(boolean active, short yVel) {
        return active ? (short) -yVel : yVel;
    }

    /**
     * Mirrors a terrain angle about the vertical axis: {@code addi.b #$40,d0 / neg.b d0 /
     * subi.b #$40,d0}, the sequence {@code Call_Player_AnglePos} (sonic3k.asm:22329-22343)
     * applies before and after {@code Player_AnglePos} and that {@code sub_11FD6} /
     * {@code sub_11FEE} (:24127-24149) apply to the distance probe's returned angle.
     *
     * <p>It swaps floor ($00) with ceiling ($80) and fixes both walls ($40, $C0).
     */
    public static int mirrorAngle(int angle) {
        int d0 = (angle + 0x40) & 0xFF;
        d0 = (-d0) & 0xFF;
        return (d0 - 0x40) & 0xFF;
    }

    /** Negates a Y <em>position</em> adjustment (radius, snap or push-out) while the flag is set. */
    public static int mirrorYDelta(boolean active, int delta) {
        return active ? -delta : delta;
    }
}
