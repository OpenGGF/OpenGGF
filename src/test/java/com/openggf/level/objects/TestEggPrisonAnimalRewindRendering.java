package com.openggf.level.objects;

import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rewind recreates a released capsule animal through its spawn constructor before
 * services are injected. Its renderer must therefore be resolved when drawing; a
 * constructor-cached null renderer hid every recreated animal while all captured
 * gameplay state matched (SOZ/MHZ capsule-results replay windows).
 */
class TestEggPrisonAnimalRewindRendering {

    @Test
    void animalConstructedWithoutServicesDrawsOnceServicesAreInjected() throws Exception {
        EggPrisonAnimalInstance animal = new EggPrisonAnimalInstance(
                new ObjectSpawn(0x100, 0x120, 0x28, 0, 0, false, 0));
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getAnimalRenderer()).thenReturn(renderer);
        ObjectServices services = mock(ObjectServices.class);
        when(services.renderManager()).thenReturn(renderManager);
        animal.setServices(services);
        releaseFromPrison(animal);

        animal.appendRenderCommands(new ArrayList<>());

        verify(renderer).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
    }

    private static void releaseFromPrison(EggPrisonAnimalInstance animal) throws Exception {
        Field state = EggPrisonAnimalInstance.class.getDeclaredField("state");
        state.setAccessible(true);
        Class<?> stateType = state.getType();
        for (Object constant : stateType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals("MAIN")) {
                state.set(animal, constant);
                return;
            }
        }
        throw new AssertionError("EggPrisonAnimalInstance.State.MAIN missing");
    }
}
