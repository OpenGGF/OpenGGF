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
import com.openggf.physics.Direction;
import com.openggf.physics.GlideWallGrabTerrain;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Engine landing callbacks must observe the native .reachedFloor turn byte,
 * not a transient generic-exit clear that is repaired after the callback.
 * S3K 8b2cf3c .reachedFloor/Knux_TouchFloor (sonic3k.asm 31279-31290,
 * 32869-32911) and KiS2 c336fede's corresponding climb/ResetOnFloor owners
 * clear double_jump_flag without writing double_jump_property. FixBugs=0.
 * Declared terrain ports qualify these landing components, not MHZ route cause.
 */
@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestKnucklesClimbLandingCallbackContract {
    private LandingObserverKnuckles player;
    private PlayableSpriteMovement movement;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    private void configure(boolean kis2, boolean reverse, int state) {
        TestEnvironment.configureGameModuleFixture(kis2
                ? new Kis2ClimbModule() : new Sonic3kGameModule());
        var camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.setMinX((short) 0);
        camera.setMaxX((short) 2000);
        camera.setMinY((short) 0);
        camera.setMaxY((short) 1024);
        GameServices.gameState().setReverseGravityActive(reverse);
        player = new LandingObserverKnuckles();
        player.setAir(true);
        player.setJumping(true);
        player.setRollingJump(true);
        player.setPushing(true);
        player.setDoubleJumpFlag(state);
        player.setDoubleJumpProperty((byte) 0x80);
        if (kis2) player.restoreDefaultRadii();
        else player.applyCustomRadii(10, 10);
        player.setCentreX((short) 100);
        player.setCentreY((short) 200);
        player.setSubpixelRaw(100, 0xB600); // native wall anchor, retained Y fraction
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0x400);
        player.setDirection(Direction.RIGHT);
        player.setMappingFrame(0xB7);
        player.setAbilityMappingFrameControl(true);
        player.setForcedAnimationId(0x20);
        movement = (PlayableSpriteMovement) player.getMovementManager();
        assertSame(movement, player.getMovementManager());
        assertTrue(player.landings.isEmpty());
    }

    private void step(boolean down) {
        movement.handleMovement(false, down, false, false,
                false, false, false, false);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void downFloorPreservesNativePropertyDuringLandingCallback(boolean kis2, boolean reverse) {
        configure(kis2, reverse, 4);
        try (var terrain = new ClimbTerrainPorts()) {
            step(true);
            assertEquals(List.of(new WallQuery(reverse ? 189 : 211, true, 10, 10)),
                    terrain.walls, "wall probe precedes the floor probe with native climb radii");
            assertEquals(List.of(new VerticalQuery(false, reverse, 10, 10, (byte) 0x80)),
                    terrain.verticals);
            assertNativeFloorLanding(reverse ? 203 : 197, reverse ? 0x5C : 0x24);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void s3kIdleFloorPreservesPropertyAndKeepsItsUnmirroredProbe(boolean reverse) {
        configure(false, reverse, 4);
        try (var terrain = new ClimbTerrainPorts()) {
            step(false);
            assertTrue(terrain.walls.isEmpty(), "idle floor does not execute a wall-distance probe");
            assertEquals(List.of(new VerticalQuery(false, false, 10, 10, (byte) 0x80)),
                    terrain.verticals, "retail idle floor remains in normal-world scanner space");
            assertNativeFloorLanding(197, 0x24);
        }
    }

    private void assertNativeFloorLanding(int y, int angle) {
        assertNativeFloorLanding(y, angle, (byte) 0x80);
    }

    private void assertNativeFloorLanding(int y, int angle, byte finalProperty) {
        assertAll(
                () -> assertEquals(List.of(new Landing((byte) 0x80, 0, false,
                        0, 0, 0, 100, y, angle, 9, 19, 100, 0xB600)), player.landings,
                        "the callback must see the native byte before any post-return repair"),
                () -> assertEquals(List.of("ground:128", "landed:128"), player.callbacks,
                        "both synchronous landing hooks observe native state in publication order"),
                () -> assertEquals(finalProperty, player.getDoubleJumpProperty()),
                () -> assertGroundedExit(100, y, angle),
                () -> assertEquals(0xB7, player.getMappingFrame(),
                        "floor landing does not run a ledge animation table row"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeFloorDoesNotOverwriteAPropertyWrittenByTheLandingCallback(boolean reverse) {
        configure(false, reverse, 4);
        player.writePropertyAtLanding = true;
        try (var terrain = new ClimbTerrainPorts()) {
            step(true);
            assertEquals(List.of(new VerticalQuery(false, reverse, 10, 10, (byte) 0x80)),
                    terrain.verticals);
            assertNativeFloorLanding(reverse ? 203 : 197, reverse ? 0x5C : 0x24, (byte) 0x66);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void actualFinalLedgeRowStillClearsPropertyBeforeLandingCallback(boolean kis2, boolean reverse) {
        configure(kis2, reverse, 5);
        player.setDirection(Direction.LEFT);
        // Four real rows: byte offset 12 selects {D2,+8,-5,6}, advances
        // the offset to 16, then takes the same-pass ledge completion tail.
        player.setDoubleJumpProperty((byte) 12);
        player.setAnimationTick(0);
        try (var terrain = new ClimbTerrainPorts()) {
            step(false);
            int y = reverse ? 205 : 195;
            assertEquals(List.of(new Landing((byte) 0, 0, false,
                    0, 0, 0, 91, y, 0, 9, 19, 100, 0xB600)), player.landings,
                    "ledge completion retains its distinct clear-before-callback semantics");
            assertEquals((byte) 0, player.getDoubleJumpProperty());
            assertEquals(List.of("ground:0", "landed:0"), player.callbacks);
            assertGroundedExit(91, y, 0);
            assertEquals(0xD2, player.getMappingFrame(), "the actual last row must execute");
            assertEquals(6, player.getAnimationTick());
            assertTrue(terrain.walls.isEmpty());
            assertTrue(terrain.verticals.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryPublicLandingStillClearsPropertyBeforeItsCallback(boolean kis2) {
        configure(kis2, false, 4);
        player.setAir(false);
        assertEquals(1, player.landings.size());
        Landing landing = player.landings.getFirst();
        assertEquals((byte) 0, landing.property());
        assertEquals(0, landing.flag());
        assertFalse(landing.air());
        assertEquals((byte) 0, player.getDoubleJumpProperty());
        assertEquals(List.of("ground:0", "landed:0"), player.callbacks);
        assertEquals(0, player.getDoubleJumpFlag());
        assertFalse(player.getAir());
        assertFalse(player.isJumping());
        assertEquals(9, player.getXRadius());
        assertEquals(19, player.getYRadius());
        assertEquals(0x400, player.getGSpeed(),
                "ordinary setAir does not execute the climb owner's velocity reset");
    }

    private void assertGroundedExit(int x, int y, int angle) {
        assertFalse(player.getAir());
        assertFalse(player.isJumping());
        assertFalse(player.getRollingJump());
        assertFalse(player.getPushing());
        assertEquals(0, player.getDoubleJumpFlag());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertEquals(5, player.getAnimationId());
        assertEquals(-1, player.getForcedAnimationId());
        assertFalse(player.isObjectMappingFrameControl());
        assertEquals(9, player.getXRadius());
        assertEquals(19, player.getYRadius());
        assertEquals(x, player.getCentreX());
        assertEquals(y, player.getCentreY());
        assertEquals(angle, player.getAngle() & 0xFF);
        assertEquals(100, player.getXSubpixelRaw());
        assertEquals(0xB600, player.getYSubpixelRaw());
    }

    private record Landing(byte property, int flag, boolean air, int xSpeed, int ySpeed,
                           int gSpeed, int x, int y, int angle, int xr, int yr,
                           int xFraction, int yFraction) { }

    private static final class LandingObserverKnuckles extends Knuckles {
        final List<Landing> landings = new ArrayList<>();
        final List<String> callbacks = new ArrayList<>();
        boolean writePropertyAtLanding;

        LandingObserverKnuckles() { super("climb-landing-observer", (short) 0, (short) 0); }

        @Override
        protected void onLanded() {
            callbacks.add("landed:" + (getDoubleJumpProperty() & 0xFF));
            landings.add(new Landing(getDoubleJumpProperty(), getDoubleJumpFlag(), getAir(),
                    getXSpeed(), getYSpeed(), getGSpeed(), getCentreX(), getCentreY(),
                    getAngle() & 0xFF, getXRadius(), getYRadius(),
                    getXSubpixelRaw(), getYSubpixelRaw()));
            super.onLanded();
            // The original late restore also overwrote writes made by a callback:
            // https://github.com/OpenGGF/OpenGGF/blob/37286d6dd91ca0c40307c53f4de61eb259495f93/src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java#L2484-L2487
            if (writePropertyAtLanding) setDoubleJumpProperty((byte) 0x66);
        }

        @Override
        protected void onGroundStateChanged(boolean airborne) {
            if (!airborne) callbacks.add("ground:" + (getDoubleJumpProperty() & 0xFF));
            super.onGroundStateChanged(airborne);
        }
    }

    private record WallQuery(int y, boolean right, int xr, int yr) { }
    private record VerticalQuery(boolean towardsLedge, boolean reverse, int xr, int yr,
                                 byte property) { }

    private final class ClimbTerrainPorts implements AutoCloseable {
        final List<WallQuery> walls = new ArrayList<>();
        final List<VerticalQuery> verticals = new ArrayList<>();
        final MockedStatic<GlideWallGrabTerrain> terrain = mockStatic(GlideWallGrabTerrain.class);

        ClimbTerrainPorts() {
            terrain.when(() -> GlideWallGrabTerrain.distanceFromWall(
                    eq(player), anyInt(), anyBoolean())).thenAnswer(call -> {
                walls.add(new WallQuery(call.getArgument(1), call.getArgument(2),
                        player.getXRadius(), player.getYRadius()));
                return new TerrainCheckResult(0, (byte) 0, 1);
            });
            terrain.when(() -> GlideWallGrabTerrain.climbVerticalDistance(
                    eq(player), anyBoolean(), anyBoolean())).thenAnswer(call -> {
                verticals.add(new VerticalQuery(call.getArgument(1), call.getArgument(2),
                        player.getXRadius(), player.getYRadius(), player.getDoubleJumpProperty()));
                return new TerrainCheckResult(-3, (byte) 0x24, 2);
            });
        }

        @Override
        public void close() { terrain.close(); }
    }

    private static final class Kis2ClimbModule extends DelegatingGameModule {
        Kis2ClimbModule() { super(new Sonic2GameModule(), "test:kis2-climb-landing-callback"); }
        @Override public PhysicsProvider getPhysicsProvider() { return new Kis2PhysicsProvider(); }
        @Override public GameRules getRules() { return Kis2Rules.RULES; }
    }
}
