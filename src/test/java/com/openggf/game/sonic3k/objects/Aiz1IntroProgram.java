package com.openggf.game.sonic3k.objects;

import com.openggf.tests.route.InputProgram;
import com.openggf.tests.route.InputRun;
import java.util.List;

/** Pad program whose first player input waits for the live intro handoff, if it is late. */
final class Aiz1IntroProgram {
    private final InputProgram.Cursor cursor;
    private boolean playerInputStarted;
    private int heldFrames;

    Aiz1IntroProgram(List<InputRun> runs, int firstRow) {
        cursor = new InputProgram.Cursor(runs, firstRow);
    }

    int next(boolean levelStarted) {
        int row = cursor.row();
        int mask = cursor.next();
        if (!playerInputStarted && mask != 0) {
            if (!levelStarted) {
                // loc_61F10 -> loc_61F22 waits for Knuckles' previous render flag
                // to clear. A wider viewport can extend that live exit. Preserve
                // the first non-neutral row until the production owner releases it.
                cursor.seekToRow(row);
                heldFrames++;
                return 0;
            }
            playerInputStarted = true;
        }
        return mask;
    }

    int row() { return cursor.row(); }
    boolean exhausted() { return cursor.exhausted(); }
    int heldFrames() { return heldFrames; }
}
