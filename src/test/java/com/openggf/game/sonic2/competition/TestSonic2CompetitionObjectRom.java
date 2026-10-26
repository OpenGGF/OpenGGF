package com.openggf.game.sonic2.competition;

import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** Independent shipped-code anchors: historical disassembly address comments are not the oracle. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionObjectRom {
    @Test void rev01ObjectInstructionsProveClockQuestionShellGoalOrderAndTeleportRoutineGate() throws Exception {
        byte[] rom = TestEnvironment.currentRom().readAllBytes();
        assertEquals("8bca5dcef1af3e00098666fd892dc1c2a76333f9",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(rom)));
        bytes(rom, 0x126F4, "117c0009001c"); // Obj26_Init forces question-mark shell.
        bytes(rom, 0x1289A, "3038fe04024000075240"); // Level_frame_counter &7, plus1.
        bytes(rom, 0x19258, "302900089068000865740c400020646e"); // P1 no-borrow, exclusive $20.
        bytes(rom, 0x12AA6, "52520c380006b024"); // Count monitor before rejecting routine >=6.
    }
    private static void bytes(byte[] rom, int offset, String hex) {
        byte[] expected = HexFormat.of().parseHex(hex);
        assertArrayEquals(expected, java.util.Arrays.copyOfRange(rom, offset, offset + expected.length),
                "ROM code at " + Integer.toHexString(offset));
    }
}
