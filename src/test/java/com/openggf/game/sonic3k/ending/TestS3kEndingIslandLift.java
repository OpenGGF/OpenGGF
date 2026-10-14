package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.util.PatternDecompressor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Literal IslandLift graph/vector checks, independent of the long rescue route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingIslandLift {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(int width){
        var c=SonicConfigurationService.getInstance();c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        GameServices.camera().refreshViewportDimensions(c);
        return HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
    }
    private EndingIslandLiftObjectInstance create(int subtype,int ordinal,int x,int y){
        return GameServices.level().getObjectManager().createDynamicObject(()->new EndingIslandLiftObjectInstance(subtype,ordinal,x,y));
    }
    @Test void verifiedRomPointersAndEveryMappingFrameFitTheNativeModule() throws Exception {
        boot(320);var s=TestEnvironment.objectServices();var rom=s.romReader();
        assertEquals(S3kEndingResources.ISLAND_LIFT,rom.readU32BE(0x5DBA4));
        assertEquals(S3kEndingResources.MAP_ISLAND_LIFT,rom.readU32BE(0x5EDAE));
        var patterns=PatternDecompressor.kosinskiModuled(s.rom(),S3kEndingResources.ISLAND_LIFT);
        assertEquals(62,patterns.length);
        var frames=S3kSpriteDataLoader.loadMappingFrames(rom,S3kEndingResources.MAP_ISLAND_LIFT);
        assertEquals(27,frames.size());
        for(int i=0;i<26;i++){
            assertEquals(1,frames.get(i).pieces().size());var p=frames.get(i).pieces().getFirst();
            assertTrue(p.tileIndex()+p.widthTiles()*p.heightTiles()<=patterns.length,"native frame "+i);
        }
        assertTrue(frames.get(26).pieces().isEmpty());
    }
    @ParameterizedTest @CsvSource({"65,0,0,1,1", "66,0,4,5,1", "66,1,4,5,5", "67,0,26,8,1", "67,1,26,8,25",
        "68,0,26,11,1", "68,1,26,11,1", "68,2,26,11,17", "69,0,0,15,1", "69,1,0,15,13", "70,0,0,19,1", "71,0,0,23,1"})
    void nativeInitReturnsAndOrdinalOffsetsDelayOnlyTheOwningScripts(int subtype,int ordinal,int initial,int first,int ticks){
        var f=boot(320);var o=create(subtype,ordinal,0x100,0x100);o.update(0,f.sprite());
        assertEquals(initial,o.mappingFrame());assertFalse(o.drawAdmitted(),"loc_5EDAC setup returns before Draw_Sprite");
        for(int i=0;i<ticks-1;i++)o.update(2+i*2,f.sprite());
        assertEquals(initial,o.mappingFrame(),"the native byte delay has not expired");
        o.update(2+(ticks-1)*2,f.sprite());assertEquals(first,o.mappingFrame());assertTrue(o.drawAdmitted());assertEquals(6,o.getPriorityBucket());
        assertEquals(subtype>=0x42&&subtype<=0x44,o.isHighPriority());
    }
    @ParameterizedTest @ValueSource(ints={320,352,400,528,800})
    void priorCameraCopyThresholdConvertsOnceThenMovesFractionallyAndUsesVIntParity(int width){
        var f=boot(width);var camera=GameServices.camera();camera.setXCopy((short)0x140);camera.setYCopy((short)0x200);
        var o=create(0x41,0,0x2B0,0xD4);o.update(0,f.sprite());
        camera.setYCopy((short)0x20F);o.update(1,f.sprite());assertTrue(o.worldCoordinates());assertFalse(o.drawAdmitted());
        camera.setYCopy((short)0x210);o.update(2,f.sprite());assertFalse(o.worldCoordinates());assertTrue(o.drawAdmitted());
        assertEquals(0x1F0,o.getX());assertEquals(0xFF44,o.getY(),"native word projection wraps; no same-dispatch MoveSprite2");
        for(int i=0;i<15;i++)o.update(4+i*2,f.sprite());assertEquals(0xFF44,o.getY());
        o.update(34,f.sprite());assertEquals(0xFF45,o.getY());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        for(int i=0;i<20;i++)o.update(35+i,f.sprite());var expected=registry.capture();registry.restore(saved);same(saved,registry.capture());
        o=GameServices.level().getObjectManager().activeObjectsOfType(EndingIslandLiftObjectInstance.class).getFirst();
        for(int i=0;i<20;i++)o.update(35+i,f.sprite());same(expected,registry.capture());
    }
    @Test void nativeScreenSatMasksCompletedPieceWordsAndReplacesZeroX(){
        assertEquals(0x144,EndingIslandLiftObjectInstance.screenSatY(0xFF44,-8));
        assertEquals(516,EndingIslandLiftObjectInstance.screenSatY(4,-8));
        assertEquals(1,EndingIslandLiftObjectInstance.screenSatX(0,0));
        assertEquals(5,EndingIslandLiftObjectInstance.screenSatX(4,-4));
        assertEquals(0x144,EndingIslandLiftObjectInstance.screenSatX(0xFF44,-8));
    }
    @Test void fixedScrollProjectionHonorsUnsignedHiddenGateAndNativeTerminalDeletion(){
        var f=boot(320);var state=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();var o=create(0x42,0,0x69C,0x384);
        o.update(0,f.sprite());o.update(1,f.sprite());assertFalse(o.drawAdmitted());assertEquals(0x71C,o.getX());assertEquals(0x384,o.getY());
        state.screen().scrollXFixed=0x60C0000;state.screen().scrollYFixed=0x3040000;o.update(2,f.sprite());
        assertEquals(0x110,o.getX());assertEquals(0x100,o.getY());assertTrue(o.drawAdmitted());
        state.screen().scrollXFixed=0x61C0000;state.ending().setFlag(3);o.update(3,f.sprite());
        assertEquals(0x100,o.getX(),"sub_5FE48 still publishes native position on the deletion dispatch");assertTrue(o.isDestroyed());assertFalse(o.drawAdmitted());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void lowestFreeAllocationUsesOnePassTableOrderAndRecreatesTheAdmittedGraph(boolean fragmented) throws Exception {
        var f=boot(320);var manager=GameServices.level().getObjectManager();
        for(var existing:java.util.List.copyOf(manager.getActiveObjects()))if(existing instanceof AbstractObjectInstance a){
            ObjectLifetimeOps.deleteNoRespawn(a);manager.removeDynamicObject(a);}
        if(fragmented){manager.createDynamicObjectAtSlot(()->new EndingAnimalObjectInstance(false,0),5);
            manager.createDynamicObjectAtSlot(()->new EndingAnimalObjectInstance(false,1),8);}
        EndingIslandLiftObjectInstance.createGroup(TestEnvironment.objectServices(),0x5DDD2,0x41,6);
        var graph=manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class);
        assertEquals(6,graph.size());assertArrayEquals(fragmented?new int[]{4,6,7,9,10,11}:new int[]{4,5,6,7,8,9},graph.stream().mapToInt(AbstractObjectInstance::getSlotIndex).sorted().toArray());
        for(int i=0;i<6;i++){assertEquals(0x2B0+32*i,graph.get(i).getX());assertEquals(0xD4,graph.get(i).getY());graph.get(i).update(0,f.sprite());}
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        for(int step=0;step<32;step++)for(var o:graph)o.update(step,f.sprite());var expected=registry.capture();
        registry.restore(saved);same(saved,registry.capture());graph=manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class);
        for(int step=0;step<32;step++)for(var o:graph)o.update(step,f.sprite());same(expected,registry.capture());
    }
    private void same(CompositeSnapshot a,CompositeSnapshot b){assertEquals(a.entries().keySet(),b.entries().keySet());
        for(String key:a.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)).isEmpty(),key);}
}
