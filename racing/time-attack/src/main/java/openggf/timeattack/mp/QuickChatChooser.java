package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneKeys;
import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInput;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.net.protocol.QuickChatPhrase;
import com.openggf.net.chat.EnglishQuickChat;
import openggf.timeattack.ui.ViewInput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Canned messages only. Race input stays independent of gameplay movement, jump and pause. */
public final class QuickChatChooser {
    private boolean open;
    private int selected;
    private int controllerId = -1;
    private boolean clickHeld, triggerHeld, controllerHelp;
    private int stickDirection, stickTicks;

    public boolean isOpen() { return open; }
    public void open() { open = true; selected = 0; controllerHelp = false; }
    public void close() { open = false; controllerHelp = false; }

    /** Loading or leaving a run invalidates the old pad baseline. */
    public void resetRaceInput() { close(); controllerId = -1; }

    /**
     * Race controls use only the right stick and trigger. Always sample, even while chat is
     * unavailable, so a held control cannot become a new press after retry or a mode change.
     */
    public void updateController(PhysicalInput input, boolean allowed, Consumer<QuickChatPhrase> send) {
        PhysicalGamepad pad = input.gamepad(controllerId);
        if (pad == null) {
            if (controllerId != -1 && controllerHelp) close();
            pad = input.gamepads().isEmpty() ? null : input.gamepads().getFirst();
            controllerId = pad == null ? -1 : pad.deviceId();
            clickHeld = pad != null && pad.buttonDown(PhysicalGamepad.BUTTON_RIGHT_THUMB);
            triggerHeld = pad != null && pad.axis(PhysicalGamepad.AXIS_RIGHT_TRIGGER) > 0.55f;
            stickDirection = pad == null ? 0 : stickDirection(pad.axis(PhysicalGamepad.AXIS_RIGHT_Y), 0);
            stickTicks = 0;
            if (!allowed) close();
            return; // Connecting or replacing a pad establishes its baseline.
        }
        boolean click = pad.buttonDown(PhysicalGamepad.BUTTON_RIGHT_THUMB);
        boolean trigger = pad.axis(PhysicalGamepad.AXIS_RIGHT_TRIGGER) > (triggerHeld ? 0.30f : 0.55f);
        int direction = stickDirection(pad.axis(PhysicalGamepad.AXIS_RIGHT_Y), stickDirection);
        boolean clicked = click && !clickHeld;
        boolean sent = trigger && !triggerHeld;
        boolean moved = direction != 0 && direction != stickDirection;
        stickTicks = direction != 0 && direction == stickDirection ? stickTicks + 1 : 0;
        moved |= direction != 0 && stickTicks >= 24 && (stickTicks - 24) % 4 == 0;
        clickHeld = click;
        triggerHeld = trigger;
        stickDirection = direction;
        if (!allowed) { close(); return; }
        if (clicked) {
            if (open) close();
            else { open(); controllerHelp = true; }
            return; // An opening click never also sends from a simultaneous trigger.
        }
        if (!open) return;
        if (moved) {
            selected = Math.floorMod(selected + direction, QuickChatPhrase.values().length);
            controllerHelp = true;
        }
        if (sent) choose(selected, send);
    }

    private static int stickDirection(float axis, int previous) {
        if (axis > (previous == 1 ? 0.30f : 0.55f)) return 1;
        if (axis < (previous == -1 ? -0.30f : -0.55f)) return -1;
        return 0;
    }

    public String raceHint(String keyboardToggle) {
        return keyboardToggle + (controllerId == -1 ? "" : " / RS CLICK") + " QUICK CHAT";
    }

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
        return layoutWithHelp(width, height, zoneName, "QUICK CHAT", "1-8 SEND  " + closeLabel + " CLOSE");
    }

    HudTextLayout.Panel raceLayout(int width, int height, String zoneName, String keyboardToggle) {
        return controllerHelp
                ? layoutWithHelp(width, height, zoneName, "QUICK CHAT  RS CLICK CLOSE",
                    "RS UP/DN  RIGHT TRIGGER SEND")
                : layout(width, height, zoneName, keyboardToggle);
    }

    private HudTextLayout.Panel layoutWithHelp(int width, int height, String zoneName, String title, String help) {
        List<HudTextLayout.Line> lines = new ArrayList<>();
        lines.add(new HudTextLayout.Line(title, HudTextLayout.ACCENT));
        lines.add(new HudTextLayout.Line(help, HudTextLayout.MUTED));
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
        draw(canvas, layout(canvas.width(), canvas.height(), zoneName, closeLabel));
    }

    public void drawRace(PixelCanvas canvas, String zoneName, String keyboardToggle) {
        draw(canvas, raceLayout(canvas.width(), canvas.height(), zoneName, keyboardToggle));
    }

    private void draw(PixelCanvas canvas, HudTextLayout.Panel panel) {
        HudTextLayout.drawPanel(canvas, panel);
        int rowTop = panel.y() + 2 + HEADER_LINES * HudTextLayout.LINE_HEIGHT
                + selected * HudTextLayout.NAME_LINE_HEIGHT;
        canvas.fill(panel.x(), rowTop, 2, HudTextLayout.NAME_LINE_HEIGHT, HudTextLayout.GOLD);
        canvas.fill(panel.x() + 3, rowTop + 4, 1, 5, HudTextLayout.GOLD);
    }
}
