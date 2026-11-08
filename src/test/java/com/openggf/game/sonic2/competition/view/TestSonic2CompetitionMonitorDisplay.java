package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.competition.*;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.objects.MonitorContentsObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionMonitorDisplay {
    // Obj2E_Init (s2.asm:25745-25752): literal instruction operands at REV01 loc_12868.
    private static final int OBJ2E_INIT = 0x12868;
    @Test void eachInitializedContentsProjectsTheRomSelectedFirstPieceWithoutAdvancingTheOwner() throws Exception {
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());var h=harness(rom);
        int table=Sonic2Constants.MAP_UNC_MONITOR_ADDR;
        for(int clock=0;clock<8;clock++) {
            when(h.level.getFrameCounter()).thenReturn(clock);
            var owner=new Sonic2CompetitionMonitorContents(300,200,0,null);owner.setServices(h.services);
            assertNull(owner.nativeDisplaySlot(),"a newly allocated owner has not reached Obj2E_Init");
            owner.delayFirstIconUpdateForPassedSlot();owner.update(99,null);
            assertNull(owner.nativeDisplaySlot(),"the passed SST slot has no DisplaySprite call yet");
            owner.update(100,null);
            int frame=clock+2; // native content=(Level_frame_counter&7)+1, then loc_128C6 adds one.
            int pointer=table+rom.readS16BE(table+frame*2)+2;
            int rawArt=rom.readU16BE(OBJ2E_INIT+6);
            int adjusted=(rawArt&0xF800)|((rawArt&0x07FF)>>>1);
            var expected=new ObjectSlot(0x2E,rom.readU16BE(OBJ2E_INIT+16),300,197,
                    rom.readU16BE(OBJ2E_INIT+28),0,new Mapping(pointer,0),adjusted,null);
            var before=owner.captureRewindState();
            assertEquals(expected,Sonic2CompetitionObjectDisplay.project(owner,null,
                    ()->fail("native monitor projection cannot fall back to first-piece drawing")));
            assertEquals(rom.readU16BE(OBJ2E_INIT+22),owner.getPriorityBucket());
            assertEquals(before,owner.captureRewindState(),"projection reads existing state only");
            assertTrue(rom.readU16BE(pointer-2)>0,"the selected ROM mapping contains its first piece");
            owner.setDestroyed(true);assertNull(owner.nativeDisplaySlot());
        }
    }
    @Test void onePlayerMonitorContentsKeepsTheOrdinaryFrameOffsetOriginAndFirstPieceDraw() {
        var renderer=mock(PatternSpriteRenderer.class);var sheet=mock(ObjectSpriteSheet.class);
        var render=mock(ObjectRenderManager.class);
        var icon=new SpriteMappingPiece(-8,-8,2,2,4,false,false,0);
        when(render.getMonitorRenderer()).thenReturn(renderer);when(render.getMonitorSheet()).thenReturn(sheet);
        when(renderer.isReady()).thenReturn(true);when(sheet.getFrameCount()).thenReturn(16);
        when(sheet.getFrame(2)).thenReturn(new SpriteMappingFrame(List.of(icon,
                new SpriteMappingPiece(0,0,1,1,9,false,false,0))));
        var owner=new MonitorContentsObjectInstance(220,240,1,null);
        owner.setServices(new StubObjectServices(){@Override public ObjectRenderManager renderManager(){return render;}});
        owner.appendRenderCommands(List.of());
        verify(sheet).getFrame(2);verify(renderer).drawPieces(List.of(icon),220,240,false,false);
        verify(renderer).isReady();verifyNoMoreInteractions(renderer);
    }
    private record Harness(LevelManager level,ObjectServices services) { }
    private static Harness harness(RomByteReader rom) throws Exception {
        var session=new Sonic2CompetitionSession();
        session.updateMenu(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(0,0,0,0,true,true),
                com.openggf.control.PlayerInputState.neutral()),true);
        var policy=mock(WorldSessionPolicyState.class);
        var host=mock(Sonic2CompetitionObjectHost.class);
        when(policy.getService(Sonic2CompetitionSession.class)).thenReturn(session);
        when(policy.getService(Sonic2CompetitionObjectHost.class)).thenReturn(host);
        var module=mock(GameModule.class);
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world->policy);
        var world=new WorldSession(module);var level=mock(LevelManager.class);
        var services=new StubObjectServices(){
            @Override public WorldSession worldSession(){return world;}
            @Override public LevelManager levelManager(){return level;}
            @Override public RomByteReader romReader(){return rom;}
        };
        return new Harness(level,services);
    }
}
