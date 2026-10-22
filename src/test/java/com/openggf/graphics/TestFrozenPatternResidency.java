package com.openggf.graphics;

import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Physical bytes, rather than a virtual-ID version cache, own frozen art residency. */
class TestFrozenPatternResidency {
    private final PatternAtlas atlas = new PatternAtlas(16, 8);
    private final TestPatternAtlasDirtyUploads.RecordingSink sink = new TestPatternAtlasDirtyUploads.RecordingSink();
    private static SpritePresentation.PatternVersion version(int value) {
        byte[] pixels = new byte[64]; java.util.Arrays.fill(pixels, (byte) value);
        return SpritePresentation.patternVersion(pixels);
    }
    private void resident() {
        atlas.cachePatternHeadless(null, 0);
        atlas.beginBatch(); atlas.endBatch();
        atlas.assignHeadlessTextureIdsForTesting(); atlas.setUploadSink(sink);
    }
    @Test void unchangedSkipsAndChangedAndABAUploadExactPixels() {
        resident();
        atlas.restoreFrozenPattern(version(1), 0, false);
        atlas.restoreFrozenPattern(version(1), 0, false);
        assertEquals(1, sink.uploads.size());
        assertEquals(version(1), atlas.scenePatternSample(0));
        atlas.restoreFrozenPattern(version(2), 0, false);
        atlas.restoreFrozenPattern(version(1), 0, false);
        assertEquals(3, sink.uploads.size());
        for (int i = 0; i < 3; i++) {
            byte[] expected = new byte[64]; java.util.Arrays.fill(expected, (byte) (i == 1 ? 2 : 1));
            assertArrayEquals(expected, sink.uploads.get(i).pixels());
        }
        atlas.cleanupHeadless();
    }
    @Test void sampleOnlyHeadlessPublicationDoesNotCertifyPhysicalBytes() {
        atlas.restoreFrozenPattern(version(7), 0, true);
        resident();
        atlas.restoreFrozenPattern(version(7), 0, false);
        assertEquals(1, sink.uploads.size());
        atlas.cleanupHeadless();
    }
    @Test void aliasesAndReusedSlotsReadCurrentPhysicalOccupant() {
        resident();
        atlas.restoreFrozenPattern(version(1), 0, false);
        assertTrue(atlas.aliasEntry(1, 0));
        atlas.restoreFrozenPattern(version(2), 1, false);
        atlas.restoreFrozenPattern(version(1), 0, false);
        assertEquals(3, sink.uploads.size(), "alias writes invalidate physical residency without an ID cache");
        int slot = atlas.getEntry(0).slot();
        atlas.removeEntry(0); atlas.removeEntry(1);
        atlas.cachePatternHeadless(null, 2);
        assertEquals(slot, atlas.getEntry(2).slot());
        atlas.restoreFrozenPattern(version(1), 2, false);
        assertEquals(3, sink.uploads.size(), "equal bytes in a reused slot need no upload");
        atlas.restoreFrozenPattern(version(3), 2, false);
        assertEquals(4, sink.uploads.size());
        atlas.cleanupHeadless();
    }
    @Test void fullPageDirtySurvivesEqualRestoreAndNestedBatch() {
        resident(); atlas.restoreFrozenPattern(version(1), 0, false); sink.reset();
        atlas.markPageFullyDirty(0);
        atlas.beginBatch(); atlas.beginBatch();
        atlas.restoreFrozenPattern(version(1), 0, false);
        atlas.endBatch(); assertTrue(sink.uploads.isEmpty());
        atlas.endBatch();
        assertEquals(1, sink.uploads.size()); assertEquals(16 * 8, sink.bytesUploaded);
        atlas.cleanupHeadless();
    }
    @Test void equalRestoreRetriesFailedUpload() {
        resident();
        atlas.setUploadSink(new PatternAtlas.AtlasUploadSink() {
            public void begin(int texture) { }
            public void upload(byte[] bytes, int offset, int stride, int x, int y, int width, int height) {
                throw new IllegalStateException("upload failed");
            }
            public void end() { }
        });
        assertThrows(IllegalStateException.class, () -> atlas.restoreFrozenPattern(version(9), 0, false));
        atlas.setUploadSink(sink);
        atlas.restoreFrozenPattern(version(9), 0, false);
        assertEquals(1, sink.uploads.size());
        atlas.restoreFrozenPattern(version(9), 0, false);
        assertEquals(1, sink.uploads.size());
        atlas.cleanupHeadless();
    }
    @Test void ordinaryWritesRemainImmediateAndCleanupDropsPhysicalAuthority() {
        resident();
        var pattern = new Pattern(); pattern.setPixel(0, 0, (byte) 17);
        // Ordinary uploads retain byte values; nibble equality alone must not skip frozen replay.
        atlas.uploadPattern(pattern, atlas.getEntry(0));
        atlas.uploadPattern(pattern, atlas.getEntry(0));
        atlas.restoreFrozenPattern(new SpritePresentation.PatternVersion(1, 0, 0, 0), 0, false);
        assertEquals(3, sink.uploads.size()); assertEquals(1, sink.uploads.getLast().pixels()[0]);
        atlas.cleanupHeadless(); resident();
        atlas.restoreFrozenPattern(new SpritePresentation.PatternVersion(1, 0, 0, 0), 0, false);
        assertEquals(4, sink.uploads.size());
        atlas.cleanupHeadless();
    }
}
