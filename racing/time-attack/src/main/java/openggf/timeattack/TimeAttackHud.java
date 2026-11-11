package openggf.timeattack;

import com.openggf.mods.ui.LevelOverlayCanvas;
import openggf.timeattack.mp.HudTextLayout;
import openggf.timeattack.ui.LapTimeFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * The best time and the latest split, directly under the game's own HUD rows (TIME, whose clock is
 * the attempt's, and RINGS; see {@link TimeAttackRuntime#hudProfile}) and lined up with their
 * labels; a single bottom strip while the stock act-clear card is visible.
 *
 * <p>On a Competition arena the band under the native rows belongs to the arena's own HUD, and the
 * lap rows live in the room's right column ({@code MultiplayerHudRenderer}), so only the finish
 * strip is drawn, with the total as the ROM shows it (the sum of the truncated laps).
 */
public final class TimeAttackHud {
    /** Native y just below the RINGS row (y 24, sixteen pixels tall) with a two-pixel gap. */
    static final int UNDER_NATIVE_ROWS = 42;

    private TimeAttackHud() { }

    public static void draw(LevelOverlayCanvas canvas, TimeAttackHudState state) {
        HudTextLayout.Panel panel = layout(canvas.width(), canvas.height(), state, canvas.hudOriginX());
        if (panel != null) HudTextLayout.drawPanel(canvas, panel);
    }

    /** {@link #layout(int, int, TimeAttackHudState, int)} with the native HUD centred. */
    public static HudTextLayout.Panel layout(int width, int height, TimeAttackHudState state) {
        return layout(width, height, state, (width - 320) / 2);
    }

    /** @param hudOriginX the native HUD frame's left edge on screen ({@code LevelOverlayCanvas.hudOriginX}) */
    public static HudTextLayout.Panel layout(int width, int height, TimeAttackHudState state, int hudOriginX) {
        if (state == null || !state.active()) return null;
        CompetitionHudState competition = state.competition();
        boolean arena = competition.isCompetition() && competition.isArena();
        if (state.finished()) {
            String time = arena ? LapTimeFormat.finish(competition.trackKind(), state.elapsedDisplayFrames(),
                    competition.completedLapFrames()) : TimeAttackTimeFormat.frames(state.elapsedDisplayFrames());
            String line = "TIME " + time
                    + (state.newBest() ? "  NEW BEST" : "  FINISHED");
            if (!state.saveNotice().isEmpty()) {
                line = time + "  " + state.saveNotice();
            }
            return HudTextLayout.panel(width, HudTextLayout.finishStripTop(height), 220,
                    List.of(new HudTextLayout.Line(line,
                            state.newBest() ? HudTextLayout.GOLD
                                    : state.saveNotice().isEmpty() ? HudTextLayout.WHITE : 0xFFFF8E83)),
                    state.newBest() ? HudTextLayout.GOLD : HudTextLayout.ACCENT);
        }
        if (arena) return null;
        List<HudTextLayout.Line> lines = new ArrayList<>();
        if (state.bestTimeFrames() >= 0) {
            lines.add(new HudTextLayout.Line("BEST " + TimeAttackTimeFormat.frames(state.bestTimeFrames()),
                    HudTextLayout.MUTED));
        }
        String delta = TimeAttackTimeFormat.delta(state.lastSplitDelta());
        if (!delta.isEmpty()) {
            lines.add(new HudTextLayout.Line("SPLIT " + delta,
                    state.lastSplitDelta() < 0 ? 0xFF79E5A8 : 0xFFFF8E83));
        }
        if (lines.isEmpty()) return null;
        return HudTextLayout.panelAt(hudOriginX + HudTextLayout.NATIVE_LABEL_X - HudTextLayout.TEXT_INSET,
                UNDER_NATIVE_ROWS, HudTextLayout.PANEL_WIDTH, lines, HudTextLayout.ACCENT);
    }
}
