package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;

import java.io.IOException;

/**
 * Locates the user-supplied S&amp;K + Sonic 2 lock-on dump the way the engine
 * does: through the ROM image catalogue over the working directory. Tier-two
 * tests skip when it returns {@code null}.
 */
final class Kis2TestRoms {

    private Kis2TestRoms() {
    }

    /** The full {@code KIS2} lock-on image (3.25 MiB), or {@code null} when no image serves it. */
    static RomByteReader lockOnDumpOrNull() {
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
}
