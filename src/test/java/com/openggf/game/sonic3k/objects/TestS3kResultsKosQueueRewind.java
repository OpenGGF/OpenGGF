package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingSnapshot;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kResultsKosQueueRewind {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void createPollsPendingArtAndRewindRebindsWithoutResubmission() throws Exception {
        ObjectServices services = TestEnvironment.objectServices();
        var timing = services.hardwareTiming();
        S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                services,
                () -> new S3kResultsScreenObjectInstance(
                        PlayerCharacter.SONIC_AND_TAILS, 0));

        List<HardwareWorkHandle> submitted = timing.pendingHandles();
        assertEquals(List.of(0L, 1L, 2L),
                submitted.stream().map(HardwareWorkHandle::ordinal).toList());
        assertCreateHasNotRun(results);

        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        results.update(0, player);
        assertCreateHasNotRun(results);
        assertEquals(submitted, timing.pendingHandles());

        HardwareTimingSnapshot pendingSnapshot = timing.capture();

        for (int frame = 0;
                frame < 100_000
                        && timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE) > 0;
                frame++) {
            timing.service(HardwareServiceBoundary.VINT_SERVICE);
            timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            timing.service(HardwareServiceBoundary.POST_OBJECTS);
        }
        assertEquals(0, timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
        HardwareTimingSnapshot readySnapshot = timing.capture();

        timing.restore(pendingSnapshot);
        S3kResultsScreenObjectInstance pendingRestored =
                recreateWithCapturedOrdinals(results, services);
        assertEquals(submitted, timing.pendingHandles());
        assertEquals(3L, nextKosOrdinal(timing.capture()));
        pendingRestored.update(1, player);
        assertCreateHasNotRun(pendingRestored);
        assertEquals(3L, nextKosOrdinal(timing.capture()),
                "pending restore must bind the original three ordinals");

        timing.restore(readySnapshot);
        S3kResultsScreenObjectInstance readyRestored =
                recreateWithCapturedOrdinals(results, services);
        assertEquals(submitted, timing.pendingHandles(),
                "ready-but-unclaimed jobs retain their original identities");
        readyRestored.update(2, player);

        assertTrue((boolean) field(readyRestored, "resultsChildrenCreated"));
        assertEquals(0, field(readyRestored, "stateTimer"),
                "native zero-duration slide dispatch advances state and resets its timer");
        assertEquals(1, field(readyRestored, "totalFrames"));
        assertTrue(timing.pendingHandles().isEmpty());
        assertEquals(3L, nextKosOrdinal(timing.capture()),
                "ready restore must claim, never submit replacement art");
    }

    private static S3kResultsScreenObjectInstance recreateWithCapturedOrdinals(
            S3kResultsScreenObjectInstance source,
            ObjectServices services) throws Exception {
        S3kResultsScreenObjectInstance restored =
                (S3kResultsScreenObjectInstance) source.recreateForRewind(
                        new RewindRecreateContext(null, null, services));
        restored.setServices(services);
        for (String ordinalField : List.of(
                "resultsGeneralArtOrdinal",
                "resultsNumberArtOrdinal",
                "resultsCharacterArtOrdinal")) {
            setField(restored, ordinalField, field(source, ordinalField));
        }
        setField(restored, "mappingFrames", field(source, "mappingFrames"));
        return restored;
    }

    private static void assertCreateHasNotRun(
            S3kResultsScreenObjectInstance results) throws Exception {
        assertFalse((boolean) field(results, "resultsChildrenCreated"));
        assertEquals(0, field(results, "stateTimer"));
        assertEquals(0, field(results, "totalFrames"));
        // Children are tracked by a remaining count rather than a slot array, so
        // "Create has not run" is an untouched count plus the unset created flag.
        assertEquals(0, field(results, "childrenRemaining"),
                "Obj_LevelResultsCreate must return while Kos_modules_left is nonzero");
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static long nextKosOrdinal(HardwareTimingSnapshot snapshot) {
        return snapshot.nextOrdinals().getOrDefault(
                HardwareWorkKind.KOS_MODULE_QUEUE, 0L);
    }
}
