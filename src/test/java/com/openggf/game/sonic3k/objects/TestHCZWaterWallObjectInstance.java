package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHCZWaterWallObjectInstance {

    @Test
    void verticalGeyserLockAndReleaseUseFullObjectControlPolicyForPlayerAndSidekick() throws Exception {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x01C8);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0x0208, (short) 0x01C8);
        HCZWaterWallObjectInstance waterWall = new HCZWaterWallObjectInstance(
                new ObjectSpawn(0x0200, 0x0200, 0x3B, 1, 0, false, 0));
        waterWall.setServices(new TestObjectServices().withSidekicks(List.of(sidekick)));
        int playerInitialY = player.getY();
        int sidekickInitialY = sidekick.getY();

        waterWall.update(0, player);

        assertFullControl(player);
        assertTrue(player.isControlLocked());
        assertEquals(playerInitialY - 8, player.getY());
        assertFullControl(sidekick);
        assertTrue(sidekick.isControlLocked());
        assertEquals(sidekickInitialY - 8, sidekick.getY());

        invokeReleasePlayers(waterWall, player);

        assertNoControl(player);
        assertFalse(player.isControlLocked());
        assertNoControl(sidekick);
        assertFalse(sidekick.isControlLocked());
    }

    @Test
    void verticalGeyserProcessesAllEngineSidekicksFromPlayerQuery() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x01C8);
        TestablePlayableSprite firstSidekick = new TestablePlayableSprite("tails", (short) 0x0208, (short) 0x01C8);
        TestablePlayableSprite extraSidekick = new TestablePlayableSprite("knuckles", (short) 0x0210, (short) 0x01C8);
        HCZWaterWallObjectInstance waterWall = new HCZWaterWallObjectInstance(
                new ObjectSpawn(0x0200, 0x0200, 0x3B, 1, 0, false, 0));
        waterWall.setServices(new QueryOnlyServices(player, List.of(firstSidekick, extraSidekick)));

        waterWall.update(0, player);

        assertFullControl(firstSidekick);
        assertTrue(firstSidekick.isControlLocked());
        assertFullControl(extraSidekick);
        assertTrue(extraSidekick.isControlLocked());
    }

    private static void assertFullControl(TestablePlayableSprite player) {
        assertTrue(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
    }

    private static void assertNoControl(TestablePlayableSprite player) {
        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
    }

    private static void invokeReleasePlayers(
            HCZWaterWallObjectInstance waterWall,
            TestablePlayableSprite player) throws Exception {
        Method method = HCZWaterWallObjectInstance.class
                .getDeclaredMethod("releasePlayers", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        method.setAccessible(true);
        method.invoke(waterWall, player);
    }

    private static final class QueryOnlyServices extends TestObjectServices {
        private final ObjectPlayerQuery playerQuery;

        QueryOnlyServices(TestablePlayableSprite main, List<TestablePlayableSprite> sidekicks) {
            this.playerQuery = new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return playerQuery;
        }

        @Override
        public List<com.openggf.game.PlayableEntity> sidekicks() {
            throw new AssertionError("HCZ water wall should use ObjectPlayerQuery for engine sidekick participation");
        }
    }
}
