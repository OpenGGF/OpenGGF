package com.openggf.game.sonic1.titlescreen;

import static com.openggf.game.sonic1.Sonic1RomProfile.resolve;

import com.openggf.data.Rom;
import com.openggf.game.preview.GeneratedTitlePreviewSequence;
import com.openggf.game.preview.TitlePreviewProvider;
import com.openggf.game.preview.TitlePreviewProvider.Image;
import com.openggf.game.preview.TitlePreviewProvider.Sequence;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.util.Optional;

import static com.openggf.game.preview.TitlePreviewRaster.*;

/** ROM-backed, detached title animation for the master-title stock card. */
public final class Sonic1TitlePreviewProvider implements TitlePreviewProvider {
    private static final int S1_PREVIEW_WIDTH_TILES = 40;
    private static final int S1_PREVIEW_HEIGHT_TILES = 28;
    private static final int S1_TITLE_WIDTH_TILES = 34;
    private static final int S1_TITLE_HEIGHT_TILES = 22;
    private static final int S1_PLANE_A_SPLIT_ROW = 9;
    private static final int S1_PLANE_A_PIXEL_X = 32;
    private static final int S1_PLANE_A_PIXEL_Y = 32;
    private static final int S1_SONIC_MASK_Y = S1_PLANE_A_PIXEL_Y + S1_PLANE_A_SPLIT_ROW * Pattern.PATTERN_HEIGHT;
    private static final int S1_PREVIEW_SONIC_FRAME = 6;
    private static final int S1_SONIC_X = 0xF0 - 128;
    private static final int S1_SONIC_START_Y = 0xDE;
    private static final int S1_SONIC_FINAL_Y = 0x96;
    private static final int S1_SONIC_DELAY_FRAMES = 30;
    private static final int S1_SONIC_MOVE_STEP = 8;
    private static final int S1_SONIC_ANIM_DURATION = 7;

    @Override
    public Optional<Sequence> load(Rom rom) throws IOException {
        return Optional.of(loadSonic1Sequence(rom));
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

    private static Sequence loadSonic1Sequence(Rom rom) throws IOException {
        Pattern[] patterns = PatternDecompressor.nemesis(
                rom, resolve(rom, Sonic1Constants.ART_NEM_TITLE_FG_ADDR), 8192, "MasterPreviewS1TitleFg");
        Pattern[] sonicPatterns = PatternDecompressor.nemesis(
                rom, resolve(rom, Sonic1Constants.ART_NEM_TITLE_SONIC_ADDR), 65536, "MasterPreviewS1TitleSonic");
        Pattern[] tmPatterns = PatternDecompressor.nemesis(
                rom, resolve(rom, Sonic1Constants.ART_NEM_TITLE_TM_ADDR), 1024, "MasterPreviewS1TitleTM");
        int[] map = loadEnigmaMap(rom, resolve(rom, Sonic1Constants.MAP_ENI_TITLE_ADDR), 0, 1024);
        Palette[] palettes = loadPaletteLines(rom.readBytes(resolve(rom, Sonic1Constants.PAL_TITLE_ADDR), 128));

        int stillTick = 128;
        return new GeneratedTitlePreviewSequence(sonic1PreviewWidth(), sonic1PreviewHeight(), stillTick,
                Sonic1TitlePreviewProvider::sonic1PreviewTokenAt,
                tick -> composeSonic1Preview(patterns, sonicPatterns, tmPatterns, map, palettes, tick));
    }

    private static Image composeSonic1Preview(Pattern[] patterns,
                                              Pattern[] sonicPatterns,
                                              Pattern[] tmPatterns,
                                              int[] map,
                                              Palette[] palettes,
                                              int tick) {
        Image image = emptyTilemapImage(S1_PREVIEW_WIDTH_TILES, S1_PREVIEW_HEIGHT_TILES);
        overlayTilemapRows(image, patterns, palettes, map, S1_TITLE_WIDTH_TILES,
                0, S1_PLANE_A_SPLIT_ROW, Sonic1Constants.ARTTILE_TITLE_FOREGROUND,
                S1_PLANE_A_PIXEL_X, S1_PLANE_A_PIXEL_Y);
        SpriteMappingFrame sonic = Sonic1TitleScreenMappings.createFrames()
                .get(sonic1PreviewSonicFrameAt(tick));
        overlaySpriteFrameClipped(image, sonicPatterns, new Palette[] { palettes[1] }, sonic,
                S1_SONIC_X, sonic1PreviewSonicYAt(tick) - 128, 0,
                Sonic1TitlePreviewProvider::sonic1PreviewSonicPixelVisible);
        overlayTilemapRows(image, patterns, palettes, map, S1_TITLE_WIDTH_TILES,
                S1_PLANE_A_SPLIT_ROW, S1_TITLE_HEIGHT_TILES, Sonic1Constants.ARTTILE_TITLE_FOREGROUND,
                S1_PLANE_A_PIXEL_X, S1_PLANE_A_PIXEL_Y);
        overlaySpriteFrame(image, tmPatterns, new Palette[] { palettes[1] },
                Sonic1TitleScreenMappings.createFrames().get(Sonic1TitleScreenMappings.FRAME_TM),
                0x170 - 128, 0xF8 - 128, 0);
        return image;
    }

    private static int sonic1PreviewSonicYAt(int tick) {
        if (tick < S1_SONIC_DELAY_FRAMES) {
            return S1_SONIC_START_Y;
        }
        int moved = (tick - S1_SONIC_DELAY_FRAMES + 1) * S1_SONIC_MOVE_STEP;
        return Math.max(S1_SONIC_FINAL_Y, S1_SONIC_START_Y - moved);
    }

    private static int sonic1PreviewTokenAt(int tick) {
        int normalized = Math.max(0, tick);
        if (normalized < S1_SONIC_DELAY_FRAMES) {
            return 0;
        }
        int finalTick = S1_SONIC_DELAY_FRAMES + ((S1_SONIC_START_Y - S1_SONIC_FINAL_Y) / S1_SONIC_MOVE_STEP);
        if (normalized < finalTick) {
            return normalized;
        }
        int frame = (normalized - finalTick) / (S1_SONIC_ANIM_DURATION + 1);
        if (frame <= Sonic1TitleScreenMappings.FRAME_TITLE_SONIC_7) {
            return finalTick + frame * (S1_SONIC_ANIM_DURATION + 1);
        }
        int loopFrame = 6 + ((frame - 8) & 1);
        return finalTick + loopFrame * (S1_SONIC_ANIM_DURATION + 1);
    }

    private static int sonic1PreviewSonicFrameAt(int tick) {
        int finalTick = S1_SONIC_DELAY_FRAMES + ((S1_SONIC_START_Y - S1_SONIC_FINAL_Y) / S1_SONIC_MOVE_STEP);
        if (tick < finalTick) {
            return Sonic1TitleScreenMappings.FRAME_TITLE_SONIC_0;
        }
        int animTick = tick - finalTick;
        int frame = animTick / (S1_SONIC_ANIM_DURATION + 1);
        if (frame <= Sonic1TitleScreenMappings.FRAME_TITLE_SONIC_7) {
            return frame;
        }
        return 6 + ((frame - 8) & 1);
    }

    private static boolean sonic1PreviewSonicPixelVisible(int screenX, int screenY) {
        return screenY < S1_SONIC_MASK_Y;
    }
}
