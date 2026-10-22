package openggf.timeattack;

import com.openggf.mods.ui.LevelOverlayCanvas;
import openggf.timeattack.mp.HudTextLayout;

import java.util.ArrayList;
import java.util.List;

/** Compact run clock, with a single bottom strip while the stock act-clear card is visible. */
public final class TimeAttackHud {
    private TimeAttackHud() { }

    public static void draw(LevelOverlayCanvas canvas, TimeAttackHudState state) {
        HudTextLayout.Panel panel = layout(canvas.width(), canvas.height(), state);
        if (panel != null) HudTextLayout.drawPanel(canvas, panel);
    }

    public static HudTextLayout.Panel layout(int width, int height, TimeAttackHudState state) {
        if (state == null || !state.active()) return null;
        if (state.finished()) {
            String line = "TIME " + TimeAttackTimeFormat.frames(state.elapsedDisplayFrames())
                    + (state.newBest() ? "  NEW BEST" : "  FINISHED");
            if (!state.saveNotice().isEmpty()) {
                line = TimeAttackTimeFormat.frames(state.elapsedDisplayFrames()) + "  " + state.saveNotice();
            }
            return HudTextLayout.panel(width, height - 22, 220,
                    List.of(new HudTextLayout.Line(line,
                            state.newBest() ? HudTextLayout.GOLD
                                    : state.saveNotice().isEmpty() ? HudTextLayout.WHITE : 0xFFFF8E83)),
                    state.newBest() ? HudTextLayout.GOLD : HudTextLayout.ACCENT);
        }
        List<HudTextLayout.Line> lines = new ArrayList<>();
        lines.add(new HudTextLayout.Line("TIME "
                + TimeAttackTimeFormat.frames(state.elapsedDisplayFrames()), HudTextLayout.WHITE));
        if (state.bestTimeFrames() >= 0) {
            lines.add(new HudTextLayout.Line("BEST " + TimeAttackTimeFormat.frames(state.bestTimeFrames()),
                    HudTextLayout.MUTED));
        }
        String delta = TimeAttackTimeFormat.delta(state.lastSplitDelta());
        if (!delta.isEmpty()) {
            lines.add(new HudTextLayout.Line("SPLIT " + delta,
                    state.lastSplitDelta() < 0 ? 0xFF79E5A8 : 0xFFFF8E83));
        }
        return HudTextLayout.panel(width, 4, HudTextLayout.PANEL_WIDTH, lines, HudTextLayout.ACCENT);
    }
}
