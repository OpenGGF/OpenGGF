package com.openggf.mods.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Scene textures are uploaded once per image, evicted when idle, and re-uploaded on the next draw. */
class TestSceneTextureCache {
    private final List<Integer> deleted = new ArrayList<>();
    private int uploads;
    private final SceneTextureCache cache = new SceneTextureCache(new SceneTextureCache.Gpu() {
        @Override
        public int upload(SceneImage image) {
            return ++uploads;
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
}
