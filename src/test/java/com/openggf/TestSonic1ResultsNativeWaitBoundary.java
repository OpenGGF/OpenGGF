package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.resources.DynamicArtDiagnosticsSnapshot;
import com.openggf.game.resources.DynamicArtDiagnosticsSnapshot.Request;
import com.openggf.game.resources.DynamicArtGapTransition.GapEdge;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1PlayerArt;
import com.openggf.game.sonic1.objects.Sonic1FixedEndCardSlot;
import com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance;
import com.openggf.level.objects.AbstractResultsScreen;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Got_Wait releases its next routine after a real ordinary VBlank, without replay data. */
@Isolated
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1ResultsNativeWaitBoundary {
    private GameLoop loop;
    private List<SpriteDplcFrame> art;

    @BeforeEach
    void loadRomPlayerArt() throws IOException {
        art = new Sonic1PlayerArt(RomByteReader.fromRom(TestEnvironment.currentRom()))
                .loadSonic().dplcFrames();
    }

    @AfterEach
    void closeOwnedLoopAndSession() {
        try { if (loop != null) loop.closePresence(); }
        finally { loop = null; SessionManager.clear(); }
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 0x31})
    void ordinaryBoundaryServicesLastPreparedArtBeforeNextRoutineStartsFade(int mapping) throws Exception {
        Harness h = open(0, 0, false);
        armLastWaitPass(h.card());
        loop.step();
        assertEquals(180, timer(h.card()));
        assertFalse(h.mode().getLevelManager().isLevelInactiveForTransition(),
                "Got_Wait only advances routine on expiry");
        assertFalse(h.mode().getFadeManager().isActive());

        var service = h.mode().dynamicArtLifecycle();
        // Independent ROM work at the ordinary production staging port. The
        // continuous movie separately proves the player's actual mapping producer.
        service.observePlayerDplc(GameId.S1, "sonic", mapping, art.get(mapping));
        var prepared = service.capture();
        assertEquals(mapping, prepared.s1Preparation().mappingFrame());
        List<GapEdge> history = List.copyOf(service.gapEdges());
        var registry = h.mode().getRewindRegistry();
        var saved = registry.capture();
        assertTrue(saved.containsKey("object-manager"));
        assertTrue(saved.containsKey(DynamicArtLifecycleService.REWIND_KEY));
        var objects = h.mode().getLevelManager().getObjectManager();
        objects.removeDynamicObject(h.card());
        service.observePlayerDplc(GameId.S1, "sonic", 0x35, art.get(0x35));
        registry.restore(saved);
        var restored = objects.activeObjectsOfType(Sonic1ResultsScreenObjectInstance.class).getFirst();
        assertNotSame(h.card(), restored, "restore must recreate the removed fixed-slot card");
        assertEquals(Sonic1FixedEndCardSlot.SLOT, restored.getSlotIndex());
        assertEquals(180, timer(restored));
        assertEquals(prepared.s1Preparation(), service.capture().s1Preparation());
        assertEquals(history, service.gapEdges());
        assertFalse(h.mode().getFadeManager().hasPendingCompletion(),
                "the snapshot is before any transient transition callback exists");

        loop.step();
        assertTrue(h.mode().getLevelManager().isLevelInactiveForTransition());
        assertTrue(h.mode().getFadeManager().hasPendingCompletion());
        List<GapEdge> journal = List.copyOf(service.gapEdges());
        assertEquals(history, journal.subList(0, history.size()));
        List<GapEdge> pair = journal.subList(history.size(), journal.size());
        assertEquals(2, pair.size(), "one exact transfer, without a filtered journal");
        assertEquals(List.of("submitted", "completed"), pair.stream().map(GapEdge::phase).toList());
        for (int i = 0; i < 2; i++) {
            assertEquals(prepared.nextTransferId(), pair.get(i).transferId());
            assertEquals(prepared.nextEdgeOrdinal() + i, pair.get(i).edgeOrdinal());
            assertEquals(mapping, pair.get(i).mappingFrame());
        }
        assertEquals(mapping == 9 ? List.of(
                Request.rom(0x23090, 84, 0xF000, 192),
                Request.rom(0x23270, 99, 0xF0C0, 192),
                Request.rom(0x23330, 105, 0xF180, 192),
                Request.rom(0x233F0, 111, 0xF240, 64))
                : List.of(Request.rom(0x27F90, 716, 0xF000, 512)), pair.getFirst().requests());
        assertEquals(List.of(Request.ram(0xC800, 0xF000, 0x2E0)), pair.getLast().requests());
        int executed = objects.getFrameCounter();
        for (int frame = 0; frame < 4; frame++) {
            loop.step();
            assertEquals(executed, objects.getFrameCounter(), "the real blocking fade holds source objects");
            assertEquals(journal, service.gapEdges(), "palette fade adds no player-art transfer");
        }
    }

    @Test
    void specialStageModeRequestAlsoBelongsToTheFollowingObjectRoutine() throws Exception {
        Harness h = open(0, 0, true);
        armLastWaitPass(h.card());
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, canonical(h));
        assertFalse(h.mode().getLevelManager().getTransitions().isSpecialStageRequested());
        assertFalse(h.card().isDestroyed());
        assertFalse(h.mode().getFadeManager().isActive());
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, canonical(h));
        assertTrue(h.mode().getLevelManager().getTransitions().isSpecialStageRequested());
        assertTrue(h.card().isDestroyed());
        assertFalse(h.mode().getFadeManager().isActive(), "GM_Special owns its white-out, not this card");
    }

    @Test
    void sbz2ExpiryStillArmsSlideOutWithoutAddingASecondWaitPass() throws Exception {
        Harness h = open(5, 1, false);
        int[] positions = positions(h.card());
        int[] targets = {0x124, 0x120, 0x14C, 0x120, 0x120, 0x120, 0x14C};
        System.arraycopy(targets, 0, positions, 0, targets.length);
        armLastWaitPass(h.card());
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, canonical(h));
        assertArrayEquals(targets, positions);
        assertEquals(5, state(h.card()), "expiry already arms Got_SBZ2_MoveOut");
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, canonical(h));
        assertEquals(0x140, positions[5], "the following native routine moves by $20 immediately");
        assertFalse(h.mode().getLevelManager().isLevelInactiveForTransition());
        assertFalse(h.mode().getFadeManager().isActive());
    }

    @Test
    void isolatedHostReturnKeepsItsExistingExpiryAndBranchPriority() throws Exception {
        Harness h = open(0, 0, false);
        var services = new TestObjectServices().withLevelManager(h.mode().getLevelManager())
                .withRunPolicy(GameplayRunPolicy.isolatedAct());
        var card = new Sonic1ResultsScreenObjectInstance(30, 7, 1);
        card.setServices(services);
        card.setSpecialStageAfter(true);
        armLastWaitPass(card);
        card.update(1, null);
        assertTrue(services.hostReturnRequested());
        assertTrue(card.isDestroyed());
        assertFalse(h.mode().getLevelManager().getTransitions().isSpecialStageRequested());
        assertFalse(h.mode().getFadeManager().isActive());
    }

    @Test
    void retailWaitAndRestartInstructionsRemainIndependentOfEngineTiming() throws IOException {
        var rom = TestEnvironment.currentRom();
        assertArrayEquals(java.util.HexFormat.of().parseHex("5368001e66045428002460000fc4"),
                rom.readBytes(0xCCC2, 14));
        assertArrayEquals(java.util.HexFormat.of().parseHex("31fc0001fe02"), rom.readBytes(0xCD78, 6));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeExitOwnerPreservesPublishedPrefixAcrossClosingAndGapTokens(boolean special) {
        Harness h = open(0, 0, special);
        armLastWaitPass(h.card());
        DynamicArtDiagnosticsSnapshot prefix = publishExternalPrefix(h);
        var service = h.mode().dynamicArtLifecycle();
        assertFalse(h.mode().getLevelManager().isLevelInactiveForTransition());
        assertFalse(h.mode().getFadeManager().isActive());
        assertFalse(h.mode().getLevelManager().getTransitions().isSpecialStageRequested());

        // Independent ROM-backed work staged through the production port;
        // the actual GameLoop token must issue it before Got_NextLevel runs.
        service.observePlayerDplc(GameId.S1, "sonic", 0x31, art.get(0x31));
        var prepared = service.capture();
        List<GapEdge> history = List.copyOf(service.gapEdges());
        loop.step();
        DynamicArtDiagnosticsSnapshot afterExit = service.latestSnapshot();
        boolean closedAtExit = !service.isComparisonSegmentOpen();
        List<GapEdge> exitJournal = List.copyOf(service.gapEdges());
        var afterExitState = service.capture();
        assertEquals(special ? GameMode.SPECIAL_STAGE : GameMode.LEVEL, loop.getCurrentGameMode());
        if (special) {
            assertTrue(loop.getActiveSpecialStageProvider().isEntryFadeToWhiteActive(),
                    "the actual provider owns the entry white-out without a completion callback");
            assertTrue(h.card().isDestroyed());
        } else {
            assertTrue(h.mode().getFadeManager().hasPendingCompletion(),
                    "the actual restart owner must have entered its native blocking fade");
            assertTrue(h.mode().getLevelManager().isLevelInactiveForTransition());
        }

        int executed = h.mode().getLevelManager().getObjectManager().getFrameCounter();
        loop.step();
        DynamicArtDiagnosticsSnapshot afterGap = service.latestSnapshot();
        List<GapEdge> gapJournal = List.copyOf(service.gapEdges());
        assertEquals(executed, h.mode().getLevelManager().getObjectManager().getFrameCounter(),
                "the following real fade token must not execute source objects");

        // No structural close is supplied by this test. A missing caller hook
        // must stay red, even though the physical DMA itself still executed.
        assertAll("actual native " + (special ? "special entry" : "restart")
                        + ": prefix=" + prefix + ", afterExit=" + afterExit
                        + ", afterGap=" + afterGap + ", exitJournal=" + exitJournal,
                () -> assertTrue(closedAtExit, "native exit owner must close before token publication"),
                () -> assertEquals(prefix, afterExit, "closing token must retain the saved publication"),
                () -> assertEquals(prefix, afterGap, "the next gap token must retain that publication"),
                () -> assertFalse(service.isComparisonSegmentOpen(), "external ownership must prevent reopening"),
                () -> assertEquals(prepared.nextTransferId() + 1, afterExitState.nextTransferId(),
                        "closing VBlank must issue the staged transfer"),
                () -> assertEquals(prepared.nextEdgeOrdinal() + 2, afterExitState.nextEdgeOrdinal()),
                () -> assertEquals(history, exitJournal.subList(0, history.size())),
                () -> assertTransferPair(exitJournal.subList(history.size(), exitJournal.size()),
                        prepared.nextTransferId(), prepared.nextEdgeOrdinal(), 0x31,
                        List.of(Request.rom(0x27F90, 716, 0xF000, 512))),
                () -> {
                    if (special) {
                        // The special-stage owner may submit further real work.
                        // Preserve the complete closing journal as a prefix;
                        // this check does not qualify the entry handler phase.
                        assertEquals(exitJournal, gapJournal.subList(0, exitJournal.size()),
                                "the next owner must retain every closing transfer");
                    } else {
                        assertEquals(exitJournal, gapJournal, "palette fade adds no player-art transfer");
                    }
                });
    }

    @Test
    void refusedSpecialEntryDoesNotCloseTheSourceWindow() {
        Harness h = open(0, 0, true);
        armLastWaitPass(h.card());
        DynamicArtDiagnosticsSnapshot prefix = publishExternalPrefix(h);
        field(h.card(), Sonic1ResultsScreenObjectInstance.class, "stageEntryPermit", 0L);

        loop.step();

        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        assertFalse(h.mode().getLevelManager().getTransitions().isSpecialStageRequested());
        assertTrue(h.mode().dynamicArtLifecycle().isComparisonSegmentOpen(),
                "an unadmitted exit cannot take publication ownership");
        assertEquals(prefix.deliverySerial() + 1,
                h.mode().dynamicArtLifecycle().latestSnapshot().deliverySerial());
    }

    @Test
    void nonExitOwnerStillPublishesTheNextPhysicalToken() {
        Harness h = open(0, 0, false);
        DynamicArtDiagnosticsSnapshot prefix = publishExternalPrefix(h);
        var service = h.mode().dynamicArtLifecycle();
        List<GapEdge> history = List.copyOf(service.gapEdges());
        service.observePlayerDplc(GameId.S1, "sonic", 0x31, art.get(0x31));
        var prepared = service.capture();

        loop.step();

        DynamicArtDiagnosticsSnapshot next = service.latestSnapshot();
        assertTrue(service.isComparisonSegmentOpen());
        assertEquals(prefix.frame() + 1, next.frame());
        assertEquals(prefix.deliverySerial() + 1, next.deliverySerial());
        assertEquals(prefix.segmentGeneration(), next.segmentGeneration());
        assertEquals(List.of("submitted", "completed"),
                next.edges().stream().map(DynamicArtDiagnosticsSnapshot.Edge::phase).toList());
        for (int i = 0; i < next.edges().size(); i++) {
            var edge = next.edges().get(i);
            assertEquals(prepared.nextTransferId(), edge.transferId());
            assertEquals(prepared.nextEdgeOrdinal() + i, edge.edgeOrdinal());
            assertEquals(0x31, edge.mappingFrame());
            assertEquals(next.frame(), edge.publicationFrame());
            assertFalse(edge.terminalForwarded());
        }
        assertEquals(List.of(Request.rom(0x27F90, 716, 0xF000, 512)),
                next.edges().getFirst().requests());
        assertEquals(List.of(Request.ram(0xC800, 0xF000, 0x2E0)), next.edges().getLast().requests());
        assertEquals(history, service.gapEdges());
        assertFalse(h.mode().getFadeManager().isActive());
        assertFalse(h.mode().getLevelManager().isLevelInactiveForTransition());
    }

    @Test
    void standaloneTerminalCompletionKeepsStagedArtForTheNextPhysicalClaim() {
        Harness h = open(0, 0, false);
        DynamicArtDiagnosticsSnapshot prefix = publishExternalPrefix(h);
        var service = h.mode().dynamicArtLifecycle();
        List<GapEdge> history = List.copyOf(service.gapEdges());
        service.observePlayerDplc(GameId.S1, "sonic", 0x31, art.get(0x31));
        var prepared = service.capture();

        // This control explicitly owns a standalone structural terminal. It
        // exercises that API separately; neither Results exit above calls it.
        h.mode().serviceTerminalDynamicArtVBlank();
        h.mode().endDynamicArtComparisonSegment();

        assertFalse(service.isComparisonSegmentOpen());
        assertEquals(prefix, service.latestSnapshot());
        assertEquals(prepared.s1Preparation(), service.capture().s1Preparation(),
                "terminal completion cannot invent the physical S1 buffer DMA");
        assertEquals(prepared.nextTransferId(), service.capture().nextTransferId());
        assertEquals(prepared.nextEdgeOrdinal(), service.capture().nextEdgeOrdinal());
        assertEquals(history, service.gapEdges());

        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, canonical(h));

        assertEquals(prefix, service.latestSnapshot());
        assertFalse(service.isComparisonSegmentOpen(), "the external lease must survive the standalone close");
        List<GapEdge> journal = List.copyOf(service.gapEdges());
        assertEquals(history, journal.subList(0, history.size()));
        assertTransferPair(journal.subList(history.size(), journal.size()),
                prepared.nextTransferId(), prepared.nextEdgeOrdinal(), 0x31,
                List.of(Request.rom(0x27F90, 716, 0xF000, 512)));
    }

    private DynamicArtDiagnosticsSnapshot publishExternalPrefix(Harness h) {
        var service = h.mode().dynamicArtLifecycle();
        assertFalse(service.isComparisonSegmentOpen(), "acquiring the lease alone opens no segment");
        service.openComparisonSegment();
        assertTrue(service.isComparisonSegmentOpen());
        assertFalse(service.latestSnapshot().published());
        service.observePlayerDplc(GameId.S1, "sonic", 9, art.get(9));
        loop.step();
        DynamicArtDiagnosticsSnapshot prefix = service.latestSnapshot();
        assertTrue(prefix.published());
        assertEquals(List.of("submitted", "completed"),
                prefix.edges().stream().map(DynamicArtDiagnosticsSnapshot.Edge::phase).toList());
        assertEquals(List.of(9, 9), prefix.edges().stream()
                .map(DynamicArtDiagnosticsSnapshot.Edge::mappingFrame).toList());
        assertTrue(prefix.outstandingTransferIds().isEmpty());
        return prefix;
    }

    private static void assertTransferPair(List<GapEdge> pair, long transferId,
                                           long firstOrdinal, int mapping, List<Request> submitted) {
        assertEquals(2, pair.size(), "one exact transfer in the full unfiltered gap journal");
        assertEquals(List.of("submitted", "completed"), pair.stream().map(GapEdge::phase).toList());
        for (int i = 0; i < pair.size(); i++) {
            assertEquals(transferId, pair.get(i).transferId());
            assertEquals(firstOrdinal + i, pair.get(i).edgeOrdinal());
            assertEquals(mapping, pair.get(i).mappingFrame());
        }
        assertEquals(submitted, pair.getFirst().requests());
        assertEquals(List.of(Request.ram(0xC800, 0xF000, 0x2E0)), pair.getLast().requests());
    }

    private Harness open(int zone, int act, boolean special) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
        var mode = fixture.gameplayMode();
        var level = mode.getLevelManager();
        level.consumeTitleCardRequest();
        mode.plcFrameLifecycle().acquireExternalComparisonSegmentOwnership();
        InputHandler input = new InputHandler();
        loop = new GameLoop(input);
        loop.setGameplayMode(mode);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        loop.step();
        loop.step();
        var card = Sonic1FixedEndCardSlot.claim(level.getObjectManager().getObjectServices(),
                new Sonic1FixedEndCardSlot.ResultsData(30, 7, act + 1, special)).requireCard();
        card.markResultsPlcCommitted();
        for (int frame = 0; timer(card) == 0; frame++) {
            assertTrue(frame < 512, "the actual routine-zero PLC gate must release");
            loop.step();
        }
        assertEquals(Sonic1FixedEndCardSlot.SLOT, card.getSlotIndex());
        return new Harness(mode, input, card);
    }

    private static LevelFrameResult canonical(Harness h) {
        return h.mode().plcFrameLifecycle().runLogicalIteration(h.mode().getFadeManager()::update,
                frame -> LevelFrameStep.execute(LevelFrameContext.from(h.mode()), frame,
                        PlcLifecyclePhase.ORDINARY_LEVEL, h.mode().getLevelManager(), h.mode().getCamera(),
                        () -> h.mode().getSpriteManager().update(h.input()), (name, step) -> step.run()));
    }

    private static void armLastWaitPass(Sonic1ResultsScreenObjectInstance card) {
        field(card, AbstractResultsScreen.class, "state", 3);
        field(card, AbstractResultsScreen.class, "stateTimer", 179);
    }

    private static int timer(Sonic1ResultsScreenObjectInstance card) {
        return (int) read(card, AbstractResultsScreen.class, "stateTimer");
    }

    private static int state(Sonic1ResultsScreenObjectInstance card) {
        return (int) read(card, AbstractResultsScreen.class, "state");
    }

    private static int[] positions(Sonic1ResultsScreenObjectInstance card) {
        return (int[]) read(card, Sonic1ResultsScreenObjectInstance.class, "elemCurrentX");
    }

    private static Object read(Object target, Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void field(Object target, Class<?> owner, String name, Object value) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private record Harness(GameplayModeContext mode, InputHandler input, Sonic1ResultsScreenObjectInstance card) { }
}
