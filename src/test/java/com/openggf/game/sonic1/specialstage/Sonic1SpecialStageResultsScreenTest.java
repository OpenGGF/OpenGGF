package com.openggf.game.sonic1.specialstage;

import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.clearInvocations;

@FullReset
@ExtendWith(SingletonResetExtension.class)
public class Sonic1SpecialStageResultsScreenTest {

    private GameStateManager gameState;

    @BeforeEach
    public void setUp() {
        TestEnvironment.activeGameplayMode();
        gameState = GameServices.gameState();
        gameState.configureSpecialStageProgress(6, 6);
        gameState.resetSession();
    }

    @AfterEach
    public void tearDown() {
        gameState.resetSession();
        SessionManager.clear();
    }

    @Test
    public void testFailedScenarioUsesSpecialStageMessage() {
        Sonic1SpecialStageResultsScreen screen =
                new Sonic1SpecialStageResultsScreen(0, false, 0, 0);

        assertEquals(11, screen.getScenarioFrameForTesting(), "Failed run should show SPECIAL STAGE message frame");
    }

    @Test
    public void testGotEmeraldScenarioUsesChaosEmeraldsMessage() {
        Sonic1SpecialStageResultsScreen screen =
                new Sonic1SpecialStageResultsScreen(12, true, 1, 3);

        assertEquals(10, screen.getScenarioFrameForTesting(), "Emerald run should show CHAOS EMERALDS message frame");
    }

    @Test
    public void testGotAllEmeraldsScenarioUsesGotThemAllMessage() {
        Sonic1SpecialStageResultsScreen screen =
                new Sonic1SpecialStageResultsScreen(20, true, 5, 6);

        assertEquals(12, screen.getScenarioFrameForTesting(), "All-emerald run should show SONIC GOT THEM ALL frame");
    }

    @Test
    public void testRingBonusTalliesIntoScoreAndCompletes() {
        Sonic1SpecialStageResultsScreen screen =
                new Sonic1SpecialStageResultsScreen(5, true, 0, 1);

        int startScore = gameState.getScore();

        int frame = 0;
        while (!screen.isComplete() && frame < 1000) {
            frame++;
            screen.update(frame, null);
        }

        assertTrue(screen.isComplete(), "Results flow should complete within expected frame budget");
        assertEquals(0, screen.getRingBonus(), "Ring bonus should fully count down");
        assertEquals(startScore + 500, gameState.getScore(), "Five rings award 500 displayed points");
    }

    @Test
    void continueRowShowsTextThenAnimatedMiniSonicAtTheNativeCenteredPosition() throws Exception {
        Sonic1SpecialStageResultsScreen screen = new Sonic1SpecialStageResultsScreen(50, false, 0, 0);
        PatternSpriteRenderer continueRenderer = installRenderers(screen);
        screen.setViewportWidth(400);
        int frame = 0;
        for (; frame < 40; frame++) {
            screen.update(frame + 1, null);
        }
        int x = GameServices.camera().getX() + 160 + 40;
        int y = GameServices.camera().getY() + 184;
        screen.appendRenderCommands(new ArrayList<GLCommand>());
        verify(continueRenderer).drawFrameIndex(2, x, y, false, false);

        while (!screen.isContinueAwarded() && frame < 1000) {
            screen.update(++frame, null);
        }
        assertTrue(screen.isContinueAwarded());
        // SSR_Continue selects the foot-down frame; the continue slot toggles
        // on that same pass when the VBlank byte is a multiple of sixteen.
        int initialFrame = (frame & 15) == 0 ? 1 : 0;
        clearInvocations(continueRenderer);
        screen.appendRenderCommands(new ArrayList<GLCommand>());
        verify(continueRenderer).drawFrameIndex(initialFrame, x, y, false, false);

        do {
            screen.update(++frame, null);
        } while ((frame & 15) != 0);
        clearInvocations(continueRenderer);
        screen.appendRenderCommands(new ArrayList<GLCommand>());
        verify(continueRenderer).drawFrameIndex(initialFrame ^ 1, x, y, false, false);
    }

    @Test
    void fewerThanFiftyRingsDoNotRenderAContinueRow() throws Exception {
        Sonic1SpecialStageResultsScreen screen = new Sonic1SpecialStageResultsScreen(49, false, 0, 0);
        PatternSpriteRenderer continueRenderer = installRenderers(screen);
        screen.appendRenderCommands(new ArrayList<GLCommand>());
        verify(continueRenderer, never()).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
    }

    private PatternSpriteRenderer installRenderers(Sonic1SpecialStageResultsScreen screen) throws Exception {
        PatternSpriteRenderer continueRenderer = mock(PatternSpriteRenderer.class);
        setField(screen, "resultsRenderer", mock(PatternSpriteRenderer.class));
        setField(screen, "continueRenderer", continueRenderer);
        setField(screen, "artCached", true);
        return continueRenderer;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

