package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real solid-loop coverage for locked-on {@code Obj_FBZMagneticPlatform}. */
class TestFbzMagneticPlatformObjectManagerIntegration {
    private static final int PLATFORM_X = 0x1000;
    private static final int PLATFORM_Y = 0x0700;
    private static final int ONSCREEN_CAMERA_X = 0x0F80;
    private static final int ONSCREEN_CAMERA_Y = 0x0680;

    private GameModule previousModule;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        previousModule = GameModuleRegistry.getCurrent();
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
        GameModuleRegistry.setCurrent(previousModule);
        SessionManager.clear();
    }

    @Test
    void fallingPlayerNaturallyLandsAndRidesTheOffsetSurface() {
        Harness harness = harness(ONSCREEN_CAMERA_X, ONSCREEN_CAMERA_Y);
        FbzMagneticPlatformObjectInstance platform = harness.addPlatform();
        TestPlayer player = landingPlayer(PLATFORM_X);

        harness.step(player, List.of(), 1);

        assertSame(platform, harness.manager().getRidingObject(player));
        assertTrue(player.isOnObject());
        assertFalse(player.getAir());
        assertEquals(0, player.getYSpeed());
        assertEquals(platform.getY() - 8 - 9 - player.getYRadius() - 1,
                player.getCentreY(),
                "SolidObjectFull_Offset must land on obj_y + d3 - d2 - y_radius - 1");
    }

    @Test
    void continuedRideTracksEveryVerticalStepDuringTheActiveRise() {
        Harness harness = harness(ONSCREEN_CAMERA_X, ONSCREEN_CAMERA_Y);
        FbzMagneticPlatformObjectInstance platform = harness.addPlatform();
        TestPlayer player = landingPlayer(PLATFORM_X);
        harness.step(player, List.of(), 1);
        assertSame(platform, harness.manager().getRidingObject(player));

        harness.events().setMagneticState(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 0);
        int initialPlatformY = platform.getY();
        harness.step(player, List.of(), 2);
        assertSame(platform, harness.manager().getRidingObject(player));
        int continuedRideOffset = platform.getY() - player.getCentreY();
        assertEquals(8 + 9 + player.getYRadius(), continuedRideOffset,
                "MvSonicOnPtfm uses d2=$8 after the fresh-landing -1-pixel seam");
        boolean observedUpwardPixel = false;

        for (int frame = 3; frame <= 40; frame++) {
            int previousY = platform.getY();
            harness.step(player, List.of(), frame);
            assertSame(platform, harness.manager().getRidingObject(player),
                    "the continued SolidObjectFull_Offset standing path must retain the rider");
            assertEquals(continuedRideOffset, platform.getY() - player.getCentreY(),
                    "each object Y delta must be applied to the rider in the same solid pass");
            observedUpwardPixel |= platform.getY() < previousY;
        }

        assertTrue(observedUpwardPixel, "the ACTIVE polarity must reach a negative vertical step");
        assertTrue(platform.getY() < initialPlatformY,
                "the covered interval must include net upward platform travel");
    }

    @Test
    void exactInclusiveRightEdgeStillProducesFreshGroundedSideContact() {
        Harness harness = harness(ONSCREEN_CAMERA_X, ONSCREEN_CAMERA_Y);
        harness.addPlatform();
        TestPlayer player = groundedSidePlayer(PLATFORM_X + 0x23);

        harness.step(player, List.of(), 1);

        assertTrue(player.getPushing(),
                "SolidObjectFull_Offset rejects with bhi, so relX == d1*2 remains a side contact");
    }

    @Test
    void offscreenPlatformStillRunsFreshContactThroughTheOffsetHelper() {
        Harness harness = harness(0, 0);
        FbzMagneticPlatformObjectInstance platform = harness.addPlatform();
        TestPlayer player = groundedSidePlayer(PLATFORM_X + 0x22);

        harness.step(player, List.of(), 1);

        assertFalse(platform.isWithinSolidContactBounds(),
                "the test must exercise the real off-screen gate predicate");
        assertTrue(player.getPushing(),
                "SolidObjectFull_Offset enters SolidObject_cont without the SolidObjectFull on-screen gate");
    }

    @Test
    void nativeAndAdditionalCpuSidekicksEstablishIndependentRides() {
        Harness harness = harness(ONSCREEN_CAMERA_X, ONSCREEN_CAMERA_Y);
        FbzMagneticPlatformObjectInstance platform = harness.addPlatform();
        TestPlayer main = groundedSidePlayer(PLATFORM_X + 0x100);
        TestPlayer nativeSidekick = landingPlayer(PLATFORM_X - 4);
        TestPlayer additionalSidekick = landingPlayer(PLATFORM_X + 4);
        nativeSidekick.setCpuControlled(true);
        additionalSidekick.setCpuControlled(true);

        harness.step(main, List.of(nativeSidekick, additionalSidekick), 1);

        assertFalse(harness.manager().isRidingObject(main));
        assertSame(platform, harness.manager().getRidingObject(nativeSidekick));
        assertSame(platform, harness.manager().getRidingObject(additionalSidekick));
        assertTrue(nativeSidekick.isOnObject());
        assertTrue(additionalSidekick.isOnObject());
    }

    private static TestPlayer landingPlayer(int x) {
        TestPlayer player = new TestPlayer();
        player.setCentreX((short) x);
        // Inside loc_1E154's unsigned 0..$F fresh-landing band.
        player.setCentreY((short) (PLATFORM_Y - 9 - 8 - player.getYRadius() + 7));
        player.setYSpeed((short) 0x100);
        player.setAir(true);
        return player;
    }

    private static TestPlayer groundedSidePlayer(int x) {
        TestPlayer player = new TestPlayer();
        player.setCentreX((short) x);
        player.setCentreY((short) (PLATFORM_Y - 9));
        player.setAir(false);
        player.setOnObject(false);
        player.setDirectionalInputPressed(false, false, true, false);
        player.setXSpeed((short) -0x100);
        player.setGSpeed((short) -0x100);
        return player;
    }

    private static Harness harness(int cameraX, int cameraY) {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(
                0, PlayerCharacter.SONIC_AND_TAILS, events);
        TestCamera camera = new TestCamera(cameraX, cameraY);
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public ZoneRuntimeState zoneRuntimeState() { return runtime; }
            @Override public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> null, List::of);
            }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        return new Harness(manager, events, cameraX);
    }

    private record Harness(ObjectManager manager, Sonic3kFBZEvents events, int cameraX) {
        private FbzMagneticPlatformObjectInstance addPlatform() {
            return manager.createDynamicObject(() -> new FbzMagneticPlatformObjectInstance(
                    new ObjectSpawn(PLATFORM_X, PLATFORM_Y, Sonic3kObjectIds.FBZ_MAGNETIC_PLATFORM,
                            0x0F, 0, false, 0)));
        }

        private void step(PlayableEntity main, List<? extends PlayableEntity> sidekicks, int frame) {
            manager.update(cameraX, main, sidekicks, frame,
                    false, true, false);
        }
    }

    private static final class TestCamera extends Camera {
        private final int x;
        private final int y;

        private TestCamera(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override public short getX() { return (short) x; }
        @Override public short getY() { return (short) y; }
        @Override public short getWidth() { return 320; }
        @Override public short getHeight() { return 224; }
        @Override public boolean isVerticalWrapEnabled() { return false; }
    }

    private static final class TestPlayer extends AbstractPlayableSprite {
        private TestPlayer() {
            super("FBZ_MAGNETIC_PLATFORM_TEST", (short) 0, (short) 0);
            setWidth(20);
            setHeight(38);
            setGameRulesForTest(GameRules.SONIC_3K);
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 0;
            runDecel = 0;
            friction = 0;
            max = 0;
            jump = 0;
            angle = 0;
            slopeRunning = 0;
            slopeRollingDown = 0;
            slopeRollingUp = 0;
            rollDecel = 0;
            minStartRollSpeed = 0;
            minRollSpeed = 0;
            maxRoll = 0;
            rollHeight = 28;
            runHeight = 38;
            standXRadius = 9;
            standYRadius = 19;
            rollXRadius = 7;
            rollYRadius = 14;
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override public void draw() { }
    }
}
