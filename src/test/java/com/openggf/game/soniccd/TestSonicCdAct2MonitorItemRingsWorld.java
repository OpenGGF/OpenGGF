package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.ready;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Real cold Harness and public controller/GameLoop ingress to returned12892, then explicitly
 * CONSTRUCTED slot/global fixtures. No actual13265 route/pixel claim or failed-state hydration.
 * No forged Frame/Snapshot/outcome or public record equality supplies current producer authority.
 * N1ecc5f3a Monitor and Time Post/Main.asm:419-425,479-517,624-638. DEV request metadata,
 * never acoustic/native sound timing evidence. Source-only authoring: all20 Java cases UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@Isolated
class TestSonicCdAct2MonitorItemRingsWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement) { }

    @Test void constructedTenRingsUsesOriginalRewardPrefixResultAndDraw() throws Exception {
        var s=scene();var before=new CdRingGlobals(17,7,0xA1,0xA4,0xD2,0xBEEF,0x81,0xFE);
        var observed=award(s,before,new CdRingGlobals(27,7,0xA1,1,0,0xBEEF,0x81,0xFE),CdMonitorItem.RingAwardSound.FM_RING);
        assertSame(observed.result(),observed.completion().effects().stream().filter(CdMonitorItem.RingsAwarded.class::isInstance)
                .map(CdMonitorItem.RingsAwarded.class::cast).findFirst().orElseThrow().result());
        assertSame(item(s),actualField(s.visit(),"monitorItem"));assertSame(actualField(s.visit(),"monitorItemAssets"),item(s).assets());
        assertEquals(4,s.placement().pool().state(127).routine());assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));
        assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));retired(s);
    }

    @Test void constructedHundredThresholdWrapsLifeAndHudBytesAndSelectsSubCpu() throws Exception {
        var s=scene();award(s,new CdRingGlobals(90,255,0xA1,0xA4,255,0xBEEF,0x81,0xFE),
                new CdRingGlobals(100,0,0xA3,1,1,0xBEEF,0x81,0xFE),CdMonitorItem.RingAwardSound.SCMD_YESSFX);
    }

    @Test void constructedTwoHundredThresholdUsesOldHundredFlag() throws Exception {
        var s=scene();award(s,new CdRingGlobals(190,12,0xA3,0xA4,9,0xBEEF,0x81,0xFE),
                new CdRingGlobals(200,13,0xA7,1,1,0xBEEF,0x81,0xFE),CdMonitorItem.RingAwardSound.SCMD_YESSFX);
    }

    @Test void constructedNewHundredBitBranchesBeforeCheckingTwoHundred() throws Exception {
        var s=scene();award(s,new CdRingGlobals(195,12,0xA1,0xA4,9,0xBEEF,0x81,0xFE),
                new CdRingGlobals(205,13,0xA3,1,1,0xBEEF,0x81,0xFE),CdMonitorItem.RingAwardSound.SCMD_YESSFX);
    }

    @Test void constructedAlreadyPaidThresholdsEmitOnlyRingFm() throws Exception {
        var s=scene();award(s,new CdRingGlobals(190,12,0xA7,0xA4,9,0xBEEF,0x81,0xFE),
                new CdRingGlobals(200,12,0xA7,1,0,0xBEEF,0x81,0xFE),CdMonitorItem.RingAwardSound.FM_RING);
    }

    @Test void constructedWordWrapAndDirtyUnrelatedFieldsRemainNative() throws Exception {
        // Kernel raw HUD-byte wrapping is covered separately; this real World path respects preceding HUD writes.
        var s=scene();award(s,new CdRingGlobals(65530,255,0xFD,0,0,0xFFFF,0xFF,0xFF),
                new CdRingGlobals(4,255,0xFD,1,0,0xFFFF,0xFF,0xFF),CdMonitorItem.RingAwardSound.FM_RING);
    }

    @Test void constructedInitMovesThirtyTwoTimesBeforeOriginalThirtyThirdAward() throws Exception {
        var s=scene();seedItem(s,127,0,1,-17);var b=item(s);var o=itemObserver(b);var awards=new int[1];
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.RingCall r){awards[0]++;assertEquals(4,r.source().routine());assertEquals(29,r.source().word(MONITOR_ITEM_DELETE));}
            super.beforeOperation(c);
        }});
        for(int n=1;n<=32;n++){next(s);assertEquals(0,awards[0]);}
        assertEquals(0x20A8B5,s.placement().pool().state(127).longBits(MAPPINGS));next(s);
        assertEquals(1,awards[0]);assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));retired(s);
    }

    @Test void constructedAwardDeletesAfterThirtyLaterCallsAndStillDrawsIdZero() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var b=item(s);var o=itemObserver(b);var awards=new int[1];var deletedDraws=new int[1];
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.RingCall)awards[0]++;
            if(c instanceof CdMonitorItem.DrawCall d && d.source().id()==0){deletedDraws[0]++;assertArrayEquals(new byte[64],d.source().copyBytes());}
            super.beforeOperation(c);
        }});
        next(s);assertEquals(1,ringSounds(s).size());for(int n=1;n<=30;n++)next(s);
        assertEquals(1,awards[0]);assertEquals(1,deletedDraws[0]);assertEquals(0,s.placement().pool().state(127).id());
        assertEquals(0,ringSounds(s).size(),"Sound ledger is current-call, not an award history");retired(s);
    }

    @Test void constructedFullPoolAwardsEarlierAndLaterPhysicalSlotsWithoutAllocation() throws Exception {
        var s=scene();for(int whole=32;whole<128;whole++)seedItem(s,whole,2,1,-0x100);
        seedItem(s,32,2,1,0);seedItem(s,127,2,1,0);assertTrue(s.placement().pool().findObjSlot().isEmpty());
        var b=item(s);var o=itemObserver(b);var order=new ArrayList<Integer>();var reached=new ArrayList<Integer>();
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.RingCall r){order.add(r.source().identity().wholeIndex());reached.add(uncheckedRing(s).rings());}
            super.beforeOperation(c);
        }});
        next(s);assertEquals(List.of(32,127),order);assertEquals((reached.getFirst()+10)&65535,reached.getLast());
        assertEquals(2,ringSounds(s).size());assertTrue(s.placement().pool().findObjSlot().isEmpty());retired(s);
    }

    @Test void publicEqualNoopResultCannotReplaceActualRingAndSoundAdoption() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var cut=observeRingCut(s);var b=item(s);
        set(b,"ringAward",(CdMonitorItem.RingAwardPort)c->new CdMonitorItem.RingAwardResult(CdMonitorItem.RingAwardSound.FM_RING));
        assertThrows(IllegalStateException.class,()->next(s));assertEquals(cut.get().ring(),ring(s));assertSame(cut.get().ledger(),ledger(s));
        assertEquals(4,s.placement().pool().state(127).routine());assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));retired(s);
    }

    @Test void clonedResultAfterRealAwardRetainsPrefixButCannotCompleteReceipt() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var cut=observeRingCut(s);var b=item(s);var port=(CdMonitorItem.RingAwardPort)actualField(b,"ringAward");
        set(b,"ringAward",(CdMonitorItem.RingAwardPort)c->new CdMonitorItem.RingAwardResult(port.award(c).sound()));
        assertThrows(IllegalStateException.class,()->next(s));assertEquals((cut.get().ring().rings()+10)&65535,ring(s).rings());
        assertEquals(1,ringSounds(s).size());assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));retired(s);
    }

    @Test void skippedOriginalRingPreparationKeepsOnlyCompletedNativePrefix() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var cut=observeRingCut(s);var b=item(s);var o=itemObserver(b);
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.RingCall)try {cut.set(new RingCut(ring(s),ledger(s)));}
            catch(Exception e){throw new AssertionError(e);}
            else super.beforeOperation(c); // Deliberately omit original Ring preparation only.
        }});
        assertThrows(IllegalStateException.class,()->next(s));assertEquals(cut.get().ring(),ring(s));assertSame(cut.get().ledger(),ledger(s));
        assertEquals(4,s.placement().pool().state(127).routine());assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));retired(s);
    }

    @Test void skippedOriginalRingCompletionKeepsOneActualAwardAndClosesFrame() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var cut=observeRingCut(s);var b=item(s);var o=itemObserver(b);
        set(b,"observer",new ItemForward(o){@Override public void afterOperation(Object c){if(!(c instanceof CdMonitorItem.RingCall))super.afterOperation(c);}});
        assertThrows(IllegalStateException.class,()->next(s));assertEquals((cut.get().ring().rings()+10)&65535,ring(s).rings());
        assertEquals(1,ringSounds(s).size());assertTrue(s.frame().closed());retired(s);
    }

    @Test void noopDrawAfterRingAwardCannotEraseOrRepeatCompletedReward() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var cut=observeRingCut(s);set(item(s),"drawObject",(CdMonitor.DrawObjectCall)(pool,id)->{});
        assertThrows(IllegalStateException.class,()->next(s));assertEquals((cut.get().ring().rings()+10)&65535,ring(s).rings());
        assertEquals(1,ringSounds(s).size());assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));retired(s);
    }

    @Test void originalRingCallbacksRejectClonesReentryCaptureRenderRestoreAndStaleScope() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var b=item(s);var o=itemObserver(b);var stale=new AtomicReference<Object>();var count=new int[1];
        var registry=s.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.RingCall){stale.set(c);count[0]++;
                assertThrows(IllegalStateException.class,()->o.beforeOperation(cloneUnchecked(c)));
                assertThrows(IllegalStateException.class,registry::capture);assertThrows(IllegalStateException.class,registry::captureCourse);
                assertThrows(IllegalStateException.class,()->registry.restore(full));assertThrows(IllegalStateException.class,()->registry.restoreCourse(course));
                assertThrows(IllegalStateException.class,()->s.h().module().act2SelectedDisplay());
                assertThrows(IllegalStateException.class,()->s.h().module().serviceAct2Loading(8));
                assertThrows(IllegalStateException.class,()->b.run(127,Optional.empty()));
                assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runMonitor",new Class<?>[]{int.class},127));
                assertFalse(s.frame().closed());
            }super.beforeOperation(c);
        }});
        next(s);assertEquals(1,count[0]);set(b,"observer",o);
        assertThrows(IllegalStateException.class,()->o.beforeOperation(stale.get()));
        assertThrows(IllegalStateException.class,()->b.run(127,Optional.empty()));assertFalse(s.frame().closed());retired(s);
    }

    @Test void actualPrimitiveRejectsClonedForeignAndRepeatedRequestsWithoutDisturbingOriginal() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var b=item(s);var port=(CdMonitorItem.RingAwardPort)actualField(b,"ringAward");
        set(b,"ringAward",(CdMonitorItem.RingAwardPort)c->{
            assertThrows(IllegalStateException.class,()->port.award(new CdMonitorItem.RingAwardCall(c.identity(),1)));
            assertThrows(IllegalStateException.class,()->port.award(new CdMonitorItem.RingAwardCall(s.placement().pool().state(126).identity(),1)));
            var result=port.award(c);assertThrows(IllegalStateException.class,()->port.award(c));return result;
        });
        next(s);assertEquals(1,ringSounds(s).size());assertFalse(s.frame().closed());retired(s);
    }

    @Test void unselectedDynamicMutationRejectsRingAwardBeforeAnyExternalStore() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var cut=observeRingCut(s);var b=item(s);var o=itemObserver(b);
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.RingCall){var other=s.placement().pool().state(126);s.placement().pool().write(other.moveByte(SUBTYPE,other.subtype()^64));}
            super.beforeOperation(c);
        }});
        assertThrows(IllegalStateException.class,()->next(s));assertEquals(cut.get().ring(),ring(s));assertSame(cut.get().ledger(),ledger(s));
        assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));retired(s);
    }

    @Test void unselectedReservedMutationRejectsRingAwardWithoutOverwritingOldLedger() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var cut=observeRingCut(s);var b=item(s);var o=itemObserver(b);
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.RingCall)try{
                var values=actualField(s.visit(),"values");byte[] raw=((CdAssetBytes)actualField(values,"hudObjects")).copy();raw[29*64+0x37]^=64;
                set(s.visit(),"values",cloneRecord(values,Map.of("hudObjects",CdAssetBytes.copyOf(raw))));
            }catch(Exception e){throw new AssertionError(e);}
            super.beforeOperation(c);
        }});
        assertThrows(IllegalStateException.class,()->next(s));assertEquals(cut.get().ring(),ring(s));assertSame(cut.get().ledger(),ledger(s));
        assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));retired(s);
    }

    @Test void originalPostAwardThrowablePreservesCompletedNativePrefixesAndIsNotReplaced() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var cut=observeRingCut(s);var b=item(s);var o=itemObserver(b);var sentinel=new IllegalStateException("after-real-monitor-rings");
        set(b,"observer",new ItemForward(o){@Override public void afterOperation(Object c){
            super.afterOperation(c);if(c instanceof CdMonitorItem.RingCall)throw sentinel;
        }});
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->next(s)));assertEquals(0,sentinel.getSuppressed().length);
        assertEquals((cut.get().ring().rings()+10)&65535,ring(s).rings());assertEquals(1,ringSounds(s).size());
        assertEquals(4,s.placement().pool().state(127).routine());assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));retired(s);
    }

    @Test void constructedAwardFullAndCourseAdoptionReplayAndAppendOnlyMetadataRemainExact() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);seedRing(s,new CdRingGlobals(90,255,0xA1,0xA4,255,0xBEEF,0x81,0xFE));replayNext(s);
        assertEquals(130,SonicCdGameModule.Act2LoadCall.values().length);
        assertEquals(118,SonicCdGameModule.Act2LoadCall.SHIELD_ACTIVE.ordinal());
        assertEquals(119,SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_RING_AWARD.ordinal());
        assertEquals(List.of("PastIntroFm","PastIntroSubCpu","PastCcFm","PastKamaSickleFm","PastCheckpointFm","PastFrameSound","PastSubCpuSound","PastRingFm","PastRingSubCpu","PastSpringFm",
                "PastTunnelDoorFm","PastMonitorFm","PastTimePostSubCpu","PastTamabbohFm","PastCollapsePlatformFm","PastMosquiFm",
                "PastLostRingFm","PastRobotGeneratorFm","PastProjectorFm","PastSignpostSound","PastBigRingFm","PastResultsSound",
                "PastProgrammeSound","PastWallFm","PastTunnelPathFm","PastMonitorItemShieldFm","PastMonitorItemRing","PastTunnelDoorSplashFm",
                "PastDiscFm","PastVerticalDoorFm","PastSpeedShoesSubCpu","PastLevelMusic","PastR13BossFm","PastR13BossSubCpu","PastR13CapsuleExplosion","PastR13CapsuleSubCpu","PastGameOverSubCpu","PastBumperSubCpu"),
                Arrays.stream(SonicCdGameModule.PastSound.class.getPermittedSubclasses()).map(Class::getSimpleName).toList());
        assertThrows(NullPointerException.class,()->new SonicCdGameModule.PastMonitorItemRing(null));
        assertEquals(1,ringSounds(s).size());retired(s);
    }

    private record RingCut(CdRingGlobals ring,List<SonicCdGameModule.PastSound> ledger) { }
    /** Capture the owner's actual primitive cut, after every earlier physical/native prefix. */
    private static AtomicReference<RingCut> observeRingCut(Scene s) throws Exception {
        var cut=new AtomicReference<RingCut>();var b=item(s);var o=itemObserver(b);
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.RingCall)try{cut.set(new RingCut(ring(s),ledger(s)));}
            catch(Exception e){throw new AssertionError(e);}
            super.beforeOperation(c);
        }});
        return cut;
    }
    private record AwardObservation(CdRingGlobals before,CdRingGlobals after,CdMonitorItem.RingAwardResult result,
                                    CdMonitorItem.Result completion) { }
    private record Nonwrites(Object values,CdPlayerBody.State state,CdPlayerBody.Environment environment,
                             Optional<CdPlayerBody.DisplayPublication> display,
                             com.openggf.game.soniccd.resources.CdPlayerArtRuntime.State art,
                             CdPlayerFrameRuntime.VIntClock clock,CdPlayerFrameRuntime.ActorState actor,
                             CdPlayerFrameRuntime.SharedState shared) { }
    /** Only fixture object/global values are constructed; the current producer/receipt is always genuine. */
    private static AwardObservation award(Scene s,CdRingGlobals before,CdRingGlobals expected,CdMonitorItem.RingAwardSound sound) throws Exception {
        seedRing(s,before);seedItem(s,127,2,1,0);var b=item(s);var o=itemObserver(b);
        var nonwrites=new AtomicReference<Nonwrites>();var reached=new AtomicReference<CdRingGlobals>();
        var result=new AtomicReference<CdMonitorItem.RingAwardResult>();var completion=new AtomicReference<CdMonitorItem.Result>();
        var draws=new int[1];
        set(b,"observer",new ItemForward(o){
            @Override public void beforeOperation(Object c){
                if(c instanceof CdMonitorItem.RingCall r)try{
                    assertSame(r.source().identity(),r.request().identity());assertEquals(1,r.request().rawAnimation());
                    assertEquals(1,r.source().unsignedByte(ANIMATION));assertEquals(4,r.source().routine());assertEquals(29,r.source().word(MONITOR_ITEM_DELETE));
                    // Actual earlier selector8 HUD consumes both dirty flags before the Item cut.
                    // Raw WORD-wrap case explicitly suppresses its refresh, so native HUD does not clamp to999.
                    var reachedBefore=new CdRingGlobals(before.rings(),before.lives(),before.livesFlags(),0,0,
                            before.timeStopTimer(),before.ringAnimationTimer(),before.ringAnimationFrame());
                    assertEquals(reachedBefore,ring(s));reached.set(ring(s));
                    nonwrites.set(new Nonwrites(actualField(s.visit(),"values"),s.frame().state(),s.frame().environment(),
                            s.frame().display(),s.frame().artState(),s.frame().vIntClock(),s.frame().captureActor(),s.frame().captureShared()));
                }catch(Exception e){throw new AssertionError(e);}
                if(c instanceof CdMonitorItem.DrawCall){draws[0]++;assertNotNull(result.get(),"Draw follows actual ring award");}
                super.beforeOperation(c);
            }
            @Override public void afterOperation(Object c){
                super.afterOperation(c);
                if(c instanceof CdMonitorItem.RingCall r)try{
                    assertEquals(expected,ring(s));assertRingOnly(nonwrites.get().values(),actualField(s.visit(),"values"));
                    var n=nonwrites.get();assertSame(n.state(),s.frame().state());assertSame(n.environment(),s.frame().environment());
                    assertSame(n.display(),s.frame().display());assertSame(n.art(),s.frame().artState());assertSame(n.clock(),s.frame().vIntClock());
                    assertEquals(n.actor(),s.frame().captureActor());assertEquals(n.shared(),s.frame().captureShared());
                    var actual=s.placement().capture();assertEquals(r.cut().status(),actual.status());
                    assertEquals(r.cut().pool().slots(),actual.pool().slots());assertArrayEquals(r.cut().savedFlags().copy(),actual.savedFlags().copy());
                    result.set((CdMonitorItem.RingAwardResult)b.currentResult(c));assertEquals(sound,result.get().sound());
                    assertEquals(sound,ringSounds(s).getLast().sound());
                }catch(Exception e){throw new AssertionError(e);}
                if(c instanceof CdMonitorItem.CompletionCall)completion.set((CdMonitorItem.Result)b.currentResult(c));
            }
        });
        next(s);set(b,"observer",o);assertNotNull(reached.get());assertNotNull(result.get());assertNotNull(completion.get());assertEquals(1,draws[0]);
        assertEquals(1,ringSounds(s).size());retired(s);return new AwardObservation(reached.get(),expected,result.get(),completion.get());
    }
    private static void assertRingOnly(Object before,Object after) throws Exception {
        for(var c:before.getClass().getRecordComponents()){
            if(c.getName().equals("ring") || c.getName().equals("calls"))continue;
            var a=c.getAccessor();a.setAccessible(true);assertEquals(a.invoke(before),a.invoke(after),c.getName());
        }
        @SuppressWarnings("unchecked") var old=(List<SonicCdGameModule.Act2LoadCall>)actualField(before,"calls");
        @SuppressWarnings("unchecked") var now=(List<SonicCdGameModule.Act2LoadCall>)actualField(after,"calls");
        var expected=new ArrayList<>(old);expected.add(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_RING_AWARD);assertEquals(expected,now);
    }
    private static void seedRing(Scene s,CdRingGlobals ring) throws Exception {
        set(s.visit(),"values",cloneRecord(actualField(s.visit(),"values"),Map.of("ring",ring)));
    }
    private static CdRingGlobals ring(Scene s) throws Exception {return (CdRingGlobals)rawValue(s,"ring");}
    private static CdRingGlobals uncheckedRing(Scene s){try{return ring(s);}catch(Exception e){throw new AssertionError(e);}}
    private static List<SonicCdGameModule.PastMonitorItemRing> ringSounds(Scene s) throws Exception {
        return ledger(s).stream().filter(SonicCdGameModule.PastMonitorItemRing.class::isInstance).map(SonicCdGameModule.PastMonitorItemRing.class::cast).toList();
    }
    private static void retired(Scene s) throws Exception {
        for(String field:new String[]{"activeItemShield","activeTimeStars","activePrimary","activeDrain","activeReserved","activeMonitor"})
            assertNull(actualField(s.visit(),field),field);
        for(String field:new String[]{"itemShieldPort","drawingItemShield","runningObjects"})assertFalse((boolean)actualField(s.visit(),field),field);
        var b=item(s);assertFalse((boolean)actualField(b,"running"));assertFalse((boolean)actualField(b,"primitiveActive"));
        assertNull(actualField(b,"pendingOperation"));
    }
    private static class ItemForward implements CdMonitorItem.DispatchObserver {
        final CdMonitorItem.DispatchObserver actual;ItemForward(CdMonitorItem.DispatchObserver actual){this.actual=actual;}
        public void beforeOperation(Object c){actual.beforeOperation(c);}public void afterOperation(Object c){actual.afterOperation(c);}
        public void failedOperation(Object c,Throwable t){actual.failedOperation(c,t);}
    }

    private static Scene scene() throws Exception {
        var old=ready();var h=old.harness();
        for(int call=old.returned()+1;call<=12892;call++)step(h,input(call));
        var visit=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");
        var s=new Scene(h,visit,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(visit,"placement"));
        assertFalse(s.frame().closed());assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,calls(s).getLast());
        assertEquals(0,s.frame().environment().movement().gates().zoneAct()>>>8);retired(s);return s;
    }
    private static LogicalInputSnapshot input(int call) {
        if(call<=8276)return route(call);
        int directions=8,actions=call<=8308?2:0;
        if(call>=9001 && call<=9203)directions=4;
        else if(call>=9206 && call<=9217)directions=0;
        else if(call>=9218 && call<=9263){directions=2;actions=call==9219?2:0;}
        else if(call>=9336 && call<=9367)actions=2;
        if(call>10419 && call>=11214 && !(call>=12671 && call<=12699))actions=(call-11214)%128<32?2:0;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions,0,actions,0,false,false),PlayerInputState.neutral());
    }
    private static void next(Scene s){step(s.h(),input(12893));}
    private static void replayNext(Scene s) throws Exception {
        var registry=s.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(s.h());
        var source=s.frame().stageSource();var world=s.h().mode().getWorldSession();long generation=s.h().mode().getLevelManager().getCompletedProductionLoadGeneration();
        next(s);var after=captureNative(s.h());retired(s);
        registry.restore(full);assertNativeCapture(before,s.h());next(s);assertNativeCapture(after,s.h());
        registry.restoreCourse(course);assertNativeCapture(before,s.h());next(s);assertNativeCapture(after,s.h());
        assertSame(source,s.frame().stageSource());assertSame(world,s.h().mode().getWorldSession());
        assertSame(s.frame(),s.h().module().requirePlayerFrame());assertEquals(generation,s.h().mode().getLevelManager().getCompletedProductionLoadGeneration());retired(s);
    }
    private static CdObjectState seedItem(Scene s,int whole,int routine,int animation,int velocity) {
        var fg=s.h().module().act2LoadState().actor().state().motion();
        var row=s.placement().pool().state(whole).moveByte(ID,0x1A).moveByte(ROUTINE,routine).moveByte(ANIMATION,animation)
                .moveByte(SPRITE_FLAGS,0x24).moveByte(PRIORITY,3).moveByte(WIDTH,8).moveByte(FLAGS,0).moveByte(LAYER,0)
                .moveLong(MAPPINGS,0x20A8B5).moveLong(X_POSITION,fg.centreX16_16()).moveLong(Y_POSITION,fg.centreY16_16())
                .moveWord(X_VELOCITY,0x80).moveWord(Y_VELOCITY,velocity).moveWord(MONITOR_ITEM_DELETE,10);
        s.placement().pool().write(row);return row;
    }
    private static CdMonitorItem item(Scene s) throws Exception{return (CdMonitorItem)actualField(s.visit(),"monitorItem");}
    private static CdMonitorItem.DispatchObserver itemObserver(CdMonitorItem b) throws Exception{return (CdMonitorItem.DispatchObserver)actualField(b,"observer");}
    private static Object rawValue(Scene s,String name) throws Exception{return actualField(actualField(s.visit(),"values"),name);}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.PastSound> ledger(Scene s) throws Exception{return (List<SonicCdGameModule.PastSound>)actualField(s.visit(),"sounds");}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.Act2LoadCall> calls(Scene s) throws Exception{return (List<SonicCdGameModule.Act2LoadCall>)rawValue(s,"calls");}
    @SuppressWarnings("unchecked") private static CdObjectDrawQueue.CpuSat cpu(Scene s) throws Exception{return ((Optional<CdObjectDrawQueue.CpuSat>)actualField(rawValue(s,"loop"),"completedDraw")).orElseThrow();}
    private static Object cloneUnchecked(Object r){try{return cloneRecord(r,Map.of());}catch(Exception e){throw new AssertionError(e);}}
    private static Object cloneRecord(Object r,Map<String,Object> changes) throws Exception {
        var cs=r.getClass().getRecordComponents();Class<?>[] types=new Class<?>[cs.length];Object[] values=new Object[cs.length];
        for(int j=0;j<cs.length;j++){var c=cs[j];types[j]=c.getType();var a=c.getAccessor();a.setAccessible(true);values[j]=changes.containsKey(c.getName())?changes.get(c.getName()):a.invoke(r);}
        var ctor=r.getClass().getDeclaredConstructor(types);ctor.setAccessible(true);return ctor.newInstance(values);
    }
    private static void set(Object owner,String name,Object value) throws Exception {var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Object invoke(Object owner,String name,Class<?>[] types,Object...args) throws Exception {
        var m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(owner,args);}catch(InvocationTargetException e){var c=e.getCause();if(c instanceof RuntimeException r)throw r;if(c instanceof Error r)throw r;throw new AssertionError(c);}
    }
}
