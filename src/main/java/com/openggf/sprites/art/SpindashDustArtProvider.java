package com.openggf.sprites.art;

import java.io.IOException;

/**
 * Optional interface for games that can provide spindash dust art from ROM.
 */
@com.openggf.game.ModApi
public interface SpindashDustArtProvider {
    SpriteArtSet loadSpindashDustArt(String characterCode) throws IOException;
}
