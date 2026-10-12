package com.openggf.level;

import com.openggf.level.objects.ObjectCallbackAbortException;
import java.io.IOException;
import java.util.Objects;

/** Host-owned checked load failure retaining a deferred callback's exact abort signal. */
public final class DeferredLevelLoadException extends IOException {
    private final ObjectCallbackAbortException callbackAbort;

    // Only the level loader constructs this trusted carrier, after executing its profile.
    DeferredLevelLoadException(ObjectCallbackAbortException callbackAbort) {
        super("Failed to load level due to unexpected error.", Objects.requireNonNull(callbackAbort));
        this.callbackAbort = callbackAbort;
    }

    /** Direct checked load failure or the ordinary native load caller's single runtime wrapper. */
    public static ObjectCallbackAbortException callbackAbortInLoadFailure(Throwable failure) {
        if (failure != null && failure.getClass() == RuntimeException.class) failure = failure.getCause();
        return failure instanceof DeferredLevelLoadException deferred ? deferred.callbackAbort : null;
    }

    /** IO fallback consumers must let a trusted creator abort reach host frame recovery. */
    public static void rethrowCallbackAbort(IOException failure) {
        if (failure instanceof DeferredLevelLoadException deferred) throw deferred.callbackAbort;
    }
}
