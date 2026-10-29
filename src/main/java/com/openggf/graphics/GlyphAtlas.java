package com.openggf.graphics;

import com.openggf.graphics.text.GlyphBitmap;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * On-demand GL pages for rasterized host-text glyphs (fallback bitmap fonts and shaped
 * OpenType glyphs). Glyphs are packed in shelves with a one-texel gutter and uploaded as
 * white with coverage in alpha. A fixed page budget bounds memory: when it is exhausted the
 * owner flushes pending quads, {@link #reset()}s the pages and re-uploads what it draws next.
 * Render-thread confined.
 */
final class GlyphAtlas {
    static final int PAGE_SIZE = 256;
    static final int MAX_PAGES = 8;

    /** GL seam so geometry can be tested without a context. */
    interface Pages {
        int create(int width, int height);
        void upload(int texture, int x, int y, int width, int height, byte[] rgba);
        void delete(int texture);
    }

    /** Texel rectangle of an uploaded glyph. */
    record Slot(int texture, int x, int y, int width, int height) { }

    private final Pages pages;
    private final List<Integer> textures = new ArrayList<>();
    private final Map<Long, Slot> slots = new HashMap<>();
    private int shelfX;
    private int shelfY;
    private int shelfHeight;

    GlyphAtlas(Pages pages) {
        this.pages = pages;
    }

    /** The glyph's slot, uploading it if needed; null when the page budget is exhausted. */
    Slot slot(long key, GlyphBitmap bitmap) {
        Slot known = slots.get(key);
        if (known != null) return known;
        int width = bitmap.width();
        int height = bitmap.height();
        if (width + 1 > PAGE_SIZE || height + 1 > PAGE_SIZE) throw new IllegalArgumentException("glyph larger than an atlas page");
        if (textures.isEmpty() || shelfX + width + 1 > PAGE_SIZE) {
            shelfX = 0;
            shelfY += shelfHeight;
            shelfHeight = 0;
        }
        if (textures.isEmpty() || shelfY + height + 1 > PAGE_SIZE) {
            if (textures.size() == MAX_PAGES) return null;
            textures.add(pages.create(PAGE_SIZE, PAGE_SIZE));
            shelfX = 0;
            shelfY = 0;
            shelfHeight = 0;
        }
        int texture = textures.getLast();
        byte[] rgba = new byte[width * height * 4];
        for (int i = 0; i < width * height; i++) {
            rgba[i * 4] = rgba[i * 4 + 1] = rgba[i * 4 + 2] = (byte) 255;
            rgba[i * 4 + 3] = bitmap.alpha()[i];
        }
        pages.upload(texture, shelfX, shelfY, width, height, rgba);
        Slot slot = new Slot(texture, shelfX, shelfY, width, height);
        slots.put(key, slot);
        shelfX += width + 1;
        shelfHeight = Math.max(shelfHeight, height + 1);
        return slot;
    }

    /** Drops every page; callers flush first because queued quads sample these textures. */
    void reset() {
        textures.forEach(pages::delete);
        textures.clear();
        slots.clear();
        shelfX = shelfY = shelfHeight = 0;
    }

    int pageCount() {
        return textures.size();
    }

    /** OpenGL pages; rows are uploaded top-down, matching the texture coordinates MenuPixelFont writes. */
    static final Pages GL = new Pages() {
        @Override
        public int create(int width, int height) {
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            // Clear to transparent so gutters never sample stale memory.
            ByteBuffer clear = MemoryUtil.memCalloc(width * height * 4);
            try {
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, clear);
            } finally {
                MemoryUtil.memFree(clear);
                glBindTexture(GL_TEXTURE_2D, 0);
            }
            return texture;
        }

        @Override
        public void upload(int texture, int x, int y, int width, int height, byte[] rgba) {
            ByteBuffer pixels = MemoryUtil.memAlloc(rgba.length);
            try {
                pixels.put(rgba).flip();
                glBindTexture(GL_TEXTURE_2D, texture);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            } finally {
                glBindTexture(GL_TEXTURE_2D, 0);
                MemoryUtil.memFree(pixels);
            }
        }

        @Override
        public void delete(int texture) {
            PngTextureLoader.deleteTexture(texture);
        }
    };
}
