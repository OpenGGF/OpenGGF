package com.openggf.level;


import java.io.IOException;
import java.nio.file.Path;

/** Trust-boundary port used by creator tooling before publishing a staged level export. */
@FunctionalInterface
public interface LevelExportStagingValidator {
    void validate(Path stagingDirectory) throws IOException;
}
