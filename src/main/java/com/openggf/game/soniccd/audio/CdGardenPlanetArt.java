package com.openggf.game.soniccd.audio;

import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.device.CdStampGeometry;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.level.Palette;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.memCalloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/** Static Present stamp art from the authenticated USA disc, owned by one Garden visit. */
final class CdGardenPlanetArt {
    private static final Logger LOGGER = Logger.getLogger(CdGardenPlanetArt.class.getName());
    // InitPlanetData (DA Garden/Sub.asm:265-288) places the static art before the animation bank.
    private static final int ART_BASE = 0x200;
    private static final int STATIC_ART_LIMIT = 0x6E00 - ART_BASE;
    // InitGfxOperation (DA Garden/Sub.asm:624-630) selects 32-pixel stamps.
    private static final int EDGE = 32, COLUMNS = 8, ROWS = 7;
    private static final int WIDTH = COLUMNS * EDGE, HEIGHT = ROWS * EDGE;
    private static final int STAMP_UNIT_BYTES = 16 * 16 / 2;
    private static final int STAMP_BYTES = EDGE * EDGE / 2;

    private final CdProgramAssets disc;
    private TexturedQuadRenderer renderer;
    private int texture;
    private boolean failed;

    CdGardenPlanetArt(CdProgramAssets disc) {
        this.disc = disc;
    }

    boolean prepare() {
        if (texture != 0) return true;
        if (failed) return false;
        try {
            load();
            return true;
        } catch (IOException e) {
            reset();
            failed = true;
            LOGGER.log(Level.WARNING, "Cannot load D.A. Garden Present art", e);
            return false;
        }
    }

    private void load() throws IOException {
        // Stamps_Present (DA Garden/Sub.asm:1290-1292) and StampMap_Present (DA Garden/Sub.asm:1302-1304).
        byte[] compressed = disc.read(CdNativeFile.DAGARDSUB, 0x0AD0, 0x3230 - 0x0AD0);
        byte[] art = KosinskiReader.decompress(Channels.newChannel(new ByteArrayInputStream(compressed)));
        if (art.length > STATIC_ART_LIMIT) throw new IOException("Present art exceeds the static stamp bank");
        var map = ByteBuffer.wrap(disc.read(CdNativeFile.DAGARDSUB, 0x8B10, ROWS * COLUMNS * Short.BYTES));
        // PalCyc_PlanetPresent (DA Garden/Data/Palette.asm:45-80), first palette (.Pal0).
        var palette = new Palette();
        palette.fromSegaFormat(disc.read(CdNativeFile.DAGARDMAIN, 0x2A8A, Palette.PALETTE_SIZE_IN_ROM));
        int[] rgba = new int[Palette.PALETTE_SIZE];
        for (int i = 1; i < rgba.length; i++) {
            var color = palette.colors[i];
            rgba[i] = ((color.r & 0xFF) << 24) | ((color.g & 0xFF) << 16) | ((color.b & 0xFF) << 8) | 0xFF;
        }

        ByteBuffer pixels = memCalloc(WIDTH * HEIGHT * Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        try {
            for (int row = 0; row < ROWS; row++) {
                for (int column = 0; column < COLUMNS; column++) {
                    int entry = Short.toUnsignedInt(map.getShort());
                    int stamp = CdStampGeometry.stampNumber(entry, EDGE);
                    if (stamp == 0) continue;
                    int start = stamp * STAMP_UNIT_BYTES - ART_BASE;
                    if (start < 0 || start + STAMP_BYTES > art.length) {
                        throw new IOException("Present map references a stamp outside the static bank");
                    }
                    drawStamp(pixels, row, column, art, start, entry >>> 13, rgba);
                }
            }
            renderer = new TexturedQuadRenderer();
            renderer.init();
            texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glBindTexture(GL_TEXTURE_2D, 0);
        } finally {
            memFree(pixels);
        }
    }

    private static void drawStamp(ByteBuffer pixels, int row, int column, byte[] art, int start,
                                  int orientation, int[] rgba) {
        for (int y = 0; y < EDGE; y++) {
            // The shared quad renderer maps the bottom of the image to texture row zero.
            int dst = ((HEIGHT - 1 - row * EDGE - y) * WIDTH + column * EDGE) * Integer.BYTES;
            for (int x = 0; x < EDGE; x++) {
                int nibble = CdStampGeometry.artNibble(EDGE, orientation, x, y);
                int pair = art[start + (nibble >>> 1)] & 0xFF;
                int index = (pair >>> ((nibble & 1) == 0 ? 4 : 0)) & 15;
                pixels.putInt(dst + x * Integer.BYTES, rgba[index]);
            }
        }
    }

    void draw(float[] projection, int x, int y, int width) {
        renderer.setProjectionMatrix(projection);
        renderer.drawTexture(texture, x, y, width, heightAtWidth(width));
    }

    static int heightAtWidth(int width) {
        return width * HEIGHT / WIDTH;
    }

    void reset() {
        if (texture != 0) glDeleteTextures(texture);
        if (renderer != null) renderer.cleanup();
        texture = 0;
        renderer = null;
        failed = false;
    }
}
