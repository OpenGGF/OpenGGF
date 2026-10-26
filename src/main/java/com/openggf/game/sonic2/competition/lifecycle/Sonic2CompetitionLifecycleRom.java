package com.openggf.game.sonic2.competition.lifecycle;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Native lifecycle operands and ROM-generation identity from the injected retail program. */
final record Sonic2CompetitionLifecycleRom(int gravity, int deathMargin, int respawnAnimation,
        int sonicArtTile, int tailsArtTile, String fingerprint) {
    private static final int ART_ADJUSTMENT = 0x16D6E;

    static Sonic2CompetitionLifecycleRom load(Rom source) throws IOException {
        RomByteReader rom = RomByteReader.fromRom(Objects.requireNonNull(source, "rom"));
        try {
            // ObjectMoveAndFall (s2.asm:30164-30177): ADDI.W #gravity,y_vel(a0).
            requireWords(rom, 0x16396, 0x0668);
            requireWords(rom, 0x1639A, 0x0012);
            int gravity = rom.readS16BE(0x16398);

            // CheckGameOver (s2.asm:38274-38280) and Obj02_CheckGameOver
            // (s2.asm:41142-41150) compare centre Y against their own camera limit plus a word.
            int deathMargin = deathMargin(rom, 0x1B228, 0xEECE);
            requireEqual("death margins", deathMargin, deathMargin(rom, 0x1CC82, 0xEEFE));

            // Obj01_ResetLevel_Part2 (s2.asm:38319-38337) and Obj02_ResetLevel_Part3
            // (s2.asm:41211-41226) write the same respawn animation byte.
            int animation = respawnAnimation(rom, 0x1B2F0);
            requireEqual("respawn animations", animation, respawnAnimation(rom, 0x1CD6A));

            // Adjust2PArtPointer (s2.asm:31456-31466) halves only tile bits, preserving flags.
            requireWords(rom, ART_ADJUSTMENT, 0x4A78, 0xFFD8, 0x6714, 0x3028, 0x0002,
                    0x0240, 0x07FF, 0xE248, 0x0268, 0xF800, 0x0002, 0xD168, 0x0002, 0x4E75);
            int tileMask = rom.readU16BE(ART_ADJUSTMENT + 12);
            int flagMask = rom.readU16BE(ART_ADJUSTMENT + 18);
            // Obj01_Init (s2.asm:36192-36200) and Obj02_Init_2Pmode (s2.asm:38926-38938).
            int sonicArt = adjustedInitialArt(rom, 0x19FB8, tileMask, flagMask);
            int tailsArt = adjustedInitialArt(rom, 0x1B952, tileMask, flagMask);
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rom.slice(0, rom.size())));
            return new Sonic2CompetitionLifecycleRom(gravity, deathMargin, animation,
                    sonicArt, tailsArt, fingerprint);
        } catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
            throw new IOException("Invalid Sonic 2 REV01 competition lifecycle program in supplied ROM", failure);
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError("SHA-256 unavailable", failure);
        }
    }

    private static int deathMargin(RomByteReader rom, int instruction, int cameraAddress) {
        requireWords(rom, instruction, 0x3038, cameraAddress, 0x0640);
        requireWords(rom, instruction + 8, 0xB068, 0x000C, 0x6C00);
        return rom.readS16BE(instruction + 6);
    }

    private static int respawnAnimation(RomByteReader rom, int instruction) {
        requireWords(rom, instruction, 0x117C);
        requireWords(rom, instruction + 4, 0x001C);
        return rom.readU8(instruction + 3);
    }

    private static int adjustedInitialArt(RomByteReader rom, int instruction, int tileMask, int flagMask) {
        requireWords(rom, instruction, 0x317C);
        requireWords(rom, instruction + 4, 0x0002, 0x6100);
        int callTarget = instruction + 8 + rom.readS16BE(instruction + 8);
        requireEqual("initial-art adjustment target", ART_ADJUSTMENT, callTarget);
        int artWord = rom.readU16BE(instruction + 2);
        return (artWord & flagMask) | ((artWord & tileMask) >>> 1);
    }

    private static void requireWords(RomByteReader rom, int address, int... words) {
        for (int index = 0; index < words.length; index++) {
            int wordAddress = address + index * 2;
            if (rom.readU16BE(wordAddress) != words[index]) {
                throw new IllegalArgumentException("Unexpected lifecycle instruction at $"
                        + Integer.toHexString(wordAddress));
            }
        }
    }

    private static void requireEqual(String operand, int expected, int actual) {
        if (expected != actual) {
            throw new IllegalArgumentException("Mismatched lifecycle " + operand);
        }
    }
}
