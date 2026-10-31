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
        try {
            for (ObjectInstance object : List.copyOf(manager.getSolidProviderObjects())) {
                if (object.isDestroyed()) {
                    continue;
                }
                solid.processCompatibilityCheckpoint(object, player, sidekicks, postMovement);
            }
        } finally {
            solid.finishInlineFrame(player, sidekicks);
        }
    }
}
