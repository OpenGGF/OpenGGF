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

/** Genuine retained original-USA assets/Frame/shared Solid, explicit component ingress.
 * The actual Sonic Init precedes dynamic objects in this physical walk. These are component/owner-port
 * tests, not a World, Spawn, ordinary Main, native-execution, route or pixel claim. No comparison data supplies state.
 * N1ecc5f3a common Spikes/Main.asm:19-83; Palmtree Panic/Objects/Log Shadow/Main.asm:7-70.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestSonicCdR12bSpikesLogPass {
    static CdR12bTerrainAssets terrain;static CdR12bPlayerAssets player;
    static final int SPIKE=40,LOG=45;
    @BeforeAll static void media() throws Exception {
        var root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){terrain=CdR12bTerrainAssets.fromPrograms(programs);player=CdR12bPlayerAssets.fromPrograms(programs);}
    }
    @Test void bothActualInitBodiesKeepFull64NonwritesAndDrainTheirRealMappingPieces(){
        var f=fixture(2,0,0,0x200);f.spike(0);f.log(0);var placed=f.placement.capture();var actor=afterInit(f.actorBeforeBody());var shared=f.sharedBeforeBody();var art=f.frame.artState();var clock=f.frame.vIntClock();var expectedSpike=spikeInit(f.slot(SPIKE));var expectedLog=logInit(f.slot(LOG));
        var pass=f.pass();pass.runWalk(1);expectedSpike[1]&=0x7F;expectedLog[1]&=0x7F;assertArrayEquals(expectedSpike,f.slot(SPIKE).copyBytes());assertArrayEquals(expectedLog,f.slot(LOG).copyBytes());assertEquals(actor,f.frame.captureActor());assertEquals(shared,f.frame.captureShared());nonwrites(placed,f,Set.of(SPIKE,LOG));
        var cpu=pass.finishDraw(cameras());assertEquals(4,cpu.provenance().stream().filter(p->p.slotRef().wholeIndex()==SPIKE).count());assertEquals(1,cpu.provenance().stream().filter(p->p.slotRef().wholeIndex()==LOG).count());assertSame(cpu,f.ports.cut.completedCpu().orElseThrow());assertSame(art,f.frame.artState());assertSame(clock,f.frame.vIntClock());assertTrue(f.ports.cut.sounds().isEmpty());assertFalse(f.frame.closed());
    }
    @Test void genuineActualSolidLandingAdoptsTheSameFrameBeforeVflipDraw() throws Exception {
        var f=fixture(2,0,0,0x405);f.spike(2);f.edit(SPIKE,s->s.moveByte(SPRITE_FLAGS,0x82));var pass=f.pass();var body=body(pass);var calls=new ArrayList<CdSpikes.PrefixSolidCall>();var original=get(body,"prefixSolidCommit",CdSpikes.PrefixSolidCommit.class);set(body,"prefixSolidCommit",(CdSpikes.PrefixSolidCommit)c->{calls.add(c);original.commit(c);});
        pass.runWalk(1);assertEquals(1,calls.size());var result=assertInstanceOf(CdSolidObject.Completed.class,calls.getFirst().outcome()).completedResult();assertEquals(CdSolidObject.Path.STAND_ON,result.path());assertEquals(OptionalInt.of(1),result.d0());assertEquals(SPIKE,f.frame.state().objectLinks().standObject());assertEquals(0,f.frame.state().motion().yVelocity());assertTrue((f.slot(SPIKE).flags()&8)!=0);assertEquals(0x83,f.slot(SPIKE).unsignedByte(COLLISION_TYPE));assertTrue(f.ports.cut.sounds().isEmpty());pass.finishDraw(cameras());
    }
    @Test void directYAndHurtUseTheOriginalSolidFrameAndFreshActualPoolCut() throws Exception {
        var f=fixture(8,SPIKE,0,0x405);f.spikeMain();var pass=f.pass();var body=body(pass);var order=new ArrayList<String>();var solids=new ArrayList<CdSpikes.PrefixSolidCall>();var ys=new ArrayList<CdSpikes.YRewindCall>();var hurts=new ArrayList<CdSpikes.HurtCall>();
        var solid=get(body,"prefixSolidCommit",CdSpikes.PrefixSolidCommit.class);set(body,"prefixSolidCommit",(CdSpikes.PrefixSolidCommit)c->{order.add("solid");solids.add(c);solid.commit(c);});
        var y=get(body,"yRewindCommit",CdSpikes.YRewindCommit.class);set(body,"yRewindCommit",(CdSpikes.YRewindCommit)c->{order.add("y");ys.add(c);y.commit(c);});var hurt=get(body,"hurtCommit",CdSpikes.HurtCommit.class);set(body,"hurtCommit",(CdSpikes.HurtCommit)c->{order.add("hurt");hurts.add(c);hurt.commit(c);});
        var draw=get(body,"drawObject",CdSpikes.DrawObjectCall.class);set(body,"drawObject",(CdSpikes.DrawObjectCall)(p,id)->{order.add("draw");draw.drawObject(p,id);});var before=f.placement.capture();var clock=f.frame.vIntClock();pass.runWalk(1);
        assertEquals(List.of("solid","y","hurt","draw"),order);var result=assertInstanceOf(CdSolidObject.Completed.class,solids.getFirst().outcome()).completedResult();assertEquals(result.player().actor(),ys.getFirst().expectedActor());assertEquals(ys.getFirst().finalYBits(),hurts.getFirst().expectedActor().motion().centreY16_16());assertNotSame(solids.getFirst().actualCut(),hurts.getFirst().callCut());assertEquals(hurts.getFirst().result().actor().motion(),f.frame.state().motion());assertEquals(4,f.frame.state().motion().routine());assertEquals(0x78,f.frame.state().timers().hurt());assertEquals(0,f.ports.cut.globals().contact().shield());assertEquals(hurts.getFirst().result().player().combineRing(),f.ports.cut.globals().contact().combineRing());assertEquals(0,f.frame.environment().movement().globals().scoreChain());assertSame(clock,f.frame.vIntClock());nonwrites(before,f,Set.of(SPIKE));pass.finishDraw(cameras());
    }
    @Test void actualStoppedSolidCommitsOnlyItsPrefixThenRethrowsAndClosesThePass() throws Exception {
        var f=fixture(8,1,0,0x405);f.spikeMain();f.edit(SPIKE,s->s.moveByte(SOLID_TYPE,2));var before=f.placement.capture();var pass=f.pass();var body=body(pass);var calls=new ArrayList<CdSpikes.PrefixSolidCall>();var original=get(body,"prefixSolidCommit",CdSpikes.PrefixSolidCommit.class);set(body,"prefixSolidCommit",(CdSpikes.PrefixSolidCommit)c->{calls.add(c);original.commit(c);});
        var error=assertThrows(CdSolidObject.UnsupportedSolidObject.class,()->pass.runWalk(1));assertEquals(1,calls.size());var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,calls.getFirst().outcome());assertSame(error,stopped.stop().originalException());assertEquals(CdSolidObject.StopSite.STAND_RELEASE,stopped.stop().site());assertEquals(stopped.prefix().object(),f.slot(SPIKE));assertEquals(stopped.prefix().player().actor().motion(),f.frame.state().motion());assertFalse(body.isCurrentPrefixSolidCall(calls.getFirst()));assertTrue(f.ports.cut.completedCpu().isEmpty());assertThrows(IllegalStateException.class,()->pass.finishDraw(cameras()));assertFalse(f.frame.closed());nonwrites(before,f,Set.of(SPIKE));
    }
    @Test void secondaryFaultRetainsOnlyNativeInitStoresAndNeverPublishesFrameOrCpu(){
        var f=fixture(2,0,0,0x200);f.spike(0);f.edit(SPIKE,s->s.moveByte(SUBTYPE2,1));var before=f.placement.capture();var actor=afterInit(f.actorBeforeBody());var bytes=f.slot(SPIKE).copyBytes();bytes[0x24]+=2;putLong(bytes,4,0x20BFA6);bytes[1]|=4;bytes[0x18]=3;var pass=f.pass();assertEquals(CdSpikes.UnsupportedBoundary.TILE_ID_SUBTYPE2,assertThrows(CdSpikes.UnsupportedSpikes.class,()->pass.runWalk(1)).boundary());assertArrayEquals(bytes,f.slot(SPIKE).copyBytes());assertEquals(actor,f.frame.captureActor());assertTrue(f.ports.receipts.isEmpty());nonwrites(before,f,Set.of(SPIKE));
    }
    @Test void allFourOriginalSpikesReceiptsRejectEqualClonesAndRetireAfterReturn() throws Exception {
        var f=fixture(8,SPIKE,0,0x405);f.spikeMain();var pass=f.pass();var body=body(pass);var observed=new ArrayList<Object>();
        var solid=get(body,"prefixSolidCommit",CdSpikes.PrefixSolidCommit.class);set(body,"prefixSolidCommit",(CdSpikes.PrefixSolidCommit)c->{assertTrue(body.isCurrentPrefixSolidCall(c));assertFalse(body.isCurrentPrefixSolidCall(new CdSpikes.PrefixSolidCall(c.actualCut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome())));observed.add(c);solid.commit(c);});
        var y=get(body,"yRewindCommit",CdSpikes.YRewindCommit.class);set(body,"yRewindCommit",(CdSpikes.YRewindCommit)c->{assertTrue(body.isCurrentYRewindCall(c));assertFalse(body.isCurrentYRewindCall(new CdSpikes.YRewindCall(c.sourceIdentity(),c.expectedActor(),c.finalYBits())));observed.add(c);y.commit(c);});
        var hurt=get(body,"hurtCommit",CdSpikes.HurtCommit.class);set(body,"hurtCommit",(CdSpikes.HurtCommit)c->{assertTrue(body.isCurrentHurtCall(c));assertFalse(body.isCurrentHurtCall(new CdSpikes.HurtCall(c.callCut(),c.sourceA2(),c.expectedActor(),c.expectedPlayer(),c.expectedWorld(),c.result())));observed.add(c);hurt.commit(c);});
        var draw=get(body,"drawObject",CdSpikes.DrawObjectCall.class);set(body,"drawObject",(CdSpikes.DrawObjectCall)(p,id)->{assertTrue(body.isCurrentDrawCall(p,id));assertFalse(body.isCurrentDrawCall(p,new CdObjectState.Identity(id.wholeIndex())));observed.add(id);draw.drawObject(p,id);});pass.runWalk(1);assertEquals(4,observed.size());assertFalse(body.isCurrentPrefixSolidCall((CdSpikes.PrefixSolidCall)observed.get(0)));assertFalse(body.isCurrentYRewindCall((CdSpikes.YRewindCall)observed.get(1)));assertFalse(body.isCurrentHurtCall((CdSpikes.HurtCall)observed.get(2)));assertFalse(body.isCurrentDrawCall(f.placement.pool(),(CdObjectState.Identity)observed.get(3)));pass.finishDraw(cameras());
    }
    @Test void replacingEachSpikesCallbackWithAnEqualRecordCannotBorrowItsPrivateOperation() throws Exception {
        for(String selected:List.of("solid","y","hurt","draw")){
            var f=fixture(8,SPIKE,0,0x405);f.spikeMain();var pass=f.pass();var body=body(pass);
            switch(selected){
                case "solid"->{var old=get(body,"prefixSolidCommit",CdSpikes.PrefixSolidCommit.class);set(body,"prefixSolidCommit",(CdSpikes.PrefixSolidCommit)c->old.commit(new CdSpikes.PrefixSolidCall(c.actualCut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome())));}
                case "y"->{var old=get(body,"yRewindCommit",CdSpikes.YRewindCommit.class);set(body,"yRewindCommit",(CdSpikes.YRewindCommit)c->old.commit(new CdSpikes.YRewindCall(c.sourceIdentity(),c.expectedActor(),c.finalYBits())));}
                case "hurt"->{var old=get(body,"hurtCommit",CdSpikes.HurtCommit.class);set(body,"hurtCommit",(CdSpikes.HurtCommit)c->old.commit(new CdSpikes.HurtCall(c.callCut(),c.sourceA2(),c.expectedActor(),c.expectedPlayer(),c.expectedWorld(),c.result())));}
                case "draw"->{var old=get(body,"drawObject",CdSpikes.DrawObjectCall.class);set(body,"drawObject",(CdSpikes.DrawObjectCall)(p,id)->old.drawObject(p,new CdObjectState.Identity(id.wholeIndex())));}
            }
            assertThrows(IllegalStateException.class,()->pass.runWalk(1),selected);assertNull(field(body,"pendingOperation"));assertFalse(f.frame.closed());assertTrue(f.ports.cut.completedCpu().isEmpty());assertThrows(IllegalStateException.class,()->pass.finishDraw(cameras()));
        }
    }
    @Test void thrownSpikesCallbackRetiresEveryPendingReceiptAndKeepsEarlierNativeCuts() throws Exception {
        for(String selected:List.of("solid","y","hurt","draw")){
            var f=fixture(8,SPIKE,0,0x405);f.spikeMain();var pass=f.pass();var body=body(pass);var fault=new Fault();
            switch(selected){case "solid"->set(body,"prefixSolidCommit",(CdSpikes.PrefixSolidCommit)c->{throw fault;});case "y"->set(body,"yRewindCommit",(CdSpikes.YRewindCommit)c->{throw fault;});case "hurt"->set(body,"hurtCommit",(CdSpikes.HurtCommit)c->{throw fault;});case "draw"->set(body,"drawObject",(CdSpikes.DrawObjectCall)(p,id)->{throw fault;});}
            assertSame(fault,assertThrows(Fault.class,()->pass.runWalk(1)));assertNull(field(body,"pendingOperation"));assertNull(field(body,"pendingDrawCut"));assertEquals(-1,field(body,"runningWhole"));assertTrue(f.ports.cut.completedCpu().isEmpty());assertFalse(f.frame.closed());
            if(selected.equals("draw")){assertEquals(4,f.frame.state().motion().routine());assertEquals(0,f.ports.cut.globals().contact().shield());}else assertEquals(2,f.frame.state().motion().routine());
        }
    }
    @Test void malformedPoolCasRejectsTheFirstSolidAdoptionBeforeFrameAndPoolOutputs(){
        var f=fixture(2,0,0,0x405);f.spike(0);f.edit(SPIKE,s->s.moveByte(SOLID_TYPE,0xA5));var actor=afterInit(f.actorBeforeBody());var pass=f.pass();f.ports.before=adoption->{f.edit(90,s->s.moveByte(SUBTYPE,0x77));};assertThrows(IllegalArgumentException.class,()->pass.runWalk(1));assertEquals(actor,f.frame.captureActor());assertEquals(0xA5,f.slot(SPIKE).unsignedByte(SOLID_TYPE));assertEquals(0x77,f.slot(90).subtype());assertTrue(f.ports.cut.completedCpu().isEmpty());
    }
    @Test void logOriginalStoreAndDrawReceiptsRejectCopiesAndCheckAllNinetySixSlots() throws Exception {
        var f=fixture(2,0,0,0x200);f.log(0);var before=f.placement.capture();var pass=f.pass();var body=get(pass,"logShadow",CdLogShadow.class);var old=get(body,"observer",CdLogShadow.DispatchObserver.class);var seen=new ArrayList<Object>();
        set(body,"observer",new CdLogShadow.DispatchObserver(){public void beforePublication(CdLogShadow.Publication c){assertTrue(body.isCurrentCall(c));assertFalse(body.isCurrentCall(new CdLogShadow.Publication(c.ordinal(),c.cut(),c.source(),c.output(),c.savedIndex())));seen.add(c);old.beforePublication(c);}public void afterPublication(CdLogShadow.Publication c){old.afterPublication(c);}public void beforeCall(Object c){var d=(CdLogShadow.DrawCall)c;assertTrue(body.isCurrentCall(c));assertFalse(body.isCurrentCall(new CdLogShadow.DrawCall(d.cut(),d.source())));seen.add(c);old.beforeCall(c);}public void afterCall(Object c){old.afterCall(c);}});
        pass.runWalk(1);assertEquals(4,seen.size());seen.forEach(c->assertFalse(body.isCurrentCall(c)));nonwrites(before,f,Set.of(LOG));pass.finishDraw(cameras());
    }
    @Test void equalLogPublicationAndDrawCannotGrantOriginalReceiptAuthority() throws Exception {
        for(boolean publication:List.of(true,false)){
            var f=fixture(2,0,0,0x200);f.log(0);var pass=f.pass();var body=get(pass,"logShadow",CdLogShadow.class);
            if(publication){var old=get(body,"observer",CdLogShadow.DispatchObserver.class);set(body,"observer",new CdLogShadow.DispatchObserver(){public void beforePublication(CdLogShadow.Publication c){old.beforePublication(new CdLogShadow.Publication(c.ordinal(),c.cut(),c.source(),c.output(),c.savedIndex()));}public void afterPublication(CdLogShadow.Publication c){old.afterPublication(c);}public void beforeCall(Object c){old.beforeCall(c);}public void afterCall(Object c){old.afterCall(c);}});}else{var old=get(body,"sourceDraw",CdLogShadow.SourceDraw.class);set(body,"sourceDraw",(CdLogShadow.SourceDraw)c->old.draw(new CdLogShadow.DrawCall(c.cut(),c.source())));}
            assertThrows(IllegalStateException.class,()->pass.runWalk(1));assertTrue(f.ports.cut.completedCpu().isEmpty());assertFalse(f.frame.closed());
        }
    }
    @Test void logOffscreenTailClearsOnlyItsActualSavedBitThenAllSixtyFourSourceBytes(){
        var f=fixture(2,0,0,0x200);f.log(0);f.edit(LOG,s->s.moveWord(X,0x4000).moveByte(SAVED_FLAGS_ID,7));f.placement.bsetSavedFlag(7,0,CdObjectPlacementRuntime.SavedBit.LOADED);var before=f.placement.capture();var pass=f.pass();pass.runWalk(1);assertArrayEquals(new byte[64],f.slot(LOG).copyBytes());byte[] bank=before.savedFlags().copy();bank[23]&=0x7F;assertArrayEquals(bank,f.placement.capture().savedFlags().copy());assertEquals(before.status(),f.placement.status());for(var s:before.pool().slots())if(s.identity().wholeIndex()!=LOG)assertEquals(s,f.slot(s.identity().wholeIndex()));assertTrue(pass.finishDraw(cameras()).provenance().isEmpty());
    }
    @Test void logDrawOwnerFaultRetainsTheThreeNativeInitStoresAndNoCpu(){
        var f=fixture(2,0,0,0x200);f.log(0);byte[] expected=logInit(f.slot(LOG));var fault=new Fault();var pass=f.pass();f.ports.before=adoption->{throw fault;};assertSame(fault,assertThrows(Fault.class,()->pass.runWalk(1)));assertArrayEquals(expected,f.slot(LOG).copyBytes());assertEquals(0,f.ports.cut.sounds().size());assertTrue(f.ports.cut.completedCpu().isEmpty());assertFalse(f.frame.closed());
    }
    @Test void laterUnownedPhysicalIdKeepsBothCompletedPrefixesAndCannotDrainCpu(){
        var f=fixture(2,0,0,0x200);f.spike(0);f.log(0);f.edit(50,s->s.moveByte(ID,0x13));var pass=f.pass();var error=assertThrows(Unowned.class,()->pass.runWalk(1));assertEquals(Boundary.OBJECT_KIND,error.boundary());assertEquals(50,error.wholeIndex());assertEquals(2,f.slot(SPIKE).routine());assertEquals(2,f.slot(LOG).routine());assertTrue(f.ports.cut.completedCpu().isEmpty());assertThrows(IllegalStateException.class,()->pass.finishDraw(cameras()));
    }
    @Test void recursiveWalkAndNoopAdoptionCannotConsumeOriginalOperationsTwice(){
        var f=fixture(2,0,0,0x200);f.log(0);var pass=f.pass();f.ports.before=a->{assertThrows(IllegalStateException.class,()->pass.runWalk(1));assertThrows(IllegalStateException.class,()->pass.finishDraw(cameras()));};pass.runWalk(1);assertEquals(1,f.ports.receipts.size());pass.finishDraw(cameras());
        var missing=fixture(2,0,0,0x200);missing.log(0);missing.ports.omit=true;var failed=missing.pass();assertThrows(IllegalStateException.class,()->failed.runWalk(1));assertTrue(missing.ports.cut.completedCpu().isEmpty());
    }
    static final class TestPorts implements Ports {
        Cut cut;boolean omit;Consumer<SonicCdAct2PastObjectPass.Adoption> before=a->{};final List<SonicCdAct2PastObjectPass.Adoption> receipts=new ArrayList<>();TestPorts(Cut c){cut=c;}
        public void validate(SonicCdAct2PastObjectPass p,CdPlayerFrameRuntime f,CdR12bTerrainAssets t,CdR12bPlayerAssets a,CdObjectPlacementRuntime r){assertSame(terrain,t);assertSame(player,a);}
        public Cut read(){return cut;}public void adopt(SonicCdAct2PastObjectPass.Adoption a){assertEquals(cut,a.expected());assertTrue(a.owner().isCurrentAdoption(a));before.accept(a);if(omit)return;a.applyNative();cut=a.next();receipts.add(a);}
    }
    record F(CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement,TestPorts ports,
             CdPlayerFrameRuntime.ActorState actorBeforeBody,CdPlayerFrameRuntime.SharedState sharedBeforeBody){
        SonicCdAct2PastObjectPass pass(){return new SonicCdAct2PastObjectPass(ports,terrain,player,frame,placement);}CdObjectState slot(int whole){return placement.pool().state(whole);}void edit(int whole,UnaryOperator<CdObjectState> op){placement.pool().write(op.apply(slot(whole)));}
        void spike(int flip){edit(SPIKE,s->s.moveByte(ID,0x26).moveByte(ROUTINE,0).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0).moveByte(SPRITE_FLAGS,0x40|flip).moveWord(X,0x700).moveWord(Y,0x420));}
        void spikeMain(){spike(0);edit(SPIKE,s->s.moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,8).moveByte(SOLID_TYPE,0).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveByte(PRIORITY,3).moveWord(TILE,0x330).moveLong(MAPPINGS,0x20BFA6).moveByte(MAP_FRAME,0));}
        void log(int routine){edit(LOG,s->s.moveByte(ID,0x33).moveByte(ROUTINE,routine).moveByte(SAVED_FLAGS_ID,0).moveByte(SUBTYPE,1).moveByte(SPRITE_FLAGS,0xC3).moveWord(X,0x740).moveWord(Y,0x420));}
    }
    static F fixture(int flags,int stand,int debug,int y){
        var m=new CdPlayerMotionState(0x07001234,(y<<16)|0x5678,-0x123,0x100,0x345,0,0x10,flags,4,9,19,0,0,1,0,0,0,0,0x56,0x78);
        var state=new CdPlayerBody.State(m,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0x230F60,0x780,2,24),new CdPlayerBody.Timers(0,0,0,0x3456),new CdPlayerBody.History(bytes(256,0x35),0),0x12,new CdPlayerBody.ObjectLinks(0,stand),0x34);
        var b=terrain.loadPlan().bounds();var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,0xC,0x80),new CdPlayerMovement.Bounds(b.left(),b.right(),b.bottom(),0),new CdPlayerMovement.Gates(1,0,0,0,0,0,0),new CdPlayerMovement.Globals(0,96,0x12,0x34,0x5678,4,0,0,0,0)),new CdPlayerBody.Controls(0,0,0,0,0),new CdPlayerBody.Globals(0,160,0,debug,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        var frame=CdPlayerFrameRuntime.fromAct2PastEntry(terrain,player,state,env,new CdPlayerArtRuntime.State(new byte[768],new byte[768],0,false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(100),0));
        var closedActor=frame.captureActor();var closedShared=frame.captureShared();
        frame.bindArtPublication((request,value)->fail("Init has no DPLC prime"));var input=frame.prepareInput(0);frame.serviceVBlank(input.phase());frame.advanceLevelMainFrame();
        var globals=new SonicCdAct2PastWorldHost.Globals(new CdRingGlobals(7,3,0,0,0,0,0,0),CdCheckpointLiveAux.unknown(),new CdPlayerBody.ContactGlobals(0,1,7),new CdOpeningObjectRuntime.ScoreGlobals(0,5000,0),0,CdCheckpointSaveData.State.unknown(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),Optional.empty(),Optional.empty(),Optional.empty(),bytes(7,0),bytes(7,0));
        var cut=new Cut(globals,new CdLevelGlobals(0,0,0,2,0,0,0,0,0),1,OptionalInt.empty(),bytes(31*64,0),bytes(640,0x65),Optional.empty(),List.of(),0x680,0x380,0,OptionalInt.empty());var placement=CdObjectPlacementRuntime.fromAssets(terrain,bytes(CdObjectPlacementRuntime.SAVED_BANK_BYTES,0x35));
        // Expected comparison values only. Never capture a pending Frame, restore or publish these values.
        var expectedActor=new CdPlayerFrameRuntime.ActorState(closedActor.owner(),frame.state(),closedActor.display());
        var expectedShared=new CdPlayerFrameRuntime.SharedState(closedShared.owner(),frame.environment(),frame.artState(),frame.paused(),frame.vIntClock());
        return new F(frame,placement,new TestPorts(cut),expectedActor,expectedShared);
    }
    /** Source Init stores (CdPlayerBody.initialize): routine2,9/19 radii, selected mapping/tile780/prio2/width24,flags4 and normal physics.
     * This fixture already declares every value except routine0; this expected projection is never an input. */
    static CdPlayerFrameRuntime.ActorState afterInit(CdPlayerFrameRuntime.ActorState before){var s=before.state();var m=s.motion();var n=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),2,m.angle(),m.flags(),4,9,19,m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());return new CdPlayerFrameRuntime.ActorState(before.owner(),new CdPlayerBody.State(n,s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle()),before.display());}
    static CdSpikes body(SonicCdAct2PastObjectPass p)throws Exception{return get(p,"spikes",CdSpikes.class);}static <T>T get(Object o,String n,Class<T> t)throws Exception{return t.cast(field(o,n));}static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}static void set(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static void nonwrites(CdObjectPlacementRuntime.Snapshot before,F f,Set<Integer> changed){for(var s:before.pool().slots())if(!changed.contains(s.identity().wholeIndex()))assertEquals(s,f.slot(s.identity().wholeIndex()));assertEquals(before.status(),f.placement.status());assertArrayEquals(before.savedFlags().copy(),f.placement.capture().savedFlags().copy());}
    static byte[] spikeInit(CdObjectState s){byte[] b=s.copyBytes();b[0x24]+=2;putLong(b,4,0x20BFA6);b[1]|=4;b[0x18]=3;putWord(b,2,0x330);b[0x19]=16;b[0x16]=8;if((b[1]&2)!=0){b[0x19]=18;b[0x20]=(byte)0x83;}return b;}
    static byte[] logInit(CdObjectState s){byte[] b=s.copyBytes();b[0x24]+=2;b[1]|=4;b[0x18]=6;putLong(b,4,0x2087A4);b[0x1A]=b[0x28];b[0x19]=16;b[0x16]=16;putWord(b,2,0x445E);return b;}
    static void putWord(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}static void putLong(byte[] b,int at,int v){putWord(b,at,v>>>16);putWord(b,at+2,v);}static CdAssetBytes bytes(int n,int v){byte[] b=new byte[n];Arrays.fill(b,(byte)v);return CdAssetBytes.copyOf(b);}static CdObjectDrawQueue.DrawCameras cameras(){var c=new CdObjectDrawQueue.CameraWords(0x680,0x380);return new CdObjectDrawQueue.DrawCameras(c,c,c);}static class Fault extends RuntimeException{private static final long serialVersionUID=1L;}
}
