package com.openggf.mods.code;

import java.util.Objects;
import java.util.function.Supplier;

/** Thread-confined owner stack for callbacks that may create runtime objects. */
final class OwnerCallbackScope {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private OwnerCallbackScope() { }

    static String current() {
        Frame frame = CURRENT.get();
        return frame == null ? null : frame.owner;
    }

    static <T> T call(String owner, Supplier<T> callback) {
        Frame previous = CURRENT.get();
        CURRENT.set(new Frame(Objects.requireNonNull(owner, "owner"), previous));
        try {
            return Objects.requireNonNull(callback, "callback").get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    private record Frame(String owner, Frame previous) { }
}
