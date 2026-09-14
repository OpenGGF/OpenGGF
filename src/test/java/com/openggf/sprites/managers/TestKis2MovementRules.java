package com.openggf.sprites.managers;

import com.openggf.game.rules.GameRules;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.camera.Camera;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.timer.TimerManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.game.sonic2.kis2.Kis2Physics;
import com.openggf.physics.Direction;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.SuperStateController;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** KiS2 gameRevision=3 branches, alongside the unchanged stock rule defaults. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestKis2MovementRules {
    private Knuckles player;
    private PlayableSpriteMovement movement;

    @BeforeEach
    void setup() throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        player = new Knuckles("knuckles", (short) 0, (short) 0);
        movement = new PlayableSpriteMovement(player);
        rules(Kis2Rules.RULES);
    }

    @Test
    void airSteeringRetainsExistingSuperspeedBothDirectionsButStockS2Caps() throws Exception {
        for (GameRules rules : new GameRules[]{Kis2Rules.RULES, GameRules.SONIC_2}) {
            rules(rules);
            for (int direction : new int[]{-1, 1}) {
                player.setXSpeed((short) (direction * 0x900));
                player.setYSpeed((short) 0x100); // Outside upward drag window.
                player.setRollingJump(false);
                input(false, false, direction < 0, direction > 0, false, false);
                invoke("doChgJumpDir");
                assertEquals(direction * (rules == Kis2Rules.RULES ? 0x900 : player.getMax()),
                        player.getXSpeed());
            }
        }
    }

    @Test
    void skidPreservesSignedThresholdWordAndStillRejectsSteepAngles() throws Exception {
        Method skid = method("shouldTriggerGroundSkid", short.class, boolean.class);
        player.setAngle((byte) 0);
        assertFalse((boolean) skid.invoke(movement, (short) -0x3FF, true));
        assertTrue((boolean) skid.invoke(movement, (short) -0x400, true));
        assertFalse((boolean) skid.invoke(movement, (short) 0x3FF, false));
        assertTrue((boolean) skid.invoke(movement, (short) 0x400, false));
        player.setAngle((byte) 0x20);
        assertFalse((boolean) skid.invoke(movement, (short) 0x800, false));
        rules(GameRules.SONIC_2);
        player.setAngle((byte) 0);
        assertTrue((boolean) skid.invoke(movement, (short) -0x3FF, true),
                "Stock S2 retains the d0 low-byte clobber");
    }

    @Test
    void wallPushRequiresFacingIntoEitherWall() throws Exception {
        Method push = method("shouldSetGroundWallPush", int.class);
        for (Direction facing : new Direction[]{Direction.LEFT, Direction.RIGHT}) {
            player.setDirection(facing);
            assertEquals(facing == Direction.LEFT, push.invoke(movement, 0x40));
            assertEquals(facing == Direction.RIGHT, push.invoke(movement, 0xC0));
        }
        rules(GameRules.SONIC_2);
        assertTrue((boolean) push.invoke(movement, 0x40));
    }

    @Test
    void glideUsesSmallTerrainRadiiThenStandingTouchRadiiWithoutMovingCentre() throws Exception {
        player.setCentreX((short) 0x100);
        player.setCentreY((short) 0x200);
        player.setDoubleJumpFlag(1);
        player.setXSpeed((short) 0x400);
        player.setYSpeed((short) -1);
        player.restoreDefaultRadii();
        try (var terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkRightWallDist(0x10A, 0x200))
                    .thenAnswer(call -> {
                        assertEquals(10, player.getXRadius());
                        assertEquals(10, player.getYRadius());
                        return null;
                    });
            invoke("doGlideCollision");
            terrain.verify(() -> ObjectTerrainUtils.checkRightWallDist(0x10A, 0x200));
        }
        assertEquals(9, player.getXRadius());
        assertEquals(19, player.getYRadius());
        assertEquals(0x100, player.getCentreX());
        assertEquals(0x200, player.getCentreY());
        rules(GameRules.SONIC_3K);
        player.applyCustomRadii(10, 10);
        try (var terrain = mockStatic(ObjectTerrainUtils.class)) { invoke("doGlideCollision"); }
        assertEquals(10, player.getYRadius(), "S3K retains glide radii during touch response");
    }

    @Test
    void idleClimbKeepsFrameAndDoesNotDetachFromFloorButStockDoes() throws Exception {
        for (GameRules rules : new GameRules[]{Kis2Rules.RULES, GameRules.SONIC_3K}) {
            rules(rules);
            player.setCentreX((short) 0x100);
            player.setCentreY((short) 0x200);
            player.setWallClimbX(player.getX());
            player.setDoubleJumpFlag(4);
            player.setDoubleJumpProperty((byte) 0);
            player.setMappingFrame(0xB9);
            player.setAir(true);
            input(false, false, false, false, false, false);
            try (var terrain = mockStatic(ObjectTerrainUtils.class)) {
                terrain.when(() -> ObjectTerrainUtils.checkFloorDist(0x100, 0x209))
                        .thenReturn(new TerrainCheckResult(-3, (byte) 0, 1));
                invoke("updateWallClimb");
                if (rules == Kis2Rules.RULES) {
                    terrain.verifyNoInteractions();
                    assertEquals(4, player.getDoubleJumpFlag());
                    assertEquals(0xB9, player.getMappingFrame());
                    assertEquals(19, player.getYRadius());
                } else {
                    assertEquals(0, player.getDoubleJumpFlag());
                    assertFalse(player.getAir());
                }
            }
        }
    }

    @Test
    void freshSecondButtonReachesSuperWithoutReleaseAndReplaysAfterRestore() throws Exception {
        SuperStateController superState = mock(SuperStateController.class);
        when(superState.activateFromAirAbility()).thenReturn(true);
        player.setSuperStateController(superState);
        player.setAir(true);
        player.setJumping(true);
        player.setYSpeed((short) -0x300);
        input(false, false, false, false, true, false); // A held, no fresh edge.
        invoke("doJumpHeight");
        verifyNoInteractions(superState);
        var snapshot = movement.captureRewindState();
        input(false, false, false, false, true, true); // B edge while A remains held.
        invoke("doJumpHeight");
        verify(superState).activateFromAirAbility();
        movement.restoreRewindState(snapshot);
        input(false, false, false, false, true, true);
        invoke("doJumpHeight");
        verify(superState, times(2)).activateFromAirAbility();
        rules(GameRules.SONIC_3K);
        movement.restoreRewindState(snapshot);
        input(false, false, false, false, true, true);
        invoke("doJumpHeight");
        verifyNoMoreInteractions(superState);
    }

    @Test
    void turningTowardBalanceEdgeRestartsAtFrameFourAndPreservesRewindState() throws Exception {
        player.applyExternalPhysicsProfile(Kis2Physics.KNUCKLES);
        player.setAnimationProfile(new ScriptedVelocityAnimationProfile().setBalanceAnimId(6));
        player.setAnimationId(5);
        player.setAnimationFrameIndex(1);
        player.setAnimationTick(9);
        Method balance = method("setBalanceForEdge", boolean.class, boolean.class, int.class);
        balance.invoke(movement, true, true, 0); // Left edge, initially facing right.
        assertEquals(Direction.LEFT, player.getDirection());
        assertEquals(6, player.getAnimationId());
        assertEquals(4, player.getAnimationFrameIndex());
        assertEquals(0, player.getAnimationTick());
        assertEquals(6, player.getAnimationManager().captureRewindState().lastAnimationId());
        var state = player.captureRewindState();
        player.setAnimationFrameIndex(2);
        player.setAnimationTick(7);
        balance.invoke(movement, true, false, 0); // Already facing the edge.
        assertEquals(2, player.getAnimationFrameIndex());
        assertEquals(7, player.getAnimationTick());
        player.restoreRewindState(state);
        assertEquals(4, player.getAnimationFrameIndex());
        assertEquals(0, player.getAnimationTick());
        assertEquals(6, player.getAnimationManager().captureRewindState().lastAnimationId());
    }

    @Test
    void recordedOverlappingButtonsReachSuperThroughProductionInputPublication() throws Exception {
        var gameplay = TestEnvironment.activeGameplayMode();
        SpriteManager sprites = new SpriteManager();
        Camera camera = new Camera();
        camera.setMaxX((short) 0x3000);
        camera.setMaxY((short) 0x1000);
        camera.setMaxYTarget((short) 0x1000);
        gameplay.attachGameplayManagers(camera, new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2),
                new DefaultSolidExecutionRegistry());
        gameplay.attachLevelManagers(new WaterSystem(), new ParallaxManager(),
                mock(TerrainCollisionManager.class), mock(CollisionSystem.class), sprites,
                mock(LevelManager.class));
        player.setCentreX((short) 0x100);
        player.setCentreY((short) 0x200);
        player.setAir(true);
        player.setJumping(true);
        player.setYSpeed((short) -0x300);
        SuperStateController superState = mock(SuperStateController.class);
        when(superState.activateFromAirAbility()).thenReturn(true);
        player.setSuperStateController(superState);
        sprites.addSprite(player);
        Bk2FrameInput heldA = new Bk2FrameInput(10, AbstractPlayableSprite.INPUT_JUMP,
                InputActionMasks.ACTION_A, false, "A held");
        Bk2FrameInput heldAAndB = new Bk2FrameInput(11, AbstractPlayableSprite.INPUT_JUMP,
                InputActionMasks.ACTION_A | InputActionMasks.ACTION_B, false, "A held; B pressed");
        InputHandler input = new InputHandler();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(heldA, heldA));
        sprites.update(input);
        verify(superState, never()).activateFromAirAbility();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(heldAAndB, heldA));
        assertEquals(InputActionMasks.ACTION_B,
                RecordedInputSnapshots.fromBk2(heldAAndB, heldA).player1().actionPressedMask());
        sprites.update(input);
        assertTrue(player.isJumpPressed());
        assertTrue(player.isJumpJustPressed());
        verify(superState).activateFromAirAbility();
    }

    @Test
    void temporaryClimbRadiiPreservePinnedTopLeftAndCentre() throws Exception {
        player.setCentreX((short) 0x100);
        player.setCentreY((short) 0x200);
        player.setWallClimbX(player.getX());
        player.setDoubleJumpFlag(4);
        player.setMappingFrame(0xB9);
        int originalX = player.getX();
        int originalY = player.getY();
        input(false, false, false, false, false, false);
        for (int tick = 0; tick < 3; tick++) {
            try (var terrain = mockStatic(ObjectTerrainUtils.class)) { invoke("updateWallClimb"); }
            assertEquals(originalX, player.getX());
            assertEquals(originalY, player.getY());
            assertEquals(0x100, player.getCentreX());
            assertEquals(0x200, player.getCentreY());
            assertEquals(9, player.getXRadius());
            assertEquals(19, player.getYRadius());
        }
    }

    private void rules(GameRules rules) throws Exception {
        Field field = AbstractPlayableSprite.class.getDeclaredField("gameRules");
        field.setAccessible(true);
        field.set(player, rules);
    }

    private void input(boolean up, boolean down, boolean left, boolean right,
            boolean held, boolean edge) throws Exception {
        player.setJumpInputPressed(held, edge);
        method("storeInputState", boolean.class, boolean.class, boolean.class, boolean.class,
                boolean.class).invoke(movement, up, down, left, right, held);
    }

    private Method method(String name, Class<?>... parameters) throws Exception {
        Method method = PlayableSpriteMovement.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private void invoke(String name) throws Exception { method(name).invoke(movement); }
}
