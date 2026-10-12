package starpost.art;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Local expected-load status, separate from successfully prepared assets and creator faults. */
public final class AssetFailures implements AutoCloseable {
    public enum Kind { OPTIONAL_ROM_MISSING, UNSUPPORTED, INVALID_DATA, UNAVAILABLE }
    public record Failure(String owner, String request, Kind kind, String cause, long retryAt) {}
    private final String owner;
    private final LongSupplier clock;
    private final LinkedHashMap<String, Failure> failures = new LinkedHashMap<>();
    private boolean closed;

    public AssetFailures(String owner, LongSupplier clock) {
        this.owner = Objects.requireNonNull(owner);
        this.clock = Objects.requireNonNull(clock);
    }
    public List<Failure> failures() { return List.copyOf(failures.values()); }
    public <T> T load(String request, Supplier<T> load) {
        requireOpen();
        long now = clock.getAsLong(); // A broken clock is a creator fault, outside the load catches.
        Failure prior = failures.get(request);
        if (prior != null && now < prior.retryAt()) return null;
        try {
            T value = load.get();
            if (value == null) {
                record(request, Kind.UNSUPPORTED, "Provider returned no asset", now);
            } else {
                failures.remove(request);
            }
            return value;
        } catch (MissingRom missing) {
            record(request, Kind.OPTIONAL_ROM_MISSING, missing.getMessage(), now);
        } catch (Unavailable unavailable) {
            record(request, Kind.UNAVAILABLE, unavailable.getMessage(), now);
        } catch (UnsupportedOperationException unsupported) {
            record(request, Kind.UNSUPPORTED, cause(unsupported), now);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            record(request, Kind.INVALID_DATA, cause(invalid), now);
        }
        // NPE, CallbackAborted, ordinary RuntimeException and Error retain the owner boundary.
        return null;
    }
    public static <T> T requireRom(T rom, String game) {
        if (rom == null) throw new MissingRom("Supplied " + game + " ROM is unavailable");
        return rom;
    }
    public static void unavailable(String cause) { throw new Unavailable(cause); }
    private void record(String request, Kind kind, String cause, long now) {
        if (!failures.containsKey(request) && failures.size() == 64) failures.remove(failures.keySet().iterator().next());
        long retryAt = now > Long.MAX_VALUE - 120 ? Long.MAX_VALUE : now + 120;
        failures.put(request, new Failure(owner, bounded(request), kind, bounded(cause), retryAt));
    }
    private static String bounded(String value) { return value.length() <= 256 ? value : value.substring(0, 256); }
    private static String cause(RuntimeException failure) { return failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), ""); }
    private void requireOpen() { if (closed) throw new IllegalStateException("Asset owner is closed"); }
    @Override public void close() { closed = true; failures.clear(); }
    private static final class MissingRom extends RuntimeException { MissingRom(String cause) { super(cause); } }
    private static final class Unavailable extends RuntimeException { Unavailable(String cause) { super(cause); } }
}
