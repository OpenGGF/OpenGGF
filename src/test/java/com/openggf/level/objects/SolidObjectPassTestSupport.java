package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

import java.util.List;

/**
 * Test support for solid-kernel unit tests that need every solid object's
 * checkpoint without running the object routines themselves.
 *
 * <p>The live object pass ({@link ObjectManager#update}) runs each object's
 * routine and resolves its solid checkpoint inside that slot. These helpers
 * resolve only the checkpoint half, in slot order, exactly as
 * {@code SolidExecutionMode.AUTO_AFTER_UPDATE} does after an object routine
 * returns. They replace the deleted player-side sweep these tests once drove.
 */
public final class SolidObjectPassTestSupport {

    private SolidObjectPassTestSupport() {
    }

    /**
     * The tail checkpoint of an object routine a test drove itself with
     * {@code snapshotPreUpdatePosition(); update(...)}: d4 is the snapshot x,
     * as {@code ObjectExecutionController} captures it at routine entry.
     */
    public static com.openggf.game.solid.SolidCheckpointBatch routineCheckpoint(ObjectManager manager,
            ObjectInstance object, PlayableEntity player, List<? extends PlayableEntity> sidekicks) {
        SolidObjectProvider provider = (SolidObjectProvider) object;
        SolidEntryRegisters captured = SolidEntryRegisters.capture(manager, object, provider);
        return manager.solidContacts().processCompatibilityCheckpoint(object, player, sidekicks, true,
                new SolidEntryRegisters(manager, object, provider, object.getPreUpdateX(),
                        captured.pieceEntryX(), null));
    }

    /** One participant's checkpoint for {@code object}, with its current x as d4. */
    public static com.openggf.game.solid.SolidCheckpointBatch participantCheckpoint(ObjectManager manager,
            ObjectInstance object, PlayableEntity player, boolean postMovement) {
        SolidEntryRegisters registers = object instanceof SolidObjectProvider provider
                ? SolidEntryRegisters.capture(manager, object, provider)
                : new SolidEntryRegisters(manager, object, null, object.getX(), null, null);
        return manager.solidContacts().processParticipantCheckpoint(object, player, postMovement, registers);
    }

    public static void resolveSolidCheckpoints(ObjectManager manager, PlayableEntity player) {
        resolveSolidCheckpoints(manager, player, List.of(), false);
    }

    public static void resolveSolidCheckpoints(ObjectManager manager, PlayableEntity player,
            boolean postMovement) {
        resolveSolidCheckpoints(manager, player, List.of(), postMovement);
    }

    public static void resolveSolidCheckpoints(ObjectManager manager, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks, boolean postMovement) {
        ObjectSolidContactController solid = manager.solidContacts();
        solid.beginInlineFrame(player, sidekicks, postMovement);
        for (ObjectInstance object : List.copyOf(manager.getSolidProviderObjects())) {
                if (object.isDestroyed() || !(object instanceof SolidObjectProvider provider)) {
                    continue;
                }
                // These tests move objects themselves between snapshotPreUpdatePosition()
                // and the checkpoint, so the routine-entry d4 is the snapshot x.
                SolidEntryRegisters captured = SolidEntryRegisters.capture(manager, object, provider);
                SolidEntryRegisters registers = new SolidEntryRegisters(manager, object, provider,
                        object.getPreUpdateX(), captured.pieceEntryX(), null);
                solid.processCompatibilityCheckpoint(object, player, sidekicks, postMovement, registers);
        }
    }
}
