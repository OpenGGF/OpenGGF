package com.openggf.game.sonic3k;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.save.SaveReason;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kGameOverCardObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.resources.NemesisPlcServiceQueue;
import com.openggf.level.resources.PlcParser.PlcDefinition;
import com.openggf.level.resources.PlcParser.PlcEntry;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kGameOverPlcService {
    // Actual locked-on PLC_03 at $924CC and ArtNem_GameOver's $8022 header.
    // Executing the ROM's $1780 init / $17F4 service routines yields twelve
    // VBlanks, with the final service writing one pattern instead of three.
    private static final NemesisPlcQueueSnapshot.Entry GAME_OVER =
            new NemesisPlcQueueSnapshot.Entry(0x191DE4, 0x79C, 34, 34);

    @Test
    void loadPlcTwoAbortsActiveWorkAndSubmitsTheActualRomDescriptor() throws Exception {
        var queue = new NemesisPlcServiceQueue();
        queue.append(new PlcDefinition(-1, List.of(new PlcEntry(0x100, 0x10))), List.of(99));
        queue.prepareHead();
        queue.servicePatterns(3);
        assertEquals(96, queue.capture().activeEntry().remainingPatterns());
        var service = new Sonic3kGameOverPlcService(TestEnvironment.currentRom(), queue);

        service.beginGameOver();

        assertTrue(service.isBusy());
        assertNull(queue.capture().activeEntry(), "Load_PLC_2 clears active decoder variables");
        assertEquals(List.of(GAME_OVER), queue.capture().queuedEntries());
        service.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(31, queue.capture().activeEntry().remainingPatterns());
        service.beginGameOver();
        assertEquals(new NemesisPlcQueueSnapshot(null, List.of(GAME_OVER)), queue.capture(),
                "a new producer invocation replaces an in-flight job rather than appending it");
    }

    @Test
    void ordinaryAndPauseVblanksOnlyServicePreparedWorkAndLagDoesNothing() throws Exception {
        var queue = new NemesisPlcServiceQueue();
        var service = new Sonic3kGameOverPlcService(TestEnvironment.currentRom(), queue);
        service.beginGameOver();
        var submitted = queue.capture();

        service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        service.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE);
        service.prepareAfterLoop(PlcLifecyclePhase.NORMAL_PAUSE);
        assertEquals(submitted, queue.capture(), "neither service nor pause arms an unprepared head");
        assertFalse(service.hasPreparationBoundary(PlcLifecyclePhase.NORMAL_PAUSE));

        service.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        var prepared = queue.capture();
        service.serviceVBlank(PlcLifecyclePhase.LAG);
        service.prepareAfterLoop(PlcLifecyclePhase.LAG);
        assertEquals(prepared, queue.capture());
        service.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE);
        assertEquals(31, queue.capture().activeEntry().remainingPatterns(),
                "outside special stages VInt_10 falls through to VInt_8");

        var inFlight = queue.capture();
        service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(28, queue.capture().activeEntry().remainingPatterns());
        queue.restore(inFlight);
        service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(28, queue.capture().activeEntry().remainingPatterns(),
                "restoring shared physical progress does not resubmit the job");
    }

    @Test
    void fadeUsesSixPatternsAndUnownedPhasesLeaveTheSharedQueueUntouched() throws Exception {
        var queue = new NemesisPlcServiceQueue();
        var service = new Sonic3kGameOverPlcService(TestEnvironment.currentRom(), queue);
        service.beginGameOver();
        service.prepareAfterLoop(PlcLifecyclePhase.PALETTE_FADE);
        var prepared = queue.capture();
        for (var phase : PlcLifecyclePhase.values()) {
            if (phase == PlcLifecyclePhase.ORDINARY_LEVEL
                    || phase == PlcLifecyclePhase.NORMAL_PAUSE
                    || phase == PlcLifecyclePhase.PALETTE_FADE) continue;
            assertFalse(service.hasPreparationBoundary(phase), phase.name());
            service.serviceVBlank(phase);
            service.prepareAfterLoop(phase);
            assertEquals(prepared, queue.capture(), phase.name());
        }
        service.serviceVBlank(PlcLifecyclePhase.PALETTE_FADE);
        assertEquals(28, queue.capture().activeEntry().remainingPatterns());
    }

    @Test
    void worldDispatcherPreservesOnePhysicalJobAcrossPhaseSwitchesAndOneRewindOwner() throws Exception {
        var module = new Sonic3kGameModule();
        var rom = TestEnvironment.currentRom();
        assertNotNull(module.createGame(rom));
        assertNull(module.getGameService(Sonic3kLevelTitlePlcService.class));
        assertNull(module.getGameService(Sonic3kGameOverPlcService.class));
        assertNull(module.getGameService(PlcLifecycleService.class));
        assertTrue(module.rewindAdapters().isEmpty(), "the module cannot select or capture another world's FIFO");
        var source = com.openggf.game.StockGameDataSources.pinned(rom, module);
        var world = new com.openggf.game.session.WorldSession(module, module, source, null);
        var otherWorld = new com.openggf.game.session.WorldSession(module, module, source, null);
        var title = com.openggf.game.session.WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class);
        var event = com.openggf.game.session.WorldPlcLifecycleAccess.require(world, Sonic3kGameOverPlcService.class);
        var dispatch = com.openggf.game.session.WorldPlcLifecycleAccess.require(world, PlcLifecycleService.class);
        var otherTitle = com.openggf.game.session.WorldPlcLifecycleAccess.require(otherWorld, Sonic3kLevelTitlePlcService.class);
        var otherEvent = com.openggf.game.session.WorldPlcLifecycleAccess.require(otherWorld, Sonic3kGameOverPlcService.class);
        assertSame(title, dispatch, "the world lifecycle retains its original title facade/adapter identity");
        assertNotSame(title, otherTitle);
        assertNotSame(event, otherEvent, "a reused module cannot share its event delegate across worlds");
        otherEvent.beginGameOver();
        var otherQueue = otherTitle.capture();
        var otherRecipes = otherTitle.recipeState().capture();
        var registry = new com.openggf.game.rewind.RewindRegistry();
        com.openggf.game.session.WorldPlcLifecycleAccess.register(world, registry);
        var registered = registry.capture();
        assertEquals(1, registered.entries().keySet().stream().filter(title.key()::equals).count());
        assertEquals(1, registered.entries().keySet().stream().filter(title.recipeState().key()::equals).count());

        title.beginSpecialStageResults();
        dispatch.prepareAfterLoop(PlcLifecyclePhase.SPECIAL_STAGE_RESULTS);
        dispatch.serviceVBlank(PlcLifecyclePhase.SPECIAL_STAGE_RESULTS);
        var priorOwnerWork = title.capture();
        dispatch.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        var afterOrdinary = title.capture();
        assertEquals(priorOwnerWork.activeEntry().sourceAddress(), afterOrdinary.activeEntry().sourceAddress());
        assertEquals(priorOwnerWork.activeEntry().remainingPatterns() - 3,
                afterOrdinary.activeEntry().remainingPatterns(),
                "ordinary service preserves and continues the preceding owner's descriptor");
        assertEquals(priorOwnerWork.queuedEntries(), afterOrdinary.queuedEntries());
        dispatch.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        assertEquals(afterOrdinary.activeEntry().remainingPatterns() - 6,
                title.capture().activeEntry().remainingPatterns());

        event.beginGameOver();
        dispatch.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertNull(title.capture().activeEntry());
        assertEquals(List.of(GAME_OVER), title.capture().queuedEntries());
        dispatch.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        var prepared = title.capture();
        assertEquals(GAME_OVER, prepared.activeEntry());
        assertEquals(Sonic3kLevelTitlePlcService.RecipePurpose.EVENT_READINESS,
                title.recipeState().capture().activePurpose());
        var registeredPrepared = registry.capture();

        // Each opportunity consumes only its native budget, with no reset,
        // second owner drain, or producer re-submission at a phase switch.
        var phases = List.of(PlcLifecyclePhase.LEVEL_TITLE_CARD, PlcLifecyclePhase.ORDINARY_LEVEL,
                PlcLifecyclePhase.SPECIAL_STAGE_RESULTS, PlcLifecyclePhase.PALETTE_FADE,
                PlcLifecyclePhase.NORMAL_PAUSE);
        int[] remaining = {28, 25, 22, 16, 13};
        for (int i = 0; i < phases.size(); i++) {
            dispatch.serviceVBlank(phases.get(i));
            var active = title.capture().activeEntry();
            assertEquals(new NemesisPlcQueueSnapshot.Entry(0x191DE4, 0x79C, 34, remaining[i]),
                    active, phases.get(i).name());
            assertTrue(title.capture().queuedEntries().isEmpty());
            assertTrue(event.isBusy());
        }

        registry.restore(registeredPrepared);
        dispatch.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(31, title.capture().activeEntry().remainingPatterns(),
                "the existing title rewind adapter also restores event-owned progress");
        title.beginSpecialStageResults();
        assertNull(title.capture().activeEntry());
        assertNotEquals(List.of(GAME_OVER), title.capture().queuedEntries(),
                "the next native queue clear also replaces an event job");
        registry.restore(registeredPrepared);
        assertTrue(event.isBusy());
        assertNotNull(module.createGame(rom), "game recreation cannot replace a world-owned queue");
        module.resetModuleScopedState();
        assertSame(title, com.openggf.game.session.WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class));
        assertSame(event, com.openggf.game.session.WorldPlcLifecycleAccess.require(world, Sonic3kGameOverPlcService.class));
        assertEquals(prepared, title.capture(), "module recreation/reset cannot clear this world's event");
        assertTrue(event.isBusy());
        assertEquals(otherQueue, otherTitle.capture(), "this world's service/restore cannot touch the other world");
        assertEquals(otherRecipes, otherTitle.recipeState().capture());
    }

    @Test
    void worldEventPurposeCannotArmGenericReadinessOrRetirePhysicalRecipes() throws Exception {
        var module = new Sonic3kGameModule();
        var source = com.openggf.game.StockGameDataSources.pinned(TestEnvironment.currentRom(), module);
        var world = new com.openggf.game.session.WorldSession(module, module, source, null);
        var title = com.openggf.game.session.WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class);
        var event = com.openggf.game.session.WorldPlcLifecycleAccess.require(world, Sonic3kGameOverPlcService.class);
        var readiness = Sonic3kLevelTitlePlcService.RecipePurpose.READINESS;
        var physical = Sonic3kLevelTitlePlcService.RecipePurpose.PHYSICAL_PATTERNS;
        var eventPurpose = Sonic3kLevelTitlePlcService.RecipePurpose.EVENT_READINESS;
        // Identical original descriptors in distinct explicit roles: never infer purpose from bytes.
        title.appendRaw(0x083D74, readiness);
        title.appendRaw(0x083D74);
        title.appendRaw(0x083D74, readiness);
        var queued = title.capture();
        assertEquals(List.of(readiness, physical, readiness), title.recipeState().capture().queuedPurposes());
        title.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        title.prepareAfterLoop(PlcLifecyclePhase.PALETTE_FADE);
        assertEquals(queued, title.capture(), "ordinary/event Init cannot arm or skip generic readiness");
        title.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        int total = title.capture().activeEntry().totalPatterns();
        for (int remaining = total; remaining > 0; remaining -= 6)
            title.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        title.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        var physicalActive = title.capture();
        assertEquals(physical, title.recipeState().capture().activePurpose());
        for (var phase : PlcLifecyclePhase.values()) {
            title.serviceVBlank(phase);
            event.serviceVBlank(phase);
        }
        assertEquals(physicalActive, title.capture(), "no readiness/event phase retires physical patterns");

        event.beginGameOver();
        assertNull(title.capture().activeEntry(), "PLC2 clears the prior physical decoder");
        assertEquals(List.of(GAME_OVER), title.capture().queuedEntries());
        assertNull(title.recipeState().capture().activePurpose());
        assertEquals(List.of(eventPurpose), title.recipeState().capture().queuedPurposes(),
                "PLC2 clears preceding active/queued purposes as well as descriptor counts");
        title.prepareAfterLoop(PlcLifecyclePhase.NORMAL_PAUSE);
        assertNull(title.capture().activeEntry(), "pause cannot arm an event head");
        title.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        title.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(31, title.capture().activeEntry().remainingPatterns());
        title.appendRaw(0x083D74);
        assertEquals(List.of(physical), title.recipeState().capture().queuedPurposes());
        for (int step = 0; step < 11; step++) title.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertNull(title.capture().activeEntry());
        title.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        var afterEvent = title.capture();
        assertEquals(physical, title.recipeState().capture().activePurpose());
        for (var phase : PlcLifecyclePhase.values()) title.serviceVBlank(phase);
        assertEquals(afterEvent, title.capture(), "physical retirement still requires confirmed journal writes");
        for (var phase : List.of(PlcLifecyclePhase.LEVEL_TITLE_CARD, PlcLifecyclePhase.SPECIAL_STAGE_RESULTS,
                PlcLifecyclePhase.ORDINARY_LEVEL, PlcLifecyclePhase.PALETTE_FADE)) {
            event.beginGameOver();
            title.prepareAfterLoop(phase);
            assertEquals(GAME_OVER, title.capture().activeEntry(), "the actual event head arms in " + phase);
            assertEquals(eventPurpose, title.recipeState().capture().activePurpose());
        }
    }

    @Test
    void unavailableWorldRomFailsBeforeClearingExistingCountsOrRecipePurposes() throws Exception {
        var module = new Sonic3kGameModule();
        int[] romRequests = {0};
        var missing = new com.openggf.game.GameDataSource() {
            @Override public java.util.Optional<com.openggf.data.Rom> rom() {
                romRequests[0]++;
                return java.util.Optional.empty();
            }
            @Override public java.io.InputStream openAsset(String path) throws java.io.IOException {
                throw new java.io.IOException("component source has no assets");
            }
            @Override public String identity() { return "component:missing-game-over-rom"; }
        };
        var world = new com.openggf.game.session.WorldSession(module, module, missing, null);
        var title = com.openggf.game.session.WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class);
        var event = com.openggf.game.session.WorldPlcLifecycleAccess.require(world, Sonic3kGameOverPlcService.class);
        assertEquals(0, romRequests[0], "world construction/delegate lookup remains ROM-free");
        // Explicit component snapshot ingress, not a natural event or route qualification.
        var retained = new NemesisPlcQueueSnapshot(null, List.of(GAME_OVER));
        title.restore(retained);
        title.recipeState().restore(new Sonic3kLevelTitlePlcService.Recipes(retained, null,
                List.of(Sonic3kLevelTitlePlcService.RecipePurpose.EVENT_READINESS)));
        var retainedRecipes = title.recipeState().capture();
        assertThrows(java.io.IOException.class, event::beginGameOver);
        assertEquals(1, romRequests[0]);
        assertEquals(retained, title.capture());
        assertEquals(retainedRecipes, title.recipeState().capture(), "failed source resolution cannot clear recipe state");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void productionProviderAndCardConsumersWaitForTheNativeJob(boolean timeOver) {
        var queue = new NemesisPlcServiceQueue();
        var service = new Sonic3kGameOverPlcService(TestEnvironment.currentRom(), queue);
        var services = new RecordingServices(service);
        var lifecycle = new PlcFrameLifecycleCoordinator(service);
        lifecycle.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
            new Sonic3kGameOverFlowProvider().beginGameOverCard(services, timeOver);
            services.updateCards();
            frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
            return null;
        });

        assertEquals(List.of(Sonic3kMusic.GAME_OVER.id), services.music);
        assertEquals(GAME_OVER, queue.capture().activeEntry());
        var word = services.cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD);
        var over = services.cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_OVER);
        assertEquals(timeOver ? 2 : 0, word.getMappingFrame());
        assertEquals(timeOver ? 3 : 1, over.getMappingFrame());
        for (int frameNumber = 1; frameNumber <= 12; frameNumber++) {
            assertEquals(0, word.getRoutine());
            assertEquals(0, over.getRoutine());
            assertFalse(word.isDisplayedThisFrame());
            assertFalse(over.isDisplayedThisFrame());
            assertTrue(services.saves.isEmpty());
            lifecycle.runLogicalIteration(() -> { }, frame -> {
                frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
                services.updateCards();
                frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
                return null;
            });
            if (frameNumber < 12) {
                assertEquals(34 - frameNumber * 3, queue.capture().activeEntry().remainingPatterns());
            }
        }
        assertFalse(service.isBusy());
        assertEquals(2, word.getRoutine());
        assertEquals(2, over.getRoutine());
        assertEquals(0x60, word.getVdpX());
        assertEquals(0x1E0, over.getVdpX());
        assertTrue(word.isDisplayedThisFrame());
        assertTrue(over.isDisplayedThisFrame());
        assertEquals(timeOver ? List.of() : List.of(new SavePoint(
                SaveReason.LIVES_CONTINUES_SAVE, 0, 0, false)), services.saves,
                "only GAME saves, after the queue drains and before its position/routine setup");
    }

    @Test
    void missingProductionOwnerCannotSilentlyReleaseTheCard() {
        var services = new TestObjectServices().withIsolatedObjectManager();
        assertThrows(IllegalStateException.class,
                () -> new Sonic3kGameOverFlowProvider().beginGameOverCard(services, false));
        assertTrue(services.objectManager().getActiveObjects().isEmpty());
        var card = ObjectConstructionContext.construct(services, S3kGameOverCardObjectInstance::new);
        assertThrows(IllegalStateException.class, () -> card.update(0, null));
    }

    private record SavePoint(SaveReason reason, int routine, int x, boolean pending) { }

    private static final class RecordingServices extends TestObjectServices {
        private final Sonic3kGameOverPlcService service;
        private final List<Integer> music = new ArrayList<>();
        private final List<SavePoint> saves = new ArrayList<>();

        private RecordingServices(Sonic3kGameOverPlcService service) {
            this.service = service;
            withIsolatedObjectManager();
        }

        @Override
        public <T> T gameService(Class<T> type) {
            return type == Sonic3kGameOverPlcService.class ? type.cast(service) : null;
        }

        @Override
        public void playMusic(int musicId) {
            music.add(musicId);
            assertFalse(service.isBusy(), "the native producer requests music before Load_PLC_2");
        }

        @Override
        public void requestSessionSave(SaveReason reason) {
            var word = cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD);
            saves.add(new SavePoint(reason, word.getRoutine(), word.getVdpX(), service.isBusy()));
        }

        private S3kGameOverCardObjectInstance cardAt(int slot) {
            return objectManager().activeObjectsOfType(S3kGameOverCardObjectInstance.class).stream()
                    .filter(card -> card.getSlotIndex() == slot).findFirst().orElseThrow();
        }

        private void updateCards() {
            cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_WORD).update(0, null);
            cardAt(Sonic3kConstants.SST_SLOT_GAME_OVER_OVER).update(0, null);
        }
    }
}
