package com.openggf.mods;

import com.openggf.game.ModKeySyntax;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Objects;

/** Complete discovery diagnostics paired with one immutable effective snapshot. */
public record ModCatalog(List<ModCatalogEntry> scanned, EffectiveModCatalog effective,
                         Map<String, ModEligibility> eligibility) {
    public ModCatalog(List<ModCatalogEntry> scanned, EffectiveModCatalog effective) {
        this(scanned, effective, Map.of());
    }

    public ModCatalog {
        scanned = List.copyOf(Objects.requireNonNull(scanned, "scanned"));
        Objects.requireNonNull(effective, "effective");
        LinkedHashMap<String, ModEligibility> eligibilityCopy = new LinkedHashMap<>();
        Objects.requireNonNull(eligibility, "eligibility").forEach((id, value) -> {
            String validId = ModKeySyntax.requireManifestId(Objects.requireNonNull(id, "eligibility key"));
            ModEligibility validValue = Objects.requireNonNull(value, "eligibility value");
            if (!validId.equals(validValue.modId())) {
                throw new IllegalArgumentException("Eligibility key must match its mod id: " + validId);
            }
            eligibilityCopy.put(validId, validValue);
        });
        eligibility = Collections.unmodifiableMap(eligibilityCopy);
    }
}
