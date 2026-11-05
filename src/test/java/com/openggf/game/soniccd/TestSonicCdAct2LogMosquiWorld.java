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

/** Cold/Past/Results/normal Act2/Title release with explicitly labelled dynamic64-only object inputs.
 * No actor/global/art/queue/bank/camera/clock seed. Full/course captures are actual engine captures.
 * N1ecc5f3a Log Shadow7-70, Mosqui7-165, Object Spawner385-446, Collision Check and Object Animate.
 * This preparatory family qualification is not a measured Log/Mosqui route frontier. All Java UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2LogMosquiWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void logInitUsesActualRawPresentActWordAndPreservesEveryUnwrittenDirtyByteAcrossBothRewinds() throws Exception {
        var s=scene();var body=actualField(s.visit(),"logShadow");var source=image(s,127,0x33,0,0)
                .moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468).moveWord(X_VELOCITY,0x3579)
                .moveWord(Y_VELOCITY,0x468A).moveByte(ANIMATION,0xA5).moveByte(PREVIOUS_ANIMATION,0x5A)
                .moveByte(ANIMATION_TIME,0x7E).moveLong(MOSQUI_X_DELTA,0x12345678);
        seed(s,source);var expected=logInit(source);replayNext(s);
        assertEquals(expected,s.placement().pool().state(127));assertSame(body,actualField(s.visit(),"logShadow"));
        assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.LOG_SHADOW_BODY));
    }

    @Test void logMainRetainsItsStaticFrameAndCurrentProducerWithoutCreatingAnyNewArtOwner() throws Exception {
        var s=scene();var source=image(s,127,0x33,2,1).moveByte(MAP_FRAME,1);seed(s,source);
        var current=s.h().module().act2LoadState().section().residency().region(CdR12aCameraArtResidency.Region.AT_69C0);
        var objects=s.assets().objects();var visual=objects.logShadow();replayNext(s);
        assertEquals(source,s.placement().pool().state(127));replayNext(s);
        assertSame(current,s.h().module().act2LoadState().section().residency().region(CdR12aCameraArtResidency.Region.AT_69C0));
        assertSame(objects,s.assets().objects());assertSame(visual,objects.logShadow());
        assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));
    }

    @Test void mosquiNormalAndDamagedInitBorrowExactCachedRootsAndKeepNativeFractionalMovement() throws Exception {
        var s=scene();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();var body=actualField(s.visit(),"mosqui");
        var nativeAssets=actualField(body,"nativeAssets");
        assertSame(s.assets().objects().normalMosqui(),actualField(nativeAssets,"normal"));
        assertSame(s.assets().objects().damagedMosqui(),actualField(nativeAssets,"damaged"));
        for(int subtype:new int[]{0,255}) {
            registry.restore(base);var source=image(s,127,0x3D,0,subtype).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468);
            seed(s,source);var expected=mosquiHeader(source).moveWord(TILE,0x23A7)
                    .moveLong(MAPPINGS,map(subtype)).moveLong(MOSQUI_ANIMATION_SCRIPT,script(subtype))
                    .moveLong(MOSQUI_X_DELTA,subtype==0?-0x10000:-0x8000)
                    .addLong(X_POSITION,subtype==0?-0x10000:-0x8000)
                    .moveByte(ANIMATION_TIME,subtype==0?1:4).moveByte(ANIMATION_FRAME,1);
            replayNext(s);assertEquals(expected,s.placement().pool().state(127));assertSame(body,actualField(s.visit(),"mosqui"));
            assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.MOSQUI_BODY));
            assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.MOSQUI_PRIMARY_ANGLE));
        }
    }

    @Test void increasingPhysicalLogThenMosquiWalkUsesFreshSourceImagesAndTheExistingSelectedSat() throws Exception {
        var s=scene();var log=image(s,126,0x33,0,0);var fly=image(s,127,0x3D,2,0).moveLong(MOSQUI_X_DELTA,0);
        seed(s,log);seed(s,fly);replayNext(s);assertEquals(logInit(log),s.placement().pool().state(126));
        assertTrue(calls(s).indexOf(SonicCdGameModule.Act2LoadCall.LOG_SHADOW_BODY)<calls(s).indexOf(SonicCdGameModule.Act2LoadCall.MOSQUI_BODY));
        replayNext(s);assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==126));
        assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));
    }

    @Test void mosquiPatrolReversesAt128ThenAddsTheOppositeDeltaBeforeAnimating() throws Exception {
        var s=scene();var source=image(s,127,0x3D,2,0).moveLong(MOSQUI_X_DELTA,0x10000);
        source=source.moveWord(MOSQUI_ANCHOR_X,source.centreX()-127);seed(s,source);replayNext(s);
        var expected=source.moveLong(MOSQUI_X_DELTA,-0x10000).moveByte(SPRITE_FLAGS,source.spriteFlags()^1)
                .moveByte(FLAGS,source.flags()^1).moveByte(ANIMATION_TIME,1).moveByte(ANIMATION_FRAME,1);
        assertEquals(expected,s.placement().pool().state(127));
    }

    @Test void aHeldAnimationDoesNotReadTheForeignScriptUntilTheFollowingReachedReadFault() throws Exception {
        var s=scene();var source=image(s,127,0x3D,4,0).moveLong(MOSQUI_ANIMATION_SCRIPT,0x7E123456).moveByte(ANIMATION_TIME,1);
        seed(s,source);replayNext(s);assertEquals(source.moveByte(ANIMATION_TIME,0),s.placement().pool().state(127));beforeForward(s);
        var failure=assertThrows(CdMosqui.UnsupportedMosqui.class,()->step(s.h(),neutral()));
        assertEquals(CdMosqui.UnsupportedBoundary.ANIMATION_SCRIPT_POINTER,failure.boundary());
        assertEquals(source.moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void realDiveFloorStoresAndFmPrecedeDrawAndReplayThroughBothFullAndCourseCaptures() throws Exception {
        var s=scene();var source=landing(s);var probe=source.addWord(Y,6);var writes=new ArrayList<Integer>();
        var floor=CdObjectCollisionCheck.objGetFloorDist(CdPlayerTerrain.fromStageTerrain(s.assets().terrain()),probe,writes::add);
        assertTrue(floor.distance()<=-8,"native floor landing prerequisite at the labelled object cut");
        var expected=probe.addWord(Y,floor.distance()+8).addByte(ROUTINE,2);seed(s,source);replayNext(s);
        assertEquals(expected,s.placement().pool().state(127));
        assertEquals(floor.primaryAngle(),s.frame().environment().movement().globals().primaryAngle());
        assertEquals((long)writes.size(),calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.MOSQUI_PRIMARY_ANGLE).count());
        assertTrue(calls(s).indexOf(SonicCdGameModule.Act2LoadCall.MOSQUI_PRIMARY_ANGLE)<calls(s).indexOf(SonicCdGameModule.Act2LoadCall.MOSQUI_FM));
        assertTrue(calls(s).indexOf(SonicCdGameModule.Act2LoadCall.MOSQUI_FM)<calls(s).indexOf(SonicCdGameModule.Act2LoadCall.MOSQUI_BODY));
    }

    @Test void originalFloorFaultAfterSameValuePrezeroRetainsOnlyTheCompletedNativePrefix() throws Exception {
        var s=scene();var source=image(s,127,0x3D,6,0);seed(s,source);var body=(CdMosqui)actualField(s.visit(),"mosqui");
        var original=(CdMosqui.PrimaryAngleCommit)actualField(body,"angleCommit");var reached=new AtomicReference<CdMosqui.PrimaryAngleCall>();
        var sentinel=new AssertionError("after genuine Mosqui floor prezero");
        set(body,"angleCommit",(CdMosqui.PrimaryAngleCommit)c->{reached.set(c);original.commit(c);throw sentinel;});
        beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(0,reached.get().value());assertEquals(source.addWord(Y,6),s.placement().pool().state(127));
        assertEquals(1L,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.MOSQUI_PRIMARY_ANGLE).count());
        assertEquals(0,sentinel.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void noOpFloorCannotBorrowAFrameReceiptOrPermitLaterFloorReadsAndPublications() throws Exception {
        var s=scene();var source=image(s,127,0x3D,6,0);seed(s,source);var body=(CdMosqui)actualField(s.visit(),"mosqui");
        set(body,"angleCommit",(CdMosqui.PrimaryAngleCommit)c->{});beforeForward(s);
        var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(failure.getMessage().contains("returned without its private once-only receipt"));assertEquals(source.addWord(Y,6),s.placement().pool().state(127));
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.MOSQUI_PRIMARY_ANGLE));assertTrue(s.frame().closed());retired(s);
    }

    @Test void duplicateAndFreshEqualFloorCallsCannotPublishAfterTheRealZero() throws Exception {
        var s=scene();seed(s,image(s,127,0x3D,6,0));var body=(CdMosqui)actualField(s.visit(),"mosqui");
        var original=(CdMosqui.PrimaryAngleCommit)actualField(body,"angleCommit");var sentinel=new AssertionError("after duplicate/clone negatives");
        set(body,"angleCommit",(CdMosqui.PrimaryAngleCommit)c->{
            original.commit(c);assertThrows(IllegalStateException.class,()->original.commit(c));
            assertThrows(IllegalStateException.class,()->original.commit(new CdMosqui.PrimaryAngleCall(c.actualCut(),c.expectedSource(),c.value())));
            throw sentinel;
        });beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(1L,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.MOSQUI_PRIMARY_ANGLE).count());
        assertFalse(body.isCurrentCall(null));assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void realLandingFmRejectsACloneAndDuplicateWithoutASecondLedgerOrValuesPublication() throws Exception {
        var s=scene();seed(s,landing(s));var body=(CdMosqui)actualField(s.visit(),"mosqui");
        var original=(CdMosqui.PlayFmSound)actualField(body,"playFm");var sentinel=new AssertionError("after actual FM_A7");
        set(body,"playFm",(CdMosqui.PlayFmSound)c->{
            assertThrows(IllegalStateException.class,()->original.play(new CdMosqui.FmCall(c.actualCut(),c.expectedSource(),c.sound())));
            original.play(c);assertThrows(IllegalStateException.class,()->original.play(c));throw sentinel;
        });beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(1L,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.MOSQUI_FM).count());
        assertEquals(8,s.placement().pool().state(127).routine());assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void noOpFmStopsBeforeDrawAfterTheActualFloorAndLandingPoolPrefix() throws Exception {
        var s=scene();seed(s,landing(s));var body=(CdMosqui)actualField(s.visit(),"mosqui");
        set(body,"playFm",(CdMosqui.PlayFmSound)c->{});beforeForward(s);var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));
        assertTrue(failure.getMessage().contains("returned without its private once-only receipt"));
        assertEquals(8,s.placement().pool().state(127).routine());assertTrue(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.MOSQUI_PRIMARY_ANGLE));
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.MOSQUI_FM));retired(s);
    }

    @Test void noOpAndClonedLogDrawCannotBorrowTheActualPendingProducer() throws Exception {
        var s=scene();var source=image(s,127,0x33,2,0).moveWord(X,0x7000).moveByte(SPRITE_FLAGS,4).moveByte(SAVED_FLAGS_ID,1);
        seed(s,source);var saved=s.placement().capture().savedFlags();var body=(CdLogShadow)actualField(s.visit(),"logShadow");
        var original=(CdLogShadow.SourceDraw)actualField(body,"sourceDraw");
        set(body,"sourceDraw",(CdLogShadow.SourceDraw)c->{
            assertThrows(IllegalStateException.class,()->original.draw(new CdLogShadow.DrawCall(c.cut(),c.source())));
        });beforeForward(s);var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));
        assertTrue(failure.getMessage().contains("returned without its private once-only receipt"));
        assertEquals(source,s.placement().pool().state(127));assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.LOG_SHADOW_BODY));retired(s);
    }

    @Test void noOpMosquiDrawStopsImmediatelyBeforeTheActualAnchorSavedClearAndDeleteTail() throws Exception {
        var s=scene();var source=image(s,127,0x3D,4,0).moveByte(SPRITE_FLAGS,4).moveWord(MOSQUI_ANCHOR_X,0x7000).moveByte(SAVED_FLAGS_ID,1);
        seed(s,source);var saved=s.placement().capture().savedFlags();var body=(CdMosqui)actualField(s.visit(),"mosqui");
        set(body,"sourceDraw",(CdMosqui.SourceDraw)c->{});beforeForward(s);var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));
        assertTrue(failure.getMessage().contains("returned without its private once-only receipt"));
        assertEquals(source.moveByte(ANIMATION_TIME,1).moveByte(ANIMATION_FRAME,1),s.placement().pool().state(127));
        assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());retired(s);
    }

    @Test void mosquiDrawFaultPreservesCompletedMotionAnimationAndOriginalExceptionWithoutTailReplay() throws Exception {
        var s=scene();var source=image(s,127,0x3D,4,0);seed(s,source);var body=(CdMosqui)actualField(s.visit(),"mosqui");
        var original=(CdMosqui.SourceDraw)actualField(body,"sourceDraw");
        var sentinel=new AssertionError("after genuine Mosqui Draw return");set(body,"sourceDraw",(CdMosqui.SourceDraw)c->{original.draw(c);throw sentinel;});
        beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(source.moveByte(ANIMATION_TIME,1).moveByte(ANIMATION_FRAME,1).moveByte(SPRITE_FLAGS,4),s.placement().pool().state(127));
        assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void logDespawnUsesTheActualSavedByteThenDeletesAll64WithoutTouchingOtherSavedBytes() throws Exception {
        var s=scene();var source=image(s,127,0x33,2,0).moveWord(X,0x7000).moveByte(SPRITE_FLAGS,4).moveByte(SAVED_FLAGS_ID,1);
        seed(s,source);var body=(CdLogShadow)actualField(s.visit(),"logShadow");var draw=(CdLogShadow.SourceDraw)actualField(body,"sourceDraw");
        var reached=new AtomicReference<CdLogShadow.DrawCall>();set(body,"sourceDraw",(CdLogShadow.SourceDraw)c->{reached.set(c);draw.draw(c);});
        replayNext(s);assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());
        var expected=reached.get().cut().savedFlags().copy();expected[2+3+(s.frame().environment().movement().gates().timeZone()&127)]&=0x7F;
        assertArrayEquals(expected,s.placement().capture().savedFlags().copy());
    }

    @Test void mosquiWaitDeletesBeforeItsUnconditionalDrawWhichReceivesTheActualZeroImage() throws Exception {
        var s=scene();var source=image(s,127,0x3D,8,0).moveByte(SPRITE_FLAGS,4);seed(s,source);
        var body=(CdMosqui)actualField(s.visit(),"mosqui");var draw=(CdMosqui.SourceDraw)actualField(body,"sourceDraw");
        var zero=new AtomicReference<CdObjectState>();set(body,"sourceDraw",(CdMosqui.SourceDraw)c->{zero.set(c.source());draw.draw(c);});
        replayNext(s);assertArrayEquals(new byte[64],zero.get().copyBytes());assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());
    }

    @Test void selectedTileFaultLeavesOnlyMosquiInitHeaderAndNoDrawAngleOrSavedWrite() throws Exception {
        var s=scene();var source=image(s,127,0x3D,0,0).moveByte(SUBTYPE2,1);seed(s,source);var saved=s.placement().capture().savedFlags();
        beforeForward(s);assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));
        assertEquals(mosquiHeader(source),s.placement().pool().state(127));assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.MOSQUI_PRIMARY_ANGLE));retired(s);
    }

    @Test void actualFloorCallChecksAll96ImagesBeforeAnyFrameOrValuesAdoption() throws Exception {
        var s=scene();seed(s,image(s,127,0x3D,6,0));var body=(CdMosqui)actualField(s.visit(),"mosqui");
        var original=(CdMosqui.PrimaryAngleCommit)actualField(body,"angleCommit");var state=new AtomicReference<CdPlayerBody.State>();
        var env=new AtomicReference<CdPlayerBody.Environment>();
        set(body,"angleCommit",(CdMosqui.PrimaryAngleCommit)c->{
            state.set(s.frame().state());env.set(s.frame().environment());
            // Explicit dynamic64 adversary at a different physical image, after the genuine call cut.
            s.placement().pool().write(s.placement().pool().state(126).moveByte(ROUTINE_2,0x5A));original.commit(c);
        });beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));
        assertSame(state.get(),s.frame().state());assertSame(env.get(),s.frame().environment());
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.MOSQUI_PRIMARY_ANGLE));retired(s);
    }

    @Test void cachedComponentReentryAndInactiveWorldDispatchCannotBorrowHealthyPublicValues() throws Exception {
        var s=scene();var before=captureNative(s.h());assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runMosqui",new Class<?>[]{int.class},127));
        assertNativeCapture(before,s.h());seed(s,image(s,127,0x3D,6,0));var body=(CdMosqui)actualField(s.visit(),"mosqui");
        set(body,"angleCommit",(CdMosqui.PrimaryAngleCommit)c->{
            var m=s.frame().state().motion();var g=s.frame().environment().movement().gates();
            body.run(127,new CdMosqui.Globals(0,g.timeZone(),g.timeWarpDirection(),s.frame().environment().globals().debugMode(),
                    0,m.centreX16_16()>>>16,m.centreY16_16()>>>16));
        });beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));retired(s);
    }

    private static CdObjectState logInit(CdObjectState s) {
        return s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(PRIORITY,6)
                .moveLong(MAPPINGS,CdR12aProfile.Asset.MAP_SPR_LOG_SHADOW.nativeAddress()).moveByte(MAP_FRAME,s.subtype())
                .moveByte(WIDTH,16).moveByte(Y_RADIUS,16).moveWord(TILE,0x43F0);
    }
    private static CdObjectState mosquiHeader(CdObjectState s) {
        return s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(PRIORITY,3)
                .moveByte(X_RADIUS,16).moveByte(WIDTH,16).moveByte(Y_RADIUS,16).moveByte(COLLISION_TYPE,0x2B).moveWord(MOSQUI_ANCHOR_X,s.centreX());
    }
    private static int map(int subtype) {return (subtype==0?CdR12aProfile.Asset.MAP_SPR_MOSQUI_NORMAL:CdR12aProfile.Asset.MAP_SPR_MOSQUI_DAMAGED).nativeAddress();}
    private static int script(int subtype) {return (subtype==0?CdR12aProfile.Asset.ANI_MOSQUI_NORMAL:CdR12aProfile.Asset.ANI_MOSQUI_DAMAGED).nativeAddress();}
    private static CdObjectState image(Scene s,int whole,int id,int routine,int subtype) {
        var m=s.frame().state().motion();int x=((m.centreX16_16()>>>16)+64)&65535,y=m.centreY16_16()>>>16;
        return s.placement().pool().state(whole).moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,0)
                .moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0).moveWord(X,x).moveWord(Y,y)
                .moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0)
                .moveByte(WIDTH,16).moveByte(Y_RADIUS,16).moveByte(X_RADIUS,16).moveByte(PRIORITY,id==0x33?6:3)
                .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,0)
                .moveWord(TILE,id==0x33?0x43F0:0x23A7).moveLong(MAPPINGS,id==0x33?CdR12aProfile.Asset.MAP_SPR_LOG_SHADOW.nativeAddress():map(subtype))
                .moveWord(MOSQUI_ANCHOR_X,x).moveLong(MOSQUI_X_DELTA,0).moveLong(MOSQUI_ANIMATION_SCRIPT,script(subtype));
    }
    private static CdObjectState landing(Scene s) {
        var motion=s.frame().state().motion();int x=motion.centreX16_16()>>>16;
        var terrain=CdPlayerTerrain.fromStageTerrain(s.assets().terrain());
        // One fixed X at the genuine standing actor; align the object's bottom to the real floor.
        // Only this labelled dynamic object is placed. No search, actor/terrain edit or fake probe.
        var support=image(s,127,0x3D,6,0).moveWord(X,x).moveWord(MOSQUI_ANCHOR_X,x)
                .moveWord(Y,((motion.centreY16_16()>>>16)+motion.yRadius()-16)&65535);
        var floor=CdObjectCollisionCheck.objGetFloorDist(terrain,support);
        support=support.addWord(Y,floor.distance());
        assertEquals(0,CdObjectCollisionCheck.objGetFloorDist(terrain,support).distance(),"Fixed standing-column floor alignment prerequisite");
        // Native Dive adds six before probing. Eight pixels past that real surface selects <=-8.
        var source=support.addWord(Y,8-6);
        assertTrue(CdObjectCollisionCheck.objGetFloorDist(terrain,source.addWord(Y,6)).distance()<=-8,
                "Fixed source-derived contact must reach the native landing branch");return source;
    }
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.Act2LoadCall> rawCalls(Scene s) throws Exception {return (List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(s.visit(),"values"),"calls");}

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
        for(String name:new String[]{"activeLogMosqui","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"logMosquiPort","drawingLogMosqui","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
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
