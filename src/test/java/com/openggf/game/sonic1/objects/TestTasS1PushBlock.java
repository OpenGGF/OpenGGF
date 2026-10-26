package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rules.GameRules;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mockStatic;

/**
 * S1_PUSH_BLOCK component regression for the elevated MZ3 push described by
 * 447M/907S and the enemy-assisted MZ1 push described by 783M/1383S in the local
 * TAS audit. This exercises their height-sensitive Obj33 mechanism, not either
 * publication's route, enemy setup, timing or REV00 reproduction.
 *
 * <p>Oracle: s1disasm pin 28872a46e2d4c94c5c4818cbd3c04e6eb17edb67,
 * Solid_Collision / Solid_SideAir (_incObj/sub SolidObject.asm:193-248), then
 * PushB_SolidAction_NotOnPlatform (_incObj/33 MZ, LZ Pushable Blocks.asm:352-405).
 * With d0=2, penetration 4 skips separation; penetration 5 first subtracts 2.
 * Obj33 then adds 1 to both X positions and writes inertia=$40, x_vel=0.
 * The side return d4=1 permits pushing even when Solid_SideAir clears Status_Push.
 * Terrain responses are controlled at the wall/floor boundary; the player,
 * object, ObjectManager checkpoint, collision classification and push are real.
 */
@Isolated
class TestTasS1PushBlock {
    private static final int BLOCK_X = 0x180;
    private static final int BLOCK_Y = 0x100;
    private static final int PLAYER_X = BLOCK_X - 0x1B + 2;
    private static final int PLAYER_X_FRACTION = 0xA57B;
    private static final int PLAYER_Y_FRACTION = 0x39C1;
    private static final int BLOCK_X_FRACTION = 0x5AC3;
    private static final int BLOCK_Y_FRACTION = 0xC17D;
    private static final short ENTRY_X_SPEED = 0x200;
    private static final short ENTRY_INERTIA = 0x180;
    private static final short ENTRY_Y_SPEED = -0x100;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_1);
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetEngineOnly();
    }

    @ParameterizedTest(name = "penetration={0}, airborne={1}, separation={2}, pushing={3}")
    @CsvSource({
            "4, false,  0, false",
            "5, false, -2, true",
            "4, true,   0, false",
            "5, true,  -2, false"
    })
    void heightChangesPlayerDisplacementButAirDoesNotPreventObj33Push(
            int penetration, boolean air, int separation, boolean pushing) throws Exception {
        // d1=0 is admissible at ObjHitWallRight: only a negative distance blocks.
        Fixture fixture = runCheckpoint(penetration, air, Direction.RIGHT, 0);

        assertCheckpoint(fixture, air, separation, pushing);
        assertEquals(BLOCK_X + 1, fixture.block.getX(), "Obj33's addi.l #$10000");
        assertEquals(PLAYER_X + separation + 1, fixture.player.getCentreX(),
                "4px penetration advances Sonic 1px; 5px separates -2 then pushes +1");
        assertEquals(0x40, fixture.player.getGSpeed());
        assertEquals(0, fixture.player.getXSpeed());
        assertUnchangedVerticalStateAndFractions(fixture, air);
    }

    @ParameterizedTest(name = "opposite-facing, penetration={0}, separation={1}, pushing={2}")
    @CsvSource({"4, 0, false", "5, -2, true"})
    void oppositeFacingRejectsObj33PushAfterTheRealSolidCheckpoint(
            int penetration, int separation, boolean pushing) throws Exception {
        Fixture fixture = runCheckpoint(penetration, false, Direction.LEFT, 0);

        assertCheckpoint(fixture, false, separation, pushing);
        assertEquals(BLOCK_X, fixture.block.getX());
        assertEquals(PLAYER_X + separation, fixture.player.getCentreX(),
                "Facing gates Obj33's +1, not SolidObject's preliminary separation");
        assertEquals(separation == 0 ? ENTRY_INERTIA : 0, fixture.player.getGSpeed());
        assertEquals(separation == 0 ? ENTRY_X_SPEED : 0, fixture.player.getXSpeed());
        assertUnchangedVerticalStateAndFractions(fixture, false);
    }

    @ParameterizedTest(name = "wall blocked, penetration={0}, separation={1}, pushing={2}")
    @CsvSource({"4, 0, false", "5, -2, true"})
    void negativeWallDistanceRejectsObj33PushAfterTheRealSolidCheckpoint(
            int penetration, int separation, boolean pushing) throws Exception {
        Fixture fixture = runCheckpoint(penetration, false, Direction.RIGHT, -1);

        assertCheckpoint(fixture, false, separation, pushing);
        assertEquals(BLOCK_X, fixture.block.getX());
        assertEquals(PLAYER_X + separation, fixture.player.getCentreX());
        assertEquals(separation == 0 ? ENTRY_INERTIA : 0, fixture.player.getGSpeed());
        assertEquals(separation == 0 ? ENTRY_X_SPEED : 0, fixture.player.getXSpeed());
        assertUnchangedVerticalStateAndFractions(fixture, false);
    }

    private static Fixture runCheckpoint(int penetration, boolean air, Direction direction,
            int wallDistance) throws Exception {
        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        assertSame(GameRules.SONIC_1, player.getGameRules());
        assertEquals(0x13, player.getYRadius(), "Native standing Sonic radius");
        player.setCentreX((short) PLAYER_X);
        // Solid_ChkCollision: d3 = SonicY - blockY + 4 + ($10 + y_radius).
        // d3=4/5 and d0=2 select the two adjacent side branches, never a landing.
        int playerY = BLOCK_Y - 4 - (0x10 + 0x13) + penetration;
        player.setCentreY((short) playerY);
        player.setSubpixelRaw(PLAYER_X_FRACTION, PLAYER_Y_FRACTION);
        player.setAir(air);
        player.setOnObject(false);
        player.setPushing(false);
        player.setDirection(direction);
        player.setXSpeed(ENTRY_X_SPEED);
        player.setYSpeed(ENTRY_Y_SPEED);
        player.setGSpeed(ENTRY_INERTIA);

        ProbePushBlock block = new ProbePushBlock(player);
        // Seed only the native position fractions; no contact, routine or latch is forged.
        Field motionField = Sonic1PushBlockObjectInstance.class.getDeclaredField("motion");
        motionField.setAccessible(true);
        SubpixelMotion.State motion = (SubpixelMotion.State) motionField.get(block);
        motion.xSub = BLOCK_X_FRACTION;
        motion.ySub = BLOCK_Y_FRACTION;

        Camera camera = new Camera();
        camera.setX((short) 0x100);
        camera.setY((short) 0x80);
        ObjectManager[] holder = new ObjectManager[1];
        SolidExecutionRegistry registry = new DefaultSolidExecutionRegistry();
        var services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public int romZoneId() { return Sonic1Constants.ZONE_MZ; }
            @Override public int currentAct() { return 2; }
            @Override public SolidExecutionRegistry solidExecutionRegistry() { return registry; }
        };
        ObjectManager manager = new ObjectManager(List.of(), null, 0,
                null, null, null, camera, services);
        holder[0] = manager;
        manager.addDynamicObject(block);

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkRightWallDist(BLOCK_X + 0x10, BLOCK_Y))
                    .thenReturn(new TerrainCheckResult(wallDistance, (byte) 0, 1));
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(BLOCK_X + 1, BLOCK_Y, 0x0F))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 1));
            // S1's object slot sees the already-moved player. Do not advance player physics
            // or compensate velocity again before resolving this exact entry state.
            manager.update(camera.getX(), player, List.of(), 0, false, true, true);
        }
        return new Fixture(player, block, motion, manager, playerY);
    }

    private static void assertCheckpoint(Fixture fixture, boolean air, int separation, boolean pushing) {
        ProbePushBlock block = fixture.block;
        assertEquals(1, block.checkpointCalls, "Exactly one production Obj33 checkpoint");
        assertNotNull(block.checkpointBatch);
        PlayerSolidContactResult contact = block.checkpointBatch.perPlayer().get(fixture.player);
        assertNotNull(contact);
        assertEquals(ContactKind.SIDE, contact.kind());
        assertEquals(2, contact.sideDistX(), "Preserve pre-separation d0 for Obj33");
        assertFalse(contact.standingNow());
        assertEquals(air, contact.preContact().air());
        assertEquals(ENTRY_X_SPEED, contact.preContact().xSpeed());
        assertEquals(pushing, contact.pushingNow());
        assertEquals(separation == 0 ? ENTRY_X_SPEED : 0, contact.postContact().xSpeed());
        assertEquals(PLAYER_X + separation, block.playerXAfterCheckpoint,
                "Observe the SolidObject result before Obj33 applies its own push");
        assertEquals(separation == 0 ? ENTRY_INERTIA : 0, block.inertiaAfterCheckpoint);
        assertEquals(pushing, fixture.player.getPushing());
        assertEquals(pushing, fixture.manager.hasObjectPushingBit(fixture.player));
        assertFalse(fixture.manager.isRidingObject(fixture.player));
    }

    private static void assertUnchangedVerticalStateAndFractions(Fixture fixture, boolean air) {
        assertEquals(BLOCK_Y, fixture.block.getY());
        assertEquals(fixture.playerY, fixture.player.getCentreY());
        assertEquals(ENTRY_Y_SPEED, fixture.player.getYSpeed());
        assertEquals(air, fixture.player.getAir());
        assertFalse(fixture.player.isOnObject());
        assertEquals(PLAYER_X_FRACTION, fixture.player.getXSubpixelRaw());
        assertEquals(PLAYER_Y_FRACTION, fixture.player.getYSubpixelRaw());
        assertEquals(BLOCK_X_FRACTION, fixture.motion.xSub);
        assertEquals(BLOCK_Y_FRACTION, fixture.motion.ySub);
    }

    private record Fixture(Sonic player, ProbePushBlock block, SubpixelMotion.State motion,
                           ObjectManager manager, int playerY) {}

    /** Observation only: super resolves the actual ObjectManager checkpoint. */
    private static final class ProbePushBlock extends Sonic1PushBlockObjectInstance {
        private final Sonic player;
        private int checkpointCalls;
        private SolidCheckpointBatch checkpointBatch;
        private int playerXAfterCheckpoint;
        private int inertiaAfterCheckpoint;

        private ProbePushBlock(Sonic player) {
            super(new ObjectSpawn(BLOCK_X, BLOCK_Y, Sonic1ObjectIds.PUSH_BLOCK, 0, 0, false, 0));
            this.player = player;
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            checkpointCalls++;
            checkpointBatch = super.checkpointAll();
            playerXAfterCheckpoint = player.getCentreX();
            inertiaAfterCheckpoint = player.getGSpeed();
            return checkpointBatch;
        }
    }
}
