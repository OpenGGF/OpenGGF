package com.openggf.game.sonic1;

import com.openggf.game.sonic1.objects.Sonic1SpikeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S1_DAMAGE (all audited publications, e.g. 1948M/3402S) component contracts.
 * Oracle: S1 28872a46, HurtSonic/Touch_ChkHurt in Sonic ReactToItem.asm,
 * Sonic_Display in 01 Sonic.asm, Spik_Hurt in 36 Spikes.asm (FixBugs=0).
 * This exercises real damage/timer/spike owners; it does not certify a TAS
 * obstacle crossing, ring-loss allocation, or the unspecified REV00 lava setup.
 */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestTasS1Damage {
    private Sonic sonic;

    @BeforeEach
    void setup() {
        TestEnvironment.configureGameModuleFixture(new Sonic1GameModule());
        sonic = new Sonic("sonic", (short) 0, (short) 0);
        sonic.setCentreX((short) 0x100);
        sonic.setCentreY((short) 0x100);
    }

    @ParameterizedTest
    @CsvSource({"false,255,512,-1024", "false,256,512,-1024", "false,257,-512,-1024",
            "true,255,256,-512", "true,256,256,-512", "true,257,-256,-512"})
    void nativeRecoilUsesSourceSideIncludingEqualityAndWater(
            boolean water, int sourceX, int expectedX, int expectedY) {
        sonic.setInWater(water);
        sonic.setGSpeed((short) 0x600);
        // The caller has already sampled the pre-loss ring count. This test owns
        // HurtSonic, not ring-loss spawning (covered by the real LZ3 spike test).
        assertTrue(sonic.applyHurtOrDeath(sourceX, false, true));
        assertTrue(sonic.isHurt());
        assertTrue(sonic.getAir());
        assertFalse(sonic.getDead());
        assertEquals(expectedX, sonic.getXSpeed());
        assertEquals(expectedY, sonic.getYSpeed());
        assertEquals(0, sonic.getGSpeed());
        assertEquals(120, sonic.getInvulnerableFrames());

        assertFalse(sonic.applyHurtOrDeath(sourceX, false, false),
                "A second ordinary hit cannot kill while the acquired flash timer is live");
        assertFalse(sonic.getDead());
        assertEquals(expectedX, sonic.getXSpeed());
        for (int i = 0; i < 3; i++) {
            sonic.tickInvulnerabilityDisplayTimerBeforeTouchResponse();
            sonic.endOfTick();
        }
        assertEquals(120, sonic.getInvulnerableFrames(),
                "Hurt routine uses DisplaySprite directly, leaving Sonic_Display's timer frozen");
    }

    @Test
    void zeroRingsDiesButShieldAllowsTheSameContactToRecoil() {
        assertTrue(sonic.applyHurtOrDeath(0x101, false, false));
        assertTrue(sonic.getDead());

        Sonic shielded = new Sonic("sonic", (short) 0, (short) 0);
        shielded.setCentreX((short) 0x100);
        shielded.giveShield();
        assertTrue(shielded.hasShield());
        assertTrue(shielded.applyHurtOrDeath(0x101, false, false));
        assertFalse(shielded.hasShield());
        assertFalse(shielded.getDead());
        assertTrue(shielded.isHurt());
        assertEquals(-0x200, shielded.getXSpeed());
        assertEquals(-0x400, shielded.getYSpeed());
    }

    @Test
    void finalFlashTickExpiresBeforeOrdinaryTouchResponse() {
        // Exact recovered-state boundary of Sonic_Display -> ReactToItem.
        sonic.setInvulnerableFrames(1);
        assertFalse(sonic.applyHurtOrDeath(0x101, false, false));
        assertFalse(sonic.getDead());
        sonic.tickInvulnerabilityDisplayTimerBeforeTouchResponse();
        assertEquals(0, sonic.getInvulnerableFrames());
        assertTrue(sonic.applyHurtOrDeath(0x101, false, false));
        assertTrue(sonic.getDead());
    }

    @Test
    void retailSpikesBypassRecoveredFlashButRespectInvincibility() {
        // Recovered ordinary-routine state, overlapping a sideways spike.
        // The native solid contact is supplied at its callback boundary;
        // geometric collision acquisition is covered separately in LZ3.
        Sonic1SpikeObjectInstance spike = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x36, 0x10, 0, false, 0));
        spike.setServices(new StubObjectServices());
        SolidContact side = new SolidContact(false, true, false, false, false);
        sonic.setInvulnerableFrames(120);
        sonic.giveInvincibility(1);
        spike.onSolidContact(sonic, side, 1);
        assertFalse(sonic.getDead());
        assertFalse(sonic.isHurt());
        sonic.tickStatus();
        assertEquals(0, sonic.getInvincibleFrames());
        assertTrue(sonic.getInvulnerableFrames() > 0);
        assertFalse(sonic.applyHurtOrDeath(0x100, false, false),
                "Ordinary hazard remains blocked by the same flash timer");
        spike.onSolidContact(sonic, side, 2);
        assertTrue(sonic.getDead(), "FixBugs=0 Spik_Hurt ignores the post-hit flash timer");
    }
}
