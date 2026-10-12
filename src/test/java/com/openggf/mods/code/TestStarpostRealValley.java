package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.ZoneKey;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.level.Block;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.SolidTile;
import com.openggf.mods.testing.ModTestKit;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.modsdk.GgfModCli;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Starpost Valley's valley as a real Sonic 3&amp;K act (design P1): the mod's placeholder zone, filled at
 * load time with Sonic 1 Green Hill blocks re-encoded as S3K level data, played by the engine's own
 * Sonic, Tails and Knuckles. The mod is compiled and packaged through {@code ggfmod} and launched
 * through the production module resolver, so the content patch / explicit patch order is the real
 * one. Needs both Sonic 3&amp;K and Sonic 1; skipped without Sonic 1.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarpostRealValley {
    private static final Path PROJECT = Path.of("examples/starpost-valley");
    private static final ZoneKey.Mod VALLEY = new ZoneKey.Mod("starpost-valley", "valley");
    private static final int[] COLUMNS = {13, 45, 60, 60, 60, 60, 45, 3, 45, 53, 38, 1, 16};
    private static final int SKY = 128;
    private static final int LOOP_X = 9 * 256;

    @TempDir
    static Path temp;
    private static Path repository;

    private SharedLevel bootstrap;
    private ModTestKit kit;

    @BeforeAll
    static void packageMod() throws Exception {
        repository = Files.createDirectories(temp.resolve("mods"));
        Path classes = Files.createDirectories(temp.resolve("classes"));
        List<String> args = new ArrayList<>(List.of("--release", "21", "-nowarn", "-cp",
                System.getProperty("java.class.path"), "-d", classes.toString()));
        try (Stream<Path> files = Files.walk(PROJECT.resolve("src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        Path resources = PROJECT.resolve("src/main/resources");
        try (Stream<Path> files = Files.walk(resources)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                Path target = classes.resolve(resources.relativize(p).toString());
                Files.createDirectories(target.getParent());
                Files.copy(p, target);
            }
        }
        assertEquals(0, GgfModCli.run(new String[] {"package", "--input", classes.toString(), "--out",
                repository.resolve("starpost-valley.jar").toString()}, System.out));
    }

    @AfterEach
    void close() throws Exception {
        try {
            if (kit != null) {
                kit.close();
            }
        } finally {
            if (bootstrap != null) {
                bootstrap.dispose();
            }
        }
    }

    /** Launches the valley act with {@code character} alone at {@code aspect}. */
    private HeadlessTestFixture launch(String character, WidescreenAspect aspect) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        assumeTrue(LogicalRomResolver.fromRomManager(GameServices.rom()).isAvailable(LogicalRom.S1),
                "Sonic 1 was not supplied");
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        kit = ModTestKit.openTrusted(repository, temp.resolve("storage-" + System.nanoTime()),
                LogicalRomResolver.fromRomManager(GameServices.rom()),
                SonicConfigurationService.createStandalone(), List.of());
        GameModule base = GameServices.module();
        GameModule effective = kit.launch(base, new GameplayLaunchRequest("s3k", character, List.of()));
        int zone = effective.getZoneRegistry().resolveZoneKey(VALLEY).orElseThrow();
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, 0).build();
        GameServices.audio().stopMusic();
        assertEquals(java.util.Map.of(), kit.findings(), "the mod's fault boundary caught nothing");
        return fixture;
    }

    static Stream<Arguments> teams() {
        List<Arguments> result = new ArrayList<>();
        for (String character : new String[] {"sonic", "tails", "knuckles"}) {
            for (WidescreenAspect aspect : new WidescreenAspect[] {WidescreenAspect.NATIVE_4_3,
                    WidescreenAspect.WIDE_16_9}) {
                result.add(Arguments.of(character, aspect));
            }
        }
        return result.stream();
    }

    @ParameterizedTest(name = "{0} at {1}")
    @MethodSource("teams")
    void eachFarmerStartsAtTheFarmGateAndRunsTheValley(String character, WidescreenAspect aspect) throws Exception {
        HeadlessTestFixture fixture = launch(character, aspect);
        Level level = GameServices.level().getCurrentLevel();
        assertInstanceOf(Sonic3kLevel.class, level);
        assertFalse(((Sonic3kLevel) level).hasStockRomZoneIdentity());
        assertEquals(26, level.getMap().getWidth(), "13 Sonic 1 blocks, two S3K blocks each");
        assertEquals(aspect.pixelWidth(), fixture.camera().getWidth());
        AbstractPlayableSprite player = fixture.sprite();
        assertEquals(character, player.getCode());
        assertEquals(150, player.getCentreX(), "starts at the farm gate");
        fixture.stepIdleFrames(30);
        assertFalse(player.getAir(), "standing at the gate: y=" + player.getCentreY());
        int floor = player.getCentreY();
        int maxX = 0;
        int ledgeJumpFrames = 0;
        boolean jumpedAtLedge = false;
        for (int frame = 0; frame < 1800 && player.getCentreX() < COLUMNS.length * 256 - 200; frame++) {
            // Block 3 begins with the source's 64px ledge. The spring is on that ledge;
            // jump onto it using native input, rather than expecting held-right to climb a wall.
            if (!jumpedAtLedge && player.getCentreX() >= 1710 && !player.getAir()) {
                jumpedAtLedge = true;
                ledgeJumpFrames = 20;
            }
            fixture.stepFrame(false, false, false, true, ledgeJumpFrames-- > 0);
            assertFalse(player.getDead(), "died at x=" + player.getCentreX() + " y=" + player.getCentreY());
            maxX = Math.max(maxX, player.getCentreX());
        }
        assertTrue(maxX >= COLUMNS.length * 256 - 200, character + " crossed the valley: reached x=" + maxX
                + " y=" + player.getCentreY() + " (floor at start " + floor + ")");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sonic", "tails", "knuckles"})
    void theLoopTakesTheFarmerOverItsTopAndOutTheFarSide(String character) throws Exception {
        HeadlessTestFixture fixture = launch(character, WidescreenAspect.WIDE_16_9);
        AbstractPlayableSprite player = fixture.sprite();
        fixture.stepIdleFrames(5);
        // Start on the flat block west of the loop at full speed, as running up from the town would.
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player, LOOP_X - 200);
        player.setGSpeed((short) 0x0C00);
        fixture.camera().updatePosition(true);
        com.openggf.level.LevelCameraInitialization.recenterPositionedEntry(GameServices.level());
        GameServices.level().updateObjectPositions();
        int topY = Integer.MAX_VALUE;
        boolean wentOverTop = false;
        boolean wentBehind = false;
        for (int frame = 0; frame < 400 && player.getCentreX() < LOOP_X + 300; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            int x = player.getCentreX();
            if (x >= LOOP_X && x < LOOP_X + 256) {
                topY = Math.min(topY, player.getCentreY());
                if (player.getCentreY() < SKY + 80 && !player.getAir()) {
                    wentOverTop = true;
                }
                wentBehind |= player.getTopSolidBit() == 0x0E;
            }
            assertFalse(player.getDead());
        }
        System.out.printf("REAL_VALLEY A4 %s: apex y=%d, exit x=%d, secondary=%s%n",
                character, topY, player.getCentreX(), wentBehind);
        assertTrue(wentOverTop, "ran over the loop's top: highest y=" + topY);
        assertTrue(wentBehind, "took the loop's second path across the top");
        assertTrue(player.getCentreX() >= LOOP_X + 300, "came out the far side: x=" + player.getCentreX());
        assertEquals(0x0C, player.getTopSolidBit() & 0xFF, "back on the primary path after the loop");
    }

    @Test
    void tailsFliesAndKnucklesGlides() throws Exception {
        HeadlessTestFixture fixture = launch("tails", WidescreenAspect.WIDE_16_9);
        AbstractPlayableSprite tails = fixture.sprite();
        fixture.stepIdleFrames(10);
        int groundY = tails.getCentreY();
        fixture.stepFrame(false, false, false, false, true);
        fixture.stepFrame(false, false, false, false, false);
        for (int i = 0; i < 12; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        fixture.stepFrame(false, false, false, false, true);     // second press: fly
        int highest = tails.getCentreY();
        for (int i = 0; i < 60; i++) {
            fixture.stepFrame(false, false, false, true, i % 8 == 0);
            highest = Math.min(highest, tails.getCentreY());
            assertTrue(tails.getDoubleJumpFlag() != 0 || !tails.getAir(), "Tails is flying at frame " + i);
        }
        assertTrue(tails.getDoubleJumpFlag() != 0, "still flying");
        assertTrue(highest < groundY - 60, "flapping keeps Tails up: highest " + highest + " ground " + groundY);
        close();
        kit = null;
        bootstrap = null;

        fixture = launch("knuckles", WidescreenAspect.WIDE_16_9);
        AbstractPlayableSprite knuckles = fixture.sprite();
        fixture.stepIdleFrames(10);
        fixture.stepFrame(false, false, false, true, true);
        for (int i = 0; i < 14; i++) {
            fixture.stepFrame(false, false, false, true, true);
        }
        fixture.stepFrame(false, false, false, true, false);
        fixture.stepFrame(false, false, false, true, true);       // second press: glide
        int startX = knuckles.getCentreX();
        boolean glided = false;
        for (int i = 0; i < 40; i++) {
            fixture.stepFrame(false, false, false, true, true);
            glided |= knuckles.getDoubleJumpFlag() == 1;
        }
        assertTrue(glided, "Knuckles glided");
        assertTrue(knuckles.getCentreX() > startX + 60, "the glide carried him east: " + startX + " -> "
                + knuckles.getCentreX());
    }

    /** A1/A2: host palette ownership and exact visible colour/priority survive compaction. */
    @Test
    void terrainAndBackgroundKeepTheirRomColoursWithinHostBudgets() throws Exception {
        HeadlessTestFixture fixture = launch("sonic", WidescreenAspect.WIDE_16_9);
        fixture.stepIdleFrames(20); // Includes the registered-placeholder palette bridge.
        Level valley = GameServices.level().getCurrentLevel();
        assertTrue(valley.getPatternCount() <= 2048);
        assertTrue(valley.getChunkCount() <= 1024);
        assertTrue(valley.getBlockCount() <= 256);
        assertTrue(valley.getSolidTileCount() <= 256);
        Level source;
        try (Rom rom = Rom.fromReader(LogicalRomResolver.fromRomManager(GameServices.rom())
                .openOrThrow(LogicalRom.S1), "test s1")) {
            source = new Sonic1(rom).buildDetachedLevel(0x80);
        }
        int pixels = 0;
        for (int plane = 0; plane < 2; plane++) {
            int height = plane == 0 ? 256 : 384;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < COLUMNS.length * 256; x++) {
                    int sourceId = plane == 0 ? COLUMNS[x / 256]
                            : source.getMap().getValue(1, x / 256, y / 256) & 255;
                    int vy = y + (plane == 0 ? SKY : 0);
                    int targetId = valley.getMap().getValue(plane, x / 128, vy / 128) & 255;
                    long expected = pixel(source, source.getBlock(sourceId), x % 256, y % 256, false);
                    long actual = pixel(valley, valley.getBlock(targetId), x % 128, vy % 128, true);
                    if (actual != expected) {
                        assertEquals(expected, actual, "colour/priority plane=" + plane + " x=" + x + " y=" + y);
                    }
                    pixels++;
                }
            }
        }
        System.out.printf("REAL_VALLEY A1/A2: %d patterns, %d chunks, %d blocks, %d profiles; %d exact art pixels%n",
                valley.getPatternCount(), valley.getChunkCount(), valley.getBlockCount(),
                valley.getSolidTileCount(), pixels);
    }

    private static long pixel(Level level, Block block, int x, int y, boolean checkReservations) {
        ChunkDesc cell = block.getChunkDesc(x / 16, y / 16);
        int cx = cell.getHFlip() ? 15 - x % 16 : x % 16;
        int cy = cell.getVFlip() ? 15 - y % 16 : y % 16;
        var desc = level.getChunk(cell.getChunkIndex()).getPatternDesc(cx / 8, cy / 8);
        int px = desc.getHFlip() ? 7 - cx % 8 : cx % 8;
        int py = desc.getVFlip() ? 7 - cy % 8 : cy % 8;
        int colour = level.getPattern(desc.getPatternIndex()).getPixel(px, py);
        if (colour == 0) return 0;
        int line = desc.getPaletteIndex();
        if (checkReservations) {
            assertFalse(line == 0 || line == 1 && (colour == 1 || colour == 5 || colour == 12 || colour == 14 || colour == 15)
                    || line == 3 && colour == 15, "art uses host/placeholder palette cell");
        }
        var rgb = level.getPalette(line).getColor(colour);
        return ((long) (desc.getPriority() ? 1 : 0) << 32)
                | (rgb.r & 255) << 16 | (rgb.g & 255) << 8 | rgb.b & 255;
    }

    /**
     * A3: every foreground pixel's solidity, on both paths and for both solidity bits, matches Sonic 1
     * (the loop block's second path is its low-plane twin, as {@code Sonic1Level.resolveCollisionBlockIndex}),
     * and the engine's own floor sensor finds each column's top surface where Sonic 1's data puts it.
     */
    @Test
    void floorScanMatchesSonic1GreenHill() throws Exception {
        launch("sonic", WidescreenAspect.NATIVE_4_3);
        Level valley = GameServices.level().getCurrentLevel();
        Level source;
        try (Rom rom = Rom.fromReader(LogicalRomResolver.fromRomManager(GameServices.rom())
                .openOrThrow(LogicalRom.S1), "test s1")) {
            source = new Sonic1(rom).buildDetachedLevel(0x80);
        }
        int width = COLUMNS.length * 256;
        int mismatches = 0;
        String first = null;
        for (int path = 0; path < 2; path++) {
            for (int bit = 0; bit < 2; bit++) {
                for (int y = 0; y < 256; y++) {
                    for (int x = 0; x < width; x++) {
                        int column = x / 256;
                        int s1Block = COLUMNS[column] == 53 && path == 1 ? 54 : COLUMNS[column];
                        boolean expected = solid(source, source.getBlock(s1Block), 16, x % 256, y, path, bit);
                        int s3kBlock = valley.getMap().getValue(0, x / 128, (y + SKY) / 128) & 0xFF;
                        boolean actual = solid(valley, valley.getBlock(s3kBlock), 8, x % 128, (y + SKY) % 128,
                                path, bit);
                        if (expected != actual) {
                            mismatches++;
                            if (first == null) {
                                first = "path " + path + " bit " + bit + " x=" + x + " y=" + y;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(0, mismatches, "solidity differs from Sonic 1; first at " + first);

        var levelManager = GameServices.level();
        int checked = 0;
        for (int x = 0; x < width; x++) {
            int surface = -1;
            int column = x / 256;
            for (int y = 0; y < 256 && surface < 0; y++) {
                if (solid(source, source.getBlock(COLUMNS[column]), 16, x % 256, y, 0, 0)) {
                    surface = y;
                }
            }
            if (surface < 8) {
                continue;
            }
            int probeY = SKY + surface - 8;
            TerrainCheckResult result = ObjectTerrainUtils.checkFloorDist(levelManager,
                    BackgroundPlaneCollisionProvider.FOREGROUND_ONLY, false, x, probeY);
            assertNotNull(result);
            // FindFloor reports 15 - height - probeY, one pixel before the first occupied pixel.
            assertEquals(SKY + surface - 1, probeY + result.distance(), "floor sensor at x=" + x);
            checked++;
        }
        System.out.printf("REAL_VALLEY A3: %d exact collision pixels, %d production floor probes%n",
                width * 256 * 4, checked);
        assertTrue(checked > width / 2, "most columns have a floor to sense: " + checked);
    }

    /** Whether pixel (x, y) of a block is solid for the path's top ({@code bit} 0) or side/bottom (1) sensor. */
    private static boolean solid(Level level, Block block, int grid, int x, int y, int path, int bit) {
        ChunkDesc desc = block.getChunkDesc(x / 16, y / 16);
        if (!desc.isSolidityBitSet(0x0C + path * 2 + bit)) {
            return false;
        }
        var chunk = level.getChunk(desc.getChunkIndex());
        int index = path == 0 ? chunk.getSolidTileIndex() : chunk.getSolidTileAltIndex();
        if (index == 0) {
            return false;
        }
        SolidTile tile = level.getSolidTile(index);
        int px = desc.getHFlip() ? 15 - x % 16 : x % 16;
        int py = desc.getVFlip() ? 15 - y % 16 : y % 16;
        int height = tile.heights[px];
        return height > 0 ? py >= 16 - Math.min(16, height) : height < 0 && py < Math.min(16, -height);
    }

    @Test
    void rewindRestoresARunMidValley() throws Exception {
        HeadlessTestFixture fixture = launch("sonic", WidescreenAspect.WIDE_16_9);
        AbstractPlayableSprite player = fixture.sprite();
        fixture.stepIdleFrames(5);
        for (int i = 0; i < 120; i++) {
            fixture.stepFrame(false, false, false, true, false);
        }
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        for (int i = 0; i < 300; i++) {
            fixture.stepFrame(false, false, false, true, i % 90 == 0);
        }
        int x = player.getCentreX();
        int y = player.getCentreY();
        short speed = player.getGSpeed();
        var objects = registry.capture().entries().get("object-manager");
        registry.restore(before);
        for (int i = 0; i < 300; i++) {
            fixture.stepFrame(false, false, false, true, i % 90 == 0);
        }
        assertEquals(x, player.getCentreX());
        assertEquals(y, player.getCentreY());
        assertEquals(speed, player.getGSpeed());
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", objects,
                registry.capture().entries().get("object-manager")));
    }
}
