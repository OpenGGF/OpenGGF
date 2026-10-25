package com.openggf.mods.scene.host;

import com.openggf.mods.scene.SceneClipboard;
import java.util.function.BooleanSupplier;

/** One visit's consumable user activation and single pending platform write. */
final class ManagedSceneClipboard implements SceneClipboard {
    private static final long GESTURE_NANOS = 5_000_000_000L;
    private final SceneClipboardPlatform platform;
    private final BooleanSupplier ownerUpdating;
    private Gesture gesture;
    private Long invalidatedAt;
    private Pending pending;
    private boolean retired;

    ManagedSceneClipboard(SceneClipboardPlatform platform, BooleanSupplier ownerUpdating) {
        this.platform = platform;
        this.ownerUpdating = ownerUpdating;
    }

    void userGesture(long observedAt) {
        if (!retired && platform.foreground()
                && (invalidatedAt == null || observedAt - invalidatedAt > 0)) {
            gesture = new Gesture(observedAt);
        }
    }

    @Override public Write writeText(String text) {
        if (!platform.isOwnerThread() || !ownerUpdating.getAsBoolean() || retired
                || !platform.foreground() || !validText(text) || pending != null || gesture == null) {
            return () -> Result.DENIED;
        }
        long observedAt = gesture.atNanos();
        long elapsed = platform.nanoTime() - observedAt;
        gesture = null;
        if (elapsed < 0 || elapsed >= GESTURE_NANOS) return () -> Result.DENIED;
        if (!platform.available()) return () -> Result.UNAVAILABLE;
        pending = new Pending(text, observedAt);
        return pending.state;
    }

    void flush() {
        if (!platform.isOwnerThread()) throw new IllegalStateException("Clipboard requires the platform thread");
        Pending work = pending;
        pending = null;
        if (work != null) {
            long elapsed = platform.nanoTime() - work.gestureAt;
            work.state.result = retired || elapsed < 0 || elapsed >= GESTURE_NANOS
                    ? Result.DENIED : platform.write(work.text);
        }
    }

    void invalidate() {
        invalidatedAt = platform.nanoTime();
        gesture = null;
        if (pending != null) {
            pending.state.result = Result.DENIED;
            pending = null;
        }
    }

    void retire() {
        retired = true;
        invalidate();
    }

    private static boolean validText(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_TEXT_LENGTH) return false;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (value == 0 || Character.isLowSurrogate(value)) return false;
            if (Character.isHighSurrogate(value)
                    && (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))) return false;
        }
        return true;
    }

    private record Gesture(long atNanos) {}

    private static final class Pending {
        final String text;
        final long gestureAt;
        final State state = new State();
        Pending(String text, long gestureAt) { this.text = text; this.gestureAt = gestureAt; }
    }

    private static final class State implements Write {
        private volatile Result result = Result.PENDING;
        @Override public Result result() { return result; }
    }
}
