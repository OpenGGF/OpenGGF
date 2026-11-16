package com.openggf.game.internal;

import com.openggf.game.GameDataSource;
import com.openggf.mods.scene.SceneRomArt;

/** Detached art from a file-addressed stock medium; the session retains source ownership. */
public interface MediaSceneArt {
    SceneRomArt defaultArt(GameDataSource source);
    SceneRomArt program(GameDataSource source, String path);
}
