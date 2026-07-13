package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestOOZPoppingPlatformMultiSidekick {

    @Test
    void playerTriggeredPlatformLocksMainAndThreeStandingSidekicks() {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        OOZPoppingPlatformObjectInstance platform = new OOZPoppingPlatformObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x33, 1, 0, false, 0), "OOZPoppingPform");
        ObjectManager objectManager = mock(ObjectManager.class);
        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            when(objectManager.isRidingObject(player, platform)).thenReturn(true);
        }
        platform.setServices(new Services(main, List.of(first, second, third), objectManager));

        platform.update(1, main);

        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            assertTrue(player.isObjectControlled(), player.getCode() + " should be captured by the shared pop");
        }
    }

    private static TestablePlayableSprite player(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x400, (short) 0x2F0);
        player.setCpuControlled(!"sonic".equals(code));
        return player;
    }

    private static final class Services extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> sidekicks;
        private final ObjectManager objectManager;

        private Services(PlayableEntity main, List<? extends PlayableEntity> sidekicks,
                ObjectManager objectManager) {
            this.main = main;
            this.sidekicks = sidekicks;
            this.objectManager = objectManager;
        }

        @Override public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }

        @Override public ObjectManager objectManager() {
            return objectManager;
        }
    }
}
