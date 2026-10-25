package openggf.timeattack.mp;

import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.net.chat.EnglishQuickChat;
import com.openggf.net.protocol.QuickChatPhrase;

import java.util.Arrays;
import java.util.List;

/**
 * Eight-wedge controller quick chat wheel. Wedges follow {@link QuickChatPhrase} order clockwise
 * from north; the full phrase is previewed below the wheel so wedges carry only short captions.
 * Every size is fixed, so the wheel fits a 256x224 view and is simply centred on wider ones.
 */
public final class QuickChatRadialMenu {
    private static final int OUTER = 82, INNER = 30, RIM = 2, POP = 3, HUB = 24;
    private static final double GAP = 1.0;
    private static final int SELECTED_FILL = 0xE8284868;
    private static final int DIM_RIM = 0x8067DDF5;

    /** Short captions; each keeps the key words of its {@link EnglishQuickChat} phrase. */
    private final String[][] CAPTIONS = {
            {"BRUTAL!"}, {"TOO", "SLOW!"}, {"KNOW THE", "WAY?"}, {"THIS IS", "THE WAY"},
            {"NO", "GOOD!"}, {"THIS IS", "ZONE!"}, {"GOOD", "LUCK!"}, {"WELL", "PLAYED!"}};
    /** Caption centres, tuned so each two-line box stays inside its wedge between the spokes. */
    private final int[] CAPTION_CENTRE = {0, -58, 42, -42, 55, 0, 42, 42, 0, 57, -42, 42, -55, 0, -42, -42};
    /** Hub notch centres pointing at the chosen wedge, clear of the hub title. */
    private final int[] NOTCH = {0, -18, 13, -13, 18, 0, 13, 13, 0, 18, -13, 13, -18, 0, -13, -13};

    /** Row runs as (dy, dx, width) triples relative to the wheel centre, built once per chooser; creator mods cannot keep array caches in static state. */
    private final int[][] BODY = band(INNER, OUTER - RIM, true);
    private final int[][] EDGE = band(OUTER - RIM, OUTER, true);
    private final int[][] LIFT = band(OUTER, OUTER + POP, true);
    private final int[] HUB_DISC = band(0, HUB, false)[0];

    public QuickChatRadialMenu() { }

    /** {@code selected} is null while the stick is centred: nothing is highlighted or implied. */
    public void draw(PixelCanvas canvas, QuickChatPhrase selected, String zoneName, boolean lobby) {
        int chosen = selected == null ? -1 : selected.ordinal();
        int panelHeight = 4 + HudTextLayout.NAME_LINE_HEIGHT + HudTextLayout.LINE_HEIGHT;
        int top = Math.max(2, (canvas.height() - 2 * (OUTER + POP) - 4 - panelHeight) / 2);
        int cx = canvas.width() / 2, cy = top + OUTER + POP;

        for (int s = 0; s < 8; s++) {
            boolean on = s == chosen;
            runs(canvas, cx, cy, BODY[s], on ? SELECTED_FILL : HudTextLayout.FILL);
            runs(canvas, cx, cy, EDGE[s], on ? HudTextLayout.GOLD
                    : chosen < 0 ? HudTextLayout.ACCENT : DIM_RIM);
            if (on) runs(canvas, cx, cy, LIFT[s], HudTextLayout.GOLD);
            int color = on ? HudTextLayout.GOLD : chosen < 0 ? HudTextLayout.WHITE : HudTextLayout.MUTED;
            String[] caption = CAPTIONS[s];
            int y = cy + CAPTION_CENTRE[2 * s + 1] - (caption.length * 9 - 2) / 2;
            for (String line : caption) {
                CompactFont.draw(canvas, line, cx + CAPTION_CENTRE[2 * s] - CompactFont.width(line, 1) / 2,
                        y, 1, color);
                y += 9;
            }
        }

        runs(canvas, cx, cy, HUB_DISC, HudTextLayout.FILL);
        CompactFont.draw(canvas, "QUICK", cx - CompactFont.width("QUICK", 1) / 2, cy - 8, 1, HudTextLayout.ACCENT);
        CompactFont.draw(canvas, "CHAT", cx - CompactFont.width("CHAT", 1) / 2, cy + 1, 1, HudTextLayout.ACCENT);
        if (chosen >= 0) {
            canvas.fill(cx + NOTCH[2 * chosen] - 2, cy + NOTCH[2 * chosen + 1] - 2, 4, 4, HudTextLayout.GOLD);
        }

        // Preview and hints share one measured HUD panel, so its height never changes with the choice.
        HudTextLayout.Line preview = chosen < 0
                ? HudTextLayout.Line.named("", "Aim the right stick", HudTextLayout.MUTED, "", HudTextLayout.MUTED)
                : HudTextLayout.Line.named("", EnglishQuickChat.text(selected, zoneName),
                        HudTextLayout.WHITE, "", HudTextLayout.GOLD);
        String hint = lobby ? "RS AIM  RT SEND  B / RS CLICK CLOSE" : "RS AIM  RT SEND  RS CLICK CLOSE";
        HudTextLayout.Panel panel = HudTextLayout.panelAt(0, cy + OUTER + POP + 4, canvas.width() - 8,
                List.of(preview, new HudTextLayout.Line(hint, HudTextLayout.MUTED)),
                chosen < 0 ? HudTextLayout.ACCENT : HudTextLayout.GOLD);
        HudTextLayout.drawPanel(canvas, new HudTextLayout.Panel((canvas.width() - panel.width()) / 2,
                panel.y(), panel.width(), panel.height(), panel.lines(), panel.accent()));
    }

    private static void runs(PixelCanvas canvas, int cx, int cy, int[] runs, int argb) {
        for (int i = 0; i < runs.length; i += 3) canvas.fill(cx + runs[i + 1], cy + runs[i], runs[i + 2], 1, argb);
    }

    /** Rasterises the annulus {@code inner <= r < outer} into per-wedge row runs, with spoke gaps. */
    private static int[][] band(double inner, double outer, boolean wedges) {
        int[][] runs = new int[8][0];
        int[] counts = new int[8];
        int extent = (int) Math.ceil(outer);
        for (int y = -extent; y < extent; y++) {
            int open = -1, start = 0;
            for (int x = -extent; x <= extent; x++) {
                int s = x < extent ? wedge(x + 0.5, y + 0.5, inner, outer, wedges) : -1;
                if (s == open) continue;
                if (open >= 0) {
                    if (counts[open] + 3 > runs[open].length) {
                        runs[open] = Arrays.copyOf(runs[open], Math.max(48, runs[open].length * 2));
                    }
                    runs[open][counts[open]++] = y;
                    runs[open][counts[open]++] = start;
                    runs[open][counts[open]++] = x - start;
                }
                open = s;
                start = x;
            }
        }
        for (int s = 0; s < 8; s++) runs[s] = Arrays.copyOf(runs[s], counts[s]);
        return runs;
    }

    /** Wedge 0 is centred on north, counting clockwise; -1 outside the band or inside a spoke. */
    private static int wedge(double px, double py, double inner, double outer, boolean wedges) {
        double r = Math.hypot(px, py);
        if (r < inner || r >= outer) return -1;
        if (!wedges) return 0;
        double step = Math.PI / 4;
        double angle = Math.atan2(px, -py) + step / 2;
        if (angle < 0) angle += 2 * Math.PI;
        double offset = angle % step;
        if (r * Math.sin(Math.min(offset, step - offset)) < GAP) return -1;
        return (int) (angle / step) % 8;
    }
}
