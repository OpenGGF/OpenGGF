package com.openggf.sprites.managers;

import com.openggf.game.GameServices;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.kis2.Kis2PhysicsProvider;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import com.openggf.physics.Direction;
import com.openggf.physics.GlideWallGrabTerrain;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.SuperStateController;
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
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Declared climb-state components, not wall-admission or route replays. Real Knuckles,
 * public movement, descriptors, height/width profiles and GroundSensor scans run.
 * Only layout/profile ownership is supplied by the LevelManager fixture.
 * Explicit camera bounds keep the declared Y512 climb inlet alive through the
 * common native prelude; signed/wrapping local-clamp cases are handler controls,
 * not death-plane arithmetic or route qualification.
 * Literal results are hand-derived from skdisasm044 Knuckles_Wall_Climb,
 * sub_F828 / CheckCeilingDist_WithRadius / FindFloor and KiS2 c336fede's port.
 */
@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestKnucklesWallClimbVerticalContract {
    private TerrainWorld terrain;
    private Knuckles player;
    private PlayableSpriteMovement movement;

    @BeforeEach
    void setUp() {
        configure(false);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"true,false,0x1E0,true,-1", "false,false,0x210,false,-3",
            "true,true,0x210,false,-2", "false,true,0x1E0,true,-2"})
    void realVerticalProfilesIncludeRadiusForAllFourNativeBranches(
            boolean ledge, boolean reverse, int row, boolean flip, int distance) {
        terrain.put(0x120, row, 15, 16, 0x20,
                (1 << (ledge ? 13 : 12)) | (flip ? 0x800 : 0));
        var result = GlideWallGrabTerrain.climbVerticalDistance(player, ledge, reverse);
        assertEquals(distance, result.distance());
        // Literal native points: -8-XR, +9+XR, +8+XR, -9-XR.
        int expectedY = reverse ? (ledge ? 0x212 : 0x1ED) : (ledge ? 0x1EE : 0x213);
        assertEquals(new Point(0x126, expectedY), terrain.points.getFirst());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uprightCeilingPenetrationUsesHeightProfileAndPreservesPositionWords(boolean kis2) {
        configure(kis2);
        terrain.wall(13);
        terrain.put(0x120, 0x1E0, 15, 16, 0x20, 0x2800);
        assertWallZero(-11);
        step(true, false);
        assertClimbingAt(0x126, 0x201);
        assertTrue(terrain.points.contains(new Point(0x126, 0x1EE)), "native CY-8-XR");
        assertEquals(kis2 ? 9 : 10, player.getXRadius());
        assertEquals(kis2 ? 19 : 10, player.getYRadius());
    }

    @ParameterizedTest
    @CsvSource({"false,0x20", "false,0x21", "true,0x20", "true,0x21"})
    void negativeFloorPublishesRawAngleWaitAndRetainsProperty(boolean kis2, int rawAngle) {
        configure(kis2);
        terrain.wall(13);
        terrain.put(0x120, 0x210, 15, 16, rawAngle, 0x1000);
        player.setPushing(true);
        player.setRollingJump(true);
        // The public roll-jump setter restores standing radii; declare the climb box again.
        player.applyCustomRadii(10, 10);
        player.setJumping(true);
        player.setFlipAngle(0x40);
        player.setFlipType(1);
        player.setFlipsRemaining(2);
        assertWallZero(11);
        step(false, true);
        assertFloorAt(0x1FD, rawAngle);
        assertFalse(player.getPushing());
        assertFalse(player.getRollingJump());
        assertFalse(player.isJumping());
        assertEquals(0, player.getFlipAngle());
        assertEquals(0, player.getFlipType());
        assertEquals(0, player.getFlipsRemaining());
    }

    @ParameterizedTest
    @CsvSource({"false,false,0x201", "false,true,0x202", "true,false,0x201", "true,true,0x202"})
    void exactlyZeroFloorContinuesClimbing(boolean kis2, boolean powered, int expectedY) {
        configure(kis2);
        terrain.wall(13);
        // At $213: 15 - (height12 + lowY3) = 0; BPL takes movement.
        terrain.put(0x120, 0x210, 12, 16, 0x20, 0x1000);
        power(powered);
        step(false, true);
        assertClimbingAt(0x126, expectedY);
        assertEquals(0x20, player.getAnimationId());
    }

    @ParameterizedTest
    @CsvSource({"false,true,0x1FE", "false,false,0x202", "true,true,0x1FE", "true,false,0x202"})
    void poweredClearanceMovesTwoNativeWordPixels(boolean kis2, boolean up, int expectedY) {
        configure(kis2);
        terrain.wall(13);
        power(true);
        step(up, !up);
        assertClimbingAt(0x126, expectedY);
    }

    @ParameterizedTest
    @CsvSource({"false,0x1F0,0x200,0x200", "true,0x1F0,0x200,0x200",
            "false,-256,0x200,0x1FF", "true,-256,0x200,0x1FE",
            "false,0x7FF8,0x7FF0,0x7FEF", "true,0x7FF8,0x7FF0,0x7FEE",
            "false,-256,-32768,32767", "true,-256,-32768,32766"})
    void upperClampUsesSignedWordsAndWrappingBypass(boolean powered, int minY, int y, int expectedY) {
        terrain.wall(13);
        if (y != 0x200) {
            player.setCentreY((short) y);
            player.setSubpixelRaw(0x126, 0xA500);
            terrain.put(0x130, y == -32768 ? 0x7FF0 : 0x7FE0, 0, 15, 0x40, 0x2000);
        }
        GameServices.camera().setMinY((short) minY);
        power(powered);
        assertWallZero(-11);
        step(true, false);
        assertClimbingAt(0x126, expectedY);
    }

    @Test
    void negativeCeilingCorrectionBypassesClearMovementClamp() {
        terrain.wall(13);
        terrain.put(0x120, 0x1E0, 15, 16, 0x20, 0x2800);
        GameServices.camera().setMinY((short) 0x200); // clear-path target would be $210
        power(true);
        step(true, false);
        assertClimbingAt(0x126, 0x201);
    }

    @ParameterizedTest
    @CsvSource({"false,false,0x123,0x204", "false,true,0x129,0x204",
            "true,false,0x123,0x1FC", "true,true,0x129,0x1FC"})
    void downUndoesBdPoseBeforeBothProbesWithoutReanchoring(
            boolean reverse, boolean left, int expectedX, int expectedY) {
        GameServices.gameState().setReverseGravityActive(reverse);
        player.setDirection(left ? Direction.LEFT : Direction.RIGHT);
        player.setMappingFrame(0xBD);
        // Right after undo: probe X=$123+10=$12D, width2 => 15-2-13=0.
        // Left after undo: probe X=$129-11=$11E; Hflip width14 => 14-14=0.
        terrain.put(left ? 0x110 : 0x120, reverse ? 0x1F0 : 0x200,
                0, left ? 14 : 2, 0x40, 0x2000 | (left ? 0x400 : 0));
        step(false, true);
        assertClimbingAt(expectedX, expectedY);
        assertEquals(0xB7, player.getMappingFrame());
        // The next entry sees the original x_sub anchor and lets go.
        step(false, true);
        assertEquals(2, player.getDoubleJumpFlag());
    }

    @ParameterizedTest
    @CsvSource({"true,0x210,false,0x1FE,0", "false,0x1E0,true,0x202,0x60"})
    void reverseRealProfilesUseRadiusAndMirrorOnlyFloorAngle(
            boolean up, int row, boolean flip, int expectedY, int angle) {
        GameServices.gameState().setReverseGravityActive(true);
        GameServices.camera().setMinY((short) -256);
        terrain.wall(13);
        terrain.put(0x120, row, 15, 16, flip ? 0x60 : 0x20,
                (1 << (up ? 13 : 12)) | (flip ? 0x800 : 0));
        step(up, !up);
        if (up) assertClimbingAt(0x126, expectedY);
        else assertFloorAt(expectedY, angle);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void idleUsesRadiusAndRetailDistanceClobberOnlyOnS3k(boolean kis2) {
        configure(kis2);
        terrain.put(0x120, 0x210, 11, 16, 0x20, 0x1000);
        player.setDoubleJumpProperty((byte) 0);
        step(false, false);
        assertClimbingAt(0x126, 0x200);
        assertEquals(kis2 ? 0xB9 : 0xBA, player.getMappingFrame());
        assertEquals(kis2 ? 0 : 3, player.getDoubleJumpProperty());
        assertEquals(kis2, terrain.points.isEmpty(), "KiS2 has no idle floor scan");
    }

    @Test
    void reverseIdleStillUsesNormalWorldFloorAndFloorTail() {
        GameServices.gameState().setReverseGravityActive(true);
        terrain.put(0x120, 0x210, 15, 16, 0x21, 0x1000);
        step(false, false);
        assertFloorAt(0x1FD, 0x21);
    }

    @ParameterizedTest
    @CsvSource({"13,true", "15,true", "13,false", "15,false"})
    void queryingPlayerOwnsLiveSolidityAndCollisionArray(int lrbBit, boolean up) {
        player.setLrbSolidBit((byte) lrbBit);
        player.setTopSolidBit((byte) (lrbBit - 1));
        Knuckles other = new Knuckles("other-camera-focus", (short) 0, (short) 0);
        other.setLrbSolidBit((byte) (lrbBit == 13 ? 15 : 13));
        other.setTopSolidBit((byte) (lrbBit == 13 ? 14 : 12));
        GameServices.camera().setFocusedSprite(other);
        terrain.wall(lrbBit);
        int bodyBit = up ? lrbBit : lrbBit - 1;
        terrain.put(0x120, up ? 0x1E0 : 0x210, 15, 16, 0x20,
                (1 << bodyBit) | (up ? 0x800 : 0));
        step(up, !up);
        if (up) assertClimbingAt(0x126, 0x201);
        else assertFloorAt(0x1FD, 0x20);
        assertTrue(terrain.lookups.contains(new Lookup(bodyBit, lrbBit == 15)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oppositeSolidityDoesNotBlockVerticalMovement(boolean up) {
        terrain.wall(13);
        terrain.put(0x120, up ? 0x1E0 : 0x210, 15, 16, 0x20,
                (1 << (up ? 12 : 13)) | (up ? 0x800 : 0));
        step(up, !up);
        assertClimbingAt(0x126, up ? 0x1FF : 0x201);
    }

    @Test
    void unequalLiveRadiiUseXRadiusWhileKis2InstallsItsTemporaryTen() {
        for (boolean kis2 : new boolean[] {false, true}) {
            configure(kis2);
            player.applyCustomRadii(10, 4);
            terrain.wall(13);
            terrain.put(0x120, 0x1E0, 15, 16, 0x20, 0x2800);
            step(true, false);
            assertClimbingAt(0x126, 0x201);
            assertEquals(kis2 ? 19 : 4, player.getYRadius());
        }
    }

    @ParameterizedTest
    @CsvSource({"16,0x200,4", "13,0x200,4", "11,0x1FD,5"})
    void wallGatePrecedesVerticalProbeAndLedgeStartsAtFour(int width, int expectedY, int state) {
        // width16 regresses to a blank tile and returns -1; width13 dip2, width11 ledge4.
        terrain.put(0x130, 0x1F0, 0, width, 0x40, 0x2000);
        terrain.put(0x120, 0x1E0, 15, 16, 0x20, 0x2800);
        step(true, false);
        assertEquals(state, player.getDoubleJumpFlag());
        assertEquals(expectedY, player.getCentreY());
        assertFalse(terrain.points.contains(new Point(0x126, 0x1EE)));
    }

    @Test
    void correctionAndBoundaryReplayRetainExistingSnapshotState() {
        terrain.wall(13);
        terrain.put(0x120, 0x1E0, 15, 16, 0x20, 0x2800);
        var spriteState = player.captureRewindState();
        var movementState = movement.captureRewindState();
        var cameraState = GameServices.camera().capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                player.restoreRewindState(spriteState);
                movement.restoreRewindState(movementState);
                GameServices.camera().restore(cameraState);
            }
            step(true, false);
            assertClimbingAt(0x126, 0x201);
            // At Y=$201 the next probe is exactly on the ceiling; the normal
            // movement step meets the declared minY+$10=$200 boundary.
            GameServices.camera().setMinY((short) 0x1F0);
            step(true, false);
            assertClimbingAt(0x126, 0x200);
        }
    }

    private void configure(boolean kis2) {
        TestEnvironment.configureGameModuleFixture(kis2 ? new Kis2ComponentModule() : new Sonic3kGameModule());
        terrain = new TerrainWorld();
        var context = TestEnvironment.activeGameplayMode();
        context.attachLevelManagers(context.getWaterSystem(), context.getParallaxManager(),
                context.getTerrainCollisionManager(), context.getCollisionSystem(),
                context.getSpriteManager(), terrain.level);
        context.attachBackgroundPlaneCollisionProvider(BackgroundPlaneCollisionProvider.FOREGROUND_ONLY);
        player = new Knuckles("climb-component", (short) 0, (short) 0);
        player.applyCustomRadii(10, 10);
        player.setCentreX((short) 0x126);
        player.setCentreY((short) 0x200);
        player.setSubpixelRaw(0x126, 0xA500);
        player.setDirection(Direction.RIGHT);
        player.setAir(true);
        player.setDoubleJumpFlag(4);
        player.setDoubleJumpProperty((byte) 2);
        player.setMappingFrame(0xB9);
        player.setAnimationId(0x20);
        player.getAnimationManager().publishPreviousAnimationId(0x20);
        player.setForcedAnimationId(0x20);
        player.setAbilityMappingFrameControl(true);
        var camera = GameServices.camera();
        camera.setMinX((short) 0);
        camera.setMaxX((short) 2000);
        camera.setMinY((short) 0);
        // Keep every declared signed-word Y inlet inside the synthetic lower
        // bound. The default maxY=0 would kill Y512 before the climb handler.
        camera.setMaxY(Short.MAX_VALUE);
        camera.setFocusedSprite(player);
        movement = assertInstanceOf(PlayableSpriteMovement.class, player.getMovementManager());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertSame(kis2 ? Kis2Rules.RULES : GameRules.SONIC_3K, player.getGameRules());
    }

    private void step(boolean up, boolean down) {
        movement.handleMovement(up, down, false, false, false, false, false, false);
    }

    private void power(boolean active) {
        var controller = mock(SuperStateController.class);
        when(controller.isSuper()).thenReturn(active);
        player.setSuperStateController(controller);
    }

    private void assertWallZero(int yOffset) {
        assertEquals(0, GlideWallGrabTerrain.distanceFromWall(player,
                player.getCentreY() + yOffset, true).distance(), "independent real wall inlet");
        terrain.points.clear();
        terrain.lookups.clear();
    }

    private void assertClimbingAt(int x, int y) {
        assertAll(() -> assertEquals(x, player.getCentreX()),
                () -> assertEquals(y, player.getCentreY()),
                () -> assertEquals(0x126, player.getXSubpixelRaw(), "native wall-anchor word"),
                () -> assertEquals(0xA500, player.getYSubpixelRaw()),
                () -> assertEquals(4, player.getDoubleJumpFlag()),
                () -> assertTrue(player.getAir()));
    }

    private void assertFloorAt(int y, int angle) {
        assertAll(() -> assertEquals(y, player.getCentreY()),
                () -> assertEquals(0x126, player.getCentreX()),
                () -> assertEquals(0x126, player.getXSubpixelRaw()),
                () -> assertEquals(0xA500, player.getYSubpixelRaw()),
                () -> assertEquals(0, player.getDoubleJumpFlag()),
                () -> assertEquals(2, player.getDoubleJumpProperty(), "Knux_TouchFloor does not write property"),
                () -> assertFalse(player.getAir()),
                () -> assertEquals(angle, player.getAngle() & 0xFF),
                () -> assertEquals(5, player.getAnimationId(), "native current anim byte"),
                () -> assertEquals(-1, player.getForcedAnimationId(), "landing does not pin future animations"),
                () -> assertEquals(0x20, player.getAnimationManager().captureRewindState().lastAnimationId(),
                        "native landing does not write prev_anim"),
                () -> assertEquals(0, player.getXSpeed()),
                () -> assertEquals(0, player.getYSpeed()),
                () -> assertEquals(0, player.getGSpeed()));
    }

    private static final class Kis2ComponentModule extends DelegatingGameModule {
        Kis2ComponentModule() { super(new Sonic2GameModule(), "test:kis2-climb-vertical"); }
        @Override public PhysicsProvider getPhysicsProvider() { return new Kis2PhysicsProvider(); }
        @Override public GameRules getRules() { return Kis2Rules.RULES; }
    }

    private record Point(int x, int y) { }
    private record Lookup(int bit, boolean secondary) { }
    private record Profile(ChunkDesc desc, SolidTile tile) { }

    private static final class TerrainWorld {
        final LevelManager level = mock(LevelManager.class);
        final Map<Point, Profile> cells = new HashMap<>();
        final Map<ChunkDesc, SolidTile> profiles = new HashMap<>();
        final List<Point> points = new ArrayList<>();
        final List<Lookup> lookups = new ArrayList<>();

        TerrainWorld() {
            when(level.getChunkDescAt(anyByte(), anyInt(), anyInt())).thenAnswer(call ->
                    descriptor(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean())).thenAnswer(call ->
                    descriptor(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            doCallRealMethod().when(level).getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt());
            when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt(), anyBoolean()))
                    .thenAnswer(call -> {
                        ChunkDesc desc = call.getArgument(0);
                        int bit = call.getArgument(1);
                        lookups.add(new Lookup(bit, call.getArgument(2)));
                        return desc.isSolidityBitSet(bit) ? profiles.get(desc) : null;
                    });
        }

        void wall(int lrbBit) {
            for (int y = 0x1E0; y <= 0x220; y += 16) put(0x130, y, 0, 15, 0x40, 1 << lrbBit);
        }

        void put(int x, int y, int height, int width, int angle, int flags) {
            int index = profiles.size() + 1;
            var desc = new ChunkDesc(index | flags);
            byte[] heights = new byte[16];
            byte[] widths = new byte[16];
            Arrays.fill(heights, (byte) height);
            Arrays.fill(widths, (byte) width);
            var tile = new SolidTile(index, heights, widths, (byte) angle);
            cells.put(new Point(x, y), new Profile(desc, tile));
            profiles.put(desc, tile);
        }

        private ChunkDesc descriptor(byte layer, int x, int y) {
            assertEquals(0, layer);
            points.add(new Point(x, y));
            var profile = cells.get(new Point(x & ~15, y & ~15));
            return profile == null ? null : profile.desc();
        }
    }
}
