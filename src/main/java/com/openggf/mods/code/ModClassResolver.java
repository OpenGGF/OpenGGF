package com.openggf.mods.code;

import com.openggf.level.objects.RewindClassResolver;

import java.util.Objects;
import java.util.Optional;

/** Loader-aware rewind resolver backed by one boot-scoped compiled-mod runtime. */
public final class ModClassResolver implements RewindClassResolver {
    private final ModRuntime runtime;
    private final ClassLoader engineLoader;

    public ModClassResolver(ModRuntime runtime, ClassLoader engineLoader) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.engineLoader = Objects.requireNonNull(engineLoader, "engineLoader");
    }

    @Override
    public Optional<Class<?>> resolve(String ownerModId, String binaryName) {
        Objects.requireNonNull(binaryName, "binaryName");
        try {
            return Optional.of(ownerModId == null
                    ? Class.forName(binaryName, false, engineLoader)
                    : runtime.loadOwned(ownerModId, binaryName));
        } catch (ClassNotFoundException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> ownerOf(Class<?> type) {
        return runtime.ownerOf(Objects.requireNonNull(type, "type"));
    }
}
