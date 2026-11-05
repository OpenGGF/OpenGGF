package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.control.InputActionMasks;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
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

/** Actual programme/Act2/Title release, then labelled dynamic64 object inputs. No actor/global/reserved/
 * bank/art/terrain/camera/clock/queue seeds and no fabricated Solid outcome. The existing prior Results
 * fixture is explicitly a dynamic64 ingress, not a controller-only route or failed11588 rehydration.
 * N1ecc5f3a Door7-129, Object Solid174-478, Animate14-95, Object Spawner385-446. All Java UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2TunnelDoorWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void subtypeOneDirtyInitUsesActualBothPlayersAndPreservesAllUnwrittenBytesThroughFullAndCourseReplay() throws Exception {
        var s=scene();var source=image(s,0,1).moveWord(TILE,0x1111).moveLong(MAPPINGS,0x7E123456).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468)
                .moveWord(X_VELOCITY,0x3579).moveWord(Y_VELOCITY,0x468A).moveByte(X_RADIUS,0x55)
                .moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,3).moveByte(ANIMATION_TIME,99)
                .moveLong(MOSQUI_X_DELTA,0x12345678);seed(s,source);
        var body=actualField(s.visit(),"tunnelDoor");var data=(CdTunnelDoor.Assets)actualField(body,"assets");
        assertSame(s.assets().objects().tunnelDoor(),data.visual());assertSame(s.assets().objects().view(CdR12aProfile.Asset.OBJECT_TILE_IDS),data.objectTileIds());
        replayNext(s);assertEquals(header(source).moveWord(TILE,0x834E).moveByte(SPRITE_FLAGS,0x84),s.placement().pool().state(127));
        assertSame(body,actualField(s.visit(),"tunnelDoor"));assertEquals(1L,count(s,SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_PRIMARY_TOP));
        assertEquals(1L,count(s,SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_TOP));
        assertTrue(calls(s).indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_PRIMARY_TOP)<calls(s).indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_READ));
        assertTrue(calls(s).indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_READ)<calls(s).indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_TOP));
        assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_FM));assertTrue(s.placement().pool().capture().slots().stream().noneMatch(o->o.id()==0x0B));
    }

    @Test void actualWholeOneReadsUseTheRetainedRawSliceAndNeverASecondActorOrReservedBank() throws Exception {
        var s=scene();seed(s,image(s,0,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");
        var original=(CdTunnelDoor.SecondarySource)actualField(body,"secondarySource");var seen=new ArrayList<CdAssetBytes>();
        set(body,"secondarySource",(CdTunnelDoor.SecondarySource)()->{var raw=original.readSecondary();seen.add(raw);return raw;});
        replayNext(s);assertFalse(seen.isEmpty());var current=(CdAssetBytes)actualField(actualField(s.visit(),"values"),"hudObjects");
        for(var raw:seen)assertArrayEquals(current.slice(0,64).copy(),raw.copy());assertArrayEquals(new byte[64],current.slice(0,64).copy());
    }

    @Test void actualJumpOpensSubtypeOneThenStillChecksSecondaryWithoutPrimaryTopChildOrParentFm() throws Exception {
        var s=scene();assertEquals(0,s.frame().state().motion().flags()&2,"real released scene grounded prerequisite");
        var m=s.frame().state().motion();var source=image(s,2,1).moveByte(SPRITE_FLAGS,0x84)
                .moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16);seed(s,source);
        replayNext(s,jump());assertEquals(4,s.placement().pool().state(127).routine());
        assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_PRIMARY_TOP));
        assertEquals(1L,count(s,SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_TOP));
        assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_FM));assertTrue(s.placement().pool().capture().slots().stream().noneMatch(o->o.id()==0x0B));
    }

    @Test void visibleContactUsesTheRealSharedTopOutcomeAtTheActualPlayerBottomAlignment() throws Exception {
        var s=scene();var m=s.frame().state().motion();var source=image(s,2,1).moveByte(SPRITE_FLAGS,0x84)
                .moveWord(X,m.centreX16_16()>>>16).moveWord(Y,(m.centreY16_16()>>>16)+m.yRadius()+8);seed(s,source);
        var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");var original=(CdTunnelDoor.PrefixSolidOwner)actualField(body,"prefixSolidOwner");
        var reached=new AtomicReference<CdSolidObject.Outcome>();set(body,"prefixSolidOwner",new CdTunnelDoor.PrefixSolidOwner(){
            public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){return original.preflight(c);}
            public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){if(c.cut().participant()==CdTunnelDoor.Participant.PRIMARY)reached.set(c.outcome());return original.commit(c);}
        });replayNext(s);assertNotNull(reached.get());var done=assertInstanceOf(CdSolidObject.Completed.class,reached.get());
        assertEquals(CdSolidObject.Entry.TOP_SOLID_OBJECT,done.completedResult().entry());
        assertTrue(done.completedResult().playerWrites().contains(CdSolidObject.PlayerField.STAND_OBJECT),"actual aligned contact must reach native stand; do not fit a different position");
    }

    @Test void malformedSubtypeTwoStopsAfterOnlyItsRealInitHeaderBeforeAnyPreflightOrSecondaryRead() throws Exception {
        var s=scene();var source=image(s,0,1).moveByte(SUBTYPE2,1).moveWord(TILE,0x3579);seed(s,source);var saved=s.placement().capture().savedFlags();beforeForward(s);
        var failure=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,()->step(s.h(),neutral()));assertEquals(CdTunnelDoor.UnsupportedBoundary.TILE_ID_SUBTYPE2,failure.boundary());
        assertEquals(header(source),s.placement().pool().state(127));assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_READ));retired(s);
    }

    @Test void validAnimationExecutesOnceAndFcThenResetDoesNotReenterMainInThatDispatch() throws Exception {
        var s=scene();var source=image(s,4,1).moveByte(SPRITE_FLAGS,0x84).moveByte(ANIMATION_FRAME,10);seed(s,source);replayNext(s);
        assertEquals(source.addByte(ROUTINE,2),s.placement().pool().state(127));
        replayNext(s);assertEquals(source.moveByte(ROUTINE,2).moveByte(PREVIOUS_ANIMATION,1).moveByte(MAP_FRAME,0),s.placement().pool().state(127));
        assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_PRIMARY_TOP));assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_READ));
    }

    @Test void malformedAnimationDirectoryPreservesOnlyTheNativeResetAndSubqPrefix() throws Exception {
        var s=scene();var source=image(s,4,1).moveByte(SPRITE_FLAGS,0x84).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,0);seed(s,source);beforeForward(s);
        assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));
        assertEquals(source.moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));retired(s);
    }

    @Test void malformedFrameReadRetainsItsActualSameValueSpeedStoreWithoutAnotherSuccessfulAnimation() throws Exception {
        var s=scene();var source=image(s,4,1).moveByte(SPRITE_FLAGS,0x84).moveByte(ANIMATION_FRAME,255);seed(s,source);
        var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");var observer=(CdTunnelDoor.DispatchObserver)actualField(body,"observer");var timers=new ArrayList<Integer>();
        set(body,"observer",new CdTunnelDoor.DispatchObserver(){
            public void beforePublication(CdTunnelDoor.Publication c){observer.beforePublication(c);}
            public void afterPublication(CdTunnelDoor.Publication c){observer.afterPublication(c);timers.add(c.output().unsignedByte(ANIMATION_TIME));}
            public void beforeCall(Object c){observer.beforeCall(c);}
            public void afterCall(Object c,Object r){observer.afterCall(c,r);}
        });beforeForward(s);assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));
        assertEquals(List.of(255,0),timers);assertEquals(source,s.placement().pool().state(127));retired(s);
    }

    @Test void heldUnknownAnimationRemainsUnreadUntilTheNextReachedDirectoryFault() throws Exception {
        var s=scene();var source=image(s,4,1).moveByte(SPRITE_FLAGS,0x84).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_TIME,1);
        seed(s,source);replayNext(s);assertEquals(source.moveByte(ANIMATION_TIME,0),s.placement().pool().state(127));beforeForward(s);
        assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));assertEquals(source.moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));retired(s);
    }

    @Test void noOpPrimaryPreflightCannotPermitTheActualHelperOrLaterNativeReads() throws Exception {
        var s=scene();var source=image(s,0,1);seed(s,source);var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");
        var original=(CdTunnelDoor.PrefixSolidOwner)actualField(body,"prefixSolidOwner");
        set(body,"prefixSolidOwner",new CdTunnelDoor.PrefixSolidOwner(){
            public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){return new CdTunnelDoor.LiveSolid(c.expectedPrimary(),c.expectedGates(),c.expectedGlobals());}
            public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){return original.commit(c);}
        });beforeForward(s);receiptFailure(s);assertEquals(header(source).moveWord(TILE,0x834E),s.placement().pool().state(127));
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_PRIMARY_TOP));assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_READ));retired(s);
    }

    @Test void noOpSameValuePrimaryCommitNeedsItsOwnImmediatePrivateReceipt() throws Exception {noOpCommit(CdTunnelDoor.Participant.PRIMARY);}
    @Test void noOpSameValueSecondaryCommitNeedsItsOwnImmediatePrivateReceipt() throws Exception {noOpCommit(CdTunnelDoor.Participant.SECONDARY);}
    private void noOpCommit(CdTunnelDoor.Participant selected) throws Exception {
        var s=scene();seed(s,image(s,0,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");var original=(CdTunnelDoor.PrefixSolidOwner)actualField(body,"prefixSolidOwner");
        set(body,"prefixSolidOwner",new CdTunnelDoor.PrefixSolidOwner(){
            public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){return original.preflight(c);}
            public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){return c.cut().participant()==selected?
                    new CdTunnelDoor.LiveSolid(c.cut().expectedPrimary(),c.cut().expectedGates(),c.cut().expectedGlobals()):original.commit(c);}
        });beforeForward(s);receiptFailure(s);
        assertFalse(rawCalls(s).contains(selected==CdTunnelDoor.Participant.PRIMARY?SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_PRIMARY_TOP:SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_TOP));retired(s);
    }

    @Test void clonedPreflightDescriptionsAndEqualLiveReturnCannotBorrowActualProducerAuthority() throws Exception {
        var s=scene();seed(s,image(s,0,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");var original=(CdTunnelDoor.PrefixSolidOwner)actualField(body,"prefixSolidOwner");
        set(body,"prefixSolidOwner",new CdTunnelDoor.PrefixSolidOwner(){
            public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){
                assertThrows(IllegalStateException.class,()->original.preflight(new CdTunnelDoor.PrefixSolidCut(c.participant(),c.objectIdentity(),c.callCut(),c.expectedPrimary(),c.expectedGates(),c.expectedGlobals(),c.secondary())));
                var actual=original.preflight(c);return new CdTunnelDoor.LiveSolid(actual.primary(),actual.gates(),actual.globals());
            }
            public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){return original.commit(c);}
        });beforeForward(s);receiptFailure(s);assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_PRIMARY_TOP));retired(s);
    }

    @Test void duplicateAndClonedActualOutcomesCannotPublishASecondFrameOrObservation() throws Exception {
        var s=scene();seed(s,image(s,0,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");var original=(CdTunnelDoor.PrefixSolidOwner)actualField(body,"prefixSolidOwner");
        var sentinel=new AssertionError("after genuine Door primary adoption");set(body,"prefixSolidOwner",new CdTunnelDoor.PrefixSolidOwner(){
            public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){return original.preflight(c);}
            public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){
                assertThrows(IllegalStateException.class,()->original.commit(new CdTunnelDoor.PrefixSolidCall(c.cut(),c.outcome())));
                original.commit(c);assertThrows(IllegalStateException.class,()->original.commit(c));throw sentinel;
            }
        });beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(1L,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_PRIMARY_TOP).count());retired(s);
    }

    @Test void secondaryFaultAfterActualAdoptionAllowsOnlyRealReadOnlyVerificationAndPreservesOriginalException() throws Exception {
        var s=scene();seed(s,image(s,0,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");
        var original=(CdTunnelDoor.PrefixSolidOwner)actualField(body,"prefixSolidOwner");var sentinel=new AssertionError("after genuine secondary adoption");
        set(body,"prefixSolidOwner",new CdTunnelDoor.PrefixSolidOwner(){
            public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){return original.preflight(c);}
            public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){var actual=original.commit(c);
                if(c.cut().participant()==CdTunnelDoor.Participant.SECONDARY)throw sentinel;return actual;}
        });beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(0,sentinel.getSuppressed().length);assertEquals(1L,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_TOP).count());
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_BODY));retired(s);
    }

    @Test void aNoOpSecondarySourceCannotReturnEvenEqualActualBytesWithoutTheReachedWorldRead() throws Exception {
        var s=scene();seed(s,image(s,0,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");
        var raw=(CdAssetBytes)actualField(actualField(s.visit(),"values"),"hudObjects");
        set(body,"secondarySource",(CdTunnelDoor.SecondarySource)()->raw.slice(0,64));beforeForward(s);receiptFailure(s);
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_READ));assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_TOP));retired(s);
    }

    @Test void forgedNonzeroSecondaryReturnIsRejectedBeforeAnySecondaryFrameOrPoolAdoption() throws Exception {
        var s=scene();seed(s,image(s,0,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");var original=(CdTunnelDoor.SecondarySource)actualField(body,"secondarySource");
        set(body,"secondarySource",(CdTunnelDoor.SecondarySource)()->{var actual=original.readSecondary();byte[] forged=actual.copy();forged[63]=1;return CdAssetBytes.copyOf(forged);});
        beforeForward(s);receiptFailure(s);assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_TOP));
        var raw=(CdAssetBytes)actualField(actualField(s.visit(),"values"),"hudObjects");assertArrayEquals(new byte[64],raw.slice(0,64).copy());retired(s);
    }

    @Test void noOpDrawStopsImmediatelyBeforeTheNativeSavedClearAndDeleteTail() throws Exception {
        var s=scene();var source=image(s,4,1).moveWord(X,0x7000).moveByte(SAVED_FLAGS_ID,1);seed(s,source);var saved=s.placement().capture().savedFlags();
        var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");set(body,"drawObject",(CdTunnelDoor.DrawObjectCall)(pool,id)->{});
        beforeForward(s);receiptFailure(s);assertEquals(source.moveByte(ANIMATION_FRAME,1),s.placement().pool().state(127));
        assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());retired(s);
    }

    @Test void originalDrawFaultAfterItsRealBclrRetainsTheCompletedPrefixWithoutTailReplay() throws Exception {
        var s=scene();var source=image(s,4,1).moveByte(SPRITE_FLAGS,0x84);seed(s,source);var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");
        var original=(CdTunnelDoor.DrawObjectCall)actualField(body,"drawObject");var sentinel=new AssertionError("after genuine Door Draw");
        set(body,"drawObject",(CdTunnelDoor.DrawObjectCall)(pool,id)->{original.drawObject(pool,id);throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(source.moveByte(ANIMATION_FRAME,1).moveByte(SPRITE_FLAGS,4),s.placement().pool().state(127));retired(s);
    }

    @Test void actualResetDrawDespawnClearsOnlyItsReachedSavedByteAndDeletesEverySourceByte() throws Exception {
        var s=scene();seed(s,image(s,6,1).moveWord(X,0x7000).moveByte(SAVED_FLAGS_ID,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");
        var original=(CdTunnelDoor.DrawObjectCall)actualField(body,"drawObject");var cut=new AtomicReference<CdObjectPlacementRuntime.Snapshot>();
        set(body,"drawObject",(CdTunnelDoor.DrawObjectCall)(pool,id)->{cut.set(s.placement().capture());original.drawObject(pool,id);});replayNext(s);
        assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());byte[] expected=cut.get().savedFlags().copy();expected[6]&=0x7F;
        assertArrayEquals(expected,s.placement().capture().savedFlags().copy());
    }

    @Test void actualPreflightChecksEveryPhysicalImageBeforeAnyFramePublication() throws Exception {
        var s=scene();seed(s,image(s,0,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");var original=(CdTunnelDoor.PrefixSolidOwner)actualField(body,"prefixSolidOwner");
        var state=new AtomicReference<CdPlayerBody.State>();var env=new AtomicReference<CdPlayerBody.Environment>();
        set(body,"prefixSolidOwner",new CdTunnelDoor.PrefixSolidOwner(){
            public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){state.set(s.frame().state());env.set(s.frame().environment());
                s.placement().pool().write(s.placement().pool().state(126).moveByte(ROUTINE_2,0x5A));return original.preflight(c);}
            public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){return original.commit(c);}
        });beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertSame(state.get(),s.frame().state());assertSame(env.get(),s.frame().environment());retired(s);
    }

    @Test void preparedFrameRejectsDetachedWrongActorValuesBeforeAnyOwnerPublication() throws Exception {
        var s=scene();seed(s,image(s,0,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");
        var original=(CdTunnelDoor.PrefixSolidOwner)actualField(body,"prefixSolidOwner");var sentinel=new AssertionError("after actual Frame CAS negative");
        set(body,"prefixSolidOwner",new CdTunnelDoor.PrefixSolidOwner(){
            public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){return original.preflight(c);}
            public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){
                var done=assertInstanceOf(CdSolidObject.Completed.class,c.outcome());var p=c.cut().expectedPrimary();var a=p.actor();
                var detached=new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(a.motion(),a.tile()^1,a.priority(),a.hurt(),a.rotationDistance()),p.slotId(),p.pushObject(),p.standObject());
                var state=s.frame().state();var env=s.frame().environment();
                assertThrows(IllegalArgumentException.class,()->s.frame().prepareObjectSolidResult(detached,c.cut().expectedGlobals(),done.completedResult()));
                assertSame(state,s.frame().state());assertSame(env,s.frame().environment());original.commit(c);throw sentinel;
            }
        });beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(1L,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_PRIMARY_TOP).count());retired(s);
    }

    @Test void realSubtypeZeroAllocationAndFmRemainConditionalAndEarlierUnboundSplashStopsHonestlyNextPass() throws Exception {
        var s=scene();assertEquals(0,s.frame().state().motion().flags()&2);var m=s.frame().state().motion();
        // Declared ID0 dynamic64 ingress: FindObjSlot must not clear its dirty child image.
        for(var free:s.placement().pool().capture().slots())if(free.id()==0 && free.identity().wholeIndex()!=127)
            seed(s,free.moveByte(ROUTINE,6).moveByte(SUBTYPE,0xA5).moveByte(SUBTYPE2,0xEE).moveByte(ANIMATION,0x6C)
                    .moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0));
        seed(s,image(s,2,0).moveByte(SPRITE_FLAGS,0x84).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16));
        step(s.h(),jump());healthy(s);var children=s.placement().pool().capture().slots().stream().filter(o->o.id()==0x0B).toList();assertEquals(1,children.size());
        var child=children.getFirst();assertTrue(child.identity().wholeIndex()<127,"actual first-free child earlier than parent prerequisite");
        assertEquals(6,child.routine());assertEquals(0xA5,child.subtype());assertEquals(0xEE,child.subtype2());assertEquals(0x6C,child.unsignedByte(ANIMATION));
        assertEquals(0x1357,child.word(X_FRACTION));assertEquals(0x2468,child.word(Y_FRACTION));assertEquals(1L,count(s,SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_FM));
        beforeForward(s);var failure=assertThrows(SonicCdGameModule.UnsupportedAct2Loading.class,()->step(s.h(),neutral()));assertEquals(SonicCdGameModule.Act2Boundary.DYNAMIC_OBJECTS,failure.boundary());retired(s);
    }

    @Test void noOpParentFmCannotPermitSecondaryReadsAfterItsRealDirtyChildPublication() throws Exception {
        var s=scene();assertEquals(0,s.frame().state().motion().flags()&2);var m=s.frame().state().motion();
        seed(s,image(s,2,0).moveByte(SPRITE_FLAGS,0x84).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16));
        var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");set(body,"playFm",(CdTunnelDoor.PlayFmCall)c->{});beforeForward(s);
        var failure=assertThrows(IllegalStateException.class,()->step(s.h(),jump()));assertTrue(failure.getMessage().contains("exact private adoption/readback receipt"));
        assertTrue(s.placement().pool().capture().slots().stream().anyMatch(o->o.id()==0x0B));assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_READ));retired(s);
    }

    @Test void cachedComponentReentryAndInactiveWorldValuesGrantNoDoorAuthority() throws Exception {
        var s=scene();var before=captureNative(s.h());assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runDoor",new Class<?>[]{int.class},127));assertNativeCapture(before,s.h());
        seed(s,image(s,0,1));var body=(CdTunnelDoor)actualField(s.visit(),"tunnelDoor");var original=(CdTunnelDoor.PrefixSolidOwner)actualField(body,"prefixSolidOwner");
        set(body,"prefixSolidOwner",new CdTunnelDoor.PrefixSolidOwner(){
            public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){body.run(127,new CdTunnelDoor.Globals(1,0,0),new CdTunnelDoor.SolidInputs(c.expectedPrimary(),c.expectedGates(),c.expectedGlobals()));throw new AssertionError();}
            public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){return original.commit(c);}
        });beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertFalse(body.isCurrentCall(null));retired(s);
    }

    private static CdObjectState image(Scene s,int routine,int subtype) {
        var m=s.frame().state().motion();return s.placement().pool().state(127).moveByte(ID,0x0D).moveByte(ROUTINE,routine)
                .moveByte(SOLID_TYPE,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0)
                .moveWord(X,(m.centreX16_16()>>>16)+64).moveWord(Y,m.centreY16_16()>>>16).moveByte(SPRITE_FLAGS,4).moveByte(FLAGS,0)
                .moveByte(WIDTH,44).moveByte(Y_RADIUS,8).moveByte(PRIORITY,1).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0)
                .moveLong(MAPPINGS,CdR12aProfile.Asset.MAP_SPR_TUNNEL_DOOR.nativeAddress()).moveWord(TILE,0x834E)
                .moveByte(MAP_FRAME,0).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0);
    }
    private static CdObjectState header(CdObjectState s) {return s.addByte(ROUTINE,2).moveLong(MAPPINGS,CdR12aProfile.Asset.MAP_SPR_TUNNEL_DOOR.nativeAddress())
            .moveByte(PRIORITY,1).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(WIDTH,s.subtype()==2?24:44).moveByte(Y_RADIUS,8);}
    private static long count(Scene s,SonicCdGameModule.Act2LoadCall c){return calls(s).stream().filter(v->v==c).count();}
    private static void receiptFailure(Scene s) {var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(failure.getMessage().contains("exact private adoption/readback receipt"));}
    private static LogicalInputSnapshot jump(){return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0,0,InputActionMasks.ACTION_B,InputActionMasks.ACTION_B,false,false),PlayerInputState.neutral());}
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
        for(String name:new String[]{"activeDoor","activeLogMosqui","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"doorPort","drawingDoor","logMosquiPort","drawingLogMosqui","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
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
