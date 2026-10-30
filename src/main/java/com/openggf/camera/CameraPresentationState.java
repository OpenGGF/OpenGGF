package com.openggf.camera;

import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Internal drawing-scope restore, preserving an explicit owner including a playerless editor camera. */
public final class CameraPresentationState {
    private CameraPresentationState() { }

    public static void restore(Camera camera, CameraSnapshot snapshot, AbstractPlayableSprite owner) {
        camera.restorePresentation(snapshot, owner);
    }
}
