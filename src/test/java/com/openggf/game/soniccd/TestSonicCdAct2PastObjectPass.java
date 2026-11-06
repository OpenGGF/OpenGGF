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
    @Test void actualBoulderInitUsesTheOriginalBRootAndItsTwoLivePiecesWithoutOtherSlotWrites(){
        actual();var f=fixture(false);var pool=f.placement.pool();
        pool.write(pool.state(65).moveByte(ID,0x1B).moveWord(X,0x700).moveWord(Y,0x420).moveWord(X_FRACTION,0xBEEF));
        var before=pool.capture();var pass=f.pass();pass.runWalk(1);var b=pool.state(65);
        assertEquals(2,b.routine());assertEquals(0x20940A,b.longBits(CdObjectState.LongField.MAPPINGS));assertEquals(0x035E,b.word(TILE));
        assertEquals(0xBEEF,b.word(X_FRACTION));assertEquals(0,f.frame.state().objectLinks().standObject());
        for(int whole=32;whole<128;whole++)if(whole!=65)assertEquals(before.slots().get(whole-32),pool.state(whole));
        var cpu=pass.finishDraw(cameras());assertEquals(2,cpu.provenance().stream().filter(piece->piece.slotRef().wholeIndex()==65).count());
        assertEquals(List.of(0,1),cpu.provenance().stream().filter(piece->piece.slotRef().wholeIndex()==65).map(CdObjectDrawQueue.Provenance::mappingPieceOrdinal).toList());
        assertTrue((pool.state(65).spriteFlags()&128)!=0);assertTrue(f.ports.cut.sounds().isEmpty());assertFalse(f.frame.closed());
    }
    @Test void actualBoulderPlainSolidPublishesTopLandingToTheSameFrameBeforeItsDeferredDraw(){
        actual();var f=fixture(false,0x03FD5678);var pool=f.placement.pool();
        pool.write(pool.state(65).moveByte(ID,0x1B).moveByte(SPRITE_FLAGS,0x84).moveWord(X,0x700).moveWord(Y,0x420));
        var pass=f.pass();pass.runWalk(1);
        assertEquals(65,f.frame.state().objectLinks().standObject(),"Genuine plain Solid must adopt its standing-slot write into Frame");
        assertEquals(8,f.frame.state().motion().flags()&10);assertEquals(0,f.frame.state().motion().yVelocity());
        assertEquals(1,f.frame.environment().globals().levelFrames());assertEquals(0,f.frame.state().history().index(),"Init has no fabricated Main/history work");
        var cpu=pass.finishDraw(cameras());assertEquals(2,cpu.provenance().stream().filter(piece->piece.slotRef().wholeIndex()==65).count());
        assertFalse(f.frame.closed());
    }
    @Test void actualWallInitSelectsTheReachedBRootAndPublishesRealSolidBeforeItsDeferredDraw(){
        actual();var f=fixture(false);var pool=f.placement.pool();
        pool.write(pool.state(69).moveByte(ID,0x36).moveWord(X,0x740).moveWord(Y,0x420));
        var pass=f.pass();pass.runWalk(1);var wall=pool.state(69);
        assertEquals(2,wall.routine());assertEquals(0x20EE64,wall.longBits(CdObjectState.LongField.MAPPINGS));
        assertEquals(0x44BE,wall.word(TILE));assertEquals(0xEF,wall.unsignedByte(COLLISION_TYPE));
        assertEquals(-0x123,f.frame.state().motion().xVelocity());assertEquals(0x234,f.frame.state().motion().yVelocity());
        assertTrue(f.ports.cut.sounds().isEmpty());assertFalse(f.ports.receipts.isEmpty());
        var cpu=pass.finishDraw(cameras());assertTrue(cpu.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==69));
        assertFalse(f.frame.closed());
    }
    @Test void actualWallBreakPublishesFmThenSignedHalfVelocityAndEarlierChildrenWaitForNextPass(){
        actual();var f=fixture(false);var pool=f.placement.pool();
        pool.write(pool.state(69).moveByte(ID,0x36).moveByte(COLLISION_STATUS,1).moveWord(X,0x740).moveWord(Y,0x420));
        var pass=f.pass();pass.runWalk(1);
        assertEquals(-146,f.frame.state().motion().xVelocity());assertEquals(0x234,f.frame.state().motion().yVelocity());
        assertEquals(0x07001234,f.frame.state().motion().centreX16_16());assertEquals(0x04205678,f.frame.state().motion().centreY16_16());
        assertEquals(List.of(new SonicCdGameModule.PastWallFm(0xB0)),f.ports.cut.sounds());
        assertEquals(4,pool.state(69).routine());
        // PieceSpeeds row0 replaces the earlier saved XV word at the same2A storage.
        assertEquals(0x0002F684,pool.state(69).longBits(CdObjectState.LongField.TAMABBOH_MISSILE_X_DELTA));
        for(int whole=32;whole<=36;whole++){assertEquals(0x36,pool.state(whole).id());assertEquals(4,pool.state(whole).routine());}
        assertEquals(6,pool.capture().slots().stream().filter(s->s.id()==0x36).count());
        var cpu=pass.finishDraw(cameras());assertTrue(cpu.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==69));
        assertTrue(cpu.provenance().stream().noneMatch(p->p.slotRef().wholeIndex()>=32&&p.slotRef().wholeIndex()<=36));
        assertEquals(0,f.frame.state().history().index());assertFalse(f.frame.closed());
    }
    @Test void wallFragmentsAllocatedAfterAnEarlyParentRunTheirOwnFallInTheSamePhysicalPass(){
        actual();var f=fixture(false);var pool=f.placement.pool();
        pool.write(pool.state(32).moveByte(ID,0x36).moveByte(COLLISION_STATUS,1).moveWord(X,0x740).moveWord(Y,0x420));
        var pass=f.pass();pass.runWalk(1);
        // Main.asm PieceSpeeds row1 has Y delta0; its later physical Fall adds4000 before integration.
        assertEquals(0x4000,pool.state(33).longBits(CdObjectState.LongField.TAMABBOH_MISSILE_Y_DELTA));
        assertEquals(0x04204000,pool.state(33).longBits(CdObjectState.LongField.Y_POSITION));
        assertEquals(0x73B,pool.state(33).centreX());assertEquals(9,pool.state(33).unsignedByte(MAP_FRAME));
        var cpu=pass.finishDraw(cameras());
        for(int whole=32;whole<=37;whole++){int selected=whole;assertEquals(4,pool.state(whole).routine());assertTrue(cpu.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==selected));}
        assertEquals(List.of(new SonicCdGameModule.PastWallFm(0xB0)),f.ports.cut.sounds());assertFalse(f.frame.closed());
    }
    @Test void wallFmPublicationFaultRetainsOnlyItsNativeInitAndSavedVelocityPrefix(){
        actual();var f=fixture(false);var pool=f.placement.pool();
        pool.write(pool.state(69).moveByte(ID,0x36).moveByte(COLLISION_STATUS,1).moveWord(X,0x740).moveWord(Y,0x420));
        var original=new IllegalStateException("constructed Wall FM owner fault");f.ports.beforeApply=()->{throw original;};
        var pass=f.pass();assertSame(original,assertThrows(IllegalStateException.class,()->pass.runWalk(1)));
        assertEquals(4,pool.state(69).routine());assertEquals(-0x123,(short)pool.state(69).word(TITLE_CARD_DEST_X));
        assertEquals(-0x123,f.frame.state().motion().xVelocity());assertTrue(f.ports.cut.sounds().isEmpty());
        assertEquals(1,pool.capture().slots().stream().filter(s->s.id()!=0).count());assertFalse(f.frame.closed());
    }
    @Test void wallHalfPublicationFaultPreservesTheEarlierFmAndNeverAllocatesFragments(){
        actual();var f=fixture(false);var pool=f.placement.pool();
        pool.write(pool.state(69).moveByte(ID,0x36).moveByte(COLLISION_STATUS,1).moveWord(X,0x740).moveWord(Y,0x420));
        var original=new IllegalStateException("constructed Wall half owner fault");
        f.ports.beforeApply=()->f.ports.beforeApply=()->{throw original;};
        var pass=f.pass();assertSame(original,assertThrows(IllegalStateException.class,()->pass.runWalk(1)));
        assertEquals(-0x123,f.frame.state().motion().xVelocity());assertEquals(List.of(new SonicCdGameModule.PastWallFm(0xB0)),f.ports.cut.sounds());
        assertEquals(4,pool.state(69).routine());assertEquals(1,pool.capture().slots().stream().filter(s->s.id()!=0).count());
        assertFalse(f.frame.closed());
    }
    @Test void wallFallDeletionCompletesWithTheActualClearedSourceAndNoDrawReceipt(){
        actual();var f=fixture(false);var pool=f.placement.pool();
        pool.write(pool.state(69).moveByte(ID,0x36).moveByte(ROUTINE,4).moveWord(X,0x740).moveWord(Y,0x600));
        var pass=f.pass();pass.runWalk(1);assertArrayEquals(new byte[64],pool.state(69).copyBytes());
        assertTrue(pass.finishDraw(cameras()).provenance().stream().noneMatch(p->p.slotRef().wholeIndex()==69));
        assertTrue(f.ports.cut.sounds().isEmpty());assertEquals(-0x123,f.frame.state().motion().xVelocity());assertFalse(f.frame.closed());
    }
    @Test void equalWallSolidRecordCannotBorrowTheActualProducerPublication() throws Exception{
        actual();var f=fixture(false);var pool=f.placement.pool();pool.write(pool.state(69).moveByte(ID,0x36).moveWord(X,0x740).moveWord(Y,0x420));
        var pass=f.pass();var field=SonicCdAct2PastObjectPass.class.getDeclaredField("wall");field.setAccessible(true);var body=(CdBreakableWall)field.get(pass);
        var port=CdBreakableWall.class.getDeclaredField("solidCommit");port.setAccessible(true);var original=(CdBreakableWall.SolidCommit)port.get(body);
        port.set(body,(CdBreakableWall.SolidCommit)c->original.commit(new CdBreakableWall.SolidCall(c.cut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome())));
        assertThrows(IllegalStateException.class,()->pass.runWalk(1));assertEquals(2,pool.state(69).routine());
        assertEquals(-0x123,f.frame.state().motion().xVelocity());assertTrue(f.ports.cut.sounds().isEmpty());assertFalse(f.frame.closed());
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
    private static Fixture fixture(boolean reserved){return fixture(reserved,0x04205678);}
    private static Fixture fixture(boolean reserved,int centreY16_16){
        var m=new CdPlayerMotionState(0x07001234,centreY16_16,-0x123,0x234,-0x345,0,0,0x42,0,0,0,0,0,0,0,0,0,0,0,0);
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
