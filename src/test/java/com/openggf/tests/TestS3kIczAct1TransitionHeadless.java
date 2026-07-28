package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kICZEvents;
import com.openggf.game.sonic3k.resources.S3kKosRamDestinations;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingJob;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kIczAct1TransitionHeadless {

    @Test
    void act1OutdoorTransitionReloadsIcz2AndAppliesRomCameraBounds() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kICZEvents events = manager.getIczEvents();

        sonic.setCentreX((short) 0x6950);
        sonic.setCentreY((short) 0x0700);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x6900);
        camera.setY((short) 0x0600);
        camera.setMinX((short) 0);
        camera.setMaxX((short) 0x7000);
        camera.setMinY((short) -0x0100);
        camera.setMaxY((short) 0x0800);
        camera.setMaxYTarget((short) 0x0800);
        events.forceAct1NormalBackgroundRoutineForTest();

        manager.update();

        assertEquals(0, GameServices.level().getCurrentAct(),
                "ICZ1BGE_Normal queues the Act 2 archives before changing levels");
        var jobs = GameServices.hardwareTiming().capture().jobs();
        var directJobs = jobs.stream()
                .filter(job -> job.kind() == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE)
                .toList();
        var moduleJobs = jobs.stream()
                .filter(job -> job.kind() == HardwareWorkKind.KOS_MODULE_QUEUE)
                .toList();
        assertEquals(2, directJobs.size(),
                "ICZ1BGE_Normal must queue the secondary 128x128 and 16x16 streams");
        assertEquals(1, moduleJobs.size(),
                "ICZ1BGE_Normal must queue the secondary KosM archive");
        assertTrue(moduleJobs.getFirst().exportableAcrossSegment(),
                "the exact ICZ2 KosM parent must remain exportable across the structural act handoff");
        assertEquals(
                java.util.Set.of(
                        S3kKosRamDestinations.RAM_START + 0x0A00,
                        S3kKosRamDestinations.blockTableOffset(0x0408)),
                directJobs.stream()
                        .map(HardwareTimingJob.Snapshot::destinationAddress)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(0x0122 * 32, moduleJobs.getFirst().destinationAddress());

        service(HardwareServiceBoundary.POST_OBJECTS);
        int frames = 0;
        while (GameServices.s3kKosDecompressionQueue().decompressionsPending()
                && frames++ < 100_000) {
            service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            manager.update();
            if (GameServices.s3kKosDecompressionQueue().decompressionsPending()) {
                assertEquals(0, GameServices.level().getCurrentAct(),
                        "ICZ1BGE_Transition must wait on physical direct FIFO occupancy");
                service(HardwareServiceBoundary.POST_OBJECTS);
            }
        }

        assertTrue(events.isAct2TransitionRequested(),
                "ICZ1BGE_Transition must request the ICZ2 reload on the first direct-empty scan "
                        + "(docs/skdisasm/sonic3k.asm:110280-110323)");
        assertEquals(1, GameServices.level().getCurrentAct(),
                "ROM writes Current_zone_and_act=$0501 before Load_Level");
        Sonic3kICZEvents icz2Events = manager.getIczEvents();
        assertNotSame(events, icz2Events,
                "the seamless reload must transfer queue ownership to the new ICZ2 event owner");
        var beforePost = GameServices.hardwareTiming().capture().jobs();
        assertTrue(beforePost.stream()
                        .filter(job -> job.kind() == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE)
                        .filter(job -> job.destinationAddress()
                                != S3kKosRamDestinations.KOS_DECOMP_BUFFER)
                        .allMatch(HardwareTimingJob.Snapshot::claimed),
                "ICZ2 must publish and claim both direct payloads after the in-frame reload");
        assertFalse(beforePost.stream()
                        .filter(job -> job.kind() == HardwareWorkKind.KOS_MODULE_QUEUE)
                        .findFirst().orElseThrow().claimed(),
                "the KosM parent cannot publish before the later POST boundary");

        HardwareTimingJob.Snapshot parent;
        int moduleFrames = 0;
        do {
            service(HardwareServiceBoundary.POST_OBJECTS);
            parent = GameServices.hardwareTiming().capture().jobs().stream()
                    .filter(job -> job.kind() == HardwareWorkKind.KOS_MODULE_QUEUE)
                    .findFirst().orElseThrow();
            assertFalse(parent.claimed(),
                    "POST module work remains invisible to the gameplay consumer in the same frame");
            if (!parent.ready()) {
                service(HardwareServiceBoundary.PRE_MAIN_LOOP);
                manager.update();
            }
        } while (!parent.ready() && moduleFrames++ < 100_000);
        assertTrue(parent.ready(), "test setup must reach ICZ2 KosM parent retirement");
        manager.update();
        assertTrue(GameServices.hardwareTiming().capture().jobs().stream()
                        .filter(job -> job.kind() == HardwareWorkKind.KOS_MODULE_QUEUE)
                        .findFirst().orElseThrow().claimed(),
                "ICZ2 must publish the transferred KosM payload on the following scan");
        assertEquals(0x00D0, sonic.getCentreX() & 0xFFFF,
                "ICZ1BGE_Transition subtracts d0=$6880 from player x_pos");
        assertEquals(0x0800, sonic.getCentreY() & 0xFFFF,
                "ICZ1BGE_Transition subtracts d1=-$100 from player y_pos");
        assertEquals(0x0000, camera.getMinX() & 0xFFFF);
        assertEquals(0x7000, camera.getMaxX() & 0xFFFF);
        assertEquals(0x0000, camera.getMinY() & 0xFFFF);
        assertEquals(0x0B20, camera.getMaxY() & 0xFFFF);
        assertEquals(0x0B20, camera.getMaxYTarget() & 0xFFFF);
    }

    private static void service(HardwareServiceBoundary boundary) {
        GameServices.hardwareTiming().service(boundary);
        GameServices.s3kKosDecompressionQueue().afterTimingService(boundary);
        GameServices.s3kKosModuleQueue().afterTimingService(boundary);
    }
}
