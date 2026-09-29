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
class TestLrzRockCrusherResources {
    @Test void productionCrusherSubmitsOnlyAfterTheCameraGate() {
        HeadlessTestFixture.builder().withZoneAndAct(9, 0).build();
        var services = TestEnvironment.objectServices();
        var camera = services.camera();
        camera.setX((short) 0xD00); camera.setY((short) 0x600);
        var crusher = new LrzRockCrusherObjectInstance(new com.openggf.level.objects.ObjectSpawn(
                0xF40, 0x780, 0x9C, 0, 0, false, 0));
        crusher.setServices(services);
        var physical = services.kosinskiModuleQueue();
        crusher.update(0, null);
        assertTrue(physical.queuedArchives().stream().noneMatch(a -> a.archiveAddress() == 0x16F928));
        camera.setX((short) 0xE00);
        crusher.update(1, null);
        crusher.update(2, null);
        assertEquals(1, physical.queuedArchives().stream()
                .filter(a -> a.archiveAddress() == 0x16F928).count());
    }

    @Test void nativeQueueUploadsEveryPixelAndClaimsItsOwnPreparedJob() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(9,0).build();
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
        var state = new LrzRockCrusherArtState(); state.submit(services);
        long ordinal = state.captureRewindStateValue().ordinal();
        assertTrue(ordinal >= 0);
        var handle = timing.pendingHandle(HardwareWorkKind.KOS_MODULE_QUEUE,ordinal).orElseThrow();
        var descriptor = coordinator.moduleQueue().descriptor(handle);
        assertEquals(0x16F928, descriptor.sourceAddress());
        assertEquals(0xA5C0, descriptor.destinationAddress());
        assertEquals(rom.read16BitAddr(0x16F928), descriptor.destinationLength());
        assertEquals(0x16F928,physical.queuedArchives().getFirst().archiveAddress());
        assertEquals(0xA5C0,physical.activeDestinationVramBytes());
        var saved = state.captureRewindStateValue();
        var physicalBefore = physical.capture();
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
        state.restoreRewindStateValue(saved);
        assertEquals(ordinal, state.captureRewindStateValue().ordinal());
        state.restoreRewindStateValue(new LrzRockCrusherArtState.Value(-1));
        var sheet = GameServices.level().getObjectRenderManager().getSheet(Sonic3kObjectArtKeys.LRZ_ROCK_CRUSHER);
        assertNotNull(sheet);
        assertEquals(3, sheet.getFrameCount());
        for (int frame = 0; frame < 3; frame++) assertEquals(3, sheet.getFrame(frame).pieces().size());
        assertEquals(1, sheet.getPaletteIndex());
        byte[] expected = KosinskiReader.decompressModuled(rom.readBytes(0x16F928,0x10000),0);
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                physical.restore(physicalBefore);
                assertFalse(physical.isIdle());
                for (int pass = 0; pass < 100 && !physical.isIdle(); pass++) physical.processNativeFrame();
                assertTrue(physical.isIdle());
            }
            for (int i=0; i<expected.length; i++) {
                var tile = level.getPattern(0x52E+i/32); int pixel=(i%32)*2;
                assertSame(tile, sheet.getPatterns()[i/32], "renderer must follow uploaded crusher art");
                assertEquals((expected[i]>>>4)&15,tile.getPixel(pixel%8,pixel/8));
                assertEquals(expected[i]&15,tile.getPixel((pixel+1)%8,(pixel+1)/8));
            }
        }
    }
}
