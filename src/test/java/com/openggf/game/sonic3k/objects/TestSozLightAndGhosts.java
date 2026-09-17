package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.rewind.identity.*;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.physics.*;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozLightAndGhosts {
    private Rom rom;private SozZoneRuntimeState state;private StubObjectServices services;
    private TestablePlayableSprite player;private RespawnState checkpoint;private Camera camera;private ObjectManager manager;
    private final List<AbstractObjectInstance> children=new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        rom=new Rom();assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
        state=new SozZoneRuntimeState(1,PlayerCharacter.SONIC_AND_TAILS);checkpoint=mock(RespawnState.class);
        camera=mock(Camera.class);manager=mock(ObjectManager.class);var level=mock(LevelManager.class);
        var rng=new GameRng(GameRng.Flavour.S3K);
        services=new StubObjectServices(){
            @Override public Rom rom(){return rom;}@Override public SozZoneRuntimeState zoneRuntimeState(){return state;}
            @Override public RespawnState checkpointState(){return checkpoint;}@Override public Camera camera(){return camera;}
            @Override public ObjectManager objectManager(){return manager;}@Override public LevelManager levelManager(){return level;}
            @Override public GameRng rng(){return rng;}
        };
        doAnswer(i->{var o=(AbstractObjectInstance)i.getArgument(0);o.setServices(services);children.add(o);return null;})
                .when(manager).addDynamicObjectAfterCurrent(any());
        player=new TestablePlayableSprite("sonic",(short)0,(short)0);
        services.withPlayerQuery(new ObjectPlayerQuery(()->player,List::of));
        AbstractObjectInstance.updateCameraBounds(0,0,320,224,0);
    }
    @AfterEach void close() throws Exception {rom.close();AbstractObjectInstance.resetCameraBoundsForTests();}
    private SozLightSwitchObjectInstance light(int subtype){
        var l=new SozLightSwitchObjectInstance(new ObjectSpawn(100,100,0x41,subtype,0,false,0));l.setServices(services);
        player.setCentreX((short)100);player.setCentreY((short)148);return l;
    }
    private SozHyudoroControllerObjectInstance controller(){var c=new SozHyudoroControllerObjectInstance(new ObjectSpawn(0,0,0xAA,0,0,false,0));c.setServices(services);return c;}
    private void dark(int level){while(state.lighting().darknessLevel()<level)state.lighting().tickPalette();}
    private static Object get(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static int value(Object o,String name){return (Integer)get(o,name);}
    private static RewindCaptureContext context(AbstractObjectInstance...objects){var t=new RewindIdentityTable();for(int i=0;i<objects.length;i++)t.registerObject(objects[i],ObjectRefId.dynamic(i,0,i));return RewindCaptureContext.withIdentityTable(t);}
    @Test void owningMappingAndDplcPointersMatchLockedOnRom() throws Exception {
        assertArrayEquals(new byte[]{0x21,0x7C,0,4,0x10,(byte)0x90},rom.readBytes(0x40E7A,6));
        assertEquals(0x1872B6,rom.read32BitAddr(0x8F624));assertEquals(0x16B4BC,rom.read32BitAddr(0x8F67A));assertEquals(0x8F6DC,rom.read32BitAddr(0x8F67E));
        assertEquals(7,S3kSpriteDataLoader.loadMappingFrames(RomByteReader.fromRom(rom),0x41090,7).size());
        assertEquals(18,S3kSpriteDataLoader.loadMappingFrames(RomByteReader.fromRom(rom),0x1872B6,18).size());
    }
    @Test void eitherNativeRiderPullsAndResetsLightAtFullThirtyTwoPixelExtension(){
        state.lighting().initializeSeamlessDarkness();var l=light(4);l.update(0,player);assertTrue(l.isPlayerHeld(player));
        for(int i=0;i<15;i++)l.update(i,player);assertEquals(30,l.extension());assertEquals(5,state.lighting().darknessLevel());
        l.update(16,player);assertEquals(32,l.extension());assertEquals(0,state.lighting().darknessLevel());assertEquals(899,state.lighting().masterTimer());assertEquals(-4,state.lighting().fadeRemaining());
        assertEquals(180,player.getCentreY());
        player.setLogicalInputState(false,false,false,false,true,true);l.update(17,player);assertFalse(l.isPlayerHeld(player));assertEquals(-0x380,player.getYSpeed());assertTrue(player.getAir());assertTrue(player.getRolling());
        player.setLogicalInputState(false,false,false,false,false,false);for(int i=0;i<16;i++)l.update(i,player);assertEquals(0,l.extension());
    }
    @Test void floorReleaseOnlyAppliesToTheTwoReachedHighBitSwitches(){
        try(var floor=mockStatic(ObjectTerrainUtils.class)){
            floor.when(()->ObjectTerrainUtils.checkFloorDist(any(),any(),anyBoolean(),anyInt(),anyInt())).thenReturn(new TerrainCheckResult(0,(byte)0,0));
            var high=light(0x84);high.update(0,player);assertTrue(high.isPlayerHeld(player));high.update(1,player);assertFalse(high.isPlayerHeld(player));assertFalse(player.isObjectControlled());
            var normal=light(4);normal.update(2,player);normal.update(3,player);assertTrue(normal.isPlayerHeld(player));
        }
    }
    @Test void switchCaptureAndSharedFadeRestoreForForwardReplay(){
        state.lighting().initializeSeamlessDarkness();var l=light(4);l.update(0,player);for(int i=0;i<10;i++)l.update(i,player);
        var snap=l.captureRewindState();var lighting=state.captureBytes();
        for(int i=0;i<8;i++){l.update(i,player);state.lighting().tickPalette();}var expected=state.captureBytes();
        l.restoreRewindState(snap);state.restoreBytes(lighting);for(int i=0;i<8;i++){l.update(i,player);state.lighting().tickPalette();}
        assertArrayEquals(expected,state.captureBytes());assertEquals(32,l.extension());assertTrue(l.isPlayerHeld(player));
    }
    @Test void sonicAndTailsCheckpointGateAndKnucklesBypassHaveNoColdDarknessInjection(){
        var c=controller();c.update(0,player);assertEquals(0,c.ghostCount());assertEquals(0,state.lighting().darknessLevel());
        dark(1);c.update(1,player);assertEquals(0,c.ghostCount());
        when(checkpoint.getLastCheckpointIndex()).thenReturn(1);c.update(2,player);assertEquals(1,c.ghostCount());assertEquals(1,children.size());
        when(checkpoint.getLastCheckpointIndex()).thenReturn(0);var knux=mock(Knuckles.class);var second=controller();second.update(3,knux);assertEquals(1,second.ghostCount());
    }
    @Test void countUsesDarknessTableAndSixtyFourPassSpacingBeforeAllocation(){
        when(checkpoint.getLastCheckpointIndex()).thenReturn(1);dark(5);var c=controller();c.update(0,player);assertEquals(1,c.ghostCount());
        for(int i=0;i<63;i++)c.update(i,player);assertEquals(1,c.ghostCount());c.update(64,player);assertEquals(2,c.ghostCount());
        for(int i=0;i<64;i++)c.update(i,player);assertEquals(3,c.ghostCount());assertEquals(3,children.size());
        for(int i=0;i<100;i++)c.update(i,player);assertEquals(3,children.size());
        state.lighting().resetLight();c.update(0,player);assertEquals(0,c.ghostCount());
    }
    @Test void darkFiveGhostBecomesWorldAttackAndRollingTouchFadesWithoutPoints(){
        when(checkpoint.getLastCheckpointIndex()).thenReturn(1);dark(5);var c=controller();c.update(0,player);
        var ghost=assertInstanceOf(SozHyudoroBodyObjectInstance.class,children.getFirst());
        for(int i=0;i<500&&ghost.getCollisionFlags()==0;i++)ghost.update(i,player);
        assertEquals(0xD7,ghost.getCollisionFlags());assertEquals(16,value(ghost,"routine"));
        player.setAnimationId(2);ghost.onTouchResponse(player,null,0);ghost.update(1,player);
        assertEquals(0,ghost.getCollisionFlags());assertTrue((Boolean)get(ghost,"fading"));assertEquals(1,children.size());
        for(int i=0;i<40&&!ghost.isDestroyed();i++)ghost.update(i,player);assertTrue(ghost.isDestroyed());assertEquals(0,c.ghostCount());
    }
    @Test void ghostDeletesOnPassAfterPostCameraRenderClearsOnScreenBit(){
        // Hyudoro_body tests render_flags from the previous Render_Sprites, which uses the moved camera
        // (soz_completerun row 44145 frees the count one pass before the pre-camera bounds would).
        when(checkpoint.getLastCheckpointIndex()).thenReturn(1);dark(5);var c=controller();c.update(0,player);
        var ghost=assertInstanceOf(SozHyudoroBodyObjectInstance.class,children.getFirst());
        AbstractObjectInstance.updateCameraBounds(0x4000,0x4000,0x4140,0x40E0,0);
        ghost.refreshPostCameraRenderState();ghost.update(0,player);
        assertEquals(1,c.ghostCount(),"screen-positioned routines are always marked on-screen");
        AbstractObjectInstance.updateCameraBounds(0,0,320,224,0);
        for(int i=0;i<500&&ghost.getCollisionFlags()==0;i++)ghost.update(i,player);
        assertEquals(16,value(ghost,"routine"));
        AbstractObjectInstance.updateCameraBounds(0x4000,0x4000,0x4140,0x40E0,0);
        ghost.update(1,player);assertEquals(1,c.ghostCount(),"the moved camera is not visible until Render_Sprites");
        ghost.refreshPostCameraRenderState();ghost.update(2,player);assertEquals(0,c.ghostCount());
    }
    @Test void apparitionPatrolAndDarknessMorphReplayWithRealRomAnimationPrograms(){
        when(checkpoint.getLastCheckpointIndex()).thenReturn(1);dark(1);var c=controller();c.update(0,player);
        var ghost=assertInstanceOf(SozHyudoroBodyObjectInstance.class,children.getFirst());
        for(int i=0;i<60;i++)ghost.update(i,player);assertEquals(4,value(ghost,"routine"));assertEquals(0,ghost.getCollisionFlags());
        dark(2);for(int i=0;i<80;i++)ghost.update(i,player);assertEquals(2,value(ghost,"darkness"));
        var ctx=context(c,ghost);var snap=ghost.captureRewindState(ctx);for(int i=0;i<60;i++)ghost.update(i,player);
        int x=ghost.getX(),y=ghost.getY(),frame=value(ghost,"frame");ghost.restoreRewindState(snap,ctx);
        for(int i=0;i<60;i++)ghost.update(i,player);assertEquals(x,ghost.getX());assertEquals(y,ghost.getY());assertEquals(frame,value(ghost,"frame"));
        dark(3);for(int i=0;i<100;i++)ghost.update(i,player);assertEquals(0x180,Math.abs(value(ghost,"xVelocity")));
        dark(4);for(int i=0;i<100;i++)ghost.update(i,player);assertEquals(4,value(ghost,"darkness"));
    }
    @Test void capsuleCreatesRealButtonAndRespectsCheckpointAndKnucklesAlreadyOpenGate(){
        var capsule=new SozHyudoroCapsuleObjectInstance(new ObjectSpawn(100,100,0xAC,0,0,false,0));capsule.setServices(services);capsule.update(0,player);
        assertFalse(capsule.opened());assertInstanceOf(SozHyudoroCapsuleObjectInstance.Button.class,children.getFirst());
        when(checkpoint.getLastCheckpointIndex()).thenReturn(1);var reloaded=new SozHyudoroCapsuleObjectInstance(capsule.getSpawn());reloaded.setServices(services);reloaded.update(0,player);assertTrue(reloaded.opened());
        assertTrue((Boolean)get(children.getLast(),"recessed"));
        when(checkpoint.getLastCheckpointIndex()).thenReturn(0);var knux=new SozHyudoroCapsuleObjectInstance(capsule.getSpawn());knux.setServices(services);knux.update(0,mock(Knuckles.class));assertTrue(knux.opened());
        assertFalse((Boolean)get(children.getLast(),"recessed"),"Knuckles opens capsule body but checkpoint alone recesses button");
    }
    @Test void capsuleEscapeUsesNativeSingleDelayInterpretationOfRomAnimation(){
        var ghost=new SozHyudoroCapsuleObjectInstance.EscapeGhost(new ObjectSpawn(160,100,0,0,0,false,0));ghost.setServices(services);ghost.update(0,player);
        for(int frame:new int[]{1,3,2,4,3,1}){ghost.update(0,player);assertEquals(frame,value(ghost,"frame"));}
        assertTrue(ghost.getY()<100);assertEquals(148,ghost.getX());
    }
    @Test void reachedSpriteMaskUsesFrameFourGeometryAndPriorityZero(){
        var mask=new SozSpriteMaskObjectInstance(new ObjectSpawn(0x5200,0x750,0x8B,0x40,0,false,0));mask.setServices(services);
        assertEquals(0,mask.getPriorityBucket());assertEquals(0x20,mask.getOnScreenHalfWidth());assertEquals(0x10,mask.getOnScreenHalfHeight());
        var snap=mask.captureRewindState();mask.restoreRewindState(snap);assertEquals(0x5200,mask.getX());
    }
}
