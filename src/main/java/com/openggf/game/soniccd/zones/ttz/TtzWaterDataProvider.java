package com.openggf.game.soniccd.zones.ttz;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdZone;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.Palette;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.function.Supplier;

/**
 * Tidal Tempest water through the shared water system (docs/scddisasm pin 1ecc5f3a).
 *
 * <ul>
 *   <li>Every Tidal Tempest stage has water: {@code InitStage} sets it up when {@code zone} is 2
 *       ({@code cmpi.b #2,zone; bne.s}, {@code src/r4/main.asm:191-192}), and the level height is
 *       read by act and time zone from the water-height table (:196-203 and :604-607): {@code $300 $280 $1D0} (Past, Present,
 *       Future) in Act 1, {@code $5B0 $530 $510} in Act 2 and {@code $490} in Act 3. Good and Bad Future
 *       share the Future column. {@code water_speed} starts at 1 (:209).</li>
 *   <li>The underwater palette is palette table entry 0, loaded by {@code LoadWaterPalette}
 *       ({@code r4/main.asm:297-301}, {@code common/palette_load_water.asm:3-15}): 32 longs, four palette
 *       lines, read from the stage file through the stage's own {@code PaletteTable}. Natively V-blank
 *       swaps these into CRAM from the {@code water_y} scanline down (HBlank, {@code r4/main.asm:1316-1358}),
 *       which the shared water renderer reproduces from {@link #getUnderwaterPalette}. {@code CyclePalette}
 *       writes only {@code palette}, so the underwater lines never cycle.</li>
 *   <li>The level moves and bobs in {@link TtzWaterHandler}; the bob is both the gameplay and the visual
 *       offset because natively one variable, {@code water_y}, serves both ({@code r4/main.asm:582-583}).</li>
 * </ul>
 * Not in scope: currents ({@code CheckWaterCurrents}), the water-surface and bubble objects, drowning and
 * water physics.
 */
public final class TtzWaterDataProvider implements WaterDataProvider {
    private static final int ENTRY_WATER = 0;                 // moveq #0,d0 ; bsr.w LoadWaterPalette (main.asm:299-301)
    private static final int[][] WATER_HEIGHTS = {            // WaterHeights (scddisasm/src/r4/main.asm:604-607): rows act 1-3
            {0x300, 0x280, 0x1D0}, {0x5B0, 0x530, 0x510}, {0x490, 0x490, 0x490}};

    /** The player facts {@code WaterEvents} reads (a stand-in point for tests while the player lane is separate). */
    public interface PlayerView {
        /** {@code player_object+obj.x}, the centre X. */
        int centreX();

        /** {@code player_object+obj.y}, the centre Y. */
        int centreY();

        /** {@code player_object+obj.routine < 6}. */
        boolean alive();
    }

    /** The live player, from the camera's focused sprite. */
    static PlayerView focusedSprite() {
        return new PlayerView() {
            private AbstractPlayableSprite sprite() {
                Camera camera = GameServices.camera();
                return camera == null ? null : camera.getFocusedSprite();
            }
            @Override public int centreX() { var s = sprite(); return s == null ? 0 : s.getCentreX() & 0xFFFF; }
            @Override public int centreY() { var s = sprite(); return s == null ? 0 : s.getCentreY() & 0xFFFF; }
            @Override public boolean alive() { var s = sprite(); return s != null && !s.getDead(); }
        };
    }

    private final Supplier<CdStageImage> stage;
    private final PlayerView player;
    private volatile int bob;

    /** {@code stage} yields the loaded stage file (null before the first load). */
    public TtzWaterDataProvider(Supplier<CdStageImage> stage) {
        this(stage, focusedSprite());
    }

    public TtzWaterDataProvider(Supplier<CdStageImage> stage, PlayerView player) {
        this.stage = stage;
        this.player = player;
    }

    void setBob(int bob) {
        this.bob = bob;
    }

    /** The current sine offset added to the base level, signed pixels. */
    int bob() {
        return bob;
    }

    private static CdStagePayload payload(int zoneId, int actId) {
        return SonicCdZoneRegistry.payload(zoneId, actId);
    }

    @Override
    public boolean hasWater(int zoneId, int actId, PlayerCharacter character) {
        return zoneId == CdZone.TIDAL_TEMPEST.ordinal();
    }

    @Override
    public int getStartingWaterLevel(int zoneId, int actId) {
        CdStagePayload payload = payload(zoneId, actId);
        return WATER_HEIGHTS[payload.stage().actNumber() - 1][TtzWaterHandler.timeZone(payload.stage().period())];
    }

    @Override
    public Palette[] getUnderwaterPalette(Rom rom, int zoneId, int actId, PlayerCharacter character) {
        CdStageImage image = stage.get();
        if (image == null) return null;
        int table = CdStageCatalogue.entry(payload(zoneId, actId)).paletteTable();
        int entry = table + ENTRY_WATER * 8;
        int source = image.offsetOf(image.u32(entry));
        int bytes = (image.u16(entry + 6) + 1) * 4;           // dbf over longwords
        byte[] data = image.slice(source, bytes);
        Palette[] palettes = new Palette[bytes / Palette.PALETTE_SIZE_IN_ROM];
        for (int line = 0; line < palettes.length; line++) {
            palettes[line] = new Palette();
            palettes[line].fromSegaFormat(java.util.Arrays.copyOfRange(data, line * Palette.PALETTE_SIZE_IN_ROM,
                    (line + 1) * Palette.PALETTE_SIZE_IN_ROM));
        }
        return palettes;
    }

    @Override
    public DynamicWaterHandler getDynamicHandler(int zoneId, int actId, PlayerCharacter character) {
        bob = 0;                                              // water_angle starts at 0 with the stage variables
        return new TtzWaterHandler(payload(zoneId, actId), this, player);
    }

    @Override
    public int getWaterSpeed(int zoneId, int actId) {
        return 1;
    }

    @Override
    public int getGameplayWaterLevelOffset(int zoneId, int actId) {
        return bob;
    }

    @Override
    public int getVisualWaterLevelOffset(int zoneId, int actId) {
        return bob;
    }
}
