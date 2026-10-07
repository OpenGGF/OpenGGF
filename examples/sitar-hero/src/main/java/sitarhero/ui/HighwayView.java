package sitarhero.ui;

import com.openggf.mods.scene.SceneCanvas;
import sitarhero.controls.ControlSettings;
import sitarhero.model.ChartNote;
import sitarhero.model.RhythmSession;
import java.util.List;

/** One readable highway; update owns feedback timers, draw only reads them. */
public final class HighwayView {
    private int hits, misses;
    private long feedbackUntil;
    private boolean good;

    public void observe(RhythmSession session, long ticks) {
        if (session.hits() != hits || session.misses() != misses) {
            good = session.hits() > hits;
            feedbackUntil = ticks + 12;
            hits = session.hits(); misses = session.misses();
        }
    }
    public void reset() { hits = 0; misses = 0; feedbackUntil = 0; }

    public void draw(SceneCanvas c, RhythmSession session, long position, int rate,
                     ControlSettings settings, boolean drums, int lanes, int x, int top,
                     int width, int height, long ticks) {
        int mid = x + width / 2, bottom = top + height;
        long display = position + (long) settings.displayOffsetMs() * rate / 1000;
        long ahead = rate * 200L / settings.scrollSpeed();
        int color = session.starActive() && !settings.reducedFlashes() ? SitarUi.CYAN : 0xFFBCD7DC;
        for (int y = top; y <= bottom; y++) {
            int w = highwayWidth(y, top, height, width);
            c.fill(mid - w / 2 - 1, y, w + 2, 1, color);
            c.fill(mid - w / 2, y, w, 1, 0xED111B32);
            for (int lane = 1; lane < lanes; lane++) c.fill(mid - w / 2 + lane * w / lanes, y, 1, 1, 0x664B719A);
        }
        long beat = session.chart().samplesPerBeat();
        for (long at = Math.max(0, display / beat) * beat; at <= display + ahead; at += beat) {
            int y = noteY(at - display, ahead, top, height);
            if (y < top || y > bottom) continue;
            int w = highwayWidth(y, top, height, width);
            c.fill(mid - w / 2, y, w, 1, 0x5548729A);
        }
        List<ChartNote> notes = session.chart().notes();
        for (int i = Math.max(0, session.nextNote() - 3); i < notes.size(); i++) {
            ChartNote note = notes.get(i);
            if (note.onset() > display + ahead) break;
            if (note.end() < display - rate / 5 || session.status(i) == 2) continue;
            int y = noteY(note.onset() - display, ahead, top, height);
            if (note.end() > note.onset()) {
                int endY = noteY(note.end() - display, ahead, top, height);
                for (int tailY = Math.max(top, endY); tailY < Math.min(bottom, y); tailY += 2)
                    for (int lane = 0; lane < lanes; lane++) if ((note.lanes() & 1 << lane) != 0)
                        c.fill(laneX(mid, tailY, lane, lanes, settings.lefty(), top, height, width) - 1,
                                tailY, 3, 2, laneColor(lane));
            }
            if (session.status(i) == 1 || y < top || y > bottom + 5) continue;
            if (drums && (note.lanes() & 16) != 0) {
                int w = highwayWidth(y, top, height, width);
                c.fill(mid - w / 2 + 2, y - 2, w - 4, 5, SitarUi.GOLD);
            }
            for (int lane = 0; lane < lanes; lane++) if ((note.lanes() & 1 << lane) != 0)
                gem(c, laneX(mid, y, lane, lanes, settings.lefty(), top, height, width), y,
                        3 + (y - top) * 3 / height, laneColor(lane), note.hopo(), note.phrase() >= 0);
        }
        for (int lane = 0; lane < lanes; lane++) gem(c,
                laneX(mid, bottom, lane, lanes, settings.lefty(), top, height, width), bottom, 6,
                (session.held() & 1 << lane) != 0 ? SitarUi.CREAM : laneColor(lane), false, false);
        if (drums) c.fill(x + 8, bottom + 10, width - 16, 5,
                (session.held() & 16) != 0 ? SitarUi.CREAM : SitarUi.GOLD);
        if (ticks < feedbackUntil) {
            SitarUi.center(c, good ? "HIT!" : "MISS", x, top + 8, width, good ? SitarUi.CYAN : 0xFFFF5969);
            if (!settings.reducedFlashes()) c.fill(x + 8, bottom - 3, width - 16, 2, good ? SitarUi.CYAN : 0xFFFF5969);
        }
    }
    private static int highwayWidth(int y, int top, int height, int width) {
        return width / 3 + (y - top) * (width - width / 3) / height;
    }
    private static int laneX(int mid, int y, int lane, int count, boolean lefty, int top, int height, int width) {
        if (lefty) lane = count - 1 - lane;
        int w = highwayWidth(y, top, height, width);
        return mid - w / 2 + (lane * 2 + 1) * w / (count * 2);
    }
    private static int noteY(long distance, long ahead, int top, int height) {
        double t = 1 - distance / (double) ahead;
        return (int) Math.round(top + height * t * Math.abs(t));
    }
    private static int laneColor(int lane) {
        return switch (lane) { case 0 -> 0xFF51DC61; case 1 -> 0xFFF85765; case 2 -> SitarUi.GOLD;
            case 3 -> 0xFF609CFF; default -> 0xFFFF934B; };
    }
    private static void gem(SceneCanvas c, int x, int y, int r, int color, boolean hopo, boolean star) {
        c.fill(x - r, y - r + 2, r * 2 + 1, r * 2 - 2, SitarUi.INK);
        c.fill(x - r + 1, y - r + 1, r * 2 - 1, r * 2 - 1, color);
        c.fill(x - r + 2, y - r, r * 2 - 3, 2, SitarUi.CREAM);
        if (hopo) c.fill(x - 1, y - 2, 3, 3, 0xFFFFFFFF);
        if (star) { c.fill(x - 3, y - 1, 7, 1, 0xFFFFFFFF); c.fill(x, y - 4, 1, 7, 0xFFFFFFFF); }
    }
}
