package com.openggf.level.objects;

/**
 * A solid object resolved by {@link ReevaluatingSolidRoutine}. The routine takes no registers:
 * it reads the object's own width byte, Y radius, velocity and render flags, and a few object
 * identities change its branches. Those identities are {@link Role}s here, never object IDs.
 *
 * <p>The object calls the routine from its own routine (manual checkpoint) with the entry it
 * uses; {@link #solidEntry()} reports that entry for the call in progress.
 */
public interface ReevaluatingSolidObject extends SolidObjectProvider {
    /** {@code SolidObject}, {@code TopSolidObject} or {@code BtmSolidObject}. */
    enum Entry { SOLID, TOP, BOTTOM }

    /** Object identities the routine tests by ID. */
    enum Role {
        ORDINARY,
        /** Never pushed against, never side-hit from the air, no crush, no floor recheck. */
        SPRING,
        /** A rolling player passes through its top and bottom. */
        MONITOR,
        /** A player below passes through. */
        PASS_UNDER,
        /** Never crushes a player between it and another pushed object. */
        CRUSH_EXEMPT
    }

    /** {@code oWidth}. */
    int solidWidth();

    /** {@code oYRadius}. */
    int solidYRadius();

    /** {@code tst.b oSprFlags}: drawn last frame. */
    boolean solidRenderedOnScreen();

    /** The entry of the call in progress. */
    Entry solidEntry();

    /** {@code oXVel}: a standing rider moves with it. */
    default int solidXVelocity() { return 0; }

    /** {@code oYVel}. */
    default int solidYVelocity() { return 0; }

    default Role solidRole() { return Role.ORDINARY; }

    /** A {@link Role#SPRING} in its upward routine keeps the rider's ground speed. */
    default boolean solidSpringFacesUp() { return false; }

    /** {@code btst #1,oSprFlags}. */
    default boolean solidVerticallyFlipped() { return false; }

    @Override
    default SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }

    @Override
    default SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(solidWidth(), solidYRadius(), solidYRadius());
    }
}
