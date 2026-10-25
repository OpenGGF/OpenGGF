package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.net.protocol.QuickChatPhrase;
import com.openggf.net.chat.EnglishQuickChat;
import openggf.timeattack.ui.ViewInput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Canned messages only. Race input uses toggle + number edges without holding or pausing gameplay. */
public final class QuickChatChooser {
    private boolean open;
    private int selected;

    public boolean isOpen() { return open; }
    public void open() { open = true; selected = 0; }
    public void close() { open = false; }

    /** The lobby also accepts ordinary menu directions/confirm and B to close. */
    public void update(ViewInput input, Consumer<QuickChatPhrase> send) {
        if (!open) return;
        if (input.back()) { close(); return; }
        if (input.up()) selected = Math.floorMod(selected - 1, QuickChatPhrase.values().length);
        if (input.down()) selected = (selected + 1) % QuickChatPhrase.values().length;
        if (number(input::keyPressed, send)) return;
        if (input.accept()) choose(selected, send);
    }

    /** Returns whether a number edge selected a phrase, keeping vote/retry handling separate. */
    public boolean number(java.util.function.IntPredicate pressed, Consumer<QuickChatPhrase> send) {
        if (!open) return false;
        for (int i = 0; i < QuickChatPhrase.values().length; i++) {
            if (pressed.test(SceneKeys.DIGIT_1 + i)) {
                choose(i, send);
                return true;
            }
        }
        return false;
    }

    private void choose(int index, Consumer<QuickChatPhrase> send) {
        send.accept(QuickChatPhrase.values()[index]);
        close();
    }

    /** Title and help rows sit above the numbered catalogue rows. */
    private static final int HEADER_LINES = 2;

    /** Measured name-font rows preserve catalogue punctuation and leave gameplay visible. */
    public HudTextLayout.Panel layout(int width, int height, String zoneName, String closeLabel) {
        List<HudTextLayout.Line> lines = new ArrayList<>();
        lines.add(new HudTextLayout.Line("QUICK CHAT", HudTextLayout.ACCENT));
        lines.add(new HudTextLayout.Line("1-8 SEND  " + closeLabel + " CLOSE", HudTextLayout.MUTED));
        QuickChatPhrase[] phrases = QuickChatPhrase.values();
        for (int i = 0; i < phrases.length; i++) {
            int key = selected == i ? HudTextLayout.GOLD : HudTextLayout.ACCENT;
            lines.add(HudTextLayout.Line.named((i + 1) + " ",
                    EnglishQuickChat.text(phrases[i], zoneName), HudTextLayout.WHITE, "", key));
        }
        int panelHeight = 4 + lines.stream().mapToInt(HudTextLayout.Line::height).sum();
        return HudTextLayout.panelAt(4, Math.max(4, (height - panelHeight) / 2), width - 8,
                lines, HudTextLayout.ACCENT);
    }

    /** The focused row swaps the cyan edge for gold beside its key number, clear of the wording. */
    public void draw(PixelCanvas canvas, String zoneName, String closeLabel) {
        HudTextLayout.Panel panel = layout(canvas.width(), canvas.height(), zoneName, closeLabel);
        HudTextLayout.drawPanel(canvas, panel);
        int rowTop = panel.y() + 2 + HEADER_LINES * HudTextLayout.LINE_HEIGHT
                + selected * HudTextLayout.NAME_LINE_HEIGHT;
        canvas.fill(panel.x(), rowTop, 2, HudTextLayout.NAME_LINE_HEIGHT, HudTextLayout.GOLD);
        canvas.fill(panel.x() + 3, rowTop + 4, 1, 5, HudTextLayout.GOLD);
    }
}
