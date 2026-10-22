package com.openggf.graphics;

import java.util.Objects;
import java.util.function.Consumer;

/** Scoped observation of SAT-only controls; independent of objects, levels and gameplay. */
public final class SpriteCaptureControl {
    private static final ThreadLocal<Consumer<String>> CURRENT = new ThreadLocal<>();

    private SpriteCaptureControl() {}

    public static void control(String description) {
        Consumer<String> observer = CURRENT.get();
        if (observer != null) observer.accept(description);
    }

    public static void observe(Consumer<String> observer, Runnable draw) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested sprite control capture");
        CURRENT.set(Objects.requireNonNull(observer, "observer"));
        try {
            draw.run();
        } finally {
            CURRENT.remove();
        }
    }
}
