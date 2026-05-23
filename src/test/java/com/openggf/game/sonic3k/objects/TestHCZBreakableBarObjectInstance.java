package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
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

    @Test
    void nativeP2CaptureUsesObjectPlayerQueryWhenRawSidekickListIsEmpty() {
        TestablePlayableSprite main = positionedAwayFromBar("sonic");
        TestablePlayableSprite nativeP2 = positionedPlayer("tails");
        HCZBreakableBarObjectInstance bar = verticalBar(0x40);
        bar.setServices(new QueryOnlyPlayerServices(main, List.of(nativeP2), List.of()));

        bar.update(0, main);

        assertNativeBitZeroControl(nativeP2);
    }

    @Test
    void extraSidekickDoesNotShareNativeP2CaptureSlot() {
        TestablePlayableSprite main = positionedAwayFromBar("sonic");
        TestablePlayableSprite nativeP2 = positionedAwayFromBar("tails");
        TestablePlayableSprite extraSidekick = positionedPlayer("knuckles");
        List<PlayableEntity> sidekicks = List.of(nativeP2, extraSidekick);
        HCZBreakableBarObjectInstance bar = verticalBar(0x40);
        bar.setServices(new QueryOnlyPlayerServices(main, sidekicks, sidekicks));

        bar.update(0, main);

        assertNoObjectControl(extraSidekick);
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

    private static TestablePlayableSprite positionedAwayFromBar(String character) {
        TestablePlayableSprite player = new TestablePlayableSprite(character, (short) 0, (short) 0);
        player.setCentreX((short) 0x0100);
        player.setCentreY((short) 0x0100);
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

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> queriedSidekicks;
        private final List<PlayableEntity> rawSidekicks;

        private QueryOnlyPlayerServices(PlayableEntity main,
                                        List<? extends PlayableEntity> queriedSidekicks,
                                        List<PlayableEntity> rawSidekicks) {
            this.main = main;
            this.queriedSidekicks = List.copyOf(queriedSidekicks);
            this.rawSidekicks = List.copyOf(rawSidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return rawSidekicks;
        }
    }
}
