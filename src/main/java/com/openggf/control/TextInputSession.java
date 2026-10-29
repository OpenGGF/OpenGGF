package com.openggf.control;

import java.util.BitSet;
import java.util.Objects;
import static org.lwjgl.glfw.GLFW.*;

/** One window's host text lease. Native reset requests never stand in for native acknowledgment. */
public final class TextInputSession {
    public interface NativeHost {
        void reset();
        void caretRectangle(int x, int y, int width, int height);
    }
    private static final NativeHost INERT = new NativeHost() {
        public void reset() { }
        public void caretRectangle(int x, int y, int width, int height) { }
    };
    private enum Phase { INACTIVE, ACTIVE, DRAINING }
    public final class Lease implements AutoCloseable {
        private final Object identity;
        private final long generation;
        private Lease(Object identity, long generation) { this.identity = identity; this.generation = generation; }
        public Composition composition() { return current() && phase == Phase.ACTIVE ? composition : Composition.EMPTY; }
        public boolean draining() { return current() && phase == Phase.DRAINING; }
        public boolean suppressCommands() { return current() && TextInputSession.this.suppressCommands(); }
        public void cancelComposition() { if (current()) drain(); }
        public void caretRectangle(int x, int y, int width, int height) {
            if (current() && phase == Phase.ACTIVE) nativeHost.caretRectangle(x, y, width, height);
        }
        private boolean current() { return owner == identity && epoch == generation; }
        @Override public void close() { if (current()) withdraw(); }
    }
    private final InputHandler input;
    private final Object legacyOwner = new Object();
    private final BitSet suppressed = new BitSet();
    private NativeHost nativeHost = INERT;
    private Object owner;
    private Lease lease;
    private long epoch;
    private long renewedFrame = Long.MIN_VALUE;
    private long touchedFrame = Long.MIN_VALUE;
    private Composition composition = Composition.EMPTY;
    private Phase phase = Phase.INACTIVE;
    private boolean acknowledged;
    private boolean focused = true;
    private boolean hostPresentation;

    TextInputSession(InputHandler input) { this.input = input; }

    public Lease claim(Object identity) {
        Objects.requireNonNull(identity);
        if (!focused) return new Lease(identity, -1);
        if (owner != identity) {
            boolean oldComposition = !composition.isEmpty() || phase == Phase.DRAINING;
            if (owner != null) input.discardMenuText();
            if (oldComposition) drain();
            hostPresentation = false;
            owner = identity;
            lease = new Lease(identity, ++epoch);
            if (phase != Phase.DRAINING) phase = Phase.ACTIVE;
        }
        renewedFrame = input.menuFrame;
        return lease;
    }

    void captureLegacy() { captureLegacy(legacyOwner); }
    Lease captureLegacy(Object identity) {
        Lease result = claim(identity);
        if (owner == identity) hostPresentation = true;
        return result;
    }
    void prepareHostConsumer(Object identity) {
        if (hostPresentation && owner != identity) withdraw();
    }
    void releaseHostCapture(Object identity) {
        if (hostPresentation && owner == identity) withdraw();
    }
    public Composition legacyComposition() { return hostPresentation && phase == Phase.ACTIVE ? composition : Composition.EMPTY; }
    public void legacyCaretRectangle(int x, int y, int width, int height) {
        if (hostPresentation && lease != null) lease.caretRectangle(x, y, width, height);
    }
    public void attach(NativeHost host) { nativeHost = Objects.requireNonNull(host); }
    public void detach() { withdraw(); nativeHost = INERT; }
    public void focus(boolean value) { focused = value; if (!value) withdraw(); }

    /** Existing host ownership and its unfinished native reset take precedence over window chrome. */
    public boolean ownsPreedit() { return owner != null || phase == Phase.DRAINING; }

    public void preedit(Composition next) {
        Objects.requireNonNull(next);
        if (owner != null) touchComposition();
        if (phase == Phase.DRAINING) {
            acknowledged = next.isEmpty();
            return;
        }
        if (owner == null || !focused) {
            if (!next.isEmpty()) drain();
            return;
        }
        if (!next.isEmpty()) input.noteKeyboardInput();
        composition = next;
    }

    void malformedPreedit() { if (owner != null) touchComposition(); }
    private void touchComposition() {
        touchedFrame = input.menuFrame;
        suppressHeldKeys();
    }
    boolean windowFocused() { return focused; }
    public boolean acceptsCommit() { return phase != Phase.DRAINING && focused; }
    public boolean suppressCommands() { return !focused || owner != null && (phase == Phase.DRAINING || !composition.isEmpty() || touchedFrame == input.menuFrame); }
    boolean hidesKey(int key) { return suppressCommands() || key >= 0 && suppressed.get(key); }

    /** Called before chrome filtering. Releases remain visible to physical state owners. */
    public boolean key(int key, int action) {
        if (key < 0) return !suppressCommands();
        if (action == GLFW_RELEASE) { suppressed.clear(key); return true; }
        if (hidesKey(key)) { suppressed.set(key); return false; }
        return true;
    }

    private void suppressHeldKeys() {
        for (int k = 0; k < input.keys.length; k++) if (input.keys[k]) suppressed.set(k);
        input.discardCompositionKeyEvents();
    }
    private void drain() {
        if (phase != Phase.DRAINING) {
            phase = Phase.DRAINING;
            acknowledged = false;
            composition = Composition.EMPTY;
            if (owner != null) touchComposition();
            input.discardMenuText();
            nativeHost.reset();
        }
    }
    private void withdraw() {
        if (!composition.isEmpty() || phase == Phase.DRAINING) drain();
        else phase = Phase.INACTIVE;
        owner = null;
        lease = null;
        hostPresentation = false;
        epoch++;
        input.discardMenuText();
    }
    /** Clear acknowledgment releases a drain only after the old callback batch is discarded. */
    public void endEvents() {
        if (phase == Phase.DRAINING && acknowledged) {
            phase = owner == null ? Phase.INACTIVE : Phase.ACTIVE;
            acknowledged = false;
        }
    }
    void endFrame() {
        if (owner != null && renewedFrame < input.menuFrame - 1) withdraw();
    }
}
