package com.openggf.game.patch;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPatchEnablementContract {

    /** Reference managed implementation used to validate the contract shape. */
    private static PatchEnablement managed(Map<PatchOwner, Integer> orderByOwner) {
        return new PatchEnablement() {
            @Override
            public boolean isEnabled(PatchOwner owner) {
                return owner instanceof PatchOwner.BuiltIn
                        || orderByOwner.getOrDefault(owner, -1) >= 0;
            }

            @Override
            public int orderOf(PatchOwner owner) {
                return owner instanceof PatchOwner.BuiltIn
                        ? PatchEnablement.BUILTIN_ORDER
                        : orderByOwner.getOrDefault(owner, Integer.MAX_VALUE);
            }
        };
    }

    @Test
    void allEnabledEnablesEverythingInRegistrationOrder() {
        assertTrue(PatchEnablement.ALL_ENABLED.isEnabled(new PatchOwner.BuiltIn("kis2")));
        assertTrue(PatchEnablement.ALL_ENABLED.isEnabled(new PatchOwner.Mod("anything")));
    }

    @Test
    void explicitBuiltinsAreOrderedBeforeManagedOwners() {
        PatchOwner a = new PatchOwner.Mod("mod-a");
        PatchOwner b = new PatchOwner.Mod("mod-b");
        PatchEnablement e = managed(Map.of(a, 0, b, 1));
        assertTrue(e.orderOf(new PatchOwner.BuiltIn("kis2")) < e.orderOf(a));
        assertTrue(e.orderOf(a) < e.orderOf(b));
    }

    @Test
    void sameLocalPatchIdCanBelongToDistinctOwners() {
        assertNotEquals(new PatchOwner.Mod("a"), new PatchOwner.Mod("b"));
        assertNotEquals(new PatchOwner.BuiltIn("shared"), new PatchOwner.Mod("shared"));
    }

    @Test
    void ownerIdentityMustBeExplicit() {
        assertThrows(NullPointerException.class, () -> new PatchOwner.BuiltIn(null));
        assertThrows(NullPointerException.class, () -> new PatchOwner.Mod(null));
    }

    @Test
    void enablementRejectsUnknownOwner() {
        assertThrows(NullPointerException.class, () -> PatchEnablement.ALL_ENABLED.isEnabled(null));
        assertThrows(NullPointerException.class, () -> PatchEnablement.ALL_ENABLED.orderOf(null));
    }
}
