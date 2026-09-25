package com.openggf.game.sonic3k.objects;

import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkKind;
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
class TestDezEndBossResources {
    @Test void allFortyFramesFitTheDedicatedRomArchive() throws Exception {
        var services = TestEnvironment.objectServices();
        var rom = services.rom();
        byte[] art = KosinskiReader.decompressModuled(rom.readBytes(0x181002,0x10000),0);
        assertEquals(0x1B80, art.length);
        var frames = S3kSpriteDataLoader.loadMappingFrames(services.romReader(),0x185B82);
        assertEquals(40, frames.size());
        assertEquals(12,frames.getFirst().pieces().size());
        for (var frame : frames) for (var piece : frame.pieces()) {
            assertTrue(piece.tileIndex()+piece.widthTiles()*piece.heightTiles() <= art.length/32);
        }
        var entry = Sonic3kPlcArtRegistry.getPlan(11,1).levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.DEZ_END_BOSS)).findFirst().orElseThrow();
        assertEquals(Sonic3kConstants.MAP_DEZ_END_BOSS_ADDR, entry.mappingAddr());
        assertEquals(0x38A,entry.artTileBase());
    }

    @Test void nativeQueueUploadsEveryPixelAndClaimsItsOwnPreparedJob() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(11,1).build();
        var level = GameServices.level().getCurrentLevel();
        var rom = TestEnvironment.objectServices().rom();
        var timing = new HardwareTimingService();
        var coordinator = new S3kRuntimeArtCoordinator(timing);
        var physical = new KosinskiModuleQueue();
        var services = mock(ObjectServices.class);
        when(services.rom()).thenReturn(rom);
        when(services.currentLevel()).thenReturn(level);
        when(services.levelManager()).thenReturn(GameServices.level());
        when(services.kosinskiModuleQueue()).thenReturn(physical);
        when(services.runtimeArtCoordinator()).thenReturn(coordinator);
        when(services.hardwareTiming()).thenReturn(timing);
        var state = new DezEndBossArtState(); state.submit(services);
        long ordinal = state.captureRewindStateValue().ordinal();
        assertTrue(ordinal >= 0);
        var handle = timing.pendingHandle(HardwareWorkKind.KOS_MODULE_QUEUE,ordinal).orElseThrow();
        var descriptor = coordinator.moduleQueue().descriptor(handle);
        assertEquals(0x181002, descriptor.sourceAddress());
        assertEquals(0x7140, descriptor.destinationAddress());
        assertEquals(0x1B80, descriptor.destinationLength());
        assertEquals(0x181002,physical.queuedArchives().getFirst().archiveAddress());
        assertEquals(0x7140,physical.activeDestinationVramBytes());
        state.service(services);
        assertEquals(ordinal,state.captureRewindStateValue().ordinal(),"submission is not readiness");
        for (int pass=0; pass<10000 && (!physical.isIdle() || state.captureRewindStateValue().ordinal()>=0); pass++) {
            physical.processNativeFrame();
            coordinator.moduleQueue().prepareQueuedModuleBeforeVSync();
            coordinator.moduleQueue().beforeTimingService(HardwareServiceBoundary.PRE_MAIN_LOOP);
            coordinator.moduleQueue().processModuleQueueAfterObjects();
            state.service(services);
        }
        assertTrue(physical.isIdle());
        assertEquals(-1,state.captureRewindStateValue().ordinal());
        byte[] expected = KosinskiReader.decompressModuled(rom.readBytes(0x181002,0x10000),0);
        for (int i=0; i<expected.length; i++) {
            var tile = level.getPattern(0x38A+i/32); int pixel=(i%32)*2;
            assertEquals((expected[i]>>>4)&15,tile.getPixel(pixel%8,pixel/8));
            assertEquals(expected[i]&15,tile.getPixel((pixel+1)%8,(pixel+1)/8));
        }
    }
}
