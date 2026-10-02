package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.NoOpResultsScreen;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import com.openggf.game.timing.HardwareTimingJob;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSpecialStageResultsPreparation {
    @Test
    void ownerIsInstalledOnlyAfterRomArchivesAndPreparedNemesisHeadFinish() throws Exception {
        var rom = TestEnvironment.currentRom();
        var timing = new HardwareTimingService();
        var art = new S3kRuntimeArtCoordinator(timing);
        var nemesis = new Sonic3kLevelTitlePlcService(rom);
        var installations = new AtomicInteger();
        var preparation = new S3kSpecialStageResultsPreparation(rom,
                PlayerCharacter.SONIC_AND_TAILS, art.moduleQueue(), nemesis,
                () -> { installations.incrementAndGet(); return NoOpResultsScreen.INSTANCE; });
        var submitted = timing.capture().jobs();
        assertEquals(List.of(Sonic3kConstants.ART_KOSM_RESULTS_GENERAL_ADDR,
                        Sonic3kConstants.ART_KOSM_SS_RESULTS_SUPER_ADDR,
                        Sonic3kConstants.ART_KOSM_RESULTS_SONIC_ADDR,
                        Sonic3kConstants.ART_KOSM_SS_RESULTS_ADDR),
                submitted.stream().map(HardwareTimingJob.Snapshot::romSourceAddress).toList());
        assertEquals(List.of(0x5B8 * 32, 0x50F * 32, 0x4F1 * 32, 0x523 * 32),
                submitted.stream().map(HardwareTimingJob.Snapshot::destinationAddress).toList());
        assertEquals(List.of(1, 1, 1, 2),
                submitted.stream().map(HardwareTimingJob.Snapshot::moduleCount).toList());
        assertNull(nemesis.capture().activeEntry());
        assertEquals(1, nemesis.capture().queuedEntries().size());
        assertEquals(Sonic3kConstants.ART_NEM_RING_HUD_TEXT_ADDR,
                nemesis.capture().queuedEntries().getFirst().sourceAddress());
        int patternCount = nemesis.capture().queuedEntries().getFirst().totalPatterns();
        int expectedIterations = 1 + (patternCount + 2) / 3;
        assertEquals(38, patternCount, "retail RingHUDText Nemesis header");
        for (int iteration = 1; iteration <= expectedIterations; iteration++) {
            nemesis.serviceVBlank(PlcLifecyclePhase.SPECIAL_STAGE_RESULTS);
            service(art, timing, HardwareServiceBoundary.VINT_SERVICE);
            service(art, timing, HardwareServiceBoundary.POST_OBJECTS);
            service(art, timing, HardwareServiceBoundary.PRE_MAIN_LOOP);
            nemesis.prepareAfterLoop(PlcLifecyclePhase.SPECIAL_STAGE_RESULTS);
            preparation.finishResultsPreparationIteration();
            assertEquals(iteration == expectedIterations ? 1 : 0, installations.get(),
                    "result sprite cannot exist before the native resource tail finishes");
        }
        assertFalse(preparation.isPreparingResults());
        assertFalse(art.moduleQueue().hasPendingPhysicalModules());
        preparation.finishResultsPreparationIteration();
        assertEquals(1, installations.get(), "owner installation is one-shot");
    }

    private static void service(S3kRuntimeArtCoordinator art, HardwareTimingService timing,
            HardwareServiceBoundary boundary) {
        art.beforeTimingService(boundary);
        timing.service(boundary);
        art.afterTimingService(boundary);
    }
}
