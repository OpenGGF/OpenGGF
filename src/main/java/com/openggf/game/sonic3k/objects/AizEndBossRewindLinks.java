package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

final class AizEndBossRewindLinks {
    private AizEndBossRewindLinks() {
    }

    static AizEndBossInstance nearestBoss(RewindRecreateContext ctx) {
        return nearest(ctx, AizEndBossInstance.class);
    }

    static AizEndBossArmChild nearestArm(RewindRecreateContext ctx) {
        return nearest(ctx, AizEndBossArmChild.class);
    }

    static AizEndBossArmChild nearestArm(RewindRecreateContext ctx, int subtype) {
        if (ctx.objectServices() == null || ctx.objectServices().objectManager() == null) {
            return null;
        }
        for (ObjectInstance object : ctx.objectServices().objectManager().getActiveObjects()) {
            if (object instanceof AizEndBossArmChild arm
                    && !arm.isDestroyed()
                    && arm.rewindSubtype() == subtype) {
                return arm;
            }
        }
        return nearestArm(ctx);
    }

    static AizEndBossPropellerChild nearestPropeller(RewindRecreateContext ctx) {
        return nearest(ctx, AizEndBossPropellerChild.class);
    }

    static int capturedPropellerSubtype(
            RewindRecreateContext ctx,
            AizEndBossInstance boss,
            AizEndBossArmChild arm) {
        if (boss == null || arm == null) {
            return 0;
        }
        AizEndBossPropellerChild probe = new AizEndBossPropellerChild(boss, arm, 0);
        seedCapturedScalars(probe, ctx);
        return probe.rewindSubtype();
    }

    static void seedCapturedScalars(AbstractObjectInstance object, RewindRecreateContext ctx) {
        if (ctx.state() == null || ctx.state().compactGenericState() == null) {
            return;
        }
        GenericFieldCapturer.restoreObjectSubclassScalarsCompact(object, ctx.state().compactGenericState());
    }

    private static <T extends ObjectInstance> T nearest(
            RewindRecreateContext ctx,
            Class<T> type) {
        if (ctx.objectServices() == null || ctx.objectServices().objectManager() == null) {
            return null;
        }
        ObjectManager objectManager = ctx.objectServices().objectManager();
        ObjectSpawn spawn = ctx.spawn();
        T best = null;
        long bestDistance = Long.MAX_VALUE;
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (!type.isInstance(object) || object.isDestroyed()) {
                continue;
            }
            if (spawn == null) {
                return type.cast(object);
            }
            long dx = object.getX() - spawn.x();
            long dy = object.getY() - spawn.y();
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = type.cast(object);
            }
        }
        return best;
    }
}
