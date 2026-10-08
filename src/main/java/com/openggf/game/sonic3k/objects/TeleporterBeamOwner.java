package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectInstance;

/**
 * The object that allocated an {@link TeleporterBeamObjectInstance} and reads its completion.
 *
 * <p>ROM {@code Obj_TeleporterBeamExpand} stores the allocating object in {@code parent2(a0)}
 * ({@code $48}) and, when the beam finishes contracting, does {@code clr.b $38(a1)} on it
 * (sonic3k.asm:91383-91387). Both the HPZ/SSZ teleporter pads ({@code Obj_SSZHPZTeleporter}) and
 * the Sky Sanctuary arrival controller ({@code Obj_57C1E}) are such parents.
 */
public interface TeleporterBeamOwner extends ObjectInstance {
    /** {@code clr.b $38(a1)} from the beam's deletion path. */
    void onBeamFinished();
}
