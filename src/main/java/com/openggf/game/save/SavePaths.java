package com.openggf.game.save;

import java.nio.file.Path;

/** Process save-root policy; production defaults to the historical working-directory path. */
public final class SavePaths {
    public static final String ROOT_PROPERTY = "openggf.saveRoot";

    private SavePaths() { }

    public static Path root() {
        String configured = System.getProperty(ROOT_PROPERTY);
        return configured == null || configured.isBlank() ? Path.of("saves") : Path.of(configured);
    }
}
