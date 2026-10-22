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
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    void repeatedEmptyAndOffCenterWaitsReuseClearedStandingStorage() throws Exception {
        Fixture f=fixture(spy(player("sonic")));
        var storage=scratchPlayers(f.platform,"standingScratch");
        for(var p:List.of(f.main,f.first,f.omitted)) when(f.services.objectManager.isRidingObject(p,f.platform)).thenReturn(false);
        for(int i=0;i<128;i++) { f.platform.update(i,f.main); assertScratchReleased(f.platform); }
        for(var p:List.of(f.main,f.first,f.omitted)) when(f.services.objectManager.isRidingObject(p,f.platform)).thenReturn(true);
        f.main.setCentreX((short)0x410); // exclusive center-trigger boundary; still standing
        AtomicInteger visits=new AtomicInteger();
        doAnswer(call -> {
            assertSame(storage,scratchPlayers(f.platform,"standingScratch"));
            assertEquals(List.of(f.main,f.first,f.omitted),storage,"all contacts are snapshotted before X validation");
            assertTrue(scratchBorrowed(f.platform,"standingScratch"));
            visits.incrementAndGet();
            return call.callRealMethod();
        }).when(f.main).getCentreX();
        for(int i=128;i<256;i++) { f.platform.update(i,f.main); assertScratchReleased(f.platform); }
        assertEquals(128,visits.get()); assertFalse(f.main.isObjectControlled());
        f.main.setCentreX((short)0x400);
        doCallRealMethod().when(f.main).getCentreX();
        f.platform.update(256,f.main);
        assertTrue(f.main.isObjectControlled()); assertTrue(f.first.isObjectControlled());
        assertScratchReleased(f.platform);
    }

    @Test
    void nestedUnloadDoesNotOverwriteTheOuterNativeOrderCarrySnapshot() throws Exception {
        Fixture f=fixture(spy(player("sonic"))); f.platform.update(1,f.main);
        f.services.sidekicks=List.of(f.omitted,f.first);
        var storage=scratchPlayers(f.platform,"lockedScratch");
        AtomicBoolean nested=new AtomicBoolean();
        doAnswer(call -> {
            assertEquals(List.of(f.main,f.omitted,f.first),storage);
            assertTrue(scratchBorrowed(f.platform,"lockedScratch"));
            if(nested.compareAndSet(false,true)) f.platform.onUnload();
            assertEquals(List.of(f.main,f.omitted,f.first),storage,"nested release uses independent storage");
            assertTrue(scratchBorrowed(f.platform,"lockedScratch"));
            return false;
        }).when(f.main).getDead();
        f.platform.update(2,f.main);
        assertTrue(nested.get());
        for(var p:List.of(f.main,f.first,f.omitted)) {
            assertFalse(p.isObjectControlled());
            assertEquals(f.platform.getY()-8,p.getCentreY(),"outer snapshot still carries every original rider");
        }
        assertScratchReleased(f.platform);
    }

    @Test
    void standingAndReleaseSnapshotsHaveIndependentLifetimes() throws Exception {
        Fixture f=fixture(spy(player("sonic")));
        AtomicBoolean nested=new AtomicBoolean();
        doAnswer(call -> {
            assertEquals(List.of(f.main,f.first,f.omitted),scratchPlayers(f.platform,"standingScratch"));
            if(nested.compareAndSet(false,true)) f.platform.onUnload();
            assertEquals(List.of(f.main,f.first,f.omitted),scratchPlayers(f.platform,"standingScratch"));
            return call.callRealMethod();
        }).when(f.main).setXSpeed((short)0);
        f.platform.update(1,f.main);
        assertTrue(nested.get());
        for(var p:List.of(f.main,f.first,f.omitted)) assertTrue(p.isObjectControlled());
        assertScratchReleased(f.platform);
        f.platform.onUnload(); assertScratchReleased(f.platform);
    }

    @Test
    void exceptionsReleaseStandingAndCarryStorageForSubsequentUpdates() throws Exception {
        Fixture f=fixture(spy(player("sonic")));
        doThrow(new IllegalStateException("lock callback")).when(f.main).setXSpeed((short)0);
        assertThrows(IllegalStateException.class,()->f.platform.update(1,f.main));
        assertScratchReleased(f.platform);
        doCallRealMethod().when(f.main).setXSpeed((short)0);
        f.platform.update(2,f.main);
        doThrow(new IllegalStateException("carry callback")).when(f.main).getDead();
        assertThrows(IllegalStateException.class,()->f.platform.update(3,f.main));
        assertScratchReleased(f.platform);
        doCallRealMethod().when(f.main).getDead();
        f.platform.update(4,f.main); assertScratchReleased(f.platform);
        for(var p:List.of(f.main,f.first,f.omitted)) assertEquals(f.platform.getY()-8,p.getCentreY());
    }

    @Test
    void retentionBoundDoesNotLimitLargeTeamsOrRetainTheirPlayers() throws Exception {
        var main=spy(player("sonic")); var followers=new ArrayList<TestablePlayableSprite>();
        for(int i=0;i<12;i++) followers.add(player("follower-"+i));
        var platform=new OOZPoppingPlatformObjectInstance(new ObjectSpawn(0x400,0x300,0x33,1,0,false,0),"OOZPoppingPform");
        var manager=mock(ObjectManager.class);
        when(manager.isRidingObject(main,platform)).thenReturn(true);
        for(var p:followers) when(manager.isRidingObject(p,platform)).thenReturn(true);
        platform.setServices(new Services(main,followers,manager));
        doAnswer(call -> {
            assertFalse(scratchBorrowed(platform,"standingScratch"));
            assertTrue(scratchPlayers(platform,"standingScratch").isEmpty());
            return call.callRealMethod();
        }).when(main).getCentreX();
        platform.update(1,main);
        for(var p:followers) assertTrue(p.isObjectControlled());
        doAnswer(call -> { assertFalse(scratchBorrowed(platform,"lockedScratch")); return false; }).when(main).getDead();
        platform.update(2,main);
        for(var p:followers) assertEquals(platform.getY()-8,p.getCentreY());
        platform.onUnload();
        for(var p:followers) assertFalse(p.isObjectControlled());
        assertScratchReleased(platform);
    }

    @Test
    void capturedRiseRestoresAndReplaysApexWithTransientStorageExcluded() throws Exception {
        Fixture f=fixture(); f.platform.update(1,f.main);
        var players=List.of(f.main,f.first,f.omitted);
        var context=context(f.main,f.first,f.omitted);
        var state=f.platform.captureRewindState(context);
        var playerStates=players.stream().map(TestablePlayableSprite::captureRewindState).toList();
        assertTrue(com.openggf.game.rewind.GenericFieldCapturer
                .defaultObjectSubclassCapturedFieldsForAudit(f.platform.getClass()).stream()
                .noneMatch(field -> field.getName().endsWith("Scratch")));
        var expected=new ArrayList<List<Integer>>();
        for(int frame=2;frame<=17;frame++) {
            f.platform.update(frame,f.main); expected.add(observe(f)); assertScratchReleased(f.platform);
        }
        for(var p:players) { assertFalse(p.isObjectControlled()); assertEquals(-0x1000,p.getYSpeed()); }
        f.platform.restoreRewindState(state,context);
        for(int i=0;i<players.size();i++) players.get(i).restoreRewindState(playerStates.get(i));
        assertScratchReleased(f.platform);
        for(int frame=2;frame<=17;frame++) {
            f.platform.update(frame,f.main); assertEquals(expected.get(frame-2),observe(f)); assertScratchReleased(f.platform);
        }
    }

    private static List<Integer> observe(Fixture f) {
        return List.of(f.platform.getY(),(int)f.main.getCentreY(),(int)f.first.getCentreY(),(int)f.omitted.getCentreY(),
                (int)f.main.getYSpeed(),(int)f.first.getYSpeed(),(int)f.omitted.getYSpeed(),
                f.main.isObjectControlled()?1:0,f.first.isObjectControlled()?1:0,f.omitted.isObjectControlled()?1:0);
    }

    @SuppressWarnings("unchecked")
    private static List<AbstractPlayableSprite> scratchPlayers(OOZPoppingPlatformObjectInstance platform,String name) throws Exception {
        Object scratch=field(platform,name); return (List<AbstractPlayableSprite>)field(scratch,"players");
    }
    private static boolean scratchBorrowed(OOZPoppingPlatformObjectInstance platform,String name) throws Exception {
        return (boolean)field(field(platform,name),"borrowed");
    }
    private static Object field(Object target,String name) throws Exception {
        Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void assertScratchReleased(OOZPoppingPlatformObjectInstance platform) throws Exception {
        for(String name:List.of("standingScratch","lockedScratch")) {
            assertTrue(scratchPlayers(platform,name).isEmpty(),name+" must release player references");
            assertFalse(scratchBorrowed(platform,name),name+" must release its borrow even on early return/exception");
        }
    }

    private static Fixture fixture() {
        return fixture(player("sonic"));
    }

    private static Fixture fixture(TestablePlayableSprite main) {
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
