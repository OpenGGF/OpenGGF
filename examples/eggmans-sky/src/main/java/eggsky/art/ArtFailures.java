package eggsky.art;

import com.openggf.mods.scene.SceneContext;
import java.util.LinkedHashMap;
import java.util.List;

/** Small scene-owned diagnostics, without retained exception graphs or retries on every draw. */
final class ArtFailures {
    enum Kind { OPTIONAL_ROM_MISSING, UNSUPPORTED, INVALID_REQUEST }
    record Entry(String request, String owner, Kind kind, String cause, long retryAt) { }
    private static final int CAPACITY = 64;
    private static final long RETRY_TICKS = 120;
    private final SceneContext context;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    ArtFailures(SceneContext context) { this.context = context; }

    boolean ready(String request) {
        Entry entry = entries.get(bounded(request));
        return entry == null || context.ticks() >= entry.retryAt();
    }

    void record(String request, Kind kind, String cause) {
        request = bounded(request);
        long now = context.ticks();
        long retryAt = now > Long.MAX_VALUE - RETRY_TICKS ? Long.MAX_VALUE : now + RETRY_TICKS;
        if (!entries.containsKey(request) && entries.size() == CAPACITY)
            entries.remove(entries.keySet().iterator().next());
        entries.put(request, new Entry(bounded(request), context.ownerModId(), kind, bounded(cause), retryAt));
    }

    void record(String request, RuntimeException cause) {
        record(request, Kind.INVALID_REQUEST, cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    void succeeded(String request) { entries.remove(bounded(request)); }
    List<Entry> entries() { return List.copyOf(entries.values()); }
    private static String bounded(String text) { return text.length() <= 256 ? text : text.substring(0, 256); }
}
