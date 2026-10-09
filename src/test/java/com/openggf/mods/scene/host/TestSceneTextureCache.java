package com.openggf.mods.scene.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.openggf.mods.scene.SceneImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Scene textures are uploaded once per image, evicted when idle, and re-uploaded on the next draw. */
class TestSceneTextureCache {
    private final List<Integer> deleted = new ArrayList<>();
    private int uploads;
    private final List<Integer> reuploaded = new ArrayList<>();
    private final SceneTextureCache cache = new SceneTextureCache(new SceneTextureCache.Gpu() {
        @Override
        public int upload(SceneImage image) {
            return ++uploads;
        }

        @Override
        public void reupload(int texture, SceneImage image) {
            reuploaded.add(texture);
        }

        @Override
        public void delete(int texture) {
            deleted.add(texture);
        }
    });

    private static SceneImage image() {
        return new SceneImage(1, 1, new int[] {0xFFFFFFFF});
    }

    @Test
    void anImageDrawnEveryFrameKeepsItsOneTexture() {
        SceneImage kept = image();
        int texture = cache.texture(kept);
        for (int frame = 0; frame < 1000; frame++) {
            assertEquals(texture, cache.texture(kept));
            cache.endFrame();
        }
        assertEquals(1, uploads);
        assertEquals(List.of(), deleted);
    }

    @Test
    void anImageNoLongerDrawnIsDeletedAfterTheIdleFramesAndReuploadedWhenDrawnAgain() {
        SceneImage dropped = image();
        SceneImage kept = image();
        int first = cache.texture(dropped);
        for (int frame = 0; frame < SceneTextureCache.IDLE_FRAMES; frame++) {
            cache.texture(kept);
            cache.endFrame();
        }
        assertEquals(List.of(), deleted, "still inside the idle window");
        cache.texture(kept);
        cache.endFrame();
        assertEquals(List.of(first), deleted, "idle for more than IDLE_FRAMES frames");
        assertEquals(1, cache.size(), "the dropped image is forgotten, so it can be collected");

        int again = cache.texture(dropped);
        assertNotEquals(first, again, "drawing it again uploads a new texture");
        assertEquals(3, uploads);
    }

    @Test
    void closingTheSceneDeletesEveryTexture() {
        int a = cache.texture(image());
        int b = cache.texture(image());
        cache.releaseAll();
        assertEquals(List.of(a, b), deleted.stream().sorted().toList());
        assertEquals(0, cache.size());
    }

    @Test
    void aStreamingImageRefreshesItsOneTextureOnlyAfterAnUpdate() {
        SceneImage frame = SceneImage.streaming(2, 1);
        int texture = cache.texture(frame);
        cache.texture(frame);
        assertEquals(List.of(), reuploaded, "unchanged pixels are not uploaded again");
        frame.update(new int[] {0xFF00FF00, 0xFFFF0000});
        assertEquals(texture, cache.texture(frame));
        assertEquals(texture, cache.texture(frame));
        assertEquals(List.of(texture), reuploaded, "one refresh per update, into the same texture");
        assertEquals(1, uploads);
        assertEquals(0xFFFF0000, frame.pixel(1, 0));
    }

    @Test
    void ordinaryImagesRejectUpdates() {
        SceneImage still = image();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> still.update(new int[] {0}));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SceneImage.streaming(2, 2).update(new int[] {0}));
    }
}
