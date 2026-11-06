package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual-source asset/Frame components, explicit input carries and true Solid outcomes; no World/route/pixel claim. Java UNRUN. */
class TestCdR12bBreakableWallBinding {
    static final int SLOT=69,MAP=0x20EE64,SAVED=7;
    static final CdObjectState.LongField DX=TAMABBOH_MISSILE_X_DELTA,DY=TAMABBOH_MISSILE_Y_DELTA;
    static final int[] XS={0xFFFD097C,0xFFFC25EE,0xFFFD097C,0xFFFD097C,0xFFFC97B5,0xFFFD097C};
    static final int[] YS={0xFFFEB750,0,0x000148B0,0xFFFE4445,0,0x0001BBBB};
    static final int[][] FR={{8,9,8,12,13,12},{8,9,8,10,11,10},{10,11,10,10,11,10},{10,11,10,12,13,12},{9,8,9,13,12,13},{9,8,9,11,10,11},{11,10,11,11,10,11},{11,10,11,13,12,13}};
    static final int[][] DELTA={{-8,-16},{0,16},{0,32},{16,0},{16,16},{16,32}};
    static CdR12bTerrainAssets terrain,equalTerrain;static CdR12bPlayerAssets playerAssets;static CdR12aTerrainAssets present;static CdR11bAssets otherPast;
    @BeforeAll static void originalUsa() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(programs);equalTerrain=CdR12bTerrainAssets.fromPrograms(programs);playerAssets=CdR12bPlayerAssets.fromPrograms(programs);
            present=CdR12aTerrainAssets.fromPrograms(programs);otherPast=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
    }
    @Test void additiveEightPortTargetFactoryKeepsLegacyConstructorAssetsAndBothFactoriesExact() throws Exception {
        assertNotNull(CdBreakableWall.class.getMethod("fromAct2PastAssets",CdObjectPlacementRuntime.class,CdR12bTerrainAssets.class,CdSolidObject.class,CdBreakableWall.SolidCommit.class,CdBreakableWall.HalveXVelocity.class,CdBreakableWall.DrawObject.class,CdBreakableWall.PlayFm.class,CdBreakableWall.DispatchObserver.class));
        assertEquals(1,CdBreakableWall.class.getConstructors().length);assertEquals(7,CdBreakableWall.class.getConstructors()[0].getParameterCount());assertEquals(4,CdBreakableWall.Assets.class.getRecordComponents().length);
        assertEquals(2,Arrays.stream(CdBreakableWall.class.getDeclaredMethods()).filter(m->m.getName().equals("fromAssets")).count());
        var h=new H();var before=h.placement.capture();var actor=h.frame.captureActor();var shared=h.frame.captureShared();
        assertThrows(IllegalArgumentException.class,()->new CdBreakableWall(h.placement,CdBreakableWall.Assets.fromAct2PastAssets(terrain.objects()),actualSolid(),null,null,null,null));
        nonwrites(before,h,Set.of());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertTrue(h.order.isEmpty());assertTrue(h.published.isEmpty());
    }
    @Test void trueUsedMappingAndCompleteFiniteTablesRemainOriginalBProducts() throws Exception {
        var o=terrain.objects();var assets=CdBreakableWall.Assets.fromAct2PastAssets(o);var visual=o.breakableWall();
        assertSame(visual,o.breakableWall());assertEquals(14,visual.mappingFrames().size());assertTrue(visual.animations().isEmpty());
        for(var f:visual.mappingFrames())for(var piece:f.pieces())assertTrue(piece.tileOffset()+piece.tileCount()<=57);
        assertEquals(MAP,assets.mappingsAddress());assertEquals(MAP,CdR12bObjectAssets.View.MAP_SPR_BREAKABLE_WALL.range().nativeAddress());assertEquals(40,CdR12bObjectAssets.View.MAP_SPR_BREAKABLE_WALL.ordinal());
        var map=o.view(CdR12bObjectAssets.View.MAP_SPR_BREAKABLE_WALL);assertEquals(312,map.length());assertEquals("c561a76556cc21a46155e135205ab7c39cc8b007b9730c66e0a34a707d671226",sha(map));
        assertEquals("9ea247159cb7c1830c8392d11b18cd1ce1de576fa33e4451179201fbe800ac7f",sha(assets.pieceFrames()));
        assertEquals("86444e752d8300afadd9aa1b5e2fa7a3252febf156c023d05f7bcf152f706e94",sha(assets.pieceDeltas()));assertEquals("7a1571aadadf44de0c59b260de3ec3464b1b3676a4ea2cef273b069284032b31",sha(assets.pieceSpeeds()));
        assertEquals(64,assets.pieceFrames().length());assertEquals(24,assets.pieceDeltas().length());assertEquals(48,assets.pieceSpeeds().length());
        for(int subtype=0;subtype<8;subtype++)for(int row=0;row<6;row++)assertEquals(FR[subtype][row],assets.pieceFrames().unsignedByte(subtype*8+row));
        for(int row=0;row<6;row++){assertEquals(DELTA[row][0],(short)assets.pieceDeltas().unsignedWord(row*4));assertEquals(DELTA[row][1],(short)assets.pieceDeltas().unsignedWord(row*4+2));assertEquals(XS[row],(int)assets.pieceSpeeds().unsignedLong(row*8));assertEquals(YS[row],(int)assets.pieceSpeeds().unsignedLong(row*8+4));}
        assertThrows(IllegalArgumentException.class,()->new CdBreakableWall.Assets(assets.pieceFrames().slice(0,63),assets.pieceDeltas(),assets.pieceSpeeds(),MAP));
    }
    @Test void exactRetainedSourceGateRunsBeforeMetadataAndObserverOrPlayerOwners() throws Exception {
        var h=new H();var before=h.placement.capture();assertEquals(terrain.identity(),equalTerrain.identity());assertNotSame(terrain.placements(),equalTerrain.placements());assertNull(field(equalTerrain,"objects"));
        for(var p:List.of(new CdObjectPlacementRuntime(terrain.placements()),CdObjectPlacementRuntime.fromAssets(present,bank()),CdObjectPlacementRuntime.fromAssets(otherPast,bank())))
            assertThrows(IllegalArgumentException.class,()->CdBreakableWall.fromAct2PastAssets(p,terrain,null,null,null,null,null,null));
        assertThrows(IllegalArgumentException.class,()->CdBreakableWall.fromAct2PastAssets(h.placement,equalTerrain,null,null,null,null,null,null));assertNull(field(equalTerrain,"objects"));
        assertThrows(NullPointerException.class,()->CdBreakableWall.fromAct2PastAssets(null,terrain,null,null,null,null,null,null));
        assertThrows(NullPointerException.class,()->CdBreakableWall.fromAct2PastAssets(h.placement,null,null,null,null,null,null,null));
        assertThrows(NullPointerException.class,()->CdBreakableWall.fromAct2PastAssets(h.placement,terrain,actualSolid(),null,null,null,null,null));nonwrites(before,h,Set.of());
    }
    @Test void actualInitFallsIntoPlainSolidEvenWithPositiveSpriteAndOnlyReached64BytesChange() {
        var h=new H();h.edit(s->s.moveByte(ROUTINE,0).moveByte(SUBTYPE,5).moveByte(SPRITE_FLAGS,0x63).moveByte(COLLISION_STATUS,0));
        var before=h.placement.capture();byte[] expected=h.slot().copyBytes();init(expected);expected[0x25]=0;expected[1]&=0x7F;
        var actor=h.frame.captureActor();var shared=h.frame.captureShared();var result=h.run();assertArrayEquals(expected,result.state().copyBytes());
        var done=assertInstanceOf(CdSolidObject.Completed.class,h.solids.getFirst().outcome()).completedResult();assertEquals(CdSolidObject.Entry.SOLID_OBJECT,done.entry());assertEquals(Optional.of(CdSolidObject.GetOffReason.OBJECT_OFFSCREEN),done.getOffReason());
        assertEquals(0x44BE,result.state().word(TILE));assertEquals(MAP,result.state().longBits(MAPPINGS));assertEquals(List.of("solid","draw"),h.order);assertEquals(2,h.published.size());assertEquals(1,h.completed.size());
        assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void actualSourceSideSolidUsesRealFramePreparationAndPublishesOneNativeOutcome() {
        var h=new H(actualSolid(),0x600,0,0x200,0x0FE78000,0,0,0);h.main();var before=h.placement.capture();var clock=h.frame.vIntClock();h.run();
        var done=assertInstanceOf(CdSolidObject.Completed.class,h.solids.getFirst().outcome()).completedResult();assertEquals(CdSolidObject.Path.HALT_ON_X,done.path());
        assertEquals(0x0FE68000,h.frame.state().motion().centreX16_16());assertEquals(0,h.frame.state().motion().xVelocity());assertEquals(SLOT,h.frame.state().objectLinks().pushObject());
        assertEquals(done.player(),h.player());assertEquals(done.globals(),h.solidGlobals());assertEquals(0x20,h.slot().flags());assertEquals(List.of("solid","draw"),h.order);assertEquals(clock,h.frame.vIntClock());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void genuineStoppedSolidPrefixIsPreparedAdoptedAcknowledgedThenRethrownUnchanged() {
        var h=new H(new CollisionFixture().solid(),0x123,0,0x1D5,0x10008000,8,1,0);h.main();h.edit(s->s.moveByte(SOLID_TYPE,2));var before=h.placement.capture();
        RuntimeException original=assertThrows(RuntimeException.class,h::run);var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,h.solids.getFirst().outcome());
        assertSame(stopped.stop().originalException(),original);assertEquals(CdSolidObject.StopSite.STAND_RELEASE,stopped.stop().site());assertEquals(stopped.prefix().object(),h.slot());
        assertEquals(stopped.prefix().player(),h.player());assertEquals(stopped.prefix().globals(),h.solidGlobals());assertEquals(List.of("solid"),h.order);assertEquals(List.of(original),h.failed);assertTrue(h.completed.isEmpty());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void actualBTableBreaksStageSixDirtyFragmentsAndFixedSignedFrameXOnlyAfterFm() {
        for(int subtype=0;subtype<8;subtype++)for(int xv:new int[]{0x601,-3}){
            var h=new H(actualSolid(),xv,0x234,0x200,0x10008000,2,0,0x100);h.breaking(subtype);var before=h.placement.capture();var actor=h.frame.state();var environment=h.frame.environment();var clock=h.frame.vIntClock();
            var expected=breakImages(before.pool(),List.of(32,33,34,35,36),subtype,xv<0);var result=h.run();assertImages(expected,h.pool().capture());assertEquals(List.of("fm","halve","draw"),h.order);
            assertEquals(6,result.effects().stream().filter(CdBreakableWall.PieceInitialized.class::isInstance).count());assertEquals(3,h.published.size());assertEquals(1,h.completed.size());assertTrue(h.solids.isEmpty());
            assertEquals(xv>>1,h.frame.state().motion().xVelocity());assertEquals(withX(actor,xv>>1),h.frame.state());assertSame(environment,h.frame.environment());assertEquals(clock,h.frame.vIntClock());
            assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());assertEquals(before.status(),h.placement.status());
        }
    }
    @Test void allocationCutsRetainEveryEarlierNativeChildAndFallOnlyTheParent() {
        for(int capacity=0;capacity<5;capacity++){
            var h=new H();h.breaking(7);for(int whole=32;whole<128;whole++)if(whole!=SLOT)h.pool().write(h.pool().state(whole).moveByte(ID,0x55));
            var holes=new ArrayList<Integer>();for(int i=0;i<capacity;i++){int whole=new int[]{32,70,80,100}[i];holes.add(whole);h.pool().write(h.pool().state(whole).moveByte(ID,0));}
            var before=h.placement.capture();var expected=breakImages(before.pool(),holes,7,false);var result=h.run();assertImages(expected,h.pool().capture());
            var searches=result.effects().stream().filter(CdBreakableWall.SlotSearchReturned.class::isInstance).map(CdBreakableWall.SlotSearchReturned.class::cast).toList();assertEquals(capacity+1,searches.size());assertTrue(searches.getLast().wholeIndex().isEmpty());
            assertEquals(capacity+1,result.effects().stream().filter(CdBreakableWall.PieceInitialized.class::isInstance).count());assertEquals(List.of("fm","halve","draw"),h.order);assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
        }
    }
    @Test void reachedSubtypeLimitRetainsFmAsrAndDimensionsBeforeTheFirstMissingFrameByte() {
        var h=new H();h.breaking(8);var before=h.placement.capture();byte[] expected=h.slot().copyBytes();breakPrefix(expected,0x601,0x234);expected[0x17]=expected[0x19]=expected[0x16]=8;
        assertEquals(CdBreakableWall.Boundary.PIECE_FRAME_READ,assertThrows(CdBreakableWall.UnsupportedWall.class,h::run).boundary());assertArrayEquals(expected,h.slot().copyBytes());
        assertEquals(0x300,h.frame.state().motion().xVelocity());assertEquals(List.of("fm","halve"),h.order);assertEquals(1,h.failed.size());assertTrue(h.completed.isEmpty());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void fmAndHalveFaultsRetainOnlyTheirRealOriginalPrefixesAndFailureIdentity() {
        var fm=new H();fm.breaking(2);var before=fm.placement.capture();byte[] expected=fm.slot().copyBytes();breakPrefix(expected,0x601,0x234);var fault=new Fault();fm.fmAfter=c->{throw fault;};
        assertSame(fault,assertThrows(Fault.class,fm::run));assertArrayEquals(expected,fm.slot().copyBytes());assertEquals(0x601,fm.frame.state().motion().xVelocity());assertEquals(List.of("fm"),fm.order);assertEquals(List.of(fault),fm.failed);nonwrites(before,fm,Set.of(SLOT));
        var half=new H();half.breaking(2);before=half.placement.capture();expected=half.slot().copyBytes();breakPrefix(expected,0x601,0x234);var halfFault=new Fault();half.halveAfter=c->{throw halfFault;};
        assertSame(halfFault,assertThrows(Fault.class,half::run));assertArrayEquals(expected,half.slot().copyBytes());assertEquals(0x300,half.frame.state().motion().xVelocity());assertEquals(List.of("fm","halve"),half.order);assertEquals(List.of(halfFault),half.failed);nonwrites(before,half,Set.of(SLOT));
    }
    @Test void fallUsesNativeLongWrapAndSignedDeletionWithoutCameraSavedOrSolidWork() {
        for(int difference:new int[]{-224,-223}){
            var h=new H(actualSolid(),0x601,0x234,(0x200+difference)&65535,0x10008000,2,0,0x100);h.edit(s->s.moveByte(ROUTINE,4).moveByte(SAVED_FLAGS_ID,255).moveByte(SPRITE_FLAGS,0x84).moveLong(X_POSITION,0x7FFFF000).moveLong(Y_POSITION,0x02001234).moveLong(DX,0x4000).moveLong(DY,-0x4000));
            h.globals=new CdBreakableWall.Globals(127,0xFF,0);var before=h.placement.capture();byte[] expected=h.slot().copyBytes();fall(expected);expected[1]&=0x7F;var actor=h.frame.captureActor();var shared=h.frame.captureShared();
            var result=h.run();assertArrayEquals(difference<=-224?new byte[64]:expected,result.state().copyBytes());assertEquals(difference<=-224,result.deleted());assertEquals(difference<=-224?List.of():List.of("draw"),h.order);
            assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());nonwrites(before,h,Set.of(SLOT));
        }
    }
    @Test void actualPrivatePendingReceiptsRejectCopiesOmittedAckAndSynchronousReentry() {
        var h=new H();h.breaking(0);h.fmAfter=c->assertThrows(IllegalStateException.class,h::run);h.run();assertEquals(List.of("fm","halve","draw"),h.order);assertFalse(h.body.matchesPendingOperation(h.completed.getFirst()));
        var missing=new H();missing.main();missing.edit(s->s.moveByte(SOLID_TYPE,0xA5));missing.ack=false;
        assertThrows(IllegalStateException.class,missing::run);assertEquals(0,missing.slot().unsignedByte(SOLID_TYPE));assertEquals(List.of("solid"),missing.order);assertTrue(missing.completed.isEmpty());assertEquals(1,missing.failed.size());assertFalse(missing.frame.closed());
    }

    static final class H implements CdBreakableWall.DispatchObserver {
        final CdObjectPlacementRuntime placement=CdObjectPlacementRuntime.fromAssets(terrain,bank());final CdPlayerFrameRuntime frame;final CdBreakableWall body;
        final List<String> order=new ArrayList<>();final List<Object> published=new ArrayList<>();final List<CdBreakableWall.Result> completed=new ArrayList<>();final List<Throwable> failed=new ArrayList<>();final List<CdBreakableWall.SolidCall> solids=new ArrayList<>();
        Consumer<CdBreakableWall.FmCall> fmAfter=c->{};Consumer<CdBreakableWall.HalveXVelocityCall> halveAfter=c->{};boolean ack=true;
        CdBreakableWall.Globals globals=new CdBreakableWall.Globals(0,0,0x1080);
        H(){this(actualSolid(),0x601,0x234,0x200,0x10008000,2,0,0x100);}
        H(CdSolidObject solid,int xv,int yv,int y,int x,int flags,int stand,int debug){
            for(int whole=32;whole<128;whole++)pool().write(dirty(pool().state(whole)).moveByte(ID,0));
            edit(s->s.moveByte(ID,0x36).moveByte(ROUTINE,0).moveByte(SUBTYPE,0).moveByte(COLLISION_STATUS,0).moveByte(SAVED_FLAGS_ID,SAVED).moveByte(SPRITE_FLAGS,0x63).moveByte(FLAGS,0).moveWord(X,0x1000).moveWord(Y,0x200));
            var motion=new CdPlayerMotionState(x,(y<<16)|0x1234,xv,yv,0x333,2,0x10,flags,4,9,19,0,0,1,0,0,0,0,0x56,0x78);
            var state=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0x230F60,0x780,2,24),new CdPlayerBody.Timers(0,0,0,0x3456),new CdPlayerBody.History(bytes(256,0x35),0),0x12,new CdPlayerBody.ObjectLinks(0,stand),0x34);
            var b=terrain.loadPlan().bounds();var environment=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,0xC,0x80),new CdPlayerMovement.Bounds(b.left(),b.right(),b.bottom(),0),new CdPlayerMovement.Gates(1,0,0,0,0,0,0),new CdPlayerMovement.Globals(0,96,0x12,0x34,0x5678,4,0,0,0,0)),new CdPlayerBody.Controls(0,0,0,0,0),new CdPlayerBody.Globals(0x1234,160,0,debug,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
            frame=CdPlayerFrameRuntime.fromAct2PastEntry(terrain,playerAssets,state,environment,new CdPlayerArtRuntime.State(new byte[768],new byte[768],0,false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),17));
            body=CdBreakableWall.fromAct2PastAssets(placement,terrain,solid,this::solid,this::halve,this::draw,this::fm,this);
        }
        CdObjectPool pool(){return placement.pool();}CdObjectState slot(){return pool().state(SLOT);}void edit(java.util.function.UnaryOperator<CdObjectState> op){pool().write(op.apply(slot()));}
        void main(){edit(s->s.moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(COLLISION_STATUS,0).moveByte(SOLID_TYPE,0).moveByte(X_RADIUS,16).moveByte(WIDTH,16).moveByte(Y_RADIUS,24).moveByte(PRIORITY,1).moveWord(TILE,0x44BE).moveLong(MAPPINGS,MAP).moveWord(X_VELOCITY,0).moveWord(Y_VELOCITY,0));}
        void breaking(int subtype){main();edit(s->s.moveByte(SUBTYPE,subtype).moveByte(COLLISION_TYPE,0xEF).moveByte(COLLISION_STATUS,1));}
        CdSolidObject.Player player(){var s=frame.state();var h=s.header();return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());}
        CdSolidObject.Globals solidGlobals(){var e=frame.environment();var g=e.movement().globals();return new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());}
        CdBreakableWall.Inputs inputs(){var e=frame.environment();return new CdBreakableWall.Inputs(player(),new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),solidGlobals());}
        CdBreakableWall.Result run(){return body.run(SLOT,globals,inputs());}
        void claim(Object call,Object equalCopy){assertTrue(body.matchesPendingOperation(call));body.validateCurrentCall(call);assertFalse(body.matchesPendingOperation(equalCopy));assertThrows(IllegalStateException.class,()->body.validateCurrentCall(equalCopy));}
        void acknowledge(Object call){if(ack){body.acknowledgeCurrentCall(call);assertThrows(IllegalStateException.class,()->body.acknowledgeCurrentCall(call));}}
        void solid(CdBreakableWall.SolidCall call){claim(call,new CdBreakableWall.SolidCall(call.cut(),call.expectedPlayer(),call.expectedGlobals(),call.outcome()));order.add("solid");solids.add(call);
            CdObjectState object;List<CdObjectState> others;CdPlayerFrameRuntime.ObjectSolidPublication readyFrame;
            if(call.outcome() instanceof CdSolidObject.Completed d){var result=d.completedResult();object=result.object();others=result.otherSlots();readyFrame=frame.prepareObjectSolidResult(call.expectedPlayer(),call.expectedGlobals(),result);}
            else{var prefix=((CdSolidObject.NativeStopped)call.outcome()).prefix();object=prefix.object();others=prefix.otherSlots();readyFrame=frame.prepareObjectSolidPrefix(call.expectedPlayer(),call.expectedGlobals(),prefix);}
            var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(others);var readyPool=CdObjectPool.overlay(call.cut().placement().pool(),images);pool().validateRestore(readyPool);frame.validatePreparedObjectSolidPublication(readyFrame);
            images.forEach(pool()::write);frame.applyPreparedObjectSolidPublication(readyFrame);assertEquals(readyPool.slots(),pool().capture().slots());acknowledge(call);
        }
        void fm(CdBreakableWall.FmCall call){claim(call,new CdBreakableWall.FmCall(call.cut(),call.nativeId()));order.add("fm");var actor=frame.captureActor();var shared=frame.captureShared();fmAfter.accept(call);assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());acknowledge(call);}
        void halve(CdBreakableWall.HalveXVelocityCall call){claim(call,new CdBreakableWall.HalveXVelocityCall(call.cut(),call.expectedPlayer(),call.finalXVelocity()));order.add("halve");assertEquals(call.expectedPlayer(),player());
            var ready=frame.prepareObjectBreakableWallXVelocity(frame.state(),frame.environment());assertEquals(call.finalXVelocity(),ready.finalState().motion().xVelocity());frame.validatePreparedObjectSolidPublication(ready);frame.applyPreparedObjectSolidPublication(ready);halveAfter.accept(call);acknowledge(call);}
        void draw(CdObjectPool pool,CdObjectState.Identity identity){assertSame(pool(),pool);body.validateCurrentDraw(pool,identity);claim(identity,new CdObjectState.Identity(identity.wholeIndex()));order.add("draw");var source=pool.state(identity.wholeIndex());pool.write(source.moveByte(SPRITE_FLAGS,source.spriteFlags()&0x7F));acknowledge(identity);}
        public void published(Object call){assertTrue(body.matchesPendingOperation(call));published.add(call);}
        public void completed(CdBreakableWall.Result result){assertTrue(body.matchesPendingOperation(result));assertFalse(body.matchesPendingOperation(new CdBreakableWall.Result(result.state(),result.effects())));body.validateCurrentDispatch();completed.add(result);}
        public void failed(Throwable original){assertTrue(body.matchesPendingOperation(original));failed.add(original);}
    }
    /** Existing complete decoded-format collision control; actual shared algorithm computes the stop, never a fabricated Outcome. */
    static final class CollisionFixture {
        CdSolidObject solid(){byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256],heights=new byte[4096],widths=new byte[4096];fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)2);ids[1]=1;ids[2]=2;Arrays.fill(heights,16,32,(byte)16);Arrays.fill(widths,32,48,(byte)16);angles[2]=(byte)0xC0;
            var terrain=CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths);byte[] animations=new byte[578];for(int i=0;i<60;i++)w(animations,i*2,120);animations[122]=(byte)0xFF;byte[] maps=new byte[398],dplcs=new byte[398];for(int i=0;i<198;i++){w(maps,i*2,396);w(dplcs,i*2,396);}var visual=CdSonicVisualAssets.decode(maps,dplcs,new byte[69472]);byte[] atan=new byte[257];for(int i=0;i<=256;i++)atan[i]=(byte)(i/8);
            return new CdSolidObject(new CdPlayerMovement(terrain,CdR11aPlayerAssets.decode(animations,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)0xFF},new byte[4],new byte[640],atan,visual)));}
    }
    private static CdPlayerBody.State withX(CdPlayerBody.State s,int x){var m=s.motion();var n=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),x,m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());return new CdPlayerBody.State(n,s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());}
    private static Map<Integer,byte[]> breakImages(CdObjectPool.Snapshot before,List<Integer> holes,int subtype,boolean flip){var expected=new LinkedHashMap<Integer,byte[]>();for(var s:before.slots())expected.put(s.identity().wholeIndex(),s.copyBytes());var parent=expected.get(SLOT);breakPrefix(parent,0,0);var selected=new ArrayList<Integer>();selected.add(SLOT);selected.addAll(holes);
        for(int row=0;row<selected.size();row++){var piece=expected.get(selected.get(row));if(row!=0){piece[0]=parent[0];piece[0x24]=parent[0x24];w(piece,8,word(parent,8));w(piece,12,word(parent,12));piece[1]=parent[1];piece[0x18]=parent[0x18];l(piece,4,longword(parent,4));w(piece,2,word(parent,2));}piece[0x17]=piece[0x19]=piece[0x16]=8;piece[0x1A]=(byte)FR[subtype][row];w(piece,8,word(piece,8)+DELTA[row][0]);w(piece,12,word(piece,12)+DELTA[row][1]);l(piece,0x2E,YS[row]);l(piece,0x2A,flip?-XS[row]:XS[row]);}fall(parent);parent[1]&=0x7F;return expected;}
    private static void init(byte[] b){b[0x24]+=2;b[1]|=4;b[0x18]=1;b[0x17]=b[0x19]=16;b[0x16]=24;b[0x20]=(byte)0xEF;w(b,2,0x44BE);l(b,4,MAP);b[0x1A]=b[0x28];}
    private static void breakPrefix(byte[] b,int x,int y){b[0x20]=b[0x21]=0;b[0x24]+=2;w(b,0x2A,x);w(b,0x2E,y);}
    private static void fall(byte[] b){l(b,0x2E,longword(b,0x2E)+0x4000);l(b,8,longword(b,8)+longword(b,0x2A));l(b,12,longword(b,12)+longword(b,0x2E));}
    private static int word(byte[] b,int at){return((b[at]&255)<<8)|(b[at+1]&255);}private static int longword(byte[] b,int at){return(word(b,at)<<16)|word(b,at+2);}private static void w(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}private static void l(byte[] b,int at,int v){w(b,at,v>>>16);w(b,at+2,v);}
    private static void assertImages(Map<Integer,byte[]> expected,CdObjectPool.Snapshot actual){assertEquals(96,actual.slots().size());for(var image:actual.slots())assertArrayEquals(expected.get(image.identity().wholeIndex()),image.copyBytes());}
    private static void nonwrites(CdObjectPlacementRuntime.Snapshot before,H h,Set<Integer> changed){for(var image:before.pool().slots())if(!changed.contains(image.identity().wholeIndex()))assertEquals(image,h.pool().state(image.identity().wholeIndex()));assertEquals(before.status(),h.placement.status());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());assertFalse(h.frame.closed());}
    private static CdSolidObject actualSolid(){return new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromAct2Past(terrain,playerAssets)));}
    private static CdAssetBytes bank(){byte[] b=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];for(int i=0;i<b.length;i++)b[i]=(byte)(i*37+0xA6);return CdAssetBytes.copyOf(b);}private static CdAssetBytes bytes(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
    private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}private static String sha(CdAssetBytes b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.copy()));}
    private static CdObjectState dirty(CdObjectState s){try{byte[] b=new byte[64];for(int i=0;i<64;i++)b[i]=(byte)(s.identity().wholeIndex()*13+i*7);var c=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);c.setAccessible(true);return c.newInstance(field(s,"owner"),s.identity(),b);}catch(Exception e){throw new AssertionError(e);}}
    static final class Fault extends RuntimeException{private static final long serialVersionUID=1L;}
}
