package com.openggf.game.sonic1;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.credits.Sonic1CreditsManager;
import com.openggf.game.sonic1.resources.Sonic1PlcService;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.PlcParser;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Executes the S1-owned credits producer and records the complete descriptor
 * sequence it leaves in the real native FIFO.  This deliberately compares ROM
 * descriptors rather than {@code isBusy()}: identical queue length is not
 * sufficient evidence that a producer selected the right PLC.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1PlcProducerCoverage {
    private static final int[] CREDITS_NATIVE_ZONES = {0, 2, 4, 1, 3, 5, 5, 0, 1};

    @BeforeEach
    void createSonic1Services() {
        GameServices.module().createGame(TestEnvironment.currentRom());
    }

    @Test
    void creditsOwnerPrequeuesEveryTextPageInNativeRomZoneOrder() throws Exception {
        Sonic1CreditsManager credits = new Sonic1CreditsManager();
        Sonic1PlcService queue = GameServices.module().getGameService(Sonic1PlcService.class);

        for (int credit = 0; credit < CREDITS_NATIVE_ZONES.length; credit++) {
            setCreditsNumber(credits, credit);
            credits.onReturnToText();

            int primary = primaryForNativeZone(CREDITS_NATIVE_ZONES[credit]);
            assertEquals(expectedDescriptors(primary, 1), queue.capture().queuedEntries(),
                    "credits page " + credit + " must publish ClearPLC, primary AddPLC, Main2 AddPLC");
        }
    }

    @Test
    void everyAuditedS1CueParsesAndPreservesItsExactAppendOrReplaceDescriptorOrder()
            throws Exception {
        Sonic1PlcService queue = GameServices.module().getGameService(Sonic1PlcService.class);
        // This covers the concrete producer IDs in the audit.  Owner-to-facade
        // wiring is guarded by TestPlcProducerCoverageGuard; this assertion
        // catches an ID/table regression even where a visual owner cannot be
        // booted headlessly (title card and boss presentation paths).
        int[] appendIds = {1, 2, 17, 21, 22, 23, 24, 25, 26, 27, 30, 31};
        for (int plcId : appendIds) {
            queue.transact(Sonic1PlcService.clear(), Sonic1PlcService.appendOperation(plcId));
            assertEquals(expectedDescriptors(plcId), queue.capture().queuedEntries(),
                    "S1 audited append PLC " + plcId);
        }
        for (int plcId : new int[] {0, 16}) {
            queue.transact(Sonic1PlcService.replace(plcId));
            assertEquals(expectedDescriptors(plcId), queue.capture().queuedEntries(),
                    "S1 audited replace PLC " + plcId);
        }
    }

    private static void setCreditsNumber(Sonic1CreditsManager credits, int value) throws Exception {
        Field field = Sonic1CreditsManager.class.getDeclaredField("creditsNum");
        field.setAccessible(true);
        field.setInt(credits, value);
    }

    private static int primaryForNativeZone(int zone) throws IOException {
        int header = Sonic1Constants.LEVEL_HEADERS_ADDR + zone * 16;
        return GameServices.rom().getRom().readByte(header) & 0xFF;
    }

    private static List<NemesisPlcQueueSnapshot.Entry> expectedDescriptors(int... ids)
            throws IOException {
        List<NemesisPlcQueueSnapshot.Entry> result = new ArrayList<>();
        for (int id : ids) {
            PlcParser.PlcDefinition definition = PlcParser.parse(GameServices.rom().getRom(),
                    Sonic1Constants.ART_LOAD_CUES_ADDR, id);
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
