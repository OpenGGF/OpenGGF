package com.openggf.game.sonic3k.bluespheres;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomManager;

import java.io.IOException;
import java.util.Arrays;

/**
 * Decides whether the attached cartridge unlocks the full Blue Spheres game.
 *
 * <p>Locking a cartridge onto S&amp;K is modelled as a second logical ROM from
 * the user's catalogue; only its header is read. ROM: sub_4CCA6
 * (sonic3k.asm:100423-100461) compares the 13-character serial at
 * {@code LockonSerialNumber} ({@code $180} in the attached cartridge) with
 * {@code BlueSpheresSerialsText}. A match sets {@code Blue_spheres_mode} to
 * the full game. The loop runs four times over a two-entry table, so the
 * last two comparisons read the code that follows it; those bytes are read
 * from the ROM too.
 */
public final class S3kBlueSpheresLockOn {
    /** {@code BlueSpheresSerialsText} in the S&amp;K half. */
    static final long SERIALS_TEXT = 0x4CCFE;
    /** {@code moveq #$D-1,d2}: serial characters compared. */
    static final int SERIAL_LENGTH = 13;
    /** {@code moveq #4-1,d1}: table entries compared. */
    static final int SERIAL_COMPARES = 4;
    /** {@code LockonSerialNumber} relative to the attached cartridge. */
    static final int HEADER_SERIAL = 0x180;

    private S3kBlueSpheresLockOn() {
    }

    /** True when a Sonic 1 image is available to lock on. */
    public static boolean sonic1Available(RomManager roms) {
        return roms != null && roms.isLogicalRomAvailable(RomIdentity.S1);
    }

    /**
     * True when the attached Sonic 1 cartridge's serial matches one of the
     * serials S&amp;K accepts for the full game.
     */
    public static boolean unlocksFullGame(RomManager roms, RomByteReader sk) throws IOException {
        if (!sonic1Available(roms)) return false;
        RomByteReader attached = roms.openLogicalRom(RomIdentity.S1);
        return serialMatches(sk.slice((int) SERIALS_TEXT, SERIAL_LENGTH * SERIAL_COMPARES),
                attached.slice(HEADER_SERIAL, SERIAL_LENGTH));
    }

    static boolean serialMatches(byte[] serialsText, byte[] attachedSerial) {
        for (int entry = 0; entry < SERIAL_COMPARES; entry++) {
            int from = entry * SERIAL_LENGTH;
            if (Arrays.equals(serialsText, from, from + SERIAL_LENGTH,
                    attachedSerial, 0, SERIAL_LENGTH)) {
                return true;
            }
        }
        return false;
    }
}
