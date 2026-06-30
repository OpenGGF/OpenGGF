package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TestS3kBreakableWallPlayerParticipation {

    @Test
    void breakableWallAppliesCheckpointContactToQueryOnlySidekick() {
        TestablePlayableSprite main = player("sonic", 0x1200, 0x1000);
        TestablePlayableSprite sidekick = rollingPlayer("tails", 0x0F00, 0x1000);
        TestableBreakableWallObjectInstance wall = new TestableBreakableWallObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic3kObjectIds.BREAKABLE_WALL, 0, 0, false, 0));
        wall.setCheckpointBatch(new SolidCheckpointBatch(wall, Map.of(
                main, noContact(),
                sidekick, sideContact(0x0480)
        )));
        wall.setServices(queryOnlyServices(main, List.of(sidekick)));

        wall.update(1, main);

        assertTrue(wall.isDestroyed(),
                "S3K breakable wall should use ObjectPlayerQuery participants for sidekick checkpoint contact");
    }

    @Test
    void breakableWallPreservesUpdatePrimaryContactWhenQueryMainDiffers() {
        TestablePlayableSprite updatePrimary = rollingPlayer("sonic", 0x0F00, 0x1000);
        TestablePlayableSprite queriedMain = player("knuckles", 0x1200, 0x1000);
        TestableBreakableWallObjectInstance wall = new TestableBreakableWallObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic3kObjectIds.BREAKABLE_WALL, 0, 0, false, 0));
        wall.setCheckpointBatch(new SolidCheckpointBatch(wall, Map.of(
                updatePrimary, sideContact(0x0480),
                queriedMain, noContact()
        )));
        wall.setServices(queryOnlyServices(queriedMain, List.of()));

        wall.update(1, updatePrimary);

        assertTrue(wall.isDestroyed(),
                "S3K breakable wall should process the update-time primary before queried participants");
    }

    @Test
    void breakableWallUsesPlayerOneRollAnimationSnapshotBeforeSidekickContact() {
        TestablePlayableSprite sonic = player("sonic", 0x0F00, 0x1000);
        sonic.setXSpeed((short) 0);
        TestablePlayableSprite tails = rollingPlayer("tails", 0x0F00, 0x1000);
        tails.setXSpeed((short) 0);
        TestableBreakableWallObjectInstance wall = new TestableBreakableWallObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic3kObjectIds.BREAKABLE_WALL, 0, 0, false, 0));
        wall.setCheckpointBatch(new SolidCheckpointBatch(wall, Map.of(
                sonic, sideContact(0x0480, false, 2),
                tails, sideContact(0x0600, true, 2)
        )));
        wall.setServices(queryOnlyServices(sonic, List.of(tails)));

        wall.update(1, sonic);

        assertTrue(wall.isDestroyed(),
                "Obj_BreakableWall checks Player_1's anim/x_vel snapshot before falling through to Player_2");
        assertEquals(0x0480, sonic.getXSpeed() & 0xFFFF,
                "Player_1 should receive the ROM-saved x_vel from $30(a0)");
        assertEquals(0, tails.getXSpeed(),
                "Player_2 must not consume the break when Player_1 satisfies the ROM roll-animation gate");
    }

    @Test
    void iczBreakableWallAppliesKnucklesContactToQueryOnlySidekick() {
        TestablePlayableSprite main = player("knuckles", 0x1200, 0x0700);
        TestablePlayableSprite sidekick = player("tails", 0x3200, 0x0700);
        ObjectManager objectManager = mock(ObjectManager.class);
        TestableIczBreakableWallObjectInstance wall = new TestableIczBreakableWallObjectInstance(
                new ObjectSpawn(0x3200, 0x0700, Sonic3kObjectIds.ICZ_BREAKABLE_WALL, 0, 0, false, 0));
        wall.setCheckpointBatch(new SolidCheckpointBatch(wall, Map.of(
                main, noContact(),
                sidekick, sideContact(0)
        )));
        wall.setServices(queryOnlyKnucklesServices(main, List.of(sidekick), objectManager));

        wall.update(1, main);

        assertTrue(wall.isDestroyed(),
                "ICZ breakable wall should use ObjectPlayerQuery participants for sidekick checkpoint contact");
        verify(objectManager, times(9)).addDynamicObjectAfterCurrent(any());
    }

    @Test
    void iczBreakableWallPreservesUpdatePrimaryContactWhenQueryMainDiffers() {
        TestablePlayableSprite updatePrimary = player("knuckles", 0x3200, 0x0700);
        TestablePlayableSprite queriedMain = player("sonic", 0x1200, 0x0700);
        ObjectManager objectManager = mock(ObjectManager.class);
        TestableIczBreakableWallObjectInstance wall = new TestableIczBreakableWallObjectInstance(
                new ObjectSpawn(0x3200, 0x0700, Sonic3kObjectIds.ICZ_BREAKABLE_WALL, 0, 0, false, 0));
        wall.setCheckpointBatch(new SolidCheckpointBatch(wall, Map.of(
                updatePrimary, sideContact(0),
                queriedMain, noContact()
        )));
        wall.setServices(queryOnlyKnucklesServices(queriedMain, List.of(), objectManager));

        wall.update(1, updatePrimary);

        assertTrue(wall.isDestroyed(),
                "ICZ breakable wall should process the update-time primary before queried participants");
        verify(objectManager, times(9)).addDynamicObjectAfterCurrent(any());
    }

    private static TestablePlayableSprite player(String code, int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) x, (short) y);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        return player;
    }

    private static TestablePlayableSprite rollingPlayer(String code, int x, int y) {
        TestablePlayableSprite player = player(code, x, y);
        player.setAir(false);
        player.setRolling(true);
        player.setXSpeed((short) 0x0480);
        player.setCpuControlled(true);
        return player;
    }

    private static PlayerSolidContactResult noContact() {
        return new PlayerSolidContactResult(ContactKind.NONE, false, false, false, false,
                PreContactState.ZERO, null, 0);
    }

    private static PlayerSolidContactResult sideContact(int xSpeed) {
        return sideContact(xSpeed, true, 2);
    }

    private static PlayerSolidContactResult sideContact(int xSpeed, boolean rolling, int animationId) {
        return new PlayerSolidContactResult(ContactKind.SIDE, false, false, true, false,
                new PreContactState((short) xSpeed, (short) 0, rolling, animationId), null, 0);
    }

    private static TestObjectServices queryOnlyServices(
            PlayableEntity main,
            List<? extends PlayableEntity> sidekicks) {
        return new QueryOnlyPlayerServices(main, sidekicks);
    }

    private static TestObjectServices queryOnlyKnucklesServices(
            PlayableEntity main,
            List<? extends PlayableEntity> sidekicks,
            ObjectManager objectManager) {
        SonicConfigurationService configuration = SonicConfigurationService.createStandalone();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        return new QueryOnlyPlayerServices(main, sidekicks) {
            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }

            @Override
            public SonicConfigurationService configuration() {
                return configuration;
            }
        };
    }

    private static class QueryOnlyPlayerServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> queriedSidekicks;
        private final SonicConfigurationService configuration = SonicConfigurationService.createStandalone();

        private QueryOnlyPlayerServices(PlayableEntity main, List<? extends PlayableEntity> queriedSidekicks) {
            this.main = main;
            this.queriedSidekicks = List.copyOf(queriedSidekicks);
            this.configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            this.configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return List.of();
        }

        @Override
        public SonicConfigurationService configuration() {
            return configuration;
        }
    }

    private static final class TestableBreakableWallObjectInstance extends BreakableWallObjectInstance {
        private SolidCheckpointBatch checkpointBatch;

        private TestableBreakableWallObjectInstance(ObjectSpawn spawn) {
            super(spawn);
        }

        private void setCheckpointBatch(SolidCheckpointBatch checkpointBatch) {
            this.checkpointBatch = checkpointBatch;
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            return checkpointBatch;
        }
    }

    private static final class TestableIczBreakableWallObjectInstance extends IczBreakableWallObjectInstance {
        private SolidCheckpointBatch checkpointBatch;

        private TestableIczBreakableWallObjectInstance(ObjectSpawn spawn) {
            super(spawn);
        }

        private void setCheckpointBatch(SolidCheckpointBatch checkpointBatch) {
            this.checkpointBatch = checkpointBatch;
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            return checkpointBatch;
        }
    }
}
