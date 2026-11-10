package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Tests first. Constructed component data never grants a World/Frame scope.
 * Actual-source cases use the retained user-media pipeline; all Java UNRUN by author. */
class TestCdOriginalWorldBindings extends TestCdOriginalR11LoadingWorld {
    @Test void sharedArtContractUsesTheActualRuntimeTransferAndPreservesTheResidentTail() {
        byte[] resident=filled(0x300,0x61), cpu=filled(0x300,0x37);
        var complete=new CdPlayerArtRuntime.State(cpu,resident,17,false);
        var request=new CdPlayerArtRuntime.DmaRequest(CdAssetBytes.copyOf(Arrays.copyOf(resident,0x2E0)));
        CdOriginalWorldBindings.requirePlayerArt(request,complete);
        resident[0x2E0]^=1;
        CdOriginalWorldBindings.requirePlayerArt(request,new CdPlayerArtRuntime.State(cpu,resident,17,false));
        assertArrayEquals(filled(0x300,0x61),complete.residentBank().copy());
        assertArrayEquals(filled(0x300,0x37),complete.cpuBuffer().copy());
    }

    @Test void mismatchingPlayerArtIsRejectedWithoutRewritingEitherCompleteWindow() {
        var complete=new CdPlayerArtRuntime.State(filled(0x300,0x37),filled(0x300,0x61),17,true);
        var request=new CdPlayerArtRuntime.DmaRequest(CdAssetBytes.copyOf(filled(0x2E0,0x62)));
        assertThrows(IllegalStateException.class,()->CdOriginalWorldBindings.requirePlayerArt(request,complete));
        assertArrayEquals(filled(0x300,0x61),complete.residentBank().copy());
        assertArrayEquals(filled(0x300,0x37),complete.cpuBuffer().copy());
        assertTrue(complete.dirty());assertEquals(17,complete.lastFrame());
    }

    @Test void actualR11FactoryRetainsExactSourceAndPlacementDataWithoutCreatingARuntime()throws Exception {
        var assets=TestCdOriginalBootPrefix.source().assets();var source=CdStagePlayerSource.fromPresent(assets);
        var art=CdR11aSectionArt.fromAssetsForLoading(assets);
        var binding=new CdR11OriginalWorldBindings(source,assets,art,assets.placements());
        assertSame(source,binding.playerSource());assertSame(assets.placements(),binding.placements());
        assertEquals(source.ordinaryZoneActWord(),binding.nativeZoneActWord());
        assertTrue(binding.startupObjects().owns(assets));
        assertNotNull(binding.objectAssets());
    }

    @Test void unrelatedAuthenticatedProgrammeSourceCannotSelectTheR11Scene()throws Exception {
        var data=TestCdOriginalBootPrefix.source();var assets=data.assets();
        var other=CdStagePlayerSource.fromR31aAssets(data.r31aAssets().orElseThrow());
        var art=CdR11aSectionArt.fromAssetsForLoading(assets);
        assertThrows(IllegalArgumentException.class,()->new CdR11OriginalWorldBindings(other,assets,art,assets.placements()));
    }

    @Test void paletteProjectionRetainsTheUntouchedSeventhCountersAndSharedKernelWrites()throws Exception {
        var assets=TestCdOriginalBootPrefix.source().assets();var source=CdStagePlayerSource.fromPresent(assets);
        var binding=new CdR11OriginalWorldBindings(source,assets,CdR11aSectionArt.fromAssetsForLoading(assets),assets.placements());
        var palette=CdAssetBytes.copyOf(filled(128,0x44));var timers=CdAssetBytes.copyOf(new byte[]{1,2,3,4,5,6,0x71});var steps=CdAssetBytes.copyOf(new byte[]{0,0,0,0,0,0,0x72});
        var expected=CdLevelPaletteCycle.pass(assets.paletteCycle().scripts(),new CdLevelPaletteCycle.State(palette,timers.slice(0,6),steps.slice(0,6)));
        var actual=binding.paletteCycle(palette,timers,steps);
        assertEquals(expected,actual.nativeResult());assertEquals(0x71,actual.timers7().unsignedByte(6));assertEquals(0x72,actual.steps7().unsignedByte(6));
        assertArrayEquals(expected.state().timers().copy(),actual.timers7().slice(0,6).copy());
        assertArrayEquals(expected.state().steps().copy(),actual.steps7().slice(0,6).copy());
        assertEquals(0x71,timers.unsignedByte(6));assertEquals(0x72,steps.unsignedByte(6));
    }

    @Test void extractedScrollUsesTheSharedForegroundAndActualR11BackgroundWithoutTouchingCopies()throws Exception {
        var assets=TestCdOriginalBootPrefix.source().assets();var binding=new CdR11OriginalWorldBindings(CdStagePlayerSource.fromPresent(assets),assets,CdR11aSectionArt.fromAssetsForLoading(assets),assets.placements());
        var foreground=new CdR11aForegroundScroll.State(0,0,16,16,0,0,0,0x1000,0,0,0x0100);
        var background=new CdR11aBackgroundScroll.State(0x11223344,0x55667788,0x12345678,0x23456789,0x3456789A,0x456789AB,bytes(8),0x1234,0x2345,0x3456,bytes(512),bytes(512),bytes(1024),0x76543210);
        var cameras=new CdR11aBackgroundPlane.CopiedCameras(new CdR11aBackgroundPlane.Camera(1,2),new CdR11aBackgroundPlane.Camera(3,4),new CdR11aBackgroundPlane.Camera(5,6));
        var copies=new SonicCdR31aLoadingVisit.Copies(0x11223344,0x55667788,cameras,0x4567,new CdR11aBackgroundPlane.FlagWords(1,2,3));
        var scroll=new SonicCdR31aLoadingVisit.Scroll(foreground,background,copies,0x7788,0x0020,0x1122);
        var input=new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(160,96,0,0,0,0),new CdPlayerMovement.Bounds(0,0x4000,0x1000,0),0x20,160,96,CdReleaseVariant.USA,0,320,224);
        var actual=binding.scroll(scroll,input,0,0,160,0);
        assertEquals(CdR11aForegroundScroll.stepForPeriodBackground(foreground,input,0),actual.foreground());
        assertSame(copies,actual.scroll().copies());assertSame(background,actual.scroll().background());
        assertEquals(0x7788,actual.scroll().foregroundXFractionWord());assertEquals(0x1122,actual.scroll().waterDeformWord());
    }

    @Test void genuineOriginalControllerStillPublishesAndReplaysTheSameClosedOrdinaryCut()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);
            for(int row=0;row<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;row++)step(loop,0);
            assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());step(loop,0);step(loop,8);
            var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            step(loop,8);var actor=module.originalR11EntryFrame().captureActor();var shared=module.originalR11EntryFrame().captureShared();var prefix=module.originalR11LoadingPrefix();var image=module.originalR11WorldImage().orElseThrow();var cpu=image.cpu().orElseThrow();
            registry.restore(full);step(loop,8);assertEquals(actor,module.originalR11EntryFrame().captureActor());CdCapturedValueAssertions.assertCapturedValueEquals(shared,module.originalR11EntryFrame().captureShared());assertPrefix(prefix,module.originalR11LoadingPrefix());assertArrayEquals(cpu.bytes(),module.originalR11WorldImage().orElseThrow().cpu().orElseThrow().bytes());assertEquals(cpu.provenance(),module.originalR11WorldImage().orElseThrow().cpu().orElseThrow().provenance());assertEquals(image.sounds(),module.originalR11WorldImage().orElseThrow().sounds());
            registry.restoreCourse(course);step(loop,8);assertEquals(actor,module.originalR11EntryFrame().captureActor());CdCapturedValueAssertions.assertCapturedValueEquals(shared,module.originalR11EntryFrame().captureShared());assertPrefix(prefix,module.originalR11LoadingPrefix());assertArrayEquals(cpu.bytes(),module.originalR11WorldImage().orElseThrow().cpu().orElseThrow().bytes());assertEquals(cpu.provenance(),module.originalR11WorldImage().orElseThrow().cpu().orElseThrow().provenance());assertEquals(image.sounds(),module.originalR11WorldImage().orElseThrow().sounds());
            assertFalse(module.originalR11EntryFrame().closed());assertTrue(module.originalR11FirstLoad().selection().inputs().selectedCpu().isPresent());
        }
    }
    private static void assertPrefix(SonicCdGameModule.R11LoadingPrefix expected,SonicCdGameModule.R11LoadingPrefix actual){
        CdCapturedValueAssertions.assertCapturedValueEquals(expected,actual);
    }
    private static byte[] filled(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return b;}
    private static CdAssetBytes bytes(int n){return CdAssetBytes.copyOf(filled(n,0x35));}
}
