package com.openggf.game;

import com.openggf.data.Rom;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.titlescreen.Sonic1TitleScreenMappings;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.titlescreen.TitleScreenMappings;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.titlescreen.Sonic3kTitleScreenMappings;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.tools.EnigmaReader;
import com.openggf.util.PatternDecompressor;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
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

/**
 * Runtime-only preview art for the master title selector.
 *
 * <p>The preview is decoded from user-supplied ROM data into an in-memory RGBA
 * texture. It deliberately does not write extracted title art into resources.
 */
final class MasterTitleRomPreview {
    private static final Logger LOGGER = Logger.getLogger(MasterTitleRomPreview.class.getName());

    private static final int S1_PREVIEW_WIDTH_TILES = 40;
    private static final int S1_PREVIEW_HEIGHT_TILES = 28;
    private static final int S1_TITLE_WIDTH_TILES = 34;
    private static final int S1_TITLE_HEIGHT_TILES = 22;
    private static final int S2_TITLE_WIDTH_TILES = 40;
    private static final int S2_TITLE_HEIGHT_TILES = 28;
    private static final int S3K_TITLE_WIDTH_TILES = 40;
    private static final int S3K_TITLE_HEIGHT_TILES = 28;
    private static final int S1_PLANE_A_SPLIT_ROW = 9;
    private static final int S1_PLANE_A_PIXEL_X = 24;
    private static final int S1_PLANE_A_PIXEL_Y = 32;
    private static final int S1_PREVIEW_SONIC_FRAME = 6;
    private static final int S2_CHARACTER_PREVIEW_Y_OFFSET = 8;
    private static final boolean S2_PREVIEW_DRAWS_HANDS_BEHIND_LOGO_OCCLUSION = true;
    private static final boolean S2_PREVIEW_DRAWS_HANDS_BEHIND_FULL_LOGO_TEXT = false;
    private static final int S2_LOGO_OCCLUSION_BASE_Y = 13 * 8;
    private static final int S2_LOGO_OCCLUSION_CURVE_PIXELS = 16;
    private static final int S2_SCREEN_WIDTH = 320;
    private static final int S2_LOGO_OCCLUSION_HALF_WIDTH = 104;
    private static final int S3K_PREVIEW_BANNER_Y = 112;
    private static final boolean S3K_PREVIEW_DRAWS_MENU_SELECTION = false;

    private MasterTitleRomPreview() {
    }

    record Image(int width, int height, byte[] rgba) {
        Image {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Preview image dimensions must be positive");
            }
            if (rgba == null || rgba.length != width * height * 4) {
                throw new IllegalArgumentException("RGBA data must be width*height*4 bytes");
            }
        }

        boolean hasVisiblePixels() {
            for (int i = 3; i < rgba.length; i += 4) {
                if ((rgba[i] & 0xFF) != 0) {
                    return true;
                }
            }
            return false;
        }
    }

    static Optional<Image> loadFor(MasterTitleScreen.GameEntry entry, Path romPath) {
        if (entry == null || romPath == null || !Files.isRegularFile(romPath)) {
            return Optional.empty();
        }

        try (Rom rom = new Rom()) {
            if (!rom.open(romPath.toString())) {
                return Optional.empty();
            }
            Image image = switch (entry) {
                case SONIC_1 -> loadSonic1Preview(rom);
                case SONIC_2 -> loadSonic2Preview(rom);
                case SONIC_3K -> loadSonic3kPreview(rom);
            };
            return image != null && image.hasVisiblePixels() ? Optional.of(image) : Optional.empty();
        } catch (RuntimeException | IOException e) {
            LOGGER.log(Level.WARNING, "Failed to build master-title ROM preview for " + entry.gameId, e);
            return Optional.empty();
        }
    }

    static int uploadTexture(Image image) {
        ByteBuffer pixels = MemoryUtil.memAlloc(image.rgba.length);
        try {
            // TexturedQuadRenderer uses OpenGL texture coordinates; mirror the PNG
            // loader by uploading rows bottom-to-top.
            int stride = image.width * 4;
            for (int y = image.height - 1; y >= 0; y--) {
                pixels.put(image.rgba, y * stride, stride);
            }
            pixels.flip();

            int texId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, image.width, image.height,
                    0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glBindTexture(GL_TEXTURE_2D, 0);
            return texId;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    static int sonic1PreviewSonicFrameIndex() {
        return S1_PREVIEW_SONIC_FRAME;
    }

    static int sonic1PreviewWidth() {
        return S1_PREVIEW_WIDTH_TILES * Pattern.PATTERN_WIDTH;
    }

    static int sonic1PreviewHeight() {
        return S1_PREVIEW_HEIGHT_TILES * Pattern.PATTERN_HEIGHT;
    }

    static int sonic1PlaneAX() {
        return S1_PLANE_A_PIXEL_X;
    }

    static int sonic1PlaneAY() {
        return S1_PLANE_A_PIXEL_Y;
    }

    static int sonic2PreviewCharacterYOffset() {
        return S2_CHARACTER_PREVIEW_Y_OFFSET;
    }

    static boolean sonic2PreviewDrawsHandsBehindLogoOcclusion() {
        return S2_PREVIEW_DRAWS_HANDS_BEHIND_LOGO_OCCLUSION;
    }

    static boolean sonic2PreviewDrawsHandsBehindFullLogoText() {
        return S2_PREVIEW_DRAWS_HANDS_BEHIND_FULL_LOGO_TEXT;
    }

    static int sonic2LogoOcclusionStartPixel(int screenX) {
        double center = (S2_SCREEN_WIDTH - 1) * 0.5;
        double norm = Math.abs((screenX - center) / S2_LOGO_OCCLUSION_HALF_WIDTH);
        norm = Math.min(1.0, norm);
        int curveOffset = (int) Math.round(norm * norm * S2_LOGO_OCCLUSION_CURVE_PIXELS);
        return S2_LOGO_OCCLUSION_BASE_Y + curveOffset;
    }

    static boolean sonic3kPreviewDrawsMenuSelection() {
        return S3K_PREVIEW_DRAWS_MENU_SELECTION;
    }

    static int sonic3kPreviewBannerY() {
        return S3K_PREVIEW_BANNER_Y;
    }

    static Image composeTilemapImage(Pattern[] patterns,
                                     Palette[] palettes,
                                     int[] map,
                                     int widthTiles,
                                     int heightTiles) {
        return composeTilemapImage(patterns, palettes, map, widthTiles, heightTiles, 0);
    }

    static Image composeTilemapImage(Pattern[] patterns,
                                     Palette[] palettes,
                                     int[] map,
                                     int widthTiles,
                                     int heightTiles,
                                     int tileIndexBase) {
        int width = widthTiles * Pattern.PATTERN_WIDTH;
        int height = heightTiles * Pattern.PATTERN_HEIGHT;
        byte[] rgba = new byte[width * height * 4];

        if (patterns == null || palettes == null || map == null) {
            return new Image(width, height, rgba);
        }

        int tileCount = Math.min(map.length, widthTiles * heightTiles);
        for (int tile = 0; tile < tileCount; tile++) {
            int word = map[tile];
            int patternIndex = (word & 0x7FF) - tileIndexBase;
            if (patternIndex < 0 || patternIndex >= patterns.length) {
                continue;
            }
            Pattern pattern = patterns[patternIndex];
            if (pattern == null) {
                continue;
            }

            int paletteIndex = (word >> 13) & 0x03;
            Palette palette = paletteIndex < palettes.length ? palettes[paletteIndex] : null;
            if (palette == null) {
                continue;
            }

            boolean hFlip = (word & 0x0800) != 0;
            boolean vFlip = (word & 0x1000) != 0;
            int tileX = tile % widthTiles;
            int tileY = tile / widthTiles;
            blitPattern(rgba, width, tileX * 8, tileY * 8, pattern, palette, hFlip, vFlip);
        }

        return new Image(width, height, rgba);
    }

    static void overlaySpriteFrame(Image image,
                                   Pattern[] patterns,
                                   Palette[] palettes,
                                   SpriteMappingFrame frame,
                                   int originX,
                                   int originY,
                                   int tileIndexBase) {
        if (image == null || patterns == null || palettes == null || frame == null || frame.pieces() == null) {
            return;
        }
        for (int i = frame.pieces().size() - 1; i >= 0; i--) {
            overlaySpritePiece(image, patterns, palettes, frame.pieces().get(i), originX, originY, tileIndexBase);
        }
    }

    private static Image loadSonic1Preview(Rom rom) throws IOException {
        Pattern[] patterns = PatternDecompressor.nemesis(
                rom, Sonic1Constants.ART_NEM_TITLE_FG_ADDR, 8192, "MasterPreviewS1TitleFg");
        Pattern[] sonicPatterns = PatternDecompressor.nemesis(
                rom, Sonic1Constants.ART_NEM_TITLE_SONIC_ADDR, 65536, "MasterPreviewS1TitleSonic");
        Pattern[] tmPatterns = PatternDecompressor.nemesis(
                rom, Sonic1Constants.ART_NEM_TITLE_TM_ADDR, 1024, "MasterPreviewS1TitleTM");
        int[] map = loadEnigmaMap(rom, Sonic1Constants.MAP_ENI_TITLE_ADDR, 0, 1024);
        Palette[] palettes = loadPaletteLines(rom.readBytes(Sonic1Constants.PAL_TITLE_ADDR, 128));

        Image image = emptyTilemapImage(S1_PREVIEW_WIDTH_TILES, S1_PREVIEW_HEIGHT_TILES);
        overlayTilemapRows(image, patterns, palettes, map, S1_TITLE_WIDTH_TILES,
                0, S1_PLANE_A_SPLIT_ROW, Sonic1Constants.ARTTILE_TITLE_FOREGROUND,
                S1_PLANE_A_PIXEL_X, S1_PLANE_A_PIXEL_Y);
        SpriteMappingFrame sonic = Sonic1TitleScreenMappings.createFrames()
                .get(S1_PREVIEW_SONIC_FRAME);
        overlaySpriteFrame(image, sonicPatterns, new Palette[] { palettes[1] }, sonic,
                0xF0 - 128, 0x96 - 128, 0);
        overlayTilemapRows(image, patterns, palettes, map, S1_TITLE_WIDTH_TILES,
                S1_PLANE_A_SPLIT_ROW, S1_TITLE_HEIGHT_TILES, Sonic1Constants.ARTTILE_TITLE_FOREGROUND,
                S1_PLANE_A_PIXEL_X, S1_PLANE_A_PIXEL_Y);
        overlaySpriteFrame(image, tmPatterns, new Palette[] { palettes[1] },
                Sonic1TitleScreenMappings.createFrames().get(Sonic1TitleScreenMappings.FRAME_TM),
                0x170 - 128, 0xF8 - 128, 0);
        return image;
    }

    private static Image loadSonic2Preview(Rom rom) throws IOException {
        Pattern[] patterns = PatternDecompressor.nemesis(
                rom, Sonic2Constants.ART_NEM_TITLE_ADDR, 8192, "MasterPreviewS2Title");
        Pattern[] spritePatterns = PatternDecompressor.nemesis(
                rom, Sonic2Constants.ART_NEM_TITLE_SPRITES_ADDR, 65536, "MasterPreviewS2TitleSprites");
        Pattern[] menuJunkPatterns = PatternDecompressor.nemesis(
                rom, Sonic2Constants.ART_NEM_MENU_JUNK_ADDR, 1024, "MasterPreviewS2MenuJunk");
        spritePatterns = appendPatternsAt(spritePatterns, menuJunkPatterns, 0x2A2);
        int[] map = loadEnigmaMap(rom, Sonic2Constants.MAP_ENI_TITLE_LOGO_ADDR, 0xE000, 1024);
        Palette[] palettes = loadSonic2TitlePalettes(rom);
        Image image = composeTilemapImage(patterns, palettes, map, S2_TITLE_WIDTH_TILES, S2_TITLE_HEIGHT_TILES);
        Image logo = composeTilemapImage(patterns, palettes, map, S2_TITLE_WIDTH_TILES, S2_TITLE_HEIGHT_TILES);
        clearSonic2LogoChromaGreen(image);
        clearSonic2LogoChromaGreen(logo);
        var frames = TitleScreenMappings.createFrames();
        int yOffset = S2_CHARACTER_PREVIEW_Y_OFFSET;
        overlaySpriteFrame(image, spritePatterns, palettes, frames.get(0x12), 136, 24 + yOffset, 0);
        overlaySpriteFrame(image, spritePatterns, palettes, frames.get(4), 72, 32 + yOffset, 0);
        overlaySpriteFrame(image, spritePatterns, palettes, frames.get(9), 193, 65 + yOffset, 0);
        overlaySpriteFrame(image, spritePatterns, palettes, frames.get(0x13), 141, 81 + yOffset, 0);
        overlaySonic2CurvedLogoOcclusion(image, logo);
        return image;
    }

    private static Image loadSonic3kPreview(Rom rom) throws IOException {
        Pattern[] patterns = PatternDecompressor.kosinski(rom, Sonic3kConstants.ART_KOS_TITLE_SONIC_D_ADDR);
        Pattern[] spritePatterns = loadSonic3kSpritePatterns(rom);
        int[] map = loadEnigmaMap(rom, Sonic3kConstants.MAP_ENI_TITLE_SONIC_D_ADDR,
                0x8000, Sonic3kConstants.MAP_ENI_TITLE_READ_SIZE);
        Palette[] palettes = loadPaletteLines(rom.readBytes(
                Sonic3kConstants.PAL_TITLE_SONIC_D_ADDR,
                Sonic3kConstants.PAL_TITLE_SONIC_D_SIZE));
        Image image = composeTilemapImage(patterns, palettes, map, S3K_TITLE_WIDTH_TILES, S3K_TITLE_HEIGHT_TILES);

        int bannerY = sonic3kPreviewBannerY();
        overlaySpriteFrame(image, spritePatterns, palettes, Sonic3kTitleScreenMappings.createBannerFrames().get(0),
                0x120 - 128, bannerY, 0);
        overlaySpriteFrame(image, spritePatterns, palettes, Sonic3kTitleScreenMappings.createAndKnucklesFrames().get(0),
                0x120 - 128, bannerY + 0x5C, 0);
        if (sonic3kPreviewDrawsMenuSelection()) {
            overlaySpriteFrame(image, spritePatterns, palettes, Sonic3kTitleScreenMappings.createSelectionFrames().get(0),
                    0xF0 - 128, 0x140 - 128, 0);
        }
        overlaySpriteFrame(image, spritePatterns, palettes, Sonic3kTitleScreenMappings.createCopyrightFrame().get(0),
                0x158 - 128, 0x14C - 128, 0);
        return image;
    }

    private static int[] loadEnigmaMap(Rom rom, int address, int startingArtTile, int readSize) throws IOException {
        byte[] compressed = rom.readBytes(address, readSize);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             ReadableByteChannel channel = Channels.newChannel(bais)) {
            byte[] decompressed = EnigmaReader.decompress(channel, startingArtTile);
            int[] words = new int[decompressed.length / 2];
            ByteBuffer buf = ByteBuffer.wrap(decompressed);
            for (int i = 0; i < words.length; i++) {
                words[i] = buf.getShort() & 0xFFFF;
            }
            return words;
        }
    }

    private static Palette[] loadPaletteLines(byte[] data) {
        int lines = data.length / Palette.PALETTE_SIZE_IN_ROM;
        Palette[] palettes = new Palette[lines];
        for (int line = 0; line < lines; line++) {
            palettes[line] = loadPalette(Arrays.copyOfRange(data,
                    line * Palette.PALETTE_SIZE_IN_ROM,
                    (line + 1) * Palette.PALETTE_SIZE_IN_ROM));
        }
        return palettes;
    }

    private static Palette loadPalette(byte[] data) {
        Palette palette = new Palette();
        palette.fromSegaFormat(data);
        return palette;
    }

    private static Palette[] loadSonic2TitlePalettes(Rom rom) throws IOException {
        Palette[] palettes = new Palette[4];
        palettes[0] = loadPalette(rom.readBytes(
                Sonic2Constants.PAL_TITLE_SONIC_ADDR,
                Sonic2Constants.PAL_TITLE_SONIC_SIZE));
        palettes[1] = loadPalette(rom.readBytes(
                Sonic2Constants.PAL_TITLE_ADDR,
                Sonic2Constants.PAL_TITLE_SIZE));
        palettes[2] = loadPalette(rom.readBytes(
                Sonic2Constants.PAL_TITLE_BACKGROUND_ADDR,
                Sonic2Constants.PAL_TITLE_BACKGROUND_SIZE));
        palettes[3] = loadPalette(rom.readBytes(
                Sonic2Constants.PAL_TITLE_EMBLEM_ADDR,
                Sonic2Constants.PAL_TITLE_EMBLEM_SIZE));
        return palettes;
    }

    private static Pattern[] loadSonic3kSpritePatterns(Rom rom) {
        Pattern[] sonicSprites = PatternDecompressor.nemesis(rom,
                Sonic3kConstants.ART_NEM_TITLE_SONIC_SPRITES_ADDR, 65536, "MasterPreviewS3kTitleSonicSprites");
        Pattern[] andKnuckles = PatternDecompressor.nemesis(rom,
                Sonic3kConstants.ART_NEM_TITLE_AND_KNUCKLES_ADDR, 8192, "MasterPreviewS3kTitleAndKnuckles");
        Pattern[] banner = PatternDecompressor.nemesis(rom,
                Sonic3kConstants.ART_NEM_TITLE_BANNER_ADDR, 65536, "MasterPreviewS3kTitleBanner");
        Pattern[] menuText = PatternDecompressor.nemesis(rom,
                Sonic3kConstants.ART_NEM_TITLE_SCREEN_TEXT_ADDR, 8192, "MasterPreviewS3kTitleScreenText");

        Pattern[] combined = appendPatternsAt(new Pattern[0], sonicSprites,
                Sonic3kConstants.VRAM_TITLE_MISC - 0x400);
        combined = appendPatternsAt(combined, andKnuckles,
                Sonic3kConstants.VRAM_TITLE_AND_KNUCKLES - 0x400);
        combined = appendPatternsAt(combined, banner,
                Sonic3kConstants.VRAM_TITLE_BANNER - 0x400);
        return appendPatternsAt(combined, menuText,
                Sonic3kConstants.VRAM_TITLE_MENU - 0x400);
    }

    private static Pattern[] appendPatternsAt(Pattern[] base, Pattern[] addition, int offset) {
        if (addition == null || addition.length == 0) {
            return base != null ? base : new Pattern[0];
        }
        int baseLength = base != null ? base.length : 0;
        int total = Math.max(baseLength, offset + addition.length);
        Pattern[] combined = new Pattern[total];
        if (base != null) {
            System.arraycopy(base, 0, combined, 0, base.length);
        }
        System.arraycopy(addition, 0, combined, offset, addition.length);
        return combined;
    }

    private static Image emptyTilemapImage(int widthTiles, int heightTiles) {
        return new Image(widthTiles * Pattern.PATTERN_WIDTH,
                heightTiles * Pattern.PATTERN_HEIGHT,
                new byte[widthTiles * Pattern.PATTERN_WIDTH * heightTiles * Pattern.PATTERN_HEIGHT * 4]);
    }

    private static void overlayTilemapRows(Image image,
                                           Pattern[] patterns,
                                           Palette[] palettes,
                                           int[] map,
                                           int widthTiles,
                                           int startTileRow,
                                           int endTileRow,
                                           int tileIndexBase) {
        overlayTilemapRows(image, patterns, palettes, map, widthTiles, startTileRow, endTileRow,
                tileIndexBase, 0, 0);
    }

    private static void overlayTilemapRows(Image image,
                                           Pattern[] patterns,
                                           Palette[] palettes,
                                           int[] map,
                                           int widthTiles,
                                           int startTileRow,
                                           int endTileRow,
                                           int tileIndexBase,
                                           int pixelOffsetX,
                                           int pixelOffsetY) {
        if (patterns == null || palettes == null || map == null) {
            return;
        }
        int tileCount = Math.min(map.length, widthTiles * endTileRow);
        for (int tile = Math.max(0, startTileRow) * widthTiles; tile < tileCount; tile++) {
            int word = map[tile];
            int patternIndex = (word & 0x7FF) - tileIndexBase;
            if (patternIndex < 0 || patternIndex >= patterns.length || patterns[patternIndex] == null) {
                continue;
            }
            int paletteIndex = (word >> 13) & 0x03;
            Palette palette = paletteIndex < palettes.length ? palettes[paletteIndex] : null;
            if (palette == null) {
                continue;
            }
            blitPattern(image.rgba, image.width,
                    pixelOffsetX + (tile % widthTiles) * 8,
                    pixelOffsetY + (tile / widthTiles) * 8,
                    patterns[patternIndex],
                    palette,
                    (word & 0x0800) != 0,
                    (word & 0x1000) != 0);
        }
    }

    private static void overlaySonic2CurvedLogoOcclusion(Image image, Image logo) {
        if (image == null || logo == null || image.width != logo.width || image.height != logo.height) {
            return;
        }
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                if (y < sonic2LogoOcclusionStartPixel(x)) {
                    continue;
                }
                int offset = ((y * image.width) + x) * 4;
                if ((logo.rgba[offset + 3] & 0xFF) == 0) {
                    continue;
                }
                image.rgba[offset] = logo.rgba[offset];
                image.rgba[offset + 1] = logo.rgba[offset + 1];
                image.rgba[offset + 2] = logo.rgba[offset + 2];
                image.rgba[offset + 3] = logo.rgba[offset + 3];
            }
        }
    }

    static void clearSonic2LogoChromaGreen(Image image) {
        if (image == null) {
            return;
        }
        for (int offset = 0; offset < image.rgba.length; offset += 4) {
            int r = image.rgba[offset] & 0xFF;
            int g = image.rgba[offset + 1] & 0xFF;
            int b = image.rgba[offset + 2] & 0xFF;
            if (r == 0x92 && g == 0xFF && b == 0x00) {
                image.rgba[offset] = 0;
                image.rgba[offset + 1] = 0;
                image.rgba[offset + 2] = 0;
                image.rgba[offset + 3] = 0;
            }
        }
    }

    private static void overlaySpritePiece(Image image,
                                           Pattern[] patterns,
                                           Palette[] palettes,
                                           SpriteMappingPiece piece,
                                           int originX,
                                           int originY,
                                           int tileIndexBase) {
        SpritePieceRenderer.renderPiece(piece, originX, originY, -tileIndexBase, -1, false, false,
                (patternIndex, hFlip, vFlip, paletteIndex, drawX, drawY) -> {
                    Palette palette = paletteIndex < palettes.length ? palettes[paletteIndex] : null;
                    if (palette == null || patternIndex < 0 || patternIndex >= patterns.length
                            || patterns[patternIndex] == null) {
                        return;
                    }
                    blitPattern(image.rgba, image.width, drawX, drawY,
                            patterns[patternIndex], palette, hFlip, vFlip);
                });
    }

    private static void blitPattern(byte[] rgba,
                                    int imageWidth,
                                    int dstX,
                                    int dstY,
                                    Pattern pattern,
                                    Palette palette,
                                    boolean hFlip,
                                    boolean vFlip) {
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            int sourceY = vFlip ? Pattern.PATTERN_HEIGHT - 1 - y : y;
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                int sourceX = hFlip ? Pattern.PATTERN_WIDTH - 1 - x : x;
                int colorIndex = pattern.getPixel(sourceX, sourceY) & 0x0F;
                if (colorIndex == 0) {
                    continue;
                }
                int outX = dstX + x;
                int outY = dstY + y;
                if (outX < 0 || outY < 0 || outX >= imageWidth || outY >= rgba.length / 4 / imageWidth) {
                    continue;
                }
                Palette.Color color = palette.getColor(colorIndex);
                int offset = ((outY * imageWidth) + outX) * 4;
                rgba[offset] = color.r;
                rgba[offset + 1] = color.g;
                rgba[offset + 2] = color.b;
                rgba[offset + 3] = (byte) 0xFF;
            }
        }
    }
}
