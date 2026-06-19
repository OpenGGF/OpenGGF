package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.AbstractPointsObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

/**
 * Sonic 2 floating points display object (Obj29).
 * <p>
 * Frame indices based on obj29.asm mappings:
 * Frame 0: "100", Frame 1: "200", Frame 2: "500",
 * Frame 3: "1000", Frame 4: "10", Frame 5: "1000" alt (chain bonus max).
 */
public class PointsObjectInstance extends AbstractPointsObjectInstance implements RewindRecreatable {

    public PointsObjectInstance(ObjectSpawn spawn, ObjectServices services, int points) {
        super(spawn, "Points", services, points);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new PointsObjectInstance(ctx.spawn(), ctx.objectServices(), 0);
    }

    @Override
    protected int getFrameForScore(int score) {
        return switch (score) {
            case 10 -> 4;      // "10" display
            case 20 -> 0;      // No dedicated "20" graphic; use "100"
            case 50 -> 2;      // No dedicated "50" graphic; use "500"
            case 100 -> 0;     // "100"
            case 200 -> 1;     // "200"
            case 500 -> 2;     // "500"
            case 1000 -> 5;    // "1000" (large version)
            default -> 0;      // Default to "100"
        };
    }
}
