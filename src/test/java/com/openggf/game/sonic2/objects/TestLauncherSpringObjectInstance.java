package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestLauncherSpringObjectInstance {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void launcherSpringUsesSolidObjectAlwaysOffscreenBypass() {
        LauncherSpringObjectInstance spring = new LauncherSpringObjectInstance(
                new ObjectSpawn(0x1070, 0x06F0, Sonic2ObjectIds.LAUNCHER_SPRING, 0, 0, false, 0),
                "LauncherSpring");

        assertTrue(spring.bypassesOffscreenSolidGate(),
                "Obj85 calls SolidObject_Always_SingleCharacter for both players");
    }

    @Test
    void nativeP2QuerySidekickCompressesAndLaunchesWhenRawSidekickListIsEmpty() {
        LauncherSpringObjectInstance spring = new LauncherSpringObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.LAUNCHER_SPRING, 0, 0, false, 0),
                "LauncherSpring");
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x1200, (short) 0x1000);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0x1000, (short) 0x1000);
        spring.setServices(new QueryOnlyPlayerServices(main, List.of(sidekick)));

        spring.onSolidContact(sidekick, new SolidContact(true, false, false, true, false), 0);
        sidekick.setJumpInputPressed(true);
        spring.update(0, main);
        sidekick.setJumpInputPressed(false);
        spring.update(1, main);
        spring.update(2, main);

        assertTrue(sidekick.getYSpeed() < 0,
                "Obj85 native P2 slot must be processed through ObjectPlayerQuery NATIVE_P1_P2");
    }

    @Test
    void extensionSidekickCompressesAndLaunchesIndependently() {
        LauncherSpringObjectInstance spring = new LauncherSpringObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.LAUNCHER_SPRING, 0, 0, false, 0),
                "LauncherSpring");
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x1200, (short) 0x1000);
        TestablePlayableSprite nativeP2 = new TestablePlayableSprite("tails", (short) 0x1200, (short) 0x1000);
        TestablePlayableSprite extension = new TestablePlayableSprite("knuckles", (short) 0x1000, (short) 0x1000);
        spring.setServices(new QueryOnlyPlayerServices(main, List.of(nativeP2, extension)));

        spring.onSolidContact(extension, new SolidContact(true, false, false, true, false), 0);
        extension.setJumpInputPressed(true);
        spring.update(0, main);
        extension.setJumpInputPressed(false);
        spring.update(1, main);
        spring.update(2, main);

        assertTrue(extension.getYSpeed() < 0);
    }

    @Test
    void traceDebugDetailsUsesNativeP2QueryWhenRawSidekickListIsEmpty() {
        LauncherSpringObjectInstance spring = new LauncherSpringObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.LAUNCHER_SPRING, 0, 0, false, 0),
                "LauncherSpring");
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x1200, (short) 0x1000);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0x1000, (short) 0x1000);
        spring.setServices(new QueryOnlyPlayerServices(main, List.of(sidekick)));

        spring.onSolidContact(sidekick, new SolidContact(true, false, false, true, false), 0);

        String details = spring.traceDebugDetails();

        assertTrue(details.contains("p2state=1"),
                "Trace diagnostics should resolve native P2 from ObjectPlayerQuery, not raw sidekicks()");
        assertTrue(details.contains("p2solid=false"),
                "Captured native P2 should report the per-player non-solid state");
    }

    @Test
    void verticalCaptureSeatsTheRiderFromItsStateAtTheSolidCall() {
        // SolidObject_Landed leaves y = y_pos(a0) - $20 - y_radius - 1; an in-air
        // rider then takes RideObject_SetRide's ResetOnFloor_Part2 (rolling:
        // standing radius, subq.w #1 for Tails); Obj85 writes y_radius=$E last
        // without moving y (s2.asm:35588-35626, 35986-36030, 41024-41031).
        assertEquals(0x06C0, captureTailsSeat(true, true),
                "airborne rolling Tails: roll-radius seat, then Part2's one-pixel lift");
        assertEquals(0x06C1, captureTailsSeat(false, true),
                "grounded rolling Tails seats on her roll radius and gets no Part2");
        assertEquals(0x06C0, captureTailsSeat(true, false),
                "standing-radius Tails seats on $F before Obj85's $E write");
    }

    private static int captureTailsSeat(boolean air, boolean rolling) {
        LauncherSpringObjectInstance spring = new LauncherSpringObjectInstance(
                new ObjectSpawn(0x1070, 0x06F0, Sonic2ObjectIds.LAUNCHER_SPRING, 0, 0, false, 0),
                "LauncherSpring");
        spring.setServices(new TestObjectServices().withIsolatedObjectManager());
        Tails tails = new Tails("tails", (short) 0x1070, (short) 0x0600);
        tails.setRolling(rolling);
        tails.setAir(air);
        tails.setCentreY((short) 0x06C3);
        tails.setYSpeed((short) 0);

        spring.onSolidContact(tails, new SolidContact(true, false, false, true, false), 0);
        return tails.getCentreY() & 0xFFFF;
    }

    @Test
    void diagonalCapturePreservesRomYWriteWhenTailsAlreadyHasRollSizedHeight() {
        LauncherSpringObjectInstance spring = new LauncherSpringObjectInstance(
                new ObjectSpawn(0x21A1, 0x0448, Sonic2ObjectIds.LAUNCHER_SPRING, 0x81, 0, false, 0),
                "LauncherSpring");
        spring.setServices(new TestObjectServices().withIsolatedObjectManager());
        Tails tails = new Tails("tails", (short) 0x21B4, (short) 0x0430);
        tails.setHeight(28);
        tails.setYSpeed((short) 0);

        spring.onSolidContact(tails, new SolidContact(true, false, false, true, false), 0);

        assertEquals(0x0435, tails.getCentreY() & 0xFFFF,
                "S2 Obj85 diagonal capture writes y_pos(a1)=y_pos(a0)-$13 before setting y_radius=$E");
    }

    @Test
    void allLaunchTypesOwnRollThroughControlWithoutBorrowingNativePinballState() {
        for (int subtype : new int[] {0, 1, 0x81}) {
            LauncherSpringObjectInstance spring = new LauncherSpringObjectInstance(
                    new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.LAUNCHER_SPRING,
                            subtype, 0, false, 0), "LauncherSpring");
            TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x1000);
            spring.setServices(new QueryOnlyPlayerServices(player, List.of()));
            spring.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
            assertTrue(player.getRolling());
            assertTrue(player.isObjectControlSuppressesMovement());
            assertFalse(player.getPinballMode(), "Obj85 never writes the native $39 alias");
            assertFalse(player.getSpindash());
            player.setJumpInputPressed(true);
            spring.update(0, player);
            player.setJumpInputPressed(false);
            spring.update(1, player);
            spring.update(2, player);
            assertTrue(player.getRolling(), "release keeps the actual roll status");
            assertFalse(player.isObjectControlSuppressesMovement());
            assertFalse(player.getPinballMode(), "slope release must not latch native boosts/jump blocking");
            assertFalse(player.getSpindash());
            if (subtype == 0x81) {
                assertFalse(player.getAir());
                assertEquals((byte) 0xE0, player.getAngle());
                assertTrue(player.getGSpeed() > 0);
            } else {
                assertTrue(player.getAir());
                assertTrue(player.getYSpeed() < 0);
            }
        }
    }

    @Test
    void releaseDoesNotRestoreStaleNativeStateOverAnOverlappingOwner() {
        for (boolean initial : new boolean[] {false, true}) {
            LauncherSpringObjectInstance spring = new LauncherSpringObjectInstance(
                    new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.LAUNCHER_SPRING, 0, 0, false, 0),
                    "LauncherSpring");
            TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x1000);
            spring.setServices(new QueryOnlyPlayerServices(player, List.of()));
            player.setPinballMode(initial);
            player.setSpindash(initial);
            spring.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
            assertEquals(initial, player.getPinballMode());
            // Model a later Obj84 enter/exit write while Obj85 still holds control.
            player.setPinballMode(!initial);
            player.setSpindash(!initial);
            player.setHurt(true);
            spring.update(0, player);
            assertFalse(player.isObjectControlSuppressesMovement());
            assertEquals(!initial, player.getPinballMode());
            assertEquals(!initial, player.getSpindash());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 0x81})
    void diagonalFreshP2CaptureJoinsTheSamePassReleaseDecision(int subtype) {
        var riders = captureP2AsP1Releases(subtype, false);
        // loc_2AF06 returns immediately after capture; the aggregate release
        // check then sees both riders. loc_2B018 launches on the next dispatch
        // using (one compression step + four) << 7 (s2.asm:58249-58281, 58375-58400).
        assertEquals(0x280, riders[0].getXSpeed());
        assertEquals(0x280, riders[1].getXSpeed(),
                "a newly captured P2 joins P1's release without another held-seat pass");
        assertFalse(riders[1].isObjectControlSuppressesMovement());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 0x81})
    void diagonalFreshP2HoldingJumpPreventsEitherRiderLaunching(int subtype) {
        var riders = captureP2AsP1Releases(subtype, true);
        // Obj85_Diagonal samples both captured-state/input bytes only after
        // the two per-player calls (s2.asm:58261-58281). P2's fresh held input
        // therefore vetoes P1's otherwise released jump in this same pass.
        assertEquals(0, riders[0].getXSpeed(),
                "new P2 held input must be considered before committing P1's release");
        assertEquals(0, riders[1].getXSpeed());
        assertTrue(riders[0].isObjectControlSuppressesMovement());
        assertTrue(riders[1].isObjectControlSuppressesMovement());
    }

    private static AbstractPlayableSprite[] captureP2AsP1Releases(int subtype, boolean p2HoldsJump) {
        var spring = new LauncherSpringObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.LAUNCHER_SPRING,
                        subtype, 0, false, 0), "LauncherSpring");
        var main = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x1000);
        var sidekick = new Tails("tails", (short) 0x1300, (short) 0x1000);
        var services = new QueryOnlyPlayerServices(main, List.of(sidekick));
        var camera = new com.openggf.camera.Camera();
        camera.setX((short) 0x0F00);
        camera.setY((short) 0x0F80);
        services.withCamera(camera)
                .withSolidExecutionRegistry(new com.openggf.game.solid.DefaultSolidExecutionRegistry())
                .withIsolatedObjectManager();
        var manager = services.objectManager();
        manager.addDynamicObject(spring);
        spring.onSolidContact(main, new SolidContact(true, false, false, true, false), 0);
        main.setJumpInputPressed(true);
        manager.update(0x0F00, main, List.of(sidekick), 0, false, true, true);

        main.setJumpInputPressed(false);
        sidekick.setCentreX((short) 0x1013);
        sidekick.setCentreY((short) (0x1000 - 8 - sidekick.getYRadius() - 1));
        sidekick.setAir(true);
        sidekick.setYSpeed((short) 0x100);
        sidekick.setJumpInputPressed(p2HoldsJump);
        manager.update(0x0F00, main, List.of(sidekick), 1, false, true, true);
        assertTrue(sidekick.isObjectControlSuppressesMovement(),
                "the real solid pass must capture the previously absent P2");
        manager.update(0x0F00, main, List.of(sidekick), 2, false, true, true);
        return new AbstractPlayableSprite[] {main, sidekick};
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 0x81})
    void diagonalCooldownStillResolvesSolidGeometry(int subtype) {
        var scene = captureDiagonalMainThroughSolidPass(subtype);
        var player = scene.player();
        player.setJumpInputPressed(false);
        scene.step(2);
        scene.step(3);
        assertTrue(player.getXSpeed() > 0, "the setup must have launched the rider");
        assertFalse(player.isOnObject());

        player.setRolling(false);
        player.setCentreX((short) (scene.spring().getX() + 0x13));
        player.setCentreY((short) (scene.spring().getY() - 8 - player.getYRadius() - 1));
        player.setAir(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0x100);
        scene.step(4);

        assertTrue(player.isOnObject(), "capture cooldown must not make the head intangible");
        assertSame(scene.spring(), scene.manager().getRidingObject(player));
        assertEquals(0, player.getYSpeed());
        assertFalse(player.isObjectControlSuppressesMovement(),
                "retain the inherited capture cooldown while still resolving the solid helper");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 0x81})
    void diagonalOffscreenReleaseClearsItsRideImmediately(int subtype) {
        var scene = captureDiagonalMainThroughSolidPass(subtype);
        scene.camera().setX((short) 0x3000);
        scene.step(2);

        assertTrue(scene.player().getAir());
        assertFalse(scene.player().isObjectControlSuppressesMovement());
        assertFalse(scene.player().isOnObject(), "the native offscreen tail clears OnObject");
        assertNull(scene.manager().getRidingObject(scene.player()),
                "manual release must retire its own standing/ride ownership in this pass");
    }

    private static DiagonalSpringScene captureDiagonalMainThroughSolidPass(int subtype) {
        var spring = new LauncherSpringObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.LAUNCHER_SPRING,
                        subtype, 0, false, 0), "LauncherSpring");
        var player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x1000);
        var services = new QueryOnlyPlayerServices(player, List.of());
        var camera = new com.openggf.camera.Camera();
        camera.setX((short) 0x0F00);
        camera.setY((short) 0x0F80);
        services.withCamera(camera)
                .withSolidExecutionRegistry(new com.openggf.game.solid.DefaultSolidExecutionRegistry())
                .withIsolatedObjectManager();
        var manager = services.objectManager();
        manager.addDynamicObject(spring);
        player.setCentreX((short) 0x1013);
        player.setCentreY((short) (0x1000 - 8 - player.getYRadius() - 1));
        player.setAir(true);
        player.setYSpeed((short) 0x100);
        player.setJumpInputPressed(true);
        var scene = new DiagonalSpringScene(spring, player, camera, manager);
        scene.step(0);
        scene.step(1);
        assertTrue(player.isOnObject(), "the setup must establish a real captured ride");
        assertSame(spring, manager.getRidingObject(player));
        assertTrue(player.isObjectControlSuppressesMovement());
        return scene;
    }

    private record DiagonalSpringScene(LauncherSpringObjectInstance spring,
                                      TestablePlayableSprite player,
                                      com.openggf.camera.Camera camera,
                                      com.openggf.level.objects.ObjectManager manager) {
        void step(int frame) {
            manager.update(camera.getX(), player, List.of(), frame, false, true, true);
        }
    }

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> queriedSidekicks;

        private QueryOnlyPlayerServices(PlayableEntity main, List<? extends PlayableEntity> queriedSidekicks) {
            this.main = main;
            this.queriedSidekicks = List.copyOf(queriedSidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return List.of();
        }
    }
}
