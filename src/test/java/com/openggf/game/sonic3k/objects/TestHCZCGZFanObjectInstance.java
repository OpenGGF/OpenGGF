package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHCZCGZFanObjectInstance {

    @Test
    void activeFanPushesUniqueQueryParticipantsIndependently() {
        HCZCGZFanObjectInstance fan = new HCZCGZFanObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x10, 0, false, 0));
        TestablePlayableSprite main = playerInFanColumn("sonic");
        TestablePlayableSprite tails = playerInFanColumn("tails");
        TestablePlayableSprite knuckles = playerInFanColumn("knuckles");
        fan.setServices(new QueryOnlyPlayerServices(main, List.of(tails, tails, knuckles)));

        fan.update(0, main);

        assertTrue(main.getAir());
        assertEquals(main.getCentreY(), tails.getCentreY(),
                "Duplicate sidekick entries should be de-duplicated by the participation policy");
        assertEquals(main.getCentreY(), knuckles.getCentreY(),
                "All engine sidekicks should still receive the independent fan push");
    }

    private static TestablePlayableSprite playerInFanColumn(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x1000, (short) 0x1000);
        player.setCentreX((short) 0x1000);
        player.setCentreY((short) 0x1000);
        return player;
    }

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> queriedSidekicks;

        private QueryOnlyPlayerServices(PlayableEntity main, List<? extends PlayableEntity> queriedSidekicks) {
            this.main = main;
            this.queriedSidekicks = List.copyOf(queriedSidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return List.of();
        }
    }
}
