package com.openggf.game.rewind;

import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestRewindSnapshotDiffTerrain {
    @Test void clonedTerrainDescriptorsCompareByContentButRetainGameplayDifferences() {
        var first = new Chunk();
        first.restoreState(new int[]{1, 2, 3, 4, 5, 6});
        var replay = new Chunk();
        replay.restoreState(first.saveState());
        var before = new LevelSnapshot(1, new Block[0], new Chunk[]{first}, new byte[]{3}, 20);
        var after = new LevelSnapshot(7, new Block[0], new Chunk[]{replay}, new byte[]{3}, 20);
        assertTrue(RewindSnapshotDiff.diffKey("level", before, after).isEmpty());
        assertFalse(RewindSnapshotDiff.diffKey("level", before,
                new LevelSnapshot(7, new Block[0], new Chunk[]{replay}, new byte[]{3}, 21)).isEmpty());
        replay.restoreState(new int[]{1, 2, 3, 4, 5, 7});
        assertFalse(RewindSnapshotDiff.diffKey("level", before, after).isEmpty());
    }
}
