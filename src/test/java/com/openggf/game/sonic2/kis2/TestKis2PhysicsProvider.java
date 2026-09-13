package com.openggf.game.sonic2.kis2;

import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.rules.GameRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KiS2 physics per docs/kis2/BRANCH_DIFFS.md §Physics constants (s2disasm
 * knuckles-in-sonic-2 branch), not inferred from S3K donation.
 */
class TestKis2PhysicsProvider {

    private final Kis2PhysicsProvider provider = new Kis2PhysicsProvider();

    @Test
    void knucklesProfileHasLockOnJumpVelocity() {
        PhysicsProfile profile = provider.getProfile("knuckles");
        assertSame(Kis2Physics.KNUCKLES, profile);
        assertEquals((short) 0x600, profile.jump(), "KiS2 Sonic_Jump: move.w #$600,d2");
        assertEquals(PhysicsProfile.SONIC_2_SONIC.runAccel(), profile.runAccel());
        assertEquals(PhysicsProfile.SONIC_2_SONIC.runDecel(), profile.runDecel());
        assertEquals(PhysicsProfile.SONIC_2_SONIC.max(), profile.max());
        assertEquals(PhysicsProfile.SONIC_2_SONIC.standYRadius(), profile.standYRadius());
        assertEquals(PhysicsProfile.SONIC_2_SONIC.standXRadius(), profile.standXRadius());
    }

    @Test
    void knucklesBalancesLikeTailsNotLikeS3kKnuckles() {
        // KiS2 Sonic_BalanceOnObjRight/Left: single AniIDSonAni_Balance state,
        // facing turned toward the edge (BRANCH_DIFFS.md §Physics constants).
        assertTrue(Kis2Physics.KNUCKLES.singleFacingBalance());
        assertFalse(PhysicsProfile.SONIC_3K_KNUCKLES.singleFacingBalance());
        assertEquals(PhysicsProfile.SONIC_3K_KNUCKLES.onObjectBalanceShift(),
                Kis2Physics.KNUCKLES.onObjectBalanceShift());
    }

    @Test
    void sidekickCharactersFallThroughToStockSonic2Profiles() {
        assertSame(PhysicsProfile.SONIC_2_TAILS, provider.getProfile("tails"));
        assertSame(PhysicsProfile.SONIC_2_SONIC, provider.getProfile("sonic"));
        assertSame(PhysicsProfile.SONIC_2_SONIC, provider.getProfile(null));
    }

    @Test
    void underwaterJumpIsKnucklesModifier() {
        // KiS2 Sonic_Jump: move.w #$300,d2 when underwater.
        assertSame(PhysicsModifiers.KNUCKLES, provider.getModifiers());
        assertEquals((short) 0x300, provider.getModifiers().waterJump());
    }

    @Test
    void rulesAreSonic2WithTheKis2LandingAndDuckFrameChanges() {
        GameRules rules = provider.getRules();
        assertSame(Kis2Rules.RULES, rules);
        assertNotSame(GameRules.SONIC_2, rules);
        // KiS2 Sonic_ResetOnFloor_Part2: y_pos += y_radius - 19 (S3K form).
        assertTrue(rules.playerMovement().landing().landingRollClearUsesCurrentYRadiusDelta());
        assertFalse(GameRules.SONIC_2.playerMovement().landing().landingRollClearUsesCurrentYRadiusDelta());
        // KiS2 Touch_Rings / TouchResponse: cmpi.b #$9C,mapping_frame.
        assertEquals(0x9C, rules.objectInteraction().duckTouchBoxMappingFrame());
        assertEquals(0x4D, GameRules.SONIC_2.objectInteraction().duckTouchBoxMappingFrame());
        // Everything else is stock Sonic 2.
        assertSame(GameRules.SONIC_2.collision(), rules.collision());
        assertSame(GameRules.SONIC_2.playerAnimation(), rules.playerAnimation());
        assertSame(GameRules.SONIC_2.playerCapability(), rules.playerCapability());
        assertSame(GameRules.SONIC_2.sidekickCpu(), rules.sidekickCpu());
        assertSame(GameRules.SONIC_2.powerUp(), rules.powerUp());
        assertSame(GameRules.SONIC_2.ring(), rules.ring());
        assertEquals(GameRules.SONIC_2.playerMovement().levelBoundary(),
                rules.playerMovement().levelBoundary());
    }

    @Test
    void providerIsStatelessAcrossCalls() {
        provider.getProfile("tails");
        assertSame(PhysicsModifiers.KNUCKLES, provider.getModifiers());
        provider.getProfile("knuckles");
        assertSame(PhysicsProfile.SONIC_2_TAILS, provider.getProfile("tails"));
    }

    @Test
    void stockProfilesAreUntouched() {
        assertEquals((short) 0x680, PhysicsProfile.SONIC_2_SONIC.jump());
        assertEquals((short) 0x600, PhysicsProfile.SONIC_3K_KNUCKLES.jump());
    }
}
