package com.openggf.level.objects;

import java.util.Objects;

/**
 * Engine-only cross-package access to placed owners' folded native SST reservations.
 * This unannotated adapter is not a supported creator API. It owns no state and does not
 * change raw FindNextFreeObj allocation or the object-manager snapshot format.
 */
public final class NativeChildSlotReservations {
    private NativeChildSlotReservations() { }

    /** The genuine placed parent must already have its fixed reservation prefix. */
    public static int reserveAfter(ObjectManager manager, ObjectInstance parent,
                                   int predecessor, int protectedPrefix) {
        return Objects.requireNonNull(manager, "manager")
                .reservePlacedChildSlotAfter(parent, predecessor, protectedPrefix);
    }

    /** Clear the caller's saved slot on first release; slot numbers are not generation tokens. */
    public static boolean release(ObjectManager manager, ObjectInstance parent,
                                  int slot, int protectedPrefix) {
        return Objects.requireNonNull(manager, "manager")
                .releasePlacedChildSlot(parent, slot, protectedPrefix);
    }
}
