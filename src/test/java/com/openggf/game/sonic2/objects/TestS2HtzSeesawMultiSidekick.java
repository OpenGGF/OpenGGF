package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS2HtzSeesawMultiSidekick {

    @Test
    void ballLaunchesMainThenNativeP2ThenExtensionsWithExactVelocity() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        SeesawObjectInstance seesaw = new SeesawObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x14, 0xFF, 0, false, 0), "Seesaw");
        seesaw.setServices(services(main, List.of(first, second, third)));
        writeField(seesaw, "standingPlayer1", main);
        writeField(seesaw, "standingPlayer2", first);
        @SuppressWarnings("unchecked")
        Set<PlayableEntity> extensions = (Set<PlayableEntity>) readField(seesaw, "extensionStandingPlayers");
        extensions.add(second);
        extensions.add(third);

        Method ordered = SeesawObjectInstance.class.getDeclaredMethod("getStandingPlayersInNativeOrder");
        ordered.setAccessible(true);
        assertEquals(List.of(main, first, second, third), ordered.invoke(seesaw));

        SeesawBallObjectInstance ball = new SeesawBallObjectInstance(
                0x400, 0x310, 0x428, 0x320, seesaw, false);
        ball.setServices(services(main, List.of(first, second, third)));
        writeField(ball, "yVel", 0x500);
        Method launch = SeesawBallObjectInstance.class.getDeclaredMethod("launchStandingPlayers");
        launch.setAccessible(true);
        launch.invoke(ball);

        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            assertEquals((short) -0x500, player.getYSpeed());
            assertTrue(player.getAir());
        }
    }

    @Test
    void rewindRestoresStandingReferencesToReplacementRoster() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        SeesawObjectInstance seesaw = new SeesawObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x14, 0xFF, 0, false, 0), "Seesaw");
        seesaw.setServices(services(main, List.of(first, second, third)));
        writeField(seesaw, "standingPlayer1", main);
        writeField(seesaw, "standingPlayer2", first);
        @SuppressWarnings("unchecked")
        Set<PlayableEntity> extensions = (Set<PlayableEntity>) readField(seesaw, "extensionStandingPlayers");
        extensions.add(second);
        extensions.add(third);
        var state = seesaw.captureRewindState(rewindContext(main, first, second, third));

        TestablePlayableSprite restoredMain = player("sonic");
        TestablePlayableSprite restoredFirst = player("tails");
        TestablePlayableSprite restoredSecond = player("knuckles");
        TestablePlayableSprite restoredThird = player("sonic-2");
        seesaw.setServices(services(restoredMain, List.of(restoredFirst, restoredSecond, restoredThird)));
        seesaw.restoreRewindState(state,
                rewindContext(restoredMain, restoredFirst, restoredSecond, restoredThird));

        assertEquals(List.of(restoredMain, restoredFirst, restoredSecond, restoredThird),
                seesaw.getStandingPlayersInNativeOrder());
        assertFalse(seesaw.getStandingPlayersInNativeOrder().contains(main));
        assertFalse(seesaw.getStandingPlayersInNativeOrder().contains(second));
    }

    private static TestObjectServices services(
            TestablePlayableSprite main,
            List<TestablePlayableSprite> sidekicks) {
        return new TestObjectServices() {
            private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> sidekicks);
            @Override public ObjectPlayerQuery playerQuery() { return query; }
        };
    }

    private static TestablePlayableSprite player(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x400, (short) 0x300);
        player.setCpuControlled(!"sonic".equals(code));
        return player;
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void writeField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static RewindCaptureContext rewindContext(
            TestablePlayableSprite main, TestablePlayableSprite... sidekicks) {
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(main, PlayerRefId.mainPlayer());
        for (int index = 0; index < sidekicks.length; index++) {
            identities.registerPlayer(sidekicks[index], PlayerRefId.sidekick(index));
        }
        return RewindCaptureContext.withIdentityTable(identities);
    }
}
