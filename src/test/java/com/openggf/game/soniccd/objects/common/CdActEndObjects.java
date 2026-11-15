package com.openggf.game.soniccd.objects.common;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.objects.ObjectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-side view of the engine's end-of-act objects (goal post $16, signpost $17, big ring $14 and the
 * results lines $3A, Level/_Objects/Level End/Main.asm, Results/Main.asm) and the level flags they drive, in
 * the terms the native RAM probe records them ({@code native/rehost-goal}, README there).
 */
public final class CdActEndObjects {
    private CdActEndObjects() { }

    /** One results line: its frame (0 the tally line, 1 the counters, 2 + act the zone line), routine, X and timer. */
    public record Line(int frame, int routine, int x, int screenY, int timer) { }

    /**
     * The engine's end-of-act state after a level frame: signpost routine and frame (-1 absent), the results
     * lines by frame, the shared bonuses, whether the results reported the next level, and the flags.
     */
    /** A signpost: its place, routine, frame and slot. */
    public record Sign(int x, int y, int routine, int frame, int slot) { }

    /** A goal post ($16) or big ring ($14, not its flash): ID, place and routine. */
    public record Marker(int id, int x, int y, int routine) { }

    public record Snapshot(List<Sign> signs, List<Marker> markers, List<Line> lines, int timeBonus, int ringBonus,
                           boolean nextLevel, boolean ctrlLocked, int ctrlHold, boolean specialStage,
                           int resultsSlot) { }

    public static Snapshot capture() {
        List<Sign> signs = new ArrayList<>();
        List<Marker> markers = new ArrayList<>();
        int resultsSlot = -1;
        List<Line> lines = new ArrayList<>();
        int timeBonus = 0, ringBonus = 0;
        boolean nextLevel = false;
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed()) continue;
            if (o instanceof CdSignpost s) {
                signs.add(new Sign(s.getX(), s.getY(), s.routine(), s.mapFrame(), s.getSlotIndex()));
            } else if (o instanceof CdGoalPost g) {
                markers.add(new Marker(0x16, g.getX(), g.getY(), g.routine()));
            } else if (o instanceof CdBigRing b && b.getSpawn().subtype() == 0) {
                markers.add(new Marker(0x14, b.getX(), b.getY(), b.routine()));
            } else if (o instanceof CdResults r) {
                CdResults.Bonuses b = r.bonuses();
                timeBonus = b.time;
                ringBonus = b.ring;
                nextLevel |= b.nextLevel;
                if (r.lineFrame() == 0 || r.routine() < 4) resultsSlot = r.getSlotIndex();
                if (r.routine() >= 4) lines.add(new Line(r.lineFrame(), r.routine(), r.getX(), r.screenY(), r.timer()));
            }
        }
        lines.sort(java.util.Comparator.comparingInt(Line::frame));
        var level = (CdLevel) GameServices.level().getCurrentLevel();
        var flags = level.flags();
        return new Snapshot(List.copyOf(signs), List.copyOf(markers), List.copyOf(lines), timeBonus, ringBonus, nextLevel,
                flags.ctrlLocked, flags.playerCtrlHold & 0xFFFF, flags.specialStage, resultsSlot);
    }

    /** The results objects still waiting to spawn their lines (routines 0 and 2), with their timers. */
    public static List<int[]> pendingResults() {
        List<int[]> out = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o instanceof CdResults r && !r.isDestroyed() && r.routine() < 4) out.add(new int[] {r.routine(), r.timer(), r.getSlotIndex()});
            if (o instanceof CdSignpost s && !s.isDestroyed()) out.add(new int[] {0x17, s.routine(), s.getSlotIndex()});
        }
        return out;
    }
}
