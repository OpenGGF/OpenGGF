package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAizLrzRockPlayerParticipation {

    @Test
    void sideBreakUsesRollAnimationSnapshotNotRollingFlag() {
        TestablePlayableSprite sonic = player("sonic", 0x0F00, 0x1000);
        sonic.setXSpeed((short) 0);
        TestableAizLrzRockObjectInstance rock = new TestableAizLrzRockObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic3kObjectIds.AIZLRZ_ROCK, 0x04, 0, false, 0));
        rock.setCheckpointBatch(new SolidCheckpointBatch(rock, Map.of(
                sonic, sideContact(0x0480, false, 2)
        )));
        rock.setServices(queryOnlyServices(sonic, List.of()));

        rock.update(1, sonic);

        assertTrue(rock.isDestroyed(),
                "Obj_AIZLRZEMZRock checks anim(a1)==2 for push breaks, not the rolling status bit");
        assertEquals(0x0480, sonic.getXSpeed() & 0xFFFF,
                "Player_1 should receive the ROM-saved x_vel from $30(a0)");
    }

    @Test
    void sideBreakUsesPlayerOneSnapshotBeforeSidekickContact() {
        TestablePlayableSprite sonic = player("sonic", 0x0F00, 0x1000);
        sonic.setXSpeed((short) 0);
        TestablePlayableSprite tails = rollingPlayer("tails", 0x0F00, 0x1000);
        tails.setXSpeed((short) 0);
        TestableAizLrzRockObjectInstance rock = new TestableAizLrzRockObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic3kObjectIds.AIZLRZ_ROCK, 0x04, 0, false, 0));
        rock.setCheckpointBatch(new SolidCheckpointBatch(rock, Map.of(
                sonic, sideContact(0x0480, false, 2),
                tails, sideContact(0x0600, true, 2)
        )));
        rock.setServices(queryOnlyServices(sonic, List.of(tails)));

        rock.update(1, sonic);

        assertTrue(rock.isDestroyed(),
                "Obj_AIZLRZEMZRock checks Player_1 before falling through to Player_2");
        assertEquals(0x0480, sonic.getXSpeed() & 0xFFFF,
                "Player_1 should receive the ROM-saved x_vel from $30(a0)");
        assertEquals(0, tails.getXSpeed(),
                "Player_2 must not consume or overwrite Player_1's push-break snapshot");
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

    private static PlayerSolidContactResult sideContact(int xSpeed, boolean rolling, int animationId) {
        return new PlayerSolidContactResult(ContactKind.SIDE, false, false, true, false,
                new PreContactState((short) xSpeed, (short) 0, rolling, animationId), null, 0);
    }

    private static TestObjectServices queryOnlyServices(
            PlayableEntity main,
            List<? extends PlayableEntity> sidekicks) {
        return new QueryOnlyPlayerServices(main, sidekicks);
    }

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
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

    private static final class TestableAizLrzRockObjectInstance extends AizLrzRockObjectInstance {
        private SolidCheckpointBatch checkpointBatch;

        private TestableAizLrzRockObjectInstance(ObjectSpawn spawn) {
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
