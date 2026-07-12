package com.openggf.level;

import com.openggf.game.ModApi;

import java.io.IOException;
import java.nio.file.Path;

/** Trust-boundary port used by creator tooling before publishing a staged level export. */
@FunctionalInterface
@ModApi
public interface LevelExportStagingValidator {
    void validate(Path stagingDirectory) throws IOException;
}
