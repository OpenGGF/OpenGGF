package com.openggf.level;

import com.openggf.level.objects.ObjectInstance;

import java.util.Objects;

/**
 * Immutable identity/address tuple captured before an in-place level reload.
 *
 * <p>The object instance's mutable slot field is rebound while the replacement
 * {@code ObjectManager} is assembled. ROM transition scans, however, operate on
 * the occupant's original SST address. Keeping that address beside the identity
 * prevents rebuild order or later slot writes from changing survival and offset
 * eligibility.</p>
 */
public record TransitionSstOccupant(ObjectInstance identity, int originalSlot) {
    public TransitionSstOccupant {
        Objects.requireNonNull(identity, "identity");
    }
}
