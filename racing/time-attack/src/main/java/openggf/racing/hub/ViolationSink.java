package openggf.racing.hub;

/** Receives stream-validation violations with owner-supplied player context. */
@FunctionalInterface
public interface ViolationSink {
    void onViolation(String kind, String detail);
}
