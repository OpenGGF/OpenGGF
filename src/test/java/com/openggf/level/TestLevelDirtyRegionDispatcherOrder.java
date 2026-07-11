package com.openggf.level;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestLevelDirtyRegionDispatcherOrder {
    @Test
    void ringResyncPrecedesObjectResyncWhenObjectBackedRingsDirtyBothTables() {
        List<String> calls = new ArrayList<>();
        LevelDirtyRegionDispatcher.resyncDirtySpawnLists(true, true,
                () -> calls.add("rings"), () -> calls.add("objects"));
        assertEquals(List.of("rings", "objects"), calls);
    }
}
