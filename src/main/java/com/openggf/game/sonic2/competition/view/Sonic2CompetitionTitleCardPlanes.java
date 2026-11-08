package com.openggf.game.sonic2.competition.view;

import com.openggf.game.sonic2.titlecard.TitleCardManager;
import com.openggf.game.sonic2.titlecard.TitleCardNativeDisplayAccess;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionPlanes.PlaneId;
import static com.openggf.game.sonic2.titlecard.TitleCardNativeDisplayAccess.Foreground;

/** DrawLevelTitleCard projection. All requests and their consumption belong to Obj34. */
public final class Sonic2CompetitionTitleCardPlanes {
    private final TitleCardManager title;
    private final Sonic2CompetitionPlanes planes;
    private final Sonic2CompetitionPlaneDraw draw;
    private final Sonic2CompetitionPlaneScroll cameras;

    public Sonic2CompetitionTitleCardPlanes(TitleCardManager title, Sonic2CompetitionPlanes planes,
            Sonic2CompetitionPlaneDraw draw, Sonic2CompetitionPlaneScroll cameras) {
        this.title = java.util.Objects.requireNonNull(title);
        this.planes = java.util.Objects.requireNonNull(planes);
        this.draw = java.util.Objects.requireNonNull(draw);
        this.cameras = java.util.Objects.requireNonNull(cameras);
    }
    private static PlaneId plane(Foreground foreground) {
        return foreground == Foreground.PLAYER_ONE ? PlaneId.FOREGROUND_P1 : PlaneId.FOREGROUND_P2;
    }
    /** The native host resumes at the leading leave pass after omitted arrival.
     * This is level initialization, never draw-time terrain reconstruction. The
     * canonical untouched LeftOut state identifies that boundary without a clock.
     */
    public void initializeOmittedArrival() {
        var state = TitleCardNativeDisplayAccess.read(title);
        if (!state.twoPlayer() || !state.leaving() || state.parts().size() != 6) return;
        var left = state.parts().get(5);
        if (left.routine() != 14 || left.location() != 10 || left.x() != left.target()) return;
        for (int player = 0; player < 2; player++)
            for (int row = 0; row < 32; row++) for (int column = 0; column < 64; column++) {
                int word = TitleCardNativeDisplayAccess.foregroundWord(title, player, column, row);
                if (word != 0) planes.write(player == 0 ? PlaneId.FOREGROUND_P1 : PlaneId.FOREGROUND_P2,
                        column, row, word);
            }
    }
    /** Vint_TitleCard invokes this before refreshing P1's camera copy. */
    public void serviceVBlank() {
        // loc_155C6 / loc_15614 / loc_15670 / loc_156CE (s2.asm:28998-29124):
        // Obj34 supplies the 2P dimensions/words and both semantic destinations.
        for (var fill : TitleCardNativeDisplayAccess.pendingFills(title)) {
            var destination = fill.destination();
            for (int row = 0; row < fill.rows(); row++) for (int column = 0; column < fill.columns(); column++)
                planes.write(plane(destination.plane()), destination.column() + column,
                        destination.row() + row, fill.tileWord());
        }
        var reveals = TitleCardNativeDisplayAccess.pendingRowReveals(title);
        if (!reveals.isEmpty()) {
            var live = cameras.capture().live();
            var first = live.get(0); var second = live.get(1);
            // loc_15714 / loc_15758 (s2.asm:29125-29179): P2 first. The ROM
            // reads LIVE Camera_X_pos_P2 at $EE20 (s2.constants.asm:1226-1227)
            // and Camera_X_pos at $EE00 (s2.constants.asm:1215-1216), not
            // Camera_Positions_Copy (s2.constants.asm:1268-1273).
            // P2's copy remains stale independently;
            // using it here was an incorrect interpretation of the design.
            planes.apply(draw.titleCardForegroundRows(
                    new Sonic2CompetitionPlaneDraw.CameraCopy(first.x(), first.y()),
                    new Sonic2CompetitionPlaneDraw.CameraCopy(second.x(), second.y()),
                    reveals.getFirst().screenY()));
        }
    }
}
