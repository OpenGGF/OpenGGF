package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.PhysicalImage;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Locates the user-supplied S&amp;K + Sonic 2 lock-on dump the way the engine
 * does: through the ROM image catalogue over the working directory. Tier-two
 * tests skip when it returns {@code null}. An explicit {@code kis2.rom.path}
 * is classified independently and fails on an invalid image instead of skipping.
 */
final class Kis2TestRoms {

    private Kis2TestRoms() {
    }

    /** The full {@code KIS2} lock-on image (3.25 MiB), or {@code null} when no image serves it. */
    static RomByteReader lockOnDumpOrNull() {
        String explicit = System.getProperty("kis2.rom.path");
        if (explicit != null && !explicit.isBlank()) {
            try {
                RomImageCatalogue catalogue = new RomImageCatalogue(
                        List.of(PhysicalImage.probe(Path.of(explicit))), Map.of(), false);
                if (!catalogue.isAvailable(RomIdentity.KIS2)) {
                    throw new IllegalArgumentException("kis2.rom.path is not a KiS2 lock-on image: " + explicit);
                }
                return catalogue.open(RomIdentity.KIS2);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read explicit kis2.rom.path: " + explicit, e);
            }
        }
        try {
            RomImageCatalogue catalogue = RomImageCatalogue.forCurrentWorkingDirectory(
                    SonicConfigurationService.getInstance());
            if (!catalogue.isAvailable(RomIdentity.KIS2)) {
                return null;
            }
            return catalogue.open(RomIdentity.KIS2);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
    /** Configure only the caller's standalone test configuration, never persisted preferences. */
    static void configureExplicitCatalogue(SonicConfigurationService config) {
        String explicit = System.getProperty("kis2.rom.path");
        if (explicit == null || explicit.isBlank()) return;
        lockOnDumpOrNull(); // An explicitly requested wrong/missing image must fail, not skip.
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY,
                Path.of(explicit).toAbsolutePath().normalize().getParent().toString());
        bindRomProperty(config, "sonic2.rom.path", SonicConfiguration.SONIC_2_ROM);
        bindRomProperty(config, "s3k.rom.path", SonicConfiguration.SONIC_3K_ROM);
    }

    private static void bindRomProperty(SonicConfigurationService config, String property,
            SonicConfiguration key) {
        String path = System.getProperty(property);
        if (path != null && !path.isBlank()) {
            config.setConfigValue(key, Path.of(path).toAbsolutePath().normalize().toString());
        }
    }

}
