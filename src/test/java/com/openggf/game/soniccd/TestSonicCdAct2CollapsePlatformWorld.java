package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.control.InputActionMasks;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine cold/Past/Results Next8/normal Act2 ingress; only labelled dynamic full64 object inputs.
 * Source N1ecc5f3a Collapsing Platform/Main7-308 and actual Act2 Data, Object Animate14-95,
 * Collision Check/Spawner/Player Object Collision. No actor/global/art/queue/bank/camera/clock seed.
 * Private callbacks observe the actual reached source or throw a named fault, never forge success.
 * All Java UNRUN. Natural Collapse continuation remains Root's independent execution obligation. */
@Isolated
class TestSonicCdAct2CollapsePlatformWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void everyActualPlacementShapeInitializesFromTheOneTwoSevenTwoOwnerAndOneFreshTop() throws Exception {
        var s=scene();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();var body=actualField(s.visit(),"collapsePlatform");
        for(int subtype:new int[]{0,0x10,0x80,0xC0,0xC1}){registry.restore(base);var source=image(s,127,0,subtype).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468);seed(s,source);
            var nativeBody=(CdCollapsePlatform)body;var original=(CdCollapsePlatform.SolidCommit)actualField(nativeBody,"commit");var first=new AtomicReference<CdCollapsePlatform.SolidCall>();
            set(nativeBody,"commit",(CdCollapsePlatform.SolidCommit)c->{first.compareAndSet(null,c);original.commit(c);});replayNext(s);set(nativeBody,"commit",original);
            var expected=init(source);assertNotNull(first.get());assertEquals(expected,first.get().actualCut().slots().get(95));
            assertImageExceptScreen(expected,s.placement().pool().state(127));assertSame(body,actualField(s.visit(),"collapsePlatform"));
            assertEquals(List.of(SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID,SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_BODY),familyCalls(s));
        }
    }

    @Test void rawSizeDirectoryAndHeaderFailuresRetainOnlyTheLiteralReachedInitPrefix() throws Exception {
        for(int subtype:new int[]{1,15,0xC2}){var s=scene();var source=image(s,127,0,subtype);seed(s,source);beforeForward(s);
            var failure=assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class,()->step(s.h(),neutral()));
            assertEquals(subtype==15?CdCollapsePlatform.UnsupportedBoundary.SIZE_DIRECTORY_READ:CdCollapsePlatform.UnsupportedBoundary.SIZE_HEADER_READ,failure.boundary());
            var expected=source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveByte(PRIORITY,3).moveWord(TILE,0x44BE)
                    .moveLong(MAPPINGS,(subtype&128)==0?0x20C348:0x20C414).moveByte(MAP_FRAME,subtype&15);
            assertEquals(expected,s.placement().pool().state(127));assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID));assertTrue(s.frame().closed());retired(s);}
    }

    @Test void delayZeroWrapAndZeroTransitionKeepFractionsAndDoNotRunFallEarly() throws Exception {
        var s=scene();var reg=s.h().mode().getRewindRegistry();var base=reg.capture();
        for(int timer:new int[]{0,1,2}){reg.restore(base);var source=image(s,127,4,0).moveWord(COLLAPSE_DELAY,timer).moveByte(COLLAPSE_SOLID_MARKER,0).moveLong(COLLAPSE_FALL_DELTA,0x12345678);seed(s,source);replayNext(s);
            var expected=source.addWord(COLLAPSE_DELAY,-1);if(timer==1)expected=expected.addByte(ROUTINE,2);assertImageExceptScreen(expected,s.placement().pool().state(127));
            assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID));}
    }

    @Test void delayMarkerUsesTheFreshTopAndKeepsTheActualTransitionRoutineSix() throws Exception {
        var s=scene();var source=image(s,127,4,0).moveWord(COLLAPSE_DELAY,1).moveByte(COLLAPSE_SOLID_MARKER,0x7F);seed(s,source);replayNext(s);
        assertImageExceptScreen(source.moveWord(COLLAPSE_DELAY,0).moveByte(ROUTINE,6),s.placement().pool().state(127));
        assertEquals(1,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID).count());
    }

    @Test void fallUsesFullLongOldDeltaThenAccelerationAndSkipsRoutineFourPlusDespawn() throws Exception {
        var s=scene();var source=image(s,127,6,0).moveWord(X,0x9000).moveWord(Y_FRACTION,0xE000).moveLong(COLLAPSE_FALL_DELTA,0x18000);seed(s,source);var bank=s.placement().capture().savedFlags();replayNext(s);
        assertImageExceptScreen(source.addLong(Y_POSITION,0x18000).addLong(COLLAPSE_FALL_DELTA,0x4000),s.placement().pool().state(127));assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
        assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID));
    }

    @Test void genuineFallDistanceDeletesTheWholeImageBeforeItsUnconditionalIdZeroDraw() throws Exception {
        var s=scene();var source=image(s,127,6,0).moveWord(Y,(s.frame().state().motion().centreY16_16()>>>16)+0x400).moveLong(COLLAPSE_FALL_DELTA,0);seed(s,source);replayNext(s);
        assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());assertEquals(List.of(SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_BODY),familyCalls(s));
    }

    @Test void nativeC0SupportGetOffFmAndEightDirtyFirstFreeChildrenPublishWithoutRedispatchingEarlierRows() throws Exception {
        var s=scene();var source=contact(s,127,0xC0);seed(s,source);var free=s.placement().capture().pool().slots().stream().filter(x->x.id()==0).limit(8).toList();assertEquals(8,free.size(),"Actual pool capacity prerequisite");
        for(var row:free)seed(s,row.moveByte(SPRITE_FLAGS,0x41).moveByte(FLAGS,0xA0).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468).moveWord(X_VELOCITY,0x3579));
        var before=s.placement().capture().pool();replayNext(s);assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());
        for(int i=0;i<8;i++){var old=before.slots().get(free.get(i).identity().wholeIndex()-32);var expected=single(old,source,i);assertEquals(expected,s.placement().pool().state(old.identity().wholeIndex()));}
        assertEquals(List.of(SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID,SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID,
                SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_FM,SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_BODY),familyCalls(s));assertEquals(0,s.frame().state().objectLinks().standObject());
    }

    @Test void newlyAllocatedLaterPhysicalChildrenRunTheirNativeDelayInTheSamePass() throws Exception {
        var s=scene();int parent=s.placement().capture().pool().slots().stream().filter(x->x.id()==0).mapToInt(x->x.identity().wholeIndex()).min().orElseThrow();var source=contact(s,parent,0xC0);seed(s,source);
        var free=s.placement().capture().pool().slots().stream().filter(x->x.id()==0).limit(8).toList();assertEquals(8,free.size());assertTrue(free.stream().allMatch(x->x.identity().wholeIndex()>parent));replayNext(s);
        for(int i=0;i<8;i++){var child=s.placement().pool().state(free.get(i).identity().wholeIndex());assertEquals(4,child.routine());assertEquals(8+12*i,child.word(COLLAPSE_DELAY));}
    }

    @Test void genuineFullPoolTakesCapacityTopAndGetOffAfterFmThenDeletesOnlyParent() throws Exception {
        var s=scene();for(var row:s.placement().capture().pool().slots())if(row.id()==0&&row.identity().wholeIndex()!=127)seed(s,row.moveByte(ID,0x12).moveByte(ROUTINE,12));
        seed(s,contact(s,127,0xC0));replayNext(s);assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());
        assertEquals(4,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID).count());assertEquals(1,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_FM).count());
    }

    @Test void invalidLowFiveBitBreakupDirectoryStopsAfterTheRealGetOffFmAndAddqPrefix() throws Exception {
        var s=scene();var source=contact(s,127,0xD0);seed(s,source);var free=s.placement().capture().pool().slots().stream().filter(x->x.id()==0).toList();beforeForward(s);
        var failure=assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class,()->step(s.h(),neutral()));assertEquals(CdCollapsePlatform.UnsupportedBoundary.SIZE_DIRECTORY_READ,failure.boundary());
        assertEquals(source.addByte(ROUTINE,2),s.placement().pool().state(127));for(var old:free)assertEquals(old,s.placement().pool().state(old.identity().wholeIndex()));
        assertEquals(1,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_FM).count());assertTrue(s.frame().closed());retired(s);
    }

    @Test void actualRawMultiSelectorSevenRetainsSeventeenChildrenBeforeItsFiniteScriptReadStop() throws Exception {
        var s=scene();var source=contact(s,127,7);seed(s,source);var free=s.placement().capture().pool().slots().stream().filter(x->x.id()==0).toList();assertTrue(free.size()>=18,"Actual free-slot prerequisite before script offset24");beforeForward(s);
        var failure=assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class,()->step(s.h(),neutral()));assertEquals(CdCollapsePlatform.UnsupportedBoundary.MULTI_FRAME_READ,failure.boundary());assertEquals(24,failure.byteOffset());
        assertEquals(source.addByte(ROUTINE,2).moveByte(COLLAPSE_SAVED_SOURCE_ID,0x20),s.placement().pool().state(127));
        for(int i=0;i<17;i++){var child=s.placement().pool().state(free.get(i).identity().wholeIndex());assertEquals(0x20,child.id());assertEquals(4,child.routine());assertEquals(9+12*i,child.word(COLLAPSE_DELAY));}
        assertEquals(free.get(17),s.placement().pool().state(free.get(17).identity().wholeIndex()));assertTrue(s.frame().closed());retired(s);
    }

    @Test void clonedSolidCannotPublishBeforeTheExactPendingCallAndDuplicateNoopCannotPublishTwice() throws Exception {
        var s=scene();seed(s,image(s,127,2,0));var body=(CdCollapsePlatform)actualField(s.visit(),"collapsePlatform");var original=(CdCollapsePlatform.SolidCommit)actualField(body,"commit");
        set(body,"commit",(CdCollapsePlatform.SolidCommit)c->{assertThrows(IllegalStateException.class,()->original.commit(new CdCollapsePlatform.SolidCall(c.site(),c.entry(),c.actualCut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome())));original.commit(c);assertThrows(IllegalStateException.class,()->original.commit(c));});replayNext(s);
        assertEquals(1,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID).count());
    }

    @Test void skippedSameValueTopCannotAuthorizeTheFollowingDraw() throws Exception {
        var s=scene();seed(s,image(s,127,2,0));var body=(CdCollapsePlatform)actualField(s.visit(),"collapsePlatform");set(body,"commit",(CdCollapsePlatform.SolidCommit)c->{});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID));assertTrue(s.frame().closed());retired(s);
    }

    @Test void exactFmFaultKeepsOnlyItsRealPrefixAndDoesNotCreateAChildOrDraw() throws Exception {
        var s=scene();var source=contact(s,127,0xC0);seed(s,source);var free=s.placement().capture().pool().slots().stream().filter(x->x.id()==0).toList();var body=(CdCollapsePlatform)actualField(s.visit(),"collapsePlatform");
        var original=(CdCollapsePlatform.FmCommit)actualField(body,"act2Fm");var fault=new AssertionError("actual collapse A3 prefix");set(body,"act2Fm",(CdCollapsePlatform.FmCommit)c->{original.play(c);throw fault;});beforeForward(s);
        assertSame(fault,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(source,s.placement().pool().state(127));for(var old:free)assertEquals(old,s.placement().pool().state(old.identity().wholeIndex()));
        assertEquals(1,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_FM).count());assertTrue(s.frame().closed());retired(s);
    }

    @Test void completedInitCannotBeRestoredAtTheActualDrawCallback() throws Exception {
        var s=scene();var source=image(s,127,0,0);seed(s,source);var body=(CdCollapsePlatform)actualField(s.visit(),"collapsePlatform");var original=(CdCollapsePlatform.DrawObjectCall)actualField(body,"drawObject");
        set(body,"drawObject",(CdCollapsePlatform.DrawObjectCall)(pool,id)->{pool.write(source);original.drawObject(pool,id);});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(s.frame().closed());retired(s);
    }

    @Test void clonedA3BeforeTheRealCallAndDuplicateRealRequestCannotPublishAnotherSound() throws Exception {
        var s=scene();seed(s,contact(s,127,0xC0));var body=(CdCollapsePlatform)actualField(s.visit(),"collapsePlatform");var original=(CdCollapsePlatform.FmCommit)actualField(body,"act2Fm");
        set(body,"act2Fm",(CdCollapsePlatform.FmCommit)c->{assertThrows(IllegalStateException.class,()->original.play(new CdCollapsePlatform.FmRequest(c.actualCut(),c.expectedSource(),c.soundId())));original.play(c);assertThrows(IllegalStateException.class,()->original.play(c));});
        replayNext(s);assertEquals(1,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_FM).count());
    }

    @Test void realSolidPublicationFaultRetainsTheReadyImageAndTheIdenticalOriginalException() throws Exception {
        var s=scene();seed(s,image(s,127,2,0));var body=(CdCollapsePlatform)actualField(s.visit(),"collapsePlatform");var original=(CdCollapsePlatform.SolidCommit)actualField(body,"commit");
        var reached=new AtomicReference<CdCollapsePlatform.SolidCall>();var fault=new AssertionError("actual collapse Solid prefix");
        set(body,"commit",(CdCollapsePlatform.SolidCommit)c->{reached.set(c);original.commit(c);throw fault;});beforeForward(s);
        assertSame(fault,assertThrows(AssertionError.class,()->step(s.h(),neutral())));var outcome=assertInstanceOf(CdSolidObject.Completed.class,reached.get().outcome()).completedResult();
        assertEquals(outcome.object(),s.placement().pool().state(127));assertTrue(s.frame().closed());retired(s);
    }


    @Test void returningNoopFmRejectsBeforeAddqChildrenOrParentDeletion() throws Exception {
        var s=scene();seed(s,contact(s,127,0xC0));
        var body=(CdCollapsePlatform)actualField(s.visit(),"collapsePlatform");
        var reached=new AtomicReference<CdCollapsePlatform.FmRequest>();
        set(body,"act2Fm",(CdCollapsePlatform.FmCommit)c->reached.set(c));beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertNotNull(reached.get());
        assertEquals(reached.get().actualCut().slots(),s.placement().pool().capture().slots(),"Missing FM receipt must reject before the next native store");
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_FM));
        assertTrue(s.frame().closed());retired(s);
    }

    @Test void returningNoopDrawRejectsBeforeOutsideWindowSavedClearOrDeletion() throws Exception {
        var s=scene();seed(s,image(s,127,2,0).moveWord(X,((s.frame().state().motion().centreX16_16()>>>16)+0x500)&0xFFFF).moveByte(SPRITE_FLAGS,4));
        var body=(CdCollapsePlatform)actualField(s.visit(),"collapsePlatform");
        var reached=new AtomicReference<CdObjectPool.Snapshot>();var bank=s.placement().capture().savedFlags();
        set(body,"drawObject",(CdCollapsePlatform.DrawObjectCall)(pool,id)->reached.set(pool.capture()));beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertNotNull(reached.get());
        assertEquals(reached.get().slots(),s.placement().pool().capture().slots(),"Missing Draw receipt must reject before the native despawn tail");
        assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
        assertTrue(s.frame().closed());retired(s);
    }

    private static CdObjectState image(Scene s,int whole,int routine,int subtype){var m=s.frame().state().motion();int x=(m.centreX16_16()>>>16)+96,y=m.centreY16_16()>>>16;
        return s.placement().pool().state(whole).moveByte(ID,0x20).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0)
                .moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(PRIORITY,3).moveByte(X_RADIUS,40).moveByte(WIDTH,40).moveByte(Y_RADIUS,34).moveByte(MAP_FRAME,0)
                .moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0).moveWord(X,x).moveWord(Y,y).moveWord(TILE,0x44BE)
                .moveLong(MAPPINGS,routine>=4?0x20C3D6:(subtype&128)==0?0x20C348:0x20C414).moveWord(COLLAPSE_DELAY,9).moveLong(COLLAPSE_FALL_DELTA,0x20000)
                .moveByte(COLLAPSE_SOLID_MARKER,0).moveByte(COLLAPSE_SAVED_SOURCE_ID,0);}
    private static CdObjectState init(CdObjectState s){int sub=s.subtype();boolean single=(sub&128)!=0;var out=s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(PRIORITY,3).moveWord(TILE,0x44BE)
            .moveLong(MAPPINGS,single?0x20C414:0x20C348).moveByte(MAP_FRAME,sub&15);if((sub&16)!=0)out=out.moveByte(SPRITE_FLAGS,out.spriteFlags()|1).moveByte(FLAGS,out.flags()|1);
        return out.moveByte(X_RADIUS,single?64:40).moveByte(WIDTH,single?64:40).moveByte(Y_RADIUS,single?((sub&15)==0?26:18):34);}
    private static CdObjectState contact(Scene s,int whole,int subtype){var m=s.frame().state().motion();boolean single=(subtype&128)!=0;int yr=single?26:34;
        return image(s,whole,2,subtype).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,(m.centreY16_16()>>>16)+yr+19-16).moveByte(X_RADIUS,single?64:40).moveByte(WIDTH,single?64:40).moveByte(Y_RADIUS,yr);}
    private static CdObjectState single(CdObjectState old,CdObjectState parent,int n){return old.moveByte(PRIORITY,3).moveWord(TILE,0x44BE).moveByte(SPRITE_FLAGS,old.spriteFlags()|4).moveLong(MAPPINGS,0x20C54A)
            .moveLong(COLLAPSE_FALL_DELTA,0x20000).moveByte(ID,0x20).moveByte(ROUTINE,4).moveWord(Y,parent.centreY()).moveByte(COLLAPSE_SOLID_MARKER,255)
            .moveByte(X_RADIUS,8).moveByte(WIDTH,8).moveByte(Y_RADIUS,26).moveByte(MAP_FRAME,0).moveWord(X,parent.centreX()-56+16*n).moveWord(COLLAPSE_DELAY,9+12*n);}
    private static void assertImageExceptScreen(CdObjectState a,CdObjectState b){assertEquals(a.moveByte(SPRITE_FLAGS,a.spriteFlags()&127),b.moveByte(SPRITE_FLAGS,b.spriteFlags()&127));}
    private static List<SonicCdGameModule.Act2LoadCall> familyCalls(Scene s){return calls(s).stream().filter(c->c.name().startsWith("COLLAPSE_PLATFORM")).toList();}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.Act2LoadCall> rawCalls(Scene s)throws Exception{return (List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(s.visit(),"values"),"calls");}

    private static Scene scene() throws Exception {return scene(ready());}
    private static Scene scene(Prepared past) throws Exception {
        var old=oldReturned(past); step(old.harness(),neutral()); var h=old.harness();
        for(int service=0;service<64;service++) {
            step(h,neutral());
            if(h.module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN)) {
                var visit=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");
                var scene=new Scene(h,visit,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(visit,"placement"),h.module().act2LoadState().assets());
                assertEquals(0,scene.placement().pool().state(127).id()); healthy(scene);
                for(int ordinary=0;ordinary<256;ordinary++) {
                    if(scene.frame().environment().controls().controlLocked()==0
                            && scene.h().module().act2LoadState().section().queue().headSource()==0) return scene;
                    step(h,neutral());healthy(scene);
                }
                throw new AssertionError("Actual Title/PLC2 did not release within its native finite bound");
            }
        }
        throw new AssertionError("Actual normal Act2 load did not reach its first ordinary cut");
    }
    private static void seed(Scene s,CdObjectState image) throws Exception {
        var before=s.placement().capture(); var actor=s.frame().captureActor(); var shared=s.frame().captureShared();
        s.placement().pool().write(image);
        assertEquals(before.status(),s.placement().status()); assertArrayEquals(before.savedFlags().copy(),s.placement().capture().savedFlags().copy());
        assertEquals(actor,s.frame().captureActor()); assertEquals(shared,s.frame().captureShared());
        for(var old:before.pool().slots()) if(old.identity().wholeIndex()!=image.identity().wholeIndex())
            assertEquals(old,s.placement().pool().state(old.identity().wholeIndex()));
    }
    private static void beforeForward(Scene s) throws Exception {
        var registry=s.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(s.h());
        registry.restore(full);assertNativeCapture(before,s.h());registry.restoreCourse(course);assertNativeCapture(before,s.h());
    }
    private static void replayNext(Scene s) throws Exception {
        var h=s.h(); var registry=h.mode().getRewindRegistry(); var full=registry.capture(); var course=registry.captureCourse();
        var before=captureNative(h); var source=s.frame().stageSource(); var world=h.mode().getWorldSession();
        long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        registry.restore(full); assertNativeCapture(before,h); registry.restoreCourse(course); assertNativeCapture(before,h);
        step(h,neutral()); healthy(s); var after=captureNative(h);
        registry.restore(full); assertNativeCapture(before,h); step(h,neutral()); healthy(s); assertNativeCapture(after,h);
        registry.restoreCourse(course); assertNativeCapture(before,h); step(h,neutral()); healthy(s); assertNativeCapture(after,h);
        assertSame(source,s.frame().stageSource()); assertSame(world,h.mode().getWorldSession());
        assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }
    private static void healthy(Scene s) throws Exception {
        assertSame(s.frame(),s.h().module().requirePlayerFrame()); assertFalse(s.frame().closed());
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,s.h().module().act2LoadState().calls().getLast()); retired(s);
    }
    private static void retired(Scene s) throws Exception {
        for(String name:new String[]{"activeCollapse","activeCheckpoint","activeTamabboh","activeFloat","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"collapsePort","drawingCollapse","checkpointPort","drawingCheckpoint","tamabbohPort","drawingTamabboh","floatPort","drawingFloat","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
    }
    private static List<SonicCdGameModule.Act2LoadCall> calls(Scene s) { return s.h().module().act2LoadState().calls(); }
    private static CdAssetBytes bytes(Scene s,String name) throws Exception { return (CdAssetBytes)actualField(actualField(s.visit(),"values"),name); }
    @SuppressWarnings("unchecked") private static CdObjectDrawQueue.CpuSat cpu(Scene s) throws Exception {
        return ((Optional<CdObjectDrawQueue.CpuSat>)actualField(actualField(actualField(s.visit(),"values"),"loop"),"completedDraw")).orElseThrow();
    }
    private static Object invoke(Object owner,String name,Class<?>[] types,Object...arguments) throws Exception {
        var method=owner.getClass().getDeclaredMethod(name,types); method.setAccessible(true);
        try { return method.invoke(owner,arguments); }
        catch(InvocationTargetException wrapped) {
            var original=wrapped.getCause(); if(original instanceof Error error) throw error;
            if(original instanceof RuntimeException error) throw error; if(original instanceof Exception error) throw error;
            throw new AssertionError(original);
        }
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        var field=owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner,value);
    }
    private static Prepared oldReturned() throws Exception { return oldReturned(ready()); }
    private static Prepared oldReturned(Prepared p) throws Exception {
        for(int call=p.returned()+1;call<=8278;call++) step(p.harness(),route(call));
        seed(p,fixed(p,127).moveByte(ROUTINE,8)); step(p.harness(),neutral());
        for(int wait=0;wait<22;wait++) step(p.harness(),neutral());
        step(p.harness(),neutral()); step(p.harness(),neutral());
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED,p.harness().module().pastLoadState().phase());
        assertFalse(p.frame().closed()); return p;
    }
    private static CdObjectState fixed(Prepared p, int whole) {
        var old = p.placement().pool().state(whole); assertEquals(0, old.id());
        return old.moveByte(ID, 0x3A).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0).moveWord(X, 288)
                .moveWord(RESULTS_DEST_X, 288).moveWord(RESULTS_SCREEN_Y_WORD, 204).moveWord(Y, 0x2468)
                .moveWord(Y_FRACTION, 0x1357).moveByte(PRIORITY, 0).moveByte(MAP_FRAME, 0).moveWord(TILE, 0x83C4)
                .moveLong(MAPPINGS, p.assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS).range().nativeAddress());
    }
    private static void seed(Prepared p, CdObjectState row) {
        var before = p.placement().capture(); p.placement().pool().write(row);
        assertEquals(before.status(), p.placement().status());
        assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var other : before.pool().slots()) if (other.identity().wholeIndex() != row.identity().wholeIndex())
            assertEquals(other, p.placement().pool().state(other.identity().wholeIndex()));
    }
    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral()); }
}
