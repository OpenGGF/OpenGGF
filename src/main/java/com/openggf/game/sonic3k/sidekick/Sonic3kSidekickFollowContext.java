package com.openggf.game.sonic3k.sidekick;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.AizGiantRideVineObjectInstance;
import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kCollapsingPlatformObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;

/**
 * S3K-only sidekick follow contexts that depend on live zone/object ordering.
 */
public final class Sonic3kSidekickFollowContext {
    private static final int ROM_FOLLOW_DELAY_FRAMES = 16;
    private static final int AIZ_HOLLOW_TREE_CONTEXT_RADIUS_X = 0x80;
    private static final int AIZ_HOLLOW_TREE_CONTEXT_RADIUS_Y = 0x100;
    private static final int AIZ_VINE_PUSH_BRIDGE_CONTEXT_RADIUS_X = 0x100;
    private static final int AIZ_VINE_PUSH_BRIDGE_CONTEXT_RADIUS_Y = 0x80;

    private Sonic3kSidekickFollowContext() {
    }

    public static boolean isObjectOrderFollowSteeringContext(
            AbstractPlayableSprite sidekick,
            AbstractPlayableSprite effectiveLeader) {
        return isAizHollowTreeFollowSteeringContext(sidekick, effectiveLeader)
                || isAizGiantRideVinePushBridgeContext(sidekick, effectiveLeader);
    }

    public static boolean isObjectOrderFollowNudgeContext(
            AbstractPlayableSprite sidekick,
            AbstractPlayableSprite effectiveLeader) {
        return isAizHollowTreeFollowSteeringContext(sidekick, effectiveLeader);
    }

    public static boolean isDoorSupportGraceFollowSteeringContext(
            AbstractPlayableSprite sidekick,
            ObjectInstance ridingObject) {
        if (sidekick == null || !sidekick.isOnObject() || sidekick.getAir()) {
            return false;
        }
        boolean doorSupport = isObjectId(ridingObject, Sonic3kObjectIds.DOOR)
                || sidekick.getLatchedSolidObjectId() == Sonic3kObjectIds.DOOR
                || isObjectId(sidekick.getLatchedSolidObjectInstance(), Sonic3kObjectIds.DOOR);
        if (!doorSupport) {
            return false;
        }
        // ROM loc_13DD0 only tests current Status_Push before falling into
        // FollowLeft/FollowRight (sonic3k.asm:26702-26724). Obj3C Door
        // keeps P2 marked as standing via SolidObjectFull (sonic3k.asm:
        // 66249-66258), so a stale engine push-grace bridge must not suppress
        // the follow nudge when Tails' current status byte has Status_Push clear.
        return true;
    }

    public static boolean usesRomVisibleCatchUpMarkerFrameCounterBridge(AbstractPlayableSprite sidekick) {
        SidekickCpuController controller = sidekick != null ? sidekick.getCpuController() : null;
        return controller != null && controller.hasLevelEventDormantMarkerReleasePending();
    }

    private static boolean isAizHollowTreeFollowSteeringContext(
            AbstractPlayableSprite sidekick,
            AbstractPlayableSprite effectiveLeader) {
        return hasAizHollowTreeContext(sidekick)
                && (isAizHollowTreeNear(sidekick, sidekick.getCentreX(), sidekick.getCentreY())
                    || isAizHollowTreeFollowNudgeContext(sidekick, effectiveLeader));
    }

    private static boolean isAizHollowTreeFollowNudgeContext(
            AbstractPlayableSprite sidekick,
            AbstractPlayableSprite effectiveLeader) {
        if (effectiveLeader == null || !hasAizHollowTreeContext(sidekick)) {
            return false;
        }
        ObjectInstance latched = effectiveLeader.getLatchedSolidObjectInstance();
        if (latched instanceof AizHollowTreeObjectInstance) {
            return true;
        }

        return isAizHollowTreeNear(sidekick, effectiveLeader.getCentreX(), effectiveLeader.getCentreY());
    }

    private static boolean hasAizHollowTreeContext(AbstractPlayableSprite sidekick) {
        return isAizHollowTreeNear(sidekick,
                sidekick != null ? sidekick.getCentreX() : 0,
                sidekick != null ? sidekick.getCentreY() : 0);
    }

    private static boolean isAizHollowTreeNear(AbstractPlayableSprite sidekick, int x, int y) {
        ObjectManager objectManager = objectManager(sidekick);
        if (objectManager == null) {
            return false;
        }
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (object == null || object.isDestroyed() || object.getSpawn() == null) {
                continue;
            }
            // S3K object ids are zone-set aliases: SKL slot $03 is MHZ's
            // twisted vine, while S3KL slot $03 is the AIZ hollow tree. The
            // bridge belongs to the live routine owner, not the numeric slot.
            if (!(object instanceof AizHollowTreeObjectInstance)) {
                continue;
            }
            if (Math.abs(object.getX() - x) <= AIZ_HOLLOW_TREE_CONTEXT_RADIUS_X
                    && Math.abs(object.getY() - y) <= AIZ_HOLLOW_TREE_CONTEXT_RADIUS_Y) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAizGiantRideVinePushBridgeContext(
            AbstractPlayableSprite sidekick,
            AbstractPlayableSprite effectiveLeader) {
        return isNearAizIntroOrderingObject(sidekick)
                && (hasAizVineBridgeLatchedObject(sidekick)
                    || hasAizVineBridgeLatchedObject(effectiveLeader)
                    || isAizVineBridgeObjectNear(sidekick, sidekick.getCentreX(), sidekick.getCentreY()));
    }

    private static boolean hasAizVineBridgeLatchedObject(AbstractPlayableSprite sprite) {
        if (sprite == null) {
            return false;
        }
        ObjectInstance latched = sprite.getLatchedSolidObjectInstance();
        return latched instanceof Sonic3kCollapsingPlatformObjectInstance
                || latched instanceof AizGiantRideVineObjectInstance;
    }

    private static boolean isAizVineBridgeObjectNear(AbstractPlayableSprite sidekick, int x, int y) {
        ObjectManager objectManager = objectManager(sidekick);
        if (objectManager == null) {
            return false;
        }
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (object == null || object.isDestroyed() || object.getSpawn() == null) {
                continue;
            }
            if (!(object instanceof Sonic3kCollapsingPlatformObjectInstance)
                    && !(object instanceof AizGiantRideVineObjectInstance)) {
                continue;
            }
            if (Math.abs(object.getX() - x) <= AIZ_VINE_PUSH_BRIDGE_CONTEXT_RADIUS_X
                    && Math.abs(object.getY() - y) <= AIZ_VINE_PUSH_BRIDGE_CONTEXT_RADIUS_Y) {
                return true;
            }
        }
        return false;
    }

    private static ObjectManager objectManager(AbstractPlayableSprite sidekick) {
        LevelManager levelManager = sidekick != null ? sidekick.currentLevelManager() : null;
        return levelManager != null ? levelManager.getObjectManager() : null;
    }

    private static boolean isNearAizIntroOrderingObject(AbstractPlayableSprite sidekick) {
        if (sidekick == null) {
            return false;
        }
        return isAizHollowTreeNear(sidekick, sidekick.getCentreX(), sidekick.getCentreY())
                || isAizVineBridgeObjectNear(sidekick, sidekick.getCentreX(), sidekick.getCentreY());
    }

    private static boolean isObjectId(ObjectInstance object, int objectId) {
        return object != null
                && object.getSpawn() != null
                && (object.getSpawn().objectId() & 0xFF) == objectId;
    }
}
