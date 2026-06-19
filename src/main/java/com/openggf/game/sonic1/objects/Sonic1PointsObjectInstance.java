package com.openggf.game.sonic1.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.AbstractPointsObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

/**
 * Sonic 1 points popup object (Obj29).
 * <p>
 * Uses Sonic 1 point mappings loaded by {@code Sonic1ObjectArtProvider}:
 * frame 0=100, 1=200, 2=500, 3=1000, 4=10, 5=10000, 6=100000.
 */
public class Sonic1PointsObjectInstance extends AbstractPointsObjectInstance implements RewindRecreatable {

    public Sonic1PointsObjectInstance(ObjectSpawn spawn, ObjectServices services, int points) {
        super(spawn, "S1Points", services, points);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new Sonic1PointsObjectInstance(ctx.spawn(), ctx.objectServices(), 0);
    }

    @Override
    protected int getFrameForScore(int score) {
        return switch (score) {
            case 10 -> 4;
            case 100 -> 0;
            case 200 -> 1;
            case 500 -> 2;
            case 1000 -> 3;
            case 10000 -> 5;
            case 100000 -> 6;
            default -> 0;
        };
    }

    /**
     * ROM-translated callers can assign Obj29 frame index directly.
     */
    public void setScoreFrameIndex(int frameIndex) {
        this.scoreFrame = Math.max(0, frameIndex);
    }
}
