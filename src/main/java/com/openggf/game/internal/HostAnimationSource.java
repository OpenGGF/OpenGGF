package com.openggf.game.internal;

import com.openggf.game.AnimationFamilies;
import com.openggf.sprites.art.SpriteArtSet;
import java.io.IOException;
import java.util.Map;

/** Native host scripts from their owning media; never a donor art source. */
public interface HostAnimationSource {
    Map<Integer, AnimationFamilies.Binding> families();
    SpriteArtSet load(String character) throws IOException;
}
