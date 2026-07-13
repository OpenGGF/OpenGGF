package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestForcedSpinMultiSidekick {

    @Test
    void mainAndThreeSidekicksCrossIndependently() {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        MutableServices services = new MutableServices(main, List.of(first, second, third));
        ForcedSpinObjectInstance trigger = trigger(services);

        trigger.update(0, main);
        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            player.setCentreX((short) 0x0100);
        }
        trigger.update(1, main);

        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            assertTrue(player.getPinballMode(), player.getCode() + " should cross ForcedSpin independently");
        }
    }

    @Test
    void reorderAndCompactRestoreKeepCrossingStateWithPlayerIdentity() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        MutableServices services = new MutableServices(main, List.of(first, second, third));
        ForcedSpinObjectInstance trigger = trigger(services);
        trigger.update(0, main);
        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            player.setCentreX((short) 0x0100);
        }
        trigger.update(1, main);

        services.sidekicks = List.of(third, first, second);
        trigger.update(2, main);
        var blob = CompactFieldCapturer.capture(trigger, context(main, first, second, third));

        TestablePlayableSprite restoredMain = player("restored-main");
        TestablePlayableSprite restoredFirst = player("restored-first");
        TestablePlayableSprite restoredSecond = player("restored-second");
        TestablePlayableSprite restoredThird = player("restored-third");
        services.main = restoredMain;
        services.sidekicks = List.of(restoredThird, restoredFirst, restoredSecond);
        CompactFieldCapturer.restore(trigger, blob,
                context(restoredMain, restoredFirst, restoredSecond, restoredThird));

        Map<?, ?> extensions = extensionStates(trigger);
        assertTrue(extensions.containsKey(restoredFirst));
        assertTrue(extensions.containsKey(restoredSecond));
        assertFalse(extensions.containsKey(first));
        assertFalse(extensions.containsKey(second));
    }

    @Test
    void deadOrOmittedExtensionsCannotRetainOrAcquireCrossingState() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite dead = player("dead");
        MutableServices services = new MutableServices(main, List.of(first, dead));
        ForcedSpinObjectInstance trigger = trigger(services);
        trigger.update(0, main);

        dead.setDead(true);
        dead.setCentreX((short) 0x0100);
        trigger.update(1, main);
        assertFalse(dead.getPinballMode());
        assertFalse(extensionStates(trigger).containsKey(dead));

        services.sidekicks = List.of(first);
        trigger.update(2, main);
        assertFalse(extensionStates(trigger).containsKey(dead));
    }

    private static ForcedSpinObjectInstance trigger(MutableServices services) {
        ForcedSpinObjectInstance trigger = new ForcedSpinObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic2ObjectIds.FORCED_SPIN, 0, 0, false, 0),
                "ForcedSpin");
        trigger.setServices(services);
        return trigger;
    }

    private static TestablePlayableSprite player(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x00F0, (short) 0x0100);
        player.setCpuControlled(!"sonic".equals(code) && !"restored-main".equals(code));
        return player;
    }

    private static RewindCaptureContext context(
            TestablePlayableSprite main, TestablePlayableSprite... sidekicks) {
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(main, PlayerRefId.mainPlayer());
        for (int index = 0; index < sidekicks.length; index++) {
            identities.registerPlayer(sidekicks[index], PlayerRefId.sidekick(index));
        }
        return RewindCaptureContext.withIdentityTable(identities);
    }

    @SuppressWarnings("unchecked")
    private static Map<PlayableEntity, ?> extensionStates(ForcedSpinObjectInstance trigger) throws Exception {
        Field field = ForcedSpinObjectInstance.class.getDeclaredField("extensionStates");
        field.setAccessible(true);
        return (Map<PlayableEntity, ?>) field.get(trigger);
    }

    private static final class MutableServices extends TestObjectServices {
        private PlayableEntity main;
        private List<? extends PlayableEntity> sidekicks;

        private MutableServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks) {
            this.main = main;
            this.sidekicks = sidekicks;
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }
    }
}
