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
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.SonicCdAct2PastObjectPass.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual retained pair/Frame/placement, constructed ingress and Ports. No World/route/pixel claim.
 * Capture actor/shared owners BEFORE arming input; expected Init projection is comparison-only.
 * N1ecc5f3 Pata-Bata/Main:7-80; Object Functions:11-46,137-196,327-343.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestSonicCdR12bPataBataPass {
    static CdR12bTerrainAssets terrain;static CdR12bPlayerAssets player;static final int SLOT=45;
    @BeforeAll static void media() throws Exception {
        var root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){terrain=CdR12bTerrainAssets.fromPrograms(programs);player=CdR12bPlayerAssets.fromPrograms(programs);}
    }

    @Test void actualInitWalkUsesBothSourceRootsAndOnlyThenDrainsTheirNativePieces()throws Exception{
        var f=fixture(2,0,0,0x200);f.pata(40,0);f.pata(45,1);var placed=f.placement.capture();var art=f.frame.artState();var clock=f.frame.vIntClock();var pass=f.pass();var body=get(pass,"pataBata",CdPataBata.class);assertSame(field(pass,"math"),body.assets().math());
        pass.runWalk(1);assertEquals(afterInit(f.actorBefore),f.frame.captureActor());assertEquals(f.sharedBefore,f.frame.captureShared());assertEquals(2,f.slot(40).routine());assertEquals(2,f.slot(45).routine());assertEquals(0x20B2E4,f.slot(40).longBits(MAPPINGS));assertEquals(0x20B306,f.slot(45).longBits(MAPPINGS));assertEquals(0x23EA,f.slot(40).word(TILE));assertEquals(0x23EA,f.slot(45).word(TILE));assertEquals(0x06FFC000,f.slot(40).longBits(X_POSITION));assertEquals(0x03FF1FFF,f.slot(40).longBits(Y_POSITION));assertEquals(0x07000000,f.slot(45).longBits(X_POSITION));assertEquals(0x04002FFF,f.slot(45).longBits(Y_POSITION));nonwrites(placed,f,Set.of(40,45));assertTrue(f.ports.cut.completedCpu().isEmpty());
        var cpu=pass.finishDraw(cameras());assertEquals(2,cpu.provenance().stream().filter(p->p.slotRef().wholeIndex()==40).count());assertEquals(2,cpu.provenance().stream().filter(p->p.slotRef().wholeIndex()==45).count());assertSame(cpu,f.ports.cut.completedCpu().orElseThrow());assertSame(art,f.frame.artState());assertSame(clock,f.frame.vIntClock());assertFalse(f.frame.closed());assertTrue(f.ports.cut.sounds().isEmpty());
    }
    @Test void nativeConversionLeavesExplosionAtTheSamePhysicalSlotWithoutCallingItAgain(){
        var f=fixture(2,0,0,0x200);f.pata(SLOT,1);f.ports.cut=good(f.ports.cut,1);var before=f.placement.capture();var pass=f.pass();pass.runWalk(1);byte[] expected=new byte[64];expected[0]=0x18;putWord(expected,8,0x700);putWord(expected,12,0x400);assertArrayEquals(expected,f.slot(SLOT).copyBytes());nonwrites(before,f,Set.of(SLOT));assertTrue(pass.finishDraw(cameras()).provenance().isEmpty());assertTrue(f.ports.cut.sounds().isEmpty());assertFalse(f.frame.closed());
    }
    @Test void originalDrawReceiptCannotBeClonedEscapedOrReplayedAndReentryIsHealthy()throws Exception{
        var f=fixture(2,0,0,0x200);f.pata(SLOT,0);var pass=f.pass();var body=get(pass,"pataBata",CdPataBata.class);var actual=get(body,"drawObject",CdPataBata.DrawObjectCall.class);var ids=new ArrayList<CdObjectState.Identity>();set(body,"drawObject",(CdPataBata.DrawObjectCall)(p,id)->{assertTrue(body.isCurrentDrawCall(p,id));assertFalse(body.isCurrentDrawCall(p,new CdObjectState.Identity(id.wholeIndex())));assertThrows(IllegalStateException.class,()->body.run(SLOT,new CdPataBata.Globals(0,0,0,0x680)));assertThrows(IllegalStateException.class,()->pass.runWalk(1));ids.add(id);actual.drawObject(p,id);});
        pass.runWalk(1);assertEquals(1,ids.size());assertFalse(body.isCurrentDrawCall(f.placement.pool(),ids.getFirst()));assertThrows(IllegalStateException.class,()->actual.drawObject(f.placement.pool(),ids.getFirst()));assertFalse(f.frame.closed());pass.finishDraw(cameras());
        var bad=fixture(2,0,0,0x200);bad.pata(SLOT,0);var failed=bad.pass();var b=get(failed,"pataBata",CdPataBata.class);var draw=get(b,"drawObject",CdPataBata.DrawObjectCall.class);set(b,"drawObject",(CdPataBata.DrawObjectCall)(p,id)->draw.drawObject(p,new CdObjectState.Identity(id.wholeIndex())));var error=assertThrows(CdPataBata.UnsupportedPataBata.class,()->failed.runWalk(1));assertEquals(CdPataBata.UnsupportedBoundary.DRAW_OBJECT,error.boundary());assertTrue(bad.ports.cut.completedCpu().isEmpty());assertFalse(bad.frame.closed());
    }
    @Test void tileFailureAndLaterUnownedKindPreserveExactlyTheirReachedPhysicalPrefixes(){
        var f=fixture(2,0,0,0x200);f.pata(SLOT,0);f.edit(SLOT,s->s.moveByte(SUBTYPE2,255).moveWord(TILE,0xBEEF));var before=f.placement.capture();var pass=f.pass();var error=assertThrows(CdPataBata.UnsupportedPataBata.class,()->pass.runWalk(1));assertEquals(CdPataBata.UnsupportedBoundary.SET_OBJECT_TILE_ID,error.boundary());assertEquals(2,f.slot(SLOT).routine());assertEquals(0xBEEF,f.slot(SLOT).word(TILE));assertEquals(0,f.slot(SLOT).longBits(MAPPINGS));assertEquals(0x2A,f.slot(SLOT).unsignedByte(COLLISION_TYPE));nonwrites(before,f,Set.of(SLOT));assertTrue(f.ports.cut.completedCpu().isEmpty());assertThrows(IllegalStateException.class,()->pass.finishDraw(cameras()));
        var next=fixture(2,0,0,0x200);next.pata(SLOT,0);next.edit(50,s->s.moveByte(ID,0x13));var other=next.pass();var stop=assertThrows(Unowned.class,()->other.runWalk(1));assertEquals(Boundary.OBJECT_KIND,stop.boundary());assertEquals(50,stop.wholeIndex());assertEquals(2,next.slot(SLOT).routine());assertEquals(0x20B2E4,next.slot(SLOT).longBits(MAPPINGS));assertTrue(next.ports.cut.completedCpu().isEmpty());assertFalse(next.frame.closed());
    }
    @Test void fullPoolTamperAndOmittedAdoptionCannotLaunderACompletedDraw()throws Exception{
        var f=fixture(2,0,0,0x200);f.pata(SLOT,0);var pass=f.pass();var body=get(pass,"pataBata",CdPataBata.class);var actual=get(body,"drawObject",CdPataBata.DrawObjectCall.class);set(body,"drawObject",(CdPataBata.DrawObjectCall)(p,id)->{p.write(p.state(46).moveByte(ID,0x12));actual.drawObject(p,id);});assertThrows(CdPataBata.UnsupportedPataBata.class,()->pass.runWalk(1));assertTrue(f.ports.cut.completedCpu().isEmpty());
        var missing=fixture(2,0,0,0x200);missing.pata(SLOT,0);missing.ports.omit=true;var failed=missing.pass();assertThrows(CdPataBata.UnsupportedPataBata.class,()->failed.runWalk(1));assertEquals(2,missing.slot(SLOT).routine());assertTrue(missing.ports.cut.completedCpu().isEmpty());assertFalse(missing.frame.closed());
    }
    static final class TestPorts implements Ports{
        Cut cut;boolean omit;TestPorts(Cut c){cut=c;}public void validate(SonicCdAct2PastObjectPass p,CdPlayerFrameRuntime f,CdR12bTerrainAssets t,CdR12bPlayerAssets a,CdObjectPlacementRuntime r){assertSame(terrain,t);assertSame(player,a);}public Cut read(){return cut;}public void adopt(SonicCdAct2PastObjectPass.Adoption a){assertEquals(cut,a.expected());assertTrue(a.owner().isCurrentAdoption(a));if(omit)return;a.applyNative();cut=a.next();}
    }
    record F(CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement,TestPorts ports,CdPlayerFrameRuntime.ActorState actorBefore,CdPlayerFrameRuntime.SharedState sharedBefore){
        SonicCdAct2PastObjectPass pass(){return new SonicCdAct2PastObjectPass(ports,terrain,player,frame,placement);}CdObjectState slot(int whole){return placement.pool().state(whole);}void edit(int whole,UnaryOperator<CdObjectState> f){placement.pool().write(f.apply(slot(whole)));}void pata(int whole,int subtype){edit(whole,s->s.moveByte(ID,0x3E).moveByte(SUBTYPE,subtype).moveByte(SPRITE_FLAGS,0).moveLong(X_POSITION,0x07004000).moveLong(Y_POSITION,0x04001234));}
    }
    static F fixture(int flags,int stand,int debug,int y){
        var m=new CdPlayerMotionState(0x07001234,(y<<16)|0x5678,-0x123,0x100,0x345,0,0x10,flags,4,9,19,0,0,1,0,0,0,0,0x56,0x78);
        var state=new CdPlayerBody.State(m,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0x230F60,0x780,2,24),new CdPlayerBody.Timers(0,0,0,0x3456),new CdPlayerBody.History(bytes(256,0x35),0),0x12,new CdPlayerBody.ObjectLinks(0,stand),0x34);
        var b=terrain.loadPlan().bounds();var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,0xC,0x80),new CdPlayerMovement.Bounds(b.left(),b.right(),b.bottom(),0),new CdPlayerMovement.Gates(1,0,0,0,0,0,0),new CdPlayerMovement.Globals(0,96,0x12,0x34,0x5678,4,0,0,0,0)),new CdPlayerBody.Controls(0,0,0,0,0),new CdPlayerBody.Globals(0,160,0,debug,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        var frame=CdPlayerFrameRuntime.fromAct2PastEntry(terrain,player,state,env,new CdPlayerArtRuntime.State(new byte[768],new byte[768],0,false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(100),0));
        frame.bindArtPublication((request,value)->fail("Init has no DPLC prime"));var actorBefore=frame.captureActor();var sharedBefore=frame.captureShared();var input=frame.prepareInput(0);frame.serviceVBlank(input.phase());frame.advanceLevelMainFrame();
        // Comparison value only: retain the already-captured owner, read raw serviced references while Body is entitled.
        var expectedShared=new CdPlayerFrameRuntime.SharedState(sharedBefore.owner(),frame.environment(),frame.artState(),frame.paused(),frame.vIntClock());
        var globals=new SonicCdAct2PastWorldHost.Globals(new CdRingGlobals(7,3,0,0,0,0,0,0),CdCheckpointLiveAux.unknown(),new CdPlayerBody.ContactGlobals(0,1,7),new CdOpeningObjectRuntime.ScoreGlobals(0,5000,0),0,CdCheckpointSaveData.State.unknown(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),Optional.empty(),Optional.empty(),Optional.empty(),bytes(7,0),bytes(7,0));
        var cut=new Cut(globals,new CdLevelGlobals(0,0,0,2,0,0,0,0,0),0,OptionalInt.empty(),bytes(31*64,0),bytes(640,0x65),Optional.empty(),List.of(),0x680,0x380,0,OptionalInt.empty());var placement=CdObjectPlacementRuntime.fromAssets(terrain,bytes(CdObjectPlacementRuntime.SAVED_BANK_BYTES,0x35));
        return new F(frame,placement,new TestPorts(cut),actorBefore,expectedShared);
    }
    /** Source Init stores (CdPlayerBody.initialize): routine2,9/19 radii, selected mapping/tile780/prio2/width24,flags4 and normal physics.
     * This fixture already declares every value except routine0; this expected projection is never an input. */
    static CdPlayerFrameRuntime.ActorState afterInit(CdPlayerFrameRuntime.ActorState before){var s=before.state();var m=s.motion();var n=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),2,m.angle(),m.flags(),4,9,19,m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());return new CdPlayerFrameRuntime.ActorState(before.owner(),new CdPlayerBody.State(n,s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle()),before.display());}

    static Cut good(Cut c,int value){return new Cut(c.globals(),c.level(),value,c.goodFutureFlags(),c.reservedSlots(),c.cpuSat(),c.completedCpu(),c.sounds(),c.cameraXWord(),c.cameraYWord(),c.timeAttackModeByte(),c.layerByte());}
    static <T>T get(Object o,String n,Class<T> t)throws Exception{return t.cast(field(o,n));}static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}static void set(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static void nonwrites(CdObjectPlacementRuntime.Snapshot before,F f,Set<Integer> changed){for(var s:before.pool().slots())if(!changed.contains(s.identity().wholeIndex()))assertEquals(s,f.slot(s.identity().wholeIndex()));assertEquals(before.status(),f.placement.status());assertArrayEquals(before.savedFlags().copy(),f.placement.capture().savedFlags().copy());}
    static void putWord(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}static CdAssetBytes bytes(int n,int v){byte[] b=new byte[n];Arrays.fill(b,(byte)v);return CdAssetBytes.copyOf(b);}static CdObjectDrawQueue.DrawCameras cameras(){var c=new CdObjectDrawQueue.CameraWords(0x680,0x380);return new CdObjectDrawQueue.DrawCameras(c,c,c);}
}
