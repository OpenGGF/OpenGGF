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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS2MovingVineMultiSidekick {

    @Test
    void mainPlusThreeSurviveReorderAndUnloadByIdentity() {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        MovingVineObjectInstance vine = new MovingVineObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x80, 0, 0, false, 0), "MovingVine");
        vine.setServices(services(main, List.of(first, second, third)));

        vine.update(1, main);
        assertControlled(main, first, second, third);

        vine.setServices(services(main, List.of(third, first, second)));
        vine.update(2, main);
        assertControlled(main, first, second, third);

        vine.onUnload();
        assertTrue(main.isObjectControlled(), "native P1 preserves the ROM MarkObjGone quirk");
        assertTrue(third.isObjectControlled(), "the reordered native P2 preserves the ROM MarkObjGone quirk");
        for (TestablePlayableSprite player : List.of(first, second)) {
            assertFalse(player.isObjectControlled(), player.getCode() + " must be released on unload");
        }
    }

    @Test
    void rewindRestoresExtensionGrabStateThroughReplacementPlayerReferences() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        MovingVineObjectInstance vine = new MovingVineObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x80, 0, 0, false, 0), "MovingVine");
        vine.setServices(services(main, List.of(first, second, third)));
        vine.update(1, main);
        var state = vine.captureRewindState(rewindContext(main, first, second, third));

        TestablePlayableSprite restoredMain = player("sonic");
        TestablePlayableSprite restoredFirst = player("tails");
        TestablePlayableSprite restoredSecond = player("knuckles");
        TestablePlayableSprite restoredThird = player("sonic-2");
        vine.setServices(services(restoredMain, List.of(restoredFirst, restoredSecond, restoredThird)));
        vine.restoreRewindState(state,
                rewindContext(restoredMain, restoredFirst, restoredSecond, restoredThird));

        Map<PlayableEntity, Boolean> restored = readGrabbed(vine);
        assertTrue(restored.get(restoredSecond));
        assertTrue(restored.get(restoredThird));
        assertFalse(restored.containsKey(second));
        assertFalse(restored.containsKey(third));
        for (TestablePlayableSprite player : List.of(
                restoredMain, restoredFirst, restoredSecond, restoredThird)) {
            player.setObjectControlled(true);
        }
        vine.onUnload();
        assertTrue(restoredMain.isObjectControlled());
        assertTrue(restoredFirst.isObjectControlled());
        for (TestablePlayableSprite player : List.of(restoredSecond, restoredThird)) {
            assertFalse(player.isObjectControlled());
        }
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
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x1000, (short) 0x1088);
        player.setCpuControlled(!"sonic".equals(code));
        return player;
    }

    private static void assertControlled(TestablePlayableSprite... players) {
        for (TestablePlayableSprite player : players) {
            assertTrue(player.isObjectControlled(), player.getCode() + " should remain on MovingVine");
        }
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

    @SuppressWarnings("unchecked")
    private static Map<PlayableEntity, Boolean> readGrabbed(MovingVineObjectInstance vine) throws Exception {
        var field = MovingVineObjectInstance.class.getDeclaredField("extraPlayerGrabbed");
        field.setAccessible(true);
        return (Map<PlayableEntity, Boolean>) field.get(vine);
    }
}
