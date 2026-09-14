package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.route.RouteSteering;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Cold FBZ1 route: pad input is the sole traversal authority; timing is live. */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("fbz-route")
class TestFbzAct1ColdRoute {
    private static final int MOVIE_START = 237913;

    @Test
    void sonicAndTailsReachAct2FromColdAct1ThroughRealBossAndResults() throws Exception {
        runColdRoute(320, "off", "tails");
    }

    @Test
    void fourHundredPixelColdRouteReachesReleasedAct2() throws Exception {
        runColdRoute(400, "off", "tails");
    }

    private void runColdRoute(int width, String donor, String sidekick) throws Exception {
        var graphics = GameServices.graphics();
        int viewportX = graphics.getViewportX();
        int viewportY = graphics.getViewportY();
        int viewportWidth = graphics.getViewportWidth();
        int viewportHeight = graphics.getViewportHeight();
        var configuration = SonicConfigurationService.getInstance();
        Map<SonicConfiguration, Object> previous = new EnumMap<>(SonicConfiguration.class);
        for (var key : SonicConfiguration.values()) {
            if (configuration.hasSessionOverride(key)) previous.put(key, configuration.getConfigValue(key));
        }
        try {
            configuration.clearSessionOverrides();
            configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick);
            configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, !donor.equals("off"));
            configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
            configuration.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                    com.openggf.configuration.WidescreenAspect.NATIVE_4_3.name());
            configuration.resolveDisplayAspect();
            configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
            var builder = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 0)
                    .withFreshLevelStartLifecycle();
            if (!donor.equals("off")) {
                var donorRom = com.openggf.tests.RomTestUtils.ensureSonic2RomAvailable();
                assertNotNull(donorRom, "the explicit S2 donor route requires its ROM");
                assertTrue(donorRom.isFile());
                configuration.setSessionOverride(SonicConfiguration.SONIC_2_ROM, donorRom.getAbsolutePath());
                builder.withCrossGameDonation(donor);
            }
            com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
            com.openggf.game.session.SessionManager.clear();
            com.openggf.tests.TestEnvironment.activeGameplayMode();
            var fixture = builder.build();
            graphics.setViewport(0, 0, width, 224);
            assertEquals(width, fixture.camera().getWidth() & 0xFFFF);
            assertEquals(width, graphics.getViewportWidth());
            assertEquals(!donor.equals("off"), com.openggf.game.CrossGameFeatureProvider.isActive());
            if (!donor.equals("off")) {
                assertEquals(donor, com.openggf.game.CrossGameFeatureProvider.getInstance().getDonorGameId());
                assertNotSame(GameServices.module().getRules(), fixture.sprite().getGameRules());
            }
            var player = assertInstanceOf(com.openggf.sprites.playable.Sonic.class, fixture.sprite());
            assertFalse(player.isCpuControlled());
            if (!sidekick.isEmpty()) {
                var follower = assertInstanceOf(com.openggf.sprites.playable.Tails.class,
                        GameServices.sprites().getSidekicks().getFirst());
                assertTrue(follower.isCpuControlled());
                assertSame(player, follower.getCpuController().getLeader());
            }
            assertEquals(0x60, player.getCentreX() & 0xFFFF);
            assertEquals(0x76C, player.getCentreY() & 0xFFFF);
            assertEquals(sidekick.isEmpty() ? 0 : 1, GameServices.sprites().getSidekicks().size());
            // Consumption executes the production one-shot object/animation
            // setup callback; it does not merely discard the lifecycle token.
            assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
            var movie = new Bk2MovieLoader().load(Path.of(
                    "src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2"));
            var history = new ArrayDeque<String>();
            boolean bossSeen = false;
            boolean bossDefeated = false;
            boolean signSeen = false;
            boolean resultsSeen = false;
            var outdoor = new OutdoorController(width);
            var lower = new LowerGapController(width);
            var magnetic = new MagneticCorridorController();
            var carriers = new MagneticCarrierController();
            boolean upperMissilePassed = false;
            boolean firstRotatingPassed = false;
            int remainingRotatingStage = 0;
            boolean missileExitPassed = false;
            int missileColumn = 0;
            boolean missileCrossing = false;
            var lateCarriers = new LateCarrierController();
            var carousel = new CarouselController();
            var wideSnake = new WideSnakeController();
            var widePoles = new WidePoleController();
            var wideMagneticFloor = new WideMagneticFloorController();
            var wideLowerWires = new WideLowerWireController();
            boolean wideDropFinished = false;
            boolean wideDescentFinished = false;
            int wideDescentStage = 0;
            TestFbzAct1RouteHeadless.EncounterInputDriver bossDriver = null;
            boolean wireReached = false;
            boolean upperJump = false;
            int previousMask = 0;
            boolean trapApproach = false;
            boolean trapAcquired = false;
            boolean trapReleased = false;
            boolean trapDone = false;
            boolean liftActive = false;
            boolean snakeReached = false;
            FbzSnakePlatformObjectInstance snakeTarget = null;
            boolean upperLeftActive = false;
            boolean upperTrapAcquired = false;
            boolean upperTrapReleased = false;
            boolean upperLeftDone = false;
            int movieRow = 0;
            for (int frame = 0; frame < 35_400; frame++) {
                int mask = movieRow == 0 ? 0 : movie.getFrame(
                        movie.bk2FrameToIndex(MOVIE_START + movieRow - 1)).p1InputMask();
                movieRow++;
                int px = player.getCentreX() & 0xFFFF;
                int py = player.getCentreY() & 0xFFFF;
                if (!outdoor.finished && ((px >= 0x850 && py >= 0x9D0) || outdoor.active)) {
                    outdoor.active = true;
                    mask = outdoor.next(player, GameServices.level().getObjectManager());
                    if (px >= 0xED0 && py < 0x850) {
                        assertTrue(outdoor.launcherAcquired && outdoor.launcherReleased,
                                "upper route requires actual launcher carry and release");
                        outdoor.finished = true;
                        movieRow = 3400;
                    }
                }
                if (outdoor.finished && !wireReached) {
                    if (player.isOnObject() && !player.getAir()
                            && player.getLatchedSolidObjectInstance() instanceof FbzWireCageObjectInstance) {
                        wireReached = true;
                        movieRow = 3900;
                        mask = 4;
                    } else {
                        mask = 4;
                        if (!player.getAir() && px <= 0xEA0 && (previousMask & 16) == 0) {
                            upperJump = true;
                            mask |= 16;
                        } else if (upperJump && player.getAir() && player.getYSpeed() < 0) {
                            mask |= 16;
                        }
                    }
                }
                // Let the terrain own the entire curved climb before
                // braking toward the trap on the upper floor.
                trapApproach |= wireReached && px >= 0x950 && py < 0x6C0
                        && player.getXSpeed() > 0;
                if (trapApproach && !trapDone) {
                    var trap = GameServices.level().getObjectManager()
                            .activeObjectsOfType(FbzFlamethrowerObjectInstance.class).stream()
                            .filter(o -> o.getX() == 0x990 && o.getY() == 0x6D8)
                            .findFirst().orElse(null);
                    if (trap != null) {
                        if (player.isOnObject() && !player.getAir()
                                && player.getLatchedSolidObjectInstance() == trap) {
                            trapAcquired = true;
                            // Neutral preserves inertia and can slide off
                            // before the native sixty-update standing timer.
                            mask = player.getMoveLockTimer() > 0 ? 0 : trapLandingMask(player, trap.getX());
                        } else if (trapAcquired && player.getYSpeed() < -0xC00) {
                            trapReleased = true;
                            mask = 0;
                        } else if (trapReleased) {
                            mask = 0;
                            if (px >= 0xA60 && py < 0x5B0) {
                                trapDone = true;
                                movieRow = 4300;
                            }
                        } else {
                            mask = trapLandingMask(player, trap.getX());
                            if (!player.getAir() && py >= trap.getY() - 0x18
                                    && player.getGroundMode() == com.openggf.game.GroundMode.GROUND
                                    && (previousMask & 16) == 0) mask |= 16;
                            else if (player.getAir() && player.getYSpeed() < 0
                                    && (previousMask & 16) != 0) mask |= 16;
                        }
                    }
                }
                if (trapDone && !snakeReached) {
                    var support = player.isOnObject() && !player.getAir()
                            ? player.getLatchedSolidObjectInstance() : null;
                    liftActive |= support instanceof FbzFloatingPlatformObjectInstance platform
                            && platform.getOutOfRangeReferenceX() == 0xD04;
                    if (liftActive) {
                        if (support instanceof FbzSnakePlatformObjectInstance) {
                            mask = 0;
                            if (px <= 0xCD0 && py <= 0x408) {
                                snakeReached = true;
                                movieRow = 5300;
                            }
                        } else if (support instanceof FbzFloatingPlatformObjectInstance platform) {
                            mask = RouteSteering.steerMask(player, platform.getX(), 2);
                            if (py <= 0x450 && (previousMask & 16) == 0) {
                                snakeTarget = GameServices.level().getObjectManager()
                                        .activeObjectsOfType(FbzSnakePlatformObjectInstance.class).stream()
                                        .filter(o -> o.getY() == 0x420 && o.getX() >= 0xD04
                                                && o.getX() <= 0xD64 && o.xVelocity() < 0)
                                        .min(java.util.Comparator.comparingInt(FbzSnakePlatformObjectInstance::getX))
                                        .orElse(null);
                                if (snakeTarget != null) mask |= 16;
                            }
                        } else if (snakeTarget != null) {
                            mask = RouteSteering.steerMask(player, snakeTarget.getX(), 2)
                                    | (player.getYSpeed() < 0 ? 16 : 0);
                        }
                    }
                }
                if (width > 320 && snakeReached && !wideSnake.finished && !upperLeftActive) {
                    mask = wideSnake.next(player, GameServices.level().getObjectManager());
                }
                upperLeftActive |= snakeReached && px < 0xA80 && py < 0x380;
                if (upperLeftActive && !upperLeftDone) {
                    var upperTrap = GameServices.level().getObjectManager()
                            .activeObjectsOfType(FbzFlamethrowerObjectInstance.class).stream()
                            .filter(o -> o.getX() == 0x8B0 && o.getY() == 0x378)
                            .findFirst().orElse(null);
                    if (upperTrap != null && player.isOnObject() && !player.getAir()
                            && player.getLatchedSolidObjectInstance() == upperTrap) {
                        upperTrapAcquired = true;
                        mask = player.getMoveLockTimer() > 0 ? 0 : trapLandingMask(player, upperTrap.getX());
                    } else if (upperTrapAcquired && player.getYSpeed() < -0xC00) {
                        upperTrapReleased = true;
                        mask = 0;
                    } else if (upperTrapReleased) {
                        mask = py <= 0x200 && !player.getAir() ? 8 : 0;
                        if (px >= 0x950 && py <= 0x200 && !player.getAir()) {
                            upperLeftDone = true;
                            movieRow = 6700;
                        }
                    } else if (px > 0x950) {
                        mask = 4;
                        if (!player.getAir() && px > 0x9C0 && py >= 0x340
                                && (previousMask & 16) == 0) mask |= 16;
                        else if (player.getAir() && player.getYSpeed() < 0
                                && (previousMask & 16) != 0) mask |= 16;
                    } else if (upperTrap != null) {
                        mask = trapLandingMask(player, upperTrap.getX());
                        if (!player.getAir() && py >= upperTrap.getY() - 0x18
                                && (previousMask & 16) == 0) mask |= 16;
                        else if (player.getAir() && player.getYSpeed() < 0
                                && (previousMask & 16) != 0) mask |= 16;
                    }
                }
                if (width > 320 && upperLeftDone && !widePoles.finished) {
                    mask = widePoles.next(player, GameServices.level().getObjectManager());
                    if (widePoles.finished) movieRow = 8100;
                }
                if (width > 320 && !wideMagneticFloor.finished
                        && (wideMagneticFloor.active || widePoles.finished && px >= 0x1030 && py >= 0x340)) {
                    wideMagneticFloor.active = true;
                    mask = wideMagneticFloor.next(player, GameServices.level().getObjectManager());
                    if (wideMagneticFloor.finished) movieRow = 9000;
                }
                if (width > 320 && wideMagneticFloor.finished && !wideDropFinished
                        && px >= 0x1400 && px < 0x1500 && py >= 0x500) {
                    mask = trapLandingMask(player, 0x1460);
                    if (player.isOnObject() && !player.getAir()
                            && player.getLatchedSolidObjectInstance() instanceof FbzFloatingPlatformObjectInstance drop
                            && drop.getOutOfRangeReferenceX() == 0x1460 && drop.movementMode() == 4
                            && drop.mode4Completed()) {
                        wideDropFinished = true;
                        movieRow = 9750;
                    }
                }
                if (width > 320 && wideDropFinished && !wideDescentFinished) {
                    Object support = player.isOnObject() && !player.getAir()
                            ? player.getLatchedSolidObjectInstance() : null;
                    if (wideDescentStage == 0) {
                        mask = trapLandingMask(player, 0x1420);
                        if (support instanceof FbzFloatingPlatformObjectInstance step
                                && step.getOutOfRangeReferenceX() == 0x1420 && py >= 0xA20) {
                            wideDescentStage = 1;
                        }
                    } else if (wideDescentStage == 1) {
                        mask = trapLandingMask(player, 0x1460);
                        if (support instanceof FbzFloatingPlatformObjectInstance step
                                && step.getOutOfRangeReferenceX() == 0x1460 && step.movementMode() == 1
                                && py >= 0xA50) wideDescentStage = 2;
                    } else {
                        mask = trapLandingMask(player, py < 0xAB0 ? 0x1410 : 0x1450);
                    }
                    if (py >= 0xAE0 && !player.getAir()) {
                        wideDescentFinished = true;
                        movieRow = 10300;
                    }
                }
                if (width > 320 && wideDescentFinished && !wideLowerWires.finished) {
                    mask = wideLowerWires.next(player, GameServices.level().getObjectManager());
                }
                boolean onLowerGap = player.isOnObject() && !player.getAir()
                        && player.getLatchedSolidObjectInstance() instanceof FbzFloatingPlatformObjectInstance platform
                        && platform.getOutOfRangeReferenceX() == 0x1940;
                if (!lower.finished && (lower.active || onLowerGap)) {
                    lower.active = true;
                    mask = lower.next(player);
                    if (lower.finished) movieRow = 12200;
                }
                if (!magnetic.finished && (magnetic.active
                        || (lower.finished && px >= 0x1C60 && py >= 0x800))) {
                    magnetic.active = true;
                    mask = magnetic.next(player, GameServices.level().getObjectManager());
                    if (magnetic.finished) {
                        assertEquals(4, GameServices.level().getCheckpointState().getLastCheckpointIndex(),
                                "the corridor must physically activate its real starpost");
                        movieRow = 12800;
                    }
                }
                if (!carriers.finished && (carriers.active
                        || (magnetic.finished && px >= 0x22A0 && py >= 0x600))) {
                    carriers.active = true;
                    mask = carriers.next(player, GameServices.level().getObjectManager());
                    if (carriers.finished) movieRow = 14200;
                }
                if (carriers.finished && !upperMissilePassed && px >= 0x2070 && px < 0x2200 && py < 0x200) {
                    mask = 8;
                    if (player.getAir()) {
                        mask = trapLandingMask(player, 0x2170) | (player.getYSpeed() < 0 ? 16 : 0);
                    } else if (px >= 0x2168) {
                        upperMissilePassed = true;
                        movieRow = 15200;
                        mask = 0;
                    } else if (px >= 0x20F0 && (previousMask & 16) == 0) {
                        mask |= 16;
                    }
                }
                if (upperMissilePassed && !firstRotatingPassed && px < 0x2300 && py < 0x220) {
                    mask = 8;
                    if (!player.getAir() && px >= 0x2190 && (previousMask & 16) == 0) mask |= 16;
                    else if (player.getAir()) {
                        mask = trapLandingMask(player, 0x22B0) | (player.getYSpeed() < 0 ? 16 : 0);
                    }
                    if (px >= 0x2270 && !player.getAir()) {
                        mask = trapLandingMask(player, 0x22B0);
                        if (Math.abs(px - 0x22B0) <= 4 && Math.abs(player.getGSpeed()) <= 0x20) {
                            firstRotatingPassed = true;
                            movieRow = 15700;
                        }
                    }
                }
                if (firstRotatingPassed && remainingRotatingStage < 2 && py < 0x240) {
                    int launchX = remainingRotatingStage == 0 ? 0x2320 : 0x2420;
                    int landingX = remainingRotatingStage == 0 ? 0x2408 : 0x2508;
                    mask = 8;
                    if (player.getAir()) {
                        mask = trapLandingMask(player, landingX) | (player.getYSpeed() < 0 ? 16 : 0);
                    } else if (px >= landingX - 0x20) {
                        mask = trapLandingMask(player, landingX);
                        if (Math.abs(px - landingX) <= 4 && Math.abs(player.getGSpeed()) <= 0x20) {
                            remainingRotatingStage++;
                            if (remainingRotatingStage == 2) movieRow = 16050;
                        }
                    } else if (px >= launchX && (previousMask & 16) == 0) {
                        mask |= 16;
                    }
                }
                if (remainingRotatingStage == 2 && !missileExitPassed) {
                    int launcherX = missileColumn == 0 ? 0x2530 : missileColumn == 1 ? 0x25B0 : 0x2630;
                    var launcher = GameServices.level().getObjectManager()
                            .activeObjectsOfType(FbzMissileLauncherObjectInstance.class).stream()
                            .filter(o -> o.getX() == launcherX).findFirst().orElse(null);
                    int phase = launcher == null ? 0 : (GameServices.level().getFrameCounter()
                            + launcher.phaseOffset()) & 0xFF;
                    int crossing = RouteSteering.ordinaryRightCrossingBudget(player, launcherX + 0x50 - px, 256);
                    // Three missiles launch 17 updates apart, rise for 64, then
                    // fall to this floor. After phase $B4 the complete burst has expired.
                    if (phase >= 0xB4 && 0xFF - phase > crossing
                            && (missileColumn < 2 || (launcher != null && !launcher.targeting()))) missileCrossing = true;
                    mask = missileCrossing || missileColumn >= 3 ? 8
                            : RouteSteering.steerMask(player, launcherX + 0x10, 3);
                    if (missileColumn < 3 && px > launcherX + 0x50) {
                        missileColumn++;
                        missileCrossing = false;
                    }
                    if (px >= 0x2690 && py >= 0x240) {
                        missileExitPassed = true;
                        movieRow = 16800;
                    }
                }
                if (missileExitPassed && !lateCarriers.finished && (lateCarriers.active || px >= 0x2A50)) {
                    lateCarriers.active = true;
                    mask = lateCarriers.next(player, GameServices.level().getObjectManager());
                    if (lateCarriers.finished) movieRow = 19400;
                }
                if (lateCarriers.finished && !carousel.finished) {
                    mask = carousel.next(player, GameServices.level().getObjectManager());
                    if (carousel.finished) movieRow = 19800;
                }
                if (carousel.finished) {
                    var liveBoss = GameServices.level().getObjectManager().activeObjectsOfType(FbzMinibossInstance.class)
                            .stream().findFirst().orElse(null);
                    if (liveBoss != null && (bossDriver != null || liveBoss.isPlungerStarted()) && !liveBoss.isDefeated()) {
                        if (bossDriver == null) {
                            assertEquals(5, GameServices.level().getCheckpointState().getLastCheckpointIndex());
                            bossDriver = new TestFbzAct1RouteHeadless.EncounterInputDriver(liveBoss, true);
                        }
                        var input = bossDriver.next(player, GameServices.level().getObjectManager());
                        mask = (px > input.targetX() + 2 ? 4 : px < input.targetX() - 2 ? 8 : 0)
                                | (input.jump() ? 16 : 0);
                    } else if (liveBoss != null && liveBoss.isDefeated()) {
                        assertEquals(6, liveBoss.scriptedImpactCount());
                        mask = 0;
                    } else if (bossDefeated) mask = 0;
                }
                if (GameServices.level().getCurrentAct() == 1) mask = 0;
                previousMask = mask;
                fixture.stepFrame((mask & 1) != 0, (mask & 2) != 0,
                        (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0);
                var objects = GameServices.level().getObjectManager();
                for (var boss : objects.activeObjectsOfType(FbzMinibossInstance.class)) {
                    bossSeen = true;
                    bossDefeated |= boss.isDefeated();
                }
                signSeen |= !objects.activeObjectsOfType(S3kSignpostInstance.class).isEmpty();
                resultsSeen |= !objects.activeObjectsOfType(S3kResultsScreenObjectInstance.class).isEmpty();
                String state = String.format("f%d i%02X p%04X,%04X v%04X,%04X air%s owner%s cam%04X,%04X",
                        frame, mask, player.getCentreX() & 0xFFFF, player.getCentreY() & 0xFFFF,
                        player.getXSpeed() & 0xFFFF, player.getYSpeed() & 0xFFFF, player.getAir(),
                        player.getLatchedSolidObjectInstance() == null ? "none"
                                : player.getLatchedSolidObjectInstance().getClass().getSimpleName(),
                        fixture.camera().getX() & 0xFFFF, fixture.camera().getY() & 0xFFFF);
                history.addLast(state);
                if (history.size() > 45) history.removeFirst();
                assertFalse(player.getDead(), () -> "cold route died " + history
                        + " controller=" + outdoor + "; magnetic=" + magnetic
                        + "; carriers=" + carriers + "; late=" + lateCarriers + "; carousel=" + carousel
                        + " nearby=" + objects.getActiveObjects().stream()
                        .filter(o -> o.getSpawn() != null)
                        .filter(o -> Math.abs(o.getX() - player.getCentreX()) < 0x180)
                        .map(o -> o.getClass().getSimpleName() + "@" + Integer.toHexString(o.getX())
                                + "," + Integer.toHexString(o.getY())).toList());
                if (GameServices.level().getCurrentAct() == 1) {
                    assertTrue(bossSeen && bossDefeated && signSeen && resultsSeen,
                            "Act2 requires the full real miniboss/sign/results sequence");
                    assertSame(player, GameServices.sprites().getMainPlayable());
                    if (objects.activeObjectsOfType(S3kResultsScreenObjectInstance.class).isEmpty()
                            && GameServices.module().getTitleCardProvider().isComplete()) {
                        assertFalse(player.isControlLocked());
                        assertFalse(player.isObjectControlled());
                        System.out.printf("FBZ1 cold %dpx donor=%s sidekick=%s completed with released Act2 control after %d ordinary frames%n",
                                width, donor, sidekick, frame + 1);
                        return;
                    }
                }
            }
            fail("cold Act1 route exhausted act budget: " + history);
        } finally {
            configuration.clearSessionOverrides();
            previous.forEach(configuration::setSessionOverride);
            configuration.resolveDisplayAspect();
            graphics.setViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        }
    }
    private static final class WideLowerWireController {
        int stage;
        int lastMask;
        boolean finished;
        boolean finalWalkReady;
        int previousPlatformY;

        int next(AbstractPlayableSprite player, ObjectManager objects) {
            int x = player.getCentreX() & 0xFFFF;
            Object support = player.isOnObject() && !player.getAir()
                    ? player.getLatchedSolidObjectInstance() : null;
            if (support instanceof FbzWireCageObjectInstance wire) {
                if (wire.getX() == 0x1700 && stage == 0) stage = 1;
                if (wire.getX() == 0x1880 && stage == 2) stage = 3;
            }
            if (support instanceof FbzFloatingPlatformObjectInstance platform) {
                if (platform.getOutOfRangeReferenceX() == 0x17C0 && stage == 1) stage = 2;
                if (platform.getOutOfRangeReferenceX() == 0x1940) finished = true;
            }
            int target = stage == 0 ? 0x1700 : stage == 1 ? 0x17C0 : stage == 2 ? 0x1880 : 0x1940;
            int launch = stage == 0 ? 0x15E0 : stage == 1 ? 0x1750 : stage == 2 ? 0x17C8 : 0x18E0;
            var landing = objects.activeObjectsOfType(FbzFloatingPlatformObjectInstance.class).stream()
                    .filter(o -> o.getOutOfRangeReferenceX() == 0x1940).findFirst().orElse(null);
            int departureTicks = 0;
            int projectedX = x << 8;
            int projectedSpeed = player.getGSpeed();
            while (projectedX < 0x190000 && departureTicks < 100) {
                projectedSpeed = Math.min(player.getMax(), projectedSpeed + player.getRunAccel());
                projectedX += projectedSpeed;
                departureTicks++;
            }
            // Horizontal wire rotation advances four native angle units per
            // update. Leave near its upper arc; a low departure falls below
            // the next platform before crossing its left collision edge.
            int departureAngle = (player.getFlipAngle() + 4 * departureTicks) & 0xFF;
            int departureY = 0xAC0
                    + (com.openggf.physics.TrigLookupTable.cosHex(departureAngle) * 0x28 >> 8);
            if (stage == 3 && landing != null && landing.getY() >= 0xAE0
                    && landing.getY() > previousPlatformY && Math.abs(x - 0x18F0) <= 3
                    && departureY <= 0xAA0) finalWalkReady = true;
            if (landing != null) previousPlatformY = landing.getY();
            int mask;
            if (stage == 3 && !finalWalkReady) {
                mask = trapLandingMask(player, 0x18F0);
            } else if (stage == 3) {
                // The final wire ends above the platform: walk off its edge
                // during a real descending-platform phase rather than jumping
                // into the low ceiling over this handoff.
                mask = player.getAir() ? trapLandingMask(player, target) : 8;
            } else if (player.getAir()) {
                // The second cage rejects landing for forty updates after
                // entering its upper band. A short ordinary jump from the low
                // intermediate platform stays below that band.
                mask = trapLandingMask(player, target) | (stage != 2 && player.getYSpeed() < 0 ? 16 : 0);
            } else if (stage == 2 && (player.getCentreY() & 0xFFFF) < 0xAC0) {
                mask = trapLandingMask(player, 0x17C0);
            } else {
                mask = 8;
                if (x >= launch && (lastMask & 16) == 0) mask |= 16;
            }
            lastMask = mask;
            return mask;
        }
    }

    private static final class WideMagneticFloorController {
        boolean active;
        boolean finished;
        boolean crossing;

        int next(AbstractPlayableSprite player, ObjectManager objects) {
            int x = player.getCentreX() & 0xFFFF;
            int y = player.getCentreY() & 0xFFFF;
            var platforms = objects.activeObjectsOfType(FbzMagneticPlatformObjectInstance.class).stream()
                    .filter(o -> o.getX() >= x - 0x30 && o.getX() <= 0x12C0)
                    .toList();
            var runtime = GameServices.zoneRuntimeRegistry()
                    .currentAs(com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.class).orElseThrow();
            // The native platforms have eight-pixel harmful half-height. Walk
            // below their actual raised envelope, with enough active time to
            // leave the entire authored corridor before polarity reverses.
            boolean clear = !platforms.isEmpty() && platforms.stream()
                    .allMatch(o -> o.getY() + 8 + 2 < y - player.getYRadius());
            int crossingFrames = RouteSteering.ordinaryRightCrossingBudget(player, 0x1300 - x, 600);
            if (clear && runtime.magneticPolarity()
                    == com.openggf.game.sonic3k.events.Sonic3kFBZEvents.MagneticPolarity.ACTIVE
                    && 0xFF - runtime.magneticTimerPhase() > crossingFrames) crossing = true;
            if (x >= 0x1300 && !player.getAir()) finished = true;
            return crossing ? 8 : RouteSteering.steerMask(player, 0x1070, 3);
        }
    }

    private static final class WidePoleController {
        // Actual pole centres/base heights, with release levels between the
        // authored propeller planes. Release is native RIGHT/LEFT+JUMP only.
        private static final int[][] ROUTE = {
                {0xA08, 0x1E8, 0x1E8, 8}, {0xB08, 0x1E8, 0x168, 8},
                {0xC88, 0x168, 0x138, 8}, {0xD88, 0x168, 0x130, 8},
                {0xE88, 0x168, 0x130, 8}, {0xF88, 0x168, 0x0D0, 4},
                {0xE88, 0x0E8, 0x070, 8}, {0xF88, 0x0A8, 0x070, 8}
        };
        int stage;
        int lastMask;
        boolean finished;

        int next(AbstractPlayableSprite player, ObjectManager objects) {
            int x = player.getCentreX() & 0xFFFF;
            int y = player.getCentreY() & 0xFFFF;
            if (stage == ROUTE.length) {
                finished = x >= 0x11A0;
                return 8;
            }
            int[] waypoint = ROUTE[stage];
            var pole = objects.activeObjectsOfType(FbzSpinningPoleObjectInstance.class).stream()
                    .filter(o -> o.getX() == waypoint[0] && o.getY() == waypoint[1]).findFirst().orElse(null);
            boolean held = pole != null && player.isObjectControlled()
                    && Math.abs(x - pole.getX()) <= 0x18
                    && y <= pole.getY() && y >= pole.getY() - pole.poleHeight();
            int mask;
            if (held) {
                if (y > waypoint[2]) mask = 1;
                else if ((lastMask & 16) == 0) {
                    mask = waypoint[3] | 16;
                    stage++;
                } else mask = 0;
            } else {
                mask = x < waypoint[0] ? 8 : 4;
                if (stage == 0 && (!player.getAir() && (lastMask & 16) == 0
                        || player.getAir() && player.getYSpeed() < 0)) mask |= 16;
            }
            lastMask = mask;
            return mask;
        }
    }

    private static final class WideSnakeController {
        int stage;
        int lastMask;
        boolean finished;
        FbzSnakePlatformObjectInstance target;

        int next(AbstractPlayableSprite player, ObjectManager objects) {
            int x = player.getCentreX() & 0xFFFF;
            int y = player.getCentreY() & 0xFFFF;
            Object support = player.isOnObject() && !player.getAir()
                    ? player.getLatchedSolidObjectInstance() : null;
            int mask;
            if (stage == 0) {
                mask = trapLandingMask(player, 0xC68);
                if (support instanceof FbzSnakePlatformObjectInstance && x <= 0xC90 && (lastMask & 16) == 0) mask |= 16;
                else if (player.getAir() && player.getYSpeed() < 0) mask |= 16;
                if (support instanceof FbzBentPipeObjectInstance && y <= 0x400) stage = 1;
            } else if (stage == 1) {
                if (support instanceof FbzSnakePlatformObjectInstance actual && actual.getY() < 0x400) {
                    target = actual;
                    stage = 2;
                    mask = 0;
                } else {
                    if (target == null) {
                        target = objects.activeObjectsOfType(FbzSnakePlatformObjectInstance.class).stream()
                                .filter(o -> o.getY() == 0x3C8 && o.getX() >= 0xC60 && o.getX() <= 0xCA0
                                        && o.xVelocity() < 0).findFirst().orElse(null);
                    }
                    mask = target == null ? trapLandingMask(player, 0xC68)
                            : trapLandingMask(player, target.getX())
                                | (!player.getAir() && (lastMask & 16) == 0 || player.getYSpeed() < 0 ? 16 : 0);
                }
            } else if (stage == 2) {
                mask = 0;
                if (x <= 0xBA8 && y <= 0x3C0) {
                    stage = 3;
                    mask = 4 | 16;
                }
            } else if (stage == 3) {
                mask = trapLandingMask(player, 0xB70) | (player.getAir() && player.getYSpeed() < 0 ? 16 : 0);
                if (support instanceof FbzBentPipeObjectInstance && y < 0x3B0) stage = 4;
            } else {
                mask = 4;
                if (!player.getAir() && x <= 0xB50 && (lastMask & 16) == 0) mask |= 16;
                else if (player.getAir() && player.getYSpeed() < 0) mask |= 16;
                finished = x < 0xA80 && y < 0x380;
            }
            lastMask = mask;
            return mask;
        }
    }

    private static final class CarouselController {
        boolean finished;
        int stage;
        int lastMask;
        FbzRotatingPlatformObjectInstance target;

        int next(AbstractPlayableSprite player, ObjectManager objects) {
            int x = player.getCentreX() & 0xFFFF;
            int y = player.getCentreY() & 0xFFFF;
            if (stage == 0 && target == null && y <= 0x780 && Math.abs(x - 0x2BCC) <= 4) {
                target = objects.activeObjectsOfType(FbzRotatingPlatformObjectInstance.class).stream()
                        .filter(o -> o.getOutOfRangeReferenceX() == 0x2C80)
                        .filter(o -> Math.abs((byte) o.memberRadius()) == 0x5C)
                        .filter(o -> o.getX() < 0x2C80 && o.getY() >= 0x7A4)
                        .findFirst().orElse(null);
            }
            if (stage == 0 && player.isOnObject() && !player.getAir()
                    && player.getLatchedSolidObjectInstance() instanceof FbzRotatingPlatformObjectInstance actual) {
                target = actual;
                stage = 1;
            }
            int mask;
            if (stage == 0) {
                if (target == null) mask = trapLandingMask(player, 0x2BCC);
                else if (!player.getAir()) mask = 8 | (x >= 0x2BEC && (lastMask & 16) == 0 ? 16 : 0);
                else mask = trapLandingMask(player, target.getX()) | (player.getYSpeed() < 0 ? 16 : 0);
            } else if (stage == 1) {
                mask = 0;
                if (y <= 0x730 && x >= 0x2CB0) {
                    stage = 2;
                    mask = 8 | 16;
                }
            } else if (stage == 2) {
                var landing = objects.activeObjectsOfType(FbzFloatingPlatformObjectInstance.class).stream()
                        .filter(o -> o.getOutOfRangeReferenceX() == 0x2CE0).findFirst().orElse(null);
                mask = trapLandingMask(player, 0x2CE0) | (player.getAir() && player.getYSpeed() < 0 ? 16 : 0);
                if (landing != null && player.isOnObject() && !player.getAir()
                        && player.getLatchedSolidObjectInstance() == landing) {
                    stage = 3;
                    target = null;
                }
            } else if (stage == 3) {
                if (target == null) {
                    target = objects.activeObjectsOfType(FbzRotatingPlatformObjectInstance.class).stream()
                            .filter(o -> o.getOutOfRangeReferenceX() == 0x2C80)
                            .filter(o -> Math.abs((byte) o.memberRadius()) == 0x5C)
                            .filter(o -> o.getX() > 0x2C80 && o.getY() >= 0x680 && o.getY() <= 0x6B0)
                            .findFirst().orElse(null);
                }
                mask = target == null ? trapLandingMask(player, 0x2CE0)
                        : trapLandingMask(player, target.getX()) | (!player.getAir() || player.getYSpeed() < 0 ? 16 : 0);
                if (target != null && player.isOnObject() && !player.getAir()
                        && player.getLatchedSolidObjectInstance() instanceof FbzRotatingPlatformObjectInstance actual) {
                    target = actual;
                    stage = 4;
                }
            } else if (stage == 4) {
                mask = 0;
                if (x >= 0x2C80 && y <= 0x620) {
                    stage = 5;
                    mask = 8 | 16;
                }
            } else {
                mask = trapLandingMask(player, 0x2D90) | (player.getAir() && player.getYSpeed() < 0 ? 16 : 0);
                finished = !player.getAir() && y < 0x620 && x >= 0x2D60;
            }
            lastMask = mask;
            return mask;
        }

        @Override public String toString() { return stage + "/" + finished + "/"
                + (target == null ? "none" : "%04X,%04X".formatted(target.getX(), target.getY())); }
    }

    private static final class LateCarrierController {
        boolean active;
        boolean finished;
        int stage;
        int lastMask;

        int next(AbstractPlayableSprite player, ObjectManager objects) {
            int x = player.getCentreX() & 0xFFFF;
            if (player.isObjectControlled()
                    && player.getLatchedSolidObjectInstance() instanceof FbzChainLinkObjectInstance chain) {
                stage = 2;
                int mask = 8 | (x >= chain.getX() + chain.rangePixels() - 0x20 ? 16 : 0);
                lastMask = mask;
                return mask;
            }
            int targetX = stage < 2 ? 0x2AD4 : 0x2BCC;
            var target = objects.activeObjectsOfType(FbzMagneticPlatformObjectInstance.class).stream()
                    .filter(o -> o.getX() == targetX).findFirst().orElse(null);
            boolean riding = target != null && player.isOnObject() && !player.getAir()
                    && player.getLatchedSolidObjectInstance() == target;
            int mask;
            if (riding) {
                mask = trapLandingMask(player, targetX);
                if (target.displacement() >= target.maximumRise() - 2) {
                    if (stage < 2) {
                        stage = 1;
                        mask = 8 | ((lastMask & 16) == 0 ? 16 : 0);
                    } else finished = true;
                }
            } else if (stage == 1) {
                mask = trapLandingMask(player, 0x2B40) | (player.getYSpeed() < 0 ? 16 : 0);
            } else if (player.getAir()) {
                mask = trapLandingMask(player, targetX) | (player.getYSpeed() < 0 ? 16 : 0);
            } else {
                mask = trapLandingMask(player, targetX) | ((lastMask & 16) == 0 ? 16 : 0);
            }
            lastMask = mask;
            return mask;
        }

        @Override public String toString() { return stage + "/" + finished; }
    }

    private static final class MagneticCarrierController {
        boolean active;
        boolean finished;
        int stage;
        int lastMask;
        String geometry = "";

        int next(AbstractPlayableSprite player, ObjectManager objects) {
            int x = player.getCentreX() & 0xFFFF;
            int y = player.getCentreY() & 0xFFFF;
            int targetX = stage >= 4 ? 0x2540 : stage == 0 ? 0x2340 : 0x23C0;
            var target = objects.activeObjectsOfType(FbzMagneticPlatformObjectInstance.class).stream()
                    .filter(o -> o.getX() == targetX).findFirst().orElse(null);
            boolean riding = player.isOnObject() && !player.getAir()
                    && player.getLatchedSolidObjectInstance() == target && target != null;
            if (riding && stage < 2) {
                stage++;
                lastMask = 0;
                return 0;
            }
            int mask;
            if (stage >= 4) {
                if (riding) {
                    stage = 5;
                    mask = trapLandingMask(player, 0x252A);
                    finished = target.displacement() >= target.maximumRise() - 2;
                } else if (player.getAir()) {
                    mask = trapLandingMask(player, 0x2540) | (player.getYSpeed() < 0 ? 16 : 0);
                } else {
                    var runtime = GameServices.zoneRuntimeRegistry()
                            .currentAs(com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.class).orElseThrow();
                    boolean low = target != null && target.getY() >= 0x540;
                    boolean phase = runtime.magneticPolarity()
                            == com.openggf.game.sonic3k.events.Sonic3kFBZEvents.MagneticPolarity.INACTIVE
                            && runtime.magneticTimerPhase() < 0x90;
                    mask = low && phase ? 8 | (x >= 0x24A8 && (lastMask & 16) == 0 ? 16 : 0)
                            : RouteSteering.steerMask(player, 0x2490, 3);
                }
            } else if (stage >= 2) {
                if (riding && target.displacement() >= target.maximumRise() - 2) {
                    mask = 8 | ((lastMask & 16) == 0 ? 16 : 0);
                    stage = 3;
                } else if (stage == 3) {
                    mask = trapLandingMask(player, 0x2490)
                            | (player.getAir() && player.getYSpeed() < 0 ? 16 : 0);
                    if (!player.getAir() && x >= 0x2490 && y < 0x580) {
                        stage = 4;
                        mask = 0;
                    }
                } else {
                    mask = trapLandingMask(player, targetX);
                }
            } else if (player.getAir()) {
                mask = trapLandingMask(player, targetX) | (player.getYSpeed() < 0 ? 16 : 0);
            } else {
                var runtime = GameServices.zoneRuntimeRegistry()
                        .currentAs(com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.class).orElseThrow();
                boolean low = target != null && target.getY() >= 0x640;
                boolean phase = runtime.magneticPolarity()
                        == com.openggf.game.sonic3k.events.Sonic3kFBZEvents.MagneticPolarity.INACTIVE
                        && runtime.magneticTimerPhase() < 0x90;
                mask = low && phase ? 8 | ((lastMask & 16) == 0 ? 16 : 0)
                        : RouteSteering.steerMask(player, stage == 0 ? 0x22D0 : 0x2340, 3);
            }
            geometry = target == null ? "missing" : "%04X/%04X/%d".formatted(
                    target.getX(), target.getY(), target.displacement());
            lastMask = mask;
            return mask;
        }

        @Override public String toString() { return stage + "/" + finished + "/" + geometry; }
    }

    private static final class MagneticCorridorController {
        boolean active;
        boolean finished;
        boolean staticBallPassed;
        boolean committed;
        int committedLastBallX;
        String clearance = "";
        int lastMask;

        int next(AbstractPlayableSprite player, ObjectManager objects) {
            int x = player.getCentreX() & 0xFFFF;
            int y = player.getCentreY() & 0xFFFF;
            int mask;
            if (!staticBallPassed) {
                staticBallPassed = !player.getAir() && x >= 0x1CE8;
                if (staticBallPassed) {
                    mask = 0;
                } else if (player.getAir()) {
                    mask = trapLandingMask(player, 0x1CF0) | (player.getYSpeed() < 0 ? 16 : 0);
                } else {
                    mask = 8;
                    if (x >= 0x1C78 && (lastMask & 16) == 0) mask |= 16;
                }
            } else {
                var balls = objects.activeObjectsOfType(FbzMagneticSpikeBallObjectInstance.class).stream()
                        .filter(o -> o.kind() == FbzMagneticSpikeBallObjectInstance.Kind.BALL)
                        .filter(o -> o.getX() >= x - 0x21 && o.getX() <= 0x1F40)
                        .sorted(java.util.Comparator.comparingInt(FbzMagneticSpikeBallObjectInstance::getX))
                        .toList();
                var runtime = GameServices.zoneRuntimeRegistry()
                        .currentAs(com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.class).orElseThrow();
                int lastBall = balls.isEmpty() ? 0 : balls.getLast().getX();
                if (lastBall > committedLastBallX) committed = false;
                int runway = 0xFF - runtime.magneticTimerPhase();
                int crossing = RouteSteering.ordinaryRightCrossingBudget(player,
                        lastBall + 0x22 - x, 600);
                // Touch_Sizes[$1A] has $0C half-height. Require a two-pixel
                // gap from the live standing radius; the local ceiling stops at $839.
                boolean raised = balls.stream().allMatch(o -> o.rising()
                        && o.getY() + 0x0C + 2 < y - player.getYRadius());
                clearance = runtime.magneticPolarity() + ":" + runtime.magneticTimerPhase()
                        + ":" + raised + ":" + crossing + ":"
                        + balls.stream().map(o -> "%04X/%04X/%s".formatted(o.getX(), o.getY(), o.rising())).toList();
                if (balls.isEmpty() || (raised
                        && runtime.magneticPolarity()
                            == com.openggf.game.sonic3k.events.Sonic3kFBZEvents.MagneticPolarity.ACTIVE
                        && runway > crossing)) {
                    committed = true;
                    committedLastBallX = lastBall;
                }
                // Newly streamed balls must settle before entering their envelope, but
                // they do not require retreating across balls already cleared by this gate.
                var pending = balls.stream().filter(o -> o.getX() > committedLastBallX).toList();
                int holdX = (pending.isEmpty() ? balls : pending).stream()
                        .mapToInt(FbzMagneticSpikeBallObjectInstance::getX).findFirst().orElse(x + 0x48) - 0x48;
                mask = committed || balls.isEmpty() ? 8
                        : RouteSteering.steerMask(player, holdX, 3);
                if (x >= 0x1FD0 && y <= 0x880 && !player.getAir()) finished = true;
            }
            lastMask = mask;
            return mask;
        }

        @Override public String toString() { return active + "/" + staticBallPassed + "/" + committed + "/" + finished + "/" + clearance; }
    }

    private static final class LowerGapController {
        private final int routeWidth;

        LowerGapController(int routeWidth) {
            this.routeWidth = routeWidth;
        }

        boolean active;
        boolean finished;
        int stage;
        int lastMask;

        int next(AbstractPlayableSprite player) {
            int x = player.getCentreX() & 0xFFFF;
            int y = player.getCentreY() & 0xFFFF;
            var support = player.isOnObject() && !player.getAir()
                    ? player.getLatchedSolidObjectInstance() : null;
            if (stage == 0 && support instanceof FbzWireCageObjectInstance cage
                    && cage.getX() == 0x1A00) stage = 1;
            if (support instanceof FbzFloatingPlatformObjectInstance platform
                    && platform.getOutOfRangeReferenceX() == 0x1B20) stage = 2;
            if ((stage == 1 || routeWidth > 320 && stage == 2 && y >= 0xAE0)
                    && !player.getAir() && support == null && x >= 0x1AC0) stage = 3;
            int mask;
            if (stage == 3) {
                mask = RouteSteering.steerMask(player, 0x1B20, 2);
                if (Math.abs(x - 0x1B20) <= 4 && Math.abs(player.getGSpeed()) <= 0x20) {
                    if (player.getDirection() != com.openggf.physics.Direction.RIGHT) mask = 8;
                    else { stage = 4; mask = 2; }
                }
            } else if (stage == 4) {
                // Fewer ordinary taps give the wider route enough energy for
                // the curve without carrying P1 past the small platform top.
                int chargeTarget = routeWidth > 320 ? 0x400 : 0x600;
                if (player.getSpindash() && player.getSpindashCounter() >= chargeTarget) {
                    stage = 5;
                    mask = 8;
                } else mask = 2 | ((lastMask & 16) == 0 ? 16 : 0);
            } else if (stage == 5) {
                // A normal charged roll follows the curved wall. Directional
                // input on its ceiling would brake away the climbing momentum.
                mask = player.getAir() && y < 0xAB0 ? trapLandingMask(player, 0x1B20) : 0;
            } else if (stage == 2) {
                mask = routeWidth > 320 ? trapLandingMask(player, 0x1B20)
                        : RouteSteering.steerMask(player, 0x1B20, 2);
                if (y <= 0x890 && support != null) finished = true;
            } else {
                int targetX = stage == 0 ? 0x1A00 : 0x1B20;
                if (player.getAir()) {
                    mask = trapLandingMask(player, targetX) | (player.getYSpeed() < 0 ? 16 : 0);
                } else if (stage == 0) {
                    mask = 8;
                    if (x >= 0x1950 && (lastMask & 16) == 0) mask |= 16;
                } else {
                    // The native path leaves this cage onto the lower floor,
                    // then rolls up the curved wall to the elevated platform.
                    mask = 8;
                }
            }
            lastMask = mask;
            return mask;
        }

        @Override public String toString() { return stage + "/" + active + "/" + finished; }
    }

    private static int trapLandingMask(AbstractPlayableSprite player, int targetX) {
        // Air braking is much weaker than floor friction. Reserve the full
        // ordinary air stopping distance while descending onto the small top.
        int velocity = player.getXSpeed();
        int acceleration = Math.max(1, 2 * player.getRunAccel());
        int stoppingDistance = velocity * Math.abs(velocity) / (2 * acceleration * 0x100);
        return RouteSteering.steerMask((player.getCentreX() & 0xFFFF) + stoppingDistance, targetX, 2);
    }

    private static final class OutdoorController {
        private final int routeWidth;

        OutdoorController(int routeWidth) {
            this.routeWidth = routeWidth;
        }

        boolean active;
        boolean finished;
        FbzFloatingPlatformObjectInstance target;
        int departedAnchor = -1;
        boolean jumping;
        int lastMask;
        boolean launcherAcquired;
        boolean launcherReleased;

        int next(AbstractPlayableSprite player, ObjectManager objects) {
            int x = player.getCentreX() & 0xFFFF;
            if (player.isObjectControlled()
                    && player.getLatchedSolidObjectInstance() instanceof FbzChainLinkObjectInstance chain) {
                target = null;
                int edge = chain.getX() + chain.rangePixels() - 0x20;
                return 8 | (chain.horizontalMode() && x >= edge ? 16 : 0);
            }
            var support = !player.getAir() && player.isOnObject()
                    && player.getLatchedSolidObjectInstance() instanceof FbzFloatingPlatformObjectInstance platform
                    ? platform : null;
            if (support != null) {
                departedAnchor = support.getOutOfRangeReferenceX();
                jumping = false;
                target = null;
            }
            if (target == null) {
                int minimum = x - 0x20;
                target = objects.activeObjectsOfType(FbzFloatingPlatformObjectInstance.class).stream()
                        .filter(p -> !p.isDestroyed() && p.getX() >= minimum)
                        .filter(p -> p.getOutOfRangeReferenceX() > departedAnchor)
                        .filter(p -> p.getY() >= 0x900)
                        .min(java.util.Comparator.comparingInt(FbzFloatingPlatformObjectInstance::getX)).orElse(null);
            }
            if (departedAnchor >= 0xC90 && x >= 0xD20) {
                var launcher = objects.activeObjectsOfType(FbzDezPlayerLauncherObjectInstance.class)
                        .stream().filter(o -> o.getY() == 0xA80).findFirst().orElse(null);
                if (launcher != null) {
                    if (player.isOnObject() && !player.getAir()
                            && player.getLatchedSolidObjectInstance() == launcher) {
                        launcherAcquired = true;
                        lastMask = 0;
                        return 0;
                    }
                    launcherReleased |= launcherAcquired && launcher.returning();
                    if (launcherReleased) {
                        lastMask = 8;
                        return 8;
                    }
                    int approach = RouteSteering.steerMask(player, launcher.getX(), 2);
                    if (!player.getAir() && (lastMask & 16) == 0) {
                        jumping = true;
                        approach |= 16;
                    } else if (player.getAir() && jumping && player.getYSpeed() < 0) {
                        approach |= 16;
                    }
                    lastMask = approach;
                    return approach;
                }
            }
            int aimX = target == null ? x + 0x80
                    : departedAnchor >= 0xB10 ? landingAim(player, target) : target.getX();
            int mask;
            if (player.getAir()) {
                mask = RouteSteering.steerMask(player, aimX, 3)
                        | (jumping && player.getYSpeed() < 0 ? 16 : 0);
            } else if (routeWidth > 320 && support != null && departedAnchor == 0x9C0
                    && (player.getCentreY() & 0xFFFF) > 0xA20
                        + 3 * player.getJump() * player.getJump() / (8 * (int) player.getGravity() * 0x100)) {
                mask = RouteSteering.steerMask(player, support.getX(), 2);
            } else if (support != null && target != null && departedAnchor >= 0xB10
                    && !canReachLanding(player, target)) {
                // Wait on actual support for a reachable phase. A circle that
                // is close now can be beyond the jump's reach on descent.
                mask = RouteSteering.steerMask(player, support.getX(), 2);
            } else if (support != null && x < support.getX() + 0x10) {
                mask = 8;
            } else if (support == null && x < 0x880) {
                mask = 8;
            } else if ((lastMask & 16) == 0) {
                jumping = true;
                mask = 24;
            } else {
                mask = 8;
            }
            lastMask = mask;
            return mask;
        }

        private static boolean canReachLanding(AbstractPlayableSprite player,
                                                        FbzFloatingPlatformObjectInstance platform) {
            int clock = GameServices.level().getFrameCounter();
            int sign = (platform.getSpawn().renderFlags() & 1) != 0 ? -1 : 1;
            int phase = (sign * clock + platform.phase()) & 0xFF;
            int anchorY = platform.getY()
                    - (com.openggf.physics.TrigLookupTable.cosHex(phase) >> 2);
            int y = ((player.getCentreY() & 0xFFFF)
                    + player.getYRadius() - player.getRollYRadius()) << 8;
            int x = (player.getCentreX() & 0xFFFF) << 8;
            int vx = player.getXSpeed();
            int vy = -player.getJump();
            for (int tick = 1; tick <= 90; tick++) {
                vx = Math.min(player.getMax(), vx + 2 * player.getRunAccel());
                if (vy < 0 && vy >= -0x400) vx -= vx >> 5;
                x += vx;
                y += vy;
                vy += (int) player.getGravity();
                int angle = (sign * (clock + tick) + platform.phase()) & 0xFF;
                int surface;
                int landingX;
                if (platform.movementMode() == 3) {
                    surface = anchorY + (com.openggf.physics.TrigLookupTable.cosHex(angle) >> 2) - 0x19;
                    landingX = platform.getOutOfRangeReferenceX()
                            + (com.openggf.physics.TrigLookupTable.sinHex(angle) >> 2);
                } else {
                    // A vertical oscillator can rise during flight. Its ROM
                    // amplitude bounds the earliest possible top crossing.
                    int amplitude = platform.movementMode() == 1 ? 0x20
                            : platform.movementMode() == 2 ? 0x40 : 8;
                    int topmostY = platform.getY() - 2 * amplitude;
                    if (platform.movementMode() == 1 || platform.movementMode() == 2) {
                        int offset = platform.movementMode() == 1 ? 0x08 : 0x1C;
                        int displacement = com.openggf.game.OscillationManager.getByte(offset) - amplitude;
                        int anchor = platform.getY() - sign * displacement;
                        topmostY = anchor - amplitude;
                    }
                    surface = topmostY - 0x19;
                    landingX = platform.getX();
                }
                if (vy >= 0 && (y >> 8) + player.getRollYRadius() >= surface) {
                    return (x >> 8) >= landingX - platform.getSolidParams().halfWidth()
                            + player.getXRadius() + 8;
                }
            }
            return false;
        }

        private static int landingAim(AbstractPlayableSprite player,
                                      FbzFloatingPlatformObjectInstance platform) {
            if (platform.movementMode() != 3) return platform.getX();
            // Obj71 mode 3 reads the live low byte of Level_frame_counter.
            // Forecast that same circle until the player's descending feet
            // cross its top; only pad steering consumes this estimate.
            int clock = GameServices.level().getFrameCounter();
            int sign = (platform.getSpawn().renderFlags() & 1) != 0 ? -1 : 1;
            int phase = (sign * clock + platform.phase()) & 0xFF;
            int anchorY = platform.getY()
                    - (com.openggf.physics.TrigLookupTable.cosHex(phase) >> 2);
            int y = (player.getCentreY() & 0xFFFF) << 8;
            int velocity = player.getYSpeed();
            int angle = phase;
            for (int tick = 1; tick <= 90; tick++) {
                y += velocity;
                velocity += (int) player.getGravity();
                angle = (sign * (clock + tick) + platform.phase()) & 0xFF;
                int surfaceY = anchorY + (com.openggf.physics.TrigLookupTable.cosHex(angle) >> 2) - 0x19;
                if (velocity >= 0 && (y >> 8) + player.getYRadius() >= surfaceY) break;
            }
            return platform.getOutOfRangeReferenceX()
                    + (com.openggf.physics.TrigLookupTable.sinHex(angle) >> 2);
        }

        @Override public String toString() {
            return "outdoor(target=" + (target == null ? "none"
                    : Integer.toHexString(target.getX()) + "," + Integer.toHexString(target.getY()))
                    + ",departed=" + Integer.toHexString(departedAnchor) + ",jump=" + jumping
                    + ",launcher=" + launcherAcquired + "/" + launcherReleased + ")";
        }
    }

}
