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
 * Source N1ecc5f3a Anton7-103, Pata-Bata7-92, Object Time Check12-44, Object Animate14-95,
 * Collision Check/Spawner/Player Object Collision. No actor/global/art/queue/bank/camera/clock seed.
 * Private callbacks observe the actual reached source or throw a named fault, never forge success.
 * All Java UNRUN. Natural Anton/Pata continuation remains Root's independent execution obligation. */
@Isolated
class TestSonicCdAct2AntonPataWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void antonNormalDamagedAndRealSecondaryAliasInitializeThroughActualFloorAndBothRewindScopes() throws Exception {
        var s=scene();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();var body=actualField(s.visit(),"anton");
        for(int[] vector:new int[][]{{0,0},{1,0},{1,1}}) {
            registry.restore(base);var source=image(s,127,0x3F,0,vector[0]).moveByte(SUBTYPE2,vector[1])
                    .moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468).moveWord(X_VELOCITY,0x3579).moveWord(Y_VELOCITY,0x468A);
            seed(s,source);var expected=antonInit(s,source).addLong(Y_POSITION,0x10000);var writes=new ArrayList<Integer>();
            var floor=CdObjectCollisionCheck.objGetFloorDist(CdPlayerTerrain.fromStageTerrain(s.assets().terrain()),expected,writes::add);
            if(floor.distance()<0)expected=expected.addByte(ROUTINE,2);expected=expected.moveByte(SPRITE_FLAGS,expected.spriteFlags()|0x80);
            replayNext(s);assertEquals(expected,s.placement().pool().state(127));assertSame(body,actualField(s.visit(),"anton"));
            assertEquals((long)writes.size(),calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.ANTON_PRIMARY_ANGLE).count());
            assertEquals(floor.primaryAngle(),s.frame().environment().movement().globals().primaryAngle());
            assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.ANTON_BODY));
        }
    }

    @Test void antonMainTurnsAtTheNativeBoundaryThenMovesAndAnimatesUsingActualTerrainAndCurrentArt() throws Exception {
        var s=scene();var source=image(s,127,0x3F,4,0).moveLong(ANTON_X_DELTA,0x10000);
        source=source.moveWord(ANTON_ANCHOR_X,source.centreX()-127);source=flatAnton(s,source);seed(s,source);
        var current=s.h().module().act2LoadState().section().residency().region(CdR12aCameraArtResidency.Region.AT_69C0);
        replayNext(s);var turn=s.placement().pool().state(127);
        assertEquals(source.longBits(X_POSITION),turn.longBits(X_POSITION));assertEquals(-0x10000,turn.longBits(ANTON_X_DELTA));assertEquals(1,turn.flags()&1);
        assertEquals(1,turn.spriteFlags()&1);assertEquals(1,turn.unsignedByte(ANIMATION_FRAME));assertEquals(3,turn.unsignedByte(ANIMATION_TIME));
        replayNext(s);assertEquals(turn.longBits(X_POSITION)-0x10000,s.placement().pool().state(127).longBits(X_POSITION));
        assertSame(current,s.h().module().act2LoadState().section().residency().region(CdR12aCameraArtResidency.Region.AT_69C0));
        replayNext(s);assertInstanceOf(SonicCdPastDisplay.Ready.class,s.h().module().act2SelectedDisplay().projection());
        assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));
    }

    @Test void pataNormalDamagedAndEveryNonzeroRoutineBorrowOneAnimationRootWithoutAPlayerStepOrNewMathOwner() throws Exception {
        var s=scene();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();var body=(CdPataBata)actualField(s.visit(),"pataBata");
        assertSame(s.assets().objects().pataBataNormal(),body.assets().normal());assertSame(s.assets().objects().pataBataDamaged(),body.assets().damaged());
        assertSame(s.assets().objects().view(CdR12aProfile.Asset.ANI_PATA_BATA),body.assets().animations().bytes());
        for(int[] v:new int[][]{{0,0},{0,1},{1,0},{255,1}}) {
            registry.restore(base);var source=image(s,127,0x3E,v[0],v[1]).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468);
            seed(s,source);var expected=pataExpected(s,source,true);replayNext(s);assertEquals(expected,s.placement().pool().state(127));
            assertSame(body,actualField(s.visit(),"pataBata"));assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.PATA_BATA_BODY));
            assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.ANTON_PRIMARY_ANGLE));
        }
    }

    @Test void increasingPhysicalPataThenAntonUsesFreshSourceImagesAndAngleReceiptsWithTheExistingSelectedPixels() throws Exception {
        var s=scene();var pata=image(s,126,0x3E,255,1).moveLong(PATA_BATA_X_DELTA,0x10000);
        pata=pata.moveWord(PATA_BATA_ANCHOR_X,pata.centreX()-127);var anton=flatAnton(s,image(s,127,0x3F,4,0).moveLong(ANTON_X_DELTA,0));
        seed(s,pata);seed(s,anton);replayNext(s);assertEquals(pataExpected(s,pata,true),s.placement().pool().state(126));
        assertEquals(-0x10000,s.placement().pool().state(126).longBits(PATA_BATA_X_DELTA));
        assertTrue(calls(s).indexOf(SonicCdGameModule.Act2LoadCall.PATA_BATA_BODY)<calls(s).indexOf(SonicCdGameModule.Act2LoadCall.ANTON_PRIMARY_ANGLE));
        assertTrue(calls(s).indexOf(SonicCdGameModule.Act2LoadCall.ANTON_PRIMARY_ANGLE)<calls(s).indexOf(SonicCdGameModule.Act2LoadCall.ANTON_BODY));
        replayNext(s);assertInstanceOf(SonicCdPastDisplay.Ready.class,s.h().module().act2SelectedDisplay().projection());
        assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==126));assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));
    }

    @Test void antonRawTileEntryStopsAfterItsExactInitHeaderWithoutFloorDrawOrBankWrites() throws Exception {
        var s=scene();var source=image(s,127,0x3F,0,1).moveByte(SUBTYPE2,2);seed(s,source);var bank=s.placement().capture().savedFlags();
        beforeForward(s);var failure=assertThrows(CdAnton.UnsupportedAnton.class,()->step(s.h(),neutral()));assertEquals(CdAnton.UnsupportedBoundary.TILE_ENTRY,failure.boundary());
        assertEquals(antonHeader(source),s.placement().pool().state(127));assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
        assertEquals(0,failure.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void pataRawTileAddressStopsAfterOnlyItsActualInitHeaderWithoutNormalizingTheFullByte() throws Exception {
        var s=scene();var source=image(s,127,0x3E,0,0).moveByte(SUBTYPE2,255);seed(s,source);var bank=s.placement().capture().savedFlags();
        beforeForward(s);var failure=assertThrows(CdPataBata.UnsupportedPataBata.class,()->step(s.h(),neutral()));assertEquals(CdPataBata.UnsupportedBoundary.SET_OBJECT_TILE_ID,failure.boundary());
        assertEquals(pataHeader(source),s.placement().pool().state(127));assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
        assertEquals(0,failure.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void antonAnimationBadReadRetainsItsIncrementalResetPrefixAfterActualFloorStores() throws Exception {
        var s=scene();var source=flatAnton(s,image(s,127,0x3F,4,0).moveLong(ANTON_X_DELTA,0)).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,0);
        seed(s,source);beforeForward(s);var failure=assertThrows(CdAnton.UnsupportedAnton.class,()->step(s.h(),neutral()));
        assertEquals(CdAnton.UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY,failure.boundary());
        assertEquals(source.moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));
        assertEquals(0,failure.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void pataAnimationBadReadKeepsItsWholePreAnimationMotionImageAndOriginalCompletedPrefix() throws Exception {
        var s=scene();var source=image(s,127,0x3E,255,0).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255);seed(s,source);
        beforeForward(s);var failure=assertThrows(CdPataBata.UnsupportedPataBata.class,()->step(s.h(),neutral()));
        assertEquals(CdPataBata.UnsupportedBoundary.ANIMATE_OBJECT,failure.boundary());assertEquals(pataExpected(s,source,false).moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));
        assertEquals(0,failure.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void antonNativeBackEdgeCycleKeepsTheOriginalFiniteSupportStopInsteadOfChoosingATurnaroundLimit() throws Exception {
        var s=scene();var source=image(s,127,0x3F,4,0).moveLong(ANTON_X_DELTA,0);source=source.moveWord(ANTON_ANCHOR_X,source.centreX()-128);seed(s,source);
        beforeForward(s);var failure=assertThrows(CdAnton.UnsupportedAnton.class,()->step(s.h(),neutral()));assertEquals(CdAnton.UnsupportedBoundary.NATIVE_NON_RETURNING_CYCLE,failure.boundary());
        assertEquals(source,s.placement().pool().state(127));assertEquals(0,failure.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void originalAntonAngleFaultAfterTheRealSameValuePrezeroKeepsThatSinglePublicationAndSourceCut() throws Exception {
        var s=scene();seed(s,image(s,127,0x3F,2,0));var body=(CdAnton)actualField(s.visit(),"anton");var original=(CdAnton.PrimaryAngleCommit)actualField(body,"angleCommit");
        var reached=new AtomicReference<CdAnton.PrimaryAngleCall>();var sentinel=new AssertionError("actual Anton raw angle fault");
        set(body,"angleCommit",(CdAnton.PrimaryAngleCommit)c->{reached.set(c);original.commit(c);throw sentinel;});
        beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertNotNull(reached.get());assertEquals(0,reached.get().value());
        assertEquals(reached.get().expectedSource(),s.placement().pool().state(127));assertEquals(0,s.frame().environment().movement().globals().primaryAngle());
        assertEquals(0,sentinel.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void duplicateNoopAntonAngleReceiptIsRejectedBeforeASecondFrameOrLedgerPublication() throws Exception {
        var s=scene();seed(s,image(s,127,0x3F,2,0));var body=(CdAnton)actualField(s.visit(),"anton");var original=(CdAnton.PrimaryAngleCommit)actualField(body,"angleCommit");
        set(body,"angleCommit",(CdAnton.PrimaryAngleCommit)c->{original.commit(c);original.commit(c);});
        beforeForward(s);var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(0,failure.getSuppressed().length);
        assertEquals(1L,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.ANTON_PRIMARY_ANGLE).count());assertTrue(s.frame().closed());retired(s);
    }

    @Test void freshPublicAntonAngleCannotPublishAfterTheRealZeroEvenWhenItsPoolImagesMatch() throws Exception {
        var s=scene();seed(s,image(s,127,0x3F,2,0));var body=(CdAnton)actualField(s.visit(),"anton");
        var original=(CdAnton.PrimaryAngleCommit)actualField(body,"angleCommit");var sentinel=new AssertionError("after actual Anton zero");
        set(body,"angleCommit",(CdAnton.PrimaryAngleCommit)c->{
            original.commit(c);var fresh=CdObjectPool.overlay(c.callCut(),List.of(c.expectedSource()));
            assertThrows(IllegalStateException.class,()->original.commit(new CdAnton.PrimaryAngleCall(fresh,c.expectedSource(),c.value(),c.value()^0x5A)));
            assertThrows(IllegalStateException.class,()->original.commit(new CdAnton.PrimaryAngleCall(fresh,c.expectedSource(),c.value(),c.value())));
            throw sentinel;
        });
        beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(1L,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.ANTON_PRIMARY_ANGLE).count());
        assertEquals(0,s.frame().environment().movement().globals().primaryAngle());assertEquals(0,sentinel.getSuppressed().length);
        assertTrue(s.frame().closed());assertFalse(body.isCurrentPrimaryAngleCall(null));retired(s);
    }

    @Test void originalPataDrawFaultRetainsItsActualMotionAnimationPrefixAndDoesNotInventAQueueReceipt() throws Exception {
        var s=scene();var source=image(s,127,0x3E,255,1);seed(s,source);var body=(CdPataBata)actualField(s.visit(),"pataBata");var sentinel=new AssertionError("actual Pata Draw fault");
        set(body,"drawObject",(CdPataBata.DrawObjectCall)(pool,identity)->{throw sentinel;});
        beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(pataExpected(s,source,true),s.placement().pool().state(127));assertEquals(0,sentinel.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void realAntonComponentReentryAndHealthyPublicValuesCannotBorrowPrivateWorldAuthority() throws Exception {
        var s=scene();var before=captureNative(s.h());assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runPataBata",new Class<?>[]{int.class},127));assertNativeCapture(before,s.h());
        var source=image(s,127,0x3F,2,0);seed(s,source);var body=(CdAnton)actualField(s.visit(),"anton");var seen=new AtomicReference<Throwable>();
        set(body,"angleCommit",(CdAnton.PrimaryAngleCommit)c->{
            try {
                var actual=(CdAnton.Globals)actualField(actualField(s.visit(),"activeAnton"),"globals");
                var error=assertThrows(IllegalStateException.class,()->body.run(127,actual));seen.set(error);throw error;
            } catch(ReflectiveOperationException impossible) {throw new AssertionError(impossible);}
        });
        beforeForward(s);var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertSame(seen.get(),failure);
        assertEquals(0,failure.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void actualJumpContactPublishesOwnedScoreBeforeGenuineExplosionAndPointsPhysicalWork() throws Exception {
        var s=scene();var motion=s.frame().state().motion();assertEquals(0,motion.flags()&2,"actual released scene must be grounded");
        var score=(CdOpeningObjectRuntime.ScoreGlobals)actualField(actualField(actualField(s.visit(),"values"),"globals"),"score");
        var source=image(s,127,0x3E,2,0).moveWord(X,motion.centreX16_16()>>>16).moveWord(Y,motion.centreY16_16()>>>16).moveByte(COLLISION_TYPE,0x2A);
        seed(s,source);var body=(CdExplosion)actualField(s.visit(),"explosion");var original=(CdExplosion.DispatchObserver)actualField(body,"observer");
        var atInit=new AtomicReference<CdObjectState>();var scoreAtInit=new AtomicReference<CdOpeningObjectRuntime.ScoreGlobals>();
        set(body,"observer",new CdExplosion.DispatchObserver() {
            @Override public void beforePublication(CdExplosion.Publication c) {
                if(c.kind()==CdExplosion.StoreKind.INIT_HEADER && c.source().identity().wholeIndex()==127 && atInit.compareAndSet(null,c.source())) {
                    try {scoreAtInit.set((CdOpeningObjectRuntime.ScoreGlobals)actualField(actualField(actualField(s.visit(),"values"),"globals"),"score"));}
                    catch(Exception failure){throw new AssertionError("Read actual pre-Explosion score",failure);}
                }
                original.beforePublication(c);
            }
            @Override public void afterPublication(CdExplosion.Publication c){original.afterPublication(c);}
            @Override public void beforeCall(Object c){original.beforeCall(c);}
            @Override public void afterCall(Object c){original.afterCall(c);}
        });
        var registry=s.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(s.h());
        registry.restore(full);assertNativeCapture(before,s.h());registry.restoreCourse(course);assertNativeCapture(before,s.h());
        var jump=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0,0,InputActionMasks.ACTION_B,InputActionMasks.ACTION_B,false,false),PlayerInputState.neutral());
        step(s.h(),jump);healthy(s);var after=captureNative(s.h());
        assertNotNull(atInit.get());assertEquals(0x18,atInit.get().id());assertEquals(0,atInit.get().routine());
        int expectedScore=score.score()+s.assets().objects().view(CdR12aProfile.Asset.ENEMY_POINTS).unsignedWord(0);
        assertEquals(expectedScore,scoreAtInit.get().score(),"Actual owned score is published before Explosion's first native store");
        var now=(CdOpeningObjectRuntime.ScoreGlobals)actualField(actualField(actualField(s.visit(),"values"),"globals"),"score");
        assertEquals(expectedScore,now.score());assertEquals(0x18,s.placement().pool().state(127).id());assertEquals(2,s.placement().pool().state(127).routine());
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.PATA_BATA_BODY));assertTrue(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY));
        assertTrue(s.placement().pool().capture().slots().stream().anyMatch(row->row.id()==0x1C && (row.subtype()&0x80)!=0),"Genuine first-free Points child is retained");
        registry.restore(full);assertNativeCapture(before,s.h());step(s.h(),jump);healthy(s);assertNativeCapture(after,s.h());
        registry.restoreCourse(course);assertNativeCapture(before,s.h());step(s.h(),jump);healthy(s);assertNativeCapture(after,s.h());
    }

    private static CdObjectState image(Scene s,int whole,int id,int routine,int subtype) {
        var old=s.placement().pool().state(whole);var m=s.frame().state().motion();int x=((m.centreX16_16()>>>16)+64)&65535,y=m.centreY16_16()>>>16;
        var image=old.moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0)
                .moveWord(X,x).moveWord(Y,y).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0)
                .moveByte(WIDTH,id==0x3F?24:16).moveByte(Y_RADIUS,id==0x3F?19:16).moveByte(X_RADIUS,id==0x3F?24:16).moveByte(PRIORITY,id==0x3F?4:3)
                .moveByte(ANIMATION,subtype==0?0:1).moveByte(PREVIOUS_ANIMATION,subtype==0?0:1).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,0)
                .moveWord(TILE,id==0x3F?0x2400:0x23A7).moveLong(MAPPINGS,id==0x3F?0x20B1D2:subtype==0?0x20B356:0x20B378);
        return id==0x3F ? image.moveWord(ANTON_ANCHOR_X,x).moveLong(ANTON_X_DELTA,0)
                :image.moveWord(PATA_BATA_ANCHOR_X,x).moveLong(PATA_BATA_ANCHOR_Y,(y<<16)|0x8000).moveLong(PATA_BATA_X_DELTA,subtype==0?-0x8000:-0x4000)
                .moveWord(PATA_BATA_PHASE,0).moveWord(PATA_BATA_PHASE_SPEED,subtype==0?-0x200:-0x100).moveWord(PATA_BATA_SHIFT,subtype==0?3:4);
    }
    private static CdObjectState antonHeader(CdObjectState s) {return s.moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(PRIORITY,4).moveLong(MAPPINGS,0x20B1D2)
            .moveByte(X_RADIUS,24).moveByte(WIDTH,24).moveByte(Y_RADIUS,19).moveByte(COLLISION_TYPE,0x29).moveWord(ANTON_ANCHOR_X,s.centreX());}
    private static CdObjectState antonInit(Scene s,CdObjectState source) {return antonHeader(source).moveWord(TILE,CdR11aObjectAssets.decodeTileWord(s.assets().objects().view(CdR12aProfile.Asset.OBJECT_TILE_IDS).copy(),2,source.subtype2()))
            .moveLong(ANTON_X_DELTA,source.subtype()==0?-0x10000:-0x8000).moveByte(ANIMATION,source.subtype()==0?0:1);}
    private static CdObjectState flatAnton(Scene s,CdObjectState source) {
        var terrain=CdPlayerTerrain.fromStageTerrain(s.assets().terrain());var floor=CdObjectCollisionCheck.objGetFloorDist(terrain,source);
        var aligned=source.addWord(Y,floor.distance());assertEquals(0,CdObjectCollisionCheck.objGetFloorDist(terrain,aligned).distance());return aligned;
    }
    private static CdObjectState pataHeader(CdObjectState s) {return s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(PRIORITY,3).moveByte(COLLISION_TYPE,0x2A)
            .moveByte(X_RADIUS,16).moveByte(WIDTH,16).moveByte(Y_RADIUS,16).moveWord(PATA_BATA_ANCHOR_X,s.centreX()).moveLong(PATA_BATA_ANCHOR_Y,(s.centreY()<<16)|0x8000);}
    private static CdObjectState pataExpected(Scene scene,CdObjectState source,boolean animate) {
        var s=source;if(s.routine()==0) {s=pataHeader(s);var tiles=scene.assets().objects().view(CdR12aProfile.Asset.OBJECT_TILE_IDS);s=s.moveWord(TILE,tiles.unsignedWord((short)tiles.unsignedWord(2)+s.subtype2()*2));
            boolean d=s.subtype()!=0;s=s.moveLong(PATA_BATA_X_DELTA,d?-0x4000:-0x8000).moveWord(PATA_BATA_PHASE_SPEED,d?-0x100:-0x200).moveWord(PATA_BATA_SHIFT,d?4:3)
                    .moveByte(ANIMATION,d?1:0).moveLong(MAPPINGS,d?0x20B378:0x20B356);}
        int delta=s.longBits(PATA_BATA_X_DELTA);s=s.addLong(X_POSITION,delta);int distance=(s.centreX()-s.word(PATA_BATA_ANCHOR_X))&65535;if((short)distance<0)distance=(-distance)&65535;
        if((short)distance>=128) {delta=-delta;s=s.moveLong(PATA_BATA_X_DELTA,delta).addLong(X_POSITION,delta).moveByte(SPRITE_FLAGS,s.spriteFlags()^1).moveByte(FLAGS,s.flags()^1).moveWord(PATA_BATA_PHASE,0);}
        s=s.addWord(PATA_BATA_PHASE,s.signedWord(PATA_BATA_PHASE_SPEED));int angle=s.word(PATA_BATA_PHASE)>>>8;int sine=(short)scene.assets().objects().view(CdR12aProfile.Asset.SINE_TABLE).unsignedWord(angle*2);
        int swapped=((sine&65535)<<16)|(delta>>>16),count=s.word(PATA_BATA_SHIFT)&63;s=s.moveLong(Y_POSITION,s.longBits(PATA_BATA_ANCHOR_Y)+(count>=32?swapped>>31:swapped>>count));
        if(!animate)return s;var out=CdObjectAnimation.step(new CdObjectAnimation.State(s.unsignedByte(ANIMATION),s.unsignedByte(PREVIOUS_ANIMATION),s.unsignedByte(ANIMATION_FRAME),s.unsignedByte(ANIMATION_TIME),s.unsignedByte(MAP_FRAME),s.spriteFlags(),s.flags(),s.routine(),s.unsignedByte(ROUTINE_2)),
                new CdObjectAnimation.AnimationTable(scene.assets().objects().view(CdR12aProfile.Asset.ANI_PATA_BATA),0,2));
        return s.moveByte(ANIMATION,out.animationId()).moveByte(PREVIOUS_ANIMATION,out.previousId()).moveByte(ANIMATION_FRAME,out.scriptIndex()).moveByte(ANIMATION_TIME,out.timer()).moveByte(MAP_FRAME,out.mapFrame())
                .moveByte(SPRITE_FLAGS,out.spriteFlags()).moveByte(FLAGS,out.statusFlags()).moveByte(ROUTINE,out.routine()).moveByte(ROUTINE_2,out.routine2());
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
        for(String name:new String[]{"activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
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
