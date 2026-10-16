package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for "Egg Prison opens but releases no (visible) animals".
 *
 * <p>The animals ARE spawned (the object count rises), but each
 * {@link EggPrisonAnimalInstance} captures its sprite renderer at construction via
 * {@code getRenderManager()}. The egg prison created them through raw
 * {@code new ... + addDynamicObject(...)}, which does NOT set the
 * {@code CONSTRUCTION_CONTEXT}, so after the DI migration made
 * {@code getRenderManager()} resolve through per-instance services
 * ({@code tryServices()} returns null during context-less construction) the
 * captured renderer was {@code null} and every released animal was invisible.
 *
 * <p>The fix routes the spawn through {@code spawnFreeChild(...)} (which sets the
 * construction context, exactly like every other child spawn), so the animal's
 * constructor resolves the render manager.
 *
 * <p>Since 62299509f the animal resolves its renderer when drawing (rewind
 * recreates it before services are injected). The guard therefore checks the
 * behaviour itself: a released animal must reach the animal renderer and draw.
 */
@ExtendWith(SingletonResetExtension.class)
@FullReset
public class TestEggPrisonAnimalRelease {

    @BeforeEach
    public void setUp() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    public void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    public void releasedAnimalsCaptureAUsableRenderer() {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x100);
        when(camera.getY()).thenReturn((short) 0x100);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.isVerticalWrapEnabled()).thenReturn(false);

        // A render manager IS available at gameplay time — the animal just has to
        // be able to reach it through its injected services.
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer animalRenderer = mock(PatternSpriteRenderer.class);
        when(animalRenderer.isReady()).thenReturn(true);
        when(renderManager.getAnimalRenderer()).thenReturn(animalRenderer);
        when(renderManager.getAnimalTypeA()).thenReturn(0);
        when(renderManager.getAnimalTypeB()).thenReturn(1);

        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public ObjectRenderManager renderManager() { return renderManager; }
        };
        ObjectManager manager = new ObjectManager(
                List.of(), null, 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;

        ObjectSpawn spawn = new ObjectSpawn(0x180, 0x180, 0x3E, 0, 0, false, 0);
        EggPrisonObjectInstance prison =
                manager.createDynamicObject(() -> new EggPrisonObjectInstance(spawn, "EggPrison"));
        prison.onButtonTriggered();

        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getX()).thenReturn((short) -10000);
        when(player.getY()).thenReturn((short) -10000);

        EggPrisonAnimalInstance animal = null;
        boolean drew = false;
        for (int frame = 0; frame < 400 && !drew; frame++) {
            manager.update(0x100, player, List.of(), frame);
            animal = firstAnimal(manager);
            if (animal != null) {
                // Prison animals stay hidden only while waiting in the capsule
                // (Obj28_Prison); every later draw must reach the animal renderer.
                animal.appendRenderCommands(new ArrayList<>());
                drew = mockingDetails(animalRenderer).getInvocations().stream()
                        .anyMatch(invocation -> invocation.getMethod().getName().equals("drawFrameIndex"));
            }
        }

        assertNotNull(animal, "the egg prison should spawn at least one animal");
        assertTrue(drew,
                "Regression: released egg-prison animals could not reach a usable renderer "
                        + "(getRenderManager() returned null because the spawn did not supply "
                        + "services), so every animal was invisible.");
        verify(animalRenderer, atLeastOnce())
                .drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
    }

    private static EggPrisonAnimalInstance firstAnimal(ObjectManager manager) {
        for (ObjectInstance obj : manager.getActiveObjects()) {
            if (obj instanceof EggPrisonAnimalInstance animal && !animal.isDestroyed()) {
                return animal;
            }
        }
        return null;
    }
}
