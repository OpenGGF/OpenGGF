package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestHudRenderManagerWidescreen {

    @Test
    void normalHudUsesCenteredNativeOrigin() {
        GraphicsManager graphics = mock(GraphicsManager.class);
        Camera camera = mock(Camera.class);
        GameStateManager gameState = mock(GameStateManager.class);
        LevelState levelState = mock(LevelState.class);
        when(camera.getXWithShake()).thenReturn((short) 0);
        when(camera.getYWithShake()).thenReturn((short) 0);
        when(levelState.getRings()).thenReturn(7);
        when(levelState.getFlashCycle()).thenReturn(false);
        when(levelState.shouldFlashTimer()).thenReturn(false);
        when(levelState.getDisplayTime()).thenReturn("0:10");
        when(gameState.getScore()).thenReturn(123);
        when(gameState.getLives()).thenReturn(3);

        List<RenderCall> calls = new ArrayList<>();
        doAnswer(invocation -> {
            calls.add(new RenderCall(invocation.getArgument(0),
                    invocation.getArgument(2), invocation.getArgument(3)));
            return null;
        }).when(graphics).renderPatternWithId(anyInt(), any(), anyInt(), anyInt());

        HudRenderManager hud = new HudRenderManager(graphics, camera, gameState);
        hud.setViewportWidth(400);
        hud.setDigitPatternIndex(200);
        hud.setLivesNumbersPatternIndex(220);
        hud.draw(levelState, null);

        assertTrue(calls.contains(new RenderCall(202, 128, 8)));
        assertTrue(calls.contains(new RenderCall(214, 120, 40)));
        assertTrue(calls.contains(new RenderCall(223, 96, 208)));
    }

    @Test
    void bonusHudKeepsNativeOriginAtWidescreen() {
        GraphicsManager graphics = mock(GraphicsManager.class);
        Camera camera = mock(Camera.class);
        GameStateManager gameState = mock(GameStateManager.class);
        LevelState levelState = mock(LevelState.class);
        when(camera.getXWithShake()).thenReturn((short) 0);
        when(camera.getYWithShake()).thenReturn((short) 0);
        when(levelState.getRings()).thenReturn(7);
        when(levelState.getFlashCycle()).thenReturn(false);

        List<RenderCall> calls = new ArrayList<>();
        doAnswer(invocation -> {
            calls.add(new RenderCall(invocation.getArgument(0),
                    invocation.getArgument(2), invocation.getArgument(3)));
            return null;
        }).when(graphics).renderPatternWithId(anyInt(), any(), anyInt(), anyInt());

        HudRenderManager hud = new HudRenderManager(graphics, camera, gameState);
        hud.setViewportWidth(400);
        hud.setDigitPatternIndex(200);
        hud.setBonusStageHudLayout(true);
        hud.draw(levelState, null);

        assertTrue(calls.contains(new RenderCall(214, 80, 8)));
    }

    private record RenderCall(int pattern, int x, int y) {}
}
