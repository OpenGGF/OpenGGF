package com.openggf.level;

import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.animation.AnimatedPatternManager;

import java.util.Objects;

/** Internal pattern-owner replacement without retiring a shared native palette owner. */
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

    static void clearNativePatternOwner(LevelManager level) {
        if (level.nativePatternBinding != null) {
            level.nativePatternBinding.registry().deregister(level.nativePatternBinding.key());
            level.nativePatternBinding = null;
        }
    }

    record Binding(RewindRegistry registry, String key) { }
}
