package com.openggf.sprites.render;

import com.openggf.sprites.art.SpriteArtSet;

/** Internal owner-injected art for an attached effect whose player uses a different ROM from the world. */
public interface PlayerAttachedEffectArt {
    SpriteArtSet instaShieldArt();
    PlayerSpriteRenderer instaShieldRenderer();
}
