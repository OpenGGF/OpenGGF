package com.openggf.level.objects;

import com.openggf.game.ModApi;

import java.util.Optional;

/** Resolves captured rewind classes without coupling the object system to mod loaders. */
@ModApi
public interface RewindClassResolver {
    RewindClassResolver ENGINE_ONLY = new RewindClassResolver() {
        @Override
        public Optional<Class<?>> resolve(String ownerModId, String binaryName) {
            if (ownerModId != null) {
                return Optional.empty();
            }
            try {
                return Optional.of(Class.forName(binaryName, false,
                        RewindClassResolver.class.getClassLoader()));
            } catch (ClassNotFoundException missing) {
                return Optional.empty();
            }
        }

        @Override
        public Optional<String> ownerOf(Class<?> type) {
            return Optional.empty();
        }
    };

    Optional<Class<?>> resolve(String ownerModId, String binaryName);

    Optional<String> ownerOf(Class<?> type);
}
