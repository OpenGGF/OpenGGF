package com.openggf.level;

import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;

import java.util.Objects;

/** Internal animation-owner replacement with independent rewind ownership for each half. */
public final class LevelAnimationInternalAccess {
    private LevelAnimationInternalAccess() { }

    public static void installNativeAnimatedPatternManager(
            GameplayModeContext context, AnimatedPatternManager manager) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(manager, "manager");
        if (!(manager instanceof RewindSnapshottable<?> patterns)) {
            throw new IllegalArgumentException("Native animated patterns require rewind state");
        }
        var level = Objects.requireNonNull(context.getLevelManager(), "levelManager");
        var registry = Objects.requireNonNull(context.getRewindRegistry(), "rewindRegistry");
        var palette = level.animatedPaletteManager instanceof RewindSnapshottable<?> owner ? owner : null;
        if (palette != null && patterns.key().equals(palette.key())) {
            throw new IllegalArgumentException("Pattern replacement must have its own rewind key");
        }
        clearNativePatternOwner(level);
        // S3K's ordinary manager owns both AniPLC and AnPal. Its adapter must
        // survive a competition-only AniPLC replacement: palette timers/fades
        // still execute on that original object.
        if (level.animatedPatternManager instanceof RewindSnapshottable<?> old && old != palette) {
            registry.deregister(old.key());
        }
        if (palette != null) {
            registry.deregister(palette.key());
            registry.register(palette);
        }
        registry.deregister(patterns.key());
        registry.register(patterns);
        level.animatedPatternManager = manager;
        level.nativePatternBinding = new Binding(registry, patterns.key());
    }

    /**
     * Binds the palette owner while retaining an old combined owner's active pattern state.
     * A retained combined owner's update still executes its own palette work.
     */
    public static void installNativeAnimatedPaletteManager(
            GameplayModeContext context, AnimatedPaletteManager manager) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(manager, "manager");
        if (!(manager instanceof RewindSnapshottable<?> palettes)) {
            throw new IllegalArgumentException("Native animated palettes require rewind state");
        }
        String key = palettes.key();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Native animated palettes require a nonblank rewind key");
        }
        var level = Objects.requireNonNull(context.getLevelManager(), "levelManager");
        var registry = Objects.requireNonNull(context.getRewindRegistry(), "rewindRegistry");
        var patterns = level.animatedPatternManager instanceof RewindSnapshottable<?> owner ? owner : null;
        var previous = level.animatedPaletteManager instanceof RewindSnapshottable<?> owner ? owner : null;
        var binding = level.nativePaletteBinding;
        boolean sameNativeKey = binding != null && binding.registry() == registry && key.equals(binding.key());
        if ((patterns != null && key.equals(patterns.key()))
                || (previous != null && key.equals(previous.key()) && !sameNativeKey)) {
            throw new IllegalArgumentException("Palette replacement must have its own rewind key");
        }
        // Admit a new key before removing anything: a collision with an unrelated
        // subsystem must leave both active animation owners and their adapters intact.
        if (!sameNativeKey) registry.register(palettes);
        clearNativePaletteOwner(level);
        if (previous != null && previous != patterns) registry.deregister(previous.key());
        if (sameNativeKey) registry.register(palettes);
        level.animatedPaletteManager = manager;
        level.nativePaletteBinding = new Binding(registry, key);
    }

    static void clearNativePatternOwner(LevelManager level) {
        if (level.nativePatternBinding != null) {
            level.nativePatternBinding.registry().deregister(level.nativePatternBinding.key());
            level.nativePatternBinding = null;
        }
    }

    static void clearNativePaletteOwner(LevelManager level) {
        var binding = level.nativePaletteBinding;
        if (binding != null) {
            // Normal initialization installs patterns before palettes. If its new
            // combined owner retained this key, that active pattern adapter stays.
            var patterns = level.animatedPatternManager instanceof RewindSnapshottable<?> owner ? owner : null;
            if (patterns == null || !binding.key().equals(patterns.key()))
                binding.registry().deregister(binding.key());
            level.nativePaletteBinding = null;
        }
    }

    static void clearAnimatedPaletteOwner(LevelManager level) {
        clearNativePaletteOwner(level);
        level.animatedPaletteManager = null;
    }

    static void clearAnimatedOwners(LevelManager level) {
        clearNativePatternOwner(level);
        level.animatedPatternManager = null;
        clearAnimatedPaletteOwner(level);
    }

    record Binding(RewindRegistry registry, String key) { }
}
