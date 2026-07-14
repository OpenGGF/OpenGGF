package com.openggf.mods.code;

import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.ObjectArtProvider;
import com.openggf.level.objects.BakedSheetReader;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Objects;

/** Engine-owned proxy that keeps every standalone callback inside its owner boundary. */
final class OwnerAwareStandaloneModule {
    private OwnerAwareStandaloneModule() { }

    static GameModule wrap(String owner, GameModule delegate, ModFaultBoundary boundary,
                           java.util.Map<com.openggf.game.CharacterKey,
                                   com.openggf.game.CharacterDefinition> characters) {
        return wrap(owner, delegate, boundary, characters, java.util.Map.of());
    }

    static GameModule wrap(String owner, GameModule delegate, ModFaultBoundary boundary,
                           java.util.Map<com.openggf.game.CharacterKey,
                                   com.openggf.game.CharacterDefinition> characters,
                           java.util.Map<String, BakedSheetReader.BakedSheet> preparedObjectArt) {
        Objects.requireNonNull(delegate, "delegate");
        InvocationHandler handler = new BoundaryHandler(owner, delegate, boundary, characters,
                preparedObjectArt);
        return (GameModule) Proxy.newProxyInstance(GameModule.class.getClassLoader(),
                new Class<?>[] { GameModule.class }, handler);
    }

    private static final class BoundaryHandler implements InvocationHandler {
        private final String owner;
        private final Object delegate;
        private final ModFaultBoundary boundary;
        private final java.util.Map<com.openggf.game.CharacterKey,
                com.openggf.game.CharacterDefinition> characters;
        private final java.util.Map<String, BakedSheetReader.BakedSheet> preparedObjectArt;
        private final IdentityHashMap<Object, Object> wrapped = new IdentityHashMap<>();
        private ObjectArtProvider decoratedObjectArtProvider;

        private BoundaryHandler(String owner, Object delegate, ModFaultBoundary boundary,
                java.util.Map<com.openggf.game.CharacterKey,
                        com.openggf.game.CharacterDefinition> characters) {
            this(owner, delegate, boundary, characters, java.util.Map.of());
        }

        private BoundaryHandler(String owner, Object delegate, ModFaultBoundary boundary,
                java.util.Map<com.openggf.game.CharacterKey,
                        com.openggf.game.CharacterDefinition> characters,
                java.util.Map<String, BakedSheetReader.BakedSheet> preparedObjectArt) {
            this.owner = com.openggf.game.ModKeySyntax.requireManifestId(owner);
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.boundary = Objects.requireNonNull(boundary, "boundary");
            this.characters = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                    Objects.requireNonNull(characters, "characters")));
            this.preparedObjectArt = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                    Objects.requireNonNull(preparedObjectArt, "preparedObjectArt")));
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "OwnerAwareStandaloneModule[" + owner + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> throw new UnsupportedOperationException(method.toString());
                };
            }
            Object value = boundary.callStandalone(owner,
                    () -> validateOwnedReturn(method, invokeCallback(delegate, method, args)));
            return wrapReturnedValue(method, args, value);
        }

        private Object validateOwnedReturn(Method method, Object value) {
            if (method.getName().equals("getLevelMusicReference")) {
                if (!(value instanceof com.openggf.game.MusicReference.Namespaced namespaced)
                        || !owner.equals(namespaced.owner())) {
                    throw new IllegalArgumentException(
                            "Standalone level music must be a namespaced track owned by " + owner);
                }
            }
            return value;
        }

        private Object wrapReturnedValue(Method method, Object[] args, Object value) {
            Class<?> declaredType = method.getReturnType();
            if (declaredType == com.openggf.game.PlayableCharacterRegistry.class) {
                com.openggf.game.PlayableCharacterRegistry registry = value == null
                        ? com.openggf.game.PlayableCharacterRegistry.empty()
                        : (com.openggf.game.PlayableCharacterRegistry) value;
                for (var entry : characters.entrySet()) {
                    registry = registry.register(entry.getKey(), OwnerAwareCharacterDefinition.wrap(
                            entry.getKey(), entry.getValue(), boundary));
                }
                return registry;
            }
            if (method.getName().equals("getObjectArtProvider")) {
                if (preparedObjectArt.isEmpty()) return value;
                if (decoratedObjectArtProvider == null) {
                    ObjectArtProvider base = value instanceof ObjectArtProvider provider
                            ? provider : new EmptyObjectArtProvider();
                    decoratedObjectArtProvider = ModArtOverlayProvider.decorate(base, preparedObjectArt);
                }
                return decoratedObjectArtProvider;
            }
            if (value == null) return null;
            if (value instanceof java.util.function.Function<?, ?> function) {
                @SuppressWarnings("unchecked")
                java.util.function.Function<Object, Object> callback =
                        (java.util.function.Function<Object, Object>) function;
                return (java.util.function.Function<Object, Object>) argument ->
                        boundary.callStandalone(owner, () -> callback.apply(argument));
            }
            if (method.getName().equals("getGameService") && args != null && args.length == 1
                    && args[0] instanceof Class<?> requested && requested.isInterface()) {
                return wrapped.computeIfAbsent(value, ignored -> Proxy.newProxyInstance(
                        requested.getClassLoader(), new Class<?>[] { requested },
                        new BoundaryHandler(owner, value, boundary, java.util.Map.of())));
            }
            if (value instanceof Game game) {
                return wrapped.computeIfAbsent(value,
                        ignored -> new OwnerAwareStandaloneGame(owner, game, boundary));
            }
            if (value instanceof com.openggf.level.objects.ObjectRegistry registry) {
                return wrapped.computeIfAbsent(value, ignored ->
                        new OwnerAwareStandaloneObjectRegistry(owner, registry, boundary));
            }
            // ObjectManager must retain the concrete AbstractObjectInstance so it can
            // inject ObjectServices and apply the normal lifecycle/rewind contracts.
            // The registry factory invocation itself has already crossed this owner boundary.
            if (value instanceof com.openggf.level.objects.ObjectInstance) {
                return value;
            }
            if (!declaredType.isInterface() || declaredType.isSealed()
                    || !declaredType.getPackageName().startsWith("com.openggf")) {
                return value;
            }
            return wrapped.computeIfAbsent(value, ignored -> Proxy.newProxyInstance(
                    declaredType.getClassLoader(), new Class<?>[] { declaredType },
                    new BoundaryHandler(owner, value, boundary, java.util.Map.of())));
        }
    }

    private static final class OwnerAwareStandaloneObjectRegistry
            implements com.openggf.level.objects.ObjectRegistry,
            java.util.function.Supplier<Object> {
        private final String owner;
        private final com.openggf.level.objects.ObjectRegistry delegate;
        private final ModFaultBoundary boundary;
        private final ObjectCallbackOwnerLookup ownerLookup = new ObjectCallbackOwnerLookup();

        private OwnerAwareStandaloneObjectRegistry(String owner,
                com.openggf.level.objects.ObjectRegistry delegate, ModFaultBoundary boundary) {
            this.owner = owner;
            this.delegate = delegate;
            this.boundary = boundary;
        }

        @Override public com.openggf.level.objects.ObjectInstance create(
                com.openggf.level.objects.ObjectSpawn spawn) {
            return boundary.callStandalone(owner, () -> {
                if (!owner.equals(spawn.ownerModId())) {
                    throw new IllegalArgumentException("Standalone object spawn owner mismatch");
                }
                return ownerLookup.remember(owner, delegate.create(spawn));
            });
        }
        @Override public void reportCoverage(java.util.List<com.openggf.level.objects.ObjectSpawn> spawns) {
            boundary.runStandalone(owner, () -> delegate.reportCoverage(spawns));
        }
        @Override public String getPrimaryName(int objectId) {
            return boundary.callStandalone(owner, () -> delegate.getPrimaryName(objectId));
        }
        @Override public com.openggf.level.objects.ObjectSlotLayout objectSlotLayout() {
            return boundary.callStandalone(owner, delegate::objectSlotLayout);
        }
        @Override public com.openggf.level.objects.ObjectWindowingStrategy objectWindowingStrategy() {
            return boundary.callStandalone(owner, delegate::objectWindowingStrategy);
        }
        @Override public java.util.List<String> getAliases(int objectId) {
            return boundary.callStandalone(owner, () -> delegate.getAliases(objectId));
        }
        @Override public boolean hasObjectKey(String objectKey) {
            return boundary.callStandalone(owner, () -> delegate.hasObjectKey(objectKey));
        }
        @Override public java.util.List<String> browsableObjectKeys() {
            return boundary.callStandalone(owner, delegate::browsableObjectKeys);
        }
        @Override public java.util.Optional<String> editorPreviewArtKey(int stockObjectId) {
            return boundary.callStandalone(owner, () -> delegate.editorPreviewArtKey(stockObjectId));
        }
        @Override public java.util.Optional<String> editorPreviewArtKey(String objectKey) {
            return boundary.callStandalone(owner, () -> delegate.editorPreviewArtKey(objectKey));
        }
        @Override public Object get() {
            return java.util.Map.entry(boundary, ownerLookup);
        }
    }

    private static Object invokeCallback(Object target, Method method, Object[] args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new CheckedCallbackFailure(cause);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot invoke standalone callback " + method, failure);
        }
    }

    private static final class CheckedCallbackFailure extends RuntimeException {
        private CheckedCallbackFailure(Throwable cause) { super(cause); }
    }

    private static final class OwnerAwareStandaloneGame extends Game {
        private final String owner;
        private final Game delegate;
        private final ModFaultBoundary boundary;

        private OwnerAwareStandaloneGame(String owner, Game delegate, ModFaultBoundary boundary) {
            this.owner = owner;
            this.delegate = delegate;
            this.boundary = boundary;
        }

        private <T> T call(java.util.function.Supplier<T> callback) {
            return boundary.callStandalone(owner, callback);
        }

        @Override public boolean isCompatible() { return call(delegate::isCompatible); }
        @Override public String getIdentifier() { return call(delegate::getIdentifier); }
        @Override public java.util.List<String> getTitleCards() { return call(delegate::getTitleCards); }
        @Override public com.openggf.level.Level loadLevel(int levelIdx) {
            return call(() -> {
                try { return delegate.loadLevel(levelIdx); }
                catch (java.io.IOException failure) { throw new CheckedCallbackFailure(failure); }
            });
        }
        @Override public int getMusicId(int levelIdx) {
            return call(() -> {
                try { return delegate.getMusicId(levelIdx); }
                catch (java.io.IOException failure) { throw new CheckedCallbackFailure(failure); }
            });
        }
        @Override public java.util.Map<com.openggf.audio.GameSound, Integer> getSoundMap() {
            return call(delegate::getSoundMap);
        }
        @Override public boolean canRelocateLevels() { return call(delegate::canRelocateLevels); }
        @Override public boolean canSave() { return call(delegate::canSave); }
        @Override public boolean relocateLevels(boolean unsafe) {
            return call(() -> {
                try { return delegate.relocateLevels(unsafe); }
                catch (java.io.IOException failure) { throw new CheckedCallbackFailure(failure); }
            });
        }
        @Override public boolean save(int levelIdx, com.openggf.level.Level level) {
            return call(() -> delegate.save(levelIdx, level));
        }
        @Override public int[] getBackgroundScroll(int levelIdx, int cameraX, int cameraY) {
            return call(() -> delegate.getBackgroundScroll(levelIdx, cameraX, cameraY));
        }
        @Override public Rom getRom() {
            Rom rom = call(delegate::getRom);
            if (rom != null) {
                return call(() -> { throw new IllegalStateException(
                        "Standalone games must not expose a ROM"); });
            }
            return null;
        }
    }
}
