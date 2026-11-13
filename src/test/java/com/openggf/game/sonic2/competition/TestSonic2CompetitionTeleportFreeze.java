package com.openggf.game.sonic2.competition;

import com.openggf.game.sonic2.competition.state.Sonic2TeleportClock;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionTeleportFreeze {
    @Test void onlyVintSpendsTheTimerAndTheReleasePassExecutesActors() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            var lifecycle=w.host.context().getWorldSession().getGameModule().getGameService(
                    com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle.class);
            lifecycle.recordCameraDisplacement(0,0x1200,0x3400);
            lifecycle.recordCameraDisplacement(1,0x5600,0x7800);
            w.host.requestTeleport(w.session);
            var fixed=com.openggf.level.objects.NativeSstSlotAccess.currentOwner(w.host.context().getObjectManager(),0x88);
            var fixedAngles=starArray(fixed,"angleByte");var fixedFrames=starArray(fixed,"animCounter");
            var objects=w.host.context().getObjectManager();
            var probe=com.openggf.level.objects.ObjectConstructionContext.construct(objects.getObjectServices(),TickProbe::new);
            objects.addDynamicObject(probe);
            int x=w.session.player(0).getCentreX(),y=w.session.player(0).getCentreY();
            int vb=w.host.context().getObjectManager().getVblaCounter();
            int level=w.host.context().getLevelManager().getFrameCounter();
            for(int i=0;i<63;i++) {
                w.tick();
                assertEquals(63-i,w.session.teleportTimer());
                assertEquals(x,w.session.player(0).getCentreX());assertEquals(y,w.session.player(0).getCentreY());
            }
            assertEquals(0x5600,lifecycle.capture().slots().get(0).cameraXDiff());
            assertNotEquals(0x7800,lifecycle.capture().slots().get(0).cameraYDiff());
            assertArrayEquals(fixedAngles,starArray(fixed,"angleByte"));
            assertArrayEquals(fixedFrames,starArray(fixed,"animCounter"));
            assertEquals(0,probe.ticks,"dynamic object execution must stay frozen");
            assertEquals(vb+63,w.host.context().getObjectManager().getVblaCounter());
            assertEquals(level+63,w.host.context().getLevelManager().getFrameCounter());
            w.tick();assertEquals(0,w.session.teleportTimer());
            assertEquals(1,probe.ticks,"the zero-transition pass executes objects");
        });
    }
    @Test void pauseAndSlowMotionWaitHoldWhileAnInFlightResultsAwardStillExpires() {
        var s=new Sonic2CompetitionSession();
        var host=org.mockito.Mockito.mock(Sonic2CompetitionHost.class);
        org.mockito.Mockito.when(host.swapNativeActorWorldState()).thenReturn(true);
        s.updateMenu(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(0,0,0,0,true,true),
                com.openggf.control.PlayerInputState.neutral()),true);
        assertTrue(s.observeGoal(0,0,0));assertTrue(s.observeGoal(1,0,0));s.completeSignpostSpin();
        assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS,s.phase());
        assertTrue(s.teleportInitializedMonitor(host)); // award already admitted before results publication
        for(int i=0;i<100;i++) {s.advanceActFlow();s.teleportClock().afterNativeWait(true);}
        assertEquals(64,s.teleportTimer());
        for(int i=0;i<64;i++)s.teleportClock().afterNativeWait(false);
        assertEquals(0,s.teleportTimer());
        assertFalse(s.teleportClock().active());
    }
    private static int[] starArray(Object owner,String field) throws Exception {
        var f=owner.getClass().getDeclaredField(field);f.setAccessible(true);return ((int[])f.get(owner)).clone();
    }
    private static final class TickProbe extends com.openggf.level.objects.AbstractObjectInstance {
        int ticks;
        TickProbe(){super(new com.openggf.level.objects.ObjectSpawn(96,96,0x7F,0,0,false,0,0),"teleport-freeze-probe");}
        @Override public void update(int vIntRunCount,com.openggf.game.PlayableEntity player){ticks++;}
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands){ }
    }

}
