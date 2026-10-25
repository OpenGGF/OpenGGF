package com.openggf.level.objects;

import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Structural owner repair after native human recreation, before generic object probes. */
final class NativeCompetitionPlayerBoundRestore {
    private NativeCompetitionPlayerBoundRestore() { }

    static DynamicObjectEntry rebind(ObjectManager manager, ObjectServices services, DynamicObjectEntry entry) {
        if (services == null || !(entry.playerOwner() instanceof AbstractPlayableSprite captured)
                || captured.isCpuControlled()) {
            return entry;
        }
        var world = services.worldSession();
        var level = services.levelManager();
        var sprites = services.spriteManager();
        if (world == null || level == null || sprites == null
                || services.objectManager() != manager || level.getObjectManager() != manager
                || level.getWorldSession() != world) {
            return entry;
        }
        var roster = NativeCompetitionRoster.from(world);
        if (roster == null) return entry;
        for (int slot = 0; slot < 2; slot++) {
            var actor = roster.player(slot);
            if (actor != null && !actor.isCpuControlled() && sprites.getSprite(actor.getCode()) == actor
                    && actor.currentLevelManagerIfAvailable() == level
                    && actor.getCode().equals(captured.getCode())
                    && actor.characterKey().equals(captured.characterKey())) {
                if (actor == captured) return entry;
                // Identity metadata only: no constructor, actor mutation, timeline
                // write or captured RAM application is permitted at this boundary.
                return new DynamicObjectEntry(entry.className(), entry.spawn(), entry.slotIndex(), entry.state(),
                        actor, entry.objectId(), entry.ownerModId(), entry.rewindableAuxiliary());
            }
        }
        return entry;
    }
}
