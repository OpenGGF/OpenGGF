package com.openggf.sprites.managers;

import com.openggf.audio.AudioRequestObserver;
import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.kis2.Kis2PhysicsProvider;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.GlideWallGrabTerrain;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Public-dispatch components for Knux_Glide_Freespace, Knuckles_Move_Glide's
 * camera tail and Knuckles_Sliding (S3K 8b2cf3c: sonic3k.asm 30715-30737,
 * 31747-31757, 31032-31058); KiS2 c336fede GlideControl/Move_Glide
 * (s2.asm 38147-38170, 39005-39014, 38468-38481). FixBugs=0.
 * Terrain returns are declared inputs, not route or native terrain evidence.
 * Audio observations prove request intent/order only; ROM loader namespace
 * qualification belongs to the separate ROM-backed audio contract.
 */
@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestKnucklesGlidePreludeContract {
    private Knuckles player;
    private PlayableSpriteMovement movement;
    private Camera camera;
    private LevelManager level;
    private final List<Integer> biasAtBoundary = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (player != null) GameServices.audio().setRequestObserver(null);
        SessionManager.clear();
    }

    private void configure(boolean kis2, int state) {
        GameModule module = kis2 ? new Kis2PreludeModule() : new Sonic3kGameModule();
        TestEnvironment.configureGameModuleFixture(module);
        var context = TestEnvironment.activeGameplayMode();
        camera = spy(context.getCamera());
        context.attachGameplayManagers(camera, context.getTimerManager(),
                context.getGameStateManager(), context.getFadeManager(),
                context.getRng(), context.getSolidExecutionRegistry());
        camera.setMinX((short) 0);
        camera.setMaxX((short) 2000);
        camera.setMinY((short) 0);
        camera.setMaxY((short) 2000);
        biasAtBoundary.clear();
        doAnswer(call -> {
            biasAtBoundary.add(camera.getYPosBias() & 0xFFFF);
            return call.callRealMethod();
        }).when(camera).getMinX();

        level = mock(LevelManager.class);
        when(level.getFrameCounter()).thenReturn(7); // no cadence cue in unrelated cases
        context.attachLevelManagers(context.getWaterSystem(), context.getParallaxManager(),
                context.getTerrainCollisionManager(), mock(CollisionSystem.class),
                context.getSpriteManager(), level);
        player = new Knuckles("glide-prelude-contract", (short) 0, (short) 0);
        player.setAir(true);
        player.setJumping(true);
        player.applyCustomRadii(10, 10);
        player.setCentreX((short) 100);
        player.setCentreY((short) 200);
        player.setSubpixelRaw(0xA500, 0xB600);
        player.setDoubleJumpFlag(state);
        player.setDoubleJumpProperty((byte) 0);
        player.setGSpeed((short) 0x400);
        player.setXSpeed((short) 0x100);
        player.setYSpeed((short) 0x100);
        player.setMappingFrame(0xCC);
        player.setAbilityMappingFrameControl(true);
        player.setAnimationTick(7); // state 5 must return before table movement
        movement = (PlayableSpriteMovement) player.getMovementManager();
        assertSame(movement, player.getMovementManager());
    }

    private void step(boolean heldJump) {
        movement.handleMovement(false, false, false, false,
                heldJump, false, false, false);
    }

    static Stream<Arguments> biasCases() {
        // Initial WORD, result after one native tail, result after two tails.
        int[][] words = {{0, 2, 4}, {94, 96, 96}, {95, 97, 95}, {96, 96, 96},
                {97, 95, 97}, {98, 96, 96}, {0x8000, 0x7FFE, 0x7FFC},
                {0xFFFF, 0xFFFD, 0xFFFB}};
        return Stream.of(false, true).flatMap(kis2 -> Stream.of(1, 2, 3, 4, 5)
                .flatMap(state -> Stream.of(words).map(word ->
                        Arguments.of(kis2, state, word[0], word[1], word[2]))));
    }

    @ParameterizedTest
    @MethodSource("biasCases")
    void literalUnsignedCameraTailPrecedesBoundaryAndEarlyStateReturns(
            boolean kis2, int state, int initial, int once, int twice) {
        configure(kis2, state);
        camera.setYPosBias((short) initial);
        // The camera deliberately has no focused sprite. Actor ownership is
        // the established non-CPU gate, not inferred camera focus.
        assertNull(camera.getFocusedSprite());
        try (var ports = new TerrainPorts()) {
            step(true);
            assertEquals(List.of(once), biasAtBoundary,
                    "speed-owner tail runs before the common LevelBound call");
            assertEquals(state == 2 ? twice : once, camera.getYPosBias() & 0xFFFF,
                    "fall-from-glide also owns its later ChgJumpDir camera tail");
            if (state == 4) {
                assertEquals(2, player.getDoubleJumpFlag(), "displaced wall anchor returns early");
                assertEquals(0, ports.floorCalls);
            } else if (state == 5) {
                assertEquals(5, player.getDoubleJumpFlag());
                assertEquals(7, player.getAnimationTick(), "nonzero ledge timer returns early");
                assertEquals(0, ports.floorCalls);
            }
        }
    }

    static Stream<Arguments> integrationCases() {
        return Stream.of(false, true).flatMap(kis2 -> Stream.of(3, 4, 5)
                .flatMap(state -> Stream.of(false, true).map(reverse ->
                        Arguments.of(kis2, state, reverse))));
    }

    @ParameterizedTest
    @MethodSource("integrationCases")
    void bothOldVelocityWordsIntegrateExactlyOnceBeforeEachHandler(
            boolean kis2, int state, boolean reverse) {
        configure(kis2, state);
        GameServices.gameState().setReverseGravityActive(reverse);
        try (var ports = new TerrainPorts()) {
            step(true);
            assertEquals(101, player.getCentreX());
            assertEquals(reverse ? 199 : 201, player.getCentreY());
            assertEquals(0xA500, player.getXSubpixelRaw());
            assertEquals(0xB600, player.getYSubpixelRaw());
            assertEquals(0x100, player.getYSpeed(), "common move adds no gravity");
            assertEquals(state == 3 ? 0xE0 : 0x100, player.getXSpeed(),
                    "slide deceleration and displaced-anchor return happen after the move");
            if (state == 4) assertEquals(2, player.getDoubleJumpFlag());
        }
    }

    @ParameterizedTest
    @MethodSource("integrationCases")
    void predictedXBoundaryClampsBeforeMovementAndBeforeEarlyReturns(
            boolean kis2, int state, boolean reverse) {
        configure(kis2, state);
        GameServices.gameState().setReverseGravityActive(reverse);
        player.setCentreX((short) 17);
        player.setSubpixelRaw(17, 0xB600); // valid wall anchor before the clamp
        player.setXSpeed((short) -0x200); // predicted X=15 is outside minX+16
        try (var ports = new TerrainPorts()) {
            step(true);
            assertEquals(16, player.getCentreX());
            assertEquals(0, player.getXSpeed());
            assertEquals(0, player.getGSpeed());
            // Sliding gets up after boundary zeroes x_vel and adjusts its
            // 10px Y radius back to 19; other early handlers keep the move.
            int radiusRestore = state == 3 ? (reverse ? 9 : -9) : 0;
            assertEquals(200 + (reverse ? -1 : 1) + radiusRestore, player.getCentreY());
            assertEquals(state == 3 ? 0 : state == 4 ? 2 : 5, player.getDoubleJumpFlag());
            assertEquals(0, ports.floorCalls, "clamp/get-up or displaced anchor exits before terrain");
        }
    }

    @ParameterizedTest
    @CsvSource({"false,1", "false,2", "false,3", "false,4", "false,5",
            "true,1", "true,2", "true,3", "true,4", "true,5"})
    void cpuActorsDoNotOwnTheSharedCameraBias(boolean kis2, int state) {
        configure(kis2, state);
        player.setCpuControlled(true);
        camera.setFocusedSprite(player); // focus must not grant CPU camera authority
        camera.setYPosBias((short) 0x8000);
        try (var ports = new TerrainPorts()) {
            step(true);
            assertEquals(0x8000, camera.getYPosBias() & 0xFFFF);
            assertEquals(List.of(0x8000), biasAtBoundary);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,160,166", "true,210,208"})
    void wallClimbRetainsItsSeparatePostMovementVerticalClamp(
            boolean reverse, int initialY, int expectedY) {
        configure(false, 4);
        GameServices.gameState().setReverseGravityActive(reverse);
        player.setCentreY((short) initialY);
        player.setSubpixelRaw(100, 0xB600); // valid wall anchor
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        camera.setMinY((short) (reverse ? 0 : 150));
        camera.setMaxY((short) (reverse ? 0 : 2000));
        try (var ports = new TerrainPorts()) {
            movement.handleMovement(true, false, false, false,
                    false, false, false, false);
            assertEquals(4, player.getDoubleJumpFlag());
            assertEquals(expectedY, player.getCentreY(),
                    "clear upright uses minY+16; reverse uses maxY+$D0");
            assertEquals(0xB600, player.getYSubpixelRaw());
        }
    }

    @ParameterizedTest
    @CsvSource({"false,0,true", "false,8,true", "false,256,true",
            "false,7,false", "false,9,false", "true,0,false", "true,8,false", "true,256,false"})
    void slideCueUsesLevelClockOnlyAndFollowsSuccessfulSecondFloorWrites(
            boolean kis2, int levelClock, boolean expected) {
        configure(kis2, 3);
        // The native level clock is deliberately opposite to both tempting
        // alternative clocks. This catches accidental reuse of movementFrame.
        int otherClock = (levelClock & 7) == 0 ? 7 : 8;
        when(level.getFrameCounter()).thenReturn(levelClock);
        var objects = mock(ObjectManager.class);
        when(objects.getVblaCounter()).thenReturn(otherClock);
        when(objects.getFrameCounter()).thenReturn(otherClock);
        when(level.getObjectManager()).thenReturn(objects);
        TestEnvironment.activeGameplayMode().getSpriteManager().setFrameCounter(otherClock);
        GameServices.audio().setRom(null);
        GameServices.audio().setAudioProfile((kis2 ? new Sonic2GameModule() : new Sonic3kGameModule())
                .getAudioProfile());
        List<Cue> cues = new ArrayList<>();
        GameServices.audio().setRequestObserver((kind, id) -> cues.add(new Cue(
                kind, id, player.getCentreY(), player.getAngle() & 0xFF)));
        try (var ports = new TerrainPorts()) {
            ports.secondFloorDistance = 3;
            ports.secondFloorAngle = 0x18;
            step(true);
            assertEquals(4, ports.floorCalls, "collision pass, then independent second floor");
            assertEquals(204, player.getCentreY());
            assertEquals(0x18, player.getAngle() & 0xFF);
            assertEquals(expected ? List.of(new Cue(AudioRequestObserver.RequestClass.SFX,
                    0x7E, 204, 0x18)) : List.of(), cues,
                    "request follows Y/angle correction; KiS2 has no GroundSlide block");
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false,0", "false,true,14", "true,false,0", "true,true,14"})
    void getUpAndSecondFloorFallReturnBeforeTheSlideCue(
            boolean kis2, boolean heldJump, int secondDistance) {
        configure(kis2, 3);
        when(level.getFrameCounter()).thenReturn(8);
        List<Integer> cues = new ArrayList<>();
        GameServices.audio().setRequestObserver((kind, id) -> cues.add(id));
        try (var ports = new TerrainPorts()) {
            ports.secondFloorDistance = secondDistance;
            step(heldJump);
            assertEquals(heldJump ? 2 : 0, player.getDoubleJumpFlag());
            assertTrue(cues.isEmpty());
        }
    }

    private record Cue(AudioRequestObserver.RequestClass kind, int id, int y, int angle) { }

    private final class TerrainPorts implements AutoCloseable {
        final org.mockito.MockedStatic<GlideWallGrabTerrain> walls = mockStatic(GlideWallGrabTerrain.class);
        final org.mockito.MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class);
        int floorCalls;
        int secondFloorDistance;
        int secondFloorAngle;

        TerrainPorts() {
            walls.when(() -> GlideWallGrabTerrain.distanceFromWall(
                    eq(player), anyInt(), anyBoolean()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 1));
            walls.when(() -> GlideWallGrabTerrain.climbVerticalDistance(
                    eq(player), anyBoolean(), anyBoolean()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 2));
            walls.when(() -> GlideWallGrabTerrain.glideWallDistance(
                    eq(player), anyBoolean(), anyInt(), anyBoolean()))
                    .thenReturn(new TerrainCheckResult(8, (byte) 0, 1));
            terrain.when(() -> ObjectTerrainUtils.checkPlayerFloorDistWithFlipAwareAngle(
                    anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> {
                boolean second = floorCalls++ >= 2;
                return new TerrainCheckResult(second ? secondFloorDistance : 0,
                        (byte) (second ? secondFloorAngle : 0), 2);
            });
            terrain.when(() -> ObjectTerrainUtils.checkPlayerCeilingDistWithFlipAwareAngle(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0x80, 3));
        }

        @Override
        public void close() {
            terrain.close();
            walls.close();
        }
    }

    private static final class Kis2PreludeModule extends DelegatingGameModule {
        Kis2PreludeModule() { super(new Sonic2GameModule(), "test:kis2-glide-prelude-contract"); }
        @Override public PhysicsProvider getPhysicsProvider() { return new Kis2PhysicsProvider(); }
        @Override public GameRules getRules() { return Kis2Rules.RULES; }
    }
}
