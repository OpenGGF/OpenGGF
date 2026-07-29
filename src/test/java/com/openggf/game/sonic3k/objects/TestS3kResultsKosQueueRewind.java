package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingJob;
import com.openggf.game.timing.HardwareTimingSnapshot;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.PerObjectRewindSnapshot;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kResultsKosQueueRewind {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
    }

    @Test
    void createPollsPendingArtAndRewindRebindsWithoutResubmission() throws Exception {
        ObjectServices services = TestEnvironment.objectServices();
        var timing = services.hardwareTiming();
        S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                services,
                () -> new S3kResultsScreenObjectInstance(
                        PlayerCharacter.SONIC_AND_TAILS, 0));
        results.setServices(services);

        List<HardwareWorkHandle> submitted = timing.pendingHandles();
        assertEquals(List.of(0L, 1L, 2L),
                submitted.stream().map(HardwareWorkHandle::ordinal).toList());
        assertCreateHasNotRun(results);
        assertEquals(List.of(), readyHandles(timing, submitted),
                "results art must remain pending before the first hardware service boundary");

        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        results.update(0, player);
        assertCreateHasNotRun(results);
        assertEquals(submitted, timing.pendingHandles());

        RewindCaptureContext captureContext = rewindContextFor(player);
        HardwareTimingSnapshot pendingTimingSnapshot = timing.capture();
        PerObjectRewindSnapshot pendingObjectSnapshot = results.captureRewindState(captureContext);

        serviceResultsArtToReadiness(timing, submitted);
        assertEquals(0, timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
        assertEquals(submitted, readyHandles(timing, submitted),
                "every results-art handle must become ready in FIFO order at POST_OBJECTS");
        HardwareTimingSnapshot readyTimingSnapshot = timing.capture();
        PerObjectRewindSnapshot readyObjectSnapshot = results.captureRewindState(captureContext);

        RewindCaptureContext restoreContext = rewindContextFor(player);

        timing.restore(pendingTimingSnapshot);
        S3kResultsScreenObjectInstance pendingRestored =
                recreateWithCapturedRewindState(
                        results, pendingObjectSnapshot, services, restoreContext);
        assertEquals(submitted, timing.pendingHandles());
        assertEquals(3L, nextKosOrdinal(timing.capture()));
        pendingRestored.update(1, player);
        assertCreateHasNotRun(pendingRestored);
        assertEquals(3L, nextKosOrdinal(timing.capture()),
                "pending restore must bind the original three ordinals");

        timing.restore(readyTimingSnapshot);
        S3kResultsScreenObjectInstance readyRestored =
                recreateWithCapturedRewindState(
                        results, readyObjectSnapshot, services, restoreContext);
        assertEquals(submitted, timing.pendingHandles(),
                "ready-but-unclaimed jobs retain their original identities");
        setField(readyRestored, "createGateFrames", 0);
        readyRestored.update(2, player);

        assertTrue((boolean) field(readyRestored, "resultsChildrenCreated"));
        assertEquals(0, field(readyRestored, "stateTimer"),
                "native zero-duration slide dispatch advances state and resets its timer");
        assertEquals(1, field(readyRestored, "totalFrames"));
        assertTrue(timing.pendingHandles().isEmpty());
        assertEquals(3L, nextKosOrdinal(timing.capture()),
                "ready restore must claim, never submit replacement art");
    }

    @Test
    void postClaimRewindRebuildsRenderOwnerFromClaimedLedgerPayloads() throws Exception {
        ObjectServices services = TestEnvironment.objectServices();
        var timing = services.hardwareTiming();
        S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                services,
                () -> new S3kResultsScreenObjectInstance(
                        PlayerCharacter.SONIC_AND_TAILS, 0));
        results.setServices(services);
        List<HardwareWorkHandle> submitted = timing.pendingHandles();
        serviceResultsArtToReadiness(timing, submitted);

        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        setField(results, "createGateFrames", 0);
        results.update(0, player);
        assertTrue((boolean) field(results, "resultsChildrenCreated"));
        assertTrue(timing.pendingHandles().isEmpty());
        assertEquals(List.of(0L, 1L, 2L), resultsArtOrdinals(results),
                "claimed results art must retain its stable ledger references");

        RewindCaptureContext rewindContext = rewindContextFor(player);
        HardwareTimingSnapshot claimedTimingSnapshot = timing.capture();
        PerObjectRewindSnapshot claimedObjectSnapshot =
                results.captureRewindState(rewindContext);

        timing.restore(claimedTimingSnapshot);
        HardwareTimingSnapshot ledgerBeforeRestore = timing.capture();
        S3kResultsScreenObjectInstance restored =
                recreateWithCapturedRewindState(
                        results, claimedObjectSnapshot, services, rewindContext);
        HardwareTimingSnapshot ledgerAfterRestore = timing.capture();

        assertTrue((boolean) field(restored, "artLoaded"));
        assertTrue((boolean) field(restored, "resultsChildrenCreated"));
        assertNotNull(field(restored, "combinedPatterns"));
        assertNotNull(field(restored, "spriteSheet"));
        assertNotNull(field(restored, "renderer"),
                "post-claim rewind must reconstruct the transient render owner");
        assertFalse((boolean) field(restored, "artCached"),
                "the reconstructed renderer must republish patterns to the live graphics owner");
        assertLedgerLifecycleUnchanged(ledgerBeforeRestore, ledgerAfterRestore);
    }

    private static void serviceResultsArtToReadiness(
            com.openggf.game.timing.HardwareTimingService timing,
            List<HardwareWorkHandle> submitted) {
        for (int frame = 0; frame < 100_000; frame++) {
            List<HardwareWorkHandle> readyBefore = readyHandles(timing, submitted);
            if (readyBefore.size() == submitted.size()) {
                return;
            }

            timing.service(HardwareServiceBoundary.VINT_SERVICE);
            TestEnvironment.activeGameplayMode().runtimeArtCoordinator()
                    .afterTimingService(HardwareServiceBoundary.VINT_SERVICE);
            assertEquals(readyBefore, readyHandles(timing, submitted),
                    "VINT_SERVICE must not expose results art before its FIFO POST_OBJECTS retirement");

            timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            TestEnvironment.activeGameplayMode().runtimeArtCoordinator()
                    .afterTimingService(HardwareServiceBoundary.PRE_MAIN_LOOP);
            assertEquals(readyBefore, readyHandles(timing, submitted),
                    "PRE_MAIN_LOOP must prepare work without exposing results art readiness");

            timing.service(HardwareServiceBoundary.POST_OBJECTS);
            TestEnvironment.activeGameplayMode().runtimeArtCoordinator()
                    .afterTimingService(HardwareServiceBoundary.POST_OBJECTS);
            List<HardwareWorkHandle> readyAfterPostObjects = readyHandles(timing, submitted);
            assertEquals(submitted.subList(0, readyAfterPostObjects.size()), readyAfterPostObjects,
                    "POST_OBJECTS may expose only the next FIFO results-art handle");
        }
        throw new AssertionError("results art did not become ready within the bounded hardware service loop");
    }

    private static List<HardwareWorkHandle> readyHandles(
            com.openggf.game.timing.HardwareTimingService timing,
            List<HardwareWorkHandle> submitted) {
        return submitted.stream().filter(timing::isReady).toList();
    }

    private static S3kResultsScreenObjectInstance recreateWithCapturedRewindState(
            S3kResultsScreenObjectInstance source,
            PerObjectRewindSnapshot capturedState,
            ObjectServices services,
            RewindCaptureContext restoreContext) throws Exception {
        S3kResultsScreenObjectInstance restored =
                (S3kResultsScreenObjectInstance) source.recreateForRewind(
                        new RewindRecreateContext(null, capturedState, services));
        restored.setServices(services);
        restored.restoreRewindState(capturedState, restoreContext);
        return restored;
    }

    private static RewindCaptureContext rewindContextFor(Sonic player) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerPlayer(player, PlayerRefId.mainPlayer());
        return RewindCaptureContext.withIdentityTable(table);
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

    private static List<Long> resultsArtOrdinals(
            S3kResultsScreenObjectInstance results) throws Exception {
        return List.of(
                (long) field(results, "resultsGeneralArtOrdinal"),
                (long) field(results, "resultsNumberArtOrdinal"),
                (long) field(results, "resultsCharacterArtOrdinal"));
    }

    private static void assertLedgerLifecycleUnchanged(
            HardwareTimingSnapshot before,
            HardwareTimingSnapshot after) {
        assertEquals(before.nextOrdinals(), after.nextOrdinals(),
                "render-owner reconstruction must not submit replacement work");
        assertEquals(before.lastServicedBoundary(), after.lastServicedBoundary(),
                "render-owner reconstruction must not service hardware work");
        assertEquals(before.jobs().size(), after.jobs().size());
        assertEquals(
                before.jobs().stream().map(HardwareTimingJob.Snapshot::handle).toList(),
                after.jobs().stream().map(HardwareTimingJob.Snapshot::handle).toList());
        assertEquals(
                before.jobs().stream().map(HardwareTimingJob.Snapshot::ready).toList(),
                after.jobs().stream().map(HardwareTimingJob.Snapshot::ready).toList());
        assertEquals(
                before.jobs().stream().map(HardwareTimingJob.Snapshot::claimed).toList(),
                after.jobs().stream().map(HardwareTimingJob.Snapshot::claimed).toList(),
                "render-owner reconstruction must not release or claim hardware work");
    }
}
