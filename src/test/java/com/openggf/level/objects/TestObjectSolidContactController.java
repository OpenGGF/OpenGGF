package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class TestObjectSolidContactController {

    @Test
    void resetClearsInlineSupportAndStaleSupportLossState() {
        ObjectSolidContactController controller =
                new ObjectSolidContactController(mock(ObjectManager.class));
        PlayableEntity player = mock(PlayableEntity.class);

        controller.markObjectSupportThisFrame(player);
        controller.forceAirOnStaleObjectSupportLoss(player);
        ObjectManagerSnapshot.SolidContactState before = controller.captureRewindState();
        assertEquals(1, before.inlineSupportedPlayers().size());
        assertEquals(1, before.forceAirOnStaleSupportLoss().size());

        controller.reset();

        ObjectManagerSnapshot.SolidContactState after = controller.captureRewindState();
        assertTrue(after.inlineSupportedPlayers().isEmpty(),
                "reset must clear per-frame inline support players");
        assertTrue(after.forceAirOnStaleSupportLoss().isEmpty(),
                "reset must clear stale-support force-air players");
    }

    @Test
    void globalSolidProviderGetterRunsInsideCreatorFaultBoundary() {
        HostileSolidObject hostile = new HostileSolidObject(true, false);
        ObjectManager manager = boundaryEnabledManager(hostile);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class,
                () -> manager.hasStandingContact(mock(PlayableEntity.class)));

        assertEquals("solid-owner", aborted.owner());
        assertTrue(aborted.getCause() instanceof IllegalStateException);
    }

    @Test
    void compatibilitySolidListenerRunsInsideCreatorFaultBoundary() {
        HostileSolidObject hostile = new HostileSolidObject(false, true);
        ObjectManager manager = boundaryEnabledManager(hostile);
        PlayableEntity player = mock(PlayableEntity.class);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class,
                () -> manager.processImmediateInlineSolidCheckpoint(
                        hostile, player, List.of()));

        assertEquals("solid-owner", aborted.owner());
        assertTrue(aborted.getCause() instanceof IllegalStateException);
    }

    private static ObjectManager boundaryEnabledManager(ObjectInstance instance) {
        ModFaultBoundary faultBoundary = new ModFaultBoundary(
                Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        ObjectRegistry registry = new BoundaryRegistry(faultBoundary);
        ObjectServices services = mock(ObjectServices.class);
        SolidExecutionRegistry solidExecutionRegistry = mock(SolidExecutionRegistry.class);
        when(services.solidExecutionRegistry()).thenReturn(solidExecutionRegistry);
        when(solidExecutionRegistry.previousStanding(any(), any()))
                .thenReturn(PlayerStandingState.NONE);
        ObjectManager manager = new ObjectManager(List.of(), registry, 0, null, null,
                mock(GraphicsManager.class), mock(Camera.class), services);
        manager.reset(0);
        manager.addDynamicObject(instance);
        manager.objectCallbacks().register(instance, instance.getSpawn());
        return manager;
    }

    private record BoundaryRegistry(ModFaultBoundary faultBoundary)
            implements ObjectRegistry, Supplier<ModFaultBoundary> {
        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            return null;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "HOSTILE";
        }

        @Override
        public ModFaultBoundary get() {
            return faultBoundary;
        }
    }

    private static final class HostileSolidObject
            implements ObjectInstance, SolidObjectProvider, SolidObjectListener {
        private final ObjectSpawn spawn = new ObjectSpawn(
                100, 100, 0, 0, 0, false, 0, -1,
                "solid-owner", "solid-owner:hostile");
        private final boolean failProvider;
        private final boolean failListener;

        private HostileSolidObject(boolean failProvider, boolean failListener) {
            this.failProvider = failProvider;
            this.failListener = failListener;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return spawn;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public boolean isSolidFor(PlayableEntity player) {
            if (failProvider) {
                throw new IllegalStateException("hostile solid provider");
            }
            return false;
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return new SolidObjectParams(16, 16, 16);
        }

        @Override
        public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
            if (failListener) {
                throw new IllegalStateException("hostile solid listener");
            }
        }

        @Override
        public void onSolidContactCleared(PlayableEntity player, int frameCounter) {
            if (failListener) {
                throw new IllegalStateException("hostile solid listener");
            }
        }
    }
}
