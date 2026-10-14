package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.NativeSstState;
import com.openggf.level.objects.NativeSstStateSource;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Full native allocator pressure: independent raw bytes, retained residue and same-slot execution. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingContinuationPressure {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(){
        var c=SonicConfigurationService.getInstance();c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        GameServices.camera().refreshViewportDimensions(c);
        var f=HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
        for(var o:List.copyOf(GameServices.level().getObjectManager().getActiveObjects()))if(o instanceof AbstractObjectInstance a){
            ObjectLifetimeOps.deleteNoRespawn(a);GameServices.level().getObjectManager().removeDynamicObject(a);}
        return f;
    }
    @Test void literalRetailOpcodesOverwriteOnlyTheFiveOwningFieldsAtGlobal93() throws Exception {
        boot();var oracle=new ContinueSpriteRamOracle(TestEnvironment.objectServices().romReader());
        for(int slot=4;slot<=93;slot++){
            byte[] bytes=new byte[0x4A];Arrays.fill(bytes,(byte)(slot+0x40));oracle.putSlot(slot,bytes);
        }
        byte[] before=oracle.slot(93),before92=oracle.slot(92);assertEquals(0xCAE2,oracle.allocate());
        oracle.group(0x5DBB6,0x42,2);byte[] after=oracle.slot(93);
        assertEquals(0x5EDAC,ContinueSpriteRamOracle.u32(after,0));assertEquals(0x42,after[0x2C]&255);
        assertEquals(4,after[0x2D]&255);assertEquals(0x6DC,ContinueSpriteRamOracle.u16(after,0x10));
        assertEquals(0x384,ContinueSpriteRamOracle.u16(after,0x14));
        for(int at=0;at<0x4A;at++)if(!(at<4||at==0x10||at==0x11||at==0x14||at==0x15||at==0x2C||at==0x2D))
            assertEquals(before[at],after[at],"unchecked helper retains byte "+at);
        assertArrayEquals(before92,oracle.slot(92),"the overflow never walks backwards");
    }
    @Test void fullManagerTableKeepsNativeLastSlotAndItsFractionAnimationResidue() throws Exception {
        var f=boot();var manager=GameServices.level().getObjectManager();
        for(int slot=4;slot<=93;slot++)assertNotNull(manager.createDynamicObjectAtSlot(
                ()->new EndingIslandLiftObjectInstance(0x41,0,0x100,0x100),slot));
        var old=manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class).getLast();
        old.update(0,f.sprite());
        set(old,"xFixed",0x100ABCD);set(old,"yFixed",0x1001234);
        var animation=(S3kRawAnimation.State)get(old,"animation");animation.animFrame=3;animation.animFrameTimer=7;animation.mappingFrame=6;
        EndingIslandLiftObjectInstance.createGroup(TestEnvironment.objectServices(),0x5DBB6,0x42,2);
        var last=manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class).stream().filter(o->o.getSlotIndex()==93).findFirst().orElseThrow();
        assertEquals(0x42,last.getSpawn().subtype(),"full-table AllocateObject retains A1 at SST93");
        assertEquals(90,manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class).size());
        assertEquals(0x6DCABCD,(int)get(last,"xFixed"));assertEquals(0x3841234,(int)get(last,"yFixed"));
        assertEquals(3,((S3kRawAnimation.State)get(last,"animation")).animFrame);
        last.update(2,f.sprite());assertEquals(4,last.mappingFrame());assertTrue(last.worldCoordinates(),"subtype42 does not clear retained render_flags bit2");
        assertEquals(4,((S3kRawAnimation.State)get(last,"animation")).animFrameTimer);
        last.update(3,f.sprite());assertEquals(3,((S3kRawAnimation.State)get(last,"animation")).animFrameTimer);
    }
    @ParameterizedTest @ValueSource(ints={0x45,0x46,0x47})
    void rawFrameAndCursorSurviveLateFamilyInitialization(int subtype) throws Exception {
        var f=boot();var manager=GameServices.level().getObjectManager();
        for(int slot=4;slot<=93;slot++)assertNotNull(manager.createDynamicObjectAtSlot(PressureOccupant::new,slot));
        EndingIslandLiftObjectInstance.createGroup(TestEnvironment.objectServices(),0x5DE02,subtype,2);
        var last=manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class).getFirst();
        last.update(2,f.sprite());
        assertEquals(6,last.mappingFrame(),"loc5EE86 does not write mapping22");
        assertEquals(3,last.nativeSstState().animationFrame());assertEquals(0xC,last.nativeSstState().animationTimer());
        assertEquals(7,last.nativeSstState().renderFlags());assertEquals(0x81,last.nativeSstState().status());
        assertEquals(0x62FF,last.nativeSstState().artTile());assertEquals(0x300,last.nativeSstState().priority());
    }
    @ParameterizedTest @ValueSource(ints={4,109})
    void uncheckedReplacementDoesNotUnloadAndDispatchesAtItsOwningCursor(int callerSlot) throws Exception {
        var f=boot();var manager=GameServices.level().getObjectManager();
        for(int slot=4;slot<=93;slot++)assertNotNull(manager.createDynamicObjectAtSlot(PressureOccupant::new,slot));
        var old=(PressureOccupant)manager.getActiveObjects().stream().filter(o->((AbstractObjectInstance)o).getSlotIndex()==93).findFirst().orElseThrow();
        var identity=manager.objectQuery().identityOf(old).orElseThrow();
        assertSame(old,NativeSstSlotAccess.currentOwner(manager,93));
        if(callerSlot==4){var first=manager.getActiveObjects().stream().filter(o->((AbstractObjectInstance)o).getSlotIndex()==4).findFirst().orElseThrow();manager.removeDynamicObject(first);}
        if(callerSlot==4)assertNotNull(manager.createDynamicObjectAtSlot(PressureWriter::new,callerSlot));
        else manager.addDynamicObjectAtSlot(new PressureWriter(),callerSlot);
        manager.update(0,f.sprite(),List.of(),2);
        var last=manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class).getFirst();
        assertEquals(0,old.unloads,"an unchecked code write is not a Delete/Unload call");
        assertSame(last,NativeSstSlotAccess.currentOwner(manager,93));
        assertEquals(0x81,last.nativeSstState().status(),"loc5EDAC does not clear prior parent status");
        assertEquals(callerSlot==4?0x300:0x280,last.nativeSstState().priority());
        assertEquals(callerSlot==4?0xE2FF:0xC52E,last.nativeSstState().artTile());
        assertEquals(3,last.nativeSstState().renderFlags(),"low flip bits survive both helper and initializer");
        assertEquals(identity,manager.objectQuery().identityOf(last).orElseThrow(),"numerical SST links survive code replacement");
        assertEquals(callerSlot==4?0:1,old.updates,"the old code executes only if its slot precedes the writer");
        assertEquals(callerSlot==4?4:6,last.mappingFrame(),"a later SST initializes in this pass; an earlier SST retains old mapping until next");
        manager.update(0,f.sprite(),List.of(),3);
        assertEquals(4,last.mappingFrame());assertEquals(0,old.unloads);
        assertEquals(90,manager.getActiveObjectSlotCount());
        assertNull(manager.createDynamicObject(PressureOccupant::new),"checked callers continue to reject a full table");
    }
    @Test void oneEarlierHoleIsAdmittedOnceAndEveryRemainingEntryOverwritesTheFinalSlot() throws Exception {
        boot();var manager=GameServices.level().getObjectManager();var oracle=new ContinueSpriteRamOracle(TestEnvironment.objectServices().romReader());
        for(int slot=4;slot<=93;slot++)if(slot!=50){
            assertNotNull(manager.createDynamicObjectAtSlot(PressureOccupant::new,slot));
            byte[] raw=new byte[0x4A];ContinueSpriteRamOracle.put32(raw,0,1);oracle.putSlot(slot,raw);
        }
        oracle.group(0x5DDD2,0x41,6);
        EndingIslandLiftObjectInstance.createGroup(TestEnvironment.objectServices(),0x5DDD2,0x41,6);
        var graph=manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class);assertEquals(2,graph.size());
        assertEquals(50,graph.get(0).getSlotIndex());assertEquals(93,graph.get(1).getSlotIndex());
        for(var owner:graph){byte[] expected=oracle.slot(owner.getSlotIndex());assertEquals(ContinueSpriteRamOracle.u16(expected,0x10),owner.getX());
            assertEquals(ContinueSpriteRamOracle.u16(expected,0x14),owner.getY());assertEquals(expected[0x2D]&255,(int)get(owner,"nativeOrdinalOffset"));}
        assertEquals(90,manager.getActiveObjectSlotCount());
    }
    @Test void realNativeCloudResidueAndSstIdentityRestoreInBothDirectionsWithWholeWorldReplay() throws Exception {
        var f=boot();var manager=GameServices.level().getObjectManager();
        var emitter=manager.createDynamicObjectAtSlot(EndingCloudObjectInstance::new,4);assertNotNull(emitter);
        for(int slot=5;slot<=92;slot++)assertNotNull(manager.createDynamicObjectAtSlot(
                ()->new EndingIslandLiftObjectInstance(0x41,0,0x100,0x100),slot));
        emitter.update(0,f.sprite());
        var zone=(com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState)GameServices.zoneRuntimeState();
        zone.ending().clouds().configure(0x1F,0x100,0x100);emitter.update(32,f.sprite());
        var cloud=manager.activeObjectsOfType(EndingCloudObjectInstance.class).stream().filter(o->!o.emitter()).findFirst().orElseThrow();
        assertEquals(93,cloud.getSlotIndex());cloud.update(33,f.sprite());
        var residue=cloud.nativeSstState();assertTrue(residue.xFraction()!=0||residue.yFraction()!=0,"real cloud motion supplies fractional RAM");
        var identity=manager.objectQuery().identityOf(cloud).orElseThrow();
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        EndingIslandLiftObjectInstance.createGroup(TestEnvironment.objectServices(),0x5DDD2,0x41,6);
        var replacement=manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class).getLast();
        assertEquals(93,replacement.getSlotIndex());assertEquals(0x350,replacement.getX());assertEquals(0xD4,replacement.getY());
        assertEquals(residue,replacement.nativeSstState());assertEquals(identity,manager.objectQuery().identityOf(replacement).orElseThrow());
        assertSame(replacement,NativeSstSlotAccess.currentOwner(manager,93));
        var replaced=registry.capture();
        for(int i=0;i<30;i++)manager.update(0,f.sprite(),List.of(),34+i);
        var expected=registry.capture();registry.restore(before);same(before,registry.capture());
        assertEquals(93,manager.activeObjectsOfType(EndingCloudObjectInstance.class).stream().filter(o->!o.emitter()).findFirst().orElseThrow().getSlotIndex());
        assertInstanceOf(EndingCloudObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,93));
        registry.restore(replaced);same(replaced,registry.capture());
        assertInstanceOf(EndingIslandLiftObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,93));
        for(int i=0;i<30;i++)manager.update(0,f.sprite(),List.of(),34+i);
        same(expected,registry.capture());registry.restore(before);same(before,registry.capture());
        EndingIslandLiftObjectInstance.createGroup(TestEnvironment.objectServices(),0x5DDD2,0x41,6);
        same(replaced,registry.capture());
    }
    @Test void nativeDirectorStatusRetirementDoesNotInventControlBitOne() throws Exception {
        boot();var rom=TestEnvironment.objectServices().romReader();
        int[] words={0x08E8,0x0007,0x002A}; // loc5DC02: BSET #7,status(a0), not byte38.
        for(int i=0;i<words.length;i++)assertEquals(words[i],rom.readU16BE(0x5DC16+i*2));
        var director=GameServices.level().getObjectManager().createDynamicObject(EndingRescueDirectorObjectInstance::new);
        assertNotNull(director);var field=director.getClass().getDeclaredField("retired");field.setAccessible(true);field.setBoolean(director,true);
        assertEquals(0x80,director.nativeSstState().status());assertEquals(0,director.nativeSstState().flags38()&2);
    }
    @Test void realPaletteFlashHasOnlyNativeTimerAndStepWritesBeforeReplacement() throws Exception {
        var f=boot();var manager=GameServices.level().getObjectManager();
        for(int slot=4;slot<=92;slot++)assertNotNull(manager.createDynamicObjectAtSlot(
                ()->new EndingIslandLiftObjectInstance(0x41,0,0x100,0x100),slot));
        var flash=manager.createDynamicObjectAtSlot(EndingKnucklesPaletteFlashObjectInstance::new,93);
        assertNotNull(flash);flash.update(2,f.sprite());assertEquals(NativeSstState.CLEARED,flash.nativeSstState());
        var identity=manager.objectQuery().identityOf(flash).orElseThrow();
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        EndingIslandLiftObjectInstance.createGroup(TestEnvironment.objectServices(),0x5DDD2,0x41,6);
        var after=registry.capture();assertEquals(NativeSstState.CLEARED,((NativeSstStateSource)NativeSstSlotAccess.currentOwner(manager,93)).nativeSstState());
        assertEquals(identity,manager.objectQuery().identityOf(NativeSstSlotAccess.currentOwner(manager,93)).orElseThrow());
        registry.restore(before);same(before,registry.capture());assertInstanceOf(EndingKnucklesPaletteFlashObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,93));
        registry.restore(after);same(after,registry.capture());assertInstanceOf(EndingIslandLiftObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,93));
    }
    private static void same(CompositeSnapshot a,CompositeSnapshot b){
        assertEquals(a.entries().keySet(),b.entries().keySet());
        for(String key:a.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)).isEmpty(),key);
    }
    /** Declared pressure RAM for the manager contract, not a native-scene acceptance substitute. */
    private static class PressureOccupant extends AbstractObjectInstance implements SpawnRewindRecreatable,NativeSstStateSource {
        private int updates,unloads;
        PressureOccupant(){this(new ObjectSpawn(0x100,0x100,1,0,0,false,0));}
        private PressureOccupant(ObjectSpawn spawn){super(spawn,"PressureOccupant");}
        @Override public NativeSstState nativeSstState(){return new NativeSstState(3,0xABCD,0x1234,6,3,7,0,0x81,0x280,0xC52E);}
        @Override public void update(int vIntRunCount,PlayableEntity player){updates++;}
        @Override public void onUnload(){unloads++;}
        @Override public boolean isPersistent(){return true;}
        @Override public void appendRenderCommands(List<GLCommand> commands){}
    }
    private static final class PressureWriter extends PressureOccupant {
        private boolean issued;
        PressureWriter(){this(new ObjectSpawn(0,0,1,0,0,false,0));}
        private PressureWriter(ObjectSpawn spawn){super(spawn);}
        @Override public void update(int vIntRunCount,PlayableEntity player){
            if(!issued){issued=true;EndingIslandLiftObjectInstance.createGroup(services(),0x5DBB6,0x42,2);}
        }
    }
    private static Object get(Object object,String name) throws Exception {var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void set(Object object,String name,int value) throws Exception {var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.setInt(object,value);}
}
