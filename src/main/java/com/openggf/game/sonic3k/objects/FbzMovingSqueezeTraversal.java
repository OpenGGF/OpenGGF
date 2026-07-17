package com.openggf.game.sonic3k.objects;

import com.openggf.game.GroundMode;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;
import java.util.Optional;

/**
 * Production-owned geometry for FBZ2's Obj28/elevator-car squeeze.
 *
 * <p>S3K executes the player slot before dynamic objects. The calculation
 * therefore advances rolling movement first and then evaluates the retained
 * SST slot order: Obj28 may see the prior car surface before the later car
 * moves/reseats its rider, while the opposite slot order sees the moved car.
 */
public final class FbzMovingSqueezeTraversal {
    public static final int NATIVE_RELEASE_SPEED = 0x0800;
    private static final int PROJECTION_FRAME_LIMIT = 0x200;

    private FbzMovingSqueezeTraversal() {
    }

    public record Episode(Sonic3kInvisibleBlockObjectInstance block,
                          FbzElevatorObjectInstance.Car car) {
        public Episode {
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(car, "car");
        }

        public boolean carBeforeBlock() {
            return car.getSlotIndex() < block.getSlotIndex();
        }
    }

    public record Projection(boolean safe, boolean escapeReached,
                             boolean carAcquired, boolean overlapTraversed,
                             boolean crushOverlapObserved,
                             int dangerEdge, int minimumGap,
                             int entrySpeed, int exitX) {
        public boolean clears() {
            return safe && escapeReached && carAcquired && overlapTraversed;
        }
    }

    private record ProjectedGap(boolean safe, int dangerEdge, int minimumGap) {
    }

    /** Finds the nearest exact live terrain-launch pair, failing closed on ties. */
    public static Optional<Episode> findEpisode(ObjectManager objects,
                                                AbstractPlayableSprite player) {
        if (objects == null || !hasLaunchFloorAuthority(player)
                || player.getGSpeed() <= 0) {
            return Optional.empty();
        }
        int playerX = player.getCentreX() & 0xFFFF;
        Episode nearest = null;
        int nearestDanger = Integer.MAX_VALUE;
        boolean unresolvedTie = false;
        for (Sonic3kInvisibleBlockObjectInstance candidate
                : objects.activeObjectsOfType(Sonic3kInvisibleBlockObjectInstance.class)) {
            if (!isLiveNormalInvisibleSolid(candidate, player)) continue;
            var params = candidate.getSolidParams();
            int anchorX = candidate.getX() + params.offsetX();
            int blockLeft = anchorX - params.halfWidth();
            if (playerX >= blockLeft) continue;
            for (FbzElevatorObjectInstance.Car car
                    : objects.activeObjectsOfType(FbzElevatorObjectInstance.Car.class)) {
                Episode episode = new Episode(candidate, car);
                if (!isActive(episode, player)) continue;
                Projection nativeFloor = project(episode, player, NATIVE_RELEASE_SPEED);
                Projection current = project(episode, player, player.getGSpeed());
                boolean genuineSqueeze = nativeFloor.dangerEdge() >= 0
                        || current.crushOverlapObserved();
                if (!nativeFloor.clears() || !genuineSqueeze) continue;
                int danger = nativeFloor.dangerEdge() >= 0
                        ? nativeFloor.dangerEdge() : current.dangerEdge();
                if (danger <= playerX) continue;
                if (danger < nearestDanger) {
                    nearest = episode;
                    nearestDanger = danger;
                    unresolvedTie = false;
                } else if (danger == nearestDanger && nearest != null
                        && (nearest.block() != candidate || nearest.car() != car)) {
                    unresolvedTie = true;
                }
            }
        }
        return nearest == null || unresolvedTie ? Optional.empty() : Optional.of(nearest);
    }

    public static boolean isActive(Episode episode, AbstractPlayableSprite player) {
        return episode != null && player != null
                && isLiveUpwardCar(episode.car())
                && isLiveNormalInvisibleSolid(episode.block(), player)
                && pairGeometryValid(episode);
    }

    /**
     * True only before rolling Sonic already overlaps the Obj28 underside.
     * This is the geometry-derived launch frontier used by native and donation
     * controllers; it contains no route coordinates or frame numbers.
     */
    public static boolean beforeLaunchFrontier(Episode episode,
                                               AbstractPlayableSprite player) {
        if (!isActive(episode, player) || !hasLaunchFloorAuthority(player)) {
            return false;
        }
        var params = episode.block().getSolidParams();
        int blockLeft = episode.block().getX() + params.offsetX() - params.halfWidth();
        if ((player.getCentreX() & 0xFFFF) >= blockLeft) return false;
        int gap = currentGap(episode, player);
        int required = (player.getStandYRadius() & 0xFFFF)
                + (player.getRollYRadius() & 0xFFFF) - 4;
        return gap >= required;
    }

    public static int currentGap(Episode episode, AbstractPlayableSprite player) {
        var blockParams = episode.block().getSolidParams();
        int blockBottom = episode.block().getY() + blockParams.offsetY()
                + blockParams.airHalfHeight();
        int feetY = (player.getCentreY() & 0xFFFF) + player.getYRadius();
        return feetY - blockBottom;
    }

    /** Projects a rolling release applied after the current object/event frame. */
    public static Projection project(Episode episode,
                                     AbstractPlayableSprite player,
                                     int initialSpeed) {
        if (!isActive(episode, player) || initialSpeed <= 0
                || episode.block().getSlotIndex() == episode.car().getSlotIndex()) {
            return new Projection(false, false, false, false, false, -1,
                    Integer.MAX_VALUE, initialSpeed, -1);
        }

        Sonic3kInvisibleBlockObjectInstance block = episode.block();
        FbzElevatorObjectInstance.Car car = episode.car();
        boolean carBeforeBlock = episode.carBeforeBlock();
        var blockParams = block.getSolidParams();
        int blockAnchorX = block.getX() + blockParams.offsetX();
        int blockLeft = blockAnchorX - blockParams.halfWidth();
        int blockRight = blockAnchorX + blockParams.halfWidth();
        int blockBottom = block.getY() + blockParams.offsetY()
                + blockParams.airHalfHeight();
        var carParams = car.getSolidParams();
        int carAnchorX = car.getCentreX() + carParams.offsetX();
        int carLeft = carAnchorX - carParams.halfWidth();
        int carRightExclusive = carAnchorX + carParams.halfWidth();
        byte[] slope = car.getSlopeData();
        int playerX = player.getCentreX() & 0xFFFF;
        boolean startsOnCar = player.isOnObject()
                && player.getLatchedSolidObjectInstance() == car;
        boolean startsOnTerrain = hasLaunchFloorAuthority(player)
                && playerX < blockLeft;
        if (!startsOnCar && !startsOnTerrain) {
            return new Projection(false, false, false, false, false, -1,
                    Integer.MAX_VALUE, initialSpeed, -1);
        }
        int standingRequiredGap = 2 * (player.getStandYRadius() & 0xFFFF) - 4;
        int rollingRequiredGap = (player.getStandYRadius() & 0xFFFF)
                + (player.getRollYRadius() & 0xFFFF) - 4;
        int carCatchDepth = carParams.groundHalfHeight()
                + (player.getStandYRadius() & 0xFFFF);
        long xFixed = ((long) playerX << 16)
                | (player.getXSubpixelRaw() & 0xFFFFL);
        int feetY = (player.getCentreY() & 0xFFFF) + player.getYRadius();
        int speed = initialSpeed;
        int carY = car.getCentreY();
        int timer = car.travelTimer();
        int dangerEdge = -1;
        int minimumGap = Integer.MAX_VALUE;
        boolean carAcquired = startsOnCar;
        boolean overlapTraversed = false;

        for (int frame = 0; frame < PROJECTION_FRAME_LIMIT; frame++) {
            int preMoveX = (int) (xFixed >> 16);
            int naturalDecel = (player.getRunAccel() & 0xFFFF) / 2;
            speed = Math.max(0, speed - naturalDecel);
            if (player.getGameRules().playerMovement().rollStopsBelowMinimumSpeed()
                    && speed < (player.getMinRollSpeed() & 0xFFFF)) {
                return new Projection(false, false, carAcquired, overlapTraversed,
                        false, dangerEdge,
                        minimumGap, initialSpeed, -1);
            }
            int movementSpeed = Math.min(speed, 0x1000);
            xFixed += (long) movementSpeed << 8;
            int postMoveX = (int) (xFixed >> 16);

            if (carBeforeBlock) {
                timer--;
                if (timer < 0) return failed(carAcquired, overlapTraversed,
                        dangerEdge, minimumGap, initialSpeed);
                carY += car.yVelocity();
                SupportProjection support = projectSupport(postMoveX, carLeft,
                        carRightExclusive, carY, feetY, slope, carAnchorX,
                        carParams.halfWidth(), carCatchDepth, carAcquired);
                if (!support.valid()) return failed(carAcquired, overlapTraversed,
                        dangerEdge, minimumGap, initialSpeed);
                feetY = support.feetY();
                carAcquired = support.acquired();
                if (carAcquired && postMoveX >= blockLeft && postMoveX <= blockRight) {
                    overlapTraversed = true;
                }
                if (carAcquired && !support.exited()) {
                    ProjectedGap gap = scanProjectedGap(preMoveX, postMoveX,
                            blockLeft, blockRight, blockBottom,
                            carLeft, carRightExclusive, carY, feetY,
                            slope, carAnchorX, carParams.halfWidth(), true,
                            standingRequiredGap, rollingRequiredGap);
                    if (!gap.safe()) {
                        return crushed(true, overlapTraversed,
                                gap.dangerEdge(), gap.minimumGap(), initialSpeed);
                    }
                    if (dangerEdge < 0) dangerEdge = gap.dangerEdge();
                    minimumGap = Math.min(minimumGap, gap.minimumGap());
                }
                if (carAcquired && overlapTraversed && support.exited()) {
                    return new Projection(true, true, true, true, false,
                            dangerEdge, minimumGap, initialSpeed, postMoveX);
                }
            } else {
                ProjectedGap gap = scanProjectedGap(preMoveX, postMoveX,
                        blockLeft, blockRight, blockBottom,
                        carLeft, carRightExclusive, carY, feetY,
                        slope, carAnchorX, carParams.halfWidth(), false,
                        standingRequiredGap, rollingRequiredGap);
                if (!gap.safe()) {
                    return crushed(carAcquired, overlapTraversed,
                            gap.dangerEdge(), gap.minimumGap(), initialSpeed);
                }
                if (dangerEdge < 0) dangerEdge = gap.dangerEdge();
                minimumGap = Math.min(minimumGap, gap.minimumGap());
                timer--;
                if (timer < 0) return failed(carAcquired, overlapTraversed,
                        dangerEdge, minimumGap, initialSpeed);
                carY += car.yVelocity();
                SupportProjection support = projectSupport(postMoveX, carLeft,
                        carRightExclusive, carY, feetY, slope, carAnchorX,
                        carParams.halfWidth(), carCatchDepth, carAcquired);
                if (!support.valid()) return failed(carAcquired, overlapTraversed,
                        dangerEdge, minimumGap, initialSpeed);
                feetY = support.feetY();
                carAcquired = support.acquired();
                if (carAcquired && postMoveX >= blockLeft && postMoveX <= blockRight) {
                    overlapTraversed = true;
                }
                if (carAcquired && overlapTraversed && support.exited()) {
                    return new Projection(true, true, true, true, false,
                            dangerEdge, minimumGap, initialSpeed, postMoveX);
                }
            }

            if (!carAcquired && postMoveX >= carRightExclusive) {
                return failed(false, overlapTraversed,
                        dangerEdge, minimumGap, initialSpeed);
            }
            if (carAcquired && overlapTraversed && postMoveX > blockRight) {
                return new Projection(true, true, true, true, false, dangerEdge,
                        minimumGap, initialSpeed, postMoveX);
            }
        }
        return failed(carAcquired, overlapTraversed,
                dangerEdge, minimumGap, initialSpeed);
    }

    /**
     * ROM launch-floor authority for the FBZ2 squeeze. The native complete run
     * charges on Obj_Button's flat standing contact, then acquires the rising
     * elevator car after release. No other object support is terrain-equivalent.
     */
    public static boolean hasLaunchFloorAuthority(AbstractPlayableSprite player) {
        if (player == null || player.getAir()
                || player.getGroundMode() != GroundMode.GROUND
                || (player.getAngle() & 0xFF) != 0
                || player.isSliding() || player.isObjectControlled()
                || player.isControlLocked() || player.getMoveLockTimer() != 0
                || player.getPushing()) {
            return false;
        }
        if (!player.isOnObject()) return true;
        if (player.getLatchedSolidObjectInstance() == null
                || player.getLatchedSolidObjectInstance().getClass()
                != Sonic3kButtonObjectInstance.class) {
            return false;
        }
        Sonic3kButtonObjectInstance button =
                (Sonic3kButtonObjectInstance) player.getLatchedSolidObjectInstance();
        if (button.isDestroyed() || button.isSkipSolidContactThisFrame()
                || !button.isSolidFor(player)) {
            return false;
        }
        var params = button.getSolidParams();
        int playerX = player.getCentreX() & 0xFFFF;
        int anchorX = button.getX() + params.offsetX();
        if (playerX < anchorX - params.halfWidth()
                || playerX > anchorX + params.halfWidth()) {
            return false;
        }
        int surfaceY = button.getY() + params.offsetY()
                - params.groundHalfHeight();
        int liveFeetY = (player.getCentreY() & 0xFFFF)
                + (player.getYRadius() & 0xFFFF);
        // New landing (loc_1E45A) probes with +4, then seats with +3:
        // y_pos = surface - y_radius - 1. A retained rider instead runs
        // MvSonicOnPtfm (loc_1E1CA), which seats exactly on the surface.
        return liveFeetY == surfaceY - 1 || liveFeetY == surfaceY;
    }

    private static Projection failed(boolean carAcquired, boolean overlapTraversed,
                                     int dangerEdge, int minimumGap,
                                     int entrySpeed) {
        return new Projection(false, false, carAcquired, overlapTraversed,
                false, dangerEdge,
                minimumGap, entrySpeed, -1);
    }

    private static Projection crushed(boolean carAcquired, boolean overlapTraversed,
                                      int dangerEdge, int minimumGap,
                                      int entrySpeed) {
        return new Projection(false, false, carAcquired, overlapTraversed,
                true, dangerEdge,
                minimumGap, entrySpeed, -1);
    }

    public static boolean isLiveNormalInvisibleSolid(
            Sonic3kInvisibleBlockObjectInstance block,
            AbstractPlayableSprite player) {
        return block != null
                && block.getClass() == Sonic3kInvisibleBlockObjectInstance.class
                && !block.isDestroyed()
                && !block.isSkipSolidContactThisFrame()
                && block.isSolidFor(player);
    }

    public static boolean isLiveUpwardCar(FbzElevatorObjectInstance.Car car) {
        return car != null && !car.isDestroyed()
                && car.yVelocity() < 0 && car.travelTimer() > 0;
    }

    static boolean spansOverlap(int leftA, int rightA, int leftB, int rightB) {
        return leftA <= rightB && leftB <= rightA;
    }

    static boolean canAcquireCarSurface(int feetY, int surfaceY, int catchDepth) {
        int delta = surfaceY - feetY;
        return delta >= 0 && delta <= catchDepth;
    }

    private static boolean pairGeometryValid(Episode episode) {
        var blockParams = episode.block().getSolidParams();
        int blockAnchorX = episode.block().getX() + blockParams.offsetX();
        int blockLeft = blockAnchorX - blockParams.halfWidth();
        int blockRight = blockAnchorX + blockParams.halfWidth();
        int blockBottom = episode.block().getY() + blockParams.offsetY()
                + blockParams.airHalfHeight();
        var carParams = episode.car().getSolidParams();
        int carAnchorX = episode.car().getCentreX() + carParams.offsetX();
        int carLeft = carAnchorX - carParams.halfWidth();
        int carRight = carAnchorX + carParams.halfWidth() - 1;
        if (!spansOverlap(blockLeft, blockRight, carLeft, carRight)) return false;
        int maximumSlope = 0;
        for (byte sample : episode.car().getSlopeData()) {
            maximumSlope = Math.max(maximumSlope, sample & 0xFF);
        }
        return episode.car().getCentreY() - maximumSlope > blockBottom;
    }

    private static SupportProjection projectSupport(
            int x, int carLeft, int carRightExclusive,
            int carY, int feetY, byte[] slope, int carAnchorX,
            int carHalfWidth, int catchDepth, boolean acquired) {
        if (x < carLeft || x >= carRightExclusive) {
            return new SupportProjection(true, acquired, acquired, feetY);
        }
        Integer sample = carSlopeSample(slope, x, carAnchorX, carHalfWidth);
        if (sample == null) return new SupportProjection(false, acquired, false, feetY);
        int surfaceY = carY - sample;
        if (acquired) return new SupportProjection(true, true, false, surfaceY);
        if (surfaceY < feetY) {
            return new SupportProjection(false, false, false, feetY);
        }
        if (canAcquireCarSurface(feetY, surfaceY, catchDepth)) {
            return new SupportProjection(true, true, false, surfaceY);
        }
        return new SupportProjection(true, false, false, feetY);
    }

    private record SupportProjection(boolean valid, boolean acquired,
                                     boolean exited, int feetY) {
    }

    private static ProjectedGap scanProjectedGap(
            int preMoveX, int postMoveX,
            int blockLeft, int blockRight, int blockBottom,
            int carLeft, int carRightExclusive,
            int carY, int fixedFeetY, byte[] slope, int carAnchorX,
            int carHalfWidth, boolean resampleCarSurface,
            int standingRequiredGap, int rollingRequiredGap) {
        int dangerEdge = -1;
        int minimumGap = Integer.MAX_VALUE;
        int fromX = Math.min(preMoveX, postMoveX);
        int toX = Math.max(preMoveX, postMoveX);
        for (int x = fromX; x <= toX; x++) {
            if (x < blockLeft || x > blockRight) continue;
            int feetY = fixedFeetY;
            if (resampleCarSurface) {
                if (x < carLeft || x >= carRightExclusive) continue;
                Integer sample = carSlopeSample(slope, x, carAnchorX, carHalfWidth);
                if (sample == null) continue;
                feetY = carY - sample;
            }
            int gap = feetY - blockBottom;
            minimumGap = Math.min(minimumGap, gap);
            if (gap < standingRequiredGap && dangerEdge < 0) dangerEdge = x;
            if (gap < rollingRequiredGap && x == postMoveX) {
                return new ProjectedGap(false, dangerEdge, minimumGap);
            }
        }
        return new ProjectedGap(true, dangerEdge, minimumGap);
    }

    private static Integer carSlopeSample(byte[] slope, int x,
                                          int carAnchorX, int carHalfWidth) {
        int relX = x - carAnchorX + carHalfWidth;
        if (relX < 0 || relX >= carHalfWidth * 2) return null;
        int index = relX >>> 1;
        if (index < 0 || index >= slope.length) return null;
        return slope[index] & 0xFF;
    }
}
