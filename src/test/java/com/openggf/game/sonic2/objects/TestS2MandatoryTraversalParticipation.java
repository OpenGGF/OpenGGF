package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS2MandatoryTraversalParticipation {
    @Test
    void mtzProximityPlatformIncludesEveryConfiguredSidekick() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite p2 = player("tails");
        TestablePlayableSprite extra1 = player("knuckles");
        TestablePlayableSprite extra2 = player("sonic-extra");
        MTZLongPlatformObjectInstance platform = new MTZLongPlatformObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x65, 3, 0, false, 0));
        platform.setServices(new QueryServices(main, List.of(p2, extra1, extra2)));
        Method method = MTZLongPlatformObjectInstance.class.getDeclaredMethod(
                "proximityParticipants", AbstractPlayableSprite.class);
        method.setAccessible(true);

        List<?> participants = (List<?>) method.invoke(platform, main);

        assertEquals(4, participants.size());
    }

    private static TestablePlayableSprite player(String code) {
        return new TestablePlayableSprite(code, (short) 0x1000, (short) 0x1000);
    }

    private static final class QueryServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> sidekicks;
        private QueryServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks) {
            this.main = main; this.sidekicks = sidekicks;
        }
        @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> main, () -> sidekicks); }
    }
}
