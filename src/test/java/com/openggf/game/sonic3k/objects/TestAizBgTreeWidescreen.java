package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
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
}
