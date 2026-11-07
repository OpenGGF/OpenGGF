package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Tests-first/source-only; all Java UNRUN. Actual original-USA C/D aggregates plus explicitly
 * CONSTRUCTED COMPLETE COMPONENT VALUES for State/Environment/art/clock. These are not measured
 * destination values or native programme/World/FULL ingress. No Body/Init/music/boss is invoked.
 * Existing Frame load kernel: native N1ecc5f3a Main860-876,894-907,704-714,971-995.
 */
class TestCdR13FuturePlayerFrameEntry {
    private static final Runnable NOTHING=()->{};

    @Test void newFactoryIsOneExplicitFiveArgumentIngressAndAllExistingFactoriesRemain()throws Exception{
        var m=CdPlayerFrameRuntime.class.getDeclaredMethod("fromAct3FutureEntry",CdR13FutureAssets.class,CdPlayerBody.State.class,
                CdPlayerBody.Environment.class,CdPlayerArtRuntime.State.class,CdPlayerFrameRuntime.VIntClock.class);
        assertTrue(Modifier.isPublic(m.getModifiers()));assertTrue(Modifier.isStatic(m.getModifiers()));assertEquals(CdPlayerFrameRuntime.class,m.getReturnType());
        assertEquals(1,Arrays.stream(CdPlayerFrameRuntime.class.getDeclaredMethods()).filter(v->v.getName().equals("fromAct3FutureEntry")).count());
        var factories=Arrays.stream(CdPlayerFrameRuntime.class.getDeclaredMethods()).filter(v->Modifier.isPublic(v.getModifiers())&&Modifier.isStatic(v.getModifiers())&&v.getReturnType()==CdPlayerFrameRuntime.class)
                .map(v->v.getName()+"/"+v.getParameterCount()).collect(Collectors.toSet());
        assertEquals(Set.of("fromPastEntry/5","fromAct2Entry/6","fromAct2PastEntry/6","fromAct3FutureEntry/5","fromR31aEntry/5","terrainOnlyDevelopment/1","terrainOnlyDevelopment/2",
                "externalTouchRequiredDevelopment/1","externalTouchRequiredDevelopment/2","externalTouchRequiredDevelopment/3","checkpointRestartDevelopment/4"),factories);
        assertEquals(2,CdPlayerFrameRuntime.class.getDeclaredConstructors().length);for(var c:CdPlayerFrameRuntime.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(c.getModifiers()));
        assertEquals(3,CdPlayerFrameRuntime.ActorState.class.getRecordComponents().length);assertEquals(5,CdPlayerFrameRuntime.SharedState.class.getRecordComponents().length);
    }
    @Test void missingAggregateHasNoFallbackOrMediaRequirement(){
        var e=assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.fromAct3FutureEntry(null,null,null,null,null));assertEquals("Act3 Future assets",e.getMessage());
    }
    @Test void eachActualAggregateStillRequiresEveryExplicitValueInFactoryOrder()throws Exception{
        for(var a:actual()){var s=state();var e=environment(2,2);var art=art();var clock=clock();
            assertEquals("Act3 Future player state",assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.fromAct3FutureEntry(a,null,e,art,clock)).getMessage());
            assertEquals("Act3 Future player environment",assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.fromAct3FutureEntry(a,s,null,art,clock)).getMessage());
            assertEquals("Act3 Future player art ingress",assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.fromAct3FutureEntry(a,s,e,null,clock)).getMessage());
            assertEquals("Act3 Future player V-int clock",assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.fromAct3FutureEntry(a,s,e,art,null)).getMessage());
        }
    }
    @Test void actualCandDBorrowTheirExactTerrainPlayer167VisualOwnerAndActualMapping()throws Exception{
        var pair=actual();var good=pair.get(0);var bad=pair.get(1);
        for(var a:pair){var f=frame(a,2,2);var s=f.stageSource();assertTrue(s.ownsAct3Future(a));assertFalse(s.ownsAct3Future(a==good?bad:good));assertFalse(s.ownsAct3Future(null));
            assertSame(a.terrain(),s.terrain());assertSame(a.player(),s.player());assertSame(a.sonic(),s.sonic());assertSame(a.player().sonic(),s.sonic());
            assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_167,s.sonic().sourceFormat());assertEquals(167,s.sonic().mappingFrames().size());
            assertEquals(a.identity().stage(),s.stage());assertEquals(3,s.stage().actNumber());assertEquals(2,s.ordinaryPeriod());assertEquals(CdReleaseVariant.USA,s.variant());
            assertEquals(a.range(CdR13FutureAssets.Asset.MAP_SPR_SONIC).nativeAddress(),s.mappingAddress());assertEquals(0x22DF20,s.mappingAddress());
            assertFalse(s.ownsPresent(null));assertFalse(s.ownsPast(null));assertFalse(s.ownsAct2(null,null));assertFalse(s.ownsAct2Past(null,null));assertThrows(IllegalStateException.class,f::assets);
        }
        assertEquals(CdStagePayload.R13C.stage(),good.identity().stage());assertEquals(CdStagePayload.R13D.stage(),bad.identity().stage());
        assertNotSame(good.sonic(),bad.sonic());assertArrayEquals(good.sonic().mappings().copy(),bad.sonic().mappings().copy());
    }
    @Test void factoryRetainsCompleteReferencesWithoutClearsInitArtPrimeInputOrWorldEntitlement()throws Exception{
        for(var a:actual()){var s=state();var e=environment(2,2);var art=art();var clock=clock();var f=CdPlayerFrameRuntime.fromAct3FutureEntry(a,s,e,art,clock);
            assertSame(s,f.state());assertSame(e,f.environment());assertSame(art,f.artState());assertSame(clock,f.vIntClock());
            assertSame(s,f.captureActor().state());assertSame(e,f.captureShared().environment());assertSame(art,f.captureShared().artState());assertSame(clock,f.captureShared().vIntClock());
            assertEquals(0x12345678,f.state().header().mappingAddress(),"Caller header is retained; actual source map22DF20 does not imply Init ran");
            assertTrue(f.display().isEmpty());assertFalse(f.paused());assertFalse(f.closed());assertThrows(IllegalStateException.class,()->f.runBody(1));
            var input=f.prepareInput(8);assertThrows(IllegalStateException.class,()->f.serviceVBlank(input.phase()));f.clearPendingInput();
            assertSame(s,f.state());assertSame(e,f.environment());assertSame(art,f.artState());assertSame(clock,f.vIntClock());assertFalse(f.closed());
        }
    }
    @Test void ordinaryTwoTwoAndExistingMarkedPeriodsUseTheSameLoadingServicesWithoutNormalizingGates()throws Exception{
        for(var a:actual())for(int period:new int[]{2,0x80,0x81,0x82}){var f=frame(a,2,period);var s=f.state();var art=f.artState();var source=f.stageSource();
            f.serviceLevelLoad(8,NOTHING,NOTHING,NOTHING);f.serviceLevelPaletteFade(8,NOTHING,NOTHING);
            assertEquals(2,f.environment().movement().gates().zoneAct());assertEquals(period,f.environment().movement().gates().timeZone());
            assertSame(s,f.state());assertSame(art,f.artState());assertSame(source,f.stageSource());assertEquals(2,f.vIntClock().deltaSinceAdmittedCut());assertTrue(f.vIntClock().absoluteValue().isEmpty());assertFalse(f.closed());
        }
    }
    @Test void wrongActOrUnmarkedPeriodRejectsBeforeCallbacksAndKeepsTheCompleteCutHealthy()throws Exception{
        for(var a:actual())for(var pair:new int[][]{{0,2},{1,2},{0x102,2},{0xFFFF,2},{2,0},{2,1},{2,0x7F},{2,0x83},{2,0xFF}}){var f=frame(a,pair[0],pair[1]);var actor=f.captureActor();var shared=f.captureShared();
            sourceRejected(()->f.serviceLevelLoad(8,TestCdR13FuturePlayerFrameEntry::unreached,TestCdR13FuturePlayerFrameEntry::unreached,TestCdR13FuturePlayerFrameEntry::unreached));
            sourceRejected(()->f.serviceLevelPaletteFade(8,TestCdR13FuturePlayerFrameEntry::unreached,TestCdR13FuturePlayerFrameEntry::unreached));
            assertEquals(actor,f.captureActor());assertEquals(shared,f.captureShared());assertFalse(f.closed());
        }
    }
    @Test void futureSourceCannotBorrowTheR11OrR12OldProgrammeExitException()throws Exception{
        for(var a:actual())for(var pair:new int[][]{{2,2},{1,1}}){var f=frame(a,pair[0],pair[1]);var actor=f.captureActor();var shared=f.captureShared();
            sourceRejected(()->f.serviceOldProgrammeExitLoad(8,actor.state(),shared.environment(),TestCdR13FuturePlayerFrameEntry::unreached,TestCdR13FuturePlayerFrameEntry::unreached,TestCdR13FuturePlayerFrameEntry::unreached));
            sourceRejected(()->f.serviceOldProgrammeExitPaletteFade(8,actor.state(),shared.environment(),TestCdR13FuturePlayerFrameEntry::unreached,TestCdR13FuturePlayerFrameEntry::unreached));
            assertEquals(actor,f.captureActor());assertEquals(shared,f.captureShared());assertFalse(f.closed());
        }
    }
    @Test void freshOwnersRejectEqualSourceAndCrossFutureCapturesWhileGenuineOwnRestoreHasNoEffects()throws Exception{
        var pair=actual();var s=state();var e=environment(2,2);var art=art();var clock=clock();var f=CdPlayerFrameRuntime.fromAct3FutureEntry(pair.get(0),s,e,art,clock);var source=f.stageSource();var actor=f.captureActor();var shared=f.captureShared();
        for(var a:pair){var other=CdPlayerFrameRuntime.fromAct3FutureEntry(a,s,e,art,clock);assertNotSame(source,other.stageSource());assertNotSame(actor.owner(),other.captureActor().owner());
            assertThrows(IllegalArgumentException.class,()->other.restoreActor(actor));assertThrows(IllegalArgumentException.class,()->other.restoreShared(shared));
            assertThrows(IllegalArgumentException.class,()->f.restoreActor(other.captureActor()));assertThrows(IllegalArgumentException.class,()->f.restoreShared(other.captureShared()));
        }
        int[] calls={0};Runnable count=()->calls[0]++;f.serviceLevelLoad(8,count,count,count);assertEquals(3,calls[0]);
        f.restoreActor(actor);f.restoreShared(shared);assertEquals(3,calls[0]);assertSame(source,f.stageSource());assertSame(s,f.state());assertSame(e,f.environment());assertSame(art,f.artState());assertSame(clock,f.vIntClock());
    }
    @Test void actualSourceLoadKernelReadsControllersThenPublishesCopiesServicesAndFinishesExactlyOnce()throws Exception{
        for(var a:actual()){var s=state();var e=environment(2,2);var art=art();var clock=new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFFFFFFFEL),1);var f=CdPlayerFrameRuntime.fromAct3FutureEntry(a,s,e,art,clock);var calls=new ArrayList<String>();
            f.serviceLevelLoad(0xF5,()->{calls.add("publication");assertEquals(new CdPlayerBody.Controls(0xF5,0xD1,8,0x10,0x81),f.environment().controls());assertSame(clock,f.vIntClock());assertSame(s,f.state());assertSame(art,f.artState());},
                    ()->{calls.add("copy/draw");assertEquals(0xFFFF,f.environment().movement().globals().boredTimer());assertEquals(0xFFFE,f.environment().globals().timeWarpTimer());},
                    ()->{calls.add("fastPLC");assertSame(clock,f.vIntClock());assertThrows(IllegalStateException.class,f::captureShared);});
            assertEquals(List.of("publication","copy/draw","fastPLC"),calls);assertSame(s,f.state());assertSame(art,f.artState());assertEquals(0,f.environment().movement().globals().boredTimer());assertEquals(0xFFFF,f.environment().globals().timeWarpTimer());
            assertEquals(e.globals().levelFrames(),f.environment().globals().levelFrames());assertSame(e.movement().physics(),f.environment().movement().physics());assertSame(e.movement().bounds(),f.environment().movement().bounds());assertSame(e.movement().gates(),f.environment().movement().gates());
            assertEquals(2,f.vIntClock().deltaSinceAdmittedCut());assertEquals(0,f.vIntClock().absoluteValue().orElseThrow());assertFalse(f.paused());assertFalse(f.closed());assertThrows(IllegalStateException.class,()->f.runBody(1));
        }
    }
    @Test void pendingInputAndCaughtComponentReentryDoNotCloseTheHealthySource()throws Exception{
        for(var a:actual()){var f=frame(a,2,2);var actor=f.captureActor();var shared=f.captureShared();f.prepareInput(8);assertThrows(IllegalStateException.class,()->f.serviceLevelLoad(8,NOTHING,NOTHING,NOTHING));f.clearPendingInput();assertEquals(actor,f.captureActor());assertEquals(shared,f.captureShared());
            f.serviceLevelLoad(8,()->assertThrows(IllegalStateException.class,()->f.serviceLevelPaletteFade(8,NOTHING,NOTHING)),NOTHING,NOTHING);assertFalse(f.closed());assertEquals(1,f.vIntClock().deltaSinceAdmittedCut());
        }
    }
    @Test void originalRuntimeAndErrorFaultsKeepTheReadPrefixWithoutClockOrArtCompletion()throws Exception{
        for(var a:actual())for(Throwable original:List.of(new IllegalArgumentException("actual-source component copy fault"),new AssertionError("actual-source component copy error"))){var f=frame(a,2,2);var s=f.state();var e=f.environment();var art=f.artState();var clock=f.vIntClock();var calls=new ArrayList<String>();
            var caught=assertThrows(Throwable.class,()->f.serviceLevelLoad(0xF5,()->calls.add("publication"),()->{calls.add("copy/draw");if(original instanceof RuntimeException runtime)throw runtime;throw (Error)original;},()->calls.add("unreached fast")));
            assertSame(original,caught);assertEquals(List.of("publication","copy/draw"),calls);assertTrue(f.closed());assertSame(s,f.state());assertSame(art,f.artState());assertSame(clock,f.vIntClock());assertSame(e.movement(),f.environment().movement());assertSame(e.globals(),f.environment().globals());assertEquals(0xF5,f.environment().controls().rawHeld());assertEquals(0xD1,f.environment().controls().rawTap());
        }
    }
    @Test void explicitCloseRejectsCaptureServiceAndOwnRestoreWithoutDestroyingRetainedValues()throws Exception{
        for(var a:actual()){var f=frame(a,2,2);var actor=f.captureActor();var shared=f.captureShared();var source=f.stageSource();f.close();
            assertThrows(IllegalStateException.class,f::captureActor);assertThrows(IllegalStateException.class,f::captureShared);assertThrows(IllegalStateException.class,()->f.restoreActor(actor));assertThrows(IllegalStateException.class,()->f.restoreShared(shared));assertThrows(IllegalStateException.class,()->f.serviceLevelLoad(8,NOTHING,NOTHING,NOTHING));assertSame(source,f.stageSource());assertSame(actor.state(),f.state());assertSame(shared.environment(),f.environment());
        }
    }

    private static List<CdR13FutureAssets> actual()throws Exception{String root=System.getProperty("soniccd.media.root");org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Optional original CD media is absent");assertTrue(Files.isDirectory(Path.of(root)),"Configured bad media root must fail");return TestCdR13FutureAssets.retail().all();}
    private static CdPlayerFrameRuntime frame(CdR13FutureAssets a,int act,int period){return CdPlayerFrameRuntime.fromAct3FutureEntry(a,state(),environment(act,period),art(),clock());}
    private static CdPlayerFrameRuntime.VIntClock clock(){return new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),0);}
    private static CdPlayerArtRuntime.State art(){byte[] cpu=new byte[0x300],resident=new byte[0x300];for(int i=0;i<cpu.length;i++){cpu[i]=(byte)(i*7+3);resident[i]=(byte)(i*11+5);}return new CdPlayerArtRuntime.State(cpu,resident,7,true);}
    private static CdPlayerBody.State state(){byte[] history=new byte[256];for(int i=0;i<history.length;i++)history[i]=(byte)(i*13+7);return new CdPlayerBody.State(new CdPlayerMotionState(0x01234567,0x023489AB,-0x123,0x234,-0x345,0,0x56,0x42,0x23,7,11,6,7,9,0,-0x1234,0x44,0x10,0x77,0x88),new CdPlayerBody.AnimationCursor(5,6,3),new CdPlayerBody.Header(1,0x12345678,0x2345,3,0x21),new CdPlayerBody.Timers(0x78,0x1234,0x2345,0x3456),new CdPlayerBody.History(CdAssetBytes.copyOf(history),0x42),0x67,new CdPlayerBody.ObjectLinks(0x20,0x7F),0x89);}
    private static CdPlayerBody.Environment environment(int act,int period){return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123,0x23,0x34),new CdPlayerMovement.Bounds(0x12,0x2345,0x3456,0x78),new CdPlayerMovement.Gates(act,period,0x12,0x34,0x56,0x78,0xFF),new CdPlayerMovement.Globals(0x12,0x2345,0x34,0x56,0xFFFF,0x4567,0x78,0x89,0x9A,0xAB)),new CdPlayerBody.Controls(0x24,0x18,8,0x10,0x81),new CdPlayerBody.Globals(0x1234,0x2345,0x12,0x34,0x4567,0xFFFE,0x56,0x78),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);}
    private static void sourceRejected(org.junit.jupiter.api.function.Executable action){assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,action).boundary());}
    private static void unreached(){fail("Rejected source must not reach any callback");}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
