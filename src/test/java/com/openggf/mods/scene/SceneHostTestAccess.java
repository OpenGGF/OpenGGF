package com.openggf.mods.scene;

/** Test access to a scene host's last recorded frame, from tests outside this package. */
public final class SceneHostTestAccess {
    private SceneHostTestAccess() {
    }

    /** How many draw operations the last {@link ModSceneHost#draw} recorded. */
    public static int lastFrameOps(ModSceneHost host) {
        return host.lastFrame().size();
    }
}
