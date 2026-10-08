package com.openggf.game;

import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.RewindSnapshottable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** One application's service graph. The registration host derives ownership and bounds every callback. */
@ModApi
public final class GameServiceBundle {
    private final Map<Class<?>, Object> services;
    private final Map<Class<?>, Supplier<?>> dynamicServices;
    private final Map<String, RewindSnapshottable<?>> rewindAdapters;

    private GameServiceBundle(Builder builder) {
        services = Collections.unmodifiableMap(new LinkedHashMap<>(builder.services));
        dynamicServices = Collections.unmodifiableMap(new LinkedHashMap<>(builder.dynamicServices));
        rewindAdapters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.rewindAdapters));
    }

    public static Builder builder() { return new Builder(); }
    public Map<Class<?>, Object> services() { return services; }
    public Map<Class<?>, Supplier<?>> dynamicServices() { return dynamicServices; }
    /** Keys are owner-local; an adapter's creator-reported key never supplies registration authority. */
    public Map<String, RewindSnapshottable<?>> rewindAdapters() { return rewindAdapters; }

    @ModApi
    public static final class Builder {
        private final Map<Class<?>, Object> services = new LinkedHashMap<>();
        private final Map<Class<?>, Supplier<?>> dynamicServices = new LinkedHashMap<>();
        private final Map<String, RewindSnapshottable<?>> rewindAdapters = new LinkedHashMap<>();
        private Builder() { }

        public <T> Builder service(Class<T> contract, T instance) {
            requireUnusedContract(contract);
            services.put(contract, contract.cast(Objects.requireNonNull(instance, "instance")));
            return this;
        }

        /** Nullable dynamic results represent a temporarily unavailable service, such as the current arena. */
        public <T> Builder dynamicService(Class<T> contract, Supplier<? extends T> supplier) {
            requireUnusedContract(contract);
            dynamicServices.put(contract, Objects.requireNonNull(supplier, "supplier"));
            return this;
        }

        public Builder rewindAdapter(String localKey, RewindSnapshottable<?> adapter) {
            String key = ModKeySyntax.requireLocalName(localKey);
            if (rewindAdapters.putIfAbsent(key, Objects.requireNonNull(adapter, "adapter")) != null)
                throw new IllegalArgumentException("Duplicate bundle rewind key: " + key);
            return this;
        }

        /** Registers one mutable state object as both its concrete service and captured adapter. */
        public <T extends RewindSnapshottable<?>> Builder capturedService(String localKey, Class<T> contract, T instance) {
            requireUnusedContract(contract);
            if (rewindAdapters.containsKey(ModKeySyntax.requireLocalName(localKey)))
                throw new IllegalArgumentException("Duplicate bundle rewind key: " + localKey);
            service(contract, instance);
            rewindAdapter(localKey, instance);
            return this;
        }

        /** Alternative gameplay and rewind share exactly one underlying state object. */
        public <T extends GameplayFrameController & RewindSnapshottable<?>> Builder frameController(String localKey, T controller) {
            requireUnusedContract(GameplayFrameController.class);
            if (rewindAdapters.containsKey(ModKeySyntax.requireLocalName(localKey)))
                throw new IllegalArgumentException("Duplicate bundle rewind key: " + localKey);
            service(GameplayFrameController.class, controller);
            rewindAdapter(localKey, controller);
            return this;
        }

        public GameServiceBundle build() { return new GameServiceBundle(this); }

        private void requireUnusedContract(Class<?> contract) {
            Objects.requireNonNull(contract, "contract");
            if (contract.isPrimitive() || services.containsKey(contract) || dynamicServices.containsKey(contract))
                throw new IllegalArgumentException("Invalid or duplicate bundle service contract: " + contract.getName());
        }
    }
}
