package com.openggf.game.sonic2.competition;

import com.openggf.level.objects.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionTeleportRestore {
    @Test void sessionCapturesTheSingleClockAndMidFreezeRestoresItsExactReleaseBoundary() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            w.host.requestTeleport(w.session);
            for(int i=0;i<20;i++)w.tick();
            w.controls.writeSlowMotionFlag(true);
            var registry=w.host.context().getRewindRegistry();var snapshot=registry.capture();
            var player=w.session.player(0);var camera=w.session.camera(0);
            for(int i=0;i<43;i++)w.tick();
            assertEquals(1,w.session.teleportTimer());
            w.controls.writeSlowMotionFlag(false);
            registry.restore(snapshot);
            assertTrue(w.controls.getAsBoolean());
            var objects=w.host.context().getObjectManager();
            for(int slot:new int[]{0x86,0x87,0x88,0x8C})assertNotNull(NativeSstSlotAccess.currentOwner(objects,slot));
            assertSame(NativeSstSlotAccess.currentOwner(objects,0x86),w.session.player(0).getShieldObject());
            assertSame(NativeSstSlotAccess.currentOwner(objects,0x88),w.session.player(0).getInvincibilityObject());
            assertSame(NativeSstSlotAccess.currentOwner(objects,0x87),w.session.player(1).getShieldObject());
            assertSame(NativeSstSlotAccess.currentOwner(objects,0x8C),w.session.player(1).getInvincibilityObject());
            assertSame(player,w.session.player(0));assertSame(camera,w.session.camera(0));
            assertEquals(44,w.session.teleportTimer());
            for(int i=0;i<43;i++)w.tick();
            assertEquals(1,w.session.teleportTimer());
            w.tick();assertEquals(0,w.session.teleportTimer());
        });
    }
    @Test void standingRepairOrdersP1BeforeP2AndDoesNotLandOrStopEitherActor() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            var manager=w.host.context().getObjectManager();
            var object=manager.getActiveObjects().stream().filter(o -> o instanceof AbstractObjectInstance)
                    .map(o -> (AbstractObjectInstance)o).filter(o -> o.getSlotIndex()>=16).findFirst().orElseThrow();
            var p1=w.session.player(0);var p2=w.session.player(1);
            manager.forceRidingObjectForBootstrap(p1,object);manager.forceRidingObjectForBootstrap(p2,object);
            p1.setInteractSlotIndex(object.getSlotIndex());p2.setInteractSlotIndex(object.getSlotIndex());
            p1.setAir(true);p2.setAir(true);p1.setOnObject(true);p2.setOnObject(true);
            p1.setXSpeed((short)0x123);p2.setXSpeed((short)-0x234);
            p1.setYSpeed((short)-0x345);p2.setYSpeed((short)0x456);
            ObjectManagerInternalAccess.repairNativeTeleportStanding(manager,p1,p2);
            assertFalse(manager.hasObjectStandingBit(p1,object));assertTrue(manager.hasObjectStandingBit(p2,object));
            assertSame(object,manager.getRidingObject(p1));assertSame(object,manager.getRidingObject(p2));
            assertTrue(p1.getAir());assertTrue(p2.getAir());assertTrue(p1.isOnObject());assertTrue(p2.isOnObject());
            assertEquals(0x123,p1.getXSpeed());assertEquals(-0x234,p2.getXSpeed());
            assertEquals(-0x345,p1.getYSpeed());assertEquals(0x456,p2.getYSpeed());
        });
    }
}
