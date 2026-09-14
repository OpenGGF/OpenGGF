package com.openggf.game.sonic2.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Obj6F's split-name presentation: independent heading objects and Super-message
 * movement. Coordinates are screen pixels (ROM x_pixel minus 128).
 * This owns presentation clocks only; tally/score remain in the results owner.
 */
public final class SplitNameResultsMessages {
    public enum Phase { ARRIVAL, LEAVE, ENTER_SUPER, HOLD, DISPLAY_ONLY, COMPLETE }
    public record Line(int frame, int x, int y) { }
    public record Snapshot(Phase phase, int mainX, int titleX, int nameX, int superX, int timer) { }
    private final boolean gotEmerald, allEmeralds;
    private Phase phase = Phase.ARRIVAL;
    private int mainX = 448, titleX, nameX, superX = -208, timer;

    public SplitNameResultsMessages(boolean gotEmerald, boolean allEmeralds) {
        this.gotEmerald = gotEmerald;
        this.allEmeralds = allEmeralds;
        titleX = gotEmerald && allEmeralds ? -60 : -48;
        nameX = gotEmerald && allEmeralds ? -164 : -120;
    }

    public void tick() {
        switch (phase) {
            case ARRIVAL -> {
                mainX = toward(mainX, 160, 16);
                titleX = toward(titleX, gotEmerald && allEmeralds ? 228 : 240, 16);
                nameX = toward(nameX, allEmeralds ? 124 : 168, 16);
            }
            case LEAVE -> {
                if (mainX == 448) {
                    // Obj6F_InitAndMoveSuperMsg; later objects execute this same pass.
                    phase = Phase.ENTER_SUPER;
                    titleX -= 4;
                    titleX = toward(titleX, 160, 16);
                    superX = toward(superX, 160, 16);
                } else {
                    mainX += 32;
                    titleX -= 32;
                }
                nameX -= 32;
            }
            case ENTER_SUPER -> {
                // Obj6F_MoveAndDisplay latches the timer on the pass after arrival.
                if (mainX == 160) { phase = Phase.HOLD; timer = 180; }
                else mainX = toward(mainX, 160, 16);
                titleX = toward(titleX, 160, 16);
                superX = toward(superX, 160, 16);
            }
            case HOLD -> {
                titleX = toward(titleX, 160, 16);
                superX = toward(superX, 160, 16);
                if (--timer == 0) phase = Phase.DISPLAY_ONLY;
            }
            case DISPLAY_ONLY -> phase = Phase.COMPLETE;
            case COMPLETE -> { }
        }
    }

    /** Called by the tally owner on its zero-count pass, before later objects run. */
    public void startSuper() {
        if (!gotEmerald || !allEmeralds || phase != Phase.ARRIVAL) return;
        phase = Phase.LEAVE;
        nameX -= 32; // Obj6F_Knuckles observes routine $30 in the same object pass.
    }

    public boolean complete() { return phase == Phase.COMPLETE; }
    public List<Line> lines() {
        List<Line> result = new ArrayList<>();
        boolean superMessage = phase.ordinal() >= Phase.ENTER_SUPER.ordinal();
        result.add(new Line(superMessage ? 27 : allEmeralds ? 25 : gotEmerald ? 4 : 0,
                mainX, superMessage ? 34 : 42));
        if (gotEmerald || allEmeralds) result.add(new Line(superMessage ? 26 : allEmeralds ? 22 : 1,
                titleX, superMessage ? 16 : 24));
        // Obj6F_Knuckles deletes at hardware x<=32; its arrival clipping is x>=80.
        if (gotEmerald && !superMessage && (phase == Phase.ARRIVAL ? nameX >= -48 : nameX > -96))
            result.add(new Line(29, nameX, 24));
        if (superMessage) result.add(new Line(28, superX, 52));
        return List.copyOf(result);
    }
    public Snapshot capture() { return new Snapshot(phase, mainX, titleX, nameX, superX, timer); }
    public void restore(Snapshot snapshot) {
        phase = snapshot.phase(); mainX = snapshot.mainX(); titleX = snapshot.titleX();
        nameX = snapshot.nameX(); superX = snapshot.superX(); timer = snapshot.timer();
    }
    private static int toward(int value, int target, int speed) {
        return value < target ? Math.min(value + speed, target) : Math.max(value - speed, target);
    }
}
