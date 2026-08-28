package com.openggf.level;

import com.openggf.game.LevelState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.level.objects.HudRenderManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TestHudViewportWiring {

    @Test
    void uiPipelineLeavesViewportResolutionWithTheInjectedHudOwner() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setProjectionWidth(528);
        UiRenderPipeline pipeline = new UiRenderPipeline(graphics);
        RecordingHud hud = new RecordingHud(graphics);
        pipeline.setHudRenderManager(hud);

        pipeline.renderOverlay(mock(LevelState.class), null);

        assertEquals(0, hud.viewportWidth,
                "the graphics pipeline must not reach into the level-owned HUD API");
        assertEquals(1, hud.drawCount);
    }

    @Test
    void levelRendererHudPreparationForwardsProjectionWidthBeforeDraw() throws Exception {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setProjectionWidth(528);
        RecordingHud hud = new RecordingHud(graphics);

        Method preparation = Arrays.stream(LevelRenderer.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("prepareHudForDraw"))
                .findFirst()
                .orElse(null);
        assertTrue(preparation != null,
                "LevelRenderer must expose the executable HUD preparation seam");
        preparation.setAccessible(true);
        preparation.invoke(null, hud, graphics);
        hud.draw(mock(LevelState.class), null);

        assertEquals(528, hud.viewportWidthAtDraw,
                "the direct gameplay HUD owner must forward width before drawing");
    }

    private static final class RecordingHud extends HudRenderManager {
        private int viewportWidth;
        private int viewportWidthAtDraw;
        private int drawCount;

        private RecordingHud(GraphicsManager graphics) {
            super(graphics, null, null);
        }

        @Override
        public void setViewportWidth(int viewportWidth) {
            this.viewportWidth = viewportWidth;
        }

        @Override
        public void draw(LevelState levelState, com.openggf.game.PlayableEntity player) {
            viewportWidthAtDraw = viewportWidth;
            drawCount++;
        }
    }
}
