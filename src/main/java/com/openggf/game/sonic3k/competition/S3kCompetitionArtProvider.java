package com.openggf.game.sonic3k.competition;

import com.openggf.game.ObjectArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.art.ObjectArtBundle;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Internal competition consumer of the existing object-art contract.
 * Loading and sheet queries are CPU-only. The native host explicitly supplies
 * graphics at its object-art upload boundary; campaign PLCs, title owners and
 * full-size player art have no path through this provider.
 */
public final class S3kCompetitionArtProvider implements ObjectArtProvider {
    private final S3kCompetitionAssets assets;
    private final Map<String,ObjectSpriteSheet> sheets;
    private final Map<String,SpriteAnimationSet> animations;
    private final Map<String,PatternSpriteRenderer> renderers=new LinkedHashMap<>();
    private final List<String> keys;
    private final List<Pattern[]> banks;
    private final int regularPatternCount;
    private final ObjectArtBundle bundle;
    private boolean loaded;
    private GraphicsManager cachedGraphics;
    private int cachedBase=-1;

    public S3kCompetitionArtProvider(S3kCompetitionAssets assets) {
        this.assets=Objects.requireNonNull(assets,"assets");
        sheets=new LinkedHashMap<>(new TreeMap<>(assets.sheets()));
        keys=List.copyOf(sheets.keySet());
        Map<String,SpriteAnimationSet> animationSets=new LinkedHashMap<>();
        IdentityHashMap<Pattern[],Boolean> seen=new IdentityHashMap<>();
        List<Pattern[]> uniqueBanks=new ArrayList<>();
        int count=0;
        for (String key:keys) {
            SpriteAnimationSet animation=assets.animations(key);
            if (animation!=null) animationSets.put(key,animation);
            Pattern[] bank=sheets.get(key).getPatterns();
            if (seen.put(bank,Boolean.TRUE)==null) { uniqueBanks.add(bank); count=Math.addExact(count,bank.length); }
        }
        banks=List.copyOf(uniqueBanks); regularPatternCount=count;
        animations=Map.copyOf(animationSets);
        bundle=ObjectArtBundle.builder().sheets(sheets).animations(animations).build();
    }

    public S3kCompetitionAssets assets() { return assets; }

    @Override public void loadArtForZone(int zoneIndex) {
        S3kCompetitionSession.Arena.forZone(zoneIndex);
        loaded=true;
        // Level loading may have cleared texture residency even when the CPU
        // sheets are identical. One new upload generation follows this load.
        renderers.clear(); cachedGraphics=null; cachedBase=-1;
    }
    @Override public ObjectSpriteSheet getSheet(String key) { return sheets.get(key); }
    @Override public SpriteAnimationSet getAnimations(String key) { return animations.get(key); }
    @Override public PatternSpriteRenderer getRenderer(String key) { return renderers.get(key); }
    @Override public List<String> getRendererKeys() { return keys; }
    @Override public ObjectArtBundle getArtBundle() { return bundle; }
    @Override public int getRegularPatternCount() { return regularPatternCount; }
    @Override public int getZoneData(String key,int zoneIndex) { return -1; }

    @Override public int ensurePatternsCached(GraphicsManager graphics,int baseIndex) {
        Objects.requireNonNull(graphics,"graphics");
        if (!loaded) throw new IllegalStateException("Load a native competition arena before uploading art");
        long end=(long)baseIndex+regularPatternCount;
        if (baseIndex<PatternAtlasRange.OBJECTS.base() || end>PatternAtlasRange.OBJECTS.endExclusive())
            throw new IllegalArgumentException("Competition art exceeds the OBJECTS pattern range");
        if (cachedGraphics==graphics && cachedBase==baseIndex) return (int)end;

        renderers.clear(); cachedGraphics=null; cachedBase=-1;
        IdentityHashMap<Pattern[],Integer> bases=new IdentityHashMap<>();
        int next=baseIndex;
        for (Pattern[] bank:banks) {
            bases.put(bank,next);
            for (int i=0;i<bank.length;i++) graphics.cachePatternTexture(bank[i],next+i);
            next+=bank.length;
        }
        for (String key:keys) {
            ObjectSpriteSheet sheet=sheets.get(key);
            PatternSpriteRenderer renderer=new PatternSpriteRenderer(sheet,graphics);
            // Null here means bind this renderer's virtual base without another
            // upload. Its explicitly injected draw graphics remains `graphics`.
            // Every shared Pattern[] has already been uploaded exactly once.
            renderer.ensurePatternsCached(null,bases.get(sheet.getPatterns()));
            renderers.put(key,renderer);
        }
        cachedGraphics=graphics; cachedBase=baseIndex;
        return next;
    }

    @Override public boolean isReady() { return cachedGraphics!=null; }

    // Native competition HUD objects own their ROM mappings and both players'
    // timer/lap/item state. Ordinary campaign HUD art is deliberately absent.
    @Override public Pattern[] getHudDigitPatterns() { return null; }
    @Override public Pattern[] getHudTextPatterns() { return null; }
    @Override public Pattern[] getHudLivesPatterns() { return null; }
    @Override public Pattern[] getHudLivesNumbers() { return null; }
}
