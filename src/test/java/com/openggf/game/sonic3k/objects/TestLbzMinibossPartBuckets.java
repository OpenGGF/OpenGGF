package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ROM child slots owned inline by the LBZ1 miniboss objects carry their own
 * {@code priority} words, so the owners report them through
 * {@code MultiBucketRenderable} instead of drawing every part in the owner's bucket.
 */
class TestLbzMinibossPartBuckets {

    private static final int PIECE_BUCKET = RenderPriority.fromS3kWord(0x100);
    private static final int LINGER_BUCKET = RenderPriority.fromS3kWord(0x380);

    /**
     * ROM loc_8CF10: a drifting box piece writes priority $380 when its late
     * Animate_Raw script ends (sonic3k.asm:192502-192505); the six burst pieces
     * delete instead (off_8D1AC, sonic3k.asm:192733-192739).
     */
    @Test
    void driftingBoxPieceMovesToTheBackBucketWhenItsLateAnimationEnds() {
        LbzMinibossBoxRig rig = new LbzMinibossBoxRig(0x1000, 0x400);
        for (int i = 0; i < 10; i++) {
            assertEquals(PIECE_BUCKET, rig.pieceBucketForTest(i), "ObjDat3_8D23C $100 at loc_8CE64");
        }
        assertEquals(0, LbzMinibossBoxRig.extraRenderBuckets(PIECE_BUCKET, rig).length);

        rig.release();
        int transitionFrame = -1;
        for (int frame = 0; frame < 0x400 && transitionFrame < 0; frame++) {
            rig.update(LbzMinibossBoxRig.NO_CAMERA);
            for (int i = 0; i < 6; i++) {
                assertEquals(PIECE_BUCKET, rig.pieceBucketForTest(i),
                        "burst piece " + i + " never reaches loc_8CF10");
            }
            if (rig.pieceBucketForTest(6) == LINGER_BUCKET) {
                transitionFrame = frame;
            }
        }
        assertTrue(transitionFrame > 0, "piece 6 reached the loc_8CF10 $380 write");
        // Rig ticks from release to the $380 write: the word_8D170 $40 release delay expires
        // on its ($40+1)th tick, the zero-delay raw script {0} shows its frame then completes
        // on the next tick, the loc_8CEAE $5F drift expires on its ($5F+1)th tick, and the
        // 5-delay late script {0,1,2,$F4} takes three (5+1)-tick advances before the callback.
        int expectedTicks = (0x40 + 1) + 2 + (0x5F + 1) + 3 * (5 + 1);
        assertEquals(expectedTicks, transitionFrame + 1, "ticks from release to the $380 write");
        assertArrayEquals(new int[] {LINGER_BUCKET},
                LbzMinibossBoxRig.extraRenderBuckets(PIECE_BUCKET, rig));

        // Bucket-filtered draw: only lingering pieces go to the back bucket.
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        rig.draw(renderer, 0, LINGER_BUCKET);
        ArgumentCaptor<Integer> frames = ArgumentCaptor.forClass(Integer.class);
        verify(renderer).drawFrameIndex(frames.capture(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyInt());
        assertEquals(List.of(2), frames.getAllValues(), "piece 6 shows its final late frame");

        // Rewind: the bucket is derived from the restored phase, so a fresh rig restored
        // from this snapshot draws piece 6 in the back bucket as well.
        LbzMinibossBoxRig restored = new LbzMinibossBoxRig(0, 0);
        restored.restoreRewindStateValue(rig.captureRewindStateValue());
        assertEquals(LINGER_BUCKET, restored.pieceBucketForTest(6));
        assertEquals(PIECE_BUCKET, restored.pieceBucketForTest(7), "piece 7 is still drifting");
        assertArrayEquals(new int[] {LINGER_BUCKET},
                LbzMinibossBoxRig.extraRenderBuckets(PIECE_BUCKET, restored));
    }

    @Test
    void boxRigDrawsNothingForABucketNoPieceOccupies() {
        LbzMinibossBoxRig rig = new LbzMinibossBoxRig(0x1000, 0x400);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        rig.draw(renderer, 0, LINGER_BUCKET);
        verify(renderer, never()).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyInt());
    }

    /**
     * ROM word_727E2 ($300/$380/$300/$380/$300/$280) written per arm subtype at
     * loc_727B0 (sonic3k.asm:151737, 151749-151750) and word_72962 $200 for the
     * centre child (sonic3k.asm:151906); every child copies the body's art_tile
     * bit 15 through CreateChild1_Normal / CreateChild4_LinkListRepeated.
     */
    @Test
    void minibossPanelsReportTheirOwnRomBuckets() {
        LbzMinibossInstance boss = new LbzMinibossInstance(
                new ObjectSpawn(0x1000, 0x400, 0x9A, 0, 0, false, 0));
        boss.forceOpenForTest(0x1000, 0x400);

        assertEquals(RenderPriority.fromS3kWord(0x280), boss.getPriorityBucket());
        assertEquals(RenderPriority.fromS3kWord(0x200), boss.getCenterPriorityBucketForTest());
        int[] expected = {0x300, 0x380, 0x300, 0x380, 0x300, 0x280};
        for (int link = 0; link < expected.length; link++) {
            int bucket = RenderPriority.fromS3kWord(expected[link]);
            assertEquals(bucket, boss.getPanelPriorityBucketForTest(link, false), "ring A link " + link);
            assertEquals(bucket, boss.getPanelPriorityBucketForTest(link, true), "ring B link " + link);
        }

        int[] extra = boss.extraRenderBuckets();
        Arrays.sort(extra);
        assertArrayEquals(new int[] {
                RenderPriority.fromS3kWord(0x200),
                RenderPriority.fromS3kWord(0x300),
                RenderPriority.fromS3kWord(0x380)}, extra,
                "the $280 link shares the body's bucket and is not an extra");
        assertTrue(boss.isHighPriority());
        for (int bucket : extra) {
            assertTrue(boss.isHighPriority(bucket), "children inherit the body's art_tile bit 15");
        }
        assertFalse(Arrays.stream(extra).anyMatch(b -> b == boss.getPriorityBucket()));
    }
}
