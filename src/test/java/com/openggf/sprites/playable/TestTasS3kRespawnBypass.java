package com.openggf.sprites.playable;

import com.openggf.game.rules.GameRules;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TASVideos 4086M / 6240S, co-op ring attack: Tails return is particularly
 * important in AIZ2. This is the exact local return gate, not that ring route.
 * Oracle: Tails_Catch_Up_Flying (sonic3k.asm:26514-26560), including
 * loc_13B50 / loc_13B78, skdisasm 8b2cf3c4, FixBugs=0.
 * A/B/C/Start bypass both the 64-frame test and leader object-control test;
 * directional input does neither. Normal gravity places Tails $C0 above P1.
 */
@ExtendWith(SingletonResetExtension.class)
class TestTasS3kRespawnBypass {
    @BeforeEach
    void reset() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @CsvSource({"0x10, 1", "0x20, 1", "0x10, 64", "0x20, 64"})
    void manualJumpOrStartBypassesCadenceAndControlledLeader(int input, int levelFrame) {
        var leader = sprite("sonic", 0x1234, 0x0400);
        leader.setObjectControlled(true);
        leader.setObjectControlAllowsCpu(false);
        var tails = sprite("tails_p2", 0x0200, 0x0500);
        tails.setCpuControlled(true);
        tails.setXSpeed((short) 0x123);
        tails.setYSpeed((short) -0x234);
        tails.setGSpeed((short) 0x345);
        var controller = new SidekickCpuController(tails, leader);
        controller.forceStateForTest(SidekickCpuController.State.CATCH_UP_FLIGHT, 0);
        // Engine input encoding: jump=$10, Start=$20 (native Ctrl_2 bits
        // A/B/C=$70 and Start=$80 are translated by the input owner).
        controller.setController2Input(0, input);

        controller.update(levelFrame);

        assertSame(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, controller.getState());
        assertEquals(0x1234, tails.getCentreX() & 0xFFFF);
        assertEquals(0x0340, tails.getCentreY() & 0xFFFF);
        assertEquals(0, tails.getXSpeed());
        assertEquals(0, tails.getYSpeed());
        assertEquals(0, tails.getGSpeed());
        assertTrue(tails.getAir());
        assertTrue(tails.isObjectControlled());
        assertTrue(tails.isObjectControlSuppressesMovement());
        assertFalse(tails.isObjectControlAllowsCpu(), "return writes object_control=$81");
    }

    @ParameterizedTest
    @CsvSource({"1, false", "1, true", "64, true"})
    void directionOnlyDoesNotBypassEitherGate(int levelFrame, boolean controlled) {
        var leader = sprite("sonic", 0x1234, 0x0400);
        leader.setObjectControlled(controlled);
        leader.setObjectControlAllowsCpu(false);
        var tails = sprite("tails_p2", 0x0200, 0x0500);
        tails.setCpuControlled(true);
        var controller = new SidekickCpuController(tails, leader);
        controller.forceStateForTest(SidekickCpuController.State.CATCH_UP_FLIGHT, 0);
        controller.setController2Input(AbstractPlayableSprite.INPUT_RIGHT, AbstractPlayableSprite.INPUT_RIGHT);

        controller.update(levelFrame);

        assertSame(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState());
        assertEquals(0x0200, tails.getCentreX() & 0xFFFF);
        assertEquals(0x0500, tails.getCentreY() & 0xFFFF);
    }

    private static FixtureSprite sprite(String code, int x, int y) {
        var sprite = new FixtureSprite(code);
        sprite.setCentreX((short) x);
        sprite.setCentreY((short) y);
        return sprite;
    }

    private static final class FixtureSprite extends AbstractPlayableSprite {
        private FixtureSprite(String code) {
            super(code, (short) 0, (short) 0);
            setGameRulesForTest(GameRules.SONIC_3K);
        }

        @Override public void draw() { }
        @Override public void defineSpeeds() { }
        @Override protected void createSensorLines() { }
    }
}
