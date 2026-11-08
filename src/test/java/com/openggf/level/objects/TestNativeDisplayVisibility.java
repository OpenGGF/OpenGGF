package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.*;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic2.competition.view.*;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

@RequiresRom(SonicGame.SONIC_2)
class TestNativeDisplayVisibility {
    @Test void objectsReadThePreviousBuildAndMergedP2VisibilityRestoresFromTheExistingCapturedBit() throws Exception {
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        try {
            var h=harness(GameId.S2,true,graphics);var owner=new Probe(500);
            h.manager.addDynamicObjectAtSlot(owner,40);
            owner.update(0,null);assertEquals(List.of(false),owner.reads);assertFalse(owner.readOnScreenMargin());
            var builder=new Sonic2CompetitionSatBuilder(com.openggf.data.RomByteReader.fromRom(TestEnvironment.currentRom()));
            var objects=Sonic2CompetitionObjectDisplay.admissions(h.manager,null);
            var input=new Build(new Sonic2CompetitionDisplayInputs.Camera(0,0),new Sonic2CompetitionDisplayInputs.Camera(300,0),
                    new Hud(false,0,new PlayerHud(0,0,0,0),new PlayerHud(0,0,0,0),0),
                    new RingWindow(List.of()),new RingWindow(List.of()),0,objects,false);
            var first=builder.buildPlayer1(input);assertEquals(List.of(new Visibility(40,false)),first.visibility());
            var second=builder.buildPlayer2(first,input);assertEquals(List.of(new Visibility(40,true)),second.visibility());
            ObjectDrawInternalAccess.commitNativeDisplayVisibility(owner,second.visibility().getFirst().onScreen());
            assertEquals(List.of(false),owner.reads,"BuildSprites does not execute object logic again");
            h.manager.refreshPostCameraRenderState(); // A P1-only geometric refresh must not overwrite it.
            owner.update(1,null);assertEquals(List.of(false,true),owner.reads);
            assertTrue(owner.readOnScreenMargin(),"margin query must also read the previous merged native bit");
            assertTrue(owner.isWithinSolidContactBounds(),"solid logic reads the same native bit");
            var saved=owner.captureRewindState();
            ObjectDrawInternalAccess.commitNativeDisplayVisibility(owner,false);owner.update(2,null);
            assertEquals(List.of(false,true,false),owner.reads);assertFalse(owner.readOnScreenMargin());
            owner.restoreRewindState(saved);assertTrue(owner.readOnScreen());assertTrue(owner.isWithinSolidContactBounds());
        } finally {graphics.resetState();AbstractObjectInstance.resetCameraBoundsForTests();}
    }
    @Test void onePlayerAndSonic3kKeepTheirExistingQueryAndIgnoreForeignNativeWrites() {
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        try {
            for(var game:new GameId[]{GameId.S2,GameId.S3K}) {
                var h=harness(game,false,graphics);
                for(int x:new int[]{100,500}) {
                    var owner=new Probe(x);h.manager.addDynamicObjectAtSlot(owner,40);
                    h.manager.refreshPostCameraRenderState();var saved=owner.captureRewindState();
                    boolean expected=x==100;assertEquals(expected,owner.readOnScreen());
                    ObjectDrawInternalAccess.commitNativeDisplayVisibility(owner,!expected);
                    assertEquals(expected,owner.readOnScreen(),"ordinary "+game+" computation remains authoritative");
                    assertEquals(expected,owner.readOnScreenMargin(),"ordinary margin query keeps its camera computation");
                    assertEquals(saved,owner.captureRewindState(),"foreign write changes no captured bit");
                    h.manager.removeDynamicObject(owner);
                }
            }
        } finally {graphics.resetState();AbstractObjectInstance.resetCameraBoundsForTests();}
    }
    private record Harness(ObjectManager manager) { }
    private static Harness harness(GameId game,boolean nativeDisplay,GraphicsManager graphics) {
        var module=mock(GameModule.class);when(module.getGameId()).thenReturn(game);
        if(nativeDisplay)when(module.getGameService(ObjectDrawInternalAccess.NativeDisplayOwner.class))
                .thenReturn(ObjectDrawInternalAccess.NATIVE_DISPLAY_OWNER);
        var services=new StubObjectServices(){@Override public GameModule gameModule(){return module;}};
        var camera=mock(com.openggf.camera.Camera.class);when(camera.getWidth()).thenReturn((short)320);when(camera.getHeight()).thenReturn((short)224);
        AbstractObjectInstance.updateCameraBounds(0,0,320,224,0);
        return new Harness(new ObjectManager(List.of(),null,0,null,null,graphics,camera,services));
    }
    private static final class Probe extends AbstractObjectInstance implements Sonic2NativeDisplaySource {
        @RewindTransient(reason="test-only read observations, not gameplay state")
        final List<Boolean> reads=new ArrayList<>();
        Probe(int x){super(new ObjectSpawn(x,80,0x11,8,0,false,0),"visibility-probe");}
        boolean readOnScreen(){return isOnScreen();}
        boolean readOnScreenMargin(){return isOnScreen(64);}
        @Override public void update(int vint,PlayableEntity player){reads.add(isOnScreen());}
        @Override public void appendRenderCommands(List<GLCommand> commands){fail("explicit native source must not draw");}
        @Override public ObjectSlot nativeDisplaySlot(){return new ObjectSlot(0x11,4,getX(),getY(),8,0,new Mapping(0xFC70,0),0x41DB,null);}
    }
}
