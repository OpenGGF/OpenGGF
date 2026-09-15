package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.*;
import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSozBreakableSandRock {
    private SozBreakableSandRockObjectInstance rock() {
        var registry = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return 8; }
        };
        return assertInstanceOf(SozBreakableSandRockObjectInstance.class,
                registry.create(new ObjectSpawn(0x260, 0x5B0, 0x44, 0, 0, false, 0)));
    }
    private TestablePlayableSprite player(int animation) {
        var p = new TestablePlayableSprite("sonic", (short)0, (short)0);
        p.setAnimationId(animation); p.setCentreY((short)100); p.setOnObject(true);
        return p;
    }
    private void contacts(SozBreakableSandRockObjectInstance rock,
                          List<TestablePlayableSprite> players, Set<TestablePlayableSprite> standing) {
        contacts(rock, players, standing, standing);
    }
    private void contacts(SozBreakableSandRockObjectInstance rock,
                          List<TestablePlayableSprite> players, Set<TestablePlayableSprite> standing,
                          Set<TestablePlayableSprite> retainedStanding) {
        var manager = mock(ObjectManager.class);
        for (var player : players) when(manager.hasObjectStandingBit(player, rock))
                .thenReturn(retainedStanding.contains(player));
        var context = mock(ObjectSolidExecutionContext.class);
        when(context.resolveSolidNowAll()).thenAnswer(invocation -> {
            var results = new IdentityHashMap<PlayableEntity, PlayerSolidContactResult>();
            for (var p : players) {
                boolean on = standing.contains(p);
                results.put(p, new PlayerSolidContactResult(on ? ContactKind.TOP : ContactKind.NONE,
                        on, false, false, false, PreContactState.ZERO, PostContactState.ZERO, 0));
                if (on) p.setAnimationId(0); // Real landing resets the pre-contact roll animation.
            }
            return new SolidCheckpointBatch(rock, results);
        });
        rock.setServices(new StubObjectServices() {
            @Override public ObjectSolidExecutionContext solidExecution() { return context; }
            @Override public ObjectManager objectManager() { return manager; }
        }.withPlayerQuery(new ObjectPlayerQuery(() -> players.getFirst(),
                () -> new ArrayList<PlayableEntity>(players.subList(1, players.size())))));
    }
    @Test void preservesCnzTrapDoorBinding() {
        var registry = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return 3; }
        };
        assertInstanceOf(CnzTrapDoorInstance.class,
                registry.create(new ObjectSpawn(0,0,0x44,0,0,false,0)));
    }
    @Test void rollingLandingUsesPreCollisionAnimationAndDoesNotShiftCentre() {
        var r=rock(); var p=player(2); contacts(r,List.of(p),Set.of(p));
        r.update(0,p);
        assertEquals(-0x300,p.getYSpeed()); assertEquals(100,p.getCentreY());
        assertEquals(14,p.getYRadius()); assertEquals(7,p.getXRadius());
        assertTrue(p.getRolling()); assertTrue(p.getAir()); assertFalse(p.isOnObject());
        assertEquals(2,p.getAnimationId()); assertFalse(r.isSolidFor(p));
    }
    @Test void walkingLandingAndRollingSideContactDoNotBreakRock() {
        var r=rock(); var walking=player(0); var rolling=player(2);
        contacts(r,List.of(walking,rolling),Set.of(walking)); r.update(0,walking);
        assertTrue(r.isSolidFor(walking)); assertEquals(0,walking.getYSpeed());
        assertFalse(walking.getAir());
    }
    @Test void oneRollingRiderReleasesStandingPartnersWithoutBouncingThem() {
        var r=rock(); var p=player(0); var p2=player(2); var extra=player(0);
        p.setYSpeed((short)0x55); extra.setYSpeed((short)0x66);
        contacts(r,List.of(p,p2,extra),Set.of(p,p2,extra)); r.update(0,p);
        assertTrue(p.getAir()); assertFalse(p.isOnObject()); assertEquals(0x55,p.getYSpeed());
        assertEquals(-0x300,p2.getYSpeed()); assertEquals(0x66,extra.getYSpeed());
        assertTrue(extra.getAir()); assertFalse(extra.isOnObject());
    }
    @Test void offscreenStandingLatchCanTriggerBreakAndReleasesBothRiders() {
        var r=rock(); var p=player(0); var p2=player(2);
        contacts(r,List.of(p,p2),Set.of(p),Set.of(p,p2)); r.update(0,p);
        assertFalse(r.isSolidFor(p)); assertTrue(p.getAir()); assertTrue(p2.getAir());
        assertEquals(-0x300,p2.getYSpeed()); assertFalse(p2.isOnObject());
    }
    @Test void nonStandingPartnerIsUntouched() {
        var r=rock(); var p=player(2); var p2=player(2);
        contacts(r,List.of(p,p2),Set.of(p)); r.update(0,p);
        assertFalse(p2.getAir()); assertEquals(0,p2.getYSpeed()); assertTrue(p2.isOnObject());
    }
    @Test void breakAnimationExpiresOnTwentyFifthPassAndReplaysAfterRestore() {
        var r=rock(); var p=player(2); contacts(r,List.of(p),Set.of(p)); r.update(0,p);
        var snapshot=r.captureRewindState(); var replay=rock(); replay.restoreRewindState(snapshot);
        for(int pass=2;pass<=24;pass++) {
            r.update(pass,p); replay.update(pass,p);
            assertEquals(0x260,r.getX()); assertEquals(r.getX(),replay.getX());
        }
        r.update(25,p); replay.update(25,p);
        assertEquals(0x7F00,r.getX()); assertEquals(r.getX(),replay.getX());
        assertTrue(r.isCustomOutOfRange(0)); assertFalse(replay.isSolidFor(p));
    }
}
