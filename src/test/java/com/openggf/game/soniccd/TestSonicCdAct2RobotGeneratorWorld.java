package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.openggf.game.soniccd.resources.CdR12aCameraArtResidency;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine public cold/Past/Results/normal R12A/Title ingress, with labelled constructed dynamic64 inputs.
 * N1ecc5f3a Robot Generator25-125, Object Functions137-196/228-237 and Object Spawner385-446.
 * Tests authored before the two owned production mirrors existed. All Java/World tests UNRUN.
 * Ordinary inactive-Past routine0/2 only; no natural13386/pixel/all-routine certification.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2RobotGeneratorWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void actualCachedAssetsAndAppendOnlyCallCensusKeepTheExistingSourceOwner() throws Exception {
        var s=scene();var b=robot(s);var before=captureNative(s.h());
        assertSame(s.assets().objects().robotGenerator(),b.assets().visual());assertEquals(0x20E4F6,b.assets().mappingsAddress());
        assertEquals(0x20E734,b.assets().explosionScheduleAddress());assertEquals(22,b.assets().baseTiles().length());
        assertEquals(130,SonicCdGameModule.Act2LoadCall.values().length);assertEquals(119,SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_RING_AWARD.ordinal());
        assertEquals(120,SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY.ordinal());assertEquals(121,SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM.ordinal());
        assertEquals(122,SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY.ordinal());assertEquals(38,SonicCdGameModule.PastSound.class.getPermittedSubclasses().length);
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runRobotGenerator",new Class<?>[]{int.class},127));assertNativeCapture(before,s.h());
    }

    @Test void dirtyInitUsesActualRawFiveGlobalsAndPreservesAllNonwrittenBytesAcrossFullAndCourseReplay() throws Exception {
        // Bit5 selects native static pieces; this success fixture retains dirty flags in counted-table mode.
        var s=scene();var source=image(s,127,0).moveByte(SPRITE_FLAGS,0xDF).moveByte(FLAGS,3);seed(s,source);
        var b=robot(s);var original=(CdRobotGenerator.DrawObjectCall)actualField(b,"drawObject");var expected=new AtomicReference<CdObjectState>();
        set(b,"drawObject",(CdRobotGenerator.DrawObjectCall)c->{
            var i=actual(s.visit(),"activeRobotGenerator");var g=(CdRobotGenerator.Globals)actual(i,"globals");var gates=s.frame().environment().movement().gates();
            assertEquals(gates.timeZone(),g.timeZone());assertEquals(gates.zoneAct()&255,g.act());assertEquals(gates.timeWarpDirection(),g.timeWarpDirection());
            assertEquals(((SonicCdPlayerPresentation)actual(s.visit(),"presentation")).act2ForegroundImage().cameraX(),g.cameraXWord());
            var dev=actual(actual(actual(s.visit(),"values"),"globals"),"retainedDev");assertEquals(actual(dev,"goodFuture"),g.goodFuture());
            assertEquals(initialized(source,g),c.expectedSource());expected.set(c.expectedSource());
            var actor=s.frame().captureActor();var shared=s.frame().captureShared();var values=actual(s.visit(),"values");var ledger=actual(s.visit(),"sounds");
            original.drawObject(c);assertEquals(actor,s.frame().captureActor());assertEquals(shared,s.frame().captureShared());
            assertSame(values,actual(s.visit(),"values"));assertSame(ledger,actual(s.visit(),"sounds"));
        });
        replayNext(s);assertScreen(expected.get(),s.placement().pool().state(127));assertEquals(0x0400,s.placement().pool().state(127).word(TILE));
        assertEquals(List.of(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY),familyCalls(s));
    }

    @Test void dirtyStaticFlagKeepsInitAndDrawPrefixBeforeItsExplicitUnownedMappingBoundary() throws Exception {
        var s=scene();var source=image(s,127,0).moveByte(SPRITE_FLAGS,0xFF).moveByte(FLAGS,3);seed(s,source);
        var b=robot(s);var draw=(CdRobotGenerator.DrawObjectCall)actualField(b,"drawObject");var reached=new AtomicReference<CdObjectState>();
        set(b,"drawObject",(CdRobotGenerator.DrawObjectCall)c->{reached.set(c.expectedSource());draw.drawObject(c);});beforeForward(s);
        var failure=assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));
        assertTrue(failure.getMessage().contains("static=true"));assertNotNull(failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("no admitted raw static piece at this pointer"));
        assertNotNull(reached.get());assertEquals(0xFF,reached.get().spriteFlags());assertEquals(2,reached.get().routine());
        assertEquals(reached.get().moveByte(SPRITE_FLAGS,0x7F),s.placement().pool().state(127));retired(s);
    }

    @Test void mainSkipsAnimationSolidPlayerAndSoundAndRetainsDirtyTimersFractionsAndPointers() throws Exception {
        var s=scene();var source=image(s,127,2).moveByte(MAP_FRAME,2).moveByte(ANIMATION_TIME,0x80).moveByte(COLLISION_STATUS,0xFF)
                .moveLong(ROBOT_GENERATOR_SCHEDULE,0x12345678).moveWord(ROBOT_GENERATOR_TIMER,0xBEEF).moveWord(ROBOT_GENERATOR_FLOAT_STEP,0xA55A);seed(s,source);
        var b=robot(s);set(b,"solidCommit",(CdRobotGenerator.SolidCommit)c->{throw new AssertionError("Unreached Solid");});
        set(b,"goodFutureStore",(CdRobotGenerator.GoodFutureStore)c->{throw new AssertionError("Unreached global store");});
        set(b,"addPoints",(CdRobotGenerator.AddPointsCall)c->{throw new AssertionError("Unreached AddPoints");});set(b,"fmCall",(CdRobotGenerator.FmCall)c->{throw new AssertionError("Unreached FM");});
        replayNext(s);assertScreen(source,s.placement().pool().state(127));
    }

    @Test void allEightRawMappingFramesUseExistingCpuDrainWithoutASecondArtOwner() throws Exception {
        var s=scene();var base=s.h().mode().getRewindRegistry().capture();
        for(int frame=0;frame<8;frame++) {s.h().mode().getRewindRegistry().restore(base);seed(s,image(s,127,2).moveByte(MAP_FRAME,frame));replayNext(s);
            assertEquals(s.assets().objects().robotGenerator().mappingFrames().get(frame).pieces().size(),cpu(s).provenance().stream().filter(v->v.slotRef().wholeIndex()==127).count());}
    }

    @Test void nativeDrawClearsBit7BeforeWidthCullAndUsesActualCamera() throws Exception {
        var s=scene();var base=s.h().mode().getRewindRegistry().capture();var p=(SonicCdPlayerPresentation)actualField(s.visit(),"presentation");var camera=p.act2ForegroundImage();
        var source=image(s,127,2).moveWord(X,camera.cameraX()+320).moveWord(Y,camera.cameraYWord()+100).moveByte(WIDTH,0).moveByte(Y_RADIUS,0).moveByte(SPRITE_FLAGS,0x84);seed(s,source);
        replayNext(s);assertEquals(4,s.placement().pool().state(127).spriteFlags());assertFalse(cpu(s).provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==127));
        s.h().mode().getRewindRegistry().restore(base);seed(s,source.moveByte(WIDTH,1));replayNext(s);assertEquals(0x84,s.placement().pool().state(127).spriteFlags());
    }

    @Test void outsideTailClearsOnlyRealSavedAddressThenDeletesAll64() throws Exception {
        var s=scene();var source=image(s,127,2).moveWord(X,0x7000).moveByte(SAVED_FLAGS_ID,185).moveByte(SPRITE_FLAGS,0x84);seed(s,source);
        var b=robot(s);var draw=(CdRobotGenerator.DrawObjectCall)actualField(b,"drawObject");var reached=new AtomicReference<CdRobotGenerator.DrawCall>();
        set(b,"drawObject",(CdRobotGenerator.DrawObjectCall)c->{reached.set(c);draw.drawObject(c);});replayNext(s);
        assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());var expected=reached.get().actualCut().savedFlags().copy();expected[558]&=0x7F;
        assertArrayEquals(expected,s.placement().capture().savedFlags().copy());
    }

    @Test void noOpBeforePublicationCannotReachHeaderStore() throws Exception {
        var s=scene();var source=image(s,127,0);seed(s,source);var b=robot(s);set(b,"observer",observer((CdRobotGenerator.DispatchObserver)actualField(b,"observer"),true,false));beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,s.placement().pool().state(127));assertTrue(familyCalls(s).isEmpty());retired(s);
    }

    @Test void noOpAfterPublicationStopsAtExactHeaderBeforeTableAndDraw() throws Exception {
        var s=scene();var source=image(s,127,0);seed(s,source);var b=robot(s);set(b,"observer",observer((CdRobotGenerator.DispatchObserver)actualField(b,"observer"),false,true));beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(header(source),s.placement().pool().state(127));assertTrue(familyCalls(s).isEmpty());retired(s);
    }

    @Test void equalClonesAndDuplicatePublicationsCannotBorrowOriginalOrdinalReceipt() throws Exception {
        var s=scene();seed(s,image(s,127,0));var b=robot(s);var original=(CdRobotGenerator.DispatchObserver)actualField(b,"observer");var seen=new AtomicReference<Object>();
        set(b,"observer",new CdRobotGenerator.DispatchObserver(){
            public void beforePublication(CdRobotGenerator.Publication c){seen.set(c);assertThrows(IllegalStateException.class,()->original.beforePublication(new CdRobotGenerator.Publication(c.ordinal(),c.kind(),c.actualCut(),c.expectedSource(),c.before(),c.output(),c.savedByteIndex(),c.previousValue(),c.value())));original.beforePublication(c);assertThrows(IllegalStateException.class,()->original.beforePublication(c));}
            public void afterPublication(CdRobotGenerator.Publication c){original.afterPublication(c);assertThrows(IllegalStateException.class,()->original.afterPublication(c));}
            public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}
        });replayNext(s);assertFalse(b.isCurrentCall(seen.get()));assertFalse(b.isCurrentCall(null));
        var cut=captureNative(s.h());assertThrows(IllegalStateException.class,()->original.beforePublication((CdRobotGenerator.Publication)seen.get()));assertNativeCapture(cut,s.h());
    }

    @Test void noOpDrawCannotReachSavedClearOrDelete() throws Exception {
        var s=scene();var source=image(s,127,2).moveWord(X,0x7000).moveByte(SPRITE_FLAGS,0x84);seed(s,source);var saved=s.placement().capture().savedFlags();
        set(robot(s),"drawObject",(CdRobotGenerator.DrawObjectCall)c->{});beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));
        assertEquals(source,s.placement().pool().state(127));assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());retired(s);
    }

    @Test void clonedAndDuplicateDrawCannotPublishQueueWorkOrPermitAnotherTail() throws Exception {
        var s=scene();seed(s,image(s,127,2));var b=robot(s);var original=(CdRobotGenerator.DrawObjectCall)actualField(b,"drawObject");
        set(b,"drawObject",(CdRobotGenerator.DrawObjectCall)c->{assertThrows(IllegalStateException.class,()->original.drawObject(new CdRobotGenerator.DrawCall(c.actualCut(),c.expectedSource())));original.drawObject(c);assertThrows(IllegalStateException.class,()->original.drawObject(c));});replayNext(s);
    }

    @Test void retiredOriginalDrawCannotReusePublicSourceAndHealthyCapture() throws Exception {
        var s=scene();seed(s,image(s,127,2));var b=robot(s);var draw=(CdRobotGenerator.DrawObjectCall)actualField(b,"drawObject");var seen=new AtomicReference<CdRobotGenerator.DrawCall>();
        set(b,"drawObject",(CdRobotGenerator.DrawObjectCall)c->{seen.set(c);draw.drawObject(c);});replayNext(s);var before=captureNative(s.h());
        assertFalse(b.isCurrentCall(seen.get()));assertThrows(IllegalStateException.class,()->draw.drawObject(seen.get()));assertNativeCapture(before,s.h());retired(s);
    }

    @Test void faultAfterGenuineDrawKeepsBclrAndOriginalThrowableWithoutReplayingTail() throws Exception {
        var s=scene();var source=image(s,127,2).moveWord(X,0x7000).moveByte(SPRITE_FLAGS,0x84);seed(s,source);var b=robot(s);var original=(CdRobotGenerator.DrawObjectCall)actualField(b,"drawObject");var sentinel=new AssertionError("after genuine Robot Draw");
        set(b,"drawObject",(CdRobotGenerator.DrawObjectCall)c->{original.drawObject(c);throw sentinel;});beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(source.moveByte(SPRITE_FLAGS,4),s.placement().pool().state(127));assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void faultBeforeDeleteRetainsOnlyDrawAndRealSavedClearPrefix() throws Exception {
        var s=scene();var source=image(s,127,2).moveWord(X,0x7000).moveByte(SAVED_FLAGS_ID,185).moveByte(SPRITE_FLAGS,0x84);seed(s,source);var b=robot(s);
        var original=(CdRobotGenerator.DispatchObserver)actualField(b,"observer");var sentinel=new AssertionError("before Robot DeleteObject");var saved=new AtomicReference<byte[]>();
        set(b,"observer",new CdRobotGenerator.DispatchObserver(){public void beforePublication(CdRobotGenerator.Publication c){original.beforePublication(c);if(c.kind()==CdRobotGenerator.StoreKind.DELETE){saved.set(c.actualCut().savedFlags().copy());throw sentinel;}}
            public void afterPublication(CdRobotGenerator.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
        beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(source.moveByte(SPRITE_FLAGS,4),s.placement().pool().state(127));
        assertArrayEquals(saved.get(),s.placement().capture().savedFlags().copy());assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void reentryCaptureAndOtherFamilyCannotBorrowActualRobotScope() throws Exception {
        var s=scene();seed(s,image(s,127,0));var b=robot(s);var original=(CdRobotGenerator.DispatchObserver)actualField(b,"observer");
        set(b,"observer",new CdRobotGenerator.DispatchObserver(){public void beforePublication(CdRobotGenerator.Publication c){
            assertThrows(IllegalStateException.class,()->b.run(127,new CdRobotGenerator.Globals(0,1,1,0,0),null));
            assertThrows(IllegalStateException.class,()->s.h().mode().getRewindRegistry().capture());assertThrows(IllegalStateException.class,()->s.h().mode().getRewindRegistry().captureCourse());
            assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runTunnelDoorSplash",new Class<?>[]{int.class},126));original.beforePublication(c);}
            public void afterPublication(CdRobotGenerator.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});replayNext(s);
    }

    @Test void unrelatedFull64MutationFailsBeforeOriginalSourcePublication() throws Exception {
        var s=scene();var source=image(s,127,0);seed(s,source);var b=robot(s);var original=(CdRobotGenerator.DispatchObserver)actualField(b,"observer");
        set(b,"observer",new CdRobotGenerator.DispatchObserver(){public void beforePublication(CdRobotGenerator.Publication c){original.beforePublication(c);s.placement().pool().write(s.placement().pool().state(126).moveByte(ROUTINE_2,0x5A));}
            public void afterPublication(CdRobotGenerator.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,s.placement().pool().state(127));retired(s);
    }

    @Test void foreignEqualPlacementCannotBorrowTheActualWorldOwner() throws Exception {
        var s=scene();var source=image(s,127,0);seed(s,source);var foreign=CdObjectPlacementRuntime.fromAssets(s.assets(),s.placement().capture().savedFlags());
        var detached=new Scene(s.h(),s.visit(),s.frame(),foreign,s.assets());var row=image(detached,127,0);foreign.pool().write(row);assertArrayEquals(source.copyBytes(),row.copyBytes());set(robot(s),"placement",foreign);beforeForward(s);
        var failure=assertThrows(IllegalArgumentException.class,()->step(s.h(),neutral()));assertEquals("Foreign CD placement source/runtime capture",failure.getMessage());
        assertEquals(source,s.placement().pool().state(127));assertArrayEquals(row.copyBytes(),foreign.pool().state(127).copyBytes());retired(s);
    }

    @Test void explodingAndRebuildRemainExplicitWorldScopeBoundariesWithoutAStore() throws Exception {
        for(int routine:new int[]{4,6}) {var s=scene();var source=image(s,127,routine);seed(s,source);beforeForward(s);
            var failure=assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));assertTrue(failure.getMessage().contains("routine0/2"));assertEquals(source,s.placement().pool().state(127));retired(s);}
    }

    @Test void invalidRawRoutineKeepsNativeDispatchFailureWithoutNormalizationOrAWorldStore() throws Exception {
        for(int routine:new int[]{1,3,8,255}) {var s=scene();var source=image(s,127,routine);seed(s,source);beforeForward(s);
            var failure=assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class,()->step(s.h(),neutral()));
            assertEquals(CdRobotGenerator.UnsupportedBoundary.ROUTINE_DISPATCH,failure.boundary());assertEquals(source,s.placement().pool().state(127));retired(s);}
    }

    @Test void actualRobotCpuUsesOnlyRetainedCurrentAndConstructedMissingKnowledgeHasNoAssetFallback() throws Exception {
        var s=scene();seed(s,image(s,127,2));var b=robot(s);var draw=(CdRobotGenerator.DrawObjectCall)actualField(b,"drawObject");
        set(b,"drawObject",(CdRobotGenerator.DrawObjectCall)c->{var v=actual(s.visit(),"values");var section=actual(v,"section");draw.drawObject(c);assertSame(v,actual(s.visit(),"values"));assertSame(section,actual(actual(s.visit(),"values"),"section"));});
        replayNext(s);var sat=cpu(s);assertTrue(sat.provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==127));
        var selected=s.h().module().act2SelectedDisplay().inputs();var actual=s.h().module().act2LoadState().section().residency();
        var unknown=withoutRobotWindow(actual);var projected=SonicCdAct2Display.project(new SonicCdAct2Display.Inputs(Optional.of(sat),CdAssetBytes.copyOf(sat.bytes()),
                selected.palette(),selected.hscroll(),selected.vscrollLongBits(),true,selected.foreground(),selected.background(),unknown,selected.player()));
        var failure=assertInstanceOf(SonicCdPastDisplay.Blocked.class,projected.projection()).failure();assertEquals(SonicCdPastDisplay.Boundary.UNKNOWN_PATTERN,failure.boundary());
        assertTrue(failure.nativePatternId()>=0x400 && failure.nativePatternId()<0x417);assertSame(actual,s.h().module().act2LoadState().section().residency());
    }
    /** A constructed immutable projection control, never installed in the actual current owner. */
    private static CdR12aCameraArtResidency.State withoutRobotWindow(CdR12aCameraArtResidency.State actual) {
        var out=CdR12aCameraArtResidency.unknown();
        for(var region:CdR12aCameraArtResidency.Region.values())for(int offset=0;offset<region.byteLength();offset+=32) {
            int at=region.destinationWord()+offset;if((at<0x8000 || at>=0x82E0) && actual.region(region).knownByte(offset).isPresent())
                out=CdR12aCameraArtResidency.withTile(out,at,CdR12aCameraArtResidency.readTile(actual,at>>>5));
        }
        return out;
    }

    private static CdRobotGenerator robot(Scene s) throws Exception{return (CdRobotGenerator)actualField(s.visit(),"robotGenerator");}
    private static CdObjectState image(Scene s,int whole,int routine) {
        var m=s.frame().state().motion();return s.placement().pool().state(whole).moveByte(ID,0x2D).moveByte(ROUTINE,routine).moveByte(SUBTYPE,0x91).moveByte(SUBTYPE2,0x67)
                .moveWord(X,(m.centreX16_16()>>>16)+64).moveWord(Y,m.centreY16_16()>>>16).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468)
                .moveByte(SPRITE_FLAGS,4).moveByte(FLAGS,0).moveByte(WIDTH,0x22).moveByte(X_RADIUS,0x22).moveByte(Y_RADIUS,0x20).moveByte(PRIORITY,4).moveByte(COLLISION_TYPE,0x13).moveByte(COLLISION_STATUS,0)
                .moveByte(SAVED_FLAGS_ID,1).moveByte(ROUTINE_2,0xE3).moveWord(X_VELOCITY,0xCAFE).moveWord(Y_VELOCITY,0xBEEF)
                .moveLong(MAPPINGS,0x20E4F6).moveWord(TILE,0x0400).moveByte(MAP_FRAME,2).moveByte(ANIMATION,0xE1).moveByte(PREVIOUS_ANIMATION,0x77).moveByte(ANIMATION_FRAME,0x52).moveByte(ANIMATION_TIME,0x34);
    }
    private static CdObjectState header(CdObjectState s){return s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(PRIORITY,4).moveByte(X_RADIUS,0x22).moveByte(WIDTH,0x22).moveByte(Y_RADIUS,0x20);}
    private static CdObjectState initialized(CdObjectState s,CdRobotGenerator.Globals g){
        int d0=g.act()<<2;d0=(d0&0xFF00)|((d0+g.timeZone())&255);assertEquals(10,(short)((d0+d0)&65535));
        return header(s).moveWord(TILE,0x0400).moveLong(MAPPINGS,0x20E4F6).moveLong(ROBOT_GENERATOR_SCHEDULE,0x20E734)
                .moveWord(ROBOT_GENERATOR_BASE_Y,s.centreY()).moveWord(ROBOT_GENERATOR_TIMER,4).moveWord(ROBOT_GENERATOR_FLOAT_STEP,1).moveByte(MAP_FRAME,(g.goodFuture()==0?2:0)+(g.timeZone()==0?1:0));
    }
    private static void assertScreen(CdObjectState expected,CdObjectState actual){assertEquals(expected.moveByte(SPRITE_FLAGS,expected.spriteFlags()&127),actual.moveByte(SPRITE_FLAGS,actual.spriteFlags()&127));}
    private static Object actual(Object owner,String name){try{return actualField(owner,name);}catch(Exception e){throw new AssertionError(e);}}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.Act2LoadCall> rawCalls(Scene s) throws Exception{return (List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(s.visit(),"values"),"calls");}
    private static List<SonicCdGameModule.Act2LoadCall> familyCalls(Scene s) throws Exception{return rawCalls(s).stream().filter(v->v==SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY).toList();}
    private static CdRobotGenerator.DispatchObserver observer(CdRobotGenerator.DispatchObserver original,boolean skipBefore,boolean skipAfter){return new CdRobotGenerator.DispatchObserver(){
        public void beforePublication(CdRobotGenerator.Publication c){if(!skipBefore)original.beforePublication(c);}public void afterPublication(CdRobotGenerator.Publication c){if(!skipAfter)original.afterPublication(c);}
        public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}};}
    private static Scene scene() throws Exception {
        var old=oldReturned(); step(old.harness(),neutral()); var h=old.harness();
        for(int service=0;service<64;service++) {
            step(h,neutral());
            if(h.module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN)) {
                var visit=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");
                var scene=new Scene(h,visit,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(visit,"placement"),h.module().act2LoadState().assets());
                assertEquals(0,scene.placement().pool().state(127).id()); healthy(scene);
                for(int ordinary=0;ordinary<256;ordinary++) {
                    if(scene.frame().environment().controls().controlLocked()==0
                            && scene.h().module().act2LoadState().section().queue().headSource()==0) {
                        assertEquals(1,scene.frame().environment().movement().gates().timeZone());return scene;
                    }
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
    private static void replayNext(Scene s) throws Exception {replayNext(s,neutral());}
    private static void replayNext(Scene s,LogicalInputSnapshot input) throws Exception {
        var h=s.h(); var registry=h.mode().getRewindRegistry(); var full=registry.capture(); var course=registry.captureCourse();
        var before=captureNative(h); var source=s.frame().stageSource(); var world=h.mode().getWorldSession();
        long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        registry.restore(full); assertNativeCapture(before,h); registry.restoreCourse(course); assertNativeCapture(before,h);
        step(h,input); healthy(s); var after=captureNative(h);
        registry.restore(full); assertNativeCapture(before,h); step(h,input); healthy(s); assertNativeCapture(after,h);
        registry.restoreCourse(course); assertNativeCapture(before,h); step(h,input); healthy(s); assertNativeCapture(after,h);
        assertSame(source,s.frame().stageSource()); assertSame(world,h.mode().getWorldSession());
        assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }
    private static void healthy(Scene s) throws Exception {
        assertSame(s.frame(),s.h().module().requirePlayerFrame()); assertFalse(s.frame().closed());
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,s.h().module().act2LoadState().calls().getLast()); retired(s);
    }
    private static void retired(Scene s) throws Exception {
        for(String name:new String[]{"activeRobotGenerator","activeTunnelDoorSplash","activeDoor","activeChildEffect","activeLogMosqui","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"robotPort","drawingRobotGenerator","splashPort","drawingSplash","doorPort","drawingDoor","drawingChildEffect","logMosquiPort","drawingLogMosqui","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
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
    private static Prepared oldReturned() throws Exception {
        var old = ready();
        for (int call = old.returned() + 1; call <= 10419; call++) step(old.harness(), controller(call));
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED, old.harness().module().pastLoadState().phase());
        assertFalse(old.frame().closed());
        return old;
    }
    /** Exact immutable controller fixture recipe; finite endpoint12884 supplies no gameplay values. */
    private static LogicalInputSnapshot controller(int call) {
        if(call<1 || call>12884)throw new IllegalArgumentException("Bounded source recipe ends12884");
        if(call<=8276)return route(call);
        int directions=8,actions=call<=8308?2:0;
        if(call>=9001 && call<=9203)directions=4;
        else if(call>=9206 && call<=9217)directions=0;
        else if(call>=9218 && call<=9263){directions=2;actions=call==9219?2:0;}
        else if(call>=9336 && call<=9367)actions=2;
        if(call>=11214)actions=(call-11214)%128<32?2:0;
        if(call==11587 || call>=12671 && call<=12699)actions=0;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions,0,actions,0,false,false),PlayerInputState.neutral());
    }

    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),PlayerInputState.neutral()); }
}
