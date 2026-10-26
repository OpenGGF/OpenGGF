package com.openggf.game.sonic2.competition;

import com.openggf.level.rings.NativeCompetitionRingStreaming;
import com.openggf.level.rings.RingSpawn;

import java.util.List;

/** RingsManager_Init/Main (s2.asm:31799-31923), with two address pairs and shared ring status. */
public final class Sonic2CompetitionRingStreaming implements NativeCompetitionRingStreaming {
    private List<RingSpawn> spawns = List.of();
    private final int[] starts = new int[2];
    private final int[] ends = new int[2];

    private record State(int firstStart, int firstEnd, int secondStart, int secondEnd) { }

    @Override public void reset(List<RingSpawn> spawns, int initialCameraX) {
        this.spawns = List.copyOf(spawns);
        starts[0] = ends[0] = 0;
        updateSlot(0, initialCameraX);
        // Init copies P1's pair; P2 first diverges in RingsManager_Main.
        starts[1] = starts[0]; ends[1] = ends[0];
    }

    @Override public void update(int firstCameraX, int secondCameraX) {
        updateSlot(0, firstCameraX);
        updateSlot(1, secondCameraX);
    }

    private void updateSlot(int slot, int cameraX) {
        int x = cameraX & 0xffff;
        int left = x <= 8 ? 1 : x - 8;
        int right = (left + 320 + 16) & 0xffff;
        starts[slot] = seek(starts[slot], left);
        ends[slot] = seek(ends[slot], right);
    }

    private int seek(int cursor, int edge) {
        while (cursor < spawns.size() && spawns.get(cursor).x() < edge) cursor++;
        while (cursor > 0 && spawns.get(cursor - 1).x() >= edge) cursor--;
        return cursor;
    }

    @Override public int start(int slot) { return starts[slot]; }
    @Override public int end(int slot) { return ends[slot]; }
    @Override public Object capture() { return new State(starts[0], ends[0], starts[1], ends[1]); }
    @Override public void restore(Object snapshot) {
        State s = (State) snapshot;
        starts[0] = s.firstStart; ends[0] = s.firstEnd;
        starts[1] = s.secondStart; ends[1] = s.secondEnd;
    }
    @Override public void swapSlots() {
        // teleport_swap_table swaps Ring_Manager_Addresses only (s2.asm:26177).
        int s = starts[0]; starts[0] = starts[1]; starts[1] = s;
        int e = ends[0]; ends[0] = ends[1]; ends[1] = e;
    }
}
