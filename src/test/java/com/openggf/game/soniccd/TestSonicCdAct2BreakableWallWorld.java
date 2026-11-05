package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual cold controller/Past/Results/FULL/Title release, then labelled dynamic64-only Wall cuts.
 * No actor/global/camera/art/queue/bank/clock seeds. N1ecc5f3a Breakable Wall21-159.
 * Preparatory family qualification, not a measured natural Wall frontier. All new Java UNRUN. */
@Isolated
class TestSonicCdAct2BreakableWallWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets){}
    private static final int[] XV={0xFFFD097C,0xFFFC25EE,0xFFFD097C,0xFFFD097C,0xFFFC97B5,0xFFFD097C};
    private static final int[] YV={0xFFFEB750,0,0x148B0,0xFFFE4445,0,0x1BBBB};
    private static final int[][] DELTA={{-8,-16},{0,16},{0,32},{16,0},{16,16},{16,32}};
    private static final int[] FRAMES={11,10,11,13,12,13}; // Native subtype7 row, all six literal bytes.

    @Test void initFallsIntoOneOffscreenPlainSolidWithActualMapTileNonwritesAndBothReplayScopes() throws Exception {
        var s=scene();var body=body(s);var source=image(s,127,0,5).moveByte(SPRITE_FLAGS,4).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468);
        seed(s,source);var calls=new AtomicReference<CdBreakableWall.SolidCall>();var original=(CdBreakableWall.SolidCommit)actualField(body,"solidCommit");
        set(body,"solidCommit",(CdBreakableWall.SolidCommit)c->{calls.set(c);original.commit(c);});replay(s);
        var actual=s.placement().pool().state(127);assertEquals(2,actual.routine());assertEquals(0x20EED8,actual.longBits(MAPPINGS));assertEquals(0x44BE,actual.word(TILE));
        assertEquals(16,actual.unsignedByte(WIDTH));assertEquals(16,actual.unsignedByte(X_RADIUS));assertEquals(24,actual.unsignedByte(Y_RADIUS));
        assertEquals(0xEF,actual.unsignedByte(COLLISION_TYPE));assertEquals(5,actual.unsignedByte(MAP_FRAME));
        assertEquals(0x1357,actual.word(X_FRACTION));assertEquals(0x2468,actual.word(Y_FRACTION));
        var done=assertInstanceOf(CdSolidObject.Completed.class,calls.get().outcome()).completedResult();
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT,done.entry());assertEquals(Optional.of(CdSolidObject.GetOffReason.OBJECT_OFFSCREEN),done.getOffReason());
        assertEquals(1,count(s,SonicCdGameModule.Act2LoadCall.WALL_SOLID));assertSame(body,body(s));
    }

    @Test void realVisibleSolidAdoptsItsExactCompletedPlayerOrTheGenuineStoppedPrefixBeforeAnyDraw() throws Exception {
        var s=scene();var m=s.frame().state().motion();seed(s,image(s,127,2,0).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,(m.centreY16_16()>>>16)+43));
        var body=body(s);var original=(CdBreakableWall.SolidCommit)actualField(body,"solidCommit");var observed=new AtomicReference<CdSolidObject.Outcome>();
        set(body,"solidCommit",(CdBreakableWall.SolidCommit)c->{observed.set(c.outcome());original.commit(c);
            var player=c.outcome() instanceof CdSolidObject.Completed d?d.completedResult().player():((CdSolidObject.NativeStopped)c.outcome()).prefix().player();
            assertEquals(player.actor().motion(),s.frame().state().motion());assertEquals(player.pushObject(),s.frame().state().objectLinks().pushObject());assertEquals(player.standObject(),s.frame().state().objectLinks().standObject());});
        RuntimeException failure=null;try{step(s.h(),neutral());}catch(RuntimeException e){failure=e;}
        assertNotNull(observed.get());
        if(observed.get() instanceof CdSolidObject.NativeStopped stop){assertSame(stop.stop().originalException(),failure);assertTrue(s.frame().closed());
            assertEquals(stop.prefix().object(),s.placement().pool().state(127));System.out.println("Actual Wall Solid native stop: "+stop.stop().site()+" / "+stop.stop().detail()+" / "+failure);retired(s);}
        else{if(failure!=null)throw failure;healthy(s);assertEquals(1,count(s,SonicCdGameModule.Act2LoadCall.WALL_SOLID));}
    }

    @Test void breakupAllFiveAllocationFailureCutsAndSixPiecesRetainDirtyImagesAndPhysicalSchedulingWithExactAsr() throws Exception {
        var s=scene();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();int[] holes={100,111,115,120,127};
        for(int capacity=0;capacity<=5;capacity++) {
            registry.restore(base);seed(s,image(s,110,2,7).moveByte(COLLISION_STATUS,1));
            for(int whole=32;whole<128;whole++)if(s.placement().pool().state(whole).id()==0)
                seed(s,dirty(s.placement().pool().state(whole)).moveByte(ID,0x24).moveByte(SUBTYPE,0).moveByte(SPRITE_FLAGS,0).moveByte(COLLISION_TYPE,0)
                        .moveByte(SAVED_FLAGS_ID,0).moveWord(X,s.frame().state().motion().centreX16_16()>>>16));
            for(int n=0;n<capacity;n++)seed(s,s.placement().pool().state(holes[n]).moveByte(ID,0));
            var body=body(s);var fm=(CdBreakableWall.PlayFm)actualField(body,"fm");var halve=(CdBreakableWall.HalveXVelocity)actualField(body,"halve");
            var cut=new AtomicReference<CdBreakableWall.SourceCut>();var velocity=new AtomicReference<Integer>();int expectedCapacity=capacity;
            set(body,"fm",(CdBreakableWall.PlayFm)c->{cut.set(c.cut());velocity.set(s.frame().state().motion().xVelocity()>>1);
                assertEquals(Arrays.stream(holes).limit(expectedCapacity).boxed().toList(),c.cut().placement().pool().slots().stream().filter(row->row.id()==0).map(row->row.identity().wholeIndex()).toList(),
                        "Labelled first-free fixture must still have exactly its declared holes at the genuine FM cut");fm.play(c);});
            set(body,"halve",(CdBreakableWall.HalveXVelocity)c->{var before=s.frame().state();var environment=s.frame().environment();halve.halve(c);
                assertEquals(withXVelocity(before,before.motion().xVelocity()>>1),s.frame().state());assertSame(environment,s.frame().environment());});
            replay(s);var parentAtFm=cut.get().source();var parent=piece(parentAtFm,0,parentAtFm,velocity.get());
            assertImageExceptScreen(fall(parent),s.placement().pool().state(110));
            for(int n=0;n<capacity;n++) {
                var old=cut.get().placement().pool().slots().get(holes[n]-32);var child=piece(old,n+1,parent,velocity.get());
                if(holes[n]>110)child=fall(child);assertImageExceptScreen(child,s.placement().pool().state(holes[n]));
            }
            assertEquals(1,count(s,SonicCdGameModule.Act2LoadCall.WALL_FM));assertEquals(1,count(s,SonicCdGameModule.Act2LoadCall.WALL_HALVE_X));
            // Wrappers are test faults/observations, not retained runtime owners, and must not nest across cases.
            set(body,"fm",fm);set(body,"halve",halve);
        }
    }

    @Test void fallUsesWrappingLongMotionThenDrawOrDirectFullDeleteWithoutSavedUnload() throws Exception {
        var s=scene();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();
        for(boolean delete:new boolean[]{false,true}){registry.restore(base);var source=image(s,127,4,0).moveLong(X_POSITION,0x7FFFF000)
                .moveWord(X,(s.frame().state().motion().centreX16_16()>>>16)+64).moveWord(Y,(s.frame().state().motion().centreY16_16()>>>16)+(delete?1024:0))
                .moveLong(TAMABBOH_MISSILE_X_DELTA,0x18000).moveLong(TAMABBOH_MISSILE_Y_DELTA,-0x4000).moveByte(SAVED_FLAGS_ID,240);
            seed(s,source);var bank=s.placement().capture().savedFlags();replay(s);
            if(delete)assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());else assertImageExceptScreen(fall(source),s.placement().pool().state(127));
            assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());assertEquals(0,count(s,SonicCdGameModule.Act2LoadCall.WALL_SOLID));}
    }

    @Test void fmFaultRetainsOriginalVelocityWordsBeforeHalveOrAnyFragmentAllocation() throws Exception {
        var s=scene();var source=image(s,127,2,0).moveByte(COLLISION_STATUS,1);seed(s,source);var body=body(s);var boom=new AssertionError("Wall FM fault");
        var prefix=new AtomicReference<CdObjectState>();set(body,"fm",(CdBreakableWall.PlayFm)c->{prefix.set(c.cut().source());
            assertEquals(s.frame().state().motion().xVelocity()&65535,c.cut().source().word(TITLE_CARD_DEST_X));assertEquals(s.frame().state().motion().yVelocity()&65535,c.cut().source().word(TITLE_CARD_DEST_Y));throw boom;});
        assertSame(boom,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(prefix.get(),s.placement().pool().state(127));
        assertEquals(0,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_HALVE_X));assertEquals(0,boom.getSuppressed().length);retired(s);
    }

    @Test void failedActualSolidPublicationKeepsItsFullCommittedPrefixAndExactOriginalFault() throws Exception {
        var s=scene();seed(s,image(s,127,2,0).moveByte(SPRITE_FLAGS,4));var body=body(s);var original=(CdBreakableWall.SolidCommit)actualField(body,"solidCommit");
        var observed=new AtomicReference<CdObjectState>();var boom=new IllegalStateException("Wall Solid fault");
        set(body,"solidCommit",(CdBreakableWall.SolidCommit)c->{original.commit(c);observed.set(s.placement().pool().state(127));throw boom;});
        assertSame(boom,assertThrows(IllegalStateException.class,()->step(s.h(),neutral())));assertEquals(observed.get(),s.placement().pool().state(127));
        assertEquals(1,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_SOLID));assertEquals(0,boom.getSuppressed().length);retired(s);
    }

    @Test void skippedFmAndForgedAcknowledgementCannotAdvanceToAsrOrFragmentStores() throws Exception {
        var s=scene();seed(s,image(s,127,2,0).moveByte(COLLISION_STATUS,1));var body=body(s);var prefix=new AtomicReference<CdObjectState>();
        set(body,"fm",(CdBreakableWall.PlayFm)c->{prefix.set(c.cut().source());body.acknowledgeCurrentCall(c);});
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(prefix.get(),s.placement().pool().state(127));
        assertEquals(0,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_FM));assertEquals(0,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_HALVE_X));retired(s);
    }

    @Test void clonedSolidCallCannotBorrowThePendingActualOutcomeOrPublishFrameValues() throws Exception {
        var s=scene();seed(s,image(s,127,2,0).moveByte(SPRITE_FLAGS,4));var body=body(s);var original=(CdBreakableWall.SolidCommit)actualField(body,"solidCommit");
        set(body,"solidCommit",(CdBreakableWall.SolidCommit)c->original.commit(new CdBreakableWall.SolidCall(c.cut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome())));
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(0,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_SOLID));retired(s);
    }

    @Test void halveFaultKeepsSoundAndActualXOnlyPublicationButNoFragmentStores() throws Exception {
        var s=scene();seed(s,image(s,127,2,0).moveByte(COLLISION_STATUS,1));var body=body(s);var original=(CdBreakableWall.HalveXVelocity)actualField(body,"halve");
        var prefix=new AtomicReference<CdObjectState>();var boom=new IllegalArgumentException("Wall halve fault");
        set(body,"halve",(CdBreakableWall.HalveXVelocity)c->{prefix.set(c.cut().source());original.halve(c);assertEquals(c.finalXVelocity(),s.frame().state().motion().xVelocity());throw boom;});
        assertSame(boom,assertThrows(IllegalArgumentException.class,()->step(s.h(),neutral())));assertEquals(prefix.get(),s.placement().pool().state(127));
        assertEquals(1,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_FM));assertEquals(1,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_HALVE_X));retired(s);
    }

    @Test void drawFaultAfterNativeQueueClearKeepsMovedFallPrefixAndOriginalFault() throws Exception {
        var s=scene();var source=image(s,127,4,0).moveLong(TAMABBOH_MISSILE_X_DELTA,0x8000).moveLong(TAMABBOH_MISSILE_Y_DELTA,0);seed(s,source);
        var body=body(s);var original=(CdBreakableWall.DrawObject)actualField(body,"draw");var boom=new AssertionError("Wall Draw fault");
        set(body,"draw",(CdBreakableWall.DrawObject)(pool,id)->{original.draw(pool,id);throw boom;});
        assertSame(boom,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(fall(source).moveByte(SPRITE_FLAGS,source.spriteFlags()&127),s.placement().pool().state(127));
        assertEquals(0,boom.getSuppressed().length);retired(s);
    }

    @Test void readonlyFaultSuppressesFull96CorruptionWithoutRollbackOrReplacingOriginal() throws Exception {
        var s=scene();seed(s,image(s,127,2,0).moveByte(COLLISION_STATUS,1));var body=body(s);var boom=new AssertionError("Wall corrupted FM");
        set(body,"fm",(CdBreakableWall.PlayFm)c->{var other=s.placement().pool().state(126);s.placement().pool().write(other.moveWord(TITLE_CARD_START_Y,0x7654));throw boom;});
        assertSame(boom,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertTrue(boom.getSuppressed().length>=1);
        assertEquals(0x7654,s.placement().pool().state(126).word(TITLE_CARD_START_Y));assertEquals(0,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_HALVE_X));retired(s);
    }

    @Test void invalidSubtypeStopsAtItsReachedFrameReadAfterSoundAsrAndDimensionsWithoutFabricatedPicture() throws Exception {
        var s=scene();seed(s,image(s,127,2,8).moveByte(COLLISION_STATUS,1));
        var fault=assertThrows(CdBreakableWall.UnsupportedWall.class,()->step(s.h(),neutral()));assertEquals(CdBreakableWall.Boundary.PIECE_FRAME_READ,fault.boundary());
        assertEquals(8,s.placement().pool().state(127).unsignedByte(WIDTH));assertEquals(4,s.placement().pool().state(127).routine());
        assertEquals(1,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_FM));assertEquals(1,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_HALVE_X));assertEquals(0,fault.getSuppressed().length);retired(s);
    }

    @Test void skippedSameValueSolidCannotReachDrawEvenWhenItsExpectedPoolBytesAlreadyMatch() throws Exception {
        var s=scene();var source=image(s,127,2,0).moveByte(SPRITE_FLAGS,4).moveByte(SOLID_TYPE,0);seed(s,source);
        set(body(s),"solidCommit",(CdBreakableWall.SolidCommit)c->{});
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,s.placement().pool().state(127));
        assertEquals(0,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_SOLID));retired(s);
    }

    @Test void skippedAsrWithForgedAcknowledgementStopsBeforeAnyNativeFragmentStore() throws Exception {
        var s=scene();seed(s,image(s,127,2,0).moveByte(COLLISION_STATUS,1));var body=body(s);var prefix=new AtomicReference<CdObjectState>();
        set(body,"halve",(CdBreakableWall.HalveXVelocity)c->{prefix.set(c.cut().source());body.acknowledgeCurrentCall(c);});
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(prefix.get(),s.placement().pool().state(127));
        assertEquals(1,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_FM));assertEquals(0,rawCount(s,SonicCdGameModule.Act2LoadCall.WALL_HALVE_X));retired(s);
    }

    @Test void reentryAtActualFmCannotBorrowTheCachedBodyOrRetainItsTransientCursor() throws Exception {
        var s=scene();seed(s,image(s,127,2,0).moveByte(COLLISION_STATUS,1));var body=body(s);var original=(CdBreakableWall.PlayFm)actualField(body,"fm");
        set(body,"fm",(CdBreakableWall.PlayFm)c->{assertThrows(IllegalStateException.class,()->body.run(127,null,null));original.play(c);});
        replay(s);assertEquals(1,count(s,SonicCdGameModule.Act2LoadCall.WALL_FM));assertEquals(1,count(s,SonicCdGameModule.Act2LoadCall.WALL_HALVE_X));
    }

    @Test void mainDrawPrecedesActualMaskedDespawnAndChangesOnlyTheReachedSavedByteBeforeFullDelete() throws Exception {
        var s=scene();var x=s.h().mode().getCamera().getX();seed(s,image(s,127,2,0).moveByte(SPRITE_FLAGS,4).moveWord(X,(x-0x400)&65535).moveByte(SAVED_FLAGS_ID,240));
        var bank=s.placement().capture().savedFlags();replay(s);assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());
        var expected=bank.copy();expected[2+3*240+1]&=127;assertArrayEquals(expected,s.placement().capture().savedFlags().copy());
        assertEquals(1,count(s,SonicCdGameModule.Act2LoadCall.WALL_SOLID));
    }

    private static CdObjectState piece(CdObjectState target,int row,CdObjectState parent,int finalX){
        if(row!=0)target=target.moveByte(ID,parent.id()).moveByte(ROUTINE,parent.routine()).moveWord(X,parent.centreX()).moveWord(Y,parent.centreY())
                .moveByte(SPRITE_FLAGS,parent.spriteFlags()).moveByte(PRIORITY,parent.unsignedByte(PRIORITY)).moveLong(MAPPINGS,parent.longBits(MAPPINGS)).moveWord(TILE,parent.word(TILE));
        return target.moveByte(X_RADIUS,8).moveByte(WIDTH,8).moveByte(Y_RADIUS,8).moveByte(MAP_FRAME,FRAMES[row]).addWord(X,DELTA[row][0]).addWord(Y,DELTA[row][1])
                .moveLong(TAMABBOH_MISSILE_Y_DELTA,YV[row]).moveLong(TAMABBOH_MISSILE_X_DELTA,finalX<0?-XV[row]:XV[row]);
    }
    private static CdObjectState fall(CdObjectState s){s=s.addLong(TAMABBOH_MISSILE_Y_DELTA,0x4000);return s.addLong(X_POSITION,s.longBits(TAMABBOH_MISSILE_X_DELTA)).addLong(Y_POSITION,s.longBits(TAMABBOH_MISSILE_Y_DELTA));}
    private static CdPlayerBody.State withXVelocity(CdPlayerBody.State s,int x){var m=s.motion();return new CdPlayerBody.State(new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),x,m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle()),s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());}
    private static CdObjectState dirty(CdObjectState s){for(var f:CdObjectState.ByteField.values())s=s.moveByte(f,0x51+7*f.byteOffset());for(var f:CdObjectState.WordField.values())s=s.moveWord(f,0xA157+0x103*f.byteOffset());for(var f:CdObjectState.LongField.values())s=s.moveLong(f,0xA1536729+0x1030507*f.byteOffset());return s;}
    private static CdObjectState image(Scene s,int whole,int routine,int subtype){var m=s.frame().state().motion();return s.placement().pool().state(whole).moveByte(ID,0x36).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype).moveByte(SAVED_FLAGS_ID,0).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(COLLISION_TYPE,0xEF).moveByte(COLLISION_STATUS,0).moveByte(PRIORITY,1).moveByte(WIDTH,16).moveByte(X_RADIUS,16).moveByte(Y_RADIUS,24).moveWord(X,(m.centreX16_16()>>>16)+64).moveWord(Y,m.centreY16_16()>>>16).moveWord(TILE,0x44BE).moveLong(MAPPINGS,0x20EED8).moveByte(MAP_FRAME,Math.min(subtype,7));}
    private static CdBreakableWall body(Scene s)throws Exception{return (CdBreakableWall)actualField(s.visit(),"wall");}
    private static void assertImageExceptScreen(CdObjectState a,CdObjectState b){assertEquals(a.moveByte(SPRITE_FLAGS,a.spriteFlags()&127),b.moveByte(SPRITE_FLAGS,b.spriteFlags()&127));}
    private static long count(Scene s,SonicCdGameModule.Act2LoadCall c){return s.h().module().act2LoadState().calls().stream().filter(x->x==c).count();}
    @SuppressWarnings("unchecked") private static long rawCount(Scene s,SonicCdGameModule.Act2LoadCall c)throws Exception{return ((List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(s.visit(),"values"),"calls")).stream().filter(x->x==c).count();}
    private static void seed(Scene s,CdObjectState image)throws Exception{var old=s.placement().capture();var actor=s.frame().captureActor();var shared=s.frame().captureShared();s.placement().pool().write(image);assertEquals(old.status(),s.placement().status());assertArrayEquals(old.savedFlags().copy(),s.placement().capture().savedFlags().copy());assertEquals(actor,s.frame().captureActor());assertEquals(shared,s.frame().captureShared());for(var row:old.pool().slots())if(row.identity().wholeIndex()!=image.identity().wholeIndex())assertEquals(row,s.placement().pool().state(row.identity().wholeIndex()));}
    private static Scene scene()throws Exception{var old=ready();var h=old.harness();for(int call=old.returned()+1;call<=10700;call++)step(h,call<=10419?controller(call):controller(10419));var v=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");var s=new Scene(h,v,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(v,"placement"),h.module().act2LoadState().assets());assertEquals(CdStageKey.parse("R12A"),s.frame().stageSource().stage());assertTrue(old.frame().closed());assertEquals(0,s.frame().environment().controls().controlLocked());assertEquals(0,h.module().act2LoadState().section().queue().headSource());assertEquals(0,h.module().act2LoadState().reservedObjects().unsignedByte(3*64));assertEquals(0,s.placement().pool().state(127).id());healthy(s);return s;}
    private static void replay(Scene s)throws Exception{var h=s.h();var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(h);var world=h.mode().getWorldSession();var stage=s.frame().stageSource();long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();registry.restore(full);assertNativeCapture(before,h);registry.restoreCourse(course);assertNativeCapture(before,h);step(h,neutral());healthy(s);var after=captureNative(h);registry.restore(full);assertNativeCapture(before,h);step(h,neutral());healthy(s);assertNativeCapture(after,h);registry.restoreCourse(course);assertNativeCapture(before,h);step(h,neutral());healthy(s);assertNativeCapture(after,h);assertSame(world,h.mode().getWorldSession());assertSame(stage,s.frame().stageSource());assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());}
    private static void healthy(Scene s)throws Exception{assertFalse(s.frame().closed());assertSame(s.frame(),s.h().module().requirePlayerFrame());assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,s.h().module().act2LoadState().calls().getLast());retired(s);}
    private static void retired(Scene s)throws Exception{assertNull(actualField(s.visit(),"activeWall"));assertFalse((boolean)actualField(s.visit(),"wallPort"));assertFalse((boolean)actualField(s.visit(),"drawingWall"));assertFalse((boolean)actualField(s.visit(),"runningObjects"));assertNull(actualField(body(s),"dispatch"));}
    private static void set(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private static LogicalInputSnapshot neutral(){return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),PlayerInputState.neutral());}
    private static LogicalInputSnapshot controller(int call){if(call<1||call>10419)throw new IllegalArgumentException("Original controller recipe ends10419");if(call<=8276)return route(call);int directions=8,actions=call<=8308?2:0;if(call>=9001&&call<=9203)directions=4;else if(call>=9206&&call<=9217)directions=0;else if(call>=9218&&call<=9263){directions=2;actions=call==9219?2:0;}else if(call>=9336&&call<=9367)actions=2;return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions,0,actions,0,false,false),PlayerInputState.neutral());}
}
