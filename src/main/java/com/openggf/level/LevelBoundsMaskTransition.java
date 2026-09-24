package com.openggf.level;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.graphics.ArenaMaskState;

/** Common viewport presentation history, not an encounter switch. Each world column
 * fades when bounds begin covering it. Camera motion projects that history into the
 * viewport; newly exposed columns inherit the adjacent fade instead of arriving opaque. */
final class LevelBoundsMaskTransition implements RewindSnapshottable<LevelBoundsMaskTransition.State> {
    static final int FADE_FRAMES = 45;
    static final int FEATHER_PIXELS = 12;
    static final int FEATHER_COLUMN_FRAMES = 4;
    record State(int cameraX, int width, int tick, ArenaMaskState sample) { }
    private static final State EMPTY = new State(0, 0, 0, null);
    private State state;

    void advance(ArenaMaskState geometry, int cameraX, int width) {
        if (state != null && state.tick() == geometry.noiseFrame() && state.width() == width) return;
        float[] opacity = new float[width];
        for (int x = 0; x < width; x++) {
            float target = width <= 320 ? 0 : geometry.targetOpacity(x);
            boolean hasHistory = state != null && state.width() == width
                    && Math.abs((long) cameraX - state.cameraX()) < width;
            int oldX = hasHistory ? Math.clamp(cameraX + x - state.cameraX(), 0, width - 1) : x;
            // No overlap means a new view (for example a positioned capture or warp),
            // not old screen-space opacity dragged thousands of world pixels away.
            // With overlap, extend the adjacent fade into newly exposed world columns.
            // Initializing those columns opaque was the original moving-wipe bug.
            float previous = hasHistory ? state.sample().opacityAt(oldX) : target;
            // Original hardware has no mask. In the 12px widescreen border,
            // activate from the outside inward: a transparent column waits for
            // its outer neighbour to finish, then fades rather than snapping on.
            // A changed target reverses immediately from actual opacity; no
            // queued phase must finish before release can start.
            int distance = x < geometry.left() ? geometry.left() - 1 - x : x - geometry.right();
            boolean feather = target > 0 && distance < FEATHER_PIXELS;
            float step = 1f / FADE_FRAMES;
            if (feather) {
                int outerX = oldX + (x < geometry.left() ? -1 : 1);
                float outer = !hasHistory || outerX < 0 || outerX >= width
                        ? 1 : state.sample().opacityAt(outerX);
                step = previous > 0 || outer >= 1 ? 1f / FEATHER_COLUMN_FRAMES : 0;
            }
            opacity[x] = target > previous ? Math.min(target, previous + step)
                    : Math.max(target, previous - step);
            if (Math.abs(opacity[x] - target) < 0.000001f) opacity[x] = target;
        }
        state = new State(cameraX, width, geometry.noiseFrame(),
                new ArenaMaskState(geometry.left(), geometry.right(), geometry.noiseFrame(), opacity));
    }
    ArenaMaskState sample() { return state == null ? null : state.sample(); }
    void reset() { state = null; }
    @Override public String key() { return "level-bounds-mask"; }
    @Override public State capture() { return state == null ? EMPTY : state; }
    @Override public void restore(State snapshot) { state = snapshot.sample() == null ? null : snapshot; }
    @Override public void resetForMissingSnapshot() { reset(); }
}
