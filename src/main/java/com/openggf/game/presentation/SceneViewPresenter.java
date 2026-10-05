package com.openggf.game.presentation;

import com.openggf.game.ModApi;

/** Read-only consumer bound to locally ROM-loaded art residency and an agreed viewport. */
@ModApi
public interface SceneViewPresenter extends AutoCloseable {
    boolean accept(ScenePresentationFrame frame);
    long revision();
    SceneImage image(int offsetX, int offsetY);
    void draw(int offsetX, int offsetY);
    SceneImage image(int offsetX, int offsetY, ScenePlayerPose player);
    void draw(int offsetX, int offsetY, ScenePlayerPose player);
    @Override void close();
}
