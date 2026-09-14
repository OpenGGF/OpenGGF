package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.route.RouteSteering;

import java.util.Comparator;
import java.util.stream.Stream;

import static com.openggf.sprites.playable.AbstractPlayableSprite.*;

/**
 * Test-only ordinary-pad continuation for the AIZ1 axis routes. The recording
 * remains authoritative until it ends or requests an unavailable spindash.
 * Thereafter targets come from live objects and player capabilities, never
 * comparison rows, viewport/donor identities, or measured frame coordinates.
 */
final class Aiz1RouteContinuation {
    private enum Ascent {
        NONE, LOWER_SPRING, UPPER_SPRING, PLATFORM, JUMP_LEFT, LAND_LEFT,
        APPROACH_RIGHT, LAND_RIGHT, RELEASE_JUMP, JUMP_RIGHT, COMPLETE
    }

    private Ascent ascent = Ascent.NONE;
    private Sonic3kSpringObjectInstance spring;
    private FloatingPlatformObjectInstance platform;
    private boolean ascentJump;
    private boolean jumping;
    private boolean charging;
    private boolean chargeJump;
    private boolean needBoost;
    private int stationaryFrames;
    private int lastX = -1;
    private int previousYSpeed;
    private int continuationFrames;

    int next(AbstractPlayableSprite player, Aiz1IntroProgram program) {
        int x = player.getCentreX() & 0xffff;
        int mask;
        if (!program.exhausted() && ascent == Ascent.NONE) {
            mask = program.next(GameServices.camera().isLevelStarted());
            if (!player.getGameRules().playerCapability().spindashEnabled()
                    && !player.getAir() && (mask & (INPUT_DOWN | INPUT_JUMP)) == (INPUT_DOWN | INPUT_JUMP)) {
                spring = springs().filter(o -> upward(o) && o.getX() > x && o.getX() - x < 512
                                && Math.abs(o.getY() - (player.getCentreY() & 0xffff)) < 96)
                        .min(Comparator.comparingInt(o -> o.getX() - x)).orElse(null);
                if (spring != null) ascent = Ascent.LOWER_SPRING;
            }
        } else {
            continuationFrames++;
            stationaryFrames = x == lastX ? stationaryFrames + 1 : 0;
            mask = traverseRight(player, x);
        }
        mask = ascend(player, x, mask);
        if (program.exhausted() || ascent == Ascent.COMPLETE) {
            var boss = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(AizMinibossCutsceneInstance.class::isInstance)
                    .map(AizMinibossCutsceneInstance.class::cast).findFirst().orElse(null);
            // loc_6852C locks the arena before routine 4. Let the unkillable
            // FireBreath cutscene run; walking into its right-hand body is fatal
            // when ringless. Its ROM arena remains 320px even on wider displays.
            if (boss != null && boss.getState().routine >= 4) {
                mask = RouteSteering.steerMask(player, GameServices.camera().getX() + 160, 8);
            }
        }
        lastX = x;
        previousYSpeed = player.getYSpeed();
        return mask;
    }

    private int traverseRight(AbstractPlayableSprite player, int x) {
        if (player.getLatchedSolidObjectInstance() instanceof AizHollowTreeObjectInstance
                && player.isObjectMappingFrameControl()) {
            jumping = false;
        } else if (player.isObjectControlled() && player.isObjectControlSuppressesMovement()) {
            // Vine loc_22136 needs a fresh A/B/C edge. Hollow-tree ownership
            // deliberately leaves normal movement active; jumping would eject us.
            jumping = !jumping;
        } else if (!player.getAir() && jumping) {
            jumping = false;
        } else if (!player.getAir() && (stationaryFrames > 8 || springs().anyMatch(o ->
                ((o.getSpawn().subtype() >> 3) & 14) == 2 && (o.getSpawn().renderFlags() & 1) != 0
                        && o.getX() > x && o.getX() - x < 4 * o.getSolidParams().halfWidth()
                        && Math.abs(o.getY() - (player.getCentreY() & 0xffff)) < 64))) {
            jumping = true;
        }
        int mask = INPUT_RIGHT | (jumping ? INPUT_JUMP : 0);
        var capability = player.getGameRules().playerCapability();
        int angle = player.getAngle() & 0xff;
        if (!player.getAir() && angle >= 0xc0 && angle <= 0xe0 && player.getGSpeed() < 0) {
            needBoost = true;
        }
        // Charge only after returning to flat terrain and turning right. Charging
        // on the rollback slope would launch left or immediately uncrouch.
        if (!charging && needBoost && !player.getAir() && angle == 0
                && player.getGSpeed() >= 0 && player.getGSpeed() < 0x100
                && capability.spindashEnabled() && player.getDirection() == Direction.RIGHT) {
            charging = true;
        }
        if (charging) {
            jumping = false;
            if (player.getSpindash() && (player.getSpindashCounter() & 0xffff)
                    >= (capability.spindashSpeedTable().length - 1) * 0x100) {
                charging = false;
                needBoost = false;
                stationaryFrames = 0;
                mask = INPUT_RIGHT;
            } else if (!player.getCrouching() && !player.getSpindash()) {
                chargeJump = false;
                mask = INPUT_DOWN;
            } else {
                chargeJump = !chargeJump;
                mask = INPUT_DOWN | (chargeJump ? INPUT_JUMP : 0);
            }
        }
        return mask;
    }

    private int ascend(AbstractPlayableSprite player, int x, int mask) {
        if (ascent == Ascent.LOWER_SPRING || ascent == Ascent.UPPER_SPRING) {
            if (springLaunch(player, x)) {
                if (ascent == Ascent.LOWER_SPRING) {
                    int oldX = spring.getX();
                    int oldY = spring.getY();
                    spring = springs().filter(o -> upward(o) && o.getY() < oldY
                                    && oldY - o.getY() < 240 && Math.abs(o.getX() - oldX) < 192)
                            .max(Comparator.comparingInt(Sonic3kSpringObjectInstance::getY))
                            .orElseThrow(() -> new AssertionError("no live upper spring for AIZ ascent"));
                    ascent = Ascent.UPPER_SPRING;
                } else {
                    platform = GameServices.level().getObjectManager().getActiveObjects().stream()
                            .filter(FloatingPlatformObjectInstance.class::isInstance)
                            .map(FloatingPlatformObjectInstance.class::cast)
                            .filter(o -> o.getX() < x && x - o.getX() < 512)
                            .max(Comparator.comparingInt(FloatingPlatformObjectInstance::getX))
                            .orElseThrow(() -> new AssertionError("no live platform for AIZ ascent"));
                    ascent = Ascent.PLATFORM;
                }
            }
            // Land on the upper spring's left half: centring it leaves too little
            // horizontal reach for the moving platform. Both targets are ROM objects.
            int target = spring.getX() - (ascent == Ascent.UPPER_SPRING
                    ? spring.getSolidParams().halfWidth() / 2 : 0);
            mask = RouteSteering.steerMask(player, target, 2);
            if (!player.getAir()) ascentJump = !ascentJump;
            if (ascentJump) mask |= INPUT_JUMP;
        } else if (ascent == Ascent.PLATFORM) {
            mask = RouteSteering.steerMask(player, platform.getX(), 4);
            if (!player.getAir()) {
                ascent = Ascent.JUMP_LEFT;
                jumping = false;
            }
        }
        switch (ascent) {
            case JUMP_LEFT -> {
                mask = INPUT_LEFT | INPUT_JUMP;
                if (player.getAir()) ascent = Ascent.LAND_LEFT;
            }
            case LAND_LEFT -> {
                mask = INPUT_LEFT | INPUT_JUMP;
                if (!player.getAir()) {
                    ascent = Ascent.APPROACH_RIGHT;
                    jumping = false;
                }
            }
            case APPROACH_RIGHT -> {
                mask = INPUT_RIGHT;
                if (stationaryFrames > 8 || x > platform.getX() - 4 * platform.getSolidParams().halfWidth()) {
                    ascentJump = !ascentJump;
                    mask |= ascentJump ? INPUT_JUMP : 0;
                }
                if (player.getAir()) ascent = Ascent.LAND_RIGHT;
            }
            case LAND_RIGHT -> {
                mask = RouteSteering.steerMask(player, platform.getX(), 4) | INPUT_JUMP;
                // This can be terrain or the ledge's obstruction. A sticky last
                // solid-owner reference is not proof of landing on the platform.
                if (!player.getAir()) ascent = Ascent.RELEASE_JUMP;
            }
            case RELEASE_JUMP -> {
                mask = INPUT_RIGHT;
                ascent = Ascent.JUMP_RIGHT;
            }
            case JUMP_RIGHT -> {
                mask = INPUT_RIGHT | INPUT_JUMP;
                if (player.getAir()) ascent = Ascent.COMPLETE;
            }
            default -> { }
        }
        return mask;
    }

    private boolean springLaunch(AbstractPlayableSprite player, int x) {
        int aboveSpring = spring.getY() - (player.getCentreY() & 0xffff);
        return player.getAir() && !player.isJumping() && previousYSpeed >= 0 && player.getYSpeed() < 0
                && Math.abs(x - spring.getX()) < 48 && aboveSpring > 0 && aboveSpring < 48;
    }

    private static boolean upward(Sonic3kSpringObjectInstance spring) {
        return ((spring.getSpawn().subtype() >> 3) & 14) == 0;
    }

    private static Stream<Sonic3kSpringObjectInstance> springs() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(Sonic3kSpringObjectInstance.class::isInstance)
                .map(Sonic3kSpringObjectInstance.class::cast);
    }

    boolean used() { return ascent != Ascent.NONE || continuationFrames > 0; }

    String diagnostic() {
        return "ascent=" + ascent + " continuationFrames=" + continuationFrames + " charging=" + charging;
    }
}
