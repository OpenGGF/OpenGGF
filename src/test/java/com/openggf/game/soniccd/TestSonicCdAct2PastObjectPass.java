package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.SonicCdAct2PastObjectPass.*;

/** Actual detached media, constructed explicit component ingress/Ports. No World/route/pixel proof. */
class TestSonicCdAct2PastObjectPass {
    private static CdR12bTerrainAssets terrain, otherTerrain;
    private static CdR12bPlayerAssets player;
    @BeforeAll static void media() throws Exception {
        String root=System.getProperty("soniccd.media.root"); if(root==null||root.isBlank())return;
        Path cue;try(var paths=Files.list(Path.of(root).resolve("USA"))){
            var found=paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,found.size());cue=found.getFirst();
        }
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(source);otherTerrain=CdR12bTerrainAssets.fromPrograms(source);
            player=CdR12bPlayerAssets.fromPrograms(source);
        }
    }
    @Test void nullIngressRejectsBeforeAnyBody(){
        assertThrows(NullPointerException.class,()->new SonicCdAct2PastObjectPass(null,null,null,null,null));
    }
    @Test void foreignPlacementAndEqualMetadataFramePairNeverAuthenticate(){
        actual();var f=fixture(false);
        assertThrows(IllegalArgumentException.class,()->new SonicCdAct2PastObjectPass(f.ports,otherTerrain,player,f.frame,f.placement));
        var constructed=new CdObjectPlacementRuntime(terrain.placements());
        assertThrows(IllegalArgumentException.class,()->new SonicCdAct2PastObjectPass(f.ports,terrain,player,f.frame,constructed));
        var foreign=CdObjectPlacementRuntime.fromAssets(otherTerrain,bytes(CdObjectPlacementRuntime.SAVED_BANK_BYTES,0));
        assertThrows(IllegalArgumentException.class,()->new SonicCdAct2PastObjectPass(f.ports,terrain,player,f.frame,foreign));
        assertEquals(0,f.frame.state().motion().routine());assertTrue(f.ports.receipts.isEmpty());
    }
    @Test void actualFirstInitReservedBodiesAndCompleteCpuDrainPreserveArtClockAndUnusedRawTail(){
        actual();var f=fixture(true);var art=f.frame.artState();var clock=f.frame.vIntClock();
        var pass=f.pass();pass.runWalk(1);
        assertEquals(2,f.frame.state().motion().routine());assertEquals(0x230F60,f.frame.state().header().mappingAddress());
        assertEquals(0x07001234,f.frame.state().motion().centreX16_16());assertEquals(0x04205678,f.frame.state().motion().centreY16_16());
        assertSame(art,f.frame.artState());assertSame(clock,f.frame.vIntClock());assertTrue(f.frame.display().isEmpty());
        assertEquals(2,f.slot(2).unsignedByte(0x24));assertEquals(2,f.slot(3).unsignedByte(0x24));
        assertEquals(2,f.slot(5).unsignedByte(0x24));assertEquals(2,f.slot(31).unsignedByte(0x24));
        assertTrue(f.ports.cut.completedCpu().isEmpty());
        var cpu=pass.finishDraw(cameras());assertSame(cpu,f.ports.cut.completedCpu().orElseThrow());
        assertArrayEquals(cpu.bytes(),f.ports.cut.cpuSat().copy());assertTrue(cpu.spriteCount()>0);assertTrue(cpu.spriteCount()<80);
        for(int at=cpu.spriteCount()*8+4;at<640;at++)assertEquals(0x65,Byte.toUnsignedInt(cpu.bytes()[at]),"retained unused raw SAT byte "+at);
        assertTrue(cpu.provenance().stream().noneMatch(p->p.slotRef().wholeIndex()==0));
        assertSame(art,f.frame.artState());assertSame(clock,f.frame.vIntClock());assertFalse(f.frame.closed());
        assertThrows(IllegalStateException.class,()->pass.finishDraw(cameras()));pass.discard();
    }
    @Test void sourceBackedObjNullOnlyClearsIdForEveryAliasAndKeepsOther63Bytes(){
        actual();var f=fixture(false);int[] ids={0x1D,0x1E,0x25,0x27,0x2A,0x2B,0x2C,0x2F,0x30,0x31,0x37,0x38,0x39};
        var before=new ArrayList<byte[]>();for(int i=0;i<ids.length;i++){
            assertEquals(0x203D4A,terrain.objects().view(CdR12bObjectAssets.View.OBJECT_INDEX).unsignedLong((ids[i]-1)*4));
            var s=f.placement.pool().state(32+i).moveByte(ID,ids[i]).moveByte(SUBTYPE,0x63).moveWord(X,0x3456).moveWord(Y_FRACTION,0x789A);
            f.placement.pool().write(s);before.add(s.copyBytes());
        }
        var pass=f.pass();pass.runWalk(1);
        for(int i=0;i<ids.length;i++){var expected=before.get(i);expected[0]=0;assertArrayEquals(expected,f.placement.pool().state(32+i).copyBytes());}
        assertTrue(pass.finishDraw(cameras()).provenance().isEmpty());
    }
    @Test void ringExpansionIntoLaterPhysicalSlotsExecutesChildrenThisPassWithLivePointers(){
        actual();var f=fixture(false);var pool=f.placement.pool();
        pool.write(pool.state(32).moveByte(ID,0x10).moveByte(SUBTYPE,2).moveWord(X,0x700).moveWord(Y,0x420));
        var pass=f.pass();pass.runWalk(1);
        assertEquals(2,pool.state(32).routine());assertEquals(2,pool.state(33).routine());assertEquals(2,pool.state(34).routine());
        var cpu=pass.finishDraw(cameras());var refs=cpu.provenance().stream().map(p->p.slotRef().wholeIndex()).toList();
        assertTrue(refs.contains(32));assertTrue(refs.contains(33));assertTrue(refs.contains(34));
    }
    @Test void allocationsIntoAlreadyVisitedSlotsWaitForNextPass(){
        actual();var f=fixture(false);var pool=f.placement.pool();
        pool.write(pool.state(40).moveByte(ID,0x29).moveByte(SUBTYPE,1).moveWord(X,0x700).moveWord(Y,0x420));
        var pass=f.pass();pass.runWalk(1);
        assertEquals(0x29,pool.state(32).id());assertEquals(0,pool.state(32).routine());
        var cpu=pass.finishDraw(cameras());assertTrue(cpu.provenance().stream().noneMatch(p->p.slotRef().wholeIndex()==32));
    }
    @Test void unownedLaterIdKeepsCompletedPrimaryAndEarlierSlotPrefixWithoutPublishingCpu(){
        actual();var f=fixture(false);var pool=f.placement.pool();
        pool.write(pool.state(32).moveByte(ID,0x1D).moveWord(X_FRACTION,0xBEEF));pool.write(pool.state(33).moveByte(ID,0x18));
        var pass=f.pass();var stop=assertThrows(Unowned.class,()->pass.runWalk(1));
        assertEquals(Boundary.OBJECT_KIND,stop.boundary());assertEquals(33,stop.wholeIndex());
        assertEquals(2,f.frame.state().motion().routine());assertEquals(0,pool.state(32).id());assertEquals(0xBEEF,pool.state(32).word(X_FRACTION));
        assertEquals(0x18,pool.state(33).id());assertTrue(f.ports.cut.completedCpu().isEmpty());
        assertThrows(IllegalStateException.class,()->pass.finishDraw(cameras()));assertFalse(f.frame.closed());
    }
    @Test void nextSonicMainUsesActualTouchSupportAndTheOriginalCompletedExternalPublication(){
        actual();var f=fixture(false);var pass=f.pass();pass.runWalk(1);pass.finishDraw(cameras());
        var sample=f.frame.prepareInput(0);f.frame.serviceVBlank(sample.phase());f.frame.advanceLevelMainFrame();
        var next=f.pass();var before=f.frame.state();var clock=f.frame.vIntClock();var art=f.frame.artState();
        assertDoesNotThrow(()->next.runWalk(2));assertFalse(f.frame.closed());assertEquals(2,f.frame.state().motion().routine());
        assertEquals((before.history().index()+4)&255,f.frame.state().history().index(),"Source RecordPos common tail runs once");
        assertSame(clock,f.frame.vIntClock());assertSame(art,f.frame.artState());assertEquals(2,f.frame.environment().globals().levelFrames());
        assertNotNull(next.finishDraw(cameras()));
    }
    @Test void escapedOrRepeatedOpaqueAdoptionCannotReplayNativeEffects(){
        actual();var f=fixture(true);var pass=f.pass();pass.runWalk(1);assertFalse(f.ports.receipts.isEmpty());
        var receipt=f.ports.receipts.getFirst();assertFalse(pass.isCurrentAdoption(receipt));
        var actor=f.frame.captureActor();var placement=f.placement.capture();
        assertThrows(IllegalStateException.class,receipt::applyNative);assertEquals(actor,f.frame.captureActor());
        assertEquals(placement.pool().slots(),f.placement.capture().pool().slots());assertFalse(f.frame.closed());
    }
    @Test void caughtRecursiveWalkAndFinishAreRejectedBeforeAnyNestedMutation(){
        actual();var f=fixture(true);f.ports.reentry=true;var pass=f.pass();f.ports.pass=pass;
        pass.runWalk(1);assertTrue(f.ports.rejections>0);pass.finishDraw(cameras());assertFalse(f.frame.closed());
    }
    @Test void portsCannotClaimSuccessfulAdoptionWithoutActualNativeReceipt(){
        actual();var f=fixture(true);f.ports.omit=true;var pass=f.pass();
        assertThrows(IllegalStateException.class,()->pass.runWalk(1));assertTrue(f.ports.cut.completedCpu().isEmpty());
    }
    @Test void discardedTransientPassHasNoSnapshotOrNewNativeStateOwner(){
        actual();var f=fixture(false);var pass=f.pass();var a=f.frame.state();var e=f.frame.environment();
        var display=f.frame.display();var art=f.frame.artState();var clock=f.frame.vIntClock();var s=f.placement.capture();pass.discard();
        assertThrows(IllegalStateException.class,()->pass.runWalk(1));assertSame(a,f.frame.state());assertSame(e,f.frame.environment());
        assertSame(display,f.frame.display());assertSame(art,f.frame.artState());assertSame(clock,f.frame.vIntClock());
        assertEquals(s.pool().slots(),f.placement.capture().pool().slots());
        assertTrue(Arrays.stream(SonicCdAct2PastObjectPass.class.getDeclaredMethods()).noneMatch(m->m.getName().equals("capture")||m.getName().equals("restore")));
    }
    @Test void wholePoolCasFailsBeforeFirstNativeAdoptionAssignment(){
        actual();var f=fixture(true);var pool=f.placement.pool();var pass=f.pass();
        f.ports.beforeApply=()->pool.write(pool.state(127).moveByte(ID,0x1D).moveWord(X_FRACTION,0xABCD));
        assertThrows(IllegalArgumentException.class,()->pass.runWalk(1));
        assertEquals(0,f.slot(2).unsignedByte(0x24),"HUD Init store has not been adopted");
        assertEquals(0xABCD,pool.state(127).word(X_FRACTION));assertEquals(0x1D,pool.state(127).id());
        assertEquals(2,f.frame.state().motion().routine(),"completed primary prefix remains");assertTrue(f.ports.receipts.isEmpty());
    }
    @Test void authenticatedComponentsDoNotReplaceActualWorldLifetimeAuthority(){
        actual();var f=fixture(false);var pass=f.pass();f.ports.valid=false;var before=f.frame.state();var env=f.frame.environment();
        assertThrows(IllegalStateException.class,()->pass.runWalk(1));assertSame(before,f.frame.state());assertSame(env,f.frame.environment());
        assertTrue(f.placement.pool().capture().slots().stream().allMatch(s->s.id()==0));assertTrue(f.ports.receipts.isEmpty());assertFalse(f.frame.closed());
    }
    private static final class TestPorts implements Ports {
        Cut cut;boolean valid=true,omit,reentry;int rejections;SonicCdAct2PastObjectPass pass;
        Runnable beforeApply;final List<SonicCdAct2PastObjectPass.Adoption> receipts=new ArrayList<>();
        TestPorts(Cut cut){this.cut=cut;}
        public void validate(SonicCdAct2PastObjectPass p,CdPlayerFrameRuntime f,CdR12bTerrainAssets t,CdR12bPlayerAssets a,CdObjectPlacementRuntime r){if(!valid)throw new IllegalStateException("constructed closed visit");}
        public Cut read(){return cut;}
        public void adopt(SonicCdAct2PastObjectPass.Adoption a){
            assertEquals(cut,a.expected());assertTrue(a.owner().isCurrentAdoption(a));
            if(reentry){assertThrows(IllegalStateException.class,()->pass.runWalk(1));assertThrows(IllegalStateException.class,()->pass.finishDraw(cameras()));rejections++;}
            if(omit)return;if(beforeApply!=null){var action=beforeApply;beforeApply=null;action.run();}a.applyNative();cut=a.next();receipts.add(a);
        }
    }
    private record Fixture(CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement,TestPorts ports){
        SonicCdAct2PastObjectPass pass(){return new SonicCdAct2PastObjectPass(ports,terrain,player,frame,placement);}
        CdAssetBytes slot(int whole){return ports.cut.reservedSlots().slice((whole-1)*64,64);}
    }
    private static Fixture fixture(boolean reserved){
        var m=new CdPlayerMotionState(0x07001234,0x04205678,-0x123,0x234,-0x345,0,0,0x42,0,0,0,0,0,0,0,0,0,0,0,0);
        var state=new CdPlayerBody.State(m,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0,0,0,0),new CdPlayerBody.Timers(0x78,0,0,0),new CdPlayerBody.History(bytes(256,0),0),0,new CdPlayerBody.ObjectLinks(0,0),0);
        var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0,0,0),new CdPlayerMovement.Bounds(0x10,0x2000,0x700,0),new CdPlayerMovement.Gates(1,0,0,0,0,0,0),new CdPlayerMovement.Globals(0,0x60,0,0,0,0,0x12,0x34,0x56,0x78)),new CdPlayerBody.Controls(0,0,0,0,1),new CdPlayerBody.Globals(0,160,0,0,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        var frame=CdPlayerFrameRuntime.fromAct2PastEntry(terrain,player,state,env,new CdPlayerArtRuntime.State(new byte[768],new byte[768],0,false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(100),0));
        frame.bindArtPublication((request,value)->fail("Init has no DPLC prime"));var sample=frame.prepareInput(0);frame.serviceVBlank(sample.phase());frame.advanceLevelMainFrame();
        byte[] bank=new byte[31*64];if(reserved){for(int id:new int[]{2,3,5})bank[(id-1)*64]=0x1C;bank[30*64]=0x19;bank[30*64+0x28]=10;}
        var globals=new SonicCdAct2PastWorldHost.Globals(new CdRingGlobals(7,3,0,0,0,0,0,0),CdCheckpointLiveAux.unknown(),new CdPlayerBody.ContactGlobals(0,0,0),new CdOpeningObjectRuntime.ScoreGlobals(0,5000,0),0,CdCheckpointSaveData.State.unknown(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),Optional.empty(),Optional.empty(),Optional.empty(),bytes(7,0),bytes(7,0));
        var cut=new Cut(globals,new CdLevelGlobals(0,0,0,2,0,0,0,0,0),1,OptionalInt.empty(),CdAssetBytes.copyOf(bank),bytes(640,0x65),Optional.empty(),List.of(),0x680,0x380,0,OptionalInt.empty());
        return new Fixture(frame,CdObjectPlacementRuntime.fromAssets(terrain,bytes(CdObjectPlacementRuntime.SAVED_BANK_BYTES,0)),new TestPorts(cut));
    }
    private static CdObjectDrawQueue.DrawCameras cameras(){var c=new CdObjectDrawQueue.CameraWords(0x680,0x380);return new CdObjectDrawQueue.DrawCameras(c,c,c);}
    private static CdAssetBytes bytes(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
    private static void actual(){assumeTrue(terrain!=null,"configured original USA media required");}
}
