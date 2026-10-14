package com.openggf.level;


import java.io.IOException;
import java.nio.file.Path;

/** Trust-boundary port used by creator tooling before publishing a staged level export. */
@FunctionalInterface
public interface LevelExportStagingValidator {
    void validate(Path stagingDirectory) throws IOException;

    /** Applies destination-host and enabled-dependency checks to in-memory editor playtests. */
    default void validateHostData(com.openggf.game.modzone.ModZoneLevelData data) throws IOException {
    }
}
