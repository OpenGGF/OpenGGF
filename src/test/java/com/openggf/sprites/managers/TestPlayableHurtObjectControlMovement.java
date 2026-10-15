package com.openggf.sprites.managers;

import com.openggf.game.GameServices;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Direction;
import com.openggf.physics.Sensor;
import com.openggf.physics.SensorResult;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestPlayableHurtObjectControlMovement {
    @BeforeEach
    void setup() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
    }

    @Test
    void injuredTubeRiderStillIntegratesOldVelocityAndNativeHurtGravity() throws Exception {
        var player = controlledPlayer(true);
        int beforeY = player.getCentreY();
        player.setYSpeed((short) 0x100);

        invoke(player, "doObjectMoveAndFall");

        assertEquals(beforeY + 1, player.getCentreY(), "Hurt runs ObjectMove before adding gravity");
        assertEquals(0x130, player.getYSpeed(), "Obj01_Hurt_Normal adds $30 even with obj_control=$81");
        assertTrue(player.isHurt(), "fixBugs=0 does not wake the hurt player on tube capture");
        assertTrue(player.isObjectControlled());
    }

    @Test
    void ordinaryTubeControlStillSuppressesGravityAndBoundaries() throws Exception {
        var player = controlledPlayer(false);
        player.setYSpeed((short) 0x100);
        GameServices.camera().setMinX((short) 0x200);
        GameServices.camera().setMaxX((short) 0x300);
        int beforeX = player.getCentreX();

        invoke(player, "applyGravity");
        invoke(player, "doLevelBoundary");

        assertEquals(0x100, player.getYSpeed());
        assertEquals(beforeX, player.getCentreX());
    }

    @Test
    void hurtRoutineKeepsItsBoundaryPassWhileATubeControlsNormalMovement() throws Exception {
        var player = controlledPlayer(true);
        GameServices.camera().setMinX((short) 0x200);
        GameServices.camera().setMaxX((short) 0x300);
        player.setXSpeed((short) -0x100);

        invoke(player, "doLevelBoundary");

        assertEquals(0x210, player.getCentreX(), "Hurt calls Sonic_LevelBound after HurtStop");
        assertEquals(0, player.getXSpeed());
        assertTrue(player.isObjectControlled(), "boundary clamping does not release the tube");
    }

    static Stream<Arguments> hurtLandingProfiles() {
        return Stream.of(
                Arguments.of("S2 downward", GameRules.SONIC_2, (short) 0),
                Arguments.of("S2 direct floor", GameRules.SONIC_2, (short) 0x400),
                Arguments.of("S3K downward", GameRules.SONIC_3K, (short) 0),
                Arguments.of("S3K direct floor", GameRules.SONIC_3K, (short) 0x400));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hurtLandingProfiles")
    void heldHurtPlayerRecoversThroughTerrainCollision(String profile, GameRules rules,
                                                       short xSpeed) throws Exception {
        var fixture = floorFixture(rules, true);
        var player = fixture.player();
        int beforeX = player.getCentreX();
        int beforeY = player.getCentreY();
        player.setXSpeed(xSpeed);
        player.setYSpeed((short) 0x100);

        fixture.movement().handleMovement(false, false, false, false,
                false, false, false, false);

        assertTrue(fixture.floorScans()[0] > 0, "HurtStop must resolve the real terrain floor");
        assertEquals(beforeX + (xSpeed >> 8), player.getCentreX(),
                "Hurt integrates the old horizontal velocity before landing");
        assertEquals(beforeY, player.getCentreY(),
                "The one-pixel recoil move must be corrected by the negative floor distance");
        assertFalse(player.getAir(), "Player_TouchFloor clears InAir before HurtStop tests it");
        assertFalse(player.isHurt());
        assertFalse(player.isObjectControlled(), "HurtStop clears object_control after terrain landing");
        assertFalse(player.isObjectControlSuppressesMovement());
        assertFalse(player.isObjectControlAllowsCpu());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertEquals(0x78, player.getInvulnerableFrames());
    }

    static Stream<Arguments> normalControlProfiles() {
        return Stream.of(Arguments.of("S2", GameRules.SONIC_2),
                Arguments.of("S3K", GameRules.SONIC_3K));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("normalControlProfiles")
    void ordinaryHeldPlayerDoesNotEnterTerrainCollision(String profile, GameRules rules)
            throws Exception {
        var fixture = floorFixture(rules, false);
        var player = fixture.player();
        int beforeX = player.getCentreX();
        int beforeY = player.getCentreY();
        player.setXSpeed((short) 0x400);
        player.setYSpeed((short) 0x100);

        fixture.movement().handleMovement(false, false, false, false,
                false, false, false, false);

        assertEquals(0, fixture.floorScans()[0], "Normal object control skips movement and terrain");
        assertEquals(beforeX, player.getCentreX());
        assertEquals(beforeY, player.getCentreY());
        assertEquals(0x400, player.getXSpeed());
        assertEquals(0x100, player.getYSpeed());
        assertTrue(player.getAir());
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertFalse(player.isHurt());
    }

    private record FloorFixture(TestablePlayableSprite player, PlayableSpriteMovement movement,
                                int[] floorScans) { }

    private static FloorFixture floorFixture(GameRules rules, boolean hurt) throws Exception {
        if (rules == GameRules.SONIC_3K) {
            TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        }
        var player = controlledPlayer(hurt);
        player.setGameRulesForTest(rules);
        player.setCentreX((short) 0x400);
        player.setCentreY((short) 0x200);
        player.setSubpixelRaw(0, 0);
        int[] floorScans = {0};
        player.setGroundSensors(new Sensor[] {
                fixedSensor(player, Direction.DOWN, (byte) -1, floorScans),
                fixedSensor(player, Direction.DOWN, (byte) -1, floorScans)
        });
        player.setCeilingSensors(new Sensor[] {
                fixedSensor(player, Direction.UP, (byte) 16, null),
                fixedSensor(player, Direction.UP, (byte) 16, null)
        });
        player.setPushSensors(new Sensor[] {
                fixedSensor(player, Direction.LEFT, (byte) 16, null),
                fixedSensor(player, Direction.RIGHT, (byte) 16, null)
        });
        var collision = new CollisionSystem(new TerrainCollisionManager());
        Field collisionField = GameplayModeContext.class.getDeclaredField("collisionSystem");
        collisionField.setAccessible(true);
        collisionField.set(TestEnvironment.activeGameplayMode(), collision);
        var camera = GameServices.camera();
        camera.setMinX((short) 0);
        camera.setMaxX((short) 0x1000);
        camera.setMinY((short) 0);
        camera.setMinYTarget((short) 0);
        camera.setMaxY((short) 0x1000);
        camera.setMaxYTarget((short) 0x1000);
        camera.setLevelStarted(true);
        return new FloorFixture(player,
                new PlayableSpriteMovement(player, collision, GameServices.gameState()), floorScans);
    }

    private static Sensor fixedSensor(TestablePlayableSprite player, Direction direction,
                                      byte distance, int[] scans) {
        return new Sensor(player, direction, (byte) 0, (byte) 0, true) {
            @Override
            protected SensorResult doScan(short dx, short dy) {
                if (scans != null) {
                    scans[0]++;
                }
                return new SensorResult((byte) 0, distance, 1, direction);
            }
        };
    }

    private static TestablePlayableSprite controlledPlayer(boolean hurt) {
        var player = new TestablePlayableSprite("sonic", (short) 100, (short) 100);
        player.setGameRulesForTest(GameRules.SONIC_2);
        player.setAir(true);
        player.setHurt(hurt);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        return player;
    }

    private static void invoke(TestablePlayableSprite player, String name) throws Exception {
        var method = PlayableSpriteMovement.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(player.getMovementManager());
    }
}
