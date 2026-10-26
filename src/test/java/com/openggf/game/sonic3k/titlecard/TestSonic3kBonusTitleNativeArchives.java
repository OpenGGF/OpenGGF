package com.openggf.game.sonic3k.titlecard;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kKosDecompressionQueue;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingJob;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareTimingSnapshot;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.HardwareBoundaryPump;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent ROM submission oracle for the locked-on Knuckles/Gumball title owner.
 * Origin: R24, after accepted R23 actual submit-site witness; no replay fixture input.
 * Native044 Obj_TitleCardInit62143..62200 always queues four archives here. The
 * portal61952..61959 writes current/apparent $1300, selecting Num1 at62173..62179.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kBonusTitleNativeArchives {
    private static final int GUMBALL = 0x13;

    @AfterEach
    void cleanup() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test
    void knucklesGumballQueuesNum1AsItsOwnArchiveBeforeBonusGraphic() throws Exception {
        Session session = start(GUMBALL);
        assertEquals(0, GameServices.level().getApparentAct(),
                "declared Gumball $1300 inlet owns native apparent act zero");
        HardwareTimingSnapshot before = session.timing().capture();

        session.title().initializeBonus();

        List<HardwareTimingJob.Snapshot> submitted = addedJobs(before, session.timing().capture());
        assertPendingOwner(session, submitted, true);
        // This is an independent four-descriptor ROM oracle, not ordinal or completion-row fitting.
        assertEquals(expectedArchives(Sonic3kConstants.ART_KOSM_BONUS_TITLE_CARD_ADDR),
                submitted.stream().map(Archive::from).toList(),
                "Obj_TitleCardInit queues Num1 at $53D even when only BONUS/STAGE is drawn");
    }

    @Test
    void normalFreshOwnerKeepsNativeFourArchiveOrder() throws Exception {
        Session session = start(0);
        assertEquals(0, GameServices.level().getApparentAct());
        // Existing production resource handoff creates the normal title's exact lease;
        // it does not submit a speculative timing job or replace the provider/queue.
        session.provider().prepareRuntimeArtForActTransition(0, RuntimeArtAdmissionPolicy.TITLE_OWNER);
        HardwareTimingSnapshot before = session.timing().capture();

        session.title().initialize(0, 0);

        List<HardwareTimingJob.Snapshot> submitted = addedJobs(before, session.timing().capture());
        assertPendingOwner(session, submitted, false);
        assertEquals(expectedArchives(Sonic3kConstants.TITLE_CARD_ZONE_ART_ADDRS[0]),
                submitted.stream().map(Archive::from).toList(),
                "normal fresh title retains RedAct, Zone, Num1, AIZ order");
    }

    @Test
    void pendingBonusJobsRestoreAndReplayThroughRegisteredOwnersTwice() throws Exception {
        Session session = start(GUMBALL);
        HardwareTimingSnapshot before = session.timing().capture();
        session.title().initializeBonus();
        List<HardwareTimingJob.Snapshot> submitted = addedJobs(before, session.timing().capture());
        assertPendingOwner(session, submitted, true);
        CompositeSnapshot pending = session.registry().capture();
        var retainedTitle = assertInstanceOf(Sonic3kTitleCardManager.Snapshot.class,
                pending.get(Sonic3kTitleCardManager.REWIND_KEY));
        assertFalse(retainedTitle.artHandles().isEmpty());
        assertEquals(submitted.stream().map(HardwareTimingJob.Snapshot::handle).toList(),
                retainedTitle.artHandles(), "actual registered snapshot owns the submitted jobs");
        HardwareTimingSnapshot pendingTiming = assertInstanceOf(HardwareTimingSnapshot.class,
                pending.get(HardwareTimingService.REWIND_KEY));

        progressPendingTitle(session);
        CompositeSnapshot forward = session.registry().capture();
        HardwareTimingSnapshot forwardTiming = assertInstanceOf(HardwareTimingSnapshot.class,
                forward.get(HardwareTimingService.REWIND_KEY));
        assertTrue(forwardTiming.jobs().size() > pendingTiming.jobs().size(),
                "real POST module processing must submit a direct child before this control can pass");
        assertTrue(session.title().capture().artLoading(),
                "one child service cannot satisfy the entire title archive batch");

        for (int cycle = 0; cycle < 2; cycle++) {
            session.registry().restore(pending);
            assertOwnerPartition(session, pending, session.registry().capture(), "restored pending cycle " + cycle);
            progressPendingTitle(session);
            assertOwnerPartition(session, forward, session.registry().capture(), "forward replay cycle " + cycle);
        }
        // Full registry restore is used, but only the declared title/resource partition
        // is compared. This is not a whole-world or completed-title/route certificate.
    }

    private static Session start(int zone) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, 0).build();
        assertInstanceOf(Knuckles.class, fixture.sprite());
        assertEquals(zone, GameServices.level().getCurrentZone());
        assertEquals(0, GameServices.level().getCurrentAct());
        var title = assertInstanceOf(Sonic3kTitleCardManager.class,
                GameServices.module().getTitleCardProvider());
        var provider = assertInstanceOf(Sonic3kObjectArtProvider.class,
                GameServices.module().getObjectArtProvider());
        var art = S3kRuntimeArtCoordinator.current();
        assertTrue(art.moduleQueue().hasCapacityFor(4),
                "inlet must have native four-entry capacity; do not clear or rewrite queues to satisfy it");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        CompositeSnapshot registered = registry.capture();
        for (String key : ownerKeys(provider)) assertNotNull(registered.get(key), "production key " + key);
        assertTrue(RewindSnapshotDiff.keyEquals(Sonic3kTitleCardManager.REWIND_KEY,
                title.capture(), registered.get(Sonic3kTitleCardManager.REWIND_KEY)));
        return new Session(title, provider, GameServices.hardwareTiming(), registry);
    }

    private static void assertPendingOwner(Session session,
                                           List<HardwareTimingJob.Snapshot> submitted, boolean bonus) {
        assertFalse(submitted.isEmpty(), "ordinary title initialization must actually submit work");
        var title = session.title().capture();
        assertEquals(bonus, title.bonusMode());
        assertTrue(title.artLoading());
        assertEquals(submitted.stream().map(HardwareTimingJob.Snapshot::handle).toList(), title.artHandles());
        for (var job : submitted) {
            assertEquals(HardwareWorkKind.KOS_MODULE_QUEUE, job.kind());
            assertFalse(job.ready());
            assertFalse(job.claimed());
            assertNull(job.preparedPayload());
            assertTrue(session.timing().isPending(job.handle()));
        }
    }

    private static List<HardwareTimingJob.Snapshot> addedJobs(
            HardwareTimingSnapshot before, HardwareTimingSnapshot after) {
        Set<HardwareWorkHandle> retained = before.jobs().stream()
                .map(HardwareTimingJob.Snapshot::handle).collect(Collectors.toSet());
        // Do not filter kind or sort: unexpected jobs and production FIFO order remain observable.
        return after.jobs().stream().filter(job -> !retained.contains(job.handle())).toList();
    }

    private static List<Archive> expectedArchives(int graphicSource) throws IOException {
        var rom = TestEnvironment.currentRom();
        byte[] image = rom.readBytes(0, Math.toIntExact(rom.getSize()));
        return List.of(
                Archive.fromRom(image, Sonic3kConstants.ART_KOSM_TITLE_CARD_RED_ACT_ADDR, 0x500),
                Archive.fromRom(image, Sonic3kConstants.ART_KOSM_TITLE_CARD_S3K_ZONE_ADDR, 0x510),
                Archive.fromRom(image, Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM1_ADDR, 0x53D),
                Archive.fromRom(image, graphicSource, 0x54D));
    }

    private static void progressPendingTitle(Session session) {
        HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
        HardwareBoundaryPump.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        session.title().update();
    }

    private static List<String> ownerKeys(Sonic3kObjectArtProvider provider) {
        return List.of(HardwareTimingService.REWIND_KEY, S3kKosDecompressionQueue.REWIND_KEY,
                S3kRuntimeArtCoordinator.REWIND_KEY, Sonic3kTitleCardManager.REWIND_KEY, provider.key());
    }

    private static void assertOwnerPartition(Session session, CompositeSnapshot expected,
                                             CompositeSnapshot actual, String phase) {
        for (String key : ownerKeys(session.provider())) {
            assertNotNull(expected.get(key), phase + " expected key " + key);
            assertNotNull(actual.get(key), phase + " actual key " + key);
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> phase + " " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    private record Session(Sonic3kTitleCardManager title, Sonic3kObjectArtProvider provider,
                           HardwareTimingService timing, RewindRegistry registry) { }

    private record Archive(HardwareWorkKind kind, int source, int compressedLength,
                           int destinationBytes, int outputBytes, int modules, String compression) {
        private static Archive from(HardwareTimingJob.Snapshot job) {
            return new Archive(job.kind(), job.romSourceAddress(), job.compressedLength(),
                    job.destinationAddress(), job.destinationLength(), job.moduleCount(), job.compressionVariant());
        }

        private static Archive fromRom(byte[] image, int source, int destinationTile) throws IOException {
            var info = KosinskiReader.inspectModuled(image, source);
            return new Archive(HardwareWorkKind.KOS_MODULE_QUEUE, source, info.compressedLength(),
                    destinationTile * 32, info.decompressedLength(), info.moduleCount(), "kosinski_moduled");
        }
    }
}
