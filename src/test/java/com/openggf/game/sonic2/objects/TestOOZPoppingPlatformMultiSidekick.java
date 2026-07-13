package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestOOZPoppingPlatformMultiSidekick {

    @Test
    void playerTriggeredPlatformLocksMainAndThreeStandingSidekicks() {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        OOZPoppingPlatformObjectInstance platform = new OOZPoppingPlatformObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x33, 1, 0, false, 0), "OOZPoppingPform");
        ObjectManager objectManager = mock(ObjectManager.class);
        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            when(objectManager.isRidingObject(player, platform)).thenReturn(true);
        }
        platform.setServices(new Services(main, List.of(first, second, third), objectManager));

        platform.update(1, main);

        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            assertTrue(player.isObjectControlled(), player.getCode() + " should be captured by the shared pop");
        }
    }

    @Test
    void omittedLockedExtensionIsReleasedAndForgottenImmediately() throws Exception {
        Fixture fixture = fixture();
        fixture.platform.update(1, fixture.main);
        assertTrue(fixture.omitted.isObjectControlled());

        fixture.services.sidekicks = List.of(fixture.first);
        fixture.platform.update(2, fixture.main);

        assertFalse(fixture.omitted.isObjectControlled());
        assertFalse(extensionLocks(fixture.platform).containsKey(fixture.omitted));
    }

    @Test
    void reorderKeepsLocksWithIdentitiesAndUnloadReleasesAll() {
        Fixture fixture = fixture();
        fixture.platform.update(1, fixture.main);
        fixture.services.sidekicks = List.of(fixture.omitted, fixture.first);

        fixture.platform.update(2, fixture.main);
        for (TestablePlayableSprite player : List.of(fixture.main, fixture.first, fixture.omitted)) {
            assertTrue(player.isObjectControlled());
        }

        fixture.platform.onUnload();
        for (TestablePlayableSprite player : List.of(fixture.main, fixture.first, fixture.omitted)) {
            assertFalse(player.isObjectControlled());
        }
    }

    @Test
    void reorderedLocksLaunchTheirOwnPlayersAtApex() throws Exception {
        Fixture fixture = fixture();
        fixture.platform.update(1, fixture.main);
        fixture.services.sidekicks = List.of(fixture.omitted, fixture.first);
        setField(fixture.platform, "currentY", 0x283);
        setField(fixture.platform, "velocity", 0);

        fixture.platform.update(2, fixture.main);

        for (TestablePlayableSprite player : List.of(fixture.main, fixture.first, fixture.omitted)) {
            assertFalse(player.isObjectControlled());
            assertEquals((short) -0x1000, player.getYSpeed());
        }
    }

    @Test
    void extensionLocksRestoreToReplacementPlayerRefs() throws Exception {
        Fixture fixture = fixture();
        fixture.platform.update(1, fixture.main);
        var blob = CompactFieldCapturer.capture(fixture.platform,
                context(fixture.main, fixture.first, fixture.omitted));

        TestablePlayableSprite replacementMain = player("replacement-main");
        TestablePlayableSprite replacementFirst = player("replacement-first");
        TestablePlayableSprite replacementOmitted = player("replacement-extension");
        CompactFieldCapturer.restore(fixture.platform, blob,
                context(replacementMain, replacementFirst, replacementOmitted));

        assertTrue(extensionLocks(fixture.platform).containsKey(replacementOmitted));
        assertFalse(extensionLocks(fixture.platform).containsKey(fixture.omitted));
    }

    private static Fixture fixture() {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite omitted = player("knuckles");
        OOZPoppingPlatformObjectInstance platform = new OOZPoppingPlatformObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x33, 1, 0, false, 0), "OOZPoppingPform");
        ObjectManager objectManager = mock(ObjectManager.class);
        for (TestablePlayableSprite player : List.of(main, first, omitted)) {
            when(objectManager.isRidingObject(player, platform)).thenReturn(true);
        }
        MutableServices services = new MutableServices(main, List.of(first, omitted), objectManager);
        platform.setServices(services);
        return new Fixture(main, first, omitted, platform, services);
    }

    @SuppressWarnings("unchecked")
    private static Map<PlayableEntity, Boolean> extensionLocks(OOZPoppingPlatformObjectInstance platform) throws Exception {
        Field field = OOZPoppingPlatformObjectInstance.class.getDeclaredField("extensionLockedPlayers");
        field.setAccessible(true);
        java.util.Set<PlayableEntity> set = (java.util.Set<PlayableEntity>) field.get(platform);
        Map<PlayableEntity, Boolean> result = new java.util.IdentityHashMap<>();
        set.forEach(player -> result.put(player, true));
        return result;
    }

    private static RewindCaptureContext context(TestablePlayableSprite main, TestablePlayableSprite... sidekicks) {
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(main, PlayerRefId.mainPlayer());
        for (int i = 0; i < sidekicks.length; i++) identities.registerPlayer(sidekicks[i], PlayerRefId.sidekick(i));
        return RewindCaptureContext.withIdentityTable(identities);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Fixture(TestablePlayableSprite main, TestablePlayableSprite first,
                           TestablePlayableSprite omitted, OOZPoppingPlatformObjectInstance platform,
                           MutableServices services) { }

    private static TestablePlayableSprite player(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x400, (short) 0x2F0);
        player.setCpuControlled(!"sonic".equals(code));
        return player;
    }

    private static final class Services extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> sidekicks;
        private final ObjectManager objectManager;

        private Services(PlayableEntity main, List<? extends PlayableEntity> sidekicks,
                ObjectManager objectManager) {
            this.main = main;
            this.sidekicks = sidekicks;
            this.objectManager = objectManager;
        }

        @Override public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }

        @Override public ObjectManager objectManager() {
            return objectManager;
        }
    }

    private static final class MutableServices extends TestObjectServices {
        private final PlayableEntity main;
        private List<? extends PlayableEntity> sidekicks;
        private final ObjectManager objectManager;
        private MutableServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks,
                                ObjectManager objectManager) {
            this.main = main; this.sidekicks = sidekicks; this.objectManager = objectManager;
        }
        @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> main, () -> sidekicks); }
        @Override public ObjectManager objectManager() { return objectManager; }
    }
}
