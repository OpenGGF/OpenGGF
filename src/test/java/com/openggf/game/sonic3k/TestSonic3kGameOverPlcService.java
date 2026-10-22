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
    void moduleDispatcherPreservesOnePhysicalJobAcrossPhaseSwitchesAndOneRewindOwner() throws Exception {
        var module = new Sonic3kGameModule();
        assertNotNull(module.createGame(TestEnvironment.currentRom()));
        var title = module.getGameService(Sonic3kLevelTitlePlcService.class);
        var event = module.getGameService(Sonic3kGameOverPlcService.class);
        var dispatch = module.getGameService(PlcLifecycleService.class);
        assertSame(title, dispatch, "the native lifecycle publication retains its existing adapter identity");
        assertEquals(List.of(title), module.rewindAdapters(),
                "one registered adapter captures the shared physical queue exactly once");
        assertEquals(List.of("s3k-level-title-plc"),
                module.rewindAdapters().stream().map(adapter -> adapter.key()).toList());

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

        title.restore(prepared);
        dispatch.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(31, title.capture().activeEntry().remainingPatterns(),
                "the existing title rewind adapter also restores event-owned progress");
        title.beginSpecialStageResults();
        assertNull(title.capture().activeEntry());
        assertNotEquals(List.of(GAME_OVER), title.capture().queuedEntries(),
                "the next native queue clear also replaces an event job");
        title.restore(prepared);
        assertTrue(event.isBusy());
        module.resetModuleScopedState();
        assertFalse(event.isBusy(), "module reset clears the one physical FIFO for both owners");
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
