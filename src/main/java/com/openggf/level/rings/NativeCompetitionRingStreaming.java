package com.openggf.level.rings;

import java.util.List;

/** Internal independent cursor pairs over one shared native ring table. */
public interface NativeCompetitionRingStreaming {
    void reset(List<RingSpawn> spawns, int initialCameraX);
    void update(int firstCameraX, int secondCameraX);
    int start(int slot);
    int end(int slot);
    Object capture();
    void restore(Object snapshot);
    void swapSlots();
}
