package secondstrike.routes;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import static com.openggf.sprites.playable.AbstractPlayableSprite.*;

/** Controller-only authoring recovery, based on the maintained Aiz1RouteContinuation. */
final class TestSecondStrikeRoutesLiveInput {
    private int lastX = -1;
    private int stationary;
    private boolean jump, charge, chargeJump, boost, waitingForFollowup, followupSeen;
    private boolean guardJump;
    private int priorFollowup = -1;

    Bk2FrameInput next(AbstractPlayableSprite player, int frame) {
        int x = player.getCentreX() & 0xffff;
        stationary = x == lastX ? stationary + 1 : 0;
        lastX = x;
        var objects = GameServices.level().getObjectManager().getActiveObjects();
        var boss = objects.stream().filter(AizMinibossInstance.class::isInstance)
                .map(AizMinibossInstance.class::cast).findFirst().orElse(null);
        boolean bossActive = boss != null && GameServices.gameState().getCurrentBossId() != 0;
        int mask = bossActive ? INPUT_RIGHT : traverse(player, x);
        if (!bossActive && Boolean.getBoolean("secondstrike.route.hard_enemy_author")) {
            mask = approachGuard(player, mask);
        }
        var cutscene = objects.stream().filter(AizMinibossCutsceneInstance.class::isInstance)
                .map(AizMinibossCutsceneInstance.class::cast).findFirst().orElse(null);
        if (cutscene != null && cutscene.getState().routine >= 4) {
            mask = steer(player, GameServices.camera().getX() + 203, 8);
        }
        if (bossActive) {
            String detail = boss.traceDebugDetails();
            int hp = Integer.parseInt(detail.split("hp=")[1].split(" ")[0]);
            int timer = readInt(boss, "difficultyFollowupTimer");
            if (timer > 0) followupSeen = true;
            if (followupSeen && priorFollowup > 0 && timer < 0 && hp > 0) waitingForFollowup = false;
            priorFollowup = timer;
            if (hp <= 0) {
                mask = 0;
            } else {
                if (hp <= 3 && !followupSeen) waitingForFollowup = true;
                if (waitingForFollowup) {
                    jump = false;
                    mask = steer(player, (GameServices.camera().getMinX() & 0xffff) + 16, 6);
                } else {
                    // Approach and jump through ordinary native hit eligibility.
                    if (!player.getAir()) jump = !jump;
                    mask = steer(player, boss.getX() - 16, 8) | (jump ? INPUT_JUMP : 0);
                }
            }
        } else if (boss != null && boss.traceDebugDetails().contains("hp=0")
                && GameServices.level().getApparentAct() == 0) mask = 0;
        return new Bk2FrameInput(frame, mask, (mask & INPUT_JUMP) != 0 ? 1 : 0, false, "authored-live-pad");
    }

    private int traverse(AbstractPlayableSprite player, int x) {
        if (player.getLatchedSolidObjectInstance() instanceof AizHollowTreeObjectInstance
                && player.isObjectMappingFrameControl()) jump = false;
        else if (player.isObjectControlled() && player.isObjectControlSuppressesMovement()) jump = !jump;
        else if (!player.getAir() && jump) jump = false;
        else if (!player.getAir() && (stationary > 8 || GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(Sonic3kSpringObjectInstance.class::isInstance).map(Sonic3kSpringObjectInstance.class::cast)
                .anyMatch(spring -> ((spring.getSpawn().subtype() >> 3) & 14) == 2
                        && (spring.getSpawn().renderFlags() & 1) != 0 && spring.getX() > x
                        && spring.getX() - x < 4 * spring.getSolidParams().halfWidth()
                        && Math.abs(spring.getY() - (player.getCentreY() & 0xffff)) < 64))) jump = true;
        int mask = INPUT_RIGHT | (jump ? INPUT_JUMP : 0);
        int angle = player.getAngle() & 255;
        if (!player.getAir() && angle >= 0xc0 && angle <= 0xe0 && player.getGSpeed() < 0) boost = true;
        var capability = player.getGameRules().playerCapability();
        if (!charge && !player.getAir() && angle == 0 && player.getGSpeed() >= 0 && player.getGSpeed() < 0x100
                && player.getDirection() == Direction.RIGHT && capability.spindashEnabled()
                && (boost || stationary > 20)) charge = true;
        if (charge) {
            jump = false;
            if (player.getSpindash() && (player.getSpindashCounter() & 0xffff)
                    >= (capability.spindashSpeedTable().length - 1) * 0x100) {
                charge = false; boost = false; stationary = 0; mask = INPUT_RIGHT;
            } else if (!player.getCrouching() && !player.getSpindash()) {
                chargeJump = false; mask = INPUT_DOWN;
            } else {
                chargeJump = !chargeJump; mask = INPUT_DOWN | (chargeJump ? INPUT_JUMP : 0);
            }
        }
        return mask;
    }

    private static int steer(AbstractPlayableSprite player, int target, int tolerance) {
        int dx = target - (player.getCentreX() & 0xffff);
        return dx > tolerance ? INPUT_RIGHT : dx < -tolerance ? INPUT_LEFT : 0;
    }

    /** Optional controller authoring only; the exported recipe contains pad rows, not observations. */
    private int approachGuard(AbstractPlayableSprite player, int ordinaryMask) {
        if (player.isObjectControlled() || player.isControlLocked()) return ordinaryMask;
        var guard = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals("secondstrike.enemies.GuardBadnik"))
                .filter(object -> !object.isDestroyed()
                        && Math.abs(object.getX() - player.getCentreX()) < 128
                        && Math.abs(object.getY() - player.getCentreY()) < 72)
                .min(java.util.Comparator.comparingInt(object -> Math.abs(object.getX() - player.getCentreX())))
                .orElse(null);
        if (guard == null) {
            guardJump = false;
            return ordinaryMask;
        }
        // Stay above a committed charge, then approach its recovery with real jump edges.
        // Health, movement, collision and rewards are never written by this author.
        if (!player.getAir()) guardJump = !guardJump;
        charge = false;
        return steer(player, guard.getX(), 8) | (guardJump ? INPUT_JUMP : 0);
    }

    private static int readInt(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(owner);
        } catch (ReflectiveOperationException missing) { return -1; }
    }
}
