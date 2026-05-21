package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.IczPathFollowPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kSpringObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kIcz1PathFollowPlatformHeadless {
    private static final int START_X = 22142;
    private static final int START_Y = 1049;
    private static final int PLATFORM_X = 22211;
    private static final int PLATFORM_Y = 1050;
    private static final int WATCH_Y = 1205;
    private static final int JUMP_DELAY_AFTER_ACTIVATION = 170;
    private static final int JUMP_HOLD_FRAMES = 1;
    private static final int MAX_FRAMES = 1800;

    private static SharedLevel sharedLevel;
    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;

    private HeadlessTestFixture fixture;
    private AbstractPlayableSprite sprite;
    private IczPathFollowPlatformObjectInstance platform;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_ICZ, 0);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition((short) START_X, (short) START_Y)
                .build();
        sprite = fixture.sprite();
        sprite.setX((short) START_X);
        sprite.setY((short) START_Y);
        sprite.setPushing(false);
        sprite.setRolling(false);
        sprite.setJumping(false);
        sprite.setControlLocked(false);
        sprite.setObjectControlled(false);
        sprite.setMoveLockTimer(0);
        // Keep the harness focused on the post-spike platform path rather than
        // the earlier pre-existing hurt-block contact in this partial ICZ slice.
        sprite.setRingCount(1);
        sprite.setDirection(Direction.RIGHT);
        sprite.clearWallClingState();

        Camera camera = fixture.camera();
        camera.updatePosition(true);
        GameServices.level().postCameraObjectPlacementSync();
        GameServices.level().getObjectManager().reset(camera.getX());

        platform = findPathFollowPlatform();
        assertNotNull(platform, "Expected the ICZ path-follow platform near the repro start to be active");
    }

    @Test
    void icz1PushBlockFollowsPostSpikeSlopeAndStopsWithoutKillingSonic() {
        RouteResult result = runRoute();

        assertTrue(result.activated,
                "Expected holding right to activate the subtype 0x02 path-follow platform. " + result.detail);
        assertTrue(result.landedOnPlatform,
                "Expected Sonic to land on top of the moving platform before releasing input. " + result.detail);
        assertTrue(result.crossedWatchY,
                "Expected Sonic to ride the platform past top-left Y=" + WATCH_Y + ". " + result.detail);
        assertTrue(result.platformCrossedWatchY,
                "Expected the platform to continue downward past Y=" + WATCH_Y + ". " + result.detail);
        assertTrue(result.platformDestroyed,
                "Expected the platform to be deleted after the post-spike wall stop. " + result.detail);
        assertTrue(result.springSpawned,
                "Expected the deleted platform to reveal the upward spring. " + result.detail);
        assertFalse(result.sonicDied,
                "Sonic must not be crushed or killed while the platform follows the post-spike slope. " + result.detail);
    }

    private RouteResult runRoute() {
        return runRoute(JUMP_DELAY_AFTER_ACTIVATION, JUMP_HOLD_FRAMES);
    }

    private RouteResult runRoute(int jumpDelayAfterActivation, int jumpHoldFrames) {
        boolean activated = false;
        boolean jumpStarted = false;
        int jumpPressedFrames = 0;
        boolean landedOnPlatform = false;
        boolean crossedWatchY = false;
        boolean platformCrossedWatchY = false;
        boolean sonicDied = false;
        boolean platformDestroyed = false;
        boolean springSpawned = false;
        int landingFrame = -1;
        int landingRelX = 0;
        int activatedFrames = 0;
        StringBuilder trace = new StringBuilder();

        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            if (platform.getRoutineByteForTesting() == 0x08) {
                activated = true;
                activatedFrames++;
            }
            ObjectInstance riding = GameServices.level().getObjectManager().getRidingObject(sprite);
            if (jumpStarted && riding == platform && !sprite.getAir()) {
                landedOnPlatform = true;
                if (landingFrame < 0) {
                    landingFrame = frame;
                    landingRelX = sprite.getCentreX() - platform.getX();
                }
            }

            boolean releaseInput = landedOnPlatform;
            boolean delayReached = jumpDelayAfterActivation < 0
                    ? true
                    : activatedFrames >= jumpDelayAfterActivation;
            if (activated && delayReached && !jumpStarted && !sprite.getAir()) {
                jumpStarted = true;
            }
            boolean jump = jumpStarted && jumpPressedFrames < jumpHoldFrames;
            if (jump) {
                jumpPressedFrames++;
            }
            fixture.stepFrame(false, false, false, !releaseInput, jump);

            crossedWatchY |= sprite.getY() >= WATCH_Y;
            platformCrossedWatchY |= platform.getY() >= WATCH_Y;
            sonicDied |= sprite.getDead();
            platformDestroyed |= platform.isDestroyed();
            springSpawned |= findSpawnedSpring() != null;

            if (frame % 30 == 0 || sonicDied || platformDestroyed || springSpawned
                    || sprite.getY() >= WATCH_Y - 12) {
                appendTrace(trace, frame);
            }
            if (sonicDied || platformDestroyed) {
                break;
            }
        }

        return new RouteResult(activated, landedOnPlatform, crossedWatchY, platformCrossedWatchY,
                sonicDied, platformDestroyed, springSpawned,
                "Final " + describeState() + " landingFrame=" + landingFrame + " landingRelX=" + landingRelX
                        + "\nTrace:\n" + trace);
    }

    private IczPathFollowPlatformObjectInstance findPathFollowPlatform() {
        IczPathFollowPlatformObjectInstance best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ObjectInstance obj : GameServices.level().getObjectManager().getActiveObjects()) {
            if (obj instanceof IczPathFollowPlatformObjectInstance candidate
                    && candidate.getRoutineByteForTesting() == 0x06) {
                int distance = Math.abs(candidate.getX() - PLATFORM_X) + Math.abs(candidate.getY() - PLATFORM_Y);
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return bestDistance <= 128 ? best : null;
    }

    private Sonic3kSpringObjectInstance findSpawnedSpring() {
        for (ObjectInstance obj : GameServices.level().getObjectManager().getActiveObjects()) {
            if (obj instanceof Sonic3kSpringObjectInstance spring
                    && Math.abs(spring.getX() - 0x5D5A) <= 4
                    && Math.abs(spring.getY() - 0x027A) <= 4) {
                return spring;
            }
        }
        return null;
    }

    private void appendTrace(StringBuilder trace, int frame) {
        trace.append(String.format(
                "f=%04d sonic=(%d,%d) centre=(%d,%d) air=%s dead=%s ride=%s top=%02X lrb=%02X plat=(%d,%d) r=%02X xv=%d yv=%d%n",
                frame,
                sprite.getX(), sprite.getY(),
                sprite.getCentreX(), sprite.getCentreY(),
                sprite.getAir(), sprite.getDead(),
                GameServices.level().getObjectManager().getRidingObject(sprite) == platform,
                sprite.getTopSolidBit() & 0xFF,
                sprite.getLrbSolidBit() & 0xFF,
                platform.getX(), platform.getY(),
                platform.getRoutineByteForTesting(),
                platform.getXVelocityForTesting(),
                platform.getYVelocityForTesting()));
    }

    private String describeState() {
        return String.format(
                "frame=%d sonic=(%d,%d) centre=(%d,%d) air=%s dead=%s plat=(%d,%d) r=%02X xv=%d yv=%d",
                fixture.frameCount(),
                sprite.getX(), sprite.getY(),
                sprite.getCentreX(), sprite.getCentreY(),
                sprite.getAir(), sprite.getDead(),
                platform.getX(), platform.getY(),
                platform.getRoutineByteForTesting(),
                platform.getXVelocityForTesting(),
                platform.getYVelocityForTesting());
    }

    private record RouteResult(boolean activated,
                               boolean landedOnPlatform,
                               boolean crossedWatchY,
                               boolean platformCrossedWatchY,
                               boolean sonicDied,
                               boolean platformDestroyed,
                               boolean springSpawned,
                               String detail) {
    }
}
