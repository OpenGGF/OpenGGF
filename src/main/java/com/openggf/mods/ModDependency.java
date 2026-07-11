package com.openggf.mods;

import com.openggf.io.ModKeySyntax;

import java.util.Objects;

public record ModDependency(String id, VersionRange versionRange) {
    public ModDependency {
        id = ModKeySyntax.requireManifestId(id);
        Objects.requireNonNull(versionRange, "versionRange");
    }
}
