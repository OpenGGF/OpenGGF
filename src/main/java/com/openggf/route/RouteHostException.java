package com.openggf.route;

/** A host refused a launch, a row or a checkpoint. */
public final class RouteHostException extends RuntimeException {
    /** Why a host refused. */
    public enum Reason {
        /** The resolver has no ROM matching the launch's identity. */
        ROM_UNAVAILABLE,
        /** The launch asks for something this host cannot reproduce. */
        UNSUPPORTED_LAUNCH,
        /** Something other than the recorded rows could own gameplay input (a creator frame controller). */
        INPUT_NOT_OWNED,
        /** A checkpoint was refused (pending assembly, callback fade, non-level mode). */
        CHECKPOINT_REFUSED,
        /** A checkpoint does not belong to this host, epoch or registry layout. */
        CHECKPOINT_MISMATCH
    }

    private final Reason reason;

    public RouteHostException(Reason reason, String message) {
        super(reason + ": " + message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
