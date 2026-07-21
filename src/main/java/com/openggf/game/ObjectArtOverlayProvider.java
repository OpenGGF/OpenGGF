package com.openggf.game;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.HudStaticArt;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.art.ObjectArtBundle;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Engine-side implementation of a static sheet overlay over an object-art provider. */
public class ObjectArtOverlayProvider implements ObjectArtProvider {
    private final ObjectArtProvider base;
    private final Map<String, ObjectSpriteSheet> sheets;
    private final Map<String, PatternSpriteRenderer> renderers = new LinkedHashMap<>();

    public ObjectArtOverlayProvider(ObjectArtProvider base,
                                    Map<String, ObjectSpriteSheet> overlays) {
        this.base = Objects.requireNonNull(base, "base");
        this.sheets = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(overlays, "overlays")));
    }

    @Override public void loadArtForZone(int zoneIndex) throws IOException {
        base.loadArtForZone(zoneIndex);
    }

    @Override public synchronized PatternSpriteRenderer getRenderer(String key) {
        ObjectSpriteSheet sheet = sheets.get(key);
        if (sheet == null) return base.getRenderer(key);
        return renderers.computeIfAbsent(key, ignored -> new PatternSpriteRenderer(sheet));
    }

    @Override public ObjectSpriteSheet getSheet(String key) {
        ObjectSpriteSheet sheet = sheets.get(key);
        return sheet != null ? sheet : base.getSheet(key);
    }

    @Override public SpriteAnimationSet getAnimations(String key) { return base.getAnimations(key); }

    @Override public ObjectArtBundle getArtBundle() {
        ObjectArtBundle inherited = base.getArtBundle();
        ObjectArtBundle.Builder merged = ObjectArtBundle.builder()
                .sheets(inherited.sheets()).sheets(sheets)
                .animations(inherited.animations())
                .hudDigitPatterns(inherited.hudDigitPatterns())
                .hudTextPatterns(inherited.hudTextPatterns())
                .hudLivesPatterns(inherited.hudLivesPatterns())
                .hudLivesNumbers(inherited.hudLivesNumbers())
                .hudHexDigitPatterns(inherited.hudHexDigitPatterns());
        inherited.zoneData().forEach(merged::zoneData);
        return merged.build();
    }

    @Override public int getZoneData(String key, int zoneIndex) {
        return base.getZoneData(key, zoneIndex);
    }
    @Override public Pattern[] getHudDigitPatterns() { return base.getHudDigitPatterns(); }
    @Override public Pattern[] getHudTextPatterns() { return base.getHudTextPatterns(); }
    @Override public Pattern[] getHudLivesPatterns() { return base.getHudLivesPatterns(); }
    @Override public Pattern[] getHudLivesNumbers() { return base.getHudLivesNumbers(); }
    @Override public HudStaticArt getHudStaticArt() { return base.getHudStaticArt(); }
    @Override public Pattern[] getHudHexDigitPatterns() { return base.getHudHexDigitPatterns(); }
    @Override public Palette getHudLivesPaletteOverride() { return base.getHudLivesPaletteOverride(); }

    @Override public List<String> getRendererKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>(base.getRendererKeys());
        keys.addAll(sheets.keySet());
        return List.copyOf(keys);
    }

    @Override public int getRegularPatternCount() {
        int count = base.getRegularPatternCount();
        if (count < 0) {
            throw new IllegalStateException("Invalid base regular object pattern count: " + count);
        }
        for (ObjectSpriteSheet sheet : sheets.values()) {
            count = Math.addExact(count, sheet.getPatterns().length);
        }
        return count;
    }

    @Override public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
        int next = base.ensurePatternsCached(graphicsManager, baseIndex);
        for (String key : sheets.keySet()) {
            getRenderer(key).ensurePatternsCached(graphicsManager, next);
            next = Math.addExact(next, sheets.get(key).getPatterns().length);
        }
        return next;
    }

    @Override public boolean isReady() { return base.isReady(); }
    @Override public int getHudTextPaletteLine() { return base.getHudTextPaletteLine(); }
    @Override public int getHudFlashPaletteLine() { return base.getHudFlashPaletteLine(); }
    @Override public void registerLevelTileArt(Level level, int zoneIndex) {
        base.registerLevelTileArt(level, zoneIndex);
    }
    @Override public void reloadStandaloneArtForActTransition(int zoneIndex) {
        base.reloadStandaloneArtForActTransition(zoneIndex);
    }
}
