package com.openggf.game.soniccd.special;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;

import java.util.Arrays;

/**
 * Engine presentation of the special stage: the floor image the session shows this frame,
 * drawn as the native plane does it. {@code DrawStageMap} (scddisasm/src/Special Stage/Main.asm:3659-3705) lays each
 * 256 x 96 buffer out as 32 x 12 cells on plane A with palette line 1 ({@code $2001}/{@code $2181}
 * base tiles), cells running down each column, rows 16-27 of an H32 screen ({@code $C800} with a
 * 64-cell plane, {@code StageVDPRegs}, scddisasm/src/Special Stage/Main.asm:3496-3515), i.e. screen rows 128-223.
 *
 * <p>Palette: {@code Pal_Stage} (Main.asm:101-107) with the stage's 32 colours over lines 1-2
 * ({@code LoadStagePal}, scddisasm/src/Special Stage/Main.asm:823-830).
 *
 * <p>Not yet presented: the background plane and its scroll, palette cycles, sprites (Sonic,
 * UFOs, shadows, items), the HUD, the title card and the fades.
 */
public final class CdSpecialStagePresentation {
    private static final int FLOOR_BASE = PatternAtlasRange.SPECIAL_STAGE_PLAYFIELD.base();
    private static final int FLOOR_CELLS = 32 * 12;
    private static final int FLOOR_TOP = 128;

    private final Palette[] palettes = new Palette[4];
    private final Pattern[] floor = new Pattern[FLOOR_CELLS];
    private final byte[] uploaded = new byte[CdStampFloor.IMAGE_WIDTH * CdStampFloor.IMAGE_HEIGHT];
    private final PatternDesc desc = new PatternDesc();
    private boolean palettesUploaded;
    private boolean patternsCached;

    public CdSpecialStagePresentation(CdSpecialStageAssets assets, int stageId) {
        byte[] colours = assets.raw("Pal_Stage");
        byte[] stage = assets.raw("Pal_SS" + (stageId + 1));
        System.arraycopy(stage, 0, colours, Palette.PALETTE_SIZE_IN_ROM, stage.length);
        for (int line = 0; line < 4; line++) {
            palettes[line] = new Palette();
            palettes[line].fromSegaFormat(Arrays.copyOfRange(colours, line * Palette.PALETTE_SIZE_IN_ROM,
                    (line + 1) * Palette.PALETTE_SIZE_IN_ROM));
        }
        for (int i = 0; i < FLOOR_CELLS; i++) floor[i] = new Pattern();
        Arrays.fill(uploaded, (byte) -1);
    }

    public Palette palette(int line) { return palettes[line]; }

    /** Converts the floor image into its 384 plane cells; returns whether any cell changed. */
    boolean updateCells(byte[] image) {
        if (Arrays.equals(image, uploaded)) return false;
        for (int col = 0; col < 32; col++) {
            for (int row = 0; row < 12; row++) {
                Pattern cell = floor[col * 12 + row];
                for (int y = 0; y < 8; y++) {
                    int at = (row * 8 + y) * CdStampFloor.IMAGE_WIDTH + col * 8;
                    for (int x = 0; x < 8; x++) cell.setPixel(x, y, image[at + x]);
                }
            }
        }
        System.arraycopy(image, 0, uploaded, 0, image.length);
        return true;
    }

    Pattern cell(int column, int row) { return floor[column * 12 + row]; }

    public void draw(GraphicsManager graphics, CdSpecialStageSession session, int originX) {
        if (graphics == null || session == null) return;
        if (!palettesUploaded) {
            for (int line = 0; line < 4; line++) graphics.cachePaletteTexture(palettes[line], line);
            palettesUploaded = true;
        }
        if (updateCells(session.displayedFloor()) || !patternsCached) {
            for (int i = 0; i < FLOOR_CELLS; i++) {
                if (patternsCached) graphics.updatePatternTexture(floor[i], FLOOR_BASE + i);
                else graphics.cachePatternTexture(floor[i], FLOOR_BASE + i);
            }
            patternsCached = true;
        }
        desc.setPaletteIndex(1);
        for (int col = 0; col < 32; col++) {
            for (int row = 0; row < 12; row++) {
                graphics.renderPatternWithId(FLOOR_BASE + col * 12 + row, desc, originX + col * 8, FLOOR_TOP + row * 8);
            }
        }
    }
}
