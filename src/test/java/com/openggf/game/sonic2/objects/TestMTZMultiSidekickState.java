package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestMTZMultiSidekickState {

    @Test
    void spinTubeCapturesMainAndThreeSidekicksAndReleasesThemOnUnload() throws Exception {
        Roster roster = rosterAt(0x7A8, 0x270);
        MTZSpinTubeObjectInstance tube = new MTZSpinTubeObjectInstance(
                new ObjectSpawn(0x7A8, 0x270, Sonic2ObjectIds.MTZ_SPIN_TUBE, 0, 0, false, 0));
        tube.setServices(roster.services);

        tube.update(0, roster.main);

        for (TestablePlayableSprite player : roster.all()) assertTrue(player.isObjectControlled());
        assertEquals(2, stateMap(tube, "extensionStates").size());
        tube.onUnload();
        for (TestablePlayableSprite player : roster.all()) assertFalse(player.isObjectControlled());
    }

    @Test
    void nutExtensionMapRestoresToReplacementPlayerRefs() throws Exception {
        Roster oldRoster = rosterAt(0x1000, 0x1000);
        NutObjectInstance nut = new NutObjectInstance(
                new ObjectSpawn(0x1000, 0x1020, Sonic2ObjectIds.NUT, 0, 0, false, 0), "Nut");
        nut.setServices(oldRoster.services);
        SolidContact standing = new SolidContact(true, false, false, true, false);
        for (TestablePlayableSprite player : oldRoster.all()) nut.onSolidContact(player, standing, 0);
        nut.update(0, oldRoster.main);
        var blob = CompactFieldCapturer.capture(nut, context(oldRoster));

        Roster replacement = rosterAt(0x1000, 0x1000);
        nut.setServices(replacement.services);
        CompactFieldCapturer.restore(nut, blob, context(replacement));

        Map<?, ?> states = stateMap(nut, "extensionPlayerStates");
        assertTrue(states.containsKey(replacement.second));
        assertTrue(states.containsKey(replacement.third));
        assertFalse(states.containsKey(oldRoster.second));
        assertFalse(states.containsKey(oldRoster.third));
    }

    @Test
    void tubeExtensionMapRestoresToReplacementPlayerRefs() throws Exception {
        Roster oldRoster = rosterAt(0x7A8, 0x270);
        MTZSpinTubeObjectInstance tube = new MTZSpinTubeObjectInstance(
                new ObjectSpawn(0x7A8, 0x270, Sonic2ObjectIds.MTZ_SPIN_TUBE, 0, 0, false, 0));
        tube.setServices(oldRoster.services);
        tube.update(0, oldRoster.main);
        var blob = CompactFieldCapturer.capture(tube, context(oldRoster));

        Roster replacement = rosterAt(0x7A8, 0x270);
        tube.setServices(replacement.services);
        CompactFieldCapturer.restore(tube, blob, context(replacement));

        Map<?, ?> states = stateMap(tube, "extensionStates");
        assertTrue(states.containsKey(replacement.second));
        assertTrue(states.containsKey(replacement.third));
        assertFalse(states.containsKey(oldRoster.second));
        assertFalse(states.containsKey(oldRoster.third));
    }

    @Test
    void nutProcessesEveryStandingExtensionWithoutAliasingNativeP2() throws Exception {
        Roster roster = rosterAt(0x1000, 0x1000);
        NutObjectInstance nut = new NutObjectInstance(
                new ObjectSpawn(0x1000, 0x1020, Sonic2ObjectIds.NUT, 0, 0, false, 0), "Nut");
        nut.setServices(roster.services);
        SolidContact standing = new SolidContact(true, false, false, true, false);
        for (TestablePlayableSprite player : roster.all()) nut.onSolidContact(player, standing, 0);

        nut.update(0, roster.main);

        assertEquals(2, stateMap(nut, "extensionPlayerStates").size());
        for (TestablePlayableSprite player : roster.all()) assertEquals(0x1000, player.getCentreX());
    }

    @SuppressWarnings("unchecked")
    private static Map<PlayableEntity, ?> stateMap(Object object, String fieldName) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<PlayableEntity, ?>) field.get(object);
    }

    private static Roster rosterAt(int x, int y) {
        TestablePlayableSprite main = player("main", x, y);
        TestablePlayableSprite first = player("first", x, y);
        TestablePlayableSprite second = player("second", x, y);
        TestablePlayableSprite third = player("third", x, y);
        MutableServices services = new MutableServices(main, List.of(first, second, third));
        return new Roster(main, first, second, third, services);
    }

    private static TestablePlayableSprite player(String code, int x, int y) {
        return new TestablePlayableSprite(code, (short) x, (short) y);
    }

    private static RewindCaptureContext context(Roster roster) {
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(roster.main, PlayerRefId.mainPlayer());
        identities.registerPlayer(roster.first, PlayerRefId.sidekick(0));
        identities.registerPlayer(roster.second, PlayerRefId.sidekick(1));
        identities.registerPlayer(roster.third, PlayerRefId.sidekick(2));
        return RewindCaptureContext.withIdentityTable(identities);
    }

    private record Roster(TestablePlayableSprite main, TestablePlayableSprite first,
                          TestablePlayableSprite second, TestablePlayableSprite third,
                          MutableServices services) {
        List<TestablePlayableSprite> all() { return List.of(main, first, second, third); }
    }

    private static final class MutableServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> sidekicks;

        private MutableServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks) {
            this.main = main; this.sidekicks = sidekicks;
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }
    }
}
