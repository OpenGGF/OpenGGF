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

class TestS2VineSwitchMultiSidekick {

    @Test
    void mainAndThreeSidekicksGrabIndependentlyInOneNativeOrderedPass() {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        VineSwitchObjectInstance vine = vine(main, List.of(first, second, third));

        vine.update(1, main);

        assertControlled(main, first, second, third);

        third.setDead(true);
        vine.update(2, main);

        assertFalse(third.isObjectControlled());
        assertControlled(main, first, second);

        vine.onUnload();
        assertControlled(main, first);
        assertReleased(second, third);
    }

    @Test
    void sidekickReorderDoesNotTransferOrDropGrabOwnership() {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        VineSwitchObjectInstance vine = vine(main, List.of(first, second, third));
        vine.update(1, main);

        vine.setServices(services(main, List.of(third, first, second)));
        vine.update(2, main);

        assertControlled(main, first, second, third);
    }

    @Test
    void rewindRestoresGrabOwnershipThroughReplacementPlayerReferences() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        VineSwitchObjectInstance vine = vine(main, List.of(first, second, third));
        vine.update(1, main);
        var state = vine.captureRewindState(rewindContext(main, first, second, third));

        TestablePlayableSprite restoredMain = player("sonic");
        TestablePlayableSprite restoredFirst = player("tails");
        TestablePlayableSprite restoredSecond = player("knuckles");
        TestablePlayableSprite restoredThird = player("sonic-2");
        vine.setServices(services(restoredMain, List.of(restoredFirst, restoredSecond, restoredThird)));
        vine.restoreRewindState(state,
                rewindContext(restoredMain, restoredFirst, restoredSecond, restoredThird));

        assertTrue(readExtensionStates(vine).containsKey(restoredSecond));
        assertTrue(readExtensionStates(vine).containsKey(restoredThird));
        assertFalse(readExtensionStates(vine).containsKey(second));
        assertFalse(readExtensionStates(vine).containsKey(third));
        for (TestablePlayableSprite player : List.of(
                restoredMain, restoredFirst, restoredSecond, restoredThird)) {
            player.setObjectControlled(true);
        }
        vine.onUnload();
        assertControlled(restoredMain, restoredFirst);
        assertReleased(restoredSecond, restoredThird);
    }

    private static VineSwitchObjectInstance vine(
            TestablePlayableSprite main,
            List<TestablePlayableSprite> sidekicks) {
        VineSwitchObjectInstance vine = new VineSwitchObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x7F, 1, 0, false, 0), "VineSwitch");
        vine.setServices(services(main, sidekicks));
        return vine;
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
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x400, (short) 0x330);
        player.setCpuControlled(!"sonic".equals(code));
        return player;
    }

    private static void assertControlled(TestablePlayableSprite... players) {
        for (TestablePlayableSprite player : players) {
            assertTrue(player.isObjectControlled(), player.getCode() + " should be owned by VineSwitch");
        }
    }

    private static void assertReleased(TestablePlayableSprite... players) {
        for (TestablePlayableSprite player : players) {
            assertFalse(player.isObjectControlled(), player.getCode() + " should be released by VineSwitch");
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
    private static Map<PlayableEntity, ?> readExtensionStates(VineSwitchObjectInstance vine) throws Exception {
        var field = VineSwitchObjectInstance.class.getDeclaredField("extensionStates");
        field.setAccessible(true);
        return (Map<PlayableEntity, ?>) field.get(vine);
    }
}
