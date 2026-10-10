package com.openggf.mods.scene.host;

/** Isolated diagnostic access, outside Maven source roots. Creator calls remain
 * in the production host; inspection only reads a public scene status getter.
 */
public final class NativeSceneInspection {
    public static Object scene(ModSceneHost host) {
        return com.openggf.mods.code.OwnedSceneFactory.unwrap(host.openScene());
    }
}
