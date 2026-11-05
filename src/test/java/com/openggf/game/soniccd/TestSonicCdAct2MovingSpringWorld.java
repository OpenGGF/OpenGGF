package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdR12aCameraArtResidency;
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

/** Real cold/Past/Results/ordinary Act2/Title/PLC ingress; all edited dynamic inputs below are constructed.
 * N1ecc5f3a Spring/Main.asm:7-96 and130-167; Object Functions.asm:10-40; Object Spawner.asm:329-340.
 * Tests were written while both owned production mirrors were absent. All Java/World/EGL work UNRUN.
 * No natural13423 floor, allocation, child, current readiness or pixel qualification.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2MovingSpringWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void cachedActualSourceAndAppendOnlyDiagnosticKeepOnePlacementTerrainAndVisualOwner() throws Exception {
        var s=scene();var b=wheel(s);var before=captureNative(s.h());
        assertSame(s.assets().objects().movingSpring(),b.assets().visual());assertEquals(0x209A96,b.assets().mappingsAddress());
        assertEquals(74,b.assets().objectTileIds().length());assertEquals(2,b.assets().visual().mappingFrames().size());
        assertEquals(124,SonicCdGameModule.Act2LoadCall.values().length);assertEquals(122,SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY.ordinal());
        assertEquals(123,SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY.ordinal());
        assertSame(s.placement(),actual(b,"placement"));assertNativeCapture(before,s.h());
    }

    @Test void initPublishesDirtyFirstFreeChildThenActualAlignWithoutDrawOrAnimation() throws Exception {
        var s=scene();clearConstructedDynamic(s);var source=image(s,127,0);seed(s,source);
        var dirty=s.placement().pool().state(32).moveByte(ROUTINE,0x6A).moveByte(SPRITE_FLAGS,0x91).moveByte(SAVED_FLAGS_ID,0xA4)
                .moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468).moveByte(SPRING_PARENT_X_OFFSET,0x31);seed(s,dirty);
        var b=wheel(s);set(b,"drawObject",(CdMovingSpring.DrawObjectCall)c->{throw new AssertionError("Init/Align cannot Draw");});
        var original=(CdMovingSpring.DispatchObserver)actual(b,"observer");var child=new AtomicReference<CdObjectState>();
        set(b,"observer",new CdMovingSpring.DispatchObserver(){
            public void beforePublication(CdMovingSpring.Publication c){original.beforePublication(c);}
            public void afterPublication(CdMovingSpring.Publication c){original.afterPublication(c);if(c.kind()==CdMovingSpring.StoreKind.CHILD_STORE){
                var expected=dirty.moveByte(ID,0x0A).moveWord(X,source.centreX()).moveWord(Y,source.centreY()-0x10)
                        .moveByte(SPRING_PARENT_Y_OFFSET,0xF0).moveWord(SPRING_PARENT,source.identity().nativeAddress24()&65535).moveByte(SUBTYPE,source.subtype());
                assertEquals(expected,c.output());child.set(c.output());assertEquals(expected,s.placement().pool().state(32));}}
            public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
        var ready=header(source).moveWord(TILE,0x0338);var floor=CdObjectCollisionCheck.objGetFloorDist(terrain(s),ready);
        replayNext(s);assertNotNull(child.get());assertEquals(child.get(),s.placement().pool().state(32));
        assertEquals(aligned(ready,floor.distance()),s.placement().pool().state(127));assertEquals(floor.primaryAngle(),s.frame().environment().movement().globals().primaryAngle());
    }

    @Test void alignNegativeAndNonnegativeUseActualTerrainAndRetainEveryFractionAndSavedByte() throws Exception {
        var s=scene();clearConstructedDynamic(s);var source=image(s,127,2);var base=s.h().mode().getRewindRegistry().capture();
        for(int penetration:new int[]{-8,8}) {s.h().mode().getRewindRegistry().restore(base);
            var ground=source.addWord(Y,CdObjectCollisionCheck.objGetFloorDist(terrain(s),source).distance());
            var row=ground.addWord(Y,penetration);seed(s,row);var distance=CdObjectCollisionCheck.objGetFloorDist(terrain(s),row);
            replayNext(s);assertEquals(aligned(row,distance.distance()),s.placement().pool().state(127));}
    }

    @Test void genuineAnglePortChangesOnlyPrimaryScratchAndPreservesCompleteActorAndNonangleEnvironment() throws Exception {
        var s=scene();clearConstructedDynamic(s);seed(s,image(s,127,2));var b=wheel(s);var original=(CdMovingSpring.PrimaryAngleCommit)actual(b,"angleCommit");var count=new int[1];
        set(b,"angleCommit",(CdMovingSpring.PrimaryAngleCommit)c->{var state=s.frame().state();var actor=s.frame().captureActor();var env=s.frame().environment();
            original.commit(c);count[0]++;assertSame(state,s.frame().state());assertEquals(actor,s.frame().captureActor());
            assertSame(env.controls(),s.frame().environment().controls());assertSame(env.globals(),s.frame().environment().globals());
            assertSame(env.dynamicSlots(),s.frame().environment().dynamicSlots());assertSame(env.movement().gates(),s.frame().environment().movement().gates());
            assertEquals(c.value(),s.frame().environment().movement().globals().primaryAngle());});replayNext(s);assertTrue(count[0]>=3);
    }

    @Test void actualPrezeroFaultKeepsOriginalThrowableAndCompletedScratchHeaderChildPrefix() throws Exception {
        var s=scene();clearConstructedDynamic(s);var source=image(s,127,0);seed(s,source);var b=wheel(s);var original=(CdMovingSpring.PrimaryAngleCommit)actual(b,"angleCommit");var sentinel=new AssertionError("after native MovingSpring prezero");
        set(b,"angleCommit",(CdMovingSpring.PrimaryAngleCommit)c->{original.commit(c);throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(header(source).moveWord(TILE,0x338),s.placement().pool().state(127));
        assertEquals(0,s.frame().environment().movement().globals().primaryAngle());assertEquals(0x0A,s.placement().pool().state(32).id());assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void finiteSelectorFailureKeepsOnlyTheActualHeaderBeforeAnyChildOrFloor() throws Exception {
        var s=scene();clearConstructedDynamic(s);var row=image(s,127,0).moveByte(SUBTYPE2,1);seed(s,row);beforeForward(s);
        var failure=assertThrows(CdMovingSpring.UnsupportedMovingSpring.class,()->step(s.h(),neutral()));
        assertEquals(CdMovingSpring.UnsupportedBoundary.TILE_ID_SUBTYPE2,failure.boundary());assertEquals(header(row),s.placement().pool().state(127));
        for(int whole=32;whole<127;whole++)assertArrayEquals(new byte[64],s.placement().pool().state(whole).copyBytes());retired(s);
    }

    @Test void nonzeroRawTimeStopWordSkipsFloorSupportMovementAndAnimationButStillDraws() throws Exception {
        for(int timer:new int[]{1,0x100,0x8000,0xFFFF}) {var s=scene();clearConstructedDynamic(s);timeStopConstructed(s,timer);var row=image(s,127,4).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,0).moveByte(FLAGS,8).moveByte(STAND_LINK_3D,255);seed(s,row);
            var b=wheel(s);set(b,"angleCommit",(CdMovingSpring.PrimaryAngleCommit)c->{throw new AssertionError("stopped floor");});set(b,"supportRead",(CdMovingSpring.SupportRead)c->{throw new AssertionError("stopped support");});
            replayNext(s);assertScreen(row,s.placement().pool().state(127));}
    }

    @Test void mainUsesNativeUnsignedTurnRawMoveFractionsAndOneSharedAnimator() throws Exception {
        var s=scene();clearConstructedDynamic(s);var row=image(s,127,4).moveWord(MOVING_SPRING_BASE_Y,0).moveWord(X_VELOCITY,0x180).moveWord(Y_VELOCITY,0xFF80);seed(s,row);
        var floor=CdObjectCollisionCheck.objGetFloorDist(terrain(s),row);var adjusted=row.addWord(Y,floor.distance());
        if(((adjusted.word(MOVING_SPRING_BASE_Y)-adjusted.centreY())&65535)>=12)adjusted=adjusted.moveWord(X_VELOCITY,-adjusted.signedWord(X_VELOCITY));
        var moved=CdPlayerMovement.moveObject(new CdPlayerMovement.RawObjectMotion(adjusted.longBits(X_POSITION),adjusted.longBits(Y_POSITION),adjusted.signedWord(X_VELOCITY),adjusted.signedWord(Y_VELOCITY),adjusted.flags(),adjusted.unsignedByte(RAW_CONTROL_2C),adjusted.unsignedByte(STAND_LINK_3D)),Optional.empty());
        replayNext(s);var actual=s.placement().pool().state(127);assertEquals(moved.centreX16_16(),actual.longBits(X_POSITION));assertEquals(moved.centreY16_16(),actual.longBits(Y_POSITION));
        assertEquals(adjusted.word(X_VELOCITY),actual.word(X_VELOCITY));assertEquals(adjusted.word(Y_VELOCITY),actual.word(Y_VELOCITY));assertEquals(8,actual.unsignedByte(ANIMATION_TIME));assertEquals(1,actual.unsignedByte(ANIMATION_FRAME));
    }

    @Test void heldAnimationTimerDoesNotReadTheUnreachedDirtyDirectory() throws Exception {
        var s=scene();clearConstructedDynamic(s);seed(s,image(s,127,4).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_TIME,2));
        replayNext(s);assertEquals(1,s.placement().pool().state(127).unsignedByte(ANIMATION_TIME));
    }

    @Test void reachedNativeAnimationFailureKeepsFloorMoveAndTimerPrefix() throws Exception {
        var s=scene();clearConstructedDynamic(s);var row=image(s,127,4).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_TIME,0);seed(s,row);beforeForward(s);
        assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));var after=s.placement().pool().state(127);
        assertEquals(255,after.unsignedByte(ANIMATION_TIME));assertEquals(255,after.unsignedByte(ANIMATION));assertNotEquals(row.longBits(X_POSITION),after.longBits(X_POSITION));retired(s);
    }

    @Test void supportIsLazyAndUsesActualPrimaryOrDynamicIdFlagsWithoutMutatingEither() throws Exception {
        for(int link:new int[]{0,126}) {var s=scene();clearConstructedDynamic(s);if(link==126)seed(s,s.placement().pool().state(126).moveByte(ID,0x24).moveByte(FLAGS,1));
            var row=image(s,127,4).moveByte(FLAGS,8).moveByte(STAND_LINK_3D,link);seed(s,row);var b=wheel(s);var original=(CdMovingSpring.SupportRead)actual(b,"supportRead");var reads=new int[1];
            set(b,"supportRead",(CdMovingSpring.SupportRead)c->{var actor=s.frame().captureActor();var shared=s.frame().captureShared();var pool=s.placement().capture();var value=original.read(c);reads[0]++;
                assertEquals(link,value.orElseThrow().wholeIndex());assertEquals(actor,s.frame().captureActor());assertEquals(shared,s.frame().captureShared());assertEquals(pool.pool().slots(),s.placement().capture().pool().slots());return value;});
            replayNext(s);assertEquals(3,reads[0]);}
    }

    @Test void reservedSupportStopsAtItsActualFirstReadAfterFloorAndBeforeMove() throws Exception {
        var s=scene();clearConstructedDynamic(s);var row=image(s,127,4).moveByte(FLAGS,8).moveByte(STAND_LINK_3D,3);seed(s,row);beforeForward(s);
        assertThrows(CdPlayerMovement.UnsupportedMotion.class,()->step(s.h(),neutral()));assertEquals(row.longBits(X_POSITION),s.placement().pool().state(127).longBits(X_POSITION));retired(s);
    }

    @Test void anchorCullIsPlainDeleteAndNeverClearsDirtySavedFlags() throws Exception {
        var s=scene();clearConstructedDynamic(s);var row=image(s,127,2).moveWord(MOVING_SPRING_ANCHOR_X,0x7000).moveByte(SAVED_FLAGS_ID,185);seed(s,row);var saved=s.placement().capture().savedFlags();
        replayNext(s);assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());
    }

    @Test void fullPoolCapacityDeleteStillRunsWrapperAndCanDeleteTheZeroSourceAgain() throws Exception {
        var s=scene();clearConstructedDynamic(s);for(int whole=32;whole<127;whole++)seed(s,s.placement().pool().state(whole).moveByte(ID,0x24));var row=image(s,127,0);seed(s,row);
        var b=wheel(s);var original=(CdMovingSpring.DispatchObserver)actual(b,"observer");var deletes=new int[1];
        int cameraBefore=((SonicCdPlayerPresentation)actual(s.visit(),"presentation")).act2ForegroundImage().cameraX();
        set(b,"observer",new CdMovingSpring.DispatchObserver(){public void beforePublication(CdMovingSpring.Publication c){original.beforePublication(c);if(c.kind()==CdMovingSpring.StoreKind.DELETE)deletes[0]++;}
            public void afterPublication(CdMovingSpring.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
        replayNext(s);assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());int expected=1+((((0-((cameraBefore-0x80)&0xFF80))&65535)>0x280)?1:0);assertEquals(3*expected,deletes[0]);
    }

    @Test void capacityFailurePerformsBothActualDeletesAtTheGenuineLaterCamera() throws Exception {
        var s=laterScene();clearConstructedDynamic(s);for(int whole=32;whole<127;whole++)seed(s,s.placement().pool().state(whole).moveByte(ID,0x24).moveByte(SPRITE_FLAGS,0x80));seed(s,image(s,127,0));
        int camera=((SonicCdPlayerPresentation)actual(s.visit(),"presentation")).act2ForegroundImage().cameraX();assertTrue((((0-((camera-0x80)&0xFF80))&65535)>0x280));
        var b=wheel(s);var original=(CdMovingSpring.DispatchObserver)actual(b,"observer");var deletes=new int[1];var saved=new AtomicReference<CdAssetBytes>();
        set(b,"observer",new CdMovingSpring.DispatchObserver(){public void beforePublication(CdMovingSpring.Publication c){original.beforePublication(c);if(c.ordinal()==1)saved.set(c.actualCut().savedFlags());if(c.kind()==CdMovingSpring.StoreKind.DELETE)deletes[0]++;}
            public void afterPublication(CdMovingSpring.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
        replayNext(s);assertEquals(6,deletes[0]);assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());assertNotNull(saved.get());assertArrayEquals(saved.get().copy(),s.placement().capture().savedFlags().copy());
    }

    @Test void bothPhysicalChildOrdersUseOnlyTheIncreasingOrdinaryWalk() throws Exception {
        for(boolean later:new boolean[]{false,true}) {var s=scene();clearConstructedDynamic(s);int parent=later?32:127,child=later?127:32;
            if(later)for(int whole=33;whole<127;whole++)seed(s,s.placement().pool().state(whole).moveByte(ID,0x24));
            seed(s,image(s,parent,0));replayNext(s);var calls=calls(s);assertTrue(calls.contains(SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY));
            assertEquals(later,calls.contains(SonicCdGameModule.Act2LoadCall.SPRING_BODY));assertEquals(0x0A,s.placement().pool().state(child).id());
            if(later)assertTrue(calls.indexOf(SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY)<calls.indexOf(SonicCdGameModule.Act2LoadCall.SPRING_BODY));
            else {assertEquals(0,s.placement().pool().state(child).routine());replayNext(s);assertTrue(calls(s).indexOf(SonicCdGameModule.Act2LoadCall.SPRING_BODY)<calls(s).indexOf(SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY));}}
    }

    @Test void originalOrdinarySpringDrawPrecedesFollowingAZeroOrReusedParent() throws Exception {
        var s=scene();clearConstructedDynamic(s);var parent=image(s,126,2);seed(s,parent);var child=s.placement().pool().state(127).moveByte(ID,0x0A).moveWord(X,parent.centreX()).moveWord(Y,parent.centreY()-0x10).moveByte(SUBTYPE,0)
                .moveWord(SPRING_PARENT,parent.identity().nativeAddress24()&65535).moveByte(SPRING_PARENT_Y_OFFSET,0xF0).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468);seed(s,child);
        replayNext(s);var before=s.placement().pool().state(127);s.placement().pool().deleteObject(126);
        var spring=(CdSpring)actual(s.visit(),"spring");var original=(CdSpring.DrawObjectCall)actual(spring,"drawObject");var expectedDraw=new AtomicReference<CdObjectState>(before);var draws=new int[1];
        set(spring,"drawObject",(CdSpring.DrawObjectCall)(pool,identity)->{var row=pool.state(identity.wholeIndex());assertEquals(expectedDraw.get().centreX(),row.centreX());assertEquals(expectedDraw.get().centreY(),row.centreY());draws[0]++;original.drawObject(pool,identity);});
        replayNext(s);var zero=s.placement().pool().state(127);assertEquals(3,draws[0]);
        assertEquals(0,zero.centreX());assertEquals(0xFFF0,zero.centreY());assertEquals(before.word(X_FRACTION),zero.word(X_FRACTION));
        expectedDraw.set(zero);seed(s,s.placement().pool().state(126).moveByte(ID,0x24).moveByte(SPRITE_FLAGS,0x80).moveWord(X,0x1234).moveWord(Y,0x5678));replayNext(s);var reused=s.placement().pool().state(127);
        assertEquals(0x1234,reused.centreX());assertEquals(0x5668,reused.centreY());
    }

    @Test void noOpBeforeAndAfterPublicationCannotMasqueradeAsCompletedNativeStores() throws Exception {
        for(boolean before:new boolean[]{true,false}) {var s=scene();clearConstructedDynamic(s);var row=image(s,127,0);seed(s,row);var b=wheel(s);var original=(CdMovingSpring.DispatchObserver)actual(b,"observer");
            set(b,"observer",new CdMovingSpring.DispatchObserver(){public void beforePublication(CdMovingSpring.Publication c){if(!before)original.beforePublication(c);}public void afterPublication(CdMovingSpring.Publication c){if(before)original.afterPublication(c);}
                public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));
            assertEquals(before?row:header(row),s.placement().pool().state(127));retired(s);}
    }

    @Test void equalClonedAndDuplicatePublicationCannotBorrowItsOriginalOrdinal() throws Exception {
        var s=scene();clearConstructedDynamic(s);seed(s,image(s,127,0));var b=wheel(s);var original=(CdMovingSpring.DispatchObserver)actual(b,"observer");var last=new AtomicReference<CdMovingSpring.Publication>();
        set(b,"observer",new CdMovingSpring.DispatchObserver(){public void beforePublication(CdMovingSpring.Publication c){last.set(c);assertThrows(IllegalStateException.class,()->original.beforePublication(new CdMovingSpring.Publication(c.ordinal(),c.kind(),c.actualCut(),c.expectedSource(),c.before(),c.output())));original.beforePublication(c);assertThrows(IllegalStateException.class,()->original.beforePublication(c));}
            public void afterPublication(CdMovingSpring.Publication c){original.afterPublication(c);assertThrows(IllegalStateException.class,()->original.afterPublication(c));}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
        replayNext(s);assertFalse(b.isCurrentCall(last.get()));var cut=captureNative(s.h());assertThrows(IllegalStateException.class,()->original.beforePublication(last.get()));assertNativeCapture(cut,s.h());
    }

    @Test void noOpAngleAndDrawCannotBorrowSameValueCompletion() throws Exception {
        for(boolean angle:new boolean[]{true,false}) {var s=scene();clearConstructedDynamic(s);seed(s,image(s,127,angle?2:4));var b=wheel(s);
            if(angle)set(b,"angleCommit",(CdMovingSpring.PrimaryAngleCommit)c->{});else {timeStopConstructed(s,1);set(b,"drawObject",(CdMovingSpring.DrawObjectCall)c->{});}
            beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));retired(s);}
    }

    @Test void reentryCaptureOtherFamilyAndUnrelatedFull64MutationAreRejected() throws Exception {
        var s=scene();clearConstructedDynamic(s);var row=image(s,127,0);seed(s,row);var b=wheel(s);var original=(CdMovingSpring.DispatchObserver)actual(b,"observer");
        set(b,"observer",new CdMovingSpring.DispatchObserver(){public void beforePublication(CdMovingSpring.Publication c){
            assertThrows(IllegalStateException.class,()->b.run(127,new CdMovingSpring.Globals(0,0)));assertThrows(IllegalStateException.class,()->s.h().mode().getRewindRegistry().capture());
            assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runRobotGenerator",new Class<?>[]{int.class},126));original.beforePublication(c);s.placement().pool().write(s.placement().pool().state(125).moveByte(ROUTINE_2,0x44));}
            public void afterPublication(CdMovingSpring.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(row,s.placement().pool().state(127));retired(s);
    }

    @Test void clonedDuplicateAndRetiredAngleSupportAndDrawCannotBorrowActualPortReceipts() throws Exception {
        for(int kind=0;kind<3;kind++) {var s=scene();clearConstructedDynamic(s);var b=wheel(s);seed(s,image(s,127,kind==0?2:4).moveByte(FLAGS,kind==1?8:0).moveByte(STAND_LINK_3D,0));
            if(kind==0){var original=(CdMovingSpring.PrimaryAngleCommit)actual(b,"angleCommit");var last=new AtomicReference<CdMovingSpring.PrimaryAngleCall>();
                set(b,"angleCommit",(CdMovingSpring.PrimaryAngleCommit)c->{last.set(c);assertThrows(IllegalStateException.class,()->original.commit(new CdMovingSpring.PrimaryAngleCall(c.actualCut(),c.expectedSource(),c.value())));original.commit(c);assertThrows(IllegalStateException.class,()->original.commit(c));});
                replayNext(s);var before=captureNative(s.h());assertFalse(b.isCurrentCall(last.get()));assertThrows(IllegalStateException.class,()->original.commit(last.get()));assertNativeCapture(before,s.h());
            }else if(kind==1){var original=(CdMovingSpring.SupportRead)actual(b,"supportRead");var last=new AtomicReference<CdMovingSpring.SupportCall>();
                set(b,"supportRead",(CdMovingSpring.SupportRead)c->{last.set(c);assertThrows(IllegalStateException.class,()->original.read(new CdMovingSpring.SupportCall(c.actualCut(),c.expectedSource(),c.wholeIndex())));var answer=original.read(c);assertThrows(IllegalStateException.class,()->original.read(c));return answer;});
                replayNext(s);var before=captureNative(s.h());assertThrows(IllegalStateException.class,()->original.read(last.get()));assertNativeCapture(before,s.h());
            }else{timeStopConstructed(s,1);var original=(CdMovingSpring.DrawObjectCall)actual(b,"drawObject");var last=new AtomicReference<CdMovingSpring.DrawCall>();
                set(b,"drawObject",(CdMovingSpring.DrawObjectCall)c->{last.set(c);assertThrows(IllegalStateException.class,()->original.drawObject(new CdMovingSpring.DrawCall(c.actualCut(),c.expectedSource())));original.drawObject(c);assertThrows(IllegalStateException.class,()->original.drawObject(c));});
                replayNext(s);var before=captureNative(s.h());assertThrows(IllegalStateException.class,()->original.drawObject(last.get()));assertNativeCapture(before,s.h());}
        }
    }

    @Test void faultAfterGenuineDrawKeepsNativeBclrAndPreventsAnchorTail() throws Exception {
        var s=scene();clearConstructedDynamic(s);timeStopConstructed(s,1);var row=image(s,127,4).moveByte(SPRITE_FLAGS,0x84).moveWord(MOVING_SPRING_ANCHOR_X,0x7000);seed(s,row);
        var b=wheel(s);var original=(CdMovingSpring.DrawObjectCall)actual(b,"drawObject");var sentinel=new AssertionError("after genuine wheel Draw");
        set(b,"drawObject",(CdMovingSpring.DrawObjectCall)c->{original.drawObject(c);throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(row.moveByte(SPRITE_FLAGS,4),s.placement().pool().state(127));assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void foreignEqualPlacementCannotSupplyTheActualWorldPublicationCut() throws Exception {
        var s=scene();clearConstructedDynamic(s);var row=image(s,127,0);seed(s,row);
        var foreign=CdObjectPlacementRuntime.fromAssets(s.assets(),s.placement().capture().savedFlags());var foreignRow=image(new Scene(s.h(),s.visit(),s.frame(),foreign,s.assets()),127,0);foreign.pool().write(foreignRow);assertArrayEquals(row.copyBytes(),foreign.pool().state(127).copyBytes());
        set(wheel(s),"placement",foreign);beforeForward(s);assertThrows(IllegalArgumentException.class,()->step(s.h(),neutral()));assertEquals(row,s.placement().pool().state(127));retired(s);
    }

    @Test void missingActualCurrentHasNoWheelArtFallback() throws Exception {
        var s=scene();clearConstructedDynamic(s);timeStopConstructed(s,1);
        var fg=((SonicCdPlayerPresentation)actual(s.visit(),"presentation")).act2ForegroundImage();
        seed(s,image(s,127,4).moveWord(X,fg.cameraX()+100).moveWord(Y,fg.cameraYWord()+100));
        step(s.h(),neutral());healthy(s);var sat=cpu(s);assertTrue(sat.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));
        var selected=s.h().module().act2SelectedDisplay().inputs();var current=s.h().module().act2LoadState().section().residency();
        // Constructed immutable projection control only; never adopted into the actual current owner.
        var unknown=CdR12aCameraArtResidency.unknown();
        for(var region:CdR12aCameraArtResidency.Region.values())for(int offset=0;offset<region.byteLength();offset+=32){
            int at=region.destinationWord()+offset;if((at<0x6700 || at>=0x6800) && current.region(region).knownByte(offset).isPresent())
                unknown=CdR12aCameraArtResidency.withTile(unknown,at,CdR12aCameraArtResidency.readTile(current,at>>>5));}
        var projected=SonicCdAct2Display.project(new SonicCdAct2Display.Inputs(Optional.of(sat),CdAssetBytes.copyOf(sat.bytes()),
                selected.palette(),selected.hscroll(),selected.vscrollLongBits(),true,selected.foreground(),selected.background(),unknown,selected.player()));
        var failure=assertInstanceOf(SonicCdPastDisplay.Blocked.class,projected.projection()).failure();
        assertEquals(SonicCdPastDisplay.Boundary.UNKNOWN_PATTERN,failure.boundary());assertTrue(failure.nativePatternId()>=0x338 && failure.nativePatternId()<0x340);
        assertSame(current,s.h().module().act2LoadState().section().residency());
    }

    private static CdMovingSpring wheel(Scene s)throws Exception{return (CdMovingSpring)actualField(s.visit(),"movingSpring");}
    private static Object actual(Object owner,String name){try{return actualField(owner,name);}catch(Exception e){throw new AssertionError(e);}}
    private static CdPlayerTerrain terrain(Scene s){return CdPlayerTerrain.fromStageTerrain(s.assets().terrain());}
    /** Constructed dynamic input; offscreen X avoids inventing any wheel-current readiness. Anchor remains native-retained. */
    private static CdObjectState image(Scene s,int whole,int routine) {
        var fg=((SonicCdPlayerPresentation)actual(s.visit(),"presentation")).act2ForegroundImage();
        return s.placement().pool().state(whole).moveByte(ID,0x0F).moveByte(ROUTINE,routine).moveWord(X,fg.cameraX()+400).moveWord(Y,fg.cameraYWord()+100)
                .moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468).moveByte(SPRITE_FLAGS,4).moveByte(FLAGS,0).moveByte(SUBTYPE,0).moveByte(SUBTYPE2,0)
                .moveByte(PRIORITY,4).moveByte(WIDTH,8).moveByte(Y_RADIUS,7).moveLong(MAPPINGS,0x209A96).moveWord(TILE,0x338)
                .moveWord(MOVING_SPRING_ANCHOR_X,fg.cameraX()+400).moveWord(MOVING_SPRING_BASE_Y,fg.cameraYWord()+100).moveWord(X_VELOCITY,0x180).moveWord(Y_VELOCITY,0)
                .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,0).moveByte(SAVED_FLAGS_ID,185);
    }
    private static CdObjectState header(CdObjectState s){return s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(PRIORITY,4).moveLong(MAPPINGS,0x209A96).moveByte(WIDTH,8).moveByte(Y_RADIUS,7).moveWord(MOVING_SPRING_ANCHOR_X,s.centreX()).moveWord(X_VELOCITY,0x180);}
    private static CdObjectState aligned(CdObjectState s,int distance){return distance<0?s.addWord(Y,distance).moveWord(MOVING_SPRING_BASE_Y,s.centreY()+distance).addByte(ROUTINE,2):s.addWord(Y,1);}
    private static void assertScreen(CdObjectState expected,CdObjectState actual){assertEquals(expected.moveByte(SPRITE_FLAGS,expected.spriteFlags()&127),actual.moveByte(SPRITE_FLAGS,actual.spriteFlags()&127));}
    /** Clearly constructed full96 test input; ordinary ingress, terrain, actor, camera and current remain untouched. */
    private static void clearConstructedDynamic(Scene s){for(int whole=32;whole<128;whole++)s.placement().pool().deleteObject(whole);}
    /** Constructed retained ring WORD input for the native TST.W branch; no private Snapshot clone. */
    private static void timeStopConstructed(Scene s,int timer)throws Exception{
        var v=actual(s.visit(),"values");var ring=(CdRingGlobals)actual(v,"ring");var replacement=new CdRingGlobals(ring.rings(),ring.lives(),ring.livesFlags(),ring.updateHudRings(),ring.updateHudLives(),timer,ring.ringAnimationTimer(),ring.ringAnimationFrame());
        var components=v.getClass().getRecordComponents();var types=new Class<?>[components.length];var args=new Object[components.length];
        for(int n=0;n<components.length;n++){types[n]=components[n].getType();var getter=components[n].getAccessor();getter.setAccessible(true);args[n]=components[n].getName().equals("ring")?replacement:getter.invoke(v);}
        var ctor=v.getClass().getDeclaredConstructor(types);ctor.setAccessible(true);set(s.visit(),"values",ctor.newInstance(args));
    }
    /** Real fixed-controller continuation to an already-owned earlier checkpoint, never a camera/actor seed. */
    private static Scene laterScene() throws Exception {
        var old=oldReturned();var h=old.harness();for(int call=10420;call<=11033;call++)step(h,controller(call));
        var visit=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");
        var scene=new Scene(h,visit,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(visit,"placement"),h.module().act2LoadState().assets());healthy(scene);return scene;
    }
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
        for(String name:new String[]{"activeMovingSpring","activeRobotGenerator","activeTunnelDoorSplash","activeDoor","activeChildEffect","activeLogMosqui","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"movingSpringPort","drawingMovingSpring","robotPort","drawingRobotGenerator","splashPort","drawingSplash","doorPort","drawingDoor","drawingChildEffect","logMosquiPort","drawingLogMosqui","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
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
