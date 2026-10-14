package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Native ending graph admission under a declared full SST table, not a cold route oracle.
 * Real CreateChild groups supply SST93; literal retail fields and the byte-RAM allocator
 * independently bind partial overwrite. No synthetic owner supplies scene residue.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingNativeGraphPressure {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(String main){
        var c=SonicConfigurationService.getInstance();
        c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,main);
        c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        GameServices.camera().refreshViewportDimensions(c);
        var f=HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
        var game=GameServices.gameState();game.configureSpecialStageProgress(7,7);
        for(int i=0;i<7;i++){game.markEmeraldCollected(i);game.markSuperEmeraldCollected(i);}
        return f;
    }
    private EndingRescueDirectorObjectInstance owner(){return GameServices.level().getObjectManager()
            .activeObjectsOfType(EndingRescueDirectorObjectInstance.class).getFirst();}
    private void until(HeadlessTestFixture f,int routine,boolean lastTimer) throws Exception {
        for(int n=0;n<5000;n++){
            if(owner().routine()==routine&&(!lastTimer||(int)get(owner(),"timer")==0))return;
            f.stepFrame(false,false,false,false,false);
        }
        fail("native phase "+routine+" must admit before the finite cap");
    }
    /** Preserve every current native actor; occupy only holes with actual IslandLift owners.
     * The source's Nth after-parent allocation reaches93, with its native prefix admitted once.
     */
    private void fillLeavingPrefix(int count,List<Integer> requiredFree){
        var manager=GameServices.level().getObjectManager();var free=new ArrayList<Integer>(requiredFree);
        for(int slot=5;slot<93&&free.size()<count-1;slot++)
            if(!free.contains(slot)&&NativeSstSlotAccess.currentOwner(manager,slot)==null)free.add(slot);
        assertEquals(count-1,free.size());
        for(int slot=4;slot<93;slot++)if(!free.contains(slot)&&NativeSstSlotAccess.currentOwner(manager,slot)==null)
            assertNotNull(manager.createDynamicObjectAtSlot(()->new EndingIslandLiftObjectInstance(0x41,0,0x100,0x100),slot));
        assertNull(NativeSstSlotAccess.currentOwner(manager,93));
    }
    @ParameterizedTest @CsvSource({"sonic,4,0,1","sonic,4,2,2","sonic,5,0,3","sonic,3,0,4","sonic,7,0,5",
            "tails,4,0,1","tails,4,2,2","tails,5,0,3","tails,3,0,4","tails,7,0,5",
            "sonic,6,0,1","sonic,8,0,2","tails,6,0,1","tails,8,0,2"})
    void admittedNativeGroupPreservesRealChildResidueAndBothRewindDirections(String main,int role,int subtype,int prefix) throws Exception {
        var f=boot(main);
        if(role==6||role==8){
            until(f,24,false);
            for(int n=0;n<1000&&owner().getX()!=0x220;n++)f.stepFrame(false,false,false,false,false);
            assertEquals(24,owner().routine());assertEquals(0x220,owner().getX());
        }else until(f,20,true);
        var manager=GameServices.level().getObjectManager();
        var oldLarge=manager.activeObjectsOfType(EndingKnucklesFlightChildObjectInstance.class).stream()
                .filter(c->c.role()==3||c.role()==4||c.role()==5).toList();
        fillLeavingPrefix(prefix,List.of());
        f.stepFrame(false,false,false,false,false);
        var victim=assertInstanceOf(EndingKnucklesFlightChildObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,93));
        assertEquals(role,victim.role());assertEquals(subtype,victim.subtype());assertEquals(owner().getSlotIndex(),victim.parentSlot());
        assertFalse(victim.submittedForDraw(),"native initializer returns before Draw_Sprite");
        int frame=switch(role){case 4->(main.equals("tails")?0xD:0xC)+(subtype==0?0:2);
            case 5->4;case 3,6->0x16;case 7->9;case 8->0x11;default->throw new AssertionError(role);};
        int tile=role==8?0x426E:role==6?0x226E:0x826E;
        int priority=role==3||role==6?0x180:0x200;
        var literal=new NativeSstState(0,0,0,frame,0,0,0,0,priority,tile);
        assertEquals(literal,victim.nativeSstState(),"source attributes/copy fields, not measured comparison rows");
        if(role==6||role==8){
            assertEquals(4,oldLarge.size());assertTrue(oldLarge.stream().allMatch(AbstractObjectInstance::isDestroyed),
                    "native flag4 retires these old groups after the same-pass small admission");
            assertEquals(86,manager.getActiveObjectSlotCount());
            // Declare subsequent allocator pressure, without retrying or healing native child admission.
            for(int slot=4;slot<93;slot++)if(NativeSstSlotAccess.currentOwner(manager,slot)==null)
                assertNotNull(manager.createDynamicObjectAtSlot(()->new EndingIslandLiftObjectInstance(0x41,0,0x100,0x100),slot));
        }
        assertEquals(90,manager.getActiveObjectSlotCount(),"partial overwrite is observed at a declared full-table boundary");
        assertReplacementCycles(f,victim,literal);
    }
    @ParameterizedTest @CsvSource({"sonic,12,0x81E3","tails,11,0x826E"})
    void fixedTornadoActuallyAdmitsItsManagedPilotAt93(String main,int frame,int tile) throws Exception {
        var f=boot(main);var manager=GameServices.level().getObjectManager();
        // Obj_5D8BC reserves8/48; plane8 creates catcher first, then pilot. Neither is an inline draw.
        fillLeavingPrefix(4,List.of(8,48,9));
        f.stepFrame(false,false,false,false,false);
        var pilot=assertInstanceOf(EndingKnucklesFlightChildObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,93));
        assertEquals(1,pilot.role());assertEquals(8,pilot.parentSlot());
        var literal=new NativeSstState(0,0,0,frame,0,0,0,0,0x200,tile);
        assertEquals(literal,pilot.nativeSstState());assertReplacementCycles(f,pilot,literal);
    }
    @Test void nativeMasterCatcherAliasReadsPhysicalSuccessorBeforeAndAfterFirstDispatchAndRestoresTwice() throws Exception {
        var f=boot("sonic");var manager=GameServices.level().getObjectManager();
        fillLeavingPrefix(3,List.of(8,48));
        f.stepFrame(false,false,false,false,false);
        var catcher=assertInstanceOf(EndingRescueDirectorObjectInstance.Actor.class,NativeSstSlotAccess.currentOwner(manager,93));
        assertEquals(EndingRescueDirectorObjectInstance.Actor.CATCHER,(int)get(catcher,"role"));
        assertEquals(new NativeSstState(0,0,0,7,0,0,0,0,0x200,0x826E),catcher.nativeSstState());
        var master=assertInstanceOf(EndingRescueDirectorObjectInstance.Actor.class,NativeSstSlotAccess.currentOwner(manager,48));
        int readX=0,readY=0;
        for(int n=0;n<3000&&(int)get(master,"routine")!=6;n++){
            readX=catcher.getX();readY=catcher.getY();f.stepFrame(false,false,false,false,false);
        }
        assertEquals(6,(int)get(master,"routine"));assertEquals(93,(int)get(master,"parentSlot"));
        // Physical48 executes before93: compare the actual value available at the native read boundary.
        assertEquals(readX-0x20,master.getX());assertEquals(readY+1,master.getY());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        var identity=manager.objectQuery().identityOf(catcher).orElseThrow();
        CompositeSnapshot expected=null;
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);same(before,registry.capture());
            var restored=NativeSstSlotAccess.currentOwner(manager,93);assertInstanceOf(EndingRescueDirectorObjectInstance.Actor.class,restored);
            assertEquals(identity,manager.objectQuery().identityOf(restored).orElseThrow());
            EndingIslandLiftObjectInstance.createGroup(TestEnvironment.objectServices(),0x5DBB6,0x42,2);
            var replacement=assertInstanceOf(EndingIslandLiftObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,93));
            var liveMaster=assertInstanceOf(EndingRescueDirectorObjectInstance.Actor.class,NativeSstSlotAccess.currentOwner(manager,48));
            liveMaster.update(2,f.sprite()); // parent3 reads the same physical address even before loc5EDAC.
            assertEquals(0x6BC,liveMaster.getX());assertEquals(0x385,liveMaster.getY());
            replacement.update(2,f.sprite());liveMaster.update(3,f.sprite());
            assertEquals(0x6BC,liveMaster.getX());assertEquals(0x385,liveMaster.getY());
            assertFalse(restored.isDestroyed(),"partial code overwrite is not Go_Delete/onUnload");
            assertEquals(identity,manager.objectQuery().identityOf(replacement).orElseThrow());
            var after=registry.capture();if(expected==null)expected=after;else same(expected,after);
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
            var next=NativeSstSlotAccess.currentOwner(manager,93);assertInstanceOf(EndingIslandLiftObjectInstance.class,next);
            assertEquals(identity,manager.objectQuery().identityOf(next).orElseThrow());
        }
    }
    private void assertReplacementCycles(HeadlessTestFixture f,AbstractObjectInstance victim,NativeSstState literal) throws Exception {
        var manager=GameServices.level().getObjectManager();var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        var identity=manager.objectQuery().identityOf(victim).orElseThrow();var before=registry.capture();
        var oracle=new ContinueSpriteRamOracle(TestEnvironment.objectServices().romReader());
        for(int slot=4;slot<=93;slot++){byte[] raw=new byte[0x4A];ContinueSpriteRamOracle.put32(raw,0,1);oracle.putSlot(slot,raw);}
        byte[] raw=rawHeader(literal);oracle.putSlot(93,raw);oracle.group(0x5DBB6,0x42,2);
        var retained=readHeader(oracle.slot(93));assertEquals(literal,retained);
        CompositeSnapshot initialAfter=null,initialForward=null;
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);same(before,registry.capture());
            var old=NativeSstSlotAccess.currentOwner(manager,93);assertEquals(victim.getClass(),old.getClass());
            assertEquals(identity,manager.objectQuery().identityOf(old).orElseThrow());
            EndingIslandLiftObjectInstance.createGroup(TestEnvironment.objectServices(),0x5DBB6,0x42,2);
            var replacement=assertInstanceOf(EndingIslandLiftObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,93));
            assertEquals(retained,replacement.nativeSstState());assertEquals(0x6DC,replacement.getX());assertEquals(0x384,replacement.getY());
            assertFalse(old.isDestroyed());assertEquals(identity,manager.objectQuery().identityOf(replacement).orElseThrow());
            var after=registry.capture();if(initialAfter==null)initialAfter=after;else same(initialAfter,after);
            for(int n=0;n<20;n++)f.stepFrame(false,false,false,false,false);
            var forward=registry.capture();if(initialForward==null)initialForward=forward;else same(initialForward,forward);
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
            for(int n=0;n<20;n++)f.stepFrame(false,false,false,false,false);
            same(forward,registry.capture());
        }
    }
    private static byte[] rawHeader(NativeSstState s){
        byte[] raw=new byte[0x4A];ContinueSpriteRamOracle.put32(raw,0,1);raw[4]=(byte)s.renderFlags();
        ContinueSpriteRamOracle.put16(raw,8,s.priority());ContinueSpriteRamOracle.put16(raw,0xA,s.artTile());
        ContinueSpriteRamOracle.put16(raw,0x12,s.xFraction());ContinueSpriteRamOracle.put16(raw,0x16,s.yFraction());
        raw[0x22]=(byte)s.mappingFrame();raw[0x23]=(byte)s.animationFrame();raw[0x24]=(byte)s.animationTimer();
        raw[0x38]=(byte)s.flags38();raw[0x2A]=(byte)s.status();return raw;
    }
    private static NativeSstState readHeader(byte[] b){return new NativeSstState(b[4]&255,ContinueSpriteRamOracle.u16(b,0x12),
            ContinueSpriteRamOracle.u16(b,0x16),b[0x22]&255,b[0x23]&255,b[0x24]&255,b[0x38]&255,b[0x2A]&255,
            ContinueSpriteRamOracle.u16(b,8),ContinueSpriteRamOracle.u16(b,0xA));}
    private static Object get(Object object,String name) throws Exception {var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void same(CompositeSnapshot a,CompositeSnapshot b){assertEquals(a.entries().keySet(),b.entries().keySet());
        for(String key:a.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)).isEmpty(),key);}
}
