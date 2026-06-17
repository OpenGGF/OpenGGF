package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestAsteronBadnikInstance {

    @Test
    void rendersWithRomHighPriorityBit() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic2ObjectArtKeys.ASTERON)).thenReturn(renderer);

        AsteronBadnikInstance asteron = new AsteronBadnikInstance(
                new ObjectSpawn(0x0200, 0x0180, Sonic2ObjectIds.ASTERON, 0x00, 0, false, 0));
        asteron.setServices(new StubObjectServices() {
            @Override
            public ObjectRenderManager renderManager() {
                return renderManager;
            }
        });

        asteron.appendRenderCommands(new ArrayList<GLCommand>());

        verify(renderer).drawFrameIndexForcedPriority(0, 0x0200, 0x0180, false, false, -1, true);
    }

    @Test
    void spawnedSpikesRenderWithRomHighPriorityBit() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic2ObjectArtKeys.ASTERON)).thenReturn(renderer);

        BadnikProjectileInstance spike = new BadnikProjectileInstance(
                new ObjectSpawn(0x0200, 0x0180, Sonic2ObjectIds.ASTERON, 0x00, 0, false, 0),
                BadnikProjectileInstance.ProjectileType.ASTERON_SPIKE,
                0x0220, 0x0190, 0x0300, 0x0000, false, true, 0, 3);
        spike.setServices(new StubObjectServices() {
            @Override
            public ObjectRenderManager renderManager() {
                return renderManager;
            }
        });

        spike.appendRenderCommands(new ArrayList<GLCommand>());

        verify(renderer).drawFrameIndexForcedPriority(3, 0x0220, 0x0190, true, false, -1, true);
    }

    @Test
    void explosionReusesAsteronSlotAndSpikesAreObj98Children() throws Exception {
        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        ObjectSpawn spawn = new ObjectSpawn(0x0710, 0x01A0, Sonic2ObjectIds.ASTERON, 0x00, 0, false, 0);
        AsteronBadnikInstance asteron = new AsteronBadnikInstance(spawn);
        asteron.setSlotIndex(25);
        asteron.setServices(new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }

            @Override
            public ObjectRenderManager renderManager() {
                return renderManager;
            }
        });

        Method explode = AsteronBadnikInstance.class.getDeclaredMethod("explode");
        explode.setAccessible(true);
        explode.invoke(asteron);

        verify(objectManager).addDynamicObjectAtSlot(
                org.mockito.ArgumentMatchers.argThat(ExplosionObjectInstance.class::isInstance),
                eq(25));

        ArgumentCaptor<BadnikProjectileInstance> projectiles =
                ArgumentCaptor.forClass(BadnikProjectileInstance.class);
        verify(objectManager, times(5)).addDynamicObjectAfterCurrent(projectiles.capture());
        List<BadnikProjectileInstance> spawned = projectiles.getAllValues();
        assertEquals(5, spawned.size());
        for (BadnikProjectileInstance projectile : spawned) {
            assertEquals(Sonic2ObjectIds.PROJECTILE, projectile.getSpawn().objectId() & 0xFF,
                    "Obj_CreateProjectiles writes Obj98 into each spike child slot");
        }
        assertTrue(asteron.isDestroyed());
        assertEquals(-1, asteron.getSlotIndex(),
                "The original Asteron must detach its slot so the Obj27 replacement keeps it");
        verify(objectManager).removeFromActiveSpawns(spawn);
        verify(objectManager, times(5)).addDynamicObjectAfterCurrent(any(BadnikProjectileInstance.class));
    }
}
