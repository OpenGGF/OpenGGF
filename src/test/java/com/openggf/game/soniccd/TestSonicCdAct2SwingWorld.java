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

/** Genuine public cold/Past/Results/Act2/Title/PLC ingress. All staged dynamic/actor inputs are labelled constructed.
 * Dirty secondary codec controls are detached value proofs, never private World invocation/producer authority.
 * N1ecc5f3a Swing/Main.asm:20-197; selected-Solid seam and original receipts remain production-owned.
 * Tests authored before the owned production mirrors. All Java/World/native/GL checks UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6 <noreply@openai.com> */
@Isolated
class TestSonicCdAct2SwingWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void cachedSwingUsesTheOneActualSourceMathSolidAndAppendOnly125CallCensus() throws Exception {
        var s=scene();var b=swing(s);var before=captureNative(s.h());
        assertSame(s.assets().objects().swing(),b.assets().visual());assertEquals(0x20ED60,b.assets().mappingAddressBits());
        assertSame(s.assets().objects().view(CdR12aProfile.Asset.SWING_MOTION),b.assets().motion());assertEquals(64,b.assets().motion().length());
        assertSame(s.placement(),actual(b,"placement"));assertEquals(3,b.assets().visual().mappingFrames().size());
        assertEquals(130,SonicCdGameModule.Act2LoadCall.values().length);assertEquals(124,SonicCdGameModule.Act2LoadCall.SWING_BODY.ordinal());
        assertEquals(123,SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY.ordinal());assertNativeCapture(before,s.h());
    }

    @Test void initParentAllocatesRealDirtyEarlyChildrenAndSkipsEveryPlayerPort() throws Exception {
        var s=scene();clearConstructedDynamic(s);var source=image(s,127,0).moveByte(SUBTYPE,4);seed(s,source);
        var dirty=s.placement().pool().state(32).moveByte(ROUTINE,0x6A).moveByte(SPRITE_FLAGS,0x91).moveByte(SAVED_FLAGS_ID,0xA4)
                .moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468).moveByte(SUBTYPE2,0x7F);seed(s,dirty);
        var b=swing(s);replaceTyped(b,CdSwing.PlayerPort.class,new PortProbe(port(b)) {
            @Override public CdSwing.PlayerCut read(CdSwing.PlayerReadCall c){throw new AssertionError("part0/count4 has no player read");}
            @Override public void commitSolid(CdSwing.SolidCall c){throw new AssertionError("parent has no Solid");}
            @Override public void commitY(CdSwing.PlayerYCall c){throw new AssertionError("parent has no player Y store");}});
        replayNext(s);var after=s.placement().pool().state(127);assertEquals(2,after.routine());assertEquals(4,after.unsignedByte(SWING_COUNT));
        assertEquals(source.centreX(),after.word(SWING_CENTRE_X));assertEquals(source.centreY(),after.word(SWING_CENTRE_Y));
        var expected=dirty.moveByte(ID,0x29).moveWord(SWING_CENTRE_X,source.centreX()).moveWord(SWING_CENTRE_Y,source.centreY())
                .moveByte(SUBTYPE,4).moveByte(SWING_COUNT,4).moveByte(SWING_PART,4).moveByte(MAP_FRAME,1);
        assertEquals(expected,s.placement().pool().state(32));assertEquals(0x6A,s.placement().pool().state(32).routine());
        for(int whole=33;whole<=35;whole++){assertEquals(0x29,s.placement().pool().state(whole).id());assertEquals(2,s.placement().pool().state(whole).unsignedByte(MAP_FRAME));}
    }

    @Test void finiteTileFaultKeepsOnlyNativeHeaderAndNoSizeCentreChildOrDraw() throws Exception {
        var s=scene();clearConstructedDynamic(s);var source=image(s,127,0).moveByte(SUBTYPE2,1);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdSwing.UnsupportedSwing.class,()->step(s.h(),neutral()));assertEquals(CdSwing.UnsupportedBoundary.TILE_ID_SUBTYPE2,failure.boundary());assertEquals(header(source),s.placement().pool().state(127));
        for(int whole=32;whole<127;whole++)assertArrayEquals(new byte[64],s.placement().pool().state(whole).copyBytes());retired(s);
    }

    @Test void finiteMotionFaultRetainsActualHeaderTileSizeCentresAndAllocatedChildren() throws Exception {
        var s=scene();clearConstructedDynamic(s);var source=image(s,127,0).moveByte(SUBTYPE,0x84);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdSwing.UnsupportedSwing.class,()->step(s.h(),neutral()));assertEquals(CdSwing.UnsupportedBoundary.MOTION_WORD_READ,failure.boundary());var after=s.placement().pool().state(127);
        assertEquals(header(source).moveWord(TILE,0x418).moveByte(WIDTH,24).moveByte(Y_RADIUS,8).moveWord(SWING_CENTRE_X,source.centreX())
                .moveWord(SWING_CENTRE_Y,source.centreY()).moveByte(SWING_COUNT,4),after);
        assertEquals(0x29,s.placement().pool().state(32).id());assertEquals(4,s.placement().pool().state(32).unsignedByte(SWING_PART));retired(s);
    }

    @Test void nonzeroPartInitRetainsItsDirtyCentresCountAndSkipsAllAllocation() throws Exception {
        var s=scene();clearConstructedDynamic(s);var source=image(s,127,0).moveByte(SWING_PART,2).moveByte(SWING_COUNT,3).moveByte(SUBTYPE,1);seed(s,source);
        replayNext(s);var after=s.placement().pool().state(127);assertEquals(source.word(SWING_CENTRE_X),after.word(SWING_CENTRE_X));
        assertEquals(source.word(SWING_CENTRE_Y),after.word(SWING_CENTRE_Y));assertEquals(3,after.unsignedByte(SWING_COUNT));
        for(int whole=32;whole<127;whole++)assertArrayEquals(new byte[64],s.placement().pool().state(whole).copyBytes());
    }

    @Test void nativeMotionUsesHighPhaseUnsignedProductsTwoTruncationsFractionsAndRawReversing() throws Exception {
        var s=scene();clearConstructedDynamic(s);var base=s.h().mode().getRewindRegistry().capture();
        int[][] vectors={{1,0,0xC0FE,8,0x200,0xFE00,0},{0x7F,0x1F8,0x3FFE,8,0x200,0xFE00,0},
                {0x80,0xFE08,0x7F01,8,0x200,0xFE00,0x72},{0xFF,0x1234,0x40FF,0xFFFE,0x100,0xFF00,0x80}};
        for(var v:vectors){s.h().mode().getRewindRegistry().restore(base);var source=image(s,127,2).moveByte(SWING_PART,v[0]).moveByte(SWING_COUNT,0)
                .moveWord(SWING_ANGLE,v[1]).moveWord(SWING_PHASE,v[2]).moveWord(SWING_STEP,v[3]).moveWord(SWING_MAX,v[4]).moveWord(SWING_MIN,v[5]).moveByte(SWING_REVERSING,v[6]);seed(s,source);
            var expected=motion(s,source);replayNext(s);assertScreen(expected,s.placement().pool().state(127));
            assertEquals(0x1357,s.placement().pool().state(127).word(X_FRACTION));assertEquals(0x2468,s.placement().pool().state(127).word(Y_FRACTION));}
    }

    @Test void childFrameTwoThenPlatformFrameOneAreSeparateOriginalStores() throws Exception {
        var s=scene();clearConstructedDynamic(s);seed(s,image(s,127,0).moveByte(SUBTYPE,1));var b=swing(s);var original=observer(b);var frames=new ArrayList<Integer>();
        replaceTyped(b,CdSwing.DispatchObserver.class,new Observe(original){@Override public void afterPublication(CdSwing.Publication c){super.afterPublication(c);
            if(c.kind()==CdSwing.StoreKind.CHILD_STORE)frames.add(c.output().unsignedByte(MAP_FRAME));}});
        replayNext(s);assertEquals(List.of(2,1,2,1,2,1),frames);
    }

    @Test void bothPhysicalChildOrdersUseOnlyTheActualIncreasingWalkAndFreshNativeInit() throws Exception {
        for(boolean later:new boolean[]{false,true}){var s=scene();clearConstructedDynamic(s);int parent=later?32:127,child=later?127:32;
            if(later)for(int whole=33;whole<127;whole++)seed(s,s.placement().pool().state(whole).moveByte(ID,0x24));
            seed(s,image(s,parent,0).moveByte(SUBTYPE,1));replayNext(s);assertEquals(0x29,s.placement().pool().state(child).id());
            assertEquals(later?2:0,s.placement().pool().state(child).routine());assertEquals(later?2:1,Collections.frequency(calls(s),SonicCdGameModule.Act2LoadCall.SWING_BODY));
            if(!later){replayNext(s);assertEquals(2,s.placement().pool().state(child).routine());}}
    }

    @Test void zeroCountWithFullPoolKeepsNative256FailedAttemptsAndStillCallsBothPlayers() throws Exception {
        var s=scene();clearConstructedDynamic(s);for(int whole=32;whole<127;whole++)seed(s,s.placement().pool().state(whole).moveByte(ID,0x24));
        seed(s,image(s,127,0).moveByte(SUBTYPE,0));var b=swing(s);var original=observer(b);var children=new int[1];var solids=new int[1];
        replaceTyped(b,CdSwing.DispatchObserver.class,new Observe(original){@Override public void afterPublication(CdSwing.Publication c){super.afterPublication(c);if(c.kind()==CdSwing.StoreKind.CHILD_STORE)children[0]++;}
            @Override public void beforeCall(Object c){super.beforeCall(c);if(c instanceof CdSwing.SolidCall)solids[0]++;}});
        replayNext(s);assertEquals(0,children[0]);assertEquals(12,solids[0]);assertEquals(0,s.placement().pool().state(127).unsignedByte(SWING_COUNT));assertEquals(0x29,s.placement().pool().state(127).id());
    }

    @Test void actualZeroSecondaryStillExecutesBothGenuineTopCallsWithFreshSameOwnerCuts() throws Exception {
        var s=scene();clearConstructedDynamic(s);seed(s,image(s,127,2).moveByte(SWING_PART,1).moveByte(SWING_COUNT,1));var b=swing(s);var original=port(b);var reads=new ArrayList<CdSolidObject.PlayerSlot>();var secondary=new int[1];
        replaceTyped(b,CdSwing.PlayerPort.class,new PortProbe(original){@Override public CdSwing.PlayerCut read(CdSwing.PlayerReadCall c){var bank=bytesUnchecked(s,"hudObjects");var a=s.frame().captureActor();var shared=s.frame().captureShared();
            var cut=super.read(c);reads.add(c.slot());assertEquals(a,s.frame().captureActor());assertEquals(shared,s.frame().captureShared());assertSame(bank,bytesUnchecked(s,"hudObjects"));
            if(c.slot()==CdSolidObject.PlayerSlot.SECONDARY){assertArrayEquals(new byte[64],bank.slice(0,64).copy());assertEquals(codecPlayer(s,bank.slice(0,64)),cut.player());}return cut;}
            @Override public void commitSolid(CdSwing.SolidCall c){var result=assertInstanceOf(CdSolidObject.Completed.class,c.genuineSelectedOutcome().outcome()).completedResult();
                if(c.expectedPlayerCut().selection().selected()==CdSolidObject.PlayerSlot.SECONDARY){secondary[0]++;assertEquals(Optional.of(CdSolidObject.GetOffReason.PLAYER_ABSENT),result.getOffReason());assertTrue(result.playerWrites().isEmpty());assertTrue(result.globalWrites().isEmpty());}
                super.commitSolid(c);}});
        replayNext(s);assertEquals(6,secondary[0]);assertEquals(12,reads.size());assertEquals(List.of(CdSolidObject.PlayerSlot.PRIMARY,CdSolidObject.PlayerSlot.PRIMARY,CdSolidObject.PlayerSlot.SECONDARY,CdSolidObject.PlayerSlot.SECONDARY),reads.subList(0,4));
    }

    @Test void genuinePrimaryCollisionUsesFreshSelectedCutAndSeparateIntegerYPublication() throws Exception {
        var s=scene();clearConstructedDynamic(s);seed(s,collisionImage(s,0));var b=swing(s);var original=port(b);var yCalls=new int[1];var scratch=new int[1];var lastY=new AtomicReference<CdSwing.PlayerYCall>();
        replaceTyped(b,CdSwing.PlayerPort.class,new PortProbe(original){@Override public void commitSolid(CdSwing.SolidCall c){
            var before=s.frame().state().motion();var outcome=c.genuineSelectedOutcome().outcome();
            var g=outcome instanceof CdSolidObject.Completed done?done.completedResult().globals():((CdSolidObject.NativeStopped)outcome).prefix().globals();
            var writes=outcome instanceof CdSolidObject.Completed done?done.completedResult().globalWrites():((CdSolidObject.NativeStopped)outcome).prefix().globalWrites();
            super.commitSolid(c);var e=s.frame().environment();assertEquals(g,new CdSolidObject.Globals(e.globals().invincible(),e.movement().globals().scoreChain(),e.movement().globals().primaryAngle(),e.movement().globals().secondaryAngle()));
            assertEquals(before.previousPrimaryAngle(),s.frame().state().motion().previousPrimaryAngle());assertEquals(before.previousSecondaryAngle(),s.frame().state().motion().previousSecondaryAngle());
            if(writes.contains(CdSolidObject.GlobalField.PRIMARY_ANGLE)||writes.contains(CdSolidObject.GlobalField.SECONDARY_ANGLE))scratch[0]++;
        }@Override public void commitY(CdSwing.PlayerYCall c){
            var before=s.frame().captureActor();var shared=s.frame().captureShared();var p=c.expectedPlayerCut().player();
            assertEquals((c.expectedSource().centreY()-8-(byte)p.actor().motion().yRadius()+2)&65535,c.value());
            assertEquals(p.actor().motion().centreY16_16()>>>16,c.expectedYWord());lastY.set(c);
            assertThrows(IllegalStateException.class,()->original.commitY(new CdSwing.PlayerYCall(c.actualPoolCut(),c.expectedSource(),c.expectedPlayerCut(),c.expectedYWord(),c.value())));
            super.commitY(c);yCalls[0]++;assertThrows(IllegalStateException.class,()->original.commitY(c));
            if(c.expectedPlayerCut().selection().selected()==CdSolidObject.PlayerSlot.PRIMARY){assertEquals(c.value(),s.frame().state().motion().centreY16_16()>>>16);assertEquals(before.state().motion().centreY16_16()&65535,s.frame().state().motion().centreY16_16()&65535);}
            assertEquals(shared,s.frame().captureShared());}});
        replayNext(s);assertTrue(yCalls[0]>0,"The genuine first landing must reach the separate native Y WORD");assertTrue(scratch[0]>0,"The actual shared floor probe must declare its scratch stores");
        var closed=captureNative(s.h());assertThrows(IllegalStateException.class,()->original.commitY(lastY.get()));assertNativeCapture(closed,s.h());
    }

    @Test void nativeFallbackStopAdoptsOnlyOriginalRadius12PrefixAndIdenticalException() throws Exception {
        // Constructed native controlled/charged input skips earlier MODE balance, so reserved link2 is first read by TopSolid.
        var s=scene();clearConstructedDynamic(s);constructStandingPrimary(s,2);seed(s,collisionImage(s,4));var b=swing(s);var original=port(b);var reached=new AtomicReference<CdSolidObject.NativeStopped>();
        replaceTyped(b,CdSwing.PlayerPort.class,new PortProbe(original){@Override public void commitSolid(CdSwing.SolidCall c){if(c.genuineSelectedOutcome().outcome() instanceof CdSolidObject.NativeStopped stop)reached.set(stop);super.commitSolid(c);}});
        beforeForward(s);var thrown=assertThrows(CdSolidObject.UnsupportedSolidObject.class,()->step(s.h(),neutral()));assertNotNull(reached.get());
        assertSame(reached.get().stop().originalException(),thrown);assertEquals(CdSolidObject.StopSite.STAND_RELEASE,reached.get().stop().site());
        assertEquals(12,s.placement().pool().state(127).unsignedByte(Y_RADIUS));assertEquals(reached.get().prefix().object(),s.placement().pool().state(127));
        assertFalse(((List<?>)actualField(actualField(s.visit(),"values"),"calls")).contains(SonicCdGameModule.Act2LoadCall.SWING_BODY));retired(s);
    }

    @Test void nativeCentreCullUsesReturnedDrawSourceThenOnlyActualSavedByteAndFull64Delete() throws Exception {
        var s=scene();clearConstructedDynamic(s);var fg=((SonicCdPlayerPresentation)actual(s.visit(),"presentation")).act2ForegroundImage();
        var source=image(s,127,2).moveWord(SWING_CENTRE_X,fg.cameraX()+800).moveByte(SWING_PART,32).moveByte(SWING_COUNT,1).moveWord(SWING_ANGLE,0).moveWord(SWING_PHASE,0xC000).moveWord(SWING_STEP,0);seed(s,source);
        var b=swing(s);var original=observer(b);var saved=new AtomicReference<CdSwing.Publication>();
        replaceTyped(b,CdSwing.DispatchObserver.class,new Observe(original){@Override public void beforePublication(CdSwing.Publication c){super.beforePublication(c);if(c.kind()==CdSwing.StoreKind.SAVED_FLAG_CLEAR){saved.set(c);assertEquals((fg.cameraX()+288)&65535,c.expectedSource().centreX());}}});
        replayNext(s);assertNotNull(saved.get());var expected=saved.get().actualPoolCut().savedFlags().copy();expected[saved.get().savedByteIndex()]&=0x7F;
        assertArrayEquals(expected,s.placement().capture().savedFlags().copy());assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());
    }

    @Test void rawWarpAddressClampIsOnlyADetachedNativeValueProofNotWorldInvocationAuthority() throws Exception {
        var s=scene();var source=image(s,127,2).moveByte(SAVED_FLAGS_ID,255);
        for(int period:new int[]{0,1,2,0x80,0x81,0x82,0xFF})for(int direction:new int[]{0,1,0xFF,0x80}){
            var g=new CdSwing.Globals(0,period,direction);int p=period&127;if((period&128)!=0){int w=(p-(byte)direction)&65535;p=(short)w<0?0:w<3?w:2;}
            assertEquals(2+(short)((3*255+p)&65535),invoke(s.visit(),"swingSavedByteIndex",new Class<?>[]{CdSwing.Globals.class,CdObjectState.class},g,source));}
    }

    @Test void noOpBeforeOrAfterOriginalPublicationCannotBorrowPreparedOrCompletedReceipts() throws Exception {
        for(boolean before:new boolean[]{true,false}){var s=scene();clearConstructedDynamic(s);var source=image(s,127,0);seed(s,source);var original=observer(swing(s));
            replaceTyped(swing(s),CdSwing.DispatchObserver.class,new Observe(original){@Override public void beforePublication(CdSwing.Publication c){if(!before)super.beforePublication(c);}
                @Override public void afterPublication(CdSwing.Publication c){if(before)super.afterPublication(c);}});
            beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(before?source:header(source),s.placement().pool().state(127));retired(s);}
    }

    @Test void equalClonedDuplicateAndRetiredPublicationsCannotBorrowOriginalSharedOrdinal() throws Exception {
        var s=scene();clearConstructedDynamic(s);seed(s,image(s,127,0).moveByte(SUBTYPE,1));var b=swing(s);var original=observer(b);var last=new AtomicReference<CdSwing.Publication>();
        replaceTyped(b,CdSwing.DispatchObserver.class,new Observe(original){@Override public void beforePublication(CdSwing.Publication c){last.set(c);
            assertThrows(IllegalStateException.class,()->original.beforePublication(new CdSwing.Publication(c.ordinal(),c.kind(),c.actualPoolCut(),c.expectedSource(),c.before(),c.output(),c.savedByteIndex(),c.previousValue(),c.value())));
            super.beforePublication(c);assertThrows(IllegalStateException.class,()->original.beforePublication(c));}
            @Override public void afterPublication(CdSwing.Publication c){super.afterPublication(c);assertThrows(IllegalStateException.class,()->original.afterPublication(c));}});
        replayNext(s);assertFalse(b.isCurrentCall(last.get()));var before=captureNative(s.h());assertThrows(IllegalStateException.class,()->original.beforePublication(last.get()));assertNativeCapture(before,s.h());
    }

    @Test void noOpSolidYOrDrawCannotBorrowSuccessfulSameValueCompletion() throws Exception {
        for(int kind=0;kind<3;kind++){var s=scene();clearConstructedDynamic(s);seed(s,kind==2?collisionImage(s,0):image(s,127,2).moveByte(SWING_PART,kind==0?1:0).moveByte(SWING_COUNT,1));var b=swing(s);
            if(kind==0)replaceTyped(b,CdSwing.PlayerPort.class,new PortProbe(port(b)){@Override public void commitSolid(CdSwing.SolidCall c){}});
            else if(kind==2)replaceTyped(b,CdSwing.PlayerPort.class,new PortProbe(port(b)){@Override public void commitY(CdSwing.PlayerYCall c){}});
            else replaceTyped(b,CdSwing.DrawObjectCall.class,(CdSwing.DrawObjectCall)c->{});
            beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));retired(s);}
    }

    @Test void clonedRepeatedRetiredReadSolidAndDrawCallsCannotAcquireWorldAuthority() throws Exception {
        var s=scene();clearConstructedDynamic(s);seed(s,image(s,127,2).moveByte(SWING_PART,1).moveByte(SWING_COUNT,1));var b=swing(s);var original=port(b);var lastRead=new AtomicReference<CdSwing.PlayerReadCall>();var lastSolid=new AtomicReference<CdSwing.SolidCall>();
        replaceTyped(b,CdSwing.PlayerPort.class,new PortProbe(original){@Override public CdSwing.PlayerCut read(CdSwing.PlayerReadCall c){lastRead.set(c);
            assertThrows(IllegalStateException.class,()->original.read(new CdSwing.PlayerReadCall(c.actualPoolCut(),c.expectedSource(),c.slot())));var answer=super.read(c);assertThrows(IllegalStateException.class,()->original.read(c));return answer;}
            @Override public void commitSolid(CdSwing.SolidCall c){lastSolid.set(c);assertThrows(IllegalStateException.class,()->original.commitSolid(new CdSwing.SolidCall(c.actualPoolCut(),c.expectedSource(),c.expectedPlayerCut(),c.genuineSelectedOutcome())));super.commitSolid(c);assertThrows(IllegalStateException.class,()->original.commitSolid(c));}});
        var draw=typed(b,CdSwing.DrawObjectCall.class);var lastDraw=new AtomicReference<CdSwing.DrawCall>();
        replaceTyped(b,CdSwing.DrawObjectCall.class,(CdSwing.DrawObjectCall)c->{lastDraw.set(c);assertThrows(IllegalStateException.class,()->draw.drawObject(new CdSwing.DrawCall(c.actualPoolCut(),c.expectedSource())));draw.drawObject(c);assertThrows(IllegalStateException.class,()->draw.drawObject(c));});
        replayNext(s);var before=captureNative(s.h());assertThrows(IllegalStateException.class,()->original.read(lastRead.get()));assertThrows(IllegalStateException.class,()->original.commitSolid(lastSolid.get()));assertThrows(IllegalStateException.class,()->draw.drawObject(lastDraw.get()));assertNativeCapture(before,s.h());
    }

    @Test void equalPlayerCutCloneCannotReplaceTheOriginalReturnedReadAtTheNextNativeCall() throws Exception {
        var s=scene();clearConstructedDynamic(s);seed(s,image(s,127,2).moveByte(SWING_PART,1).moveByte(SWING_COUNT,1));var b=swing(s);var original=port(b);
        var seenSource=new AtomicReference<CdObjectState>();var seenActor=new AtomicReference<CdPlayerFrameRuntime.ActorState>();var seenShared=new AtomicReference<CdPlayerFrameRuntime.SharedState>();
        replaceTyped(b,CdSwing.PlayerPort.class,new PortProbe(original){@Override public CdSwing.PlayerCut read(CdSwing.PlayerReadCall c){var answer=super.read(c);
            seenSource.set(c.expectedSource());seenActor.set(s.frame().captureActor());seenShared.set(s.frame().captureShared());return new CdSwing.PlayerCut(answer.player(),answer.gates(),answer.globals(),answer.selection());}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertNotNull(seenSource.get());
        assertEquals(seenSource.get(),s.placement().pool().state(127));
        // Native failure closes Frame admission; inspect immutable values without reopening capture.
        assertEquals(seenActor.get().state(),s.frame().state());assertEquals(seenActor.get().display(),s.frame().display());
        assertEquals(seenShared.get().environment(),s.frame().environment());assertEquals(seenShared.get().artState(),s.frame().artState());
        assertEquals(seenShared.get().paused(),s.frame().paused());assertEquals(seenShared.get().vIntClock(),s.frame().vIntClock());retired(s);
    }

    @Test void reentryCaptureOtherFamilyAndUnrelatedFull64CorruptionFailBeforePublication() throws Exception {
        var s=scene();clearConstructedDynamic(s);var source=image(s,127,0);seed(s,source);var b=swing(s);var original=observer(b);
        replaceTyped(b,CdSwing.DispatchObserver.class,new Observe(original){@Override public void beforePublication(CdSwing.Publication c){
            assertThrows(IllegalStateException.class,()->b.run(127,new CdSwing.Globals(0,1,0)));assertThrows(IllegalStateException.class,()->s.h().mode().getRewindRegistry().capture());
            assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runMovingSpring",new Class<?>[]{int.class},126));super.beforePublication(c);
            s.placement().pool().write(s.placement().pool().state(125).moveByte(ROUTINE_2,0x44));}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,s.placement().pool().state(127));assertEquals(0x44,s.placement().pool().state(125).unsignedByte(ROUTINE_2));retired(s);
    }

    @Test void foreignEqualPlacementCannotSupplyOriginalWorldCutOrRepairItsSource() throws Exception {
        var s=scene();clearConstructedDynamic(s);var source=image(s,127,0);seed(s,source);var foreign=CdObjectPlacementRuntime.fromAssets(s.assets(),s.placement().capture().savedFlags());foreign.pool().write(image(new Scene(s.h(),s.visit(),s.frame(),foreign,s.assets()),127,0));
        set(swing(s),"placement",foreign);beforeForward(s);assertThrows(IllegalArgumentException.class,()->step(s.h(),neutral()));assertEquals(source,s.placement().pool().state(127));retired(s);
    }

    @Test void genuineDrawFaultKeepsItsBclrAndPreventsTheSavedAndDeleteTail() throws Exception {
        var s=scene();clearConstructedDynamic(s);var source=image(s,127,2).moveWord(SWING_CENTRE_X,0x7000).moveByte(SPRITE_FLAGS,0x84);seed(s,source);var b=swing(s);var draw=typed(b,CdSwing.DrawObjectCall.class);var sentinel=new AssertionError("after original Swing Draw");
        replaceTyped(b,CdSwing.DrawObjectCall.class,(CdSwing.DrawObjectCall)c->{draw.drawObject(c);throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(0x29,s.placement().pool().state(127).id());assertEquals(4,s.placement().pool().state(127).spriteFlags());assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void realCpuMappingUsesExistingCurrentAndMissingSwingKnowledgeHasNoAssetFallback() throws Exception {
        var s=scene();clearConstructedDynamic(s);var fg=((SonicCdPlayerPresentation)actual(s.visit(),"presentation")).act2ForegroundImage();
        seed(s,image(s,127,2).moveWord(SWING_CENTRE_X,fg.cameraX()+100).moveWord(SWING_CENTRE_Y,fg.cameraYWord()+100).moveByte(MAP_FRAME,1));
        step(s.h(),neutral());healthy(s);var sat=cpu(s);assertTrue(sat.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));
        var selected=s.h().module().act2SelectedDisplay().inputs();var current=s.h().module().act2LoadState().section().residency();var unknown=CdR12aCameraArtResidency.unknown();
        for(var region:CdR12aCameraArtResidency.Region.values())for(int offset=0;offset<region.byteLength();offset+=32){int at=region.destinationWord()+offset;
            if((at<0x8300 || at>=0x84C0)&&current.region(region).knownByte(offset).isPresent())unknown=CdR12aCameraArtResidency.withTile(unknown,at,CdR12aCameraArtResidency.readTile(current,at>>>5));}
        var projected=SonicCdAct2Display.project(new SonicCdAct2Display.Inputs(Optional.of(sat),CdAssetBytes.copyOf(sat.bytes()),selected.palette(),selected.hscroll(),selected.vscrollLongBits(),true,selected.foreground(),selected.background(),unknown,selected.player()));
        var failure=assertInstanceOf(SonicCdPastDisplay.Blocked.class,projected.projection()).failure();assertEquals(SonicCdPastDisplay.Boundary.UNKNOWN_PATTERN,failure.boundary());assertTrue(failure.nativePatternId()>=0x418&&failure.nativePatternId()<0x426);assertSame(current,s.h().module().act2LoadState().section().residency());
    }

    @Test void detachedDirtySecondaryCodecReadsAll27SemanticInputsWithTheirNativeWidths() throws Exception {
        var s=scene();var raw=dirtySecondary();var p=codecPlayer(s,raw);var m=p.actor().motion();
        assertEquals((int)raw.unsignedLong(8),m.centreX16_16());assertEquals((int)raw.unsignedLong(12),m.centreY16_16());
        assertEquals((short)raw.unsignedWord(16),m.xVelocity());assertEquals((short)raw.unsignedWord(18),m.yVelocity());assertEquals((short)raw.unsignedWord(20),m.groundVelocity());
        assertEquals(raw.unsignedByte(36),m.routine());assertEquals(raw.unsignedByte(38),m.angle());assertEquals(raw.unsignedByte(34),m.flags());assertEquals(raw.unsignedByte(1),m.spriteFlags());
        assertEquals(raw.unsignedByte(23),m.xRadius());assertEquals(raw.unsignedByte(22),m.yRadius());assertEquals(raw.unsignedByte(28),m.animation());assertEquals(raw.unsignedByte(29),m.previousAnimation());
        assertEquals(raw.unsignedByte(60),m.playerJump());assertEquals(raw.unsignedByte(42),m.charge());assertEquals((short)raw.unsignedWord(62),m.moveLock());assertEquals(raw.unsignedByte(56),m.stick());
        assertEquals(raw.unsignedByte(44),m.playerControl());assertEquals(raw.unsignedByte(54),m.previousPrimaryAngle());assertEquals(raw.unsignedByte(55),m.previousSecondaryAngle());
        assertEquals(raw.unsignedWord(2),p.actor().tile());assertEquals(raw.unsignedByte(24),p.actor().priority());assertEquals(raw.unsignedWord(48),p.actor().hurt());assertEquals(raw.unsignedByte(57),p.actor().rotationDistance());
        assertEquals(raw.unsignedByte(0),p.slotId());assertEquals(raw.unsignedByte(32),p.pushObject());assertEquals(raw.unsignedByte(61),p.standObject());
    }

    @Test void detachedDirtyIdZeroUsesRealSelectedGetOffSoundAndMasksWithoutClaimingWorldProducerAuthority() throws Exception {
        var s=scene();var beforeWorld=captureNative(s.h());var raw=dirtySecondary().copy();raw[0]=0;raw[0x24]=2;raw[0x1C]=0;raw[0x2C]=1;
        raw[0x22]=8;raw[0x2A]=5;raw[0x20]=40;raw[0x3D]=40;var image=CdAssetBytes.copyOf(raw);var selected=codecPlayer(s,image);
        var pool=new CdObjectPool();var source=pool.state(40).moveByte(ID,0x29).moveByte(FLAGS,8);pool.write(source);
        var e=s.frame().environment();var g=e.movement().globals();var globals=new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());
        var actual=new CdSolidObject(CdPlayerMovement.fromStageSource(s.frame().stageSource())).runWithPrefix(CdSolidObject.Entry.TOP_SOLID_OBJECT,source,selected,
                new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),globals,pool.capture(),new CdSolidObject.PlayerSelection(CdSolidObject.PlayerSlot.SECONDARY,s.frame().state().motion().flags()));
        var result=assertInstanceOf(CdSolidObject.Completed.class,actual.outcome()).completedResult();assertEquals(Optional.of(CdSolidObject.GetOffReason.PLAYER_ABSENT),result.getOffReason());
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),result.fmRequests());assertEquals(0,result.player().pushObject());assertEquals(0,result.player().standObject());assertEquals(5,result.player().actor().motion().charge());
        var patched=codecImage(s,image,selected,result.player(),result.playerWrites());assertEquals(result.player(),codecPlayer(s,patched));assertEquals(0,patched.unsignedByte(0));
        assertEquals(0,image.unsignedByte(0));assertNotEquals(image.unsignedByte(0x22),patched.unsignedByte(0x22));assertEquals(globals,result.globals());assertNativeCapture(beforeWorld,s.h());
    }

    @Test void detachedSecondaryCodecPatchesEvery22MaskIndependentlyAndPreservesAllOtherRawBytes() throws Exception {
        var s=scene();var raw=dirtySecondary();var before=codecPlayer(s,raw);
        for(var field:CdSolidObject.PlayerField.values()){byte[] changed=raw.copy();int at=field.byteOffset();changed[at]^=1;var after=codecPlayer(s,CdAssetBytes.copyOf(changed));
            var result=codecImage(s,raw,before,after,Set.of(field));assertArrayEquals(changed,result.copy(),field.name());
            assertThrows(IllegalArgumentException.class,()->codecImage(s,raw,before,after,Set.of()),field.name());}
        assertEquals(22,CdSolidObject.PlayerField.values().length);
        for(int at:new int[]{1,0x1D,0x2A,0x3E,0x36,0x37,0}){var changed=raw.copy();changed[at]^=1;var wrong=codecPlayer(s,CdAssetBytes.copyOf(changed));
            assertThrows(IllegalArgumentException.class,()->codecImage(s,raw,before,wrong,Set.of(CdSolidObject.PlayerField.values())));}
    }

    @Test void detachedCodecRejectsStaleProjectionAndPatchesOnlyReservedSlotOneInTheFull31Bank() throws Exception {
        var s=scene();var raw=dirtySecondary();var p=codecPlayer(s,raw);var changed=raw.copy();changed[0]^=1;var stale=codecPlayer(s,CdAssetBytes.copyOf(changed));
        assertThrows(IllegalArgumentException.class,()->codecImage(s,raw,stale,p,Set.of()));assertThrows(IllegalArgumentException.class,()->codecPlayer(s,CdAssetBytes.copyOf(new byte[63])));
        byte[] full=new byte[31*64];for(int i=0;i<full.length;i++)full[i]=(byte)(17*i+3);var bank=CdAssetBytes.copyOf(full);
        var result=(CdAssetBytes)invoke(s.visit(),"replaceSwingSecondary",new Class<?>[]{CdAssetBytes.class,CdAssetBytes.class},bank,raw);
        assertArrayEquals(raw.copy(),result.slice(0,64).copy());assertArrayEquals(bank.slice(64,30*64).copy(),result.slice(64,30*64).copy());
        assertArrayEquals(full,bank.copy());
    }

    private static CdSwing swing(Scene s)throws Exception{return (CdSwing)actualField(s.visit(),"swing");}
    private static Object actual(Object owner,String name){try{return actualField(owner,name);}catch(Exception e){throw new AssertionError(e);}}
    private static CdAssetBytes bytesUnchecked(Scene s,String name){try{return bytes(s,name);}catch(Exception e){throw new AssertionError(e);}}
    private static <T>T typed(Object owner,Class<T> type){try{for(var f:owner.getClass().getDeclaredFields())if(f.getType()==type){f.setAccessible(true);return type.cast(f.get(owner));}throw new AssertionError("No field "+type);}catch(IllegalAccessException e){throw new AssertionError(e);}}
    private static void replaceTyped(Object owner,Class<?> type,Object value)throws Exception{for(var f:owner.getClass().getDeclaredFields())if(f.getType()==type){f.setAccessible(true);f.set(owner,value);return;}throw new AssertionError("No field "+type);}
    private static CdSwing.PlayerPort port(CdSwing body){return typed(body,CdSwing.PlayerPort.class);}
    private static CdSwing.DispatchObserver observer(CdSwing body){return typed(body,CdSwing.DispatchObserver.class);}
    private static class PortProbe implements CdSwing.PlayerPort {
        final CdSwing.PlayerPort original;PortProbe(CdSwing.PlayerPort original){this.original=original;}
        public CdSwing.PlayerCut read(CdSwing.PlayerReadCall c){return original.read(c);}
        public void commitSolid(CdSwing.SolidCall c){original.commitSolid(c);}
        public void commitY(CdSwing.PlayerYCall c){original.commitY(c);}
    }
    private static class Observe implements CdSwing.DispatchObserver {
        final CdSwing.DispatchObserver original;Observe(CdSwing.DispatchObserver original){this.original=original;}
        public void beforePublication(CdSwing.Publication c){original.beforePublication(c);}
        public void afterPublication(CdSwing.Publication c){original.afterPublication(c);}
        public void beforeCall(Object c){original.beforeCall(c);}
        public void afterCall(Object c){original.afterCall(c);}
    }
    /** Constructed offscreen, native-retained source; it introduces no art/current/actor/camera state. */
    private static CdObjectState image(Scene s,int whole,int routine){
        var fg=((SonicCdPlayerPresentation)actual(s.visit(),"presentation")).act2ForegroundImage();
        return s.placement().pool().state(whole).moveByte(ID,0x29).moveByte(ROUTINE,routine).moveWord(X,fg.cameraX()+400).moveWord(Y,fg.cameraYWord()+100)
                .moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468).moveByte(SPRITE_FLAGS,4).moveByte(FLAGS,0).moveByte(SUBTYPE,4).moveByte(SUBTYPE2,0)
                .moveByte(PRIORITY,3).moveByte(WIDTH,24).moveByte(Y_RADIUS,8).moveLong(MAPPINGS,0x20ED60).moveWord(TILE,0x418).moveByte(MAP_FRAME,0).moveByte(SAVED_FLAGS_ID,185)
                .moveWord(SWING_CENTRE_X,fg.cameraX()+400).moveWord(SWING_CENTRE_Y,fg.cameraYWord()+100).moveWord(SWING_ANGLE,0).moveWord(SWING_PHASE,0xC000)
                .moveWord(SWING_STEP,8).moveWord(SWING_MAX,0x200).moveWord(SWING_MIN,0xFE00).moveByte(SWING_PART,0).moveByte(SWING_COUNT,1).moveByte(SWING_REVERSING,0);
    }
    private static CdObjectState header(CdObjectState source){return source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveByte(PRIORITY,3).moveLong(MAPPINGS,0x20ED60);}
    private static CdObjectState motion(Scene s,CdObjectState source){
        int angle=(source.word(SWING_ANGLE)+(source.unsignedByte(SWING_REVERSING)==0?source.word(SWING_STEP):-source.word(SWING_STEP)))&65535;
        int phase=(source.word(SWING_PHASE)+angle)&65535;int reversing=source.unsignedByte(SWING_REVERSING);
        if(reversing==0)reversing=angle==source.word(SWING_MAX)?255:0;else if(angle==source.word(SWING_MIN))reversing=0;
        var trig=new CdPlayerMath(s.frame().stageSource().player()).calcSine(phase>>>8);int radius=(((short)(byte)source.unsignedByte(SWING_PART))<<4)&65535;
        int x=(short)((int)(((long)(trig.sine()&65535)*radius)&0xFFFFFFFFL)>>4)>>4;
        int y=(short)((int)(((long)(trig.cosine()&65535)*radius)&0xFFFFFFFFL)>>4)>>4;
        return source.moveWord(SWING_ANGLE,angle).moveWord(SWING_PHASE,phase).moveByte(SWING_REVERSING,reversing)
                .moveWord(Y,source.word(SWING_CENTRE_Y)+y).moveWord(X,source.word(SWING_CENTRE_X)+x);
    }
    private static CdObjectState collisionImage(Scene s,int extra){var p=s.frame().state().motion();return image(s,127,2).moveByte(SPRITE_FLAGS,0x84).moveByte(SWING_PART,1).moveByte(SWING_COUNT,1)
            .moveWord(SWING_CENTRE_X,(p.centreX16_16()>>>16)+16).moveWord(SWING_CENTRE_Y,(p.centreY16_16()>>>16)+p.yRadius()+8+extra)
            .moveWord(X,p.centreX16_16()>>>16).moveWord(Y,(p.centreY16_16()>>>16)+p.yRadius()+8+extra).moveWord(SWING_ANGLE,0).moveWord(SWING_STEP,0).moveWord(SWING_PHASE,0xC000);}
    /** Explicit constructed actor/link fixture through the existing public same-owner capture API. */
    private static void constructStandingPrimary(Scene s,int stand){var a=s.frame().captureActor();var b=a.state();var m=b.motion();
        var next=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags()|8,m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),1,m.moveLock(),m.stick(),1,m.previousPrimaryAngle(),m.previousSecondaryAngle());
        s.frame().restoreActor(new CdPlayerFrameRuntime.ActorState(a.owner(),new CdPlayerBody.State(next,b.animation(),b.header(),b.timers(),b.history(),b.rotationDistance(),new CdPlayerBody.ObjectLinks(b.objectLinks().pushObject(),stand),b.rotationAngle()),a.display()));}
    private static void assertScreen(CdObjectState expected,CdObjectState actual){assertEquals(expected.moveByte(SPRITE_FLAGS,expected.spriteFlags()&127),actual.moveByte(SPRITE_FLAGS,actual.spriteFlags()&127));}
    private static void clearConstructedDynamic(Scene s){for(int whole=32;whole<128;whole++)s.placement().pool().deleteObject(whole);}
    private static CdAssetBytes dirtySecondary(){byte[] b=new byte[64];for(int i=0;i<64;i++)b[i]=(byte)(0x80+5*i);return CdAssetBytes.copyOf(b);}
    private static CdSolidObject.Player codecPlayer(Scene s,CdAssetBytes raw){try{return (CdSolidObject.Player)invoke(s.visit(),"swingSecondaryPlayer",new Class<?>[]{CdAssetBytes.class},raw);}catch(RuntimeException|Error e){throw e;}catch(Exception e){throw new AssertionError(e);}}
    private static CdAssetBytes codecImage(Scene s,CdAssetBytes raw,CdSolidObject.Player before,CdSolidObject.Player after,Set<CdSolidObject.PlayerField> writes){try{return (CdAssetBytes)invoke(s.visit(),"swingSecondaryImage",new Class<?>[]{CdAssetBytes.class,CdSolidObject.Player.class,CdSolidObject.Player.class,Set.class},raw,before,after,writes);}catch(RuntimeException|Error e){throw e;}catch(Exception e){throw new AssertionError(e);}}
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
        for(String name:new String[]{"activeSwing","activeMovingSpring","activeRobotGenerator","activeTunnelDoorSplash","activeDoor","activeChildEffect","activeLogMosqui","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"swingPort","drawingSwing","movingSpringPort","drawingMovingSpring","robotPort","drawingRobotGenerator","splashPort","drawingSplash","doorPort","drawingDoor","drawingChildEffect","logMosquiPort","drawingLogMosqui","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
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
