package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS2DeathEggTeamSafety {
    @Test
    void defeatWalkContainsEveryValidExtensionPlayer() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite p2 = player("tails");
        TestablePlayableSprite extra1 = player("knuckles");
        TestablePlayableSprite extra2 = player("sonic-extra");
        Sonic2DeathEggRobotInstance boss = new Sonic2DeathEggRobotInstance(
                new ObjectSpawn(0x600, 0x15C, 0, 0, 0, false, 0));
        boss.setServices(new QueryServices(main, List.of(p2, extra1, extra2)));
        Method method = Sonic2DeathEggRobotInstance.class.getDeclaredMethod(
                "updateDefeatWalkPlayer", int.class, AbstractPlayableSprite.class);
        method.setAccessible(true);

        method.invoke(boss, 1, main);

        for (TestablePlayableSprite extension : List.of(p2, extra1, extra2)) {
            assertTrue(extension.isControlLocked());
            assertTrue(extension.isForceInputRight());
        }
    }

    @Test
    void omissionDeathAndUnloadReleaseOnlyBossOwnedControl() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite p2 = player("tails");
        TestablePlayableSprite omitted = player("knuckles");
        TestablePlayableSprite dead = player("sonic-extra");
        QueryServices services = new QueryServices(main, List.of(p2, omitted, dead));
        Sonic2DeathEggRobotInstance boss = boss(services);
        invokeWalk(boss, main);

        dead.setDead(true);
        services.sidekicks = List.of(p2, dead);
        invokeWalk(boss, main);

        assertFalse(omitted.isControlLocked());
        assertFalse(dead.isControlLocked());
        boss.onUnload();
        assertFalse(p2.isControlLocked());
    }

    @Test
    void unrelatedPreexistingControlIsNotClaimedOrCleared() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite otherOwned = player("tails");
        otherOwned.setControlLocked(true);
        otherOwned.setForcedInputMask(AbstractPlayableSprite.INPUT_LEFT);
        Sonic2DeathEggRobotInstance boss = boss(new QueryServices(main, List.of(otherOwned)));

        invokeWalk(boss, main);
        boss.onUnload();

        assertTrue(otherOwned.isControlLocked());
        assertTrue((otherOwned.getForcedInputMask() & AbstractPlayableSprite.INPUT_LEFT) != 0);
    }

    @Test
    void rewindRestoresBossOwnershipToReplacementPlayerRefs() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite oldSidekick = player("tails");
        Sonic2DeathEggRobotInstance boss = boss(new QueryServices(main, List.of(oldSidekick)));
        invokeWalk(boss, main);
        var blob = CompactFieldCapturer.capture(boss, context(main, oldSidekick));

        TestablePlayableSprite replacementMain = player("replacement-main");
        TestablePlayableSprite replacementSidekick = player("replacement-sidekick");
        CompactFieldCapturer.restore(boss, blob, context(replacementMain, replacementSidekick));

        assertTrue(ownedPlayers(boss).contains(replacementSidekick));
        assertFalse(ownedPlayers(boss).contains(oldSidekick));
    }

    private static Sonic2DeathEggRobotInstance boss(QueryServices services) {
        Sonic2DeathEggRobotInstance boss = new Sonic2DeathEggRobotInstance(
                new ObjectSpawn(0x600, 0x15C, 0, 0, 0, false, 0));
        boss.setServices(services);
        return boss;
    }

    private static void invokeWalk(Sonic2DeathEggRobotInstance boss, AbstractPlayableSprite main) throws Exception {
        Method method = Sonic2DeathEggRobotInstance.class.getDeclaredMethod(
                "updateDefeatWalkPlayer", int.class, AbstractPlayableSprite.class);
        method.setAccessible(true);
        method.invoke(boss, 1, main);
    }

    private static RewindCaptureContext context(PlayableEntity main, PlayableEntity sidekick) {
        RewindIdentityTable ids = new RewindIdentityTable();
        ids.registerPlayer(main, PlayerRefId.mainPlayer());
        ids.registerPlayer(sidekick, PlayerRefId.sidekick(0));
        return RewindCaptureContext.withIdentityTable(ids);
    }

    @SuppressWarnings("unchecked")
    private static Set<PlayableEntity> ownedPlayers(Sonic2DeathEggRobotInstance boss) throws Exception {
        Field field = Sonic2DeathEggRobotInstance.class.getDeclaredField("endingControlledPlayers");
        field.setAccessible(true);
        return (Set<PlayableEntity>) field.get(boss);
    }

    private static TestablePlayableSprite player(String code) {
        return new TestablePlayableSprite(code, (short) 0x700, (short) 0x200);
    }

    private static final class QueryServices extends TestObjectServices {
        private final PlayableEntity main;
        private List<? extends PlayableEntity> sidekicks;
        private QueryServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks) {
            this.main = main; this.sidekicks = sidekicks;
        }
        @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> main, () -> sidekicks); }
    }
}
