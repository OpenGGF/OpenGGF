package com.openggf.game.arena;

import com.openggf.level.Palette;
import com.openggf.sprites.art.SpriteArtSet;
import java.io.IOException;

/** Track-selected ghost art and display origin; presentation only. */
public interface GhostArtSource {
    SpriteArtSet art(String character) throws IOException;
    Palette palette(String character);
    default int cameraX() { return 0; }
    default int cameraY() { return 0; }
}
