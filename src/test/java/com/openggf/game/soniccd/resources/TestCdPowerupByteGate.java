package com.openggf.game.soniccd.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure byte-gate contracts; no art owner, CPU/resident state, media or native execution. */
class TestCdPowerupByteGate {
    @Test
    void all256NativeBytesRetainTheirLowSevenBitsAndReportThePreviousRequestBit() {
        for (int cleared = 0; cleared <= 0x7F; cleared++) {
            var inactive = CdPowerupArtRuntime.gate(cleared);
            assertEquals(cleared, inactive.powerup(), "Inactive byte " + cleared);
            assertFalse(inactive.copyRequired(), "Inactive byte " + cleared);

            int requested = cleared + 0x80;
            var active = CdPowerupArtRuntime.gate(requested);
            assertEquals(cleared, active.powerup(), "Requested byte " + requested);
            assertTrue(active.copyRequired(), "Requested byte " + requested);
        }
    }

    @Test
    void inactiveUnboundSelector7fNeedsNoArtBinding() {
        var result = CdPowerupArtRuntime.gate(0x7F);
        assertEquals(0x7F, result.powerup());
        assertFalse(result.copyRequired());
    }

    @Test
    void oldBitSevenRequestsACopyEvenWhenTheClearedByteIsZero() {
        var requested = CdPowerupArtRuntime.gate(0x80);
        assertEquals(0, requested.powerup());
        assertTrue(requested.copyRequired());

        var cleared = CdPowerupArtRuntime.gate(requested.powerup());
        assertEquals(0, cleared.powerup());
        assertFalse(cleared.copyRequired());
    }

    @Test
    void gateRejectsNonByteInputsInsteadOfTruncatingOrSignExtendingThem() {
        int[] malformed = {-1, -128, -256, 0x100, 0x180, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int raw : malformed) {
            assertThrows(IllegalArgumentException.class, () -> CdPowerupArtRuntime.gate(raw),
                    "Non-byte input " + raw);
        }
    }

    @Test
    void resultConstructorAcceptsEveryClearedByteWithEitherDescriptiveFlag() {
        for (int powerup = 0; powerup <= 0x7F; powerup++) {
            var inactive = new CdPowerupArtRuntime.Gate(powerup, false);
            assertEquals(powerup, inactive.powerup());
            assertFalse(inactive.copyRequired());

            var active = new CdPowerupArtRuntime.Gate(powerup, true);
            assertEquals(powerup, active.powerup());
            assertTrue(active.copyRequired());
        }
    }

    @Test
    void resultConstructorRejectsBytesThatStillHaveBitSevenAndAllNonBytes() {
        int[] malformed = {-1, -128, -256, 0x80, 0xFF, 0x100, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int powerup : malformed) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CdPowerupArtRuntime.Gate(powerup, false), "Cleared-byte input " + powerup);
            assertThrows(IllegalArgumentException.class,
                    () -> new CdPowerupArtRuntime.Gate(powerup, true), "Cleared-byte input " + powerup);
        }
    }
}
