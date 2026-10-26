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
