package com.openggf.mods.code;

import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModManifest;
import com.openggf.mods.ModType;
import com.openggf.mods.SemanticVersion;
import com.openggf.mods.VersionRange;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestModClassLoaderFactorySnapshotAdmission {

    @Test
    void trustedCodeStillRequiresAnImmutableSnapshot() throws Exception {
        EffectiveModCatalog catalog = new EffectiveModCatalog(List.of(descriptor("codeowner", true)));
        try (ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog, Set.of("codeowner"))) {
            assertEquals(ModRuntime.RejectionReason.SNAPSHOT_FAILED,
                    runtime.rejectedOwners().get("codeowner").reason());
        }
    }

    @Test
    void dataOnlyModNeedsNoCodeLoader() throws Exception {
        EffectiveModCatalog catalog = new EffectiveModCatalog(List.of(descriptor("dataowner", false)));
        try (ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog, Set.of())) {
            assertFalse(runtime.rejectedOwners().containsKey("dataowner"));
        }
    }

    private static ModDescriptor descriptor(String id, boolean containsCode) {
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"),
                List.of("test"), "test", VersionRange.parse("*"), ModType.PATCH, "s1",
                containsCode ? "example." + id + ".Entry" : null,
                List.of(), Map.of(), Map.of(), null, OptionalInt.empty());
        return new ModDescriptor(Path.of(id + ".jar"), manifest,
                "0".repeat(64), containsCode, List.of());
    }
}
