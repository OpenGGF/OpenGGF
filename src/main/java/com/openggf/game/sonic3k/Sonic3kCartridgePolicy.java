package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomHeaderName;
import com.openggf.game.GameServices;
import java.io.IOException;

/** Native SK_alone_flag, derived from the actual logical cartridge, never a configured character. */
public final class Sonic3kCartridgePolicy {
    private Sonic3kCartridgePolicy() { }

    public static boolean isStandaloneSk(Rom rom) {
        if (rom == null || !rom.isOpen()) return false;
        try {
            return rom.getSize() == 0x200000
                    && RomHeaderName.of(rom.readDomesticName(), rom.readInternationalName()) == RomHeaderName.SK;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot resolve the active S&K cartridge", failure);
        }
    }

    public static boolean isStandaloneS3(Rom rom) {
        if (rom == null || !rom.isOpen()) return false;
        try {
            return rom.getSize() == 0x200000
                    && RomHeaderName.of(rom.readDomesticName(), rom.readInternationalName()) == RomHeaderName.S3;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot resolve the active Sonic 3 cartridge", failure);
        }
    }

    public static boolean isStandaloneSk() {
        if (!GameServices.rom().isRomAvailable()) return false;
        try {
            return isStandaloneSk(GameServices.rom().getRom());
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read the active S&K cartridge", failure);
        }
    }
}
