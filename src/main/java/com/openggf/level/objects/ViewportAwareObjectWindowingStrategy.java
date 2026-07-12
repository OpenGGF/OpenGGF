package com.openggf.level.objects;

/**
 * Engine extension for object windowing strategies whose boundaries follow the
 * active viewport width. Kept separate from the published {@link ObjectWindowingStrategy}
 * mod API so existing implementations retain their pinned surface and behavior.
 */
public interface ViewportAwareObjectWindowingStrategy extends ObjectWindowingStrategy {
    int loadWindowForwardEdge(int cameraX, int viewportWidth);

    int loadWindowLeftTrimEdge(int cameraX, int viewportWidth);

    boolean isOutsideUnloadWindow(int objX, int cameraX, int viewportWidth);
}
