package com.openggf.level;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.graphics.ArenaMaskState;

/** Common viewport presentation history, not an encounter switch. Each world column
 * fades when bounds begin covering it. Camera motion projects that history into the
 * viewport; newly exposed columns inherit the adjacent fade instead of arriving opaque. */
final class LevelBoundsMaskTransition implements RewindSnapshottable<LevelBoundsMaskTransition.State> {
    static final int FADE_FRAMES = 45;
    static final int FEATHER_PIXELS = 12;
    record State(int cameraX, int width, int tick, ArenaMaskState sample, float[] fadeRates) {
        State { fadeRates = fadeRates == null ? null : fadeRates.clone(); }
        @Override public float[] fadeRates() { return fadeRates == null ? null : fadeRates.clone(); }
        float rateAt(int x) { return fadeRates[x]; }
    }
    private static final State EMPTY = new State(0, 0, 0, null, null);
    private State state;

    void advance(ArenaMaskState geometry, int cameraX, int width) {
        if (state != null && state.tick() == geometry.noiseFrame() && state.width() == width) return;
        float[] opacity = new float[width];
        float[] rates = new float[width];
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
            // Original hardware has no mask. Widescreen uses the old spatial feather
            // only to lengthen the temporal fade (45–90 ticks), never to leave a gap.
            // Retain each column's rate on release after its owning bound disappears.
            rates[x] = target > 0 ? fadeRate(geometry, x)
                    : hasHistory ? state.rateAt(oldX) : 1f / FADE_FRAMES;
            opacity[x] = target > previous ? Math.min(target, previous + rates[x])
                    : Math.max(target, previous - rates[x]);
            if (Math.abs(opacity[x] - target) < 0.000001f) opacity[x] = target;
        }
        state = new State(cameraX, width, geometry.noiseFrame(),
                new ArenaMaskState(geometry.left(), geometry.right(), geometry.noiseFrame(), opacity), rates);
    }
    private static float fadeRate(ArenaMaskState geometry, int x) {
        float distance = x < geometry.left() ? geometry.left() - x - 0.5f
                : x - geometry.right() + 0.5f;
        float strength = Math.clamp(distance / FEATHER_PIXELS, 0f, 1f);
        strength = strength * strength * (3f - 2f * strength);
        return 1f / (FADE_FRAMES * (2f - strength));
    }
    ArenaMaskState sample() { return state == null ? null : state.sample(); }
    void reset() { state = null; }
    @Override public String key() { return "level-bounds-mask"; }
    @Override public State capture() { return state == null ? EMPTY : state; }
    @Override public void restore(State snapshot) { state = snapshot.sample() == null ? null : snapshot; }
    @Override public void resetForMissingSnapshot() { reset(); }
}
