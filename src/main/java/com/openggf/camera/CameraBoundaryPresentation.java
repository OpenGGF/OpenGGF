package com.openggf.camera;

import com.openggf.game.rewind.RewindSnapshottable;

/** Internal presentation view of a camera transition's destination. Gameplay still
 * consumes the current ROM bounds. Kept outside Camera's published snapshot contract. */
public final class CameraBoundaryPresentation {
    private CameraBoundaryPresentation() { }

    public static int minX(Camera camera) {
        return camera.boundaryDestination.state.pending()
                ? camera.boundaryDestination.state.minX() : camera.getMinXTarget();
    }
    public static int maxX(Camera camera) {
        return camera.boundaryDestination.state.pending()
                ? camera.boundaryDestination.state.maxX() : camera.getMaxXTarget();
    }
    /** A camera transition declares geometry, never a mask activation or visual style. */
    public static void approach(Camera camera, int minX, int maxX) {
        camera.boundaryDestination.state = new Destination.State(true, minX, maxX);
    }
    public static RewindSnapshottable<?> rewindAdapter(Camera camera) {
        return camera.boundaryDestination;
    }
    public static void reset(Camera camera) {
        camera.boundaryDestination.resetForMissingSnapshot();
    }

    static final class Destination implements RewindSnapshottable<Destination.State> {
        record State(boolean pending, int minX, int maxX) { }
        private State state = new State(false, 0, 0);
        @Override public String key() { return "camera-boundary-presentation"; }
        @Override public State capture() { return state; }
        @Override public void restore(State state) { this.state = state; }
        @Override public void resetForMissingSnapshot() { state = new State(false, 0, 0); }
    }
}
