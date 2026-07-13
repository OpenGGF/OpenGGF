package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestAizBgTreeWidescreen {

    @ParameterizedTest
    @CsvSource({"320,320", "352,352", "400,400", "528,528", "800,800"})
    void treeStartsAtActiveViewportRightEdge(int viewportWidth, int expectedScreenX) {
        AbstractObjectInstance.updateCameraBounds(0x4000, 0, 0x4000 + viewportWidth, 224, 0);
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x4000);

        AizBgTreeInstance tree = new AizBgTreeInstance(0);
        tree.setServices(new TestObjectServices().withCamera(camera));
        tree.update(0, null);

        assertEquals(expectedScreenX, tree.getX() - camera.getX(),
                "tree entry must remain just outside the active viewport");
    }

    @ParameterizedTest
    @CsvSource({"320", "352", "400", "528", "800"})
    void treeCannotRenderBeforeItsFirstDeferredUpdate(int viewportWidth) {
        AbstractObjectInstance.updateCameraBounds(0x4000, 0, 0x4000 + viewportWidth, 224, 0);
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x4000);
        LevelManager level = mock(LevelManager.class);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(level.getObjectRenderManager()).thenReturn(renderManager);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ2_BG_TREE)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);

        AizBgTreeInstance tree = new AizBgTreeInstance(0);
        tree.setServices(new TestObjectServices().withCamera(camera).withLevelManager(level));
        tree.appendRenderCommands(new ArrayList<>());

        verify(renderer, never()).drawFrameIndex(0, tree.getX(), tree.getY(), false, false);
    }
}
