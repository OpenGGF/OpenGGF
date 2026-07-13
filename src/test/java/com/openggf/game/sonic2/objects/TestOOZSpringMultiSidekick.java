package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestOOZSpringMultiSidekick {

    @Test
    void verticalPressureSpringLaunchesMainAndThreeStandingSidekicks() {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        OOZSpringObjectInstance spring = new OOZSpringObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x45, 0, 0, false, 0), "OOZSpring");
        SolidExecutionRegistry registry = mock(SolidExecutionRegistry.class);
        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            when(registry.previousStanding(spring, player))
                    .thenReturn(new PlayerStandingState(ContactKind.TOP, true, false));
        }
        spring.setServices(new Services(main, List.of(first, second, third), registry));
        for (int frame = 0; frame <= 9; frame++) {
            spring.update(frame, main);
        }

        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            assertEquals((short) -0x1000, player.getYSpeed(),
                    player.getCode() + " should receive the vertical pressure-spring launch");
        }
    }

    private static TestablePlayableSprite player(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x400, (short) 0x2E0);
        player.setCpuControlled(!"sonic".equals(code));
        return player;
    }

    private static final class Services extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> sidekicks;
        private final SolidExecutionRegistry registry;

        private Services(PlayableEntity main, List<? extends PlayableEntity> sidekicks,
                SolidExecutionRegistry registry) {
            this.main = main;
            this.sidekicks = sidekicks;
            this.registry = registry;
        }

        @Override public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }

        @Override public SolidExecutionRegistry solidExecutionRegistry() {
            return registry;
        }
    }
}
