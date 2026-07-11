package com.openggf.game.patch;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;

import java.io.IOException;
import java.util.Objects;

/** Explicit dependencies supplied while a patch decorates a module. */
public final class PatchContext {

    @FunctionalInterface
    public interface LogicalRomSource {
        RomByteReader open(LogicalRom rom) throws IOException;
    }

    private final LogicalRomSource logicalRoms;
    private final SonicConfigurationService configService;

    public PatchContext(LogicalRomSource logicalRoms, SonicConfigurationService configService) {
        this.logicalRoms = Objects.requireNonNull(logicalRoms, "logicalRoms");
        this.configService = Objects.requireNonNull(configService, "configService");
    }

    /** Opens a reader for a logical ROM declared by the applying patch. */
    public RomByteReader openLogicalRom(LogicalRom rom) throws IOException {
        return logicalRoms.open(rom);
    }

    public SonicConfigurationService configService() {
        return configService;
    }
}
