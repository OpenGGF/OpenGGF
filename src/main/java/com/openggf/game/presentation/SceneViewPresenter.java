package com.openggf.game.presentation;

import com.openggf.game.ModApi;

/** Read-only consumer bound to one locally ROM-loaded act and agreed viewport. */
@ModApi
public interface SceneViewPresenter extends AutoCloseable {
    boolean accept(ScenePresentationFrame frame);
    long revision();
    SceneImage image(int offsetX, int offsetY);
    void draw(int offsetX, int offsetY);
    @Override void close();
}
