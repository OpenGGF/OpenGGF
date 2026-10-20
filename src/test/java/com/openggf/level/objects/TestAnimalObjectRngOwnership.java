package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameRng;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TestAnimalObjectRngOwnership {
    private static final long INITIAL_SEED = 0x13579BDFL;
    private static final ObjectSpawn ANIMAL_SPAWN =
            new ObjectSpawn(0x100, 0x120, 0x28, 0, 0, false, 0);

    @Test
    void deferredAnimalConsumesRngOnceOnItsFirstOwnDispatch() {
        RecordingServices services = new RecordingServices(INITIAL_SEED);
        GameRng control = new GameRng(GameRng.Flavour.S3K, INITIAL_SEED);
        control.nextBits(1);

        AnimalObjectInstance animal =
                AnimalObjectInstance.deferredArtVariant(ANIMAL_SPAWN, services, null);
        animal.setServices(services);

        assertEquals(INITIAL_SEED, services.rng().getSeed(),
                "AllocateObject only installs Obj_Animal; construction must not execute loc_2C924");

        animal.update(0, null);
        assertEquals(control.getSeed(), services.rng().getSeed(),
                "the animal's first SST dispatch must consume exactly one Random_Number result");

        animal.update(1, null);
        assertEquals(control.getSeed(), services.rng().getSeed(),
                "later animal routines must not redraw the art variant");
    }

    @Test
    void rewindRecreateDoesNotConsumeAnimalRng() {
        RecordingServices services = new RecordingServices(INITIAL_SEED);
        AnimalObjectInstance animal =
                AnimalObjectInstance.deferredArtVariant(ANIMAL_SPAWN, services, null);

        animal.recreateForRewind(new RewindRecreateContext(ANIMAL_SPAWN, null, services));

        assertEquals(INITIAL_SEED, services.rng().getSeed(),
                "rewind recreate restores captured artVariant and must not execute Random_Number");
    }

    @Test
    void interveningSlotConsumesRngBeforeNewAnimalRunsItsOwnDispatch() {
        RecordingServices services = new RecordingServices(INITIAL_SEED);
        Camera camera = mock(Camera.class);
        services.camera = camera;
        ObjectManager manager = new ObjectManager(
                List.of(), new NoOpObjectRegistry(), 0, null, null,
                null, camera, services);
        services.objectManager = manager;

        ExplosionObjectInstance explosion = new ExplosionObjectInstance(
                0x27, 0x100, 0x120, null,
                (spawn, svc) -> AnimalObjectInstance.deferredArtVariant(spawn, svc, null),
                null, 0, false);
        RecordingRngConsumer intervening = new RecordingRngConsumer(
                new ObjectSpawn(0x110, 0x120, 0, 0, 0, false, 0));
        manager.addDynamicObjectAtSlot(explosion, 4);
        manager.addDynamicObjectAtSlot(intervening, 5);

        GameRng control = new GameRng(GameRng.Flavour.S3K, INITIAL_SEED);
        int expectedInterveningResult = control.nextRaw();
        control.nextBits(1);

        manager.update(0, null, List.of(), 1);

        assertEquals(expectedInterveningResult, intervening.result,
                "slot 5 must consume RNG after the slot-4 explosion allocates, before the later animal slot");
        manager.update(1, null, List.of(), 2);
        assertEquals(control.getSeed(), services.rng().getSeed(),
                "the allocated animal must consume the following result only when its own slot dispatches");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void walkingFlyingLaunchUsesActualFixedRenderFlipForBothVelocitySigns(boolean parentFlipX) throws Exception {
        RecordingServices services = renderingServices(0x00420002L);
        PatternSpriteRenderer renderer = services.renderManager().getAnimalRenderer();
        AnimalObjectInstance animal = AnimalObjectInstance.deferredWalkingFlyingLaunch(
                ANIMAL_SPAWN, services, AnimalType.RABBIT, AnimalType.CHICKEN, parentFlipX);
        animal.setServices(services);
        animal.update(0, null);
        var velocity = AnimalObjectInstance.class.getDeclaredField("xVelocity");
        velocity.setAccessible(true);
        assertEquals(parentFlipX ? 0x200 : -0x200, velocity.get(animal));
        animal.appendRenderCommands(new ArrayList<>());
        // Map_Animals5, variant0, native frame1. Mock zone types deliberately differ.
        verify(renderer).drawFrameIndex(25, 0x100, 0x120, true, false);
        clearInvocations(renderer);
        animal.update(1, null); // deferArtVariantRng is already false; launch mode must persist.
        animal.appendRenderCommands(new ArrayList<>());
        verify(renderer).drawFrameIndex(eq(25), anyInt(), anyInt(), eq(true), eq(false));
    }

    @Test
    void genericPopKeepsExistingUnflippedInitialRendererArguments() {
        RecordingServices services = renderingServices(0x00420002L);
        AnimalObjectInstance animal = AnimalObjectInstance.deferredArtVariant(ANIMAL_SPAWN, services, null);
        animal.setServices(services);
        animal.update(0, null);
        animal.appendRenderCommands(new ArrayList<>());
        // Generic initializer retains frame2/xVelocity0 and zone-Pig mappings; no direct-launch mode.
        verify(services.renderManager().getAnimalRenderer()).drawFrameIndex(14, 0x100, 0x120, false, false);
    }

    @Test
    void walkingFlyingFactoryRejectsNullProfilesBeforeAnyRngDraw() {
        RecordingServices services = new RecordingServices(INITIAL_SEED);
        assertThrows(NullPointerException.class, () -> AnimalObjectInstance.deferredWalkingFlyingLaunch(
                ANIMAL_SPAWN, services, null, AnimalType.CHICKEN, false));
        assertThrows(NullPointerException.class, () -> AnimalObjectInstance.deferredWalkingFlyingLaunch(
                ANIMAL_SPAWN, services, AnimalType.RABBIT, null, false));
        assertEquals(INITIAL_SEED, services.rng().getSeed());
    }

    private static RecordingServices renderingServices(long seed) {
        RecordingServices services = new RecordingServices(seed);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        services.renderManager = mock(ObjectRenderManager.class);
        when(services.renderManager.getAnimalRenderer()).thenReturn(renderer);
        when(services.renderManager.getAnimalTypeA()).thenReturn(AnimalType.PIG.ordinal());
        when(services.renderManager.getAnimalTypeB()).thenReturn(AnimalType.FLICKY.ordinal());
        when(services.camera.getX()).thenReturn((short) 0x80);
        when(services.camera.getY()).thenReturn((short) 0x80);
        return services;
    }

    private static final class RecordingServices extends StubObjectServices {
        private final GameRng rng;
        private ObjectManager objectManager;
        private ObjectRenderManager renderManager;
        private Camera camera = mock(Camera.class);

        private RecordingServices(long seed) {
            rng = new GameRng(GameRng.Flavour.S3K, seed);
        }

        @Override
        public ObjectRenderManager renderManager() {
            return renderManager;
        }

        @Override
        public GameRng rng() {
            return rng;
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }

        @Override
        public Camera camera() {
            return camera;
        }
    }

    private static final class RecordingRngConsumer extends AbstractObjectInstance {
        private int result;

        private RecordingRngConsumer(ObjectSpawn spawn) {
            super(spawn, "RngConsumer");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            result = services().rng().nextRaw();
            setDestroyed(true);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }

    private static final class NoOpObjectRegistry implements ObjectRegistry {
        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            return null;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "noop";
        }
    }
}
