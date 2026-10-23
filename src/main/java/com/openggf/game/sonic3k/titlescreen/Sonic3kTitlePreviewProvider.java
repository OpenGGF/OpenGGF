package com.openggf.game.sonic3k.titlescreen;

import com.openggf.data.Rom;
import com.openggf.game.preview.GeneratedTitlePreviewSequence;
import com.openggf.game.preview.TitlePreviewProvider;
import com.openggf.game.preview.TitlePreviewProvider.Image;
import com.openggf.game.preview.TitlePreviewProvider.Sequence;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.util.Optional;

import static com.openggf.game.preview.TitlePreviewRaster.*;

/** ROM-backed, detached title animation for the master-title stock card. */
public final class Sonic3kTitlePreviewProvider implements TitlePreviewProvider {
    private static final int S3K_TITLE_WIDTH_TILES = 40;
    private static final int S3K_TITLE_HEIGHT_TILES = 28;
    private static final int S3K_PREVIEW_BANNER_Y = 84;
    private static final int S3K_PREVIEW_VSCROLL = 16;
    private static final int S3K_PREVIEW_FINGER_X = 0x148 - 128;
    private static final int S3K_PREVIEW_FINGER_Y = 0xDC - S3K_PREVIEW_VSCROLL - 128;
    private static final int S3K_PREVIEW_FINGER_FRAME_DURATION = 6;
    private static final int S3K_PREVIEW_WINK_X = 0xF8 - 128;
    private static final int S3K_PREVIEW_WINK_Y = 0xC8 - S3K_PREVIEW_VSCROLL - 128;
    private static final int S3K_PREVIEW_SCALE_NUMERATOR = 1;
    private static final int S3K_PREVIEW_SCALE_DENOMINATOR = 1;
    private static final int S3K_PREVIEW_COPYRIGHT_X = 0x158 - 128;
    // Raise the title's y=204 copyright by four pixels to clear the master navigation hints.
    private static final int S3K_PREVIEW_COPYRIGHT_Y = 200;
    private static final boolean S3K_PREVIEW_DRAWS_COPYRIGHT = true;
    private static final boolean S3K_PREVIEW_DRAWS_MENU_SELECTION = false;
    private static final int[] S3K_PREVIEW_FINGER_WAG_FRAMES = {
            4, 4, 4, 4, 4, 4, 0, 4, 1, 4, 0, 4, 1, 4, 0, 4, 1, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4
    };
    private static final int[] S3K_PREVIEW_WINK_DATA = {
            2, 1,
            3, 7,
            2, 5,
            4, 0x67,
            4, 0x2F
    };

    @Override
    public Optional<Sequence> load(Rom rom) throws IOException {
        return Optional.of(loadSonic3kSequence(rom));
    }

    static boolean sonic3kPreviewDrawsMenuSelection() {
        return S3K_PREVIEW_DRAWS_MENU_SELECTION;
    }

    static boolean sonic3kPreviewDrawsCopyright() {
        return S3K_PREVIEW_DRAWS_COPYRIGHT;
    }

    static int sonic3kPreviewBannerY() {
        return S3K_PREVIEW_BANNER_Y;
    }

    static int sonic3kPreviewVScroll() {
        return S3K_PREVIEW_VSCROLL;
    }

    static int sonic3kPreviewFingerY() {
        return S3K_PREVIEW_FINGER_Y;
    }

    static int sonic3kPreviewWinkY() {
        return S3K_PREVIEW_WINK_Y;
    }

    static int sonic3kPreviewScaledWidth() {
        return S3K_TITLE_WIDTH_TILES * Pattern.PATTERN_WIDTH
                * S3K_PREVIEW_SCALE_NUMERATOR / S3K_PREVIEW_SCALE_DENOMINATOR;
    }

    static int sonic3kPreviewScaledHeight() {
        return S3K_TITLE_HEIGHT_TILES * Pattern.PATTERN_HEIGHT
                * S3K_PREVIEW_SCALE_NUMERATOR / S3K_PREVIEW_SCALE_DENOMINATOR;
    }

    static int sonic3kPreviewCopyrightX() {
        return S3K_PREVIEW_COPYRIGHT_X;
    }

    static int sonic3kPreviewCopyrightY() {
        return S3K_PREVIEW_COPYRIGHT_Y;
    }

    private static Sequence loadSonic3kSequence(Rom rom) throws IOException {
        Pattern[] patterns = PatternDecompressor.kosinski(rom, Sonic3kConstants.ART_KOS_TITLE_SONIC_D_ADDR);
        Pattern[] spritePatterns = loadSonic3kSpritePatterns(rom);
        int[] map = loadEnigmaMap(rom, Sonic3kConstants.MAP_ENI_TITLE_SONIC_D_ADDR,
                0x8000, Sonic3kConstants.MAP_ENI_TITLE_READ_SIZE);
        Palette[] palettes = loadPaletteLines(rom.readBytes(
                Sonic3kConstants.PAL_TITLE_SONIC_D_ADDR,
                Sonic3kConstants.PAL_TITLE_SONIC_D_SIZE));
        Image finalScene = shiftImage(composeTilemapImage(patterns, palettes, map,
                S3K_TITLE_WIDTH_TILES, S3K_TITLE_HEIGHT_TILES), 0, -sonic3kPreviewVScroll());
        int stillTick = 0;
        return new GeneratedTitlePreviewSequence(finalScene.width(), finalScene.height(), stillTick,
                Sonic3kTitlePreviewProvider::s3kPreviewTokenAt,
                token -> composeSonic3kFinalPreview(finalScene, spritePatterns, palettes,
                        s3kPreviewFingerFrameFromToken(token), s3kPreviewWinkFrameFromToken(token)));
    }

    private static Image composeSonic3kFinalPreview(Image finalScene,
                                                    Pattern[] spritePatterns,
                                                    Palette[] palettes,
                                                    int fingerFrame,
                                                    int winkFrame) {
        Image image = copyImage(finalScene);

        if (winkFrame >= 0) {
            overlaySpriteFrame(image, spritePatterns, palettes,
                    Sonic3kTitleScreenMappings.createSonicAnimFrames().get(winkFrame),
                    S3K_PREVIEW_WINK_X, S3K_PREVIEW_WINK_Y, 0);
        }
        if (fingerFrame >= 0) {
            overlaySpriteFrame(image, spritePatterns, palettes,
                    Sonic3kTitleScreenMappings.createSonicAnimFrames().get(fingerFrame),
                    S3K_PREVIEW_FINGER_X, S3K_PREVIEW_FINGER_Y, 0);
        }
        int bannerY = sonic3kPreviewBannerY();
        var bannerFrames = Sonic3kTitleScreenMappings.createBannerFrames();
        overlaySpriteFrame(image, spritePatterns, palettes, bannerFrames.get(0),
                0x120 - 128, bannerY, 0);
        // Settled title TM uses fixed VDP coordinates, as in the title-screen manager.
        overlaySpriteFrame(image, spritePatterns, palettes, bannerFrames.get(1),
                0x188 - 128, 0xEC - 128, 0);
        overlaySpriteFrame(image, spritePatterns, palettes, Sonic3kTitleScreenMappings.createAndKnucklesFrames().get(0),
                0x120 - 128, bannerY + 0x5C, 0);
        if (sonic3kPreviewDrawsMenuSelection()) {
            overlaySpriteFrame(image, spritePatterns, palettes, Sonic3kTitleScreenMappings.createSelectionFrames().get(0),
                    0xF0 - 128, 0x140 - 128, 0);
        }
        if (sonic3kPreviewDrawsCopyright()) {
            overlaySpriteFrame(image, spritePatterns, palettes, Sonic3kTitleScreenMappings.createCopyrightFrame().get(0),
                    sonic3kPreviewCopyrightX(), sonic3kPreviewCopyrightY(), 0);
        }
        return scaleImageIntoNativeCanvas(image, sonic3kPreviewScaledWidth(), sonic3kPreviewScaledHeight());
    }

    private static int s3kPreviewTokenAt(int tick) {
        return (s3kPreviewWinkFrameAt(tick) << 8) | s3kPreviewFingerFrameAt(tick);
    }

    private static int s3kPreviewFingerFrameFromToken(int token) {
        return token & 0xFF;
    }

    private static int s3kPreviewWinkFrameFromToken(int token) {
        return (token >> 8) & 0xFF;
    }

    private static int s3kPreviewFingerFrameAt(int tick) {
        int index = (Math.max(0, tick) / S3K_PREVIEW_FINGER_FRAME_DURATION)
                % S3K_PREVIEW_FINGER_WAG_FRAMES.length;
        return S3K_PREVIEW_FINGER_WAG_FRAMES[index];
    }

    private static int s3kPreviewWinkFrameAt(int tick) {
        int normalized = Math.max(0, tick) % s3kPreviewWinkCycleTicks();
        for (int i = 0; i < S3K_PREVIEW_WINK_DATA.length; i += 2) {
            int frame = S3K_PREVIEW_WINK_DATA[i];
            int duration = S3K_PREVIEW_WINK_DATA[i + 1] + 1;
            if (normalized < duration) {
                return frame;
            }
            normalized -= duration;
        }
        return 4;
    }

    private static int s3kPreviewWinkCycleTicks() {
        int total = 0;
        for (int i = 1; i < S3K_PREVIEW_WINK_DATA.length; i += 2) {
            total += S3K_PREVIEW_WINK_DATA[i] + 1;
        }
        return total;
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
}
