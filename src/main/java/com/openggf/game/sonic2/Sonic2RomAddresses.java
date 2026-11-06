package com.openggf.game.sonic2;

import com.openggf.data.RetailRomRevision;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Stock fixed-address operands for the two retail S2 programs.
 *
 * <p>Call only for an address written in engine code using the REV01 address
 * vocabulary. Pointers read from the ROM already name that image's real data
 * and must never pass through this translation. No ROM bytes are rewritten.
 * The ranges come from corresponding labels in bit-perfect fixBugs=0 builds
 * of inspected s2disasm reference 380f37a731bfc720bb0371a35a593184a7ec5e43: the REV01
 * and REV00 outputs match the original images by SHA-1. The final ColCurveMap
 * boundary restores equal addresses; all subsequent art/audio/layout banks
 * retain their retail addresses.
 */
public final class Sonic2RomAddresses {
    private Sonic2RomAddresses() { }

    public static int address(RomByteReader rom, int rev01Address) {
        return address(RetailRomRevision.detect(rom), rev01Address);
    }

    public static long address(RomByteReader rom, long rev01Address) {
        return address(rom, Math.toIntExact(rev01Address));
    }

    public static int address(Rom rom, int rev01Address) {
        try {
            return address(RomByteReader.fromRom(rom), rev01Address);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot identify Sonic 2 ROM addresses", failure);
        }
    }

    public static long address(Rom rom, long rev01Address) {
        return address(rom, Math.toIntExact(rev01Address));
    }

    public static int address(RetailRomRevision revision, int rev01Address) {
        if (revision != RetailRomRevision.S2_REV00) return rev01Address;
        // These boundaries name data/routines, not a flat displacement for an
        // entire bank. REV00 retains code that REV01 removes and vice versa.
        if (rev01Address < 0x00328) return rev01Address;
        if (rev01Address < 0x0C2E4) return rev01Address - 12; // ChecksumTest -> InitCam_Std
        if (rev01Address < 0x0C320) return rev01Address + 8;
        if (rev01Address < 0x0E5D0) return rev01Address + 42; // InitCam_Null2
        if (rev01Address < 0x129D4) return rev01Address + 44; // RunDynamicLevelEvents
        if (rev01Address < 0x12B52) return rev01Address + 12; // ChkPlayer_1up
        if (rev01Address < 0x166A6) return rev01Address; // swap_loop_objects
        if (rev01Address < 0x1671C) return rev01Address + 8; // BuildSprites_ScreenSpaceObj
        if (rev01Address < 0x16F88) return rev01Address + 14; // BuildSprites_MultiDraw
        if (rev01Address < 0x1CD0E) return rev01Address + 12; // RingsManager
        if (rev01Address < 0x20F66) return rev01Address - 16; // Obj02_ResetLevel_Part2
        if (rev01Address < 0x27042) return rev01Address - 4; // Obj74 mappings
        if (rev01Address < 0x2704C) return rev01Address + 6;
        if (rev01Address < 0x2715C) return rev01Address - 4;
        if (rev01Address < 0x2ADB0) return rev01Address; // Obj67
        if (rev01Address < 0x2AFB0) return rev01Address - 8;
        if (rev01Address < 0x42D50) return rev01Address - 16;
        return rev01Address;
    }
}
