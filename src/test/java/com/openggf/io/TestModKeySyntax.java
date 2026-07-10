package com.openggf.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestModKeySyntax {

    @Test
    void acceptsCanonicalIdsAndOwnedKeys() {
        assertEquals("mod-1", ModKeySyntax.requireManifestId("mod-1"));
        assertEquals("mod-1:path/to.name", ModKeySyntax.requireOwnedKey("mod-1", "path/to.name"));
        assertEquals("mod-1:path/to.name", ModKeySyntax.requireDisplayKey("mod-1:path/to.name"));
    }

    @Test
    void rejectsMalformedIdsAndLocalNames() {
        for (String id : new String[]{"", "Upper", "-bad", "bad_", "a".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> ModKeySyntax.requireManifestId(id), id);
        }
        for (String name : new String[]{"", "Upper", "/bad", "bad/", "a//b", "a/./b", "a/../b", "has:colon", "a".repeat(129)}) {
            assertThrows(IllegalArgumentException.class, () -> ModKeySyntax.requireLocalName(name), name);
        }
    }

    @Test
    void displayKeysRequireOneColonExactOwnerAndUtf8Budget() {
        assertThrows(IllegalArgumentException.class, () -> ModKeySyntax.requireDisplayKey("mod:name:extra"));
        assertThrows(IllegalArgumentException.class, () -> ModKeySyntax.requireDisplayKey("Mod:name"));
        assertThrows(IllegalArgumentException.class, () -> ModKeySyntax.requireOwnedKey("mod", "Mod", "name"));
        assertThrows(IllegalArgumentException.class,
                () -> ModKeySyntax.requireOwnedKey("m".repeat(64), "a".repeat(128)));
    }
}
