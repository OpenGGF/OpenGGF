package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link RenderPriority} literal transcription: S1/S2 bytes pass through
 * {@link RenderPriority#bucket(int)}, S3K words divide by $80 through
 * {@link RenderPriority#fromS3kWord(int)}, and a raw S3K word handed to
 * {@code bucket} is an error rather than silently bucket 7.
 */
class TestRenderPriority {

    @Test
    void bucketAcceptsTheEightRomDisplayLists() {
        for (int bucket = 0; bucket <= 7; bucket++) {
            assertEquals(bucket, RenderPriority.bucket(bucket));
        }
    }

    @Test
    void bucketRejectsRawS3kWordsInsteadOfClampingThem() {
        assertThrows(IllegalArgumentException.class, () -> RenderPriority.bucket(0x80));
        assertThrows(IllegalArgumentException.class, () -> RenderPriority.bucket(0x280));
        assertThrows(IllegalArgumentException.class, () -> RenderPriority.bucket(-1));
        assertThrows(IllegalArgumentException.class, () -> RenderPriority.bucket(8));
    }

    @Test
    void fromS3kWordDividesTheDisplayListOffsetByItsWidth() {
        // Draw_Sprite: adda.w priority(a0),a1 over $80-byte lists (sonic3k.asm Draw_Sprite).
        assertEquals(0, RenderPriority.fromS3kWord(0x000));
        assertEquals(1, RenderPriority.fromS3kWord(0x080));
        assertEquals(2, RenderPriority.fromS3kWord(0x100));
        assertEquals(5, RenderPriority.fromS3kWord(0x280));
        assertEquals(7, RenderPriority.fromS3kWord(0x380));
    }

    @Test
    void fromS3kWordRejectsBucketIndicesAndUnalignedWords() {
        assertThrows(IllegalArgumentException.class, () -> RenderPriority.fromS3kWord(5));
        assertThrows(IllegalArgumentException.class, () -> RenderPriority.fromS3kWord(0x2C0));
        assertThrows(IllegalArgumentException.class, () -> RenderPriority.fromS3kWord(0x400));
        assertThrows(IllegalArgumentException.class, () -> RenderPriority.fromS3kWord(-0x80));
    }

    @Test
    void clampStillFoldsRuntimeValuesForTheRenderPath() {
        assertEquals(7, RenderPriority.clamp(0x280));
        assertEquals(0, RenderPriority.clamp(-3));
        assertEquals(4, RenderPriority.clamp(4));
    }
}
