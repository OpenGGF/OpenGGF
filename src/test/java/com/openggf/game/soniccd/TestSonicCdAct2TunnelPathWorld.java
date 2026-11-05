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
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual cold programme/Past/Results/Act2/Title release, then explicitly labelled dynamic64-only cuts.
 * Native N1ecc5f3a Tunnel Path/Main119-478. Failed natural12700 row is never an ingress.
 * No actor/global/bank/terrain/art/camera/queue seed. Source-only authoring: all Java UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2TunnelPathWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void cachedActualDataAndBodyDoNotAddMappingArtOrDrawAuthority() throws Exception {
        var s=scene();var body=body(s);var a=body.assets();var before=captureNative(s.h());
        assertSame(actualField(s.visit(),"tunnelPathAssets"),a);
        assertEquals(0x208BB8,a.groundSpeedsAddressBits());assertEquals(0x208BC0,a.pathsAddressBits());assertEquals(0x21D528,a.mappingAddressBits());
        assertEquals(8,a.groundSpeeds().length());assertEquals(284,a.paths().length());
        assertEquals(122,SonicCdGameModule.Act2LoadCall.values().length);assertEquals(104,SonicCdGameModule.Act2LoadCall.FLOWER_BODY.ordinal());
        assertEquals(106,SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_BODY.ordinal());
        assertFalse(body.isCurrentCall(new Object()));assertThrows(IllegalStateException.class,()->body.currentOrdinal(new Object()));
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runTunnelPath",new Class<?>[]{int.class},127));
        assertNativeCapture(before,s.h());
    }

    @Test void dirtyInitUsesActualPathTwoAndPreservesAllUnwrittenBytesWithoutDraw() throws Exception {
        var s=scene();var source=row(s,0,2).addWord(X,64).moveByte(SUBTYPE2,0x45).moveByte(ROUTINE_2,0xA7)
                .moveByte(ANIMATION,0x63).moveByte(ANIMATION_TIME,0x52).moveWord(TUNNEL_PATH_STEPS,0x1234).moveByte(TUNNEL_PATH_SCRATCH,0x71);
        seed(s,source);replayNext(s);
        assertEquals(initTwo(source),live(s));assertEquals(2,live(s).routine());assertEquals(0,live(s).spriteFlags()&0x80);
        assertFalse(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));
        assertFalse(familyCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_FM));
    }

    @Test void actualAcquisitionPublishesControlGroundSpeedSnapThenFmInNativeOrder() throws Exception {
        var s=scene();seed(s,row(s,0,2));var body=body(s);var owner=owner(body);var sites=new ArrayList<CdTunnelPath.NativeCut>();
        set(body,"playerOwner",new PlayerForward(owner){@Override public void commit(CdTunnelPath.PlayerCall c){
            sites.add(c.site());assertSame(s.frame().state(),c.expectedPlayer().state());assertSame(s.frame().environment(),c.expectedPlayer().environment());
            assertSame(c.expectedPlayer().environment(),c.result().finalEnvironment());super.commit(c);
        }});
        replayNext(s);assertEquals(List.of(CdTunnelPath.NativeCut.ACQUIRE_BASE,CdTunnelPath.NativeCut.ACQUIRE_FINISH),sites.subList(0,2));
        var m=s.frame().state().motion();assertEquals(0x81,m.playerControl());assertEquals(0x1000,m.groundVelocity());assertEquals(0,m.xVelocity());assertEquals(0,m.yVelocity());
        assertEquals(2,m.animation());assertEquals(0,m.playerJump());assertEquals(2,m.flags()&0x22);assertEquals(4,live(s).routine());
        assertEquals(0,live(s).unsignedByte(TUNNEL_PATH_SCRATCH));assertEquals(live(s).centreX(),m.centreX16_16()>>>16);assertEquals(live(s).centreY(),m.centreY16_16()>>>16);
        assertEquals(1,s.h().module().act2LoadState().sounds().stream().filter(SonicCdGameModule.PastTunnelPathFm.class::isInstance).count());
        assertEquals(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_BODY,familyCalls(s).getLast());
    }

    @Test void nonzeroSubtypeTwoAddsInvisibleBitToActualAcquiredControl() throws Exception {
        var s=scene();seed(s,row(s,2,2).moveByte(SUBTYPE2,1));replayNext(s);assertEquals(0xC1,s.frame().state().motion().playerControl());assertEquals(4,live(s).routine());
    }

    @Test void actualAdjacentWordAliasRaisesGvelWithoutSubtypeWhitelistOrPadding() throws Exception {
        var s=scene();seed(s,row(s,2,8));replayNext(s);
        // GVels+$10 is the actual first Path0 X word ($1440), inside the same authenticated union.
        assertEquals(0x1440,s.frame().state().motion().groundVelocity());assertEquals(0x81,s.frame().state().motion().playerControl());
    }

    @Test void gotPlayerUsesLiteralYSpeedAndHighByteCountdownThenRealIntegration() throws Exception {
        var s=acquired();var m=s.frame().state().motion();var source=live(s).moveByte(ROUTINE,4)
                .moveWord(TUNNEL_PATH_TARGET_X,m.centreX16_16()>>>16).moveWord(TUNNEL_PATH_TARGET_Y,((m.centreY16_16()>>>16)+0x88)&65535);
        seed(s,source);replayNext(s);assertEquals(6,live(s).routine());assertEquals(0x0880,live(s).word(TUNNEL_PATH_STEPS));
        assertEquals(0,s.frame().state().motion().xVelocity());assertEquals(0x1000,s.frame().state().motion().yVelocity());
        var y=s.frame().state().motion().centreY16_16();replayNext(s);assertEquals(0x0780,live(s).word(TUNNEL_PATH_STEPS));
        assertEquals(y+0x00100000,s.frame().state().motion().centreY16_16());
    }

    @Test void gotPlayerUsesLiteralXSpeedAndOnlyItsSecondFmSite() throws Exception {
        var s=acquired();var m=s.frame().state().motion();seed(s,live(s).moveByte(ROUTINE,4)
                .moveWord(TUNNEL_PATH_TARGET_X,((m.centreX16_16()>>>16)+0x50)&65535).moveWord(TUNNEL_PATH_TARGET_Y,m.centreY16_16()>>>16));
        replayNext(s);assertEquals(0x0500,live(s).word(TUNNEL_PATH_STEPS));assertEquals(0x1000,s.frame().state().motion().xVelocity());assertEquals(0,s.frame().state().motion().yVelocity());
        assertEquals(1,s.h().module().act2LoadState().sounds().stream().filter(SonicCdGameModule.PastTunnelPathFm.class::isInstance).count());
    }

    @Test void completionSnapsBeforeActualNextCoordinateReadsAndSpeed() throws Exception {
        var s=acquired();var m=s.frame().state().motion();var source=live(s).moveByte(ROUTINE,6).moveWord(TUNNEL_PATH_STEPS,0)
                // Literal Path2's first node: actual completion publishes the snap, never a test actor seed.
                .moveWord(TUNNEL_PATH_TARGET_X,0x1630).moveWord(TUNNEL_PATH_TARGET_Y,0x0290)
                .moveWord(TUNNEL_PATH_INDEX_END,0x0044).moveLong(TUNNEL_PATH_DATA,0x208C98);
        seed(s,source);var body=body(s);var observer=observer(body);var cuts=new ArrayList<CdTunnelPath.NativeCut>();
        set(body,"observer",new Forward(observer){@Override public void beforeOperation(Object c){if(c instanceof CdTunnelPath.PlayerCall p)cuts.add(p.site());super.beforeOperation(c);}});
        replayNext(s);assertEquals(CdTunnelPath.NativeCut.COMPLETE_SNAP,cuts.getFirst());assertEquals(4,live(s).unsignedByte(TUNNEL_PATH_INDEX));
        assertEquals(0x1630,live(s).word(TUNNEL_PATH_TARGET_X));assertEquals(0x0318,live(s).word(TUNNEL_PATH_TARGET_Y));assertEquals(0x208C98,live(s).longBits(TUNNEL_PATH_DATA));assertEquals(0x0880,live(s).word(TUNNEL_PATH_STEPS));
    }

    @Test void finalReleaseUsesWordRoutineBugClearsControlAndRetainsFractions() throws Exception {
        var s=acquired();var m=s.frame().state().motion();int targetY=(m.centreY16_16()>>>16)|0x0800;
        var source=live(s).moveByte(ROUTINE,6).moveByte(ROUTINE_2,0xA7).moveWord(TUNNEL_PATH_STEPS,0)
                .moveWord(TUNNEL_PATH_INDEX_END,0x0004).moveWord(TUNNEL_PATH_TARGET_X,m.centreX16_16()>>>16).moveWord(TUNNEL_PATH_TARGET_Y,targetY);
        seed(s,source);replayNext(s);assertEquals(0x0002,live(s).word(TUNNEL_PATH_ROUTINE_WORD));assertEquals(0,live(s).routine());assertEquals(2,live(s).unsignedByte(ROUTINE_2));
        assertEquals(0,s.frame().state().motion().playerControl());assertEquals(targetY&0x7FF,s.frame().state().motion().centreY16_16()>>>16);
        assertEquals(m.centreY16_16()&65535,s.frame().state().motion().centreY16_16()&65535);
        assertFalse(familyCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_FM));
    }

    @Test void outsideDespawnClearsOnlyReachedSavedByteAndAllSixteenDeleteLongs() throws Exception {
        var s=scene();var source=row(s,2,2).addWord(X,0x800).moveByte(SAVED_FLAGS_ID,31);seed(s,source);
        var saved=s.placement().capture().savedFlags();int bankIndex=2+3*31+1;var observer=observer(body(s));var deletes=new int[1];
        set(body(s),"observer",new Forward(observer){@Override public void beforeOperation(Object c){
            if(c instanceof CdTunnelPath.Publication p && p.kind()==CdTunnelPath.MutationKind.DELETE){assertEquals(16,p.deleteStores().size());deletes[0]++;}super.beforeOperation(c);
        }});
        replayNext(s);assertArrayEquals(new byte[64],live(s).copyBytes());byte[] expected=saved.copy();expected[bankIndex]&=0x7F;assertArrayEquals(expected,s.placement().capture().savedFlags().copy());assertEquals(3,deletes[0]);
    }

    @Test void rawSelectedHeaderFaultRetainsOnlyActualInitHeader() throws Exception {
        var s=scene();var source=row(s,0,8);seed(s,source);beforeForward(s);
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,()->step(s.h(),neutral()));assertEquals(CdTunnelPath.UnsupportedBoundary.HEADER_WORD,e.boundary());
        assertEquals(CdTunnelPath.StopReason.UNOWNED_ADDRESS,e.reason());assertEquals(header(source),live(s));assertEquals(0,e.getSuppressed().length);retired(s);
    }

    @Test void nextOddWordFaultRetainsCompletionAndIndexBeforeTheUnownedRead() throws Exception {
        var s=acquired();var m=s.frame().state().motion();var source=live(s).moveByte(ROUTINE,6).moveWord(TUNNEL_PATH_STEPS,0)
                .moveWord(TUNNEL_PATH_INDEX_END,8).moveLong(TUNNEL_PATH_DATA,0x208CDD)
                .moveWord(TUNNEL_PATH_TARGET_X,m.centreX16_16()>>>16).moveWord(TUNNEL_PATH_TARGET_Y,m.centreY16_16()>>>16);
        seed(s,source);beforeForward(s);var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,()->step(s.h(),neutral()));
        assertEquals(CdTunnelPath.UnsupportedBoundary.NEXT_X_WORD,e.boundary());assertEquals(CdTunnelPath.StopReason.UNALIGNED_WORD,e.reason());
        assertEquals(source.moveWord(TUNNEL_PATH_STEPS,0xFF00).moveByte(TUNNEL_PATH_INDEX,4),live(s));assertEquals(0x81,s.frame().state().motion().playerControl());assertEquals(0,e.getSuppressed().length);retired(s);
    }

    @Test void unsupportedRoutineDoesNotInitializeOrDespawnTheSource() throws Exception {
        var s=scene();var source=row(s,1,2);seed(s,source);beforeForward(s);var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,()->step(s.h(),neutral()));
        assertEquals(CdTunnelPath.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());assertEquals(source,live(s));retired(s);
    }

    @Test void noOpBeforeOwnStoreCannotPublishTheHeader() throws Exception {
        var s=scene();var source=row(s,0,2);seed(s,source);var observer=observer(body(s));
        set(body(s),"observer",new Forward(observer){@Override public void beforeOperation(Object c){if(!(c instanceof CdTunnelPath.Publication))super.beforeOperation(c);}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,live(s));retired(s);
    }

    @Test void noOpAfterOwnStoreStopsBeforeTheNextNativeRead() throws Exception {
        var s=scene();var source=row(s,0,2);seed(s,source);var observer=observer(body(s));
        set(body(s),"observer",new Forward(observer){@Override public void afterOperation(Object c){if(!(c instanceof CdTunnelPath.Publication))super.afterOperation(c);}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(header(source),live(s));retired(s);
    }

    @Test void noOpActualPlayerReadCannotBorrowEqualStateAndEnvironment() throws Exception {
        var s=scene();var source=row(s,0,2);seed(s,source);set(body(s),"playerOwner",new PlayerForward(owner(body(s))){@Override public CdTunnelPath.PlayerCut read(){return new CdTunnelPath.PlayerCut(s.frame().state(),s.frame().environment());}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,live(s));retired(s);
    }

    @Test void clonedPlayerReadReturnCannotReplaceTheActualReadReceipt() throws Exception {
        var s=scene();var source=row(s,0,2);seed(s,source);set(body(s),"playerOwner",new PlayerForward(owner(body(s))){@Override public CdTunnelPath.PlayerCut read(){var c=super.read();return new CdTunnelPath.PlayerCut(c.state(),c.environment());}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,live(s));retired(s);
    }

    @Test void sameValueIntegrationStillRequiresTheRealPreparedFrameAdoption() throws Exception {
        var s=acquired();seed(s,live(s).moveByte(ROUTINE,6).moveWord(TUNNEL_PATH_STEPS,0x0100));var at=new AtomicReference<CdPlayerBody.State>();
        set(body(s),"playerOwner",new PlayerForward(owner(body(s))){@Override public void commit(CdTunnelPath.PlayerCall c){
            if(c.site()==CdTunnelPath.NativeCut.INTEGRATE){assertEquals(c.expectedPlayer().state(),c.result().finalState());at.set(s.frame().state());}else super.commit(c);
        }});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertNotNull(at.get());assertSame(at.get(),s.frame().state());assertEquals(0,live(s).word(TUNNEL_PATH_STEPS));retired(s);
    }

    @Test void noOpFmCannotPassTheComponentReceiptGuardOrAppendSound() throws Exception {
        var s=scene();seed(s,row(s,2,2));set(body(s),"playFm",(CdTunnelPath.PlayFmCall)c->{});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(4,live(s).routine());assertEquals(0x81,s.frame().state().motion().playerControl());
        assertTrue(rawSounds(s).stream().noneMatch(SonicCdGameModule.PastTunnelPathFm.class::isInstance));retired(s);
    }

    @Test void clonedAndDuplicateProducerDescriptionsNeverAcquireAuthority() throws Exception {
        var s=scene();seed(s,row(s,0,2));var observer=observer(body(s));var count=new int[1];
        set(body(s),"observer",new Forward(observer){
            @Override public void beforeOperation(Object c){assertThrows(IllegalStateException.class,()->observer.beforeOperation(cloneRecord(c)));super.beforeOperation(c);assertThrows(IllegalStateException.class,()->observer.beforeOperation(c));count[0]++;}
            @Override public void afterOperation(Object c){super.afterOperation(c);assertThrows(IllegalStateException.class,()->observer.afterOperation(c));}
        });replayNext(s);assertTrue(count[0]>20);
    }

    @Test void originalFaultAfterActualFramePairPreservesOnlyTheNativeAcquisitionPrefix() throws Exception {
        var s=scene();seed(s,row(s,2,2));var sentinel=new IllegalStateException("after-actual-tunnel-frame-pair");
        set(body(s),"playerOwner",new PlayerForward(owner(body(s))){@Override public void commit(CdTunnelPath.PlayerCall c){super.commit(c);throw sentinel;}});
        beforeForward(s);assertSame(sentinel,assertThrows(IllegalStateException.class,()->step(s.h(),neutral())));assertEquals(4,live(s).routine());
        assertEquals(0x81,s.frame().state().motion().playerControl());assertEquals(0x1000,s.frame().state().motion().groundVelocity());assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void originalFaultAfterActualFmKeepsOneRealLedgerEntryWithoutReplay() throws Exception {
        var s=scene();seed(s,row(s,2,2));var fm=(CdTunnelPath.PlayFmCall)actualField(body(s),"playFm");var sentinel=new IllegalStateException("after-actual-tunnel-fm");
        set(body(s),"playFm",(CdTunnelPath.PlayFmCall)c->{fm.play(c);throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->step(s.h(),neutral())));assertEquals(1,rawSounds(s).stream().filter(SonicCdGameModule.PastTunnelPathFm.class::isInstance).count());
        assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void componentAndWorldReentryAreRejectedBeforeAnyOtherFamilyCanBorrowTheCut() throws Exception {
        var s=scene();seed(s,row(s,0,2));var observer=observer(body(s));
        set(body(s),"observer",new Forward(observer){@Override public void beforeOperation(Object c){
            assertThrows(IllegalStateException.class,()->body(s).run(127,new CdTunnelPath.Globals(1,0,0)));
            assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runTunnelPath",new Class<?>[]{int.class},127));
            assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runFlower",new Class<?>[]{int.class},127));super.beforeOperation(c);
        }});replayNext(s);
    }

    @Test void unrelatedDynamicMutationFailsCompleteNinetySixSlotCas() throws Exception {
        var s=scene();var source=row(s,0,2);seed(s,source);var observer=observer(body(s));var other=s.placement().pool().state(126);
        set(body(s),"observer",new Forward(observer){@Override public void beforeOperation(Object c){if(c instanceof CdTunnelPath.Publication)s.placement().pool().write(other.moveByte(SUBTYPE,0x71));super.beforeOperation(c);}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,live(s));assertEquals(other.moveByte(SUBTYPE,0x71),s.placement().pool().state(126));retired(s);
    }

    @Test void actualEnvironmentMutationBeforeThePreparedPairFailsBeforeNativePoolPublication() throws Exception {
        var s=scene();seed(s,row(s,2,2));var owner=owner(body(s));var source=new AtomicReference<CdObjectState>();
        set(body(s),"playerOwner",new PlayerForward(owner){@Override public void commit(CdTunnelPath.PlayerCall c){
            source.set(live(s));s.frame().applyObjectPrimaryAngle(s.frame().environment().movement().globals().primaryAngle()^2);super.commit(c);
        }});beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertNotNull(source.get());assertEquals(source.get(),live(s));retired(s);
    }

    @Test void equalValuesCloneCannotReplaceThePrivateWorldAggregate() throws Exception {
        var s=scene();var source=row(s,0,2);seed(s,source);var observer=observer(body(s));
        set(body(s),"observer",new Forward(observer){@Override public void beforeOperation(Object c){
            try{set(s.visit(),"values",cloneRecord(actualField(s.visit(),"values")));}catch(Exception e){throw new AssertionError(e);}super.beforeOperation(c);
        }});beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,live(s));retired(s);
    }

    @Test void skippedSourceReadPreparationStopsAfterHeaderBeforeDirectory() throws Exception {
        var s=scene();var source=row(s,0,2);seed(s,source);var observer=observer(body(s));
        set(body(s),"observer",new Forward(observer){@Override public void beforeOperation(Object c){if(!(c instanceof CdTunnelPath.WordReadCall))super.beforeOperation(c);}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(header(source),live(s));retired(s);
    }

    @Test void fm91SeesTheCompletedAcquisitionAndGotPlayerCutsInOrder() throws Exception {
        var s=scene();seed(s,row(s,2,2));var fm=(CdTunnelPath.PlayFmCall)actualField(body(s),"playFm");var sites=new ArrayList<CdTunnelPath.FmSite>();
        set(body(s),"playFm",(CdTunnelPath.PlayFmCall)c->{
            assertEquals(0x91,c.sound().nativeId());assertSame(s.frame().state(),c.expectedPlayer().state());assertSame(s.frame().environment(),c.expectedPlayer().environment());
            assertEquals(0x81,s.frame().state().motion().playerControl());
            if(c.site()==CdTunnelPath.FmSite.MAIN_ACQUIRE){assertEquals(4,live(s).routine());assertEquals(0,live(s).unsignedByte(TUNNEL_PATH_SCRATCH));assertEquals(0,s.frame().state().motion().xVelocity());assertEquals(0,s.frame().state().motion().yVelocity());}
            else {assertEquals(6,live(s).routine());assertEquals(0x0880,live(s).word(TUNNEL_PATH_STEPS));assertEquals(0x1000,s.frame().state().motion().yVelocity());}
            sites.add(c.site());fm.play(c);
        });replayNext(s);var m=s.frame().state().motion();seed(s,live(s).moveByte(ROUTINE,4).moveWord(TUNNEL_PATH_TARGET_X,m.centreX16_16()>>>16)
                .moveWord(TUNNEL_PATH_TARGET_Y,((m.centreY16_16()>>>16)+0x88)&65535));replayNext(s);
        assertEquals(List.of(CdTunnelPath.FmSite.MAIN_ACQUIRE,CdTunnelPath.FmSite.MAIN_ACQUIRE,CdTunnelPath.FmSite.MAIN_ACQUIRE,
                CdTunnelPath.FmSite.GOT_PLAYER,CdTunnelPath.FmSite.GOT_PLAYER,CdTunnelPath.FmSite.GOT_PLAYER),sites);
    }

    @Test void noOpAfterSameValueSavedByteCannotReachDeleteObject() throws Exception {
        var s=scene();var source=row(s,2,2).addWord(X,0x800).moveByte(SAVED_FLAGS_ID,31);seed(s,source);var saved=s.placement().capture().savedFlags();
        assertEquals(0,saved.unsignedByte(2+3*31+1)&0x80,"actual untouched bank byte makes this a same-value native BCLR");var observer=observer(body(s));
        set(body(s),"observer",new Forward(observer){@Override public void afterOperation(Object c){
            if(!(c instanceof CdTunnelPath.Publication p && p.kind()==CdTunnelPath.MutationKind.SAVED_BYTE))super.afterOperation(c);
        }});beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,live(s));assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());retired(s);
    }

    @Test void noOpAfterDeleteCannotPretendItsReturnReceiptWasConsumed() throws Exception {
        var s=scene();seed(s,row(s,2,2).addWord(X,0x800));var observer=observer(body(s));
        set(body(s),"observer",new Forward(observer){@Override public void afterOperation(Object c){
            if(!(c instanceof CdTunnelPath.Publication p && p.kind()==CdTunnelPath.MutationKind.DELETE))super.afterOperation(c);
        }});beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertArrayEquals(new byte[64],live(s).copyBytes());retired(s);
    }

    @Test void noOpAfterSourceReadCannotPublishTheFollowingHeaderWord() throws Exception {
        var s=scene();var source=row(s,0,2);seed(s,source);var observer=observer(body(s));
        set(body(s),"observer",new Forward(observer){@Override public void afterOperation(Object c){if(!(c instanceof CdTunnelPath.WordReadCall))super.afterOperation(c);}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(header(source),live(s));retired(s);
    }

    private static class Forward implements CdTunnelPath.DispatchObserver {
        final CdTunnelPath.DispatchObserver delegate;
        Forward(CdTunnelPath.DispatchObserver delegate){this.delegate=delegate;}
        @Override public void beforeOperation(Object c){delegate.beforeOperation(c);}
        @Override public void afterOperation(Object c){delegate.afterOperation(c);}
        @Override public void failedOperation(Object c,Throwable original){delegate.failedOperation(c,original);}
    }
    private static class PlayerForward implements CdTunnelPath.PlayerOwner {
        final CdTunnelPath.PlayerOwner delegate;
        PlayerForward(CdTunnelPath.PlayerOwner delegate){this.delegate=delegate;}
        @Override public CdTunnelPath.PlayerCut read(){return delegate.read();}
        @Override public void commit(CdTunnelPath.PlayerCall c){delegate.commit(c);}
    }
    private static Object cloneRecord(Object value) throws Exception {
        var parts=value.getClass().getRecordComponents();var types=new Class<?>[parts.length];var arguments=new Object[parts.length];
        for(int at=0;at<parts.length;at++){types[at]=parts[at].getType();var accessor=parts[at].getAccessor();accessor.setAccessible(true);arguments[at]=accessor.invoke(value);}
        var constructor=value.getClass().getDeclaredConstructor(types);constructor.setAccessible(true);return constructor.newInstance(arguments);
    }
    private static CdTunnelPath body(Scene s) throws Exception {return (CdTunnelPath)actualField(s.visit(),"tunnelPath");}
    private static CdTunnelPath.PlayerOwner owner(CdTunnelPath b) throws Exception {return (CdTunnelPath.PlayerOwner)actualField(b,"playerOwner");}
    private static CdTunnelPath.DispatchObserver observer(CdTunnelPath b) throws Exception {return (CdTunnelPath.DispatchObserver)actualField(b,"observer");}
    private static CdObjectState live(Scene s){return s.placement().pool().state(127);}
    private static List<SonicCdGameModule.Act2LoadCall> familyCalls(Scene s){return calls(s).stream().filter(c->c.name().startsWith("TUNNEL_PATH_")).toList();}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.PastSound> rawSounds(Scene s) throws Exception {return (List<SonicCdGameModule.PastSound>)actualField(s.visit(),"sounds");}
    /** Sole fixture ingress: explicitly labelled selected dynamic64; actor and all other owners are actual. */
    private static CdObjectState row(Scene s,int routine,int subtype){
        var m=s.frame().state().motion();return s.placement().pool().state(127).moveByte(ID,7).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,0x51)
                .moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0)
                .moveByte(SPRITE_FLAGS,4).moveByte(FLAGS,0x70).moveByte(PRIORITY,1).moveByte(WIDTH,16)
                .moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468)
                .moveWord(TUNNEL_PATH_INDEX_END,0x0044).moveLong(TUNNEL_PATH_DATA,0x208C98).moveWord(TUNNEL_PATH_TARGET_X,0x1630).moveWord(TUNNEL_PATH_TARGET_Y,0x0290);
    }
    private static CdObjectState header(CdObjectState s){return s.moveLong(MAPPINGS,0x21D528).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,1).moveByte(WIDTH,16).moveWord(TILE,0x0541).addByte(ROUTINE,2);}
    private static CdObjectState initTwo(CdObjectState s){return header(s).moveWord(TUNNEL_PATH_INDEX_END,0x0044).moveLong(TUNNEL_PATH_DATA,0x208C98).moveWord(TUNNEL_PATH_TARGET_X,0x1630).moveWord(TUNNEL_PATH_TARGET_Y,0x0290);}
    private static Scene acquired() throws Exception {var s=scene();seed(s,row(s,2,2));replayNext(s);assertEquals(0x81,s.frame().state().motion().playerControl());return s;}

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
        assertNull(actualField(s.visit(),"activeTunnelPath"));assertFalse((boolean)actualField(s.visit(),"tunnelPathPort"));
        for(String name:new String[]{"activeFlower","activeDoor","activeChildEffect","activeLogMosqui","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        assertFalse((boolean)actualField(s.visit(),"runningObjects"));
        assertFalse((boolean)actualField(body(s),"running"));assertNull(actualField(body(s),"pendingOperation"));
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
