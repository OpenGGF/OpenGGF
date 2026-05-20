package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHCZBreakableBarObjectInstance {

    @BeforeEach
    @AfterEach
    void resetBreakableBarWaterRushLatch() {
        HCZWaterRushObjectInstance.HCZBreakableBarState.setState(0);
    }

    @Test
    void captureUsesNativeBitZeroPolicyAndNonDestructiveReleaseClearsIt() {
        TestablePlayableSprite player = positionedPlayer("sonic");
        HCZBreakableBarObjectInstance bar = verticalBar(0x40);
        bar.setServices(new TestObjectServices());

        bar.update(0, player);

        assertNativeBitZeroControl(player);

        player.setJumpInputPressed(true);
        bar.update(1, player);

        assertNoObjectControl(player);
    }

    @Test
    void destructiveReleaseBreakClearsCapturedSidekickPolicy() {
        TestablePlayableSprite player = positionedPlayer("sonic");
        TestablePlayableSprite sidekick = positionedPlayer("tails");
        HCZBreakableBarObjectInstance bar = verticalBar(0x00);
        bar.setServices(new TestObjectServices().withSidekicks(List.of(sidekick)));

        bar.update(0, player);

        assertNativeBitZeroControl(player);
        assertNativeBitZeroControl(sidekick);

        player.setJumpInputPressed(true);
        bar.update(1, player);

        assertNoObjectControl(player);
        assertNoObjectControl(sidekick);
    }

    private static HCZBreakableBarObjectInstance verticalBar(int subtype) {
        return new HCZBreakableBarObjectInstance(
                new ObjectSpawn(0x0200, 0x0200, 0x36, subtype, 0, false, 0));
    }

    private static TestablePlayableSprite positionedPlayer(String character) {
        TestablePlayableSprite player = new TestablePlayableSprite(character, (short) 0, (short) 0);
        player.setCentreX((short) 0x0214);
        player.setCentreY((short) 0x0200);
        return player;
    }

    private static void assertNativeBitZeroControl(TestablePlayableSprite player) {
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertFalse(player.isTouchResponseSuppressedByObjectControl());
    }

    private static void assertNoObjectControl(TestablePlayableSprite player) {
        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
        assertFalse(player.isTouchResponseSuppressedByObjectControl());
    }
}
