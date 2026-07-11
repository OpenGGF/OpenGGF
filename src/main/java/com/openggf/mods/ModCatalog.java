package com.openggf.mods;

import java.util.List;
import java.util.Objects;

/** Complete discovery diagnostics paired with one immutable effective snapshot. */
public record ModCatalog(List<ModCatalogEntry> scanned, EffectiveModCatalog effective) {
    public ModCatalog {
        scanned = List.copyOf(Objects.requireNonNull(scanned, "scanned"));
        Objects.requireNonNull(effective, "effective");
    }
}
