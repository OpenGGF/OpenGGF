package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.Optional;

/** Test-controller interception of the ROM car's lower catch band using ordinary inputs. */
final class FbzSqueezeRunUp {
    private FbzSqueezeRunUp() { }

    static int blockLeft(Sonic3kInvisibleBlockObjectInstance block) {
        return block.getX() + block.getSolidParams().offsetX() - block.getSolidParams().halfWidth();
    }
    // Authored local recipe: the flat floor west of this block accommodates
    // this run-up. These are controller waypoints, never production physics.
    static int startX(Sonic3kInvisibleBlockObjectInstance block) { return blockLeft(block) - 0x90; }
    static int rollX(Sonic3kInvisibleBlockObjectInstance block) { return blockLeft(block) - 0x20; }

    static boolean isOrdinaryButtonEgress(AbstractPlayableSprite player) {
        if (!player.getAir() || player.getYSpeed() < 0 || player.getDead() || player.isHurt()
                || player.isObjectControlled() || player.isControlLocked()
                || !(player.getLatchedSolidObjectInstance() instanceof Sonic3kButtonObjectInstance button)
                || button.isDestroyed()) return false;
        int x = player.getCentreX() & 0xffff;
        int feet = (player.getCentreY() & 0xffff) + player.getYRadius();
        int surface = button.getY() - button.getSolidParams().groundHalfHeight();
        // Walking off the raised button briefly falls back to the floor.
        // That is ordinary run-up control, bounded to the button's immediate
        // edge and standing-radius drop; it is not a captured-car release.
        return Math.abs(x - button.getX()) <= button.getSolidParams().halfWidth() + 0x20
                && feet >= surface - 1 && feet <= surface + player.getStandYRadius();
    }

    static Optional<FbzElevatorObjectInstance.Car> intercept(ObjectManager objects,
            Sonic3kInvisibleBlockObjectInstance block, AbstractPlayableSprite player) {
        if (!FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player)
                || player.getRolling() || Math.abs(player.getGSpeed()) > 0x20) return Optional.empty();
        FbzElevatorObjectInstance.Car selected = null;
        for (var car : objects.activeObjectsOfType(FbzElevatorObjectInstance.Car.class)) {
            if (!FbzMovingSqueezeTraversal.isActive(new FbzMovingSqueezeTraversal.Episode(block, car), player)) continue;
            int carLeft = car.getCentreX() - car.getSolidParams().halfWidth();
            long x = ((long)(player.getCentreX() & 0xffff) << 16) | (player.getXSubpixelRaw() & 0xffffL);
            int speed = player.getGSpeed();
            int frames = 0;
            boolean rolling = false;
            // Flat Sonic_MoveRight acceleration followed by SonicKnux_Roll:
            // DOWN loses ordinary friction on its entry frame; subsequent
            // Sonic_RollSpeed ticks lose half acceleration. Stop at first car
            // overlap, before the solid routine can change posture or speed.
            while ((x >> 16) < carLeft && frames < 256) {
                if ((x >> 16) < rollX(block)) speed = Math.min(player.getMax(), speed + player.getRunAccel());
                else if (!rolling) { speed -= player.getFriction(); rolling = true; }
                else speed -= player.getRunAccel() / 2;
                if (speed <= 0) break;
                x += (long)speed << 8;
                frames++;
            }
            if ((x >> 16) < carLeft || !rolling || car.travelTimer() <= frames) continue;
            int sampleIndex = ((int)(x >> 16) - carLeft) >>> 1;
            byte[] slope = car.getSlopeData();
            if (sampleIndex >= slope.length) continue;
            int surface = car.getCentreY() + frames * car.yVelocity() - (slope[sampleIndex] & 0xff);
            int feet = (player.getCentreY() & 0xffff) + player.getYRadius();
            int catchDepth = car.getSolidParams().groundHalfHeight() + player.getStandYRadius();
            int delta = surface - feet;
            // Aim in the lower half of SolidObjectTopSloped2's catch band:
            // the car is still below the floor on entry, leaving the upper
            // half as clearance while it rises through the underpass.
            if (delta < (catchDepth + 1) / 2 || delta > catchDepth) continue;
            if (selected != null) return Optional.empty();
            selected = car;
        }
        return Optional.ofNullable(selected);
    }
}
