package com.openggf.level;

import com.openggf.game.LevelState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.level.objects.HudRenderManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TestHudViewportWiring {

    @Test
    void uiPipelineForwardsLiveProjectionWidthBeforeHudDraw() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setProjectionWidth(528);
        UiRenderPipeline pipeline = new UiRenderPipeline(graphics);
        RecordingHud hud = new RecordingHud(graphics);
        pipeline.setHudRenderManager(hud);

        pipeline.renderOverlay(mock(LevelState.class), null);

        assertEquals(528, hud.viewportWidth,
                "the active UI pipeline must give the HUD the live projection width");
        assertEquals(528, hud.viewportWidthAtDraw,
                "the UI pipeline must forward the width before invoking the HUD");
        assertEquals(1, hud.drawCount);
    }

    @Test
    void levelRendererDirectHudPathForwardsProjectionWidthBeforeDraw() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/level/LevelRenderer.java"));
        String drawCall = "lm.hudRenderManager.draw(lm.levelGamestate, focusedPlayer);";
        int drawIndex = source.indexOf(drawCall);
        assertTrue(drawIndex >= 0, "LevelRenderer must retain the direct gameplay HUD draw path");

        String beforeDraw = source.substring(Math.max(0, drawIndex - 240), drawIndex);
        assertTrue(beforeDraw.contains(
                        "lm.hudRenderManager.setViewportWidth(lm.graphicsManager.getProjectionWidth());"),
                "the direct gameplay HUD path must forward the live projection width before drawing");
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
