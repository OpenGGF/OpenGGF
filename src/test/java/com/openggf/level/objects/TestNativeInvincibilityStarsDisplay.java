package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.competition.state.Sonic2LevelOnlyObjectRegistry;
import com.openggf.game.sonic2.competition.view.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

@RequiresRom(SonicGame.SONIC_2)
class TestNativeInvincibilityStarsDisplay {
    @Test void completedSstTriplesUseTheSelectedPoseBeforeCounterProgressionAndRestoreItOnce() throws Exception {
        var h=harness(true);var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
        try {
            for(int base:new int[]{0x88,0x8C}) {
                var stars=ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(h.objects,base,
                        ()->new InvincibilityStarsObjectInstance(h.player));
                assertTrue(Sonic2CompetitionObjectDisplay.admissions(h.objects,null).isEmpty());
                var otherPlayer=mock(PlayableEntity.class);when(otherPlayer.getDirection()).thenReturn(Direction.LEFT);
                int[] angles={4,0,22,44}, cursors={0,0,0,0};
                for(int frame=0;frame<37;frame++) {
                    boolean left=frame>=15;when(h.player.getDirection()).thenReturn(left?Direction.LEFT:Direction.RIGHT);
                    stars.update(frame,otherPlayer);
                    var actual=Sonic2CompetitionObjectDisplay.admissions(h.objects,null);
                    assertEquals(List.of(base,base+1,base+2,base+3),actual.stream().map(Admission::slot).toList());
                    for(int layer=0;layer<4;layer++) {
                        int descriptor=0x1D992+(layer-1)*6;
                        int table=layer==0?0x1DB82:rom.readU32BE(descriptor);
                        // Obj35's stored cursor may point at FF; selection resets it before the display write.
                        if(rom.readU8(table+cursors[layer])==255)cursors[layer]=0;
                        int primary=rom.readU8(table+cursors[layer]);
                        int secondary=layer==0?primary:rom.readU8(table+cursors[layer]+rom.readU8(descriptor+5));
                        int x=100-layer*100,y=80+layer*3;
                        var children=new ArrayList<Child>();
                        for(int child=0;child<2;child++) {
                            int offset=0x1DB42+((angles[layer]+32*child)&62);
                            children.add(new Child((x+(byte)rom.readU8(offset))&65535,
                                    (y+(byte)rom.readU8(offset+1))&65535,child==0?secondary:primary));
                        }
                        // Obj35_Main (s2.asm:42473-42481): mapping/art/flags/width/count.
                        int init=0x1D9A4;
                        assertEquals(new ObjectSlot(0x35,0x44,x&65535,y,16,0,
                                new Mapping(rom.readU32BE(init+24),0),
                                Sonic2CompetitionSpriteArtTable.adjust2PArtPointer(rom.readU16BE(init+32)),
                                new MultiSprite(0,16,0,children)),actual.get(layer).occupant(),"frame "+frame+" layer "+layer);
                        assertEquals(1,actual.get(layer).bucket());
                        cursors[layer]++;angles[layer]=(angles[layer]+(left?-1:1)*(layer==0?18:2))&255;
                    }
                    var pose=ObjectDrawInternalAccess.invincibilityStarsDisplayPose(stars);
                    pose[0]=9999;
                    assertEquals(100,ObjectDrawInternalAccess.invincibilityStarsDisplayPose(stars)[0],"copied reader");
                    var before=ObjectDrawInternalAccess.invincibilityStarsDisplayPose(stars);
                    // Drawing is read-only; the native projection never invokes ordinary draw.
                    stars.appendRenderCommands(new ArrayList<>());
                    assertArrayEquals(before,ObjectDrawInternalAccess.invincibilityStarsDisplayPose(stars));
                }
                var expected=Sonic2CompetitionObjectDisplay.admissions(h.objects,null);
                var saved=stars.captureRewindState();stars.update(100,h.player);
                assertNotEquals(expected,Sonic2CompetitionObjectDisplay.admissions(h.objects,null));
                stars=ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(h.objects,base,
                        ()->new InvincibilityStarsObjectInstance(h.player));
                assertArrayEquals(new int[0],ObjectDrawInternalAccess.invincibilityStarsDisplayPose(stars));
                stars.restoreRewindState(saved);
                assertEquals(expected,Sonic2CompetitionObjectDisplay.admissions(h.objects,null));
                var builder=new Sonic2CompetitionSatBuilder(rom);
                var build=new Build(new Sonic2CompetitionDisplayInputs.Camera(0,0),new Sonic2CompetitionDisplayInputs.Camera(0,0),
                        new Hud(false,0,new PlayerHud(0,0,0,0),new PlayerHud(0,0,0,0),0),
                        new RingWindow(List.of()),new RingWindow(List.of()),0,expected,false);
                assertEquals(List.of(new Visibility(base,true),new Visibility(base+1,true),
                        new Visibility(base+2,false),new Visibility(base+3,false)),builder.buildPlayer1(build).visibility(),
                        "each actual group is culled separately at the left edge");
                h.objects.removeDynamicObject(stars);
            }
        } finally {h.graphics.resetState();}
    }
    @Test void ordinaryOnePlayerDrawingStillReadsItsExistingIncrementedStateAndRecordsNothing() throws Exception {
        var h=harness(false);var calls=new ArrayList<List<Integer>>();
        doAnswer(inv->{calls.add(List.of(inv.getArgument(0),inv.getArgument(1),inv.getArgument(2)));return null;})
                .when(h.renderer).drawFrameIndex(anyInt(),anyInt(),anyInt(),eq(false),eq(false));
        try {
            var stars=ObjectConstructionContext.construct(h.objects.getObjectServices(),()->new InvincibilityStarsObjectInstance(h.player));
            for(int frame=0;frame<25;frame++) {
                stars.update(frame,h.player);calls.clear();stars.appendRenderCommands(new ArrayList<>());
                assertEquals(8,calls.size());
                for(int layer=0;layer<4;layer++)for(int child=0;child<2;child++) {
                    int angle=(stars.getAngleBytes()[layer]+child*32)&62;
                    int[][] frames=child==0?InvincibilityStarsObjectInstance.TRAIL_SECONDARY:InvincibilityStarsObjectInstance.TRAIL_PRIMARY;
                    int image=layer==0?InvincibilityStarsObjectInstance.PARENT_ANIM[stars.getAnimCounters()[0]]:
                            frames[layer-1][stars.getAnimCounters()[layer]];
                    int[] offset=InvincibilityStarsObjectInstance.ORBIT_OFFSETS[angle/2];
                    assertEquals(List.of(image,100-layer*100+offset[0],80+layer*3+offset[1]),calls.get(layer*2+child));
                }
                assertArrayEquals(new int[0],ObjectDrawInternalAccess.invincibilityStarsDisplayPose(stars));
            }
        } finally {h.graphics.resetState();}
    }
    private record Harness(ObjectManager objects,Sonic player,PatternSpriteRenderer renderer,GraphicsManager graphics) { }
    private static Harness harness(boolean nativeDisplay) throws Exception {
        var player=spy(new Sonic("sonic",(short)0,(short)0));
        doReturn((short)100).when(player).getCentreX();doReturn((short)80).when(player).getCentreY();
        doAnswer(inv->(short)(100-inv.<Integer>getArgument(0)/3*100)).when(player).getCentreX(anyInt());
        doAnswer(inv->(short)(80+inv.<Integer>getArgument(0))).when(player).getCentreY(anyInt());
        player.setDirection(Direction.RIGHT);
        var roster=mock(NativeCompetitionRoster.class);when(roster.player(anyInt())).thenReturn(player);
        var policy=mock(WorldSessionPolicyState.class);when(policy.getService(NativeCompetitionRoster.class)).thenReturn(roster);
        var module=mock(GameModule.class);when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world->policy);
        if(nativeDisplay)when(module.getGameService(ObjectDrawInternalAccess.NativeDisplayOwner.class))
                .thenReturn(ObjectDrawInternalAccess.NATIVE_DISPLAY_OWNER);
        var world=new WorldSession(module);var graphics=new GraphicsManager();graphics.initHeadless();
        var renderer=mock(PatternSpriteRenderer.class);var render=mock(ObjectRenderManager.class);
        when(render.getInvincibilityStarsRenderer()).thenReturn(renderer);
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
        var services=new StubObjectServices(){
            @Override public WorldSession worldSession(){return world;}
            @Override public GameModule gameModule(){return module;}
            @Override public ObjectRenderManager renderManager(){return render;}
            @Override public RomByteReader romReader(){return rom;}
        };
        var registry=mock(ObjectRegistry.class);when(registry.objectWindowingStrategy()).thenReturn(ObjectWindowingStrategy.LEGACY);
        var objects=new ObjectManager(List.of(),new Sonic2LevelOnlyObjectRegistry(registry),0,null,null,graphics,mock(Camera.class),services);
        return new Harness(objects,player,renderer,graphics);
    }
}
