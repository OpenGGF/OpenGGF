package com.openggf.game.sonic2.competition.view;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.resources.PlcParser;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionArtResidency {
    private Sonic2CompetitionArtResidency owner() {
        return new Sonic2CompetitionArtResidency(TestEnvironment.currentRom(), new Sonic2PlcService(TestEnvironment.currentRom()));
    }
    @Test void queueCapacityPhaseGateAndImmutableSlotRestore() {
        var owner = owner();
        var zero = owner.capture();
        byte[] upload = new byte[32]; Arrays.fill(upload, (byte) 0xAB);
        for (int i = 0; i < 18; i++) assertTrue(owner.queueUpload(i, upload));
        assertFalse(owner.queueUpload(18, upload));
        Arrays.fill(upload, (byte) 0);
        owner.serviceVBlank(PlcLifecyclePhase.LAG);
        owner.serviceVBlank(PlcLifecyclePhase.PALETTE_FADE);
        assertEquals(18, owner.capture().pending().size());
        assertArrayEquals(new byte[32], owner.patternBytes(0));
        owner.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertTrue(owner.capture().pending().isEmpty());
        assertEquals(0xAB, owner.patternBytes(17)[0] & 255);
        assertArrayEquals(new byte[32], zero.slots()[17].bytes());
        byte[] copy = owner.patternBytes(0); copy[0] = 0;
        assertEquals(0xAB, owner.patternBytes(0)[0] & 255);
        owner.restore(zero);
        assertArrayEquals(new byte[32], owner.patternBytes(0));
        assertThrows(IllegalArgumentException.class, () -> owner.writePatterns(0, new byte[31]));
        assertThrows(IndexOutOfBoundsException.class, () -> owner.writePatterns(2048, new byte[32]));
    }
    @Test void productionNemesisProgressUsesThreeThenSixPatternsAndPreservesPendingOnEntryReset() throws Exception {
        var owner = owner(); var plc = owner.plcService();
        plc.append(Sonic2Constants.PLC_STD1);
        var queued = plc.capture();
        owner.resetForNativeEntry();
        assertEquals(queued, plc.capture());
        owner.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertArrayEquals(new byte[32], owner.patternBytes(0));
        owner.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        var head = plc.capture().activeEntry(); assertNotNull(head);
        byte[] decoded = PlcParser.decompressEntryRaw(TestEnvironment.currentRom(),
                new PlcParser.PlcEntry(head.sourceAddress(), head.destinationTile()));
        owner.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(head.remainingPatterns() - 3, plc.capture().activeEntry().remainingPatterns());
        for (int i = 0; i < 3; i++) assertArrayEquals(Arrays.copyOfRange(decoded, i * 32, (i + 1) * 32),
                owner.patternBytes(head.destinationTile() + i));
        var saved = owner.capture();
        owner.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        assertEquals(head.remainingPatterns() - 9, plc.capture().activeEntry().remainingPatterns());
        owner.restore(saved);
        assertEquals(head.remainingPatterns() - 3, plc.capture().activeEntry().remainingPatterns());
    }
    @Test void actualTailDplcRowsPublishOnlyAtTheNativeTailSlotSpan() throws Exception {
        var owner = owner();
        var sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        var tails = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        owner.bindPlayers(sonic, tails);
        var reader = com.openggf.data.RomByteReader.fromRom(TestEnvironment.currentRom());
        for (int frame : new int[]{9, 10, 0x49, 0x4A}) {
            owner.tailsTailLoadPlc(frame, null);
            owner.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
            int at = 0x7446C + reader.readS16BE(0x7446C + frame * 2);
            int count = reader.readU16BE(at);
            int slot = 0x7B0;
            for (int cue = 0; cue < count; cue++) {
                int entry = reader.readU16BE(at + 2 + cue * 2);
                int patterns = (entry >>> 12) + 1;
                for (int tile = 0; tile < patterns; tile++) {
                    byte[] expected = TestEnvironment.currentRom().readBytes(
                            0x64320 + ((entry & 0xFFF) + tile) * 32, 32);
                    assertArrayEquals(expected, owner.patternBytes(slot++), "tail slot " + (slot - 1));
                }
            }
            assertEquals(frame, owner.capture().tailLast());
            assertArrayEquals(new byte[32], owner.patternBytes(0x7AF));
        }
    }

}
