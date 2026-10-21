package com.openggf.game.sonic3k.titlecard;

import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeArtAdmissionLease;
import com.openggf.game.RuntimeArtAdmissionOwnerKind;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.sonic3k.Sonic3k;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kKosModuleQueue;
import com.openggf.game.sonic3k.resources.S3kKosDecompressionQueue;
import com.openggf.game.sonic3k.resources.S3kKosDecompressionQueueSnapshot;
import com.openggf.level.LevelData;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareTimingSnapshot;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.tests.HardwareBoundaryPump;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kTitleCardKosQueue {
    private static final int[] NORMAL_SOURCES = {
            Sonic3kConstants.ART_KOSM_TITLE_CARD_RED_ACT_ADDR,
            Sonic3kConstants.ART_KOSM_TITLE_CARD_S3K_ZONE_ADDR,
            Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM1_ADDR,
            Sonic3kConstants.TITLE_CARD_ZONE_ART_ADDRS[0]
    };
    private static final int[] NORMAL_DESTINATIONS = {0x500, 0x510, 0x53D, 0x54D};
    private static final int[] BONUS_SOURCES = {
            Sonic3kConstants.ART_KOSM_TITLE_CARD_RED_ACT_ADDR,
            Sonic3kConstants.ART_KOSM_TITLE_CARD_S3K_ZONE_ADDR,
            Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM1_ADDR,
            Sonic3kConstants.ART_KOSM_BONUS_TITLE_CARD_ADDR
    };
    private static final int[] BONUS_DESTINATIONS = {0x500, 0x510, 0x53D, 0x54D};

    private HardwareTimingService timing;
    private Rom rom;
    private Sonic3kTitleCardManager manager;

    @BeforeEach
    void setUp() throws Exception {
        timing = GameServices.hardwareTiming();
        timing.resetForMissingSnapshot();
        rom = TestEnvironment.currentRom();
        manager = new Sonic3kTitleCardManager();
        manager.reset();
        prepareTitleLease((Sonic3kObjectArtProvider) GameServices.module()
                .getObjectArtProvider());
    }

    @Test
    void retainedResetWaitsForArtAndLastChildMovementAndRestoresThatGate() throws Exception {
        GameServices.level().resetLevelGamestate(GameServices.module().createLevelState());
        manager.initializeInLevel(0, 1);
        manager.requestLevelGamestateResetAfterCreateDispatches(
                com.openggf.game.TitleCardResetGates.NATIVE_WAIT_GATE);
        var original = GameServices.level().getLevelGamestate();
        original.setRings(37);
        for (int poll = 0; poll < 50; poll++) S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        assertEquals(37, original.getRings());
        org.junit.jupiter.api.Assertions.assertSame(original, GameServices.level().getLevelGamestate(),
                "owner polls cannot reset counters while its ROM archives are still pending");
        drainThroughPostObjects(moduleHandles());
        for (int poll = 0; poll < 100 && manager.getStateName().equals("SLIDE_IN"); poll++) {
            S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        }
        assertEquals("DISPLAY", manager.getStateName());
        var lastMovement = manager.capture();
        assertEquals(0, lastMovement.stateTimer());
        assertTrue(lastMovement.heldLevelCounterDispatchOwned());
        assertTrue(lastMovement.retainedResultsHeldLevelCounterOwned());
        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        org.junit.jupiter.api.Assertions.assertSame(original, GameServices.level().getLevelGamestate(),
                "the first parent poll clears the last movement latch and returns");
        manager.restore(lastMovement);
        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        org.junit.jupiter.api.Assertions.assertSame(original, GameServices.level().getLevelGamestate(),
                "rewind restores the unconsumed movement latch");
        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        org.junit.jupiter.api.Assertions.assertNotSame(original, GameServices.level().getLevelGamestate());
        assertEquals(0, GameServices.level().getLevelGamestate().getRings());
        assertEquals(2, manager.capture().stateTimer(),
                "global counter reset must not restart the retained presentation clock");
        assertTrue(manager.capture().heldLevelCounterDispatchOwned());
        assertTrue(manager.capture().retainedResultsHeldLevelCounterOwned());
    }

    @Test
    void normalCardQueuesFourArchivesInOrderAndRewindsInFlightBoundary() throws Exception {
        List<HardwareWorkHandle> expected =
                expectedHandles(NORMAL_SOURCES, NORMAL_DESTINATIONS);

        manager.initialize(0, 0);

        assertEquals(expected, moduleHandles(),
                "normal title art must retain native four-archive FIFO order");
        assertTrue(isArtLoading(manager));
        assertEquals(0, readyCount(expected));

        service(HardwareServiceBoundary.POST_OBJECTS);
        service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        assertEquals(0, readyCount(expected),
                "PRE_MAIN_LOOP may advance descriptors but must not publish readiness");
        HardwareTimingSnapshot rewindPoint = timing.capture();
        S3kKosDecompressionQueueSnapshot directRewindPoint =
                directQueue().capture();

        service(HardwareServiceBoundary.POST_OBJECTS);
        service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        service(HardwareServiceBoundary.POST_OBJECTS);
        timing.restore(rewindPoint);
        directQueue().restore(directRewindPoint);

        assertEquals(HardwareServiceBoundary.PRE_MAIN_LOOP,
                timing.capture().lastServicedBoundary());
        assertEquals(expected, moduleHandles());
        assertEquals(0, readyCount(expected),
                "rewind must restore the exact in-flight, not-ready decoder state");

        drainThroughPostObjects(expected);
    }

    @Test
    void bonusCardQueuesFourArchivesInOrderAndPollsUntilFinalPostObjects() throws Exception {
        List<HardwareWorkHandle> expected =
                expectedHandles(BONUS_SOURCES, BONUS_DESTINATIONS);

        manager.initializeBonus();

        assertEquals(expected, moduleHandles(),
                "bonus title art must retain native four-archive FIFO order");
        assertTrue(isArtLoading(manager));
        drainThroughPostObjects(expected);
    }

    @Test
    void readyUnclaimedTitleJobsDoNotSubmitEnemyArt() throws Exception {
        CountingObjectArtProvider provider = installCountingProvider();

        manager.initialize(0, 0);
        List<HardwareWorkHandle> titleHandles = moduleHandles();
        for (int frame = 0;
                frame < 100_000 && readyCount(titleHandles) < titleHandles.size();
                frame++) {
            service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            service(HardwareServiceBoundary.POST_OBJECTS);
        }
        assertEquals(titleHandles.size(), readyCount(titleHandles));

        provider.processRuntimeArtQueue();
        assertEquals(titleHandles, moduleHandles(),
                "readiness alone is not native title-card retirement");

        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        provider.processRuntimeArtQueue();

        assertEquals(0, provider.titleCardRetirementCount,
                "claiming title payloads is not title-owner retirement");
        assertTrue(moduleHandles().isEmpty(),
                "enemy art remains held until the title owner reaches COMPLETE");
    }

    @Test
    void repeatedSameZoneCardRequeuesItsArchivesBeforePublishingTerrain()
            throws Exception {
        Object previousGame = getLevelManagerGame();
        var previousLevel = GameServices.level().getCurrentLevel();
        var game = new Sonic3k(rom);
        setLevelManagerGame(game);
        setField(GameServices.level(), "level",
                game.loadLevel(LevelData.S3K_ANGEL_ISLAND_1.getLevelIndex()));
        try {
            installCachedTitleArt();
            manager.requestFreshLevelRuntimeArtHandoff(
                    LevelData.S3K_ANGEL_ISLAND_1.getLevelIndex());
            manager.initialize(0, 0);
            assertEquals(4, moduleHandles().size(),
                    "Obj_TitleCardInit queues its archives on every Level: entry");

            S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
            S3kRuntimeArtCoordinator.current().afterTimingService(
                    HardwareServiceBoundary.PRE_MAIN_LOOP);

            assertEquals(4, moduleHandles().size(),
                    "LoadLevelLoadBlock's terrain follows the card's loc_62CC gates");
            assertEquals(LevelData.S3K_ANGEL_ISLAND_1.getLevelIndex(),
                    manager.capture().freshLevelRuntimeArtHandoffLevelIndex(),
                    "the armed handoff waits for the loc_62CC gates");
        } finally {
            setLevelManagerGame(previousGame);
            setField(GameServices.level(), "level", previousLevel);
        }
    }

    @Test
    void freshLevelTerrainWaitsForTitleAndNemesisButNotChildExit() throws Exception {
        Object previousGame = getLevelManagerGame();
        var previousLevel = GameServices.level().getCurrentLevel();
        var game = new Sonic3k(rom);
        setLevelManagerGame(game);
        setField(GameServices.level(), "level",
                game.loadLevel(LevelData.S3K_ANGEL_ISLAND_1.getLevelIndex()));
        var plc = com.openggf.game.session.WorldPlcLifecycleAccess.require(GameServices.level().getWorldSession(),
                com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService.class);
        var previousPlc = plc.capture();
        // Level submits the FIFO before this isolated Obj_TitleCard fixture.
        plc.beginFreshZoneTitle(0, 0, "sonic");
        try {
            setPendingFreshLevelTransitionBoundary(true);
            installCachedTitleArt();
            manager.requestFreshLevelRuntimeArtHandoff(
                    LevelData.S3K_ANGEL_ISLAND_1.getLevelIndex());
            manager.initializeFreshLevelTransition(0, 0);
            assertTrue(manager.hasImmediateFreshLevelPalette());
            assertEquals(4, moduleHandles().size(),
                    "a same-act return must still submit Obj_TitleCardInit's archives");
            drainThroughPostObjects(moduleHandles());
            setField(manager, "state", Sonic3kTitleCardState.DISPLAY);
            setField(manager, "freshLevelChildMovementObserved", true);
            var movementBoundary = manager.capture();
            S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
            assertFalse(manager.capture().freshLevelTitleReady(),
                    "the first parent poll only clears the preceding movement flag");
            S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
            assertTrue(manager.capture().freshLevelTitleReady());
            assertFalse(manager.shouldPublishFreshLevelTransitionInitialBoundary(),
                    "title readiness alone cannot bypass the Nemesis FIFO");
            assertTrue(moduleHandles().isEmpty());

            manager.restore(movementBoundary);
            assertFalse(manager.capture().freshLevelTitleReady());
            for (int i = 0; i < 1000 && plc.isBusy(); i++) {
                plc.prepareAfterLoop(com.openggf.game.resources.PlcLifecyclePhase.LEVEL_TITLE_CARD);
                plc.serviceVBlank(com.openggf.game.resources.PlcLifecyclePhase.LEVEL_TITLE_CARD);
            }
            assertFalse(plc.isBusy());
            S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
            assertTrue(moduleHandles().isEmpty());
            S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
            assertEquals(Sonic3kTitleCardState.DISPLAY, manager.capture().state());
            assertTrue(manager.shouldPublishFreshLevelTransitionInitialBoundary());
            assertFalse(manager.shouldCompleteFreshLevelTransitionBoundary(),
                    "LoadLevelLoadBlock must drain before ordinary gameplay begins");
            assertEquals(2, moduleHandles().size());
            var terrainWait = manager.capture();
            S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
            assertEquals(terrainWait.stateTimer(), manager.capture().stateTimer(),
                    "loc_7870 does not dispatch the surviving title owner");
            assertEquals(2, moduleHandles().size(), "terrain submission is one-shot");
            service(HardwareServiceBoundary.POST_OBJECTS);
            service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            assertTrue(directQueue().capture().entries().isEmpty(),
                    "terrain parents queued after loc_62CC's module pass cannot expose a direct child in that pass");
        } finally {
            plc.restore(previousPlc);
            setPendingFreshLevelTransitionBoundary(false);
            setLevelManagerGame(previousGame);
            setField(GameServices.level(), "level", previousLevel);
        }
    }

    @Test
    void cachedTitleArtDoesNotRetireRuntimeArtAtInitialization() throws Exception {
        CountingObjectArtProvider provider = installCountingProvider();
        setField(manager, "artLoaded", true);
        setField(manager, "lastLoadedZone", 0);
        setField(manager, "lastLoadedAct", 0);
        setField(manager, "combinedPatterns", new com.openggf.level.Pattern[0x100]);

        manager.initialize(0, 0);
        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());

        assertEquals(0, provider.titleCardRetirementCount,
                "cached title art has no payload-readiness retirement edge");
        assertFalse(provider.capture().runtimeArtAdmissionConsumed());
    }

    @Test
    void ordinaryInLevelCompletionArmsAndSubmitsOnFollowingRuntimePass()
            throws Exception {
        Sonic3kObjectArtProvider provider = (Sonic3kObjectArtProvider)
                GameServices.module().getObjectArtProvider();
        installCachedTitleArt();
        manager.initializeInLevel(0, 0);
        prepareExitForCompletion(manager);

        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());

        PlcProgressSnapshot completed = provider.capture();
        assertTrue(manager.isComplete());
        assertTrue(completed.runtimeArtAdmissionConsumed());
        assertFalse(completed.kosSubmissionArmed());
        assertTrue((completed.runtimeState() & (1 << 4)) != 0);
        assertEquals(List.of(), completed.pendingKosOrdinals());

        provider.processRuntimeArtQueue();
        PlcProgressSnapshot edge = provider.capture();
        assertTrue(edge.kosSubmissionArmed());
        assertTrue((edge.runtimeState() & (1 << 4)) == 0);
        assertEquals(3, edge.pendingKosOrdinals().size(),
                "the following runtime pass submits the native enemy batch");
    }

    @Test
    void heldResultsTitleCompletionConsumesDirectlyWithoutDeferral()
            throws Exception {
        Sonic3kObjectArtProvider provider = (Sonic3kObjectArtProvider)
                GameServices.module().getObjectArtProvider();
        installCachedTitleArt();
        manager.initializeInLevel(0, 0);
        manager.requestLevelGamestateResetAfterCreateDispatches(1);
        prepareExitForCompletion(manager);

        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());

        PlcProgressSnapshot completed = provider.capture();
        assertTrue(manager.isComplete());
        assertTrue(completed.runtimeArtAdmissionConsumed());
        assertTrue(completed.kosSubmissionArmed());
        assertTrue((completed.runtimeState() & (1 << 4)) == 0,
                "held/carried title owners consume directly on their SST dispatch");

        provider.processRuntimeArtQueue();
        assertEquals(3, provider.capture().pendingKosOrdinals().size());
    }

    @Test
    void titleOwnerRetiresRuntimeArtExactlyOnceOnCompleteTransition() throws Exception {
        CountingObjectArtProvider provider = installCountingProvider();
        setField(manager, "artLoaded", true);
        setField(manager, "lastLoadedZone", 0);
        setField(manager, "lastLoadedAct", 0);
        setField(manager, "combinedPatterns", new com.openggf.level.Pattern[0x100]);
        manager.initialize(0, 0);
        // Obj_TitleCardInit re-queued the archives; treat them as claimed.
        installCachedTitleArt();
        setField(manager, "artLoading", false);
        prepareExitForCompletion(manager);

        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());

        assertTrue(manager.isComplete());
        assertEquals(1, provider.titleCardRetirementCount,
                "the EXIT to COMPLETE transition is the sole title-owner release");

        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        manager.reset();

        assertEquals(1, provider.titleCardRetirementCount,
                "later COMPLETE updates and reset do not replay retirement");
    }

    @Test
    void rewindAfterTitleRetirementRebindsEnemyJobsWithoutResubmission()
            throws Exception {
        Sonic3kObjectArtProvider provider =
                (Sonic3kObjectArtProvider) GameServices.module()
                        .getObjectArtProvider();

        manager.initialize(0, 0);
        List<HardwareWorkHandle> titleHandles =
                moduleHandles();
        for (int frame = 0;
                frame < 4096 && readyCount(titleHandles) < titleHandles.size();
                frame++) {
            service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            service(HardwareServiceBoundary.POST_OBJECTS);
        }
        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        prepareExitForCompletion(manager);
        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        provider.processRuntimeArtQueue();

        List<HardwareWorkHandle> enemyHandles =
                moduleHandles();
        assertEquals(List.of(4L, 5L, 6L),
                enemyHandles.stream().map(HardwareWorkHandle::ordinal).toList());
        HardwareTimingSnapshot timingSnapshot = timing.capture();
        S3kKosDecompressionQueueSnapshot directSnapshot =
                directQueue().capture();
        PlcProgressSnapshot providerSnapshot = provider.capture();
        assertTrue(providerSnapshot.kosSubmissionArmed());
        assertEquals(List.of(4L, 5L, 6L),
                providerSnapshot.pendingKosOrdinals());

        service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        service(HardwareServiceBoundary.POST_OBJECTS);
        timing.restore(timingSnapshot);
        directQueue().restore(directSnapshot);
        provider.restore(providerSnapshot);
        provider.processRuntimeArtQueue();

        assertEquals(enemyHandles, moduleHandles());
        assertEquals(7L, timing.capture().nextOrdinals()
                .get(com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void sszTitleRetirementRetainsOnePhysicalRequestAndRewindsItsPublicationIntent(boolean cached)
            throws Exception {
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        timing = GameServices.hardwareTiming();
        var provider = (Sonic3kObjectArtProvider) GameServices.module().getObjectArtProvider();
        provider.prepareRuntimeArtForActTransition(10, RuntimeArtAdmissionPolicy.TITLE_OWNER);
        var physical = fixture.gameplayMode().getKosinskiModuleQueue();
        assertTrue(moduleHandles().isEmpty());
        assertTrue(physical.isIdle());
        var prepared = provider.capture();
        assertEquals(List.of(new PlcProgressSnapshot.PendingKosModule(
                0x17B17E, 0x500, true)), prepared.pendingKosModules());
        provider.restore(prepared);
        assertEquals(prepared, provider.capture(), "pre-submission rewind retains physical intent and lease");
        if (cached) {
            installCachedTitleArt();
            setField(manager, "lastLoadedZone", 10);
        }
        manager.initializeInLevel(10, 0);
        var titleHandles = moduleHandles();
        for (int frame = 0; frame < 4096 && readyCount(titleHandles) < titleHandles.size(); frame++) {
            service(HardwareServiceBoundary.POST_OBJECTS);
            service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        }
        assertEquals(titleHandles.size(), readyCount(titleHandles));
        provider.processRuntimeArtQueue();
        assertEquals(titleHandles, moduleHandles(), "cached/ready art is not owner retirement");
        assertTrue(physical.isIdle());
        if (!cached) S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        prepareExitForCompletion(manager);
        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        assertTrue(manager.isComplete());
        assertTrue(provider.capture().runtimeArtAdmissionConsumed());
        assertTrue(physical.isIdle(), "in-level retirement defers until the next runtime pass");
        // Declared capacity control: occupy only the physical facade, then
        // release those setup entries. The title-owned intent must survive.
        for (int i = 0; i < 4; i++) assertTrue(physical.enqueue(rom, 0x17B17E, 0xA000));
        var nextOrdinals = timing.capture().nextOrdinals();
        provider.processRuntimeArtQueue();
        assertEquals(nextOrdinals, timing.capture().nextOrdinals());
        assertEquals(prepared.pendingKosModules(), provider.capture().pendingKosModules());
        assertTrue(provider.capture().pendingKosOrdinals().isEmpty());
        assertEquals(4, physical.queuedArchiveCount());
        physical.clear();
        provider.processRuntimeArtQueue();
        var handles = moduleHandles();
        assertEquals(1, handles.size());
        assertEquals(handles.stream().map(HardwareWorkHandle::ordinal).toList(),
                provider.capture().pendingKosOrdinals());
        var descriptor = S3kRuntimeArtCoordinator.current().moduleQueue().descriptor(handles.getFirst());
        assertEquals(0x17B17E, descriptor.sourceAddress());
        assertEquals(0xA000, descriptor.destinationAddress());
        assertEquals(2528, descriptor.destinationLength());
        assertEquals(1, descriptor.moduleCount());
        assertEquals(1, physical.queuedArchiveCount());
        assertEquals(0x4F0, physical.capture().archives().getFirst().lastModuleWords());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var submitted = registry.capture();
        service(HardwareServiceBoundary.POST_OBJECTS);
        service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        registry.restore(submitted);
        assertEquals(submitted.entries().keySet(), registry.capture().entries().keySet());
        for (String key : submitted.entries().keySet()) {
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,
                    submitted.get(key), registry.capture().get(key)).isEmpty(), key);
        }
        provider.processRuntimeArtQueue();
        assertEquals(handles, moduleHandles(), "restored ordinals rebind without another submission");
        assertEquals(1, physical.queuedArchiveCount());
        for (int frame = 0; frame < 4096 && !timing.isReady(handles.getFirst()); frame++) {
            service(HardwareServiceBoundary.POST_OBJECTS);
            service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            physical.processNativeFrame();
        }
        assertTrue(timing.isReady(handles.getFirst()));
        assertEquals(handles, moduleHandles(), "retained provider handle has no automatic claim owner");
        provider.processRuntimeArtQueue();
        assertTrue(moduleHandles().isEmpty());
        assertTrue(provider.capture().pendingKosOrdinals().isEmpty());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void incidentalProducersCannotAdmitSszArtOrDelayItsRetiredTitleOwner(boolean starPost)
            throws Exception {
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        timing = GameServices.hardwareTiming();
        var provider = (Sonic3kObjectArtProvider) GameServices.module().getObjectArtProvider();
        provider.prepareRuntimeArtForActTransition(10, RuntimeArtAdmissionPolicy.TITLE_OWNER);
        var pending = provider.capture();
        if (starPost) provider.queueStarPostBonusArt(Sonic3kConstants.ART_KOSM_STARPOST_STARS1_ADDR);
        else provider.queueBadnikExplosionArt();
        var incidental = moduleHandles().getFirst();
        assertFalse(timing.isReady(incidental));
        assertEquals(pending.pendingKosModules(), provider.capture().pendingKosModules());
        assertFalse(provider.capture().runtimeArtAdmissionConsumed());
        assertTrue(fixture.gameplayMode().getKosinskiModuleQueue().isIdle());
        provider.processRuntimeArtQueue();
        assertEquals(List.of(incidental), moduleHandles());

        installCachedTitleArt();
        setField(manager, "lastLoadedZone", 10);
        manager.initializeInLevel(10, 0);
        prepareExitForCompletion(manager);
        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        provider.processRuntimeArtQueue();
        assertFalse(timing.isReady(incidental), "title admission cannot wait for incidental art readiness");
        assertEquals(2, moduleHandles().size());
        assertTrue(provider.capture().pendingKosModules().isEmpty());
        assertEquals(1, fixture.gameplayMode().getKosinskiModuleQueue().queuedArchiveCount());
        assertEquals(0x17B17E, S3kRuntimeArtCoordinator.current().moduleQueue()
                .descriptor(moduleHandles().getLast()).sourceAddress());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var both = registry.capture();
        service(HardwareServiceBoundary.POST_OBJECTS);
        service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        registry.restore(both);
        for (String key : both.entries().keySet()) {
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,
                    both.get(key), registry.capture().get(key)).isEmpty(), key);
        }
        provider.processRuntimeArtQueue();
        assertEquals(2, moduleHandles().size(), "restore retains both owners without resubmission");
        assertEquals(1, fixture.gameplayMode().getKosinskiModuleQueue().queuedArchiveCount());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "true,0,true", "false,0,true", // Physical-only full; incidental still has room.
            "true,4,false", "false,4,false", // Logical-only full; neither request may enter.
            "true,3,false", "false,3,false" // Title owns the final logical slot.
    })
    void incidentalProducersRespectArmedTitleBackpressureAndReplayBothFifos(
            boolean starPost, int logicalPredecessors, boolean physicalFull) throws Exception {
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        timing = GameServices.hardwareTiming();
        var provider = (Sonic3kObjectArtProvider) GameServices.module().getObjectArtProvider();
        provider.prepareRuntimeArtForActTransition(10, RuntimeArtAdmissionPolicy.TITLE_OWNER);
        installCachedTitleArt();
        setField(manager, "lastLoadedZone", 10);
        manager.initializeInLevel(10, 0);
        prepareExitForCompletion(manager);
        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
        var physical = fixture.gameplayMode().getKosinskiModuleQueue();
        var queue = S3kRuntimeArtCoordinator.current().moduleQueue();
        var predecessors = new ArrayList<HardwareWorkHandle>();
        for (int i = 0; i < logicalPredecessors; i++) {
            predecessors.add(queue.queue(rom, 0x17FCBA, 0x300 + i * 0x50));
        }
        // Declared physical-only setup keeps the title pending while the real
        // completion callback's following runtime pass arms its exact lease.
        for (int i = 0; i < 4; i++) assertTrue(physical.enqueue(rom, 0x17B17E, 0xA000));
        provider.processRuntimeArtQueue();
        if (!physicalFull) physical.clear();
        var armed = provider.capture();
        assertTrue(armed.kosSubmissionArmed());
        assertTrue(armed.runtimeArtAdmissionConsumed());
        assertEquals(List.of(new PlcProgressSnapshot.PendingKosModule(0x17B17E, 0x500, true)),
                armed.pendingKosModules());
        var physicalBefore = physical.capture();
        var ordinalBefore = timing.capture().nextOrdinals().getOrDefault(
                com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE, 0L);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        submitIncidental(provider, starPost);
        var afterCallback = registry.capture();
        var after = provider.capture();
        assertEquals(armed.runtimeArtAdmissionLeaseId(), after.runtimeArtAdmissionLeaseId());
        assertEquals(armed.runtimeArtAdmissionGeneration(), after.runtimeArtAdmissionGeneration());
        assertEquals(armed.runtimeArtAdmissionBatchFingerprint(), after.runtimeArtAdmissionBatchFingerprint());
        assertTrue(after.runtimeArtAdmissionConsumed());
        boolean titleEntered = logicalPredecessors == 3;
        boolean incidentalEntered = physicalFull;
        assertEquals(titleEntered ? List.of() : armed.pendingKosModules(), after.pendingKosModules());
        assertEquals(ordinalBefore + (titleEntered || incidentalEntered ? 1 : 0),
                timing.capture().nextOrdinals().getOrDefault(
                        com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE, 0L));
        assertEquals(titleEntered || incidentalEntered ? 1 : 0, after.pendingKosOrdinals().size());
        if (!titleEntered) assertEquals(physicalBefore, physical.capture());
        if (titleEntered || incidentalEntered) {
            var last = queue.descriptor(moduleHandles().getLast());
            assertEquals(titleEntered ? 0x17B17E : starPost
                    ? Sonic3kConstants.ART_KOSM_STARPOST_STARS1_ADDR
                    : Sonic3kConstants.ART_KOSM_BADNIK_EXPLOSION_ADDR, last.sourceAddress());
        }
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            sameCapacityWorld(before, registry.capture());
            submitIncidental(provider, starPost);
            sameCapacityWorld(afterCallback, registry.capture());
        }

        // Release only the declared physical setup; real timed predecessors
        // regain capacity by their actual service/completion, never by clearing.
        if (physicalFull) physical.clear();
        for (int frame = 0; frame < 4096 && !provider.capture().pendingKosModules().isEmpty(); frame++) {
            service(HardwareServiceBoundary.POST_OBJECTS);
            service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            physical.processNativeFrame();
            provider.processRuntimeArtQueue();
        }
        assertTrue(provider.capture().pendingKosModules().isEmpty());
        var titles = moduleHandles().stream()
                .filter(h -> queue.descriptor(h).sourceAddress() == 0x17B17E).toList();
        assertEquals(1, titles.size(), "eventual capacity admits exactly one title handle");
        var title = titles.getFirst();
        assertTrue(provider.capture().pendingKosOrdinals().contains(title.ordinal()));
        assertEquals(1, physical.queuedArchiveCount(), "one retained handle owns one physical entry");
        assertEquals(0x17B17E, physical.capture().archives().getFirst().archiveAddress());
        assertEquals(0xA000, physical.capture().archives().getFirst().destinationVramBytes());
        assertEquals(ordinalBefore + 1 + (incidentalEntered ? 1 : 0),
                timing.capture().nextOrdinals().getOrDefault(
                        com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE, 0L));
        var admitted = registry.capture();
        drainCapacityRequests(provider, physical);
        assertTrue(timing.pendingHandles().containsAll(predecessors), "fixture predecessors keep their own claims");
        assertFalse(timing.pendingHandles().contains(title), "provider retires its one retained claim");
        assertEquals(1, S3kRuntimeArtCoordinator.current().pendingKosModuleDmaEntries().size());
        assertEquals(0x4F0, S3kRuntimeArtCoordinator.current().pendingKosModuleDmaEntries().getFirst().words());
        var completed = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(admitted);
            sameCapacityWorld(admitted, registry.capture());
            drainCapacityRequests(provider, physical);
            sameCapacityWorld(completed, registry.capture());
            registry.restore(before);
            sameCapacityWorld(before, registry.capture());
            registry.restore(completed);
            sameCapacityWorld(completed, registry.capture());
        }
    }

    private static void submitIncidental(Sonic3kObjectArtProvider provider, boolean starPost) {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            if (starPost) provider.queueStarPostBonusArt(Sonic3kConstants.ART_KOSM_STARPOST_STARS1_ADDR);
            else provider.queueBadnikExplosionArt();
        }, "ordinary physical/logical backpressure cannot become an incidental callback exception");
    }

    private void drainCapacityRequests(Sonic3kObjectArtProvider provider,
            com.openggf.level.resources.KosinskiModuleQueue physical) {
        for (int frame = 0; frame < 4096
                && (!physical.isIdle() || !provider.capture().pendingKosOrdinals().isEmpty()); frame++) {
            service(HardwareServiceBoundary.POST_OBJECTS);
            service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            physical.processNativeFrame();
            provider.processRuntimeArtQueue();
        }
        assertTrue(physical.isIdle());
        assertTrue(provider.capture().pendingKosOrdinals().isEmpty());
    }

    private static void sameCapacityWorld(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, expected.get(key), actual.get(key)).isEmpty(), key);
        }
    }

    private List<HardwareWorkHandle> expectedHandles(
            int[] sources, int[] destinations) throws Exception {
        HardwareTimingService expectedTiming = new HardwareTimingService();
        S3kKosModuleQueue expectedQueue = new S3kKosModuleQueue(
                expectedTiming, new S3kKosDecompressionQueue(expectedTiming));
        List<HardwareWorkHandle> handles = new ArrayList<>(sources.length);
        for (int i = 0; i < sources.length; i++) {
            handles.add(expectedQueue.queue(rom, sources[i], destinations[i]));
        }
        return List.copyOf(handles);
    }

    private void drainThroughPostObjects(List<HardwareWorkHandle> handles) throws Exception {
        // Kos_modules_left is decremented at exactly one site,
        // Process_Kos_Module_Queue (docs/skdisasm/sonic3k.asm:2750-2752), which LevelLoop
        // reaches at 7908 -- immediately after the object pass (ExecuteObjects,
        // 7900-7906), i.e. POST_OBJECTS. Process_Kos_Queue (7887) follows it in the same
        // loop tail and services only the decompression queue, so it can never retire an
        // archive. The object pass therefore observes readiness; it cannot create it.
        boolean sawPostObjectsPublication = false;
        for (int frame = 0; frame < 4096 && readyCount(handles) < handles.size(); frame++) {
            int readyBefore = readyCount(handles);

            S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());
            assertTrue(isArtLoading(manager),
                    "the title-card consumer must keep polling until every archive is ready");
            assertEquals(readyBefore, readyCount(handles),
                    "the object pass observes KosM readiness, it cannot create it");

            service(HardwareServiceBoundary.POST_OBJECTS);
            int readyAfterPost = readyCount(handles);
            assertTrue(readyAfterPost >= readyBefore,
                    "module readiness must never regress across a LevelLoop iteration");
            // One call to Process_Kos_Module_Queue per iteration, taking one of two
            // mutually exclusive branches: submit (2741) or DMA-and-decrement
            // (2750-2752). The shift-out tail (2778-2788) re-enters
            // Process_Kos_Module_Queue_Init (2694-2713) for the next archive, which
            // reloads Kos_modules_left rather than retiring a second archive.
            assertTrue(readyAfterPost - readyBefore <= 1,
                    "at most one archive may retire per LevelLoop iteration");
            if (readyAfterPost > readyBefore) {
                sawPostObjectsPublication = true;
            }

            service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            assertEquals(readyAfterPost, readyCount(handles),
                    "Process_Kos_Queue (7887) advances only the decompression queue and "
                            + "must not publish newly completed KosM work");
        }

        assertEquals(handles.size(), readyCount(handles));
        assertTrue(sawPostObjectsPublication,
                "at least one archive must publish readiness at the module state step");
        assertTrue(isArtLoading(manager),
                "the state step publishes readiness without running the consumer");

        S3kTitleCardWorldAccess.update(manager, GameServices.level().getWorldSession());

        assertFalse(isArtLoading(manager));
        assertTrue(timing.pendingHandles().isEmpty(),
                "the title-card consumer must claim all retained handles in FIFO order");
    }

    private int readyCount(List<HardwareWorkHandle> handles) {
        int count = 0;
        for (HardwareWorkHandle handle : handles) {
            if (timing.isReady(handle)) {
                count++;
            }
        }
        return count;
    }

    private void service(HardwareServiceBoundary boundary) {
        HardwareBoundaryPump.service(
                timing, S3kRuntimeArtCoordinator.current(), boundary);
    }

    private S3kKosDecompressionQueue directQueue() {
        return S3kRuntimeArtCoordinator.current().directQueue();
    }

    private List<HardwareWorkHandle> moduleHandles() {
        return timing.pendingHandles().stream()
                .filter(handle -> handle.kind()
                        == com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE)
                .toList();
    }

    private static boolean isArtLoading(Sonic3kTitleCardManager manager) throws Exception {
        Field field = Sonic3kTitleCardManager.class.getDeclaredField("artLoading");
        field.setAccessible(true);
        return field.getBoolean(manager);
    }

    private static CountingObjectArtProvider installCountingProvider() throws Exception {
        Sonic3kGameModule module = (Sonic3kGameModule) GameServices.module();
        Field field = Sonic3kGameModule.class.getDeclaredField("objectArtProvider");
        field.setAccessible(true);
        CountingObjectArtProvider provider = new CountingObjectArtProvider();
        field.set(module, provider);
        prepareTitleLease(provider);
        return provider;
    }

    private static void prepareTitleLease(Sonic3kObjectArtProvider provider)
            throws Exception {
        provider.prepareRuntimeArtForActTransition(
                0, RuntimeArtAdmissionPolicy.TITLE_OWNER);
    }

    private static void prepareExitForCompletion(Sonic3kTitleCardManager manager)
            throws Exception {
        // A Level-routine owner reaches EXIT only after loc_64DC handed it to
        // LevelLoop; an in-level owner has no such handoff (a no-op here).
        manager.completeFreshLevelRuntimeArtHandoff();
        setField(manager, "state", Sonic3kTitleCardState.EXIT);
        setField(manager, "exitChildrenGone", true);
        setField(manager, "actNumberVisible", true);
        boolean[] exited = (boolean[]) getField(manager, "elemExited");
        java.util.Arrays.fill(exited, true);
    }

    private void installCachedTitleArt() throws Exception {
        setField(manager, "artLoaded", true);
        setField(manager, "lastLoadedZone", 0);
        setField(manager, "lastLoadedAct", 0);
        setField(manager, "combinedPatterns", new com.openggf.level.Pattern[0x100]);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object getLevelManagerGame() throws Exception {
        Field field = GameServices.level().getClass().getDeclaredField("game");
        field.setAccessible(true);
        return field.get(GameServices.level());
    }

    private static void setLevelManagerGame(Object game) throws Exception {
        Field field = GameServices.level().getClass().getDeclaredField("game");
        field.setAccessible(true);
        field.set(GameServices.level(), game);
    }

    private static void setPendingFreshLevelTransitionBoundary(boolean pending)
            throws Exception {
        Field controllerField = GameServices.level().getClass()
                .getDeclaredField("freshLevelTransitionBoundary");
        controllerField.setAccessible(true);
        Object controller = controllerField.get(GameServices.level());
        Field field = controller.getClass().getDeclaredField("pending");
        field.setAccessible(true);
        if (!pending) {
            field.set(controller, null);
            return;
        }
        var constructor = field.getType().getDeclaredConstructor(
                short.class, short.class, int.class, List.class,
                com.openggf.game.InitialProcessSpritesLifecycle.class);
        constructor.setAccessible(true);
        var levelState = GameServices.level().getLevelGamestate();
        int destinationRings = levelState == null ? 0 : levelState.getRings();
        field.set(controller, constructor.newInstance(
                (short) 0, (short) 0, destinationRings, List.of(),
                com.openggf.game.InitialProcessSpritesLifecycle.NONE));
    }

    private static final class CountingObjectArtProvider extends Sonic3kObjectArtProvider {
        private int titleCardRetirementCount;

        @Override
        public void consumeRuntimeArtAdmission(
                RuntimeArtAdmissionLease lease,
                RuntimeArtAdmissionOwnerKind ownerKind) {
            super.consumeRuntimeArtAdmission(lease, ownerKind);
            titleCardRetirementCount++;
        }
    }
}
