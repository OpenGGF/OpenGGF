package com.openggf.game.modzone;

import com.openggf.game.ModApi;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.game.render.AdvancedRenderMode;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable typed runtime contributions created afresh for one hosted act load. */
@ModApi
public record ModZoneRuntimeServices(ZoneFeatureProvider features, com.openggf.game.WaterDataProvider water, ZoneScrollHandler scroll,
        ZoneRuntimeState state, List<AnimatedTileChannel> animatedTiles,
        AnimatedPaletteManager paletteAnimation, List<SpecialRenderEffect> renderEffects,
        List<AdvancedRenderMode> renderModes, Map<String, RewindSnapshottable<?>> rewindAdapters) {
    public ModZoneRuntimeServices(ZoneFeatureProvider features, ZoneScrollHandler scroll,
            ZoneRuntimeState state, List<AnimatedTileChannel> animatedTiles,
            AnimatedPaletteManager paletteAnimation, List<SpecialRenderEffect> renderEffects,
            List<AdvancedRenderMode> renderModes, Map<String, RewindSnapshottable<?>> rewindAdapters) {
        this(features, null, scroll, state, animatedTiles, paletteAnimation, renderEffects, renderModes, rewindAdapters);
    }
    public ModZoneRuntimeServices {
        animatedTiles = List.copyOf(animatedTiles);
        renderEffects = List.copyOf(renderEffects);
        renderModes = List.copyOf(renderModes);
        rewindAdapters = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(rewindAdapters));
        rewindAdapters.forEach((key, value) -> {
            com.openggf.game.ModKeySyntax.requireLocalName(key);
            Objects.requireNonNull(value, "rewind adapter");
        });
        long entries = animatedTiles.size() + (long)renderEffects.size() + renderModes.size() + rewindAdapters.size()
                + (features == null ? 0 : 1) + (water == null ? 0 : 1) + (scroll == null ? 0 : 1)
                + (state == null ? 0 : 1) + (paletteAnimation == null ? 0 : 1);
        if (entries > com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES)
            throw new IllegalArgumentException("Too many zone runtime contributions");
    }
    public static Builder builder() { return new Builder(); }
    public static ModZoneRuntimeServices empty() { return builder().build(); }

    @ModApi
    public static final class Builder {
        private ZoneFeatureProvider features;
        private com.openggf.game.WaterDataProvider water;
        private ZoneScrollHandler scroll;
        private ZoneRuntimeState state;
        private AnimatedPaletteManager paletteAnimation;
        private final List<AnimatedTileChannel> animations = new ArrayList<>();
        private final List<SpecialRenderEffect> effects = new ArrayList<>();
        private final List<AdvancedRenderMode> modes = new ArrayList<>();
        private final Map<String, RewindSnapshottable<?>> adapters = new LinkedHashMap<>();
        public Builder features(ZoneFeatureProvider value) { features = Objects.requireNonNull(value); return this; }
        public Builder water(com.openggf.game.WaterDataProvider value) { water = Objects.requireNonNull(value); return this; }
        public Builder scroll(ZoneScrollHandler value) { scroll = Objects.requireNonNull(value); return this; }
        public Builder state(ZoneRuntimeState value) { state = Objects.requireNonNull(value); return this; }
        public Builder animatedTile(AnimatedTileChannel value) { animations.add(Objects.requireNonNull(value)); return this; }
        public Builder paletteAnimation(AnimatedPaletteManager value) { paletteAnimation = Objects.requireNonNull(value); return this; }
        public Builder renderEffect(SpecialRenderEffect value) { effects.add(Objects.requireNonNull(value)); return this; }
        public Builder renderMode(AdvancedRenderMode value) { modes.add(Objects.requireNonNull(value)); return this; }
        public Builder rewindAdapter(String localKey, RewindSnapshottable<?> value) {
            String key = com.openggf.game.ModKeySyntax.requireLocalName(localKey);
            if (adapters.putIfAbsent(key, Objects.requireNonNull(value)) != null)
                throw new IllegalArgumentException("Duplicate runtime-local rewind identity: " + key);
            return this;
        }
        public ModZoneRuntimeServices build() {
            return new ModZoneRuntimeServices(features, water, scroll, state, animations, paletteAnimation, effects, modes, adapters);
        }
    }
}
