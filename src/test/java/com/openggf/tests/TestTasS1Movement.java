package com.openggf.tests;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bounded production-input regressions for S1_REVERSE_START (795M/1437S,
 * 1066M/1847S, 1401M/2457S) and S1_ROLL_JUMP/S1_ROLL_REJUMP (1948M/3402S).
 * GHZ1's flat floor at centre (384,940) supplies native terrain, not a published TAS route.
 *
 * <p>Oracle: pinned S1 disassembly 28872a46, {@code _incObj/01 Sonic.asm},
 * Sonic_MoveLeft/Sonic_MoveRight, Sonic_Jump, Sonic_JumpDirection and
 * Sonic_ResetOnFloor, shipped FixBugs=0. The tests observe centre coordinates;
 * the larger roll-jump hitbox must not be confused with a centre-position write.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestTasS1Movement {
    private static SharedLevel level;
    private HeadlessTestFixture fixture;
    private AbstractPlayableSprite sonic;
    private int floorCentreY;

    @BeforeAll
    static void loadLevel() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_1, 0, 0);
    }

    @AfterAll
    static void disposeLevel() {
        if (level != null) level.dispose();
    }

    @BeforeEach
    void startOnFlatNativeFloor() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(level)
                .startPosition((short) 384, (short) 940).startPositionIsCentre().build();
        sonic = fixture.sprite();
        for (int i = 0; i < 8; i++) step(false, false, false, false);
        assertFalse(sonic.getAir(), "Scenario must reach the native GHZ1 floor");
        assertEquals(0, sonic.getAngle());
        assertEquals(0, sonic.getGSpeed());
        assertFalse(sonic.isOnObject());
        floorCentreY = sonic.getCentreY();
        assertEquals(940, floorCentreY, "The selected native floor must remain the scenario's entry");
    }

    @Test
    void oneLeftFrameThenRightUsesBrakingInsteadOfOrdinaryAcceleration() {
        step(true, false, false, false);
        assertEquals(-0xC, sonic.getGSpeed());
        step(false, true, false, false);
        assertEquals(0x80, sonic.getGSpeed(), "Unsigned carry selects the +$80 reversal step");
        assertEquals(0x80, sonic.getXSpeed());
        assertFalse(sonic.getAir());
    }

    @ParameterizedTest
    @CsvSource({"false, 12", "true, 128"})
    void simultaneousDirectionsHaveLeftThenRightOrder(boolean left, int expected) {
        // A right-only launch is the negative control for the combined-pad trick.
        step(left, true, false, false);
        assertEquals(expected, sonic.getGSpeed());
        assertEquals(expected, sonic.getXSpeed());
        assertEquals(floorCentreY, sonic.getCentreY());
    }

    @ParameterizedTest
    @CsvSource({"128,true,false,0", "127,true,false,-128",
            "-128,false,true,128", "-129,false,true,-1"})
    void brakingZeroBoundaryRetainsNativeBorrowCarryAsymmetry(
            int inertia, boolean left, boolean right, int expected) {
        sonic.setGSpeed((short) inertia);
        sonic.setXSpeed((short) inertia);
        step(left, right, false, false);
        assertEquals(expected, sonic.getGSpeed());
        assertEquals(expected, sonic.getXSpeed());
        assertFalse(sonic.getAir());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void jumpUsesNativeRadiiAndOnlyRollingLaunchLocksAirSteering(boolean rollFirst) {
        sonic.setGSpeed((short) 0x200);
        sonic.setXSpeed((short) 0x200);
        if (rollFirst) enterRoll();
        int beforeJumpY = sonic.getCentreY();
        int beforeJumpX = sonic.getCentreX();
        int launchXSpeed = sonic.getXSpeed();

        step(false, false, false, true);

        assertTrue(sonic.getAir());
        assertTrue(sonic.getRolling());
        assertTrue(sonic.isJumping());
        assertEquals(rollFirst, sonic.getRollingJump());
        assertEquals(rollFirst ? 19 : 14, sonic.getYRadius());
        assertEquals(rollFirst ? 9 : 7, sonic.getXRadius());
        assertEquals(beforeJumpY + (rollFirst ? 0 : 5), sonic.getCentreY(),
                "Sonic_Jump writes +5 only on the non-rolling branch; launch skips SpeedToPos");
        assertEquals(beforeJumpX, sonic.getCentreX());
        assertEquals(-0x680, sonic.getYSpeed());

        step(true, false, false, true);
        assertEquals(rollFirst ? launchXSpeed : launchXSpeed - 0x18, sonic.getXSpeed(),
                "RollJumpLock skips the doubled $C air acceleration; ordinary jump is the control");
        assertEquals(-0x648, sonic.getYSpeed(), "First airborne ObjectFall adds $38 gravity");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rollingJumpLandingHasOneFrameElevatedRejumpWindow(boolean immediateRejump) {
        sonic.setGSpeed((short) 0x200);
        sonic.setXSpeed((short) 0x200);
        enterRoll();
        step(false, false, false, true);
        assertTrue(sonic.getRollingJump());
        // Release after one full airborne step, allowing a new press on landing.
        step(false, false, false, true);
        int frames = 0;
        while (sonic.getAir() && frames++ < 90) step(false, false, false, false);
        assertTrue(frames < 90, "Must actually land; a timeout is not coverage");
        assertFalse(sonic.getAir());
        assertFalse(sonic.getRolling());
        assertFalse(sonic.getRollingJump());
        assertEquals(19, sonic.getYRadius());
        assertEquals(floorCentreY - 5, sonic.getCentreY(),
                "ResetOnFloor subtracts 5 even though the roll-jump already had standing radii");

        step(false, false, false, immediateRejump);
        assertEquals(floorCentreY, sonic.getCentreY(),
                "Immediate jump's +5 or neutral AnglePos correction both restore this centre Y");
        assertEquals(immediateRejump, sonic.getAir());
        assertEquals(immediateRejump ? -0x680 : 0, sonic.getYSpeed());
        if (!immediateRejump) {
            step(false, false, false, true);
            assertTrue(sonic.getAir());
            assertEquals(floorCentreY + 5, sonic.getCentreY(),
                    "Waiting one grounded frame loses the five-pixel launch advantage");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void releasingForwardPreservesAboveCapAirMomentum(boolean right) {
        // Valid post-slope-jump state; the input-cap exploit happens on this next tick.
        sonic.setAir(true);
        sonic.setCentreY((short) 800);
        sonic.setXSpeed((short) 0x900);
        sonic.setYSpeed((short) -0x500);
        step(false, right, false, false);
        assertTrue(sonic.getAir());
        assertEquals(right ? 0x600 : 0x900, sonic.getXSpeed());
        assertEquals(-0x4C8, sonic.getYSpeed());
    }

    @ParameterizedTest
    @CsvSource({"0,1,62464", "65280,2,62208"})
    void fractionalAlignmentChangesTheNextIntegerCrossing(int fraction, int pixels, int remainder) {
        sonic.setGSpeed((short)0x200);
        sonic.setXSpeed((short)0x200);
        sonic.setSubpixelRaw(fraction,0);
        int entryX=sonic.getCentreX();
        step(false,false,false,false);
        assertEquals(0x1F4,sonic.getXSpeed());
        assertEquals(entryX+pixels,sonic.getCentreX());
        assertEquals(remainder,sonic.getXSubpixelRaw());
    }

    /**
     * 783M/1383S distinguishes the $1000 rolling X cap from faster grounded
     * running. The valid high-speed running entry is declared; Down must enter
     * roll through production input, and both measured ticks move on real floor.
     *
     * Native FixBugs=0: Sonic_CheckDpadLetGo (_incObj/01 Sonic.asm:499-513)
     * subtracts $C for neutral running. Sonic_RollSpeed (_incObj/01 Sonic.asm:767-812)
     * subtracts $6 on the following rolling tick.
     * Sonic_AngledRollSpeed (_incObj/01 Sonic.asm:829-875) caps X to $1000
     * without capping inertia.
     * Sonic_MoveRight (_incObj/01 Sonic.asm:705-727) caps running input to $600.
     * Sonic_MdNormal calls Sonic_Roll after Sonic_Move, so the Down entry tick
     * still uses the uncapped running conversion. Expected positions below are
     * independent native 16.16 sums of those two velocities, from X=384/fraction=0.
     */
    @ParameterizedTest
    @CsvSource({
            "6144,false,false,6120,6120,431,56320",
            "6144,true,false,6126,4096,423,62464",
            "6144,false,true,1536,1536,396,0",
            "4113,true,false,4095,4095,416,1024",
            "4114,true,false,4096,4096,416,1536",
            "4115,true,false,4097,4096,416,1792"
    })
    void groundedRunningCanOutrunTheRollingHorizontalCap(
            int initialSpeed, boolean roll, boolean forward, int expectedInertia,
            int expectedXSpeed, int expectedX, int expectedFraction) {
        step(false, true, false, false); // Acquire a real running animation/state.
        assertEquals(0xC, sonic.getGSpeed());
        assertFalse(sonic.getRolling());
        assertEquals(384, sonic.getCentreX());
        sonic.setGSpeed((short) initialSpeed);
        sonic.setXSpeed((short) initialSpeed);
        sonic.setSubpixelRaw(0, 0);

        step(false, forward, roll, false);
        int firstSpeed = forward ? 0x600 : initialSpeed - 0xC;
        assertEquals(firstSpeed, sonic.getGSpeed());
        assertEquals(firstSpeed, sonic.getXSpeed(),
                "Down enters roll after the first native running velocity conversion");
        assertEquals(384 + (firstSpeed >> 8), sonic.getCentreX());
        assertEquals((firstSpeed << 8) & 0xFFFF, sonic.getXSubpixelRaw());
        assertEquals(roll, sonic.getRolling());
        assertFalse(sonic.getAir());
        assertEquals(floorCentreY + (roll ? 5 : 0), sonic.getCentreY());

        step(false, forward, roll, false);
        assertEquals(expectedInertia, sonic.getGSpeed());
        assertEquals(expectedXSpeed, sonic.getXSpeed());
        assertEquals(expectedX, sonic.getCentreX(),
                "The cap must change actual grounded displacement, not just a speed field");
        assertEquals(expectedFraction, sonic.getXSubpixelRaw());
        assertEquals(roll, sonic.getRolling());
        assertFalse(sonic.getAir());
        assertFalse(sonic.isOnObject());
        assertEquals(0, sonic.getAngle());
        assertEquals(floorCentreY + (roll ? 5 : 0), sonic.getCentreY());
    }

    private void enterRoll() {
        step(false, false, true, false);
        assertTrue(sonic.getRolling(), "Down input must execute Sonic_Roll");
        assertFalse(sonic.getAir());
        assertEquals(14, sonic.getYRadius());
        assertEquals(floorCentreY + 5, sonic.getCentreY());
    }

    private void step(boolean left, boolean right, boolean down, boolean jump) {
        fixture.stepFrame(false, down, left, right, jump);
    }
}
