package com.openggf.game.sonic2.competition;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.audio.NullAudioBackend;
import com.openggf.control.*;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.competition.view.*;
import com.openggf.game.sonic2.objects.badniks.MasherBadnikInstance;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Declared block-edge setup, not a replay/observed-state import; real ROM layout and production host. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestS2CompetitionPlacementPhase {
    @Test void aCrossingLoadsAfterBothCamerasAndDisplayButExecutesOnTheNextObjectPass() throws Exception {
        GameServices.audio().setBackend(new NullAudioBackend());
        HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        var services=EngineServices.current();var root=SessionManager.getCurrentWorldSession().rootGameModule();
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
        // Level_MainLoop: consecutive JSR BuildSprites, JSR ObjectsManager.
        assertEquals(0x4EB9,rom.readU16BE(0x43BC));assertEquals(0x16604,rom.readU32BE(0x43BE));
        assertEquals(0x4EB9,rom.readU16BE(0x43C2));assertEquals(0x17AA4,rom.readU32BE(0x43C4));
        assertFalse(new ObjectManagerInternalAccess.NativeExecution() {
            @Override public void afterNativeWait() { }
            @Override public boolean objectsFrozen() { return false; }
        }.placementAtWorldTail(),"other worlds retain their current placement phase");
        boolean[] crossing={false},displaySawNoNewOwner={false};
        try(var gl=PresentationOffscreenTestContext.openConfigured(320,224,PresentationOffscreenTestContext.LegacyProfile.CORE_41);
            AutoCloseable cleanup=()->{try{SessionManager.closeGameplaySession();}finally{services.graphics().cleanup();}};
            var views=new Sonic2CompetitionViews(services.graphics())) {
            var graphics=services.graphics();graphics.setHeadlessMode(false);graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            var observed=new Sonic2NativeCompetitionHost.Views() {
                @Override public void prepare(GameplayModeContext context,Sonic2CompetitionSession session,
                        Sonic2CompetitionLevelProfile profile,Sonic2CompetitionNativeArt art) {
                    views.prepare(context,session,profile,art);
                    if(crossing[0]) {
                        assertEquals(0x1000,session.camera(0).getX());
                        assertNull(fish(context),"BuildSprites precedes the new block's placement");
                        displaySawNoNewOwner[0]=true;
                    }
                }
                @Override public void draw(GameplayModeContext context,Sonic2CompetitionSession session) {views.draw(context,session);}
            };
            var host=Sonic2NativeCompetitionHost.production(root,services,ignored->{},observed,false,true,
                    new com.openggf.game.sonic2.objects.Sonic2TeleportObjectFieldsAccess());
            host.clearMenuCameraRam();var input=new InputHandler();input.setLogicalOverride(LogicalInputSnapshot.neutral());
            host.bindNativeWaitInput(input::logical,()->false);
            var session=new Sonic2CompetitionSession();session.updateMenu(LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(0,0,0,0,true,true),PlayerInputState.neutral()),true);host.loadAct(session,0,0);
            // Level_FromCheckpoint (s2.asm:4980-5086): wait for native control
            // release before injecting an ordinary Level_MainLoop block crossing.
            Sonic2CompetitionTestFlow.finishTitle(host, () -> step(host,input));
            // Advance interests through consecutive 256px blocks. Each setup is
            // derived from the ROM block mask and camera's $A0 right edge, not CSV.
            for(int block=1;block<=16;block++) {
                var player=session.player(0);player.setHurt(false);player.setDead(false);
                player.setInvulnerableFrames(120);player.setAir(true);player.setRolling(false);
                player.setObjectControlled(false);player.setXSpeed((short)0x600);player.setYSpeed((short)0);
                player.setGSpeed((short)0x600);
                int cameraX=block*256-6;
                NativePositionOps.writeXPosResetSubpixel(player,cameraX+0xA0);
                NativePositionOps.writeYPosResetSubpixel(player,0x40);
                session.camera(0).setX((short)cameraX);session.camera(0).setY((short)0);
                crossing[0]=block==16;step(host,input);
            }
            crossing[0]=false;
            assertTrue(displaySawNoNewOwner[0]);var fish=assertInstanceOf(MasherBadnikInstance.class,fish(host.context()));
            assertEquals(976,fish.getY(),"tail allocation does not execute Obj5C_Init/Main");
            assertThrows(AssertionError.class,()->assertEquals(972,fish.getY()),"negative premature-execution control");
            step(host,input);assertEquals(972,fish.getY(),"the following pass executes exactly once");
        } finally {TestEnvironment.resetAll();}
    }
    private static ObjectInstance fish(GameplayModeContext context) {
        return context.getObjectManager().getActiveObjects().stream()
                .filter(object->object instanceof MasherBadnikInstance && object.getX()==4808).findFirst().orElse(null);
    }
    private static void step(Sonic2NativeCompetitionHost host,InputHandler input) {
        var context=host.context();LevelFrameStep.updateTimers(LevelFrameContext.from(context));
        context.plcFrameLifecycle().runLogicalIteration(context::updateFade,frame->{
            NativeCompetitionIterationAccess.runAdmitted(frame,()->host.step(input,frame));return null;
        });
    }
}
