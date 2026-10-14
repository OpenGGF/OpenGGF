package com.openggf.game.sonic3k;

import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.util.DplcStaticFlattener;

/** Native Obj_FireShield art published as a static sheet for independent attached effects. */
final class FireShieldDashArt {
    static final String KEY = "fire_shield_dash";
    private FireShieldDashArt() { }

    static ObjectSpriteSheet sheet(SpriteArtSet nativeArt) {
        // Ani_FireShield script 1 at $19A1A uses mappings 9..14. Every one has
        // its own nonempty DPLC; flattening addresses immutable ROM source tiles,
        // never the player-owned ArtTile_Shield staging bank. Native palette is 0.
        return new ObjectSpriteSheet(nativeArt.artTiles(),
                DplcStaticFlattener.applyDplcRemap(nativeArt.mappingFrames(), nativeArt.dplcFrames()),
                nativeArt.paletteIndex(), nativeArt.frameDelay());
    }
}
