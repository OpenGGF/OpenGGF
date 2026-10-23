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

import static org.junit.jupiter.api.Assertions.*;

class TestNativePatternOwnerReplacement {
    @AfterEach void closeWorld() { SessionManager.clear(); }

    @Test void sharedPaletteOwnerAndSeparatePatternOwnerBothRestoreAndReplay() {
        var context = context();
        var level = context.getLevelManager();
        var original = new CombinedOwner();
        level.animatedPatternManager = original;
        level.animatedPaletteManager = original;
        context.registerPatternAnimatorAdapter(original);
        var nativePatterns = new Patterns();
        LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context, nativePatterns);
        assertSame(original, level.getAnimatedPaletteManager());
        assertSame(nativePatterns, level.getAnimatedPatternManager());
        original.update();
        nativePatterns.update();
        var snapshot = context.getRewindRegistry().capture();
        assertEquals(1, snapshot.get("pattern-animator"));
        assertEquals(1, snapshot.get("sonic3k.competition.animated-tiles"));
        original.update();
        nativePatterns.update();
        context.getRewindRegistry().restore(snapshot);
        assertEquals(1, original.ticks);
        assertEquals(1, nativePatterns.ticks);
        original.update();
        nativePatterns.update();
        assertEquals(2, original.ticks);
        assertEquals(2, nativePatterns.ticks);
    }

    @Test void replacementAndOrdinaryReinitializationRemoveOnlyTheDomainAdapter() {
        var context = context();
        var level = context.getLevelManager();
        var original = new CombinedOwner();
        level.animatedPatternManager = original;
        level.animatedPaletteManager = original;
        context.registerPatternAnimatorAdapter(original);
        var retired = new Patterns();
        retired.ticks = 19;
        LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context, retired);
        var replacement = new Patterns();
        replacement.ticks = 3;
        LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context, replacement);
        var snapshot = context.getRewindRegistry().capture();
        assertEquals(3, snapshot.get(replacement.key()));
        assertTrue(snapshot.containsKey(original.key()));
        level.initAnimatedContent();
        assertFalse(context.getRewindRegistry().capture().containsKey(replacement.key()));
    }

    @Test void invalidReplacementCannotRemoveThePaletteAdapter() {
        var context = context();
        var level = context.getLevelManager();
        var original = new CombinedOwner();
        level.animatedPatternManager = original;
        level.animatedPaletteManager = original;
        context.registerPatternAnimatorAdapter(original);
        assertThrows(IllegalArgumentException.class,
                () -> LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context, () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context, original));
        assertSame(original, level.getAnimatedPatternManager());
        assertTrue(context.getRewindRegistry().capture().containsKey(original.key()));
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
        @Override public String key() { return "sonic3k.competition.animated-tiles"; }
    }
}
