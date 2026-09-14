package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.PlcParser;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** ROM-derived PLC addresses/workloads and production module/rewind ownership. */
@RequiresRom(SonicGame.SONIC_2)
class TestKis2PlcService {
    @TempDir Path tempDir;

    @Test
    void chipTableDecodesEveryListAndRetainsThePatchedStandardEntries() throws Exception {
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        try (Rom rom = Rom.fromReader(dump, "KiS2 PLC test")) {
            for (int id = 0; id < 67; id++) {
                var list = PlcParser.parse(rom, Kis2Constants.ART_LOAD_CUES, id);
                assertEquals(list.entries().size(), NemesisPlcPatternCounts.derive(rom, list).size());
            }
            // PlrList_Std2: checkpoint, monitors, Knuckles monitor patch,
            // then the combined grey shield/stars stream (not two stock jobs).
            var service = new Sonic2PlcService(rom, Kis2Constants.ART_LOAD_CUES);
            service.append(1);
            var entries = service.capture().queuedEntries();
            assertEquals(List.of(0x279A86, 0x279550, 0x33B15E, 0x33AD40),
                    entries.stream().map(e -> e.sourceAddress()).toList());
            assertEquals(66, entries.get(3).totalPatterns());
            assertEquals(0x4BE, entries.get(3).destinationTile());
        }
    }

    @Test
    void physicalQueueAddressesAndServiceProgressSurviveRestore() throws Exception {
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        try (Rom rom = Rom.fromReader(dump, "KiS2 PLC test")) {
            var service = new Sonic2PlcService(rom, Kis2Constants.ART_LOAD_CUES);
            service.append(2); // PlrList_Ehz1: Buzzer, Coconuts, Masher.
            service.prepare();
            assertEquals(0x27B592, service.capture().activeEntry().sourceAddress());
            assertEquals(68, service.capture().activeEntry().remainingPatterns());
            assertEquals(0x27393C, service.capture().queuedEntries().getFirst().sourceAddress());
            var before = service.capture();
            service.serviceLevelVBlank();
            var after = service.capture();
            assertEquals(65, after.activeEntry().remainingPatterns());
            service.restore(before);
            service.serviceLevelVBlank();
            assertEquals(after, service.capture(), "restored queue must replay the same service boundary");
        }
    }

    @Test
    void modulePublishesOneMatchingLifecycleAndRewindOwnerInBothTiers() throws Exception {
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        for (boolean chipAvailable : List.of(false, true)) {
            var base = new Sonic2GameModule();
            var context = new PatchContext(identity -> {
                if (identity == LogicalRom.KIS2) {
                    if (!chipAvailable) throw new IOException("chip absent");
                    return dump;
                }
                return dump.window(0, Kis2Constants.SK_WINDOW_END);
            }, SonicConfigurationService.createStandalone(tempDir));
            var module = new Kis2GameModule(base, context);
            try (Rom s2 = Rom.fromReader(dump.window(0x200000, 0x100000), "S2 test")) {
                module.createGame(s2);
                var service = module.getGameService(Sonic2PlcService.class);
                assertSame(service, module.getGameService(PlcLifecycleService.class));
                assertEquals(List.of(service), module.rewindAdapters().stream()
                        .filter(Sonic2PlcService.class::isInstance).toList());
                if (chipAvailable) assertNotSame(base.getGameService(Sonic2PlcService.class), service);
                else assertSame(base.getGameService(Sonic2PlcService.class), service);
                service.append(1);
                assertEquals(chipAvailable ? 0x279A86 : 0x79A86,
                        service.capture().queuedEntries().getFirst().sourceAddress());
                module.resetModuleScopedState();
                assertNull(module.getGameService(Sonic2PlcService.class));
            }
        }
    }
}
