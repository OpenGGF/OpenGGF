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
 * Source N1ecc5f3a Tamabboh7-282, Floating Block7-176, Object Time Check12-44, Object Animate14-95,
 * Collision Check/Spawner/Player Object Collision. No actor/global/art/queue/bank/camera/clock seed.
 * Private callbacks observe the actual reached source or throw a named fault, never forge success.
 * All Java UNRUN. Natural Tamabboh/Float continuation remains Root's independent execution obligation. */
@Isolated
class TestSonicCdAct2TamabbohFloatWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void actualRangeYThenXUsesFreshPrimaryAndBothRewindScopesWithoutMovingThePlayerAgain() throws Exception {
        var s=scene();var source=image(s,127,0x22,4,0);seed(s,source);var body=actualField(s.visit(),"tamabboh");
        replayNext(s);var expected=source.addByte(ROUTINE,2);assertImageExceptScreen(expected,s.placement().pool().state(127));
        assertEquals(List.of(SonicCdGameModule.Act2LoadCall.TAMABBOH_PLAYER_Y,SonicCdGameModule.Act2LoadCall.TAMABBOH_PLAYER_X,
                SonicCdGameModule.Act2LoadCall.TAMABBOH_BODY),calls(s).stream().filter(c->c.name().startsWith("TAMABBOH")).toList());
        assertSame(body,actualField(s.visit(),"tamabboh"));assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));
    }

    @Test void floatInitAndPresentMainMakeTwoDistinctActualTopsAndRetainEveryOtherRawByte() throws Exception {
        var s=scene();var source=image(s,127,0x12,0,0).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468)
                .moveWord(X_VELOCITY,0x3579).moveWord(Y_VELOCITY,0x468A);seed(s,source);var body=actualField(s.visit(),"floatBlock");
        replayNext(s);var expected=source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveLong(MAPPINGS,0x20A222)
                .moveWord(TILE,0x435A).moveByte(PRIORITY,1).moveByte(WIDTH,12).moveByte(Y_RADIUS,12).moveByte(MAP_FRAME,0);
        assertImageExceptScreen(expected,s.placement().pool().state(127));assertSame(body,actualField(s.visit(),"floatBlock"));
        assertEquals(List.of(SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_SOLID,SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_SOLID,
                SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_BODY),calls(s).stream().filter(c->c.name().startsWith("FLOAT_BLOCK")).toList());
    }

    @Test void nativeSupportReachesAppearThroughTwoSynchronousFreshTopsWithoutAnActorSeed() throws Exception {
        var s=scene();var m=s.frame().state().motion();var source=image(s,127,0x12,2,0)
                .moveWord(X,m.centreX16_16()>>>16).moveWord(Y,(m.centreY16_16()>>>16)+15);seed(s,source);
        replayNext(s);var actual=s.placement().pool().state(127);assertEquals(127,s.frame().state().objectLinks().standObject());
        assertEquals(6,actual.routine());assertEquals(1,actual.unsignedByte(ANIMATION));assertEquals(8,actual.flags()&8);
        assertEquals(2,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_SOLID).count());
    }

    @Test void fireWritesTheTwoRealDirtyFirstFreeChildrenAfterItsOrderedFmAndDoesNotRedispatchEarlierSlots() throws Exception {
        var s=scene();var source=image(s,127,0x22,10,0);seed(s,source);
        var free=s.placement().capture().pool().slots().stream().filter(o->o.id()==0).limit(2).toList();assertEquals(2,free.size());
        for(var row:free)seed(s,row.moveByte(ROUTINE,0).moveByte(SUBTYPE2,0xA5).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468));
        var dirty=free.stream().map(o->s.placement().pool().state(o.identity().wholeIndex())).toList();replayNext(s);
        for(int n=0;n<2;n++){var expected=dirty.get(n).moveByte(ID,0x22).moveByte(SUBTYPE,1).moveWord(TILE,source.word(TILE))
                .moveByte(PRIORITY,source.unsignedByte(PRIORITY)+1).moveWord(X,source.centreX()).moveWord(Y,source.centreY()-10)
                .moveByte(TAMABBOH_MISSILE_DIRECTION,n==0?0:255);assertEquals(expected,s.placement().pool().state(expected.identity().wholeIndex()));}
        assertEquals(0x78,s.placement().pool().state(127).word(TAMABBOH_COOLDOWN));
        assertEquals(1,s.h().module().act2LoadState().sounds().stream().filter(x->x instanceof SonicCdGameModule.PastTamabbohFm).count());
    }

    @Test void genuineCapacityFailureMakesNoChildrenOrFmAndStillCompletesTheParentPrefix() throws Exception {
        var s=scene();for(var row:s.placement().capture().pool().slots())if(row.id()==0&&row.identity().wholeIndex()!=127)
            seed(s,image(s,row.identity().wholeIndex(),0x12,12,0));
        var source=image(s,127,0x22,10,0);seed(s,source);replayNext(s);
        assertImageExceptScreen(source.moveByte(ROUTINE,4).moveByte(ANIMATION,0).moveWord(TAMABBOH_COOLDOWN,0x78),s.placement().pool().state(127));
        assertFalse(s.h().module().act2LoadState().sounds().stream().anyMatch(x->x instanceof SonicCdGameModule.PastTamabbohFm));
    }

    @Test void missileOffscreenDeletesItsWholeImageBeforeDrawingTheRealClearedIdZero() throws Exception {
        var s=scene();seed(s,image(s,127,0x22,2,1).moveByte(SPRITE_FLAGS,4));replayNext(s);
        assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.TAMABBOH_BODY));
    }

    @Test void rawTamabbohTileFaultRetainsTheEntireNativeHeaderBeforeAnyAngleOrDraw() throws Exception {
        var s=scene();var source=image(s,127,0x22,0,0).moveByte(SUBTYPE2,255);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdTamabboh.UnsupportedTamabboh.class,()->step(s.h(),neutral()));assertEquals(CdTamabboh.UnsupportedBoundary.TILE_ENTRY,failure.boundary());
        assertEquals(source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveByte(PRIORITY,4).moveByte(COLLISION_TYPE,0x2C)
                .moveByte(X_RADIUS,16).moveByte(WIDTH,16).moveByte(Y_RADIUS,15).moveWord(TAMABBOH_ANCHOR_X,source.centreX()),s.placement().pool().state(127));
        assertTrue(s.frame().closed());retired(s);
    }

    @Test void actualTamabbohAngleFaultAfterSameValuePrezeroRetainsTheYLongPrefixAndThatOneStore() throws Exception {
        var s=scene();var source=image(s,127,0x22,2,0);seed(s,source);var body=(CdTamabboh)actualField(s.visit(),"tamabboh");
        var original=(CdTamabboh.PrimaryAngleCommit)actualField(body,"angleCommit");var sentinel=new AssertionError("actual Tamabboh angle fault");
        set(body,"angleCommit",(CdTamabboh.PrimaryAngleCommit)c->{original.commit(c);throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(source.addLong(Y_POSITION,0x10000),s.placement().pool().state(127));
        assertEquals(0,s.frame().environment().movement().globals().primaryAngle());assertEquals(1,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TAMABBOH_PRIMARY_ANGLE).count());
        assertTrue(s.frame().closed());retired(s);
    }

    @Test void clonedPlayerReadCannotBorrowTheActualPendingProducerCallEvenBeforeTheOriginalPublishes() throws Exception {
        var s=scene();seed(s,image(s,127,0x22,4,0));var body=(CdTamabboh)actualField(s.visit(),"tamabboh");
        var original=(CdTamabboh.PlayerWordRead)actualField(body,"playerRead");
        set(body,"playerRead",(CdTamabboh.PlayerWordRead)c->{assertThrows(IllegalStateException.class,()->original.read(new CdTamabboh.PlayerReadCall(c.callCut(),c.expectedSource(),c.field())));return original.read(c);});
        replayNext(s);assertEquals(6,s.placement().pool().state(127).routine());
    }

    @Test void clonedFloatOutcomeCannotPublishBeforeTheRealCutAndDuplicateRealCutCannotPublishTwice() throws Exception {
        var s=scene();seed(s,image(s,127,0x12,2,0));var body=(CdFloatBlock)actualField(s.visit(),"floatBlock");
        var original=(CdFloatBlock.PrefixTopSolidCommit)actualField(body,"prefixCommit");
        set(body,"prefixCommit",(CdFloatBlock.PrefixTopSolidCommit)c->{assertThrows(IllegalStateException.class,()->original.commit(new CdFloatBlock.PrefixTopSolidCall(c.callCut(),c.player(),c.globals(),c.outcome())));
            original.commit(c);assertThrows(IllegalStateException.class,()->original.commit(c));});replayNext(s);
        assertEquals(2,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_SOLID).count());
    }

    @Test void noopAngleCannotProduceAHealthyReturnedWorldStep() throws Exception {
        var s=scene();seed(s,image(s,127,0x22,2,0));var body=(CdTamabboh)actualField(s.visit(),"tamabboh");
        set(body,"angleCommit",(CdTamabboh.PrimaryAngleCommit)c->{});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(s.frame().closed());retired(s);
    }

    @Test void completedFloatInitCannotBeRestoredAtItsDrawCallback() throws Exception {
        var s=scene();var source=image(s,127,0x12,0,0);seed(s,source);var body=(CdFloatBlock)actualField(s.visit(),"floatBlock");
        var original=(CdFloatBlock.DrawObjectCall)actualField(body,"drawObject");set(body,"drawObject",(CdFloatBlock.DrawObjectCall)(pool,id)->{pool.write(source);original.drawObject(pool,id);});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(s.frame().closed());retired(s);
    }

    @Test void fmFaultAfterActualPublicationKeepsTheParentPrefixAndLeavesBothFirstFreeImagesUntouched() throws Exception {
        var s=scene();var source=image(s,127,0x22,10,0);seed(s,source);var before=s.placement().capture().pool();var body=(CdTamabboh)actualField(s.visit(),"tamabboh");
        var original=(CdTamabboh.PlayFmSound)actualField(body,"playFmSound");var sentinel=new AssertionError("actual FM_A0 prefix fault");
        set(body,"playFmSound",(CdTamabboh.PlayFmSound)c->{original.play(c);throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(source.moveByte(ROUTINE,4).moveByte(ANIMATION,0).moveWord(TAMABBOH_COOLDOWN,0x78),s.placement().pool().state(127));
        for(var old:before.slots())if(old.id()==0)assertEquals(old,s.placement().pool().state(old.identity().wholeIndex()));assertTrue(s.frame().closed());retired(s);
    }

    @Test void genuineFloatCommitFaultKeepsTheActualSolidPrefixAndOriginalException() throws Exception {
        var s=scene();seed(s,image(s,127,0x12,2,0));var body=(CdFloatBlock)actualField(s.visit(),"floatBlock");
        var original=(CdFloatBlock.PrefixTopSolidCommit)actualField(body,"prefixCommit");var actual=new AtomicReference<CdFloatBlock.PrefixTopSolidCall>();var sentinel=new AssertionError("actual Float Top fault");
        set(body,"prefixCommit",(CdFloatBlock.PrefixTopSolidCommit)c->{actual.set(c);original.commit(c);throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));var done=assertInstanceOf(CdSolidObject.Completed.class,actual.get().outcome()).completedResult();
        assertEquals(done.object(),s.placement().pool().state(127));assertTrue(s.frame().closed());retired(s);
    }

    @Test void clonedAngleBeforeTheRealPrezeroAndDuplicateNoopStoreAreRejectedBeforePublication() throws Exception {
        var s=scene();seed(s,image(s,127,0x22,2,0));var body=(CdTamabboh)actualField(s.visit(),"tamabboh");
        var original=(CdTamabboh.PrimaryAngleCommit)actualField(body,"angleCommit");
        set(body,"angleCommit",(CdTamabboh.PrimaryAngleCommit)c->{
            assertThrows(IllegalStateException.class,()->original.commit(new CdTamabboh.PrimaryAngleCall(c.callCut(),c.expectedSource(),c.expectedPrimaryAngle(),c.value())));
            original.commit(c);assertThrows(IllegalStateException.class,()->original.commit(c));});replayNext(s);
        assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.TAMABBOH_PRIMARY_ANGLE));
    }

    @Test void readFaultAfterActualYPublicationRetainsOnlyThatObservationAndUnchangedParentImage() throws Exception {
        var s=scene();var source=image(s,127,0x22,4,0);seed(s,source);var body=(CdTamabboh)actualField(s.visit(),"tamabboh");
        var original=(CdTamabboh.PlayerWordRead)actualField(body,"playerRead");var sentinel=new AssertionError("fresh primary X read fault");
        set(body,"playerRead",(CdTamabboh.PlayerWordRead)c->{if(c.field()==CdTamabboh.PlayerField.X_WORD)throw sentinel;return original.read(c);});beforeForward(s);
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(source,s.placement().pool().state(127));
        assertEquals(List.of(SonicCdGameModule.Act2LoadCall.TAMABBOH_PLAYER_Y),rawCalls(s).stream().filter(c->c.name().startsWith("TAMABBOH")).toList());
        assertTrue(s.frame().closed());retired(s);
    }

    @Test void clonedFmBeforeTheRealRequestAndDuplicateRealRequestCannotAppendASecondSound() throws Exception {
        var s=scene();seed(s,image(s,127,0x22,10,0));var body=(CdTamabboh)actualField(s.visit(),"tamabboh");
        var original=(CdTamabboh.PlayFmSound)actualField(body,"playFmSound");
        set(body,"playFmSound",(CdTamabboh.PlayFmSound)c->{assertThrows(IllegalStateException.class,()->original.play(new CdTamabboh.FmCall(c.callCut(),c.expectedSource(),c.firstFreeWholeIndex(),c.sound())));
            original.play(c);assertThrows(IllegalStateException.class,()->original.play(c));});replayNext(s);
        assertEquals(1,s.h().module().act2LoadState().sounds().stream().filter(x->x instanceof SonicCdGameModule.PastTamabbohFm).count());
    }

    @Test void noopFloatDrawCannotProduceAnOrdinaryReturnEvenAfterTwoRealSolidPublications() throws Exception {
        var s=scene();seed(s,image(s,127,0x12,2,0));var body=(CdFloatBlock)actualField(s.visit(),"floatBlock");
        set(body,"drawObject",(CdFloatBlock.DrawObjectCall)(pool,id)->{});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(2,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_SOLID).count());
        assertTrue(s.frame().closed());retired(s);
    }

    @Test void publicValuesAtAClosedCutCannotMintPlayerOrAngleWorldAuthority() throws Exception {
        var s=scene();var source=image(s,127,0x22,4,0);seed(s,source);var cut=s.placement().pool().capture();
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"readTamabbohPlayerWord",new Class<?>[]{CdTamabboh.PlayerReadCall.class},new CdTamabboh.PlayerReadCall(cut,source,CdTamabboh.PlayerField.Y_WORD)));
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"commitTamabbohPrimaryAngle",new Class<?>[]{CdTamabboh.PrimaryAngleCall.class},new CdTamabboh.PrimaryAngleCall(cut,source,0,0)));
        assertEquals(source,s.placement().pool().state(127));healthy(s);
    }

    @Test void normalAndDamagedInitReachTheirLiteralCompleteHeaderAndYLongPrefixBeforeAnyFloorStore() throws Exception {
        var s=scene();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();var body=(CdTamabboh)actualField(s.visit(),"tamabboh");
        var original=(CdTamabboh.PrimaryAngleCommit)actualField(body,"angleCommit");var reached=new AtomicReference<CdTamabboh.PrimaryAngleCall>();
        set(body,"angleCommit",(CdTamabboh.PrimaryAngleCommit)c->{reached.compareAndSet(null,c);original.commit(c);});
        for(int subtype:new int[]{0,2}){registry.restore(base);reached.set(null);var source=image(s,127,0x22,0,subtype).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468);seed(s,source);
            var expected=source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveByte(PRIORITY,4).moveByte(COLLISION_TYPE,0x2C)
                    .moveByte(X_RADIUS,16).moveByte(WIDTH,16).moveByte(Y_RADIUS,15).moveWord(TAMABBOH_ANCHOR_X,source.centreX()).moveWord(TILE,0x242C)
                    .moveLong(MAPPINGS,subtype==0?0x20B790:0x20B7A6).moveLong(TAMABBOH_ANIMATION_SCRIPT,subtype==0?0x20B76A:0x20B776)
                    .moveLong(TAMABBOH_X_DELTA,subtype==0?-0xA000:-0x5000).addLong(Y_POSITION,0x10000);
            replayNext(s);assertNotNull(reached.get());assertEquals(expected,reached.get().expectedSource());
            assertTrue(Set.of(2,4).contains(s.placement().pool().state(127).routine()));assertSame(body,actualField(s.visit(),"tamabboh"));
        }
    }

    @Test void missileInitUsesAllNativeLongStoresAndRetainsDirtyFractionsWithoutFloorOrAnimation() throws Exception {
        var s=scene();var source=image(s,127,0x22,0,1).moveByte(TAMABBOH_MISSILE_DIRECTION,255).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468);seed(s,source);
        var expected=source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveByte(COLLISION_TYPE,0xAD).moveByte(X_RADIUS,8).moveByte(WIDTH,8).moveByte(Y_RADIUS,8)
                .moveLong(MAPPINGS,0x20B898).moveLong(TAMABBOH_MISSILE_X_ACCEL,0).moveLong(TAMABBOH_MISSILE_Y_ACCEL,0x2000)
                .moveLong(TAMABBOH_MISSILE_X_DELTA,-0x20000).moveLong(TAMABBOH_MISSILE_Y_DELTA,-0x40000);
        replayNext(s);assertImageExceptScreen(expected,s.placement().pool().state(127));
        assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TAMABBOH_PRIMARY_ANGLE));assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TAMABBOH_PLAYER_Y));
    }

    @Test void nativeFloatNoDrawReturnsForAppearVisibleAndResetPreserveTheirWholeRawImages() throws Exception {
        var s=scene();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();
        for(int routine:new int[]{6,8,12}){registry.restore(base);var source=image(s,127,0x12,routine,0);seed(s,source);replayNext(s);
            var expected=routine==8?source.moveByte(ANIMATION,2).moveByte(ROUTINE,10):source.moveByte(ROUTINE,2);
            assertEquals(expected,s.placement().pool().state(127));assertEquals(routine==12?0:2,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_SOLID).count());
            assertFalse(cpu(s).provenance().stream().anyMatch(q->q.slotRef().wholeIndex()==127));}
    }

    @Test void realFloatAnimationReadFailureKeepsTheReachedResetDecrementPrefixAfterBothTops() throws Exception {
        var s=scene();var source=image(s,127,0x12,10,0).moveByte(ANIMATION,255);seed(s,source);beforeForward(s);
        assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));
        assertEquals(source.moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));
        assertEquals(2,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_SOLID).count());assertTrue(s.frame().closed());retired(s);
    }

    @Test void skippedNoopFirstFloatTopIsRejectedBeforeTheSecondActualCallCanPublishAnything() throws Exception {
        var s=scene();seed(s,image(s,127,0x12,2,0));var body=(CdFloatBlock)actualField(s.visit(),"floatBlock");
        var original=(CdFloatBlock.PrefixTopSolidCommit)actualField(body,"prefixCommit");var count=new java.util.concurrent.atomic.AtomicInteger();
        set(body,"prefixCommit",(CdFloatBlock.PrefixTopSolidCommit)c->{if(count.getAndIncrement()!=0)original.commit(c);});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(2,count.get());
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_SOLID));assertNull(actualField(body,"currentCursor"));assertTrue(s.frame().closed());retired(s);
    }

    @Test void substitutedUnpublishedPlayerReadIsRejectedBeforeTheNextActualFloorStoreCanPublish() throws Exception {
        var s=scene();seed(s,image(s,127,0x22,4,0));var body=(CdTamabboh)actualField(s.visit(),"tamabboh");
        set(body,"playerRead",(CdTamabboh.PlayerWordRead)c->(c.expectedSource().centreY()+0x200)&65535);beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TAMABBOH_PRIMARY_ANGLE));
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TAMABBOH_PLAYER_Y));assertNull(actualField(body,"currentCursor"));assertTrue(s.frame().closed());retired(s);
    }

    private static CdObjectState image(Scene s,int whole,int id,int routine,int subtype){var m=s.frame().state().motion();int x=(m.centreX16_16()>>>16)+(id==0x12?64:0),y=m.centreY16_16()>>>16;
        return s.placement().pool().state(whole).moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0)
                .moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0).moveByte(PRIORITY,4).moveByte(WIDTH,id==0x12?12:16).moveByte(Y_RADIUS,id==0x12?12:15)
                .moveWord(X,x).moveWord(Y,y).moveWord(TILE,id==0x12?0x435A:0x242C).moveLong(MAPPINGS,id==0x12?0x20A222:0x20B790)
                .moveLong(TAMABBOH_ANIMATION_SCRIPT,0x20B76A).moveLong(TAMABBOH_X_DELTA,0).moveWord(TAMABBOH_ANCHOR_X,x).moveWord(TAMABBOH_COOLDOWN,0)
                .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,0);}
    private static void assertImageExceptScreen(CdObjectState expected,CdObjectState actual){assertEquals(expected.moveByte(SPRITE_FLAGS,expected.spriteFlags()&127),actual.moveByte(SPRITE_FLAGS,actual.spriteFlags()&127));}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.Act2LoadCall> rawCalls(Scene s)throws Exception{return (List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(s.visit(),"values"),"calls");}

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
        for(String name:new String[]{"activeTamabboh","activeFloat","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"tamabbohPort","drawingTamabboh","floatPort","drawingFloat","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
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
