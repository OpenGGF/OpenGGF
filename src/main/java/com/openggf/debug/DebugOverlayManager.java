package com.openggf.debug;

import com.openggf.configuration.KeyChord;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.*;

public class DebugOverlayManager {
    private static final Logger LOGGER = Logger.getLogger(DebugOverlayManager.class.getName());
    private static DebugOverlayManager debugOverlayManager;

    /** Ctrl+P copies the performance stats; it must not also toggle the panel. */
    static final KeyChord PERFORMANCE_STATS_COPY =
            KeyChord.of(GLFW_KEY_P, KeyChord.Modifier.CTRL);

    private final EnumMap<DebugOverlayToggle, Boolean> states = new EnumMap<>(DebugOverlayToggle.class);

    /** GLFW window handle for clipboard operations - set by Engine */
    private long windowHandle;

    /** Where a copied report goes; swapped out by tests that have no window. */
    private Consumer<String> clipboardWriter = this::writeToGlfwClipboard;

    /** Reusable list for shortcut lines to avoid per-frame allocations */
    private final List<String> shortcutLines = new ArrayList<>(16);
    private boolean shortcutLinesDirty = true;

    /** Per-frame text entries from object debug rendering, set by LevelManager, read by DebugRenderer */
    private List<DebugRenderContext.DebugTextEntry> pendingObjectDebugText = List.of();
    private final DebugObjectArtViewer objectArtViewer = new DebugObjectArtViewer();

    private DebugOverlayManager() {
        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            states.put(toggle, toggle.defaultEnabled());
        }
    }

    public static synchronized DebugOverlayManager getInstance() {
        if (debugOverlayManager == null) {
            debugOverlayManager = new DebugOverlayManager();
        }
        return debugOverlayManager;
    }

    public void updateInput(InputHandler handler) {
        updateInput(handler, true);
    }

    /** For callers with no configuration in play; prefer the three-argument form. */
    public void updateInput(InputHandler handler, boolean debugShortcutsEnabled) {
        updateInput(handler, debugShortcutsEnabled, (SonicConfigurationService) null);
    }

    /**
     * @param configService read for {@code capture.toggleKey}, the one configured
     *        chord that can land on an overlay toggle's key; {@code null} when
     *        there is no configuration to consult.
     */
    public void updateInput(InputHandler handler, boolean debugShortcutsEnabled,
            SonicConfigurationService configService) {
        updateInput(handler, debugShortcutsEnabled, configService == null ? null
                : configService.getKeyChord(SonicConfiguration.CAPTURE_TOGGLE_KEY));
    }

    /**
     * @param captureToggle the configured {@code capture.toggleKey} chord, or
     *        {@code null} when there is none. Only chords bound to the same key
     *        as a toggle can block it.
     */
    public void updateInput(InputHandler handler, boolean debugShortcutsEnabled,
            KeyChord captureToggle) {
        if (handler == null) {
            return;
        }
        if (togglePressed(handler, DebugOverlayToggle.PERFORMANCE.keyCode(), captureToggle)) {
            setEnabled(DebugOverlayToggle.PERFORMANCE,
                    !isEnabled(DebugOverlayToggle.PERFORMANCE));
        }

        // Ctrl+P copies performance stats to clipboard. Read as the chord the
        // toggles are checked against, so the two agree about which keystroke
        // belongs to which action; isControlDown() also honours right Ctrl,
        // which the previous isKeyDown(GLFW_KEY_LEFT_CONTROL) did not.
        //
        // Dispatched above the debug-shortcuts gate, with the PERFORMANCE toggle
        // it is paired with. The toggle stands down for this chord, so leaving
        // the copy below the gate made Ctrl+P a wholly dead keystroke on the
        // shipped default (debug.viewEnabled is false): no overlay, no copy.
        if (handler.isKeyPressed(PERFORMANCE_STATS_COPY.keyCode())
                && modifiersMatch(handler, PERFORMANCE_STATS_COPY)) {
            copyPerformanceStatsToClipboard();
        }

        if (!debugShortcutsEnabled) {
            return;
        }
        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            if (toggle == DebugOverlayToggle.PERFORMANCE) {
                continue;
            }
            if (togglePressed(handler, toggle.keyCode(), captureToggle)) {
                setEnabled(toggle, !isEnabled(toggle));
            }
        }
    }

    /**
     * A hardcoded overlay toggle fires on its bare key, but must not consume a
     * keystroke a chord has already claimed — OBJECT_DEBUG (O) would otherwise
     * fire on the {@code SHIFT+O} that starts a recording, and PERFORMANCE (P)
     * on the Ctrl+P that copies the stats.
     *
     * <p>Only those exact chords block it. A blanket "no modifier held" rule
     * would disable all sixteen toggles for as long as any modifier is down —
     * and P2's jump defaults to RIGHT_SHIFT, so in a two-player session P2
     * holding jump would silently switch every debug overlay shortcut off, as
     * would the Shift a developer holds for debug fast movement.
     */
    private static boolean togglePressed(InputHandler handler, int keyCode, KeyChord captureToggle) {
        if (!handler.isKeyPressed(keyCode)) {
            return false;
        }
        return !claims(handler, PERFORMANCE_STATS_COPY, keyCode)
                && !claims(handler, captureToggle, keyCode);
    }

    /** True when {@code chord} is a modified chord on {@code keyCode} and is satisfied now. */
    private static boolean claims(InputHandler handler, KeyChord chord, int keyCode) {
        return chord != null && chord.isBound() && chord.keyCode() == keyCode
                && !chord.modifiers().isEmpty() && modifiersMatch(handler, chord);
    }

    private static boolean modifiersMatch(InputHandler handler, KeyChord chord) {
        return chord.matchesModifiers(handler.isShiftDown(), handler.isControlDown(),
                handler.isAltDown(), handler.isSuperDown());
    }

    private void copyPerformanceStatsToClipboard() {
        StringBuilder sb = new StringBuilder();

        // Performance profiler stats
        ProfileSnapshot snapshot = GameServices.profiler().getSnapshot();
        if (snapshot.hasData()) {
            sb.append("=== Performance Stats ===\n");
            sb.append(String.format("Frame Time: %.2fms (%.1f%% of 16.67ms budget)\n",
                    snapshot.totalFrameTimeMs(),
                    (snapshot.totalFrameTimeMs() / 16.67) * 100));
            sb.append(String.format("FPS: %.1f\n\n", snapshot.fps()));

            sb.append("Section Timings:\n");
            for (SectionStats section : snapshot.getSectionsSortedByTime()) {
                sb.append(String.format("  %-12s %6.2fms (%5.1f%%)\n",
                        section.name(), section.timeMs(), section.percentage()));
            }
            sb.append("\n");
        }

        // Memory stats
        MemoryStats.Snapshot memSnapshot = GameServices.profiler().memoryStats().snapshot();
        sb.append("=== Memory Stats ===\n");
        sb.append(String.format("Heap: %.0fMB / %.0fMB (%d%%)\n",
                memSnapshot.heapUsedMB(), memSnapshot.heapMaxMB(), memSnapshot.heapPercentage()));
        sb.append(String.format("GC Collections: %d (total time: %dms)\n",
                memSnapshot.gcCount(), memSnapshot.gcTimeMs()));
        sb.append(String.format("Allocation Rate: %.2fMB/s\n\n", memSnapshot.allocationRateMBPerSec()));

        List<MemoryStats.SectionAllocation> topAllocators = memSnapshot.topAllocators();
        if (!topAllocators.isEmpty()) {
            sb.append("Top Allocators (per frame avg):\n");
            for (MemoryStats.SectionAllocation alloc : topAllocators) {
                sb.append(String.format("  %-12s %8.1fKB\n", alloc.name(), alloc.kbPerFrame()));
            }
        }

        clipboardWriter.accept(sb.toString());
    }

    /** Copy to clipboard using GLFW (avoids AWT dependency for native images). */
    private void writeToGlfwClipboard(String text) {
        if (windowHandle != 0) {
            glfwSetClipboardString(windowHandle, text);
            LOGGER.info("Performance stats copied to clipboard");
        } else {
            LOGGER.warning("Cannot copy to clipboard: window handle not set");
        }
    }

    /**
     * Redirects the copy, so a test with no GLFW window can observe that Ctrl+P
     * reached the clipboard at all. {@code null} restores the GLFW sink.
     */
    void setClipboardWriter(Consumer<String> writer) {
        this.clipboardWriter = writer == null ? this::writeToGlfwClipboard : writer;
    }

    /**
     * Sets the GLFW window handle for clipboard operations.
     * Must be called after window creation.
     */
    public void setWindowHandle(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    public boolean isEnabled(DebugOverlayToggle toggle) {
        return states.getOrDefault(toggle, Boolean.TRUE);
    }

    public void setEnabled(DebugOverlayToggle toggle, boolean enabled) {
        Boolean previous = states.put(toggle, enabled);
        if (previous == null || previous != enabled) {
            shortcutLinesDirty = true;
        }
    }

    public void setObjectDebugTextEntries(List<DebugRenderContext.DebugTextEntry> entries) {
        this.pendingObjectDebugText = entries;
    }

    public List<DebugRenderContext.DebugTextEntry> getObjectDebugTextEntries() {
        return pendingObjectDebugText;
    }

    public DebugObjectArtViewer getObjectArtViewer() {
        return objectArtViewer;
    }

    public void clearObjectDebugTextEntries() {
        pendingObjectDebugText = List.of();
    }

    public void resetState() {
        states.clear();
        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            states.put(toggle, toggle.defaultEnabled());
        }
        pendingObjectDebugText = List.of();
        shortcutLinesDirty = true;
    }

    public List<String> buildShortcutLines() {
        if (shortcutLinesDirty) {
            shortcutLines.clear();
            for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
                String state = isEnabled(toggle) ? "On" : "Off";
                shortcutLines.add(toggle.shortcutLabel() + " " + toggle.label() + ": " + state);
            }
            shortcutLinesDirty = false;
        }
        return shortcutLines;
    }
}
