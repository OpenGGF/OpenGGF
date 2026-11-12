package com.openggf.game;

import com.openggf.sprites.art.SpriteArtSet;
import java.util.Set;

/** Optional GGFP powered geometry and explicit whole-animation aliases to the normal sheet.
 * Load the sheet through the character owner's bounded assets in SuperFormSupplier.
 * Missing complete families retain the bound normal animation. Hyper may share this value.
 */
@ModApi
public record PoweredPlayableSheet(SpriteArtSet art, Set<CanonicalAnimation> normalAliases, java.util.Map<String,String> normalPhaseAliases) {
    /** Runtime script address in a bound powered view, independent of the host's native vocabulary.
     * Present only for authored keys that are not declared normal aliases. Mapping indices
     * in this script are already relocated; callers must not relocate them again. */
    public static int animationId(CanonicalAnimation animation) {
        return 0x400 + java.util.Objects.requireNonNull(animation).ordinal();
    }

    public PoweredPlayableSheet(SpriteArtSet art, Set<CanonicalAnimation> normalAliases) {
        this(art, normalAliases, java.util.Map.of());
    }
    public PoweredPlayableSheet {
        java.util.Objects.requireNonNull(art);
        normalAliases = Set.copyOf(normalAliases);
        normalPhaseAliases = java.util.Map.copyOf(normalPhaseAliases);
    }
}
