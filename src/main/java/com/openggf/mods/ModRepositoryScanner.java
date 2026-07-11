package com.openggf.mods;

import java.nio.file.Path;
import java.util.List;

/** Discovers packed mods beneath one normalized repository root. */
public interface ModRepositoryScanner {
    List<ModCatalogEntry> scan(Path normalizedModRoot);
}
