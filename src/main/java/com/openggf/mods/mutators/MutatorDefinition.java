package com.openggf.mods.mutators;

import com.openggf.game.ModKeySyntax;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A dormant, boot-prepared mutator. The engine derives its owner from ModContext;
 * activation always defaults off. Local prerequisite/conflict keys refer to this owner.
 */
@com.openggf.game.ModApi
public record MutatorDefinition(String localId, String title, String description,
                                int schemaVersion, MutatorScope enableScope,
                                MutatorScope disableScope, List<MutatorOption> options,
                                List<AtomicGroup> atomicGroups,
                                Set<String> prerequisites, Set<String> conflicts,
                                Set<MutatorCapability> capabilities, PolicyFactory factory) {
    public MutatorDefinition {
        ModKeySyntax.requireLocalName(localId);
        if (Objects.requireNonNull(title, "title").isBlank() || title.length() > 80
                || Objects.requireNonNull(description, "description").length() > 1024
                || schemaVersion < 1) throw new IllegalArgumentException("Invalid mutator metadata");
        Objects.requireNonNull(enableScope, "enableScope");
        Objects.requireNonNull(disableScope, "disableScope");
        options = List.copyOf(options);
        atomicGroups = List.copyOf(atomicGroups);
        prerequisites = Set.copyOf(prerequisites);
        conflicts = Set.copyOf(conflicts);
        capabilities = Set.copyOf(capabilities);
        Objects.requireNonNull(factory, "factory");
        if (options.size() > 32 || atomicGroups.size() > 16 || capabilities.isEmpty()
                || prerequisites.size() > 32 || conflicts.size() > 32) {
            throw new IllegalArgumentException("Mutator schema exceeds host bounds");
        }
        prerequisites.forEach(ModKeySyntax::requireLocalName);
        conflicts.forEach(ModKeySyntax::requireLocalName);
        if (prerequisites.contains(localId) || conflicts.contains(localId)
                || prerequisites.stream().anyMatch(conflicts::contains)) {
            throw new IllegalArgumentException("Invalid prerequisite/conflict graph");
        }
        Map<String, MutatorOption> byId = new LinkedHashMap<>();
        for (MutatorOption option : options) {
            if (byId.putIfAbsent(option.id(), option) != null) {
                throw new IllegalArgumentException("Duplicate option: " + option.id());
            }
            validateScope(option.editScope(), capabilities);
        }
        validateScope(enableScope, capabilities);
        validateScope(disableScope, capabilities);
        Set<String> grouped = new java.util.HashSet<>();
        Set<String> groupIds = new java.util.HashSet<>();
        for (AtomicGroup group : atomicGroups) {
            if (!groupIds.add(group.id())) throw new IllegalArgumentException("Duplicate atomic group");
            for (String member : group.optionIds()) {
                MutatorOption option = byId.get(member);
                if (option == null || !grouped.add(member) || !group.scope().admits(option.editScope())) {
                    throw new IllegalArgumentException("Atomic group has unknown, repeated or unsafe member");
                }
            }
        }
    }

    public MutatorDefinition(String localId, String title, String description,
                             MutatorScope enableScope, MutatorScope disableScope,
                             List<MutatorOption> options, Set<MutatorCapability> capabilities,
                             PolicyFactory factory) {
        this(localId, title, description, 1, enableScope, disableScope, options,
                List.of(), Set.of(), Set.of(), capabilities, factory);
    }

    public Map<String, Object> defaults() {
        Map<String, Object> result = new LinkedHashMap<>();
        options.forEach(option -> result.put(option.id(), option.defaultValue()));
        return Map.copyOf(result);
    }

    public MutatorOption option(String id) {
        return options.stream().filter(option -> option.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown option: " + id));
    }

    public MutatorScope optionScope(String id) {
        MutatorScope own = option(id).editScope();
        return atomicGroups.stream().filter(group -> group.optionIds().contains(id))
                .map(AtomicGroup::scope).findFirst().orElse(own);
    }

    @com.openggf.game.ModApi
    @FunctionalInterface
    public interface PolicyFactory {
        /** Called only for an effective activation/revision, inside the engine's owner boundary. */
        List<MutatorPolicy> prepare(MutatorOptions admittedOptions);
    }

    @com.openggf.game.ModApi
    public record AtomicGroup(String id, MutatorScope scope, Set<String> optionIds) {
        public AtomicGroup {
            ModKeySyntax.requireLocalName(id);
            Objects.requireNonNull(scope, "scope");
            optionIds = Set.copyOf(optionIds);
            if (optionIds.size() < 2 || optionIds.size() > 32) {
                throw new IllegalArgumentException("Atomic groups require 2..32 options");
            }
            optionIds.forEach(ModKeySyntax::requireLocalName);
        }
    }

    private static void validateScope(MutatorScope scope, Set<MutatorCapability> capabilities) {
        if (capabilities.stream().anyMatch(capability -> !scope.admits(capability.minimumScope()))) {
            throw new IllegalArgumentException("Unsafe capability scope");
        }
    }
}
