package com.openggf.mods.code;

import com.openggf.io.PackedModAssetRoot;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Boot-scoped, immutable ownership registry for enabled compiled-mod loaders. */
public final class ModRuntime implements AutoCloseable {
    private final Map<String, ModDependencyClassLoader> loaders;
    private final List<String> owners;
    private final List<PackedModAssetRoot> snapshots;
    private final Map<String, Rejection> rejectedOwners;
    private boolean closed;

    ModRuntime(Map<String, ModDependencyClassLoader> loaders,
               List<PackedModAssetRoot> snapshots, Map<String, Rejection> rejectedOwners) {
        this.loaders = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(loaders, "loaders")));
        this.owners = List.copyOf(loaders.keySet());
        this.snapshots = List.copyOf(Objects.requireNonNull(snapshots, "snapshots"));
        this.rejectedOwners = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(rejectedOwners, "rejectedOwners")));
    }

    public static ModRuntime empty() {
        return new ModRuntime(Map.of(), List.of(), Map.of());
    }

    /** Resolves a dynamic class through exactly the loader that owns its snapshot. */
    public synchronized Class<?> loadOwned(String ownerModId, String binaryName)
            throws ClassNotFoundException {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(binaryName, "binaryName");
        if (closed) throw new ClassNotFoundException("Mod runtime is closed");
        ModDependencyClassLoader loader = loaders.get(ownerModId);
        if (loader == null) throw new ClassNotFoundException("No compiled-mod loader for " + ownerModId);
        return loader.loadClass(binaryName);
    }

    public List<String> owners() {
        return owners;
    }

    public Map<String, Rejection> rejectedOwners() {
        return rejectedOwners;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        List<ModDependencyClassLoader> reverse = new ArrayList<>(loaders.values());
        for (int index = reverse.size() - 1; index >= 0; index--) {
            try {
                reverse.get(index).close();
            } catch (IOException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        for (int index = snapshots.size() - 1; index >= 0; index--) {
            try {
                snapshots.get(index).close();
            } catch (IOException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    public enum RejectionReason {
        HASH_MISMATCH,
        VALIDATION_FAILED,
        DEPENDENCY_UNAVAILABLE,
        INSPECTION_BUDGET_EXCEEDED,
        SNAPSHOT_FAILED
    }

    public record Rejection(RejectionReason reason, String detail) {
        public Rejection {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
            if (detail.isBlank()) throw new IllegalArgumentException("detail must be nonblank");
        }
    }
}
