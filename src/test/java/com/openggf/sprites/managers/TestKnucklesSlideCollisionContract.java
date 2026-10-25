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
import com.openggf.physics.GlideWallGrabTerrain;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Literal slide-owner contracts from Knuckles_Sliding (sonic3k.asm:30986-31071,
 * pinned 8b2cf3c) and KiS2 c336fede (s2.asm:38408-38492), FixBugs=0.
 * Declared terrain-port results test real movement/state ordering, not a measured
 * route, real terrain geometry or slide-audio coverage. The canonical public
 * dispatcher runs the prelude inside explicit synthetic 0..2000 camera bounds.
 */
@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestKnucklesSlideCollisionContract {
    private Knuckles player;
    private PlayableSpriteMovement movement;

    @BeforeEach
    void setUp() throws Exception {
        configure(false);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    private void configure(boolean kis2) throws Exception {
        TestEnvironment.configureGameModuleFixture(kis2
                ? new Kis2SlideModule() : new Sonic3kGameModule());
        var camera = GameServices.camera();
        camera.setMinX((short) 0);
        camera.setMaxX((short) 2000);
        camera.setMinY((short) 0);
        camera.setMaxY((short) 2000);
        player = new Knuckles("slide-contract", (short) 0, (short) 0);
        player.setAir(true);
        player.setJumping(true);
        player.setDoubleJumpFlag(3);
        player.setDoubleJumpProperty((byte) 0x80);
        player.setAbilityMappingFrameControl(true);
        player.setMappingFrame(0xCC);
        player.setForcedAnimationId(0x20);
        player.setGSpeed((short) 0x400);
        if (kis2) player.restoreDefaultRadii();
        else player.applyCustomRadii(10, 10);
        player.setCentreX((short) 100);
        player.setCentreY((short) 200);
        player.setSubpixelRaw(0xA500, 0xB600);
        movement = assertInstanceOf(PlayableSpriteMovement.class, player.getMovementManager());
        assertSame(movement, player.getMovementManager());
    }

    private void step() {
        step(true);
    }

    private void step(boolean heldJump) {
        movement.handleMovement(false, false, false, false,
                heldJump, false, false, false);
    }

    @ParameterizedTest
    @CsvSource({
            "false,32,3,0", "false,-32,0,0", "false,31,0,0",
            "false,-31,0,0", "false,0,0,0",
            "true,32,3,0", "true,-32,0,0", "true,31,0,0",
            "true,-31,0,0", "true,0,0,0"
    })
    void signedWordZeroBoundaryAndGetUpProperty(
            boolean kis2, int incomingX, int expectedFlag, int expectedX) throws Exception {
        configure(kis2);
        player.setXSpeed((short) incomingX);
        player.setYSpeed((short) 0);
        try (var ports = new TerrainPorts()) {
            step();
            assertEquals(expectedFlag, player.getDoubleJumpFlag());
            assertEquals(expectedX, player.getXSpeed());
            assertEquals((byte) 0x80, player.getDoubleJumpProperty(),
                    "neither native slide branch clears the turn byte");
            assertEquals((0xA500 + (incomingX << 8)) & 0xFFFF,
                    player.getXSubpixelRaw(), "move by the old word before deceleration");
            if (expectedFlag == 3) {
                assertEquals(4, ports.floors.size(), "collision pass and independent two-foot floor");
                assertEquals(0, player.getMoveLockTimer());
                assertEquals(0x400, player.getGSpeed(), "slide deceleration owns X, not inertia");
            } else {
                assertTrue(ports.all.isEmpty(), "get-up precedes both collision calls");
                assertEquals(15, player.getMoveLockTimer());
                assertEquals(0, player.getGSpeed());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothOriginalVelocityWordsMoveBeforeDeceleration(boolean reverse) throws Exception {
        GameServices.gameState().setReverseGravityActive(reverse);
        player.setXSpeed((short) 0x100);
        player.setYSpeed((short) 0x100);
        try (var ports = new TerrainPorts()) {
            step();
            assertEquals(101, player.getCentreX());
            assertEquals(reverse ? 199 : 201, player.getCentreY());
            assertEquals(0xA500, player.getXSubpixelRaw());
            assertEquals(0xB600, player.getYSubpixelRaw());
            assertEquals(0xE0, player.getXSpeed());
            assertEquals(0x100, player.getYSpeed(), "no gravity in the pre-dispatch move");
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void collisionOnlyCorrectionsPrecedeSecondFloorWithoutGlideResponse(
            boolean kis2, boolean right) throws Exception {
        configure(kis2);
        player.setXSpeed((short) (right ? 0x100 : -0x100));
        player.setYSpeed((short) 0);
        try (var ports = new TerrainPorts()) {
            ports.wallDistance = -2;
            ports.firstFloorDistance = -2;
            ports.firstFloorAngle = 0x24;
            ports.secondFloorDistance = 13;
            ports.secondFloorAngle = 0x08;
            step();
            int correctedX = right ? 99 : 101;
            assertEquals(correctedX, player.getCentreX());
            assertEquals(211, player.getCentreY(), "collision -2, then independent +13");
            assertEquals(0, player.getXSpeed(), "wall correction zeroes X but does not get up");
            assertEquals(3, player.getDoubleJumpFlag(), "no active-glide floor/wall response");
            assertEquals(0, player.getMoveLockTimer());
            assertEquals(0x400, player.getGSpeed());
            assertEquals(0xCC, player.getMappingFrame());
            assertEquals(0x08, player.getAngle());
            assertFalse(ports.aligned, "slide does not begin a wall grab");
            assertEquals(List.of(
                    new Query(correctedX + 10, 210, 12, false, 10, 10),
                    new Query(correctedX - 10, 210, 12, false, 10, 10),
                    new Query(correctedX + 10, 208, 12, false, 10, 10),
                    new Query(correctedX - 10, 208, 12, false, 10, 10)), ports.floors);
            assertEquals(kis2 ? 9 : 10, player.getXRadius());
            assertEquals(kis2 ? 19 : 10, player.getYRadius());
            assertEquals(0xA500, player.getXSubpixelRaw());
            assertEquals(0xB600, player.getYSubpixelRaw());
        }
    }

    @ParameterizedTest
    @CsvSource({"false,13,3", "false,14,2", "true,13,3", "true,14,2"})
    void onlyTheSecondFloorOwnsTheSignedFourteenGate(
            boolean kis2, int distance, int expectedFlag) throws Exception {
        configure(kis2);
        player.setXSpeed((short) 0x100);
        player.setYSpeed((short) 0);
        try (var ports = new TerrainPorts()) {
            ports.secondFloorDistance = distance;
            step();
            assertEquals(4, ports.floors.size());
            assertEquals(expectedFlag, player.getDoubleJumpFlag());
            assertEquals(distance < 14 ? 200 + distance : 200, player.getCentreY());
            if (expectedFlag == 2) {
                assertEquals(9, player.getXRadius());
                assertEquals(19, player.getYRadius());
                assertFalse(player.isObjectMappingFrameControl());
                assertEquals(0x21, player.getForcedAnimationId());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"32,0,13,0,4", "256,0,14,2,4", "32,-256,14,2,2"})
    void reverseSecondFloorInheritsTheOriginalScannerContext(
            int incomingX, int incomingY, int expectedBit, int floorCalls, int ceilingCalls)
            throws Exception {
        GameServices.gameState().setReverseGravityActive(true);
        player.setTopSolidBit((byte) 14);
        player.setLrbSolidBit((byte) 13); // path comes independently from TOP
        player.setXSpeed((short) incomingX);
        player.setYSpeed((short) incomingY);
        try (var ports = new TerrainPorts()) {
            step();
            assertEquals(3, player.getDoubleJumpFlag());
            assertEquals(floorCalls, ports.floors.size());
            assertEquals(ceilingCalls, ports.ceilings.size());
            assertTrue(ports.ceilings.stream().allMatch(q -> q.bit() == expectedBit && q.secondary()),
                    "down keeps LRB; reverse ceiling on side/up reloads TOP before continuation");
            assertTrue(ports.floors.stream().allMatch(q -> q.bit() == 14 && q.secondary()));
            assertEquals(0xB600, player.getYSubpixelRaw());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void releaseUsesSignedByteRadiusDifferenceAndRetainsTurnProperty(boolean reverse)
            throws Exception {
        GameServices.gameState().setReverseGravityActive(reverse);
        player.applyCustomRadii(10, 0xFF); // declared native byte-field boundary
        player.setCentreY((short) 200);
        player.setSubpixelRaw(0xA500, 0xB600);
        step(false);
        assertEquals(reverse ? 220 : 180, player.getCentreY(),
                "SUB.B 19 from 255 gives EC, then EXT.W gives -20");
        assertEquals(0xB600, player.getYSubpixelRaw());
        assertEquals((byte) 0x80, player.getDoubleJumpProperty());
        assertEquals(19, player.getYRadius());
        assertEquals(0, player.getDoubleJumpFlag());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void landingCallbackSeesNativeTurnByteButOrdinaryLandingClearsIt(boolean nativeFloor)
            throws Exception {
        var observer = new LandingObserverKnuckles();
        observer.setAir(true);
        observer.setJumping(true);
        observer.setDoubleJumpFlag(3);
        observer.setDoubleJumpProperty((byte) 0x80);
        observer.applyCustomRadii(10, 10);
        observer.setCentreX((short) 100);
        observer.setCentreY((short) 200);
        if (nativeFloor) {
            // Public release dispatch reaches the native slide get-up owner.
            var observerMovement = assertInstanceOf(PlayableSpriteMovement.class,
                    observer.getMovementManager());
            observerMovement.handleMovement(false, false, false, false,
                    false, false, false, false);
        } else {
            observer.setAir(false);
        }
        assertEquals(1, observer.landingCalls);
        assertEquals(nativeFloor ? (byte) 0x80 : (byte) 0, observer.propertyAtLanding,
                "preservation must happen before onLanded, not by a later restore");
        assertEquals(observer.propertyAtLanding, observer.getDoubleJumpProperty());
        assertFalse(observer.getAir());
    }

    private static final class LandingObserverKnuckles extends Knuckles {
        int landingCalls;
        byte propertyAtLanding;

        LandingObserverKnuckles() { super("slide-landing-observer", (short) 0, (short) 0); }

        @Override
        protected void onLanded() {
            landingCalls++;
            propertyAtLanding = getDoubleJumpProperty();
        }
    }

    private record Query(int x, int y, int bit, boolean secondary, int xr, int yr) { }

    private final class TerrainPorts implements AutoCloseable {
        final List<String> all = new ArrayList<>();
        final List<Query> floors = new ArrayList<>();
        final List<Query> ceilings = new ArrayList<>();
        final MockedStatic<GlideWallGrabTerrain> walls = mockStatic(GlideWallGrabTerrain.class);
        final MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class);
        int wallDistance;
        int firstFloorDistance;
        int secondFloorDistance;
        int firstFloorAngle;
        int secondFloorAngle;
        boolean aligned;

        TerrainPorts() {
            walls.when(() -> GlideWallGrabTerrain.glideWallDistance(
                    eq(player), anyBoolean(), anyInt(), anyBoolean())).thenAnswer(call -> {
                all.add("wall");
                return new TerrainCheckResult(wallDistance, (byte) 0, 1);
            });
            walls.when(() -> GlideWallGrabTerrain.align(eq(player), anyBoolean(), anyBoolean()))
                    .thenAnswer(call -> { aligned = true; return true; });
            terrain.when(() -> ObjectTerrainUtils.checkPlayerFloorDistWithFlipAwareAngle(
                    anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> {
                int prior = floors.size();
                floors.add(query(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(3)));
                all.add("floor");
                return new TerrainCheckResult(prior < 2 ? firstFloorDistance : secondFloorDistance,
                        (byte) (prior < 2 ? firstFloorAngle : secondFloorAngle), 2);
            });
            terrain.when(() -> ObjectTerrainUtils.checkPlayerCeilingDistWithFlipAwareAngle(
                    anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> {
                ceilings.add(query(call.getArgument(0), call.getArgument(1),
                        call.getArgument(3), call.getArgument(4)));
                all.add("ceiling");
                return new TerrainCheckResult(0, (byte) 0x80, 3);
            });
        }

        private Query query(int x, int y, int bit, boolean path) {
            return new Query(x, y, bit, path, player.getXRadius(), player.getYRadius());
        }

        @Override
        public void close() {
            terrain.close();
            walls.close();
        }
    }

    private static final class Kis2SlideModule extends DelegatingGameModule {
        Kis2SlideModule() { super(new Sonic2GameModule(), "test:kis2-slide-contract"); }
        @Override public PhysicsProvider getPhysicsProvider() { return new Kis2PhysicsProvider(); }
        @Override public GameRules getRules() { return Kis2Rules.RULES; }
    }
}
