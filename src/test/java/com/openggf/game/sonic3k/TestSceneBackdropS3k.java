package com.openggf.game.sonic3k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameId;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Pattern;
import com.openggf.level.render.PlaneRasterizer;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelStage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * S3K zone backdrops and level overviews for mod scenes: every supported act builds from the
 * ROM alone (no GameServices, session or configuration lookup on the building thread), its
 * bands cover the image, its pictures are opaque and varied, the animated-tile fixups change
 * the picture, and overviews are box-averaged crops of the playable area. Writes PNGs to
 * {@code target/scene-backdrops/} for visual review.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSceneBackdropS3k {
    /** A supported act, its backdrop size and its playable foreground limit. */
    private record Zone(String name, int zone, int act, int width, int height, int foregroundEndX) {
    }

    private static final List<Zone> SUPPORTED = List.of(
            new Zone("aiz1", 0, 0, 512, 0x220, Integer.MAX_VALUE),
            new Zone("aiz2", 0, 1, 512, 0x280, Integer.MAX_VALUE),
            new Zone("hcz1", 1, 0, 512, 0x400, Integer.MAX_VALUE),
            new Zone("lbz1", 6, 0, 0x600, 0x180, 0x4000),
            new Zone("ssz1", 10, 0, 512, 0x500, Integer.MAX_VALUE));

    /** Overview height used by the mod's act map. */
    private static final int MAP_HEIGHT = 196;

    /** Stage size used by the mod's fights: its screen width and the clear height above its ground row. */
    private static final int STAGE_WIDTH = 400;
    private static final int STAGE_HEADROOM = 100;
    private static final int STAGE_RISE = 24;
    private static final int STAGE_GROUND_ROW = 134;
    private static final int STAGE_HEIGHT = 224;

    private record Built(Map<String, SceneBackdrop> backdrops, Map<String, SceneImage> overviews,
            Map<String, List<SceneLevelStage>> stages, Map<String, List<SceneImage>> foregrounds) {
    }

    /**
     * Builds every supported backdrop and overview on a thread where any ambient service,
     * session or configuration lookup throws.
     */
    private static Built buildIsolated(Rom rom) throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            return executor.submit(() -> {
                try (var services = Mockito.mockStatic(GameServices.class, invocation -> {
                    throw new AssertionError("Zone art resolved GameServices." + invocation.getMethod().getName());
                });
                     var sessions = Mockito.mockStatic(SessionManager.class, invocation -> {
                         throw new AssertionError("Zone art resolved SessionManager."
                                 + invocation.getMethod().getName());
                     });
                     var config = Mockito.mockStatic(SonicConfigurationService.class, invocation -> {
                         throw new AssertionError("Zone art read configuration: "
                                 + invocation.getMethod().getName());
                     })) {
                    SceneRomArt art = SceneRomArtFactory.create(rom, GameId.S3K, () -> null, () -> null,
                            new Sonic3kZoneArt(rom));
                    Map<String, SceneBackdrop> backdrops = new LinkedHashMap<>();
                    Map<String, SceneImage> overviews = new LinkedHashMap<>();
                    Map<String, List<SceneLevelStage>> stages = new LinkedHashMap<>();
                    Map<String, List<SceneImage>> foregrounds = new LinkedHashMap<>();
                    for (Zone zone : SUPPORTED) {
                        SceneBackdrop backdrop = art.zoneBackdrop(zone.zone(), zone.act());
                        assertNotNull(backdrop, zone.name());
                        assertSame(backdrop, art.zoneBackdrop(zone.zone(), zone.act()), zone.name() + " cached");
                        backdrops.put(zone.name(), backdrop);
                        long start = System.nanoTime();
                        SceneImage overview = art.levelOverview(zone.zone(), zone.act(), MAP_HEIGHT);
                        System.out.printf("%s overview %dx%d in %d ms%n", zone.name(),
                                overview == null ? 0 : overview.width(), overview == null ? 0 : overview.height(),
                                (System.nanoTime() - start) / 1_000_000);
                        assertNotNull(overview, zone.name() + " overview");
                        assertSame(overview, art.levelOverview(zone.zone(), zone.act(), MAP_HEIGHT),
                                zone.name() + " overview cached");
                        overviews.put(zone.name(), overview);
                        start = System.nanoTime();
                        List<SceneLevelStage> found = art.levelStages(zone.zone(), zone.act(), STAGE_WIDTH,
                                STAGE_HEADROOM, STAGE_RISE);
                        System.out.printf("%s: %d stages in %d ms%n", zone.name(), found.size(),
                                (System.nanoTime() - start) / 1_000_000);
                        assertSame(found, art.levelStages(zone.zone(), zone.act(), STAGE_WIDTH, STAGE_HEADROOM, STAGE_RISE),
                                zone.name() + " stages cached");
                        stages.put(zone.name(), found);
                        List<SceneImage> fronts = new java.util.ArrayList<>();
                        for (SceneLevelStage stage : found) {
                            fronts.add(art.levelForeground(zone.zone(), zone.act(), stage.x(),
                                    stage.floorY() - STAGE_GROUND_ROW, STAGE_WIDTH, STAGE_HEIGHT));
                        }
                        foregrounds.put(zone.name(), fronts);
                    }
                    return new Built(backdrops, overviews, stages, foregrounds);
                }
            }).get(300, TimeUnit.SECONDS);
        }
    }

    @Test
    void supportedZonesBuildFromTheRomAlone() throws Exception {
        Map<String, SceneBackdrop> built = buildIsolated(TestEnvironment.currentRom()).backdrops();
        Path dir = Path.of("target", "scene-backdrops");
        Files.createDirectories(dir);
        for (Zone zone : SUPPORTED) {
            SceneBackdrop backdrop = built.get(zone.name());
            writePng(backdrop.image(), dir.resolve(zone.name() + ".png").toFile());
            assertEquals(zone.width(), backdrop.image().width(), zone.name() + " width");
            assertEquals(zone.height(), backdrop.image().height(), zone.name() + " height");

            int next = 0;
            for (SceneBackdrop.Band band : backdrop.bands()) {
                assertEquals(next, band.top(), zone.name() + " bands are contiguous");
                assertTrue(band.speed() > 0 && band.speed() <= 1, zone.name() + " speed " + band.speed());
                assertTrue(band.drift() >= 0 && band.drift() < 1, zone.name() + " drift " + band.drift());
                next += band.height();
            }
            assertEquals(zone.height(), next, zone.name() + " bands reach the bottom");

            Set<Integer> colours = new HashSet<>();
            for (int pixel : backdrop.image().pixels()) {
                assertEquals(0xFF, pixel >>> 24, zone.name() + " is opaque");
                colours.add(pixel);
            }
            // AIZ2 silhouettes use ten colours and SSZ1 sky seven; a failed decode gives one or two.
            assertTrue(colours.size() >= 6, zone.name() + " has " + colours.size() + " colours");
        }
    }

    @Test
    void bandsCarryTheDeformationRates() throws Exception {
        Map<String, SceneBackdrop> built = buildIsolated(TestEnvironment.currentRom()).backdrops();
        // AIZ1_Deform: top cloud word 11/2 * (camX - $1300)/32 plus six $2000 drifts a frame.
        assertRate(built.get("aiz1"), 0, 11 / 64.0, 0.75);
        assertRate(built.get("aiz1"), 0x180, 9 / 256.0, 0);
        assertRate(built.get("aiz1"), 0x1B0, 20 / 32.0, 0);
        // AIZ2_BGDeformMake: the far glowing trees are level 0, half camera speed.
        assertRate(built.get("aiz2"), 0x190, 0.5, 0);
        assertRate(built.get("aiz2"), 0x068, 50 / 64.0, 0);
        // HCZ1: far wall at the slowest cave level; waterline ramp starts at 1.
        assertRate(built.get("hcz1"), 0x60, 1 / 16.0, 0);
        assertRate(built.get("hcz1"), 0x1A0, 1, 0);
        assertRate(built.get("hcz1"), 0x300, 1 / 16.0, 0);
        // LBZ1: sky at camX/16, the last water band at 19/128.
        assertRate(built.get("lbz1"), 0, 1 / 16.0, 0);
        assertRate(built.get("lbz1"), 0x100, 19 / 128.0, 0);
        // SSZ1 cloud sea: nearest band 31/64 with sixteen $500 drift units.
        assertRate(built.get("ssz1"), 0x330, 31 / 64.0, 16 * 0x500 / 65536.0);
        assertRate(built.get("ssz1"), 0, 1 / 64.0, 0x500 / 65536.0);
    }

    @Test
    void levelKitsCoverEveryStockZoneFromTheRomAlone() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                try (var services = Mockito.mockStatic(GameServices.class, invocation -> {
                    throw new AssertionError("Level kit resolved GameServices." + invocation.getMethod().getName());
                }); var sessions = Mockito.mockStatic(SessionManager.class, invocation -> {
                    throw new AssertionError("Level kit resolved SessionManager." + invocation.getMethod().getName());
                })) {
                    SceneRomArt art = SceneRomArtFactory.create(rom, GameId.S3K, () -> null, () -> null,
                            new Sonic3kZoneArt(rom));
                    // Pictured acts (AIZ1) and plain ones (Marble Garden, IceCap, Lava Reef, Hidden Palace).
                    int[][] acts = {{0, 0}, {2, 0}, {5, 0}, {9, 1}, {22, 1}};
                    for (int[] act : acts) {
                        com.openggf.level.render.TestDetachedStockScenePictures.checkKit(art, act[0], act[1]);
                    }
                    assertEquals(25, art.levelKit(0, 0).backdrop().bands().size(),
                            "pictured acts keep their zone backdrop's bands");
                    assertNull(art.levelKit(13, 0), "the intro/ending zone has no kit");
                    assertNull(art.levelKit(0, 2), "no act 3");
                    return null;
                }
            }).get(300, TimeUnit.SECONDS);
        }
    }

    @Test
    void unsupportedZonesAndGamesHaveNoBackdrop() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        SceneRomArt art = SceneRomArtFactory.create(rom, GameId.S3K, () -> null, () -> null,
                new Sonic3kZoneArt(rom));
        assertNull(art.zoneBackdrop(2, 0), "MGZ1");
        assertNull(art.zoneBackdrop(10, 1), "SSZ2");
        assertNull(art.zoneBackdrop(0, 2), "no act 3");
        assertNull(art.zoneBackdrop(-1, 0), "negative zone");
        assertNull(art.levelOverview(2, 0, MAP_HEIGHT), "MGZ1 overview");
        assertNull(art.levelOverview(10, 1, MAP_HEIGHT), "SSZ2 overview");
        assertThrows(IllegalArgumentException.class, () -> art.levelOverview(1, 0, 0), "maxHeight 0");
        assertTrue(art.levelStages(2, 0, STAGE_WIDTH, STAGE_HEADROOM, STAGE_RISE).isEmpty(), "MGZ1 stages");
        assertNull(art.levelForeground(2, 0, 0, 0, STAGE_WIDTH, STAGE_HEIGHT), "MGZ1 foreground");
        for (Zone zone : SUPPORTED) {
            assertTrue(art.hasZonePictures(zone.zone(), zone.act()), zone.name() + " is supported");
        }
        assertFalse(art.hasZonePictures(2, 0), "MGZ1 is asked, not guessed");
        assertFalse(art.hasZonePictures(10, 1), "SSZ2");
        assertFalse(art.hasZonePictures(-1, 0), "negative zone");
        SceneRomArt none = SceneRomArtFactory.create(rom, GameId.S3K, () -> null, () -> null);
        assertFalse(none.hasZonePictures(0, 0), "no source");
        assertNull(none.zoneBackdrop(0, 0), "no source");
        assertNull(none.levelOverview(0, 0, MAP_HEIGHT), "no source overview");
        assertFalse(none.hasTitleCard(0, 0), "no source");
        assertNull(none.titleCard(0, 0), "no source title card");
    }

    @Test
    void rawTilesDecodeTheSlotMachineFacesColumnByColumn() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        SceneRomArt art = SceneRomArtFactory.create(rom, GameId.S3K, () -> null, () -> null);
        int faces = 0x158CAE;    // ArtUnc_SlotOptions: eight 4x4-tile faces, $200 bytes each
        int[] palette = art.palette(0xA9C7C, 16);   // Pal_Slot_Special, line 0
        byte[] bytes = art.read(faces, 8 * 0x200);
        for (int face = 0; face < 8; face++) {
            SceneImage image = art.tiles(faces, com.openggf.mods.scene.RomSpriteRequest.Compression.UNCOMPRESSED,
                    face * 16, 4, 4, true, palette);
            assertEquals(32, image.width());
            assertEquals(32, image.height());
            // The reel copy's own reading: 64 nibbles a tile, tiles a column of four at a time.
            for (int i = 0; i < 0x400; i++) {
                int tile = i / 64;
                int x = (tile / 4) * 8 + i % 8;
                int y = (tile % 4) * 8 + (i % 64) / 8;
                int b = bytes[face * 0x200 + i / 2] & 0xFF;
                int colour = (i & 1) == 0 ? b >> 4 : b & 15;
                assertEquals(colour == 0 ? 0 : palette[colour], image.pixel(x, y), "face " + face + " at " + x + "," + y);
            }
        }
        SceneImage rows = art.tiles(faces, com.openggf.mods.scene.RomSpriteRequest.Compression.UNCOMPRESSED, 0, 4, 4,
                false, palette);
        SceneImage columns = art.tiles(faces, com.openggf.mods.scene.RomSpriteRequest.Compression.UNCOMPRESSED, 0, 4,
                4, true, palette);
        assertEquals(columns.pixel(0, 8), rows.pixel(8, 0), "tile 1 goes right of tile 0 in row order");
        assertThrows(IllegalArgumentException.class, () -> art.tiles(faces,
                com.openggf.mods.scene.RomSpriteRequest.Compression.UNCOMPRESSED, 0, 0, 4, true, palette));
        assertThrows(IllegalArgumentException.class, () -> art.tiles(faces,
                com.openggf.mods.scene.RomSpriteRequest.Compression.UNCOMPRESSED, 0, 4, 4, true, new int[4]));
        // Compressed art: the Bloominator's Kosinski Moduled tiles, first two.
        SceneImage bloom = art.tiles(0x367DCA, com.openggf.mods.scene.RomSpriteRequest.Compression.KOSINSKI_MODULED,
                0, 2, 1, false, art.palette(0x0A8B7C, 16));
        assertEquals(16, bloom.width());
    }

    @Test
    void titleCardsHaveTheirFourElements() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        SceneRomArt art = SceneRomArtFactory.create(rom, GameId.S3K, () -> null, () -> null,
                new Sonic3kZoneArt(rom));
        Path dir = Files.createDirectories(Path.of("target", "scene-backdrops"));
        Map<String, int[]> zoneNames = new LinkedHashMap<>();
        for (Zone zone : List.of(SUPPORTED.get(0), SUPPORTED.get(2), SUPPORTED.get(3), SUPPORTED.get(4))) {
            assertTrue(art.hasTitleCard(zone.zone(), zone.act()), zone.name());
            com.openggf.mods.scene.SceneSpriteSet card = art.titleCard(zone.zone(), zone.act());
            assertNotNull(card, zone.name());
            assertSame(card, art.titleCard(zone.zone(), zone.act()), zone.name() + " cached");
            assertEquals(4, card.frameCount(), zone.name() + ": banner, zone name, ZONE, act");
            for (int frame = 0; frame < 4; frame++) {
                SceneImage image = card.frame(frame).image();
                writePng(image, dir.resolve("titlecard-" + zone.name() + "-" + frame + ".png").toFile());
                Set<Integer> colours = new HashSet<>();
                int opaque = 0;
                for (int pixel : image.pixels()) {
                    if ((pixel >>> 24) != 0) {
                        opaque++;
                        colours.add(pixel);
                    }
                }
                assertTrue(image.width() > 8 && opaque > 40, zone.name() + " frame " + frame + " is drawn: "
                        + image.width() + "x" + image.height() + ", " + opaque + " pixels");
                assertTrue(colours.size() >= 2, zone.name() + " frame " + frame + " has its colours");
            }
            zoneNames.put(zone.name(), card.frame(1).image().pixels());
        }
        assertFalse(java.util.Arrays.equals(zoneNames.get("aiz1"), zoneNames.get("hcz1")),
                "each zone's name comes from its own letters");
        assertFalse(art.hasTitleCard(13, 0), "no card for a competition stage");
        assertFalse(art.hasTitleCard(0, 2), "no act 3");
        assertNull(art.titleCard(13, 0));
    }

    /**
     * Break-on-purpose for the tile fixups: rasterising the raw level's own tiles (no AniPLC
     * frame, no strips) gives a different picture in every zone that needs them.
     */
    @Test
    void animatedTileFixupsChangeThePicture() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        Map<String, SceneBackdrop> built = buildIsolated(rom).backdrops();
        Sonic3k game = new Sonic3k(rom);
        assertTrue(differingPixels(game, built.get("aiz2"), 0, 1, 0, 0x280) > 1000, "AIZ2 AniPLC frame 0");
        assertTrue(differingPixels(game, built.get("hcz1"), 1, 0, 0, 0x400) > 1000, "HCZ1 waterline strips");
        assertTrue(differingPixels(game, built.get("lbz1"), 6, 0, 0, 0xD0) > 100, "LBZ1 scroll tiles");
    }

    /** Pixels in the first 512 columns of {@code rows} that differ from the raw level's tiles. */
    private static int differingPixels(Sonic3k game, SceneBackdrop backdrop, int zone, int act, int layoutX,
            int rows) throws Exception {
        Sonic3kLevel level = game.buildDetachedLevel(zone, act, Sonic3kLoadBootstrap.Mode.NORMAL);
        PlaneRasterizer.TileSource tiles = PlaneRasterizer.TileSource.of(levelTiles(level, 0x800));
        int[] raw = PlaneRasterizer.rasterize(level, tiles, PlaneRasterizer.BACKGROUND, layoutX, 0, 512, rows,
                PlaneRasterizer.argb(level.getPalette(2).getColor(0)));
        int differing = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < 512; x++) {
                if (raw[y * 512 + x] != backdrop.image().pixel(x, y)) {
                    differing++;
                }
            }
        }
        return differing;
    }

    @Test
    void overviewsShrinkThePlayableAreaByTheSmallestDivisor() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        Map<String, SceneImage> overviews = buildIsolated(rom).overviews();
        Sonic3k game = new Sonic3k(rom);
        Path dir = Path.of("target", "scene-backdrops");
        Files.createDirectories(dir);
        for (Zone zone : SUPPORTED) {
            SceneImage overview = overviews.get(zone.name());
            writePng(overview, dir.resolve(zone.name() + "-overview.png").toFile());
            Sonic3kLevel level = game.buildDetachedLevel(zone.zone(), zone.act(), zone.zone() == 0 && zone.act() == 0
                    ? Sonic3kLoadBootstrap.Mode.SKIP_INTRO : Sonic3kLoadBootstrap.Mode.NORMAL);
            Sonic3kZoneArt.Bounds bounds = Sonic3kZoneArt.playableBounds(level, zone.foregroundEndX());
            int divisor = Sonic3kZoneArt.overviewDivisor(bounds, MAP_HEIGHT);
            System.out.printf("%s bounds x=%X y=%X %dx%d divisor %d -> %dx%d%n", zone.name(), bounds.x(), bounds.y(),
                    bounds.width(), bounds.height(), divisor, overview.width(), overview.height());
            assertTrue(bounds.width() > 0 && bounds.height() > 0
                    && bounds.x() + bounds.width() <= level.getLayerWidthBlocks(0) * 128
                    && bounds.y() + bounds.height() <= level.getLayerHeightBlocks(0) * 128
                    && bounds.x() + bounds.width() <= zone.foregroundEndX(),
                    zone.name() + " bounds inside the playable foreground");
            assertTrue(bounds.x() >= Math.max(0, level.getMinX()), zone.name() + " starts at the camera's left limit");
            assertEquals(Math.ceilDiv(bounds.width(), divisor), overview.width(), zone.name() + " width");
            assertEquals(Math.ceilDiv(bounds.height(), divisor), overview.height(), zone.name() + " height");
            assertTrue(overview.height() <= MAP_HEIGHT && overview.width() <= 4096, zone.name() + " fits");
            assertTrue(divisor == 1 || Math.ceilDiv(bounds.height(), divisor - 1) > MAP_HEIGHT
                    || Math.ceilDiv(bounds.width(), divisor - 1) > 4096, zone.name() + " divisor is the smallest");
            Set<Integer> colours = new HashSet<>();
            for (int pixel : overview.pixels()) {
                assertEquals(0xFF, pixel >>> 24, zone.name() + " overview is opaque");
                colours.add(pixel);
            }
            assertTrue(colours.size() >= 64, zone.name() + " overview has " + colours.size() + " colours");
        }
        // The AIZ1 overview starts at the main level, after the intro beach.
        Sonic3kLevel aiz1 = game.buildDetachedLevel(0, 0, Sonic3kLoadBootstrap.Mode.SKIP_INTRO);
        assertTrue(Sonic3kZoneArt.playableBounds(aiz1, Integer.MAX_VALUE).x() >= 0x1300, "AIZ1 crop skips the intro");
    }

    /** Box averaging: a divided composite is the rounded mean of the full-size composite's blocks. */
    @Test
    void overviewPixelsAreBlockMeans() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        Sonic3kLevel level = new Sonic3k(rom).buildDetachedLevel(1, 0, Sonic3kLoadBootstrap.Mode.NORMAL);
        PlaneRasterizer.TileSource tiles = PlaneRasterizer.TileSource.of(levelTiles(level, 0x800));
        PlaneRasterizer.BackgroundWrap wrap = PlaneRasterizer.BackgroundWrap.layout(level);
        int w = 61;
        int h = 37;
        // A window where the foreground is partly transparent, so both planes contribute.
        int x = -1;
        int y = -1;
        int[] foreground = null;
        search:
        for (int wy = 0x100; wy < 0xA00; wy += 0x80) {
            for (int wx = 0x100; wx < 0x3000; wx += 0x100) {
                int[] fg = PlaneRasterizer.rasterize(level, tiles, PlaneRasterizer.FOREGROUND, wx, wy, w, h,
                        0);
                int clear = 0;
                for (int p : fg) {
                    if (p == 0) {
                        clear++;
                    }
                }
                if (clear > fg.length / 5 && clear < fg.length * 4 / 5) {
                    x = wx;
                    y = wy;
                    foreground = fg;
                    break search;
                }
            }
        }
        assertTrue(foreground != null, "HCZ1 has a window mixing both planes");
        int[] full = PlaneRasterizer.compositeOverview(level, tiles, x, y, w, h, 1, 0xFF000000, wrap);
        int[] background = PlaneRasterizer.rasterize(level, tiles, PlaneRasterizer.BACKGROUND, x % 512,
                y % 1024, w, h, 0xFF000000);
        for (int i = 0; i < full.length; i++) {
            assertEquals(foreground[i] != 0 ? foreground[i] : background[i], full[i], "composite pixel " + i);
        }
        int[] shrunk = PlaneRasterizer.compositeOverview(level, tiles, x, y, w, h, 4, 0xFF000000, wrap);
        assertEquals(16 * 10, shrunk.length);
        for (int oy = 0; oy < 10; oy++) {
            for (int ox = 0; ox < 16; ox++) {
                long r = 0;
                long g = 0;
                long b = 0;
                int n = 0;
                for (int sy = oy * 4; sy < Math.min(h, oy * 4 + 4); sy++) {
                    for (int sx = ox * 4; sx < Math.min(w, ox * 4 + 4); sx++) {
                        int p = full[sy * w + sx];
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                        n++;
                    }
                }
                int expected = 0xFF000000 | (int) ((r + n / 2) / n) << 16 | (int) ((g + n / 2) / n) << 8
                        | (int) ((b + n / 2) / n);
                assertEquals(expected, shrunk[oy * 16 + ox], "block " + ox + "," + oy);
            }
        }
    }

    /** The level's tiles, padded with blank ones up to {@code minimum} like empty VRAM. */
    private static Pattern[] levelTiles(Sonic3kLevel level, int minimum) {
        Pattern[] tiles = new Pattern[Math.max(minimum, level.getPatternCount())];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = i < level.getPatternCount() ? level.getPattern(i) : new Pattern();
        }
        return tiles;
    }

    /**
     * Stages are floor the level's art agrees with: just under each column's floor row the
     * foreground is mostly solid art. (Headroom is not checked against the art: Angel Island's
     * cliff walls are opaque foreground art with open space in front of them.) The floor stays
     * within the rise asked for, and the acts that swap art or layout later keep their stages
     * before that. Composites (backdrop, foreground, the floor marked) are written for review.
     */
    @Test
    void levelStagesSitOnTheFloorTheArtShows() throws Exception {
        Built built = buildIsolated(TestEnvironment.currentRom());
        Path dir = Path.of("target", "scene-backdrops", "stages");
        Files.createDirectories(dir);
        for (Zone zone : SUPPORTED) {
            List<SceneLevelStage> stages = built.stages().get(zone.name());
            List<SceneImage> fronts = built.foregrounds().get(zone.name());
            assertTrue(stages.size() >= 4, zone.name() + " has " + stages.size() + " stages");
            int stageEnd = zone.name().equals("aiz2") ? 0x3C00 : zone.name().equals("lbz1") ? 0x3B60 : Integer.MAX_VALUE;
            int previousEnd = Integer.MIN_VALUE;
            for (int i = 0; i < stages.size(); i++) {
                SceneLevelStage stage = stages.get(i);
                String where = zone.name() + " stage " + i + " at x " + stage.x() + ", floor " + stage.floorY();
                assertTrue(stage.x() >= previousEnd, where + ": stages are ordered and apart");
                previousEnd = stage.x() + stage.width();
                assertTrue(stage.width() >= STAGE_WIDTH, where + " width " + stage.width());
                assertTrue(stage.x() + stage.width() <= stageEnd, where + " ends before the act's art changes");
                int[] floor = stage.floor();
                int highest = java.util.Arrays.stream(floor).min().orElseThrow();
                int lowest = java.util.Arrays.stream(floor).max().orElseThrow();
                assertTrue(lowest - highest <= STAGE_RISE + 16, where + ": floor rows " + highest + "-" + lowest);
                SceneImage front = fronts.get(i);
                assertEquals(STAGE_WIDTH, front.width());
                assertEquals(STAGE_HEIGHT, front.height());
                int top = stage.floorY() - STAGE_GROUND_ROW;
                writePng(composite(built.backdrops().get(zone.name()), front, floor, top),
                        dir.resolve(zone.name() + "-" + i + ".png").toFile());
                double below = opaqueShare(front, floor, top, 2, 10);
                assertTrue(below >= 0.6, where + ": art under the floor is " + Math.round(below * 100) + "% solid");
            }
        }
    }

    /** The share of opaque pixels in rows {@code from .. to - 1} relative to each column's floor. */
    private static double opaqueShare(SceneImage image, int[] floor, int top, int from, int to) {
        int[] pixels = image.pixels();
        int opaque = 0;
        int total = 0;
        for (int x = 0; x < image.width(); x++) {
            for (int y = floor[x] - top + from; y < floor[x] - top + to; y++) {
                if (y < 0 || y >= image.height()) {
                    continue;
                }
                total++;
                if ((pixels[y * image.width() + x] >>> 24) != 0) {
                    opaque++;
                }
            }
        }
        return total == 0 ? 0 : opaque / (double) total;
    }

    /** The stage's foreground over the top-left of the backdrop, with each column's floor marked. */
    private static SceneImage composite(SceneBackdrop backdrop, SceneImage front, int[] floor, int top) {
        int w = front.width();
        int h = front.height();
        int[] out = new int[w * h];
        SceneImage back = backdrop.image();
        int[] fronts = front.pixels();
        int[] backs = back.pixels();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = fronts[y * w + x];
                if ((pixel >>> 24) == 0) {
                    pixel = backs[Math.min(back.height() - 1, y) * back.width() + x % back.width()];
                }
                out[y * w + x] = y == floor[x] - top - 1 && x % 4 < 2 ? 0xFFFF00FF : pixel;
            }
        }
        return new SceneImage(w, h, out);
    }

    private static void assertRate(SceneBackdrop backdrop, int row, double speed, double drift) {
        for (SceneBackdrop.Band band : backdrop.bands()) {
            if (row >= band.top() && row < band.top() + band.height()) {
                assertEquals(speed, band.speed(), 1e-12, "speed at row 0x" + Integer.toHexString(row));
                assertEquals(drift, band.drift(), 1e-12, "drift at row 0x" + Integer.toHexString(row));
                return;
            }
        }
        throw new AssertionError("No band covers row 0x" + Integer.toHexString(row));
    }

    private static void writePng(SceneImage picture, File file) throws Exception {
        int w = picture.width();
        int h = picture.height();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, w, h, picture.pixels(), 0, w);
        ImageIO.write(image, "png", file);
    }
}
