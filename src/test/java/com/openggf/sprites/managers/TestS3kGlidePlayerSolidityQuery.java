package com.openggf.sprites.managers;

import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * R106 independent SYNTHETIC component contract, not an original-route replay.
 * Native skdisasm044 sonic3k.asm Sonic_CheckFloor:19875..19927 reads the querying
 * player's top_solid_bit, selects its primary/secondary collision-index path,
 * probes both feet and keeps the signed nearer result (LEFT on ties).
 * Public player bits/geometry and camera focus are declared fixture inputs.
 * The existing private helper is invoked reflectively as in TestPlayableSpriteMovement.
 * Real ObjectTerrainUtils, descriptors, profile sampling and foot selection run;
 * only layout/profile ownership is supplied by a bounded LevelManager mock.
 * No static terrain mocking, ROM/trace hydration, clock/phase/history priming,
 * lifecycle execution, or claim about the retained native/engine divergence.
 */
@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestS3kGlidePlayerSolidityQuery {
    private static final int CX = 0x120;
    private static final int CY = 0x230;
    private static final int XR = 6;
    private static final int YR = 3;
    private static final int RIGHT = CX + XR;
    private static final int LEFT = CX - XR;
    private static final int FLOOR_Y = CY + YR;
    private static final int PRIMARY_TOP = 12;
    private static final int SECONDARY_TOP = 14;

    private GameModule previousModule;
    private TerrainFixture terrain;

    @BeforeEach
    void setUp() {
        previousModule = GameModuleRegistry.getCurrent();
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        terrain = new TerrainFixture();
        GameplayModeContext context = TestEnvironment.activeGameplayMode();
        context.attachLevelManagers(context.getWaterSystem(), context.getParallaxManager(),
                context.getTerrainCollisionManager(), context.getCollisionSystem(),
                context.getSpriteManager(), terrain.level);
        context.attachBackgroundPlaneCollisionProvider(BackgroundPlaneCollisionProvider.FOREGROUND_ONLY);
        assertFalse(GameServices.gameState().isReverseGravityActive(), "declared normal-gravity component");
        assertSame(terrain.level, GameServices.levelOrNull(), "original helper resolves the declared owner");
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        if (previousModule != null) GameModuleRegistry.setCurrent(previousModule);
        else GameModuleRegistry.reset();
    }

    @ParameterizedTest(name = "player top bit {0}, camera-focused top bit {1}")
    @CsvSource({"12,12", "12,14", "14,14", "14,12"})
    void playerFloorUsesItsOwnSolidityAndArrayRegardlessOfCameraFocus(
            int playerTopBit, int focusedTopBit) throws Exception {
        Knuckles player = player("querying", playerTopBit);
        Knuckles focused = playerTopBit == focusedTopBit ? player : player("other-focus", focusedTopBit);
        GameServices.camera().setFocusedSprite(focused);
        assertSame(focused, GameServices.camera().getFocusedSprite());
        assertEquals(playerTopBit, player.getTopSolidBit() & 0xFF);
        assertEquals(playerTopBit + 1, player.getLrbSolidBit() & 0xFF);
        assertEquals(focusedTopBit, focused.getTopSolidBit() & 0xFF);

        Method query = PlayableSpriteMovement.class.getDeclaredMethod(
                "checkGlideFloorDist", int.class, int.class, int.class, int.class);
        query.setAccessible(true);
        TerrainCheckResult actual = (TerrainCheckResult) query.invoke(
                new PlayableSpriteMovement(player), player.getCentreX(), player.getCentreY(),
                player.getXRadius(), player.getYRadius());

        boolean secondary = playerTopBit == SECONDARY_TOP;
        // Independent native arithmetic: 15 - (sampled height + (probeY & 15)).
        // Primary heights right3/left9 select LEFT; secondary right12/left6 select RIGHT.
        int expectedHeight = secondary ? 12 : 9;
        int expectedAngle = secondary ? 0x40 : 0x20;
        int expectedChunk = secondary ? 11 : 22;
        TerrainCheckResult expected = new TerrainCheckResult(
                15 - expectedHeight - (FLOOR_Y & 15), (byte) expectedAngle, expectedChunk);
        List<Lookup> expectedLookups = List.of(
                new Lookup(11, playerTopBit, secondary, secondary ? 201 : 101),
                new Lookup(22, playerTopBit, secondary, secondary ? 202 : 102));
        assertAll("live player bit/path, both original feet, independently computed winner",
                () -> assertEquals(List.of(new Point((byte) 0, RIGHT, FLOOR_Y),
                        new Point((byte) 0, LEFT, FLOOR_Y)), terrain.points),
                () -> assertEquals(expectedLookups, terrain.lookups,
                        "layout FG/BG, descriptor bit and collision-index array are separate choices"),
                () -> assertEquals(expected, actual));
    }

    @Test
    void explicitPrimaryObjectFloorKeepsObjectSolidityWithSecondaryCameraFocus() {
        GameServices.camera().setFocusedSprite(player("object-camera", SECONDARY_TOP));
        TerrainCheckResult actual = ObjectTerrainUtils.checkFloorDist(terrain.level,
                BackgroundPlaneCollisionProvider.FOREGROUND_ONLY, false, RIGHT, FLOOR_Y);
        assertAll("ordinary explicit primary object query is a separate contract",
                () -> assertEquals(List.of(new Point((byte) 0, RIGHT, FLOOR_Y)), terrain.points),
                () -> assertEquals(List.of(new Lookup(11, PRIMARY_TOP, false, 101)), terrain.lookups),
                () -> assertEquals(new TerrainCheckResult(15 - 3 - (FLOOR_Y & 15),
                        (byte) 0x10, 11), actual));
    }

    private static Knuckles player(String name, int topBit) {
        Knuckles player = new Knuckles(name, (short) 0, (short) 0);
        player.applyCustomRadii(XR, YR);
        player.setCentreX((short) CX);
        player.setCentreY((short) CY);
        player.setTopSolidBit((byte) topBit);
        player.setLrbSolidBit((byte) (topBit + 1));
        assertEquals(CX, player.getCentreX());
        assertEquals(CY, player.getCentreY());
        assertEquals(XR, player.getXRadius());
        assertEquals(YR, player.getYRadius());
        return player;
    }

    private record Point(byte layer, int x, int y) { }
    private record Lookup(int chunkIndex, int solidityBit, boolean secondaryPath, int profileIndex) { }

    private static final class TerrainFixture {
        final LevelManager level = mock(LevelManager.class);
        final List<Point> points = new ArrayList<>();
        final List<Lookup> lookups = new ArrayList<>();
        // Both bit pairs are present so actual bit/path arguments can be observed
        // at the owning LM call, rather than inferring them from an absent result.
        final ChunkDesc right = new ChunkDesc(11 | 0xF000);
        final ChunkDesc left = new ChunkDesc(22 | 0xF000);
        final SolidTile primaryRight = flat(101, 3, 0x10);
        final SolidTile primaryLeft = flat(102, 9, 0x20);
        final SolidTile secondaryRight = flat(201, 12, 0x40);
        final SolidTile secondaryLeft = flat(202, 6, 0x60);

        TerrainFixture() {
            when(level.getChunkDescAt(anyByte(), anyInt(), anyInt())).thenAnswer(call ->
                    descriptor(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> {
                assertFalse((Boolean) call.getArgument(3), "no synthetic low-plane layout switch");
                return descriptor(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            });
            // Existing player sensors may use this overload; retain the actual LM
            // bit->array delegation instead of prescribing its three-argument call.
            doCallRealMethod().when(level).getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt());
            when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt(), anyBoolean()))
                    .thenAnswer(call -> {
                        ChunkDesc desc = call.getArgument(0);
                        int bit = call.getArgument(1);
                        boolean secondary = call.getArgument(2);
                        assertTrue(desc == right || desc == left, "only the actual returned descriptors");
                        assertTrue(bit >= 12 && bit <= 15, "declared descriptor solidity bits");
                        SolidTile tile = desc == right
                                ? (secondary ? secondaryRight : primaryRight)
                                : (secondary ? secondaryLeft : primaryLeft);
                        lookups.add(new Lookup(desc.getChunkIndex(), bit, secondary, tile.getIndex()));
                        return desc.isSolidityBitSet(bit) ? tile : null;
                    });
        }

        private ChunkDesc descriptor(byte layer, int x, int y) {
            assertEquals(0, layer, "FG layout only; collision-index path remains independently selectable");
            assertEquals(FLOOR_Y, y, "nonzero partial heights require no extension/regression");
            assertTrue(x == RIGHT || x == LEFT, "only the original two foot arguments");
            points.add(new Point(layer, x, y));
            return x == RIGHT ? right : left;
        }

        private static SolidTile flat(int index, int height, int angle) {
            byte[] heights = new byte[16];
            byte[] widths = new byte[16];
            Arrays.fill(heights, (byte) height);
            Arrays.fill(widths, (byte) height);
            return new SolidTile(index, heights, widths, (byte) angle);
        }
    }

    // R108_CONTROLS_BEGIN -- declared synthetic scans; original R106 cases/helpers unchanged.
    @ParameterizedTest(name = "top {0}, reverse {1}, native scan {2}")
    @CsvSource({"12,false,EXTEND", "14,false,EXTEND", "12,false,REGRESS", "14,false,REGRESS",
            "12,false,EDGE", "14,false,EDGE", "12,true,EXTEND", "14,true,EXTEND",
            "12,true,REGRESS", "14,true,REGRESS", "12,true,EDGE", "14,true,EDGE"})
    void playerBitAndArraySurviveBothFootScanBranches(int topBit, boolean reverse, String path)
            throws Exception {
        GameServices.gameState().setReverseGravityActive(reverse); // declared component input
        OwnedScanFixture scan = new OwnedScanFixture(reverse, path);
        attachScanLevel(scan.level);
        Knuckles player = player("scan-owner", topBit);
        GameServices.camera().setFocusedSprite(player("opposite-focus", topBit == 12 ? 14 : 12));
        TerrainCheckResult actual = invokePlayerFloor(player);
        boolean secondary = topBit == 14;
        int bit = reverse ? topBit + 1 : topBit;
        int[] chunks = {11, 31, 22, 32};
        List<Lookup> expectedLookups = new ArrayList<>();
        for (int chunk : chunks) expectedLookups.add(new Lookup(
                chunk, bit, secondary, (secondary ? 200 : 100) + chunk));
        // FindFloor/sub_F30C arithmetic, independently of the implementation's result.
        // Selected height10 is LEFT on primary and RIGHT on secondary.
        int expectedDistance = reverse
                ? scan.probeY - (scan.neighborY & ~15) - 10
                : 15 - 10 - (scan.probeY & 15) + scan.neighborY - scan.probeY;
        assertAll("same live bit/path reaches initial and additional tiles of both feet",
                () -> assertEquals(List.of(new Point((byte) 0, RIGHT, scan.probeY),
                        new Point((byte) 0, RIGHT, scan.neighborY),
                        new Point((byte) 0, LEFT, scan.probeY),
                        new Point((byte) 0, LEFT, scan.neighborY)), scan.points),
                () -> assertEquals(expectedLookups, scan.lookups),
                () -> assertEquals(new TerrainCheckResult(expectedDistance,
                        (byte) (secondary ? 0x10 : 0x20), secondary ? 31 : 32), actual));
    }

    @Test
    void playerQueryKeepsBitAndArrayAcrossTranslatedLayoutPlanes() throws Exception {
        LevelManager level = mock(LevelManager.class);
        List<Point> points = new ArrayList<>();
        List<Lookup> lookups = new ArrayList<>();
        int probeY = FLOOR_Y;
        ChunkDesc foreground = new ChunkDesc(11 | 0xF000);
        ChunkDesc background = new ChunkDesc(22 | 0xF000);
        SolidTile fgPrimary = TerrainFixture.flat(101, 3, 0x10);
        SolidTile bgPrimary = TerrainFixture.flat(102, 4, 0x20);
        SolidTile fgSecondary = TerrainFixture.flat(201, 2, 0x10);
        SolidTile bgSecondary = TerrainFixture.flat(202, 8, 0x20);
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt())).thenAnswer(call -> {
            byte layer = call.getArgument(0);
            int x = call.getArgument(1), y = call.getArgument(2);
            assertTrue(layer == 0 || layer == 1);
            int dx = layer == 0 ? 0 : 32, dy = layer == 0 ? 0 : 16;
            assertTrue(x == RIGHT - dx || x == LEFT - dx);
            assertEquals(probeY - dy, y);
            points.add(new Point(layer, x, y));
            return layer == 0 ? foreground : background;
        });
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt(), anyBoolean()))
                .thenAnswer(call -> {
                    ChunkDesc desc = call.getArgument(0);
                    int bit = call.getArgument(1);
                    boolean secondary = call.getArgument(2);
                    assertTrue(desc == foreground || desc == background);
                    SolidTile tile = desc == foreground
                            ? (secondary ? fgSecondary : fgPrimary)
                            : (secondary ? bgSecondary : bgPrimary);
                    lookups.add(new Lookup(desc.getChunkIndex(), bit, secondary, tile.getIndex()));
                    return desc.isSolidityBitSet(bit) ? tile : null;
                });
        attachScanLevel(level);
        TestEnvironment.activeGameplayMode().attachBackgroundPlaneCollisionProvider(
                () -> new BackgroundPlaneCollisionProvider.State(true, 32, 16));
        Knuckles player = player("plane-owner", 14);
        GameServices.camera().setFocusedSprite(player("primary-focus", 12));
        TerrainCheckResult actual = invokePlayerFloor(player);
        int bit = 14;
        int expectedDistance = 15 - 8 - (probeY & 15);
        assertAll("FG/BG translation is independent of player collision-index ownership",
                () -> assertEquals(List.of(new Point((byte) 0, RIGHT, probeY),
                        new Point((byte) 1, RIGHT - 32, probeY - 16),
                        new Point((byte) 0, LEFT, probeY),
                        new Point((byte) 1, LEFT - 32, probeY - 16)), points),
                () -> assertEquals(List.of(new Lookup(11, bit, true, 201),
                        new Lookup(22, bit, true, 202), new Lookup(11, bit, true, 201),
                        new Lookup(22, bit, true, 202)), lookups),
                () -> assertEquals(new TerrainCheckResult(expectedDistance, (byte) 0x20, 22), actual));
    }

    private static void attachScanLevel(LevelManager level) {
        GameplayModeContext context = TestEnvironment.activeGameplayMode();
        context.attachLevelManagers(context.getWaterSystem(), context.getParallaxManager(),
                context.getTerrainCollisionManager(), context.getCollisionSystem(),
                context.getSpriteManager(), level);
        assertSame(level, GameServices.levelOrNull());
    }

    private static TerrainCheckResult invokePlayerFloor(Knuckles player) throws Exception {
        Method query = PlayableSpriteMovement.class.getDeclaredMethod(
                "checkGlideFloorDist", int.class, int.class, int.class, int.class);
        query.setAccessible(true);
        return (TerrainCheckResult) query.invoke(new PlayableSpriteMovement(player),
                player.getCentreX(), player.getCentreY(), player.getXRadius(), player.getYRadius());
    }

    private static final class OwnedScanFixture {
        final LevelManager level = mock(LevelManager.class);
        final List<Point> points = new ArrayList<>();
        final List<Lookup> lookups = new ArrayList<>();
        final int probeY;
        final int neighborY;

        OwnedScanFixture(boolean reverse, String path) {
            assertTrue(List.of("EXTEND", "REGRESS", "EDGE").contains(path));
            probeY = reverse ? CY - YR : FLOOR_Y;
            neighborY = probeY + (path.equals("EXTEND") ? (reverse ? -16 : 16) : (reverse ? 16 : -16));
            int initialHeight = path.equals("EXTEND") ? 0 : path.equals("REGRESS") ? 8 : 16;
            int initialFlip = !reverse && path.equals("REGRESS") ? 0x0800 : 0;
            int neighborFlip = reverse ? 0x0800 : 0;
            ChunkDesc right = new ChunkDesc(11 | 0xF000 | initialFlip);
            ChunkDesc left = new ChunkDesc(22 | 0xF000 | initialFlip);
            ChunkDesc nextRight = new ChunkDesc(31 | 0xF000 | neighborFlip);
            ChunkDesc nextLeft = new ChunkDesc(32 | 0xF000 | neighborFlip);
            when(level.getChunkDescAt(anyByte(), anyInt(), anyInt())).thenAnswer(call -> {
                byte layer = call.getArgument(0);
                int x = call.getArgument(1), y = call.getArgument(2);
                assertEquals(0, layer);
                assertTrue(x == RIGHT || x == LEFT);
                assertTrue(y == probeY || y == neighborY);
                points.add(new Point(layer, x, y));
                return y == probeY ? (x == RIGHT ? right : left) : (x == RIGHT ? nextRight : nextLeft);
            });
            when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt(), anyBoolean()))
                    .thenAnswer(call -> {
                        ChunkDesc desc = call.getArgument(0);
                        int bit = call.getArgument(1);
                        boolean secondary = call.getArgument(2);
                        assertTrue(desc == right || desc == left || desc == nextRight || desc == nextLeft);
                        boolean initial = desc == right || desc == left;
                        int height = initial ? initialHeight
                                : desc == nextRight ? (secondary ? 10 : 6) : (secondary ? 6 : 10);
                        int profile = (secondary ? 200 : 100) + desc.getChunkIndex();
                        int angle = desc == right || desc == nextRight ? 0x10 : 0x20;
                        SolidTile tile = TerrainFixture.flat(profile, height, angle);
                        lookups.add(new Lookup(desc.getChunkIndex(), bit, secondary, profile));
                        return desc.isSolidityBitSet(bit) ? tile : null;
                    });
        }
    }
    // R108_CONTROLS_END
}
