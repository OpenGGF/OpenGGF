package com.openggf.game.sonic2.titlescreen;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.data.Rom;
import com.openggf.game.preview.GeneratedTitlePreviewSequence;
import com.openggf.game.preview.TitlePreviewProvider;
import com.openggf.game.preview.TitlePreviewProvider.Image;
import com.openggf.game.preview.TitlePreviewProvider.Sequence;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.util.Optional;

import static com.openggf.game.preview.TitlePreviewRaster.*;

/** ROM-backed, detached title animation for the master-title stock card. */
public final class Sonic2TitlePreviewProvider implements TitlePreviewProvider {
    private static final int S2_TITLE_WIDTH_TILES = 40;
    private static final int S2_TITLE_HEIGHT_TILES = 28;
    private static final int S2_CHARACTER_PREVIEW_Y_OFFSET = 8;
    private static final boolean S2_PREVIEW_DRAWS_HANDS_BEHIND_LOGO_OCCLUSION = true;
    private static final boolean S2_PREVIEW_DRAWS_HANDS_BEHIND_FULL_LOGO_TEXT = false;
    private static final int S2_LOGO_OCCLUSION_BASE_Y = 13 * 8;
    private static final int S2_LOGO_OCCLUSION_CURVE_PIXELS = 16;
    private static final int S2_SCREEN_WIDTH = 320;
    private static final int S2_LOGO_OCCLUSION_HALF_WIDTH = 104;
    private static final int S2_SONIC_START_TICK = 0;
    private static final int S2_TAILS_START_TICK = 64;
    private static final int S2_SETTLED_TICK = 160;
    private static final int[] S2_ANIM_SONIC = { 5, 6, 7 };
    private static final int[] S2_ANIM_TAILS = { 0, 1, 2, 3, 4 };
    private static final int[][] S2_SONIC_POSITIONS = {
            { 136, 80 }, { 128, 64 }, { 120, 48 }, { 118, 38 },
            { 122, 30 }, { 128, 26 }, { 132, 25 }, { 136, 24 },
    };
    private static final int[][] S2_TAILS_POSITIONS = {
            { 87, 72 }, { 83, 56 }, { 78, 44 }, { 76, 38 },
            { 74, 34 }, { 73, 33 }, { 72, 32 },
    };
    private static final int[][] S2_SONIC_HAND_POSITIONS = {
            { 195, 65 }, { 192, 66 }, { 193, 65 },
    };
    private static final int[][] S2_TAILS_HAND_POSITIONS = {
            { 140, 80 }, { 141, 81 },
    };

    @Override
    public Optional<Sequence> load(Rom rom) throws IOException {
        return Optional.of(loadSonic2Sequence(rom));
    }

    static int sonic2PreviewCharacterYOffset() {
        return S2_CHARACTER_PREVIEW_Y_OFFSET;
    }

    static int sonic2PreviewSonicFrameAt(int tick) {
        if (tick < sonic2SonicAnimationStartTick()) {
            return S2_ANIM_SONIC[0];
        }
        int animStep = (tick - sonic2SonicAnimationStartTick()) / 2;
        return animStep >= S2_ANIM_SONIC.length ? 0x12 : S2_ANIM_SONIC[animStep];
    }

    static int sonic2PreviewSonicYAt(int tick) {
        if (tick < S2_SONIC_START_TICK) {
            return 96 + S2_CHARACTER_PREVIEW_Y_OFFSET;
        }
        return sonic2PositionAt(tick, S2_SONIC_START_TICK, S2_SONIC_POSITIONS)[1] + S2_CHARACTER_PREVIEW_Y_OFFSET;
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

    static boolean sonic2PreviewBodyPixelVisible(int screenX, int screenY) {
        return screenY < sonic2LogoOcclusionStartPixel(screenX);
    }

    private static Sequence loadSonic2Sequence(Rom rom) throws IOException {
        Pattern[] patterns = PatternDecompressor.nemesis(
                rom, Sonic2Constants.ART_NEM_TITLE_ADDR, 8192, "MasterPreviewS2Title");
        Pattern[] spritePatterns = PatternDecompressor.nemesis(
                rom, Sonic2Constants.ART_NEM_TITLE_SPRITES_ADDR, 65536, "MasterPreviewS2TitleSprites");
        Pattern[] menuJunkPatterns = PatternDecompressor.nemesis(
                rom, Sonic2Constants.ART_NEM_MENU_JUNK_ADDR, 1024, "MasterPreviewS2MenuJunk");
        Pattern[] combinedSpritePatterns = appendPatternsAt(spritePatterns, menuJunkPatterns, 0x2A2);
        // TitleScreen uploads the standard font to ArtTile_ArtNem_FontStuff_TtlScr and copies
        // the CopyrightText words ("@ 1992 SEGA", word_3E82) into the logo map at
        // planeLoc(40,28,26) before it reaches Plane A.
        Pattern[] fontPatterns = PatternDecompressor.nemesis(
                rom, Sonic2Constants.ART_NEM_FONT_STUFF_ADDR, 4096, "MasterPreviewS2FontStuff");
        patterns = appendPatternsAt(patterns, fontPatterns, Sonic2Constants.ART_TILE_FONT_STUFF_TITLE_SCREEN);
        int[] map = loadEnigmaMap(rom, Sonic2Constants.MAP_ENI_TITLE_LOGO_ADDR, 0xE000, 1024);
        map = withSonic2CopyrightText(map, readWords(rom,
                address(rom, Sonic2Constants.TITLE_COPYRIGHT_TEXT_ADDR), Sonic2Constants.TITLE_COPYRIGHT_TEXT_WORDS));
        Palette[] palettes = loadSonic2TitlePalettes(rom);
        Image image = composeTilemapImage(patterns, palettes, map, S2_TITLE_WIDTH_TILES, S2_TITLE_HEIGHT_TILES);
        Image logo = composeTilemapImage(patterns, palettes, map, S2_TITLE_WIDTH_TILES, S2_TITLE_HEIGHT_TILES);
        int stillTick = S2_SETTLED_TICK;
        return new GeneratedTitlePreviewSequence(image.width(), image.height(), stillTick,
                Sonic2TitlePreviewProvider::sonic2PreviewTokenAt,
                tick -> composeSonic2Preview(image, logo, combinedSpritePatterns, palettes, tick));
    }

    private static Image composeSonic2Preview(Image baseLogo,
                                              Image logoOcclusion,
                                              Pattern[] spritePatterns,
                                              Palette[] palettes,
                                              int tick) {
        Image image = copyImage(baseLogo);
        var frames = TitleScreenMappings.createFrames();
        if (tick >= S2_SONIC_START_TICK) {
            int[] sonic = sonic2PositionAt(tick, S2_SONIC_START_TICK, S2_SONIC_POSITIONS);
            overlaySpriteFrameClipped(image, spritePatterns, palettes, frames.get(sonic2PreviewSonicFrameAt(tick)),
                    sonic[0], sonic[1] + S2_CHARACTER_PREVIEW_Y_OFFSET, 0,
                    Sonic2TitlePreviewProvider::sonic2PreviewBodyPixelVisible);
        }
        if (tick >= S2_TAILS_START_TICK) {
            int[] tails = sonic2PositionAt(tick, S2_TAILS_START_TICK, S2_TAILS_POSITIONS);
            overlaySpriteFrameClipped(image, spritePatterns, palettes, frames.get(sonic2PreviewTailsFrameAt(tick)),
                    tails[0], tails[1] + S2_CHARACTER_PREVIEW_Y_OFFSET, 0,
                    Sonic2TitlePreviewProvider::sonic2PreviewBodyPixelVisible);
        }
        int sonicHandStart = sonic2SonicAnimationStartTick() + S2_ANIM_SONIC.length * 2;
        if (tick >= sonicHandStart) {
            int[] hand = heldPositionAt(tick, sonicHandStart, S2_SONIC_HAND_POSITIONS);
            overlaySpriteFrame(image, spritePatterns, palettes, frames.get(9),
                    hand[0], hand[1] + S2_CHARACTER_PREVIEW_Y_OFFSET, 0);
        }
        int tailsHandStart = sonic2TailsAnimationStartTick() + S2_ANIM_TAILS.length * 2;
        if (tick >= tailsHandStart) {
            int[] hand = heldPositionAt(tick, tailsHandStart, S2_TAILS_HAND_POSITIONS);
            overlaySpriteFrame(image, spritePatterns, palettes, frames.get(0x13),
                    hand[0], hand[1] + S2_CHARACTER_PREVIEW_Y_OFFSET, 0);
        }
        overlaySonic2CurvedLogoOcclusion(image, logoOcclusion);
        return image;
    }

    static int[] withSonic2CopyrightText(int[] logoMap, int[] copyrightWords) {
        int[] map = logoMap.clone();
        int base = Sonic2Constants.TITLE_COPYRIGHT_PLANE_ROW * S2_TITLE_WIDTH_TILES
                + Sonic2Constants.TITLE_COPYRIGHT_PLANE_COLUMN;
        for (int i = 0; i < copyrightWords.length && base + i < map.length; i++) {
            map[base + i] = copyrightWords[i];
        }
        return map;
    }

    private static int[] sonic2PositionAt(int tick, int startTick, int[][] positions) {
        if (tick < startTick) {
            return positions[0];
        }
        int index = Math.min((tick - startTick) / 4, positions.length - 1);
        return positions[index];
    }

    private static int[] heldPositionAt(int tick, int startTick, int[][] positions) {
        int index = Math.min(Math.max(0, (tick - startTick) / 4), positions.length - 1);
        return positions[index];
    }

    private static int sonic2SonicAnimationStartTick() {
        return S2_SONIC_START_TICK + S2_SONIC_POSITIONS.length * 4;
    }

    private static int sonic2TailsAnimationStartTick() {
        return S2_TAILS_START_TICK + S2_TAILS_POSITIONS.length * 4;
    }

    private static int sonic2PreviewTailsFrameAt(int tick) {
        if (tick < sonic2TailsAnimationStartTick()) {
            return 1;
        }
        int animStep = (tick - sonic2TailsAnimationStartTick()) / 2;
        return animStep >= S2_ANIM_TAILS.length
                ? S2_ANIM_TAILS[S2_ANIM_TAILS.length - 1]
                : S2_ANIM_TAILS[animStep];
    }

    private static int sonic2PreviewTokenAt(int tick) {
        int clamped = Math.min(Math.max(0, tick), S2_SETTLED_TICK);
        if (clamped < S2_SONIC_START_TICK) {
            return 0;
        }
        int sonicAnimStart = sonic2SonicAnimationStartTick();
        if (clamped < sonicAnimStart) {
            return S2_SONIC_START_TICK + ((clamped - S2_SONIC_START_TICK) / 4) * 4;
        }
        int sonicHandStart = sonicAnimStart + S2_ANIM_SONIC.length * 2;
        if (clamped < sonicHandStart) {
            return sonicAnimStart + ((clamped - sonicAnimStart) / 2) * 2;
        }
        if (clamped < S2_TAILS_START_TICK) {
            return sonicHandStart + Math.min((clamped - sonicHandStart) / 4,
                    S2_SONIC_HAND_POSITIONS.length - 1) * 4;
        }
        int tailsAnimStart = sonic2TailsAnimationStartTick();
        if (clamped < tailsAnimStart) {
            return S2_TAILS_START_TICK + ((clamped - S2_TAILS_START_TICK) / 4) * 4;
        }
        int tailsHandStart = tailsAnimStart + S2_ANIM_TAILS.length * 2;
        if (clamped < tailsHandStart) {
            return tailsAnimStart + ((clamped - tailsAnimStart) / 2) * 2;
        }
        if (clamped < tailsHandStart + S2_TAILS_HAND_POSITIONS.length * 4) {
            return tailsHandStart + Math.min((clamped - tailsHandStart) / 4,
                    S2_TAILS_HAND_POSITIONS.length - 1) * 4;
        }
        return S2_SETTLED_TICK;
    }

    private static Palette[] loadSonic2TitlePalettes(Rom rom) throws IOException {
        Palette[] palettes = new Palette[4];
        palettes[0] = loadPalette(rom.readBytes(
                Sonic2Constants.PAL_TITLE_SONIC_ADDR,
                Sonic2Constants.PAL_TITLE_SONIC_SIZE));
        palettes[1] = loadPalette(rom.readBytes(
                address(rom, Sonic2Constants.PAL_TITLE_ADDR),
                Sonic2Constants.PAL_TITLE_SIZE));
        palettes[2] = loadPalette(rom.readBytes(
                Sonic2Constants.PAL_TITLE_BACKGROUND_ADDR,
                Sonic2Constants.PAL_TITLE_BACKGROUND_SIZE));
        palettes[3] = loadPalette(rom.readBytes(
                Sonic2Constants.PAL_TITLE_EMBLEM_ADDR,
                Sonic2Constants.PAL_TITLE_EMBLEM_SIZE));
        return palettes;
    }

    private static void overlaySonic2CurvedLogoOcclusion(Image image, Image logo) {
        if (image == null || logo == null || image.width() != logo.width() || image.height() != logo.height()) {
            return;
        }
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if (y < sonic2LogoOcclusionStartPixel(x)) {
                    continue;
                }
                int offset = ((y * image.width()) + x) * 4;
                if ((logo.rgba()[offset + 3] & 0xFF) == 0) {
                    continue;
                }
                image.rgba()[offset] = logo.rgba()[offset];
                image.rgba()[offset + 1] = logo.rgba()[offset + 1];
                image.rgba()[offset + 2] = logo.rgba()[offset + 2];
                image.rgba()[offset + 3] = logo.rgba()[offset + 3];
            }
        }
    }
}
