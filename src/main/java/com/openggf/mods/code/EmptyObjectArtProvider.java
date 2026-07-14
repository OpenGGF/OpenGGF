package com.openggf.mods.code;

import com.openggf.game.ObjectArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.List;

/**
 * Neutral null-object {@link ObjectArtProvider}. Used as the decoration base for a
 * standalone module whose delegate has no object-art provider of its own
 * ({@code getObjectArtProvider()} returns {@code null}) but whose owner still registered
 * prepared sheets via {@code ModContext.registerObjectArt}. Engine-internal wiring detail —
 * never appears on the published {@code @ModApi} surface.
 */
final class EmptyObjectArtProvider implements ObjectArtProvider {
    @Override public void loadArtForZone(int zoneIndex) { }

    @Override public PatternSpriteRenderer getRenderer(String key) { return null; }

    @Override public ObjectSpriteSheet getSheet(String key) { return null; }

    @Override public SpriteAnimationSet getAnimations(String key) { return null; }

    @Override public int getZoneData(String key, int zoneIndex) { return -1; }

    @Override public Pattern[] getHudDigitPatterns() { return null; }

    @Override public Pattern[] getHudTextPatterns() { return null; }

    @Override public Pattern[] getHudLivesPatterns() { return null; }

    @Override public Pattern[] getHudLivesNumbers() { return null; }

    @Override public List<String> getRendererKeys() { return List.of(); }

    @Override public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
        return baseIndex;
    }

    @Override public boolean isReady() { return true; }
}
