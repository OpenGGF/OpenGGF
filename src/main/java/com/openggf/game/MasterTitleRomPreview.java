package com.openggf.game;

import com.openggf.data.Rom;
import com.openggf.game.preview.TitlePreviewProvider;
import com.openggf.game.preview.TitlePreviewProvider.Image;
import com.openggf.game.preview.TitlePreviewProvider.Sequence;
import org.lwjgl.system.MemoryUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;

/** ROM-source lifetime and GL upload boundary for detached stock title previews. */
final class MasterTitleRomPreview {
    private static final Logger LOGGER = Logger.getLogger(MasterTitleRomPreview.class.getName());

    private MasterTitleRomPreview() { }

    static Optional<Image> loadFor(MasterTitleScreen.GameEntry entry, Path romPath) {
        return loadSequenceFor(entry, romPath).map(sequence -> sequence.imageAt(sequence.stillTick()));
    }

    static Optional<Sequence> loadSequenceFor(MasterTitleScreen.GameEntry entry, Path romPath) {
        if (entry == null || romPath == null || !Files.isRegularFile(romPath)) return Optional.empty();
        try (Rom rom = new Rom()) {
            if (!rom.open(romPath.toString())) return Optional.empty();
            return loadSequenceFor(entry, rom);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to build master-title ROM preview for " + entry.gameId, e);
            return Optional.empty();
        }
    }

    static Optional<Sequence> loadSequenceFor(MasterTitleScreen.GameEntry entry, Rom rom) {
        return loadSequenceFor(entry, rom, GameModuleRegistry::createStockTitlePreview);
    }

    /** The resolver belongs to menu composition, independently of the live gameplay module. */
    static Optional<Sequence> loadSequenceFor(MasterTitleScreen.GameEntry entry, Rom rom,
                                             Function<GameId, TitlePreviewProvider> providers) {
        if (entry == null || rom == null || !rom.isOpen()) return Optional.empty();
        try {
            return providers.apply(GameId.fromCode(entry.gameId)).load(rom)
                    .filter(sequence -> sequence.imageAt(sequence.stillTick()).hasVisiblePixels());
        } catch (RuntimeException | IOException e) {
            LOGGER.log(Level.WARNING, "Failed to build master-title ROM preview for " + entry.gameId, e);
            return Optional.empty();
        }
    }

    static int uploadTexture(Image image) {
        ByteBuffer pixels = MemoryUtil.memAlloc(image.rgba().length);
        try {
            putFlippedRows(pixels, image);
            pixels.flip();

            int texId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, image.width(), image.height(),
                    0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glBindTexture(GL_TEXTURE_2D, 0);
            return texId;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    static void updateTexture(int textureId, Image image) {
        ByteBuffer pixels = MemoryUtil.memAlloc(image.rgba().length);
        try {
            putFlippedRows(pixels, image);
            pixels.flip();

            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, image.width(), image.height(),
                    GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glBindTexture(GL_TEXTURE_2D, 0);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static void putFlippedRows(ByteBuffer pixels, Image image) {
        // TexturedQuadRenderer uses OpenGL texture coordinates; mirror the PNG
        // loader by uploading rows bottom-to-top.
        int stride = image.width() * 4;
        for (int y = image.height() - 1; y >= 0; y--) {
            pixels.put(image.rgba(), y * stride, stride);
        }
    }
}
