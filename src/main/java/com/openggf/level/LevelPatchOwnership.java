package com.openggf.level;

import java.util.Objects;
import java.util.Set;

/** Engine admission bridge. This loader check binds provenance; it is not a Java sandbox. */
public final class LevelPatchOwnership {
    private LevelPatchOwnership() { }

    public static LevelPatch bind(String verifiedOwner,LevelPatch patch,Set<String> registeredOwnedKeys) {
        ClassLoader caller=com.openggf.util.EngineCallerAccess
                .callerOutside(LevelPatchOwnership.class).getClassLoader();
        if (caller!=LevelPatchOwnership.class.getClassLoader())
            throw new SecurityException("Only the engine may bind decoded level patch ownership");
        return Objects.requireNonNull(patch,"patch").bindOwnership(verifiedOwner,Set.copyOf(registeredOwnedKeys));
    }
}
