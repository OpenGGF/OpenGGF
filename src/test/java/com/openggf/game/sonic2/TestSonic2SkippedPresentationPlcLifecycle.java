package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.PlcParser;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SkippedPresentationPlcLifecycle {
    private Rom rom;

    @BeforeEach
    void setUp() {
        rom = TestEnvironment.currentRom();
    }

    @Test
    void releaseDrainsInitialQueueThenAppendsHeaderSecondaryWithoutServicingIt() throws Exception {
        Sonic2PlcService service = new Sonic2PlcService(rom);
        service.transact(
                Sonic2PlcService.clearOperation(),
                Sonic2PlcService.appendOperation(Sonic2Constants.PLC_EHZ1),
                Sonic2PlcService.appendOperation(Sonic2Constants.PLC_STD2));

        Sonic2LevelInitProfile.completeInitialPresentationPlcs(rom, service, 0);

        assertEquals(expectedDescriptors(Sonic2Constants.PLC_EHZ2),
                service.capture().queuedEntries(),
                "loadZoneBlockMaps appends the header secondary after Level_TtlCard drains");
    }

    private List<NemesisPlcQueueSnapshot.Entry> expectedDescriptors(int id) throws Exception {
        var definition = PlcParser.parse(rom, Sonic2Constants.ART_LOAD_CUES_ADDR, id);
        var counts = NemesisPlcPatternCounts.derive(rom, definition);
        List<NemesisPlcQueueSnapshot.Entry> result = new ArrayList<>();
        for (int index = 0; index < definition.entries().size(); index++) {
            var entry = definition.entries().get(index);
            result.add(new NemesisPlcQueueSnapshot.Entry(
                    entry.romAddr(), entry.tileIndex(), counts.get(index), counts.get(index)));
        }
        return result;
    }
}
