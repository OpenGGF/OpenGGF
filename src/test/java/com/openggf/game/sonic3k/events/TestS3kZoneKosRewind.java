package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.schema.ZoneEventSchemaSidecar;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingSnapshot;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.sonic3k.resources.S3kKosDecompressionQueueSnapshot;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kZoneKosRewind {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.hardwareTiming().resetForMissingSnapshot();
    }

    @Test
    void aizSidecarRestoresOrdinalsAndRebindsOriginalHandles() throws Exception {
        var timing = GameServices.hardwareTiming();
        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(1);
        invoke(events, "queueBattleshipKosArt");

        List<HardwareWorkHandle> originalHandles = timing.pendingHandles();
        assertEquals(List.of(0L, 1L),
                originalHandles.stream().map(HardwareWorkHandle::ordinal).toList());
        byte[] eventSnapshot = ZoneEventSchemaSidecar.capture(events);
        HardwareTimingSnapshot timingSnapshot = timing.capture();

        timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        timing.service(HardwareServiceBoundary.POST_OBJECTS);
        events.init(1);

        timing.restore(timingSnapshot);
        ZoneEventSchemaSidecar.restore(events, eventSnapshot);
        events.discardHardwareWorkFacadesAfterRewind();
        events.rebindHardwareWorkAfterRewind();

        assertEquals(0L, longField(events, "battleshipTerrainArtOrdinal"));
        assertEquals(1L, longField(events, "battleshipObjectArtOrdinal"));
        assertEquals(originalHandles.get(0), field(events, "battleshipTerrainArtHandle"));
        assertEquals(originalHandles.get(1), field(events, "battleshipObjectArtHandle"));
        assertEquals(originalHandles, timing.pendingHandles());
        assertEquals(2L, nextKosOrdinal(timing.capture()));

        events.update(1, 1);
        assertEquals(2L, nextKosOrdinal(timing.capture()),
                "AIZ owner restore must poll the original jobs, not resubmit them");
    }

    @Test
    void aizMainLevelSidecarRebindsDirectAndModuleHandlesWithoutResubmission()
            throws Exception {
        var timing = GameServices.hardwareTiming();
        var directQueue = S3kRuntimeArtCoordinator.current().directQueue();
        Sonic3kAIZEvents events =
                new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        GameServices.camera().setX((short) 0x1400);
        events.update(0, 0);

        HardwareWorkHandle directHandle = timing.pendingHandles().stream()
                .filter(handle -> handle.kind()
                        == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE)
                .findFirst().orElseThrow();
        HardwareWorkHandle moduleHandle = timing.pendingHandles().stream()
                .filter(handle -> handle.kind()
                        == HardwareWorkKind.KOS_MODULE_QUEUE)
                .findFirst().orElseThrow();
        byte[] eventSnapshot = ZoneEventSchemaSidecar.capture(events);
        HardwareTimingSnapshot timingSnapshot = timing.capture();
        S3kKosDecompressionQueueSnapshot directSnapshot =
                directQueue.capture();

        timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        directQueue.afterTimingService(HardwareServiceBoundary.PRE_MAIN_LOOP);
        events.init(0);

        timing.restore(timingSnapshot);
        directQueue.restore(directSnapshot);
        ZoneEventSchemaSidecar.restore(events, eventSnapshot);
        events.discardHardwareWorkFacadesAfterRewind();
        events.rebindHardwareWorkAfterRewind();

        assertEquals(directHandle, field(events, "mainLevelBlockHandle"));
        assertEquals(moduleHandle, field(events, "mainLevelArtHandle"));
        assertEquals(List.of(directHandle, moduleHandle),
                timing.pendingHandles());
        assertEquals(nextOrdinal(
                        timingSnapshot,
                        HardwareWorkKind.KOS_DECOMPRESSION_QUEUE),
                nextOrdinal(
                        timing.capture(),
                        HardwareWorkKind.KOS_DECOMPRESSION_QUEUE));
        assertEquals(nextOrdinal(
                        timingSnapshot,
                        HardwareWorkKind.KOS_MODULE_QUEUE),
                nextOrdinal(
                        timing.capture(),
                        HardwareWorkKind.KOS_MODULE_QUEUE));
    }

    @Test
    void aizMainLevelSidecarRebindsReadyUnclaimedHandlesAfterBothBoundaries()
            throws Exception {
        var timing = GameServices.hardwareTiming();
        var directQueue = S3kRuntimeArtCoordinator.current().directQueue();
        var moduleQueue = S3kRuntimeArtCoordinator.current().moduleQueue();
        Sonic3kAIZEvents events =
                new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        GameServices.camera().setX((short) 0x1400);
        events.update(0, 0);

        HardwareWorkHandle directHandle = timing.pendingHandles().stream()
                .filter(handle -> handle.kind()
                        == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE)
                .findFirst().orElseThrow();
        HardwareWorkHandle moduleHandle = timing.pendingHandles().stream()
                .filter(handle -> handle.kind()
                        == HardwareWorkKind.KOS_MODULE_QUEUE)
                .findFirst().orElseThrow();
        int boundaries = 0;
        while (!timing.isReady(moduleHandle) && boundaries++ < 100_000) {
            timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            directQueue.afterTimingService(
                    HardwareServiceBoundary.PRE_MAIN_LOOP);
            timing.service(HardwareServiceBoundary.POST_OBJECTS);
            directQueue.afterTimingService(
                    HardwareServiceBoundary.POST_OBJECTS);
            moduleQueue.afterTimingService(
                    HardwareServiceBoundary.POST_OBJECTS);
        }
        assertTrue(timing.isReady(directHandle),
                "ordinary stream must be ready after its decisive PRE boundary");
        assertTrue(timing.isReady(moduleHandle),
                "KosM parent must be ready after its decisive POST boundary");

        byte[] eventSnapshot = ZoneEventSchemaSidecar.capture(events);
        HardwareTimingSnapshot timingSnapshot = timing.capture();
        S3kKosDecompressionQueueSnapshot directSnapshot =
                directQueue.capture();

        events.init(0);

        timing.restore(timingSnapshot);
        directQueue.restore(directSnapshot);
        ZoneEventSchemaSidecar.restore(events, eventSnapshot);
        events.discardHardwareWorkFacadesAfterRewind();
        events.rebindHardwareWorkAfterRewind();

        assertEquals(directHandle, field(events, "mainLevelBlockHandle"));
        assertEquals(moduleHandle, field(events, "mainLevelArtHandle"));
        assertTrue(timing.isReady(directHandle));
        assertTrue(timing.isReady(moduleHandle));
        assertEquals(List.of(directHandle, moduleHandle),
                timing.pendingHandles());
        assertEquals(nextOrdinal(
                        timingSnapshot,
                        HardwareWorkKind.KOS_DECOMPRESSION_QUEUE),
                nextOrdinal(
                        timing.capture(),
                        HardwareWorkKind.KOS_DECOMPRESSION_QUEUE));
        assertEquals(nextOrdinal(
                        timingSnapshot,
                        HardwareWorkKind.KOS_MODULE_QUEUE),
                nextOrdinal(
                        timing.capture(),
                        HardwareWorkKind.KOS_MODULE_QUEUE));
    }

    @Test
    void iczSidecarRebindsTwoDirectHandlesAndTransferredModuleParent()
            throws Exception {
        var timing = GameServices.hardwareTiming();
        var directQueue = S3kRuntimeArtCoordinator.current().directQueue();
        Sonic3kICZEvents events = new Sonic3kICZEvents();
        events.init(0);
        GameServices.camera().setX((short) 0x6900);
        events.forceAct1NormalBackgroundRoutineForTest();
        events.update(0, 0);

        List<HardwareWorkHandle> directHandles = timing.pendingHandles().stream()
                .filter(handle -> handle.kind()
                        == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE)
                .toList();
        HardwareWorkHandle moduleHandle = timing.pendingHandles().stream()
                .filter(handle -> handle.kind()
                        == HardwareWorkKind.KOS_MODULE_QUEUE)
                .findFirst().orElseThrow();
        assertEquals(2, directHandles.size());
        byte[] eventSnapshot = ZoneEventSchemaSidecar.capture(events);
        HardwareTimingSnapshot timingSnapshot = timing.capture();
        S3kKosDecompressionQueueSnapshot directSnapshot =
                directQueue.capture();

        timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        directQueue.afterTimingService(HardwareServiceBoundary.PRE_MAIN_LOOP);
        events.init(0);

        timing.restore(timingSnapshot);
        directQueue.restore(directSnapshot);
        ZoneEventSchemaSidecar.restore(events, eventSnapshot);
        events.discardHardwareWorkFacadesAfterRewind();
        events.rebindHardwareWorkAfterRewind();

        assertEquals(directHandles.get(0),
                field(events, "act2TransitionChunkHandle"));
        assertEquals(directHandles.get(1),
                field(events, "act2TransitionBlockHandle"));
        assertEquals(moduleHandle,
                field(events, "act2TransitionArtHandle"));
        assertEquals(2L, nextOrdinal(
                timing.capture(), HardwareWorkKind.KOS_DECOMPRESSION_QUEUE));
        assertEquals(1L, nextOrdinal(
                timing.capture(), HardwareWorkKind.KOS_MODULE_QUEUE));
    }

    @Test
    void hczSidecarRestoresOrdinalAndRebindsOriginalHandle() throws Exception {
        var timing = GameServices.hardwareTiming();
        Sonic3kHCZEvents events = new Sonic3kHCZEvents(() -> 0);
        events.init(0);
        events.setEventsFg5(true);
        timing.service(HardwareServiceBoundary.POST_OBJECTS);
        events.update(0, 0);

        List<HardwareWorkHandle> originalHandles = timing.pendingHandles();
        assertEquals(List.of(0L),
                originalHandles.stream().map(HardwareWorkHandle::ordinal).toList());
        byte[] eventSnapshot = ZoneEventSchemaSidecar.capture(events);
        HardwareTimingSnapshot timingSnapshot = timing.capture();

        timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        timing.service(HardwareServiceBoundary.POST_OBJECTS);
        events.init(0);

        timing.restore(timingSnapshot);
        ZoneEventSchemaSidecar.restore(events, eventSnapshot);
        events.discardHardwareWorkFacadesAfterRewind();
        events.rebindHardwareWorkAfterRewind();

        assertEquals(0L, longField(events, "transitionKosOrdinal"));
        assertEquals(originalHandles.getFirst(), field(events, "transitionKosHandle"));
        assertEquals(originalHandles, timing.pendingHandles());
        assertEquals(1L, nextKosOrdinal(timing.capture()));

        events.update(0, 1);
        assertEquals(1L, nextKosOrdinal(timing.capture()),
                "HCZ owner restore must poll the original job, not resubmit it");
    }

    private static void invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static Object field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static long longField(Object target, String fieldName) throws Exception {
        return (long) field(target, fieldName);
    }

    private static long nextKosOrdinal(HardwareTimingSnapshot snapshot) {
        return nextOrdinal(snapshot, HardwareWorkKind.KOS_MODULE_QUEUE);
    }

    private static long nextOrdinal(
            HardwareTimingSnapshot snapshot, HardwareWorkKind kind) {
        return snapshot.nextOrdinals().getOrDefault(kind, 0L);
    }
}
