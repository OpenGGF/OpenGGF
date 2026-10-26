package com.openggf.game.sonic3k.features;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.objects.HCZWaterSkimSlotObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 6655S HCZ water-record reuse prerequisite. Runs the participant owner and
 * slot execution/unload phases, including a real skim activation and jump exit.
 * Native Obj_HCZWaterSplash's range removal can free the water record; the
 * range tail must free the record for a later allocator. */
class TestTasS3kWaterSkimLifetime {
    @AfterEach void cleanup() { HCZWaterSkimHandler.reset(); }

    @Test void waterSkimOwnerRetiresAfterParticipantExitAndRangeDeparture() {
        try (var reel = com.openggf.tests.reel.S3kTestReelPresentation.open()) {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        HCZWaterSkimHandler.reset();
        var camera=GameServices.camera();camera.setX((short)0);
        var services=new TestObjectServices().withCamera(camera);
        var manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,
                GameServices.graphics(),camera,services);
        services.withDirectObjectManager(manager);
        manager.enableExecThenLoadPlacement();manager.reset(0);
        manager.addDynamicObjectAtSlot(new com.openggf.game.sonic3k.objects.S3kCutsceneButtonObjectInstance(
                new ObjectSpawn(0x100,0x100,0x83,0,0,false,0)),4);
        var skim=new HCZWaterSkimSlotObjectInstance();
        var control=new RangeControl();
        manager.addDynamicObjectAtSlot(skim,5);manager.addDynamicObjectAtSlot(control,6);
        var p=new TestablePlayableSprite("sonic",(short)0,(short)0);
        p.setCentreX((short)0x100);p.setCentreY((short)(0x200-p.getYRadius()-1));
        p.setAir(false);p.setXSpeed((short)0x800);p.setGSpeed((short)0x800);p.setYSpeed((short)0);
        var query=new ObjectPlayerQuery(()->p,List::of);
        HCZWaterSkimHandler.update(query,0x200,1);
        manager.update(0,p,List.of(),1); reel.afterWaterSkim(p,0x200);
        assertTrue(HCZWaterSkimHandler.isSkimActiveP1());assertTrue(control.updates>0);
        assertTrue(manager.getActiveObjects().contains(control));
        p.setLogicalInputState(false,false,false,false,true,true);
        HCZWaterSkimHandler.update(query,0x200,2);
        manager.update(0,p,List.of(),2); reel.afterWaterSkim(p,0x200);
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());assertTrue(p.getAir());assertEquals(-0x680,p.getYSpeed());
        p.setLogicalInputState(false,false,false,false,false,false);
        camera.setX((short)0x3000);
        for(int frame=3;frame<=10;frame++) {
            p.move(p.getXSpeed(),p.getYSpeed());
            HCZWaterSkimHandler.update(query,0x200,frame);
            manager.update(0x3000,p,List.of(),frame); reel.afterWaterSkim(p,0x200);
        }
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());
        assertFalse(manager.getActiveObjects().contains(control),"nonpersistent control proves range-unload phase ran");
        assertFalse(manager.getActiveObjects().contains(skim),"native range tail retires the water slot after participant work");
        assertTrue(skim.isDestroyed());
        var replacement=new RangeControl();manager.addDynamicObject(replacement);
        assertEquals(5,replacement.getSlotIndex());
            }
    }

    @Test void rangeTailUsesCurrentP1PositionAndIncludesCoarseEquality() {
        var h = new Harness();
        h.p1.setCentreX((short) 0x200); // (0x200 - (-0x80)) == 0x280
        h.step(1);
        assertSame(h.skim, NativeSstSlotAccess.currentOwner(h.manager, 5));
        h.p1.setCentreX((short) 0x27F); // same aligned word, still equality
        h.step(2);
        assertSame(h.skim, NativeSstSlotAccess.currentOwner(h.manager, 5));
        h.p1.setCentreX((short) 0x280);
        h.step(3);
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 5));
    }

    @Test void activeParticipantRunsBeforeRetirementAndCannotReactivateOnReturn() {
        var h = new Harness();
        h.step(1);
        h.p1.setAir(true);
        h.p1.setCentreX((short) 0x280);
        h.p1.setCentreY((short) 0x220);
        h.p1.setYSpeed((short) 0x100);
        h.step(2);
        assertEquals(0x200 - h.p1.getYRadius() - 1, h.p1.getCentreY());
        assertEquals(0, h.p1.getYSpeed(), "retirement tick still executes native participant physics");
        assertEquals(0x800 - 0xC, h.p1.getXSpeed());
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 5));
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1(), "deleted object's status is gone");
        assertFalse(h.p1.isWaterSkimActive());
        h.p1.setCentreX((short) 0x100);
        h.step(3);
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1(), "return does not recreate a deleted init-only slot");
        assertFalse(h.p1.isWaterSkimActive());
    }

    @Test void mixedParticipantsRetireByP1AnchorAfterP2Work() {
        var h = new Harness();
        h.p2 = candidate("tails");
        h.p1.setYSpeed((short) -1); // P1 inactive, P2 enters
        h.step(1);
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());
        assertTrue(HCZWaterSkimHandler.isSkimActiveP2());
        h.p2.setCentreX((short) 0x1000);
        h.step(2);
        assertSame(h.skim, NativeSstSlotAccess.currentOwner(h.manager, 5), "P2 X does not own range");
        h.p2.setCentreX((short) 0x100);
        h.p2.setLogicalInputState(false, false, false, false, true, true);
        h.p1.setCentreX((short) 0x280);
        h.step(3);
        assertEquals(-0x680, h.p2.getYSpeed(), "P2 jump runs before P1-anchored deletion");
        assertTrue(h.p2.getAir());
        assertFalse(HCZWaterSkimHandler.isSkimActiveP2());
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 5));
    }

    @Test void rewindRestoresRetiredSlotAndReplaysRealAllocatorReuse() {
        var h = new Harness();
        h.step(1);
        var registry = new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        registry.register(new HCZWaterSkimStaticAdapter());
        registry.register(new RewindSnapshottable<PerObjectRewindSnapshot>() {
            public String key() { return "skim-player"; }
            public PerObjectRewindSnapshot capture() { return h.p1.captureRewindState(); }
            public void restore(PerObjectRewindSnapshot state) { h.p1.restoreRewindState(state); }
        });
        var before = registry.capture();
        h.p1.setCentreX((short) 0x280);
        h.p1.setLogicalInputState(false, false, false, false, true, true);
        h.step(2);
        var retired = registry.capture();
        var replacement = new com.openggf.game.sonic3k.objects.S3kCutsceneButtonObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x83, 0, 0, false, 0));
        h.manager.addDynamicObject(replacement);
        assertEquals(5, replacement.getSlotIndex());
        var reused = registry.capture();
        registry.restore(before);
        assertInstanceOf(HCZWaterSkimSlotObjectInstance.class, NativeSstSlotAccess.currentOwner(h.manager, 5));
        assertTrue(HCZWaterSkimHandler.isSkimActiveP1());
        h.p1.setCentreX((short) 0x280);
        h.p1.setLogicalInputState(false, false, false, false, true, true);
        h.step(2);
        assertEquals(-0x680, h.p1.getYSpeed());
        var replay = registry.capture();
        for (String key : retired.entries().keySet()) {
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals(key, retired.get(key), replay.get(key)),
                    () -> key + ": " + com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, retired.get(key), replay.get(key)));
        }
        registry.restore(reused);
        assertInstanceOf(com.openggf.game.sonic3k.objects.S3kCutsceneButtonObjectInstance.class,
                NativeSstSlotAccess.currentOwner(h.manager, 5));
        h.p1.setCentreX((short) 0x100);
        h.p1.setYSpeed((short) 0);
        h.p1.setCentreY((short) (0x200 - h.p1.getYRadius() - 1));
        h.step(3);
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());
        registry.restore(retired);
        assertNull(NativeSstSlotAccess.currentOwner(h.manager, 5));
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());
    }

    private static TestablePlayableSprite candidate(String name) {
        var player = new TestablePlayableSprite(name, (short) 0, (short) 0);
        player.setCentreX((short) 0x100);
        player.setCentreY((short) (0x200 - player.getYRadius() - 1));
        player.setAir(false);
        player.setXSpeed((short) 0x800);
        player.setGSpeed((short) 0x800);
        player.setYSpeed((short) 0);
        return player;
    }

    private static final class Harness {
        final ObjectManager manager;
        final HCZWaterSkimSlotObjectInstance skim = new HCZWaterSkimSlotObjectInstance();
        final TestablePlayableSprite p1;
        TestablePlayableSprite p2;
        Harness() {
            TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
            HCZWaterSkimHandler.reset();
            var camera = GameServices.camera();
            camera.setX((short) 0);
            var services = new TestObjectServices().withCamera(camera);
            manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GameServices.graphics(), camera, services);
            services.withDirectObjectManager(manager);
            manager.enableExecThenLoadPlacement();
            manager.reset(0);
            manager.addDynamicObjectAtSlot(new com.openggf.game.sonic3k.objects.S3kCutsceneButtonObjectInstance(
                    new ObjectSpawn(0x100, 0x100, 0x83, 0, 0, false, 0)), 4);
            manager.addDynamicObjectAtSlot(skim, 5);
            p1 = candidate("sonic");
        }
        void step(int frame) {
            List<com.openggf.sprites.playable.AbstractPlayableSprite> sidekicks = p2 == null ? List.of() : List.of(p2);
            HCZWaterSkimHandler.update(new ObjectPlayerQuery(() -> p1, () -> sidekicks), 0x200, frame);
            manager.update(0, p1, sidekicks, frame);
        }
    }

    private static final class RangeControl extends AbstractObjectInstance {
        int updates;
        RangeControl() { super(new ObjectSpawn(0,0,0,0,0,false,0),"range control"); }
        @Override public void update(int frame,PlayableEntity p) { updates++; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
