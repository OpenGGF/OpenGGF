package com.openggf.level;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestNativePaletteOwnerReplacement {
    @AfterEach void closeWorld() { SessionManager.clear(); }

    @Test void replacingPaletteRetainsActiveCombinedPatternStateAndRegistrationOrder() {
        var context = context();
        var level = context.getLevelManager();
        var registry = context.getRewindRegistry();
        registry.register(new Marker("before-patterns"));
        var original = installCombined(context);
        registry.register(new Marker("after-patterns"));
        var palettes = new Palettes("native.arena.palette");
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, palettes);

        assertSame(original, level.getAnimatedPatternManager());
        assertSame(palettes, level.getAnimatedPaletteManager());
        var order = registry.capture().entries().keySet().stream()
                .filter(key -> List.of("before-patterns", original.key(), "after-patterns").contains(key)).toList();
        assertEquals(List.of("before-patterns", original.key(), "after-patterns"), order);

        original.update(); palettes.update();
        var snapshot = registry.capture();
        original.update(); palettes.update();
        registry.restore(snapshot);
        assertEquals(1, original.ticks);
        assertEquals(new PaletteState(1, 0x202), palettes.capture());
        for (int i = 0; i < 17; i++) { original.update(); palettes.update(); }
        assertEquals(18, original.ticks);
        assertEquals(new PaletteState(18, 0x404), palettes.capture());
    }

    @Test void patternFirstThenPaletteRetiresOnlyTheOriginalPaletteOnlyAdapter() {
        var context = context();
        var original = installCombined(context);
        var patterns = new Patterns();
        LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context, patterns);
        assertTrue(context.getRewindRegistry().capture().containsKey(original.key()));
        var palettes = new Palettes("native.arena.palette");
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, palettes);

        var snapshot = context.getRewindRegistry().capture();
        assertFalse(snapshot.containsKey(original.key()));
        assertTrue(snapshot.containsKey(patterns.key()));
        assertTrue(snapshot.containsKey(palettes.key()));
        assertSame(patterns, context.getLevelManager().getAnimatedPatternManager());
        replayPair(context, patterns, palettes);
    }

    @Test void paletteFirstThenPatternRetiresTheCombinedOwnerAfterItsFinalRoleEnds() {
        var context = context();
        var original = installCombined(context);
        var palettes = new Palettes("native.arena.palette");
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, palettes);
        assertTrue(context.getRewindRegistry().capture().containsKey(original.key()));
        var patterns = new Patterns();
        LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context, patterns);

        var snapshot = context.getRewindRegistry().capture();
        assertFalse(snapshot.containsKey(original.key()));
        assertTrue(snapshot.containsKey(patterns.key()));
        assertTrue(snapshot.containsKey(palettes.key()));
        assertSame(palettes, context.getLevelManager().getAnimatedPaletteManager());
        replayPair(context, patterns, palettes);
    }

    @Test void sameNativeKeyReplacementRestoresTheCurrentOwnerAndReplaysForward() {
        var context = context();
        installCombined(context);
        var retired = new Palettes("native.arena.palette");
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, retired);
        retired.update();
        var checkpoint = context.getRewindRegistry().capture();
        var replacement = new Palettes(retired.key());
        replacement.ticks = 90;
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, replacement);
        context.getRewindRegistry().restore(checkpoint);

        assertSame(replacement, context.getLevelManager().getAnimatedPaletteManager());
        assertEquals(new PaletteState(1, 0x202), replacement.capture());
        for (int i = 0; i < 17; i++) replacement.update();
        assertEquals(new PaletteState(18, 0x404), replacement.capture());
        assertEquals(new PaletteState(1, 0x202), retired.capture());
    }

    @Test void distinctNativeKeyReplacementRemovesTheRetiredOwner() {
        var context = context();
        installCombined(context);
        var retired = new Palettes("native.arena.palette.first");
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, retired);
        var replacement = new Palettes("native.arena.palette.second");
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, replacement);
        var snapshot = context.getRewindRegistry().capture();
        assertFalse(snapshot.containsKey(retired.key()));
        assertTrue(snapshot.containsKey(replacement.key()));
        assertSame(replacement, context.getLevelManager().getAnimatedPaletteManager());
    }

    @Test void normalAnimationInitializationRemovesTheNativePaletteBinding() {
        var context = context();
        var level = context.getLevelManager();
        installCombined(context);
        var palettes = new Palettes("native.arena.palette");
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, palettes);
        context.getRewindRegistry().register(new Marker("unrelated"));

        level.initAnimatedContent();
        var snapshot = context.getRewindRegistry().capture();
        assertFalse(snapshot.containsKey(palettes.key()));
        assertTrue(snapshot.containsKey("unrelated"));
        assertNull(level.getAnimatedPaletteManager());
        assertNull(level.nativePaletteBinding);
    }

    @Test void gameplayTeardownRemovesBothNativeAdaptersWithoutRemovingUnrelatedState() {
        var context = context();
        var level = context.getLevelManager();
        installCombined(context);
        var patterns = new Patterns();
        var palettes = new Palettes("native.arena.palette");
        LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context, patterns);
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, palettes);
        context.getRewindRegistry().register(new Marker("unrelated"));

        level.resetGameplayState();
        var snapshot = context.getRewindRegistry().capture();
        assertFalse(snapshot.containsKey(patterns.key()));
        assertFalse(snapshot.containsKey(palettes.key()));
        assertTrue(snapshot.containsKey("unrelated"));
        assertNull(level.getAnimatedPatternManager());
        assertNull(level.getAnimatedPaletteManager());
        assertNull(level.nativePaletteBinding);
    }

    @Test void normalPaletteCleanupPreservesAnAdapterNowUsedByTheActivePatterns() {
        var context = context();
        var level = context.getLevelManager();
        installCombined(context);
        var combined = new CombinedOwner() {
            @Override public String key() { return "native.arena.combined"; }
        };
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, combined);
        // This is normal initialization's intermediate state: patterns have
        // already been installed and registered before palette initialization.
        level.animatedPatternManager = combined;
        context.registerPatternAnimatorAdapter(combined);
        LevelAnimationInternalAccess.clearNativePaletteOwner(level);
        assertTrue(context.getRewindRegistry().capture().containsKey(combined.key()));
        assertNull(level.nativePaletteBinding);
        combined.update();
        var snapshot = context.getRewindRegistry().capture();
        combined.update();
        context.getRewindRegistry().restore(snapshot);
        assertEquals(1, combined.ticks);
    }

    @Test void invalidManagersAndKeysLeaveBothOwnersAndAdaptersIntact() {
        var context = context();
        var original = installCombined(context);
        var before = context.getRewindRegistry().capture();
        assertThrows(IllegalArgumentException.class,
                () -> LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, () -> { }));
        for (String key : new String[]{null, "", " \t", original.key()}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, new Palettes(key)));
        }
        assertThrows(NullPointerException.class,
                () -> LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, null));
        assertSame(original, context.getLevelManager().getAnimatedPatternManager());
        assertSame(original, context.getLevelManager().getAnimatedPaletteManager());
        assertEquals(new ArrayList<>(before.entries().keySet()),
                new ArrayList<>(context.getRewindRegistry().capture().entries().keySet()));
    }

    @Test void ordinaryPaletteKeyCannotBeReusedEvenAfterItsPatternRoleEnded() {
        var context = context();
        var original = installCombined(context);
        var patterns = new Patterns();
        LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context, patterns);
        assertThrows(IllegalArgumentException.class,
                () -> LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, new Palettes(original.key())));
        assertSame(original, context.getLevelManager().getAnimatedPaletteManager());
        assertTrue(context.getRewindRegistry().capture().containsKey(original.key()));
        assertTrue(context.getRewindRegistry().capture().containsKey(patterns.key()));
    }

    @Test void unrelatedKeyCollisionCannotRetireTheCurrentlyBoundPaletteOwner() {
        var context = context();
        installCombined(context);
        var palettes = new Palettes("native.arena.palette");
        LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, palettes);
        context.getRewindRegistry().register(new Marker("unrelated"));
        var before = context.getRewindRegistry().capture();
        assertThrows(IllegalStateException.class,
                () -> LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, new Palettes("unrelated")));
        assertSame(palettes, context.getLevelManager().getAnimatedPaletteManager());
        assertEquals(new ArrayList<>(before.entries().keySet()),
                new ArrayList<>(context.getRewindRegistry().capture().entries().keySet()));
        levelBindingStillClears(context, palettes.key());
    }

    private static void levelBindingStillClears(GameplayModeContext context, String key) {
        context.getLevelManager().initAnimatedContent();
        assertFalse(context.getRewindRegistry().capture().containsKey(key));
    }

    private static void replayPair(GameplayModeContext context, Patterns patterns, Palettes palettes) {
        patterns.update(); palettes.update();
        var snapshot = context.getRewindRegistry().capture();
        var expected = new ArrayList<PaletteState>();
        for (int i = 0; i < 17; i++) { patterns.update(); palettes.update(); expected.add(palettes.capture()); }
        int expectedPatternTicks = patterns.ticks;
        context.getRewindRegistry().restore(snapshot);
        for (var state : expected) { patterns.update(); palettes.update(); assertEquals(state, palettes.capture()); }
        assertEquals(expectedPatternTicks, patterns.ticks);
    }

    private static CombinedOwner installCombined(GameplayModeContext context) {
        var original = new CombinedOwner();
        context.getLevelManager().animatedPatternManager = original;
        context.getLevelManager().animatedPaletteManager = original;
        context.registerPatternAnimatorAdapter(original);
        return original;
    }

    private static GameplayModeContext context() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var context = SessionManager.openGameplaySession(new com.openggf.game.sonic3k.Sonic3kGameModule());
        GameplaySessionFactory.attachManagers(context, EngineServices.current());
        return context;
    }

    private static class CombinedOwner implements AnimatedPatternManager, AnimatedPaletteManager,
            RewindSnapshottable<Integer> {
        int ticks;
        @Override public void update() { ticks++; }
        @Override public String key() { return "pattern-animator"; }
        @Override public Integer capture() { return ticks; }
        @Override public void restore(Integer state) { ticks = state; }
    }

    private static final class Patterns extends CombinedOwner {
        @Override public String key() { return "native.arena.patterns"; }
    }

    private record PaletteState(int ticks, int color) { }

    private static final class Palettes implements AnimatedPaletteManager, RewindSnapshottable<PaletteState> {
        private final String key;
        int ticks, color;
        private Palettes(String key) { this.key = key; }
        @Override public void update() { ticks++; color = (color + 0x202) & 0xEEE; }
        @Override public String key() { return key; }
        @Override public PaletteState capture() { return new PaletteState(ticks, color); }
        @Override public void restore(PaletteState state) { ticks = state.ticks(); color = state.color(); }
    }

    private record Marker(String key) implements RewindSnapshottable<Integer> {
        @Override public Integer capture() { return 0; }
        @Override public void restore(Integer state) { }
    }
}
