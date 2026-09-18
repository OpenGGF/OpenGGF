package com.openggf.game.sonic2.kis2;

import com.openggf.game.PhysicsProfile;

/**
 * Knuckles' movement profile under the lock-on program
 * ({@code docs/kis2/BRANCH_DIFFS.md} §Physics constants).
 *
 * <p>Kept in the patch package rather than on {@link PhysicsProfile} so the
 * pinned Mod API signature surface is untouched.
 */
public final class Kis2Physics {

    private Kis2Physics() {
    }

    /**
     * KiS2 {@code Sonic_Jump}: {@code move.w #$600,d2} (underwater {@code $300}
     * through {@link com.openggf.game.PhysicsModifiers#KNUCKLES}); every other
     * speed constant is stock S2 ({@code $600/$C/$80}, radii {@code $13/$9}).
     * The values match {@link PhysicsProfile#SONIC_3K_KNUCKLES} except the
     * balance handling: KiS2 {@code Sonic_BalanceOnObjRight/Left},
     * {@code Sonic_Balance} and {@code Sonic_BalanceLeft} use the single
     * {@code AniIDSonAni_Balance} state and turn toward the edge (the Tails
     * form), not S3K Knuckles' four-state set.
     */
    public static final PhysicsProfile KNUCKLES = new PhysicsProfile(
            PhysicsProfile.SONIC_3K_KNUCKLES.runAccel(),
            PhysicsProfile.SONIC_3K_KNUCKLES.runDecel(),
            PhysicsProfile.SONIC_3K_KNUCKLES.friction(),
            PhysicsProfile.SONIC_3K_KNUCKLES.max(),
            (short) 0x600,  // Sonic_Jump (KiS2 branch)
            PhysicsProfile.SONIC_3K_KNUCKLES.slopeRunning(),
            PhysicsProfile.SONIC_3K_KNUCKLES.slopeRollingUp(),
            PhysicsProfile.SONIC_3K_KNUCKLES.slopeRollingDown(),
            PhysicsProfile.SONIC_3K_KNUCKLES.rollDecel(),
            PhysicsProfile.SONIC_3K_KNUCKLES.minStartRollSpeed(),
            PhysicsProfile.SONIC_3K_KNUCKLES.minRollSpeed(),
            PhysicsProfile.SONIC_3K_KNUCKLES.maxRoll(),
            PhysicsProfile.SONIC_3K_KNUCKLES.rollHeight(),
            PhysicsProfile.SONIC_3K_KNUCKLES.runHeight(),
            PhysicsProfile.SONIC_3K_KNUCKLES.standXRadius(),
            PhysicsProfile.SONIC_3K_KNUCKLES.standYRadius(),
            PhysicsProfile.SONIC_3K_KNUCKLES.rollXRadius(),
            PhysicsProfile.SONIC_3K_KNUCKLES.rollYRadius(),
            true,           // singleFacingBalance: KiS2 Sonic_BalanceOnObjRight/Left
            PhysicsProfile.SONIC_3K_KNUCKLES.onObjectBalanceShift()
    );
    /** Knuckles_TurnSuper / Obj01_ChkShoes: $800/$18/$C0; Sonic_Jump stays $600. */
    public static final PhysicsProfile SUPER_KNUCKLES = new PhysicsProfile(
            (short) 0x18, (short) 0xC0, (short) 0x18, (short) 0x800,
            KNUCKLES.jump(), KNUCKLES.slopeRunning(), KNUCKLES.slopeRollingUp(),
            KNUCKLES.slopeRollingDown(), KNUCKLES.rollDecel(), KNUCKLES.minStartRollSpeed(),
            KNUCKLES.minRollSpeed(), KNUCKLES.maxRoll(), KNUCKLES.rollHeight(),
            KNUCKLES.runHeight(), KNUCKLES.standXRadius(), KNUCKLES.standYRadius(),
            KNUCKLES.rollXRadius(), KNUCKLES.rollYRadius(), true, KNUCKLES.onObjectBalanceShift());
}
