package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.*;
import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSozSandMechanisms {
    @org.junit.jupiter.api.BeforeEach void activeSession() { com.openggf.tests.TestEnvironment.activeGameplayMode(); }
    private static ObjectSpawn spawn(int id, int subtype) { return new ObjectSpawn(0x300,0x400,id,subtype,0,false,0); }
    private static TestablePlayableSprite player(int x, int y) {
        var p = new TestablePlayableSprite("sonic",(short)0,(short)0);
        p.setCentreX((short)x); p.setCentreY((short)y); return p;
    }
    private static StubObjectServices services(List<TestablePlayableSprite> players) {
        return new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> players.getFirst(),
                () -> new ArrayList<PlayableEntity>(players.subList(1,players.size()))));
    }
    @Test void registryKeepsS3HalfFamiliesAndBindsAllFourSklFamilies() {
        var skl=new Sonic3kObjectRegistry(){ @Override protected int currentRomZoneId(){return 8;} };
        var s3=new Sonic3kObjectRegistry(){ @Override protected int currentRomZoneId(){return 1;} };
        Class<?>[] implemented={SozSpawningSandBlocksObjectInstance.class,SozPathSwapObjectInstance.class,
                SozRisingSandWallObjectInstance.class,SozSandCorkObjectInstance.class};
        Class<?>[] preserved={HCZLargeFanObjectInstance.class,HCZHandLauncherObjectInstance.class,
                HCZBlockObjectInstance.class,CnzCylinderInstance.class};
        int[] ids={0x39,0x3A,0x40,0x47};
        for(int i=0;i<ids.length;i++) {
            assertInstanceOf(implemented[i],skl.create(spawn(ids[i],0)));
            assertInstanceOf(preserved[i],s3.create(spawn(ids[i],0)));
        }
    }
    @Test void nativeSecondaryCrossingDebounceIsIndependentForEveryParticipantAndRewinds() {
        var p=player(0x2FF,0x400);var p2=player(0x2FF,0x400);var p3=player(0x2FF,0x400);
        var marker=new SozPathSwapObjectInstance(spawn(0x3A,0x11));
        marker.setServices(services(List.of(p,p2,p3))); marker.update(0,p);
        for(var entity:List.of(p,p2,p3)) {
            entity.setTopSolidBit((byte)0xE); entity.setCentreX((short)0x300);
        }
        marker.update(1,p);
        // First crossing sets primary path. Secondary direction with primary path leaves it unchanged.
        for(var entity:List.of(p,p2,p3)) assertEquals(0xC,entity.getTopSolidBit());
        p.setTopSolidBit((byte)0xE); p.setCentreX((short)0x2FF);
        marker.update(2,p); assertEquals(0xE,p.getTopSolidBit()); // second crossing suppressed
        var saved=marker.captureRewindState();
        p.setCentreX((short)0x300); marker.update(3,p); assertEquals(0xC,p.getTopSolidBit());
        marker.restoreRewindState(saved);p.setTopSolidBit((byte)0xE);p.setCentreX((short)0x300);
        marker.update(3,p);assertEquals(0xC,p.getTopSolidBit());
        assertEquals(0xC,p2.getTopSolidBit());assertEquals(0xC,p3.getTopSolidBit());
    }
    @Test void verticalGroundOnlyExtentAndTeleportGatesConsumeCrossingWithoutApplyingPath() {
        var p=player(0x300,0x3FF);
        var marker=new SozPathSwapObjectInstance(spawn(0x3A,0xA4));
        marker.setServices(services(List.of(p)));marker.update(0,p);
        p.setAir(true);p.setCentreY((short)0x400);marker.update(1,p);assertFalse(p.isHighPriority());
        p.setAir(false);marker.update(2,p);assertFalse(p.isHighPriority());
        p.setCentreY((short)0x3FF);marker.update(3,p);
        p.setCentreX((short)0x320);p.setCentreY((short)0x400);marker.update(4,p);
        assertFalse(p.isHighPriority(),"upper extent is excluded");
        p.setCentreX((short)0x300);p.setCentreY((short)0x3FF);marker.update(5,p);
        p.setCentreY((short)0x440);marker.update(6,p);assertFalse(p.isHighPriority(),"64px jump excluded");
        p.setCentreY((short)0x3FF);marker.update(7,p);p.setCentreY((short)0x400);marker.update(8,p);
        assertTrue(p.isHighPriority());
    }
    @Test void spawningBlocksReadOscillatorPositionAfterNativeControlWord() {
        var saved = com.openggf.game.OscillationManager.snapshot();
        try {
            var values = saved.values();
            var deltas = saved.deltas();
            values[5] = 0x0300;
            deltas[5] = 0xFF80;
            com.openggf.game.OscillationManager.restore(new com.openggf.game.OscillationSnapshot(
                    values, deltas, saved.activeSpeeds(), saved.activeLimits(),
                    saved.control(), saved.lastFrame(), saved.suppressedUpdates()));
            var manager = mock(ObjectManager.class);
            var children = new ArrayList<SozSpawningSandBlocksObjectInstance>();
            when(manager.createDynamicObject(any())).thenAnswer(call -> {
                var child = (SozSpawningSandBlocksObjectInstance)
                        ((java.util.function.Supplier<?>) call.getArgument(0)).get();
                children.add(child);
                return child;
            });
            var spawner = new SozSpawningSandBlocksObjectInstance(spawn(0x39, 1));
            spawner.setServices(new StubObjectServices() {
                @Override public ObjectManager objectManager() { return manager; }
            });
            spawner.update(0, null);
            assertEquals(0x403, spawner.getY());
            assertEquals(0x403, children.getFirst().getY());

            // A zero derivative is not the native position-zero admission gate.
            deltas[5] = 0;
            values[5] = 0x0700;
            com.openggf.game.OscillationManager.restore(new com.openggf.game.OscillationSnapshot(
                    values, deltas, saved.activeSpeeds(), saved.activeLimits(),
                    saved.control(), saved.lastFrame(), saved.suppressedUpdates()));
            spawner.update(1, null);
            assertEquals(0x403, spawner.getY(), "wait preserves the last emitted height");

            values[5] = 0;
            deltas[5] = 0xFF80;
            com.openggf.game.OscillationManager.restore(new com.openggf.game.OscillationSnapshot(
                    values, deltas, saved.activeSpeeds(), saved.activeLimits(),
                    saved.control(), saved.lastFrame(), saved.suppressedUpdates()));
            spawner.update(2, null);
            assertEquals(0x400, spawner.getY(), "native position zero releases the wait");
        } finally {
            com.openggf.game.OscillationManager.restore(saved);
        }
    }
    @Test void allocatedBlockFallsSlidesForSubtypeDurationAndSinksWithNativeGravity() {
        com.openggf.game.OscillationManager.reset();
        var p=player(0x300,0x400);
        var manager=mock(ObjectManager.class);
        var captured=new ArrayList<SozSpawningSandBlocksObjectInstance>();
        when(manager.createDynamicObject(any())).thenAnswer(call->{
            var block=(SozSpawningSandBlocksObjectInstance)((java.util.function.Supplier<?>)call.getArgument(0)).get();
            captured.add(block);return block;
        });
        var context=mock(ObjectSolidExecutionContext.class);
        when(context.resolveSolidNowAll()).thenReturn(new SolidCheckpointBatch(null,Map.of()));
        var svc=new StubObjectServices(){
            @Override public ObjectManager objectManager(){return manager;}
            @Override public ObjectSolidExecutionContext solidExecution(){return context;}
        }.withPlayerQuery(new ObjectPlayerQuery(()->p,List::of));
        var spawner=new SozSpawningSandBlocksObjectInstance(spawn(0x39,1));
        spawner.setServices(svc);spawner.update(0,p);
        assertEquals(1,captured.size());var block=captured.getFirst();block.setServices(svc);
        assertTrue(block.isSolidFor(p));assertFalse(spawner.isSolidFor(p));
        assertEquals(0x308,block.getX());
        try(var floor=mockStatic(com.openggf.physics.ObjectTerrainUtils.class)) {
            floor.when(()->com.openggf.physics.ObjectTerrainUtils.checkFloorDist(
                    any(com.openggf.level.LevelManager.class),any(),eq(false),anyInt(),anyInt())).thenReturn(
                    new com.openggf.physics.TerrainCheckResult(-1,(byte)0,0));
            // Null level manager is deliberate: terrain is the bounded oracle in this routine test.
            floor.when(()->com.openggf.physics.ObjectTerrainUtils.checkFloorDist(
                    isNull(),any(),eq(false),anyInt(),anyInt())).thenReturn(new com.openggf.physics.TerrainCheckResult(-1,(byte)0,0));
            for(int i=0;i<10;i++)block.update(i,p);
            floor.when(()->com.openggf.physics.ObjectTerrainUtils.checkFloorDist(
                    isNull(),any(),eq(false),anyInt(),anyInt())).thenReturn(new com.openggf.physics.TerrainCheckResult(0,(byte)0,0));
            for(int i=0;i<15;i++)block.update(i,p);
            assertEquals(0x2F9,block.getX());
            block.update(0,p);int sinkY=block.getY();var beforeSink=block.captureRewindState();
            // Landing keeps its $D800 subpixel fraction; the sink crosses 18px on pass 35.
            for(int i=0;i<34;i++)block.update(i,p);
            assertEquals(0x2F9,block.getX());assertEquals(sinkY+18,block.getY());
            block.update(35,p);assertEquals(0x7F00,block.getX());
            block.restoreRewindState(beforeSink);
            for(int i=0;i<35;i++)block.update(i,p);
            assertEquals(0x7F00,block.getX());
        }
    }
    @Test void risingWallTriggersOnPartnerThenRisesTwentySixPassesAndRestores() {
        var p=player(0x100,0x400);var partner=player(0x300,0x3FF);
        var wall=new SozRisingSandWallObjectInstance(spawn(0x40,0x60));
        var context=mock(ObjectSolidExecutionContext.class);
        when(context.resolveSolidNowAll()).thenReturn(new SolidCheckpointBatch(wall,Map.of()));
        wall.setServices(new StubObjectServices(){
            @Override public ObjectSolidExecutionContext solidExecution(){return context;}
        }.withPlayerQuery(new ObjectPlayerQuery(()->p,()->List.of(partner))));
        wall.update(0,p);assertEquals(0x400,wall.getY());
        for(int i=1;i<=25;i++)wall.update(i,p);
        assertEquals(0x39C,wall.getY());var saved=wall.captureRewindState();
        wall.update(26,p);assertEquals(0x398,wall.getY());wall.update(27,p);assertEquals(0x398,wall.getY());
        wall.restoreRewindState(saved);wall.update(26,p);assertEquals(0x398,wall.getY());
    }
    @Test void settledWallBreaksOnlyForRollingSideContactAndReleasesOwnPushLatch() {
        var p=player(0x300,0x400);p.setAnimationId(2);p.setXSpeed((short)0x301);p.setGSpeed((short)-0x301);
        var other=player(0x100,0x400);other.setPushing(true);
        var wall=new SozRisingSandWallObjectInstance(spawn(0x40,0x60));
        var context=mock(ObjectSolidExecutionContext.class);
        when(context.resolveSolidNowAll()).thenReturn(new SolidCheckpointBatch(wall,Map.of()));
        wall.setServices(new StubObjectServices(){
            @Override public ObjectSolidExecutionContext solidExecution(){return context;}
        }.withPlayerQuery(new ObjectPlayerQuery(()->p,()->List.of(other))));
        wall.update(0,p);for(int i=0;i<26;i++)wall.update(i,p);
        when(context.resolveSolidNowAll()).thenReturn(new SolidCheckpointBatch(wall,Map.of(p,
                new PlayerSolidContactResult(ContactKind.SIDE,false,false,true,false,PreContactState.ZERO,PostContactState.ZERO,1))));
        wall.setPlayerPushing(p,true);p.setPushing(true);wall.update(27,p);
        assertFalse(wall.isSolidFor(p));assertEquals(0x180,p.getXSpeed());assertEquals(-0x181,p.getGSpeed());
        assertFalse(p.getPushing());assertTrue(other.getPushing(),"unrelated object's pushing state is untouched");
        var saved=wall.captureRewindState();
        for(int i=0;i<47;i++)wall.update(i,p);assertEquals(0x300,wall.getX());
        wall.update(48,p);assertEquals(0x7F00,wall.getX());
        wall.restoreRewindState(saved);for(int i=0;i<48;i++)wall.update(i,p);assertEquals(0x7F00,wall.getX());
    }

}
