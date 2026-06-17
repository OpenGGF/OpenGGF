package com.openggf.level.rewind;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.LevelManager;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * No-ROM regression guard for the pattern-overlay rewind contract (Fix D).
 *
 * <p>{@code Sonic3kLevel.applyPatternOverlay} (e.g. AIZ2 battleship terrain at
 * tile 0x1FC and the fire overlay at tile 0x500) overwrites live pattern pixels
 * and the GL atlas. Before the fix it mutated the shared {@code patterns[]}
 * entries in place, so a rewind that crossed an overlay left the overlay pixels
 * behind because {@link LevelSnapshot} excluded pattern bytes entirely.
 *
 * <p>The fix mirrors the chunk/block overlay copy-on-write: the overlay clones
 * the patterns array and installs a fresh {@link Pattern} for each touched slot,
 * the snapshot captures the patterns array by reference, and restore swaps the
 * captured array back. This test drives that contract through the real
 * {@link LevelRewindSnapshotAdapter} without a ROM by emulating the overlay's
 * array clone on a stub level.
 */
class TestSonic3kPatternOverlayRewind {

    private static final int OVERLAY_TILE = 1;

    @Test
    void rewindRestoresPreOverlayPatternBytes() {
        StubLevel level = new StubLevel();
        // Pre-overlay pixels for the tile the overlay will later clobber.
        byte[] preOverlayBytes = patternBytesFilled((byte) 0x03);
        level.patternsReference()[OVERLAY_TILE].fromSegaFormat(preOverlayBytes);
        byte[] expectedPreOverlay = memBytes(level.patternsReference()[OVERLAY_TILE]);

        LevelManager manager = mock(LevelManager.class);
        when(manager.getCurrentLevel()).thenReturn(level);

        RewindSnapshottable<LevelSnapshot> adapter = LevelRewindSnapshotAdapter.create(manager);

        // Keyframe captured before the overlay shares the live patterns array.
        LevelSnapshot keyframe = adapter.capture();
        Pattern[] capturedArray = keyframe.patterns();
        assertSame(level.patternsReference(), capturedArray,
                "snapshot must capture the patterns array by reference");

        // Emulate applyPatternOverlay's copy-on-write: cloned array + a fresh
        // Pattern instance carrying the overlay pixels for the touched slot.
        Pattern[] overlaidArray = level.patternsReference().clone();
        Pattern overlaid = new Pattern();
        overlaid.fromSegaFormat(patternBytesFilled((byte) 0x11));
        overlaidArray[OVERLAY_TILE] = overlaid;
        level.replacePatterns(overlaidArray);

        byte[] afterOverlay = memBytes(level.patternsReference()[OVERLAY_TILE]);
        assertFalse(java.util.Arrays.equals(expectedPreOverlay, afterOverlay),
                "live level must show the overlaid pattern pixels before rewind");
        // The captured keyframe array must be untouched by the overlay.
        assertArrayEquals(expectedPreOverlay, memBytes(capturedArray[OVERLAY_TILE]),
                "captured keyframe pattern must keep its pre-overlay pixels");

        // Rewind to the captured keyframe.
        adapter.restore(keyframe);

        assertSame(capturedArray, level.patternsReference(),
                "restore must swap the captured patterns array back in");
        assertArrayEquals(expectedPreOverlay, memBytes(level.patternsReference()[OVERLAY_TILE]),
                "rewind must restore the pre-overlay pattern pixels");
    }

    @Test
    void captureSharesPatternArrayWhenLevelUnchanged() {
        StubLevel level = new StubLevel();
        LevelManager manager = mock(LevelManager.class);
        when(manager.getCurrentLevel()).thenReturn(level);

        RewindSnapshottable<LevelSnapshot> adapter = LevelRewindSnapshotAdapter.create(manager);
        LevelSnapshot first = adapter.capture();
        LevelSnapshot second = adapter.capture();

        assertSame(first.patterns(), second.patterns(),
                "captures with no overlay between them must share the patterns array");
        assertTrue(first.patterns().length > OVERLAY_TILE);
    }

    private static byte[] patternBytesFilled(byte value) {
        byte[] bytes = new byte[Pattern.PATTERN_SIZE_IN_ROM];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }

    private static byte[] memBytes(Pattern pattern) {
        byte[] bytes = new byte[Pattern.PATTERN_SIZE_IN_MEM];
        pattern.copyInto(bytes, 0);
        return bytes;
    }

    static class StubLevel extends AbstractLevel {
        StubLevel() {
            super(0);
            this.palettes = new Palette[PALETTE_COUNT];
            this.patterns = new Pattern[16];
            this.chunks = new Chunk[16];
            this.blocks = new Block[16];
            for (int i = 0; i < 16; i++) {
                this.patterns[i] = new Pattern();
                this.chunks[i] = new Chunk();
                this.blocks[i] = new Block();
            }
            this.solidTiles = new SolidTile[16];
            this.map = new Map(2, 256, 256);
            this.objects = new ArrayList<>();
            this.rings = new ArrayList<>();
            this.patternCount = 16;
            this.chunkCount = 16;
            this.blockCount = 16;
            this.solidTileCount = 16;
            this.minX = 0;
            this.maxX = 1024;
            this.minY = 0;
            this.maxY = 1024;
        }
    }
}
