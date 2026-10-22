package com.openggf.graphics;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestPresentationViewport {
    @Test void nativeAndWideEdgesRetainPartialTilesAndCropRows() {
        var graphics = new GraphicsManager();
        for (int width : new int[]{320, 640}) {
            graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, width, 0, 224, -1, 1).get(new float[16]));
            var viewport = PresentationGraphicsAccess.viewport(graphics);
            assertNotNull(viewport);
            assertFalse(viewport.outside(width - 1, 10, width + 7, 18));
            assertTrue(viewport.outside(width + 1, 10, width + 9, 18));
            assertFalse(viewport.outside(-7, 10, 1, 18));
            assertTrue(viewport.outside(10, -8, 18, -1));
            assertFalse(viewport.outside(10, -8, 18, 1));
            // Already transformed coordinates may return to the viewport; only final geometry counts.
            assertFalse(viewport.outside(new float[]{width-2,10,width+2,12,width+1,16,width-3,14},0,0));
            assertTrue(viewport.outside(new float[]{width+1,10,width+3,12,width+4,16,width+2,14},0,0));
        }
    }
    @Test void projectionOwnsSafeAreaAndFboBoundsAndUnknownFailsOpen() {
        var graphics = new GraphicsManager();
        assertNull(PresentationGraphicsAccess.viewport(graphics));
        float[] matrix = new Matrix4f().ortho(-160, 480, 0, 300, -1, 1).get(new float[16]);
        graphics.setProjectionSource(new RenderProjection() {
            public float[] getProjectionMatrixBuffer() { return matrix; }
            public boolean isFBOProjectionActive() { return true; }
            public int getCurrentDisplayHeight() { return 300; }
        });
        var viewport = PresentationGraphicsAccess.viewport(graphics);
        assertEquals(-160, viewport.left(), .001); assertEquals(480, viewport.right(), .001);
        assertEquals(0, viewport.top(), .001); assertEquals(300, viewport.bottom(), .001);
        matrix[3] = .1f; assertNull(PresentationGraphicsAccess.viewport(graphics)); matrix[3] = 0;
        matrix[0] = Float.NaN; assertNull(PresentationGraphicsAccess.viewport(graphics));
    }
    @Test void preparingNeverCullsNativePublication() {
        var graphics = new GraphicsManager();
        graphics.initHeadless();
        graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
        SpritePresentation.prepare(graphics, 0, 0, () -> assertNull(PresentationGraphicsAccess.viewport(graphics)), ignored -> null);
        graphics.cleanup();
    }
}
