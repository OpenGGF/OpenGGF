package com.openggf.mods.scene.host;

import com.openggf.mods.scene.SceneImage;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The scene renderer's GPU textures, one per {@link SceneImage}: uploaded the first time an
 * image is drawn, kept while frames keep drawing it, and deleted once it has gone
 * {@link #IDLE_FRAMES} presented frames without being drawn. Forgetting the image as well lets
 * the garbage collector take images a scene has dropped, so a scene that stays open for a long
 * session (or builds images as it goes) does not pile up textures until it closes. Drawing an
 * evicted image again simply uploads it again. Engine-internal; GL calls go through {@link Gpu}
 * so tests can count them without a context.
 */
final class SceneTextureCache {
    /** About two seconds at 60 frames per second. */
    static final int IDLE_FRAMES = 120;

    /** Creates and deletes GL textures. */
    interface Gpu {
        int upload(SceneImage image);

        void delete(int texture);
    }

    private static final class Entry {
        private final int texture;
        private long lastFrame;

        private Entry(int texture, long lastFrame) {
            this.texture = texture;
            this.lastFrame = lastFrame;
        }
    }

    private final Gpu gpu;
    private final Map<SceneImage, Entry> textures = new IdentityHashMap<>();
    private long frame;

    SceneTextureCache(Gpu gpu) {
        this.gpu = gpu;
    }

    /** The image's texture, uploading it if this cache has none; marks it drawn this frame. */
    int texture(SceneImage image) {
        Entry entry = textures.get(image);
        if (entry == null) {
            entry = new Entry(gpu.upload(image), frame);
            textures.put(image, entry);
        } else {
            entry.lastFrame = frame;
        }
        return entry.texture;
    }

    /** Ends a presented frame, deleting textures of images not drawn for {@link #IDLE_FRAMES} frames. */
    void endFrame() {
        frame++;
        for (Iterator<Entry> it = textures.values().iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (frame - entry.lastFrame > IDLE_FRAMES) {
                gpu.delete(entry.texture);
                it.remove();
            }
        }
    }

    /** Deletes every texture (the scene closed). */
    void releaseAll() {
        for (Entry entry : textures.values()) {
            gpu.delete(entry.texture);
        }
        textures.clear();
    }

    /** How many images currently hold a texture. */
    int size() {
        return textures.size();
    }
}
