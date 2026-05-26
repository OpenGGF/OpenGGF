package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestCompositeSnapshotOwnership {

    @Test
    void publicConstructorPreservesImmutabilityViaDefensiveCopy() {
        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("k", "v1");
        CompositeSnapshot snapshot = new CompositeSnapshot(source);

        source.put("k", "v2");
        source.put("extra", "x");

        assertEquals("v1", snapshot.get("k"),
                "Public constructor must defensively copy — the public immutability "
                        + "contract holds for arbitrary callers");
        assertEquals(1, snapshot.entries().size(),
                "Mutations to the source map must not appear in the snapshot");
    }

    @Test
    void ownedFactoryTransfersOwnershipAndDoesNotCopy() {
        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("k", "v1");
        CompositeSnapshot snapshot = CompositeSnapshot.owned(source);

        // The contract here is "do not retain a reference and mutate". We mutate
        // anyway to pin the no-copy behaviour: a regression that re-adds a copy
        // would make this assertion fail.
        source.put("k", "v2");
        assertEquals("v2", snapshot.get("k"),
                "owned() must NOT copy — the production hot path relies on this");
    }

    @Test
    void entriesReturnsTheSameUnmodifiableViewEachCall() {
        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("a", 1);
        source.put("b", 2);

        CompositeSnapshot snapshot = CompositeSnapshot.owned(source);
        Map<String, Object> first = snapshot.entries();
        Map<String, Object> second = snapshot.entries();

        assertSame(first, second, "entries() must return the same instance each call");
        assertThrows(UnsupportedOperationException.class, () -> first.put("c", 3),
                "entries() must remain unmodifiable regardless of construction path");
    }
}
