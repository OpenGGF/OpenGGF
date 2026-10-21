package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.runtime.OwnerBoundCallbacks;
import java.util.*;
import java.util.function.Supplier;

/** Atomic, bounded publication of one application's verified owner service graph. */
final class OwnedModServices {
    private OwnedModServices() { }

    static GameModule decorate(GameModule base, ModRegistrationPlan plan, ModFaultBoundary boundary) {
        if (plan.serviceBundles().isEmpty() && plan.decodedLevelPatches().isEmpty()) return base;
        Objects.requireNonNull(boundary, "Service and decoded-level contributions require a fault boundary");
        com.openggf.game.rewind.NativeRewindAdapterPublication.reserve(base);
        return boundary.call(plan.ownerModId(), () -> {
            LinkedHashMap<Class<?>,Object> fixed = new LinkedHashMap<>();
            LinkedHashMap<Class<?>,Supplier<?>> dynamic = new LinkedHashMap<>();
            LinkedHashMap<String,RewindSnapshottable<?>> adapters = new LinkedHashMap<>();
            int entries = 0;
            for (var entry : plan.serviceBundles().entrySet()) {
                GameServiceBundle bundle = Objects.requireNonNull(entry.getValue().get(), "Service bundle result");
                entries = Math.addExact(entries, bundle.services().size() + bundle.dynamicServices().size() + bundle.rewindAdapters().size());
                if (entries > plan.contributionLimit()) throw new IllegalArgumentException("Owner service graph limit exceeded");
                for (var service : bundle.services().entrySet()) {
                    if (fixed.containsKey(service.getKey()) || dynamic.containsKey(service.getKey()))
                        throw new IllegalArgumentException("Duplicate owner service contract: " + service.getKey().getName());
                    fixed.put(service.getKey(), service.getKey().cast(Objects.requireNonNull(service.getValue())));
                }
                for (var service : bundle.dynamicServices().entrySet()) {
                    if (service.getKey() == GameplayFrameController.class || service.getKey() == com.openggf.game.GameplayObserver.class)
                        throw new IllegalArgumentException("Frame controllers need a stable captured bundle instance");
                    if (fixed.containsKey(service.getKey()) || dynamic.containsKey(service.getKey()))
                        throw new IllegalArgumentException("Duplicate owner service contract: " + service.getKey().getName());
                    dynamic.put(service.getKey(), Objects.requireNonNull(service.getValue()));
                }
                bundle.rewindAdapters().forEach((key,value) -> adapters.put("services/" + entry.getKey() + "/" + key,value));
            }
            Object controller = fixed.get(GameplayFrameController.class);
            if (controller instanceof GameplayFrameController incoming) {
                GameplayFrameController existing = base.gameplayFrameController();
                if (existing != null && (existing.requiresExclusiveControl() || incoming.requiresExclusiveControl()))
                    throw new IllegalArgumentException("An exclusive gameplay controller cannot replace or be replaced by another controller");
            }
            if (controller instanceof RewindSnapshottable<?> && adapters.values().stream().noneMatch(value -> value == controller))
                throw new IllegalArgumentException("A rewindable frame controller must be captured in its service graph");
            Object observer = fixed.get(com.openggf.game.GameplayObserver.class);
            if (observer != null && (!(observer instanceof RewindSnapshottable<?>)
                    || adapters.values().stream().noneMatch(value -> value == observer)))
                throw new IllegalArgumentException("Gameplay observers require a stable captured bundle instance");
            com.openggf.game.rewind.NativeRewindAdapterPublication.reserve(base);
            OwnerBoundCallbacks callbacks = new OwnerBoundCallbacks(plan.ownerModId(), boundary, plan.contributionLimit(), adapters);
            LinkedHashMap<Class<?>,Supplier<?>> services = new LinkedHashMap<>();
            fixed.forEach((contract, value) -> {
                Object bound = bind(callbacks,contract,value);
                services.put(contract, () -> bound);
            });
            dynamic.forEach((contract, provider) -> services.put(contract, dynamic(callbacks,contract,provider)));
            var inheritedObservers = base.getGameService(GameplayObserverRegistry.class);
            var observerRegistry = inheritedObservers == null ? new GameplayObserverRegistry() : inheritedObservers;
            if (observer != null) observerRegistry = observerRegistry.append(plan.ownerModId(),
                    (com.openggf.game.GameplayObserver) services.get(com.openggf.game.GameplayObserver.class).get());
            final var accumulatedObservers = observerRegistry;
            List<RewindSnapshottable<?>> ownedAdapters = adapters.values().stream().map(callbacks::adapter).toList();
            List<com.openggf.level.LevelPatch> patches = plan.decodedLevelPatches().values().stream()
                    .map(patch -> com.openggf.level.LevelPatchOwnership.bind(plan.ownerModId(), patch, plan.objectFactories().keySet())).toList();
            return new DelegatingGameModule(base, plan.ownerModId() + ":services") {
                @Override public <T> T getGameService(Class<T> type) {
                    if (type == GameplayObserverRegistry.class
                            || type == com.openggf.game.session.GameplayObserverSnapshots.class)
                        return type.cast(accumulatedObservers);
                    Supplier<?> service = services.get(type);
                    // Enum choices and opt-in attack policies withdraw at fresh native decision boundaries.
                    // Consult quarantine before dynamic providers; captured values/callbacks retain their semantics.
                    boolean lifetimeScoped = type.isEnum() || type == AizMinibossAttackPolicy.class;
                    return service == null || lifetimeScoped && !boundary.isOwnerAvailable(plan.ownerModId())
                            ? super.getGameService(type) : type.cast(service.get());
                }
                @Override public GameplayFrameController gameplayFrameController() {
                    return services.containsKey(GameplayFrameController.class)
                            ? getGameService(GameplayFrameController.class) : super.gameplayFrameController();
                }
                @Override public List<RewindSnapshottable<?>> rewindAdapters() {
                    List<RewindSnapshottable<?>> result = new ArrayList<>(super.rewindAdapters());
                    result.addAll(ownedAdapters);
                    return List.copyOf(result);
                }
                @Override public com.openggf.level.Level transformDecodedLevel(com.openggf.level.Level source) {
                    com.openggf.level.Level inherited = super.transformDecodedLevel(source);
                    return boundary.call(plan.ownerModId(), () -> {
                        com.openggf.level.Level transformed = inherited;
                        for (var patch : patches)
                            transformed = com.openggf.level.LevelPatchOwnership.applyDecoded(patch,transformed);
                        return transformed;
                    });
                }
            };
        });
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object bind(OwnerBoundCallbacks callbacks, Class<?> contract, Object value) {
        return callbacks.bind((Class)contract,value);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Supplier<?> dynamic(OwnerBoundCallbacks callbacks, Class<?> contract, Supplier<?> provider) {
        return callbacks.dynamic((Class)contract,provider);
    }
}
