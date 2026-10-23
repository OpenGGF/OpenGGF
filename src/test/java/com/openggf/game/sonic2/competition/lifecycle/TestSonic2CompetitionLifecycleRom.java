package com.openggf.game.sonic2.competition.lifecycle;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionLifecycleRom {
    @Test
    void retailOperandsMatchNativeDeathAndInterlacedArtInstructions() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        RomByteReader bytes = RomByteReader.fromRom(rom);
        var data = Sonic2CompetitionLifecycleRom.load(rom);
        assertEquals(0x0668, bytes.readU16BE(0x16396));
        assertEquals(0x0038, bytes.readU16BE(0x16398));
        assertEquals(0x0012, bytes.readU16BE(0x1639A));
        assertEquals(0x0100, bytes.readU16BE(0x1B22E));
        assertEquals(0x0100, bytes.readU16BE(0x1CC88));
        assertEquals(0x117C0005, bytes.readU32BE(0x1B2F0));
        assertEquals(0x117C0005, bytes.readU32BE(0x1CD6A));
        assertEquals(0x0780, bytes.readU16BE(0x19FBA));
        assertEquals(0x07A0, bytes.readU16BE(0x1B954));
        assertEquals(0xE248, bytes.readU16BE(0x16D7C), "LSR.W #1,D0 halves interlace tile ids");
        assertEquals(0x38, data.gravity());
        assertEquals(0x100, data.deathMargin());
        assertEquals(5, data.respawnAnimation());
        assertEquals(0x3C0, data.sonicArtTile());
        assertEquals(0x3D0, data.tailsArtTile());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(bytes.slice(0, bytes.size()))), data.fingerprint());
        assertEquals(data, Sonic2CompetitionLifecycleRom.load(rom));
    }

    @Test
    void injectedMemoryOperandsDriveValuesAndFingerprintWhileArtFlagsSurviveHalving() throws Exception {
        Rom source = TestEnvironment.currentRom();
        var retail = Sonic2CompetitionLifecycleRom.load(source);
        byte[] bytes = copyBytes(source);
        word(bytes, 0x16398, 0x48);
        word(bytes, 0x1B22E, 0x180);
        word(bytes, 0x1CC88, 0x180);
        bytes[0x1B2F3] = 9;
        bytes[0x1CD6D] = 9;
        word(bytes, 0x19FBA, 0xA740);
        word(bytes, 0x1B954, 0x87C0);
        try (Rom variant = Rom.fromReader(RomByteReader.fromBytes(bytes), "test-owned lifecycle operands")) {
            var altered = Sonic2CompetitionLifecycleRom.load(variant);
            assertEquals(0x48, altered.gravity());
            assertEquals(0x180, altered.deathMargin());
            assertEquals(9, altered.respawnAnimation());
            assertEquals(0xA3A0, altered.sonicArtTile());
            assertEquals(0x83E0, altered.tailsArtTile());
            assertNotEquals(retail.fingerprint(), altered.fingerprint());
        }
        assertEquals(retail, Sonic2CompetitionLifecycleRom.load(source),
                "An injected variant must not mutate or resolve through the globally active ROM");
    }

    @Test
    void malformedInstructionShapesAndArtAdjustmentTargetsAreRejected() throws Exception {
        for (int opcode : new int[] {0x16396, 0x1B22C, 0x1CC86, 0x1B2F0, 0x1CD6A,
                0x19FB8, 0x1B952, 0x19FBE, 0x16D7C}) {
            byte[] bytes = copyBytes(TestEnvironment.currentRom());
            word(bytes, opcode, 0x4E71);
            assertRejected(bytes, "malformed opcode at $" + Integer.toHexString(opcode));
        }
        byte[] wrongTarget = copyBytes(TestEnvironment.currentRom());
        word(wrongTarget, 0x1B95A, 0);
        assertRejected(wrongTarget, "Tails initializer calls a different adjustment routine");
    }

    @Test
    void playerDisagreementAndTruncatedProgramsAreRejected() throws Exception {
        byte[] margins = copyBytes(TestEnvironment.currentRom());
        word(margins, 0x1CC88, 0x101);
        assertRejected(margins, "P1 and P2 death margins disagree");
        byte[] animations = copyBytes(TestEnvironment.currentRom());
        animations[0x1CD6D] = 6;
        assertRejected(animations, "P1 and P2 respawn animations disagree");
        byte[] truncated = RomByteReader.fromRom(TestEnvironment.currentRom()).slice(0, 0x1639A);
        assertRejected(truncated, "Truncated injected program");
    }

    private static byte[] copyBytes(Rom rom) throws IOException {
        RomByteReader reader = RomByteReader.fromRom(rom);
        return reader.slice(0, reader.size());
    }

    private static void assertRejected(byte[] bytes, String description) throws Exception {
        try (Rom variant = Rom.fromReader(RomByteReader.fromBytes(bytes), description)) {
            assertThrows(IOException.class, () -> Sonic2CompetitionLifecycleRom.load(variant), description);
        }
    }

    private static void word(byte[] bytes, int address, int value) {
        bytes[address] = (byte) (value >>> 8);
        bytes[address + 1] = (byte) value;
    }
}
