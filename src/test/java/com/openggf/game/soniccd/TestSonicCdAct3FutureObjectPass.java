package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** Tests FIRST; Java UNRUN. Actual USA C/D assets and actual cached component/Frame/pool algorithms.
 * Complete State/Environment/saved bank/reserved/CPU values and the semantic caller below are explicitly
 * CONSTRUCTED COMPONENT INPUTS, not natural programme/FULL/World ingress. The actual pass creates every
 * private dispatch/Adoption/Plc receipt; no reflection, fake component invocation, effects replay or
 * fabricated private receipt establishes authority. Root owns later real Core/Host/World qualification. */
class TestSonicCdAct3FutureObjectPass {
    @Test void exactActualSourcePairAndExplicitCompleteBankAreRequired()throws Exception{
        var all=actual();for(var a:all){var h=new Host(a);assertTrue(h.placement.ownsAct3FutureAssets(a));assertFalse(h.placement.ownsAct3FutureAssets(all.get(a==all.get(0)?1:0)));
            assertThrows(IllegalArgumentException.class,()->new SonicCdAct3FutureObjectPass(h,all.get(a==all.get(0)?1:0),h.objects,h.frame,h.placement));
            assertThrows(NullPointerException.class,()->CdObjectPlacementRuntime.fromAct3FutureAssets(a,null));
            for(int n:new int[]{0,1,2,0x2FC,0x2FD,0x2FF,0x300})assertThrows(IllegalArgumentException.class,()->CdObjectPlacementRuntime.fromAct3FutureAssets(null,CdAssetBytes.copyOf(new byte[n])));
            assertEquals(0,h.placement.status().routine());assertArrayEquals(bank().copy(),h.placement.capture().savedFlags().copy());
            var other=new Host(a);assertThrows(IllegalArgumentException.class,()->h.placement.restore(other.placement.capture()));assertThrows(IllegalArgumentException.class,()->h.frame.restoreActor(other.frame.captureActor()));assertThrows(IllegalArgumentException.class,()->h.frame.restoreShared(other.frame.captureShared()));}
    }
    @Test void idleAndCaughtReentryNeverCloseOrMutateHealthyOwners()throws Exception{
        for(var a:actual()){var h=new Host(a);var before=h.frame.captureActor();var shared=h.frame.captureShared();var placed=h.placement.capture();
            assertThrows(IllegalStateException.class,()->h.pass.runWalk(7));assertEquals(before,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());samePlacement(placed,h.placement);
            h.beforeAdopt=()->assertThrows(IllegalStateException.class,()->h.pass.runWalk(7));h.seedReserved(2,0x1C,0);h.walk();assertFalse(h.frame.closed());assertNotNull(h.lastAdoption);
            assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);assertThrows(IllegalStateException.class,h.lastAdoption::validateBefore);h.finish();assertFalse(h.frame.closed());}
    }
    @Test void physicalTitleParentFourAllocatesEightRealChildrenAndVisitsForwardChildrenSameWalk()throws Exception{
        for(var a:actual()){var h=new Host(a);h.seedReserved(4,0x3C,0);var before=h.placement.capture();h.walk();
            assertEquals(2,h.reserved(4).unsignedByte(0x24));assertEquals(0x8360,h.reserved(4).unsignedWord(2));
            for(int row=0;row<8;row++){var s=h.placement.pool().state(32+row);assertEquals(0x3C,s.id());assertEquals(4,s.routine());assertEquals(h.objects.titleCardAssets().mappingsAddress(),s.longBits(CdObjectState.LongField.MAPPINGS));assertEquals(h.objects.titleCardAssets().initData().unsignedWord(row*8+2)-8,s.centreX());}
            assertEquals(6,h.placement.pool().state(37).unsignedByte(MAP_FRAME));assertEquals(before.status(),h.placement.status());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());h.finish();}
    }
    @Test void initialNativeSpawnerRunsOwnedRingSpringMonitorAnimalFamiliesWithoutRelabeling()throws Exception{
        for(var a:actual()){var h=new Host(a);h.placement.spawnObjects(0,2);h.good=OptionalInt.of(1);h.walk();
            var ids=h.placement.pool().capture().slots().stream().filter(s->s.id()!=0).map(CdObjectState::id).distinct().toList();assertTrue(ids.containsAll(List.of(0x10,0x0A,0x19,0x24)));
            for(var s:h.placement.pool().capture().slots())if(s.id()==0x10)assertEquals(0xA7AE,s.word(TILE));else if(s.id()==0x19)assertEquals(0x05A8,s.word(TILE));
            assertFalse(h.frame.closed());h.finish();}
    }
    @Test void goodFutureIsReadOnlyWhenReachedAndUnknownStopsBeforeAnimalStores()throws Exception{
        for(var a:actual()){var h=new Host(a);h.seedDynamic(32,0x24,1);var before=h.placement.capture();var e=assertThrows(SonicCdAct3FutureObjectPass.Unowned.class,h::walk);
            assertEquals(SonicCdAct3FutureObjectPass.Boundary.GOOD_FUTURE,e.boundary());samePlacement(before,h.placement);assertFalse(h.frame.closed());assertThrows(IllegalStateException.class,()->h.pass.runWalk(7));}
    }
    @Test void badFuturePresenceDeletesThroughTheRealSharedAnimalGate()throws Exception{
        for(var a:actual()){var h=new Host(a);h.good=OptionalInt.of(0);h.seedDynamic(32,0x24,1);h.walk();assertEquals(0,h.placement.pool().state(32).id());h.finish();}
    }
    @Test void capsuleBossAndOtherFamiliesStopAtTheirActualPhysicalIdentityWithEarlierPrefix()throws Exception{
        for(var a:actual())for(int id:new int[]{0x03}){var h=new Host(a);h.seedReserved(2,0x1C,0);h.seedDynamic(32,id,0);var old=h.reserved(2);
            var e=assertThrows(SonicCdAct3FutureObjectPass.Unowned.class,h::walk);assertEquals(id,e.value());assertEquals(32,e.wholeIndex());assertEquals(SonicCdAct3FutureObjectPass.Boundary.OBJECT_FAMILY,e.boundary());assertFalse(Arrays.equals(old.copy(),h.reserved(2).copy()));assertEquals(id,h.placement.pool().state(32).id());}
    }
    @Test void titleDefersRealPlcTwoAndRetainsReachedRoutineScrollPrefixWhenOwnerMissing()throws Exception{
        for(var a:actual()){var h=new Host(a);h.seedReserved(4,0x3C,0);h.updateReserved(4,raw-> {raw[0x24]=6;raw[0x1E]=0;putWord(raw,0x0A,0x30);putWord(raw,0x30,0x30);});
            var e=assertThrows(SonicCdAct3FutureObjectPass.Unowned.class,h::walk);assertEquals(SonicCdAct3FutureObjectPass.Boundary.PLC_OWNER,e.boundary());assertEquals(2,e.value());assertEquals(10,h.reserved(4).unsignedByte(0x24));assertEquals(0x01A5,h.scroll);assertNotNull(h.lastPlc);
            assertThrows(IllegalStateException.class,h.lastPlc::validate);assertThrows(IllegalStateException.class,h.lastPlc::acknowledge);}
    }
    @Test void titleUnknownPlcHeadStopsBeforeUnlockOrDelete()throws Exception{
        for(var a:actual()){var h=new Host(a);h.seedReserved(4,0x3C,0);h.updateReserved(4,raw->raw[0x24]=10);var before=h.reserved(4);var actor=h.frame.captureActor();var shared=h.frame.captureShared();
            var e=assertThrows(SonicCdAct3FutureObjectPass.Unowned.class,h::walk);assertEquals(SonicCdAct3FutureObjectPass.Boundary.PLC_HEAD,e.boundary());assertArrayEquals(before.copy(),h.reserved(4).copy());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());}
    }
    @Test void originalAdoptionRuntimeAndErrorPreserveIdentityAndRetireReceipt()throws Exception{
        for(var a:actual())for(Throwable original:List.of(new IllegalArgumentException("caller publication fault"),new AssertionError("caller publication error"))){var h=new Host(a);h.seedReserved(2,0x1C,0);h.fault=original;var before=h.reserved(2);var caught=assertThrows(Throwable.class,h::walk);assertSame(original,caught);assertArrayEquals(before.copy(),h.reserved(2).copy());assertNotNull(h.lastAdoption);assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);assertFalse(h.frame.closed());}
    }
    @Test void legacySpringMalformedShapesRemainRejectedAndFutureProfileUsesExact199And68()throws Exception{
        for(var a:actual()){var h=new Host(a);var old=legacy.objects();var oldData=CdSpring.Assets.fromAct2PastAssets(legacy);
            assertEquals(200,oldData.vertical().mappings().length());assertEquals(72,oldData.objectTileIds().length());
            assertThrows(IllegalArgumentException.class,()->new CdSpring.Assets(h.objects.springVertical(),h.objects.springHorizontal(),h.objects.springDiagonal(),CdR13ObjectAssets.View.MAP_SPRING.range(a.identity().file()).nativeAddress(),h.objects.view(CdR13ObjectAssets.View.SPRING_SPEEDS),oldData.objectTileIds()));
            for(int width:new int[]{0,2,36,38,66,68,70,71,73,76})assertThrows(IllegalArgumentException.class,()->new CdSpring.Assets(old.springVertical(),old.springHorizontal(),old.springDiagonal(),oldData.mappingsAddress(),oldData.speedWords(),CdAssetBytes.copyOf(new byte[width])),"CONSTRUCTED malformed table width "+width);
            assertThrows(IllegalArgumentException.class,()->new CdSpring.Assets(old.springVertical(),old.springHorizontal(),old.springDiagonal(),oldData.mappingsAddress(),oldData.speedWords(),h.objects.view(CdR13ObjectAssets.View.OBJECT_TILE_IDS)));
            h.seedDynamic(32,0x0A,0);h.walk();assertEquals(2,h.placement.pool().state(32).routine());assertEquals(CdR13ObjectAssets.View.MAP_SPRING.range(a.identity().file()).nativeAddress(),h.placement.pool().state(32).longBits(CdObjectState.LongField.MAPPINGS));h.finish();}
    }
    @Test void completeFrameAnglePreparationIsPureAndRejectsStalePairAfterActualApplication()throws Exception{
        for(var a:actual()){var h=new Host(a);var actor=h.frame.captureActor();var shared=h.frame.captureShared();int before=h.frame.environment().movement().globals().primaryAngle();var ready=h.frame.prepareObjectPrimaryAngle(before,0x37);assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());h.frame.applyPreparedObjectSolidPublication(ready);assertEquals(0x37,h.frame.environment().movement().globals().primaryAngle());assertSame(actor.state(),h.frame.state());assertThrows(IllegalArgumentException.class,()->h.frame.validatePreparedObjectSolidPublication(ready));assertThrows(IllegalArgumentException.class,()->h.frame.prepareObjectPrimaryAngle(before,0));}
    }

    @Test void ringFindNextIgnoresEarlierFreeSlotsAndForwardChildRunsLaterInTheSameWalk()throws Exception{
        for(var a:actual()){var h=new Host(a);h.seedDynamic(40,0x10,2);h.placement.pool().write(h.placement.pool().state(41).moveByte(MAP_FRAME,7));h.walk();
            assertEquals(0,h.placement.pool().state(32).id());var child=h.placement.pool().state(41);assertEquals(0x10,child.id());assertEquals(3,child.unsignedByte(RING_MEMBER));assertEquals(0,child.unsignedByte(MAP_FRAME));assertEquals(2,child.routine());h.finish();}
    }
    @Test void monitorFirstFreeItemThenExplosionBehindCursorWaitUntilTheNextWalk()throws Exception{
        for(var a:actual()){var h=new Host(a);h.seedDynamic(40,0x19,0);h.walk();h.finish();h.placement.pool().write(h.placement.pool().state(40).moveByte(ROUTINE,4));h.walk();
            assertEquals(0x1A,h.placement.pool().state(32).id());assertEquals(0x18,h.placement.pool().state(33).id());assertEquals(0,h.placement.pool().state(32).routine());assertEquals(0,h.placement.pool().state(33).routine());assertEquals(8,h.placement.pool().state(40).routine());assertEquals(1,h.sounds.size());assertInstanceOf(SonicCdGameModule.PastMonitorFm.class,h.sounds.getFirst());h.finish();
            h.walk();assertEquals(2,h.placement.pool().state(32).routine());assertEquals(2,h.placement.pool().state(33).routine());h.finish();}
    }
    @Test void ringRewardSoundAndSavedBitRetainTheirActualNativeOrder()throws Exception{
        for(var a:actual()){var h=new Host(a);h.ring=new CdRingGlobals(99,3,0,0,0,0,0,0);h.seedDynamic(32,0x10,0);var image=h.placement.pool().state(32).moveByte(ROUTINE,4).moveByte(RING_MEMBER,1).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,8).moveByte(Y_RADIUS,8).moveLong(CdObjectState.LongField.MAPPINGS,CdR13ObjectAssets.View.MAP_SPR_RING.range(a.identity().file()).nativeAddress()).moveWord(TILE,0xA7AE);h.placement.pool().write(image);var order=new ArrayList<String>();
            h.beforeAdopt=()->{var pending=h.lastAdoption;if(pending.expected().ring().rings()==99&&pending.next().ring().rings()==100){order.add("reward");assertEquals(0x2A,h.placement.savedFlag(0,2));assertTrue(h.sounds.isEmpty());}if(pending.expected().sounds().isEmpty()&&pending.next().sounds().size()==1){order.add("sound");assertEquals(100,h.ring.rings());assertEquals(0x2A,h.placement.savedFlag(0,2));}};
            h.walk();assertEquals(List.of("reward","sound"),order);assertEquals(100,h.ring.rings());assertEquals(4,h.ring.lives());assertEquals(0x2B,h.placement.savedFlag(0,2));assertInstanceOf(SonicCdGameModule.PastRingSubCpu.class,h.sounds.getFirst());h.finish();}
    }
    @Test void titleAllocationFailureKeepsTheActualParentAndCompletedFirstChildPrefix()throws Exception{
        for(var a:actual()){var h=new Host(a);for(int whole=32;whole<128;whole++)if(whole!=34)h.seedDynamic(whole,0x15,0);h.seedReserved(4,0x3C,0);
            var e=assertThrows(CdTitleCard.UnsupportedTitleCard.class,h::walk);assertEquals(CdTitleCard.UnsupportedBoundary.ALLOCATION_ADDRESS,e.boundary());assertEquals(2,h.reserved(4).unsignedByte(0x24));assertEquals(0x3C,h.placement.pool().state(34).id());assertEquals(4,h.placement.pool().state(34).routine());assertEquals(0x228,h.placement.pool().state(34).centreX());assertEquals(0x168,h.placement.pool().state(34).word(TITLE_CARD_DEST_X));
            for(int whole=32;whole<128;whole++)if(whole!=34)assertEquals(0x15,h.placement.pool().state(whole).id());assertFalse(h.frame.closed());}
    }

    @Test void publicRingSourceValuesCannotRelabelADeclaredOrAuthenticatedPlacementAsFuture()throws Exception{
        for(var a:actual()){var h=new Host(a);var data=new CdRing.SourceData(h.objects.ringDeltas(),CdR13ObjectAssets.View.MAP_SPR_RING.range(a.identity().file()).nativeAddress(),0xA7AE,h.objects.rings());
            assertThrows(IllegalArgumentException.class,()->new CdRing(h.placement,data));var declared=new CdObjectPlacementRuntime(a.placements());var before=declared.capture();var body=new CdRing(declared,data);
            var globals=new CdRing.GlobalsPort(){public CdRingGlobals read(){throw new AssertionError("Unadmitted source read globals");}public void write(CdRingGlobals value){throw new AssertionError("Unadmitted source wrote globals");}};
            var calls=new CdRing.SourceCalls(){public void playFmSound(CdRing.FmSound value){fail("Unadmitted FM");}public void subCpuCommand(CdRing.SubCpuCommand value){fail("Unadmitted command");}public void drawObject(CdObjectPool pool,int whole){fail("Unadmitted Draw");}};
            assertThrows(UnsupportedOperationException.class,()->body.executeSelected(32,new CdRing.Inputs(CdReleaseVariant.USA,a.identity().stage(),0,0,2,0),globals,calls));samePlacement(before,declared);}
    }

    private static final class Host implements SonicCdAct3FutureObjectPass.Host {
        final CdR13FutureAssets assets;final CdR13ObjectAssets objects;final CdObjectPlacementRuntime placement;final CdPlayerFrameRuntime frame;final SonicCdAct3FutureObjectPass pass;
        final Object programme=new Object();CdRingGlobals ring=new CdRingGlobals(0,3,0,0,0,0,0,0);CdAssetBytes reserved=CdAssetBytes.copyOf(new byte[1984]),cpu=CdAssetBytes.copyOf(new byte[640]);Optional<CdObjectDrawQueue.CpuSat> completed=Optional.empty();List<SonicCdGameModule.PastSound> sounds=List.of();OptionalInt good=OptionalInt.empty();int scroll=0x33A5;boolean active;Runnable beforeAdopt=()->{};Throwable fault;
        SonicCdAct3FutureObjectPass.Adoption lastAdoption;SonicCdAct3FutureObjectPass.PlcSubmission lastPlc;
        Host(CdR13FutureAssets a){assets=a;objects=CdR13ObjectAssets.fromAssets(a);placement=CdObjectPlacementRuntime.fromAct3FutureAssets(a,bank());frame=CdPlayerFrameRuntime.fromAct3FutureEntry(a,state(),environment(),art(),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0),0));pass=new SonicCdAct3FutureObjectPass(this,a,objects,frame,placement);}
        public void validate(SonicCdAct3FutureObjectPass p,CdPlayerFrameRuntime f,CdR13FutureAssets a,CdR13ObjectAssets o,CdObjectPlacementRuntime v){if(!active||p!=pass||f!=frame||a!=assets||o!=objects||v!=placement)throw new IllegalStateException("No constructed semantic caller scope");}
        public SonicCdAct3FutureObjectPass.Cut read(){return new SonicCdAct3FutureObjectPass.Cut(this,programme,ring,reserved,cpu,completed,sounds,0,0,0,OptionalInt.empty(),good,OptionalInt.empty(),scroll,OptionalLong.empty());}
        public void adopt(SonicCdAct3FutureObjectPass.Adoption a){lastAdoption=a;assertTrue(pass.isCurrentAdoption(a));a.validateBefore();beforeAdopt.run();if(fault instanceof RuntimeException r)throw r;if(fault instanceof Error e)throw e;var next=a.next();a.applyNative();ring=next.ring();reserved=next.reservedSlots();cpu=next.cpuSat();completed=next.completedCpu();sounds=next.sounds();scroll=next.scrollLockWord();}
        public void primary(SonicCdAct3FutureObjectPass.PrimaryCall c){fail("Constructed test actor ID0 does not dispatch primary; real World owns it");}
        public void submitTitlePlc(SonicCdAct3FutureObjectPass.PlcSubmission p){lastPlc=p;p.validate();throw p.unowned();}
        void walk(){active=true;try{pass.runWalk(frame.environment().globals().levelFrames());}finally{active=false;}}
        void finish(){active=true;try{pass.finishDraw(new CdObjectDrawQueue.DrawCameras(new CdObjectDrawQueue.CameraWords(0,0),new CdObjectDrawQueue.CameraWords(0,0),new CdObjectDrawQueue.CameraWords(0,0)));}finally{active=false;}}
        CdAssetBytes reserved(int whole){return reserved.slice((whole-1)*64,64);}
        void seedReserved(int whole,int id,int subtype){updateReserved(whole,b->{b[0]=(byte)id;b[0x28]=(byte)subtype;});}
        void updateReserved(int whole,java.util.function.Consumer<byte[]> change){var all=reserved.copy();var row=Arrays.copyOfRange(all,(whole-1)*64,whole*64);change.accept(row);System.arraycopy(row,0,all,(whole-1)*64,64);reserved=CdAssetBytes.copyOf(all);}
        void seedDynamic(int whole,int id,int subtype){var s=placement.pool().state(whole).moveByte(ID,id).moveByte(SUBTYPE,subtype);placement.pool().write(s);}
    }
    private static List<CdR13FutureAssets> retained;
    private static CdR12bTerrainAssets legacy;
    private static synchronized List<CdR13FutureAssets> actual()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Optional USA CD media absent");assertTrue(Files.isDirectory(Path.of(root)),"Configured bad root hard fails");List<Path> cues;try(var files=Files.list(Path.of(root).resolve("USA"))){cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();}assertEquals(1,cues.size());try(var p=CdProgramAssets.open(cues.getFirst(),Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=List.of(CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13D.stage()));legacy=CdR12bTerrainAssets.fromPrograms(p);}return retained;}
    private static CdAssetBytes bank(){byte[] b=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];Arrays.fill(b,(byte)0x2A);return CdAssetBytes.copyOf(b);}
    private static CdPlayerArtRuntime.State art(){byte[] b=new byte[0x300];for(int i=0;i<b.length;i++)b[i]=(byte)(i*7+3);return new CdPlayerArtRuntime.State(b,b,7,true);}
    private static CdPlayerBody.State state(){return new CdPlayerBody.State(new CdPlayerMotionState(0,0,0,0,0,0,0,0,0,9,19,0,0,0,0,0,0,0,0,0),new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(0,0x22DF20,0,2,16),new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),0),0,new CdPlayerBody.ObjectLinks(0,0),0x89);}
    private static CdPlayerBody.Environment environment(){return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,0xC,0x80),new CdPlayerMovement.Bounds(0,0x1000,0x1000,0),new CdPlayerMovement.Gates(2,2,0,0,0,0,0),new CdPlayerMovement.Globals(0,0x60,0x12,0x34,0,0,0,0,0,0)),new CdPlayerBody.Controls(0,0,0,0,1),new CdPlayerBody.Globals(0,0,1,0,7,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);}
    private static void samePlacement(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p){var now=p.capture();assertEquals(before.status(),now.status());assertEquals(before.pool().slots(),now.pool().slots());assertArrayEquals(before.savedFlags().copy(),now.savedFlags().copy());}
    private static void putWord(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}
}
