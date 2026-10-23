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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
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

    @Test
    void repeatedEmptyAndOffCenterWaitsDoNotAdmitUntilEveryRiderIsCentered() {
        Fixture f = fixture();
        var players = List.of(f.main, f.first, f.omitted);
        for (var p : players) when(f.services.objectManager.isRidingObject(p, f.platform)).thenReturn(false);
        for (int frame = 0; frame < 128; frame++) {
            f.platform.update(frame, f.main);
            for (var p : players) assertFalse(p.isObjectControlled());
            assertEquals(0x300, f.platform.getY());
        }
        for (var p : players) when(f.services.objectManager.isRidingObject(p, f.platform)).thenReturn(true);
        f.main.setCentreX((short) 0x410); // exclusive center-trigger boundary; still standing
        for (int frame = 128; frame < 256; frame++) {
            f.platform.update(frame, f.main);
            for (var p : players) assertFalse(p.isObjectControlled());
            assertEquals(0x300, f.platform.getY());
        }
        f.main.setCentreX((short) 0x400);
        f.platform.update(256, f.main);
        for (var p : players) assertTrue(p.isObjectControlled());
    }

    @Test
    void nestedUnloadPreservesTheOuterNativeOrderCarrySnapshot() {
        Fixture f = fixture(spy(player("sonic")), spy(player("tails")), spy(player("knuckles")));
        f.platform.update(1, f.main);
        f.services.sidekicks = List.of(f.omitted, f.first);
        var carried = new ArrayList<TestablePlayableSprite>();
        AtomicBoolean nested = new AtomicBoolean();
        for (var p : List.of(f.main, f.first, f.omitted)) {
            doAnswer(call -> {
                carried.add(p);
                if (nested.compareAndSet(false, true)) f.platform.onUnload();
                return false;
            }).when(p).getDead();
        }
        f.platform.update(2, f.main);
        assertTrue(nested.get());
        assertEquals(List.of(f.main, f.omitted, f.first), carried);
        for (var p : carried) {
            assertFalse(p.isObjectControlled());
            assertEquals(f.platform.getY() - 8, p.getCentreY(), "outer snapshot still carries every original rider");
        }
    }

    @Test
    void unloadDuringAdmissionDoesNotDiscardTheStandingSnapshot() {
        Fixture f = fixture(spy(player("sonic")));
        AtomicBoolean nested = new AtomicBoolean();
        doAnswer(call -> {
            if (nested.compareAndSet(false, true)) f.platform.onUnload();
            return call.callRealMethod();
        }).when(f.main).setXSpeed((short) 0);
        f.platform.update(1, f.main);
        assertTrue(nested.get());
        for (var p : List.of(f.main, f.first, f.omitted)) assertTrue(p.isObjectControlled());
        f.platform.onUnload();
        for (var p : List.of(f.main, f.first, f.omitted)) assertFalse(p.isObjectControlled());
    }

    @Test
    void callbackExceptionsAllowSubsequentAdmissionAndCarry() {
        Fixture f = fixture(spy(player("sonic")));
        doThrow(new IllegalStateException("lock callback")).when(f.main).setXSpeed((short) 0);
        assertThrows(IllegalStateException.class, () -> f.platform.update(1, f.main));
        doCallRealMethod().when(f.main).setXSpeed((short) 0);
        f.platform.update(2, f.main);
        for (var p : List.of(f.main, f.first, f.omitted)) assertTrue(p.isObjectControlled());
        doThrow(new IllegalStateException("carry callback")).when(f.main).getDead();
        assertThrows(IllegalStateException.class, () -> f.platform.update(3, f.main));
        doCallRealMethod().when(f.main).getDead();
        f.platform.update(4, f.main);
        for (var p : List.of(f.main, f.first, f.omitted)) assertEquals(f.platform.getY() - 8, p.getCentreY());
    }

    @Test
    void largeTeamsAreAdmittedCarriedAndReleasedWithoutTruncation() {
        var main = player("sonic");
        var followers = new ArrayList<TestablePlayableSprite>();
        for (int i = 0; i < 12; i++) followers.add(player("follower-" + i));
        var platform = new OOZPoppingPlatformObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x33, 1, 0, false, 0), "OOZPoppingPform");
        var manager = mock(ObjectManager.class);
        when(manager.isRidingObject(main, platform)).thenReturn(true);
        for (var p : followers) when(manager.isRidingObject(p, platform)).thenReturn(true);
        platform.setServices(new Services(main, followers, manager));
        platform.update(1, main);
        assertTrue(main.isObjectControlled());
        for (var p : followers) assertTrue(p.isObjectControlled());
        platform.update(2, main);
        assertEquals(platform.getY() - 8, main.getCentreY());
        for (var p : followers) assertEquals(platform.getY() - 8, p.getCentreY());
        platform.onUnload();
        assertFalse(main.isObjectControlled());
        for (var p : followers) assertFalse(p.isObjectControlled());
    }

    @Test
    void capturedRiseRestoresAndReplaysEveryUpdateThroughExactApex() {
        Fixture f = fixture();
        f.platform.update(1, f.main);
        var players = List.of(f.main, f.first, f.omitted);
        var context = context(f.main, f.first, f.omitted);
        var state = f.platform.captureRewindState(context);
        var playerStates = players.stream().map(TestablePlayableSprite::captureRewindState).toList();
        var expected = new ArrayList<List<Integer>>();
        for (int frame = 2; frame <= 17; frame++) {
            f.platform.update(frame, f.main);
            expected.add(observe(f));
        }
        for (var p : players) {
            assertFalse(p.isObjectControlled());
            assertEquals(-0x1000, p.getYSpeed());
        }
        f.platform.restoreRewindState(state, context);
        for (int i = 0; i < players.size(); i++) players.get(i).restoreRewindState(playerStates.get(i));
        for (int frame = 2; frame <= 17; frame++) {
            f.platform.update(frame, f.main);
            assertEquals(expected.get(frame - 2), observe(f));
        }
    }

    private static List<Integer> observe(Fixture f) {
        return List.of(f.platform.getY(), (int) f.main.getCentreY(), (int) f.first.getCentreY(), (int) f.omitted.getCentreY(),
                (int) f.main.getYSpeed(), (int) f.first.getYSpeed(), (int) f.omitted.getYSpeed(),
                f.main.isObjectControlled() ? 1 : 0, f.first.isObjectControlled() ? 1 : 0, f.omitted.isObjectControlled() ? 1 : 0);
    }

    private static Fixture fixture() {
        return fixture(player("sonic"));
    }

    private static Fixture fixture(TestablePlayableSprite main) {
        return fixture(main, player("tails"), player("knuckles"));
    }

    private static Fixture fixture(TestablePlayableSprite main, TestablePlayableSprite first,
                                   TestablePlayableSprite omitted) {
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
