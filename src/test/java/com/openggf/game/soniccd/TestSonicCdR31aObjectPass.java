package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** Tests FIRST; author Java UNRUN. Actual immutable USA R31 data, Frame, placement, shared
 * components and queue; explicitly CONSTRUCTED complete Core/Ports inputs, never Ready/World
 * admission, a natural layout recipe, a native run, pixel parity or a second writable bank. */
class TestSonicCdR31aObjectPass {
    @Test void constructorIsDataOnlyAndRequiresTheExactRetainedSource() throws Exception {
        try (var h=new Harness()) {
            var f=h.frame.captureActor(); var s=h.frame.captureShared(); var p=h.placement.capture();
            assertEquals(0,h.reads); assertEquals(0,h.adoptions);
            assertThrows(IllegalArgumentException.class,()->new SonicCdR31aObjectPass(h.assets,h.frame,
                new CdObjectPlacementRuntime(h.assets.placements()),h.objects,h));
            assertEquals(f,h.frame.captureActor()); assertEquals(s,h.frame.captureShared()); same(p,h.placement);
            assertTrue(Arrays.stream(SonicCdR31aObjectPass.Adoption.class.getDeclaredConstructors())
                .allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        }
    }
    @Test void inactiveOwnerRejectsBeforeAnyNativeWrite() throws Exception {
        try(var h=new Harness()){h.reserved(2,0x1C,0);var before=h.cut;
            assertThrows(IllegalStateException.class,()->h.pass.runWalk(7));assertSame(before,h.cut);assertFalse(h.frame.closed());}
    }
    @Test void actualPrimaryInitReturnsOnceWithoutMainOrDraw() throws Exception {
        try(var h=new Harness()){h.primary(1,0,0);h.service();var clock=h.frame.vIntClock();var before=h.frame.state();
            h.walk();assertEquals(2,h.frame.state().motion().routine());assertEquals(0x22DF20,h.frame.state().header().mappingAddress());
            assertEquals(0x780,h.frame.state().header().tile());assertEquals(0,h.frame.state().history().index());
            assertEquals(before.motion().centreX16_16(),h.frame.state().motion().centreX16_16());
            assertEquals(clock,h.frame.vIntClock());assertEquals(0,h.primaryPublications);assertTrue(h.frame.display().isEmpty());h.finish();}
    }
    @Test void actualHudInitUsesR31MappingAndScreenFields() throws Exception {
        try(var h=new Harness()){h.reserved(2,0x1C,0);var before=h.placement.capture();h.walk();var r=h.row(2);
            assertEquals(2,r.unsignedByte(0x24));assertEquals(0x8568,r.unsignedWord(2));
            assertEquals(h.assets.range(CdR31aAssets.Asset.HUD_MAPPINGS).nativeAddress(),(int)r.unsignedLong(4));
            assertEquals(0x90,r.unsignedWord(8));assertEquals(0x88,r.unsignedWord(10));same(before,h.placement);h.finish();}
    }
    @Test void hudLivesAndRingsUseBothActualSubtypeBytes() throws Exception {
        try(var h=new Harness()){h.reserved(3,0x1C,1);h.reserved(5,0x1C,0);h.edit(5,b->b[0x29]=1);h.walk();
            assertEquals(1,h.row(3).unsignedByte(26));assertEquals(0x148,h.row(3).unsignedWord(10));
            assertEquals(3,h.row(5).unsignedByte(26));h.finish();}
    }
    @Test void nativeTitleAllocatesFirstEightAndForwardChildrenRunInThisWalk() throws Exception {
        try(var h=new Harness()){h.reserved(4,0x3C,0);var p=h.placement.capture();h.walk();
            assertEquals(2,h.row(4).unsignedByte(0x24));assertEquals(0x118,h.row(4).unsignedWord(8));
            for(int n=0;n<8;n++){var child=h.placement.pool().state(32+n);assertEquals(0x3C,child.id());
                assertEquals(4,child.routine());assertEquals(h.objects.titleCardAssets().initData().unsignedWord(n*8+2)-8,child.centreX());
                assertEquals(h.objects.titleCardAssets().mappingsAddress(),child.longBits(CdObjectState.LongField.MAPPINGS));}
            assertEquals(p.status(),h.placement.status());assertArrayEquals(p.savedFlags().copy(),h.placement.capture().savedFlags().copy());h.finish();}
    }
    @Test void titleAllocationFailureRetainsParentAndFirstAvailableChild() throws Exception {
        try(var h=new Harness()){for(int w=32;w<128;w++)if(w!=34)h.dynamic(w,0x7F,0,0);h.reserved(4,0x3C,0);
            var e=assertThrows(CdTitleCard.UnsupportedTitleCard.class,h::walk);
            assertEquals(CdTitleCard.UnsupportedBoundary.ALLOCATION_ADDRESS,e.boundary());
            assertEquals(2,h.row(4).unsignedByte(0x24));assertEquals(0x3C,h.placement.pool().state(34).id());
            assertEquals(0x228,h.placement.pool().state(34).centreX());assertFalse(h.frame.closed());}
    }
    @Test void titleUsesActualActAndNeverAProgrammeDefault() throws Exception {
        try(var h=new Harness()){h.reserved(4,0x3C,0);h.walk();
            assertEquals(h.objects.titleCardAssets().initData().unsignedByte(5*8+6),h.placement.pool().state(37).unsignedByte(MAP_FRAME));h.finish();}
    }
    @Test void titlePlcIsDeferredAndMissingOwnerRetainsTheExactNativePrefix() throws Exception {
        try(var h=new Harness()){h.reserved(4,0x3C,0);h.edit(4,b->{b[0x24]=6;b[0x1E]=0;word(b,10,0x30);word(b,0x30,0x30);});
            var e=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);
            assertEquals(SonicCdR31aObjectPass.Boundary.TITLE_PLC_OWNER,e.boundary());assertEquals(2,e.value());
            assertEquals(10,h.row(4).unsignedByte(0x24));assertEquals(0x01A5,h.cut.scrollLockWord());
            assertNotNull(h.lastPlc);assertThrows(IllegalStateException.class,h.lastPlc::validateBefore);
            assertThrows(IllegalStateException.class,h.lastPlc::acknowledge);}
    }
    @Test void titleWaitReadsTheGenuineCorePlcHeadBeforeUnlocking() throws Exception {
        try(var h=new Harness()){h.reserved(4,0x3C,0);h.edit(4,b->b[0x24]=10);h.walk();
            assertEquals(0,h.row(4).unsignedByte(0));assertEquals(0x00A5,h.cut.scrollLockWord());
            assertEquals(0,h.frame.environment().controls().controlLocked());h.finish();}
    }
    @Test void firstMonitorInitUsesTheRealSavedBankAndDrawsWithoutSolidOrProbe() throws Exception {
        try(var h=new Harness()){h.dynamic(32,0x19,0,2);var f=h.frame.captureActor();var s=h.frame.captureShared();h.walk();
            var m=h.placement.pool().state(32);assertEquals(2,m.routine());assertEquals(0x46,m.unsignedByte(COLLISION_TYPE));
            assertEquals(0x5A8,m.word(TILE));assertEquals(2,m.unsignedByte(ANIMATION));assertEquals(f,h.frame.captureActor());assertEquals(s,h.frame.captureShared());h.finish();}
    }
    @Test void initialJoinedWalkUsesTheActualFirstLayoutRowAndLiveTitleAllocation() throws Exception {
        try(var h=new Harness()){h.primary(1,0,0);h.service();h.placement.spawnObjects(0,1);
            assertEquals(0x19,h.placement.pool().state(32).id());assertEquals(420,h.placement.pool().state(32).centreX());
            h.reserved(2,0x1C,0);h.reserved(3,0x1C,1);h.reserved(4,0x3C,0);h.reserved(5,0x1C,0);h.edit(5,b->b[0x29]=1);h.reserved(31,0x19,10);
            h.walk();assertEquals(2,h.frame.state().motion().routine());assertEquals(2,h.placement.pool().state(32).routine());
            for(int w=33;w<=40;w++){assertEquals(0x3C,h.placement.pool().state(w).id());assertEquals(4,h.placement.pool().state(w).routine());}
            assertEquals(0,h.placement.pool().state(41).id());h.finish();assertTrue(h.cut.completedCpu().isPresent());}
    }
    @Test void monitorInitPreservesTheEntireDirtySourceOutsideLiteralNativeStores() throws Exception {
        try(var h=new Harness()){h.dynamic(32,0x19,0,2);var b=h.placement.pool().state(32)
                .moveWord(X_FRACTION,0xCAFE).moveWord(Y_FRACTION,0xBEEF).moveByte(MAP_FRAME,7).moveByte(ANIMATION_TIME,0xA5);
            h.placement.pool().write(b);var expected=b.moveByte(ROUTINE,2).moveByte(Y_RADIUS,14).moveByte(X_RADIUS,14)
                .moveLong(CdObjectState.LongField.MAPPINGS,h.assets.range(CdR31aAssets.Asset.MONITOR_MAPPINGS).nativeAddress())
                .moveWord(TILE,0x5A8).moveByte(PRIORITY,3).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,15)
                .moveByte(COLLISION_TYPE,0x46).moveByte(ANIMATION,2);
            h.walk();assertArrayEquals(expected.copyBytes(),h.placement.pool().state(32).copyBytes());h.finish();}
    }
    @Test void actualR31PlainSolidNoContactKeepsTheCompleteFramePairWithoutLegacyZoneWidening() throws Exception {
        try(var h=new Harness()){h.dynamic(32,0x19,0,2);h.walk();h.finish();
            h.placement.pool().write(h.placement.pool().state(32).moveByte(SPRITE_FLAGS,0x84));
            var actor=h.frame.captureActor();var shared=h.frame.captureShared();h.walk();
            assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());
            assertEquals(0x100,h.frame.environment().movement().gates().zoneAct());assertEquals(1,h.frame.environment().movement().gates().timeZone());h.finish();}
    }
    @Test void realMonitorBreakAllocatesItemThenExplosionAndEarlierChildrenWait() throws Exception {
        try(var h=new Harness()){h.dynamic(40,0x19,0,2);h.walk();h.finish();h.placement.pool().write(h.placement.pool().state(40).moveByte(ROUTINE,4));
            h.walk();assertEquals(0x1A,h.placement.pool().state(32).id());assertEquals(0x18,h.placement.pool().state(33).id());
            assertEquals(0,h.placement.pool().state(32).routine());assertEquals(0,h.placement.pool().state(33).routine());
            assertEquals(8,h.placement.pool().state(40).routine());assertEquals(1,h.cut.sounds().size());
            assertInstanceOf(SonicCdGameModule.PastMonitorFm.class,h.cut.sounds().getFirst());h.finish();
            var e=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(32,e.wholeIndex());assertEquals(0x1A,e.value());}
    }
    @Test void laterUnsupportedObjectKeepsEarlierHudAndSkipsLaterPhysicalSlot() throws Exception {
        try(var h=new Harness()){h.reserved(2,0x1C,0);h.dynamic(32,0x06,0,0);h.dynamic(33,0x19,0,2);var later=h.placement.pool().state(33);
            var e=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(32,e.wholeIndex());assertEquals(0x06,e.value());
            assertEquals(2,h.row(2).unsignedByte(0x24));assertEquals(later,h.placement.pool().state(33));
            assertThrows(IllegalStateException.class,h::walk);}
    }
    @Test void timeIconZeroDirectionHasNativeInitButNoDraw() throws Exception {
        try(var h=new Harness()){h.reserved(31,0x19,10);h.walk();assertEquals(2,h.row(31).unsignedByte(0x24));
            assertEquals(0x85A8,h.row(31).unsignedWord(2));assertEquals(0x13,h.row(31).unsignedByte(26));h.finish();}
    }
    @Test void pointsAndTimePostsAreSourceFencesRatherThanBlankedObjects() throws Exception {
        for(int[] row:new int[][]{{2,0x1C,0x80},{31,0x19,8}})try(var h=new Harness()){
            h.reserved(row[0],row[1],row[2]);var before=h.row(row[0]);
            assertThrows(CdReservedHud.UnsupportedReservedHud.class,h::walk);assertArrayEquals(before.copy(),h.row(row[0]).copy());}
    }
    @Test void originalPublicationIsOneShotAndExpiresOnCaughtReentry() throws Exception {
        try(var h=new Harness()){h.reserved(2,0x1C,0);h.beforeAdopt=()->assertThrows(IllegalStateException.class,h::walk);
            h.walk();assertNotNull(h.lastAdoption);assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);
            assertThrows(IllegalStateException.class,h.lastAdoption::validateBefore);assertFalse(h.frame.closed());h.finish();}
    }
    @Test void callbackFaultRetainsIdentityAndRetiresItsReceiptWithoutRollback() throws Exception {
        for(Throwable fault:List.of(new IllegalStateException("constructed adopt fault"),new AssertionError("constructed adopt error")))
            try(var h=new Harness()){h.reserved(2,0x1C,0);h.fault=fault;var before=h.cut;
                assertSame(fault,assertThrows(Throwable.class,h::walk));assertSame(before,h.cut);
                assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);assertFalse(h.frame.closed());}
    }
    @Test void readOnlyPortMutationsAreRejectedAndOriginalFaultKeepsSuppressedVerification() throws Exception {
        try(var h=new Harness()){h.reserved(2,0x1C,0);var original=new AssertionError("read fault");
            h.onRead=()->{h.placement.pool().write(h.placement.pool().state(127).moveByte(ID,0x7F));throw original;};
            assertSame(original,assertThrows(AssertionError.class,h::walk));assertTrue(original.getSuppressed().length>0);
            assertEquals(0x7F,h.placement.pool().state(127).id());}
    }
    @Test void currentTerrainDivergenceStopsBeforeARealSolidProbe() throws Exception {
        try(var h=new Harness()){h.dynamic(32,0x19,0,2);h.walk();h.finish();
            h.placement.pool().write(h.placement.pool().state(32).moveByte(SPRITE_FLAGS,0x84));
            h.terrainDiverged=true;var pair=h.frame.captureActor();
            var e=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);
            assertEquals(SonicCdR31aObjectPass.Boundary.CURRENT_TERRAIN_OWNER,e.boundary());assertEquals(pair,h.frame.captureActor());}
    }
    @Test void rejectedDrainPreflightAndDiscardCannotRestartTheOldWalk() throws Exception {
        try(var h=new Harness()){h.reserved(2,0x1C,0);h.walk();assertThrows(IllegalArgumentException.class,()->h.finish(1));
            h.finish();h.pass.discard();assertThrows(IllegalStateException.class,h::walk);}
    }
    @Test void unknownMappingStopsOnlyDuringTheRealDeferredDrainAndRetainsEarlierDrawClear() throws Exception {
        try(var h=new Harness()){h.dynamic(32,0x19,8,2);h.placement.pool().write(h.placement.pool().state(32)
                .moveLong(CdObjectState.LongField.MAPPINGS,0xDEADBEEF).moveByte(SPRITE_FLAGS,0x80).moveByte(WIDTH,15));
            var cpu=h.cut.cpuSat();h.walk();assertEquals(0,h.placement.pool().state(32).spriteFlags());
            var e=assertThrows(UnsupportedOperationException.class,h::finish);assertTrue(e.getMessage().contains("DEADBEEF"));
            assertSame(cpu,h.cut.cpuSat());assertTrue(h.cut.completedCpu().isEmpty());assertThrows(IllegalStateException.class,h::walk);}
    }

    /** Explicit constructed Host substitutes only pending World wiring; it grants no Core admission. */
    @Test void deferredPoolOutputRejectsUnownedSavedFlagMutationAfterNativeAdoption() throws Exception {
        try(var h=new Harness()){h.dynamic(32,0x19,0,2);h.walk();var status=h.placement.status();var flags=h.placement.capture().savedFlags();
            h.afterAdopt=()->h.placement.bsetSavedFlag(1,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
            assertThrows(IllegalArgumentException.class,h::finish);assertEquals(status,h.placement.status());
            assertFalse(Arrays.equals(flags.copy(),h.placement.capture().savedFlags().copy()));}
    }
    @Test void deferredPoolOutputKeepsOriginalErrorAndSuppressesSavedBankViolation() throws Exception {
        try(var h=new Harness()){h.dynamic(32,0x19,0,2);h.walk();var original=new AssertionError("after native drain adoption");
            h.afterAdopt=()->{h.placement.bsetSavedFlag(1,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);throw original;};
            assertSame(original,assertThrows(AssertionError.class,h::finish));assertEquals(1,original.getSuppressed().length);
            assertInstanceOf(IllegalArgumentException.class,original.getSuppressed()[0]);}
    }
    @Test void unknownAbsoluteVIntDoesNotPrecedeScoreLivesOrZeroDirectionIconStores() throws Exception {
        assertAll(
            ()->{try(var h=new Harness(OptionalLong.empty())){h.reserved(2,0x1C,0);h.walk();assertEquals(2,h.row(2).unsignedByte(0x24));assertEquals(0,h.row(2).unsignedByte(26));h.finish();}},
            ()->{try(var h=new Harness(OptionalLong.empty())){h.reserved(3,0x1C,1);h.walk();assertEquals(2,h.row(3).unsignedByte(0x24));assertEquals(1,h.row(3).unsignedByte(26));h.finish();}},
            ()->{try(var h=new Harness(OptionalLong.empty())){h.reserved(31,0x19,10);h.walk();assertEquals(2,h.row(31).unsignedByte(0x24));assertEquals(0x13,h.row(31).unsignedByte(26));h.finish();}});
    }
    @Test void unknownAbsoluteVIntIsNotReadForANonzeroRingsCounter() throws Exception {
        try(var h=new Harness(OptionalLong.empty())){h.rings(1);h.reserved(5,0x1C,0);h.edit(5,b->b[0x29]=1);h.walk();
            assertEquals(2,h.row(5).unsignedByte(0x24));assertEquals(3,h.row(5).unsignedByte(26));h.finish();}
    }
    @Test void unknownAbsoluteVIntStopsZeroRingsOnlyAfterNativeHudInitPrefix() throws Exception {
        try(var h=new Harness(OptionalLong.empty())){h.reserved(5,0x1C,0);h.edit(5,b->b[0x29]=1);var actor=h.frame.captureActor();var shared=h.frame.captureShared();
            var failure=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(SonicCdR31aObjectPass.Boundary.VINT,failure.boundary());
            assertEquals(2,h.row(5).unsignedByte(0x24));assertEquals(0x8568,h.row(5).unsignedWord(2));assertEquals(0x90,h.row(5).unsignedWord(8));
            assertEquals(0x88,h.row(5).unsignedWord(10));assertEquals(3,h.row(5).unsignedByte(26));
            assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());}
    }

    /** Constructed complete component ingress, not Boot/World admission. The Frame/helper/pass
     * are real private producers; faults occur in the original external preparation callbacks. */
    @Test void originalPlaceScreenFaultRetainsChunkActorDirtyChildAndNoPhysicalPlaneWords()throws Exception{
        try(var h=new Harness()){
            placeActor(h);h.placement.pool().write(h.placement.pool().state(32).moveLong(CdObjectState.LongField.MAPPINGS,0x123456).moveWord(X_FRACTION,0xA5A5));
            var fault=new AssertionError("original post-chunk screen verification");h.screenFault=fault;h.service();var clock=h.frame.vIntClock();var art=h.frame.artState();
            assertSame(fault,assertThrows(AssertionError.class,h::walk));assertEquals(1,h.placeStores.size());assertEquals(12,((CdPlayerBody.R31ChunkWord)h.placeStores.getFirst().store()).byteOffset());
            assertEquals(0,h.cut.currentTerrain().chunks().unsignedWord(12));assertEquals(2,h.frame.state().motion().routine());assertEquals(120,h.frame.state().timers().hurt());assertEquals(0,h.frame.state().history().index());
            assertEquals(0x29,h.placement.pool().state(32).id());assertEquals(0x123456,h.placement.pool().state(32).longBits(CdObjectState.LongField.MAPPINGS));assertEquals(0xA5A5,h.placement.pool().state(32).word(X_FRACTION));
            assertSame(clock,h.frame.vIntClock());assertSame(art,h.frame.artState());assertTrue(h.frame.closed());assertEquals(0,h.primaryPublications);assertTrue(h.cut.sounds().isEmpty());
            assertTrue(Arrays.stream(h.placePlane).allMatch(v->v==0xBEEF));assertThrows(IllegalStateException.class,()->h.pass.requirePlace(h.placeStores.getFirst()));
        }
    }
    @Test void eachOriginalPlanePreparationFaultRetainsOnlyTheReachedPhysicalWordPrefix()throws Exception{
        for(int failing=0;failing<4;failing++)try(var h=new Harness()){
            placeActor(h);h.failPlane=failing;var fault=new IllegalArgumentException("original Plane A preparation "+failing);h.placeFault=fault;h.service();
            assertSame(fault,assertThrows(IllegalArgumentException.class,h::walk));assertEquals(0,h.cut.currentTerrain().chunks().unsignedWord(12));assertEquals(failing+2,h.placeStores.size());
            for(int n=0;n<4;n++)assertEquals(n<failing?h.assets.terrain().blocks().unsignedWord(n*2):0xBEEF,h.placePlane[(n/2)*64+12+(n&1)]);
            assertTrue(h.frame.closed());assertEquals(0,h.primaryPublications);assertTrue(h.cut.sounds().isEmpty());
        }
    }
    @Test void actualLastPrefixAllowsFinalDrawAndAppendsOnlyTheNewSoundBeforeUnknownChildDispatch()throws Exception{
        try(var h=new Harness()){
            placeActor(h);h.beforePrimary=()->{assertSame(h.placeStores.getLast().prefix().state(),h.frame.state());assertSame(h.placeStores.getLast().prefix().environment(),h.frame.environment());assertSame(h.placeStores.getLast().prefix().nativeValues().terrain(),h.cut.currentTerrain());};h.service();
            var frontier=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(SonicCdR31aObjectPass.Boundary.OBJECT_FAMILY,frontier.boundary());assertEquals(32,frontier.wholeIndex());assertEquals(0x29,frontier.value());
            assertEquals(20,h.placeStores.size());assertEquals(4,h.placeScreens);assertEquals(1,h.primaryPublications);assertEquals(1,h.cut.sounds().size());assertInstanceOf(SonicCdGameModule.PastFrameSound.class,h.cut.sounds().getFirst());
            assertEquals(4,h.originalPrimary.body().result().effects().stream().filter(CdPlayerBody.R31PlaceBlock.class::isInstance).count());assertFalse(h.frame.closed());
        }
    }
    private static void placeActor(Harness h)throws Exception{
        primaryActor(h,4,4,2,0);var t=h.assets.terrain();byte[] fg=h.assets.view(CdR31aAssets.Asset.LAYOUT_FG).copy();
        Arrays.fill(fg,2,2+t.layout().columns(CdStageTerrain.Plane.FOREGROUND)*t.layout().rows(CdStageTerrain.Plane.FOREGROUND),(byte)1);
        byte[] chunks=t.chunks().copy();int id=h.assets.view(CdR31aAssets.Asset.PLAYER_BLOCKS_A).unsignedWord(2)|0x6000;
        for(int at=0;at<512;at+=2){chunks[at]=(byte)(id>>>8);chunks[at+1]=(byte)id;}
        var c=t.collision();var current=CdStageTerrain.decode(t.shape(),t.blocks().copy(),t.patterns().copy(),chunks,fg,h.assets.view(CdR31aAssets.Asset.LAYOUT_BG).copy(),c.ids().copy(),c.angles().copy(),c.heights().copy(),c.widths().copy());
        var old=h.cut;h.cut=new SonicCdR31aObjectPass.Cut(old.core(),old.ring(),current,old.contact(),old.world(),old.noBumperByte(),0x20,0x380,old.scrollLockWord(),old.layerByte(),old.cpuSat(),old.completedCpu(),old.sounds());
        Arrays.fill(h.placePlane,0xBEEF);
    }

    // Actual R31 source/algorithm, constructed component ingress; never World route authority.
    @Test void ringBindingRequiresExactSourceAndRejectsRawOrRelabelledInputs()throws Exception{
        try(var h=new Harness()){
            var data=h.objects.ringData();assertEquals(0x209BE4,data.mappingAddress());assertEquals(0xA7AE,data.baseTileWord());assertEquals(9,data.visuals().mappingFrames().size());
            assertEquals(new CdR11aObjectAssets.Delta(-24,16),data.deltas().get(15));
            assertThrows(IllegalArgumentException.class,()->new CdRing(h.placement,data));
            try(var programs=CdProgramAssets.open(Path.of(System.getProperty("soniccd.media.root"),"USA","Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty())){
                var other=CdR31aAssets.fromPrograms(programs,CdStagePayload.R31A.stage());assertNotSame(h.assets,other);assertEquals(h.assets.identity(),other.identity());
                assertThrows(IllegalArgumentException.class,()->CdRing.fromR31aAssets(h.placement,other,CdR31aObjectAssets.fromAssets(other)));
            }
            h.dynamic(32,0x10,0,0);var before=h.placement.capture();var ring=CdRing.fromR31aAssets(h.placement,h.assets,h.objects);
            assertThrows(IllegalArgumentException.class,()->ring.executeSelected(32,new CdRing.Inputs(CdReleaseVariant.USA,CdStageKey.parse("R11A"),0,0,1,0),null,null));same(before,h.placement);
            assertThrows(UnsupportedOperationException.class,()->ring.executeSelected(32,new CdRing.Inputs(CdReleaseVariant.USA,CdStageKey.parse("R31A"),0,0,1,0),null,null));same(before,h.placement);
        }
    }
    @Test void ringInitPreservesEveryDirtyByteOutsideLiteralStoresAndDrawClear()throws Exception{
        try(var h=new Harness()){
            h.dynamic(32,0x10,0,0);var before=h.placement.pool().state(32).moveWord(X,0x100).moveWord(Y,0x100).moveWord(X_FRACTION,0xCAFE).moveWord(Y_FRACTION,0xBEEF).moveByte(SAVED_FLAGS_ID,3).moveByte(FLAGS,0xA5).moveByte(ANIMATION_TIME,0xD7).moveByte(ROUTINE_2,0xC3).moveLong(CdObjectState.LongField.CHECKPOINT_PARENT,0xA1B2C3D4).moveByte(STAND_LINK_3D,0xE5);
            h.placement.pool().write(before);h.placement.bsetSavedFlag(3,1,CdObjectPlacementRuntime.SavedBit.LOADED);var actor=h.frame.captureActor();var shared=h.frame.captureShared();
            h.walk();var expected=before.moveByte(ROUTINE,2).moveWord(RING_ANCHOR_X,0x100).moveLong(CdObjectState.LongField.MAPPINGS,0x209BE4).moveWord(TILE,0xA7AE).moveByte(PRIORITY,2).moveByte(SPRITE_FLAGS,4).moveByte(COLLISION_TYPE,0x47).moveByte(WIDTH,8).moveByte(Y_RADIUS,8).moveByte(RING_MEMBER,1).moveByte(MAP_FRAME,0);
            assertEquals(expected,h.placement.pool().state(32));assertEquals(0,h.placement.savedFlag(3,1));assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());h.finish();assertTrue(h.cut.completedCpu().isPresent());
        }
    }
    @Test void ringChildrenUseNextPhysicalFreeSlotsAndRunLaterInTheSamePass()throws Exception{
        try(var h=new Harness()){
            h.dynamic(32,0x10,0,0x22);h.placement.pool().write(h.placement.pool().state(32).moveWord(X,0x100).moveWord(Y,0x100).moveByte(SAVED_FLAGS_ID,7));
            h.dynamic(33,0x10,2,0);h.placement.pool().write(h.placement.pool().state(33).moveWord(RING_ANCHOR_X,0x400));
            h.walk();for(int[] row:new int[][]{{32,0x100,1},{34,0x120,2},{35,0x140,3}}){var state=h.placement.pool().state(row[0]);assertEquals(2,state.routine());assertEquals(row[1],state.centreX());assertEquals(row[2],state.unsignedByte(RING_MEMBER));assertEquals(0x100,state.word(RING_ANCHOR_X));assertEquals(7,state.savedFlagsId());}
            assertArrayEquals(new byte[64],h.placement.pool().state(33).copyBytes(),"Culled original occupant deletes before the already allocated later children run");h.finish();assertEquals(0x10,h.placement.pool().state(35).id());
        }
    }
    @Test void ringFullPoolStopsExpansionAfterLoadedClearAndKeepsUnknownLaterOwner()throws Exception{
        try(var h=new Harness()){
            for(int w=32;w<128;w++)h.dynamic(w,0x29,0,0);h.dynamic(32,0x10,0,6);h.placement.pool().write(h.placement.pool().state(32).moveByte(SAVED_FLAGS_ID,3));h.placement.bsetSavedFlag(3,1,CdObjectPlacementRuntime.SavedBit.LOADED);
            var status=h.placement.status();var e=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(33,e.wholeIndex());assertEquals(0x29,e.value());assertEquals(2,h.placement.pool().state(32).routine());assertEquals(0,h.placement.savedFlag(3,1));assertEquals(status,h.placement.status());assertEquals(0x29,h.placement.pool().state(127).id());
        }
    }
    @Test void ringCollectPublishesSharedWorldGlobalsAndSoundOnlyAtTheOriginalCall()throws Exception{
        for(int rings:new int[]{0,99})try(var h=new Harness()){
            h.dynamic(32,0x10,4,0);h.placement.pool().write(h.placement.pool().state(32).moveByte(SAVED_FLAGS_ID,3).moveByte(RING_MEMBER,1).moveLong(CdObjectState.LongField.MAPPINGS,0x209BE4).moveWord(TILE,0xA7AE));h.rings(rings);
            h.walk();assertEquals(rings+1,h.cut.ring().rings());assertEquals(h.cut.ring().rings(),h.cut.world().rings());assertEquals(h.cut.ring().lives(),h.cut.world().lives());assertEquals(h.cut.ring().updateHudLives(),h.cut.world().updateHudLives());assertEquals(1,h.placement.savedFlag(3,1));assertEquals(6,h.placement.pool().state(32).routine());assertEquals(1,h.cut.sounds().size());
            assertEquals(rings==99?new SonicCdGameModule.PastRingSubCpu(CdRing.SubCpuCommand.SCMD_YESSFX):new SonicCdGameModule.PastRingFm(CdRing.FmSound.FM_RING),h.cut.sounds().getFirst());h.finish();assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);
        }
    }
    @Test void ringSoundPreparationFaultRetainsRewardPrefixBeforeSavedBitAndSparkle()throws Exception{
        try(var h=new Harness()){
            h.dynamic(32,0x10,4,0);h.placement.pool().write(h.placement.pool().state(32).moveByte(SAVED_FLAGS_ID,3).moveByte(RING_MEMBER,1));var fault=new AssertionError("actual Ring FM preparation");
            h.beforeAdopt=()->{if(!h.lastAdoption.next().sounds().isEmpty())throw fault;};
            assertSame(fault,assertThrows(AssertionError.class,h::walk));assertEquals(1,h.cut.ring().rings());assertEquals(1,h.cut.world().rings());assertEquals(6,h.placement.pool().state(32).routine());assertEquals(0,h.placement.savedFlag(3,1));assertTrue(h.cut.sounds().isEmpty());assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);
        }
    }
    @Test void ringLaterMappingFaultRetainsCollectSavedSoundAndDrawPrefixWithOldCpu()throws Exception{
        try(var h=new Harness()){
            h.dynamic(32,0x10,4,0);h.placement.pool().write(h.placement.pool().state(32).moveByte(SAVED_FLAGS_ID,3).moveByte(RING_MEMBER,1).moveLong(CdObjectState.LongField.MAPPINGS,0xFF209BE4).moveByte(SPRITE_FLAGS,0x84));var old=h.cut.cpuSat();
            h.walk();assertEquals(1,h.cut.sounds().size());assertEquals(1,h.placement.savedFlag(3,1));assertEquals(4,h.placement.pool().state(32).spriteFlags());assertThrows(UnsupportedOperationException.class,h::finish);assertSame(old,h.cut.cpuSat());assertEquals(1,h.cut.ring().rings());
        }
    }

    // Constructed full component ingress, not a selected World/capture authority. Native P5
    // runs on its original cached producer; injected faults are original external preparations.
    @Test void amyUnknownCaptureKnowledgeStopsBeforePlcOrAnyObjectWrite()throws Exception{
        try(var h=new Harness()){
            h.dynamic(32,0x32,0,0);var before=h.placement.capture();var state=h.frame.state();var env=h.frame.environment();
            var fault=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);
            assertEquals(SonicCdR31aObjectPass.Boundary.INTRO_GLOBAL_OWNER,fault.boundary());same(before,h.placement);
            assertSame(state,h.frame.state());assertSame(env,h.frame.environment());assertNull(h.lastIntroPlc);
        }
    }
    @Test void amyFirstPlcFailureRetainsOriginalUninitializedObjectAndCannotBeRetried()throws Exception{
        try(var h=new Harness()){
            h.amyKnown(0);h.dynamic(32,0x32,0,0);var before=h.placement.capture();var state=h.frame.state();var env=h.frame.environment();
            var fault=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);
            assertEquals(SonicCdR31aObjectPass.Boundary.INTRO_PLC_OWNER,fault.boundary());assertEquals(11,fault.value());same(before,h.placement);
            assertSame(state,h.frame.state());assertSame(env,h.frame.environment());assertNotNull(h.lastIntroPlc);
            assertThrows(IllegalStateException.class,h.lastIntroPlc::validateBefore);assertThrows(IllegalStateException.class,h.lastIntroPlc::acknowledge);
            assertThrows(IllegalStateException.class,h::walk);
        }
    }
    @Test void amyPalettePreparationFaultRetainsGraphicPrefixAndCompleteFrameNonwrites()throws Exception{
        try(var h=new Harness()){
            h.amyKnown(0);h.dynamic(32,0x32,0,0);var before=h.placement.pool().state(32).moveByte(COLLAPSE_SOLID_MARKER,255).moveByte(SPRITE_FLAGS,0x85).moveByte(FLAGS,0xA5).moveWord(X_FRACTION,0xBEEF);
            h.placement.pool().write(before);var actor=h.frame.captureActor();var shared=h.frame.captureShared();var palette=h.cut.core().normalPalette();var cpu=h.cut.cpuSat();
            var fault=new AssertionError("original Amy palette preparation");h.beforeAdopt=()->{if(h.lastAdoption.introPalette())throw fault;};
            assertSame(fault,assertThrows(AssertionError.class,h::walk));
            var expected=before.moveByte(SPRITE_FLAGS,0x85).moveWord(TILE,0x235E).moveByte(PRIORITY,1).moveLong(CdObjectState.LongField.MAPPINGS,0x23BDB2);
            assertEquals(expected,h.placement.pool().state(32));assertSame(palette,h.cut.core().normalPalette());assertSame(cpu,h.cut.cpuSat());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());
            assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);
        }
    }
    @Test void actualFacingReadFaultKeepsR94ClearAndEarlierPaletteWithoutSelfSuppression()throws Exception{
        try(var h=new Harness()){
            h.amyKnown(0);h.dynamic(32,0x32,0,0);h.placement.pool().write(h.placement.pool().state(32).moveByte(COLLAPSE_SOLID_MARKER,255).moveByte(FLAGS,1).moveByte(SPRITE_FLAGS,0x85));
            var actor=h.frame.captureActor();var shared=h.frame.captureShared();var fault=new IllegalArgumentException("original player X read");
            h.onRead=()->{var s=h.placement.pool().state(32);if(s.longBits(CdObjectState.LongField.MAPPINGS)==0x23BDB2&&(s.flags()&1)==0&&(s.spriteFlags()&1)==0)throw fault;};
            assertSame(fault,assertThrows(IllegalArgumentException.class,h::walk));assertEquals(0,h.placement.pool().state(32).flags()&1);assertEquals(0,h.placement.pool().state(32).spriteFlags()&1);
            assertArrayEquals(CdR31IntroAssets.fromAssets(h.assets).amyPalette().copy(),h.cut.core().normalPalette().slice(32,32).copy());
            assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertTrue(Arrays.stream(fault.getSuppressed()).noneMatch(x->x==fault));
        }
    }
    @Test void capturedAmyPublishesFlagBeforePaletteAndDeletesOnlyAfterBothOriginalCalls()throws Exception{
        try(var h=new Harness()){
            h.amyKnown(1);h.dynamic(32,0x32,255,0);var before=h.placement.pool().state(32);var palette=h.cut.core().normalPalette();var fault=new AssertionError("original capture palette");
            h.beforeAdopt=()->{if(h.lastAdoption.introPalette())throw fault;};
            assertSame(fault,assertThrows(AssertionError.class,h::walk));assertEquals(OptionalInt.of(255),h.cut.amyCapturedByte());assertEquals(before,h.placement.pool().state(32));assertSame(palette,h.cut.core().normalPalette());
        }
        try(var h=new Harness()){
            h.amyKnown(1);h.dynamic(32,0x32,255,0);var actor=h.frame.captureActor();var shared=h.frame.captureShared();h.walk();
            assertEquals(OptionalInt.of(255),h.cut.amyCapturedByte());assertArrayEquals(new byte[64],h.placement.pool().state(32).copyBytes());
            assertArrayEquals(h.assets.view(CdR31aAssets.Asset.PAL_LEVEL).slice(0,32).copy(),h.cut.core().normalPalette().slice(32,32).copy());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());h.finish();
        }
    }
    @Test void metalNativeSoundPreparationFailureRetainsTimerFlipAndAccelerationPrefix()throws Exception{
        try(var h=new Harness()){
            h.dynamic(32,0x31,2,0);h.placement.pool().write(h.placement.pool().state(32).moveByte(SPRITE_FLAGS,0x84).moveByte(TUNNEL_DOOR_TIMER_HIGH,1));
            var fault=new AssertionError("original Metal FM CA");h.beforeAdopt=()->{if(!h.lastAdoption.next().sounds().isEmpty())throw fault;};
            assertSame(fault,assertThrows(AssertionError.class,h::walk));var s=h.placement.pool().state(32);assertEquals(4,s.routine());assertEquals(0xFFF0,s.word(EXPLOSION_POINTS_FRAME));assertEquals(0,Byte.toUnsignedInt(s.copyBytes()[0x3A]));assertEquals(1,s.flags()&1);assertTrue(h.cut.sounds().isEmpty());
        }
    }
    @Test void p5OriginalAdoptionRejectsOtherThreadReentryAndExpiredSameValueReceipt()throws Exception{
        try(var h=new Harness()){
            h.amyKnown(1);h.dynamic(32,0x32,255,0);h.beforeAdopt=()->{
                var a=h.lastAdoption;var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{a.applyNative();}catch(Throwable e){error.set(e);}},"foreign-P5-adoption");
                t.start();try{t.join(5000);}catch(InterruptedException e){throw new AssertionError(e);}assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,error.get());
                assertThrows(IllegalStateException.class,h::walk);assertThrows(IllegalStateException.class,h.pass::discard);
            };
            h.walk();assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);h.finish();
        }
    }
    @Test void p5UsesAllOriginalMetalFramesWithoutPaddingTheOddInclude()throws Exception{
        try(var h=new Harness()){
            var mapper=CdObjectMappings.fromR31aAssets(h.assets,h.objects);var p5=CdR31IntroAssets.fromAssets(h.assets);
            assertEquals(349,p5.metalMappingBytes().length());
            // sprites/r3/metal_sonic.asm .Sprite0..f: literal byte counts and final raw pieces.
            int[] counts={2,2,6,6,6,6,6,6,6,6,1,1,1,1,0,2};
            for(int n=0;n<16;n++)assertEquals(counts[n],mapper.resolve(p5.metalMappings(),false,n).pieces().size());
            assertEquals(List.of(new CdObjectDrawQueue.RawPiece(-24,15,0x30,-20),new CdObjectDrawQueue.RawPiece(8,9,0x40,-12)),mapper.resolve(p5.metalMappings(),false,15).pieces());
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(p5.metalMappings(),false,16));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0xFF23BC54,false,15));
        }
    }
    @Test void introCurrentTerrainFenceRetainsLiteralPrefixBeforeFirstUnownedProbe()throws Exception{
        try(var h=new Harness()){
            h.dynamic(32,0x30,2,1);h.terrainDiverged=true;var pool=h.placement.capture();var actor=h.frame.captureActor();var shared=h.frame.captureShared();
            var fault=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);
            assertEquals(SonicCdR31aObjectPass.Boundary.CURRENT_TERRAIN_OWNER,fault.boundary());assertEquals(32,fault.wholeIndex());same(pool,h.placement);
            assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertThrows(IllegalStateException.class,h::walk);
        }
    }
    @Test void p5PalettePreparerCannotChangeAnotherPhysicalSlotBeforeTheOriginalAdoption()throws Exception{
        try(var h=new Harness()){
            h.amyKnown(1);h.dynamic(32,0x32,255,0);var captured=h.cut.amyCapturedByte();var palette=h.cut.core().normalPalette();
            h.beforeAdopt=()->h.placement.pool().write(h.placement.pool().state(127).moveWord(X_FRACTION,0xCAFE));
            assertThrows(IllegalArgumentException.class,h::walk);assertEquals(captured,h.cut.amyCapturedByte());assertSame(palette,h.cut.core().normalPalette());
            assertEquals(0xCAFE,h.placement.pool().state(127).word(X_FRACTION));assertEquals(0x32,h.placement.pool().state(32).id());
        }
    }

    @Test void introYBuilderChecksEveryStateAndEnvironmentIdentityBeforePreparingTheExistingFormula()throws Exception{
        try(var h=new Harness()){
            var state=h.frame.state();var env=h.frame.environment();
            var equalState=new CdPlayerBody.State(state.motion(),state.animation(),state.header(),state.timers(),state.history(),state.rotationDistance(),state.objectLinks(),state.rotationAngle());
            var equalEnv=new CdPlayerBody.Environment(env.movement(),env.controls(),env.globals(),env.dynamicSlots());
            assertEquals(state,equalState);assertEquals(env,equalEnv);
            assertThrows(IllegalArgumentException.class,()->h.frame.prepareObjectIntroYRewind(equalState,env,state.motion().centreY16_16()));
            assertThrows(IllegalArgumentException.class,()->h.frame.prepareObjectIntroYRewind(state,equalEnv,state.motion().centreY16_16()));
            var ready=h.frame.prepareObjectIntroYRewind(state,env,state.motion().centreY16_16());
            assertEquals(state,ready.finalState());assertEquals(env,ready.finalEnvironment());assertSame(state,h.frame.state());assertSame(env,h.frame.environment());
        }
    }

    @Test void shatteredIntroGetOffRunsAcrossChangedTerrainBecauseItHasNoProbeRead()throws Exception{
        try(var h=new Harness()){
            h.amyKnown(0);h.terrainDiverged=true;h.dynamic(32,0x30,2,0);h.dynamic(33,0x31,2,0);
            h.placement.pool().write(h.placement.pool().state(32).moveWord(X,0x120).moveWord(Y,0x200).moveByte(WIDTH,18).moveByte(Y_RADIUS,32).moveLong(CdObjectState.LongField.MAPPINGS,0x23BE60));
            h.placement.pool().write(h.placement.pool().state(33).moveWord(X,0x120).moveWord(Y,0x200).moveLong(CdObjectState.LongField.MAPPINGS,0x23BC54).moveByte(MAP_FRAME,14));
            var actor=h.frame.captureActor();var shared=h.frame.captureShared();h.walk();assertEquals(4,h.placement.pool().state(32).routine());
            assertEquals(3,h.placement.pool().state(34).unsignedByte(SWING_PART));assertEquals(2,h.placement.pool().state(35).unsignedByte(SWING_PART));assertEquals(1,h.placement.pool().state(36).unsignedByte(SWING_PART));
            assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());h.finish();
        }
    }


    // Compound-fault controls use genuine cached Intro and shared Solid through public constructed
    // Frame entry. StackWalker only chooses this test port's fault cut; it never supplies runtime state.
    @Test void introNativeReservedStandStopPublishesItsAvailablePrefix() throws Exception {
        try(var h=introNativeStandHarness()) {
            var original=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::walk);
            assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS,original.boundary());
            assertEquals(1,original.slotByte());assertEquals(CdSolidObject.Routine.STAND_ON_OBJECT,original.routine());
            assertEquals(8,h.placement.pool().state(32).flags()&8);
            assertEquals(1,h.frame.state().objectLinks().standObject());
            assertEquals(0,original.getSuppressed().length);
            assertThrows(IllegalStateException.class,h::walk);
        }
    }
    @Test void introNativeStopKeepsTheOriginalWhenPrefixReadThrowsRuntime() throws Exception {
        introNativeStopWithPreparationFault(new IllegalStateException("post-native prefix preparation"));
    }
    @Test void introNativeStopKeepsTheOriginalWhenPrefixReadThrowsError() throws Exception {
        introNativeStopWithPreparationFault(new AssertionError("post-native prefix preparation error"));
    }
    private static void introNativeStopWithPreparationFault(Throwable fault) throws Exception {
        try(var h=introNativeStandHarness()) {
            var source=h.placement.pool().state(32);var pool=h.placement.capture();int[] selectedReads={0};
            h.onRead=()->{
                boolean inside=StackWalker.getInstance().walk(frames->frames.anyMatch(f->
                        f.getClassName().equals(SonicCdR31aObjectPass.class.getName())&&f.getMethodName().equals("introSolid")));
                // First: requireOriginalIntroTerrain before Solid. Second: prebuild after NativeStopped.
                if(inside&&++selectedReads[0]==2) {
                    if(fault instanceof RuntimeException e)throw e;throw (Error)fault;
                }
            };
            var original=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::walk);
            assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS,original.boundary());
            assertEquals(1,original.slotByte());assertEquals(CdSolidObject.Routine.STAND_ON_OBJECT,original.routine());
            assertNotSame(fault,original);assertEquals(2,selectedReads[0]);
            assertEquals(1,original.getSuppressed().length);assertSame(fault,original.getSuppressed()[0]);
            // Failed preparation never adopts the unavailable Solid prefix. Earlier real Init remains.
            assertSame(source,h.placement.pool().state(32));same(pool,h.placement);
            assertEquals(2,h.frame.state().motion().routine());assertEquals(8,h.frame.state().motion().flags()&8);
            assertEquals(1,h.frame.state().objectLinks().standObject());assertTrue(h.cut.completedCpu().isEmpty());
            assertThrows(IllegalStateException.class,h::walk);
            if(h.lastAdoption!=null)assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);
        }
    }
    private static Harness introNativeStandHarness() throws Exception {
        var h=new Harness();h.frame.close();var s=state(1,0,0);var m=s.motion();
        var motion=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),
                m.routine(),m.angle(),8,m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),
                m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        var actor=new CdPlayerBody.State(motion,s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),
                new CdPlayerBody.ObjectLinks(0,1),s.rotationAngle());
        h.frame=CdPlayerFrameRuntime.fromR31aEntry(h.assets,actor,environment(),art(),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0),0));
        h.pass=new SonicCdR31aObjectPass(h.assets,h.frame,h.placement,h.objects,h);
        h.dynamic(32,0x30,2,1);
        h.placement.pool().write(h.placement.pool().state(32).moveWord(X,96).moveWord(Y,1055)
                .moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,18).moveByte(Y_RADIUS,32));
        h.service();return h;
    }

    static final class Harness implements SonicCdR31aObjectPass.Ports,AutoCloseable {
        final CdR31aAssets assets=actual();final CdR31aObjectAssets objects=CdR31aObjectAssets.fromAssets(assets);
        final CdObjectPlacementRuntime placement=CdObjectPlacementRuntime.fromR31aAssets(assets,fill(CdObjectPlacementRuntime.SAVED_BANK_BYTES,0));
        CdPlayerFrameRuntime frame;SonicCdR31aObjectPass pass;SonicCdR31aObjectPass.Cut cut;
        final CdStageTerrain divergedTerrain=differentTerrain();
        boolean active,terrainDiverged;int reads,adoptions,primaryPublications;Runnable onRead=()->{},beforeAdopt=()->{},afterAdopt=()->{};Throwable fault;
        // BEGIN qualified original primary test wiring 20261107.
        boolean realPrimary,omitPrimary,failAfterPrimary;Throwable primaryFault;Runnable beforePrimary=()->{};
        SonicCdR31aObjectPass.PrimarySubmission lastPrimary;CdPlayerBody.State prePrimary,readyPrimary;
        CdPlayerFrameRuntime.R31Publication originalPrimary;
        // END qualified original primary test wiring 20261107.
        SonicCdR31aObjectPass.IntroPlcSubmission lastIntroPlc;
        SonicCdR31aObjectPass.Adoption lastAdoption;SonicCdR31aObjectPass.PlcSubmission lastPlc;
        Harness() throws Exception {this(OptionalLong.of(0));}
        Harness(OptionalLong absolute) throws Exception {
            frame=CdPlayerFrameRuntime.fromR31aEntry(assets,state(0,0,0),environment(),art(),new CdPlayerFrameRuntime.VIntClock(absolute,0));
            var art=CdR31aSectionArt.fromAssets(assets);var section=art.fromValues(fill(512,0),CdR31aArtResidency.unknown(assets),0,CdR11aPlcBuffer.cleared(),Optional.empty(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0);
            var core=new SonicCdR31aLoadingVisit.State(SonicCdR31aLoadingVisit.Phase.READY,
                new CdProgramInitialization.State(CdProgramInitialization.KnownBit.SET,CdProgramInitialization.MarkerKnowledge.EQUALS_INIT,new CdProgramInitialization.VersionCache(0,255),CdProgramInitialization.KnownBit.CLEAR),
                section,new SonicCdR31aLoadingVisit.Program(0,0,0,0,0,0,0,0,0),new CdLevelGlobals(0,0,0,0,1,0,0,0,0),fill(128,0),fill(128,0),fill(128,0),fill(128,0),fill(1984,0),Optional.empty(),Optional.empty(),0,0,true,List.of(),SonicCdR31aLoadingVisit.ReturnKind.NONE,Optional.empty());
            cut=new SonicCdR31aObjectPass.Cut(core,new CdRingGlobals(0,3,0,0,0,0,0,0),assets.terrain(),new CdPlayerBody.ContactGlobals(0,0,0),new CdPlayerTouchResponse.WorldGlobals(0,3,0,0,5000,0),OptionalInt.of(0),0,0,0x33A5,OptionalInt.empty(),fill(640,0),Optional.empty(),List.of());
            pass=new SonicCdR31aObjectPass(assets,frame,placement,objects,this);
        }
        public void validate(SonicCdR31aObjectPass p,CdR31aAssets a,CdPlayerFrameRuntime f,CdObjectPlacementRuntime v,CdR31aObjectAssets o){if(!active||p!=pass||a!=assets||f!=frame||v!=placement||o!=objects)throw new IllegalStateException("No constructed private caller");}
        public SonicCdR31aObjectPass.Cut read(){reads++;onRead.run();if(terrainDiverged)return new SonicCdR31aObjectPass.Cut(cut.core(),cut.ring(),divergedTerrain,cut.contact(),cut.world(),cut.noBumperByte(),cut.cameraXWord(),cut.cameraYWord(),cut.scrollLockWord(),cut.layerByte(),cut.cpuSat(),cut.completedCpu(),cut.sounds(),cut.amyCapturedByte());return cut;}
        public void adopt(SonicCdR31aObjectPass.Adoption a){lastAdoption=a;a.validateBefore();beforeAdopt.run();if(fault instanceof RuntimeException e)throw e;if(fault instanceof Error e)throw e;var next=a.next();a.applyNative();cut=next;adoptions++;var once=afterAdopt;afterAdopt=()->{};once.run();}
        SonicCdR31aObjectPass.BumperPublication lastBumper;
        public void bumper(SonicCdR31aObjectPass.BumperPublication p){
            lastBumper=p;p.validateBefore();beforeAdopt.run();if(fault instanceof RuntimeException e)throw e;if(fault instanceof Error e)throw e;
            p.validateBefore();var next=p.next();p.applyNative();cut=next;p.acknowledge();adoptions++;
            var once=afterAdopt;afterAdopt=()->{};once.run();
        }
        public CdPlayerBody.R31Context primaryContext(){
            if(!realPrimary)throw new AssertionError("Constructed initial tests do not mint Main/Hurt context");
            return new CdPlayerBody.R31Context(frame.stageSource(),assets,cut.currentTerrain(),placement.pool().capture(),Optional.empty(),
                CdPlayerObjectTouch.fromR31aAssets(assets),new CdPlayerBody.ResponseContext(CdPlayerTouchResponse.fromR31aAssets(assets),cut.contact(),cut.world()),
                cut.noBumperByte(),OptionalInt.empty(),OptionalInt.empty(),cut.cameraXWord(),cut.cameraYWord());
        }
        final List<CdPlayerFrameRuntime.R31PlaceBlockCall> placeStores=new ArrayList<>();int placeScreens,failPlane=-1;Throwable placeFault,screenFault;final int[] placePlane=new int[2048];
        public void placeBlock(SonicCdR31aObjectPass owner,CdPlayerFrameRuntime.R31PlaceBlockCall original){
            assertSame(pass,owner);pass.requirePlace(original);placeStores.add(original);var next=pass.preparedPlace(original);
            if(original.store() instanceof CdPlayerBody.R31PlaneAWord word){
                if(word.index()==failPlane){if(placeFault instanceof Error e)throw e;throw (RuntimeException)placeFault;}
                int at=word.row()*64+word.column(),ready=word.word();pass.requirePlace(original);pass.applyPlace(original);placePlane[at]=ready;
            }else pass.applyPlace(original);
            cut=next;
        }
        public void placeScreen(SonicCdR31aObjectPass owner,CdPlayerFrameRuntime.R31PlaceBlockScreenCall original){
            assertSame(pass,owner);pass.requirePlace(original);placeScreens++;
            if(screenFault instanceof Error e)throw e;if(screenFault instanceof RuntimeException e)throw e;pass.verifyPlaceScreen(original);
        }
        public void primary(SonicCdR31aObjectPass.PrimarySubmission p){primaryPublications++;
            if(!realPrimary)throw p.unowned();lastPrimary=p;p.validateBefore();prePrimary=frame.state();readyPrimary=p.readyState();originalPrimary=p.publication();
            var next=p.next();beforePrimary.run();
            if(!failAfterPrimary){if(primaryFault instanceof Error e)throw e;if(primaryFault instanceof RuntimeException e)throw e;}
            if(omitPrimary)return;p.applyNative();cut=next;
            if(failAfterPrimary){if(primaryFault instanceof Error e)throw e;if(primaryFault instanceof RuntimeException e)throw e;}
        }
        public void submitIntroPlc(SonicCdR31aObjectPass.IntroPlcSubmission p){lastIntroPlc=p;p.validateBefore();throw p.unowned();}
        public void submitTitlePlc(SonicCdR31aObjectPass.PlcSubmission p){lastPlc=p;p.validateBefore();throw p.unowned();}
        void primary(int id,int routine,int flags)throws java.io.IOException {frame.close();frame=CdPlayerFrameRuntime.fromR31aEntry(assets,state(id,routine,flags),environment(),art(),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0),0));pass=new SonicCdR31aObjectPass(assets,frame,placement,objects,this);}
        void service(){frame.bindArtPublication((dma,full)->{});var selected=frame.prepareInput(0);frame.serviceVBlank(selected.phase());frame.advanceLevelMainFrame();}
        void walk(){boolean prior=active;active=true;try{pass.runWalk(frame.environment().globals().levelFrames());}finally{active=prior;}}
        void finish(){finish(0);}void finish(int x){boolean prior=active;active=true;try{pass.finishDraw(new CdObjectDrawQueue.DrawCameras(new CdObjectDrawQueue.CameraWords(x,cut.cameraYWord()),new CdObjectDrawQueue.CameraWords(0,0),new CdObjectDrawQueue.CameraWords(0,0)));}finally{active=prior;}}
        CdAssetBytes row(int w){return cut.core().reserved().slice((w-1)*64,64);}
        void reserved(int w,int id,int subtype){edit(w,b->{b[0]=(byte)id;b[0x28]=(byte)subtype;});}
        void edit(int w,java.util.function.Consumer<byte[]> change){byte[] bank=cut.core().reserved().copy();byte[] row=Arrays.copyOfRange(bank,(w-1)*64,w*64);change.accept(row);System.arraycopy(row,0,bank,(w-1)*64,64);var s=cut.core();
            var ready=new SonicCdR31aLoadingVisit.State(s.phase(),s.initialization(),s.section(),s.program(),s.level(),s.normalPalette(),s.fadePalette(),s.waterPalette(),s.displayedPalette(),CdAssetBytes.copyOf(bank),s.blocksRam(),s.layoutRam(),s.initialFadeSteps(),s.fromFadeSteps(),s.displayEnabled(),s.calls(),s.returnKind(),s.music());
            cut=new SonicCdR31aObjectPass.Cut(ready,cut.ring(),cut.currentTerrain(),cut.contact(),cut.world(),cut.noBumperByte(),cut.cameraXWord(),cut.cameraYWord(),cut.scrollLockWord(),cut.layerByte(),cut.cpuSat(),cut.completedCpu(),cut.sounds());}
        void rings(int n){var r=cut.ring();cut=new SonicCdR31aObjectPass.Cut(cut.core(),new CdRingGlobals(n,r.lives(),r.livesFlags(),r.updateHudRings(),r.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),cut.currentTerrain(),cut.contact(),new CdPlayerTouchResponse.WorldGlobals(n,r.lives(),r.updateHudLives(),cut.world().score(),cut.world().nextLifeScore(),cut.world().updateHudScore()),cut.noBumperByte(),cut.cameraXWord(),cut.cameraYWord(),cut.scrollLockWord(),cut.layerByte(),cut.cpuSat(),cut.completedCpu(),cut.sounds());}
        void amyKnown(int value){cut=new SonicCdR31aObjectPass.Cut(cut.core(),cut.ring(),cut.currentTerrain(),cut.contact(),cut.world(),cut.noBumperByte(),cut.cameraXWord(),cut.cameraYWord(),cut.scrollLockWord(),cut.layerByte(),cut.cpuSat(),cut.completedCpu(),cut.sounds(),OptionalInt.of(value));}
        void dynamic(int w,int id,int routine,int subtype){placement.pool().write(placement.pool().state(w).moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype));}
        public void close(){frame.close();}
    }
    // BEGIN real primary queue tests 20261107; explicit component ingress, not World or pixel proof.
    @Test void realServicedMainStagesNativeDrawInsideTheOriginalFrameCall() throws Exception {
        try(var h=new Harness()){primaryActor(h,2,0x84,0,0);var before=h.frame.state();h.service();var serviced=h.frame.state();h.walk();
            assertNotNull(h.lastPrimary);assertSame(serviced,h.prePrimary);assertSame(h.readyPrimary,h.frame.state());
            assertEquals(0,h.frame.state().motion().spriteFlags()&0x80);assertEquals(0x80,h.originalPrimary.body().result().state().motion().spriteFlags()&0x80);
            assertEquals(h.originalPrimary.body().result().display(),h.frame.display());assertEquals(before.history().index()+4,h.frame.state().history().index());
            assertEquals(1,h.primaryPublications);assertThrows(IllegalStateException.class,h.lastPrimary::applyNative);h.finish();
            assertTrue(h.cut.completedCpu().orElseThrow().provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==0));
            assertEquals(0x80,h.frame.state().motion().spriteFlags()&0x80);assertEquals(0x80,h.frame.display().orElseThrow().spriteFlags()&0x80);}
    }
    @Test void realServicedHurtDrawUsesItsSingleOriginalProducerAndPostBodyLiveImage() throws Exception {
        try(var h=new Harness()){primaryActor(h,4,0x84,2,0);h.service();h.walk();
            assertEquals(CdPlayerBody.Boundary.HURT_RETURN,h.originalPrimary.body().result().boundary());assertEquals(1,h.primaryPublications);
            assertEquals(0,h.frame.state().motion().spriteFlags()&0x80);assertSame(h.readyPrimary,h.frame.state());h.finish();
            assertTrue(h.cut.completedCpu().orElseThrow().provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==0));}
    }
    @Test void nativeDisplaySuppressionDoesNotInventAQueueEntryOrClearBitSeven() throws Exception {
        try(var h=new Harness()){primaryActor(h,2,0x84,0,1);h.service();h.walk();
            assertFalse(h.originalPrimary.body().result().display().orElseThrow().queued());
            assertSame(h.originalPrimary.body().result().state(),h.readyPrimary);assertEquals(0x80,h.frame.state().motion().spriteFlags()&0x80);h.finish();
            assertFalse(h.cut.completedCpu().orElseThrow().provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==0));}
    }
    @Test void primaryClearAndLoadedSetPreserveTheCompleteNonwrittenFrameValues() throws Exception {
        try(var h=new Harness()){primaryActor(h,2,0xC5,0,0);h.service();h.walk();var result=h.originalPrimary.body().result();
            assertOnlySpriteBit(result.state(),h.readyPrimary,false);assertEquals(result.environment(),h.frame.environment());
            var before=h.frame.state();var env=h.frame.environment();var art=h.frame.artState();var clock=h.frame.vIntClock();h.finish();
            assertOnlySpriteBit(before,h.frame.state(),true);assertSame(env,h.frame.environment());assertSame(art,h.frame.artState());assertSame(clock,h.frame.vIntClock());}
    }
    @Test void originalPrimaryPortReentryRejectsWithoutClosingHealthyProducer() throws Exception {
        try(var h=new Harness()){primaryActor(h,2,0x84,0,0);h.beforePrimary=()->{
                assertThrows(IllegalStateException.class,h::walk);assertThrows(IllegalStateException.class,h::finish);
                assertThrows(IllegalStateException.class,()->h.pass.discard());};h.service();h.walk();assertFalse(h.frame.closed());h.finish();}
    }
    @Test void retainedPrimarySubmissionCannotRepeatOrEscapeItsThread() throws Exception {
        try(var h=new Harness()){primaryActor(h,2,0x84,0,0);h.beforePrimary=()->{
                var caught=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{h.lastPrimary.applyNative();}catch(Throwable e){caught.set(e);}},"constructed-primary-foreign-thread");
                t.start();try{t.join(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,caught.get());};
            h.service();h.walk();assertThrows(IllegalStateException.class,h.lastPrimary::validateBefore);assertThrows(IllegalStateException.class,h.lastPrimary::applyNative);h.finish();}
    }
    @Test void originalPrimaryPortFailureKeepsServicedPrefixWithoutPublishingPreparedDraw() throws Exception {
        for(Throwable e:List.of(new IllegalArgumentException("primary pre-apply"),new AssertionError("primary pre-apply error")))try(var h=new Harness()){
            primaryActor(h,2,0x84,0,0);h.primaryFault=e;h.service();var state=h.frame.state();var env=h.frame.environment();var art=h.frame.artState();var clock=h.frame.vIntClock();var pool=h.placement.capture();
            assertSame(e,assertThrows(Throwable.class,h::walk));assertSame(state,h.frame.state());assertSame(env,h.frame.environment());assertSame(art,h.frame.artState());assertSame(clock,h.frame.vIntClock());
            same(pool,h.placement);assertFalse(h.frame.closed());assertThrows(IllegalStateException.class,h.lastPrimary::applyNative);assertThrows(IllegalStateException.class,h::walk);}
    }
    @Test void originalAfterApplyFailureRetainsPreparedPrimaryPrefixAndClosesFrame() throws Exception {
        for(Throwable e:List.of(new IllegalStateException("primary post-apply"),new AssertionError("primary post-apply error")))try(var h=new Harness()){
            primaryActor(h,2,0x84,0,0);h.primaryFault=e;h.failAfterPrimary=true;h.service();assertSame(e,assertThrows(Throwable.class,h::walk));
            assertSame(h.readyPrimary,h.frame.state());assertEquals(0,h.frame.state().motion().spriteFlags()&0x80);assertTrue(h.frame.closed());
            assertThrows(IllegalStateException.class,h.lastPrimary::applyNative);}
    }
    @Test void missingPrimaryAcknowledgementConsumesEntitlementAndDoesNotAdoptDrawData() throws Exception {
        try(var h=new Harness()){primaryActor(h,2,0x84,0,0);h.omitPrimary=true;h.service();var state=h.frame.state();
            assertThrows(IllegalStateException.class,h::walk);assertSame(state,h.frame.state());assertFalse(h.frame.closed());
            assertThrows(IllegalStateException.class,h.lastPrimary::applyNative);}
    }
    @Test void drainUsesActualPostBodyPositionAndPreservesPrimaryClearOnSelectedMappingFailure() throws Exception {
        try(var h=new Harness()){primaryActor(h,2,0x84,0,0);h.dynamic(32,0x19,8,2);
            h.placement.pool().write(h.placement.pool().state(32).moveLong(CdObjectState.LongField.MAPPINGS,0xDEADBEEF).moveByte(PRIORITY,0).moveByte(SPRITE_FLAGS,0x80));
            h.service();h.walk();assertEquals(0,h.frame.state().motion().spriteFlags()&0x80);
            assertThrows(UnsupportedOperationException.class,h::finish);assertEquals(0,h.frame.state().motion().spriteFlags()&0x80);
            assertTrue(h.cut.completedCpu().isEmpty());assertFalse(h.frame.closed());}
    }
    @Test void genuinePrimaryQueueCullClearsBitSevenWithoutADeferredLoadedSet() throws Exception {
        try(var h=new Harness()){primaryActor(h,2,0x84,0,0);var c=h.cut;
            h.cut=new SonicCdR31aObjectPass.Cut(c.core(),c.ring(),c.currentTerrain(),c.contact(),c.world(),c.noBumperByte(),0x800,c.cameraYWord(),c.scrollLockWord(),c.layerByte(),c.cpuSat(),c.completedCpu(),c.sounds());
            h.service();h.walk();assertTrue(h.originalPrimary.body().result().display().orElseThrow().queued());assertEquals(0,h.frame.state().motion().spriteFlags()&0x80);
            h.finish(0x800);assertFalse(h.cut.completedCpu().orElseThrow().provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==0));assertEquals(0,h.frame.state().motion().spriteFlags()&0x80);}
    }
    @Test void laterMappingFaultRetainsTheEarlierRealPrimaryLoadedSetAndOldCpu() throws Exception {
        try(var h=new Harness()){primaryActor(h,2,0x84,0,0);h.dynamic(32,0x19,8,2);
            h.placement.pool().write(h.placement.pool().state(32).moveLong(CdObjectState.LongField.MAPPINGS,0xDEADBEEF).moveByte(PRIORITY,3).moveByte(SPRITE_FLAGS,0x80));
            var cpu=h.cut.cpuSat();h.service();h.walk();var before=h.frame.state();assertEquals(0,before.motion().spriteFlags()&0x80);
            assertThrows(UnsupportedOperationException.class,h::finish);assertOnlySpriteBit(before,h.frame.state(),true);
            assertSame(cpu,h.cut.cpuSat());assertTrue(h.cut.completedCpu().isEmpty());assertThrows(IllegalStateException.class,h::walk);}
    }
    private static void primaryActor(Harness h,int routine,int sprites,int flags,int hurt) throws Exception {
        h.frame.close();var s=state(1,routine,sprites);var m=s.motion();
        var motion=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),routine,m.angle(),flags,sprites,m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        var actual=new CdPlayerBody.State(motion,s.animation(),new CdPlayerBody.Header(1,h.assets.range(CdR31aAssets.Asset.MAP_SPR_SONIC).nativeAddress(),0x780,2,24),new CdPlayerBody.Timers(hurt,0,0,0),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());
        h.frame=CdPlayerFrameRuntime.fromR31aEntry(h.assets,actual,environment(),art(),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0),0));
        // Explicit constructed SetupCamera values from the actual native initial-centre/bounds.
        var start=h.assets.initialCentre();var bounds=h.assets.bounds();int camX=start.centreX()<160?0:(start.centreX()-160)&65535;
        if(camX>=bounds.right())camX=bounds.right();int camY=start.centreY()<0x60?0:(start.centreY()-0x60)&65535;
        if((short)camY>=(short)bounds.bottom())camY=bounds.bottom();var cut=h.cut;
        h.cut=new SonicCdR31aObjectPass.Cut(cut.core(),cut.ring(),cut.currentTerrain(),cut.contact(),cut.world(),cut.noBumperByte(),camX,camY,cut.scrollLockWord(),cut.layerByte(),cut.cpuSat(),cut.completedCpu(),cut.sounds());
        h.pass=new SonicCdR31aObjectPass(h.assets,h.frame,h.placement,h.objects,h);h.realPrimary=true;
    }
    private static void assertOnlySpriteBit(CdPlayerBody.State before,CdPlayerBody.State after,boolean set){var m=before.motion();
        var expected=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),(m.spriteFlags()&127)|(set?128:0),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        assertEquals(new CdPlayerBody.State(expected,before.animation(),before.header(),before.timers(),before.history(),before.rotationDistance(),before.objectLinks(),before.rotationAngle()),after);
    }
    // END real primary queue tests 20261107.
    private static CdR31aAssets retained;
    private static synchronized CdR31aAssets actual() throws Exception {if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Optional original USA media absent");List<Path> cues;try(var paths=Files.list(Path.of(root).resolve("USA"))){cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();}assertEquals(1,cues.size());try(var p=CdProgramAssets.open(cues.getFirst(),Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=CdR31aAssets.fromPrograms(p,CdStagePayload.R31A.stage());}return retained;}
    private static CdPlayerBody.State state(int id,int routine,int flags){return new CdPlayerBody.State(new CdPlayerMotionState(96<<16,1004<<16,0,0,0,routine,0,0,flags,9,19,0,0,0,0,0,0,0,0,0),new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(id,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(fill(256,0),0),0,new CdPlayerBody.ObjectLinks(0,0),0);}
    private static CdPlayerBody.Environment environment(){return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,12,128),new CdPlayerMovement.Bounds(0,0x1000,0x1000,0),new CdPlayerMovement.Gates(0x100,1,0,0,0,0,0),new CdPlayerMovement.Globals(0,0x60,0,0,0,0,0,0,0,0)),new CdPlayerBody.Controls(0,0,0,0,1),new CdPlayerBody.Globals(7,0x60,0,0,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);}
    private static CdPlayerArtRuntime.State art(){return new CdPlayerArtRuntime.State(fill(768,0),fill(768,0),0,false);}
    private static CdStageTerrain differentTerrain(){byte[] fg=new byte[290],bg=new byte[24];fg[0]=47;fg[1]=5;bg[0]=6;bg[1]=2;
        return CdStageTerrain.decode(CdStageTerrain.Shape.CCZ1_PRESENT,new byte[5248],new byte[22208],new byte[54784],fg,bg,new byte[700],new byte[256],new byte[4096],new byte[4096]);}
    private static CdAssetBytes fill(int n,int b){byte[] a=new byte[n];Arrays.fill(a,(byte)b);return CdAssetBytes.copyOf(a);}
    private static void word(byte[] b,int i,int v){b[i]=(byte)(v>>>8);b[i+1]=(byte)v;}
    private static void same(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p){var now=p.capture();assertEquals(before.status(),now.status());assertEquals(before.pool().slots(),now.pool().slots());assertArrayEquals(before.savedFlags().copy(),now.savedFlags().copy());}
}
