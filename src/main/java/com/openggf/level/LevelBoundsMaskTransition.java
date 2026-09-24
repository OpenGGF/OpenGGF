package com.openggf.level;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.graphics.ArenaMaskState;

/** Common presentation history, not an encounter switch. Existing visible pixels fade
 * when bounds close over them; newly revealed offscreen pixels already carry their mask.
 * World-coordinate matching preserves opaque wings during camera motion. */
final class LevelBoundsMaskTransition implements RewindSnapshottable<LevelBoundsMaskTransition.State> {
    static final int FADE_FRAMES = 45;
    record State(int cameraX, int width, int tick, ArenaMaskState sample) { }
    private static final State EMPTY = new State(0, 0, 0, null);
    private State state;

    void advance(ArenaMaskState geometry, int cameraX, int width) {
        if (state != null && state.tick() == geometry.noiseFrame() && state.width() == width) return;
        float[] opacity = new float[width];
        for (int x = 0; x < width; x++) {
            float target = width <= 320 ? 0 : geometry.targetOpacity(x);
            int oldX = state == null ? -1 : cameraX + x - state.cameraX();
            boolean wasVisible = state != null && state.width() == width && oldX >= 0 && oldX < width;
            float previous = wasVisible ? state.sample().opacityAt(oldX) : target;
            opacity[x] = target > previous ? Math.min(target, previous + 1f / FADE_FRAMES)
                    : Math.max(target, previous - 1f / FADE_FRAMES);
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
