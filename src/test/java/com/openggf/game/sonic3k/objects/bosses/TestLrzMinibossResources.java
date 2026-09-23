package com.openggf.game.sonic3k.objects.bosses;

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
class TestLrzMinibossResources {
    @Test void productionBossSubmitsOnceAtRoutineTwoAndKeepsTheNativeDelay() {
        HeadlessTestFixture.builder().withZoneAndAct(9, 0).build();
        var services = TestEnvironment.objectServices();
        var camera = services.camera();
        camera.setX((short) 0x2C00); camera.setY((short) 0x710);
        var boss = new LrzMinibossInstance(new com.openggf.level.objects.ObjectSpawn(
                0x2CA0, 0x7C0, 0x9D, 0, 0, false, 0));
        boss.setServices(services);
        for (int frame = 0; frame < 600 && !boss.isArenaGateComplete(); frame++) boss.update(frame, null);
        assertTrue(boss.isArenaGateComplete());
        boss.update(600, null);
        assertEquals(2, boss.getRoutineByte());
        var physical = services.kosinskiModuleQueue();
        long before = physical.queuedArchives().stream().filter(a -> a.archiveAddress() == 0x16FCDA).count();
        boss.update(601, null);
        assertEquals(4, boss.getRoutineByte());
        assertEquals(before + 1, physical.queuedArchives().stream()
                .filter(a -> a.archiveAddress() == 0x16FCDA).count());
        for (int pass = 0; pass < 47; pass++) boss.update(602 + pass, null);
        assertEquals(4, boss.getRoutineByte());
        boss.update(649, null);
        assertEquals(6, boss.getRoutineByte());
        assertEquals(before + 1, physical.queuedArchives().stream()
                .filter(a -> a.archiveAddress() == 0x16FCDA).count());
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
        var state = new LrzMinibossArtState(); state.submit(services);
        long ordinal = state.captureRewindStateValue().ordinal();
        assertTrue(ordinal >= 0);
        var handle = timing.pendingHandle(HardwareWorkKind.KOS_MODULE_QUEUE,ordinal).orElseThrow();
        var descriptor = coordinator.moduleQueue().descriptor(handle);
        assertEquals(0x16FCDA, descriptor.sourceAddress());
        assertEquals(0x7F60, descriptor.destinationAddress());
        assertEquals(rom.read16BitAddr(0x16FCDA), descriptor.destinationLength());
        assertEquals(0x16FCDA,physical.queuedArchives().getFirst().archiveAddress());
        assertEquals(0x7F60,physical.activeDestinationVramBytes());
        var saved = state.captureRewindStateValue();
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
        state.restoreRewindStateValue(new LrzMinibossArtState.Value(-1));
        byte[] expected = KosinskiReader.decompressModuled(rom.readBytes(0x16FCDA,0x10000),0);
        for (int i=0; i<expected.length; i++) {
            var tile = level.getPattern(0x3FB+i/32); int pixel=(i%32)*2;
            assertEquals((expected[i]>>>4)&15,tile.getPixel(pixel%8,pixel/8));
            assertEquals(expected[i]&15,tile.getPixel((pixel+1)%8,(pixel+1)/8));
        }
    }
}
