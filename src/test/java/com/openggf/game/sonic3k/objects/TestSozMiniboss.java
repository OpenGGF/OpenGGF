package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.rewind.*;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozMiniboss {
    private Rom rom;
    private RomByteReader reader;
    private ObjectManager manager;
    private SozMinibossInstance boss;
    private AbstractPlayableSprite player;
    private SozZoneRuntimeState runtime;
    private Camera camera;
    private ObjectServices services;
    private MockedStatic<ObjectTerrainUtils> terrain;
    private int clock;
    @BeforeEach void setup() throws Exception {
        rom=new Rom();assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));reader=RomByteReader.fromRom(rom);
        GraphicsManager.getInstance().initHeadless();
        player=mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenAnswer(call -> (short)(boss==null?0x43C0:boss.getX()+50));
        when(player.getCentreY()).thenReturn((short)0x9D0);
        camera=new Camera();camera.setX((short)0x42E0);camera.setY((short)0x980);camera.setMinX((short)0x4200);camera.setMaxX((short)0x4450);
        runtime=new SozZoneRuntimeState(0,PlayerCharacter.SONIC_ALONE);
        var gameState=new GameStateManager();
        var levelState=mock(LevelState.class);
        services=new StubObjectServices(){
            @Override public ObjectManager objectManager(){return manager;}
            @Override public LevelState levelGamestate(){return levelState;}
            @Override public GameStateManager gameState(){return gameState;}
            @Override public Camera camera(){return camera;}
            @Override public com.openggf.debug.DebugOverlayManager debugOverlay(){return mock(com.openggf.debug.DebugOverlayManager.class);}
            @Override public RomByteReader romReader(){return reader;}
            @Override public Rom rom(){return null;}
            @Override public SozZoneRuntimeState zoneRuntimeState(){return runtime;}
            @Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->player,List::of);}
            @Override public GraphicsManager graphicsManager(){return GraphicsManager.getInstance();}
        };
        manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,new com.openggf.game.sonic3k.Sonic3kGameModule().createTouchResponseTable(reader),GraphicsManager.getInstance(),camera,services);manager.reset(0x42E0);
        boss=manager.createDynamicObject(()->new SozMinibossInstance(spawn()));
        AbstractObjectInstance.updateCameraBounds(0x42E0,0x980,320,224,0);
        terrain=mockStatic(ObjectTerrainUtils.class);
        terrain.when(()->ObjectTerrainUtils.checkFloorDist(anyInt(),anyInt(),anyInt())).thenAnswer(call -> new TerrainCheckResult(0xA32-(int)call.getArgument(1)-(int)call.getArgument(2),(byte)0,1));
    }
    @AfterEach void cleanup() throws Exception {if(terrain!=null)terrain.close();if(rom!=null)rom.close();GraphicsManager.getInstance().resetState();AbstractObjectInstance.resetCameraBoundsForTests();}
    private ObjectSpawn spawn(){return new ObjectSpawn(0x439D,0x9F7,0x97,0,0,false,0);}
    private void step(){manager.update(0x42E0,player,List.of(),clock++,false);}
    private void until(java.util.function.BooleanSupplier gate,int limit){for(int i=0;i<limit && !gate.getAsBoolean();i++)step();assertTrue(gate.getAsBoolean(),"frontier phase="+boss.phase()+" routine="+boss.routine()+" at "+clock);}
    private List<SozMinibossChild> children(){return manager.getActiveObjects().stream().filter(SozMinibossChild.class::isInstance).map(SozMinibossChild.class::cast).toList();}
    private Object field(Object value,String name){try{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(Exception ex){throw new AssertionError(ex);}}
    private CompositeSnapshot capture(){var r=new RewindRegistry();r.register(manager.rewindSnapshottable());return r.capture();}
    private void restore(CompositeSnapshot snapshot){var r=new RewindRegistry();r.register(manager.rewindSnapshottable());r.restore(snapshot);boss=manager.getActiveObjects().stream().filter(SozMinibossInstance.class::isInstance).map(SozMinibossInstance.class::cast).findFirst().orElse(null);}
    @Test void dustRetainsItsOwnPriorityOnBothSidesOfTheSandBoundary() {
        for(int kind:new int[]{SozMinibossChild.DUST,SozMinibossChild.COLLAPSE_DUST}) {
            var child=manager.createDynamicObject(()->new SozMinibossChild(spawn(),boss,kind,0));
            child.update(0,player);
            for(int x:new int[]{0x41FF,0x4200}) {
                child.x=x;
                // ObjDat3_773CA sets $180/high; loc_770C4/770DA never call sub_770EA.
                assertEquals(3,child.getPriorityBucket());
                assertTrue(child.isHighPriority());
            }
        }
    }
    @Test void hitReactionRetainsItsCopiedArtPriorityAndOwnBucket() {
        for(int parentX:new int[]{0x41FF,0x4200}) {
            boss.x=parentX;
            var head=manager.createDynamicObject(()->new SozMinibossChild(spawn(),boss,SozMinibossChild.HITBOX,0));
            boss.x=parentX<0x4200?0x4201:0x41FE;
            head.update(clock++,player);
            head.frame=0x12; // Visible hit-reaction mapping; idle frame $1A is empty.
            assertEquals(5,head.getPriorityBucket());
            assertEquals(parentX>=0x4200,head.isHighPriority());
        }
    }
    @Test void detachedPartsRetainPriorityAcrossTheSandBoundaryAndRecreation() {
        boss.hitBy(player);
        for(int startX:new int[]{0x41FF,0x4200}) {
            var part=manager.createDynamicObject(()->new SozMinibossChild(spawn(),boss,SozMinibossChild.PART,0));
            part.x=startX;
            part.update(clock++,player);
            assertTrue((boolean)field(part,"debris"));
            int bucket=startX<0x4200?1:5;
            boolean high=startX>=0x4200;
            part.x=startX<0x4200?0x4201:0x41FE;
            assertEquals(bucket,part.getPriorityBucket());
            assertEquals(high,part.isHighPriority());
            var snapshot=capture();
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            restore(snapshot);
            var restored=children().stream().filter(c->c.getSlotIndex()==part.getSlotIndex()).findFirst().orElseThrow();
            assertEquals(bucket,restored.getPriorityBucket());
            assertEquals(high,restored.isHighPriority());
            assertTrue(RewindSnapshotDiff.diffKey("object-manager",snapshot.get("object-manager"),capture().get("object-manager")).isEmpty());
        }
    }
    @Test void romPointersAndNativeStartPauseAreExact(){
        assertEquals(0x16CB5C,reader.readU32BE(0x76A60));assertEquals(0x16E0EE,reader.readU32BE(0x76A70));
        assertEquals(0x774A6,reader.readU32BE(0x773AC));assertEquals(0x77626,reader.readU32BE(0x773CA));
        step();assertEquals(1,boss.phase());for(int i=0;i<120;i++)step();assertEquals(1,boss.phase());step();assertEquals(2,boss.phase());
        step();assertEquals(2,boss.routine());
    }
    @Test void articulatedGraphHasExactPeakAndForcedReconstruction(){
        until(()->children().size()==11,400); // two touch slots, one cover, eight pose parts.
        assertEquals(8,children().stream().filter(c->(int)field(c,"kind")==SozMinibossChild.PART).count());
        var snapshot=capture();manager.setRewindInPlaceRestoreEnabledForTest(false);
        new ArrayList<>(manager.getActiveObjects()).forEach(manager::removeDynamicObject);restore(snapshot);
        assertEquals(11,children().size());for(var child:children())assertSame(boss,field(child,"owner"));
        assertTrue(RewindSnapshotDiff.diffKey("object-manager",snapshot.get("object-manager"),capture().get("object-manager")).isEmpty());
        step();assertEquals(10,children().size(),"cover expires after the parts' creation pass");
    }
    @Test void exposedHeadPublishesCollapseAndDoesNotDefeatByHitCount(){
        until(()->children().stream().anyMatch(c->c.getCollisionFlags()==0xD7),800);
        var head=children().stream().filter(c->c.getCollisionFlags()==0xD7).findFirst().orElseThrow();
        when(player.getAnimationId()).thenReturn(2);when(player.getXSpeed()).thenReturn((short)0x100);when(player.getYSpeed()).thenReturn((short)-0x300);
        head.onTouchResponse(player,null,clock);step();assertTrue(boss.collapsing());assertEquals(2,boss.phase());
        int y=boss.getY();step();assertEquals(-0x200,boss.xVel);assertEquals(-0x200,boss.yVel);assertEquals(y,boss.getY());
        verify(player).setXSpeed((short)-0x100);verify(player).setYSpeed((short)0x300);
        assertTrue(children().stream().filter(c->(int)field(c,"kind")==SozMinibossChild.PART).allMatch(c->(boolean)field(c,"debris")));
    }
    @Test void ordinaryUnarmedBodyCannotReceiveAnEnemyOrBossHit(){
        assertFalse((Object)boss instanceof TouchResponseAttackable);
        until(()->children().size()>=2,250);
        assertTrue(children().stream().noneMatch(c->c.getCollisionFlags()==0xD7));
        assertTrue(children().stream().allMatch(c->!((Object)c instanceof TouchResponseAttackable)));
    }
    @Test void positionalDefeatLocksCameraAndSinksFor192Ticks(){
        until(()->boss.phase()==2,200);boss.y=0xA10;step();assertEquals(3,boss.phase());
        assertEquals(camera.getX(),camera.getMinX());assertEquals(camera.getX(),camera.getMaxX());
        for(int i=0;i<191;i++)step();assertFalse(boss.isDestroyed());step();assertTrue(boss.isDestroyed());
        assertTrue(manager.getActiveObjects().stream().anyMatch(SozMinibossChild.Alignment.class::isInstance));
        assertTrue(manager.getActiveObjects().stream().anyMatch(S3kBossDefeatSignpostFlow.class::isInstance));
    }
    @Test void partialHitboxAllocationIsPreservedAcrossRecreation(){
        until(()->boss.routine()==2,200);
        until(()->(int)field(boss,"timer")==0,100);
        manager.reserveAllButNFreeSlots(1);
        step();
        assertEquals(1,children().size());
        assertEquals(0,field(children().getFirst(),"index"));
        var snapshot=capture();manager.setRewindInPlaceRestoreEnabledForTest(false);
        restore(snapshot);assertEquals(1,children().size());
        assertSame(boss,field(children().getFirst(),"owner"));
    }
    @Test void resultsAlignmentPublishesOnlyAfterBothEdgesAndRestoresBounds(){
        var alignment=manager.createDynamicObject(()->new SozMinibossChild.Alignment(spawn(),0x4200,0x4450));
        manager.removeDynamicObject(boss);boss=null;
        when(player.getCentreX()).thenReturn((short)0x43A0);
        camera.setMinX((short)0x42E0);camera.setMaxX((short)0x42E0);
        alignment.update(0,player);assertEquals(0,runtime.sandCorkBackgroundFlag());
        services.gameState().setEndOfLevelActive(true);
        alignment.update(1,player);assertEquals(0,runtime.sandCorkBackgroundFlag());
        services.gameState().setEndOfLevelActive(false);
        alignment.update(2,player);assertEquals(0x55,runtime.sandCorkBackgroundFlag());
        assertTrue(alignment.isDestroyed());
        assertEquals(2,manager.activeObjectsOfType(SozMinibossChild.Bounds.class).size());
        for(int i=0;i<70;i++)step();
        assertEquals(0x4200,camera.getMinX()&65535);assertEquals(0x4450,camera.getMaxX()&65535);
        verify(player).setXSpeed((short)0);verify(player).setYSpeed((short)0);
        // loc_76EE4 clr.w (Ctrl_1_logical): the locked leader's recorded input must go idle.
        verify(player).clearLogicalInputState();
    }
    @Test void endSignConversionKeepsTheNativeSlotWhenNoFreeSlotsRemain(){
        until(()->boss.phase()==2,200);boss.y=0xA10;step();
        int slot=boss.getSlotIndex();
        for(int i=0;i<191;i++)step();
        manager.reserveAllButNFreeSlots(0);step();
        var flow=manager.activeObjectsOfType(S3kBossDefeatSignpostFlow.class).getFirst();
        assertEquals(slot,flow.getSlotIndex());
        assertTrue(manager.activeObjectsOfType(SozMinibossChild.Alignment.class).isEmpty());
        // loc_76E48 jumps into Obj_EndSignControl in the sinking pass; recorded
        // s3k-tails-full-chain-all-emeralds SOZ1 spawns Obj_EndSign 120 frames later.
        assertEquals(0x77-1,flow.waitTimerAfterInitialization(),
                "the in-pass Obj_EndSignControl install owns the first $77 wait entry");
    }

    @Test void everyRepeatedChildTableRetainsEachAllocationPrefix() throws Exception {
        int[] callbacks={0x76B30,0x76B86,0x76C5E,0x76CA2};
        int[] counts={2,8,6,6};
        int[] kinds={SozMinibossChild.HITBOX,SozMinibossChild.PART,SozMinibossChild.DUST,SozMinibossChild.COLLAPSE_DUST};
        for(int table=0;table<callbacks.length;table++)for(int prefix=0;prefix<=counts[table];prefix++){
            manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
            for(int slot=4;slot<94;slot++)manager.releaseDynamicSlot(slot);
            boss=manager.createDynamicObject(()->new SozMinibossInstance(spawn()));
            for(var entry:Map.of("phase",2,"routine",2,"timer",0,"callback",callbacks[table]).entrySet()){
                var f=SozMinibossInstance.class.getDeclaredField(entry.getKey());f.setAccessible(true);f.setInt(boss,entry.getValue());
            }
            manager.reserveAllButNFreeSlots(prefix);boss.update(clock++,player);
            int kind=kinds[table];
            assertEquals(prefix,children().stream().filter(c->(int)field(c,"kind")==kind).count(),"callback="+Integer.toHexString(callbacks[table]));
            var snapshot=capture();manager.setRewindInPlaceRestoreEnabledForTest(false);restore(snapshot);
            assertEquals(prefix,children().size());
            for(var child:children())assertSame(boss,field(child,"owner"));
            assertTrue(RewindSnapshotDiff.diffKey("object-manager",snapshot.get("object-manager"),capture().get("object-manager")).isEmpty());
        }
    }

    @Test void nativeTouchPassPublishesHeadContactThenParentConsumesIt(){
        until(()->children().stream().anyMatch(c->c.getCollisionFlags()==0xD7),800);
        var head=children().stream().filter(c->c.getCollisionFlags()==0xD7).findFirst().orElseThrow();
        when(player.getCentreX()).thenReturn((short)head.getX());when(player.getCentreY()).thenReturn((short)head.getY());
        when(player.getYRadius()).thenReturn((short)19);when(player.getAnimationId()).thenReturn(2);
        when(player.getGameRules()).thenReturn(com.openggf.game.rules.GameRules.SONIC_3K);
        manager.runTouchResponsesForPlayer(player,clock);
        assertEquals(1,head.getCollisionProperty());assertFalse(boss.collapsing());
        step();assertTrue(boss.collapsing());assertEquals(2,boss.phase());
        var snapshot=capture();manager.setRewindInPlaceRestoreEnabledForTest(false);restore(snapshot);
        assertTrue(boss.collapsing());assertEquals(1,field(boss,"attackerSlot"));
    }

    @Test void subtype04ExplosionsFollowParentAndStopAtRetirement(){
        until(()->boss.phase()==2,200);boss.y=0xA10;step();
        for(int i=0;i<100;i++)step();
        var explosions=manager.activeObjectsOfType(SozMinibossChild.Explosions.class).getFirst();
        assertEquals(boss.getX(),explosions.getX());assertEquals(boss.getY(),explosions.getY());
        var snapshot=capture();manager.setRewindInPlaceRestoreEnabledForTest(false);restore(snapshot);
        explosions=manager.activeObjectsOfType(SozMinibossChild.Explosions.class).getFirst();assertSame(boss,field(explosions,"owner"));
        for(int i=0;i<92;i++)step();
        assertTrue(manager.activeObjectsOfType(SozMinibossChild.Explosions.class).isEmpty());
    }

}
