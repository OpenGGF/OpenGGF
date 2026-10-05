package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.scroll.S3kBackdropBands;
import com.openggf.level.Map;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AniPlcParser;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.level.render.PlaneRasterizer;
import com.openggf.level.render.ZonePictureSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * S3K zone pictures for mod scenes ({@code SceneRomArt.zoneBackdrop} and
 * {@code levelOverview}). Each supported act is built as a detached level
 * ({@link Sonic3k#buildDetachedLevel}: explicit bootstrap mode and Sonic's palette, nothing
 * published), its animated tiles are put at the state the level shows on load into a private
 * copy of the tile array, and the planes are rasterised on the CPU. Nothing here reads a live
 * service, the session or user settings, or touches the running level's tiles or textures.
 * Engine-internal; not thread-safe, and meant to run on the thread that owns the ROM.
 */
public final class Sonic3kZoneArt implements ZonePictureSource {
    /** VDP plane width: background draws at a fixed X repeat at this period. */
    private static final int PLANE_WIDTH = 512;
    /** VRAM holds $800 tiles; slots the level never loads stay blank. */
    private static final int VRAM_TILES = 0x800;
    /** {@code $8720}: the VDP backdrop colour is palette line 2 colour 0. */
    private static final int BACKDROP_LINE = 2;
    /** The native screen the level's camera limits are measured for. */
    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;
    /** {@code SceneImage}'s largest side. */
    private static final int MAX_IMAGE_SIDE = 4096;
    /** {@code AnimateTiles_AIZ2}: {@code cmpi.w #$1C0,(Camera_X_pos).w}. */
    private static final int AIZ2_FIRST_TREE_CAMERA_X = 0x1C0;

    private final Rom rom;
    private Sonic3k game;
    private RomByteReader reader;
    /** The last detached build, so a backdrop and an overview of one act decode it once. */
    private Built last;

    public Sonic3kZoneArt(Rom rom) {
        this.rom = rom;
    }

    /** One tile upload into the presentation tile copy: ROM art at {@code address}. */
    private record Strip(int address, int bytes, int destinationTile) {
    }

    /**
     * A supported act: how to build it, its direct-DMA strips, what to cut from its background
     * layout for the backdrop, and how its background repeats. Background rows
     * {@code fixedTop .. fixedBottom - 1} are drawn by the ROM from X 0, so on screen they
     * repeat at the plane width (and are tiled across a wider backdrop); other rows follow the
     * layout. {@code backgroundRows} is how far down the background renders cleanly with this
     * art; the overview repeats it vertically from there. Foreground columns from
     * {@code foregroundEndX} on are not playable (staging data), or {@code Integer.MAX_VALUE}.
     */
    private record Profile(int zone, int act, Sonic3kLoadBootstrap.Mode mode, List<Strip> strips, int layoutX,
            int width, int height, int fixedTop, int fixedBottom, int backgroundRows, int foregroundEndX,
            IntFunction<List<Band>> bands) {
        PlaneRasterizer.BackgroundWrap backgroundWrap() {
            return new PlaneRasterizer.BackgroundWrap(backgroundRows, fixedTop, fixedBottom, PLANE_WIDTH);
        }
    }

    /**
     * A detached build with its presentation tiles. Where the act swaps art by camera
     * position, {@code startTiles} replace {@code tiles} in foreground columns left of
     * {@code startTilesBeforeX}.
     */
    private record Built(int zone, int act, Sonic3kLevel level, Pattern[] tiles, Pattern[] startTiles,
            int startTilesBeforeX) implements PlaneRasterizer.TileSource {
        @Override
        public Pattern tile(int layer, int patternIndex, int layoutX) {
            Pattern[] source = startTiles != null && layer == PlaneRasterizer.FOREGROUND && layoutX < startTilesBeforeX
                    ? startTiles : tiles;
            return patternIndex < source.length ? source[patternIndex] : null;
        }
    }

    /** A rectangle of world pixels. */
    record Bounds(int x, int y, int width, int height) {
    }

    private static Profile profile(int zone, int act) {
        if (zone == Sonic3kZoneIds.ZONE_AIZ && act == 0) {
            // The main level's art: LoadLevelLoadBlock's $0D00 entry, which the skip-intro
            // bootstrap selects (Sonic3k.resolveLevelLoadBlockIndex). AIZ1_BGDrawArray is
            // $220, $7FFF: rows below $220 are drawn from (camX - $1300) / 2 rather than X 0
            // (AIZ1_Deform, s3.asm:70272), so only the top $220 rows repeat at 512. The overview
            // also repeats the forest rows $220-$37F at 512: drawn from (camX - $1300) / 2, they
            // start from layout X 0 at the act's start and the layout is empty further right.
            // Rows $380 on hold the intro beach, which this art cannot draw; the main level never
            // shows them (its background Y tops out at $390 / 2 + $E0 = $2A8).
            return new Profile(zone, act, Sonic3kLoadBootstrap.Mode.SKIP_INTRO, List.of(), 0, PLANE_WIDTH, 0x220,
                    0, 0x380, 0x380, Integer.MAX_VALUE, S3kBackdropBands::aiz1);
        }
        if (zone == Sonic3kZoneIds.ZONE_AIZ && act == 1) {
            // The act's own LevelLoadBlock art and palette, as a fresh act 2 load shows them
            // (the burnt jungle); the AIZ1 fire transition's line-4 writes are not applied.
            // AIZ2_BackgroundInit draws the whole plane from X 0 (Refresh_PlaneFull, d1 = 0);
            // the layout repeats vertically every $280 rows.
            return new Profile(zone, act, Sonic3kLoadBootstrap.Mode.NORMAL, List.of(), 0, PLANE_WIDTH, 0x280,
                    0, 0x500, 0x500, Integer.MAX_VALUE, S3kBackdropBands::aiz2);
        }
        if (zone == Sonic3kZoneIds.ZONE_HCZ && act == 0) {
            // The four $2DC-$30B strips Sonic3kPatternAnimator.updateHcz1BackgroundStrips
            // uploads once the camera is $80 or more below the waterline equilibrium.
            int size = Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE;
            return new Profile(zone, act, Sonic3kLoadBootstrap.Mode.NORMAL,
                    List.of(new Strip(Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW1_ADDR, size, 0x2DC),
                            new Strip(Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW2_ADDR, size, 0x2E8),
                            new Strip(Sonic3kConstants.ART_UNC_FIX_HCZ1_LOWER_BG1_ADDR, size, 0x2F4),
                            new Strip(Sonic3kConstants.ART_UNC_FIX_HCZ1_LOWER_BG2_ADDR, size, 0x300)),
                    0, PLANE_WIDTH, 0x400, 0, 0x400, 0x400, Integer.MAX_VALUE, S3kBackdropBands::hcz1BelowWaterline);
        }
        if (zone == Sonic3kZoneIds.ZONE_LBZ && act == 0) {
            // AnimateTiles_LBZ1's scroll tiles at phase 0 (updateLbz1ScrollTiles: $140 words
            // from the start of ArtUnc_AniLBZ1_1, then the first $20-byte cap tile).
            // LBZ1_BGDrawArray is $D0, $7FFF with HScroll_table+$004 cleared
            // (LBZ1_BackgroundInit, sonic3k.asm:111168): the sky follows the 1536-px layout,
            // the water below is drawn from X 0. Foreground columns $80 on are the staging rows
            // the LBZ1_DoModN routines copy into the visible layout (Sonic3kLBZEvents
            // LBZ1_LAYOUT_MODS), so the playable foreground ends at X $4000.
            return new Profile(zone, act, Sonic3kLoadBootstrap.Mode.NORMAL,
                    List.of(new Strip(Sonic3kConstants.ART_UNC_ANI_LBZ1_1_ADDR, 0x280, 0x350),
                            new Strip(Sonic3kConstants.ART_UNC_ANI_LBZ1_2_ADDR, 0x20, 0x364)),
                    0, 0x600, 0x180, 0xD0, 0x180, 0x180, 0x4000, S3kBackdropBands::lbz1);
        }
        if (zone == Sonic3kZoneIds.ZONE_SSZ && act == 0) {
            // The cloud sea: sub_57A60 fills plane B from background X $1C00 (layout columns
            // 56-59) while the camera is in the cloud band. Its background Y reaches $41F
            // ((($EFF - $800) >> 1) + $A0), so a screen below that ends at row $4FF. Elsewhere
            // the background is plain: the layout's floating ruins moving with the camera, which
            // is what the overview shows (no fixed rows).
            return new Profile(zone, act, Sonic3kLoadBootstrap.Mode.NORMAL, List.of(), 0x1C00, PLANE_WIDTH,
                    0x500, 0, 0, 0xB00, Integer.MAX_VALUE, S3kBackdropBands::ssz1Clouds);
        }
        return null;
    }

    @Override
    public Backdrop backdrop(int zone, int act) {
        Profile profile = profile(zone, act);
        if (profile == null) {
            return null;
        }
        Built built = build(profile);
        int width = profile.width();
        int height = profile.height();
        int[] argb = PlaneRasterizer.rasterize(built.level(), built, PlaneRasterizer.BACKGROUND,
                profile.layoutX(), 0, width, height, backdropColour(built));
        if (width > PLANE_WIDTH) {
            // Rows drawn from X 0: repeat the first plane width across the image.
            for (int row = profile.fixedTop(); row < Math.min(height, profile.fixedBottom()); row++) {
                for (int x = PLANE_WIDTH; x < width; x++) {
                    argb[row * width + x] = argb[row * width + x % PLANE_WIDTH];
                }
            }
        }
        return new Backdrop(new Picture(width, height, argb), profile.bands().apply(height));
    }

    @Override
    public Picture overview(int zone, int act, int maxHeight) {
        if (maxHeight < 1) {
            throw new IllegalArgumentException("maxHeight must be positive: " + maxHeight);
        }
        Profile profile = profile(zone, act);
        if (profile == null) {
            return null;
        }
        Built built = build(profile);
        Bounds bounds = playableBounds(built.level(), profile.foregroundEndX());
        int divisor = overviewDivisor(bounds, maxHeight);
        int[] argb = PlaneRasterizer.compositeOverview(built.level(), built, bounds.x(), bounds.y(),
                bounds.width(), bounds.height(), divisor, backdropColour(built), profile.backgroundWrap());
        return new Picture(Math.ceilDiv(bounds.width(), divisor), Math.ceilDiv(bounds.height(), divisor), argb);
    }

    /**
     * The smallest whole divisor that fits {@code bounds} into {@code maxHeight} rows and
     * {@code SceneImage}'s 4096 columns (each output side is the bounds side divided, rounded up).
     */
    static int overviewDivisor(Bounds bounds, int maxHeight) {
        int rows = Math.min(maxHeight, MAX_IMAGE_SIDE);
        return Math.max(Math.ceilDiv(bounds.height(), rows), Math.ceilDiv(bounds.width(), MAX_IMAGE_SIDE));
    }

    /**
     * Where the camera can show: its limits from the level size table plus one native screen,
     * clipped to the foreground layout (and {@code foregroundEndX}) and then to the layout
     * blocks that hold anything. The table often holds open limits ($6000 wide, $1000 tall, or
     * a negative top for levels that wrap vertically), so the layout's own extent decides there.
     */
    static Bounds playableBounds(Sonic3kLevel level, int foregroundEndX) {
        int blockSize = level.getBlockPixelSize();
        int columns = level.getLayerWidthBlocks(PlaneRasterizer.FOREGROUND);
        int rows = level.getLayerHeightBlocks(PlaneRasterizer.FOREGROUND);
        int left = Math.max(0, level.getMinX());
        int top = Math.max(0, level.getMinY());
        int right = Math.min(Math.min(columns * blockSize, foregroundEndX),
                Math.max(left + 1, level.getMaxX() + SCREEN_WIDTH));
        int bottom = Math.min(rows * blockSize, Math.max(top + 1, level.getMaxY() + SCREEN_HEIGHT));
        Map map = level.getMap();
        int firstColumn = Integer.MAX_VALUE;
        int lastColumn = -1;
        int firstRow = Integer.MAX_VALUE;
        int lastRow = -1;
        for (int row = top / blockSize; row <= (bottom - 1) / blockSize; row++) {
            for (int column = left / blockSize; column <= (right - 1) / blockSize; column++) {
                if ((map.getValue(PlaneRasterizer.FOREGROUND, column, row) & 0xFF) != 0) {
                    firstColumn = Math.min(firstColumn, column);
                    lastColumn = Math.max(lastColumn, column);
                    firstRow = Math.min(firstRow, row);
                    lastRow = Math.max(lastRow, row);
                }
            }
        }
        if (lastColumn >= 0) {
            left = Math.max(left, firstColumn * blockSize);
            right = Math.min(right, (lastColumn + 1) * blockSize);
            top = Math.max(top, firstRow * blockSize);
            bottom = Math.min(bottom, (lastRow + 1) * blockSize);
        }
        return new Bounds(left, top, right - left, bottom - top);
    }

    private static int backdropColour(Built built) {
        return PlaneRasterizer.argb(built.level().getPalette(BACKDROP_LINE).getColor(0));
    }

    private Built build(Profile profile) {
        if (last != null && last.zone() == profile.zone() && last.act() == profile.act()) {
            return last;
        }
        try {
            if (game == null) {
                game = new Sonic3k(rom);
            }
            if (reader == null) {
                reader = RomByteReader.fromRom(rom);
            }
            Sonic3kLevel level = game.buildDetachedLevel(profile.zone(), profile.act(), profile.mode());
            Pattern[] tiles = presentationTiles(level, profile);
            Pattern[] startTiles = null;
            int startTilesBeforeX = 0;
            if (profile.zone() == Sonic3kZoneIds.ZONE_AIZ && profile.act() == 1) {
                // AnimateTiles_AIZ2 (sonic3k.asm:53953): left of camera X $1C0 it DMAs
                // ArtUnc_AniAIZ2_FirstTree to tile $CA every frame, over the AniPLC slots it
                // animates from $1C0 on. Those columns are what such a camera can show.
                startTiles = tiles.clone();
                upload(startTiles, new Strip(Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_ADDR,
                        Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_SIZE,
                        Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_DEST_TILE));
                startTilesBeforeX = AIZ2_FIRST_TREE_CAMERA_X + SCREEN_WIDTH;
            }
            last = new Built(profile.zone(), profile.act(), level, tiles, startTiles, startTilesBeforeX);
            return last;
        } catch (IOException e) {
            throw new IllegalStateException("Zone " + profile.zone() + " act " + (profile.act() + 1)
                    + " art unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * The level's tiles in a VRAM-sized private array, with its AniPLC scripts at their first
     * frame as the zone's animator primes them on load (LBZ1 adds {@code AniPLC_LBZSpec}) and
     * the zone's direct-DMA strips. The level and any live atlas are left alone.
     */
    private Pattern[] presentationTiles(Sonic3kLevel level, Profile profile) {
        List<AniPlcScriptState> scripts = new ArrayList<>();
        int aniPlc = Sonic3kPatternAnimator.resolveAniPlcAddr(profile.zone(), profile.act());
        if (aniPlc >= 0) {
            scripts.addAll(AniPlcParser.parseScripts(reader, aniPlc));
        }
        if (profile.zone() == Sonic3kZoneIds.ZONE_LBZ && profile.act() == 0) {
            scripts.addAll(AniPlcParser.parseScripts(reader, Sonic3kConstants.ANIPLC_LBZ_SPEC_ADDR));
        }
        int count = Math.max(VRAM_TILES, level.getPatternCount());
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = i < level.getPatternCount() ? level.getPattern(i) : new Pattern();
        }
        for (AniPlcScriptState script : scripts) {
            script.primeInto(patterns);
        }
        for (Strip strip : profile.strips()) {
            upload(patterns, strip);
        }
        return patterns;
    }

    /** Copies a strip of uncompressed ROM art into the tile array, as a DMA to VRAM would. */
    private void upload(Pattern[] patterns, Strip strip) {
        byte[] data = reader.slice(strip.address(), strip.bytes());
        for (int tile = 0; tile < strip.bytes() / Pattern.PATTERN_SIZE_IN_ROM; tile++) {
            byte[] bytes = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            System.arraycopy(data, tile * Pattern.PATTERN_SIZE_IN_ROM, bytes, 0, bytes.length);
            Pattern pattern = new Pattern();
            pattern.fromSegaFormat(bytes);
            patterns[strip.destinationTile() + tile] = pattern;
        }
    }
}
