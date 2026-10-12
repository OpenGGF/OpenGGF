package com.openggf.sprites.playable;

/**
 * Engine-owned immutable overlay. Eligibility is resolved by the session adapter,
 * never by game or zone names in shared movement/rendering code.
 *
 * <p>The initial gravity capability covers normal dry air only. Hurt, water,
 * death, object control, flight and scripted movement retain their native owners.
 */
public record PlayableMutatorPolicy(int dryAirGravityPercent, boolean suppressBody,
                                   boolean suppressAppendage, boolean suppressAttachedEffects,
                                   int headScalePercent) {
    public static final PlayableMutatorPolicy STOCK = new PlayableMutatorPolicy(100, false, false, false);

    /** Preserves the original Gravity/Stealth policy constructor. */
    public PlayableMutatorPolicy(int dryAirGravityPercent, boolean suppressBody,
                                 boolean suppressAppendage, boolean suppressAttachedEffects) {
        this(dryAirGravityPercent, suppressBody, suppressAppendage, suppressAttachedEffects, 100);
    }

    public PlayableMutatorPolicy {
        if (dryAirGravityPercent < 25 || dryAirGravityPercent > 200) {
            throw new IllegalArgumentException("Dry air gravity must be 25..200 percent");
        }
        if (headScalePercent < 100 || headScalePercent > 200) {
            throw new IllegalArgumentException("Head scale must be 100..200 percent");
        }
    }

    /** One integer scale, truncated toward zero before native word addition. */
    public int scaleDryAirGravity(int nativeAcceleration) {
        return nativeAcceleration * dryAirGravityPercent / 100;
    }
}
