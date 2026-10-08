package com.openggf.game.sonic3k.runtime;

import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalArtState {
    @Test void rootHandoffModulesKeepFifoOrderPixelsAndClaimOwnershipAcrossRestore() throws Exception {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(23,0).build();
        var rom=TestEnvironment.objectServices().rom(); var level=GameServices.level().getCurrentLevel();
        var timing=new HardwareTimingService(); var coordinator=new S3kRuntimeArtCoordinator(timing); var physical=new KosinskiModuleQueue();
        var services=mock(ObjectServices.class);
        when(services.rom()).thenReturn(rom); when(services.currentLevel()).thenReturn(level);
        when(services.levelManager()).thenReturn(GameServices.level()); when(services.kosinskiModuleQueue()).thenReturn(physical);
        when(services.runtimeArtCoordinator()).thenReturn(coordinator); when(services.hardwareTiming()).thenReturn(timing);
        var zone=new DezFinalBossZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
        // loc_8013A queues both archives immediately before Go_Delete_Sprite_2.
        zone.art().queueModule(services,0x1607D8,0x49D); zone.art().queueModule(services,0x182ED8,0x100);
        assertEquals(2,zone.art().pendingCount()); assertEquals(0x1607D8,physical.queuedArchives().getFirst().archiveAddress());
        zone.art().service(services); assertEquals(2,zone.art().pendingCount(),"submission does not imply readiness");
        var zoneSaved=zone.captureBytes(); var timingSaved=timing.capture(); var coordinationSaved=coordinator.capture(); var physicalSaved=physical.capture();
        int first=drain(zone.art(),services,physical,coordinator);
        assertPixels(rom,level,0x1607D8,0x49D); assertPixels(rom,level,0x182ED8,0x100);
        timing.restore(timingSaved); coordinator.restore(coordinationSaved); physical.restore(physicalSaved);
        var restored=new DezFinalBossZoneRuntimeState(PlayerCharacter.SONIC_ALONE); restored.restoreBytes(zoneSaved);
        assertEquals(2,restored.art().pendingCount()); assertEquals(first,drain(restored.art(),services,physical,coordinator));
        assertPixels(rom,level,0x1607D8,0x49D); assertPixels(rom,level,0x182ED8,0x100);
    }
    private int drain(DezFinalArtState art,ObjectServices services,KosinskiModuleQueue physical,S3kRuntimeArtCoordinator coordinator) {
        int pass=0;
        for(;pass<10000 && (!physical.isIdle() || art.pendingCount()!=0);pass++) {
            physical.processNativeFrame(); coordinator.moduleQueue().prepareQueuedModuleBeforeVSync();
            coordinator.moduleQueue().beforeTimingService(HardwareServiceBoundary.PRE_MAIN_LOOP);
            coordinator.moduleQueue().processModuleQueueAfterObjects(); art.service(services);
        }
        assertTrue(physical.isIdle()); assertEquals(0,art.pendingCount()); assertTrue(pass<10000); return pass;
    }
    private void assertPixels(com.openggf.data.Rom rom,com.openggf.level.Level level,int address,int base) throws Exception {
        byte[] expected=KosinskiReader.decompressModuled(rom.readBytes(address,0x10000),0);
        for(int i=0;i<expected.length;i++) {
            var tile=level.getPattern(base+i/32); int pixel=(i%32)*2;
            assertEquals((expected[i]>>>4)&15,tile.getPixel(pixel%8,pixel/8));
            assertEquals(expected[i]&15,tile.getPixel((pixel+1)%8,(pixel+1)/8));
        }
    }
}
