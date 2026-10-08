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
class TestDezMinibossResources {
    @Test void allThirtyNineFramesFitTheDedicatedRomArchive() throws Exception {
        var services = TestEnvironment.objectServices();
        var rom = services.rom();
        byte[] art = KosinskiReader.decompressModuled(rom.readBytes(0x1805A0,0x10000),0);
        assertEquals(0x1620, art.length);
        var frames = S3kSpriteDataLoader.loadMappingFrames(services.romReader(),0x184FBA);
        assertEquals(39, frames.size());
        assertEquals(6,frames.getFirst().pieces().size());
        for (var frame : frames) for (var piece : frame.pieces()) {
            assertTrue(piece.tileIndex()+piece.widthTiles()*piece.heightTiles() <= art.length/32);
        }
        var entry = Sonic3kPlcArtRegistry.getPlan(11,0).levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.DEZ_MINIBOSS)).findFirst().orElseThrow();
        assertEquals(Sonic3kConstants.MAP_DEZ_MINIBOSS_ADDR, entry.mappingAddr());
        assertEquals(0x400,entry.artTileBase());
    }

    @Test void nativeQueueUploadsEveryPixelAndClaimsItsOwnPreparedJob() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(11,0).build();
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
        var state = new DezMinibossArtState(); state.submit(services);
        long ordinal = state.captureRewindStateValue().ordinal();
        assertTrue(ordinal >= 0);
        var handle = timing.pendingHandle(HardwareWorkKind.KOS_MODULE_QUEUE,ordinal).orElseThrow();
        var descriptor = coordinator.moduleQueue().descriptor(handle);
        assertEquals(0x1805A0, descriptor.sourceAddress());
        assertEquals(0x8000, descriptor.destinationAddress());
        assertEquals(0x1620, descriptor.destinationLength());
        assertEquals(0x1805A0,physical.queuedArchives().getFirst().archiveAddress());
        assertEquals(0x8000,physical.activeDestinationVramBytes());
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
        byte[] expected = KosinskiReader.decompressModuled(rom.readBytes(0x1805A0,0x10000),0);
        for (int i=0; i<expected.length; i++) {
            var tile = level.getPattern(0x400+i/32); int pixel=(i%32)*2;
            assertEquals((expected[i]>>>4)&15,tile.getPixel(pixel%8,pixel/8));
            assertEquals(expected[i]&15,tile.getPixel((pixel+1)%8,(pixel+1)/8));
        }
    }
}
