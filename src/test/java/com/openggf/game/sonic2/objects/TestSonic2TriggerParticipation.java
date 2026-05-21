package com.openggf.game.sonic2.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2TriggerParticipation {

    @Test
    void arrowShooterDetectsQueryOnlySidekick() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x1800, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1010, 0x1000);
        ArrowShooterObjectInstance shooter = new ArrowShooterObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x22, 0, 0, false, 0),
                "ArrowShooter");
        shooter.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        shooter.update(0, main);

        assertEquals(1, intField(shooter, "currentAnim"),
                "Arrow Shooter should use ObjectPlayerQuery participants for detection");
    }

    @Test
    void barrierRisesForQueryOnlySidekickInDetectionZone() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x0F00, 0x1000);
        BarrierObjectInstance barrier = new BarrierObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x2D, 0, 0, false, 0),
                "Barrier");
        barrier.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        barrier.update(0, main);

        assertEquals(0x0FF8, barrier.getY(),
                "Barrier should rise when a query participant enters its detection zone");
    }

    @Test
    void wfzPaletteSwitcherUsesQueryOnlySidekickCrossing() {
        TestablePlayableSprite main = player("sonic", 0x0800, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x0FF0, 0x1000);
        GameStateManager gameState = new GameStateManager();
        WFZPalSwitcherObjectInstance switcher = new WFZPalSwitcherObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x8B, 0, 0, false, 0),
                "WFZPalSwitcher");
        switcher.setServices(new QueryOnlyPlayerServices(main, List.of(tails)).withGameState(gameState));

        switcher.update(0, main);
        tails.setCentreX((short) 0x1010);
        switcher.update(1, main);

        assertTrue(gameState.isWfzFireToggle(),
                "WFZ palette switcher should route sidekick crossing through ObjectPlayerQuery");
    }

    private static TestablePlayableSprite player(String code, int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) x, (short) y);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        return player;
    }

    private static int intField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
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
