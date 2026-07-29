package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.PlcParser;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Native FIFO descriptor coverage for every currently represented S2 producer cue. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2PlcProducerCoverage {
    @BeforeEach
    void createSonic2Services() {
        GameServices.module().createGame(TestEnvironment.currentRom());
    }

    @Test
    void everyAuditedS2CuePublishesItsRomDescriptorsInOperationOrder() throws Exception {
        Sonic2PlcService queue = GameServices.module().getGameService(Sonic2PlcService.class);
        int[] appendIds = {1, 2, 9, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
                50, 51, 52, 55, 57, 58, 59, 61, 62, 63, 64, 65};
        for (int plcId : appendIds) {
            queue.transact(Sonic2PlcService.clearOperation(), Sonic2PlcService.appendOperation(plcId));
            assertEquals(expectedDescriptors(plcId), queue.capture().queuedEntries(),
                    "S2 audited append PLC " + plcId);
        }
        for (int plcId : new int[] {0, 38, 66}) {
            queue.transact(Sonic2PlcService.replaceOperation(plcId));
            assertEquals(expectedDescriptors(plcId), queue.capture().queuedEntries(),
                    "S2 audited replace PLC " + plcId);
        }
    }

    @Test
    void ordinaryBossOwnerContractKeepsCapsuleThenAnimalThenExplosionOrder() throws Exception {
        Sonic2PlcService queue = GameServices.module().getGameService(Sonic2PlcService.class);
        int[] animalIds = {50, 52, 59, 51, 57, 58, 52, 55};
        for (int animal : animalIds) {
            queue.transact(Sonic2PlcService.clearOperation(),
                    Sonic2PlcService.appendOperation(Sonic2Constants.PLC_CAPSULE),
                    Sonic2PlcService.appendOperation(animal),
                    Sonic2PlcService.appendOperation(Sonic2Constants.PLC_EXPLOSION));
            assertEquals(expectedDescriptors(Sonic2Constants.PLC_CAPSULE, animal,
                    Sonic2Constants.PLC_EXPLOSION), queue.capture().queuedEntries(),
                    "ordinary boss animal cue " + animal);
        }
    }

    private static List<NemesisPlcQueueSnapshot.Entry> expectedDescriptors(int... ids)
            throws IOException {
        List<NemesisPlcQueueSnapshot.Entry> result = new ArrayList<>();
        for (int id : ids) {
            PlcParser.PlcDefinition definition = PlcParser.parse(GameServices.rom().getRom(),
                    Sonic2Constants.ART_LOAD_CUES_ADDR, id);
            List<Integer> counts = NemesisPlcPatternCounts.derive(GameServices.rom().getRom(), definition);
            for (int index = 0; index < definition.entries().size(); index++) {
                PlcParser.PlcEntry entry = definition.entries().get(index);
                int count = counts.get(index);
                result.add(new NemesisPlcQueueSnapshot.Entry(entry.romAddr(), entry.tileIndex(), count, count));
            }
        }
        return result;
    }
}
