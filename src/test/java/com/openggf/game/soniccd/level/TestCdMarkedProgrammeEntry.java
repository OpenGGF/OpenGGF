package com.openggf.game.soniccd.level;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import java.util.Arrays;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Component requirements written before production. Original bytes supply only the source;
 * all actor/global/art/clock inputs below are explicitly constructed values, not a native arrival,
 * saved-state load, Module/FULL/World authority, route or regional execution. All Java UNRUN. */
class TestCdMarkedProgrammeEntry {
    private static CdStagePlayerSource source(CdStagePayload payload)throws Exception {
        try(var media=TestCdCczProgrammeData.open()) {
            return CdProgrammeReader.open(media,payload.stage()).playerSource();
        }
    }
    private record Values(CdPlayerBody.State state,CdPlayerBody.Environment environment,
                          CdPlayerArtRuntime.State art,CdPlayerFrameRuntime.VIntClock clock) { }
    private static Values values(CdStagePlayerSource source,int period,CdPlayerFrameRuntime.VIntClock clock) {
        byte[] history=new byte[0x100],cpu=new byte[0x300],resident=new byte[0x300];
        for(int n=0;n<history.length;n++)history[n]=(byte)(n^0xA5);
        Arrays.fill(cpu,(byte)0x6B);Arrays.fill(resident,(byte)0x93);
        var motion=new CdPlayerMotionState(0x12345678,0x09ABCDEF,0x1234,-0x2345,0x3456,
                2,0x15,0x42,0x84,0x13,0x17,7,6,1,0x39,-0x1234,0x27,0x81,0x45,0x67);
        var state=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(3,5,9),
                new CdPlayerBody.Header(1,source.mappingAddress(),0xA123,3,0x18),
                new CdPlayerBody.Timers(0x1234,0x2345,0x3456,0x4567),
                new CdPlayerBody.History(CdAssetBytes.copyOf(history),0x28),0x59,
                new CdPlayerBody.ObjectLinks(0x46,0x57),0x69);
        var movement=new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,0xC,0x80),
                new CdPlayerMovement.Bounds(0x40,0x2A00,0x740,0x31),
                new CdPlayerMovement.Gates(source.nativeZoneActWord(),period,2,3,4,5,0xFF),
                new CdPlayerMovement.Globals(1,0x95,0x23,0x45,0x6789,0x4567,0x67,0x89,0xAB,0xCD));
        var environment=new CdPlayerBody.Environment(movement,new CdPlayerBody.Controls(0x18,0x10,8,8,0x81),
                new CdPlayerBody.Globals(0x1234,0x2345,1,0x3456,0x4567,0x5678,0x67,0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        return new Values(state,environment,new CdPlayerArtRuntime.State(cpu,resident,0x56,true),clock);
    }
    private static Values values(CdStagePlayerSource source,int period) {
        return values(source,period,new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFFFF_FFFEL),0x1234));
    }
    private static CdPlayerFrameRuntime marked(CdStagePlayerSource source,Values values) {
        return CdPlayerFrameRuntime.fromMarkedProgrammeEntry(source,values.state(),values.environment(),values.art(),values.clock());
    }
    private static void unchanged(CdStagePlayerSource source,Values expected,CdPlayerFrameRuntime actual) {
        assertSame(source,actual.stageSource());assertSame(expected.state(),actual.state());
        assertSame(expected.environment(),actual.environment());assertSame(expected.art(),actual.artState());
        assertSame(expected.clock(),actual.vIntClock());assertTrue(actual.display().isEmpty());
        assertFalse(actual.closed());
    }
    private static CdPlayerBody.Environment gates(CdPlayerBody.Environment environment,int zone,int period) {
        var m=environment.movement();var g=m.gates();
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(),m.bounds(),
                new CdPlayerMovement.Gates(zone,period,g.miniSonic(),g.waterSlide(),g.windTunnel(),g.speedShoes(),g.timeWarpDirection()),
                m.globals()),environment.controls(),environment.globals(),environment.dynamicSlots());
    }

    @Test void retainedPastKeepsAllMarkedActorGlobalArtAndClockValues()throws Exception {
        var source=source(CdStagePayload.R11B);var values=values(source,0x80);
        try(var frame=marked(source,values)) {
            assertTrue(source.hasRetainedPpzProgrammeSource());assertEquals(0,source.ordinaryPeriod());
            unchanged(source,values,frame);assertEquals(0x80,frame.environment().movement().gates().timeZone());
            assertEquals(0x5678,frame.state().motion().centreX16_16()&0xFFFF);
            assertEquals(0xCDEF,frame.state().motion().centreY16_16()&0xFFFF);
        }
    }
    @Test void unknownClockRetainsItsUnknownSeedAndNonzeroDelta()throws Exception {
        var source=source(CdStagePayload.R11B);var clock=new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),0xABCDEFL);
        var values=values(source,0x80,clock);try(var frame=marked(source,values)) {
            unchanged(source,values,frame);assertTrue(frame.vIntClock().absoluteValue().isEmpty());
            assertEquals(0xABCDEFL,frame.vIntClock().deltaSinceAdmittedCut());
        }
    }
    @Test void artIsNeitherPreparedNorPublishedDuringBirth()throws Exception {
        var source=source(CdStagePayload.R11B);var values=values(source,0x80);try(var frame=marked(source,values)) {
            assertSame(values.art(),frame.artState());assertTrue(frame.artState().dirty());
            assertEquals(0x56,frame.artState().lastFrame());assertEquals(0x6B,frame.artState().cpuBuffer().unsignedByte(0));
            assertEquals(0x93,frame.artState().residentBank().unsignedByte(0));
            assertEquals(values.state(),frame.captureActor().state());assertSame(values.art(),frame.captureShared().artState());
        }
    }
    @Test void markerRemainsUntilTheSeparatelyInvokedExistingNativeWriter()throws Exception {
        var source=source(CdStagePayload.R11B);var values=values(source,0x80);try(var frame=marked(source,values)) {
            assertEquals(0x80,frame.environment().movement().gates().timeZone());
            assertTrue(frame.clearLevelStartTimeZoneMarker());
            assertEquals(gates(values.environment(),source.nativeZoneActWord(),0),frame.environment());
            assertSame(values.state(),frame.state());assertSame(values.art(),frame.artState());assertSame(values.clock(),frame.vIntClock());
        } // Constructed component invocation only; this does not prove Main:315 was reached.
    }
    @Test void unmarkedAndWrongLowPeriodBytesRejectWithoutMasking()throws Exception {
        var source=source(CdStagePayload.R11B);
        for(int period:new int[]{0,1,2,0x7F,0x81,0x82,0x83,0xFF}) {
            var values=values(source,period);
            assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->marked(source,values));
            assertEquals(period,values.environment().movement().gates().timeZone());
        }
    }
    @Test void thePresentSourceCannotClaimThePastMarker()throws Exception {
        var source=source(CdStagePayload.R11A);assertTrue(source.hasRetainedPpzProgrammeSource());
        assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->marked(source,values(source,0x80)));
        var values=values(source,0x81);try(var frame=marked(source,values)){unchanged(source,values,frame);}
    }
    @Test void wrongNativeZoneWordRejectsBeforeComponentConstruction()throws Exception {
        var source=source(CdStagePayload.R11B);var values=values(source,0x80);
        for(int zone:new int[]{1,0x100,0x200,0xFFFF}) {
            var foreign=new Values(values.state(),gates(values.environment(),zone,0x80),values.art(),values.clock());
            assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->marked(source,foreign));
            assertSame(values.state(),foreign.state());assertSame(values.art(),foreign.art());
        }
    }
    @Test void waterAndR3SourceGraphsCannotClaimThePpzMarkedIngress()throws Exception {
        for(var payload:new CdStagePayload[]{CdStagePayload.R41A,CdStagePayload.R31A}) {
            var source=source(payload);assertFalse(source.hasRetainedPpzProgrammeSource());
            assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->marked(source,values(source,source.ordinaryPeriod()|0x80)));
        }
    }
    @Test void legacyPpzAggregateDoesNotAcquireRetainedProgrammeCustody()throws Exception {
        var source=legacySource(CdReleaseVariant.USA);assertFalse(source.hasRetainedPpzProgrammeSource());
        assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->marked(source,values(source,0x81)));
        var ordinary=values(source,source.ordinaryPeriod());try(var frame=CdPlayerFrameRuntime.fromProgrammeEntry(
                source,ordinary.state(),ordinary.environment(),ordinary.art(),ordinary.clock())){unchanged(source,ordinary,frame);}
    }
    @Test void unsupportedRegionalComponentMetadataStillRejects()throws Exception {
        for(var variant:new CdReleaseVariant[]{CdReleaseVariant.JAPAN,CdReleaseVariant.EUROPE}) {
            var constructed=legacySource(variant);assertEquals(variant,constructed.variant());
            assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->marked(constructed,values(constructed,0x81)));
        } // Constructed negative metadata, not JP/EU media validation or admission.
    }
    @Test void existingNormalFactoryKeepsItsExactUnmarkedContract()throws Exception {
        var source=source(CdStagePayload.R11B);var normal=values(source,0);
        try(var frame=CdPlayerFrameRuntime.fromProgrammeEntry(source,normal.state(),normal.environment(),normal.art(),normal.clock())) {
            unchanged(source,normal,frame);
        }
        var marked=values(source,0x80);
        assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->CdPlayerFrameRuntime.fromProgrammeEntry(
                source,marked.state(),marked.environment(),marked.art(),marked.clock()));
        var water=source(CdStagePayload.R41A);var values=values(water,water.ordinaryPeriod());
        try(var frame=CdPlayerFrameRuntime.fromProgrammeEntry(water,values.state(),values.environment(),values.art(),values.clock())) {
            unchanged(water,values,frame);
        }
    }
    @Test void nullInputsAndOutOfWidthGatesDoNotBecomeDefaults()throws Exception {
        var source=source(CdStagePayload.R11B);var values=values(source,0x80);
        assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.fromMarkedProgrammeEntry(null,values.state(),values.environment(),values.art(),values.clock()));
        assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.fromMarkedProgrammeEntry(source,null,values.environment(),values.art(),values.clock()));
        assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.fromMarkedProgrammeEntry(source,values.state(),null,values.art(),values.clock()));
        assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.fromMarkedProgrammeEntry(source,values.state(),values.environment(),null,values.clock()));
        assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.fromMarkedProgrammeEntry(source,values.state(),values.environment(),values.art(),null));
        assertThrows(IllegalArgumentException.class,()->gates(values.environment(),0x1_0000,0x80));
        assertThrows(IllegalArgumentException.class,()->gates(values.environment(),0,0x180));
    }
    @Test void newComponentHasFreshCaptureOwnershipAndCannotRestoreAnotherFrame()throws Exception {
        var source=source(CdStagePayload.R11B);var values=values(source,0x80);
        try(var first=marked(source,values);var second=marked(source,values)) {
            var actor=first.captureActor();var shared=first.captureShared();
            assertNotSame(actor.owner(),second.captureActor().owner());
            assertThrows(IllegalArgumentException.class,()->second.restoreActor(actor));
            assertThrows(IllegalArgumentException.class,()->second.restoreShared(shared));
            unchanged(source,values,first);unchanged(source,values,second);
        }
    }
    @Test void noOrdinaryServiceOrBodyEntitlementIsMintedByTheFactory()throws Exception {
        var source=source(CdStagePayload.R11B);var values=values(source,0x80);try(var frame=marked(source,values)) {
            assertThrows(IllegalStateException.class,()->frame.runBody(0));
            frame.prepareInput(8);
            assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
            frame.clearPendingInput();unchanged(source,values,frame);
        }
    }
    @Test void independentRetainedReadersAndOldLifetimesRemainSeparate()throws Exception {
        var firstSource=source(CdStagePayload.R11B);var secondSource=source(CdStagePayload.R11B);assertNotSame(firstSource,secondSource);
        var a=values(firstSource,0x80);var b=values(secondSource,0x80);
        try(var first=marked(firstSource,a);var second=marked(secondSource,b)) {
            var actor=first.captureActor();first.close();
            assertThrows(IllegalArgumentException.class,()->second.restoreActor(actor));
            assertTrue(first.closed());unchanged(secondSource,b,second);
        }
    }

    /** Deliberately strips Reader custody from real A component products. Regional variants are
     * explicit constructed negative metadata; no file/whole-disc authentication is claimed for them. */
    private static CdStagePlayerSource legacySource(CdReleaseVariant variant)throws Exception {
        try(var media=TestCdCczProgrammeData.open()) {
            var actual=CdR11aAssetLoader.load(media,CdReleaseVariant.USA,CdStagePayload.R11A.stage());
            var image=CdProgrammeReader.open(media,CdStagePayload.R11A.stage()).retainedOriginalR11Image();
            var id=actual.identity();var identity=new CdR11aAssets.Identity(variant,id.stage(),id.file(),id.domain(),id.mmdSha256(),id.observedWholeDiscSha256());
            // Explicit legacy-format control from genuine borrowed bytes. Reader terrain has
            // no legacy Shape admission; changing its metadata would bypass the tested boundary.
            var terrain=CdR11aTerrain.decode(actual.decoded(CdR11aAsset.LEVEL_BLOCKS).copy(),
                    actual.decoded(CdR11aAsset.ART_LEVEL_TILES).copy(),actual.view(CdR11aAsset.LEVEL_CHUNKS).bytes().copy(),
                    actual.view(CdR11aAsset.LAYOUT_FG).bytes().copy(),actual.view(CdR11aAsset.LAYOUT_BG).bytes().copy(),
                    actual.view(CdR11aAsset.LEVEL_COLLISION).bytes().copy(),actual.view(CdR11aAsset.COL_ANGLE_MAP).bytes().copy(),
                    actual.view(CdR11aAsset.COL_HEIGHT_MAP).bytes().copy(),actual.view(CdR11aAsset.COL_WIDTH_MAP).bytes().copy());
            var legacy=new CdR11aAssets(CdR11aProfile.forStage(variant,id.stage()),identity,image,
                    actual.blocksDecode(),actual.levelTilesDecode(),terrain,actual.loadPlan(),actual.placements(),actual.sonic(),actual.player(),actual.shield());
            return CdStagePlayerSource.fromPresent(legacy);
        }
    }
}
