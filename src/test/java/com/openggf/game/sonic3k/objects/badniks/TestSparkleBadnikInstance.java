package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestSparkleBadnikInstance {
    @Test
    void registryRoutesSparkleSlotToConcreteBadnik() throws Exception {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance instance = registry.create(new ObjectSpawn(
                0x2200, 0x0580, Sonic3kObjectIds.SPARKLE, 0, 0, false, 0));

        Class<?> expected = Class.forName(
                "com.openggf.game.sonic3k.objects.badniks.SparkleBadnikInstance");
        assertTrue(expected.isInstance(instance),
                "Object $A4 should be Obj_Sparkle, not a placeholder");
        assertEquals(expected, instance.getClass());
    }

    @Test
    void sparkleUsesRegisteredCnzSparkleRendererAndInitialFrame() {
        SparkleBadnikInstance sparkle = new SparkleBadnikInstance(new ObjectSpawn(
                0x2200, 0x0580, Sonic3kObjectIds.SPARKLE, 0, 0, false, 0));

        LevelManager levelManager = mock(LevelManager.class);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(levelManager.getObjectRenderManager()).thenReturn(renderManager);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.CNZ_SPARKLE)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);

        ((AbstractObjectInstance) sparkle).setServices(
                new TestObjectServices().withLevelManager(levelManager));

        sparkle.appendRenderCommands(new ArrayList<>());

        verify(renderManager).getRenderer(Sonic3kObjectArtKeys.CNZ_SPARKLE);
        verify(renderer).drawFrameIndex(0, 0x2200, 0x0580, false, false);
    }
}
