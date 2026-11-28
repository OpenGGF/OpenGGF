package com.openggf.game.patch;

import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModDependency;
import com.openggf.mods.ModType;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Engine-owned mod selection shared by patch resolution and presentation preparation. */
public final class ScopedLaunch {
    private ScopedLaunch() { }

    public static ModuleResolutionService.PreparedLaunch prepare(ModuleResolutionService resolver,
            boolean modsOn, Predicate<String> owners) {
        return resolver.prepareLaunch(modsOn ? ModuleResolutionService.LaunchPolicy.STANDARD
                : ModuleResolutionService.LaunchPolicy.DETERMINISTIC, owners);
    }

    public static boolean contributesTo(com.openggf.mods.code.ModRegistrationPlan plan, String gameId) {
        if (plan == null) return false;
        return plan.explicitPatches().stream().anyMatch(patch -> gameId.equals(patch.baseGameId()))
                || !plan.characters().isEmpty() || !plan.mutators().isEmpty()
                || !plan.objectFactories().isEmpty() || !plan.serviceBundles().isEmpty()
                || plan.requiredDisplayAspect() != null;
    }

    public static Set<String> owners(EffectiveModCatalog catalog, String gameOwner) {
        if (gameOwner == null) {
            var candidates = new HashSet<String>();
            catalog.orderedEnabled().forEach(mod -> {
                var manifest = mod.manifest();
                if (manifest.type() == ModType.PATCH && manifest.dependencies().stream()
                        .map(ModDependency::id).allMatch(candidates::contains)) candidates.add(manifest.id());
            });
            return Set.copyOf(candidates);
        }
        var dependencies = new java.util.HashMap<String, java.util.List<ModDependency>>();
        for (var mod : catalog.orderedEnabled()) {
            dependencies.put(mod.manifest().id(), mod.manifest().dependencies());
        }
        var selected = new HashSet<String>();
        var pending = new ArrayDeque<String>();
        pending.add(gameOwner);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (selected.add(id)) {
                dependencies.getOrDefault(id, java.util.List.of()).stream()
                        .map(ModDependency::id).forEach(pending::addLast);
            }
        }
        return Set.copyOf(selected);
    }
}
