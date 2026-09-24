package com.openggf.level;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.graphics.ArenaMaskState;

/** Widescreen-only presentation: the ROM has no mask or fade. Each edge owns one
 * completion deadline, rather than giving each newly covered world column its own
 * fade age. Native camera/player bounds remain unchanged. */
final class LevelBoundsMaskTransition implements RewindSnapshottable<LevelBoundsMaskTransition.State> {
    static final int FADE_FRAMES = 23;
    record Edge(ArenaMaskState sample, int remaining, int direction) { }
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
        float[] target = new float[width];
        boolean increasing = false;
        boolean decreasing = false;
        for (int x = 0; x < width; x++) {
            target[x] = width > ArenaMaskState.NATIVE_WIDTH
                    && (left ? x < geometry.left() : x >= geometry.right()) ? 1 : 0;
            int oldX = old == null ? x : (int) Math.clamp((long) cameraX + x - state.cameraX(), 0, width - 1);
            // Project history in world space, extending its nearest sample into newly
            // exposed columns. Clipped screen-edge coordinates are not new lock events.
            previous[x] = old == null ? target[x] : old.sample().opacityAt(oldX);
            increasing |= target[x] > previous[x];
            decreasing |= target[x] < previous[x];
        }
        int direction = increasing && !decreasing ? 1 : decreasing && !increasing ? -1 : 0;
        int remaining = old == null ? 0 : old.remaining();
        // Retarget all columns from their actual displayed values. Same-direction
        // motion shares the existing deadline, so a ratcheting ROM lock cannot keep
        // restarting the fade. Reversal interrupts immediately, without a queued fade.
        if ((increasing || decreasing) && (remaining == 0 || direction != old.direction())) {
            remaining = FADE_FRAMES;
        }
        for (int x = 0; x < width; x++) {
            previous[x] = remaining <= 1 ? target[x]
                    : previous[x] + (target[x] - previous[x]) / remaining;
        }
        return new Edge(new ArenaMaskState(geometry.left(), geometry.right(), geometry.noiseFrame(), previous),
                Math.max(0, remaining - 1), direction);
    }

    ArenaMaskState sample() { return state == null ? null : state.sample(); }
    void reset() { state = null; }
    @Override public String key() { return "level-bounds-mask"; }
    @Override public State capture() { return state == null ? EMPTY : state; }
    @Override public void restore(State snapshot) { state = snapshot.sample() == null ? null : snapshot; }
    @Override public void resetForMissingSnapshot() { reset(); }
}
