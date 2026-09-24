package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;

import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The slam frame's hit path, driven through the real touch pass rather than by calling
 * {@code onPlayerAttack} directly.
 *
 * <p>{@code loc_7871A} (sonic3k.asm) makes the drill solid ({@code SolidObjectFull d1=$33 d2=4
 * d3=0}) and {@code loc_786EA} publishes {@code collision_flags 6} in the same frames. In the ROM
 * those are independent: {@code Add_SpriteToCollisionResponseList} puts the drill on the touch
 * list and {@code Touch_Response} runs whatever the solid push-out did or did not do, so an
 * airborne rolling player inside the box damages the drill.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzMinibossHitPath {

    private static final int SPAWN_X = 0x2CA0;
    private static final int SPAWN_Y = 0x0880;
    private static final int ARENA_CAMERA_X = 0x2C00;
    private static final int ARENA_CAMERA_Y = 0x0710;
    private static final int ANIM_ROLL = 0x02;
    /** Native frame 23928: Player 1 (11429,1956) against a drill at (11432,1975). */
    private static final int NATIVE_HIT_OFFSET_X = -3;
    private static final int NATIVE_HIT_OFFSET_Y = -19;

    private Rom rom;
    private ObjectManager manager;
    private LrzMinibossInstance boss;
    private AbstractPlayableSprite player;
    private Camera camera;
    private int clock;

    @BeforeEach
    void setUp() throws Exception {
        TestEnvironment.resetAll();
        rom = new Rom();
        assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
        RomByteReader reader = RomByteReader.fromRom(rom);
        GraphicsManager.getInstance().initHeadless();

        player = mock(AbstractPlayableSprite.class);
        when(player.getGameRules()).thenReturn(GameRules.SONIC_3K);
        when(player.getYRadius()).thenReturn((short) 14);
        when(player.getAnimationId()).thenReturn(ANIM_ROLL);
        when(player.getRolling()).thenReturn(true);

        camera = new Camera();
        camera.resetState();
        camera.setX((short) ARENA_CAMERA_X);
        camera.setY((short) ARENA_CAMERA_Y);

        TestObjectServices services = new TestObjectServices() {
            @Override
            public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> player, List::of);
            }

            @Override
            public com.openggf.debug.DebugOverlayManager debugOverlay() {
                return mock(com.openggf.debug.DebugOverlayManager.class);
            }
        };
        services.withCamera(camera).withRomReader(reader).withGraphicsManager(GraphicsManager.getInstance());
        manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null,
                new Sonic3kGameModule().createTouchResponseTable(reader),
                GraphicsManager.getInstance(), camera, services);
        services.withDirectObjectManager(manager);
        manager.reset(ARENA_CAMERA_X);
        AbstractObjectInstance.updateCameraBounds(ARENA_CAMERA_X, ARENA_CAMERA_Y, 320, 224, 0);

        boss = manager.createDynamicObject(
                () -> new LrzMinibossInstance(new ObjectSpawn(SPAWN_X, SPAWN_Y, 0x9D, 0, 0, false, 0)));
    }

    @AfterEach
    void tearDown() {
        if (rom != null) {
            rom.close();
        }
        GraphicsManager.getInstance().resetState();
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @Test
    void shieldContactDeflectsAHandShotAndRewindsItsHarmlessFlight() {
        // loc_78A02 sets shield_reaction bit3, independently of Sprite_CheckDeleteTouchXY.
        when(player.getCentreX()).thenReturn((short) SPAWN_X);
        when(player.getCentreY()).thenReturn((short) 1900);
        when(player.hasShield()).thenReturn(true);
        when(player.getShieldType()).thenReturn(com.openggf.game.ShieldType.FIRE);
        var shot = manager.createDynamicObject(
                () -> new LrzMinibossProjectileChild(SPAWN_X, 1900, 1, false));
        assertEquals(0x98, shot.getCollisionFlags());
        manager.snapshotTouchResponseState();
        assertTrue(shot.isOnScreenForTouch(), "publish the shot before the player touch pass");
        manager.runTouchResponsesForPlayer(player, clock);
        assertEquals(0, shot.getCollisionFlags(), "Touch_ChkHurt_Bounce_Projectile clears damage");
        var context = com.openggf.game.rewind.schema.RewindCaptureContext.none();
        var saved = shot.captureRewindState(context);
        int originalX = shot.getCentreX();
        shot.update(1, player);
        assertEquals(originalX + 8, shot.getCentreX(), "the ROM's -$800 radial bounce points away from P1");
        assertEquals(1900, shot.getCentreY());
        for (int i = 2; i <= 5; i++) shot.update(i, player);
        int forwardX = shot.getCentreX();
        var restored = (LrzMinibossProjectileChild) shot.recreateForRewind(
                new com.openggf.level.objects.RewindRecreateContext(shot.getSpawn(), saved, null));
        restored.setServices(new TestObjectServices().withCamera(camera));
        restored.restoreRewindState(saved, context);
        assertEquals(0, restored.getCollisionFlags(), "recreation must not re-arm a deflected projectile");
        for (int i = 1; i <= 5; i++) restored.update(i, player);
        assertEquals(forwardX, restored.getCentreX(), "captured velocity must replay the deflected flight");
        assertTrue(!restored.isDestroyed(), "the replay stays inside the ROM culling window");
    }

    private void step() {
        manager.update(ARENA_CAMERA_X, player, List.of(), clock++, false);
    }

    /**
     * The native hit, at native's own relative geometry. In
     * {@code s3k-sonic-tails-complete-emeralds/lrz} the drill slams from frame 23832 to 23927 at
     * {@code (11432,1975)}; Player 1 comes down on it rolling and airborne, is inside the box at
     * frames 23927-23930 ({@code (11429,1956)}, so 3 px left of the drill and 19 px above it) and
     * its {@code y} reverses there -- the boss-hit rebound -- with the ring count unchanged at
     * 355. The engine must take a hit off {@code collision_property} at that same offset.
     */
    @Test
    void theNativeRelativeHitOffsetDamagesTheDrill() {
        driveToTheSlamWindow();
        assertEquals(6, boss.getCollisionProperty(), "the drill starts on six hits");

        when(player.getCentreX()).thenReturn((short) (boss.getX() + NATIVE_HIT_OFFSET_X));
        when(player.getCentreY()).thenReturn((short) (boss.getY() + NATIVE_HIT_OFFSET_Y));
        manager.runTouchResponsesForPlayer(player, clock);

        assertEquals(5, boss.getCollisionProperty(),
                "an airborne rolling player at native's own hit offset must take one hit off the "
                        + "drill; drill at (" + boss.getX() + "," + boss.getY() + ") flags="
                        + Integer.toHexString(boss.getCollisionFlagsByte()));
    }

    /**
     * The box's own size, from the other side. {@code Touch_Sizes} entry 6 is
     * {@code dc.b $10,$10} (sonic3k.asm:20713-20720), so the drill is hittable 16 px either side
     * of {@code x_pos} and 16 px above and below {@code y_pos} -- <b>not</b> across the
     * {@code $33} half-width of {@code loc_7871A}'s {@code SolidObjectFull}, which is the box the
     * player stands on. A player 40 px left of the drill is outside the touch box and inside the
     * solid one, and the ROM leaves the drill alone there.
     *
     * <p>Reading {@code $33} as the touch size is what made a near miss look like a swallowed
     * hit: it says the drill is hittable 51 px out, and it is hittable 16.
     */
    @Test
    void theSlamBoxIsSixteenPixelsWideNotFiftyOne() {
        driveToTheSlamWindow();

        when(player.getCentreX()).thenReturn((short) (boss.getX() - 40));
        when(player.getCentreY()).thenReturn((short) (boss.getY() + NATIVE_HIT_OFFSET_Y));
        manager.runTouchResponsesForPlayer(player, clock);
        assertEquals(6, boss.getCollisionProperty(),
                "40 px out is inside the $33 solid box and outside the Touch_Sizes entry 6 box");

        when(player.getCentreX()).thenReturn((short) (boss.getX() - 16));
        manager.runTouchResponsesForPlayer(player, clock);
        assertEquals(5, boss.getCollisionProperty(),
                "16 px out is the edge of Touch_Sizes entry 6 and must land");
    }

    /**
     * {@code loc_7871A} is also {@code SolidObjectFull d1=$33 d2=4 d3=0}, and native shows the
     * player standing on the drill at {@code y 1956} for frames 23870-23876 before jumping off
     * again. Standing there is not an attack, so the drill keeps its hit -- but the player must
     * be on it, not through it.
     */
    @Test
    void theSlammingDrillIsSolidAcrossItsFullWidth() {
        driveToTheSlamWindow();
        assertTrue(boss.isSolidFor(player), "loc_7871A calls SolidObjectFull every slam frame");
        assertEquals(0x33, boss.getSolidParams().halfWidth(),
                "SolidObjectFull d1=$33: the drill is 102 px wide to stand on");
    }

    private void driveToTheSlamWindow() {
        int guard = 0;
        while (boss.getRoutineByte() != LrzMinibossInstance.slamRoutineByte() && guard++ < 4000) {
            positionPlayerAwayFromTheDrill();
            step();
        }
        assertEquals(LrzMinibossInstance.slamRoutineByte(), boss.getRoutineByte(),
                "loc_7871A never ran");
    }

    /**
     * {@code loc_786EA}'s {@code move.b #6,collision_flags(a0)}: the byte itself, because every
     * geometry claim below is a claim about {@code Touch_Sizes} entry 6 and nothing else.
     */
    @Test
    void theSlamPublishesCollisionFlagsSix() {
        driveToTheSlamWindow();
        assertEquals(6, boss.getCollisionFlagsByte(),
                "loc_786EA publishes collision_flags 6 for the whole slam");
    }

    private void positionPlayerAwayFromTheDrill() {
        when(player.getCentreX()).thenReturn((short) (ARENA_CAMERA_X + 0x40));
        when(player.getCentreY()).thenReturn((short) 0x7B0);
    }
}
