package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;

/**
 * SKL $6D, Obj_InvisibleShockBlock (sonic3k.asm:43265-43267).
 * Sets shield_reaction bit 5, then enters the horizontal hurt block.
 * Subtype dimensions, face selection, hurt ordering and immunity are shared.
 */
public final class Sonic3kInvisibleShockBlockObjectInstance
        extends Sonic3kInvisibleHurtBlockHObjectInstance {
    private boolean initialized;
    private boolean contactsEnabled;

    public Sonic3kInvisibleShockBlockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "InvisibleShockBlock", REACTION_LIGHTNING_SHIELD);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // Obj_InvisibleHurtBlockHorizontal falls through on the unflipped top
        // branch; either flip installs its routine and returns once (:43292-43312).
        contactsEnabled = initialized || (spawn.renderFlags() & 3) == 0;
        initialized = true;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return contactsEnabled;
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (contactsEnabled) {
            super.onSolidContact(player, contact, frameCounter);
        }
    }

    @Override
    public boolean checksOutOfRangeAfterRoutine() {
        return true;
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        // loc_1F4A2 is after SolidObjectFull2 and damage; the flipped init return
        // does not reach it. Use the native fixed unsigned coarse-back window.
        int coarseBack = (cameraX - 0x80) & 0xFF80;
        return contactsEnabled && (((getX() & 0xFF80) - coarseBack) & 0xFFFF) > 0x280;
    }
}
