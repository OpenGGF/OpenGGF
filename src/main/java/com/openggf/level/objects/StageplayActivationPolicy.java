package com.openggf.level.objects;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.sprites.managers.SpriteManager;

import java.util.List;

/** Disposable-world activation around each performer, independent of the gameplay camera.
 * Windows are disjoint: an ensemble does not activate every object between its members.
 * Native routines, slot allocation and placement destruction/remember state remain authoritative.
 */
public final class StageplayActivationPolicy implements WorldSessionPolicyState {
    public record Window(int left, int top, int width, int height) {
        public Window {
            if (width < 1 || height < 1) throw new IllegalArgumentException("Invalid activation window");
        }
        boolean contains(int x, int y, int xMargin, int yMargin) {
            return (long) x >= (long) left - xMargin && (long) x < (long) left + width + xMargin
                    && (long) y >= (long) top - yMargin && (long) y < (long) top + height + yMargin;
        }
    }

    private List<Window> windows = List.of();
    private java.util.Set<Integer> previousPlacements = java.util.Set.of();
    private final java.util.Set<Integer> skippedFactories = new java.util.HashSet<>();
    private final java.util.Map<Integer, Integer> counterOwners = new java.util.HashMap<>();

    /** Canonical placement indices, never runtime SST slots. */
    public record Snapshot(List<Window> windows, java.util.Set<Integer> previousPlacements,
                           java.util.Map<Integer, Integer> counterOwners, java.util.Set<Integer> skippedFactories) {
        public Snapshot {
            windows = List.copyOf(windows);
            previousPlacements = java.util.Set.copyOf(previousPlacements);
            counterOwners = java.util.Map.copyOf(counterOwners);
            skippedFactories = java.util.Set.copyOf(skippedFactories);
        }
    }
    void skippedFactory(int index) { skippedFactories.add(index); }
    boolean consumeSkippedFactory(int index) { return skippedFactories.remove(index); }
    boolean entered(int index) { return !previousPlacements.contains(index); }
    void considered(java.util.Set<Integer> indices) { previousPlacements = java.util.Set.copyOf(indices); }
    boolean ownsCounter(int index, int counter) { return java.util.Objects.equals(counterOwners.get(counter), index); }
    void checkCounter(int index, int counter) {
        Integer owner = counterOwners.get(counter);
        if (owner != null && owner != index) throw counterConflict(index, "counter aliases placement " + owner);
        for (var entry : counterOwners.entrySet())
            if (entry.getValue() == index && entry.getKey() != counter)
                throw counterConflict(index, "native cursor reassigned its respawn counter");
    }
    void reserveCounter(int index, int counter) { checkCounter(index, counter); counterOwners.put(counter, index); }
    static IllegalStateException counterConflict(int index, String reason) {
        return new IllegalStateException("Stageplay native respawn counter conflict at placement " + index + ": "
                + reason + "; reduce the ensemble distance, clear objects or split the shot");
    }
    private void clear() { windows = List.of(); previousPlacements = java.util.Set.of(); counterOwners.clear(); skippedFactories.clear(); }

    public <T> T getService(Class<T> type) {
        return type == StageplayActivationPolicy.class ? type.cast(this) : null;
    }

    void setWindows(List<Window> values) { windows = List.copyOf(values); }
    boolean enabled() { return !windows.isEmpty(); }
    boolean contains(int x, int y, int xMargin, int yMargin) {
        return windows.stream().anyMatch(w -> w.contains(x, y, xMargin, yMargin));
    }
    boolean keeps(ObjectInstance object) {
        // Slot-only native process owners can have no spatial spawn. Their
        // lifetime remains native; ObjectInstance's default coordinates require one.
        return !windows.isEmpty() && object != null && object.getSpawn() != null
                && contains(object.getX(), object.getY(), 128, 128);
    }
    boolean keepsOutsideNativeView(ObjectInstance object, com.openggf.camera.Camera camera) {
        if (!keeps(object)) return false;
        return camera == null || object.getX() < camera.getX() - 128
                || object.getX() >= camera.getX() + camera.getWidth() + 128
                || object.getY() < camera.getY() - 128
                || object.getY() >= camera.getY() + camera.getHeight() + 128;
    }
    boolean loads(ObjectSpawn spawn) {
        // These are authoring windows, not new ROM constants. Match the native
        // viewport's existing placement overscan while keeping separated casts disjoint.
        return spawn != null && contains(spawn.x(), spawn.y(), 128, 128);
    }

    public RewindSnapshottable<Snapshot> rewindAdapter() {
        return new RewindSnapshottable<>() {
            public String key() { return "stageplay-activation"; }
            public Snapshot capture() { return new Snapshot(windows, previousPlacements, counterOwners, skippedFactories); }
            public void restore(Snapshot saved) {
                windows = saved.windows(); previousPlacements = saved.previousPlacements();
                counterOwners.clear(); counterOwners.putAll(saved.counterOwners());
                skippedFactories.clear(); skippedFactories.addAll(saved.skippedFactories());
            }
            public void resetForMissingSnapshot() { clear(); }
        };
    }
    public void beforeAssembly(LevelLoadCause cause) { clear(); }
    public void bindRoster(SpriteManager sprites) { }
    public void failedAssembly(LevelLoadCause cause) { clear(); }
    public void closeScreens() { }
    public void retire() { clear(); }
}
