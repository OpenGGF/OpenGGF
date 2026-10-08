package com.openggf.level;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.graphics.ArenaMaskState;

/** Widescreen-only presentation: the ROM has no mask or fade. Each edge crossfades two
 * world-space mask shapes with one progress value, rather than giving each
 * newly covered column its own fade age. Native camera/player bounds remain unchanged. */
final class LevelBoundsMaskTransition implements RewindSnapshottable<LevelBoundsMaskTransition.State> {
    static final int FADE_FRAMES = 23;
    record Edge(ArenaMaskState sample, ArenaMaskState source, ArenaMaskState target, int elapsed) { }
    record State(int cameraX, int width, int tick, ArenaMaskState sample, Edge left, Edge right) { }
    private static final State EMPTY = new State(0, 0, 0, null, null, null);
    private State state;

    void advance(ArenaMaskState geometry, int cameraX, int width) {
        if (state != null && state.tick() == geometry.noiseFrame() && state.width() == width) return;
        boolean history = state != null && state.width() == width
                && Math.abs((long) cameraX - state.cameraX()) < width;
        Edge left = advanceEdge(geometry, cameraX, width, true, history ? state.left() : null);
        Edge right = advanceEdge(geometry, cameraX, width, false, history ? state.right() : null);
        float[] opacity = new float[width];
        for (int x = 0; x < width; x++) {
            opacity[x] = Math.max(left.sample().opacityAt(x), right.sample().opacityAt(x));
        }
        state = new State(cameraX, width, geometry.noiseFrame(),
                new ArenaMaskState(geometry.left(), geometry.right(), geometry.noiseFrame(), opacity), left, right);
    }

    private Edge advanceEdge(ArenaMaskState geometry, int cameraX, int width, boolean left, Edge old) {
        float[] previous = new float[width];
        float[] source = new float[width];
        float[] target = new float[width];
        boolean changed = false;
        for (int x = 0; x < width; x++) {
            target[x] = width > ArenaMaskState.NATIVE_WIDTH
                    && (left ? x < geometry.left() : x >= geometry.right()) ? 1 : 0;
            int oldX = old == null ? x : (int) Math.clamp((long) cameraX + x - state.cameraX(), 0, width - 1);
            // Project both shapes in world space. Beyond the previous viewport,
            // extend its nearest sample: the old camera edge is the starting
            // boundary when no mask was visible, not a new opaque screen-space wing.
            previous[x] = old == null ? target[x] : old.sample().opacityAt(oldX);
            source[x] = old == null ? target[x] : old.source().opacityAt(oldX);
            changed |= old != null && target[x] != old.target().opacityAt(oldX);
        }
        // A new effective lock interrupts the old blend, regardless of direction.
        // Snapshot what was actually displayed as the new source. In particular,
        // do not give a newly locked strip the old fade's almost-expired deadline:
        // that caused it to snap opaque while neighbouring strips were still fading.
        // Continuous native ratcheting may retarget each frame; once it stops the
        // entire edge reaches its new shape within FADE_FRAMES, with no queued work.
        int elapsed = old == null ? FADE_FRAMES : changed ? 1 : Math.min(FADE_FRAMES, old.elapsed() + 1);
        if (changed) source = previous;
        float progress = (float) elapsed / FADE_FRAMES;
        float[] opacity = new float[width];
        for (int x = 0; x < width; x++) {
            opacity[x] = source[x] + (target[x] - source[x]) * progress;
        }
        return new Edge(mask(geometry, opacity), mask(geometry, source), mask(geometry, target), elapsed);
    }

    private static ArenaMaskState mask(ArenaMaskState geometry, float[] opacity) {
        return new ArenaMaskState(geometry.left(), geometry.right(), geometry.noiseFrame(), opacity);
    }

    ArenaMaskState sample() { return state == null ? null : state.sample(); }
    void reset() { state = null; }
    @Override public String key() { return "level-bounds-mask"; }
    @Override public State capture() { return state == null ? EMPTY : state; }
    @Override public void restore(State snapshot) { state = snapshot.sample() == null ? null : snapshot; }
    @Override public void resetForMissingSnapshot() { reset(); }
}
