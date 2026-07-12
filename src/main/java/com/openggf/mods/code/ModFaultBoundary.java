package com.openggf.mods.code;

import com.openggf.mods.ModAudioPreparer;
import com.openggf.mods.ModFinding;
import com.openggf.mods.ModFindingSeverity;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owner-aware boundary for callbacks that run after a registration plan is published. */
public final class ModFaultBoundary {
    private static final Logger LOG = Logger.getLogger(ModFaultBoundary.class.getName());
    private final Map<String, Set<String>> dependents;
    private final ModRuntimeFindingStore findings;
    private final ModAudioPreparer.FailureStateSink stateSink;
    private final java.util.function.Consumer<Set<String>> processDisable;

    public ModFaultBoundary(Map<String, ? extends Set<String>> dependencies,
                            ModRuntimeFindingStore findings,
                            ModAudioPreparer.FailureStateSink stateSink,
                            java.util.function.Consumer<Set<String>> processDisable) {
        this.dependents = reverse(dependencies);
        this.findings = Objects.requireNonNull(findings, "findings");
        this.stateSink = Objects.requireNonNull(stateSink, "stateSink");
        this.processDisable = Objects.requireNonNull(processDisable, "processDisable");
    }

    public void run(String owner, Runnable callback) {
        call(owner, () -> { callback.run(); return null; });
    }

    public <T> T call(String owner, Supplier<T> callback) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(callback, "callback");
        try {
            return callback.get();
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            Set<String> disabled = ownerAndDependents(owner);
            try {
                processDisable.accept(disabled);
            } catch (RuntimeException disableFailure) {
                failure.addSuppressed(disableFailure);
                LOG.log(Level.SEVERE, "Failed to mark owners disabled in process " + disabled,
                        disableFailure);
            }
            findings.upsertOwnerFinding(owner, new ModFinding(
                    ModFindingSeverity.ERROR, "MOD_CALLBACK_FAILED",
                    "Runtime callback failed; owner and dependents are pending-disabled", null));
            LOG.log(Level.SEVERE, "Mod callback failed for " + owner, failure);
            try {
                ModStateSaveResult result = stateSink.disableAndSave(disabled);
                if (result instanceof ModStateSaveResult.Failed failed) {
                    IllegalStateException persistenceFailure = new IllegalStateException(failed.message());
                    failure.addSuppressed(persistenceFailure);
                    publishSaveFailure(owner, failed.message());
                    LOG.log(Level.SEVERE, "Failed to persist pending disable for " + disabled,
                            persistenceFailure);
                }
            } catch (RuntimeException persistenceFailure) {
                failure.addSuppressed(persistenceFailure);
                publishSaveFailure(owner, persistenceFailure.getMessage() == null
                        ? persistenceFailure.getClass().getSimpleName()
                        : persistenceFailure.getMessage());
                LOG.log(Level.SEVERE, "Failed to persist pending disable for " + disabled,
                        persistenceFailure);
            }
            throw new CallbackAborted(owner, disabled, failure);
        }
    }

    /** Owner-derived boundary for a mod-character callback; builtins execute directly. */
    public <T> T callCharacter(com.openggf.game.CharacterKey key, Supplier<T> callback) {
        Objects.requireNonNull(key, "key");
        String owner = key.ownerModId().orElse(null);
        return owner == null ? Objects.requireNonNull(callback, "callback").get()
                : call(owner, callback);
    }

    public void runCharacter(com.openggf.game.CharacterKey key, Runnable callback) {
        callCharacter(key, () -> { Objects.requireNonNull(callback, "callback").run(); return null; });
    }

    public <T> T callCharacterIo(com.openggf.game.CharacterKey key, IoSupplier<T> callback)
            throws java.io.IOException {
        Objects.requireNonNull(callback, "callback");
        try {
            return callCharacter(key, () -> {
                try { return callback.get(); }
                catch (java.io.IOException failure) { throw new IoCallbackFailure(failure); }
            });
        } catch (IoCallbackFailure failure) {
            throw (java.io.IOException) failure.getCause();
        }
    }

    @FunctionalInterface
    public interface IoSupplier<T> { T get() throws java.io.IOException; }

    private static final class IoCallbackFailure extends RuntimeException {
        private IoCallbackFailure(java.io.IOException cause) { super(cause); }
    }

    /** Owner-derived boundary for a standalone module/game/provider callback. */
    public <T> T callStandalone(String ownerModId, Supplier<T> callback) {
        return call(com.openggf.game.ModKeySyntax.requireManifestId(ownerModId), callback);
    }

    public void runStandalone(String ownerModId, Runnable callback) {
        callStandalone(ownerModId, () -> { Objects.requireNonNull(callback, "callback").run(); return null; });
    }

    private void publishSaveFailure(String owner, String message) {
        findings.upsertOwnerFinding(owner, new ModFinding(ModFindingSeverity.ERROR,
                "MOD_DISABLE_SAVE_FAILED", message, null));
    }

    private Set<String> ownerAndDependents(String owner) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (result.add(current)) pending.addAll(dependents.getOrDefault(current, Set.of()));
        }
        return Set.copyOf(result);
    }

    private static Map<String, Set<String>> reverse(Map<String, ? extends Set<String>> dependencies) {
        java.util.LinkedHashMap<String, java.util.LinkedHashSet<String>> reversed = new java.util.LinkedHashMap<>();
        Objects.requireNonNull(dependencies, "dependencies").forEach((owner, required) -> {
            Objects.requireNonNull(owner, "dependency owner");
            Objects.requireNonNull(required, "owner dependencies").forEach(dependency ->
                    reversed.computeIfAbsent(Objects.requireNonNull(dependency, "dependency"),
                            ignored -> new java.util.LinkedHashSet<>()).add(owner));
        });
        java.util.LinkedHashMap<String, Set<String>> frozen = new java.util.LinkedHashMap<>();
        reversed.forEach((owner, values) -> frozen.put(owner, Set.copyOf(values)));
        return Map.copyOf(frozen);
    }

    private static void rethrowIfFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError vm) throw vm;
        if (failure instanceof ThreadDeath death) throw death;
    }

    public static final class CallbackAborted extends RuntimeException {
        private final String owner;
        private final Set<String> disabledOwners;
        CallbackAborted(String owner, Set<String> disabledOwners, Throwable cause) {
            super("Mod callback aborted gameplay for " + owner, cause);
            this.owner = owner;
            this.disabledOwners = disabledOwners;
        }
        public String owner() { return owner; }
        public Set<String> disabledOwners() { return disabledOwners; }
    }
}
