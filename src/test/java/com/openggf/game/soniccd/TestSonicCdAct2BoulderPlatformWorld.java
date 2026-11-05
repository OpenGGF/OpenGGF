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

/** Labelled component cuts in the genuine cold/Past/Results Next8/normal Act2 World.
 * Only dynamic full64 images are fixture inputs. No actor/global/camera/art/queue/clock/bank seeds.
 * N1ecc5f3a Boulder26-51; Platform30-555; shared Solid/Object Functions/Spawner.
 * Java UNRUN; natural10764 continuation is Root's separate execution obligation. */
@Isolated
class TestSonicCdAct2BoulderPlatformWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void boulderInitUsesActualR12TileAndMapThenOnePlainSolidDrawAndBothRewindScopes() throws Exception {
        var s=scene();var body=actualField(s.visit(),"boulder");var frame=s.frame();
        var source=image(s,127,0x1B,0,0).moveWord(X_FRACTION,0xA55A).moveWord(Y_FRACTION,0x5AA5);
        seed(s,source);replayNext(s);var result=s.placement().pool().state(127);
        assertEquals(2,result.routine());assertEquals(0x20947C,result.longBits(MAPPINGS));assertEquals(0x036C,result.word(TILE));
        assertEquals(4,result.unsignedByte(PRIORITY));assertEquals(16,result.unsignedByte(WIDTH));assertEquals(16,result.unsignedByte(Y_RADIUS));
        assertEquals(0xA55A,result.word(X_FRACTION));assertEquals(0x5AA5,result.word(Y_FRACTION));
        assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.BOULDER_BODY));assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.BOULDER_SOLID));
        assertSame(body,actualField(s.visit(),"boulder"));assertSame(frame,s.h().module().requirePlayerFrame());
        replayNext(s);assertSame(body,actualField(s.visit(),"boulder"));
    }

    @Test void boulderTopSupportUsesTheRealPrimaryAndKeepsFractionsAndBorrowedOwnerIdentities() throws Exception {
        var s=scene();var m=s.frame().state().motion();var source=image(s,127,0x1B,2,0)
                .moveWord(X,m.centreX16_16()>>>16).moveWord(Y,(m.centreY16_16()>>>16)+35).moveByte(WIDTH,16).moveByte(Y_RADIUS,16);
        seed(s,source);var before=s.frame().captureShared();replayNext(s);
        assertEquals(127,s.frame().state().objectLinks().standObject());assertEquals(8,s.placement().pool().state(127).flags()&8);
        assertEquals(m.centreX16_16()&65535,s.frame().state().motion().centreX16_16()&65535);
        assertEquals(m.centreY16_16()&65535,s.frame().state().motion().centreY16_16()&65535);
        assertSame(before.owner(),s.frame().captureShared().owner());replayNext(s);
    }

    @Test void rawBoulderSubtype2BoundaryKeepsExactInitHeaderBeforeTileReadAndClosesNativeFrame() throws Exception {
        var s=scene();var source=image(s,127,0x1B,0,0).moveByte(SUBTYPE2,1);seed(s,source);var sat=bytes(s,"cpuSat");var bank=s.placement().capture().savedFlags();
        var stopped=assertThrows(CdBoulder.UnsupportedBoulder.class,()->step(s.h(),neutral()));
        assertEquals(CdBoulder.UnsupportedBoundary.TILE_ID_SUBTYPE2,stopped.boundary());
        var prefix=source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveByte(PRIORITY,4)
                .moveLong(MAPPINGS,0x20947C).moveByte(WIDTH,16).moveByte(Y_RADIUS,16).moveByte(MAP_FRAME,0);
        assertEquals(prefix,s.placement().pool().state(127));assertSame(sat,bytes(s,"cpuSat"));assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
        assertEquals(0,stopped.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void boulderMaskedDespawnUsesRealPreScrollCameraAndPreservesEverySavedByte() throws Exception {
        var s=scene();var camera=s.h().mode().getCamera();var source=image(s,127,0x1B,2,0)
                .moveWord(X,(camera.getX()-0x400)&65535).moveByte(SPRITE_FLAGS,4).moveByte(SAVED_FLAGS_ID,240);
        seed(s,source);var bank=s.placement().capture().savedFlags();replayNext(s);
        assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());
    }

    @Test void allTenNativePlatformFamiliesInitializeOnceWithoutMainFallthroughThenReachTheirActualTopSites() throws Exception {
        var s=scene();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();var body=actualField(s.visit(),"platform");
        for(int family=0;family<10;family++) {
            registry.restore(base);var source=image(s,127,0x21,0,(family<<4)|1).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468);
            seed(s,source);replayNext(s);var init=s.placement().pool().state(127);
            assertEquals(2,init.routine());assertEquals(0x20C9F2,init.longBits(MAPPINGS));assertEquals(0x44BE,init.word(TILE));
            assertEquals(1,init.unsignedByte(MAP_FRAME));assertEquals(32,init.unsignedByte(WIDTH));assertEquals(8,init.unsignedByte(Y_RADIUS));
            assertEquals(source.centreX(),init.word(PLATFORM_ANCHOR_X));assertEquals(source.centreY(),init.word(PLATFORM_ANCHOR_Y));
            assertEquals(source.centreY(),init.word(PLATFORM_INITIAL_Y));assertEquals(0x1357,init.word(X_FRACTION));assertEquals(0x2468,init.word(Y_FRACTION));
            assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.PLATFORM_SOLID));assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.PLATFORM_BODY));
            replayNext(s);assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.PLATFORM_SOLID));assertSame(body,actualField(s.visit(),"platform"));
        }
    }

    @Test void platformSupportAndHorizontalCarryPublishTheActualFreshSolidCallWithoutReplayingEffects() throws Exception {
        // One16-pixel terrain block of clearance, obtained by genuine Top publication, not actor ingress.
        var s=scene();var m=s.frame().state().motion();var source=image(s,127,0x21,2,0x41)
                .moveWord(X,m.centreX16_16()>>>16).moveWord(Y,(m.centreY16_16()>>>16)+(19+8-16))
                .moveWord(PLATFORM_ANCHOR_X,m.centreX16_16()>>>16).moveWord(PLATFORM_ANCHOR_Y,(m.centreY16_16()>>>16)+(19+8-16));
        seed(s,source);replayNext(s);assertEquals(127,s.frame().state().objectLinks().standObject());assertEquals((m.centreY16_16()>>>16)-16,s.frame().state().motion().centreY16_16()>>>16);
        var standing=s.placement().pool().state(127);seed(s,standing.moveByte(SUBTYPE,0x11).moveByte(PLATFORM_PHASE,16));
        var body=(CdPlatform)actualField(s.visit(),"platform");var original=(CdPlatform.SolidCommit)actualField(body,"solidCommit");var call=new AtomicReference<CdPlatform.SolidCall>();
        set(body,"solidCommit",(CdPlatform.SolidCommit)c->{call.set(c);original.commit(c);});replayNext(s);
        var actual=call.get();assertNotNull(actual);assertEquals(CdPlatform.Site.SUBTYPE_1_TOP,actual.site());
        var done=assertInstanceOf(CdSolidObject.Completed.class,actual.outcome()).completedResult();assertEquals(CdSolidObject.Path.STAND_ON,done.path(),()->"reason="+done.getOffReason()+" player="+actual.expectedPlayer()+" source="+java.util.HexFormat.of().formatHex(actual.actualCut().slots().get(95).copyBytes()));
        assertTrue(done.calls().stream().filter(CdSolidObject.RoomInFront.class::isInstance).map(CdSolidObject.RoomInFront.class::cast).allMatch(probe->probe.distance()>=0),"Fixed fixture requires actual unobstructed horizontal carry");
        assertTrue(done.calls().stream().filter(CdSolidObject.FloorDistance.class::isInstance).map(CdSolidObject.FloorDistance.class::cast).allMatch(probe->probe.distance()>=0),"Fixed one-block clearance must remain above actual terrain");
        assertEquals(done.player().actor().motion().centreX16_16(),s.frame().state().motion().centreX16_16());
        var shifted=actual.actualCut().slots().get(95);assertNotEquals(0,shifted.signedWord(X_VELOCITY));
        assertEquals(actual.expectedPlayer().actor().motion().centreX16_16()+(shifted.signedWord(X_VELOCITY)<<8),done.player().actor().motion().centreX16_16());
        assertEquals(127,s.frame().state().objectLinks().standObject());
    }

    @Test void groundHeightHorizontalPlatformRetainsTheNativeTerrainDetachRatherThanClaimingCarry() throws Exception {
        var s=scene();var m=s.frame().state().motion();var source=image(s,127,0x21,2,0x41)
                .moveWord(X,m.centreX16_16()>>>16).moveWord(Y,(m.centreY16_16()>>>16)+27)
                .moveWord(PLATFORM_ANCHOR_X,m.centreX16_16()>>>16).moveWord(PLATFORM_ANCHOR_Y,(m.centreY16_16()>>>16)+27);
        seed(s,source);replayNext(s);assertEquals(127,s.frame().state().objectLinks().standObject());
        var standing=s.placement().pool().state(127);seed(s,standing.moveByte(SUBTYPE,0x11).moveByte(PLATFORM_PHASE,16));
        var body=(CdPlatform)actualField(s.visit(),"platform");var original=(CdPlatform.SolidCommit)actualField(body,"solidCommit");var call=new AtomicReference<CdPlatform.SolidCall>();
        set(body,"solidCommit",(CdPlatform.SolidCommit)c->{call.set(c);original.commit(c);});replayNext(s);
        var actual=call.get();assertNotNull(actual);var done=assertInstanceOf(CdSolidObject.Completed.class,actual.outcome()).completedResult();
        assertEquals(CdSolidObject.Path.GET_OFF,done.path());assertEquals(Optional.of(CdSolidObject.GetOffReason.FLOOR_UNDER_OBJECT),done.getOffReason());
        assertTrue(done.calls().stream().filter(CdSolidObject.FloorDistance.class::isInstance).map(CdSolidObject.FloorDistance.class::cast).anyMatch(probe->probe.distance()<0));
        assertEquals(0,s.frame().state().objectLinks().standObject());assertEquals(0,s.placement().pool().state(127).flags()&8);
        var expected=done.player().actor().motion();var after=s.frame().state().motion();
        // Shared Solid precedes native DrawObjects, whose selected primary alone writes sprite bit7.
        for(var component:CdPlayerMotionState.class.getRecordComponents())
            if(!component.getName().equals("spriteFlags"))
                assertEquals(component.getAccessor().invoke(expected),component.getAccessor().invoke(after),component.getName());
        boolean selected=cpu(s).provenance().stream().anyMatch(row->row.slotRef().wholeIndex()==0);
        assertEquals((expected.spriteFlags()&0x7F)|(selected?0x80:0),after.spriteFlags());
    }

    @Test void platformInitAllocatesOneActualDirtyFirstFreeSpringAndKeepsItsWholePoolNonwrites() throws Exception {
        var s=scene();var source=image(s,127,0x21,0,0x41).moveByte(SUBTYPE2,0xFA);seed(s,source);
        var before=s.placement().capture();var free=before.pool().slots().stream().filter(x->x.id()==0).findFirst().orElseThrow();replayNext(s);
        var expected=free.moveByte(ID,0x0A).moveWord(X,source.centreX()+0xF8).moveWord(Y,source.centreY()-16)
                .moveByte(SPRING_PARENT_Y_OFFSET,0xF0).moveWord(SPRING_PARENT,source.identity().nativeAddress24())
                .moveByte(SUBTYPE,2).moveByte(SPRING_PARENT_X_OFFSET,0xF8);
        assertEquals(expected,s.placement().pool().state(free.identity().wholeIndex()));
        assertEquals(before.status(),s.placement().status());assertArrayEquals(before.savedFlags().copy(),s.placement().capture().savedFlags().copy());
    }

    @Test void realRisingObjMoveReservedLinkStopKeepsItsTypedReadAndEveryRawObjectNonwrite() throws Exception {
        var s=scene();var source=image(s,127,0x21,2,0x71).moveByte(PLATFORM_SUBSTATE,2).moveByte(PLATFORM_TIMER,0)
                .moveByte(FLAGS,8).moveByte(STAND_LINK_3D,1);seed(s,source);var bank=s.placement().capture().savedFlags();var sat=bytes(s,"cpuSat");
        var stopped=assertThrows(CdPlayerMovement.UnsupportedMotion.class,()->step(s.h(),neutral()));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.RESERVED_OBJECT_SLOT,stopped.boundary());assertEquals(source,s.placement().pool().state(127));
        assertArrayEquals(bank.copy(),s.placement().capture().savedFlags().copy());assertSame(sat,bytes(s,"cpuSat"));
        assertEquals(0,stopped.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void secondaryPlatformMappingRemainsAnHonestUnownedDrawBoundaryWithoutSelectingItByName() throws Exception {
        var s=scene();var source=image(s,127,0x21,2,0x41).moveLong(MAPPINGS,0x20CA76);seed(s,source);
        var failed=assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));assertTrue(failed.getMessage().contains("0020CA76"));
        assertEquals(0x20CA76,s.placement().pool().state(127).longBits(MAPPINGS));assertTrue(s.frame().closed());retired(s);
    }

    @Test void realBoulderSolidCallbackFaultKeepsItsOriginalExceptionAndNativeFullInitPrefix() throws Exception {
        var s=scene();var source=image(s,127,0x1B,0,0);seed(s,source);var body=(CdBoulder)actualField(s.visit(),"boulder");
        var original=(CdBoulder.PrefixSolidCommit)actualField(body,"prefixCommit");var sentinel=new AssertionError("actual Boulder commit fault");
        set(body,"prefixCommit",(CdBoulder.PrefixSolidCommit)c->{original.commit(c);throw sentinel;});
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(2,s.placement().pool().state(127).routine());assertEquals(0x036C,s.placement().pool().state(127).word(TILE));
        assertEquals(0,sentinel.getSuppressed().length);assertTrue(s.frame().closed());retired(s);
    }

    @Test void actualPlatformCallbackReentryAndUnrelatedFullImageWriteCannotAcquireAnotherOwner() throws Exception {
        var s=scene();var source=image(s,127,0x21,2,0x41);seed(s,source);var body=(CdPlatform)actualField(s.visit(),"platform");
        var original=(CdPlatform.SolidCommit)actualField(body,"solidCommit");var sentinel=new AssertionError("unrelated full image");
        set(body,"solidCommit",(CdPlatform.SolidCommit)c->{
            assertThrows(IllegalStateException.class,()->body.run(127,new CdPlatform.Globals(1,0,0,0,1),new CdPlatform.Inputs(c.expectedPlayer(),new CdSolidObject.Gates(0,0),c.expectedGlobals())));
            s.placement().pool().write(s.placement().pool().state(100).moveByte(ID,0x55));throw sentinel;
        });
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertTrue(sentinel.getSuppressed().length>0);
        assertEquals(0x55,s.placement().pool().state(100).id());assertTrue(s.frame().closed());retired(s);
        assertNotNull(original);
    }

    @Test void publicSourceCutAndDrawValuesCannotMintEitherPrivateActualInvocationOnAHealthyCut() throws Exception {
        var s=scene();var before=captureNative(s.h());var source=s.placement().pool().state(127);
        var cut=new CdPlatform.PrimaryAngleCall(s.placement().capture().pool(),source,s.frame().environment().movement().globals().primaryAngle(),0);
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"commitPlatformPrimaryAngle",new Class<?>[]{CdPlatform.PrimaryAngleCall.class},cut));
        for(String method:new String[]{"drawBoulder","drawPlatform"})
            assertThrows(IllegalStateException.class,()->invoke(s.visit(),method,new Class<?>[]{CdObjectPool.class,CdObjectState.Identity.class},s.placement().pool(),source.identity()));
        assertNativeCapture(before,s.h());healthy(s);
    }

    @Test void genuineRisingCeilingByteFaultRetainsTheOneActualAngleStoreAndEveryOtherFrameValue() throws Exception {
        var s=scene();var terrain=CdPlayerTerrain.fromStageTerrain(s.assets().terrain());CdObjectState selected=null;
        int angle=s.frame().environment().movement().globals().primaryAngle();
        search:for(int y=256;y<=768;y+=16)for(int x=32;x<=256;x+=16) {
            var candidate=image(s,127,0x21,2,0x71).moveWord(X,x).moveWord(Y,y).moveWord(X_VELOCITY,0).moveWord(Y_VELOCITY,0)
                    .moveByte(PLATFORM_SUBSTATE,2).moveByte(PLATFORM_TIMER,0).moveByte(FLAGS,0).moveByte(STAND_LINK_3D,0);
            var writes=new ArrayList<Integer>();CdObjectCollisionCheck.objGetCeilDist(terrain,candidate.moveWord(Y_VELOCITY,-8),angle,writes::add);
            if(!writes.isEmpty()){selected=candidate;break search;}
        }
        assertNotNull(selected,"The bounded actual-source object-only ceiling fixture must reach a real angle byte");seed(s,selected);
        var body=(CdPlatform)actualField(s.visit(),"platform");var original=(CdPlatform.PrimaryAngleCommit)actualField(body,"primaryAngleCommit");
        var observed=new AtomicReference<CdPlatform.PrimaryAngleCall>();var sentinel=new AssertionError("after actual ceiling byte");
        set(body,"primaryAngleCommit",(CdPlatform.PrimaryAngleCommit)c->{
            var actor=s.frame().captureActor();var shared=s.frame().captureShared();observed.set(c);original.commit(c);
            assertEquals(actor,s.frame().captureActor());assertSame(shared.artState(),s.frame().artState());assertSame(shared.vIntClock(),s.frame().vIntClock());
            assertEquals(c.value(),s.frame().environment().movement().globals().primaryAngle());
            var fresh=CdObjectPool.overlay(c.actualCut(),List.of(c.expectedSource()));
            assertThrows(IllegalStateException.class,()->original.commit(new CdPlatform.PrimaryAngleCall(fresh,c.expectedSource(),c.value(),c.value()^0x5A)));
            assertThrows(IllegalStateException.class,()->original.commit(new CdPlatform.PrimaryAngleCall(fresh,c.expectedSource(),c.value(),c.value())));
            assertEquals(shared.environment().movement().globals().secondaryAngle(),s.frame().environment().movement().globals().secondaryAngle());
            assertSame(shared.environment().controls(),s.frame().environment().controls());assertSame(shared.environment().globals(),s.frame().environment().globals());
            throw sentinel;
        });
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertNotNull(observed.get());
        assertEquals(observed.get().expectedSource(),s.placement().pool().state(127));assertEquals(0,sentinel.getSuppressed().length);
        assertTrue(s.frame().closed());retired(s);
    }

    @Test void foreignPublicFullPoolCannotReplaceTheActualPlatformHelperPreimageBeforeAnyAdoption() throws Exception {
        var s=scene();seed(s,image(s,127,0x21,2,0x41));var body=(CdPlatform)actualField(s.visit(),"platform");
        var original=(CdPlatform.SolidCommit)actualField(body,"solidCommit");var foreign=new CdObjectPool().capture();
        set(body,"solidCommit",(CdPlatform.SolidCommit)c->original.commit(new CdPlatform.SolidCall(c.site(),c.entry(),foreign,c.expectedPlayer(),c.expectedGlobals(),c.outcome())));
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(s.frame().closed());
        @SuppressWarnings("unchecked")var reached=(List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(s.visit(),"values"),"calls");
        assertFalse(reached.contains(SonicCdGameModule.Act2LoadCall.PLATFORM_SOLID));retired(s);
    }

    @Test void omittedNoOpBoulderSolidCannotCertifyACompletedWorldCut() throws Exception {
        var s=scene();seed(s,image(s,127,0x1B,2,0));var body=(CdBoulder)actualField(s.visit(),"boulder");
        set(body,"prefixCommit",(CdBoulder.PrefixSolidCommit)c->{
            var done=assertInstanceOf(CdSolidObject.Completed.class,c.outcome()).completedResult();
            assertEquals(c.callCut().slots().get(95),done.object());assertEquals(c.expectedPlayer(),done.player());assertEquals(c.expectedGlobals(),done.globals());
        });
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(s.frame().closed());retired(s);
    }

    @Test void omittedNoOpPlatformSolidCannotCertifyACompletedWorldCut() throws Exception {
        var s=scene();seed(s,image(s,127,0x21,2,0x41));var body=(CdPlatform)actualField(s.visit(),"platform");
        set(body,"solidCommit",(CdPlatform.SolidCommit)c->{
            var done=assertInstanceOf(CdSolidObject.Completed.class,c.outcome()).completedResult();
            assertEquals(c.actualCut().slots().get(95),done.object());assertEquals(c.expectedPlayer(),done.player());assertEquals(c.expectedGlobals(),done.globals());
        });
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(s.frame().closed());retired(s);
    }

    @Test void doubleDelegatedNoOpPlatformSolidCannotMintASecondReceipt() throws Exception {
        var s=scene();seed(s,image(s,127,0x21,2,0x41));var body=(CdPlatform)actualField(s.visit(),"platform");
        var original=(CdPlatform.SolidCommit)actualField(body,"solidCommit");
        set(body,"solidCommit",(CdPlatform.SolidCommit)c->{
            var done=assertInstanceOf(CdSolidObject.Completed.class,c.outcome()).completedResult();
            assertEquals(c.actualCut().slots().get(95),done.object());assertEquals(c.expectedPlayer(),done.player());assertEquals(c.expectedGlobals(),done.globals());
            original.commit(c);original.commit(c);
        });
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(s.frame().closed());retired(s);
    }

    @Test void completedBoulderInitCannotRegressToItsEarlierParentAtDraw() throws Exception {
        var s=scene();var parent=image(s,127,0x1B,0,0).moveByte(SPRITE_FLAGS,4);seed(s,parent);
        var body=(CdBoulder)actualField(s.visit(),"boulder");var original=(CdBoulder.DrawObjectCall)actualField(body,"drawObject");
        set(body,"drawObject",(CdBoulder.DrawObjectCall)(pool,identity)->{pool.write(parent);original.drawObject(pool,identity);});
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(parent,s.placement().pool().state(127));
        assertTrue(s.frame().closed());retired(s);
    }

    @Test void completedPlatformInitCannotRegressToItsEarlierParentAtDraw() throws Exception {
        var s=scene();var parent=image(s,127,0x21,0,0x41);seed(s,parent);
        var body=(CdPlatform)actualField(s.visit(),"platform");var original=(CdPlatform.DrawObjectCall)actualField(body,"draw");
        set(body,"draw",(CdPlatform.DrawObjectCall)(pool,identity)->{pool.write(parent);original.drawObject(pool,identity);});
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(parent,s.placement().pool().state(127));
        assertTrue(s.frame().closed());retired(s);
    }

    @Test void completedPlatformInitCannotRestoreItsFirstFreeChildAtDraw() throws Exception {
        var s=scene();var parent=image(s,127,0x21,0,0x41).moveByte(SUBTYPE2,0xFA);seed(s,parent);
        var child=s.placement().capture().pool().slots().stream().filter(row->row.id()==0).findFirst().orElseThrow();
        var body=(CdPlatform)actualField(s.visit(),"platform");var original=(CdPlatform.DrawObjectCall)actualField(body,"draw");
        set(body,"draw",(CdPlatform.DrawObjectCall)(pool,identity)->{pool.write(child);original.drawObject(pool,identity);});
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(child,s.placement().pool().state(child.identity().wholeIndex()));
        assertTrue(s.frame().closed());retired(s);
    }

    @Test void distinctNativeRepeatedPlatformTopsRemainAcceptedInOrder() throws Exception {
        var s=scene();var motion=s.frame().state().motion();int x=motion.centreX16_16()>>>16,y=(motion.centreY16_16()>>>16)+27;
        seed(s,image(s,127,0x21,2,0x51).moveWord(X,x).moveWord(Y,y).moveWord(PLATFORM_ANCHOR_X,x).moveWord(PLATFORM_ANCHOR_Y,y));
        var body=(CdPlatform)actualField(s.visit(),"platform");var original=(CdPlatform.SolidCommit)actualField(body,"solidCommit");
        var reached=new ArrayList<CdPlatform.Site>();
        set(body,"solidCommit",(CdPlatform.SolidCommit)c->{reached.add(c.site());original.commit(c);});
        step(s.h(),neutral());healthy(s);
        assertEquals(List.of(CdPlatform.Site.SUBTYPE_5_STATIONARY_TOP,CdPlatform.Site.SUBTYPE_5_TIMER_TOP),reached);
        assertEquals(2,s.placement().pool().state(127).unsignedByte(PLATFORM_SUBSTATE));
    }

    private static CdObjectState image(Scene s,int whole,int id,int routine,int subtype) {
        var old=s.placement().pool().state(whole);var m=s.frame().state().motion();int x=((m.centreX16_16()>>>16)+64)&65535,y=m.centreY16_16()>>>16;
        return old.moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0)
                .moveWord(X,x).moveWord(Y,y).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0)
                .moveByte(WIDTH,id==0x21?32:16).moveByte(Y_RADIUS,id==0x21?8:16).moveByte(PRIORITY,id==0x21?2:4).moveByte(MAP_FRAME,0)
                .moveWord(TILE,id==0x21?0x44BE:0x036C).moveLong(MAPPINGS,id==0x21?0x20C9F2:0x20947C)
                .moveWord(PLATFORM_ANCHOR_X,x).moveWord(PLATFORM_ANCHOR_Y,y).moveWord(PLATFORM_INITIAL_Y,y)
                .moveByte(PLATFORM_PHASE,0).moveByte(PLATFORM_SUBSTATE,0).moveByte(PLATFORM_SAG,0).moveByte(PLATFORM_TIMER,0).moveByte(PLATFORM_RANGE,2);
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
        for(String name:new String[]{"activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
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
