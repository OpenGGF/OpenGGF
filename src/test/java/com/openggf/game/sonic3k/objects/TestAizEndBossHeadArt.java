package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingJob;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.tests.HardwareBoundaryPump;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@RequiresRom(SonicGame.SONIC_3K)
class TestAizEndBossHeadArt {
    @ParameterizedTest
    @ValueSource(strings = {"knuckles", "sonic", "tails"})
    void nativeHeadInitSubmitsOnlyForKnucklesAndReplaysTheSameQueueLifetime(String character) {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        try {
            boolean knuckles = character.equals("knuckles");
            short cameraX = (short) (knuckles ? 0x4100 : 0x4880);
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1)
                    .startPosition(cameraX, (short) 0x015A).startPositionIsCentre().build();
            assertEquals(CharacterKey.parsePersisted(character), fixture.sprite().characterKey());
            var objects = GameServices.level().getObjectManager();
            var physical = objects.getObjectServices().kosinskiModuleQueue();
            var registry = fixture.gameplayMode().getRewindRegistry();
            fixture.camera().setX(cameraX);
            objects.update(cameraX, fixture.sprite(), List.of(), 0, false);
            if (objects.activeObjectsOfType(AizEndBossInstance.class).isEmpty()) {
                int bossY = knuckles ? 0x0640 : 0x015A;
                objects.createDynamicObject(() -> new AizEndBossInstance(
                        new ObjectSpawn(cameraX + 0x60, bossY, 0x92, 0, 0, false, bossY)));
            }
            serviceArtTail(physical);
            for (int frame = 1; frame < 140; frame++) {
                fixture.camera().setX(cameraX);
                HardwareBoundaryPump.service(HardwareServiceBoundary.VINT_SERVICE);
                objects.update(cameraX, fixture.sprite(), List.of(), frame, false);
                if (objects.activeObjectsOfType(AizEndBossShipChild.class).isEmpty()) {
                    assertTrue(headJobs().isEmpty(), "No head job before the ship initializes");
                    serviceArtTail(physical);
                    continue;
                }

                var jobs = headJobs();
                assertEquals(knuckles ? 1 : 0, jobs.size(),
                        "Obj_RobotnikHeadInit runs in the ship allocation pass");
                var ship = objects.activeObjectsOfType(AizEndBossShipChild.class).getFirst();
                var heads = objects.activeObjectsOfType(AizEndBossHeadChild.class);
                assertEquals(1, heads.size());
                assertTrue(heads.getFirst().getSlotIndex() > ship.getSlotIndex());
                var pending = physical.queuedArchives();
                if (knuckles) {
                    var submitted = jobs.getFirst();
                    assertEquals(HardwareWorkKind.KOS_MODULE_QUEUE, submitted.kind());
                    assertEquals(0xA5C0, submitted.destinationAddress());
                    assertFalse(submitted.claimed());
                    assertEquals(1, pending.stream().filter(archive ->
                            archive.archiveAddress() == 0x15FDDC).count());
                }

                var captured = registry.capture();
                var handles = jobs.stream().map(HardwareTimingJob.Snapshot::handle).toList();
                List<QueueStep> forward = null;
                for (int replay = 0; replay < 3; replay++) {
                    if (replay > 0) {
                        registry.restore(captured);
                        assertEquals(pending, physical.queuedArchives());
                        assertEquals(handles,
                                headJobs().stream().map(HardwareTimingJob.Snapshot::handle).toList());
                    }
                    List<QueueStep> steps = new ArrayList<>();
                    for (int step = 0; step < 12; step++) {
                        serviceArtTail(physical);
                        HardwareBoundaryPump.service(HardwareServiceBoundary.VINT_SERVICE);
                        objects.update(cameraX, fixture.sprite(), List.of(), frame + step + 1, false);
                        var active = headJobs();
                        assertEquals(handles,
                                active.stream().map(HardwareTimingJob.Snapshot::handle).toList(),
                                "The initialized head retains the original submission");
                        steps.add(new QueueStep(physical.queuedArchives(),
                                active.stream().allMatch(HardwareTimingJob.Snapshot::claimed)));
                    }
                    assertTrue(steps.getLast().claimed(), "The global owner claims the completed job");
                    assertTrue(physical.queuedArchives().stream()
                            .noneMatch(archive -> archive.archiveAddress() == 0x15FDDC));
                    if (replay == 0) {
                        forward = steps;
                    } else {
                        assertEquals(forward, steps, "Queue retirement must replay at the same boundary");
                    }
                }
                return;
            }
            fail("The registered boss did not create its ship");
        } finally {
            config.clearSessionOverrides();
        }
    }

    private static void serviceArtTail(KosinskiModuleQueue physical) {
        HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
        HardwareBoundaryPump.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        // The physical FIFO also advances in the LevelFrameStep loop tail.
        physical.processNativeFrame();
    }

    private record QueueStep(List<KosinskiModuleQueue.ArchiveState> archives, boolean claimed) {}

    private static List<HardwareTimingJob.Snapshot> headJobs() {
        return GameServices.hardwareTiming().capture().jobs().stream()
                .filter(job -> job.romSourceAddress() == 0x15FDDC).toList();
    }
}
