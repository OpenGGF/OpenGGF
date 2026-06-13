package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.mockito.Mockito.mock;
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
}
