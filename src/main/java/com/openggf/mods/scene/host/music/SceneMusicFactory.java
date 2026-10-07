package com.openggf.mods.scene.host.music;

import com.openggf.audio.AudioManager;
import com.openggf.data.Rom;
import java.util.function.Function;

/** Binds finite scene music to the engine ROM catalogue and final-PCM owner. */
public final class SceneMusicFactory {
    private SceneMusicFactory() { }
    public static ManagedSceneMusic create(AudioManager audio, Function<String, Rom> roms) {
        return new ManagedSceneMusic(audio, roms);
    }
}
