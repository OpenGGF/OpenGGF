package com.openggf.game.sonic3k.objects;

import com.openggf.game.CheckpointState;
import com.openggf.level.objects.ObjectServices;

/**
 * The Sky Sanctuary act-1 pseudo-starpost.
 *
 * <p>SSZ1's placement list has no {@code $34} subtype {@code $01}: the first checkpoint is written
 * by code. Both {@code Obj_SSZCutsceneBridge} on retracting ({@code loc_44FBA}) and cutscene
 * Knuckles on leaving the screen ({@code loc_65976}) write {@code Last_star_post_hit = 1},
 * {@code Saved_X_pos = $140}, {@code Saved_Y_pos = $C6C} and call {@code Save_Level_Data}; the
 * bridge additionally does {@code clr.l (Saved_timer).w}. A death after that respawns at the
 * bridge, so {@code SSZ1_ScreenInit} skips the arrival and {@code loc_4501A} spawns the bridge
 * already retracted.
 */
final class SszCheckpointOps {
    /** {@code move.b #1,(Last_star_post_hit).w}. */
    private static final int STAR_POST_MARK = 1;

    private SszCheckpointOps() {
    }

    static void writeCutsceneStarPost(ObjectServices services, int savedX, int savedY,
                                      boolean clearSavedTimer) {
        if (!(services.checkpointState() instanceof CheckpointState checkpoint)) {
            return;
        }
        checkpoint.saveCheckpoint(STAR_POST_MARK, savedX, savedY, false);
        if (clearSavedTimer) {
            checkpoint.clearSavedActTimer();
        }
    }
}
