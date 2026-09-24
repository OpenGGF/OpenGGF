package com.openggf.graphics;

/** Immutable presentation sample derived from native camera bounds, never a level-owned switch.
 * The original games expose only a 320px window. A wider viewport can show world outside
 * the union of those native windows; mask that excess without changing gameplay bounds.
 * Capture alongside scroll registers so rewind and delayed presentation use the same frame.
 */
public record ArenaMaskState(int left, int right, int noiseFrame, float[] opacity) {
    public ArenaMaskState(int left, int right, int noiseFrame) { this(left, right, noiseFrame, null); }
    public ArenaMaskState { opacity = opacity == null ? null : opacity.clone(); }
    @Override public float[] opacity() { return opacity == null ? null : opacity.clone(); }
    public float opacityAt(int x) { return opacity == null ? targetOpacity(x) : opacity[x]; }
    public float targetOpacity(int x) {
        // Exact pixel boundary. A 12px spatial feather made the apparent left edge
        // sit about 6–8px outside the derived bound. Fade over time, not across
        // playable geometry, so the settled edge and the camera interval agree.
        // The transition may use feather strength to delay opacity, never its endpoint.
        return x >= left && x < right ? 0 : 1;
    }
    @Override public boolean equals(Object other) {
        return other instanceof ArenaMaskState state && left == state.left && right == state.right
                && noiseFrame == state.noiseFrame && java.util.Arrays.equals(opacity, state.opacity);
    }
    @Override public int hashCode() {
        return java.util.Objects.hash(left, right, noiseFrame, java.util.Arrays.hashCode(opacity));
    }
    public static final int NATIVE_WIDTH = 320;

    public static ArenaMaskState fromBounds(int minX, int maxX, int cameraX,
                                           int viewportWidth, int noiseFrame, boolean wraps) {
        // Inverted ROM bounds can describe a transient/wrapped domain. Never normalize
        // them into an invented finite arena. Native output remains entirely untouched.
        if (viewportWidth <= NATIVE_WIDTH || wraps || maxX < minX) {
            return new ArenaMaskState(0, viewportWidth, noiseFrame);
        }
        return new ArenaMaskState(clamp(minX - cameraX, viewportWidth),
                clamp(maxX + NATIVE_WIDTH - cameraX, viewportWidth), noiseFrame);
    }

    public boolean visible(int viewportWidth) {
        if (viewportWidth <= NATIVE_WIDTH) return false;
        if (opacity == null) return left > 0 || right < viewportWidth;
        for (float value : opacity) if (value > 0) return true;
        return false;
    }
    private static int clamp(int value, int width) { return Math.max(0, Math.min(width, value)); }
}
