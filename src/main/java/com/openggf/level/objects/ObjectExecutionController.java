package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;

import java.util.List;

/** Executes one SST routine inside the shared solid-checkpoint envelope. */
final class ObjectExecutionController {
    private final ObjectManager objects;

    ObjectExecutionController(ObjectManager objects) {
        this.objects = objects;
    }

    List<PlayableEntity> collectActivePlayers(
            PlayableEntity player,
            List<? extends PlayableEntity> sidekicks,
            List<PlayableEntity> destination) {
        destination.clear();
        if (player != null) {
            destination.add(player);
        }
        for (PlayableEntity sidekick : sidekicks) {
            if (sidekick != null) {
                destination.add(sidekick);
            }
        }
        return destination;
    }

    void execute(
            ObjectInstance instance,
            PlayableEntity player,
            List<? extends PlayableEntity> sidekicks,
            boolean inlineSolidResolution,
            boolean solidPostMovement) {
        var original = objects.initialDispatch().currentReservation();
        boolean suspended = ObjectDirectionAccess.suspended(objects, instance);
        InitialObjectDispatchController.requireCurrent(original);
        if (suspended) {
            return;
        }
        if (objects.stageplayDirection != null
                && !objects.stageplayDirection.allowsObject(instance)) {
            objects.stageplayDirection.removeDynamic(instance);
            return;
        }
        InitialObjectDispatchController.requireCurrent(original);
        SolidExecutionRegistry registry = objects.solidExecutionRegistry();
        var frame = original != null ? com.openggf.game.solid.SolidExecutionFrames.capture(registry) : null;
        SolidExecutionMode mode = null;
        if (instance instanceof SolidObjectProvider provider) {
            SolidExecutionMode providerMode = objects.callObjectCallback(instance, provider::solidExecutionMode);
            if (inlineSolidResolution || providerMode == SolidExecutionMode.MANUAL_CHECKPOINT) {
                mode = providerMode;
            }
        }
        ObjectSolidExecutionContext.Resolver resolver =
                mode == SolidExecutionMode.MANUAL_CHECKPOINT
                        ? new ObjectSolidExecutionContext.Resolver() {
                            @Override
                            public SolidCheckpointBatch resolveNow() {
                                InitialObjectDispatchController.requireCurrent(original);
                                var batch = objects.processManualSolidCheckpoint(instance, player, sidekicks, solidPostMovement);
                                InitialObjectDispatchController.requireCurrent(original);
                                return batch;
                            }

                            @Override
                            public SolidCheckpointBatch resolvePlayer(PlayableEntity participant) {
                                InitialObjectDispatchController.requireCurrent(original);
                                var batch = objects.solidContacts().processParticipantCheckpoint(instance, participant, solidPostMovement);
                                InitialObjectDispatchController.requireCurrent(original);
                                return batch;
                            }
                        }
                        : null;
        InitialObjectDispatchController.requireCurrent(original);
        registry.beginObject(instance, resolver);
        var objectContext = frame != null ? frame.currentObject() : null;
        try {
            // Creator object routines run inside the fault boundary so a mod
            // failure aborts its owner rather than propagating raw.
            var policies = com.openggf.game.mutators.NativeObjectPolicyAccess.port(objects.services());
            InitialObjectDispatchController.requireCurrent(original);
            boolean permitted = policies == null || policies.permitsRoutine(instance, player);
            InitialObjectDispatchController.requireCurrent(original);
            if (permitted) {
                objects.runObjectCallback(instance,
                        () -> instance.update(objects.vblaCounter(), player));
                InitialObjectDispatchController.requireCurrent(original);
                if (policies != null) policies.afterRoutine(instance, player);
                InitialObjectDispatchController.requireCurrent(original);
            }
            if (instance instanceof AbstractObjectInstance object) {
                object.clearAwaitingFirstTouchExecution();
            }
            if (mode == SolidExecutionMode.AUTO_AFTER_UPDATE
                    && !objects.callObjectCallback(instance, instance::isDestroyed)) {
                var checkpoint = objects.processCompatibilitySolidCheckpoint(instance, player, sidekicks, solidPostMovement);
                InitialObjectDispatchController.requireCurrent(original);
                registry.publishCheckpoint(checkpoint);
            }
        } finally {
            if (frame == null) registry.endObject(instance);
            else if (original.ownsManager()) frame.endObject(instance, objectContext);
        }
    }
}
