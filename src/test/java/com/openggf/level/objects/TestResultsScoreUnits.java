package com.openggf.level.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance;
import com.openggf.game.sonic2.objects.ResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Native bonus words count tens of displayed points; the engine score counts points. */
class TestResultsScoreUnits {
    @Test
    void s1TransfersTheEntireNativeTimeAndRingBonusToTheDisplayedScore() throws Exception {
        checkTally(new Sonic1ResultsScreenObjectInstance(90, 7, 0), 0);
    }

    @Test
    void s2TransfersTheEntireNativeTimeAndRingBonusToTheDisplayedScore() throws Exception {
        checkTally(new ResultsScreenObjectInstance(90, 7, 0, false), 0);
    }

    @Test
    void s3kTransfersTheEntireNativeTimeAndRingBonusToTheDisplayedScore() throws Exception {
        Constructor<S3kResultsScreenObjectInstance> constructor =
                S3kResultsScreenObjectInstance.class.getDeclaredConstructor(boolean.class);
        constructor.setAccessible(true);
        checkTally(constructor.newInstance(true), 0);
    }

    @Test
    void s3kBonusPresentationIncludesTheImplicitTrailingZero() throws Exception {
        Constructor<S3kResultsScreenObjectInstance> constructor =
                S3kResultsScreenObjectInstance.class.getDeclaredConstructor(boolean.class);
        constructor.setAccessible(true);
        S3kResultsScreenObjectInstance results = constructor.newInstance(true);
        ObjectSpriteSheet sheet = mock(ObjectSpriteSheet.class);
        when(sheet.getFrameCount()).thenReturn(32);
        field(results, "spriteSheet").set(results, sheet);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        field(results, "renderer").set(results, renderer);
        Method digits = S3kResultsScreenObjectInstance.class.getDeclaredMethod(
                "renderBonusDigits", int.class, int.class, int.class);
        digits.setAccessible(true);
        digits.invoke(results, 100, 100, 5000);
        verify(renderer).drawFrameIndex(eq(6), anyInt(), anyInt(), eq(false), eq(false));
        verify(renderer, times(4)).drawFrameIndex(eq(1), anyInt(), anyInt(), eq(false), eq(false));
    }

    private static void checkTally(AbstractResultsScreen results, int initialScore) throws Exception {
        GameStateManager score = new GameStateManager();
        score.addScore(initialScore);
        results.setServices(new TestObjectServices().withGameState(score));
        // A 1000-unit native time word is 10000 displayed points; seven rings
        // contribute another 700. Preserve the ROM decrement of ten per pass.
        set(results, "timeBonus", 1000);
        set(results, "ringBonus", 70);
        Method tally = AbstractResultsScreen.class.getDeclaredMethod("updateTally");
        tally.setAccessible(true);
        for (int pass = 0; pass < 100; pass++) {
            tally.invoke(results);
        }
        assertEquals(initialScore + 10700, score.getScore(),
                "AddPoints/HUD_AddToScore words have an implicit trailing zero in the ROM HUD");
        assertEquals(0, get(results, "timeBonus"));
        assertEquals(0, get(results, "ringBonus"));
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void set(Object target, String name, int value) throws Exception {
        field(target, name).setInt(target, value);
    }

    private static int get(Object target, String name) throws Exception {
        return field(target, name).getInt(target);
    }
}
