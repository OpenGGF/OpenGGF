package com.openggf.game.sonic1.resources;

import com.openggf.data.Rom;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.QueueDiagnosticSnapshot;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.NemesisPlcServiceQueue;
import com.openggf.level.resources.PlcParser;
import com.openggf.level.resources.PlcParser.PlcDefinition;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1PlcService {
    private Rom rom;

    @BeforeEach
    void setUp() {
        rom = TestEnvironment.currentRom();
    }

    @Test
    void parsesSonic1TableAndMapsAppendReplaceAndClear() throws IOException {
        NemesisPlcServiceQueue queue = new NemesisPlcServiceQueue();
        Sonic1PlcService service = new Sonic1PlcService(rom, queue);
        PlcDefinition first = firstNonEmptyPlc();
        PlcDefinition replacement = nextNonEmptyPlc(first.plcId());

        service.append(first.plcId());
        assertEquals(entriesFor(first), queue.capture().queuedEntries());

        service.append(first.plcId());
        assertEquals(entriesFor(first), queue.capture().queuedEntries().subList(0, first.entries().size()));
        assertEquals(entriesFor(first), queue.capture().queuedEntries().subList(
                first.entries().size(), first.entries().size() * 2));

        service.replaceQueued(replacement.plcId());
        assertEquals(entriesFor(replacement), queue.capture().queuedEntries());

        service.clearQueued();
        assertFalse(service.isBusy());
        assertTrue(queue.capture().queuedEntries().isEmpty());
    }

    @Test
    void requiresPreparationAndHonorsThreeAndNinePatternBudgets() throws IOException {
        PlcDefinition definition = firstPlcWhoseFirstEntryHasAtLeast(10);
        int totalPatterns = NemesisPlcPatternCounts.derive(rom, definition).getFirst();

        NemesisPlcServiceQueue levelQueue = new NemesisPlcServiceQueue();
        Sonic1PlcService levelService = new Sonic1PlcService(rom, levelQueue);
        levelService.append(definition.plcId());
        levelService.serviceLevelVBlank();
        assertEquals(totalPatterns, levelQueue.capture().queuedEntries().getFirst().remainingPatterns(),
                "VBlank service must not implicitly prepare a FIFO head");
        levelService.prepare();
        levelService.serviceLevelVBlank();
        assertEquals(totalPatterns - 3, levelQueue.capture().activeEntry().remainingPatterns());

        NemesisPlcServiceQueue fastQueue = new NemesisPlcServiceQueue();
        Sonic1PlcService fastService = new Sonic1PlcService(rom, fastQueue);
        fastService.append(definition.plcId());
        fastService.prepare();
        fastService.serviceFastVBlank();
        assertEquals(totalPatterns - 9, fastQueue.capture().activeEntry().remainingPatterns());
    }

    @Test
    void newPlcAndClearPlcCancelPartiallyServicedDecoder()
            throws IOException {
        NemesisPlcServiceQueue queue = new NemesisPlcServiceQueue();
        Sonic1PlcService service = new Sonic1PlcService(rom, queue);
        PlcDefinition first = firstPlcWhoseFirstEntryHasAtLeast(10);
        PlcDefinition replacement = nextNonEmptyPlc(first.plcId());

        service.append(first.plcId());
        assertTrue(service.isBusy(), "an unprepared FIFO descriptor keeps the whole buffer busy");
        service.prepare();
        assertTrue(service.isBusy(), "a prepared decoder keeps the whole buffer busy");

        service.serviceLevelVBlank();
        assertNotNull(service.capture().activeEntry(), "replacement interrupts a partial decode");
        service.replaceQueued(replacement.plcId());
        assertNull(service.capture().activeEntry());
        assertEquals(replacement.entries().size(), service.capture().queuedEntries().size());
        assertEquals(replacement.entries().getFirst().romAddr(),
                service.capture().queuedEntries().getFirst().sourceAddress());
        service.prepare();
        service.serviceLevelVBlank();
        service.clearQueued();
        assertFalse(service.isBusy());
        service.append(first.plcId());
        service.prepare();
        service.transact(Sonic1PlcService.clear(), Sonic1PlcService.replace(replacement.plcId()));
        assertNull(service.capture().activeEntry());
        assertEquals(replacement.entries().size(), service.capture().queuedEntries().size());
    }

    @Test
    void countedEntryWaitServicesThreePatternsWithoutAnyPreparationTail() throws IOException {
        PlcDefinition definition = firstPlcWhoseFirstEntryHasAtLeast(10);
        int patterns = NemesisPlcPatternCounts.derive(rom, definition).getFirst();
        var service = new Sonic1PlcService(rom);
        var lifecycle = new com.openggf.game.resources.PlcFrameLifecycleCoordinator(service);
        service.append(definition.plcId());
        assertFalse(service.hasPreparationBoundary(PlcLifecyclePhase.LEVEL_ENTRY_WAIT));
        // Level_Delay (sonic.asm:2957-2967) selects the level VBlank but never RunPLC.
        lifecycle.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.LEVEL_ENTRY_WAIT);
            return null;
        });
        assertNull(service.capture().activeEntry(), "a counted wait cannot arm a queued head");
        service.prepare();
        lifecycle.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.LEVEL_ENTRY_WAIT);
            return null;
        });
        assertEquals(patterns - 3, service.capture().activeEntry().remainingPatterns());
    }

    @Test
    void rejectsOutOfRangeIdsBeforeMutatingQueuedState() throws IOException {
        NemesisPlcServiceQueue queue = new NemesisPlcServiceQueue();
        Sonic1PlcService service = new Sonic1PlcService(rom, queue);
        PlcDefinition definition = firstNonEmptyPlc();
        service.append(definition.plcId());
        var before = queue.capture();

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> service.append(-1));
        assertTrue(negative.getMessage().contains("Sonic 1"));
        assertTrue(negative.getMessage().contains("-1"));
        assertEquals(before, queue.capture());

        IllegalArgumentException upperBound = assertThrows(IllegalArgumentException.class,
                () -> service.replaceQueued(Sonic1Constants.ART_LOAD_CUES_ENTRY_COUNT));
        assertTrue(upperBound.getMessage().contains("Sonic 1"));
        assertTrue(upperBound.getMessage().contains(
                String.valueOf(Sonic1Constants.ART_LOAD_CUES_ENTRY_COUNT)));
        assertEquals(before, queue.capture());
    }

    @Test
    void moduleRegistersOneRomBoundServiceForTheActiveGame() {
        Sonic1GameModule module = new Sonic1GameModule();
        module.createGame(rom);

        Sonic1PlcService service = assertInstanceOf(Sonic1PlcService.class,
                module.getGameService(Sonic1PlcService.class));
        assertSame(service, module.getGameService(Sonic1PlcService.class));
        assertSame(service, module.getGameService(PlcLifecycleService.class));
    }

    @Test
    void diagnosticsExposeWaitingActiveAndFrameServiceState() throws IOException {
        PlcDefinition definition = firstPlcWhoseFirstEntryHasAtLeast(10);
        Sonic1PlcService service = new Sonic1PlcService(rom);
        service.append(definition.plcId());

        QueueDiagnosticSnapshot waiting =
                service.captureQueueDiagnostics().getFirst();
        assertTrue(waiting.busy());
        assertFalse(waiting.prepared());
        assertEquals(-1, waiting.activeSource());
        assertEquals(definition.entries().size(),
                waiting.queuedFingerprints().size());

        service.prepare();
        service.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        QueueDiagnosticSnapshot active =
                service.captureQueueDiagnostics().getFirst();
        assertTrue(active.prepared());
        assertEquals(-1, active.activeSource());
        assertEquals(-1, active.activeDestination());
        assertEquals(-1, active.activeTotalWork());
        assertTrue(active.serviceObservations().isEmpty());
    }

    private PlcDefinition firstNonEmptyPlc() throws IOException {
        return plcDefinitions().stream()
                .filter(definition -> !definition.entries().isEmpty())
                .findFirst()
                .orElseThrow();
    }

    private PlcDefinition nextNonEmptyPlc(int excludedId) throws IOException {
        return plcDefinitions().stream()
                .filter(definition -> definition.plcId() != excludedId)
                .filter(definition -> !definition.entries().isEmpty())
                .findFirst()
                .orElseThrow();
    }

    private PlcDefinition firstPlcWhoseFirstEntryHasAtLeast(int minimumPatterns)
            throws IOException {
        for (PlcDefinition definition : plcDefinitions()) {
            if (!definition.entries().isEmpty()
                    && NemesisPlcPatternCounts.derive(rom, definition).getFirst() >= minimumPatterns) {
                return definition;
            }
        }
        throw new AssertionError("Sonic 1 ROM must contain a PLC entry large enough for budget testing");
    }

    private List<PlcDefinition> plcDefinitions() throws IOException {
        return java.util.stream.IntStream.rangeClosed(0, 0x1C)
                .mapToObj(id -> parse(id))
                .toList();
    }

    private PlcDefinition parse(int plcId) {
        try {
            return PlcParser.parse(rom, Sonic1Constants.ART_LOAD_CUES_ADDR, plcId);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private List<com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot.Entry> entriesFor(
            PlcDefinition definition) throws IOException {
        List<Integer> counts = NemesisPlcPatternCounts.derive(rom, definition);
        return java.util.stream.IntStream.range(0, definition.entries().size())
                .mapToObj(index -> new com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot.Entry(
                        definition.entries().get(index).romAddr(),
                        definition.entries().get(index).tileIndex(),
                        counts.get(index), counts.get(index)))
                .toList();
    }
}
