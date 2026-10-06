package com.openggf.mods.scene.host;

/** Test access to a scene host's open scene and last recorded frame, from tests outside this package. */
public final class SceneHostTestAccess {
    private SceneHostTestAccess() {
    }

    /**
     * The creator's own scene object the host has open (unwrapped from the engine's
     * fault-boundary wrapper), or null.
     */
    public static Object scene(ModSceneHost host) {
        return host.openScene() == null ? null : com.openggf.mods.code.OwnedSceneFactory.unwrap(host.openScene());
    }

    /** How many draw operations the last {@link ModSceneHost#draw} recorded. */
    public static int lastFrameOps(ModSceneHost host) {
        return host.lastFrame().size();
    }
}
