package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/**
 * Actual original-USA cold->Past->Results Next8 object-only ingress->normal Act2 load.
 * Only selected owned dynamic object images are fixture inputs. No actor/global/palette/art/queue,
 * saved-bank/clock/camera seed, forged successful capture or alternate producer grants authority.
 * Fault fixtures replace a component callback and delegate actual source work, or throw the named fault.
 * N1ecc5f3a Monitor/Post7-409, Object Animate14-61, Collision Check157-179, Object Spawner385-446.
 * All declarations authored source-only/UNRUN. Root's separate natural controller case owns frontier evidence.
 */
@Isolated
class TestSonicCdAct2MonitorWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void actualActTwoFactoryBorrowsItsCompleteRawPairAndEightMonitorInitializersThenVisibleSolidsUseTheSameOwners() throws Exception {
        var s=scene(); var objects=s.assets().objects(); var assets=CdMonitor.Assets.fromAct2Assets(objects);
        assertSame(objects.monitorTime(),assets.visual()); assertEquals(20,assets.visual().mappingFrames().size());
        assertEquals(10,assets.visual().animations().size()); assertEquals(450,assets.visual().mappings().length());
        assertEquals(136,assets.visual().animationTable().length()); assertEquals(0x20A876,assets.mappingsAddress());
        assertArrayEquals(objects.view(CdR12aProfile.Asset.MAP_SPR_MONITOR_TIME).copy(),assets.visual().mappings().copy());
        assertArrayEquals(objects.view(CdR12aProfile.Asset.ANI_MONITOR).copy(),assets.visual().animationTable().copy());
        assertThrows(NullPointerException.class,()->CdMonitor.Assets.fromAct2Assets(null));
        var registry=s.h().mode().getRewindRegistry(); var base=registry.capture();
        var current=s.h().module().act2LoadState().section().residency().region(CdR12aCameraArtResidency.Region.AT_B500);
        var body=actualField(s.visit(),"monitor"); var post=actualField(s.visit(),"timePost");
        for(int subtype=0;subtype<8;subtype++) {
            registry.restore(base); var image=image(s,127,0,subtype).moveWord(X_FRACTION,0xA55A).moveWord(Y_FRACTION,0x5AA5);
            seed(s,image); replayNext(s); var init=s.placement().pool().state(127);
            assertEquals(2,init.routine()); assertEquals(0xE,init.unsignedByte(Y_RADIUS)); assertEquals(0xE,init.unsignedByte(X_RADIUS));
            assertEquals(0x20A876,init.longBits(MAPPINGS)); assertEquals(0x5A8,init.word(TILE)); assertEquals(3,init.unsignedByte(PRIORITY));
            assertEquals(0xF,init.unsignedByte(WIDTH)); assertEquals(subtype,init.unsignedByte(ANIMATION)); assertEquals(0x46,init.unsignedByte(COLLISION_TYPE));
            assertEquals(0xA55A,init.word(X_FRACTION)); assertEquals(0x5AA5,init.word(Y_FRACTION));
            assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.MONITOR_SOLID));
            var expected=animated(init,assets.visual()); replayNext(s); var main=s.placement().pool().state(127);
            assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.MONITOR_SOLID)); assertEquals(expected,main);
            assertSame(current,s.h().module().act2LoadState().section().residency().region(CdR12aCameraArtResidency.Region.AT_B500));
            assertSame(body,actualField(s.visit(),"monitor")); assertSame(post,actualField(s.visit(),"timePost"));
        }
    }

    @Test void actualPastAndFuturePostInitTouchSixtySpinCallsDoneAndUsedInitRetainFullCourseValuesAndExactDirectionVoiceOrder() throws Exception {
        var s=scene(); var registry=s.h().mode().getRewindRegistry(); var base=registry.capture();
        for(int subtype:new int[]{8,9}) {
            registry.restore(base); seed(s,image(s,127,0,subtype)); replayNext(s);
            var init=s.placement().pool().state(127); assertEquals(2,init.routine()); assertEquals(subtype==8?10:12,init.unsignedByte(MAP_FRAME));
            assertEquals(0x20,init.unsignedByte(Y_RADIUS)); assertEquals(0xDF,init.unsignedByte(COLLISION_TYPE));
            assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TIME_POST_DIRECTION));
            seed(s,init.moveByte(COLLISION_STATUS,1)); replayNext(s); var touch=s.placement().pool().state(127);
            assertEquals(4,touch.routine()); assertEquals(60,touch.unsignedByte(TIME_POST_SPIN)); assertEquals(0,touch.unsignedByte(COLLISION_STATUS));
            assertEquals(subtype==8?0xFF:1,s.frame().environment().movement().gates().timeWarpDirection());
            assertEquals(subtype==8?1L:2L,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TIME_POST_DIRECTION).count());
            assertEquals(1L,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TIME_POST_SUB_CPU).count());
            var request=s.h().module().act2LoadState().sounds().stream().filter(x->x instanceof SonicCdGameModule.PastTimePostSubCpu).toList();
            assertEquals(List.of(new SonicCdGameModule.PastTimePostSubCpu(subtype==8?CdTimePost.SubCpuCommand.SCMD_PASTSFX:CdTimePost.SubCpuCommand.SCMD_FUTURESFX)),request);
            assertEquals(1,s.placement().savedFlag(240,1)&1);
            for(int count=0;count<59;count++) { step(s.h(),neutral()); healthy(s); }
            assertEquals(1,s.placement().pool().state(127).unsignedByte(TIME_POST_SPIN)); replayNext(s);
            var done=s.placement().pool().state(127); assertEquals(6,done.routine()); assertEquals(subtype==8?11:13,done.unsignedByte(MAP_FRAME));
            seed(s,done.moveByte(ROUTINE,0).moveByte(COLLISION_STATUS,0)); replayNext(s);
            assertEquals(6,s.placement().pool().state(127).routine()); assertEquals(subtype==8?11:13,s.placement().pool().state(127).unsignedByte(MAP_FRAME));
            assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TIME_POST_DIRECTION));
            assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TIME_POST_SUB_CPU));
        }
    }

    @Test void twoLivePostsInIncreasingPhysicalOrderUseFreshFrameDirectionAtEachFfAndConditionalOneStore() throws Exception {
        var s=scene(); var gates=s.frame().environment().movement().gates();
        seed(s,image(s,126,2,8).moveByte(Y_RADIUS,32).moveByte(MAP_FRAME,10).moveByte(COLLISION_TYPE,0xDF).moveByte(COLLISION_STATUS,1));
        seed(s,image(s,127,2,9).moveByte(SAVED_FLAGS_ID,241).moveByte(Y_RADIUS,32).moveByte(MAP_FRAME,12)
                .moveByte(COLLISION_TYPE,0xDF).moveByte(COLLISION_STATUS,1));
        replayNext(s);
        assertEquals(new CdPlayerMovement.Gates(gates.zoneAct(),gates.timeZone(),gates.miniSonic(),gates.waterSlide(),gates.windTunnel(),gates.speedShoes(),1),
                s.frame().environment().movement().gates());
        assertEquals(3L,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TIME_POST_DIRECTION).count());
        // Sonic.Main.CheckCharge1033 requests stop even with zero charge; player0 precedes both posts.
        assertEquals(List.of(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_CHARGESTOP)),
                new SonicCdGameModule.PastTimePostSubCpu(CdTimePost.SubCpuCommand.SCMD_PASTSFX),
                new SonicCdGameModule.PastTimePostSubCpu(CdTimePost.SubCpuCommand.SCMD_FUTURESFX)),s.h().module().act2LoadState().sounds());
        assertEquals(1,s.placement().savedFlag(240,1)&1); assertEquals(1,s.placement().savedFlag(241,1)&1);
        assertEquals(60,s.placement().pool().state(126).unsignedByte(TIME_POST_SPIN));
        assertEquals(60,s.placement().pool().state(127).unsignedByte(TIME_POST_SPIN));
    }

    @Test void animateAndDrawRoutinesShareOneAnimatorAndExistingSelectedCurrentPixelsWithoutAnArtOrMappingBank() throws Exception {
        var s=scene(); var registry=s.h().mode().getRewindRegistry(); var base=registry.capture(); var visual=s.assets().objects().monitorTime();
        for(int routine:new int[]{6,8}) {
            registry.restore(base); var before=image(s,127,routine,4).moveByte(ANIMATION_TIME,0).moveByte(PREVIOUS_ANIMATION,4);
            seed(s,before); var current=s.h().module().act2LoadState().section().residency().region(CdR12aCameraArtResidency.Region.AT_B500);
            replayNext(s); var after=s.placement().pool().state(127);
            assertEquals(routine==6?animated(before,visual):before,after);
            assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.MONITOR_SOLID));
            step(s.h(),neutral()); healthy(s);
            var selected=s.h().module().act2SelectedDisplay(); var cpu=selected.inputs().selectedCpu().orElseThrow();
            assertTrue(cpu.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));
            assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection());
            assertSame(current,s.h().module().act2LoadState().section().residency().region(CdR12aCameraArtResidency.Region.AT_B500));
            for(int tile=0;tile<82;tile++) assertEquals(32,CdR12aCameraArtResidency.readTile(s.h().module().act2LoadState().section().residency(),0x5A8+tile).length());
        }
    }

    @Test void fallingUsesActualActTwoTerrainAndEveryRawFloorAngleStoreAfterTheNativeGravityPrefix() throws Exception {
        var s=scene(); var before=image(s,127,2,4).moveByte(ROUTINE_2,1).moveByte(RAW_CONTROL_2C,0)
                .moveWord(Y,(s.frame().state().motion().centreY16_16()>>>16)-64).moveWord(Y_FRACTION,0x1234)
                .moveWord(X_VELOCITY,0x80).moveWord(Y_VELOCITY,0x140);
        seed(s,before);
        var moved=before.moveLong(X_POSITION,before.longBits(X_POSITION)+(0x80<<8))
                .moveLong(Y_POSITION,before.longBits(Y_POSITION)+(0x140<<8)).moveWord(Y_VELOCITY,0x178);
        var writes=new ArrayList<Integer>(); var floor=CdObjectCollisionCheck.objGetFloorDist(CdPlayerTerrain.fromStageTerrain(s.assets().terrain()),moved,writes::add);
        assertFalse(writes.isEmpty()); assertEquals(0,writes.getFirst());
        var expected=floor.distance()<0 ? moved.addWord(Y,floor.distance()).moveWord(Y_VELOCITY,0).moveByte(ROUTINE_2,0):moved;
        expected=animated(expected,s.assets().objects().monitorTime()); replayNext(s);
        assertEquals(expected,s.placement().pool().state(127));
        assertEquals(writes.size(),calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.MONITOR_FLOOR_ANGLE).count());
        assertEquals(floor.primaryAngle(),s.frame().environment().movement().globals().primaryAngle());
        assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.MONITOR_SOLID));
    }

    @Test void breakKeepsOrderedDirtyChildAllocationsNativeSavedBitAndDirectDrawThenUsedInitAndDraw() throws Exception {
        var s=scene(); var source=image(s,127,4,4).moveByte(COLLISION_TYPE,0x46).moveByte(LAYER,3);
        seed(s,source); var before=s.placement().capture();
        var free=before.pool().slots().stream().filter(x->x.id()==0).limit(2).toList(); assertEquals(2,free.size());
        replayNext(s); var parent=s.placement().pool().state(127);
        assertEquals(8,parent.routine()); assertEquals(0,parent.unsignedByte(COLLISION_TYPE)); assertEquals(17,parent.unsignedByte(MAP_FRAME));
        assertEquals(1,s.placement().savedFlag(240,1)&1);
        for(int j=0;j<2;j++) {
            var expected=free.get(j).moveByte(ID,j==0?0x1A:0x18).moveWord(X,source.centreX()).moveWord(Y,source.centreY()).moveByte(LAYER,3);
            expected=j==0 ? expected.moveByte(ANIMATION,source.unsignedByte(ANIMATION)) : expected.moveByte(ROUTINE_2,1).moveByte(SUBTYPE,1);
            assertEquals(expected,s.placement().pool().state(expected.identity().wholeIndex()));
        }
        assertEquals(1L,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.MONITOR_FM).count());
        assertEquals(List.of(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_CHARGESTOP)),
                new SonicCdGameModule.PastMonitorFm(CdMonitor.FmSound.FM_DESTROY)),s.h().module().act2LoadState().sounds());
        // Object-only isolation of the parent's used-init branch; child bodies have separate unbound owners.
        for(var child:free) s.placement().pool().deleteObject(child.identity().wholeIndex());
        seed(s,parent.moveByte(ROUTINE,0)); replayNext(s); var used=s.placement().pool().state(127);
        assertEquals(8,used.routine()); assertEquals(17,used.unsignedByte(MAP_FRAME)); assertEquals(4,used.spriteFlags());
        assertFalse(cpu(s).provenance().stream().anyMatch(x->x.slotRef().wholeIndex()==127));
        replayNext(s); assertTrue(cpu(s).provenance().stream().anyMatch(x->x.slotRef().wholeIndex()==127));
    }

    @Test void rawSavedId255StopsMonitorInitAfterItsRealHeaderPrefixBeforeAnySavedBankStore() throws Exception { savedIdBoundary(4); }
    @Test void rawSavedId255StopsPostInitAfterItsRealHeaderPrefixBeforeAnySavedBankStore() throws Exception { savedIdBoundary(8); }
    private static void savedIdBoundary(int subtype) throws Exception {
            var s=scene(); var source=image(s,127,0,subtype).moveByte(SAVED_FLAGS_ID,255); seed(s,source);
            var bank=s.placement().capture().savedFlags();
            var failed=assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));
            if(subtype<8) assertEquals(CdMonitor.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,assertInstanceOf(CdMonitor.UnsupportedMonitor.class,failed).boundary());
            else assertEquals(CdTimePost.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,assertInstanceOf(CdTimePost.UnsupportedTimePost.class,failed).boundary());
            var prefix=source.addByte(ROUTINE,2).moveByte(Y_RADIUS,subtype<8?14:32).moveByte(X_RADIUS,14)
                    .moveLong(MAPPINGS,0x20A876).moveWord(TILE,0x5A8).moveByte(PRIORITY,3).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,15);
            if(subtype>=8) prefix=prefix.moveByte(ANIMATION,subtype);
            assertEquals(prefix,s.placement().pool().state(127)); assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
            assertEquals(0,failed.getSuppressed().length); assertTrue(s.frame().closed()); retired(s);
    }

    @Test void monitorAnimationFailureIsAtomicWhileItsOriginalFiniteReadClosesTheActualWorldCycle() throws Exception {
        var s=scene(); var source=image(s,127,6,4).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,1);
        seed(s,source); var bank=s.placement().capture().savedFlags(); var sat=bytes(s,"cpuSat");
        var failed=assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));
        assertTrue(failed.getMessage().contains("255")); assertEquals(0,failed.getSuppressed().length);
        assertEquals(source,s.placement().pool().state(127)); assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
        assertSame(sat,bytes(s,"cpuSat")); assertTrue(s.frame().closed()); retired(s);
        assertThrows(IllegalStateException.class,()->s.h().mode().getRewindRegistry().capture());
    }

    @Test void rawHighPostAnimationReadKeepsItsTimerResetPrefixWithoutNormalizingSubtypeOrBorrowingAView() throws Exception {
        var s=scene(); var source=image(s,127,4,0x40).moveByte(ANIMATION,0x40).moveByte(PREVIOUS_ANIMATION,0)
                .moveByte(ANIMATION_FRAME,7).moveByte(ANIMATION_TIME,3).moveByte(TIME_POST_SPIN,2);
        seed(s,source); var bank=s.placement().capture().savedFlags();
        var failed=assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral())); assertEquals(0,failed.getSuppressed().length);
        var prefix=source.moveByte(TIME_POST_SPIN,1).moveByte(PREVIOUS_ANIMATION,0x40).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0xFF);
        assertEquals(prefix,s.placement().pool().state(127)); assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
        assertTrue(s.frame().closed()); retired(s);
    }

    @Test void selectedDynamicHudSubtypeStopsBeforeAReservedImageOrSavedBankWrite() throws Exception {
        var s=scene(); var source=image(s,127,0,0x0A); seed(s,source); var bank=s.placement().capture().savedFlags();
        var failed=assertThrows(CdTimePost.UnsupportedTimePost.class,()->step(s.h(),neutral()));
        assertEquals(CdTimePost.UnsupportedBoundary.TIME_ICON_RESERVED_SLOT,failed.boundary());
        assertEquals(source,s.placement().pool().state(127)); assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
        assertEquals(0,failed.getSuppressed().length); assertTrue(s.frame().closed()); retired(s);
    }

    @Test void originalFmFaultPrecedesAllBreakStoresAndIsNotWrappedRetriedOrRolledBack() throws Exception {
        var s=scene(); var source=image(s,127,4,4); seed(s,source); var bank=s.placement().capture().savedFlags(); var pool=s.placement().capture().pool();
        var body=(CdMonitor)actualField(s.visit(),"monitor"); var sentinel=new AssertionError("actual Monitor FM port failed");
        set(body,"playFm",(CdMonitor.PlayFmCall)(sound)->{throw sentinel;});
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(0,sentinel.getSuppressed().length); assertEquals(source,s.placement().pool().state(127));
        assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
        for(var free:pool.slots()) if(free.id()==0) assertEquals(0,s.placement().pool().state(free.identity().wholeIndex()).id());
        assertTrue(s.frame().closed()); retired(s);
    }

    @Test void actualComponentReentryAtTheRealSolidPreflightRetainsTheOriginalFailureAndNoSolidPublication() throws Exception {
        var s=scene(); var source=image(s,127,2,4); seed(s,source); var body=(CdMonitor)actualField(s.visit(),"monitor");
        var original=(CdMonitor.PrefixSolidOwner)actualField(body,"pastSolidOwner");
        var reads=(CdMonitor.PastGlobals)invoke(s.visit(),"monitorGlobals",new Class<?>[0]);
        var observed=new AtomicReference<Throwable>();
        set(body,"pastSolidOwner",new CdMonitor.PrefixSolidOwner() {
            @Override public CdMonitor.LiveSolid preflight(CdMonitor.PrefixSolidCut cut) {
                var error=assertThrows(IllegalStateException.class,()->body.runPast(127,reads,
                        new CdMonitor.SolidInputs(cut.expectedPlayer(),cut.expectedGates(),cut.expectedGlobals())));
                observed.set(error); throw error;
            }
            @Override public CdMonitor.LiveSolid commit(CdMonitor.PrefixSolidCall call) { fail("Reentry must precede the single genuine Solid"); return original.commit(call); }
        });
        var failed=assertThrows(IllegalStateException.class,()->step(s.h(),neutral())); assertSame(observed.get(),failed);
        assertEquals(0,failed.getSuppressed().length); assertEquals(source,s.placement().pool().state(127));
        assertTrue(s.frame().closed()); retired(s);
    }

    @Test void publicCallValuesCannotBorrowThePrivateMonitorSourceScopeOnAHealthyClosedCut() throws Exception {
        var s=scene(); var before=captureNative(s.h());
        var field=CdMonitor.FmSound.FM_DESTROY;
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"playMonitorFm",new Class<?>[]{CdMonitor.FmSound.class},field));
        var source=s.placement().pool().state(127);
        var direction=new CdTimePost.DirectionCall(source.identity(),s.frame().environment().movement().gates().timeWarpDirection(),0xFF);
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"commitTimePostDirection",new Class<?>[]{CdTimePost.DirectionCall.class},direction));
        assertNativeCapture(before,s.h()); assertFalse(s.frame().closed()); healthy(s);
    }

    private static Scene scene() throws Exception {
        var old=oldReturned(); step(old.harness(),neutral()); var h=old.harness();
        for(int service=0;service<64;service++) {
            step(h,neutral());
            if(h.module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN)) {
                var visit=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");
                var scene=new Scene(h,visit,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(visit,"placement"),h.module().act2LoadState().assets());
                assertEquals(0,scene.placement().pool().state(127).id()); healthy(scene); return scene;
            }
        }
        throw new AssertionError("Actual normal Act2 load did not reach its first ordinary cut");
    }
    private static CdObjectState image(Scene s,int whole,int routine,int subtype) {
        var old=s.placement().pool().state(whole); var m=s.frame().state().motion();
        return old.moveByte(ID,0x19).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0)
                .moveByte(SAVED_FLAGS_ID,240).moveWord(X,((m.centreX16_16()>>>16)+64)&65535).moveWord(Y,m.centreY16_16()>>>16)
                .moveLong(MAPPINGS,CdR12aProfile.Asset.MAP_SPR_MONITOR_TIME.nativeAddress()).moveWord(TILE,0x5A8)
                .moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(Y_RADIUS,14).moveByte(X_RADIUS,14).moveByte(WIDTH,15)
                .moveByte(PRIORITY,3).moveByte(ANIMATION,subtype).moveByte(PREVIOUS_ANIMATION,subtype).moveByte(ANIMATION_FRAME,0)
                .moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,0).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0);
    }
    private static CdObjectState animated(CdObjectState source,CdObjectVisualAssets visual) {
        var out=CdObjectAnimation.step(new CdObjectAnimation.State(source.unsignedByte(ANIMATION),source.unsignedByte(PREVIOUS_ANIMATION),
                source.unsignedByte(ANIMATION_FRAME),source.unsignedByte(ANIMATION_TIME),source.unsignedByte(MAP_FRAME),source.spriteFlags(),source.flags(),
                source.routine(),source.unsignedByte(ROUTINE_2)),new CdObjectAnimation.AnimationTable(visual.animationTable(),0,10));
        return source.moveByte(ANIMATION,out.animationId()).moveByte(PREVIOUS_ANIMATION,out.previousId()).moveByte(ANIMATION_FRAME,out.scriptIndex())
                .moveByte(ANIMATION_TIME,out.timer()).moveByte(MAP_FRAME,out.mapFrame()).moveByte(SPRITE_FLAGS,out.spriteFlags()|0x80)
                .moveByte(FLAGS,out.statusFlags()).moveByte(ROUTINE,out.routine()).moveByte(ROUTINE_2,out.routine2());
    }
    private static void seed(Scene s,CdObjectState image) throws Exception {
        var before=s.placement().capture(); var actor=s.frame().captureActor(); var shared=s.frame().captureShared();
        s.placement().pool().write(image);
        assertEquals(before.status(),s.placement().status()); assertArrayEquals(before.savedFlags().copy(),s.placement().capture().savedFlags().copy());
        assertEquals(actor,s.frame().captureActor()); assertEquals(shared,s.frame().captureShared());
        for(var old:before.pool().slots()) if(old.identity().wholeIndex()!=image.identity().wholeIndex())
            assertEquals(old,s.placement().pool().state(old.identity().wholeIndex()));
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
        assertNull(actualField(s.visit(),"activeMonitor")); assertFalse((boolean)actualField(s.visit(),"monitorPort"));
        assertFalse((boolean)actualField(s.visit(),"drawingMonitor")); assertFalse((boolean)actualField(s.visit(),"runningObjects"));
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
