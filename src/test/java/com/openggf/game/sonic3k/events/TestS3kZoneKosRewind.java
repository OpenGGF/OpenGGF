package com.openggf.game.sonic3k.events;

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
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        return snapshot.nextOrdinals().getOrDefault(
                HardwareWorkKind.KOS_MODULE_QUEUE, 0L);
    }
}
