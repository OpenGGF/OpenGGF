package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.data.RomByteReader;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackSessionScope;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.resources.DynamicArtDiagnosticsSnapshot;
import com.openggf.game.resources.DynamicArtGapTransition.GapEdge;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rules.DynamicArtDmaServiceModel;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1PlayerArt;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pending Sonic_LoadGfx readiness belongs to Level_ClrRam, not fade entry.
 * Level_ClrRam (docs/s1disasm/sonic.asm:2739-2743, recorded pin28872) clears
 * F700-F7FF at ROM381E, including F766/F767; v_sgfx_buffer
 * (docs/s1disasm/_Variables.asm:34-35) leaves C800-CADF outside that clear.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1LevelLoadPlayerArtReadiness {
    private List<SpriteDplcFrame> art;
    private GameLoop loop;

    @BeforeEach
    void loadRomDplcs() throws IOException {
        art = new Sonic1PlayerArt(RomByteReader.fromRom(TestEnvironment.currentRom()))
                .loadSonic().dplcFrames();
    }

    @AfterEach
    void closeOwnedLoopAndSession() {
        try {
            if (loop != null) loop.closePresence();
        } finally {
            loop = null;
            SessionManager.clear();
        }
    }

    @Test
    void actualFullLoadClearsOldReadinessBeforeTitleAndPublishesOnlyFreshReleaseArt() throws IOException {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var mode = fixture.gameplayMode();
        var level = mode.getLevelManager();
        var service = GameServices.dynamicArtLifecycleOrNull();
        assertNotNull(service, "use this loaded gameplay session's actual art lifecycle");
        assertSame(mode.dynamicArtLifecycle(), service);
        mode.plcFrameLifecycle().acquireExternalComparisonSegmentOwnership();
        level.consumeTitleCardRequest(); // positioned source contract omits its initial presentation only
        loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(mode);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        loop.step();
        loop.step(); // retire a real source-player transfer before testing history preservation
        List<GapEdge> history = List.copyOf(service.gapEdges());
        assertFalse(history.isEmpty(), "test must preserve an actual prior transfer history");
        long nextTransfer = service.capture().nextTransferId();
        long nextEdge = service.capture().nextEdgeOrdinal();
        stageOldGeneration(service);
        var old = service.capture();
        assertEquals(0x32, old.s1Preparation().mappingFrame());
        assertEquals(0x31, old.s1PreparationAfterPreMainLoopTail().mappingFrame());
        assertEquals(0x30, old.initialLoadPreparation().mappingFrame());

        level.requestZoneAndAct(0, 1, false);
        loop.step(); // real request consumer starts the blocking entry fade
        assertTrue(level.isLevelInactiveForTransition());
        for (int frame = 0; frame < 4; frame++) {
            loop.step();
            assertReadinessEquals(old, service.capture());
            assertEquals(history, service.gapEdges(), "the fade cannot service or erase source history");
        }

        var playback = GameServices.playbackDebug();
        Bk2Movie neutral = neutralMovie();
        try (var ownedPlayback = new PlaybackSessionScope(playback, neutral)) {
            playback.scheduleSessionAtNextLevelLoad(neutral, 0);
            boolean loaded = false;
            boolean titleSeen = false;
            boolean released = false;
            for (int frame = 0; frame < 600; frame++) {
                loop.step();
                if (level.getCurrentZone() != 0 || level.getCurrentAct() != 1) continue;
                if (!loaded) {
                    loaded = true;
                    assertFalse(level.isLevelInactiveForTransition());
                    assertFalse(playback.hasScheduledLevelLoadSession());
                    assertOldGenerationAbsentAfterLoad(service);
                    assertEquals(history, service.gapEdges(),
                            "Level_ClrRam invalidates unpublished work, never previous journal entries");
                    assertEquals(nextTransfer, service.capture().nextTransferId());
                    assertEquals(nextEdge, service.capture().nextEdgeOrdinal());
                }
                titleSeen |= loop.getCurrentGameMode() == GameMode.TITLE_CARD;
                if (titleSeen && loop.getCurrentGameMode() == GameMode.LEVEL) {
                    released = true;
                    assertFalse(fixture.sprite().isControlLocked());
                    assertEquals(1, fixture.sprite().getMappingFrame(), "actual native Wait release mapping");
                    assertEquals(0, level.getFrameCounter(), "release precedes ordinary level execution");
                    assertEquals(1, level.getObjectManager().getFrameCounter(), "real release object pass");
                    assertNotNull(service.capture().s1Preparation());
                    assertEquals(1, service.capture().s1Preparation().mappingFrame());
                    break;
                }
            }
            assertTrue(loaded, "real full load completed");
            assertTrue(titleSeen, "production scheduled-input boundary selected the destination title");
            assertTrue(released, "real title/player prelude released");
            loop.step(); // first eligible main-loop VBlank services the new prepared bank
            List<GapEdge> journal = service.gapEdges();
            assertEquals(history, journal.subList(0, history.size()));
            assertFreshWaitTransfer(journal.subList(history.size(), journal.size()), nextTransfer, nextEdge);
            assertTrue(journal.subList(history.size(), journal.size()).stream()
                    .noneMatch(edge -> edge.mappingFrame() == 0x32 || edge.mappingFrame() == 0x31),
                    "no old-generation preparation can become an upload after the load");
        }
    }

    @Test
    void existingSnapshotRestoresClearedReadinessAndItsForwardTransfersWithoutResettingHistory() throws IOException {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var mode = fixture.gameplayMode();
        var service = GameServices.dynamicArtLifecycleOrNull();
        assertNotNull(service);
        assertSame(mode.dynamicArtLifecycle(), service);
        var coordinator = mode.plcFrameLifecycle();
        coordinator.acquireExternalComparisonSegmentOwnership();
        mode.getLevelManager().consumeTitleCardRequest();
        loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(mode);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        loop.step();
        loop.step();
        List<GapEdge> history = List.copyOf(service.gapEdges());
        assertFalse(history.isEmpty());
        stageOldGeneration(service);
        claim(coordinator, PlcLifecyclePhase.PALETTE_FADE);
        var beforeClear = service.capture();
        var registry = mode.getRewindRegistry();
        var rewind = registry.capture();
        assertTrue(rewind.containsKey(DynamicArtLifecycleService.REWIND_KEY),
                "use the actual gameplay session's already registered adapter");

        service.clearPlayerDplcDedupRegistersForLevelLoad();
        assertReadinessCleared(service);
        assertEquals(history, service.gapEdges());
        claim(coordinator, PlcLifecyclePhase.LEVEL_TITLE_CARD);
        assertEquals(history, service.gapEdges(), "a clear cannot leave a later upload armed");

        service.observePlayerDplc(GameId.S1, "sonic", 0x35, art.get(0x35));
        claim(coordinator, PlcLifecyclePhase.ORDINARY_LEVEL);
        assertNotEquals(history, service.gapEdges(), "restaging destroys both pending and journal state");
        registry.restore(rewind); // the actual session's already registered production adapter
        assertReadinessEquals(beforeClear, service.capture());
        assertEquals(history, service.gapEdges());
        claim(coordinator, PlcLifecyclePhase.ORDINARY_LEVEL);
        claim(coordinator, PlcLifecyclePhase.ORDINARY_LEVEL);
        List<GapEdge> forward = service.gapEdges();
        assertEquals(history, forward.subList(0, history.size()));
        assertEquals(List.of(0x32, 0x32, 0x31, 0x31), forward.subList(history.size(), forward.size())
                .stream().map(GapEdge::mappingFrame).toList());
        assertEquals(beforeClear.nextTransferId() + 2, service.capture().nextTransferId());
        registry.restore(rewind);
        claim(coordinator, PlcLifecyclePhase.ORDINARY_LEVEL);
        claim(coordinator, PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(forward, service.gapEdges(), "restored readiness replays the same whole journal");
    }

    @Test
    void levelReadinessClearPreservesSubmittedSonic2WorkAndItsIdentity() {
        var service = new DynamicArtLifecycleService();
        service.beginRun();
        var coordinator = coordinator(service, DynamicArtDmaServiceModel.SONIC_2_PROCESS_DMA_QUEUE);
        var update = service.observePlayerDplc(GameId.S2, "sonic", 7,
                new SpriteDplcFrame(List.of(new TileLoadRequest(20, 2))));
        assertTrue(update.submitted());
        var queued = service.capture();
        List<GapEdge> submittedHistory = List.copyOf(service.gapEdges());
        assertEquals(1, submittedHistory.size());

        service.clearPlayerDplcDedupRegistersForLevelLoad();

        assertEquals(queued.ledger(), service.capture().ledger());
        assertEquals(queued.pendingS2TransferIds(), service.capture().pendingS2TransferIds());
        assertEquals(queued.nextTransferId(), service.capture().nextTransferId());
        assertEquals(submittedHistory, service.gapEdges());
        claim(coordinator, PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(submittedHistory, service.gapEdges().subList(0, 1));
        assertEquals(List.of("submitted", "completed"), service.gapEdges().stream().map(GapEdge::phase).toList());
        assertTrue(service.gapEdges().stream().allMatch(edge -> edge.transferId() == update.transferId()));
        assertTrue(service.capture().ledger().isEmpty());
    }

    private void stageOldGeneration(DynamicArtLifecycleService service) {
        service.primePlayerDplc(GameId.S1, "sonic", 0x30, art.get(0x30));
        service.observePlayerDplc(GameId.S1, "sonic", 0x32, art.get(0x32));
        service.holdPendingPlayerPreparationForPreMainLoopTail(26); // native Level_Delay4 + PalFadeIn22
        service.observePlayerDplc(GameId.S1, "sonic", 0x31, art.get(0x31));
    }

    private static void assertOldGenerationAbsentAfterLoad(DynamicArtLifecycleService service) {
        var loaded = service.capture();
        for (var preparation : java.util.Arrays.asList(loaded.s1Preparation(),
                loaded.s1PreparationAfterPreMainLoopTail(), loaded.initialLoadPreparation())) {
            if (preparation != null) {
                assertFalse(isOldMapping(preparation.mappingFrame()),
                        "fresh priming may exist, but old-generation staging must be gone");
            }
        }
        assertEquals(-1, loaded.preMainLoopTailRows(), "the old tail hold cannot survive full load");
        assertEquals(0, loaded.preMainLoopHoldBoundaryRow(), "old tail boundary is invalidated");
        assertTrue(loaded.lastMappingFrames().values().stream().noneMatch(
                TestSonic1LevelLoadPlayerArtReadiness::isOldMapping),
                "fresh dedup state is allowed; old-generation frame values are not");
    }

    private static boolean isOldMapping(int mapping) {
        return mapping == 0x30 || mapping == 0x31 || mapping == 0x32;
    }

    private static void assertReadinessCleared(DynamicArtLifecycleService service) {
        var cleared = service.capture();
        assertNull(cleared.s1Preparation());
        assertNull(cleared.s1PreparationAfterPreMainLoopTail());
        assertNull(cleared.initialLoadPreparation());
        assertEquals(-1, cleared.preMainLoopTailRows());
        assertTrue(cleared.lastMappingFrames().isEmpty());
    }

    private static void assertReadinessEquals(DynamicArtLifecycleService.RewindState expected,
                                              DynamicArtLifecycleService.RewindState actual) {
        assertEquals(expected.s1Preparation(), actual.s1Preparation());
        assertEquals(expected.s1PreparationAfterPreMainLoopTail(), actual.s1PreparationAfterPreMainLoopTail());
        assertEquals(expected.initialLoadPreparation(), actual.initialLoadPreparation());
        assertEquals(expected.preMainLoopTailRows(), actual.preMainLoopTailRows());
        assertEquals(expected.lastMappingFrames(), actual.lastMappingFrames());
    }

    private static void assertFreshWaitTransfer(List<GapEdge> edges, long transfer, long ordinal) {
        assertEquals(2, edges.size(), "complete unchanged journal suffix has exactly one new transfer");
        assertEquals(List.of("submitted", "completed"), edges.stream().map(GapEdge::phase).toList());
        assertEquals(List.of(transfer, transfer), edges.stream().map(GapEdge::transferId).toList());
        assertEquals(List.of(ordinal, ordinal + 1), edges.stream().map(GapEdge::edgeOrdinal).toList());
        assertTrue(edges.stream().allMatch(edge -> edge.owner().equals("sonic") && edge.mappingFrame() == 1));
        assertEquals(List.of(
                DynamicArtDiagnosticsSnapshot.Request.rom(0x22610, 0, 0xF000, 96),
                DynamicArtDiagnosticsSnapshot.Request.rom(0x22670, 3, 0xF060, 256),
                DynamicArtDiagnosticsSnapshot.Request.rom(0x22770, 11, 0xF160, 96),
                DynamicArtDiagnosticsSnapshot.Request.rom(0x227D0, 14, 0xF1C0, 96)), edges.getFirst().requests());
        assertEquals(List.of(DynamicArtDiagnosticsSnapshot.Request.ram(0xC800, 0xF000, 0x2E0)),
                edges.getLast().requests());
    }

    private static PlcFrameLifecycleCoordinator coordinator(DynamicArtLifecycleService service,
                                                            DynamicArtDmaServiceModel model) {
        var coordinator = new PlcFrameLifecycleCoordinator((PlcLifecycleService) null, service, model);
        coordinator.acquireExternalComparisonSegmentOwnership();
        return coordinator;
    }

    private static void claim(PlcFrameLifecycleCoordinator coordinator, PlcLifecyclePhase phase) {
        coordinator.runLogicalIteration(() -> { }, frame -> {
            assertTrue(frame.claim(phase));
            frame.prepareAfterLoop(phase);
            return null;
        });
    }

    private static Bk2Movie neutralMovie() {
        List<Bk2FrameInput> frames = new ArrayList<>();
        for (int row = 0; row < 32; row++) frames.add(new Bk2FrameInput(row, 0, 0, false, ""));
        return new Bk2Movie(Path.of("synthetic-s1-load-readiness-neutral.bk2"), "", Map.of(), frames, 1);
    }
}
