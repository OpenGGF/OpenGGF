package com.openggf.game.rewind;

import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindSnapshotDiffPatterns {
    public record ArtSnapshot(Pattern[] patterns, int timer) {}

    @Test
    void decodedAgainArtComparesByPixelsAndRetainsOtherState() {
        var first = new Pattern();
        first.setPixel(3, 5, (byte) 9);
        var decodedAgain = new Pattern();
        decodedAgain.copyFrom(first);
        var before = new ArtSnapshot(new Pattern[]{first, null}, 7);
        var replay = new ArtSnapshot(new Pattern[]{decodedAgain, null}, 7);
        assertTrue(RewindSnapshotDiff.diffKey("art", before, replay).isEmpty());
        assertFalse(RewindSnapshotDiff.diffKey("art", before,
                new ArtSnapshot(new Pattern[]{decodedAgain, null}, 8)).isEmpty());
    }

    @Test
    void changedPixelsMissingTilesAndArrayLengthRemainVisible() {
        var first = new Pattern();
        var changed = new Pattern();
        changed.setPixel(7, 7, (byte) 15);
        var differences = RewindSnapshotDiff.diffKey("art", new ArtSnapshot(new Pattern[]{first}, 7),
                new ArtSnapshot(new Pattern[]{changed}, 7));
        assertEquals(1, differences.size());
        assertTrue(differences.getFirst().contains("patterns[0].pixels[63]"), differences.toString());
        assertFalse(RewindSnapshotDiff.diffKey("art", new ArtSnapshot(new Pattern[]{first}, 7),
                new ArtSnapshot(new Pattern[]{null}, 7)).isEmpty());
        assertFalse(RewindSnapshotDiff.diffKey("art", new ArtSnapshot(new Pattern[]{first}, 7),
                new ArtSnapshot(new Pattern[0], 7)).isEmpty());
    }
}
